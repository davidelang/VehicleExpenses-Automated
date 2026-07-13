package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
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

    companion object {
        private const val TAG = "PhotoBackupWorker"
    }
}