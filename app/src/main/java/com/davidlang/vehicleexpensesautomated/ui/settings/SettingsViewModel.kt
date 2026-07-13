package com.davidlang.vehicleexpensesautomated.ui.settings

import androidx.lifecycle.ViewModel
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.CsvManager
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupCoordinator
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupResult
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetSyncCoordinator
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

    suspend fun syncSpreadsheet(): SyncResult = syncCoordinator.syncNow()

    suspend fun syncPhotoBackup(): PhotoBackupResult = photoBackupCoordinator.syncNow()

    suspend fun recountPendingBadge(): Int = photoBackupCoordinator.recountPending()
}
