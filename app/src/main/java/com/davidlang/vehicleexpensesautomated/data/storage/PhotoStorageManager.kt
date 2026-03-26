package com.davidlang.vehicleexpensesautomated.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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

    private val photosDir: File by lazy {
        File(context.filesDir, "photos").apply { mkdirs() }
    }

    private suspend fun getDriveService(): Drive {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: throw IllegalStateException("No Google account signed in")
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf("https://www.googleapis.com/auth/drive.file"))
        credential.selectedAccount = account.account
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("VehicleExpenses-Automated")
            .build()
    }

    suspend fun savePhoto(uri: Uri, fileName: String, photoType: PhotoType): String? {
        val prefs = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
        val provider = prefs.getString("photo_storage_provider", "google_drive") ?: "google_drive"

        return if (provider == "google_drive") {
            uploadToDrive(uri, fileName, photoType)
        } else {
            saveLocally(uri, fileName, photoType)
        }
    }

    private suspend fun uploadToDrive(uri: Uri, fileName: String, photoType: PhotoType): String? {
        return try {
            val drive = getDriveService()
            val folderName = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
                .getString("drive_folder", "Vehicle Expenses Photos") ?: "Vehicle Expenses Photos"

            val folderId = findOrCreateFolder(drive, folderName)

            val tempFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
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
            null
        }
    }

    private fun saveLocally(uri: Uri, fileName: String, photoType: PhotoType): String? {
        val destFile = File(photosDir, "${photoType.name.lowercase()}_$fileName")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
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
        val destFile = File(photosDir, fileName)

        return try {
            FileOutputStream(destFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            destFile.absolutePath
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
}
