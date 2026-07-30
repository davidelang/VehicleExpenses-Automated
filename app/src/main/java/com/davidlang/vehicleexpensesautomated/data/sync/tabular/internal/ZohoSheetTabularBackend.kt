package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.ZohoSheetAuth
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularCapabilities
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularTestResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZohoSheetTabularBackend @Inject constructor(
    private val client: ZohoSheetClient,
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
        val openRegex = Regex("""/open/([a-zA-Z0-9]+)""")
        openRegex.find(trimmed)?.groupValues?.get(1)?.let { return it }
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

    override suspend fun ensureHeaders(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        accountHint: String?,
    ) {
        val config = resolvedConfig(dest)
        val sheet = worksheetForTab(config, tabName)
        client.ensureWorksheet(config, sheet)
        val rows = client.readAllRows(config, sheet)
        if (rows.isEmpty() || rows.first().isEmpty()) {
            client.writeAllRows(config, sheet, listOf(headers))
        } else {
            val existing = rows.first().map { it.trim() }.filter { it.isNotEmpty() }
            val merged = TabularSchema.mergeHeaderOrder(existing, headers)
            if (merged != existing) {
                val dataRows = rows.drop(1)
                client.writeAllRows(config, sheet, listOf(merged) + dataRows)
            }
        }
    }

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> {
        val config = resolvedConfig(dest)
        return client.readAllRows(config, worksheetForTab(config, tabName))
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
    ): Boolean {
        val config = resolvedConfig(dest)
        val oldSheet = config.sheetForTab(oldTitle) ?: oldTitle
        val newSheet = config.sheetForTab(newTitle) ?: newTitle
        return client.renameWorksheet(config, oldSheet, newSheet)
    }

    override suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?) {
        val config = resolvedConfig(dest)
        client.deleteWorksheet(config, worksheetForTab(config, tabName))
    }

    override suspend fun appendRows(
        dest: SpreadsheetDestination,
        tabName: String,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        if (rows.isEmpty()) return
        val config = resolvedConfig(dest)
        val sheet = worksheetForTab(config, tabName)
        val existing = client.readAllRows(config, sheet)
        val header = existing.firstOrNull().orEmpty()
        val data = existing.drop(1) + rows
        client.writeAllRows(config, sheet, if (header.isEmpty()) data else listOf(header) + data)
    }

    override suspend fun updateRows(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        if (rows.isEmpty()) return
        val config = resolvedConfig(dest)
        val sheet = worksheetForTab(config, tabName)
        val existing = client.readAllRows(config, sheet).toMutableList()
        if (existing.isEmpty()) {
            client.writeAllRows(config, sheet, rows)
            return
        }
        val header = existing.first()
        val data = existing.drop(1).toMutableList()
        val zeroBased = (startRow - 2).coerceAtLeast(0)
        rows.forEachIndexed { i, row ->
            val idx = zeroBased + i
            if (idx < data.size) {
                data[idx] = row
            } else {
                while (data.size < idx) data.add(emptyList())
                data.add(row)
            }
        }
        client.writeAllRows(config, sheet, listOf(header) + data)
    }

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) {
        val config = resolvedConfig(dest)
        val sheet = worksheetForTab(config, tabName)
        val existing = client.readAllRows(config, sheet)
        if (existing.isEmpty()) return
        val header = existing.first()
        val keepCount = (startRow - 1).coerceAtLeast(1)
        val trimmed = existing.take(keepCount)
        client.writeAllRows(config, sheet, trimmed.ifEmpty { listOf(header) })
    }

    override suspend fun writeAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        val config = resolvedConfig(dest)
        client.writeAllRows(config, worksheetForTab(config, tabName), listOf(headers) + rows)
    }

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult {
        val config = parseConfig(dest)
            ?: return TabularTestResult(false, "Workbook id and OAuth token required")
        if (config.accessToken.isBlank()) {
            return TabularTestResult(false, "Sign in with Zoho or paste an access token")
        }
        val sheetName = config.sheetForTab(TabularSchema.TAB_VEHICLES)
            ?: config.sheets.values.firstOrNull()
            ?: return TabularTestResult(false, "Map at least one worksheet (Vehicles recommended)")
        return try {
            val resolved = resolvedConfig(dest)
            val probeSyncId = ".ve_probe_${System.currentTimeMillis()}"
            val headers = TabularSchema.VEHICLE_HEADERS
            val probeRow = headers.map { header ->
                when (header) {
                    RowDbTabularConfig.FIELD_SYNC_ID -> probeSyncId
                    "Name" -> "VE probe"
                    else -> ""
                }
            }
            client.ensureWorksheet(resolved, sheetName)
            val before = client.readAllRows(resolved, sheetName)
            val header = before.firstOrNull()?.takeIf { it.isNotEmpty() } ?: headers
            val data = before.drop(1) + listOf(probeRow)
            client.writeAllRows(resolved, sheetName, listOf(header) + data)
            val after = client.readAllRows(resolved, sheetName)
            val syncIndex = header.indexOf(RowDbTabularConfig.FIELD_SYNC_ID)
            val found = after.drop(1).any { row ->
                row.getOrElse(syncIndex) { "" } == probeSyncId
            }
            if (!found) {
                return TabularTestResult(false, "Probe row not visible after write")
            }
            val cleaned = after.drop(1).filter { row ->
                row.getOrElse(syncIndex) { "" } != probeSyncId
            }
            client.writeAllRows(resolved, sheetName, listOf(header) + cleaned)
            TabularTestResult(true, "Connection test passed")
        } catch (e: Exception) {
            TabularTestResult(false, e.message ?: "Connection test failed")
        }
    }
}