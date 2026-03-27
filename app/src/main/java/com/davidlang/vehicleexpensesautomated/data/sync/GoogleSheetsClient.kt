package com.davidlang.vehicleexpensesautomated.data.sync

import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log

class GoogleSheetsClient(private val idToken: String?) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun pushAllData(sheetId: String, vehicles: List<Vehicle>, expenses: List<ExpenseEntry>, fuelEntries: List<FuelEntry>): Int = withContext(Dispatchers.IO) {
        if (sheetId.isBlank() || idToken == null) return@withContext 0
        var pushed = 0
        Log.i("GoogleSheetsClient", "Pushing ${vehicles.size} vehicles, ${expenses.size} expenses, ${fuelEntries.size} fuel entries")

        pushed += syncVehicles(sheetId, vehicles)
        pushed += syncExpenses(sheetId, expenses)
        pushed += syncFuelEntries(sheetId, fuelEntries)

        Log.i("GoogleSheetsClient", "Push complete ($pushed items)")
        pushed
    }

    private suspend fun syncVehicles(sheetId: String, vehicles: List<Vehicle>): Int = withContext(Dispatchers.IO) {
        if (vehicles.isEmpty()) return@withContext 0
        createTabWithHeaders(sheetId, "Vehicles", listOf("ID", "Name", "Make", "Model", "Year", "License Plate", "VIN", "Notes"))
        appendRows(sheetId, "Vehicles", vehicles.map { listOf(it.id.toString(), it.name, it.make ?: "", it.model ?: "", it.year?.toString() ?: "", it.licensePlate ?: "", it.vin ?: "", it.notes ?: "") })
    }

    private suspend fun syncExpenses(sheetId: String, expenses: List<ExpenseEntry>): Int = withContext(Dispatchers.IO) {
        if (expenses.isEmpty()) return@withContext 0
        createTabWithHeaders(sheetId, "Expenses", listOf("ID", "Vehicle ID", "Amount", "Description", "Date"))
        appendRows(sheetId, "Expenses", expenses.map { listOf(it.id.toString(), it.vehicleId.toString(), it.amount.toString(), it.description, it.date.toString()) })
    }

    private suspend fun syncFuelEntries(sheetId: String, fuelEntries: List<FuelEntry>): Int = withContext(Dispatchers.IO) {
        if (fuelEntries.isEmpty()) return@withContext 0
        createTabWithHeaders(sheetId, "Fuel Entries", listOf("ID", "Vehicle ID", "Odometer", "Gallons", "Cost", "Timestamp"))
        appendRows(sheetId, "Fuel Entries", fuelEntries.map { listOf(it.id.toString(), it.vehicleId.toString(), it.odometer.toString(), it.gallons.toString(), it.cost.toString(), it.timestamp.toString()) })
    }

    private suspend fun pullVehicles(sheetId: String): List<Vehicle> = readTab(sheetId, "Vehicles") { row ->
        Vehicle(
            id = row[0].toIntOrNull() ?: 0,
            name = row[1],
            make = row[2].ifBlank { null },
            model = row[3].ifBlank { null },
            year = row[4].toIntOrNull(),
            licensePlate = row[5].ifBlank { null },
            vin = row[6].ifBlank { null },
            notes = row[7].ifBlank { null }
        )
    }

    private fun createTabWithHeaders(sheetId: String, tabName: String, headers: List<String>) {}
    private suspend fun appendRows(sheetId: String, tab: String, rows: List<List<String>>): Int = 0
    private suspend fun readTab(sheetId: String, tab: String, mapper: (List<String>) -> Vehicle): List<Vehicle> = emptyList()
}
