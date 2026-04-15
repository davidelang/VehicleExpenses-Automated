package com.davidlang.vehicleexpensesautomated

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.davidlang.vehicleexpensesautomated.data.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltAndroidApp
class VehicleExpensesApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() {
            android.util.Log.i("VehicleExpensesApp", "workManagerConfiguration requested. workerFactory initialized: ${::workerFactory.isInitialized}")
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
        }

    override fun onCreate() {
        android.util.Log.i("VehicleExpensesApp", "onCreate started")
        super.onCreate()
        android.util.Log.i("VehicleExpensesApp", "super.onCreate completed. workerFactory initialized: ${::workerFactory.isInitialized}")
        
        copyTessdataOnce(this)
        try {
            android.util.Log.i("VehicleExpensesApp", "Initializing SyncManager")
            val syncManager = SyncManager(this)
            syncManager.schedulePeriodicSync()
            syncManager.triggerImmediateSync()
            android.util.Log.i("VehicleExpensesApp", "SyncManager initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("VehicleExpensesApp", "Failed to initialize SyncManager", e)
        }
    }

    private fun copyTessdataOnce(context: Context) {
        val filesDir = File(context.filesDir, "tessdata")
        filesDir.mkdirs()
        try {
            val assetManager = context.assets
            val input = assetManager.open("tessdata/eng.traineddata")
            val output = FileOutputStream(File(filesDir, "eng.traineddata"))
            input.copyTo(output)
            input.close()
            output.close()
            android.util.Log.i("OdometerOcr", "✅ eng.traineddata copied to ${filesDir.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("OdometerOcr", "Failed to copy eng.traineddata", e)
        }
    }
}
