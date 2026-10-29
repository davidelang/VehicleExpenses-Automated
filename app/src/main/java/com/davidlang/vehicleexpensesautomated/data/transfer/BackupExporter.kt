package com.davidlang.vehicleexpensesautomated.data.transfer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import com.davidlang.vehicleexpensesautomated.data.db.APP_DB_SCHEMA_VERSION
import com.davidlang.vehicleexpensesautomated.data.db.AppDatabase
import com.davidlang.vehicleexpensesautomated.data.db.Exporting
import com.davidlang.vehicleexpensesautomated.data.db.Failed
import com.davidlang.vehicleexpensesautomated.data.db.Importing
import com.davidlang.vehicleexpensesautomated.data.db.Ok
import com.davidlang.vehicleexpensesautomated.data.db.Unchecked
import com.davidlang.vehicleexpensesautomated.stopExperimentForegroundService
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Comparator
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DataTransferEntryPoint {
    fun appDatabase(): AppDatabase
}

/**
 * Live-path export: checkpoint, copy, reopen Room, then stream.
 * A [Failed] database copies the raw files and returns to that same [Failed].
 */
class BackupExporter(private val context: Context) {
    private val app: VehicleExpensesApplication
        get() = context.applicationContext as VehicleExpensesApplication

    fun export(out: OutputStream, end: DataTransferProvider.PipeEnd) {
        val holder = app.dbState
        while (true) {
            when (val current = holder.states.value) {
                is Exporting -> {
                    end.closeError("datatransfer: export already in progress")
                    return
                }
                is Importing -> {
                    end.closeError("datatransfer: import in progress")
                    return
                }
                is Unchecked -> {
                    app.verifyDatabase()
                }
                is Ok -> {
                    val gate = Exporting(prior = Ok, gen = holder.nextGen())
                    if (!app.casLogged(Ok, gate)) continue
                    exportLive(out, end, gate)
                    return
                }
                is Failed -> {
                    val gate = Exporting(prior = current, gen = holder.nextGen())
                    if (!app.casLogged(current, gate)) continue
                    exportRaw(out, end, gate, current)
                    return
                }
            }
        }
    }

