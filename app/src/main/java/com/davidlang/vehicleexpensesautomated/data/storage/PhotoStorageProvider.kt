package com.davidlang.vehicleexpensesautomated.data.storage

import android.net.Uri

interface PhotoStorageProvider {
    fun getProviderName(): String
    suspend fun uploadPhoto(photoUri: Uri, filename: String): String?
}
