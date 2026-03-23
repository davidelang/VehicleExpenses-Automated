package com.davidlang.vehicleexpensesautomated.data.storage

import android.net.Uri

class NoOpStorageProvider : PhotoStorageProvider {
    override fun getProviderName() = "None"
    override suspend fun uploadPhoto(photoUri: Uri, filename: String): String? = null
}
