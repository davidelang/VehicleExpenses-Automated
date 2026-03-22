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

    suspend fun syncAllData(
        sheetId: String,
        vehicles: List<VehicleSummary>,
        allExpenses: Map<Int, List<Expense>>,
        allFuelFills: Map<Int, List<FuelFill>>
    ) = withContext(Dispatchers.IO) {
        if (sheetId.isBlank() || idToken == null) {
            Log.w(TAG, "Missing sheet ID or token")
            return@withContext
        }

        Log.i(TAG, "🚀 TWO-WAY SYNC: Writing + Reading for ${vehicles.size} vehicles")

        vehicles.forEach { vehicle ->
            val expenseTab = "Expenses - ${vehicle.name}"
            val fuelTab = "Fuel - ${vehicle.name}"

            createTabWithHeaders(sheetId, expenseTab, listOf("Date", "Amount", "Category", "Description", "Receipt"))
            createTabWithHeaders(sheetId, fuelTab, listOf("Date", "Gallons", "Price/Gallon", "Total Cost", "Odometer", "Fuel Type", "Notes"))

            allExpenses[vehicle.id]?.let { appendRealExpenseRows(sheetId, expenseTab, it) }
            allFuelFills[vehicle.id]?.let { appendRealFuelRows(sheetId, fuelTab, it) }

            readAndParseExpenseRows(sheetId, expenseTab, vehicle.id)
            readAndParseFuelRows(sheetId, fuelTab, vehicle.id)
        }
    }

    private fun createTabWithHeaders(sheetId: String, tabName: String, headers: List<String>) {
        val token = idToken ?: return
        try {
            val batchUrl = URL("$BASE_URL/$sheetId:batchUpdate")
            val conn = batchUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = buildJsonObject {
                putJsonArray("requests") {
                    addJsonObject {
                        putJsonObject("addSheet") {
                            putJsonObject("properties") { put("title", tabName) }
                        }
                    }
                }
            }
            OutputStreamWriter(conn.outputStream).use { it.write(json.encodeToString(JsonObject.serializer(), body)) }
            conn.responseCode
            conn.disconnect()

            val headerUrl = URL("$BASE_URL/$sheetId/values/$tabName!A1:append?valueInputOption=RAW")
            val hConn = headerUrl.openConnection() as HttpURLConnection
            hConn.requestMethod = "POST"
            hConn.setRequestProperty("Authorization", "Bearer $token")
            hConn.setRequestProperty("Content-Type", "application/json")
            hConn.doOutput = true

            val headerBody = buildJsonObject {
                put("values", buildJsonArray { addJsonArray { headers.forEach { add(it) } } })
            }
            OutputStreamWriter(hConn.outputStream).use { it.write(json.encodeToString(JsonObject.serializer(), headerBody)) }
            hConn.responseCode
            hConn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Tab setup failed for $tabName: ${e.message}")
        }
    }

    private fun appendRealExpenseRows(sheetId: String, tabName: String, expenses: List<Expense>) {
        val token = idToken ?: return
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tabName!A2:append?valueInputOption=RAW")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val rows = buildJsonArray {
                expenses.forEach { e ->
                    addJsonArray {
                        add(dateFormat.format(Date(e.dateMillis)))
                        add(e.amount)
                        add(e.category)
                        add(e.description ?: "")
                        add(e.receiptPath ?: "")
                    }
                }
            }
            val body = buildJsonObject { put("values", rows) }
            OutputStreamWriter(conn.outputStream).use { it.write(json.encodeToString(JsonObject.serializer(), body)) }
            if (conn.responseCode == 200) Log.i(TAG, "✅ ${expenses.size} expenses written")
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Expense write failed: ${e.message}")
        }
    }

    private fun appendRealFuelRows(sheetId: String, tabName: String, fuelFills: List<FuelFill>) {
        val token = idToken ?: return
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tabName!A2:append?valueInputOption=RAW")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

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
            OutputStreamWriter(conn.outputStream).use { it.write(json.encodeToString(JsonObject.serializer(), body)) }
            if (conn.responseCode == 200) Log.i(TAG, "✅ ${fuelFills.size} fuel fills written")
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Fuel write failed: ${e.message}")
        }
    }

    private fun readAndParseExpenseRows(sheetId: String, tabName: String, vehicleId: Int) {
        val token = idToken ?: return
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tabName!A2:Z")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode == 200) {
                val response = json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
                val values = response["values"]?.jsonArray ?: return
                Log.i(TAG, "✅ Read ${values.size} expense rows from $tabName")
                // Parsed objects ready for Room merge (next step)
                values.forEach { row ->
                    val r = row.jsonArray
                    if (r.size >= 4) {
                        Log.i(TAG, "Parsed expense: ${r[0]} | ${r[1]} | ${r[2]}")
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Read expense failed: ${e.message}")
        }
    }

    private fun readAndParseFuelRows(sheetId: String, tabName: String, vehicleId: Int) {
        val token = idToken ?: return
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tabName!A2:Z")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode == 200) {
                val response = json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
                val values = response["values"]?.jsonArray ?: return
                Log.i(TAG, "✅ Read ${values.size} fuel rows from $tabName")
                values.forEach { row ->
                    val r = row.jsonArray
                    if (r.size >= 5) {
                        Log.i(TAG, "Parsed fuel: ${r[0]} | ${r[1]} gal | ${r[4]} miles")
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Read fuel failed: ${e.message}")
        }
    }

    data class VehicleSummary(val id: Int, val name: String)
}
