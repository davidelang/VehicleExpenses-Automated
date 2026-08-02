package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.MicrosoftOneDriveAuth
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularCapabilities
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularTestResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcelGraphTabularBackend @Inject constructor(
    private val msAuth: MicrosoftOneDriveAuth,
    private val graphClient: GraphExcelClient,
) : TabularShareBackend {

    override val provider: SpreadsheetProvider = SpreadsheetProvider.EXCEL_GRAPH

    override fun capabilities(): TabularCapabilities = TabularCapabilities(renameTab = true, browse = false)

    override fun isConfigured(dest: SpreadsheetDestination): Boolean =
        resolveTargetId(dest).isNotBlank()

    override fun resolveAccountName(accountHint: String?): String? {
        val hint = accountHint?.takeIf { it.isNotBlank() } ?: msAuth.getPersistedAccountEmail()
        return hint
    }

    override fun resolveTargetId(dest: SpreadsheetDestination): String {
        if (dest.targetId.isNotBlank()) return dest.targetId
        return dest.configJson.trim() // legacy: workbook item id in configJson
    }

    override fun parseTargetIdFromUrl(url: String): String? {
        // OneDrive/SharePoint item URLs vary; user binds via targetId field.
        val regex = Regex("""items/([^/?]+)""")
        return regex.find(url.trim())?.groupValues?.get(1)
    }

    override fun targetUrlFromId(id: String): String =
        "https://onedrive.live.com/?id=$id"

    private suspend fun accessToken(accountHint: String?): String {
        val email = resolveAccountName(accountHint)
        val auth = msAuth.refreshSilent(email)
            ?: throw IllegalStateException("Sign in with Microsoft first")
        return auth.accessToken
    }

    private fun workbookId(dest: SpreadsheetDestination): String = resolveTargetId(dest)

    override suspend fun ensureHeaders(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        accountHint: String?,
    ) {
        val token = accessToken(accountHint)
        val wb = workbookId(dest)
        graphClient.ensureWorksheet(token, wb, tabName)
        val rows = graphClient.readRange(token, wb, tabName)
        val firstRow = rows.firstOrNull()
        if (firstRow.isNullOrEmpty()) {
            graphClient.writeRange(token, wb, tabName, "A1", listOf(headers))
        } else {
            val existing = firstRow.map { it.trim() }.filter { it.isNotEmpty() }
            val merged = TabularSchema.mergeHeaderOrder(existing, headers)
            if (merged != existing) {
                graphClient.writeRange(token, wb, tabName, "A1", listOf(merged))
            }
        }
    }

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> {
        val token = accessToken(accountHint)
        return graphClient.readRange(token, workbookId(dest), tabName)
    }

    override suspend fun listTabTitles(dest: SpreadsheetDestination, accountHint: String?): List<String> {
        val token = accessToken(accountHint)
        return graphClient.listWorksheets(token, workbookId(dest))
    }

    override suspend fun renameTab(
        dest: SpreadsheetDestination,
        oldTitle: String,
        newTitle: String,
        accountHint: String?,
    ): Boolean {
        val token = accessToken(accountHint)
        return graphClient.renameWorksheet(token, workbookId(dest), oldTitle, newTitle)
    }

    override suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?) {
        val token = accessToken(accountHint)
        graphClient.deleteWorksheet(token, workbookId(dest), tabName)
    }

    override suspend fun appendRows(
        dest: SpreadsheetDestination,
        tabName: String,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        if (rows.isEmpty()) return
        val token = accessToken(accountHint)
        val wb = workbookId(dest)
        val existing = graphClient.readRange(token, wb, tabName)
        val startRow = (existing.size + 1).coerceAtLeast(2)
        graphClient.writeRange(token, wb, tabName, "A$startRow", rows)
    }

    override suspend fun updateRows(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        if (rows.isEmpty()) return
        val token = accessToken(accountHint)
        graphClient.writeRange(token, workbookId(dest), tabName, "A$startRow", rows)
    }

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) {
        val token = accessToken(accountHint)
        graphClient.clearFromRow(token, workbookId(dest), tabName, startRow)
    }

    override suspend fun writeAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        val token = accessToken(accountHint)
        val allRows = listOf(headers) + rows
        graphClient.writeRange(token, workbookId(dest), tabName, "A1", allRows)
    }

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult {
        val wb = workbookId(dest)
        if (wb.isBlank()) {
            return TabularTestResult(false, "Workbook not configured")
        }
        return try {
            val token = accessToken(accountHint)
            val ok = graphClient.testWorkbook(token, wb)
            TabularTestResult(ok, if (ok) "Connection test passed" else "Connection test failed")
        } catch (e: Exception) {
            TabularTestResult(false, e.message ?: "Excel connection test failed")
        }
    }
}