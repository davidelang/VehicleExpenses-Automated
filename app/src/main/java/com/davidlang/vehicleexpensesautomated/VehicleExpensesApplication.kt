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
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        copyTessdataOnce(this)
        try {
            val syncManager = SyncManager(this)
            syncManager.schedulePeriodicSync()
            syncManager.triggerImmediateSync()
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    private fun copyTessdataOnce(context: Context) {
        val filesDir = File(context.filesDir, "tessdata")
        if (filesDir.exists() && filesDir.listFiles()?.isNotEmpty() == true) return
        filesDir.mkdirs()
        try {
            val assetManager = context.assets
            val input = assetManager.open("tessdata/eng.traineddata")
            val output = FileOutputStream(File(filesDir, "eng.traineddata"))
            input.copyTo(output)
            input.close()
            output.close()
            android.util.Log.i("OdometerOcr", "Copied eng.traineddata from assets to ${filesDir.absolutePath} at app startup")
        } catch (e: Exception) {
            android.util.Log.e("OdometerOcr", "Failed to copy traineddata from assets", e)
        }
    }
}
