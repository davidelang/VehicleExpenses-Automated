package com.davidlang.vehicleexpensesautomated.data.storage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PhotoType { FUEL, EXPENSE }

class PhotoStorageManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)

    private fun getCurrentProvider(): PhotoStorageProvider {
        val key = prefs.getString("photo_storage_provider", "google_drive") ?: "google_drive"
        return when (key) {
            "google_drive" -> GoogleDriveProvider(null) // token will be injected later if needed
            else -> NoOpStorageProvider()
        }
    }

    fun shouldSavePhoto(type: PhotoType): Boolean {
        return when (type) {
            PhotoType.EXPENSE -> true
            PhotoType.FUEL -> prefs.getBoolean("save_fuel_photos", false)
        }
    }

    suspend fun savePhoto(photoUri: Uri, filename: String, type: PhotoType): String? = withContext(Dispatchers.IO) {
        if (!shouldSavePhoto(type)) return@withContext null
        val provider = getCurrentProvider()
        provider.uploadPhoto(photoUri, filename)  // returns public URL or null
    }
}

interface PhotoStorageProvider {
    fun getProviderName(): String
    suspend fun uploadPhoto(photoUri: Uri, filename: String): String?
}

class NoOpStorageProvider : PhotoStorageProvider {
    override fun getProviderName() = "None"
    override suspend fun uploadPhoto(photoUri: Uri, filename: String): String? = null
}
