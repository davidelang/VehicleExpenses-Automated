package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularCapabilities
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared [TabularShareBackend] implementation for row-database providers. */
abstract class RowDbTabularBackend(
    private val client: RowDbTableClient,
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

    private fun requireTableId(config: RowDbTabularConfig, tabName: String): String =
        config.tableIdForTab(tabName)
            ?: throw IllegalStateException("Table id not mapped for tab \"$tabName\"")

    private fun rowToFieldMap(headers: List<String>, row: List<String>): Map<String, String> =
        headers.mapIndexed { index, header ->
            header to row.getOrElse(index) { "" }
        }.toMap()

    private fun fieldMapToRow(headers: List<String>, fields: Map<String, String>): List<String> =
        headers.map { header -> fields[header].orEmpty() }

    private suspend fun loadRows(
        config: RowDbTabularConfig,
        tabName: String,
        headers: List<String>,
    ): Pair<List<String>, MutableList<List<String>>> = withContext(Dispatchers.IO) {
        val tableId = requireTableId(config, tabName)
        val remote = client.listFieldMaps(config, tableId)
        val dataRows = remote.map { (_, fields) -> fieldMapToRow(headers, fields) }.toMutableList()
        val headerRow = if (dataRows.isEmpty()) headers else headers
        headerRow to dataRows
    }

    override suspend fun ensureHeaders(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        accountHint: String?,
    ) {
        val config = requireConfig(dest)
        val tableId = requireTableId(config, tabName)
        // v1: tables/fields must be pre-created in the remote UI; probe list only.
        client.listFieldMaps(config, tableId)
    }

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> = withContext(Dispatchers.IO) {
        val config = requireConfig(dest)
        val headers = headersForTab(tabName)
        val (_, dataRows) = loadRows(config, tabName, headers)
        listOf(headers) + dataRows
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
        val config = requireConfig(dest)
        val tableId = requireTableId(config, tabName)
        val headers = headersForTab(tabName)
        val remote = client.listFieldMaps(config, tableId)
        remote.forEach { (rowId, _) -> client.deleteRow(config, tableId, rowId) }
    }

    override suspend fun appendRows(
        dest: SpreadsheetDestination,
        tabName: String,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        if (rows.isEmpty()) return
        val config = requireConfig(dest)
        val headers = headersForTab(tabName)
        upsertRows(config, tabName, headers, rows)
    }

    override suspend fun updateRows(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        if (rows.isEmpty()) return
        val config = requireConfig(dest)
        val headers = headersForTab(tabName)
        val tableId = requireTableId(config, tabName)
        val remote = client.listFieldMaps(config, tableId)
        val syncIndex = headers.indexOf(RowDbTabularConfig.FIELD_SYNC_ID)
        val remoteBySyncId = remote.associate { (rowId, fields) ->
            val syncId = fields[RowDbTabularConfig.FIELD_SYNC_ID].orEmpty() to rowId
            syncId
        }
        rows.forEach { row ->
            val syncId = row.getOrElse(syncIndex) { "" }.trim()
            if (syncId.isBlank()) {
                client.createRow(config, tableId, headers, row)
            } else {
                val existingId = remoteBySyncId[syncId]
                if (existingId != null) {
                    client.updateRow(config, tableId, existingId, headers, row)
                } else {
                    client.createRow(config, tableId, headers, row)
                }
            }
        }
    }

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) {
        val config = requireConfig(dest)
        val headers = headersForTab(tabName)
        val tableId = requireTableId(config, tabName)
        val remote = client.listFieldMaps(config, tableId)
        val keepCount = (startRow - 2).coerceAtLeast(0)
        remote.drop(keepCount).forEach { (rowId, _) ->
            client.deleteRow(config, tableId, rowId)
        }
    }

    override suspend fun writeAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        val config = requireConfig(dest)
        upsertRows(config, tabName, headers, rows)
        val tableId = requireTableId(config, tabName)
        val syncIndex = headers.indexOf(RowDbTabularConfig.FIELD_SYNC_ID)
        val keepSyncIds = rows.mapNotNull { row ->
            row.getOrElse(syncIndex) { "" }.trim().takeIf { it.isNotBlank() }
        }.toSet()
        val remote = client.listFieldMaps(config, tableId)
        remote.forEach { (rowId, fields) ->
            val syncId = fields[RowDbTabularConfig.FIELD_SYNC_ID].orEmpty().trim()
            if (syncId.isNotBlank() && syncId !in keepSyncIds) {
                client.deleteRow(config, tableId, rowId)
            }
        }
    }

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult {
        val config = parseConfig(dest)
            ?: return TabularTestResult(false, "${provider.displayLabel()} base URL and token required")
        if (config.token.isBlank()) {
            return TabularTestResult(false, "API token required")
        }
        val tableId = config.tableIdForTab(TabularSchema.TAB_VEHICLES)
            ?: config.tables.values.firstOrNull()
            ?: return TabularTestResult(false, "Map at least one table id (Vehicles recommended)")
        val httpWarn = RowDbTabularConfig.httpWarning(config.baseUrl)
        return try {
            val probeSyncId = ".ve_probe_${System.currentTimeMillis()}"
            val headers = TabularSchema.VEHICLE_HEADERS
            val probeRow = headers.map { header ->
                when (header) {
                    RowDbTabularConfig.FIELD_SYNC_ID -> probeSyncId
                    "Name" -> "VE probe"
                    else -> ""
                }
            }
            val rowId = client.createRow(config, tableId, headers, probeRow)
            val listed = client.listFieldMaps(config, tableId)
            val found = listed.any { (_, fields) ->
                fields[RowDbTabularConfig.FIELD_SYNC_ID] == probeSyncId
            }
            if (!found) {
                return TabularTestResult(false, "Probe row not visible after create")
            }
            try {
                client.deleteRow(config, tableId, rowId)
            } catch (_: Exception) {
                // best-effort delete
            }
            val message = buildString {
                append("Connection test passed")
                httpWarn?.let { append(" — $it") }
            }
            TabularTestResult(true, message)
        } catch (e: Exception) {
            TabularTestResult(false, e.message ?: "Connection test failed")
        }
    }

    private suspend fun upsertRows(
        config: RowDbTabularConfig,
        tabName: String,
        headers: List<String>,
        rows: List<List<String>>,
    ) {
        val tableId = requireTableId(config, tabName)
        val remote = client.listFieldMaps(config, tableId)
        val remoteBySyncId = remote.associate { (rowId, fields) ->
            fields[RowDbTabularConfig.FIELD_SYNC_ID].orEmpty().trim() to rowId
        }
        val syncIndex = headers.indexOf(RowDbTabularConfig.FIELD_SYNC_ID)
        rows.forEach { row ->
            val syncId = row.getOrElse(syncIndex) { "" }.trim()
            if (syncId.isBlank()) {
                client.createRow(config, tableId, headers, row)
            } else {
                val existingId = remoteBySyncId[syncId]
                if (existingId != null) {
                    client.updateRow(config, tableId, existingId, headers, row)
                } else {
                    client.createRow(config, tableId, headers, row)
                }
            }
        }
    }

    private fun headersForTab(tabName: String): List<String> = when {
        tabName == TabularSchema.TAB_VEHICLES -> TabularSchema.VEHICLE_HEADERS
        tabName == TabularSchema.TAB_EXPENSES -> TabularSchema.EXPENSE_HEADERS
        tabName.startsWith(TabularSchema.FUEL_TAB_PREFIX) -> TabularSchema.FUEL_HEADERS
        else -> TabularSchema.VEHICLE_HEADERS
    }
}