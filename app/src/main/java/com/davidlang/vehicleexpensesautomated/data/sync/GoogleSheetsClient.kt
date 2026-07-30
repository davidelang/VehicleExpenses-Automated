package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
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

    companion object {
        private const val TAG = "GoogleSheetsClient"

        const val TAB_VEHICLES = "Vehicles"
        const val TAB_EXPENSES = "Expenses"
        const val TAB_SYNC_TEST = "__sync_test"
        const val FUEL_TAB_PREFIX = "Fuel - "

        val VEHICLE_HEADERS = listOf(
            "Sync ID", "ID", "Name", "Make", "Model", "Year", "License Plate", "VIN", "Notes",
            "Odo Crop L", "Odo Crop T", "Odo Crop R", "Odo Crop B",
            "Other Crop L", "Other Crop T", "Other Crop R", "Other Crop B",
            "Landmark Text Blocks JSON",
            "Cloud Manifest", "Origin Device ID", "Updated At", "Deleted", "Deleted At",
        )
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

        fun vehicleToRow(vehicle: Vehicle): List<String> = listOf(
            vehicle.syncId,
            vehicle.id.toString(),
            vehicle.name,
            vehicle.make ?: "",
            vehicle.model ?: "",
            vehicle.year?.toString() ?: "",
            vehicle.licensePlate ?: "",
            vehicle.vin ?: "",
            vehicle.notes ?: "",
            vehicle.odometerCropLeft?.toString() ?: "",
            vehicle.odometerCropTop?.toString() ?: "",
            vehicle.odometerCropRight?.toString() ?: "",
            vehicle.odometerCropBottom?.toString() ?: "",
            vehicle.otherTextCropLeft?.toString() ?: "",
            vehicle.otherTextCropTop?.toString() ?: "",
            vehicle.otherTextCropRight?.toString() ?: "",
            vehicle.otherTextCropBottom?.toString() ?: "",
            vehicle.landmarkTextBlocksJson ?: "",
            vehicle.cloudManifest ?: "",
            vehicle.originDeviceId,
            vehicle.updatedAt.toString(),
            vehicle.deleted.toString(),
            vehicle.deletedAt?.toString() ?: "",
        )

        fun rowToVehicle(row: List<String>, headerIndex: Map<String, Int>): Vehicle {
            fun cell(name: String) = row.getOrElse(headerIndex[name] ?: -1) { "" }
            fun optionalFloat(name: String): Float? = cell(name).toFloatOrNull()
            val id = cell("ID").toIntOrNull() ?: 0
            val name = cell("Name")
            val make = cell("Make").ifBlank { null }
            val model = cell("Model").ifBlank { null }
            val year = cell("Year").toIntOrNull()
            val syncIdCell = cell("Sync ID")
            val syncId = syncIdCell.ifBlank {
                SyncIdGenerator.deterministicVehicleFromSheet(id, name, make, model, year)
            }
            return Vehicle(
                syncId = syncId,
                id = id,
                name = name,
                make = make,
                model = model,
                year = year,
                licensePlate = cell("License Plate").ifBlank { null },
                vin = cell("VIN").ifBlank { null },
                notes = cell("Notes").ifBlank { null },
                odometerCropLeft = optionalFloat("Odo Crop L"),
                odometerCropTop = optionalFloat("Odo Crop T"),
                odometerCropRight = optionalFloat("Odo Crop R"),
                odometerCropBottom = optionalFloat("Odo Crop B"),
                otherTextCropLeft = optionalFloat("Other Crop L"),
                otherTextCropTop = optionalFloat("Other Crop T"),
                otherTextCropRight = optionalFloat("Other Crop R"),
                otherTextCropBottom = optionalFloat("Other Crop B"),
                landmarkTextBlocksJson = cell("Landmark Text Blocks JSON").ifBlank { null },
                cloudManifest = cell("Cloud Manifest").ifBlank { null },
                originDeviceId = cell("Origin Device ID"),
                updatedAt = cell("Updated At").toLongOrNull() ?: 0L,
                deleted = cell("Deleted").equals("true", ignoreCase = true),
                deletedAt = cell("Deleted At").toLongOrNull(),
            )
        }
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
            val spreadsheet = sheetsService(accountHint).spreadsheets().get(sheetId).execute()
            Log.i(TAG, "Opened spreadsheet: ${spreadsheet.properties?.title}")
            spreadsheet
        }

    suspend fun createSpreadsheet(title: String, accountHint: String? = null): Spreadsheet =
        withContext(Dispatchers.IO) {
            val body = Spreadsheet().setProperties(SpreadsheetProperties().setTitle(title))
            sheetsService(accountHint).spreadsheets().create(body).execute()
        }

    /** Phase 6: create missing sheet tab via batchUpdate. */
    /** List all sheet tab titles in the spreadsheet (one metadata GET). */
    suspend fun listSheetTitles(sheetId: String, accountHint: String? = null): List<String> =
        withContext(Dispatchers.IO) {
            val service = sheetsService(accountHint)
            val meta = service.spreadsheets().get(sheetId).setFields("sheets.properties.title").execute()
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
        val meta = service.spreadsheets().get(sheetId).setFields("sheets.properties").execute()
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
        service.spreadsheets().batchUpdate(sheetId, batch).execute()
        Log.i(TAG, "Renamed sheet tab: \"$oldTitle\" → \"$newTitle\"")
        true
    }

    suspend fun createTabIfMissing(sheetId: String, tabName: String, accountHint: String? = null): Int =
        withContext(Dispatchers.IO) {
            val service = sheetsService(accountHint)
            val meta = service.spreadsheets().get(sheetId).setFields("sheets.properties").execute()
            val existing = meta.sheets?.firstOrNull { it.properties?.title == tabName }
            if (existing != null) return@withContext existing.properties.sheetId

            val addSheet = AddSheetRequest().setProperties(SheetProperties().setTitle(tabName))
            val batch = BatchUpdateSpreadsheetRequest().setRequests(
                listOf(Request().setAddSheet(addSheet)),
            )
            val response = service.spreadsheets().batchUpdate(sheetId, batch).execute()
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
        val current = service.spreadsheets().values().get(sheetId, range).execute()
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
                service.spreadsheets().values()
                    .update(sheetId, "'$tabName'!A1", body)
                    .setValueInputOption("RAW")
                    .execute()
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
        val result = service.spreadsheets().values().get(sheetId, range).execute()
        result.getValues()?.map { row ->
            row.map { cell -> cell?.toString() ?: "" }
        } ?: emptyList()
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
        service.spreadsheets().values()
            .update(sheetId, range, body)
            .setValueInputOption("RAW")
            .execute()
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
        service.spreadsheets().values()
            .append(sheetId, range, body)
            .setValueInputOption("RAW")
            .setInsertDataOption("INSERT_ROWS")
            .execute()
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
        service.spreadsheets().values()
            .update(sheetId, range, body)
            .setValueInputOption("RAW")
            .execute()
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
        service.spreadsheets().values()
            .clear(sheetId, range, com.google.api.services.sheets.v4.model.ClearValuesRequest())
            .execute()
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
            val meta = service.spreadsheets().get(sheetId).setFields("sheets.properties").execute()
            val sheet = meta.sheets?.firstOrNull { it.properties?.title == tabName } ?: return@withContext
            val sheetIdNum = sheet.properties?.sheetId ?: return@withContext
            val batch = BatchUpdateSpreadsheetRequest().setRequests(
                listOf(Request().setDeleteSheet(DeleteSheetRequest().setSheetId(sheetIdNum))),
            )
            service.spreadsheets().batchUpdate(sheetId, batch).execute()
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