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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EtherCalc via **remotetable** AAR (one room per logical tab).
 * Config parse + room naming still use [EtherCalcClient] helpers.
 */
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

    private fun table(dest: SpreadsheetDestination, tabName: String): RemoteTable {
        val cfg = config(dest)
        val room = etherCalcClient.roomForTab(cfg, tabName)
        return RemoteTable(Backends.ethercalc(cfg.baseUrl, room))
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
            val rt = table(dest, tabName)
            val rows = rt.readRows(tabName)
            if (rows.headers.isEmpty()) {
                rt.writeRows(tabName, headers, emptyList(), mode = "replace")
            } else {
                val existing = rows.headers.map { it.trim() }.filter { it.isNotEmpty() }
                val merged = TabularSchema.mergeHeaderOrder(existing, headers)
                if (merged != existing) {
                    val dataRows = TabularSchema.padDataRowsToWidth(rows.rows, merged.size)
                    rt.writeRows(tabName, merged, dataRows, mode = "replace")
                }
            }
        }
    }

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> = withContext(Dispatchers.IO) {
        table(dest, tabName).readRows(tabName).toGrid()
    }

    override suspend fun listTabTitles(dest: SpreadsheetDestination, accountHint: String?): List<String> {
        return listOf(TabularSchema.TAB_VEHICLES, TabularSchema.TAB_EXPENSES)
    }

    override suspend fun renameTab(
        dest: SpreadsheetDestination,
        oldTitle: String,
        newTitle: String,
        accountHint: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        // Copy via read/write on distinct rooms (EtherCalc has no rename).
        val cfg = config(dest)
        val oldRoom = etherCalcClient.roomForTab(cfg, oldTitle)
        val newRoom = etherCalcClient.roomForTab(cfg, newTitle)
        if (oldRoom == newRoom) return@withContext true
        val oldData = RemoteTable(Backends.ethercalc(cfg.baseUrl, oldRoom)).readRows(oldTitle)
        if (oldData.headers.isEmpty() && oldData.rows.isEmpty()) return@withContext true
        val newRt = RemoteTable(Backends.ethercalc(cfg.baseUrl, newRoom))
        val existingNew = newRt.readRows(newTitle)
        if (existingNew.headers.isNotEmpty() || existingNew.rows.isNotEmpty()) return@withContext false
        newRt.writeRows(newTitle, oldData.headers, oldData.rows, mode = "replace")
        true
    }

    override suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?) {
        // Best-effort: clear room contents via replace empty.
        withContext(Dispatchers.IO) {
            table(dest, tabName).writeRows(tabName, emptyList(), emptyList(), mode = "replace")
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
            val rt = table(dest, tabName)
            val existing = rt.readRows(tabName)
            val headers = existing.headers
            rt.writeRows(tabName, headers.ifEmpty { listOf() }, rows, mode = "append")
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
            val rt = table(dest, tabName)
            val existing = rt.readRows(tabName)
            if (existing.headers.isEmpty() && existing.rows.isEmpty()) {
                rt.writeRows(tabName, rows.first(), rows.drop(1), mode = "replace")
                return@withContext
            }
            val header = existing.headers
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
            rt.writeRows(tabName, header, data, mode = "replace")
        }
    }

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) {
        withContext(Dispatchers.IO) {
            table(dest, tabName).clearFromRow(tabName, startRow)
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
            table(dest, tabName).writeRows(tabName, headers, rows, mode = "replace")
        }
    }

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult {
        val cfg = etherCalcClient.parseConfig(dest.configJson, dest.targetUrl, dest.targetId)
            ?: return TabularTestResult(false, "EtherCalc base URL not configured")
        return try {
            val conn = withContext(Dispatchers.IO) {
                val room = etherCalcClient.roomForTab(cfg, "sync-test")
                RemoteTable(Backends.ethercalc(cfg.baseUrl, room)).testConnection()
            }
            val ok = conn["ok"] == true
            TabularTestResult(
                ok,
                conn["message"]?.toString()
                    ?: if (ok) "Connection test passed" else "Connection test failed",
            )
        } catch (e: Exception) {
            TabularTestResult(false, e.message ?: "EtherCalc connection test failed")
        }
    }
}
