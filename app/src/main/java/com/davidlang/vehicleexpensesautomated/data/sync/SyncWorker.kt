package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidlang.vehicleexpensesautomated.data.network.GoogleSheetsClient
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
            if (sheetId.isBlank()) {
                Log.w("SyncWorker", "No sheet ID — skipping background sync")
                return@withContext Result.success()
            }

            Log.i("SyncWorker", "🔄 Periodic background sync running for sheet $sheetId")
            val client = GoogleSheetsClient()
            // Token will be injected from signed-in account in final version
            client.idToken = "" // placeholder — real token from prefs in production
            val dummyVehicles = listOf(
                GoogleSheetsClient.VehicleSummary(1, "Toyota Camry 2023"),
                GoogleSheetsClient.VehicleSummary(2, "Honda Civic 2022")
            )
            client.syncAllData(sheetId, dummyVehicles)
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed", e)
            Result.retry()
        }
    }
}
