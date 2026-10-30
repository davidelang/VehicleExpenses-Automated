package com.davidlang.vehicleexpensesautomated.data.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.FileNotFoundException
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
        // kept: failure stays on this call so the screen or caller can continue
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
            // kept: failure stays on this call so the screen or caller can continue
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
     * Insert a gallery photo in Pictures/VehicleExpenses, or reuse an item with the same
     * display name, size, and first 64 KB. Never overwrites, updates, or deletes.
     */
    fun saveGalleryPhoto(displayName: String, mimeType: String, source: File): Uri {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val reusable = findReusableGalleryUri(collection, displayName, source)
        if (reusable != null) return reusable
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveGalleryPhotoFromPart(displayName, mimeType, source, collection)
        } else {
            saveGalleryPhotoLegacy(displayName, mimeType, source, collection)
        }
    }

    private fun copySourceToPart(source: File, part: File) {
        try {
            source.inputStream().use { input ->
                FileOutputStream(part).use { out -> input.copyTo(out) }
            }
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }

    private fun uniqueGalleryFile(dir: File, displayName: String): File {
        var dest = File(dir, displayName)
        var n = 1
        while (dest.exists()) {
            val dot = displayName.lastIndexOf('.')
            val unique = if (dot > 0) {
                displayName.substring(0, dot) + " ($n)" + displayName.substring(dot)
            } else {
                "$displayName ($n)"
            }
            dest = File(dir, unique)
            n++
        }
        return dest
    }

    private fun saveGalleryPhotoLegacy(
        displayName: String,
        mimeType: String,
        source: File,
        collection: Uri,
    ): Uri {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "VehicleExpenses",
        )
        if (!dir.exists()) dir.mkdirs()
        val dest = uniqueGalleryFile(dir, displayName)
        val part = File(dir, "${dest.name}.part")
        copySourceToPart(source, part)
        if (!part.renameTo(dest)) {
            part.delete()
            throw IllegalStateException("Cannot rename gallery part ${part.absolutePath}")
        }
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.DATA, dest.absolutePath)
            }
            return context.contentResolver.insert(collection, values)
                ?: throw IllegalStateException("Cannot create MediaStore URI")
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }

    private fun saveGalleryPhotoFromPart(
        displayName: String,
        mimeType: String,
        source: File,
        collection: Uri,
    ): Uri {
        val part = File(context.cacheDir, "gallery_${System.nanoTime()}.part")
        copySourceToPart(source, part)
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, GALLERY_RELATIVE_PATH)
            }
            val uri = context.contentResolver.insert(collection, values)
                ?: throw IllegalStateException("Cannot create MediaStore URI")
            try {
                val out = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Cannot open MediaStore output")
                out.use { stream ->
                    part.inputStream().use { input -> input.copyTo(stream) }
                }
            } catch (e: Exception) {
                android.util.Log.e("PhotoStorageManager", "gallery stream failed for $uri", e)
                throw e
            }
            return uri
        } finally {
            part.delete()
        }
    }

    fun saveGalleryBytes(displayName: String, mimeType: String, bytes: ByteArray): Uri {
        val file = File(context.cacheDir, "gallery_${System.currentTimeMillis()}_$displayName")
        return try {
            file.writeBytes(bytes)
            saveGalleryPhoto(displayName, mimeType, file)
        } finally {
            file.delete()
        }
    }

    private fun findReusableGalleryUri(collection: Uri, displayName: String, source: File): Uri? {
        val resolver = context.contentResolver
        val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = if (q) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.RELATIVE_PATH,
            )
        } else {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATA,
            )
        }
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val picturesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "VehicleExpenses",
        ).absolutePath.trimEnd('/')
        val cursor = resolver.query(
            collection,
            projection,
            selection,
            arrayOf(displayName),
            null,
        ) ?: return null
        return cursor.use {
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val pathCol = cursor.getColumnIndex(
                if (q) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA,
            )
            while (cursor.moveToNext()) {
                if (q) {
                    if (pathCol >= 0) {
                        val relative = cursor.getString(pathCol).orEmpty().trimEnd('/')
                        if (!relative.endsWith("VehicleExpenses")) continue
                    }
                } else {
                    if (pathCol < 0) continue
                    val path = File(cursor.getString(pathCol).orEmpty()).absolutePath
                    if (path != picturesDir && !path.startsWith("$picturesDir/")) continue
                }
                if (cursor.getLong(sizeCol) != source.length()) continue
                val uri = ContentUris.withAppendedId(
                    collection,
                    cursor.getLong(idCol),
                )
                if (galleryHeadMatches(uri, source)) return uri
            }
            null
        }
    }

    private fun readUpTo(input: java.io.InputStream, dest: ByteArray): Int {
        var off = 0
        while (off < dest.size) {
            val n = input.read(dest, off, dest.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }

    private fun galleryHeadMatches(uri: Uri, source: File): Boolean {
        val want = minOf(GALLERY_HEAD_BYTES, source.length()).toInt()
        if (want == 0) return source.length() == 0L
        val fromStore = ByteArray(want)
        val fromFile = ByteArray(want)
        val storeN = context.contentResolver.openInputStream(uri)?.use { readUpTo(it, fromStore) } ?: return false
        val fileN = source.inputStream().use { readUpTo(it, fromFile) }
        if (storeN != want || fileN != want) return false
        return fromStore.contentEquals(fromFile)
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


    fun openInputStream(uri: Uri): java.io.InputStream? {
        return try {
            if (uri.scheme == "file") {
                java.io.FileInputStream(File(uri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            android.util.Log.e("PhotoStorageManager", "Failed to open input stream for URI: $uri", e)
            null
        }
    }

    enum class LocalRead { READABLE, NOT_FOUND, NEEDS_ACCESS, UNKNOWN }

    fun localRead(localPath: String?): LocalRead {
        if (localPath.isNullOrBlank()) return LocalRead.NOT_FOUND
        return try {
            when {
                localPath.startsWith("content://") -> {
                    val stream = context.contentResolver.openInputStream(Uri.parse(localPath))
                        ?: return LocalRead.NOT_FOUND
                    stream.close()
                    LocalRead.READABLE
                }
                localPath.startsWith("file://") -> {
                    val path = Uri.parse(localPath).path
                    if (path != null && File(path).exists()) LocalRead.READABLE else LocalRead.NOT_FOUND
                }
                else -> if (File(localPath).exists()) LocalRead.READABLE else LocalRead.NOT_FOUND
            }
        } catch (e: SecurityException) {
            LocalRead.NEEDS_ACCESS
        } catch (_: FileNotFoundException) {
            LocalRead.NOT_FOUND
        } catch (e: Exception) {
            android.util.Log.e("PhotoStorageManager", "local read unknown: $localPath", e)
            LocalRead.UNKNOWN
        }
    }

    fun isLocalReadable(localPath: String?): Boolean = localRead(localPath) == LocalRead.READABLE

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
                val temp = File(context.cacheDir, "drive_dl_$localFileName")
                try {
                    FileOutputStream(temp).use { out ->
                        drive.files().get(fileId).executeMediaAndDownloadTo(out)
                    }
                    saveGalleryPhoto(localFileName, mimeForGalleryName(localFileName), temp).toString()
                } finally {
                    temp.delete()
                }
            } else {
                val destFile = File(vehicleRefsDir(), localFileName)
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { out ->
                    drive.files().get(fileId).executeMediaAndDownloadTo(out)
                }
                destFile.absolutePath
            }
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            throw DriveAuthRecovery.wrapIfRecoverable(e)
        }
    }

    companion object {
        private const val GALLERY_RELATIVE_PATH = "Pictures/VehicleExpenses"
        private const val GALLERY_HEAD_BYTES = 64 * 1024L

        fun mimeForGalleryName(name: String): String = when {
            name.endsWith(".png", ignoreCase = true) -> "image/png"
            name.endsWith(".webp", ignoreCase = true) -> "image/webp"
            name.endsWith(".dng", ignoreCase = true) -> PickedImageReader.DNG_MIME
            name.endsWith(".heic", ignoreCase = true) || name.endsWith(".heif", ignoreCase = true) -> "image/heic"
            else -> "image/jpeg"
        }
    }


}