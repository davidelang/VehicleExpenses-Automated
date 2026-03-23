package com.davidlang.vehicleexpensesautomated.data.storage

import android.net.Uri
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.network.GoogleSheetsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GoogleDriveProvider(private val idToken: String?) : PhotoStorageProvider {
    private val TAG = "GoogleDriveProvider"

    override suspend fun uploadPhoto(uri: Uri, metadata: Map<String, String>): String? = withContext(Dispatchers.IO) {
        if (idToken == null) return@withContext null
        try {
            // Simple Drive v3 upload (multipart) — using existing OkHttp pattern
            val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=foo_bar_baz")
            conn.doOutput = true

            // Minimal multipart body (file + metadata)
            val body = "--foo_bar_baz\r\nContent-Type: application/json\r\n\r\n" +
                       "{\"name\": \"expense_${System.currentTimeMillis()}.jpg\"}\r\n" +
                       "--foo_bar_baz\r\nContent-Type: image/jpeg\r\n\r\n" +
                       uri.path?.let { java.io.File(it).readBytes() }?.let { String(it) } + "\r\n" +
                       "--foo_bar_baz--"

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val responseCode = conn.responseCode
            conn.disconnect()
            if (responseCode == 200) {
                Log.i(TAG, "Photo uploaded to Google Drive")
                "https://drive.google.com/file/d/UPLOADED_ID/view" // placeholder — real ID parsing in next step
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Drive upload failed", e)
            null
        }
    }

    override fun getProviderName() = "Google Drive"
}
