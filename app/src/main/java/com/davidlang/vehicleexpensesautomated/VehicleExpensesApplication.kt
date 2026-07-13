package com.davidlang.vehicleexpensesautomated

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupManager
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneLoader
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneRuntime
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdBackfill
import com.davidlang.vehicleexpensesautomated.data.sync.SyncManager
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.QuickFillDebugStore
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltAndroidApp
class VehicleExpensesApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncIdBackfill: SyncIdBackfill

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var photoBackupManager: PhotoBackupManager

    @Inject
    lateinit var rcloneRuntime: RcloneRuntime

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
        val defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                QuickFillDebugStore.writeCrashTombstone(applicationContext, throwable)
            } catch (e: Exception) {
                android.util.Log.e("VehicleExpensesApp", "Failed to write crash tombstone", e)
            }
            defaultUncaughtHandler?.uncaughtException(thread, throwable)
        }

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
            kotlinx.coroutines.runBlocking {
                syncIdBackfill.runIfNeeded()
            }
        } catch (e: Exception) {
            android.util.Log.e("VehicleExpensesApp", "syncId backfill failed", e)
        }
        try {
            android.util.Log.i("VehicleExpensesApp", "Scheduling background sync from destination settings")
            syncManager.scheduleFromDestination()
            photoBackupManager.scheduleFromDestination()
            android.util.Log.i("VehicleExpensesApp", "Background sync schedules updated")
        } catch (e: Exception) {
            android.util.Log.e("VehicleExpensesApp", "Failed to schedule background sync", e)
        }
        smokeRcloneOnStartup()
    }

    private fun smokeRcloneOnStartup() {
        Thread {
            try {
                RcloneLoader.load(applicationContext)
                val version = rcloneRuntime.smokeVersion()
                android.util.Log.i("VehicleExpensesApp", "rclone smoke OK version=$version")
            } catch (e: Exception) {
                android.util.Log.w("VehicleExpensesApp", "rclone smoke failed (non-fatal)", e)
            }
        }.start()
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
