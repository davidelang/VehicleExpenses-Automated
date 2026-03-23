package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        println("🔄 [SyncWorker stub] Google Sheets sync placeholder – real logic later")
        return Result.success()
    }
}
