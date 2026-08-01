package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.DeleteSheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest
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

        /** Max ranges per values.batchGet (under Google limits; plan ≤50). */
        private const val BATCH_GET_MAX_RANGES = 40

        const val TAB_VEHICLES = "Vehicles"
        const val TAB_EXPENSES = "Expenses"
        const val TAB_SYNC_TEST = "__sync_test"
        const val FUEL_TAB_PREFIX = "Fuel - "

        val VEHICLE_HEADERS: List<String> get() = TabularSchema.VEHICLE_HEADERS
        /** Delegates to [TabularSchema.FUEL_HEADERS] (human-first order + Notes). */
        val FUEL_HEADERS: List<String> get() = TabularSchema.FUEL_HEADERS
        val EXPENSE_HEADERS: List<String> get() = TabularSchema.EXPENSE_HEADERS

        fun fuelTabName(vehicleName: String): String = FUEL_TAB_PREFIX + sanitizeTabName(vehicleName)

        fun sanitizeTabName(name: String): String {
            val cleaned = name.replace(Regex("""[\[\]*?:/\\]"""), " ").trim()
            return cleaned.take(100).ifBlank { "Vehicle" }
        }

        fun parseSpreadsheetIdFromUrl(url: String): String? {
            val regex = Regex("""/spreadsheets/d/([a-zA-Z0-9-_]+)""")
            return regex.find(url.trim())?.groupValues?.get(1)
        }

        fun spreadsheetUrlFromId(id: String): String =
            "https://docs.google.com/spreadsheets/d/${id.trim()}/edit"

        fun vehicleToRow(vehicle: Vehicle): List<String> = TabularSchema.vehicleToRow(vehicle)

        fun rowToVehicle(row: List<String>, headerIndex: Map<String, Int>): Vehicle =
            TabularSchema.rowToVehicle(row, headerIndex)
    }

    fun resolveAccountNamePublic(hint: String?): String? =
        auth.resolveAccountFromHint(hint)?.name

    private fun sheetsService(accountHint: String? = null): Sheets {
        val account = auth.resolveAccountFromHint(accountHint)
            ?: throw IllegalStateException("No Google account signed in for Sheets")
        return auth.buildSheetsServiceForAccountName(account.name)
    }

    suspend fun getSpreadsheet(sheetId: String, accountHint: String? = null): Spreadsheet =
        withContext(Dispatchers.IO) {
            val spreadsheet = executeApi {
                sheetsService(accountHint).spreadsheets().get(sheetId).execute()
            }
            Log.i(TAG, "Opened spreadsheet: ${spreadsheet.properties?.title}")
            spreadsheet
        }

    suspend fun createSpreadsheet(title: String, accountHint: String? = null): Spreadsheet =
        withContext(Dispatchers.IO) {
            val body = Spreadsheet().setProperties(SpreadsheetProperties().setTitle(title))
            executeApi {
                sheetsService(accountHint).spreadsheets().create(body).execute()
            }
        }

    /** List all sheet tab titles in the spreadsheet (one metadata GET). */
    suspend fun listSheetTitles(sheetId: String, accountHint: String? = null): List<String> =
        withContext(Dispatchers.IO) {
            val service = sheetsService(accountHint)
            val meta = executeApi {
                service.spreadsheets().get(sheetId).setFields("sheets.properties.title").execute()
            }
            meta.sheets?.mapNotNull { it.properties?.title } ?: emptyList()
        }

    /**
     * Rename a sheet tab via batchUpdate UpdateSheetPropertiesRequest.
     * [newTitle] must already be sanitized (e.g. from [fuelTabName]).
     * Idempotent: no-op when [oldTitle] == [newTitle] or old tab already absent and new exists.
     */
    suspend fun renameTab(
        sheetId: String,
        oldTitle: String,
        newTitle: String,
        accountHint: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (oldTitle == newTitle) return@withContext true
        val service = sheetsService(accountHint)
        val meta = executeApi {
            service.spreadsheets().get(sheetId).setFields("sheets.properties").execute()
        }
        val titles = meta.sheets?.mapNotNull { it.properties?.title }.orEmpty()
        if (oldTitle !in titles) {
            return@withContext newTitle in titles
        }
        if (newTitle in titles) {
            Log.i(TAG, "Rename skipped: target tab already exists: $newTitle")
            return@withContext false
        }
        val sheet = meta.sheets?.firstOrNull { it.properties?.title == oldTitle }
            ?: return@withContext false
        val sheetIdNum = sheet.properties?.sheetId ?: return@withContext false
        val props = SheetProperties()
            .setSheetId(sheetIdNum)
            .setTitle(newTitle)
        val update = UpdateSheetPropertiesRequest()
            .setProperties(props)
            .setFields("title")
        val batch = BatchUpdateSpreadsheetRequest().setRequests(
            listOf(Request().setUpdateSheetProperties(update)),
        )
        executeApi {
            service.spreadsheets().batchUpdate(sheetId, batch).execute()
        }
        Log.i(TAG, "Renamed sheet tab: \"$oldTitle\" → \"$newTitle\"")
        true
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
        if (firstRow.isNullOrEmpty()) {
            // New / empty tab: write full canonical header order (human-first for fuel).
            writeAllRows(sheetId, tabName, headers, emptyList(), accountHint)
        } else {
            // Existing header: keep order; append missing only (do not reorder known columns).
            val existing = firstRow.map { it?.toString() ?: "" }.filter { it.isNotBlank() }
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

    /** Phase 7: read/write full tab values for merge sync. */
    suspend fun readAllRows(
        sheetId: String,
        tabName: String,
        accountHint: String? = null,
    ): List<List<String>> = withContext(Dispatchers.IO) {
        val service = sheetsService(accountHint)
        val range = "'$tabName'!A:ZZ"
        val result = executeApi {
            service.spreadsheets().values().get(sheetId, range).execute()
        }
        result.getValues()?.map { row ->
            row.map { cell -> cell?.toString() ?: "" }
        } ?: emptyList()
    }

    /**
     * Bulk compare read: one `values.batchGet` per chunk of tabs (≤[BATCH_GET_MAX_RANGES]).
     * Cuts read quota from N full-tab GETs to ~ceil(N/40) requests.
     * Missing / empty tabs map to empty lists. Ranges go through [executeApi].
     */
    suspend fun batchReadTabs(
        sheetId: String,
        tabNames: List<String>,
        accountHint: String? = null,
    ): Map<String, List<List<String>>> = withContext(Dispatchers.IO) {
        val names = tabNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) return@withContext emptyMap()
        val service = sheetsService(accountHint)
        val out = LinkedHashMap<String, List<List<String>>>(names.size)
        val chunks = names.chunked(BATCH_GET_MAX_RANGES)
        Log.i(
            TAG,
            "batchReadTabs: ${names.size} tabs in ${chunks.size} batchGet(s) " +
                "(sheetId=${sheetId.take(12)}…)",
        )
        for (chunk in chunks) {
            val ranges = chunk.map { tab -> "'$tab'!A:ZZ" }
            val response = executeApi {
                service.spreadsheets().values()
                    .batchGet(sheetId)
                    .setRanges(ranges)
                    .setMajorDimension("ROWS")
                    .execute()
            }
            val valueRanges = response.valueRanges.orEmpty()
            for ((index, tabName) in chunk.withIndex()) {
                val vr = valueRanges.getOrNull(index)
                val rows = vr?.getValues()?.map { row ->
                    row.map { cell -> cell?.toString() ?: "" }
                } ?: emptyList()
                out[tabName] = rows
            }
        }
        out
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

    /** Append data rows after the last populated row in [tabName]. */
    suspend fun appendRows(
        sheetId: String,
        tabName: String,
        rows: List<List<String>>,
        accountHint: String? = null,
    ) = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext
        val service = sheetsService(accountHint)
        val body = ValueRange().setValues(rows)
        val range = "'$tabName'!A:ZZ"
        executeApi {
            service.spreadsheets().values()
                .append(sheetId, range, body)
                .setValueInputOption("RAW")
                .setInsertDataOption("INSERT_ROWS")
                .execute()
        }
    }

    /** Update a contiguous block starting at [startRow] (1-based, includes header row semantics). */
    suspend fun updateRows(
        sheetId: String,
        tabName: String,
        startRow: Int,
        rows: List<List<String>>,
        accountHint: String? = null,
    ) = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext
        val service = sheetsService(accountHint)
        val endRow = startRow + rows.size - 1
        val body = ValueRange().setValues(rows)
        val range = "'$tabName'!A$startRow:ZZ$endRow"
        executeApi {
            service.spreadsheets().values()
                .update(sheetId, range, body)
                .setValueInputOption("RAW")
                .execute()
        }
    }

    /** Clear sheet values from [startRow] (1-based) through the end of the tab. */
    suspend fun clearTrailing(
        sheetId: String,
        tabName: String,
        startRow: Int,
        accountHint: String? = null,
    ) = withContext(Dispatchers.IO) {
        if (startRow < 1) return@withContext
        val service = sheetsService(accountHint)
        val range = "'$tabName'!A$startRow:ZZ"
        executeApi {
            service.spreadsheets().values()
                .clear(sheetId, range, com.google.api.services.sheets.v4.model.ClearValuesRequest())
                .execute()
        }
    }

    fun syncIdFromRow(row: List<String>, headerIndex: Map<String, Int>, column: String = "Sync ID"): String {
        val idx = headerIndex[column] ?: -1
        return row.getOrElse(idx) { "" }.trim()
    }

    fun rowsEqual(a: List<String>, b: List<String>): Boolean {
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            val left = a.getOrElse(i) { "" }
            val right = b.getOrElse(i) { "" }
            if (left != right) return false
        }
        return true
    }

    suspend fun deleteTab(sheetId: String, tabName: String, accountHint: String? = null) =
        withContext(Dispatchers.IO) {
            val service = sheetsService(accountHint)
            val meta = executeApi {
                service.spreadsheets().get(sheetId).setFields("sheets.properties").execute()
            }
            val sheet = meta.sheets?.firstOrNull { it.properties?.title == tabName } ?: return@withContext
            val sheetIdNum = sheet.properties?.sheetId ?: return@withContext
            val batch = BatchUpdateSpreadsheetRequest().setRequests(
                listOf(Request().setDeleteSheet(DeleteSheetRequest().setSheetId(sheetIdNum))),
            )
            executeApi {
                service.spreadsheets().batchUpdate(sheetId, batch).execute()
            }
        }

    suspend fun testConnection(sheetId: String, accountHint: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val marker = "sync-test-${System.currentTimeMillis()}"
            try {
                createTabIfMissing(sheetId, TAB_SYNC_TEST, accountHint)
                writeAllRows(sheetId, TAB_SYNC_TEST, listOf("marker"), listOf(listOf(marker)), accountHint)
                val rows = readAllRows(sheetId, TAB_SYNC_TEST, accountHint)
                val readBack = rows.drop(1).firstOrNull()?.firstOrNull()
                deleteTab(sheetId, TAB_SYNC_TEST, accountHint)
                readBack == marker
            } catch (e: Exception) {
                Log.e(TAG, "Test connection failed", e)
                try {
                    deleteTab(sheetId, TAB_SYNC_TEST, accountHint)
                } catch (_: Exception) {
                }
                throw SheetsAuthRecovery.wrapIfRecoverable(e)
            }
        }

    fun fuelToRow(entry: FuelEntry, vehicleSyncId: String = ""): List<String> =
        TabularSchema.fuelToRow(entry, vehicleSyncId)

    fun expenseToRow(entry: ExpenseEntry, vehicleSyncId: String = ""): List<String> =
        TabularSchema.expenseToRow(entry, vehicleSyncId)

    fun rowToFuel(row: List<String>, headerIndex: Map<String, Int>): FuelEntry =
        TabularSchema.rowToFuel(row, headerIndex)

    fun rowToFuelVehicleSyncId(row: List<String>, headerIndex: Map<String, Int>): String {
        fun cell(name: String) = row.getOrElse(headerIndex[name] ?: -1) { "" }
        return cell("Vehicle Sync ID")
    }

    fun rowToExpense(row: List<String>, headerIndex: Map<String, Int>): ExpenseEntry =
        TabularSchema.rowToExpense(row, headerIndex)

    fun rowToExpenseVehicleSyncId(row: List<String>, headerIndex: Map<String, Int>): String =
        TabularSchema.rowToExpenseVehicleSyncId(row, headerIndex)

    fun headerIndex(headers: List<String>): Map<String, Int> =
        headers.mapIndexed { index, name -> name to index }.toMap()
}
