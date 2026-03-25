package com.davidlang.vehicleexpensesautomated.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
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

    fun savePhotoFromUri(uri: Uri, photoType: PhotoType): String {
        val fileName = getFileNameFromUri(uri) ?: "imported_${System.currentTimeMillis()}.jpg"
        return savePhoto(uri, fileName, photoType) ?: throw IllegalArgumentException("Cannot save photo from URI")
    }

    // NEW: required for camera-first flow (TakePicturePreview returns Bitmap)
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
