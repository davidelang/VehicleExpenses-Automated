package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidlang.vehicleexpensesautomated.data.network.GoogleSheetsClient
import com.davidlang.vehicleexpensesautomated.data.repository.FuelRepository
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.first

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val googleSheetsClient: GoogleSheetsClient,
    private val fuelRepository: FuelRepository,
    private val expenseRepository: ExpenseRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val prefs = appContext.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
            val sheetId = prefs.getString("sheet_id", "") ?: ""

            if (sheetId.isBlank()) return Result.success()

            val fuelFills = fuelRepository.getAllFuelFills().first()
            val expenses = expenseRepository.getAllExpenses().first() // assume you have this method

            val (pushedExpenses, pushedFuel) = googleSheetsClient.syncAllData(sheetId, expenses, fuelFills)

            Log.i("SyncWorker", "✅ Synced $pushedExpenses expenses + $pushedFuel fuel fills")
            return Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            return Result.retry()
        }
    }
}
