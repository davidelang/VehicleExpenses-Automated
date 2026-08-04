package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidelang.remotetable.Backends
import com.davidelang.remotetable.RateLimitProgress
import com.davidelang.remotetable.RemoteTable
import com.davidelang.remotetable.TabData
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.SheetsRecoverableAuthException
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.SyncRateLimit
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularCapabilities
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Sheets tabular I/O via **remotetable** AAR ([GoogleSheetsBackend]).
 *
 * Auth still uses in-app Google Sign-In; token is passed into the library.
 * **Sheets HTTP pace, 429 retry, batchGet, range updates** live in remotetable L0
 * (see library `spec/CONTRACT.md`). App-level multi-dest cooldowns stay in
 * [SyncRateLimit] / [com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetSyncCoordinator].
 *
 * This adapter stays thin: no reimplementation of transport; [updateRows] uses
 * library range update (not read+full-tab-replace).
 */
@Singleton
class GoogleSheetsTabularBackend @Inject constructor(
    private val sheetsClient: GoogleSheetsClient,
) : TabularShareBackend {

    override val provider: SpreadsheetProvider = SpreadsheetProvider.GOOGLE_SHEETS

    /** Bridge remotetable 429/pace waits into coordinator-installed UI progress. */
    private val rateLimitProgress = RateLimitProgress { message ->
        SyncRateLimit.notifyProgress(message)
    }

    override fun capabilities(): TabularCapabilities = TabularCapabilities(renameTab = true, browse = true)

    override fun isConfigured(dest: SpreadsheetDestination): Boolean =
        resolveTargetId(dest).isNotBlank()

    override fun resolveAccountName(accountHint: String?): String? =
        sheetsClient.resolveAccountNamePublic(accountHint)

    override fun resolveTargetId(dest: SpreadsheetDestination): String {
        if (dest.targetId.isNotBlank()) return dest.targetId
        if (dest.targetUrl.isNotBlank()) {
            return parseTargetIdFromUrl(dest.targetUrl).orEmpty()
        }
        return ""
    }

    override fun parseTargetIdFromUrl(url: String): String? =
        GoogleSheetsClient.parseSpreadsheetIdFromUrl(url)

    override fun targetUrlFromId(id: String): String =
        GoogleSheetsClient.spreadsheetUrlFromId(id)

    private suspend fun table(dest: SpreadsheetDestination, accountHint: String?): RemoteTable =
        withContext(Dispatchers.IO) {
            val token = sheetsClient.accessToken(accountHint)
            val id = resolveTargetId(dest)
            RemoteTable(Backends.googleSheets(token, id, progress = rateLimitProgress))
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
            table(dest, accountHint).ensureHeaders(tabName, headers)
        }
    }

    override suspend fun readAllRows(
        dest: SpreadsheetDestination,
        tabName: String,
        accountHint: String?,
    ): List<List<String>> = withContext(Dispatchers.IO) {
        table(dest, accountHint).readRows(tabName).toGrid()
    }

    /**
     * Bulk compare read via remotetable [RemoteTable.readMany] → Sheets `values.batchGet`.
     */
    override suspend fun batchReadTabs(
        dest: SpreadsheetDestination,
        tabNames: List<String>,
        accountHint: String?,
    ): Map<String, List<List<String>>> = withContext(Dispatchers.IO) {
        val names = tabNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) return@withContext emptyMap()
        val many = table(dest, accountHint).readMany(names)
        many.mapValues { (_, data) -> data.toGrid() }
    }

    override suspend fun listTabTitles(dest: SpreadsheetDestination, accountHint: String?): List<String> =
        withContext(Dispatchers.IO) {
            table(dest, accountHint).listTabs()
        }

    override suspend fun renameTab(
        dest: SpreadsheetDestination,
        oldTitle: String,
        newTitle: String,
        accountHint: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        table(dest, accountHint).renameTab(oldTitle, newTitle)
    }

    override suspend fun deleteTab(dest: SpreadsheetDestination, tabName: String, accountHint: String?) {
        withContext(Dispatchers.IO) {
            table(dest, accountHint).deleteTab(tabName)
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
            val rt = table(dest, accountHint)
            val existing = rt.readRows(tabName)
            val headers = existing.headers.ifEmpty {
                List(rows.maxOfOrNull { it.size } ?: 0) { "Col$it" }
            }
            // Library append uses values:append (no full-tab rewrite).
            rt.writeRows(tabName, headers, rows, mode = "append")
        }
    }

    /**
     * Point update via remotetable [RemoteTable.updateRangeRows] — contiguous range write.
     * Does **not** read+full-replace the tab.
     */
    override suspend fun updateRows(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        rows: List<List<String>>,
        accountHint: String?,
    ) {
        if (rows.isEmpty()) return
        withContext(Dispatchers.IO) {
            table(dest, accountHint).updateRangeRows(tabName, startRow, rows)
        }
    }

    override suspend fun clearTrailing(
        dest: SpreadsheetDestination,
        tabName: String,
        startRow: Int,
        accountHint: String?,
    ) {
        withContext(Dispatchers.IO) {
            table(dest, accountHint).clearFromRow(tabName, startRow)
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
            table(dest, accountHint).writeRows(tabName, headers, rows, mode = "replace")
        }
    }

    override suspend fun testConnection(dest: SpreadsheetDestination, accountHint: String?): TabularTestResult {
        val sheetId = resolveTargetId(dest)
        if (sheetId.isBlank()) {
            return TabularTestResult(false, "Spreadsheet not configured")
        }
        return try {
            val conn = withContext(Dispatchers.IO) {
                table(dest, accountHint).testConnection()
            }
            val ok = conn["ok"] == true
            TabularTestResult(ok, conn["message"]?.toString() ?: if (ok) "Connection test passed" else "Connection test failed")
        } catch (e: SheetsRecoverableAuthException) {
            TabularTestResult(
                success = false,
                message = e.message ?: SheetsAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE,
                needsRemoteConsent = true,
                recoveryIntent = e.recoveryIntent,
            )
        } catch (e: Exception) {
            TabularTestResult(false, SheetsAuthRecovery.userMessage(e))
        }
    }
}
