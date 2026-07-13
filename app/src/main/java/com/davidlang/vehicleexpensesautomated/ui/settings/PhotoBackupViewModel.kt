package com.davidlang.vehicleexpensesautomated.ui.settings

import androidx.lifecycle.ViewModel
import com.davidlang.vehicleexpensesautomated.data.sync.DriveBrowserItem
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveAuth
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
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneS3Setup
import com.davidlang.vehicleexpensesautomated.data.sync.S3ProviderPreset
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneProviderInfo
import com.davidlang.vehicleexpensesautomated.data.sync.RcloneRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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

    suspend fun listFoldersForBrowse(accountHint: String, searchQuery: String): List<DriveBrowserItem> =
        driveBrowser.listFolders(accountHint.ifBlank { null }, searchQuery = searchQuery)

    suspend fun createFolderForBrowse(accountHint: String, name: String): DriveBrowserItem =
        driveBrowser.createFolder(name, accountHint.ifBlank { null })

    suspend fun testConnection(accountHint: String): PhotoBackupResult =
        coordinator.testConnection(accountHint.ifBlank { null })

    suspend fun testConnection(accountHint: String, dest: PhotoDestination): PhotoBackupResult =
        coordinator.testConnection(accountHint.ifBlank { null }, dest)

    suspend fun syncNow(accountHint: String): PhotoBackupResult =
        coordinator.syncNow(accountHint.ifBlank { null }, PhotoSyncMode.FULL)

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