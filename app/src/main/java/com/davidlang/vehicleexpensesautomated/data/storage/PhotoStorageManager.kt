package com.davidlang.vehicleexpensesautomated.data.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoStorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private suspend fun getDriveService(): Drive {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: throw IllegalStateException("No Google account signed in")
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf("https://www.googleapis.com/auth/drive.file"))
        credential.selectedAccount = account.account
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("VehicleExpenses-Automated")
            .build()
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
            val driveUrl = uploadToDrive(localUri, fileName, photoType)
            driveUrl ?: localUriString
        } else {
            localUriString
        }
    }

    private suspend fun uploadToDrive(uri: Uri, fileName: String, photoType: PhotoType): String? {
        return try {
            val drive = getDriveService()
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
                    // Ignore failures in finalizing
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
            destUri.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun findOrCreateFolder(drive: Drive, folderName: String): String {
        val query = "mimeType='application/vnd.google-apps.folder' and name='$folderName' and trashed=false"
        val result = drive.files().list().setQ(query).execute()
        if (result.files.isNotEmpty()) return result.files[0].id

        val folder = com.google.api.services.drive.model.File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
        }
        val created = drive.files().create(folder).execute()
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

    private fun openInputStream(uri: Uri): java.io.InputStream? {
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
}
