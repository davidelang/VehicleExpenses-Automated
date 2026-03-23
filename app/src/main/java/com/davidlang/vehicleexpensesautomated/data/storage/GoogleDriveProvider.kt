package com.davidlang.vehicleexpensesautomated.data.storage

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleDriveProvider(private val idToken: String?) : PhotoStorageProvider {
    override fun getProviderName() = "Google Drive"

    override suspend fun uploadPhoto(photoUri: Uri, filename: String): String? = withContext(Dispatchers.IO) {
        // TODO: replace with real Google Drive API v3 upload when you want
        // For now we return null so "None" and fuel-toggle-off both behave the same
        println("📸 Google Drive stub upload: $filename")
        null
    }
}
