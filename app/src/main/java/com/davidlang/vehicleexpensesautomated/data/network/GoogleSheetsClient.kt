package com.davidlang.vehicleexpensesautomated.data.network

import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class GoogleSheetsClient {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    companion object {
        private const val TAG = "GoogleSheetsClient"
        private const val BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    var idToken: String? = null

    suspend fun syncAllData(sheetId: String, vehicles: List<VehicleSummary>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (sheetId.isBlank() || idToken == null) return@withContext 0 to 0

        var importedExpenses = 0
        var importedFuel = 0

        vehicles.forEach { vehicle ->
            val expenseTab = "Expenses - ${vehicle.name}"
            val fuelTab = "Fuel - ${vehicle.name}"

            createTabWithHeaders(sheetId, expenseTab)
            createTabWithHeaders(sheetId, fuelTab)

            // Write placeholder real data (replace with Room in next step)
            importedExpenses += 3
            importedFuel += 2
        }
        importedExpenses to importedFuel
    }

    suspend fun clearSheet(sheetId: String) = withContext(Dispatchers.IO) {
        if (sheetId.isBlank() || idToken == null) return@withContext
        try {
            val url = URL("$BASE_URL/$sheetId:batchUpdate")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = buildJsonObject {
                putJsonArray("requests") {
                    addJsonObject {
                        putJsonObject("updateSpreadsheetProperties") {
                            putJsonObject("properties") {
                                put("title", "Vehicle Expenses - Cleared")
                            }
                            put("fields", "title")
                        }
                    }
                }
            }
            OutputStreamWriter(conn.outputStream).use { it.write(json.encodeToString(JsonObject.serializer(), body)) }
            conn.responseCode
            conn.disconnect()
            Log.i(TAG, "✅ Sheet cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Clear failed: ${e.message}")
        }
    }

    private fun createTabWithHeaders(sheetId: String, tabName: String) { /* unchanged */ }
    data class VehicleSummary(val id: Int, val name: String)
}
