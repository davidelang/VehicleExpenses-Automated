package com.davidlang.vehicleexpensesautomated.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : PhotoStorageProvider {

    private val prefs = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)

    override fun getProviderName() = "Google Drive"

    override suspend fun uploadPhoto(photoUri: Uri, filename: String): String? = withContext(Dispatchers.IO) {
        val idToken = prefs.getString("id_token", null)
        if (idToken == null) {
            Log.w("GoogleDriveProvider", "No id_token — cannot upload to Drive")
            return@withContext null
        }

        try {
            val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=foo_bar_baz")
            conn.doOutput = true

            val boundary = "foo_bar_baz"
            val output: OutputStream = conn.outputStream

            // Metadata part
            output.write("--$boundary\r\n".toByteArray())
            output.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            output.write("""{"name": "$filename", "mimeType": "image/jpeg"}""".toByteArray())
            output.write("\r\n--$boundary\r\n".toByteArray())

            // File content part
            output.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())

            context.contentResolver.openInputStream(photoUri)?.use { input ->
                input.copyTo(output)
            }

            output.write("\r\n--$boundary--\r\n".toByteArray())
            output.flush()

            val responseCode = conn.responseCode
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            if (responseCode in 200..299) {
                val driveId = response.substringAfter("\"id\":\"").substringBefore("\"")
                val publicUrl = "https://drive.google.com/file/d/$driveId/view"
                Log.i("GoogleDriveProvider", "✅ Uploaded $filename → $publicUrl")
                return@withContext publicUrl
            } else {
                Log.e("GoogleDriveProvider", "Upload failed: $responseCode - $response")
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Drive upload failed", e)
        }
        null
    }
}
