package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularCapabilities
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularTestResult
import javax.inject.Inject
import javax.inject.Singleton

/** Unknown-subtype fallback when [SpreadsheetProvider.OTHER] has no recognized backendType. */
@Singleton
class OtherTabularBackendStub @Inject constructor() : TabularShareBackend {

    override val provider: SpreadsheetProvider = SpreadsheetProvider.OTHER

    override fun capabilities(): TabularCapabilities = TabularCapabilities(renameTab = false, browse = false)

    override fun isConfigured(dest: SpreadsheetDestination): Boolean {
        val config = RowDbTabularConfig.parse(dest.configJson, dest.targetUrl, "other")
        return RowDbTabularConfig.isConfigured(config)
    }

    override fun resolveAccountName(accountHint: String?): String? = accountHint?.takeIf { it.isNotBlank() }

    override fun resolveTargetId(dest: SpreadsheetDestination): String =
        RowDbTabularConfig.parse(dest.configJson, dest.targetUrl, "other")?.baseUrl.orEmpty()

    override fun parseTargetIdFromUrl(url: String): String? = null

    override fun targetUrlFromId(id: String): String = id

    private fun unknownSubtype(dest: SpreadsheetDestination): Nothing {
        val type = try {
            org.json.JSONObject(dest.configJson).optString("backendType", "unknown")
        } catch (_: Exception) {
            "unknown"
        }
        throw UnsupportedOperationException("Unknown Other spreadsheet subtype: $type")
    }

    override suspend fun ensureHeaders(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        accountHint: String?,
    ) = unknownSubtype(dest)

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> = unknownSubtype(dest)

    override suspend fun listTabTitles(dest: SpreadsheetDestination, accountHint: String?): List<String> =
        unknownSubtype(dest)

    override suspend fun renameTab(
        dest: SpreadsheetDestination,
        oldTitle: String,
        newTitle: String,
        accountHint: String?,
    ): Boolean = unknownSubtype(dest)

    override suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?) =
        unknownSubtype(dest)

    override suspend fun appendRows(
        dest: SpreadsheetDestination,
        tabName: String,
        rows: List<List<String>>,
        accountHint: String?,
    ) = unknownSubtype(dest)

    override suspend fun updateRows(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        rows: List<List<String>>,
        accountHint: String?,
    ) = unknownSubtype(dest)

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) = unknownSubtype(dest)

    override suspend fun writeAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        headers: List<String>,
        rows: List<List<String>>,
        accountHint: String?,
    ) = unknownSubtype(dest)

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult {
        val type = try {
            org.json.JSONObject(dest.configJson).optString("backendType", "")
        } catch (_: Exception) {
            ""
        }
        return TabularTestResult(
            false,
            if (type.isBlank()) {
                "Pick a provider under Other (Baserow, NocoDB, …)"
            } else {
                "Unknown Other subtype: $type"
            },
        )
    }
}