package com.davidlang.vehicleexpensesautomated.data.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.davidlang.vehicleexpensesautomated.data.sync.DriveAuthRecovery
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveAuth
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoDestination
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DriveUploadResult(
    val fileId: String,
    val resolvedFolderId: String,
)

@Singleton
class PhotoStorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val driveAuth: GoogleDriveAuth,
) {

    private fun buildDriveService(accountHint: String?): Drive {
        val account = driveAuth.resolveAccountFromHint(accountHint)
            ?: throw IllegalStateException("No Google account signed in for Drive")
        return driveAuth.buildDriveServiceForAccountName(account.name)
    }

    /**
     * Resolve folder id for [destination]; creates folder on Drive when missing.
     * Returns resolved folder id (caller persists on destination).
     */
    fun resolveFolderId(accountHint: String?, destination: PhotoDestination): String {
        if (destination.folderId.isNotBlank()) return destination.folderId
        val drive = buildDriveService(accountHint)
        return findOrCreateFolder(drive, destination.folderName)
    }

    /**
     * Upload a local file/uri to Drive under the photo destination folder.
     * @param localSource file path, file:// URI, or content:// URI
     */
    suspend fun uploadToDestination(
        accountHint: String?,
        destination: PhotoDestination,
        localSource: String,
        remoteFileName: String,
        mimeType: String,
        existingFileId: String? = null,
    ): DriveUploadResult {
        try {
            val drive = buildDriveService(accountHint)
            val folderId = resolveFolderId(accountHint, destination)
            val tempFile = materializeToTempFile(localSource, remoteFileName)
            try {
                val mediaContent = FileContent(mimeType, tempFile)
                val fileId = upsertDriveFile(
                    drive = drive,
                    folderId = folderId,
                    remoteFileName = remoteFileName,
                    mimeType = mimeType,
                    mediaContent = mediaContent,
                    existingFileId = existingFileId,
                )
                return DriveUploadResult(
                    fileId = fileId,
                    resolvedFolderId = folderId,
                )
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            throw DriveAuthRecovery.wrapIfRecoverable(e)
        }
    }

    /**
     * Update an existing Drive file when possible; otherwise match by name in [folderId] before create.
     * Avoids duplicate objects when manifest was lost but the remote file remains.
     */
    private fun upsertDriveFile(
        drive: Drive,
        folderId: String,
        remoteFileName: String,
        mimeType: String,
        mediaContent: FileContent,
        existingFileId: String?,
    ): String {
        val metadata = com.google.api.services.drive.model.File().apply {
            name = remoteFileName
        }
        if (!existingFileId.isNullOrBlank()) {
            try {
                val updated = drive.files().update(existingFileId, metadata, mediaContent)
                    .setFields("id")
                    .execute()
                return updated.id
            } catch (_: Exception) {
                // Stale manifest id — fall through to name lookup / create.
            }
        }
        val byName = findFileIdByNameInFolder(drive, folderId, remoteFileName)
        if (!byName.isNullOrBlank()) {
            val updated = drive.files().update(byName, metadata, mediaContent)
                .setFields("id")
                .execute()
            return updated.id
        }
        val createMetadata = com.google.api.services.drive.model.File().apply {
            name = remoteFileName
            parents = listOf(folderId)
        }
        return drive.files().create(createMetadata, mediaContent)
            .setFields("id")
            .execute()
            .id
    }

    private fun findFileIdByNameInFolder(drive: Drive, folderId: String, fileName: String): String? {
        val safeName = fileName.replace("'", "\\'")
        val listed = drive.files().list()
            .setQ("'$folderId' in parents and name='$safeName' and trashed=false")
            .setFields("files(id,name)")
            .setPageSize(1)
            .execute()
        return listed.files?.firstOrNull()?.id
    }

    /**
     * Creates a MediaStore URI in the shared Pictures/VehicleExpenses folder.
     * This is used by the camera to save photos directly to shared storage.
     */
    fun createMediaStoreUri(fileName: String, photoType: PhotoType): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${photoType.name.lowercase()}_$fileName")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VehicleExpenses")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return resolver.insert(contentUri, contentValues)
    }

    suspend fun savePhoto(uri: Uri, fileName: String, photoType: PhotoType): String? {
        val prefs = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
        val provider = prefs.getString("photo_storage_provider", "google_drive") ?: "google_drive"

        val localUriString = saveLocally(uri, fileName, photoType)

        return if (provider == "google_drive" && localUriString != null) {
            val localUri = Uri.parse(localUriString)
            val driveUrl = uploadToDriveLegacy(localUri, fileName, photoType)
            driveUrl ?: localUriString
        } else {
            localUriString
        }
    }

    /** Legacy path used by PhotoPicker; destination-aware sync uses [uploadToDestination]. */
    private suspend fun uploadToDriveLegacy(uri: Uri, fileName: String, photoType: PhotoType): String? {
        return try {
            val drive = buildDriveService(null)
            val folderName = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
                .getString("drive_folder", "Vehicle Expenses Photos") ?: "Vehicle Expenses Photos"

            val folderId = findOrCreateFolder(drive, folderName)

            val tempFile = File(context.cacheDir, fileName)
            openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val mediaContent = FileContent("image/jpeg", tempFile)

            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = "${photoType.name.lowercase()}_$fileName"
                parents = listOf(folderId)
            }

            val uploaded = drive.files().create(fileMetadata, mediaContent)
                .setFields("id,webViewLink")
                .execute()

            tempFile.delete()
            uploaded.webViewLink ?: "https://drive.google.com/file/d/${uploaded.id}/view"
        } catch (e: Exception) {
            null // Drive failed → will fall back to local
        }
    }

    private fun saveLocally(uri: Uri, fileName: String, photoType: PhotoType): String? {
        // If it's already a MediaStore URI (starts with content://media/), it's already saved locally
        if (uri.toString().startsWith("content://media/")) {
            // Finalize the file by removing IS_PENDING on Android Q+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, contentValues, null, null)
                } catch (e: Exception) {
                    android.util.Log.e("PhotoStorageManager", "Failed to update IS_PENDING to 0", e)
                }
            }
            return uri.toString()
        }

        // Otherwise, copy it to a new MediaStore location
        val destUri = createMediaStoreUri(fileName, photoType) ?: return null
        return try {
            openInputStream(uri)?.use { input ->
                context.contentResolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            // Finalize
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(destUri, contentValues, null, null)
            }

            // Explicitly notify the MediaScanner so the photo appears in the Gallery immediately
            try {
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(destUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                        val path = cursor.getString(pathIndex)
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(path),
                            arrayOf("image/jpeg")
                        ) { scannedPath, scannedUri ->
                            android.util.Log.i("PhotoStorageManager", "MediaScanner scanned: $scannedPath -> $scannedUri")
                        }
                    }
                }
            } catch (scanEx: Exception) {
                android.util.Log.e("PhotoStorageManager", "MediaScanner notification failed", scanEx)
            }

            destUri.toString()
        } catch (e: Exception) {
            android.util.Log.e("PhotoStorageManager", "Failed to save photo locally", e)
            null
        }
    }

    private fun findOrCreateFolder(drive: Drive, folderName: String): String {
        val escaped = folderName.replace("'", "\\'")
        val query = "mimeType='application/vnd.google-apps.folder' and name='$escaped' and trashed=false"
        val result = drive.files().list().setQ(query).setSpaces("drive").execute()
        if (result.files.isNotEmpty()) return result.files[0].id

        val folder = com.google.api.services.drive.model.File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
        }
        val created = drive.files().create(folder).setFields("id").execute()
        return created.id
    }

    suspend fun savePhotoFromUri(uri: Uri, photoType: PhotoType): String {
        val fileName = getFileNameFromUri(uri) ?: "imported_${System.currentTimeMillis()}.jpg"
        return savePhoto(uri, fileName, photoType) ?: throw IllegalArgumentException("Cannot save photo from URI")
    }

    fun savePhotoFromBitmap(bitmap: Bitmap, photoType: PhotoType): String {
        val fileName = "${photoType.name.lowercase()}_${System.currentTimeMillis()}.jpg"
        val destUri = createMediaStoreUri(fileName, photoType) ?: throw IllegalArgumentException("Cannot create MediaStore URI")

        return try {
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            // Finalize
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(destUri, contentValues, null, null)
            }
            destUri.toString()
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot save photo from Bitmap", e)
        }
    }

    private fun materializeToTempFile(localSource: String, fileName: String): File {
        val tempFile = File(context.cacheDir, "drive_upload_$fileName")
        val uri = when {
            localSource.startsWith("content://") || localSource.startsWith("file://") -> Uri.parse(localSource)
            else -> Uri.fromFile(File(localSource))
        }
        openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Cannot read local source: $localSource")
        return tempFile
    }

    fun vehicleRefsDir(): File {
        val dir = File(context.filesDir, "vehicle_refs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index)
            }
        }
        return name
    }

    fun openInputStream(uri: Uri): java.io.InputStream? {
        return try {
            if (uri.scheme == "file") {
                java.io.FileInputStream(File(uri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            android.util.Log.e("PhotoStorageManager", "Failed to open input stream for URI: $uri", e)
            null
        }
    }

    fun isLocalReadable(localPath: String?): Boolean {
        if (localPath.isNullOrBlank()) return false
        return try {
            when {
                localPath.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(localPath))?.use { true } ?: false
                }
                localPath.startsWith("file://") -> {
                    val path = Uri.parse(localPath).path
                    path != null && File(path).exists()
                }
                else -> File(localPath).exists()
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Prefer a readable on-disk path when sheet merge would otherwise wipe local-only photo URLs. */
    fun pickPreferredLocalPath(incoming: String?, existing: String?): String? {
        val incomingReadable = isLocalReadable(incoming)
        val existingReadable = isLocalReadable(existing)
        return when {
            incomingReadable -> incoming
            existingReadable -> existing
            !incoming.isNullOrBlank() -> incoming
            !existing.isNullOrBlank() -> existing
            else -> null
        }
    }

    /** Deterministic vehicle ref filename under [vehicleRefsDir]. */
    fun vehicleRefFileName(syncId: String, cleaned: Boolean = false): String =
        if (cleaned) "vehicle_${syncId}_ref_cleaned.jpg" else "vehicle_${syncId}_ref.jpg"

    /** Return absolute path when the deterministic ref file already exists on disk. */
    fun existingVehicleRefPath(syncId: String, cleaned: Boolean = false): String? {
        if (syncId.isBlank()) return null
        val file = File(vehicleRefsDir(), vehicleRefFileName(syncId, cleaned))
        return file.absolutePath.takeIf { file.exists() && file.length() > 0 }
    }

    /**
     * Download a Drive file by [fileId] to app-private vehicle refs dir or MediaStore.
     * @return local absolute path or content URI string
     */
    suspend fun downloadFromDrive(
        accountHint: String?,
        fileId: String,
        localFileName: String,
        useMediaStore: Boolean = false,
        photoType: PhotoType = PhotoType.EXPENSE,
    ): String {
        return try {
            val drive = buildDriveService(accountHint)
            if (useMediaStore) {
                val uri = createMediaStoreUri(localFileName, photoType)
                    ?: throw IllegalStateException("Cannot create MediaStore URI")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    drive.files().get(fileId).executeMediaAndDownloadTo(out)
                } ?: throw IllegalStateException("Cannot open MediaStore output")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, contentValues, null, null)
                }
                uri.toString()
            } else {
                val destFile = File(vehicleRefsDir(), localFileName)
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { out ->
                    drive.files().get(fileId).executeMediaAndDownloadTo(out)
                }
                destFile.absolutePath
            }
        } catch (e: Exception) {
            throw DriveAuthRecovery.wrapIfRecoverable(e)
        }
    }

    /** Write inline JSON/text to a temp file and upload; used for landmark JSON backup. */
    fun writeTextToVehicleRefs(fileName: String, text: String): String {
        val file = File(vehicleRefsDir(), fileName)
        file.parentFile?.mkdirs()
        file.writeText(text)
        return file.absolutePath
    }

    /** Read text file from local path. */
    fun readTextFile(localPath: String): String? {
        return try {
            val file = when {
                localPath.startsWith("file://") -> File(Uri.parse(localPath).path ?: return null)
                else -> File(localPath)
            }
            if (!file.exists()) return null
            file.readText()
        } catch (_: Exception) {
            null
        }
    }
}