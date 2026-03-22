package com.davidlang.vehicleexpensesautomated.data.network

import android.util.Log
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

    fun createNewSheetInBrowser() {
        Log.i(TAG, "Opening browser for new Google Sheet creation")
    }

    suspend fun ensureVehicleTabs(sheetId: String, vehicles: List<VehicleSummary>) = withContext(Dispatchers.IO) {
        if (sheetId.isBlank()) {
            Log.w(TAG, "No Sheet ID — skipping tab creation")
            return@withContext
        }

        Log.i(TAG, "🚀 Creating tabs + headers + REALISTIC DATA for ${vehicles.size} vehicles")

        vehicles.forEach { vehicle ->
            val expenseTab = "Expenses - ${vehicle.name}"
            val fuelTab = "Fuel - ${vehicle.name}"

            createTabWithHeaders(sheetId, expenseTab, listOf("Date", "Amount", "Category", "Description", "Receipt"))
            createTabWithHeaders(sheetId, fuelTab, listOf("Date", "Gallons", "Price/Gallon", "Total Cost", "Odometer", "Fuel Type", "Notes"))

            appendRealisticExpenseRows(sheetId, expenseTab)
            appendRealisticFuelRows(sheetId, fuelTab)
        }
    }

    private fun createTabWithHeaders(sheetId: String, tabName: String, headers: List<String>) {
        val token = idToken ?: return
        try {
            // Create tab + headers (idempotent)
            val batchUrl = URL("$BASE_URL/$sheetId:batchUpdate")
            val connection = batchUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val requestBody = buildJsonObject {
                putJsonArray("requests") {
                    addJsonObject {
                        putJsonObject("addSheet") {
                            putJsonObject("properties") {
                                put("title", tabName)
                            }
                        }
                    }
                }
            }

            val bodyString = json.encodeToString(JsonObject.serializer(), requestBody)
            OutputStreamWriter(connection.outputStream).use { it.write(bodyString) }
            connection.responseCode
            connection.disconnect()

            // Headers
            val valueUrl = URL("$BASE_URL/$sheetId/values/$tabName!A1:append?valueInputOption=RAW")
            val valueConnection = valueUrl.openConnection() as HttpURLConnection
            valueConnection.requestMethod = "POST"
            valueConnection.setRequestProperty("Authorization", "Bearer $token")
            valueConnection.setRequestProperty("Content-Type", "application/json")
            valueConnection.doOutput = true

            val headerRow = buildJsonObject {
                put("values", buildJsonArray {
                    addJsonArray {
                        headers.forEach { add(it) }
                    }
                })
            }

            val headerString = json.encodeToString(JsonObject.serializer(), headerRow)
            OutputStreamWriter(valueConnection.outputStream).use { it.write(headerString) }
            valueConnection.responseCode
            valueConnection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup tab '$tabName': ${e.message}")
        }
    }

    private fun appendRealisticExpenseRows(sheetId: String, tabName: String) {
        val token = idToken ?: return
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tabName!A2:append?valueInputOption=RAW")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val today = dateFormat.format(Date())
            val sampleRows = buildJsonArray {
                addJsonArray { add(today); add(52.34); add("Fuel"); add("Shell station fill-up"); add("") }
                addJsonArray { add("2025-03-10"); add(18.75); add("Maintenance"); add("New wiper blades"); add("") }
                addJsonArray { add(today); add(9.99); add("Parking"); add("Downtown garage"); add("") }
            }

            val body = buildJsonObject { put("values", sampleRows) }
            val bodyString = json.encodeToString(JsonObject.serializer(), body)
            OutputStreamWriter(connection.outputStream).use { it.write(bodyString) }

            if (connection.responseCode == 200) Log.i(TAG, "✅ Realistic expenses added to $tabName")
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append expenses: ${e.message}")
        }
    }

    private fun appendRealisticFuelRows(sheetId: String, tabName: String) {
        val token = idToken ?: return
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tabName!A2:append?valueInputOption=RAW")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val today = dateFormat.format(Date())
            val sampleRows = buildJsonArray {
                addJsonArray { add(today); add(14.2); add(3.79); add(53.82); add(13245); add("Regular"); add("Full tank") }
                addJsonArray { add("2025-03-12"); add(9.8); add(4.29); add(42.04); add(13510); add("Premium"); add("Topped off") }
            }

            val body = buildJsonObject { put("values", sampleRows) }
            val bodyString = json.encodeToString(JsonObject.serializer(), body)
            OutputStreamWriter(connection.outputStream).use { it.write(bodyString) }

            if (connection.responseCode == 200) Log.i(TAG, "✅ Realistic fuel fills added to $tabName")
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append fuel: ${e.message}")
        }
    }

    data class VehicleSummary(val id: Int, val name: String)
}
