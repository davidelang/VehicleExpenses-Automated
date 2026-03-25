package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val vehicleRepository: VehicleRepository,
    private val fuelRepository: FuelEntryRepository,
    private val expenseRepository: ExpenseEntryRepository,
    private val googleSheetsClient: GoogleSheetsClient
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
            val sheetId = prefs.getString("sheet_id", null) ?: return@withContext Result.failure()

            if (!prefs.getBoolean("sync_enabled", false)) return@withContext Result.success()

            // Full bidirectional sync using exact repository methods
            googleSheetsClient.pushVehicles(vehicleRepository.getAllVehicles().first(), sheetId)
            googleSheetsClient.pushFuelEntries(fuelRepository.getAllEntries().first(), sheetId)
            googleSheetsClient.pushExpenseEntries(expenseRepository.getAllEntries().first(), sheetId)

            // Pull back
            val pulledVehicles = googleSheetsClient.pullVehicles(sheetId)
            pulledVehicles.forEach { vehicleRepository.insertVehicle(it) }

            val pulledFuel = googleSheetsClient.pullFuelEntries(sheetId)
            pulledFuel.forEach { fuelRepository.insertFuelEntry(it) }

            val pulledExpenses = googleSheetsClient.pullExpenseEntries(sheetId)
            pulledExpenses.forEach { expenseRepository.insertExpenseEntry(it) }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
