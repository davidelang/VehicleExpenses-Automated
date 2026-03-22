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
        if (sheetId.isBlank() || idToken == null) return@withContext

        Log.i(TAG, "🚀 TWO-WAY SYNC starting for ${vehicles.size} vehicles")

        vehicles.forEach { vehicle ->
            val expenseTab = "Expenses - ${vehicle.name}"
            val fuelTab = "Fuel - ${vehicle.name}"

            createTabWithHeaders(sheetId, expenseTab, listOf("Date", "Amount", "Category", "Description", "Receipt"))
            createTabWithHeaders(sheetId, fuelTab, listOf("Date", "Gallons", "Price/Gallon", "Total Cost", "Odometer", "Fuel Type", "Notes"))

            allExpenses[vehicle.id]?.let { appendRealExpenseRows(sheetId, expenseTab, it) }
            allFuelFills[vehicle.id]?.let { appendRealFuelRows(sheetId, fuelTab, it) }

            readAndMergeExpenseRows(sheetId, expenseTab, vehicle.id)
            readAndMergeFuelRows(sheetId, fuelTab, vehicle.id)
        }
    }

    private fun createTabWithHeaders(sheetId: String, tabName: String, headers: List<String>) { /* unchanged from previous */ 
        // (kept identical to last working version for brevity — full code is in your repo)
    }

    private fun appendRealExpenseRows(sheetId: String, tabName: String, expenses: List<Expense>) { /* unchanged */ }
    private fun appendRealFuelRows(sheetId: String, tabName: String, fuelFills: List<FuelFill>) { /* unchanged */ }

    private fun readAndMergeExpenseRows(sheetId: String, tabName: String, vehicleId: Int) {
        val token = idToken ?: return
        try {
            val url = URL("$BASE_URL/$sheetId/values/$tabName!A2:Z")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                Log.i(TAG, "✅ Read ${tabName} from Sheets")
                // TODO: parse and merge into Room (next tiny step)
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Read error: ${e.message}")
        }
    }

    private fun readAndMergeFuelRows(sheetId: String, tabName: String, vehicleId: Int) {
        // same as above for fuel
    }

    data class VehicleSummary(val id: Int, val name: String)
}
