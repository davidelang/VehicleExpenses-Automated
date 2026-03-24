package com.davidlang.vehicleexpensesautomated.data.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val photosDir: File by lazy {
        File(context.filesDir, "photos").apply { mkdirs() }
    }

    /**
     * EXACT signature called by your PhotoPicker.kt (and every other screen)
     * savePhoto(Uri, filename, PhotoType) → returns String? (null on failure)
     */
    fun savePhoto(uri: Uri, fileName: String, photoType: PhotoType): String? {
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

    /**
     * Helper for ImportOldPicturesScreen (gallery picker)
     */
    fun savePhotoFromUri(uri: Uri, photoType: PhotoType): String {
        val fileName = getFileNameFromUri(uri) ?: "imported_${System.currentTimeMillis()}.jpg"
        return savePhoto(uri, fileName, photoType) ?: throw IllegalArgumentException("Cannot save photo from URI")
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
