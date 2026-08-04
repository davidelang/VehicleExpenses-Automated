package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidelang.remotetable.Backends
import com.davidelang.remotetable.RemoteTable
import com.davidelang.remotetable.TabData
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularCapabilities
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Row-database tabular I/O via **remotetable** AAR ([RowDbBackend]).
 * Auth/config remains in-app ([RowDbTabularConfig]); HTTP lives in the library.
 */
abstract class RemoteTableRowDbTabularBackend(
    override val provider: SpreadsheetProvider,
    private val fallbackBackendType: String,
) : TabularShareBackend {

    override fun capabilities(): TabularCapabilities =
        TabularCapabilities(renameTab = false, incrementalWrite = true, browse = false)

    protected open fun parseConfig(dest: SpreadsheetDestination): RowDbTabularConfig? =
        RowDbTabularConfig.parse(
            dest.configJson,
            dest.targetUrl,
            fallbackBackendType,
        )

    override fun isConfigured(dest: SpreadsheetDestination): Boolean =
        RowDbTabularConfig.isConfigured(parseConfig(dest))

    override fun resolveAccountName(accountHint: String?): String? =
        accountHint?.takeIf { it.isNotBlank() } ?: "row-db"

    override fun resolveTargetId(dest: SpreadsheetDestination): String =
        parseConfig(dest)?.baseUrl.orEmpty()

    override fun parseTargetIdFromUrl(url: String): String? = url.trim().takeIf { it.isNotBlank() }

    override fun targetUrlFromId(id: String): String = id

    private fun requireConfig(dest: SpreadsheetDestination): RowDbTabularConfig =
        parseConfig(dest) ?: throw IllegalStateException("${provider.displayLabel()} not configured")

    private fun table(config: RowDbTabularConfig): RemoteTable {
        val kind = config.backendType.ifBlank { fallbackBackendType }
        return RemoteTable(
            Backends.rowDb(
                kind = kind,
                baseUrl = config.baseUrl,
                token = config.token,
                tables = config.tables,
                baseId = config.baseId,
            ),
        )
    }

    private fun TabData.toGrid(): List<List<String>> =
        if (headers.isEmpty() && rows.isEmpty()) emptyList()
        else listOf(headers) + rows

    private fun headersForTab(tabName: String): List<String> = when {
        tabName == TabularSchema.TAB_VEHICLES -> TabularSchema.VEHICLE_HEADERS
        tabName == TabularSchema.TAB_EXPENSES -> TabularSchema.EXPENSE_HEADERS
        tabName.startsWith(TabularSchema.FUEL_TAB_PREFIX) -> TabularSchema.FUEL_HEADERS
        else -> TabularSchema.VEHICLE_HEADERS
    }

    override suspend fun ensureHeaders(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        accountHint: String?,
    ) {
        withContext(Dispatchers.IO) {
            table(requireConfig(dest)).ensureHeaders(tabName, headers)
        }
    }

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> = withContext(Dispatchers.IO) {
        val config = requireConfig(dest)
        val data = table(config).readRows(tabName)
        if (data.headers.isEmpty() && data.rows.isEmpty()) {
            // Prefer schema headers when remote is empty so callers get a header row.
            listOf(headersForTab(tabName))
        } else {
            data.toGrid()
        }
    }

    override suspend fun listTabTitles(dest: SpreadsheetDestination, accountHint: String?): List<String> {
        val config = requireConfig(dest)
        val tabs = config.tables.keys.toMutableList()
        if (!tabs.contains(TabularSchema.TAB_VEHICLES)) tabs.add(0, TabularSchema.TAB_VEHICLES)
        if (!tabs.contains(TabularSchema.TAB_EXPENSES)) tabs.add(1.coerceAtMost(tabs.size), TabularSchema.TAB_EXPENSES)
        return tabs.distinct()
    }

    override suspend fun renameTab(
        dest: SpreadsheetDestination,
        oldTitle: String,
        newTitle: String,
        accountHint: String?,
    ): Boolean = false

    override suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?) {
        withContext(Dispatchers.IO) {
            table(requireConfig(dest)).deleteTab(tabName)
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
            val headers = headersForTab(tabName)
            table(requireConfig(dest)).writeRows(tabName, headers, rows, mode = "append")
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
        // Row-db upsert is by Sync ID; treat update as append upsert.
        appendRows(dest, tabName, rows, accountHint)
    }

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) {
        withContext(Dispatchers.IO) {
            val rt = table(requireConfig(dest))
            val data = rt.readRows(tabName)
            val keep = (startRow - 2).coerceAtLeast(0)
            val kept = data.rows.take(keep)
            val headers = data.headers.ifEmpty { headersForTab(tabName) }
            rt.writeRows(tabName, headers, kept, mode = "replace")
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
            table(requireConfig(dest)).writeRows(tabName, headers, rows, mode = "replace")
        }
    }

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult {
        val config = parseConfig(dest)
            ?: return TabularTestResult(false, "${provider.displayLabel()} base URL and token required")
        if (config.token.isBlank()) {
            return TabularTestResult(false, "API token required")
        }
        if (config.tables.isEmpty()) {
            return TabularTestResult(false, "Map at least one table id (Vehicles recommended)")
        }
        val httpWarn = RowDbTabularConfig.httpWarning(config.baseUrl)
        return withContext(Dispatchers.IO) {
            try {
                val result = table(config).testConnection()
                val ok = result["ok"] as? Boolean ?: false
                val msg = result["message"]?.toString().orEmpty()
                if (!ok) {
                    TabularTestResult(false, msg.ifBlank { "Connection test failed" })
                } else {
                    val message = buildString {
                        append(msg.ifBlank { "Connection test passed" })
                        httpWarn?.let { append(" — $it") }
                    }
                    TabularTestResult(true, message)
                }
            } catch (e: Exception) {
                TabularTestResult(false, e.message ?: "Connection test failed")
            }
        }
    }
}
