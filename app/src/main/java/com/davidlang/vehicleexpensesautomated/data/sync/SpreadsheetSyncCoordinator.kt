package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.batch.MergeAckStore
import com.davidlang.vehicleexpensesautomated.data.location.KnownStationStore
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseVehicleSyncIds
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.KnownStation
import com.davidlang.vehicleexpensesautomated.data.model.MergeAck
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.LocationBlobOverlay
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.PolicySyncBridge
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareApi
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.VehicleDefinitionOverlay
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val success: Boolean,
    val message: String,
    val vehiclesMerged: Int = 0,
    val expensesMerged: Int = 0,
    val fuelMerged: Int = 0,
    /** Remote LWW wins + new remote-only rows (for Stage C phase reset). */
    val fuelRemoteWins: Int = 0,
    val needsRemoteConsent: Boolean = false,
    val recoveryIntent: Intent? = null,
)

/** Per-destination fuel tab LWW stats. */
private data class FuelTabSyncStats(
    val upserted: Int,
    val remoteWins: Int,
)

@Singleton
class SpreadsheetSyncCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tabularApi: TabularShareApi,
    private val vehicleRepository: VehicleRepository,
    private val fuelRepository: FuelEntryRepository,
    private val expenseRepository: ExpenseEntryRepository,
    private val syncIdBackfill: SyncIdBackfill,
    private val photoBackupCoordinator: Lazy<PhotoBackupCoordinator>,
    private val photoStorage: PhotoStorageManager,
    /** Post-fuel LWW: field-merge partials + breaker-aware question rebuild. */
    private val batchFuelImportCoordinator: Lazy<BatchFuelImportCoordinator>,
    private val mergeAckStore: MergeAckStore,
    private val knownStationStore: KnownStationStore,
) {

    /**
     * Process-wide: only one spreadsheet sync pipeline at a time (hub all-dest,
     * per-dest edit Sync now, WorkManager). Callers queue on this mutex.
     */
    private val syncMutex = Mutex()

    /**
     * @param destId when non-null, sync **only** that destination (edit-form Sync now).
     *   Ignores [scope] list filtering other than requiring the dest to exist.
     *
     * One merge-in loop (Room only), then one field-merge and one post-merge,
     * then one publish loop. A point whose read failed is not published.
     */
    suspend fun syncNow(
        accountHint: String? = null,
        scope: SyncDestinationScope = SyncDestinationScope.CONFIGURED,
        destId: String? = null,
        onProgress: SyncProgressListener? = null,
    ): SyncResult = syncMutex.withLock {
        try {
            // So API-level waits (read or write 429) can show "Rate limited — waiting Ns (try k/n)…"
            SyncRateLimit.installProgress(onProgress)
            withContext(Dispatchers.IO) {
            syncIdBackfill.runIfNeeded()
            val store = SyncDestinationStore(context)
            val failureStore = SyncFailureStore(context)
            // Ghost failures for removed dest UUIDs never clear on success of new ids.
            failureStore.pruneToKnownDestinations(store)

            when (val resolved = resolveSyncPoints(store, scope, destId, accountHint)) {
                is SyncPoints.Stop -> return@withContext resolved.result
                is SyncPoints.Ready -> {
                    val points = resolved.points
                    if (points.size > 1) {
                        onProgress?.onStatus(
                            "Starting spreadsheet sync (${points.size} destinations)…",
                        )
                    }
                    val ready = mutableListOf<DestSession>()
                    val failed = mutableListOf<Pair<String, SyncResult>>()
                    var consentResult: SyncResult? = null
                    var consentLabel: String? = null

                    for ((index, point) in points.withIndex()) {
                        val label = destLabel(point.dest)
                        onProgress?.onStatus("Reading $label…")
                        val opened = openForMerge(point.dest, point.hint)
                        if (opened.failure != null) {
                            recordReadFailure(
                                failureStore, point.dest.id, label, opened.failure, onProgress,
                            )
                            failed.add(label to opened.failure)
                            if (opened.failure.needsRemoteConsent && consentResult == null) {
                                consentResult = opened.failure
                                consentLabel = label
                            }
                        } else {
                            try {
                                val read = mergeInDestination(
                                    point.dest, opened.backend!!, point.hint,
                                )
                                ready.add(
                                    DestSession(
                                        dest = point.dest,
                                        hint = point.hint,
                                        label = label,
                                        backend = opened.backend,
                                        read = read,
                                    ),
                                )
                                onProgress?.onStatus(
                                    "$label read (${read.vehiclesMerged} vehicles, " +
                                        "${read.expensesMerged} expenses, ${read.fuel.upserted} fuel)",
                                )
                            // kept: failure stays on this call so the screen or caller can continue
                            } catch (e: Exception) {
                                val result = failureResult(point.dest, e) ?: throw e
                                recordReadFailure(
                                    failureStore, point.dest.id, label, result, onProgress,
                                )
                                failed.add(label to result)
                                if (result.needsRemoteConsent && consentResult == null) {
                                    consentResult = result
                                    consentLabel = label
                                }
                            }
                        }
                        paceBeforeNext(index, points.lastIndex, onProgress)
                    }

                    var mergeFailures = 0
                    var mergeUpdated = 0
                    var mergeDeleted = 0
                    var mergeError: String? = null
                    try {
                        val mergeStats = batchFuelImportCoordinator.get().fieldMergeForSync { msg ->
                            Log.i(TAG, "fieldMerge: $msg")
                        }
                        mergeFailures = mergeStats.failed
                        mergeUpdated = mergeStats.updated
                        mergeDeleted = mergeStats.deleted
                        Log.i(
                            TAG,
                            "session field-merge updates=${mergeStats.updated} " +
                                "deletes=${mergeStats.deleted} failed=${mergeStats.failed}",
                        )
                    // kept: failure stays on this call so the screen or caller can continue
                    } catch (e: Exception) {
                        if (e.isNonFailureCancel()) throw e
                        mergeError = e.message ?: "error"
                        Log.w(TAG, "field-merge failed", e)
                    }
                    val fuelRowsChanged = ready.any { it.read.fuel.remoteWins > 0 } ||
                        mergeUpdated > 0 || mergeDeleted > 0
                    val postNote = runPostFuelMergeAndRebuild(fuelRowsChanged)

                    val published = mutableListOf<Pair<String, SyncResult>>()
                    for (session in ready) {
                        onProgress?.onStatus("Publishing ${session.label}…")
                        try {
                            publishDestination(session)
                            val result = readSuccessResult(session.read)
                            recordSpreadsheetResult(failureStore, session.dest.id, result)
                            published.add(session.label to result)
                            onProgress?.onStatus(
                                "${session.label} done (${result.vehiclesMerged} vehicles, " +
                                    "${result.expensesMerged} expenses, ${result.fuelMerged} fuel)",
                            )
                        // kept: failure stays on this call so the screen or caller can continue
                        } catch (e: Exception) {
                            val result = failureResult(session.dest, e) ?: throw e
                            recordSpreadsheetResult(failureStore, session.dest.id, result)
                            failed.add(session.label to result)
                            onProgress?.onStatus(failStatusLine(session.label, result.message))
                            if (result.needsRemoteConsent && consentResult == null) {
                                consentResult = result
                                consentLabel = session.label
                            }
                        }
                    }
                    if (failed.isEmpty() && published.isNotEmpty()) {
                        CorruptIncoming.consumeResolved()
                    }

                    val downloadHint = accountHint?.takeIf { it.isNotBlank() }
                        ?: points.firstOrNull()?.hint
                    runPostSyncVehicleDownloads(downloadHint)

                    val anyFailure = failed.isNotEmpty()
                    val totalVehicles = published.sumOf { it.second.vehiclesMerged }
                    val totalExpenses = published.sumOf { it.second.expensesMerged }
                    val totalFuel = published.sumOf { it.second.fuelMerged }
                    val totalFuelRemoteWins = published.sumOf { it.second.fuelRemoteWins }
                    val results = published + failed
                    var message = SyncResultMessages.spreadsheetSummary(
                        results = results,
                        anyFailure = anyFailure,
                        totalVehicles = totalVehicles,
                        totalExpenses = totalExpenses,
                        totalFuel = totalFuel,
                    )
                    destinationFailureLine(failed)?.let { line ->
                        message = if (message.isBlank()) line else "$message\n$line"
                    }
                    if (mergeFailures > 0) {
                        message = "$message · $mergeFailures merges failed"
                    }
                    if (mergeError != null) {
                        message = "$message · field-merge failed: $mergeError"
                    }
                    if (!postNote.isNullOrBlank()) {
                        message = "$message · $postNote"
                    }
                    val postFailed = postNote?.startsWith("post-fuel merge failed") == true
                    if (consentResult != null && consentLabel != null) {
                        val consentText = SyncResultMessages.consentWithDest(
                            consentLabel, consentResult.message,
                        )
                        message = if (message.isBlank()) consentText else "$message\n$consentText"
                    }
                    onProgress?.onStatus(message)
                    SyncResult(
                        success = !anyFailure && mergeError == null && !postFailed,
                        message = message,
                        vehiclesMerged = totalVehicles,
                        expensesMerged = totalExpenses,
                        fuelMerged = totalFuel,
                        fuelRemoteWins = totalFuelRemoteWins,
                        needsRemoteConsent = consentResult?.needsRemoteConsent == true,
                        recoveryIntent = consentResult?.recoveryIntent,
                    )
                }
            }
            } // withContext
        } finally {
            SyncRateLimit.installProgress(null)
        }
    }

    private data class SyncPoint(
        val dest: SpreadsheetDestination,
        val hint: String?,
    )

    private sealed class SyncPoints {
        data class Ready(val points: List<SyncPoint>) : SyncPoints()
        data class Stop(val result: SyncResult) : SyncPoints()
    }

    /**
     * [destId] is that one point. Otherwise the configured/enabled list, or the
     * legacy single spreadsheet when the list is empty.
     */
    private fun resolveSyncPoints(
        store: SyncDestinationStore,
        scope: SyncDestinationScope,
        destId: String?,
        accountHint: String?,
    ): SyncPoints {
        if (destId != null) {
            val dest = store.allSpreadsheet().find { it.id == destId }
                ?: return SyncPoints.Stop(SyncResult(false, "Destination not found"))
            val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
            return SyncPoints.Ready(listOf(SyncPoint(dest, hint)))
        }
        val destinations = when (scope) {
            SyncDestinationScope.CONFIGURED -> store.configuredSpreadsheet()
            SyncDestinationScope.ENABLED -> store.enabledSpreadsheet()
        }
        val list = if (destinations.isNotEmpty()) {
            destinations
        } else {
            val dest = store.spreadsheetDestination()
            val targetId = resolveTargetId(dest)
            if (dest == null || targetId.isBlank()) {
                val message = when (scope) {
                    SyncDestinationScope.ENABLED -> "No enabled spreadsheet destinations"
                    SyncDestinationScope.CONFIGURED -> "Spreadsheet not configured"
                }
                return SyncPoints.Stop(SyncResult(false, message))
            }
            listOf(dest)
        }
        return SyncPoints.Ready(
            list.map { dest ->
                SyncPoint(
                    dest = dest,
                    hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint,
                )
            },
        )
    }

    private fun recordReadFailure(
        failureStore: SyncFailureStore,
        destId: String,
        label: String,
        result: SyncResult,
        onProgress: SyncProgressListener?,
    ) {
        recordSpreadsheetResult(failureStore, destId, result)
        onProgress?.onStatus(failStatusLine(label, result.message))
    }

    private fun destinationFailureLine(failed: List<Pair<String, SyncResult>>): String? {
        if (failed.isEmpty()) return null
        val detail = failed.joinToString("; ") { (label, result) ->
            val reason = result.message.replace("\n", " ").trim()
            "$label — $reason"
        }
        return "${failed.size} destinations failed: $detail"
    }

    private fun readSuccessResult(read: MergedDest): SyncResult =
        SyncResult(
            success = true,
            message = buildString {
                append("Sync complete: ${read.vehiclesMerged} vehicles, ${read.expensesMerged} expenses, ")
                append("${read.fuel.upserted} fuel")
                if (read.fuel.remoteWins > 0) append(" (${read.fuel.remoteWins} remote/new)")
                if (read.mergeAcksMerged > 0) append(", ${read.mergeAcksMerged} merge-acks")
                if (read.stationsMerged > 0) append(", ${read.stationsMerged} stations")
            },
            vehiclesMerged = read.vehiclesMerged,
            expensesMerged = read.expensesMerged,
            fuelMerged = read.fuel.upserted,
            fuelRemoteWins = read.fuel.remoteWins,
        )

    private suspend fun paceBeforeNext(
        index: Int,
        lastIndex: Int,
        onProgress: SyncProgressListener?,
    ) {
        if (index >= lastIndex) return
        val pace = SyncRateLimit.interDestPaceMs
        val cooldown = SyncRateLimit.postDestReadCooldownMs
        val total = pace + cooldown
        val sec = (total / 1000L).toInt()
        onProgress?.onStatus("Pacing before next destination (${sec}s)…")
        delay(total)
    }

    private fun failStatusLine(label: String, detail: String): String {
        val short = SyncRateLimit.shortTitle(detail, forSheets = true)
        return if (short != null) "$label failed — $short" else "$label failed"
    }

    /**
     * After fuel tabular LWW: same pipeline as Import **Run merge** —
     * field-merge all live partials + detect-only odo + rebuild regenerable questions
     * (breaker-aware MPG_OUTLIER). Pending JSON is local-only; correctness = rebuild.
     */
    /**
     * @param fuelRowsChanged true when LWW wrote remote winners/inserts or field-merge
     * will see new data — resets Stage C phase to 1 (skipped items not sticky).
     */
    private suspend fun runPostFuelMergeAndRebuild(fuelRowsChanged: Boolean): String? {
        return try {
            onProgressSafe("Post-sync: merge + re-check questions…")
            val result = batchFuelImportCoordinator.get().postSyncRescanResetPhase(
                fuelRowsChanged = fuelRowsChanged,
            )
            val phaseReset = if (fuelRowsChanged) {
                " · review questions restarted (phase 1)"
            } else {
                ""
            }
            val note =
                if (result.updated > 0 || result.deleted > 0 || result.pendingAdded > 0 || fuelRowsChanged) {
                    "Sync: merged ${result.updated} partials, ${result.deleted} absorbs · " +
                        "${result.totalPending} questions$phaseReset"
                } else if (result.totalPending > 0) {
                    "Sync: ${result.totalPending} questions (no new merges)"
                } else {
                    null
                }
            if (note != null) Log.i(TAG, "post-fuel merge: $note (${result.message})")
            note
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            if (e.isNonFailureCancel()) throw e
            val text = "post-fuel merge failed: ${e.message ?: "error"}"
            Log.w(TAG, text, e)
            text
        }
    }

    private fun onProgressSafe(msg: String) {
        Log.i(TAG, msg)
    }

    /**
     * LWW "Merge acks" tab by ackId (Sync ID column). Soft-deleted acks included.
     * Uses remotetable [MergeSync] lww_row via [PolicySyncBridge] (no pilot pref).
     */
    private suspend fun syncMergeAcksTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
    ): TabMerge {
        val resolved = readRemoteTabForMerge(
            dest, backend, TabularSchema.TAB_MERGE_ACKS, TabularSchema.MERGE_ACK_HEADERS, accountHint, bulk,
        )
        val remoteRows = resolved.rows
        val headerRow = remoteRows.firstOrNull() ?: TabularSchema.MERGE_ACK_HEADERS
        val headerIndex = TabularSchema.headerIndex(headerRow)
        val remoteDataRows = remoteRows.drop(1)
            .filter { it.any { cell -> cell.isNotBlank() } }

        val localAcks = mergeAckStore.getAllIncludingDeleted()
        val merged = PolicySyncBridge.mergeAcksViaLwwRow(localAcks, remoteRows)
        for (ack in merged) {
            if (ack.ackId.isBlank()) continue
            mergeAckStore.upsertFromSync(ack)
        }
        return TabMerge(
            upserted = merged.size,
            tab = TabPublish(
                tabName = TabularSchema.TAB_MERGE_ACKS,
                writeHeaders = TabularSchema.MERGE_ACK_HEADERS,
                headerIndex = headerIndex,
                remoteDataRows = remoteDataRows,
                forceFullRewrite = resolved.forceFullRewrite,
                tabMissing = resolved.tabMissing,
                logTag = "MergeAcks",
            ),
        )
    }

    /** Publish Room acks. Does not apply sheet rows. */
    private suspend fun publishAcksTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        tab: TabPublish,
    ) {
        val sorted = mergeAckStore.getAllIncludingDeleted()
            .filter { it.ackId.isNotBlank() }
            .sortedWith(compareBy({ it.kind }, { it.ackId }))
        publishTabFromRoom(
            dest, backend, accountHint, tab,
            sortedRows = sorted.map { TabularSchema.ackToRow(it) },
            sortedSyncIds = sorted.map { it.ackId },
        )
    }

    /**
     * LWW "Stations" tab by syncId. Includes tombstones so deletes propagate.
     */
    private suspend fun syncStationsTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
    ): TabMerge {
        val resolved = readRemoteTabForMerge(
            dest, backend, TabularSchema.TAB_STATIONS, TabularSchema.STATION_HEADERS, accountHint, bulk,
        )
        val remoteRows = resolved.rows
        val headerRow = remoteRows.firstOrNull() ?: TabularSchema.STATION_HEADERS
        val headerIndex = TabularSchema.headerIndex(headerRow)
        val remoteDataRows = remoteRows.drop(1)
            .filter { it.any { cell -> cell.isNotBlank() } }
        val remoteStations = remoteDataRows.mapNotNull { TabularSchema.rowToStation(it, headerIndex) }

        val localStations = knownStationStore.getAllIncludingDeleted()
        val localById = localStations.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val remoteById = remoteStations.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val allIds = (localById.keys + remoteById.keys).toSet()

        var count = 0
        for (syncId in allIds) {
            val local = localById[syncId]
            val remote = remoteById[syncId]
            val winner = mergeLww(local, remote)
            if (winner != null && winner.syncId.isNotBlank()) {
                knownStationStore.upsertFromSync(winner)
                count++
            }
        }

        return TabMerge(
            upserted = count,
            tab = TabPublish(
                tabName = TabularSchema.TAB_STATIONS,
                writeHeaders = TabularSchema.STATION_HEADERS,
                headerIndex = headerIndex,
                remoteDataRows = remoteDataRows,
                forceFullRewrite = resolved.forceFullRewrite,
                tabMissing = resolved.tabMissing,
                logTag = "Stations",
            ),
        )
    }

    /** Publish Room stations. Does not apply sheet rows. */
    private suspend fun publishStationsTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        tab: TabPublish,
    ) {
        val sorted = knownStationStore.getAllIncludingDeleted()
            .filter { it.syncId.isNotBlank() }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.syncId }))
        publishTabFromRoom(
            dest, backend, accountHint, tab,
            sortedRows = sorted.map { TabularSchema.stationToRow(it) },
            sortedSyncIds = sorted.map { it.syncId },
        )
    }

    private fun recordSpreadsheetResult(
        failureStore: SyncFailureStore,
        destId: String,
        result: SyncResult,
    ) {
        if (result.success) {
            failureStore.clearSpreadsheetFailure(destId)
        } else if (result.message.contains("rememberCoroutineScope", ignoreCase = true) ||
            result.message.contains("left the composition", ignoreCase = true)
        ) {
            Log.w(TAG, "Skip recording spreadsheet failure (UI cancel): ${result.message}")
        } else {
            // Store full API/user message (capped), not display name alone.
            failureStore.recordSpreadsheetFailure(destId, result.message)
        }
    }

    private data class OpenedMerge(
        val backend: TabularShareBackend?,
        val failure: SyncResult?,
    )

    private data class DestSession(
        val dest: SpreadsheetDestination,
        val hint: String?,
        val label: String,
        val backend: TabularShareBackend,
        val read: MergedDest,
    )

    private data class MergedDest(
        val vehiclesMerged: Int,
        val expensesMerged: Int,
        val fuel: FuelTabSyncStats,
        val mergeAcksMerged: Int,
        val stationsMerged: Int,
        val vehicles: TabPublish,
        val expenses: TabPublish,
        val fuelTabs: List<FuelTabSnapshot>,
        val acks: TabPublish,
        val stations: TabPublish,
    )

    /** Column order and prior grid for a later publish. Merge-in does not write the sheet. */
    private data class TabPublish(
        val tabName: String,
        val writeHeaders: List<String>,
        val headerIndex: Map<String, Int>,
        val remoteDataRows: List<List<String>>,
        val forceFullRewrite: Boolean,
        val tabMissing: Boolean,
        val logTag: String,
        val remoteRowsDeferred: Boolean = false,
    )

    private data class TabMerge(
        val upserted: Int,
        val tab: TabPublish,
    )

    private data class FuelMerge(
        val stats: FuelTabSyncStats,
        val snapshots: List<FuelTabSnapshot>,
    )

    private data class FuelTabSnapshot(
        val vehicleSyncId: String,
        val vehicleId: Int,
        val tabName: String,
        val writeHeaders: List<String>,
        val headerIndex: Map<String, Int>,
        val remoteDataRows: List<List<String>>,
        val forceFullRewrite: Boolean,
        val tabMissing: Boolean,
        val renameFrom: String?,
        val deleteOrphan: String?,
        val remoteRowsDeferred: Boolean = false,
    )

    /**
     * Auth and target checks only. Sheet reads happen in [mergeInDestination].
     */
    private fun openForMerge(dest: SpreadsheetDestination, accountHint: String?): OpenedMerge {
        val backend = tabularApi.backendFor(dest)
        val targetId = backend.resolveTargetId(dest)
        if (targetId.isBlank()) {
            return OpenedMerge(null, SyncResult(false, "Spreadsheet not configured"))
        }
        val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
        if (!isAccountReady(dest, backend, hint)) {
            return OpenedMerge(null, SyncResult(false, authRequiredMessage(dest.provider)))
        }
        return OpenedMerge(backend, null)
    }

    /** @return null when [e] is a non-failure cancel so the caller rethrows. */
    private fun failureResult(dest: SpreadsheetDestination, e: Exception): SyncResult? {
        if (e is SpreadsheetMissingColumnsException) {
            Log.e(TAG, "Sync aborted for dest=${dest.id}: ${e.message}")
            return SyncResult(
                false,
                e.message ?: SpreadsheetMissingColumnsException.formatUserMessage(
                    e.tabName, e.missingColumns,
                ),
            )
        }
        if (e.isNonFailureCancel()) return null
        Log.e(TAG, "Sync failed for dest=${dest.id}", e)
        val wrapped = SheetsAuthRecovery.wrapIfRecoverable(e)
        if (wrapped is SheetsRecoverableAuthException) {
            return SyncResult(
                success = false,
                message = wrapped.message ?: SheetsAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE,
                needsRemoteConsent = true,
                recoveryIntent = wrapped.recoveryIntent,
            )
        }
        val detail = SheetsAuthRecovery.userMessage(wrapped)
        val full = buildString {
            append(detail)
            val raw = wrapped.message?.trim().orEmpty()
            if (raw.isNotBlank() && !detail.contains(raw) && raw.length > detail.length) {
                append("\n\n")
                append(raw)
            }
        }
        val message = if (SyncRateLimit.isRateLimitError(wrapped)) {
            SyncRateLimit.appendCrossDeviceHint(full)
        } else {
            full
        }
        return SyncResult(false, message)
    }

    /**
     * Read this point into Room. Does not write the sheet.
     */
    private suspend fun mergeInDestination(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
    ): MergedDest {
        val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
        vehicleRepository.ensureUnassignedVehicle()
        val sheetTitles = backend.listTabTitles(dest, hint)
        val bulk = prefetchCompareTabs(dest, backend, hint, sheetTitles)
        val vehicles = syncVehiclesTab(dest, backend, hint, bulk)
        val expenses = syncExpensesTab(dest, backend, hint, bulk)
        val fuel = syncFuelTabs(dest, backend, hint, bulk, sheetTitles)
        val acks = syncMergeAcksTab(dest, backend, hint, bulk)
        Log.i(TAG, "Merge acks tab upserted=${acks.upserted}")
        val stations = syncStationsTab(dest, backend, hint, bulk)
        Log.i(TAG, "Stations tab upserted=${stations.upserted}")
        return MergedDest(
            vehiclesMerged = vehicles.upserted,
            expensesMerged = expenses.upserted,
            fuel = fuel.stats,
            mergeAcksMerged = acks.upserted,
            stationsMerged = stations.upserted,
            vehicles = vehicles.tab.deferRows(),
            expenses = expenses.tab.deferRows(),
            fuelTabs = fuel.snapshots.map { it.deferRows() },
            acks = acks.tab.deferRows(),
            stations = stations.tab.deferRows(),
        )
    }

    private fun TabPublish.deferRows() = copy(remoteDataRows = emptyList(), remoteRowsDeferred = true)

    private fun FuelTabSnapshot.deferRows() = copy(remoteDataRows = emptyList(), remoteRowsDeferred = true)

    /** One tab at publish time so earlier destinations' grids are not kept. */
    private suspend fun remoteRowsForWrite(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        tabName: String,
        deferred: Boolean,
        cached: List<List<String>>,
    ): List<List<String>> {
        if (!deferred) return cached
        val bulk = backend.batchReadTabs(dest, listOf(tabName), accountHint)
        val grid = bulk[tabName] ?: return emptyList()
        return grid.drop(1).filter { row -> row.any { it.isNotBlank() } }
    }

    /**
     * Write Room out to this point. Does not apply sheet body rows back into Room.
     * May read tab titles and a header row so a tab that arrived from another point
     * has a column order.
     */
    private suspend fun publishDestination(session: DestSession) {
        val hint = session.hint?.takeIf { it.isNotBlank() } ?: session.dest.accountHint
        val backend = session.backend
        val dest = session.dest
        publishVehiclesTab(dest, backend, hint, session.read.vehicles)
        publishExpensesTab(dest, backend, hint, session.read.expenses)
        publishFuelTabs(dest, backend, hint, session.read.fuelTabs)
        publishAcksTab(dest, backend, hint, session.read.acks)
        publishStationsTab(dest, backend, hint, session.read.stations)
    }

    private fun isAccountReady(dest: SpreadsheetDestination, backend: TabularShareBackend, hint: String?): Boolean =
        when (dest.provider) {
            SpreadsheetProvider.ETHERCALC -> true
            SpreadsheetProvider.EXCEL_GRAPH -> backend.resolveAccountName(hint) != null
            SpreadsheetProvider.GOOGLE_SHEETS -> backend.resolveAccountName(hint) != null
            SpreadsheetProvider.BASEROW,
            SpreadsheetProvider.NOCODB,
            SpreadsheetProvider.POCKETBASE,
            SpreadsheetProvider.SUPABASE,
            SpreadsheetProvider.AIRTABLE,
            SpreadsheetProvider.FIREBASE,
            SpreadsheetProvider.ZOHO_SHEET,
            SpreadsheetProvider.OTHER,
            -> backend.isConfigured(dest)
            SpreadsheetProvider.ONLYOFFICE,
            SpreadsheetProvider.COLLABORA,
            -> false
        }

    private fun authRequiredMessage(provider: SpreadsheetProvider): String = when (provider) {
        SpreadsheetProvider.EXCEL_GRAPH -> "Sign in with Microsoft first"
        SpreadsheetProvider.GOOGLE_SHEETS -> "Sign in with Google first"
        else -> "Destination not configured"
    }

    private suspend fun runPostSyncVehicleDownloads(accountHint: String?) {
        try {
            val downloads = photoBackupCoordinator.get().downloadMissingVehicleAssets(accountHint)
            Log.i(TAG, "Post-sync vehicle image downloads attempted: $downloads")
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            Log.w(TAG, "Post-sync vehicle image download batch failed (sheet sync still OK)", e)
        }
    }

    private fun destLabel(dest: SpreadsheetDestination): String =
        dest.displayName.ifBlank {
            dest.targetId.take(12).ifBlank { dest.provider.displayLabel() }
        }

    private fun resolveTargetId(dest: SpreadsheetDestination?): String {
        if (dest == null) return ""
        return tabularApi.backendFor(dest).resolveTargetId(dest)
    }

    /**
     * Prefetch existing tabs for LWW compare via [TabularShareBackend.batchReadTabs]
     * (Google: values.batchGet). Tabs not yet on the sheet are omitted (created later).
     */
    private suspend fun prefetchCompareTabs(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        sheetTitles: List<String>,
    ): Map<String, List<List<String>>> {
        val titleSet = sheetTitles.toSet()
        val names = buildList {
            if (TabularSchema.TAB_VEHICLES in titleSet) add(TabularSchema.TAB_VEHICLES)
            if (TabularSchema.TAB_EXPENSES in titleSet) add(TabularSchema.TAB_EXPENSES)
            if (TabularSchema.TAB_MERGE_ACKS in titleSet) add(TabularSchema.TAB_MERGE_ACKS)
            if (TabularSchema.TAB_STATIONS in titleSet) add(TabularSchema.TAB_STATIONS)
            sheetTitles.filterTo(this) { it.startsWith(TabularSchema.FUEL_TAB_PREFIX) }
        }.distinct()
        if (names.isEmpty()) return emptyMap()
        Log.i(TAG, "Bulk compare prefetch: ${names.size} existing tabs")
        return backend.batchReadTabs(dest, names, accountHint)
    }

    /**
     * Resolved sheet grid for LWW + write-back.
     * [forceFullRewrite] when remote data empty (new/blank tab → writeAllRows with headers).
     * Corrupt headers throw [SpreadsheetMissingColumnsException] — never silent rewrite.
     */
    private data class ResolvedRemoteTab(
        /** Header row + data rows. */
        val rows: List<List<String>>,
        val forceFullRewrite: Boolean,
        /** Tab was not on the sheet. Publish creates it; merge-in does not. */
        val tabMissing: Boolean,
    )

    /**
     * Thrown when a tab has non-blank content but row 1 lacks required columns (e.g. Sync ID).
     * Aborts the whole destination sync so we never LWW/write into a wrong spreadsheet.
     */
    class SpreadsheetMissingColumnsException(
        val tabName: String,
        val missingColumns: List<String>,
    ) : Exception(formatUserMessage(tabName, missingColumns)) {
        companion object {
            fun formatUserMessage(tabName: String, missing: List<String>): String {
                val cols = missing.joinToString(", ")
                return "$tabName: sheet is missing required column(s): $cols. " +
                    "Fix row 1 headers (or clear the tab) and sync again."
            }
        }
    }

    /**
     * Read a tab for LWW. Does not write the sheet.
     * - **Missing / blank:** empty remote; publish creates or replaces the tab.
     * - **Valid headers:** cached grid. Incomplete optional columns force a full rewrite later.
     * - **Corrupt headers** (cells present, required names missing): **fail** — no LWW, no write.
     */
    private fun readRemoteTabForMerge(
        @Suppress("UNUSED_PARAMETER") dest: SpreadsheetDestination,
        @Suppress("UNUSED_PARAMETER") backend: TabularShareBackend,
        tabName: String,
        expectedHeaders: List<String>,
        @Suppress("UNUSED_PARAMETER") accountHint: String?,
        bulk: Map<String, List<List<String>>>?,
    ): ResolvedRemoteTab {
        fun firstNames(grid: List<List<String>>): List<String> =
            grid.firstOrNull()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

        fun failCorrupt(grid: List<List<String>>): Nothing {
            val first = firstNames(grid)
            val missing = TabularSchema.missingRequiredHeaders(first)
            Log.e(
                TAG,
                "Corrupt/missing headers on $tabName — abort dest sync. missing=$missing firstRow=$first",
            )
            throw SpreadsheetMissingColumnsException(tabName, missing.ifEmpty { listOf("Sync ID") })
        }

        val cached = bulk?.get(tabName)
        if (cached == null) {
            Log.i(TAG, "No bulk cache for $tabName — empty remote (no sheet write)")
            return ResolvedRemoteTab(
                rows = listOf(expectedHeaders),
                forceFullRewrite = true,
                tabMissing = true,
            )
        }
        if (TabularSchema.isCompletelyBlankGrid(cached)) {
            Log.i(TAG, "Blank tab $tabName — empty remote (no sheet write)")
            return ResolvedRemoteTab(
                rows = listOf(expectedHeaders),
                forceFullRewrite = true,
                tabMissing = false,
            )
        }
        val first = firstNames(cached)
        val missing = TabularSchema.missingRequiredHeaders(first)
        if (missing.isNotEmpty()) {
            failCorrupt(cached)
        }
        val dataEmpty = cached.drop(1).none { row -> row.any { it.isNotBlank() } }
        val complete = expectedHeaders.all { it in first }
        return ResolvedRemoteTab(
            rows = cached,
            forceFullRewrite = dataEmpty || !complete,
            tabMissing = false,
        )
    }

    private suspend fun publishTabFromRoom(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        tab: TabPublish,
        sortedRows: List<List<String>>,
        sortedSyncIds: List<String>,
    ) {
        if (tab.tabMissing) {
            backend.ensureHeaders(dest, tab.tabName, tab.writeHeaders, accountHint)
        }
        val remoteDataRows = remoteRowsForWrite(
            dest, backend, accountHint, tab.tabName, tab.remoteRowsDeferred, tab.remoteDataRows,
        )
        writeRowsIncremental(
            dest = dest,
            backend = backend,
            tabName = tab.tabName,
            headers = tab.writeHeaders,
            sortedRows = sortedRows,
            sortedSyncIds = sortedSyncIds,
            remoteDataRows = remoteDataRows,
            headerIndex = tab.headerIndex,
            accountHint = accountHint,
            logTag = tab.logTag,
            forceFullRewrite = tab.forceFullRewrite,
        )
    }

    /**
     * LWW "Vehicles" tab by Sync ID. Soft-deleted included.
     * remotetable MergeSync lww_row then [VehicleDefinitionOverlay] (crops/landmarks/photos).
     */
    private suspend fun syncVehiclesTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
    ): TabMerge {
        val resolved = readRemoteTabForMerge(
            dest, backend, TabularSchema.TAB_VEHICLES, TabularSchema.VEHICLE_HEADERS, accountHint, bulk,
        )
        val remoteRows = resolved.rows
        val headerRow = remoteRows.firstOrNull() ?: TabularSchema.VEHICLE_HEADERS
        val retiredOtherCrop = setOf(
            "Other Crop L", "Other Crop T", "Other Crop R", "Other Crop B",
        )
        // Read index keeps remote positions so name-based decode is not shifted.
        // Write drops retired Other Crop columns; full rewrite when they were present.
        val mergedHeaders = TabularSchema.mergeHeaderOrder(headerRow, TabularSchema.VEHICLE_HEADERS)
        val writeHeaders = mergedHeaders.filter { it !in retiredOtherCrop }
        val headerIndex = TabularSchema.headerIndex(mergedHeaders)
        val remoteDataRows = remoteRows.drop(1)
            .filter { it.any { cell -> cell.isNotBlank() } }

        val localVehicles = vehicleRepository.getAllIncludingDeleted()
        val localBySyncId = localVehicles.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val remoteVehicles = remoteDataRows.map { TabularSchema.rowToVehicle(it, headerIndex) }
        val remoteBySyncId = remoteVehicles.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }

        val winners = PolicySyncBridge.mergeVehiclesViaLwwRow(localVehicles, remoteRows)
        val merged = VehicleDefinitionOverlay.applyToMergedList(
            winners = winners,
            localBySyncId = localBySyncId,
            remoteBySyncId = remoteBySyncId,
            pickPhoto = { a, b -> photoStorage.pickPreferredLocalPath(a, b) },
            log = { msg -> Log.i(TAG, msg) },
        )

        for (v in merged) {
            if (v.syncId.isBlank()) continue
            vehicleRepository.upsertFromSync(v)
        }

        val withLandmarks = merged.count { !it.landmarkTextBlocksJson.isNullOrBlank() }
        Log.i(TAG, "Vehicles tab merged: upserted=${merged.size} withLandmarks=$withLandmarks")

        return TabMerge(
            upserted = merged.size,
            tab = TabPublish(
                tabName = TabularSchema.TAB_VEHICLES,
                writeHeaders = writeHeaders,
                headerIndex = headerIndex,
                remoteDataRows = remoteDataRows,
                forceFullRewrite = resolved.forceFullRewrite ||
                    headerRow.any { it.trim() in retiredOtherCrop },
                tabMissing = resolved.tabMissing,
                logTag = "Vehicles",
            ),
        )
    }

    /** Publish Room vehicles. Does not apply sheet rows. */
    private suspend fun publishVehiclesTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        tab: TabPublish,
    ) {
        val sorted = vehicleRepository.getAllIncludingDeleted()
            .filter { it.syncId.isNotBlank() }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.syncId }))
        publishTabFromRoom(
            dest, backend, accountHint, tab,
            sortedRows = sorted.map { TabularSchema.vehicleToRow(it, tab.writeHeaders, forPublish = true) },
            sortedSyncIds = sorted.map { it.syncId },
        )
    }

    /**
     * LWW "Expenses" tab by Sync ID. Soft-deleted included.
     * remotetable MergeSync lww_row via [PolicySyncBridge], then [LocationBlobOverlay].
     */
    private suspend fun syncExpensesTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
    ): TabMerge {
        val resolved = readRemoteTabForMerge(
            dest, backend, TabularSchema.TAB_EXPENSES, TabularSchema.EXPENSE_HEADERS, accountHint, bulk,
        )
        val remoteRows = resolved.rows
        val headerRow = remoteRows.firstOrNull() ?: TabularSchema.EXPENSE_HEADERS
        val writeHeaders = TabularSchema.mergeHeaderOrder(headerRow, TabularSchema.EXPENSE_HEADERS)
        val headerIndex = TabularSchema.headerIndex(writeHeaders)
        val allVehicles = vehicleRepository.getAllIncludingDeleted().filter { it.syncId.isNotBlank() }
        val vehicleIdBySyncId = allVehicles.associate { it.syncId to it.id }
        val vehicleSyncIdById = allVehicles.associate { it.id to it.syncId }

        val remoteDataRows = remoteRows.drop(1)
            .filter { it.any { cell -> cell.isNotBlank() } }

        val localExpenses = expenseRepository.getAllIncludingDeleted()
        val localBySyncId = localExpenses.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val winners = PolicySyncBridge.mergeExpensesViaLwwRow(
            localExpenses = localExpenses,
            remoteGrid = remoteRows,
            vehicleSyncIdById = vehicleSyncIdById,
            vehicleIdBySyncId = vehicleIdBySyncId,
        )
        val remoteExpensesBySyncId = remoteDataRows.map { row ->
            val parsed = TabularSchema.rowToExpense(row, headerIndex)
            val vehicleSyncIds = TabularSchema.rowToExpenseVehicleSyncIds(row, headerIndex)
            ExpenseVehicleSyncIds.applyResolvedVehicles(parsed, vehicleSyncIds, vehicleIdBySyncId)
        }.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }

        val merged = LocationBlobOverlay.applyToExpenseList(
            winners = winners,
            localBySyncId = localBySyncId,
            remoteBySyncId = remoteExpensesBySyncId,
        )

        for (entry in merged) {
            if (entry.syncId.isBlank()) continue
            expenseRepository.upsertFromSync(entry)
        }

        return TabMerge(
            upserted = merged.size,
            tab = TabPublish(
                tabName = TabularSchema.TAB_EXPENSES,
                writeHeaders = writeHeaders,
                headerIndex = headerIndex,
                remoteDataRows = remoteDataRows,
                forceFullRewrite = resolved.forceFullRewrite,
                tabMissing = resolved.tabMissing,
                logTag = "Expenses",
            ),
        )
    }

    /** Publish Room expenses. Does not apply sheet rows. */
    private suspend fun publishExpensesTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        tab: TabPublish,
    ) {
        val allVehicles = vehicleRepository.getAllIncludingDeleted().filter { it.syncId.isNotBlank() }
        val vehicleSyncIdById = allVehicles.associate { it.id to it.syncId }
        val sorted = expenseRepository.getAllIncludingDeleted()
            .filter { it.syncId.isNotBlank() }
            .sortedWith(compareBy({ it.date }, { it.syncId }))
        publishTabFromRoom(
            dest, backend, accountHint, tab,
            sortedRows = sorted.map { entry ->
                TabularSchema.expenseToRow(
                    entry,
                    vehicleSyncIdById[entry.vehicleId].orEmpty(),
                    forPublish = true,
                )
            },
            sortedSyncIds = sorted.map { it.syncId },
        )
    }

    private suspend fun findOrphanFuelTabs(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        vehicleSyncId: String,
        expectedTabName: String,
        sheetTitles: List<String>,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
    ): List<String> {
        val candidates = sheetTitles.filter { title ->
            title.startsWith(TabularSchema.FUEL_TAB_PREFIX) && title != expectedTabName
        }
        return candidates.filter { tabName ->
            fuelTabBelongsToVehicle(dest, backend, tabName, vehicleSyncId, accountHint, bulk)
        }
    }

    private suspend fun fuelTabBelongsToVehicle(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        tabName: String,
        vehicleSyncId: String,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
    ): Boolean {
        val remoteRows = bulk[tabName] ?: backend.readAllRows(dest, tabName, accountHint)
        if (remoteRows.size <= 1) return false
        val headerIndex = TabularSchema.headerIndex(remoteRows.first())
        val dataRows = remoteRows.drop(1).filter { row -> row.any { cell -> cell.isNotBlank() } }
        if (dataRows.isEmpty()) return false
        val matching = dataRows.count { row ->
            TabularSchema.rowToFuelVehicleSyncId(row, headerIndex) == vehicleSyncId
        }
        return matching > 0 && matching * 2 >= dataRows.size
    }

    private suspend fun resolveOrphanFuelTabs(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        vehicle: Vehicle,
        expectedTabName: String,
        sheetTitles: List<String>,
        accountHint: String?,
        hintStore: FuelTabRenameHintStore,
        bulk: Map<String, List<List<String>>> = emptyMap(),
    ): List<String> {
        val scanned = findOrphanFuelTabs(
            dest, backend, vehicle.syncId, expectedTabName, sheetTitles, accountHint, bulk,
        )
        if (scanned.isNotEmpty()) return scanned
        val hint = hintStore.peekHint(vehicle.syncId)
        if (hint != null && hint != expectedTabName && hint in sheetTitles) {
            Log.i(TAG, "Using fuel tab rename hint for syncId=${vehicle.syncId}: $hint")
            return listOf(hint)
        }
        return emptyList()
    }

    /** Old tab to rename at publish, or null. Does not write the sheet. */
    private suspend fun migrateFuelTabRenameIfNeeded(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        vehicle: Vehicle,
        expectedTabName: String,
        sheetTitles: List<String>,
        accountHint: String?,
        hintStore: FuelTabRenameHintStore,
        bulk: MutableMap<String, List<List<String>>> = mutableMapOf(),
    ): String? {
        val orphanTabs = resolveOrphanFuelTabs(
            dest, backend, vehicle, expectedTabName, sheetTitles, accountHint, hintStore, bulk,
        )
        if (orphanTabs.isEmpty()) return null
        if (orphanTabs.size > 1) {
            Log.w(
                TAG,
                "Fuel tab rename skipped: multiple orphan tabs for syncId=${vehicle.syncId}: $orphanTabs",
            )
            return null
        }
        val oldTab = orphanTabs.single()
        if (expectedTabName in sheetTitles || oldTab !in sheetTitles) return null
        Log.i(TAG, "Fuel tab rename deferred: \"$oldTab\" → \"$expectedTabName\"")
        return oldTab
    }

    private suspend fun parseFuelEntriesFromTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        tabName: String,
        vehicle: Vehicle,
        vehicleIdBySyncId: Map<String, Int>,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
    ): List<FuelEntry> {
        val remoteRows = bulk[tabName] ?: backend.readAllRows(dest, tabName, accountHint)
        return parseFuelEntriesFromRows(remoteRows, vehicle, vehicleIdBySyncId)
    }

    private fun parseFuelEntriesFromRows(
        remoteRows: List<List<String>>,
        vehicle: Vehicle,
        vehicleIdBySyncId: Map<String, Int>,
    ): List<FuelEntry> {
        val headerRow = remoteRows.firstOrNull() ?: return emptyList()
        val headerIndex = TabularSchema.headerIndex(headerRow)
        return remoteRows.drop(1)
            .filter { row -> row.any { cell -> cell.isNotBlank() } }
            .map { row ->
                val parsed = TabularSchema.rowToFuel(row, headerIndex)
                val vehicleSyncId = TabularSchema.rowToFuelVehicleSyncId(row, headerIndex)
                val resolvedVehicleId = resolveVehicleIdFromSyncId(
                    vehicleSyncId,
                    parsed.vehicleId,
                    vehicleIdBySyncId,
                ) ?: vehicle.id
                parsed.copy(vehicleId = resolvedVehicleId)
            }
            .filter { it.vehicleId == vehicle.id }
    }

    /**
     * Pull a duplicate fuel tab into Room. Returns the orphan tab to delete at publish.
     * Does not write the sheet.
     */
    private suspend fun migrateFuelTabMergeAndDeleteIfNeeded(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        vehicle: Vehicle,
        expectedTabName: String,
        sheetTitles: List<String>,
        vehicleIdBySyncId: Map<String, Int>,
        accountHint: String?,
        hintStore: FuelTabRenameHintStore,
        bulk: MutableMap<String, List<List<String>>> = mutableMapOf(),
    ): String? {
        val orphanTabs = resolveOrphanFuelTabs(
            dest, backend, vehicle, expectedTabName, sheetTitles, accountHint, hintStore, bulk,
        )
        if (orphanTabs.size != 1) return null
        val oldTab = orphanTabs.single()
        if (oldTab !in sheetTitles || expectedTabName !in sheetTitles) return null

        val oldFuel = parseFuelEntriesFromTab(
            dest, backend, oldTab, vehicle, vehicleIdBySyncId, accountHint, bulk,
        )
        val newFuel = parseFuelEntriesFromTab(
            dest, backend, expectedTabName, vehicle, vehicleIdBySyncId, accountHint, bulk,
        )
        val localFuel = fuelRepository.getAllIncludingDeleted().filter { it.vehicleId == vehicle.id }
        val localBySyncId = localFuel.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val oldBySyncId = oldFuel.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val newBySyncId = newFuel.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val allSyncIds = (localBySyncId.keys + oldBySyncId.keys + newBySyncId.keys).toSet()

        var mergedCount = 0
        for (syncId in allSyncIds) {
            val winner = mergeLww(
                mergeLww(localBySyncId[syncId], newBySyncId[syncId]),
                oldBySyncId[syncId],
            )?.let { entry ->
                (entry as FuelEntry).copy(vehicleId = vehicle.id)
            }
            if (winner != null && winner.syncId.isNotBlank()) {
                fuelRepository.upsertFromSync(winner)
                mergedCount++
            }
        }

        Log.i(
            TAG,
            "Fuel tab orphan merged into Room: \"$oldTab\" → \"$expectedTabName\" rows=$mergedCount",
        )
        return oldTab
    }

    /**
     * Fuel merge-in: LWW each `Fuel - {name}` tab into Room, then
     * [LocationBlobOverlay]. No field-merge and no sheet write.
     * Field-merge runs once in [syncNow], between merge-in and publish.
     */
    private suspend fun syncFuelTabs(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
        initialTitles: List<String>? = null,
    ): FuelMerge {
        val sheetTitles = initialTitles ?: backend.listTabTitles(dest, accountHint)
        val hintStore = FuelTabRenameHintStore(context)
        val vehicles = vehicleRepository.getAllIncludingDeleted().filter { !it.deleted && it.syncId.isNotBlank() }
        val vehicleIdBySyncId = vehicles.associate { it.syncId to it.id }
        var total = 0
        var remoteWins = 0
        val bulkRows = bulk.toMutableMap()
        val snapshots = mutableListOf<FuelTabSnapshot>()

        for (vehicle in vehicles) {
            val tabName = TabularSchema.fuelTabName(vehicle.name)
            val renameFrom = migrateFuelTabRenameIfNeeded(
                dest, backend, vehicle, tabName, sheetTitles, accountHint, hintStore, bulkRows,
            )
            val deleteOrphan = migrateFuelTabMergeAndDeleteIfNeeded(
                dest,
                backend,
                vehicle,
                tabName,
                sheetTitles,
                vehicleIdBySyncId,
                accountHint,
                hintStore,
                bulkRows,
            )
            val readTab = renameFrom ?: tabName
            val resolved = readRemoteTabForMerge(
                dest, backend, readTab, TabularSchema.FUEL_HEADERS, accountHint, bulkRows,
            )
            val remoteRows = resolved.rows
            val headerRow = remoteRows.firstOrNull()?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?: TabularSchema.FUEL_HEADERS
            val writeHeaders = TabularSchema.mergeHeaderOrder(headerRow, TabularSchema.FUEL_HEADERS)
            val headerIndex = TabularSchema.headerIndex(writeHeaders)
            val remoteDataRows = remoteRows.drop(1)
                .filter { it.any { cell -> cell.isNotBlank() } }

            val localFuel = fuelRepository.getAllIncludingDeleted()
                .filter { it.vehicleId == vehicle.id }
            val localBySyncId = localFuel.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }

            val winners = PolicySyncBridge.mergeFuelViaLwwRow(
                localEntries = localFuel,
                remoteGrid = remoteRows,
                vehicleId = vehicle.id,
                vehicleSyncId = vehicle.syncId,
                sheetName = tabName,
            )
            val remoteFuel = parseFuelEntriesFromRows(remoteRows, vehicle, vehicleIdBySyncId)
            val remoteBySyncId = remoteFuel.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
            val merged = LocationBlobOverlay.applyToFuelList(
                winners = winners,
                localBySyncId = localBySyncId,
                remoteBySyncId = remoteBySyncId,
            )
            Log.i(
                TAG,
                "Fuel LWW ${vehicle.name}: local=${localFuel.size} remote=${remoteFuel.size} " +
                    "merged=${merged.size}",
            )
            for (winner in merged) {
                if (winner.syncId.isBlank()) continue
                val local = localBySyncId[winner.syncId]
                val remote = remoteBySyncId[winner.syncId]
                val remoteWon = remote != null && (
                    local == null || remote.updatedAt > local.updatedAt
                    )
                if (remoteWon) {
                    remoteWins++
                    if (remote.deleted && local != null && !local.deleted) {
                        Log.i(
                            TAG,
                            "LWW: remote tombstone wins syncId=${winner.syncId} over local live " +
                                "id=${local.id} remoteUpdatedAt=${remote.updatedAt} " +
                                "localUpdatedAt=${local.updatedAt}",
                        )
                    }
                }
                fuelRepository.upsertFromSync(winner)
                total++
            }

            snapshots.add(
                FuelTabSnapshot(
                    vehicleSyncId = vehicle.syncId,
                    vehicleId = vehicle.id,
                    tabName = tabName,
                    writeHeaders = writeHeaders,
                    headerIndex = headerIndex,
                    remoteDataRows = remoteDataRows,
                    forceFullRewrite = resolved.forceFullRewrite,
                    tabMissing = resolved.tabMissing && renameFrom == null,
                    renameFrom = renameFrom,
                    deleteOrphan = deleteOrphan,
                ),
            )
        }
        return FuelMerge(
            stats = FuelTabSyncStats(upserted = total, remoteWins = remoteWins),
            snapshots = snapshots,
        )
    }

    /**
     * Write each fuel tab from Room, including rows that arrived from other points.
     * Does not apply sheet body rows back into Room.
     */
    private suspend fun publishFuelTabs(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        snapshots: List<FuelTabSnapshot>,
    ) {
        val hintStore = FuelTabRenameHintStore(context)
        val bySyncId = snapshots.associateBy { it.vehicleSyncId }
        val titles = backend.listTabTitles(dest, accountHint).toSet()
        val vehicles = vehicleRepository.getAllIncludingDeleted()
            .filter { !it.deleted && it.syncId.isNotBlank() }
        for (vehicle in vehicles) {
            val snap = bySyncId[vehicle.syncId]
            val tabName: String
            val writeHeaders: List<String>
            val headerIndex: Map<String, Int>
            val remoteDataRows: List<List<String>>
            val forceFullRewrite: Boolean
            val tabMissing: Boolean
            if (snap != null) {
                tabName = snap.tabName
                var renamedOk = snap.renameFrom == null
                if (snap.renameFrom != null) {
                    renamedOk = backend.renameTab(dest, snap.renameFrom, tabName, accountHint)
                    if (renamedOk) {
                        Log.i(TAG, "Fuel tab migrate: \"${snap.renameFrom}\" → \"$tabName\" (rename)")
                        hintStore.clearHint(vehicle.syncId)
                    }
                }
                if (snap.deleteOrphan != null) {
                    backend.deleteTab(dest, snap.deleteOrphan, accountHint)
                    hintStore.clearHint(vehicle.syncId)
                    Log.i(
                        TAG,
                        "Fuel tab migrate: deleted \"${snap.deleteOrphan}\" after Room merge",
                    )
                }
                writeHeaders = snap.writeHeaders
                headerIndex = snap.headerIndex
                remoteDataRows = remoteRowsForWrite(
                    dest, backend, accountHint, tabName, snap.remoteRowsDeferred, snap.remoteDataRows,
                )
                forceFullRewrite = snap.forceFullRewrite
                tabMissing = snap.tabMissing || !renamedOk
            } else {
                tabName = TabularSchema.fuelTabName(vehicle.name)
                // No merge-in read for this vehicle. An existing tab still has rows
                // that were never applied to Room; leave it for a later sync.
                if (tabName in titles) {
                    Log.i(TAG, "Fuel write ${vehicle.name}: skip unread tab $tabName")
                    continue
                }
                writeHeaders = TabularSchema.FUEL_HEADERS
                headerIndex = TabularSchema.headerIndex(writeHeaders)
                remoteDataRows = emptyList()
                forceFullRewrite = true
                tabMissing = true
            }
            if (tabMissing) {
                backend.ensureHeaders(dest, tabName, writeHeaders, accountHint)
            }
            val fresh = fuelRepository.getAllIncludingDeleted()
                .filter { it.vehicleId == vehicle.id && it.syncId.isNotBlank() }
            val sortedMerged = fresh.sortedWith(
                compareBy({ it.timestamp }, { it.odometer }, { it.syncId }),
            )
            val deletedCount = sortedMerged.count { it.deleted }
            Log.i(
                TAG,
                "Fuel write ${vehicle.name}: rows=${sortedMerged.size} deleted=$deletedCount",
            )
            writeRowsIncremental(
                dest = dest,
                backend = backend,
                tabName = tabName,
                headers = writeHeaders,
                sortedRows = sortedMerged.map {
                    TabularSchema.fuelToRow(
                        it, vehicle.syncId, writeHeaders, forPublish = true, sheetName = tabName,
                    )
                },
                sortedSyncIds = sortedMerged.map { it.syncId },
                remoteDataRows = remoteDataRows,
                headerIndex = headerIndex,
                accountHint = accountHint,
                logTag = "Fuel-${vehicle.name}",
                forceFullRewrite = forceFullRewrite,
            )
        }
    }

    private fun resolveVehicleIdFromSyncId(
        vehicleSyncId: String,
        fallbackVehicleId: Int,
        vehicleIdBySyncId: Map<String, Int>,
    ): Int? {
        if (vehicleSyncId.isNotBlank()) {
            vehicleIdBySyncId[vehicleSyncId]?.let { return it }
        }
        if (fallbackVehicleId != 0) return fallbackVehicleId
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> mergeLww(local: T?, remote: T?): T? {
        return when {
            local == null && remote == null -> null
            local == null -> remote
            remote == null -> local
            else -> {
                val localTs = updatedAtOf(local!!)
                val remoteTs = updatedAtOf(remote!!)
                val winner = if (remoteTs > localTs) remote else local
                // Location JSON: confirmed trumps unconfirmed; later unconfirmed trumps earlier
                // (winner takes whole blob after rules — see FuelLocationJson.mergeBlobs).
                when {
                    winner is FuelEntry && local is FuelEntry && remote is FuelEntry -> {
                        val mergedLoc = FuelLocationJson.mergeBlobs(
                            local.location,
                            remote.location,
                            updatedAtA = local.updatedAt,
                            updatedAtB = remote.updatedAt,
                        )
                        winner.copy(location = mergedLoc) as T
                    }
                    winner is ExpenseEntry && local is ExpenseEntry && remote is ExpenseEntry -> {
                        val mergedLoc = FuelLocationJson.mergeBlobs(
                            local.location,
                            remote.location,
                            updatedAtA = local.updatedAt,
                            updatedAtB = remote.updatedAt,
                        )
                        winner.copy(location = mergedLoc) as T
                    }
                    else -> winner as T
                }
            }
        }
    }

    private fun updatedAtOf(item: Any): Long = when (item) {
        is Vehicle -> item.updatedAt
        is FuelEntry -> item.updatedAt
        is ExpenseEntry -> item.updatedAt
        is MergeAck -> item.updatedAt
        is KnownStation -> item.updatedAt
        else -> 0L
    }

    private data class SheetWriteStats(
        val appended: Int = 0,
        val updated: Int = 0,
        val unchanged: Int = 0,
        val fullRewrite: Boolean = false,
    )

    private suspend fun writeRowsIncremental(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        tabName: String,
        headers: List<String>,
        sortedRows: List<List<String>>,
        sortedSyncIds: List<String>,
        remoteDataRows: List<List<String>>,
        headerIndex: Map<String, Int>,
        accountHint: String?,
        logTag: String,
        forceFullRewrite: Boolean = false,
    ): SheetWriteStats {
        // Empty remote (new/empty tab) or repaired invalid headers → always header+body replace.
        // Never appendDataRows onto a headerless or just-created tab without asserting headers.
        if (forceFullRewrite || remoteDataRows.isEmpty()) {
            Log.i(
                TAG,
                "$logTag sheet write: fullRewrite " +
                    "(force=$forceFullRewrite emptyRemote=${remoteDataRows.isEmpty()})",
            )
            backend.writeAllRows(dest, tabName, headers, sortedRows, accountHint)
            backend.clearTrailing(dest, tabName, sortedRows.size + 2, accountHint)
            return SheetWriteStats(appended = sortedRows.size, fullRewrite = true)
        }

        val sheetSyncIds = remoteDataRows
            .map { TabularSchema.syncIdFromRow(it, headerIndex) }
            .filter { it.isNotBlank() }

        val canPrefixAppend = sheetSyncIds.size <= sortedSyncIds.size &&
            sheetSyncIds == sortedSyncIds.take(sheetSyncIds.size)
        val exactOrder = sheetSyncIds == sortedSyncIds

        if (!canPrefixAppend && !exactOrder) {
            Log.i(TAG, "$logTag sheet write: fullRewrite (disorder or extra rows)")
            backend.writeAllRows(dest, tabName, headers, sortedRows, accountHint)
            backend.clearTrailing(dest, tabName, sortedRows.size + 2, accountHint)
            return SheetWriteStats(appended = sortedRows.size, fullRewrite = true)
        }

        var appended = 0
        var updated = 0
        var unchanged = 0

        if (exactOrder) {
            for (i in sortedSyncIds.indices) {
                val sheetRow = remoteDataRows.getOrNull(i).orEmpty()
                val newRow = sortedRows[i]
                if (TabularSchema.rowsEqual(sheetRow, newRow)) {
                    unchanged++
                } else {
                    backend.updateRows(dest, tabName, i + 2, listOf(newRow), accountHint)
                    updated++
                }
            }
            if (remoteDataRows.size > sortedRows.size) {
                backend.clearTrailing(dest, tabName, sortedRows.size + 2, accountHint)
            }
        } else {
            val prefixLen = sheetSyncIds.size
            for (i in 0 until prefixLen) {
                val sheetRow = remoteDataRows[i]
                val newRow = sortedRows[i]
                if (TabularSchema.rowsEqual(sheetRow, newRow)) {
                    unchanged++
                } else {
                    backend.updateRows(dest, tabName, i + 2, listOf(newRow), accountHint)
                    updated++
                }
            }
            val newRows = sortedRows.drop(prefixLen)
            if (newRows.isNotEmpty()) {
                backend.appendRows(dest, tabName, newRows, accountHint)
                appended = newRows.size
            }
            if (remoteDataRows.size > sortedRows.size) {
                backend.clearTrailing(dest, tabName, sortedRows.size + 2, accountHint)
            }
        }

        Log.i(
            TAG,
            "$logTag sheet write: append=$appended update=$updated unchanged=$unchanged fullRewrite=false",
        )
        return SheetWriteStats(appended = appended, updated = updated, unchanged = unchanged)
    }

    companion object {
        private const val TAG = "SpreadsheetSyncCoordinator"
    }
}