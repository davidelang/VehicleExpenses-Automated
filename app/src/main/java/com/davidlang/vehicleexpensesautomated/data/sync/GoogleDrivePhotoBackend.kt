package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.google.api.client.http.FileContent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDrivePhotoBackend @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val driveAuth: GoogleDriveAuth,
    private val photoStorage: PhotoStorageManager,
) : PhotoSyncBackend {

    override val provider = PhotoProvider.GOOGLE_DRIVE

    override fun isConfigured(dest: PhotoDestination): Boolean =
        dest.folderName.isNotBlank()

    override fun manifestProvider(): String = CloudManifest.PROVIDER_GOOGLE_DRIVE

    override suspend fun testConnection(dest: PhotoDestination, accountHint: String?): PhotoBackupResult =
        withContext(Dispatchers.IO) {
            val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
            if (driveAuth.resolveAccountFromHint(hint) == null) {
                return@withContext PhotoBackupResult(false, "Sign in with Google (Drive) first")
            }
            try {
                val drive = driveAuth.buildDriveServiceForAccountName(
                    driveAuth.resolveAccountFromHint(hint)!!.name,
                )
                val folderId = photoStorage.resolveFolderId(hint, dest)
                val probeName = ".ve_probe_${System.currentTimeMillis()}.txt"
                val probeContent = "VehicleExpenses connection test"
                val tempFile = File(context.cacheDir, probeName)
                tempFile.writeText(probeContent)
                try {
                    val metadata = com.google.api.services.drive.model.File().apply {
                        name = probeName
                        parents = listOf(folderId)
                    }
                    val created = drive.files()
                        .create(metadata, FileContent("text/plain", tempFile))
                        .setFields("id,name,mimeType")
                        .execute()
                    val listed = drive.files().list()
                        .setQ("'$folderId' in parents and name='$probeName' and trashed=false")
                        .setFields("files(id,name)")
                        .setPageSize(10)
                        .execute()
                    if (listed.files.isNullOrEmpty()) {
                        return@withContext PhotoBackupResult(false, "Drive test failed — probe not listed")
                    }
                    val downloaded = drive.files().get(created.id)
                        .executeMediaAsInputStream()
                        .bufferedReader()
                        .use { it.readText() }
                    if (downloaded != probeContent) {
                        drive.files().delete(created.id).execute()
                        return@withContext PhotoBackupResult(false, "Drive test failed — probe content mismatch")
                    }
                    var deleteSkipped = false
                    try {
                        drive.files().delete(created.id).execute()
                    } catch (e: Exception) {
                        android.util.Log.d(TAG, "Drive probe delete best-effort failed: ${e.message}")
                        deleteSkipped = true
                    }
                    val message = if (deleteSkipped) {
                        "Drive test OK — write/list/read (cleanup skipped)"
                    } else {
                        "Drive test OK — write/list/read"
                    }
                    PhotoBackupResult(success = true, message = message)
                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                handleError("Drive test failed", e)
            }
        }

    override suspend fun uploadFile(
        dest: PhotoDestination,
        accountHint: String?,
        localSource: String,
        remoteFileName: String,
        mimeType: String,
        existingFileId: String?,
    ): PhotoUploadResult {
        val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
        val result = photoStorage.uploadToDestination(
            hint, dest, localSource, remoteFileName, mimeType, existingFileId,
        )
        return PhotoUploadResult(fileId = result.fileId, resolvedFolderId = result.resolvedFolderId)
    }

    override suspend fun downloadFile(
        dest: PhotoDestination,
        accountHint: String?,
        objectKey: String,
        localFileName: String,
        useMediaStore: Boolean,
        photoType: PhotoType,
    ): String {
        val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
        return photoStorage.downloadFromDrive(hint, objectKey, localFileName, useMediaStore, photoType)
    }

    override suspend fun resolveFolderId(dest: PhotoDestination, accountHint: String?): String {
        val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
        return photoStorage.resolveFolderId(hint, dest)
    }

    private fun handleError(logMsg: String, e: Exception): PhotoBackupResult {
        android.util.Log.e(TAG, logMsg, e)
        val wrapped = DriveAuthRecovery.wrapIfRecoverable(e)
        if (wrapped is DriveRecoverableAuthException) {
            return PhotoBackupResult(
                success = false,
                message = wrapped.message ?: DriveAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE,
                needsRemoteConsent = true,
                recoveryIntent = wrapped.recoveryIntent,
            )
        }
        return PhotoBackupResult(false, DriveAuthRecovery.userMessage(wrapped))
    }

    companion object {
        private const val TAG = "GoogleDrivePhotoBackend"
    }
}