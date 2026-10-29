package com.davidlang.vehicleexpensesautomated

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.davidlang.vehicleexpensesautomated.data.db.DatabaseHealth
import com.davidlang.vehicleexpensesautomated.data.db.DatabaseHealthState
import com.davidlang.vehicleexpensesautomated.data.db.DbState
import com.davidlang.vehicleexpensesautomated.data.db.Exporting
import com.davidlang.vehicleexpensesautomated.data.db.Failed
import com.davidlang.vehicleexpensesautomated.data.db.Importing
import com.davidlang.vehicleexpensesautomated.data.db.Ok
import com.davidlang.vehicleexpensesautomated.data.db.Unchecked
import com.davidlang.vehicleexpensesautomated.data.location.KnownStationStore
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupScheduler
import com.davidlang.vehicleexpensesautomated.data.transfer.BackupImporter
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupManager
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneLoader
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneRuntime
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdBackfill
import com.davidlang.vehicleexpensesautomated.data.sync.SyncManager
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.QuickFillDebugStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VehicleExpensesApplication : Application(), Configuration.Provider {

    /** Created with the Application, before [onCreate] and before Hilt. Hilt only provides this instance. */
    val dbState: DatabaseHealthState = DatabaseHealthState()

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

    @Inject
    lateinit var knownStationStore: KnownStationStore

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
        BackupImporter.applyStartup(this)
        val blocked = dbState.states.value is Failed
        if (!blocked) {
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
        } else {
            android.util.Log.e("DatabaseHealth", "startup swap failed; opening nothing")
        }
        super.onCreate()
        val failedBeforeVerify = dbState.states.value is Failed
        verifyDatabase()
        if (dbState.states.value is Failed) {
            if (!failedBeforeVerify) {
                android.util.Log.e("DatabaseHealth", "database open failed; scheduling skipped")
            }
            return
        }

        scheduleBackgroundWork()
        smokeRcloneOnStartup()
        seedKnownStationsInBackground()
    }

    /** After a failed import restores [prior]. [Ok] schedules; [Unchecked] verifies first; [Failed] stays quiet. */
    fun resumeAfterImport(prior: DbState) {
        when (prior) {
            Ok -> scheduleBackgroundWork()
            Unchecked -> {
                verifyDatabase()
                if (dbState.states.value is Ok) scheduleBackgroundWork()
            }
            is Failed, is Exporting, is Importing -> Unit
        }
    }

    /** Same scheduling [onCreate] uses. Export and import call this when the gate clears to [Ok]. */
    fun scheduleBackgroundWork() {
        if (dbState.states.value is Failed) {
            android.util.Log.i("VehicleExpensesApp", "Skipping background scheduling; database failed")
            return
        }
        if (syncIdBackfill.isBackfillDone()) {
            try {
                android.util.Log.i("VehicleExpensesApp", "Scheduling background sync from destination settings")
                syncManager.scheduleFromDestination()
                photoBackupManager.scheduleFromDestination()
                LocationLookupScheduler.ensurePeriodic(this)
                android.util.Log.i("VehicleExpensesApp", "Background sync schedules updated")
            } catch (e: Exception) {
                android.util.Log.e("VehicleExpensesApp", "Failed to schedule background sync", e)
            }
        } else {
            android.util.Log.i(
                "VehicleExpensesApp",
                "Deferring background sync until sync-id backfill completes in UI",
            )
        }
    }

    fun casLogged(expect: DbState, update: DbState): Boolean {
        val won = dbState.compareAndSet(expect, update)
        if (won) {
            android.util.Log.i("DatabaseHealth", "DatabaseHealth: ${dbLabel(expect)} -> ${dbLabel(update)}")
        }
        return won
    }

    /** Opens Room when the state is [Unchecked]. Does nothing while a transfer gate is held. */
    fun verifyDatabase(): DbState = DatabaseHealth.verify(this)

    private fun dbLabel(state: DbState): String = when (state) {
        Unchecked -> "Unchecked"
        Ok -> "Ok"
        is Failed -> "Failed"
        is Exporting -> "Exporting"
        is Importing -> "Importing"
    }

    private fun seedKnownStationsInBackground() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                knownStationStore.seedFromConfirmedFuelsIfEmpty()
            } catch (e: Exception) {
                android.util.Log.w("VehicleExpensesApp", "known stations seed failed", e)
            }
        }
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
}
