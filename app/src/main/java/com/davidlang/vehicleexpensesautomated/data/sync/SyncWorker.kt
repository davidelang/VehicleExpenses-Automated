package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidlang.vehicleexpensesautomated.data.network.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.repository.FuelRepository
import kotlinx.coroutines.flow.first

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    private val googleSheetsClient = GoogleSheetsClient()
    private val fuelRepository = FuelRepository(/* DAO from Hilt */)

    override suspend fun doWork(): Result {
        try {
            val prefs = applicationContext.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
            val sheetId = prefs.getString("sheet_id", "") ?: ""

            if (sheetId.isBlank()) return Result.success()

            val fuelFills = fuelRepository.getAllFuelFills().first()

            val pushed = googleSheetsClient.syncFuelFills(sheetId, fuelFills)

            Log.i("SyncWorker", "✅ Synced $pushed fuel fills to Google Sheets")
            return Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            return Result.retry()
        }
    }
}
