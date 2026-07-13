package com.davidlang.vehicleexpensesautomated.data.sync

import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType

/** Provider-specific photo upload/download/test port. */
interface PhotoSyncBackend {
    val provider: PhotoProvider

    fun isConfigured(dest: PhotoDestination): Boolean

    fun manifestProvider(): String

    suspend fun testConnection(dest: PhotoDestination, accountHint: String?): PhotoBackupResult

    suspend fun uploadFile(
        dest: PhotoDestination,
        accountHint: String?,
        localSource: String,
        remoteFileName: String,
        mimeType: String,
    ): PhotoUploadResult

    suspend fun downloadFile(
        dest: PhotoDestination,
        accountHint: String?,
        objectKey: String,
        localFileName: String,
        useMediaStore: Boolean,
        photoType: PhotoType,
    ): String

    /** Google Drive: resolved folder id. Rclone: empty (unused). */
    suspend fun resolveFolderId(dest: PhotoDestination, accountHint: String?): String = ""
}

data class PhotoUploadResult(
    val fileId: String,
    val resolvedFolderId: String = "",
)