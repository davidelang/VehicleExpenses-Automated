package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularCapabilities
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularTestResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EtherCalcTabularBackend @Inject constructor(
    private val etherCalcClient: EtherCalcClient,
) : TabularShareBackend {

    override val provider: SpreadsheetProvider = SpreadsheetProvider.ETHERCALC

    override fun capabilities(): TabularCapabilities =
        TabularCapabilities(renameTab = false, incrementalWrite = true, browse = false)

    override fun isConfigured(dest: SpreadsheetDestination): Boolean =
        etherCalcClient.parseConfig(dest.configJson, dest.targetUrl, dest.targetId) != null

    override fun resolveAccountName(accountHint: String?): String? = "ethercalc"

    override fun resolveTargetId(dest: SpreadsheetDestination): String =
        etherCalcClient.parseConfig(dest.configJson, dest.targetUrl, dest.targetId)?.baseUrl.orEmpty()

    override fun parseTargetIdFromUrl(url: String): String? = url.trim().takeIf { it.isNotBlank() }

    override fun targetUrlFromId(id: String): String = id

    private fun config(dest: SpreadsheetDestination): EtherCalcClient.Config =
        etherCalcClient.parseConfig(dest.configJson, dest.targetUrl, dest.targetId)
            ?: throw IllegalStateException("EtherCalc base URL not configured")

    override suspend fun ensureHeaders(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        accountHint: String?,
    ) {
        val cfg = config(dest)
        etherCalcClient.ensureRoom(cfg.baseUrl, etherCalcClient.roomForTab(cfg, tabName))
        val rows = etherCalcClient.readAllRows(cfg, tabName)
        if (rows.isEmpty() || rows.first().isEmpty()) {
            etherCalcClient.writeAllRows(cfg, tabName, headers, emptyList())
        } else {
            val existing = rows.first().map { it.trim() }.filter { it.isNotEmpty() }
            val merged = TabularSchema.mergeHeaderOrder(existing, headers)
            if (merged != existing) {
                // Append missing header names only; keep data column order; pad new cols.
                val dataRows = TabularSchema.padDataRowsToWidth(rows.drop(1), merged.size)
                etherCalcClient.writeAllRows(cfg, tabName, merged, dataRows)
            }
        }
    }

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> = etherCalcClient.readAllRows(config(dest), tabName)

    override suspend fun listTabTitles(dest: SpreadsheetDestination, accountHint: String?): List<String> {
        // EtherCalc uses room-per-tab; logical tabs are derived from schema conventions.
        return listOf(TabularSchema.TAB_VEHICLES, TabularSchema.TAB_EXPENSES)
    }

    override suspend fun renameTab(
        dest: SpreadsheetDestination,
        oldTitle: String,
        newTitle: String,
        accountHint: String?,
    ): Boolean = etherCalcClient.renameRoom(config(dest), oldTitle, newTitle)

    override suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?) {
        etherCalcClient.deleteRoom(config(dest), tabName)
    }

    override suspend fun appendRows(
        dest: SpreadsheetDestination,
        tabName: String,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        if (rows.isEmpty()) return
        val cfg = config(dest)
        val existing = etherCalcClient.readAllRows(cfg, tabName)
        val header = existing.firstOrNull().orEmpty()
        val data = existing.drop(1) + rows
        etherCalcClient.writeAllRows(cfg, tabName, header.ifEmpty { listOf() }, data)
    }

    override suspend fun updateRows(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        if (rows.isEmpty()) return
        val cfg = config(dest)
        val existing = etherCalcClient.readAllRows(cfg, tabName).toMutableList()
        if (existing.isEmpty()) {
            etherCalcClient.writeAllRows(cfg, tabName, rows.first(), rows.drop(1))
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
        etherCalcClient.writeAllRows(cfg, tabName, header, data)
    }

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) {
        val cfg = config(dest)
        val existing = etherCalcClient.readAllRows(cfg, tabName)
        if (existing.isEmpty()) return
        val header = existing.first()
        val keepCount = (startRow - 2).coerceAtLeast(0)
        val data = existing.drop(1).take(keepCount)
        etherCalcClient.writeAllRows(cfg, tabName, header, data)
    }

    override suspend fun writeAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        etherCalcClient.writeAllRows(config(dest), tabName, headers, rows)
    }

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult {
        val cfg = etherCalcClient.parseConfig(dest.configJson, dest.targetUrl, dest.targetId)
            ?: return TabularTestResult(false, "EtherCalc base URL not configured")
        return try {
            val ok = etherCalcClient.testConnection(cfg)
            TabularTestResult(ok, if (ok) "Connection test passed" else "Connection test failed")
        } catch (e: Exception) {
            TabularTestResult(false, e.message ?: "EtherCalc connection test failed")
        }
    }
}