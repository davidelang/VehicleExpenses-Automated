package com.davidlang.vehicleexpensesautomated

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.davidlang.vehicleexpensesautomated.data.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VehicleExpensesApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        try {
            val syncManager = SyncManager(this)
            syncManager.schedulePeriodicSync()
            syncManager.triggerImmediateSync()
        } catch (e: Exception) {
            // Safe fallback on very old Android — sync is non-critical for launch
        }
    }
}
