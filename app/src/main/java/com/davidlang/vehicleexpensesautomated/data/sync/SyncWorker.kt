package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidlang.vehicleexpensesautomated.data.network.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val vehicleRepository: VehicleRepository,
    private val expenseRepository: ExpenseEntryRepository,
    private val fuelRepository: FuelEntryRepository,
    private val googleSheetsClient: GoogleSheetsClient
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
            val sheetId = prefs.getString("sheet_id", "") ?: ""
            if (sheetId.isBlank()) {
                Log.w("SyncWorker", "No sheet_id — skipping sync")
                return Result.success()
            }

            googleSheetsClient.idToken = prefs.getString("id_token", null)

            // 1. PUSH local → Sheets
            val vehicles = vehicleRepository.getAllVehicles().first()
            val expenses = expenseRepository.getAllEntries().first()
            val fuelEntries = fuelRepository.getAllEntries().first()
            val pushed = googleSheetsClient.syncAllData(sheetId, vehicles, expenses, fuelEntries)

            // 2. PULL Sheets → local (for future merge logic)
            val (pulledVehicles, pulledExpenses, pulledFuel) = googleSheetsClient.pullAllData(sheetId)

            Log.i("SyncWorker", "✅ Bidirectional sync complete (pushed $pushed items, pulled ${pulledVehicles.size + pulledExpenses.size + pulledFuel.size} items)")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}
