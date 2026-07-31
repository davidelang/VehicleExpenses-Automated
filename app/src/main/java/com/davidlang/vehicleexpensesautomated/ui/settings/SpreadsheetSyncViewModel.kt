package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator
import com.davidlang.vehicleexpensesautomated.data.sync.DriveBrowserItem
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveAuth
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveBrowseCatalog
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveBrowserClient
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleSheetsAuth
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.sync.MicrosoftOneDriveAuth
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetSyncCoordinator
import com.davidlang.vehicleexpensesautomated.data.sync.SyncManager
import com.davidlang.vehicleexpensesautomated.data.sync.SyncProgressListener
import com.davidlang.vehicleexpensesautomated.data.sync.SyncResult
import com.davidlang.vehicleexpensesautomated.data.sync.ZohoSheetAuth
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SpreadsheetSyncViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    val auth: GoogleSheetsAuth,
    val driveAuth: GoogleDriveAuth,
    val msAuth: MicrosoftOneDriveAuth,
    val zohoAuth: ZohoSheetAuth,
    private val sheetsClient: GoogleSheetsClient,
    private val driveBrowser: GoogleDriveBrowserClient,
    private val tabularApi: TabularShareApi,
    private val coordinator: SpreadsheetSyncCoordinator,
    private val syncManager: SyncManager,
    private val batchFuelImportCoordinator: BatchFuelImportCoordinator,
) : ViewModel() {

    private val _manualSyncStatus = MutableStateFlow("")
    val manualSyncStatus: StateFlow<String> = _manualSyncStatus.asStateFlow()
    private val _manualSyncInProgress = MutableStateFlow(false)
    val manualSyncInProgress: StateFlow<Boolean> = _manualSyncInProgress.asStateFlow()
    private val _manualSyncIsError = MutableStateFlow(false)
    val manualSyncIsError: StateFlow<Boolean> = _manualSyncIsError.asStateFlow()
    private val _manualSyncResult = MutableStateFlow<SyncResult?>(null)
    val manualSyncResult: StateFlow<SyncResult?> = _manualSyncResult.asStateFlow()

    /**
     * Runs spreadsheet Sync now on [viewModelScope] so leaving settings does not cancel.
     */
    fun startManualSync(accountHint: String = "", destId: String? = null) {
        if (_manualSyncInProgress.value) return
        viewModelScope.launch {
            _manualSyncInProgress.value = true
            _manualSyncIsError.value = false
            _manualSyncResult.value = null
            _manualSyncStatus.value = "Starting spreadsheet sync…"
            try {
                val result = withContext(Dispatchers.IO) {
                    coordinator.syncNow(
                        accountHint = accountHint.ifBlank { null },
                        destId = destId,
                        onProgress = SyncProgressListener { msg ->
                            _manualSyncStatus.value = msg
                            _manualSyncIsError.value = false
                        },
                    )
                }
                _manualSyncStatus.value = result.message
                _manualSyncIsError.value = !result.success
                _manualSyncResult.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _manualSyncIsError.value = true
                _manualSyncStatus.value = e.message ?: "Sync failed"
                _manualSyncResult.value = SyncResult(false, _manualSyncStatus.value)
            } finally {
                _manualSyncInProgress.value = false
            }
        }
    }

    fun clearManualSyncResult() {
        _manualSyncResult.value = null
    }

    suspend fun listSpreadsheetsForBrowse(
        accountHint: String,
        searchQuery: String,
        catalog: GoogleDriveBrowseCatalog = GoogleDriveBrowseCatalog.APP,
    ): List<DriveBrowserItem> =
        driveBrowser.listSpreadsheets(accountHint.ifBlank { null }, searchQuery, catalog)

    suspend fun syncNow(
        accountHint: String,
        onProgress: SyncProgressListener? = null,
        destId: String? = null,
    ): SyncResult = coordinator.syncNow(
        accountHint = accountHint.ifBlank { null },
        destId = destId,
        onProgress = onProgress,
    )

    /**
     * Detect-only: odo-only + pump-only pairs within merge window after a fuel pull.
     * Used for post-sync CTA — does not auto-merge.
     */
    suspend fun hasUnmatchedFuelPartials(): Boolean =
        batchFuelImportCoordinator.hasUnmatchedPartials()

    suspend fun testConnection(dest: SpreadsheetDestination): Boolean {
        val result = tabularApi.testConnection(dest)
        if (result.needsRemoteConsent && result.recoveryIntent != null) {
            throw SheetsRecoverableAuthException(result.recoveryIntent, result.message)
        }
        return result.success
    }

    suspend fun createSpreadsheet(accountHint: String, title: String = "Vehicle Expenses"): Pair<String, String> {
        try {
            val created = sheetsClient.createSpreadsheet(title, accountHint.ifBlank { null })
            val id = created.spreadsheetId
            sheetsClient.ensureHeaders(id, TabularSchema.TAB_VEHICLES, TabularSchema.VEHICLE_HEADERS, accountHint.ifBlank { null })
            sheetsClient.ensureHeaders(id, TabularSchema.TAB_EXPENSES, TabularSchema.EXPENSE_HEADERS, accountHint.ifBlank { null })
            val url = GoogleSheetsClient.spreadsheetUrlFromId(id)
            return id to url
        } catch (e: Exception) {
            throw SheetsAuthRecovery.wrapIfRecoverable(e)
        }
    }

    suspend fun createSpreadsheetForBrowse(accountHint: String, title: String): DriveBrowserItem {
        val (id, _) = createSpreadsheet(accountHint, title)
        return DriveBrowserItem(id = id, name = title)
    }

    fun rescheduleBackgroundSync() = syncManager.scheduleFromDestination()

    fun googleBackend() = tabularApi.backendFor(
        SpreadsheetDestination(provider = SpreadsheetProvider.GOOGLE_SHEETS),
    )
}