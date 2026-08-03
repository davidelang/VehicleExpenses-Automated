package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidelang.remotetable.Backends
import com.davidelang.remotetable.RemoteTable
import com.davidelang.remotetable.TabData
import com.davidlang.vehicleexpensesautomated.data.sync.MicrosoftOneDriveAuth
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularCapabilities
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Excel Online (excel-graph) via **remotetable** AAR.
 * MSAL token still from [MicrosoftOneDriveAuth].
 */
@Singleton
class ExcelGraphTabularBackend @Inject constructor(
    private val msAuth: MicrosoftOneDriveAuth,
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

    private suspend fun table(dest: SpreadsheetDestination, accountHint: String?): RemoteTable =
        withContext(Dispatchers.IO) {
            val token = accessToken(accountHint)
            RemoteTable(Backends.excelGraph(token, resolveTargetId(dest)))
        }

    private fun TabData.toGrid(): List<List<String>> =
        if (headers.isEmpty() && rows.isEmpty()) emptyList()
        else listOf(headers) + rows

    override suspend fun ensureHeaders(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        accountHint: String?,
    ) {
        withContext(Dispatchers.IO) {
            table(dest, accountHint).ensureHeaders(tabName, headers)
        }
    }

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> = withContext(Dispatchers.IO) {
        table(dest, accountHint).readRows(tabName).toGrid()
    }

    override suspend fun listTabTitles(dest: SpreadsheetDestination, accountHint: String?): List<String> =
        withContext(Dispatchers.IO) {
            table(dest, accountHint).listTabs()
        }

    override suspend fun renameTab(
        dest: SpreadsheetDestination,
        oldTitle: String,
        newTitle: String,
        accountHint: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        table(dest, accountHint).renameTab(oldTitle, newTitle)
    }

    override suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?) {
        withContext(Dispatchers.IO) {
            table(dest, accountHint).deleteTab(tabName)
        }
    }

    override suspend fun appendRows(
        dest: SpreadsheetDestination,
        tabName: String,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        if (rows.isEmpty()) return
        withContext(Dispatchers.IO) {
            val rt = table(dest, accountHint)
            val existing = rt.readRows(tabName)
            val headers = existing.headers.ifEmpty {
                List(rows.maxOfOrNull { it.size } ?: 0) { "Col$it" }
            }
            rt.writeRows(tabName, headers, rows, mode = "append")
        }
    }

    override suspend fun updateRows(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        if (rows.isEmpty()) return
        withContext(Dispatchers.IO) {
            val rt = table(dest, accountHint)
            val existing = rt.readRows(tabName)
            val headers = existing.headers
            val data = existing.rows.toMutableList()
            val zeroBased = (startRow - 2).coerceAtLeast(0)
            rows.forEachIndexed { i, row ->
                val idx = zeroBased + i
                if (idx < data.size) data[idx] = row
                else {
                    while (data.size < idx) data.add(emptyList())
                    data.add(row)
                }
            }
            rt.writeRows(tabName, headers.ifEmpty { rows.first() }, data, mode = "replace")
        }
    }

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) {
        withContext(Dispatchers.IO) {
            table(dest, accountHint).clearFromRow(tabName, startRow)
        }
    }

    override suspend fun writeAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        withContext(Dispatchers.IO) {
            table(dest, accountHint).writeRows(tabName, headers, rows, mode = "replace")
        }
    }

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult {
        val wb = resolveTargetId(dest)
        if (wb.isBlank()) {
            return TabularTestResult(false, "Workbook not configured")
        }
        return try {
            val conn = withContext(Dispatchers.IO) {
                table(dest, accountHint).testConnection()
            }
            val ok = conn["ok"] == true
            TabularTestResult(
                ok,
                conn["message"]?.toString()
                    ?: if (ok) "Connection test passed" else "Connection test failed",
            )
        } catch (e: Exception) {
            TabularTestResult(false, e.message ?: "Excel connection test failed")
        }
    }
}
