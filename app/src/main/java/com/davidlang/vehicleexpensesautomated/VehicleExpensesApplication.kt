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
        copyPaddleOcrOnce(this)   // ← new
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

    private fun copyPaddleOcrOnce(context: Context) {
        val destFile = File(context.filesDir, "paddleocr.onnx")
        if (destFile.exists() && destFile.length() > 100000) return  // already good
        try {
            val assetManager = context.assets
            val input = assetManager.open("paddleocr.onnx")
            val output = FileOutputStream(destFile)
            input.copyTo(output)
            input.close()
            output.close()
            android.util.Log.i("OdometerOcr", "✅ paddleocr.onnx copied to ${destFile.absolutePath} (${destFile.length()} bytes)")
        } catch (e: Exception) {
            android.util.Log.e("OdometerOcr", "Failed to copy paddleocr.onnx", e)
        }
    }
}
