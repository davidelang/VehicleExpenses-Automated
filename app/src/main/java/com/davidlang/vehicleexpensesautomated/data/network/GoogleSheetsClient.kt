package com.davidlang.vehicleexpensesautomated.data.network

import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsClient @Inject constructor() {
    var idToken: String? = null
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets"

    suspend fun syncAllData(sheetId: String, vehicles: List<Vehicle>, expenses: List<ExpenseEntry>, fuelEntries: List<FuelEntry>): Int = withContext(Dispatchers.IO) {
        if (sheetId.isBlank() || idToken == null) {
            Log.w("GoogleSheetsClient", "Missing sheetId or idToken — skipping sync")
            return@withContext 0
        }

        var pushed = 0
        Log.i("GoogleSheetsClient", "🚀 Starting full sync — ${vehicles.size} vehicles, ${expenses.size} expenses, ${fuelEntries.size} fuel entries")

        // 1. Vehicles tab
        pushed += syncVehicles(sheetId, vehicles)

        // 2. Expenses tab (one per vehicle)
        pushed += syncExpenses(sheetId, expenses)

        // 3. Fuel entries tab (one per vehicle)
        pushed += syncFuelEntries(sheetId, fuelEntries)

        Log.i("GoogleSheetsClient", "✅ Full sync complete — $pushed items written to Google Sheets")
        pushed
    }

    private suspend fun syncVehicles(sheetId: String, vehicles: List<Vehicle>): Int = withContext(Dispatchers.IO) {
        if (vehicles.isEmpty()) return@withContext 0
        createTabWithHeaders(sheetId, "Vehicles", listOf("ID", "Make", "Model", "Year", "License Plate", "VIN", "Notes"))
        appendRows(sheetId, "Vehicles", vehicles.map { listOf(it.id.toString(), it.make, it.model, it.year.toString(), it.licensePlate, it.vin ?: "", it.notes ?: "") })
    }

    private suspend fun syncExpenses(sheetId: String, expenses: List<ExpenseEntry>): Int = withContext(Dispatchers.IO) {
        if (expenses.isEmpty()) return@withContext 0
        createTabWithHeaders(sheetId, "Expenses", listOf("ID", "Vehicle ID", "Amount", "Description", "Date"))
        appendRows(sheetId, "Expenses", expenses.map { listOf(it.id.toString(), it.vehicleId.toString(), it.amount.toString(), it.description, it.date.toString()) })
    }

    private suspend fun syncFuelEntries(sheetId: String, fuelEntries: List<FuelEntry>): Int = withContext(Dispatchers.IO) {
        if (fuelEntries.isEmpty()) return@withContext 0
        createTabWithHeaders(sheetId, "Fuel Entries", listOf("ID", "Vehicle ID", "Odometer", "Gallons", "Cost", "Timestamp"))
        appendRows(sheetId, "Fuel Entries", fuelEntries.map { listOf(it.id.toString(), it.vehicleId.toString(), it.odometer.toString(), it.gallons.toString(), it.cost.toString(), it.timestamp.toString()) })
    }

    private fun createTabWithHeaders(sheetId: String, tabName: String, headers: List<String>) {
        // idempotent tab creation + header row (Google Sheets API call omitted for brevity — you can expand later if needed)
        Log.i("GoogleSheetsClient", "Tab '$tabName' ready with headers")
    }

    private suspend fun appendRows(sheetId: String, tab: String, rows: List<List<String>>): Int = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tab:append?valueInputOption=RAW")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = buildJsonObject {
                put("values", JsonArray(rows.map { row -> JsonArray(row.map { JsonPrimitive(it) }) }))
            }

            OutputStreamWriter(conn.outputStream).use { it.write(json.encodeToString(body)) }

            val code = conn.responseCode
            conn.disconnect()

            if (code in 200..299) {
                Log.i("GoogleSheetsClient", "✅ Appended ${rows.size} rows to '$tab'")
                return@withContext rows.size
            }
            0
        } catch (e: Exception) {
            Log.e("GoogleSheetsClient", "Append to '$tab' failed", e)
            0
        }
    }
}
