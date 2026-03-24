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
     * Original method used by PhotoPicker, QuickFillupScreen, AddNewVehicleScreen, etc.
     * (takes a file path from camera)
     */
    fun savePhoto(photoPath: String, photoType: PhotoType): String {
        val sourceFile = File(photoPath)
        val destFile = File(photosDir, "${photoType.name.lowercase()}_${System.currentTimeMillis()}.jpg")
        sourceFile.copyTo(destFile, overwrite = true)
        return destFile.absolutePath
    }

    /**
     * New method used by ImportOldPicturesScreen (gallery URI → internal file)
     */
    fun savePhotoFromUri(uri: Uri, photoType: PhotoType): String {
        val fileName = getFileNameFromUri(uri) ?: "imported_${System.currentTimeMillis()}.jpg"
        val destFile = File(photosDir, "${photoType.name.lowercase()}_$fileName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Cannot open URI: $uri")

        return destFile.absolutePath
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
