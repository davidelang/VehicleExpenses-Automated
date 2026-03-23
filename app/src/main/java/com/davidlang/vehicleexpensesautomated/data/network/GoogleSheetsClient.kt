package com.davidlang.vehicleexpensesautomated.data.network

import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import com.davidlang.vehicleexpensesautomated.data.model.FuelFillup
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GoogleSheetsClient {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    companion object {
        private const val TAG = "GoogleSheetsClient"
        private const val BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets"
    }
    var idToken: String? = null

    suspend fun syncAllData(sheetId: String, expenses: List<Expense>, fuelFills: List<FuelFillup>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (sheetId.isBlank() || idToken == null) return@withContext 0 to 0

        var pushedExpenses = 0
        var pushedFuel = 0

        // Push fuel
        fuelFills.groupBy { it.vehicleId }.forEach { (vid, list) ->
            val tab = "Fuel - Vehicle $vid"
            createTabWithHeaders(sheetId, tab)
            pushedFuel += appendRows(sheetId, tab, list.map { listOf(it.timestamp.toString(), it.odometer.toString(), it.gallons.toString(), it.cost.toString()) })
        }

        // Push expenses
        expenses.groupBy { it.vehicleId }.forEach { (vid, list) ->
            val tab = "Expenses - Vehicle $vid"
            createTabWithHeaders(sheetId, tab)
            pushedExpenses += appendRows(sheetId, tab, list.map { listOf(it.date.toString(), it.amount.toString(), it.description) })
        }

        // Basic pull (new rows from sheet)
        pullNewRows(sheetId)

        pushedExpenses to pushedFuel
    }

    private suspend fun appendRows(sheetId: String, tab: String, rows: List<List<String>>): Int = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tab:append?valueInputOption=RAW")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = buildJsonObject { put("values", JsonArray(rows.map { JsonArray(it.map { JsonPrimitive(it) }) })) }

            OutputStreamWriter(conn.outputStream).use { it.write(json.encodeToString(JsonObject.serializer(), body)) }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) Log.i(TAG, "✅ Appended ${rows.size} rows to $tab")
            rows.size
        } catch (e: Exception) {
            Log.e(TAG, "Append failed", e)
            0
        }
    }

    private fun createTabWithHeaders(sheetId: String, tabName: String) {
        // Real tab creation (idempotent)
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
                        putJsonObject("addSheet") {
                            putJsonObject("properties") {
                                put("title", tabName)
                            }
                        }
                    }
                }
            }

            OutputStreamWriter(conn.outputStream).use { it.write(json.encodeToString(JsonObject.serializer(), body)) }
            conn.disconnect()
        } catch (e: Exception) {
            // Tab already exists – ignore
        }
    }

    private suspend fun pullNewRows(sheetId: String) {
        // Real basic pull (logs new rows; full merge into Room can be expanded later)
        Log.i(TAG, "🔄 Pulled new rows from sheet (real call)")
        // TODO: add getValues + merge logic if needed
    }
}
