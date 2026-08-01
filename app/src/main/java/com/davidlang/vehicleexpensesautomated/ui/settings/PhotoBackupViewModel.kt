package com.davidlang.vehicleexpensesautomated.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidlang.vehicleexpensesautomated.data.sync.DriveBrowserItem
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveAuth
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveBrowseCatalog
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveBrowserClient
import com.davidlang.vehicleexpensesautomated.data.sync.MicrosoftAuthResult
import com.davidlang.vehicleexpensesautomated.data.sync.MicrosoftOneDriveAuth
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupCoordinator
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupManager
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupResult
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoDestination
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoSyncMode
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneConfigStepResult
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneDestConfig
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneOneDriveSetup
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneProviderInfo
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneRuntime
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneS3Setup
import com.davidlang.vehicleexpensesautomated.data.sync.S3ProviderPreset
import com.davidlang.vehicleexpensesautomated.data.sync.SyncProgressListener
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
class PhotoBackupViewModel @Inject constructor(
    val auth: GoogleDriveAuth,
    val oneDriveAuth: MicrosoftOneDriveAuth,
    private val driveBrowser: GoogleDriveBrowserClient,
    private val coordinator: PhotoBackupCoordinator,
    private val photoBackupManager: PhotoBackupManager,
    private val rcloneRuntime: RcloneRuntime,
    private val oneDriveSetup: RcloneOneDriveSetup,
    private val s3Setup: RcloneS3Setup,
) : ViewModel() {

    private val _manualSyncStatus = MutableStateFlow("")
    val manualSyncStatus: StateFlow<String> = _manualSyncStatus.asStateFlow()
    private val _manualSyncInProgress = MutableStateFlow(false)
    val manualSyncInProgress: StateFlow<Boolean> = _manualSyncInProgress.asStateFlow()
    private val _manualSyncIsError = MutableStateFlow(false)
    val manualSyncIsError: StateFlow<Boolean> = _manualSyncIsError.asStateFlow()
    private val _manualSyncResult = MutableStateFlow<PhotoBackupResult?>(null)
    val manualSyncResult: StateFlow<PhotoBackupResult?> = _manualSyncResult.asStateFlow()

    /**
     * Runs photo Sync now on [viewModelScope] so leaving the settings composable
     * does **not** cancel the backup.
     */
    fun startManualSync(accountHint: String = "", destId: String? = null) {
        if (_manualSyncInProgress.value) return
        viewModelScope.launch {
            _manualSyncInProgress.value = true
            _manualSyncIsError.value = false
            _manualSyncResult.value = null
            _manualSyncStatus.value = "Starting photo backup…"
            try {
                val result = withContext(Dispatchers.IO) {
                    coordinator.syncNow(
                        accountHint = accountHint.ifBlank { null },
                        mode = PhotoSyncMode.FULL,
                        destId = destId,
                        onProgress = SyncProgressListener { msg ->
                            // StateFlow is process-safe; no composition scope involved.
                            _manualSyncStatus.value = msg
                            _manualSyncIsError.value = false
                        },
                    )
                }
                _manualSyncStatus.value = result.message
                _manualSyncIsError.value = !result.success
                _manualSyncResult.value = result
            } catch (e: CancellationException) {
                // ViewModel cleared — not a dest failure
                throw e
            } catch (e: Exception) {
                _manualSyncIsError.value = true
                _manualSyncStatus.value = e.message ?: "Photo sync failed"
                _manualSyncResult.value = PhotoBackupResult(false, _manualSyncStatus.value)
            } finally {
                _manualSyncInProgress.value = false
            }
        }
    }

    fun clearManualSyncResult() {
        _manualSyncResult.value = null
    }

    suspend fun listFoldersForBrowse(
        accountHint: String,
        searchQuery: String,
        catalog: GoogleDriveBrowseCatalog = GoogleDriveBrowseCatalog.APP,
    ): List<DriveBrowserItem> =
        driveBrowser.listFolders(accountHint.ifBlank { null }, searchQuery = searchQuery, catalog = catalog)

    suspend fun createFolderForBrowse(accountHint: String, name: String): DriveBrowserItem =
        driveBrowser.createFolder(name, accountHint.ifBlank { null })

    suspend fun testConnection(accountHint: String): PhotoBackupResult =
        coordinator.testConnection(accountHint.ifBlank { null })

    suspend fun testConnection(accountHint: String, dest: PhotoDestination): PhotoBackupResult =
        coordinator.testConnection(accountHint.ifBlank { null }, dest)

    suspend fun syncNow(
        accountHint: String,
        onProgress: SyncProgressListener? = null,
        destId: String? = null,
    ): PhotoBackupResult =
        coordinator.syncNow(
            accountHint = accountHint.ifBlank { null },
            mode = PhotoSyncMode.FULL,
            destId = destId,
            onProgress = onProgress,
        )

    fun rescheduleBackgroundBackup() = photoBackupManager.scheduleFromDestination()

    suspend fun listRcloneRemotes(destId: String, config: RcloneDestConfig): List<String> =
        rcloneRuntime.listRemotes(destId, config)

    suspend fun listRcloneProviders(): List<RcloneProviderInfo> =
        rcloneRuntime.listProviders()

    suspend fun createRcloneRemote(
        destId: String,
        config: RcloneDestConfig,
        name: String,
        type: String,
        parameters: Map<String, String>,
        continueState: String? = null,
        continueResult: String? = null,
    ): RcloneConfigStepResult = rcloneRuntime.createRemote(
        destId = destId,
        config = config,
        name = name,
        type = type,
        parameters = parameters,
        continueState = continueState,
        continueResult = continueResult,
    )

    suspend fun updateRcloneRemote(
        destId: String,
        config: RcloneDestConfig,
        name: String,
        parameters: Map<String, String>,
        continueState: String? = null,
        continueResult: String? = null,
    ): RcloneConfigStepResult = rcloneRuntime.updateRemote(
        destId = destId,
        config = config,
        name = name,
        parameters = parameters,
        continueState = continueState,
        continueResult = continueResult,
    )

    suspend fun deleteRcloneRemote(destId: String, config: RcloneDestConfig, name: String) {
        rcloneRuntime.deleteRemote(destId, config, name)
    }

    suspend fun getRcloneRemoteType(destId: String, config: RcloneDestConfig, name: String): String? =
        rcloneRuntime.getRemoteType(destId, config, name)

    suspend fun providerForRcloneType(type: String): RcloneProviderInfo? =
        com.davidlang.vehicleexpensesautomated.data.sync.RcloneProviderCatalog
            .providerForType(rcloneRuntime, type)

    suspend fun setupOneDriveRemote(
        destId: String,
        authResult: MicrosoftAuthResult,
        pathPrefix: String,
    ): RcloneDestConfig = oneDriveSetup.applyAuthToDestination(destId, authResult, pathPrefix)

    suspend fun refreshOneDriveToken(destId: String, config: RcloneDestConfig, accountHint: String?) {
        oneDriveSetup.refreshTokenIfPossible(destId, config, accountHint)
    }

    fun managedOneDriveRemoteName(destId: String): String = oneDriveSetup.managedRemoteName(destId)

    fun managedS3RemoteName(destId: String): String = s3Setup.managedRemoteName(destId)

    fun splitS3BucketAndPrefix(fullPrefix: String): Pair<String, String> =
        s3Setup.splitBucketAndPrefix(fullPrefix)

    suspend fun setupS3Remote(
        destId: String,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        endpoint: String,
        bucket: String,
        pathPrefix: String,
        providerPreset: S3ProviderPreset,
    ): RcloneDestConfig = s3Setup.applyCredentialsToDestination(
        destId = destId,
        accessKeyId = accessKeyId,
        secretAccessKey = secretAccessKey,
        region = region,
        endpoint = endpoint,
        bucket = bucket,
        pathPrefix = pathPrefix,
        providerPreset = providerPreset,
    )
}