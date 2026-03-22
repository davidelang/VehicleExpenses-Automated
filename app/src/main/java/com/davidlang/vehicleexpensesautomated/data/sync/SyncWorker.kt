package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidlang.vehicleexpensesautomated.data.network.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
            val sheetId = prefs.getString("sheet_id", "") ?: ""
            if (sheetId.isBlank()) return@withContext Result.failure()

            // Reuse the same sync logic (dummy vehicles for now — real Room data in final step)
            val client = GoogleSheetsClient()
            client.idToken = "" // token is set at runtime from signed-in account
            val dummyVehicles = listOf(
                GoogleSheetsClient.VehicleSummary(1, "Toyota Camry 2023"),
                GoogleSheetsClient.VehicleSummary(2, "Honda Civic 2022")
            )
            client.syncAllData(sheetId, dummyVehicles)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
