package com.davidlang.vehicleexpensesautomated.ui.settings

import androidx.lifecycle.ViewModel
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.CsvManager
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupCoordinator
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupResult
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetSyncCoordinator
import com.davidlang.vehicleexpensesautomated.data.sync.SyncProgressListener
import com.davidlang.vehicleexpensesautomated.data.sync.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val csvManager: CsvManager,
    val photoStorageManager: PhotoStorageManager,
    private val syncCoordinator: SpreadsheetSyncCoordinator,
    private val photoBackupCoordinator: PhotoBackupCoordinator,
) : ViewModel() {

    suspend fun syncSpreadsheet(onProgress: SyncProgressListener? = null): SyncResult =
        syncCoordinator.syncNow(onProgress = onProgress)

    suspend fun syncPhotoBackup(onProgress: SyncProgressListener? = null): PhotoBackupResult =
        photoBackupCoordinator.syncNow(onProgress = onProgress)

    suspend fun recountPendingBadge(): Int = photoBackupCoordinator.recountPending()
}
