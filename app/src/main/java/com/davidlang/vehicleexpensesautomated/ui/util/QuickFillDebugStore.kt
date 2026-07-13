package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

/**
 * App-private Quick Fill debug session storage under [debugRoot].
 * Crash tombstones live under [crashRoot] (written by application uncaught handler).
 */
object QuickFillDebugStore {
    private const val PREFS_NAME = "vehicle_settings"
    private const val MAX_SESSIONS_KEY = "debug_quick_fill_max_sessions"
    private const val DEFAULT_MAX_SESSIONS = 10
    private const val DEFAULT_MAX_CRASH_REPORTS = 10
    private const val CRASH_PREFIX = "crash_"
    const val DEBUG_DIR_NAME = "quick_fill_debug"
    const val CRASH_DIR_NAME = "crash_reports"
    const val REPORT_EMAIL = "david@lang.hm"
    const val REPORT_SUBJECT = "vehicle expenses failure report"
    const val REPORT_BODY_SEED =
        "Thank you for reporting a bug with the information to debug it, please describe what went wrong"
    private const val SESSION_PREFIX = "session_"

    fun debugRoot(context: Context): File = File(context.filesDir, DEBUG_DIR_NAME)

    fun crashRoot(context: Context): File = File(context.filesDir, CRASH_DIR_NAME)

    fun ensureDirs(context: Context) {
        debugRoot(context).mkdirs()
        crashRoot(context).mkdirs()
    }

    fun maxSessions(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(MAX_SESSIONS_KEY, DEFAULT_MAX_SESSIONS)

    fun readSessionSummary(sessionDir: File): String {
        val metaFile = File(sessionDir, "meta.json")
        if (!metaFile.exists()) return sessionDir.name
        return try {
            val meta = JSONObject(metaFile.readText())
            val parts = mutableListOf<String>()
            meta.optString("mode").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
            meta.optString("vehicleName").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
            meta.optString("odometer").takeIf { it.isNotEmpty() }?.let { parts.add("odo $it") }
            meta.optString("cost").takeIf { it.isNotEmpty() }?.let { parts.add("cost $it") }
            meta.optString("volume").takeIf { it.isNotEmpty() }?.let { parts.add("vol $it") }
            if (parts.isEmpty()) sessionDir.name else parts.joinToString(" · ")
        } catch (_: Exception) {
            sessionDir.name
        }
    }

    fun listSessions(context: Context): List<File> {
        ensureDirs(context)
        return debugRoot(context).listFiles { file ->
            file.isDirectory && file.name.startsWith(SESSION_PREFIX)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun pruneToMax(context: Context) {
        val max = maxSessions(context)
        listSessions(context).drop(max).forEach { it.deleteRecursively() }
    }

    fun clearAll(context: Context) {
        ensureDirs(context)
        debugRoot(context).listFiles()?.forEach { it.deleteRecursively() }
    }

    fun clearAllDebugData(context: Context) {
        clearAll(context)
        crashRoot(context).listFiles()?.forEach { it.delete() }
    }

    fun saveSession(
        context: Context,
        mode: String,
        debugJson: String,
        vehicleId: Int? = null,
        vehicleName: String? = null,
        odometer: String? = null,
        cost: String? = null,
        volume: String? = null,
        error: String? = null,
    ): File {
        ensureDirs(context)
        val timestampMs = System.currentTimeMillis()
        val shortId = UUID.randomUUID().toString().take(8)
        val sessionDir = File(debugRoot(context), "${SESSION_PREFIX}${timestampMs}_$shortId")
        sessionDir.mkdirs()

        val meta = JSONObject().apply {
            put("mode", mode)
            put("timestampMs", timestampMs)
            vehicleId?.let { put("vehicleId", it) }
            vehicleName?.let { put("vehicleName", it) }
            odometer?.let { put("odometer", it) }
            cost?.let { put("cost", it) }
            volume?.let { put("volume", it) }
            error?.let { put("error", it) }
        }
        File(sessionDir, "meta.json").writeText(meta.toString(2))
        File(sessionDir, "debug.json").writeText(debugJson)

        pruneToMax(context)
        return sessionDir
    }

    fun listCrashReports(context: Context): List<File> {
        ensureDirs(context)
        return crashRoot(context).listFiles { file ->
            file.isFile && file.name.startsWith(CRASH_PREFIX)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun pruneCrashReports(context: Context, max: Int = DEFAULT_MAX_CRASH_REPORTS) {
        listCrashReports(context).drop(max).forEach { it.delete() }
    }

    fun collectAttachmentFiles(sessionPaths: Collection<String>, crashPaths: Collection<String>): List<File> {
        val files = mutableListOf<File>()
        sessionPaths.forEach { path ->
            val dir = File(path)
            if (dir.isDirectory) {
                dir.listFiles()?.filter { it.isFile }?.let { files.addAll(it) }
            }
        }
        crashPaths.forEach { path ->
            val file = File(path)
            if (file.isFile) files.add(file)
        }
        return files
    }

    fun buildFailureReportIntent(context: Context, attachmentFiles: List<File>): Intent? {
        if (attachmentFiles.isEmpty()) return null
        val authority = "${context.packageName}.fileprovider"
        val uris = attachmentFiles.map { file ->
            FileProvider.getUriForFile(context, authority, file)
        }
        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, REPORT_SUBJECT)
                putExtra(Intent.EXTRA_TEXT, REPORT_BODY_SEED)
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, REPORT_SUBJECT)
                putExtra(Intent.EXTRA_TEXT, REPORT_BODY_SEED)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun launchFailureReport(context: Context, attachmentFiles: List<File>): Boolean {
        val intent = buildFailureReportIntent(context, attachmentFiles) ?: return false
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        return true
    }

    fun writeCrashTombstone(context: Context, throwable: Throwable): File {
        ensureDirs(context)
        val timestampMs = System.currentTimeMillis()
        val file = File(crashRoot(context), "${CRASH_PREFIX}${timestampMs}.txt")
        val stackWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stackWriter))
        val body = buildString {
            appendLine("timestamp: $timestampMs")
            appendLine("thread: ${Thread.currentThread().name}")
            appendLine("exception: ${throwable.javaClass.name}: ${throwable.message}")
            appendLine()
            append(stackWriter.toString())
        }
        file.writeText(body)
        pruneCrashReports(context)
        return file
    }
}