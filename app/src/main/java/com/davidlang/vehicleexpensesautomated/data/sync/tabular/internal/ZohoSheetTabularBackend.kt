package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidelang.remotetable.Backends
import com.davidelang.remotetable.RemoteTable
import com.davidelang.remotetable.TabData
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.ZohoSheetAuth
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularCapabilities
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zoho Sheet tabular I/O via **remotetable** AAR.
 * OAuth refresh stays in-app ([ZohoSheetAuth]).
 */
@Singleton
class ZohoSheetTabularBackend @Inject constructor(
    private val zohoAuth: ZohoSheetAuth,
) : TabularShareBackend {

    override val provider: SpreadsheetProvider = SpreadsheetProvider.ZOHO_SHEET

    override fun capabilities(): TabularCapabilities =
        TabularCapabilities(renameTab = true, incrementalWrite = true, browse = false)

    override fun isConfigured(dest: SpreadsheetDestination): Boolean =
        ZohoSheetConfig.isConfigured(parseConfig(dest))

    override fun resolveAccountName(accountHint: String?): String? =
        accountHint?.takeIf { it.isNotBlank() } ?: "zoho"

    override fun resolveTargetId(dest: SpreadsheetDestination): String =
        parseConfig(dest)?.workbookId.orEmpty()

    override fun parseTargetIdFromUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        Regex("""/open/([a-zA-Z0-9]+)""").find(trimmed)?.groupValues?.get(1)?.let { return it }
        return trimmed.takeIf { it.matches(Regex("""[a-zA-Z0-9]{8,}""")) }
    }

    override fun targetUrlFromId(id: String): String =
        "https://sheet.zoho.com/sheet/open/$id"

    private fun parseConfig(dest: SpreadsheetDestination): ZohoSheetConfig? =
        ZohoSheetConfig.parse(dest.configJson, dest.targetId)

    private suspend fun resolvedConfig(dest: SpreadsheetDestination): ZohoSheetConfig {
        val base = parseConfig(dest) ?: throw IllegalStateException("Zoho Sheet not configured")
        val refreshed = zohoAuth.refreshAccessToken(base)
        return refreshed?.let { base.withTokens(it.accessToken, it.refreshToken, it.apiDomain) } ?: base
    }

    private fun worksheetForTab(config: ZohoSheetConfig, tabName: String): String =
        config.sheetForTab(tabName)
            ?: throw IllegalStateException("Worksheet not mapped for tab \"$tabName\"")

    private fun table(config: ZohoSheetConfig): RemoteTable =
        RemoteTable(
            Backends.zohoSheet(
                accessToken = config.accessToken,
                workbookId = config.workbookId,
                apiDomain = config.apiDomain,
                sheets = config.sheets,
            ),
        )

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
            val config = resolvedConfig(dest)
            // Library uses sheet map; ensure tab key maps before call
            val sheet = worksheetForTab(config, tabName)
            val cfg = config.copy(sheets = config.sheets + (tabName to sheet))
            table(cfg).ensureHeaders(tabName, headers)
        }
    }

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> = withContext(Dispatchers.IO) {
        val config = resolvedConfig(dest)
        val sheet = worksheetForTab(config, tabName)
        table(config.copy(sheets = config.sheets + (tabName to sheet))).readRows(tabName).toGrid()
    }

    override suspend fun listTabTitles(dest: SpreadsheetDestination, accountHint: String?): List<String> {
        val config = resolvedConfig(dest)
        val mapped = config.sheets.keys.toMutableList()
        if (!mapped.contains(TabularSchema.TAB_VEHICLES)) mapped.add(0, TabularSchema.TAB_VEHICLES)
        if (!mapped.contains(TabularSchema.TAB_EXPENSES)) {
            mapped.add(1.coerceAtMost(mapped.size), TabularSchema.TAB_EXPENSES)
        }
        return mapped.distinct()
    }

    override suspend fun renameTab(
        dest: SpreadsheetDestination,
        oldTitle: String,
        newTitle: String,
        accountHint: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        val config = resolvedConfig(dest)
        table(config).renameTab(
            config.sheetForTab(oldTitle) ?: oldTitle,
            config.sheetForTab(newTitle) ?: newTitle,
        )
    }

    override suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?) {
        withContext(Dispatchers.IO) {
            val config = resolvedConfig(dest)
            val sheet = worksheetForTab(config, tabName)
            table(config.copy(sheets = config.sheets + (tabName to sheet))).deleteTab(tabName)
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
            val config = resolvedConfig(dest)
            val sheet = worksheetForTab(config, tabName)
            val rt = table(config.copy(sheets = config.sheets + (tabName to sheet)))
            val data = rt.readRows(tabName)
            val headers = data.headers.ifEmpty { TabularSchema.VEHICLE_HEADERS }
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
            val config = resolvedConfig(dest)
            val sheet = worksheetForTab(config, tabName)
            val rt = table(config.copy(sheets = config.sheets + (tabName to sheet)))
            val data = rt.readRows(tabName)
            val headers = data.headers.ifEmpty { rows.firstOrNull()?.let { List(it.size) { i -> "C$i" } }.orEmpty() }
            val body = data.rows.toMutableList()
            val zeroBased = (startRow - 2).coerceAtLeast(0)
            rows.forEachIndexed { i, row ->
                val idx = zeroBased + i
                if (idx < body.size) body[idx] = row
                else {
                    while (body.size < idx) body.add(emptyList())
                    body.add(row)
                }
            }
            rt.writeRows(tabName, headers, body, mode = "replace")
        }
    }

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) {
        withContext(Dispatchers.IO) {
            val config = resolvedConfig(dest)
            val sheet = worksheetForTab(config, tabName)
            val rt = table(config.copy(sheets = config.sheets + (tabName to sheet)))
            val data = rt.readRows(tabName)
            if (data.headers.isEmpty() && data.rows.isEmpty()) return@withContext
            val keep = (startRow - 2).coerceAtLeast(0)
            rt.writeRows(tabName, data.headers, data.rows.take(keep), mode = "replace")
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
            val config = resolvedConfig(dest)
            val sheet = worksheetForTab(config, tabName)
            table(config.copy(sheets = config.sheets + (tabName to sheet)))
                .writeRows(tabName, headers, rows, mode = "replace")
        }
    }

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult {
        val config = parseConfig(dest)
            ?: return TabularTestResult(false, "Workbook id and OAuth token required")
        if (config.accessToken.isBlank()) {
            return TabularTestResult(false, "Sign in with Zoho or paste an access token")
        }
        if (config.sheets.isEmpty()) {
            return TabularTestResult(false, "Map at least one worksheet (Vehicles recommended)")
        }
        return withContext(Dispatchers.IO) {
            try {
                val resolved = resolvedConfig(dest)
                val result = table(resolved).testConnection()
                val ok = result["ok"] as? Boolean ?: false
                val msg = result["message"]?.toString().orEmpty()
                if (!ok) TabularTestResult(false, msg.ifBlank { "Connection test failed" })
                else TabularTestResult(true, msg.ifBlank { "Connection test passed" })
            } catch (e: Exception) {
                TabularTestResult(false, e.message ?: "Connection test failed")
            }
        }
    }
}
