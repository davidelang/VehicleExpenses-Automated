package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** App-private rclone.conf per photo destination. Never log file contents. */
object RcloneConfStorage {

    fun confDir(context: Context, destId: String): File {
        val dir = File(context.filesDir, "rclone/$destId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun confFile(context: Context, destId: String, confFileName: String): File =
        File(confDir(context, destId), confFileName)

    fun hasConf(context: Context, destId: String, config: RcloneDestConfig): Boolean =
        confFile(context, destId, config.confFileName).let { it.exists() && it.length() > 0 }

    /** Create an empty conf file if missing; never logs contents. */
    fun ensureEmptyConf(context: Context, destId: String, confFileName: String): File {
        val dest = confFile(context, destId, confFileName)
        dest.parentFile?.mkdirs()
        if (!dest.exists()) {
            dest.writeText("")
        }
        return dest
    }

    /** Best-effort backup before mutating conf via RPC. */
    fun backupConfBeforeWrite(context: Context, destId: String, confFileName: String) {
        val file = confFile(context, destId, confFileName)
        if (!file.exists() || file.length() == 0L) return
        try {
            val backup = File(file.parentFile, "${confFileName}.bak")
            file.copyTo(backup, overwrite = true)
        } catch (_: Exception) {
            // Non-fatal; proceed with RPC write.
        }
    }

    fun importConf(context: Context, destId: String, sourceUri: Uri, confFileName: String): Boolean {
        return try {
            val dest = confFile(context, destId, confFileName)
            dest.parentFile?.mkdirs()
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return false
            dest.exists() && dest.length() > 0
        } catch (_: Exception) {
            false
        }
    }
}