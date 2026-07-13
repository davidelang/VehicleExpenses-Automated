package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.CsvZipTabularBackend
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabularShareApiImpl @Inject constructor(
    private val registry: TabularBackendRegistry,
    private val csvZipBackend: CsvZipTabularBackend,
) : TabularShareApi {

    override fun backendFor(dest: SpreadsheetDestination): TabularShareBackend =
        registry.forDestination(dest)
            ?: throw IllegalStateException("No tabular backend for provider ${dest.provider}")

    override suspend fun testConnection(dest: SpreadsheetDestination): TabularTestResult {
        val backend = backendFor(dest)
        return backend.testConnection(dest, dest.accountHint.ifBlank { null })
    }

    override suspend fun exportCsvZip(target: CsvZipTarget, request: TabularExportRequest): TabularExportResult =
        csvZipBackend.exportZip(target, request)

    override suspend fun importCsvZip(source: CsvZipSource, request: TabularImportRequest): TabularImportResult =
        csvZipBackend.importZip(source, request)
}