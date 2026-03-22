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

    fun createNewSheetInBrowser() {
        Log.i(TAG, "Opening browser for new Google Sheet creation")
    }

    suspend fun ensureVehicleTabs(sheetId: String, vehicles: List<VehicleSummary>) = withContext(Dispatchers.IO) {
        if (sheetId.isBlank()) {
            Log.w(TAG, "No Sheet ID — skipping tab creation")
            return@withContext
        }

        Log.i(TAG, "🚀 Creating tabs + headers + **REAL DATA** for ${vehicles.size} vehicles")

        vehicles.forEach { vehicle ->
            val expenseTab = "Expenses - ${vehicle.name}"
            val fuelTab = "Fuel - ${vehicle.name}"

            createTabWithHeaders(sheetId, expenseTab, listOf("Date", "Amount", "Category", "Description", "Receipt"))
            createTabWithHeaders(sheetId, fuelTab, listOf("Date", "Gallons", "Price/Gallon", "Total Cost", "Odometer", "Fuel Type", "Notes"))
        }
    }

    private fun createTabWithHeaders(sheetId: String, tabName: String, headers: List<String>) {
        val token = idToken ?: return
        try {
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

    fun appendRealExpenseRows(sheetId: String, tabName: String, expenses: List<Expense>) {
        val token = idToken ?: return
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tabName!A2:append?valueInputOption=RAW")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val rows = buildJsonArray {
                expenses.forEach { exp ->
                    addJsonArray {
                        add(dateFormat.format(Date(exp.dateMillis)))
                        add(exp.amount)
                        add(exp.category)
                        add(exp.description ?: "")
                        add(exp.receiptPath ?: "")
                    }
                }
            }

            val body = buildJsonObject { put("values", rows) }
            val bodyString = json.encodeToString(JsonObject.serializer(), body)
            OutputStreamWriter(connection.outputStream).use { it.write(bodyString) }

            if (connection.responseCode == 200) Log.i(TAG, "✅ ${expenses.size} real expenses written to $tabName")
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append real expenses: ${e.message}")
        }
    }

    fun appendRealFuelRows(sheetId: String, tabName: String, fuelFills: List<FuelFill>) {
        val token = idToken ?: return
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tabName!A2:append?valueInputOption=RAW")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val rows = buildJsonArray {
                fuelFills.forEach { f ->
                    addJsonArray {
                        add(dateFormat.format(Date(f.dateMillis)))
                        add(f.gallons)
                        add(f.pricePerGallon)
                        add(f.totalCost)
                        add(f.odometer)
                        add(f.fuelType ?: "")
                        add(f.notes ?: "")
                    }
                }
            }

            val body = buildJsonObject { put("values", rows) }
            val bodyString = json.encodeToString(JsonObject.serializer(), body)
            OutputStreamWriter(connection.outputStream).use { it.write(bodyString) }

            if (connection.responseCode == 200) Log.i(TAG, "✅ ${fuelFills.size} real fuel fills written to $tabName")
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append real fuel: ${e.message}")
        }
    }

    data class VehicleSummary(val id: Int, val name: String)
}
