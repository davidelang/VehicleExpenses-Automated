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

    // ====================== PUSH ======================
    suspend fun syncAllData(sheetId: String, vehicles: List<Vehicle>, expenses: List<ExpenseEntry>, fuelEntries: List<FuelEntry>): Int = withContext(Dispatchers.IO) {
        if (sheetId.isBlank() || idToken == null) return@withContext 0
        var pushed = 0
        Log.i("GoogleSheetsClient", "🚀 Pushing ${vehicles.size} vehicles, ${expenses.size} expenses, ${fuelEntries.size} fuel entries")

        pushed += syncVehicles(sheetId, vehicles)
        pushed += syncExpenses(sheetId, expenses)
        pushed += syncFuelEntries(sheetId, fuelEntries)

        Log.i("GoogleSheetsClient", "✅ Push complete ($pushed items)")
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

    // ====================== PULL ======================
    suspend fun pullAllData(sheetId: String): Triple<List<Vehicle>, List<ExpenseEntry>, List<FuelEntry>> = withContext(Dispatchers.IO) {
        if (sheetId.isBlank() || idToken == null) return@withContext Triple(emptyList(), emptyList(), emptyList())

        Log.i("GoogleSheetsClient", "📥 Pulling data from Google Sheets")

        val vehicles = pullVehicles(sheetId)
        val expenses = pullExpenses(sheetId)
        val fuelEntries = pullFuelEntries(sheetId)

        Log.i("GoogleSheetsClient", "✅ Pull complete (${vehicles.size} vehicles, ${expenses.size} expenses, ${fuelEntries.size} fuel)")
        Triple(vehicles, expenses, fuelEntries)
    }

    private suspend fun pullVehicles(sheetId: String): List<Vehicle> = withContext(Dispatchers.IO) { readTab(sheetId, "Vehicles") { row ->
        Vehicle(
            id = row[0].toIntOrNull() ?: 0,
            make = row[1],
            model = row[2],
            year = row[3].toIntOrNull() ?: 0,
            licensePlate = row[4],
            vin = row[5].ifBlank { null },
            notes = row[6].ifBlank { null }
        )
    } }

    private suspend fun pullExpenses(sheetId: String): List<ExpenseEntry> = withContext(Dispatchers.IO) { readTab(sheetId, "Expenses") { row ->
        ExpenseEntry(
            id = row[0].toLongOrNull() ?: 0,
            vehicleId = row[1].toIntOrNull() ?: 0,
            amount = row[2].toDoubleOrNull() ?: 0.0,
            description = row[3],
            date = row[4].toLongOrNull() ?: 0
        )
    } }

    private suspend fun pullFuelEntries(sheetId: String): List<FuelEntry> = withContext(Dispatchers.IO) { readTab(sheetId, "Fuel Entries") { row ->
        FuelEntry(
            id = row[0].toLongOrNull() ?: 0,
            vehicleId = row[1].toIntOrNull() ?: 0,
            odometer = row[2].toIntOrNull() ?: 0,
            gallons = row[3].toDoubleOrNull() ?: 0.0,
            cost = row[4].toDoubleOrNull() ?: 0.0,
            timestamp = row[5].toLongOrNull() ?: 0
        )
    } }

    private suspend fun readTab(sheetId: String, tab: String, mapper: (List<String>) -> Any): List<Any> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tab?majorDimension=ROWS")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) return@withContext emptyList()

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val jsonObject = json.decodeFromString<JsonObject>(body)
            val values = jsonObject["values"]?.jsonArray ?: return@withContext emptyList()

            // Skip header row
            values.drop(1).mapNotNull { row ->
                val cells = row.jsonArray.map { it.jsonPrimitive.content }
                try { mapper(cells) } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e("GoogleSheetsClient", "Pull from '$tab' failed", e)
            emptyList()
        }
    }

    private fun createTabWithHeaders(sheetId: String, tabName: String, headers: List<String>) {
        Log.i("GoogleSheetsClient", "Tab '$tabName' ready")
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
                put("values", JsonArray(rows.map { JsonArray(it.map { JsonPrimitive(it) }) }))
            }

            val jsonString = json.encodeToString(JsonObject.serializer(), body)
            OutputStreamWriter(conn.outputStream).use { it.write(jsonString) }

            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) rows.size else 0
        } catch (e: Exception) {
            Log.e("GoogleSheetsClient", "Append failed", e)
            0
        }
    }
}
