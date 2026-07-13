package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoBackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun scheduleFromDestination() {
        val store = SyncDestinationStore(context)
        val enabled = store.enabledPhoto()
        val workManager = WorkManager.getInstance(context)

        if (enabled.isEmpty()) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val wifiOnly = enabled.any { it.wifiOnly }
        val chargingOnly = enabled.any { it.chargingOnly }
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresCharging(chargingOnly)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodMinutes = enabled.minOf { it.resolvedFrequencyMinutes() }.toLong()
        val initialDelayMinutes = minOf(2L, periodMinutes)
        val request = PeriodicWorkRequestBuilder<PhotoBackupWorker>(periodMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
    }

    fun triggerImmediateBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PhotoBackupWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "vehicle_expenses_photo_backup"
    }
}