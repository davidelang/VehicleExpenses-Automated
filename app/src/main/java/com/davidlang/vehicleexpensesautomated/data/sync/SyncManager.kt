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
class SyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun schedulePeriodicSync() = scheduleFromDestination()

    /** Schedule one periodic worker: min frequency + strictest constraints across enabled spreadsheet dests. */
    fun scheduleFromDestination() {
        val store = SyncDestinationStore(context)
        val enabled = store.enabledSpreadsheet()
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
        val initialDelayMinutes = SyncScheduleJitter.initialDelayMinutes(context, periodMinutes)
        val flexMinutes = SyncScheduleJitter.flexMinutes(periodMinutes)
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(periodMinutes, TimeUnit.MINUTES, flexMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            syncRequest,
        )
    }

    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(oneTimeRequest)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "vehicle_expenses_sync"
    }
}