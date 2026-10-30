package com.davidlang.vehicleexpensesautomated.data.transfer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import com.davidlang.vehicleexpensesautomated.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Hidden adb export/import. [onCreate] touches no files and no database.
 * Callers must be adb shell (uid 2000) or root.
 */
class DataTransferProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        checkCaller()
        val path = uri.path.orEmpty()
        val writing = mode.contains('w')
        return when {
            !writing && path == PATH_EXPORT -> pipe(writeErrorOnFailure = true) { out, end ->
                exportStream(out, end)
            }
            !writing && path == PATH_STATUS -> pipe(writeErrorOnFailure = true) { out, end ->
                out.write(readStatusJson().toByteArray(Charsets.UTF_8))
                end.closeOk()
            }
            writing && path == PATH_IMPORT -> importPipe()
            else -> throw IllegalArgumentException(validUris())
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        checkCaller()
        throw UnsupportedOperationException(validUris())
    }

    override fun getType(uri: Uri): String? {
        checkCaller()
        throw IllegalArgumentException(validUris())
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        checkCaller()
        throw UnsupportedOperationException(validUris())
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        checkCaller()
        throw UnsupportedOperationException(validUris())
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        checkCaller()
        throw UnsupportedOperationException(validUris())
    }

    override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle? {
        checkCaller()
        throw IllegalArgumentException(validUris())
    }

    private fun exportStream(out: java.io.OutputStream, end: PipeEnd) {
        BackupExporter(requireContext().applicationContext).export(out, end)
    }

    /** Client receives the write end. closeWithError is on the provider-owned read end. */
    private fun importPipe(): ParcelFileDescriptor {
        val ends = ParcelFileDescriptor.createReliablePipe()
        val readSide = ends[0]
        val writeSide = ends[1]
        // One I/O pump thread for an adb-only content pipe; exception to the no-parallel-threads rule.
        Thread({
            val end = PipeEnd(readSide, readSide, null)
            val input = FileInputStream(readSide.fileDescriptor)
            try {
                BackupImporter(requireContext().applicationContext).import(input, end)
                if (!end.closed) end.closeOk()
            // kept: failure stays on this call so the screen or caller can continue
            } catch (t: Throwable) {
                if (!end.closed) {
                    val reason = t.message?.ifBlank { null } ?: t.javaClass.simpleName
                    val message = if (reason.startsWith("datatransfer:")) reason else "datatransfer: $reason"
                    try {
                        end.closeError(message)
                    // kept: failure stays on this call so the screen or caller can continue
                    } catch (_: Throwable) {
                    }
                }
            }
        }, "datatransfer-import").apply { isDaemon = true }.start()
        return writeSide
    }

    private fun checkCaller() {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != Process.ROOT_UID) {
            throw SecurityException("datatransfer: adb shell only")
        }
    }

    private fun validUris(): String {
        val authority = BuildConfig.APPLICATION_ID + ".datatransfer"
        return "datatransfer: valid URIs are content://$authority/export (r), " +
            "content://$authority/import (w), content://$authority/import-status (r)"
    }

    private fun pipe(
        writeErrorOnFailure: Boolean,
        block: (java.io.OutputStream, PipeEnd) -> Unit,
    ): ParcelFileDescriptor {
        val ends = ParcelFileDescriptor.createReliablePipe()
        val readSide = ends[0]
        val writeSide = ends[1]
        // One I/O pump thread for an adb-only content pipe; exception to the no-parallel-threads rule.
        Thread({
            val stream = FileOutputStream(writeSide.fileDescriptor)
            val end = PipeEnd(writeSide, readSide, stream)
            try {
                block(stream, end)
                if (!end.closed) end.closeOk()
            // kept: failure stays on this call so the screen or caller can continue
            } catch (t: Throwable) {
                if (!end.closed && writeErrorOnFailure) {
                    val reason = t.message?.ifBlank { null } ?: t.javaClass.simpleName
                    val message = if (reason.startsWith("datatransfer:")) reason else "datatransfer: $reason"
                    try {
                        end.closeError(message)
                    // kept: failure stays on this call so the screen or caller can continue
                    } catch (_: Throwable) {
                    }
                }
            }
        }, "datatransfer").apply { isDaemon = true }.start()
        return readSide
    }

    internal fun readStatusJson(): String {
        val dir = statusDir()
        val lastImport = readRecord(File(dir, LAST_IMPORT))
        val lastExport = readRecord(File(dir, LAST_EXPORT))
        return JSONObject().apply {
            put("lastImport", lastImport ?: JSONObject.NULL)
            put("lastExport", lastExport ?: JSONObject.NULL)
        }.toString()
    }

    private fun readRecord(file: File): JSONObject? {
        if (!file.isFile) return null
        return try {
            JSONObject(file.readText(Charsets.UTF_8))
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            android.util.Log.e("DataTransfer", "status unreadable", e)
            JSONObject().put("result", "status unreadable")
        }
    }

    companion object {
        const val PATH_EXPORT = "/export"
        const val PATH_IMPORT = "/import"
        const val PATH_STATUS = "/import-status"
        const val LAST_IMPORT = "last-import.json"
        const val LAST_EXPORT = "last-export.json"
        private const val STATUS_DIR = "datatransfer"

        fun statusDir(context: android.content.Context): File =
            File(context.noBackupFilesDir, STATUS_DIR)

        fun writeStatusAtomic(context: android.content.Context, name: String, json: String) {
            val dir = statusDir(context)
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw java.io.IOException("datatransfer: could not create ${dir.path}")
            }
            val tmp = File(dir, "$name.tmp")
            FileOutputStream(tmp).use { fos ->
                fos.write(json.toByteArray(Charsets.UTF_8))
                fos.fd.sync()
            }
            val target = File(dir, name)
            if (!tmp.renameTo(target)) {
                throw java.io.IOException("datatransfer: could not replace ${target.path}")
            }
            fsyncDir(dir)
        }

        fun fsyncDir(dir: File) {
            FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
        }
    }

    private fun statusDir(): File = statusDir(requireContext())

    class PipeEnd(
        private val signalSide: ParcelFileDescriptor,
        val readSide: ParcelFileDescriptor,
        private val stream: java.io.OutputStream?,
    ) {
        var closed: Boolean = false
            private set

        fun closeOk() {
            if (closed) return
            try {
                stream?.flush()
            // kept: failure stays on this call so the screen or caller can continue
            } catch (failure: Throwable) {
                closeError(failure.message?.ifBlank { null } ?: failure.toString())
                return
            }
            closed = true
            try {
                signalSide.close()
            // kept: failure stays on this call so the screen or caller can continue
            } catch (_: Throwable) {
            }
        }

        fun closeError(message: String) {
            if (closed) return
            closed = true
            try {
                signalSide.closeWithError(message)
            // kept: failure stays on this call so the screen or caller can continue
            } catch (_: Throwable) {
            }
        }
    }
}
