package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
                Log.w("SyncWorker", "No sheet ID configured")
                return@withContext Result.failure()
            }

            Log.i("SyncWorker", "Periodic background sync running for sheet $sheetId")
            // Real data sync call will be wired in the final step
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed", e)
            Result.retry()
        }
    }
}
