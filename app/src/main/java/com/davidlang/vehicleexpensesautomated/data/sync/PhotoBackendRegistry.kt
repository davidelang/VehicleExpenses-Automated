package com.davidlang.vehicleexpensesautomated.data.sync

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoBackendRegistry @Inject constructor(
    private val googleDriveBackend: GoogleDrivePhotoBackend,
    private val rcloneBackend: RclonePhotoBackend,
) {
    fun forProvider(provider: PhotoProvider): PhotoSyncBackend? = when (provider) {
        PhotoProvider.GOOGLE_DRIVE -> googleDriveBackend
        PhotoProvider.ONEDRIVE, PhotoProvider.S3, PhotoProvider.OTHER -> rcloneBackend
        PhotoProvider.NONE -> null
    }

    fun forDestination(dest: PhotoDestination): PhotoSyncBackend? = forProvider(dest.provider)
}