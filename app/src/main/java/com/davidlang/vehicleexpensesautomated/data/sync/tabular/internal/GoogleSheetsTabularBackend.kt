package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularCapabilities
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularTestResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsTabularBackend @Inject constructor(
    private val sheetsClient: GoogleSheetsClient,
) : TabularShareBackend {

    override val provider: SpreadsheetProvider = SpreadsheetProvider.GOOGLE_SHEETS

    override fun capabilities(): TabularCapabilities = TabularCapabilities(renameTab = true, browse = true)

    override fun isConfigured(dest: SpreadsheetDestination): Boolean =
        resolveTargetId(dest).isNotBlank()

    override fun resolveAccountName(accountHint: String?): String? =
        sheetsClient.resolveAccountNamePublic(accountHint)

    override fun resolveTargetId(dest: SpreadsheetDestination): String {
        if (dest.targetId.isNotBlank()) return dest.targetId
        if (dest.targetUrl.isNotBlank()) {
            return parseTargetIdFromUrl(dest.targetUrl).orEmpty()
        }
        return ""
    }

    override fun parseTargetIdFromUrl(url: String): String? =
        GoogleSheetsClient.parseSpreadsheetIdFromUrl(url)

    override fun targetUrlFromId(id: String): String =
        GoogleSheetsClient.spreadsheetUrlFromId(id)

    override suspend fun ensureHeaders(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        accountHint: String?,
    ) {
        val sheetId = resolveTargetId(dest)
        sheetsClient.ensureHeaders(sheetId, tabName, headers, accountHint)
    }

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> {
        val sheetId = resolveTargetId(dest)
        return sheetsClient.readAllRows(sheetId, tabName, accountHint)
    }

    override suspend fun listTabTitles(dest: SpreadsheetDestination, accountHint: String?): List<String> {
        val sheetId = resolveTargetId(dest)
        return sheetsClient.listSheetTitles(sheetId, accountHint)
    }

    override suspend fun renameTab(
        dest: SpreadsheetDestination,
        oldTitle: String,
        newTitle: String,
        accountHint: String?,
    ): Boolean {
        val sheetId = resolveTargetId(dest)
        return sheetsClient.renameTab(sheetId, oldTitle, newTitle, accountHint)
    }

    override suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?) {
        val sheetId = resolveTargetId(dest)
        sheetsClient.deleteTab(sheetId, tabName, accountHint)
    }

    override suspend fun appendRows(
        dest: SpreadsheetDestination,
        tabName: String,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        val sheetId = resolveTargetId(dest)
        sheetsClient.appendRows(sheetId, tabName, rows, accountHint)
    }

    override suspend fun updateRows(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        val sheetId = resolveTargetId(dest)
        sheetsClient.updateRows(sheetId, tabName, startRow, rows, accountHint)
    }

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) {
        val sheetId = resolveTargetId(dest)
        sheetsClient.clearTrailing(sheetId, tabName, startRow, accountHint)
    }

    override suspend fun writeAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        val sheetId = resolveTargetId(dest)
        sheetsClient.writeAllRows(sheetId, tabName, headers, rows, accountHint)
    }

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult {
        val sheetId = resolveTargetId(dest)
        if (sheetId.isBlank()) {
            return TabularTestResult(false, "Spreadsheet not configured")
        }
        return try {
            val ok = sheetsClient.testConnection(sheetId, accountHint)
            TabularTestResult(ok, if (ok) "Connection test passed" else "Connection test failed")
        } catch (e: SheetsRecoverableAuthException) {
            TabularTestResult(
                success = false,
                message = e.message ?: SheetsAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE,
                needsRemoteConsent = true,
                recoveryIntent = e.recoveryIntent,
            )
        } catch (e: Exception) {
            TabularTestResult(false, SheetsAuthRecovery.userMessage(e))
        }
    }
}