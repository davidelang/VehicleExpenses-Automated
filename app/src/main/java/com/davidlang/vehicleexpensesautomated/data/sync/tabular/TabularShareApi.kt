package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination

/** App-facing façade for all tabular provider I/O (sync, test, CSV zip). */
interface TabularShareApi {
    fun backendFor(dest: SpreadsheetDestination): TabularShareBackend

    suspend fun testConnection(dest: SpreadsheetDestination): TabularTestResult

    suspend fun exportCsvZip(target: CsvZipTarget, request: TabularExportRequest = TabularExportRequest()): TabularExportResult

    suspend fun importCsvZip(source: CsvZipSource, request: TabularImportRequest = TabularImportRequest()): TabularImportResult
}