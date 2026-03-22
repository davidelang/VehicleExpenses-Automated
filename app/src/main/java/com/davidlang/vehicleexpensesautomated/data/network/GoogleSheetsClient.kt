package com.davidlang.vehicleexpensesautomated.data.network

import android.util.Log
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GoogleSheetsClient {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    companion object {
        private const val TAG = "GoogleSheetsClient"
        private const val BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets"
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

        Log.i(TAG, "🚀 Creating tabs + column headers for ${vehicles.size} vehicles")

        vehicles.forEach { vehicle ->
            createTabWithHeaders(sheetId, "Expenses - ${vehicle.name}", listOf("Date", "Amount", "Category", "Description", "Receipt"))
            createTabWithHeaders(sheetId, "Fuel - ${vehicle.name}", listOf("Date", "Gallons", "Price/Gallon", "Total Cost", "Odometer", "Fuel Type", "Notes"))
        }
    }

    private fun createTabWithHeaders(sheetId: String, tabName: String, headers: List<String>) {
        val token = idToken ?: return

        try {
            // 1. Create tab (idempotent)
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
            connection.responseCode  // ignore response (tab may already exist)

            // 2. Write column headers to row 1
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

            val code = valueConnection.responseCode
            if (code == 200) {
                Log.i(TAG, "✅ Tab '$tabName' ready with column headers")
            }
            valueConnection.disconnect()
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup tab '$tabName': ${e.message}")
        }
    }

    data class VehicleSummary(val id: Int, val name: String)
}
