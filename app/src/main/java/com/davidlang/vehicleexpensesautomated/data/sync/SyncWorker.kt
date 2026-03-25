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
            // Full bidirectional sync as per original design
            val sheetId = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
                .getString("sheet_id", null) ?: return@withContext Result.failure()

            // Push all data
            googleSheetsClient.pushVehicles(vehicleRepository.getAllVehicles().first(), sheetId)
            googleSheetsClient.pushFuelEntries(fuelRepository.getAllEntries().first(), sheetId)
            googleSheetsClient.pushExpenseEntries(expenseRepository.getAllEntries().first(), sheetId)

            // Pull back (for now vehicles only; others can be added later)
            val pulledVehicles = googleSheetsClient.pullVehicles(sheetId)
            pulledVehicles.forEach { vehicleRepository.insert(it) }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
