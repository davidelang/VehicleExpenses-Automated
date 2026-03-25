package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsClient @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private suspend fun getSheetsService(): Sheets = withContext(Dispatchers.IO) {
        val account: GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) throw IllegalStateException("No Google account signed in for sync")
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf("https://www.googleapis.com/auth/spreadsheets"))
        credential.selectedAccount = account.account
        Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("VehicleExpenses-Automated")
            .build()
    }

    suspend fun pushVehicles(vehicles: List<Vehicle>, sheetId: String) = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        val values = mutableListOf<List<Any>>()
        values.add(listOf("ID", "Make", "Model", "Year", "License Plate", "VIN", "Notes"))
        vehicles.forEach {
            values.add(listOf(it.id, it.make, it.model, it.year, it.licensePlate, it.vin ?: "", it.notes ?: ""))
        }
        val body = ValueRange().setValues(values)
        service.spreadsheets().values().update(sheetId, "Vehicles!A:Z", body)
            .setValueInputOption("RAW")
            .execute()
    }

    suspend fun pushFuelEntries(entries: List<FuelEntry>, sheetId: String) = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        val values = mutableListOf<List<Any>>()
        values.add(listOf("ID", "Vehicle ID", "Odometer", "Gallons", "Cost", "Timestamp", "Photo URL", "Partial Fill"))
        entries.forEach {
            values.add(listOf(it.id, it.vehicleId, it.odometer, it.gallons, it.cost, it.timestamp, it.photoUrl ?: "", it.isPartialFill))
        }
        val body = ValueRange().setValues(values)
        service.spreadsheets().values().update(sheetId, "Fuel_entries!A:Z", body)
            .setValueInputOption("RAW")
            .execute()
    }

    suspend fun pushExpenseEntries(entries: List<ExpenseEntry>, sheetId: String) = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        val values = mutableListOf<List<Any>>()
        values.add(listOf("ID", "Vehicle ID", "Date", "Amount", "Category", "Description", "Receipt Image Path"))
        entries.forEach {
            values.add(listOf(it.id, it.vehicleId, it.date, it.amount, it.category, it.description, it.receiptImagePath ?: ""))
        }
        val body = ValueRange().setValues(values)
        service.spreadsheets().values().update(sheetId, "Expense_entries!A:Z", body)
            .setValueInputOption("RAW")
            .execute()
    }

    suspend fun pullVehicles(sheetId: String): List<Vehicle> = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        val response = service.spreadsheets().values().get(sheetId, "Vehicles!A:Z").execute()
        val rows = response.getValues() ?: return@withContext emptyList()
        val vehicles = mutableListOf<Vehicle>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.size >= 7) {
                vehicles.add(Vehicle(
                    id = row[0].toString().toIntOrNull() ?: 0,
                    make = row[1].toString(),
                    model = row[2].toString(),
                    year = row[3].toString().toIntOrNull() ?: 0,
                    licensePlate = row[4].toString(),
                    vin = row[5].toString().ifBlank { null },
                    notes = row[6].toString().ifBlank { null }
                ))
            }
        }
        vehicles
    }

    suspend fun pullFuelEntries(sheetId: String): List<FuelEntry> = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        val response = service.spreadsheets().values().get(sheetId, "Fuel_entries!A:Z").execute()
        val rows = response.getValues() ?: return@withContext emptyList()
        val entries = mutableListOf<FuelEntry>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.size >= 8) {
                entries.add(FuelEntry(
                    id = row[0].toString().toLongOrNull() ?: 0,
                    vehicleId = row[1].toString().toIntOrNull() ?: 0,
                    odometer = row[2].toString().toIntOrNull() ?: 0,
                    gallons = row[3].toString().toDoubleOrNull() ?: 0.0,
                    cost = row[4].toString().toDoubleOrNull() ?: 0.0,
                    timestamp = row[5].toString().toLongOrNull() ?: System.currentTimeMillis(),
                    photoUrl = row[6].toString().ifBlank { null },
                    isPartialFill = row[7].toString().toBoolean()
                ))
            }
        }
        entries
    }

    suspend fun pullExpenseEntries(sheetId: String): List<ExpenseEntry> = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        val response = service.spreadsheets().values().get(sheetId, "Expense_entries!A:Z").execute()
        val rows = response.getValues() ?: return@withContext emptyList()
        val entries = mutableListOf<ExpenseEntry>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.size >= 7) {
                entries.add(ExpenseEntry(
                    id = row[0].toString().toLongOrNull() ?: 0,
                    vehicleId = row[1].toString().toIntOrNull() ?: 0,
                    date = row[2].toString().toLongOrNull() ?: System.currentTimeMillis(),
                    amount = row[3].toString().toDoubleOrNull() ?: 0.0,
                    category = row[4].toString(),
                    description = row[5].toString(),
                    receiptImagePath = row[6].toString().ifBlank { null }
                ))
            }
        }
        entries
    }
}
