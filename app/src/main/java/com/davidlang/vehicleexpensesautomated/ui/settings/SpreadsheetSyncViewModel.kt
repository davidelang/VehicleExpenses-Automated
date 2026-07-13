package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.davidlang.vehicleexpensesautomated.data.sync.DriveBrowserItem
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveBrowserClient
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleSheetsAuth
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.sync.MicrosoftOneDriveAuth
import com.davidlang.vehicleexpensesautomated.data.sync.ZohoSheetAuth
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetSyncCoordinator
import com.davidlang.vehicleexpensesautomated.data.sync.SyncManager
import com.davidlang.vehicleexpensesautomated.data.sync.SyncResult
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class SpreadsheetSyncViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    val auth: GoogleSheetsAuth,
    val msAuth: MicrosoftOneDriveAuth,
    val zohoAuth: ZohoSheetAuth,
    private val sheetsClient: GoogleSheetsClient,
    private val driveBrowser: GoogleDriveBrowserClient,
    private val tabularApi: TabularShareApi,
    private val coordinator: SpreadsheetSyncCoordinator,
    private val syncManager: SyncManager,
) : ViewModel() {

    suspend fun listSpreadsheetsForBrowse(accountHint: String, searchQuery: String): List<DriveBrowserItem> =
        driveBrowser.listSpreadsheets(accountHint.ifBlank { null }, searchQuery)

    suspend fun syncNow(accountHint: String): SyncResult = coordinator.syncNow(accountHint)

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