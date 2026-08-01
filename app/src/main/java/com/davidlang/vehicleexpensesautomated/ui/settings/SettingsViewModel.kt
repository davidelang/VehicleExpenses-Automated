package com.davidlang.vehicleexpensesautomated.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.sync.CsvManager
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupCoordinator
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupResult
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetSyncCoordinator
import com.davidlang.vehicleexpensesautomated.data.sync.SyncProgressListener
import com.davidlang.vehicleexpensesautomated.data.sync.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val csvManager: CsvManager,
    val photoStorageManager: PhotoStorageManager,
    private val syncCoordinator: SpreadsheetSyncCoordinator,
    private val photoBackupCoordinator: PhotoBackupCoordinator,
) : ViewModel() {

    private val _spreadsheetSyncStatus = MutableStateFlow("")
    val spreadsheetSyncStatus: StateFlow<String> = _spreadsheetSyncStatus.asStateFlow()
    private val _spreadsheetSyncInProgress = MutableStateFlow(false)
    val spreadsheetSyncInProgress: StateFlow<Boolean> = _spreadsheetSyncInProgress.asStateFlow()
    private val _spreadsheetSyncIsError = MutableStateFlow(false)
    val spreadsheetSyncIsError: StateFlow<Boolean> = _spreadsheetSyncIsError.asStateFlow()
    private val _spreadsheetSyncResult = MutableStateFlow<SyncResult?>(null)
    val spreadsheetSyncResult: StateFlow<SyncResult?> = _spreadsheetSyncResult.asStateFlow()

    private val _photoSyncStatus = MutableStateFlow("")
    val photoSyncStatus: StateFlow<String> = _photoSyncStatus.asStateFlow()
    private val _photoSyncInProgress = MutableStateFlow(false)
    val photoSyncInProgress: StateFlow<Boolean> = _photoSyncInProgress.asStateFlow()
    private val _photoSyncIsError = MutableStateFlow(false)
    val photoSyncIsError: StateFlow<Boolean> = _photoSyncIsError.asStateFlow()
    private val _photoSyncResult = MutableStateFlow<PhotoBackupResult?>(null)
    val photoSyncResult: StateFlow<PhotoBackupResult?> = _photoSyncResult.asStateFlow()

    fun startSpreadsheetSync() {
        if (_spreadsheetSyncInProgress.value) return
        viewModelScope.launch {
            _spreadsheetSyncInProgress.value = true
            _spreadsheetSyncIsError.value = false
            _spreadsheetSyncResult.value = null
            _spreadsheetSyncStatus.value = "Starting spreadsheet sync…"
            try {
                val result = withContext(Dispatchers.IO) {
                    syncCoordinator.syncNow(
                        onProgress = SyncProgressListener { msg ->
                            _spreadsheetSyncStatus.value = msg
                            _spreadsheetSyncIsError.value = false
                        },
                    )
                }
                _spreadsheetSyncStatus.value = result.message
                _spreadsheetSyncIsError.value = !result.success
                _spreadsheetSyncResult.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _spreadsheetSyncIsError.value = true
                _spreadsheetSyncStatus.value = e.message ?: "Sync failed"
                _spreadsheetSyncResult.value = SyncResult(false, _spreadsheetSyncStatus.value)
            } finally {
                _spreadsheetSyncInProgress.value = false
            }
        }
    }

    fun startPhotoSync() {
        if (_photoSyncInProgress.value) return
        viewModelScope.launch {
            _photoSyncInProgress.value = true
            _photoSyncIsError.value = false
            _photoSyncResult.value = null
            _photoSyncStatus.value = "Starting photo backup…"
            try {
                val result = withContext(Dispatchers.IO) {
                    photoBackupCoordinator.syncNow(
                        onProgress = SyncProgressListener { msg ->
                            _photoSyncStatus.value = msg
                            _photoSyncIsError.value = false
                        },
                    )
                }
                _photoSyncStatus.value = result.message
                _photoSyncIsError.value = !result.success
                _photoSyncResult.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _photoSyncIsError.value = true
                _photoSyncStatus.value = e.message ?: "Photo sync failed"
                _photoSyncResult.value = PhotoBackupResult(false, _photoSyncStatus.value)
            } finally {
                _photoSyncInProgress.value = false
            }
        }
    }

    fun clearSpreadsheetSyncResult() {
        _spreadsheetSyncResult.value = null
    }

    fun clearPhotoSyncResult() {
        _photoSyncResult.value = null
    }

    suspend fun syncSpreadsheet(onProgress: SyncProgressListener? = null): SyncResult =
        syncCoordinator.syncNow(onProgress = onProgress)

    suspend fun syncPhotoBackup(onProgress: SyncProgressListener? = null): PhotoBackupResult =
        photoBackupCoordinator.syncNow(onProgress = onProgress)

    suspend fun recountPendingBadge(): Int = photoBackupCoordinator.recountPending()
}