    private fun exportLive(out: OutputStream, end: DataTransferProvider.PipeEnd, gate: Exporting) {
        var staging: File? = null
        try {
            stopWorkers()
        } catch (t: Throwable) {
            abortBeforeClose(end, gate, null, messageOf(t))
            return
        }
        val planned = mutableListOf<Planned>()
        try {
            staging = File(context.noBackupFilesDir, "datatransfer/export-${System.currentTimeMillis()}")
            if (!staging.isDirectory && !staging.mkdirs()) {
                throw IllegalStateException("could not create ${staging.path}")
            }
            collectRoots(planned, rawFiles = false)
            replaceSettings(planned)
            checkPathLengths(planned)
        } catch (t: Throwable) {
            abortBeforeClose(end, gate, staging, messageOf(t))
            return
        }
        val stageDir = requireNotNull(staging)
        var raw: SQLiteDatabase? = null
        val database = entryPoint().appDatabase()
        var schema: Int? = null
        try {
            database.openHelper.close()
            val dbFile = context.getDatabasePath(BackupContents.DB_FILE_NAME)
            raw = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            Log.i(TAG, "checkpoint")
            raw.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw IllegalStateException("database busy; checkpoint incomplete")
                }
                if (cursor.getLong(0) != 0L) {
                    throw IllegalStateException("database busy; checkpoint incomplete")
                }
            }
            raw.close()
            raw = null
            val stagedDb = File(stageDir, BackupContents.DB_FILE_NAME)
            dbFile.copyTo(stagedDb, overwrite = true)
            quickCheck(stagedDb)
            planned.add(
                Planned(
                    tarPath = "${BackupContents.ROOT_DATABASES}/${BackupContents.DB_FILE_NAME}",
                    file = stagedDb,
                    bytes = null,
                    size = stagedDb.length(),
                    sha256 = BackupContents.sha256(stagedDb),
                    directory = false,
                ),
            )
            checkPathLengths(planned)
            schema = readSqliteUserVersion(stagedDb)
        } catch (t: Throwable) {
            failLive(end, gate, stageDir, raw, messageOf(t), roomReopened = false)
            return
        }
        try {
            database.openHelper.writableDatabase.query("SELECT 1").close()
        } catch (t: Throwable) {
            reopenFailed(end, gate, stageDir, t)
            return
        }
        try {
            stream(out, planned, schema, dbExport = "checkpoint-copy", dbOpenError = null, pending = null)
            DataTransferProvider.writeStatusAtomic(
                context,
                DataTransferProvider.LAST_EXPORT,
                exportJson("ok", restarted = false, message = null),
            )
            end.closeOk()
        } catch (t: Throwable) {
            failLive(end, gate, stageDir, null, messageOf(t), roomReopened = true)
            return
        }
        deleteTreeQuiet(stageDir)
        if (app.casLogged(gate, Ok)) {
            app.scheduleBackgroundWork()
        }
        app.dbState.progress.value = ""
    }

    private fun exportRaw(
        out: OutputStream,
        end: DataTransferProvider.PipeEnd,
        gate: Exporting,
        failed: Failed,
    ) {
        try {
            val planned = mutableListOf<Planned>()
            collectRoots(planned, rawFiles = true)
            replaceSettings(planned)
            checkPathLengths(planned)
            val db = planned.firstOrNull {
                it.tarPath == "${BackupContents.ROOT_DATABASES}/${BackupContents.DB_FILE_NAME}"
            }
            val schema = db?.file?.let { readSqliteUserVersion(it) }
            stream(
                out,
                planned,
                schema,
                dbExport = "raw-files",
                dbOpenError = failed.error.message,
                pending = pendingCommit(),
            )
            DataTransferProvider.writeStatusAtomic(
                context,
                DataTransferProvider.LAST_EXPORT,
                exportJson("ok", restarted = false, message = null),
            )
            end.closeOk()
        } catch (t: Throwable) {
            val reason = messageOf(t)
            try {
                DataTransferProvider.writeStatusAtomic(
                    context,
                    DataTransferProvider.LAST_EXPORT,
                    exportJson("error", restarted = false, message = reason),
                )
            } catch (e: Throwable) {
                Log.e(TAG, "raw export status write failed", e)
            }
            end.closeError("datatransfer: $reason")
        } finally {
            app.casLogged(gate, failed)
            app.dbState.progress.value = ""
        }
    }

    private fun reopenFailed(
        end: DataTransferProvider.PipeEnd,
        gate: Exporting,
        staging: File?,
        error: Throwable,
    ) {
        deleteTreeQuiet(staging)
        val reason = "database reopen failed: ${messageOf(error)}"
        try {
            DataTransferProvider.writeStatusAtomic(
                context,
                DataTransferProvider.LAST_EXPORT,
                exportJson("error", restarted = false, message = reason),
            )
        } catch (e: Throwable) {
            Log.e(TAG, "reopen status write failed", e)
        }
        end.closeError("datatransfer: $reason")
        val fromVersion = readSqliteUserVersion(File(context.dataDir, "databases/${BackupContents.DB_FILE_NAME}"))
        app.casLogged(gate, Failed(fromVersion, APP_DB_SCHEMA_VERSION, error))
        app.dbState.progress.value = ""
    }

    private fun failLive(
        end: DataTransferProvider.PipeEnd,
        gate: Exporting,
        staging: File?,
        raw: SQLiteDatabase?,
        reason: String,
        roomReopened: Boolean,
    ) {
        try {
            raw?.close()
        } catch (_: Throwable) {
        }
        deleteTreeQuiet(staging)
        try {
            DataTransferProvider.writeStatusAtomic(
                context,
                DataTransferProvider.LAST_EXPORT,
                exportJson("error", restarted = false, message = reason),
            )
        } catch (e: Throwable) {
            Log.e(TAG, "export status write failed", e)
        }
        end.closeError("datatransfer: $reason")
        if (!roomReopened) {
            try {
                val database = entryPoint().appDatabase()
                database.openHelper.writableDatabase.query("SELECT 1").close()
            } catch (t: Throwable) {
                val fromVersion = readSqliteUserVersion(
                    File(context.dataDir, "databases/${BackupContents.DB_FILE_NAME}"),
                )
                app.casLogged(gate, Failed(fromVersion, APP_DB_SCHEMA_VERSION, t))
                app.dbState.progress.value = ""
                return
            }
        }
        if (app.casLogged(gate, Ok)) {
            app.scheduleBackgroundWork()
            app.dbState.exportFailures.trySend(reason)
        }
        app.dbState.progress.value = ""
    }

    private fun abortBeforeClose(
        end: DataTransferProvider.PipeEnd,
        gate: Exporting,
        staging: File?,
        reason: String,
    ) {
        deleteTreeQuiet(staging)
        try {
            DataTransferProvider.writeStatusAtomic(
                context,
                DataTransferProvider.LAST_EXPORT,
                exportJson("error", restarted = false, message = reason),
            )
        } catch (e: Throwable) {
            Log.e(TAG, "export status write failed", e)
        }
        end.closeError("datatransfer: $reason")
        if (app.casLogged(gate, Ok)) {
            app.scheduleBackgroundWork()
            app.dbState.exportFailures.trySend(reason)
        }
        app.dbState.progress.value = ""
    }

    private fun stopWorkers() {
        stopExperimentForegroundService(context)
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWork()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (true) {
            val running = wm.getWorkInfos(
                WorkQuery.Builder.fromStates(listOf(WorkInfo.State.RUNNING)).build(),
            ).get(5, TimeUnit.SECONDS)
            if (running.isEmpty()) return
            if (System.nanoTime() >= deadline) {
                throw IllegalStateException("workers still running after 30 s; export aborted")
            }
            Thread.sleep(200)
        }
    }

    private fun collectRoots(planned: MutableList<Planned>, rawFiles: Boolean) {
        walkRoot(File(context.dataDir, "files"), BackupContents.ROOT_FILES, rawFiles, planned)
        walkRoot(File(context.dataDir, "databases"), BackupContents.ROOT_DATABASES, rawFiles, planned)
        walkRoot(File(context.dataDir, "shared_prefs"), BackupContents.ROOT_PREFS, rawFiles, planned)
        val externalParent = context.externalCacheDir?.parentFile
        if (externalParent != null) {
            walkRoot(File(externalParent, "files"), BackupContents.ROOT_EXTERNAL, rawFiles, planned)
        }
    }

    private fun replaceSettings(planned: MutableList<Planned>) {
        val index = planned.indexOfFirst {
            it.tarPath == "${BackupContents.ROOT_PREFS}/${BackupContents.SETTINGS_LEAF}"
        }
        if (index < 0) return
        val settings = planned[index]
        val source = settings.file ?: return
        val stripped = BackupContents.stripVehicleSettings(source.readBytes())
        planned[index] = settings.copy(
            file = null,
            bytes = stripped,
            size = stripped.size.toLong(),
            sha256 = BackupContents.sha256(stripped),
        )
    }

    private fun walkRoot(root: File, tarRoot: String, rawFiles: Boolean, planned: MutableList<Planned>) {
        if (!root.exists()) return
        if (Files.isSymbolicLink(root.toPath())) {
            throw IllegalStateException("refusing to export symlink: $tarRoot")
        }
        if (!root.isDirectory) return
        walk(root, tarRoot, rawFiles, planned)
    }

    private fun walk(dir: File, tarPrefix: String, rawFiles: Boolean, planned: MutableList<Planned>) {
        val children = dir.listFiles() ?: return
        for (child in children.sortedBy { it.name }) {
            val tarPath = "$tarPrefix/${child.name}"
            if (BackupContents.isExcluded(tarPath, rawFiles)) continue
            if (Files.isSymbolicLink(child.toPath())) {
                throw IllegalStateException("refusing to export symlink: $tarPath")
            }
            if (child.isDirectory) {
                planned.add(Planned(tarPath, child, null, 0L, "", directory = true))
                walk(child, tarPath, rawFiles, planned)
            } else if (child.isFile) {
                planned.add(
                    Planned(
                        tarPath = tarPath,
                        file = child,
                        bytes = null,
                        size = child.length(),
                        sha256 = BackupContents.sha256(child),
                        directory = false,
                    ),
                )
            }
        }
    }

    private fun checkPathLengths(planned: List<Planned>) {
        val manifestName = BackupContents.MANIFEST_ENTRY
        if (manifestName.toByteArray(Charsets.UTF_8).size > DataTransferTar.MAX_PATH_BYTES) {
            throw IllegalStateException("path longer than 255 bytes: $manifestName")
        }
        for (item in planned) {
            val size = item.tarPath.toByteArray(Charsets.UTF_8).size
            if (size > DataTransferTar.MAX_PATH_BYTES) {
                throw IllegalStateException("path longer than 255 bytes: ${item.tarPath}")
            }
        }
    }

    private fun stream(
        out: OutputStream,
        planned: List<Planned>,
        schema: Int?,
        dbExport: String,
        dbOpenError: String?,
        pending: JSONObject?,
    ) {
        val files = planned.filter { !it.directory }
        val manifest = BackupManifest(
            applicationId = BuildConfig.APPLICATION_ID,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
            dbSchemaVersion = schema,
            createdUtc = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            androidSdkInt = Build.VERSION.SDK_INT,
            dbExport = dbExport,
            dbOpenError = dbOpenError,
            entries = files.map { BackupFileEntry(it.tarPath, it.size, it.sha256) },
            pendingCommit = pending,
        )
        val manifestBytes = manifest.toJson().toString().toByteArray(Charsets.UTF_8)
        val writer = DataTransferTar.Writer(out)
        writer.writeFile(
            BackupContents.MANIFEST_ENTRY,
            manifestBytes.size.toLong(),
            ByteArrayInputStream(manifestBytes),
        )
        var index = 0
        val total = planned.size
        for (item in planned) {
            index++
            app.dbState.progress.value = "$index/$total ${item.tarPath}"
            if (item.directory) {
                writer.writeDirectory(item.tarPath)
            } else {
                val body = item.open()
                body.use { writer.writeFile(item.tarPath, item.size, it) }
            }
        }
        writer.finish()
    }

    private fun pendingCommit(): JSONObject? {
        val marker = File(context.noBackupFilesDir, "datatransfer/import-commit.json")
        if (!marker.isFile) return null
        val markerJson = try {
            JSONObject(marker.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "pending commit unreadable", e)
            JSONObject().put("raw", marker.readText(Charsets.UTF_8)).put("corrupt", true)
        }
        val staged = JSONArray()
        val internal = File(context.noBackupFilesDir, "datatransfer-staging")
        if (internal.exists()) staged.put(internal.absolutePath)
        val externalParent = context.externalCacheDir?.parentFile
        if (externalParent != null) {
            val external = File(externalParent, "datatransfer-staging")
            if (external.exists()) staged.put(external.absolutePath)
        }
        return JSONObject().apply {
            put("marker", markerJson)
            put("staged", staged)
            if (markerJson.optBoolean("corrupt")) put("corrupt", true)
        }
    }

    private fun quickCheck(dbFile: File) {
        val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                val results = mutableListOf<String>()
                while (cursor.moveToNext()) results.add(cursor.getString(0))
                if (results.size != 1 || results[0] != "ok") {
                    throw IllegalStateException(
                        "exported database failed quick_check: ${results.joinToString()}",
                    )
                }
            }
        } finally {
            db.close()
        }
    }

    private fun entryPoint(): DataTransferEntryPoint =
        EntryPointAccessors.fromApplication(app, DataTransferEntryPoint::class.java)

    private fun exportJson(result: String, restarted: Boolean, message: String?): String =
        JSONObject().apply {
            put("kind", "export")
            put("result", result)
            put("processRestarted", restarted)
            put("createdUtc", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
            if (message != null) put("message", message)
        }.toString()

    private fun messageOf(t: Throwable): String = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName

    private fun deleteTreeQuiet(root: File?) {
        if (root == null) return
        try {
            deleteRecursively(root)
        } catch (e: Throwable) {
            Log.e(TAG, "could not delete ${root.path}", e)
        }
    }

    companion object {
        private const val TAG = "DataTransfer"
    }
}

