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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val vehicleRepository: VehicleRepository,
    private val fuelRepository: FuelEntryRepository,
    private val expenseRepository: ExpenseEntryRepository,
    private val googleSheetsClient: GoogleSheetsClient
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = appContext.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
            val sheetId = prefs.getString("sheet_id", null) ?: return@withContext Result.failure()
            if (!prefs.getBoolean("sync_enabled", false)) return@withContext Result.success()

            // Full push using the ONLY public method that exists
            val vehicles = vehicleRepository.getAllVehicles().first()
            val expenses = expenseRepository.getAllEntries().first()
            val fuelEntries = fuelRepository.getAllEntries().first()
            googleSheetsClient.pushAllData(sheetId, vehicles, expenses, fuelEntries)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
