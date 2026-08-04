package com.davidlang.vehicleexpensesautomated.data.email

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.davidlang.vehicleexpensesautomated.data.sync.SyncScheduleJitter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailReceiptManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun rescheduleFromPrefs() {
        val prefs = EmailReceiptPrefs(context)
        if (!prefs.enabled || prefs.labelName.isBlank()) {
            cancel()
            return
        }
        schedulePeriodic()
    }

    fun schedulePeriodic() {
        val workManager = WorkManager.getInstance(context)
        val periodMinutes = 15L
        val flexMinutes = SyncScheduleJitter.flexMinutes(periodMinutes)
        val request = PeriodicWorkRequestBuilder<EmailReceiptWorker>(
            periodMinutes,
            TimeUnit.MINUTES,
            flexMinutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(2, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
        Log.i(TAG, "scheduled periodic email receipt poll ($periodMinutes min)")
    }

    fun enqueueOneShot() {
        val request = OneTimeWorkRequestBuilder<EmailReceiptWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(UNIQUE_PERIODIC)
        wm.cancelUniqueWork(UNIQUE_ONESHOT)
        Log.i(TAG, "cancelled email receipt workers")
    }

    companion object {
        private const val TAG = "EmailReceiptManager"
        const val UNIQUE_PERIODIC = "email_receipt_poll_periodic"
        const val UNIQUE_ONESHOT = "email_receipt_poll_oneshot"
    }
}
