package com.davidlang.vehicleexpensesautomated.data.network

import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.FuelFillup
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

    suspend fun syncFuelFills(sheetId: String, fuelFills: List<FuelFillup>): Int = withContext(Dispatchers.IO) {
        if (sheetId.isBlank() || idToken == null) return@withContext 0

        var pushed = 0
        fuelFills.groupBy { it.vehicleId }.forEach { (vid, list) ->
            val tab = "Fuel - Vehicle $vid"
            createTabIfNeeded(sheetId, tab)
            pushed += appendFuelRows(sheetId, tab, list)
        }
        pushed
    }

    private suspend fun appendFuelRows(sheetId: String, tab: String, rows: List<FuelFillup>): Int = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tab:append?valueInputOption=RAW")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val values = rows.map { listOf(it.timestamp.toString(), it.odometer.toString(), it.gallons.toString(), it.cost.toString()) }
            val body = buildJsonObject {
                put("values", JsonArray(values.map { JsonArray(it.map { JsonPrimitive(it) }) }))
            }

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

    private fun createTabIfNeeded(sheetId: String, tabName: String) {
        // Stub – works with your existing sheet
    }
}
