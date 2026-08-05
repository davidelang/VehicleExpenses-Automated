package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.batch.MergeAckStore
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseVehicleSyncIds
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.MergeAck
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.PolicySyncBridge
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareApi
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
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
) {

    /**
     * Process-wide: only one spreadsheet sync pipeline at a time (hub all-dest,
     * per-dest edit Sync now, WorkManager). Callers queue on this mutex.
     */
    private val syncMutex = Mutex()

    /**
     * @param destId when non-null, sync **only** that destination (edit-form Sync now).
     *   Ignores [scope] list filtering other than requiring the dest to exist.
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

            if (destId != null) {
                val dest = store.allSpreadsheet().find { it.id == destId }
                    ?: return@withContext SyncResult(false, "Destination not found")
                val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
                val label = destLabel(dest)
                onProgress?.onStatus("Syncing $label…")
                val single = syncSingleDestination(dest, hint, onProgress)
                recordSpreadsheetResult(failureStore, dest.id, single)
                runPostSyncVehicleDownloads(hint)
                val withPost = if (single.success) {
                    appendPostFuelMerge(
                        single,
                        fuelRowsChanged = single.fuelRemoteWins > 0,
                    )
                } else {
                    single
                }
                val statusLine = if (withPost.success) {
                    "$label done"
                } else {
                    failStatusLine(label, withPost.message)
                }
                onProgress?.onStatus(statusLine)
                onProgress?.onStatus(withPost.message)
                return@withContext withPost
            }

            val destinations = when (scope) {
                SyncDestinationScope.CONFIGURED -> store.configuredSpreadsheet()
                SyncDestinationScope.ENABLED -> store.enabledSpreadsheet()
            }
            if (destinations.isEmpty()) {
                val dest = resolveLegacyOrPrimaryDest(store)
                val targetId = resolveTargetId(dest)
                if (targetId.isBlank()) {
                    val message = when (scope) {
                        SyncDestinationScope.ENABLED -> "No enabled spreadsheet destinations"
                        SyncDestinationScope.CONFIGURED -> "Spreadsheet not configured"
                    }
                    return@withContext SyncResult(false, message)
                }
                val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
                val label = destLabel(dest)
                onProgress?.onStatus("Syncing $label…")
                val single = syncSingleDestination(dest, hint, onProgress)
                recordSpreadsheetResult(failureStore, dest.id, single)
                runPostSyncVehicleDownloads(hint)
                val withPost = if (single.success) {
                    appendPostFuelMerge(
                        single,
                        fuelRowsChanged = single.fuelRemoteWins > 0,
                    )
                } else {
                    single
                }
                onProgress?.onStatus(
                    if (withPost.success) "$label done" else failStatusLine(label, withPost.message),
                )
                onProgress?.onStatus(withPost.message)
                return@withContext withPost
            }

            val results = mutableListOf<Pair<String, SyncResult>>()
            var totalVehicles = 0
            var totalExpenses = 0
            var totalFuel = 0
            var totalFuelRemoteWins = 0
            var anyFailure = false
            var consentResult: SyncResult? = null

            onProgress?.onStatus("Starting spreadsheet sync (${destinations.size} destinations)…")
            for ((index, dest) in destinations.withIndex()) {
                val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
                val label = destLabel(dest)
                onProgress?.onStatus("Syncing $label…")
                val result = syncSingleDestination(dest, hint, onProgress)
                recordSpreadsheetResult(failureStore, dest.id, result)
                results.add(label to result)
                onProgress?.onStatus(
                    if (result.success) {
                        "$label done (${result.vehiclesMerged} vehicles, ${result.expensesMerged} expenses, ${result.fuelMerged} fuel)"
                    } else {
                        failStatusLine(label, result.message)
                    },
                )
                if (result.success) {
                    totalVehicles += result.vehiclesMerged
                    totalExpenses += result.expensesMerged
                    totalFuel += result.fuelMerged
                    totalFuelRemoteWins += result.fuelRemoteWins
                } else {
                    anyFailure = true
                    if (result.needsRemoteConsent && consentResult == null) {
                        consentResult = result
                    }
                }
                // Long multi-dest pace (10–20s) + read cooldown (15–30s) after heavy fuel GETs.
                if (index < destinations.lastIndex) {
                    val pace = SyncRateLimit.interDestPaceMs
                    val cooldown = SyncRateLimit.postDestReadCooldownMs
                    val total = pace + cooldown
                    val sec = (total / 1000L).toInt()
                    onProgress?.onStatus("Pacing before next destination (${sec}s)…")
                    delay(total)
                }
            }

            runPostSyncVehicleDownloads(accountHint)

            var message = SyncResultMessages.spreadsheetSummary(
                results = results,
                anyFailure = anyFailure,
                totalVehicles = totalVehicles,
                totalExpenses = totalExpenses,
                totalFuel = totalFuel,
            )
            if (consentResult != null && anyFailure) {
                val consentLabel = results.first { !it.second.success && it.second.needsRemoteConsent }.first
                val finalMessage = SyncResultMessages.consentWithDest(consentLabel, consentResult.message)
                onProgress?.onStatus(finalMessage)
                return@withContext consentResult.copy(message = finalMessage)
            }
            // Once per sync session after successful multi-dest fuel LWW
            if (!anyFailure) {
                val postNote = runPostFuelMergeAndRebuild(
                    fuelRowsChanged = totalFuelRemoteWins > 0,
                )
                if (postNote != null) {
                    message = "$message · $postNote"
                }
            }
            onProgress?.onStatus(message)
            SyncResult(
                success = !anyFailure,
                message = message,
                vehiclesMerged = totalVehicles,
                expensesMerged = totalExpenses,
                fuelMerged = totalFuel,
                fuelRemoteWins = totalFuelRemoteWins,
            )
            } // withContext
        } finally {
            SyncRateLimit.installProgress(null)
        }
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
        } catch (e: Exception) {
            Log.w(TAG, "post-fuel merge/rebuild failed", e)
            null
        }
    }

    private fun onProgressSafe(msg: String) {
        Log.i(TAG, msg)
    }

    private suspend fun appendPostFuelMerge(
        result: SyncResult,
        fuelRowsChanged: Boolean,
    ): SyncResult {
        val note = runPostFuelMergeAndRebuild(fuelRowsChanged) ?: return result
        return result.copy(message = "${result.message} · $note")
    }

    /**
     * LWW "Merge acks" tab by ackId (Sync ID column). Includes soft-deleted acks
     * so tombstones propagate.
     *
     * When prefs [PolicySyncBridge.PREF_USE_POLICY_SYNC_MERGE_ACKS] is true (default false),
     * merge uses remotetable [com.davidelang.remotetable.MergeSync] lww_row via [PolicySyncBridge]
     * (pilot; fuel/vehicles unchanged).
     */
    private suspend fun syncMergeAcksTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
    ): Int {
        val resolved = resolveRemoteTabRows(
            dest, backend, TabularSchema.TAB_MERGE_ACKS, TabularSchema.MERGE_ACK_HEADERS, accountHint, bulk,
        )
        val remoteRows = resolved.rows
        val headerRow = remoteRows.firstOrNull() ?: TabularSchema.MERGE_ACK_HEADERS
        val headerIndex = TabularSchema.headerIndex(headerRow)
        val remoteDataRows = remoteRows.drop(1)
            .filter { it.any { cell -> cell.isNotBlank() } }

        val usePolicy = context.getSharedPreferences(SyncDestinationStore.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PolicySyncBridge.PREF_USE_POLICY_SYNC_MERGE_ACKS, false)

        val localAcks = mergeAckStore.getAllIncludingDeleted()
        val merged: List<MergeAck> = if (usePolicy) {
            Log.i(TAG, "Merge acks via PolicySync/MergeSync (lww_row pilot)")
            PolicySyncBridge.mergeAcksViaLwwRow(localAcks, remoteRows)
        } else {
            // Legacy coordinator LWW (default; production path).
            val remoteAcks = remoteDataRows.map { TabularSchema.rowToAck(it, headerIndex) }
            val localById = localAcks.filter { it.ackId.isNotBlank() }.associateBy { it.ackId }
            val remoteById = remoteAcks.filter { it.ackId.isNotBlank() }.associateBy { it.ackId }
            val allIds = (localById.keys + remoteById.keys).toSet()
            val out = mutableListOf<MergeAck>()
            for (ackId in allIds) {
                val winner = mergeLww(localById[ackId], remoteById[ackId])
                if (winner != null && winner.ackId.isNotBlank()) {
                    out.add(winner)
                }
            }
            out
        }

        for (ack in merged) {
            if (ack.ackId.isBlank()) continue
            mergeAckStore.upsertFromSync(ack)
        }

        val n = writeMergedAcksAndReturn(
            dest, backend, accountHint, resolved, headerIndex, remoteDataRows, merged,
        )
        if (usePolicy) {
            Log.i(TAG, "Merge acks PolicySync pilot wrote $n rows")
        }
        return n
    }

    private suspend fun writeMergedAcksAndReturn(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        resolved: ResolvedRemoteTab,
        headerIndex: Map<String, Int>,
        remoteDataRows: List<List<String>>,
        merged: List<MergeAck>,
    ): Int {
        val sortedMerged = merged.sortedWith(compareBy({ it.kind }, { it.ackId }))
        writeRowsIncremental(
            dest = dest,
            backend = backend,
            tabName = TabularSchema.TAB_MERGE_ACKS,
            headers = TabularSchema.MERGE_ACK_HEADERS,
            sortedRows = sortedMerged.map { TabularSchema.ackToRow(it) },
            sortedSyncIds = sortedMerged.map { it.ackId },
            remoteDataRows = remoteDataRows,
            headerIndex = headerIndex,
            accountHint = accountHint,
            logTag = "MergeAcks",
            forceFullRewrite = resolved.forceFullRewrite,
        )
        return sortedMerged.size
    }

    private fun resolveLegacyOrPrimaryDest(store: SyncDestinationStore): SpreadsheetDestination {
        store.spreadsheetDestination()?.let { return it }
        val legacyId = legacySheetId()
        if (legacyId.isBlank()) {
            return SpreadsheetDestination()
        }
        val prefs = context.getSharedPreferences(SyncDestinationStore.PREFS_NAME, Context.MODE_PRIVATE)
        return SpreadsheetDestination(
            targetId = legacyId,
            enabled = prefs.getBoolean("sync_enabled", false),
            wifiOnly = prefs.getBoolean("wifi_only", true),
            chargingOnly = prefs.getBoolean("charging_only", false),
            frequencyMinutes = (prefs.getInt("frequency_hours", 6) * 60)
                .coerceIn(
                    SpreadsheetDestination.MIN_FREQUENCY_MINUTES,
                    SpreadsheetDestination.MAX_FREQUENCY_MINUTES,
                ),
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

    /**
     * Sync one destination once. Google Sheets HTTP pace / 429 retry live in
     * **remotetable** L0 ([com.davidelang.remotetable.GoogleSheetsBackend]);
     * [SyncRateLimit] still supplies multi-dest cooldowns and UI progress bridge.
     * Legacy [GoogleSheetsClient] paths (browse/create) retain [SyncRateLimit.withSheetsApiLimit].
     * No whole-dest restart that re-merges completed tabs.
     */
    private suspend fun syncSingleDestination(
        dest: SpreadsheetDestination,
        accountHint: String?,
        @Suppress("UNUSED_PARAMETER") onProgress: SyncProgressListener? = null,
    ): SyncResult {
        val backend = tabularApi.backendFor(dest)
        val targetId = backend.resolveTargetId(dest)
        if (targetId.isBlank()) {
            return SyncResult(false, "Spreadsheet not configured")
        }
        val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
        if (!isAccountReady(dest, backend, hint)) {
            return SyncResult(false, authRequiredMessage(dest.provider))
        }
        return try {
            // System Unassigned vehicle (id=0) so Fuel - Unassigned tab exists
            vehicleRepository.ensureUnassignedVehicle()
            // One titles list + bulk batchGet for compare pass (Vehicles/Expenses/acks/all Fuel - *)
            val sheetTitles = backend.listTabTitles(dest, hint)
            val bulk = prefetchCompareTabs(dest, backend, hint, sheetTitles)
            val vehiclesMerged = syncVehiclesTab(dest, backend, hint, bulk)
            val expensesMerged = syncExpensesTab(dest, backend, hint, bulk)
            val fuel = syncFuelTabs(dest, backend, hint, bulk, sheetTitles)
            val mergeAcksMerged = syncMergeAcksTab(dest, backend, hint, bulk)
            Log.i(TAG, "Merge acks tab upserted=$mergeAcksMerged")
            SyncResult(
                success = true,
                message = buildString {
                    append("Sync complete: $vehiclesMerged vehicles, $expensesMerged expenses, ")
                    append("${fuel.upserted} fuel")
                    if (fuel.remoteWins > 0) append(" (${fuel.remoteWins} remote/new)")
                    if (mergeAcksMerged > 0) append(", $mergeAcksMerged merge-acks")
                },
                vehiclesMerged = vehiclesMerged,
                expensesMerged = expensesMerged,
                fuelMerged = fuel.upserted,
                fuelRemoteWins = fuel.remoteWins,
            )
        } catch (e: SpreadsheetMissingColumnsException) {
            // User-visible: wrong/corrupt sheet headers — do not wrap as generic network error.
            Log.e(TAG, "Sync aborted for dest=${dest.id}: ${e.message}")
            SyncResult(false, e.message ?: SpreadsheetMissingColumnsException.formatUserMessage(e.tabName, e.missingColumns))
        } catch (e: Exception) {
            // Compose dispose / structured cancel must not become a stored spreadsheet failure.
            if (e.isNonFailureCancel()) throw e
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
            SyncResult(false, message)
        }
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
        } catch (e: Exception) {
            Log.w(TAG, "Post-sync vehicle image download batch failed (sheet sync still OK)", e)
        }
    }

    private fun destLabel(dest: SpreadsheetDestination): String =
        dest.displayName.ifBlank {
            dest.targetId.take(12).ifBlank { dest.provider.displayLabel() }
        }

    private fun resolveTargetId(dest: SpreadsheetDestination?): String {
        if (dest == null) return legacySheetId()
        return tabularApi.backendFor(dest).resolveTargetId(dest)
    }

    private fun legacySheetId(): String {
        val prefs = context.getSharedPreferences(SyncDestinationStore.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("sheet_id", "")?.trim().orEmpty()
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
     * Resolve full tab body for LWW.
     * - **Missing tab / blank tab:** ensureHeaders creates canonical headers; empty remote; full rewrite write.
     * - **Valid headers:** cache or ensure missing optional cols; incremental write when ordered.
     * - **Corrupt headers** (cells present, required names missing): **fail** with named columns — no ensureHeaders merge, no LWW, no writeAllRows.
     */
    private suspend fun resolveRemoteTabRows(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        tabName: String,
        expectedHeaders: List<String>,
        accountHint: String?,
        bulk: Map<String, List<List<String>>>?,
    ): ResolvedRemoteTab {
        fun firstNames(grid: List<List<String>>): List<String> =
            grid.firstOrNull()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

        fun failCorrupt(grid: List<List<String>>) {
            val first = firstNames(grid)
            val missing = TabularSchema.missingRequiredHeaders(first)
            Log.e(
                TAG,
                "Corrupt/missing headers on $tabName — abort dest sync. missing=$missing firstRow=$first",
            )
            throw SpreadsheetMissingColumnsException(tabName, missing.ifEmpty { listOf("Sync ID") })
        }

        val cached = bulk?.get(tabName)
        if (cached != null) {
            // Case 2: blank tab (exists but no cells)
            if (TabularSchema.isCompletelyBlankGrid(cached)) {
                Log.i(TAG, "Blank tab $tabName — ensureHeaders + empty remote")
                backend.ensureHeaders(dest, tabName, expectedHeaders, accountHint)
                val after = backend.readAllRows(dest, tabName, accountHint)
                return ResolvedRemoteTab(
                    rows = if (TabularSchema.isValidHeaderRow(firstNames(after))) after
                    else listOf(expectedHeaders),
                    forceFullRewrite = true,
                )
            }
            // Case 3: non-blank but missing required columns
            val first = firstNames(cached)
            val missing = TabularSchema.missingRequiredHeaders(first)
            if (missing.isNotEmpty()) {
                failCorrupt(cached)
            }
            // Valid identity headers; maybe missing optional expected cols
            val complete = expectedHeaders.all { it in first }
            if (complete) {
                val dataEmpty = cached.drop(1).none { row -> row.any { it.isNotBlank() } }
                return ResolvedRemoteTab(cached, forceFullRewrite = dataEmpty)
            }
            Log.i(TAG, "Bulk cache header incomplete for $tabName — ensureHeaders + re-read")
            backend.ensureHeaders(dest, tabName, expectedHeaders, accountHint)
            val after = backend.readAllRows(dest, tabName, accountHint)
            val afterFirst = firstNames(after)
            val afterMissing = TabularSchema.missingRequiredHeaders(afterFirst)
            if (afterMissing.isNotEmpty()) {
                failCorrupt(after)
            }
            val dataEmpty = after.drop(1).none { row -> row.any { it.isNotBlank() } }
            return ResolvedRemoteTab(after, forceFullRewrite = dataEmpty)
        }
        // Case 1: tab not on sheet (not in bulk) — create
        Log.i(TAG, "No bulk cache for $tabName — ensureHeaders (create tab)")
        backend.ensureHeaders(dest, tabName, expectedHeaders, accountHint)
        val after = backend.readAllRows(dest, tabName, accountHint)
        if (!TabularSchema.isCompletelyBlankGrid(after) &&
            TabularSchema.missingRequiredHeaders(firstNames(after)).isNotEmpty()
        ) {
            // Unexpected: created tab still corrupt
            failCorrupt(after)
        }
        return ResolvedRemoteTab(
            rows = if (TabularSchema.isValidHeaderRow(firstNames(after))) after else listOf(expectedHeaders),
            forceFullRewrite = true,
        )
    }

    private suspend fun syncVehiclesTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
    ): Int {
        val resolved = resolveRemoteTabRows(
            dest, backend, TabularSchema.TAB_VEHICLES, TabularSchema.VEHICLE_HEADERS, accountHint, bulk,
        )
        val remoteRows = resolved.rows
        val headerRow = remoteRows.firstOrNull() ?: TabularSchema.VEHICLE_HEADERS
        val writeHeaders = TabularSchema.mergeHeaderOrder(headerRow, TabularSchema.VEHICLE_HEADERS)
        val headerIndex = TabularSchema.headerIndex(writeHeaders)
        val remoteDataRows = remoteRows.drop(1)
            .filter { it.any { cell -> cell.isNotBlank() } }
        val remoteVehicles = remoteDataRows
            .map { TabularSchema.rowToVehicle(it, headerIndex) }

        val localVehicles = vehicleRepository.getAllIncludingDeleted()
        val localBySyncId = localVehicles.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val remoteBySyncId = remoteVehicles.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val allSyncIds = (localBySyncId.keys + remoteBySyncId.keys).toSet()

        val merged = mutableListOf<Vehicle>()
        var count = 0
        for (syncId in allSyncIds) {
            val local = localBySyncId[syncId]
            val remote = remoteBySyncId[syncId]
            val winner = mergeVehicleLww(local, remote)
            if (winner != null && winner.syncId.isNotBlank()) {
                vehicleRepository.upsertFromSync(winner)
                merged.add(winner)
                count++
            }
        }

        val withLandmarks = merged.count { !it.landmarkTextBlocksJson.isNullOrBlank() }
        Log.i(TAG, "Vehicles tab merged: upserted=$count withLandmarks=$withLandmarks")

        val sortedMerged = merged.sortedWith(compareBy({ it.name.lowercase() }, { it.syncId }))
        writeRowsIncremental(
            dest = dest,
            backend = backend,
            tabName = TabularSchema.TAB_VEHICLES,
            headers = writeHeaders,
            sortedRows = sortedMerged.map { TabularSchema.vehicleToRow(it) },
            sortedSyncIds = sortedMerged.map { it.syncId },
            remoteDataRows = remoteDataRows,
            headerIndex = headerIndex,
            accountHint = accountHint,
            logTag = "Vehicles",
            forceFullRewrite = resolved.forceFullRewrite,
        )
        return count
    }

    private suspend fun syncExpensesTab(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
    ): Int {
        val resolved = resolveRemoteTabRows(
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
        val remoteExpenses = remoteDataRows.map { row ->
            val parsed = TabularSchema.rowToExpense(row, headerIndex)
            val vehicleSyncIds = TabularSchema.rowToExpenseVehicleSyncIds(row, headerIndex)
            ExpenseVehicleSyncIds.applyResolvedVehicles(parsed, vehicleSyncIds, vehicleIdBySyncId)
        }

        val localExpenses = expenseRepository.getAllIncludingDeleted()
        val localBySyncId = localExpenses.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val remoteBySyncId = remoteExpenses.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val allSyncIds = (localBySyncId.keys + remoteBySyncId.keys).toSet()

        val merged = mutableListOf<ExpenseEntry>()
        var count = 0
        for (syncId in allSyncIds) {
            val local = localBySyncId[syncId]
            val remote = remoteBySyncId[syncId]
            val winner = mergeLww(local, remote)
            if (winner != null && winner.syncId.isNotBlank()) {
                expenseRepository.upsertFromSync(winner)
                merged.add(winner)
                count++
            }
        }

        val sortedMerged = merged.sortedWith(compareBy({ it.date }, { it.syncId }))
        writeRowsIncremental(
            dest = dest,
            backend = backend,
            tabName = TabularSchema.TAB_EXPENSES,
            headers = writeHeaders,
            sortedRows = sortedMerged.map { entry ->
                TabularSchema.expenseToRow(entry, vehicleSyncIdById[entry.vehicleId].orEmpty())
            },
            sortedSyncIds = sortedMerged.map { it.syncId },
            remoteDataRows = remoteDataRows,
            headerIndex = headerIndex,
            accountHint = accountHint,
            logTag = "Expenses",
            forceFullRewrite = resolved.forceFullRewrite,
        )
        return count
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

    private suspend fun migrateFuelTabRenameIfNeeded(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        vehicle: Vehicle,
        expectedTabName: String,
        sheetTitles: List<String>,
        accountHint: String?,
        hintStore: FuelTabRenameHintStore,
        bulk: MutableMap<String, List<List<String>>> = mutableMapOf(),
    ): List<String> {
        val orphanTabs = resolveOrphanFuelTabs(
            dest, backend, vehicle, expectedTabName, sheetTitles, accountHint, hintStore, bulk,
        )
        if (orphanTabs.isEmpty()) return sheetTitles
        if (orphanTabs.size > 1) {
            Log.w(
                TAG,
                "Fuel tab rename skipped: multiple orphan tabs for syncId=${vehicle.syncId}: $orphanTabs",
            )
            return sheetTitles
        }
        val oldTab = orphanTabs.single()
        if (expectedTabName in sheetTitles) return sheetTitles
        val renamed = backend.renameTab(dest, oldTab, expectedTabName, accountHint)
        if (renamed) {
            Log.i(TAG, "Fuel tab migrate: \"$oldTab\" → \"$expectedTabName\" (rename)")
            hintStore.clearHint(vehicle.syncId)
            // Keep bulk compare cache under the new tab name (no re-GET).
            bulk.remove(oldTab)?.let { bulk[expectedTabName] = it }
            return sheetTitles.map { if (it == oldTab) expectedTabName else it }
        }
        return sheetTitles
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
    ): List<String> {
        val orphanTabs = resolveOrphanFuelTabs(
            dest, backend, vehicle, expectedTabName, sheetTitles, accountHint, hintStore, bulk,
        )
        if (orphanTabs.size != 1) return sheetTitles
        val oldTab = orphanTabs.single()
        if (oldTab !in sheetTitles || expectedTabName !in sheetTitles) return sheetTitles

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

        backend.deleteTab(dest, oldTab, accountHint)
        bulk.remove(oldTab)
        hintStore.clearHint(vehicle.syncId)
        Log.i(
            TAG,
            "Fuel tab migrate: \"$oldTab\" → \"$expectedTabName\" rows=$mergedCount (merge+delete)",
        )
        return sheetTitles.filter { it != oldTab }
    }

    /**
     * Fuel sync order (locked product):
     * 1) LWW all vehicle fuel tabs into Room
     * 2) Field-merge (absorb partials; soft-delete losers) — **before** sheet write
     * 3) Write each fuel tab from **fresh** Room (incl. tombstones)
     * Stage C question rebuild stays in post-sync after this returns.
     */
    private suspend fun syncFuelTabs(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
        bulk: Map<String, List<List<String>>> = emptyMap(),
        initialTitles: List<String>? = null,
    ): FuelTabSyncStats {
        // Reuse titles from dest prefetch when provided (avoid second listTabTitles GET).
        var sheetTitles = initialTitles ?: backend.listTabTitles(dest, accountHint)
        val hintStore = FuelTabRenameHintStore(context)
        val vehicles = vehicleRepository.getAllIncludingDeleted().filter { !it.deleted && it.syncId.isNotBlank() }
        val vehicleIdBySyncId = vehicles.associate { it.syncId to it.id }
        var total = 0
        var remoteWins = 0
        // Mutable bulk map: rename migrations may invalidate cache entries.
        val bulkRows = bulk.toMutableMap()

        // Snapshot remote headers/rows per vehicle for write-back after merge
        data class FuelTabSnapshot(
            val vehicle: Vehicle,
            val tabName: String,
            /** Preserved remote header order (+ appended missing cols after ensureHeaders). */
            val writeHeaders: List<String>,
            val headerIndex: Map<String, Int>,
            val remoteDataRows: List<List<String>>,
            val forceFullRewrite: Boolean,
        )
        val snapshots = mutableListOf<FuelTabSnapshot>()

        // --- Pass 1: LWW only (no sheet write yet); use bulk cache for tab bodies ---
        for (vehicle in vehicles) {
            val tabName = TabularSchema.fuelTabName(vehicle.name)
            sheetTitles = migrateFuelTabRenameIfNeeded(
                dest, backend, vehicle, tabName, sheetTitles, accountHint, hintStore, bulkRows,
            )
            sheetTitles = migrateFuelTabMergeAndDeleteIfNeeded(
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
            val resolved = resolveRemoteTabRows(
                dest, backend, tabName, TabularSchema.FUEL_HEADERS, accountHint, bulkRows,
            )
            val remoteRows = resolved.rows
            // Keep cache in sync after resolve (may have re-read after ensureHeaders).
            bulkRows[tabName] = remoteRows
            val headerRow = remoteRows.firstOrNull()?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?: TabularSchema.FUEL_HEADERS
            // Valid headers: preserve order; invalid path already returned canonical-only rows.
            val writeHeaders = TabularSchema.mergeHeaderOrder(headerRow, TabularSchema.FUEL_HEADERS)
            val headerIndex = TabularSchema.headerIndex(writeHeaders)
            val remoteDataRows = remoteRows.drop(1)
                .filter { it.any { cell -> cell.isNotBlank() } }
            val remoteFuel = parseFuelEntriesFromRows(remoteRows, vehicle, vehicleIdBySyncId)

            val localFuel = fuelRepository.getAllIncludingDeleted()
                .filter { it.vehicleId == vehicle.id }
            val localBySyncId = localFuel.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
            val remoteBySyncId = remoteFuel.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
            val allSyncIds = (localBySyncId.keys + remoteBySyncId.keys).toSet()

            Log.i(
                TAG,
                "Fuel LWW ${vehicle.name}: local=${localFuel.size} remote=${remoteFuel.size} keys=${allSyncIds.size}",
            )

            for (syncId in allSyncIds) {
                val local = localBySyncId[syncId]
                val remote = remoteBySyncId[syncId]
                val winner = mergeLww(local, remote)?.let { entry ->
                    (entry as FuelEntry).copy(vehicleId = vehicle.id)
                }
                if (winner != null && winner.syncId.isNotBlank()) {
                    val remoteWon = remote != null && (
                        local == null || remote.updatedAt > local.updatedAt
                        )
                    if (remoteWon) {
                        remoteWins++
                        if (remote.deleted && local != null && !local.deleted) {
                            Log.i(
                                TAG,
                                "LWW: remote tombstone wins syncId=$syncId over local live " +
                                    "id=${local.id} remoteUpdatedAt=${remote.updatedAt} " +
                                    "localUpdatedAt=${local.updatedAt}",
                            )
                        }
                    }
                    fuelRepository.upsertFromSync(winner)
                    total++
                }
            }

            snapshots.add(
                FuelTabSnapshot(
                    vehicle = vehicle,
                    tabName = tabName,
                    writeHeaders = writeHeaders,
                    headerIndex = headerIndex,
                    remoteDataRows = remoteDataRows,
                    forceFullRewrite = resolved.forceFullRewrite,
                ),
            )
        }

        // --- Pass 2: field-merge on full Room (absorb partials before write-back) ---
        val liveBefore = fuelRepository.getAllIncludingDeleted().filter { !it.deleted }.size
        Log.i(TAG, "Fuel field-merge before sheet write; live=$liveBefore")
        val mergeStats = try {
            batchFuelImportCoordinator.get().fieldMergeForSync { msg ->
                Log.i(TAG, "fieldMerge: $msg")
            }
        } catch (e: Exception) {
            Log.w(TAG, "field-merge before write failed", e)
            null
        }
        if (mergeStats != null) {
            Log.i(
                TAG,
                "Fuel field-merge done updates=${mergeStats.updated} deletes=${mergeStats.deleted} " +
                    "secondPass=${mergeStats.secondPass} live=${mergeStats.liveCount}",
            )
        }

        // --- Pass 3: write each tab from fresh Room (incl. deleted tombstones) ---
        for (snap in snapshots) {
            val vehicle = snap.vehicle
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
                tabName = snap.tabName,
                // Keep sheet column order (append-only Notes); do not force human-first reorder.
                headers = snap.writeHeaders,
                sortedRows = sortedMerged.map {
                    TabularSchema.fuelToRow(it, vehicle.syncId, snap.writeHeaders)
                },
                sortedSyncIds = sortedMerged.map { it.syncId },
                remoteDataRows = snap.remoteDataRows,
                headerIndex = snap.headerIndex,
                accountHint = accountHint,
                logTag = "Fuel-${vehicle.name}",
                forceFullRewrite = snap.forceFullRewrite,
            )
        }
        return FuelTabSyncStats(upserted = total, remoteWins = remoteWins)
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

    private fun mergeVehicleLww(local: Vehicle?, remote: Vehicle?): Vehicle? {
        val base = mergeLww(local, remote) ?: return null
        if (local == null || remote == null) return base
        val loser = when {
            remote.updatedAt > local.updatedAt -> local
            local.updatedAt > remote.updatedAt -> remote
            else -> remote
        }
        return overlayVehicleDefinitionFields(base, loser)
    }

    private fun overlayVehicleDefinitionFields(winner: Vehicle, loser: Vehicle): Vehicle {
        var result = winner
        if (!hasCompleteOdoCrops(result) && hasCompleteOdoCrops(loser)) {
            Log.i(TAG, "Vehicle overlay: odo crops from syncId=${loser.syncId} onto winner=${winner.syncId}")
            result = result.copy(
                odometerCropLeft = loser.odometerCropLeft,
                odometerCropTop = loser.odometerCropTop,
                odometerCropRight = loser.odometerCropRight,
                odometerCropBottom = loser.odometerCropBottom,
            )
        }
        if (!hasCompleteOtherCrops(result) && hasCompleteOtherCrops(loser)) {
            Log.i(TAG, "Vehicle overlay: other-text crops from syncId=${loser.syncId} onto winner=${winner.syncId}")
            result = result.copy(
                otherTextCropLeft = loser.otherTextCropLeft,
                otherTextCropTop = loser.otherTextCropTop,
                otherTextCropRight = loser.otherTextCropRight,
                otherTextCropBottom = loser.otherTextCropBottom,
            )
        }
        if (result.landmarkTextBlocksJson.isNullOrBlank() && !loser.landmarkTextBlocksJson.isNullOrBlank()) {
            Log.i(TAG, "Vehicle overlay: landmarks from syncId=${loser.syncId} onto winner=${winner.syncId}")
            result = result.copy(landmarkTextBlocksJson = loser.landmarkTextBlocksJson)
        }
        if (result.cloudManifest.isNullOrBlank() && !loser.cloudManifest.isNullOrBlank()) {
            result = result.copy(cloudManifest = loser.cloudManifest)
        }
        val ref = photoStorage.pickPreferredLocalPath(
            result.referenceDashPhotoUrl,
            loser.referenceDashPhotoUrl,
        )
        val cleaned = photoStorage.pickPreferredLocalPath(
            result.cleanedReferenceDashPhotoUrl,
            loser.cleanedReferenceDashPhotoUrl,
        )
        if (ref != result.referenceDashPhotoUrl || cleaned != result.cleanedReferenceDashPhotoUrl) {
            result = result.copy(
                referenceDashPhotoUrl = ref,
                cleanedReferenceDashPhotoUrl = cleaned,
            )
        }
        return result
    }

    private fun hasCompleteOdoCrops(vehicle: Vehicle): Boolean =
        vehicle.odometerCropLeft != null && vehicle.odometerCropTop != null &&
            vehicle.odometerCropRight != null && vehicle.odometerCropBottom != null

    private fun hasCompleteOtherCrops(vehicle: Vehicle): Boolean =
        vehicle.otherTextCropLeft != null && vehicle.otherTextCropTop != null &&
            vehicle.otherTextCropRight != null && vehicle.otherTextCropBottom != null

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