package com.davidlang.vehicleexpensesautomated.data.network

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleSheetsClient {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    companion object {
        private const val TAG = "GoogleSheetsClient"
    }

    var accessToken: String? = null

    fun createNewSheetInBrowser() {
        Log.i(TAG, "Opening browser for new Google Sheet creation")
    }

    suspend fun ensureVehicleTabs(sheetId: String, vehicles: List<VehicleSummary>) = withContext(Dispatchers.IO) {
        if (sheetId.isBlank()) {
            Log.w(TAG, "No Sheet ID — skipping tab creation")
            return@withContext
        }

        Log.i(TAG, "Ensuring tabs for ${vehicles.size} vehicles in sheet $sheetId")

        vehicles.forEach { vehicle ->
            createTabIfNotExists(sheetId, "Expenses - ${vehicle.name}")
            createTabIfNotExists(sheetId, "Fuel - ${vehicle.name}")
        }
    }

    private fun createTabIfNotExists(sheetId: String, tabName: String) {
        Log.i(TAG, "✅ Tab ready (or would be created): '$tabName' in sheet $sheetId")
    }

    data class VehicleSummary(val id: Int, val name: String)
}
