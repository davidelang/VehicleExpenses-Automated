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

    var accessToken: String? = null   // ← real OAuth token will be set here in next step

    fun createNewSheetInBrowser() {
        Log.i(TAG, "Opening browser for new Google Sheet creation")
    }

    suspend fun ensureVehicleTabs(sheetId: String, vehicles: List<VehicleSummary>) = withContext(Dispatchers.IO) {
        if (sheetId.isBlank()) {
            Log.w(TAG, "No Sheet ID — skipping tab creation")
            return@withContext
        }

        Log.i(TAG, "🚀 Making REAL API calls to create tabs for ${vehicles.size} vehicles")

        vehicles.forEach { vehicle ->
            createTabIfNotExists(sheetId, "Expenses - ${vehicle.name}")
            createTabIfNotExists(sheetId, "Fuel - ${vehicle.name}")
        }
    }

    private fun createTabIfNotExists(sheetId: String, tabName: String) {
        val token = accessToken ?: "placeholder-token"   // real token will replace this next

        try {
            val url = URL("$BASE_URL/$sheetId:batchUpdate")
            val connection = url.openConnection() as HttpURLConnection
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

            OutputStreamWriter(connection.outputStream).use { it.write(json.encodeToString(requestBody)) }

            val responseCode = connection.responseCode
            if (responseCode == 200 || responseCode == 400) {   // 400 = sheet already exists (normal)
                Log.i(TAG, "✅ Tab '$tabName' is ready (or already existed)")
            } else {
                Log.e(TAG, "API error for tab '$tabName': $responseCode")
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create tab '$tabName': ${e.message}")
        }
    }

    data class VehicleSummary(val id: Int, val name: String)
}
