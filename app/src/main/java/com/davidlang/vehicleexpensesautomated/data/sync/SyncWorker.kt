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

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val coordinator: SpreadsheetSyncCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    /** Phase 17: coordinator + destination store (legacy sheet_id fallback). */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val store = SyncDestinationStore(appContext)
            val hasEnabled = store.enabledSpreadsheet().isNotEmpty()
            val legacyFallback = run {
                val prefs = appContext.getSharedPreferences(SyncDestinationStore.PREFS_NAME, Context.MODE_PRIVATE)
                val legacySheetId = prefs.getString("sheet_id", null)
                val legacyEnabled = prefs.getBoolean("sync_enabled", false)
                !legacySheetId.isNullOrBlank() && legacyEnabled
            }

            if (!hasEnabled && !legacyFallback) {
                return@withContext Result.success()
            }

            val result = coordinator.syncNow(null)
            when {
                result.success -> {
                    Log.i(TAG, result.message)
                    Result.success()
                }
                result.needsRemoteConsent -> {
                    Log.w(TAG, "Sync needs consent (open Spreadsheet Sync): ${result.message}")
                    Result.failure()
                }
                else -> {
                    Log.w(TAG, result.message)
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}