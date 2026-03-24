package com.davidlang.vehicleexpensesautomated.data.storage

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class PhotoType { FUEL, EXPENSE }

@Singleton
class PhotoStorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)

    private fun getCurrentProvider(): PhotoStorageProvider {
        val key = prefs.getString("photo_storage_provider", "google_drive") ?: "google_drive"
        return when (key) {
            "google_drive" -> GoogleDriveProvider(context)
            else -> NoOpStorageProvider()
        }
    }

    fun shouldSavePhoto(type: PhotoType): Boolean = when (type) {
        PhotoType.EXPENSE -> true
        PhotoType.FUEL -> prefs.getBoolean("save_fuel_photos", false)
    }

    suspend fun savePhoto(photoUri: Uri, filename: String, type: PhotoType): String? = withContext(Dispatchers.IO) {
        if (!shouldSavePhoto(type)) return@withContext null
        getCurrentProvider().uploadPhoto(photoUri, filename)
    }
}
