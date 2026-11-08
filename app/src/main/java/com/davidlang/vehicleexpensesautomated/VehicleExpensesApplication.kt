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
import com.davidlang.vehicleexpensesautomated.data.torque.TorqueFillDeliveryEntryPoint
import com.davidlang.vehicleexpensesautomated.data.transfer.BackupImporter
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupScheduler
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupManager
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneLoader
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdBackfill
import com.davidlang.vehicleexpensesautomated.data.sync.SyncManager
import com.davidlang.vehicleexpensesautomated.ui.util.AppLanguage
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.FillDebugStore
import dagger.hilt.android.EntryPointAccessors
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
    lateinit var knownStationStore: KnownStationStore

    /** OpenCV, Paddle, or rclone failed. [ui.startup.StartupFailureActivity] reads this. */
    @Volatile
    var startupFailure: Throwable? = null
        private set

    override val workManagerConfiguration: Configuration
        get() {
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
        }

    companion object {
        var anchoredEngineV3: NativePaddleEngine? = null; private set
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        // Unset pref stays English. System picks the first supported locale, else English.
        AppLanguage.applyFromPrefs(this)
        AppStrings.install(this)
        migrateFillDebugNames()
        com.davidlang.vehicleexpensesautomated.data.sync.CorruptIncoming.install(this)
        BackupImporter.applyStartup(this)
        val blocked = dbState.states.value is Failed
        if (!blocked) {
            val defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    FillDebugStore.writeCrashTombstone(applicationContext, throwable)
                // kept: failure stays on this call so the screen or caller can continue
                } catch (e: Exception) {
                    android.util.Log.e("VehicleExpensesApp", "Failed to write crash tombstone", e)
                }
                defaultUncaughtHandler?.uncaughtException(thread, throwable)
            }

            if (!org.opencv.android.OpenCVLoader.initLocal()) {
                startupFailure = IllegalStateException("OpenCV did not initialize")
            } else {
                try {
                    NativePaddleEngine.initializeGlobalBuffers(this)
                    anchoredEngineV3 = NativePaddleEngine(this, variant = "V3")
                    android.util.Log.i("VehicleExpensesApp", "onCreate complete. Engines anchored.")
                // kept: failure stays on this call so the screen or caller can continue
                } catch (t: Throwable) {
                    startupFailure = t
                    android.util.Log.e("VehicleExpensesApp", "paddle load failed", t)
                }
            }
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
        if (startupFailure != null) return
        if (!loadRcloneOrFail()) return
        scheduleBackgroundWork()
        seedKnownStationsInBackground()
        retryPendingFills()
    }

    /** Startup retries rows still pending. It does not scan old fills. */
    private fun retryPendingFills() {
        try {
            EntryPointAccessors.fromApplication(this, TorqueFillDeliveryEntryPoint::class.java)
                .torqueFillDelivery()
                .kick()
        } catch (e: Exception) {
            android.util.Log.i("TorqueFill", "startup retry skipped")
        }
    }

    /** One-shot rename of the debug Fill pref key and session folder. */
    private fun migrateFillDebugNames() {
        val prefs = getSharedPreferences("vehicle_settings", MODE_PRIVATE)
        if (prefs.contains("debug_quick_fill_max_sessions")) {
            val value = prefs.getInt("debug_quick_fill_max_sessions", 10)
            prefs.edit()
                .putInt("debug_fill_max_sessions", value)
                .remove("debug_quick_fill_max_sessions")
                .apply()
        }
        val oldDir = java.io.File(filesDir, "quick_fill_debug")
        val newDir = java.io.File(filesDir, "fill_debug")
        if (!oldDir.isDirectory) return
        if (!newDir.exists()) {
            if (!oldDir.renameTo(newDir)) {
                android.util.Log.e(
                    "VehicleExpensesApp",
                    "rename failed ${oldDir.absolutePath} -> ${newDir.absolutePath}",
                )
            }
            return
        }
        val children = oldDir.listFiles()
        if (children == null) {
            android.util.Log.e(
                "VehicleExpensesApp",
                "list failed ${oldDir.absolutePath}",
            )
            return
        }
        var allMoved = true
        for (child in children) {
            val dest = java.io.File(newDir, child.name)
            if (!dest.exists()) {
                if (!child.renameTo(dest)) {
                    android.util.Log.e(
                        "VehicleExpensesApp",
                        "rename failed ${child.absolutePath} -> ${dest.absolutePath}",
                    )
                    allMoved = false
                }
                continue
            }
            val collided = java.io.File(newDir, "${child.name}.from-quick_fill_debug")
            if (collided.exists()) {
                android.util.Log.e(
                    "VehicleExpensesApp",
                    "debug migrate collision ${child.absolutePath} -> ${collided.absolutePath}",
                )
                allMoved = false
                continue
            }
            android.util.Log.w(
                "VehicleExpensesApp",
                "debug migrate collision ${child.absolutePath} -> ${collided.absolutePath}",
            )
            if (!child.renameTo(collided)) {
                android.util.Log.e(
                    "VehicleExpensesApp",
                    "rename failed ${child.absolutePath} -> ${collided.absolutePath}",
                )
                allMoved = false
            }
        }
        if (allMoved && !oldDir.delete()) {
            android.util.Log.e(
                "VehicleExpensesApp",
                "delete failed ${oldDir.absolutePath}",
            )
        }
    }

    private fun loadRcloneOrFail(): Boolean {
        try {
            RcloneLoader.load(this)
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            startupFailure = t
            android.util.Log.e("VehicleExpensesApp", "rclone load failed", t)
            return false
        }
        return true
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
            // kept: failure stays on this call so the screen or caller can continue
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
            // kept: failure stays on this call so the screen or caller can continue
            } catch (e: Exception) {
                android.util.Log.w("VehicleExpensesApp", "known stations seed failed", e)
            }
        }
    }
}
