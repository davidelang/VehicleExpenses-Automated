package com.davidlang.vehicleexpensesautomated.data.storage

import android.net.Uri

interface PhotoStorageProvider {
    suspend fun uploadPhoto(uri: Uri, metadata: Map<String, String>): String?   // returns public URL or null on failure
    fun getProviderName(): String
}
