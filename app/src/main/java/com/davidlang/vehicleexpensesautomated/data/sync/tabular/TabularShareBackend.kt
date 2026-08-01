package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider

/** Provider-specific tabular I/O port; app coordinator calls only via [TabularShareApi]. */
interface TabularShareBackend {
    val provider: SpreadsheetProvider

    fun capabilities(): TabularCapabilities

    fun isConfigured(dest: SpreadsheetDestination): Boolean

    fun resolveAccountName(accountHint: String?): String?

    fun resolveTargetId(dest: SpreadsheetDestination): String

    fun parseTargetIdFromUrl(url: String): String?

    fun targetUrlFromId(id: String): String

    suspend fun ensureHeaders(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        accountHint: String?,
    )

    suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>>

    /**
     * Bulk compare read for LWW. Default: sequential [readAllRows] (non-Sheets backends).
     * Google Sheets overrides with `values.batchGet` (few API calls for many tabs).
     */
    suspend fun batchReadTabs(
        dest: SpreadsheetDestination,
        tabNames: List<String>,
        accountHint: String?,
    ): Map<String, List<List<String>>> {
        val names = tabNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) return emptyMap()
        return names.associateWith { readAllRows(dest, it, accountHint) }
    }

    suspend fun listTabTitles(dest: SpreadsheetDestination, accountHint: String?): List<String>

    suspend fun renameTab(
        dest: SpreadsheetDestination,
        oldTitle: String,
        newTitle: String,
        accountHint: String?,
    ): Boolean

    suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?)

    suspend fun appendRows(
        dest: SpreadsheetDestination,
        tabName: String,
        rows: List<List<String>>,
        accountHint: String?,
    )

    suspend fun updateRows(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        rows: List<List<String>>,
        accountHint: String?,
    )

    suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    )

    suspend fun writeAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        rows: List<List<String>>,
        accountHint: String?,
    )

    suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult
}