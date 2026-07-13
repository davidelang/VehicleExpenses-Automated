package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularCapabilities
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularTestResult
import javax.inject.Inject
import javax.inject.Singleton

/** Fallback for deferred collaborative providers (OnlyOffice, Collabora) and unknown Other subtypes. */
@Singleton
class DeferredTabularBackendStub @Inject constructor() : TabularShareBackend {

    override val provider: SpreadsheetProvider = SpreadsheetProvider.OTHER

    override fun capabilities(): TabularCapabilities = TabularCapabilities(renameTab = false, browse = false)

    override fun isConfigured(dest: SpreadsheetDestination): Boolean = false

    override fun resolveAccountName(accountHint: String?): String? = accountHint?.takeIf { it.isNotBlank() }

    override fun resolveTargetId(dest: SpreadsheetDestination): String = dest.targetUrl

    override fun parseTargetIdFromUrl(url: String): String? = null

    override fun targetUrlFromId(id: String): String = id

    private fun notImplemented(provider: SpreadsheetProvider): Nothing =
        throw UnsupportedOperationException("${provider.displayLabel()} spreadsheet sync is not yet implemented")

    override suspend fun ensureHeaders(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        accountHint: String?,
    ) = notImplemented(dest.provider)

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> = notImplemented(dest.provider)

    override suspend fun listTabTitles(dest: SpreadsheetDestination, accountHint: String?): List<String> =
        notImplemented(dest.provider)

    override suspend fun renameTab(
        dest: SpreadsheetDestination,
        oldTitle: String,
        newTitle: String,
        accountHint: String?,
    ): Boolean = notImplemented(dest.provider)

    override suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?) =
        notImplemented(dest.provider)

    override suspend fun appendRows(
        dest: SpreadsheetDestination,
        tabName: String,
        rows: List<List<String>>,
        accountHint: String?,
    ) = notImplemented(dest.provider)

    override suspend fun updateRows(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        rows: List<List<String>>,
        accountHint: String?,
    ) = notImplemented(dest.provider)

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) = notImplemented(dest.provider)

    override suspend fun writeAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        rows: List<List<String>>,
        accountHint: String?,
    ) = notImplemented(dest.provider)

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult =
        TabularTestResult(false, "${dest.provider.displayLabel()} is not yet implemented")
}