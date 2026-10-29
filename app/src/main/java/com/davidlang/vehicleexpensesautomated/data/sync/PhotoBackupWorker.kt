package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import com.davidlang.vehicleexpensesautomated.data.db.DatabaseHealthState
import com.davidlang.vehicleexpensesautomated.data.db.Exporting
import com.davidlang.vehicleexpensesautomated.data.db.Failed
import com.davidlang.vehicleexpensesautomated.data.db.Importing
import com.davidlang.vehicleexpensesautomated.data.db.Ok
import com.davidlang.vehicleexpensesautomated.data.db.Unchecked
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Background photo backup; constraints from photo destination in [PhotoBackupManager]. */
@HiltWorker
class PhotoBackupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val coordinator: PhotoBackupCoordinator,
    private val dbState: DatabaseHealthState,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        databaseGate(dbState, appContext)?.let { return@withContext it }
        try {
            val store = SyncDestinationStore(appContext)
            if (store.enabledPhoto().isEmpty()) {
                return@withContext Result.success()
            }
            val result = coordinator.syncNow(null, PhotoSyncMode.PENDING_ONLY)
            if (result.success) {
                Log.i(TAG, result.message)
                Result.success()
            } else if (result.needsRemoteConsent) {
                Log.w(TAG, "Photo backup needs consent (open Photo Backup): ${result.message}")
                Result.failure()
            } else {
                Log.w(TAG, result.message)
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Photo backup worker failed", e)
            Result.retry()
        }
    }


    private fun databaseGate(dbState: DatabaseHealthState, appContext: Context): Result? {
        val app = appContext.applicationContext as VehicleExpensesApplication
        var health = dbState.states.value
        if (health is Unchecked) {
            app.verifyDatabase()
            health = dbState.states.value
        }
        return when (health) {
            is Failed -> {
                Log.i(TAG, "database failed; failure")
                Result.failure()
            }
            is Exporting, is Importing -> {
                Log.i(TAG, "transfer in progress; retry")
                Result.retry()
            }
            Unchecked -> Result.retry()
            Ok -> null
        }
    }

    companion object {
        private const val TAG = "PhotoBackupWorker"
    }
}