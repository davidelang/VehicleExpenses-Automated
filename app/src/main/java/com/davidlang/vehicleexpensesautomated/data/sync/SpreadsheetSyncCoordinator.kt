package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseVehicleSyncIds
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareApi
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
) {

    private val syncMutex = Mutex()

    suspend fun syncNow(
        accountHint: String? = null,
        scope: SyncDestinationScope = SyncDestinationScope.CONFIGURED,
        onProgress: SyncProgressListener? = null,
    ): SyncResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            syncIdBackfill.runIfNeeded()
            val store = SyncDestinationStore(context)
            val failureStore = SyncFailureStore(context)
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
                val single = syncSingleDestination(dest, hint)
                recordSpreadsheetResult(failureStore, dest.id, label, single)
                runPostSyncVehicleDownloads(hint)
                val withPost = if (single.success) {
                    appendPostFuelMerge(
                        single,
                        fuelRowsChanged = single.fuelRemoteWins > 0,
                    )
                } else {
                    single
                }
                onProgress?.onStatus(if (withPost.success) "$label done" else "$label failed")
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
            for (dest in destinations) {
                val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
                val label = destLabel(dest)
                onProgress?.onStatus("Syncing $label…")
                val result = syncSingleDestination(dest, hint)
                recordSpreadsheetResult(failureStore, dest.id, label, result)
                results.add(label to result)
                onProgress?.onStatus(
                    if (result.success) {
                        "$label done (${result.vehiclesMerged} vehicles, ${result.expensesMerged} expenses, ${result.fuelMerged} fuel)"
                    } else {
                        "$label failed"
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
        }
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
        destName: String,
        result: SyncResult,
    ) {
        if (result.success) {
            failureStore.clearSpreadsheetFailure(destId)
        } else {
            failureStore.recordSpreadsheetFailure(destId, destName)
        }
    }

    private suspend fun syncSingleDestination(dest: SpreadsheetDestination, accountHint: String?): SyncResult {
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
            val vehiclesMerged = syncVehiclesTab(dest, backend, hint)
            val expensesMerged = syncExpensesTab(dest, backend, hint)
            val fuel = syncFuelTabs(dest, backend, hint)
            SyncResult(
                success = true,
                message = "Sync complete: $vehiclesMerged vehicles, $expensesMerged expenses, " +
                    "${fuel.upserted} fuel" +
                    if (fuel.remoteWins > 0) " (${fuel.remoteWins} remote/new)" else "",
                vehiclesMerged = vehiclesMerged,
                expensesMerged = expensesMerged,
                fuelMerged = fuel.upserted,
                fuelRemoteWins = fuel.remoteWins,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed for dest=${dest.id}", e)
            val wrapped = SheetsAuthRecovery.wrapIfRecoverable(e)
            if (wrapped is SheetsRecoverableAuthException) {
                SyncResult(
                    success = false,
                    message = wrapped.message ?: SheetsAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE,
                    needsRemoteConsent = true,
                    recoveryIntent = wrapped.recoveryIntent,
                )
            } else {
                SyncResult(false, SheetsAuthRecovery.userMessage(wrapped))
            }
        }
    }

    private fun isAccountReady(dest: SpreadsheetDestination, backend: TabularShareBackend, hint: String?): Boolean =
        when (dest.provider) {
            SpreadsheetProvider.ETHERCALC -> true
            SpreadsheetProvider.EXCEL -> backend.resolveAccountName(hint) != null
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
        SpreadsheetProvider.EXCEL -> "Sign in with Microsoft first"
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

    private suspend fun syncVehiclesTab(dest: SpreadsheetDestination, backend: TabularShareBackend, accountHint: String?): Int {
        backend.ensureHeaders(dest, TabularSchema.TAB_VEHICLES, TabularSchema.VEHICLE_HEADERS, accountHint)
        val remoteRows = backend.readAllRows(dest, TabularSchema.TAB_VEHICLES, accountHint)
        val headerRow = remoteRows.firstOrNull() ?: TabularSchema.VEHICLE_HEADERS
        val headerIndex = TabularSchema.headerIndex(headerRow)
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
            headers = TabularSchema.VEHICLE_HEADERS,
            sortedRows = sortedMerged.map { TabularSchema.vehicleToRow(it) },
            sortedSyncIds = sortedMerged.map { it.syncId },
            remoteDataRows = remoteDataRows,
            headerIndex = headerIndex,
            accountHint = accountHint,
            logTag = "Vehicles",
        )
        return count
    }

    private suspend fun syncExpensesTab(dest: SpreadsheetDestination, backend: TabularShareBackend, accountHint: String?): Int {
        backend.ensureHeaders(dest, TabularSchema.TAB_EXPENSES, TabularSchema.EXPENSE_HEADERS, accountHint)
        val remoteRows = backend.readAllRows(dest, TabularSchema.TAB_EXPENSES, accountHint)
        val headerRow = remoteRows.firstOrNull() ?: TabularSchema.EXPENSE_HEADERS
        val headerIndex = TabularSchema.headerIndex(headerRow)
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
            headers = TabularSchema.EXPENSE_HEADERS,
            sortedRows = sortedMerged.map { entry ->
                TabularSchema.expenseToRow(entry, vehicleSyncIdById[entry.vehicleId].orEmpty())
            },
            sortedSyncIds = sortedMerged.map { it.syncId },
            remoteDataRows = remoteDataRows,
            headerIndex = headerIndex,
            accountHint = accountHint,
            logTag = "Expenses",
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
    ): List<String> {
        val candidates = sheetTitles.filter { title ->
            title.startsWith(TabularSchema.FUEL_TAB_PREFIX) && title != expectedTabName
        }
        return candidates.filter { tabName ->
            fuelTabBelongsToVehicle(dest, backend, tabName, vehicleSyncId, accountHint)
        }
    }

    private suspend fun fuelTabBelongsToVehicle(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        tabName: String,
        vehicleSyncId: String,
        accountHint: String?,
    ): Boolean {
        val remoteRows = backend.readAllRows(dest, tabName, accountHint)
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
    ): List<String> {
        val scanned = findOrphanFuelTabs(dest, backend, vehicle.syncId, expectedTabName, sheetTitles, accountHint)
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
    ): List<String> {
        val orphanTabs = resolveOrphanFuelTabs(
            dest, backend, vehicle, expectedTabName, sheetTitles, accountHint, hintStore,
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
    ): List<FuelEntry> {
        val remoteRows = backend.readAllRows(dest, tabName, accountHint)
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
    ): List<String> {
        val orphanTabs = resolveOrphanFuelTabs(
            dest, backend, vehicle, expectedTabName, sheetTitles, accountHint, hintStore,
        )
        if (orphanTabs.size != 1) return sheetTitles
        val oldTab = orphanTabs.single()
        if (oldTab !in sheetTitles || expectedTabName !in sheetTitles) return sheetTitles

        val oldFuel = parseFuelEntriesFromTab(dest, backend, oldTab, vehicle, vehicleIdBySyncId, accountHint)
        val newFuel = parseFuelEntriesFromTab(dest, backend, expectedTabName, vehicle, vehicleIdBySyncId, accountHint)
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
        hintStore.clearHint(vehicle.syncId)
        Log.i(
            TAG,
            "Fuel tab migrate: \"$oldTab\" → \"$expectedTabName\" rows=$mergedCount (merge+delete)",
        )
        return sheetTitles.filter { it != oldTab }
    }

    private suspend fun syncFuelTabs(
        dest: SpreadsheetDestination,
        backend: TabularShareBackend,
        accountHint: String?,
    ): FuelTabSyncStats {
        var sheetTitles = backend.listTabTitles(dest, accountHint)
        val hintStore = FuelTabRenameHintStore(context)
        val vehicles = vehicleRepository.getAllIncludingDeleted().filter { !it.deleted && it.syncId.isNotBlank() }
        val vehicleIdBySyncId = vehicles.associate { it.syncId to it.id }
        var total = 0
        var remoteWins = 0
        for (vehicle in vehicles) {
            val tabName = TabularSchema.fuelTabName(vehicle.name)
            sheetTitles = migrateFuelTabRenameIfNeeded(
                dest, backend, vehicle, tabName, sheetTitles, accountHint, hintStore,
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
            )
            backend.ensureHeaders(dest, tabName, TabularSchema.FUEL_HEADERS, accountHint)
            val remoteRows = backend.readAllRows(dest, tabName, accountHint)
            val headerRow = remoteRows.firstOrNull() ?: TabularSchema.FUEL_HEADERS
            val headerIndex = TabularSchema.headerIndex(headerRow)
            val remoteDataRows = remoteRows.drop(1)
                .filter { it.any { cell -> cell.isNotBlank() } }
            val remoteFuel = parseFuelEntriesFromTab(dest, backend, tabName, vehicle, vehicleIdBySyncId, accountHint)

            val localFuel = fuelRepository.getAllIncludingDeleted()
                .filter { it.vehicleId == vehicle.id }
            val localBySyncId = localFuel.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
            val remoteBySyncId = remoteFuel.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
            val allSyncIds = (localBySyncId.keys + remoteBySyncId.keys).toSet()

            val merged = mutableListOf<FuelEntry>()
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
                    if (remoteWon) remoteWins++
                    fuelRepository.upsertFromSync(winner)
                    merged.add(winner)
                    total++
                }
            }

            val sortedMerged = merged.sortedWith(compareBy({ it.timestamp }, { it.odometer }, { it.syncId }))
            writeRowsIncremental(
                dest = dest,
                backend = backend,
                tabName = tabName,
                headers = TabularSchema.FUEL_HEADERS,
                sortedRows = sortedMerged.map { TabularSchema.fuelToRow(it, vehicle.syncId) },
                sortedSyncIds = sortedMerged.map { it.syncId },
                remoteDataRows = remoteDataRows,
                headerIndex = headerIndex,
                accountHint = accountHint,
                logTag = "Fuel-${vehicle.name}",
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
                @Suppress("UNCHECKED_CAST")
                val winner = if (remoteTs > localTs) remote else local
                winner as T
            }
        }
    }

    private fun updatedAtOf(item: Any): Long = when (item) {
        is Vehicle -> item.updatedAt
        is FuelEntry -> item.updatedAt
        is ExpenseEntry -> item.updatedAt
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
    ): SheetWriteStats {
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