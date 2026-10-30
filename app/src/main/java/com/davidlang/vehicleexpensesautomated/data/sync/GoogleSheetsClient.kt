package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val auth: GoogleSheetsAuth,
) {

    /**
     * **Every** Sheets `.execute()` (GET meta/values and mutating writes) goes through
     * [SyncRateLimit.withSheetsApiLimit] — pace + rate-limit detect/wait/retry.
     */
    private suspend fun <T> executeApi(block: () -> T): T =
        SyncRateLimit.withSheetsApiLimit(block)

    companion object {
        private const val TAG = "GoogleSheetsClient"

        fun parseSpreadsheetIdFromUrl(url: String): String? {
            val regex = Regex("""/spreadsheets/d/([a-zA-Z0-9-_]+)""")
            return regex.find(url.trim())?.groupValues?.get(1)
        }

        fun spreadsheetUrlFromId(id: String): String =
            "https://docs.google.com/spreadsheets/d/${id.trim()}/edit"
    }

    fun resolveAccountNamePublic(hint: String?): String? =
        auth.resolveAccountFromHint(hint)?.name

    /**
     * OAuth access token for remotetable [com.davidelang.remotetable.GoogleSheetsBackend].
     * Blocks on IO; call from Dispatchers.IO.
     */
    fun accessToken(accountHint: String? = null): String {
        val account = auth.resolveAccountFromHint(accountHint)
            ?: throw IllegalStateException("No Google account signed in for Sheets")
        return GoogleAuthUtil.getToken(
            context,
            account,
            "oauth2:${GoogleSheetsAuth.SHEETS_SCOPE}",
        )
    }

    private fun sheetsService(accountHint: String? = null): Sheets {
        val account = auth.resolveAccountFromHint(accountHint)
            ?: throw IllegalStateException("No Google account signed in for Sheets")
        return auth.buildSheetsServiceForAccountName(account.name)
    }

    suspend fun createSpreadsheet(title: String, accountHint: String? = null): Spreadsheet =
        withContext(Dispatchers.IO) {
            val body = Spreadsheet().setProperties(SpreadsheetProperties().setTitle(title))
            executeApi {
                sheetsService(accountHint).spreadsheets().create(body).execute()
            }
        }

    suspend fun createTabIfMissing(sheetId: String, tabName: String, accountHint: String? = null): Int =
        withContext(Dispatchers.IO) {
            val service = sheetsService(accountHint)
            val meta = executeApi {
                service.spreadsheets().get(sheetId).setFields("sheets.properties").execute()
            }
            val existing = meta.sheets?.firstOrNull { it.properties?.title == tabName }
            if (existing != null) return@withContext existing.properties.sheetId

            val addSheet = AddSheetRequest().setProperties(SheetProperties().setTitle(tabName))
            val batch = BatchUpdateSpreadsheetRequest().setRequests(
                listOf(Request().setAddSheet(addSheet)),
            )
            val response = executeApi {
                service.spreadsheets().batchUpdate(sheetId, batch).execute()
            }
            response.replies?.firstOrNull()?.addSheet?.properties?.sheetId ?: 0
        }

    suspend fun ensureHeaders(
        sheetId: String,
        tabName: String,
        headers: List<String>,
        accountHint: String? = null,
    ) = withContext(Dispatchers.IO) {
        createTabIfMissing(sheetId, tabName, accountHint)
        val service = sheetsService(accountHint)
        val range = "'$tabName'!A1:ZZ1"
        val current = executeApi {
            service.spreadsheets().values().get(sheetId, range).execute()
        }
        val firstRow = current.getValues()?.firstOrNull()
        val existing = firstRow?.map { it?.toString() ?: "" }?.map { it.trim() }?.filter { it.isNotBlank() }
            .orEmpty()
        if (firstRow.isNullOrEmpty()) {
            // New / blank tab: write full canonical headers.
            writeAllRows(sheetId, tabName, headers, emptyList(), accountHint)
        } else if (!TabularSchema.isValidHeaderRow(existing, headers)) {
            // Corrupt header: do not silently rewrite (coordinator fails dest sync with named columns).
            val missing = TabularSchema.missingRequiredHeaders(existing)
            Log.w(
                TAG,
                "ensureHeaders skipped rewrite on $tabName — missing required columns: $missing",
            )
        } else {
            // Valid existing header: keep order; append missing only (do not reorder known columns).
            val merged = TabularSchema.mergeHeaderOrder(existing, headers)
            if (merged != existing) {
                val missingHeaders = merged.filter { it !in existing.toSet() }
                Log.i(TAG, "Appending $tabName headers (order preserved); missing: $missingHeaders")
                val body = ValueRange().setValues(listOf(merged.map { it }))
                executeApi {
                    service.spreadsheets().values()
                        .update(sheetId, "'$tabName'!A1", body)
                        .setValueInputOption("RAW")
                        .execute()
                }
            }
        }
    }

    suspend fun writeAllRows(
        sheetId: String,
        tabName: String,
        headers: List<String>,
        rows: List<List<String>>,
        accountHint: String? = null,
    ) = withContext(Dispatchers.IO) {
        val service = sheetsService(accountHint)
        val allRows = listOf(headers.map { it }) + rows
        val body = ValueRange().setValues(allRows)
        val range = "'$tabName'!A1"
        executeApi {
            service.spreadsheets().values()
                .update(sheetId, range, body)
                .setValueInputOption("RAW")
                .execute()
        }
    }
}
