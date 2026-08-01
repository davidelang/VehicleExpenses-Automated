package com.davidlang.vehicleexpensesautomated.data.location

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Enqueue deferred location POI fill (network + exponential backoff).
 */
object LocationLookupScheduler {
    private const val TAG = "LocationLookupSched"
    const val UNIQUE_ONE_SHOT = "location_lookup_oneshot"
    const val UNIQUE_PERIODIC = "location_lookup_periodic"

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    /** After save when coords present and place empty. */
    fun enqueueSoon(context: Context) {
        try {
            val req = OneTimeWorkRequestBuilder<LocationLookupWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_SHOT,
                ExistingWorkPolicy.KEEP,
                req,
            )
            Log.i(TAG, "Enqueued one-shot location lookup")
        } catch (e: Exception) {
            Log.w(TAG, "enqueueSoon failed: ${e.message}")
        }
    }

    /** Periodic unique work (e.g. cold start) — 6h period. */
    fun ensurePeriodic(context: Context) {
        try {
            val req = PeriodicWorkRequestBuilder<LocationLookupWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
            Log.i(TAG, "Ensured periodic location lookup")
        } catch (e: Exception) {
            Log.w(TAG, "ensurePeriodic failed: ${e.message}")
        }
    }
}
