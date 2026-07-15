package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RclonePhotoBackend @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val runtime: RcloneRuntime,
    private val photoStorage: PhotoStorageManager,
) : PhotoSyncBackend {

    override val provider = PhotoProvider.OTHER

    override fun isConfigured(dest: PhotoDestination): Boolean {
        val config = RcloneDestConfig.parse(dest.configJson) ?: return false
        return RcloneConfStorage.hasConf(context, dest.id, config)
    }

    override fun manifestProvider(): String = CloudManifest.PROVIDER_RCLONE

    override suspend fun testConnection(dest: PhotoDestination, accountHint: String?): PhotoBackupResult =
        withContext(Dispatchers.IO) {
            val config = RcloneDestConfig.parse(dest.configJson)
                ?: return@withContext PhotoBackupResult(false, "Configure remote name and path prefix")
            if (!RcloneConfStorage.hasConf(context, dest.id, config)) {
                RcloneConfStorage.ensureEmptyConf(context, dest.id, config.confFileName)
                return@withContext PhotoBackupResult(false, "Create or select a remote first")
            }
            try {
                val label = when (dest.provider) {
                    PhotoProvider.ONEDRIVE -> "OneDrive"
                    PhotoProvider.S3 -> "S3"
                    else -> "Connection"
                }
                val message = runWriteListReadProbeTest(dest.id, config, label)
                PhotoBackupResult(true, message)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Rclone backend test failed destId=${dest.id}", e)
                val message = if (dest.provider == PhotoProvider.ONEDRIVE) {
                    oneDriveTestErrorMessage(e)
                } else {
                    "Connection test failed: ${e.message}"
                }
                PhotoBackupResult(false, message)
            }
        }

    override suspend fun uploadFile(
        dest: PhotoDestination,
        accountHint: String?,
        localSource: String,
        remoteFileName: String,
        mimeType: String,
        existingFileId: String?,
    ): PhotoUploadResult = withContext(Dispatchers.IO) {
        val config = RcloneDestConfig.parse(dest.configJson)
            ?: throw RcloneException("Rclone destination not configured")
        runtime.setConfigForDest(dest.id, config)
        runtime.ensureRemoteDir(config)
        val tempFile = materializeToTempFile(localSource, remoteFileName)
        try {
            runtime.rpc(
                "operations/copyfile",
                JSONObject().apply {
                    put("srcFs", tempFile.parentFile!!.absolutePath)
                    put("srcRemote", tempFile.name)
                    put("dstFs", config.remoteFs())
                    put("dstRemote", remoteFileName)
                }.toString(),
            )
            PhotoUploadResult(fileId = remoteFileName)
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun downloadFile(
        dest: PhotoDestination,
        accountHint: String?,
        objectKey: String,
        localFileName: String,
        useMediaStore: Boolean,
        photoType: PhotoType,
    ): String = withContext(Dispatchers.IO) {
        val config = RcloneDestConfig.parse(dest.configJson)
            ?: throw RcloneException("Rclone destination not configured")
        runtime.setConfigForDest(dest.id, config)
        if (useMediaStore) {
            val uri = photoStorage.createMediaStoreUri(localFileName, photoType)
                ?: throw IllegalStateException("Cannot create MediaStore URI")
            val tempFile = File(context.cacheDir, "rclone_dl_$localFileName")
            try {
                downloadToLocalFile(config, objectKey, tempFile.parentFile!!.absolutePath, tempFile.name)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("Cannot open MediaStore output")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, contentValues, null, null)
                }
                uri.toString()
            } finally {
                tempFile.delete()
            }
        } else {
            val destDir = photoStorage.vehicleRefsDir()
            downloadToLocalFile(config, objectKey, destDir.absolutePath, localFileName)
            File(destDir, localFileName).absolutePath
        }
    }

    private fun downloadToLocalFile(config: RcloneDestConfig, objectKey: String, dstFs: String, dstRemote: String) {
        runtime.rpc(
            "operations/copyfile",
            JSONObject().apply {
                put("srcFs", config.remoteFs())
                put("srcRemote", objectKey)
                put("dstFs", dstFs)
                put("dstRemote", dstRemote)
            }.toString(),
        )
    }

    private fun runWriteListReadProbeTest(destId: String, config: RcloneDestConfig, label: String): String {
        runtime.setConfigForDest(destId, config)
        runtime.ensureRemoteDir(config)
        val probeName = ".ve_probe_${System.currentTimeMillis()}.txt"
        val probeContent = "VehicleExpenses connection test"
        val tempWrite = File(context.cacheDir, probeName)
        tempWrite.writeText(probeContent)
        try {
            runtime.rpc(
                "operations/copyfile",
                JSONObject().apply {
                    put("srcFs", tempWrite.parentFile!!.absolutePath)
                    put("srcRemote", tempWrite.name)
                    put("dstFs", config.remoteFs())
                    put("dstRemote", probeName)
                }.toString(),
            )
            val listOutput = runtime.rpc(
                "operations/list",
                JSONObject().apply {
                    put("fs", config.remoteFs())
                    put("remote", "")
                    put("opt", JSONObject().put("recurse", false))
                }.toString(),
            )
            if (!listContainsName(listOutput, probeName)) {
                throw RcloneException("$label test failed — probe not listed")
            }
            val tempRead = File(context.cacheDir, "ve_probe_read_$probeName")
            try {
                runtime.rpc(
                    "operations/copyfile",
                    JSONObject().apply {
                        put("srcFs", config.remoteFs())
                        put("srcRemote", probeName)
                        put("dstFs", tempRead.parentFile!!.absolutePath)
                        put("dstRemote", tempRead.name)
                    }.toString(),
                )
                if (tempRead.readText() != probeContent) {
                    throw RcloneException("$label test failed — probe content mismatch")
                }
            } finally {
                tempRead.delete()
            }
            var deleteSkipped = false
            try {
                runtime.rpc(
                    "operations/deletefile",
                    JSONObject().apply {
                        put("fs", config.remoteFs())
                        put("remote", probeName)
                    }.toString(),
                )
            } catch (e: Exception) {
                android.util.Log.d(TAG, "Probe delete best-effort failed: ${e.message}")
                deleteSkipped = true
            }
            return if (deleteSkipped) {
                "$label test OK — write/list/read (cleanup skipped)"
            } else {
                "$label test OK — write/list/read"
            }
        } finally {
            tempWrite.delete()
        }
    }

    private fun listContainsName(listOutput: JSONObject, name: String): Boolean {
        val list = listOutput.optJSONArray("list") ?: return false
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val path = item.optString("Path", item.optString("path", ""))
            val itemName = path.trim().trimStart('/')
            if (itemName == name || itemName.endsWith("/$name")) return true
        }
        return false
    }

    private fun materializeToTempFile(localSource: String, fileName: String): File {
        val tempFile = File(context.cacheDir, "rclone_upload_$fileName")
        val uri = when {
            localSource.startsWith("content://") || localSource.startsWith("file://") ->
                Uri.parse(localSource)
            else -> Uri.fromFile(File(localSource))
        }
        photoStorage.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Cannot read local source")
        return tempFile
    }

    private fun oneDriveTestErrorMessage(e: Exception): String {
        val raw = e.message.orEmpty()
        val lower = raw.lowercase()
        val authLike = lower.contains("unauthorized") ||
            lower.contains("401") ||
            lower.contains("invalid_grant") ||
            (lower.contains("token") && (lower.contains("expired") || lower.contains("invalid"))) ||
            lower.contains("authentication") ||
            (lower.contains("auth") && lower.contains("fail"))
        return if (authLike) {
            RcloneOneDriveSetup.SESSION_EXPIRED_MESSAGE
        } else {
            "Connection test failed: $raw"
        }
    }

    companion object {
        private const val TAG = "RclonePhotoBackend"
    }
}