internal data class Planned(
    val tarPath: String,
    val file: File?,
    val bytes: ByteArray?,
    val size: Long,
    val sha256: String,
    val directory: Boolean,
) {
    fun open(): InputStream = when {
        bytes != null -> ByteArrayInputStream(bytes)
        file != null -> file.inputStream()
        else -> throw IllegalStateException("export entry has no body: $tarPath")
    }
}

internal fun deleteRecursively(path: File) {
    if (!path.exists()) return
    val paths = try {
        Files.walk(path.toPath()).use { stream ->
            stream.sorted(Comparator.reverseOrder()).toList()
        }
    } catch (e: NoSuchFileException) {
        return
    }
    for (entry in paths) {
        try {
            Files.deleteIfExists(entry)
        } catch (_: NoSuchFileException) {
        }
    }
}

internal fun readSqliteUserVersion(file: File): Int? {
    if (!file.isFile || file.length() < 64L) return null
    return try {
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(60)
            val b = ByteArray(4)
            if (raf.read(b) != 4) return null
            ((b[0].toInt() and 0xff) shl 24) or
                ((b[1].toInt() and 0xff) shl 16) or
                ((b[2].toInt() and 0xff) shl 8) or
                (b[3].toInt() and 0xff)
        }
    } catch (_: Exception) {
        null
    }
}
