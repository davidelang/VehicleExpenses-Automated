package com.davidlang.vehicleexpensesautomated

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.davidlang.vehicleexpensesautomated.data.sync.SyncManager
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
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
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
        }

    companion object {
        var anchoredEngineV3: NativePaddleEngine? = null; private set
    }

    override fun onCreate() {
        // Phase 115: Total Eager Initialization (Synchronized Order)
        // 1. Initialize stable JNI bridges first on Main thread
        if (!org.opencv.android.OpenCVLoader.initLocal()) {
            android.util.Log.e("VehicleExpensesApp", "OpenCV initialization failed!")
        }
        
        // 2. Initialize Paddle static predictors
        com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine.initializeGlobalBuffers(this)
        
        // 3. Anchor Engine Instances (External to class load loop)
        anchoredEngineV3 = NativePaddleEngine(this, variant = "V3")
        
        android.util.Log.i("VehicleExpensesApp", "onCreate complete. Engines anchored.")
        super.onCreate()
        
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
