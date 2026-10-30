package com.davidlang.vehicleexpensesautomated.data.transfer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.StatFs
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.davidlang.vehicleexpensesautomated.MainActivity
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import com.davidlang.vehicleexpensesautomated.data.db.APP_DB_SCHEMA_VERSION
import com.davidlang.vehicleexpensesautomated.data.db.DbState
import com.davidlang.vehicleexpensesautomated.data.db.Exporting
import com.davidlang.vehicleexpensesautomated.data.db.Failed
import com.davidlang.vehicleexpensesautomated.data.db.Importing
import com.davidlang.vehicleexpensesautomated.data.db.Ok
import com.davidlang.vehicleexpensesautomated.data.db.Unchecked
import com.davidlang.vehicleexpensesautomated.stopExperimentForegroundService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Stages a tar beside the live data, writes one commit marker, and restarts. */
class BackupImporter(private val context: Context) {
    private val app: VehicleExpensesApplication =
        context as? VehicleExpensesApplication
            ?: context.applicationContext as VehicleExpensesApplication

    fun import(input: InputStream, end: DataTransferProvider.PipeEnd) {
        val holder = app.dbState
        while (true) {
            when (val current = holder.states.value) {
                is Exporting -> {
                    val message = "export in progress; import refused"
                    writeImport(importJson(result = "error", message = message, manifest = null, entries = null, commitId = null, applied = null))
                    end.closeError("datatransfer: $message")
                    return
                }
                is Importing -> {
                    Log.w(TAG, "import in progress; import refused")
                    end.closeError("datatransfer: import in progress; import refused")
                    return
                }
                is Unchecked, is Ok, is Failed -> {
                    val gate = Importing(prior = current, gen = holder.nextGen())
                    if (!app.casLogged(current, gate)) continue
                    runImport(input, end, gate)
                    return
                }
            }
        }
    }

    fun applyStartup() {
        val marker = markerFile()
        if (marker.isFile) {
            startupSwap(marker)
        } else {
            deleteStaleStaging()
        }
    }

    private fun runImport(input: InputStream, end: DataTransferProvider.PipeEnd, gate: Importing) {
        var committed = false
        var commitId: String? = null
        var manifest: JSONObject? = null
        var entries = 0
        try {
            stopWorkers()
            val externalParent = requireExternalParent()
            val internalParent = context.noBackupFilesDir
            if (!internalParent.isDirectory || !internalParent.canWrite()) {
                throw IllegalStateException("internal storage unavailable: ${internalParent.path}")
            }
            discardPendingCommit()
            val roots = stagingRoots(externalParent)
            deleteStagingDirs()
            for (root in roots) {
                if (!root.staged.isDirectory && !root.staged.mkdirs()) {
                    throw IllegalStateException("could not create ${root.staged.path}")
                }
            }
            val brought = HashSet<String>()
            val reader = DataTransferTar.Reader(input)
            while (true) {
                val entry = reader.next() ?: break
                if (isManifest(entry.path)) {
                    val text = reader.readBodyBytes(entry).toString(Charsets.UTF_8)
                    manifest = try {
                        JSONObject(text)
                    // kept: failure stays on this call so the screen or caller can continue
                    } catch (e: Exception) {
                        Log.e(TAG, "backup-manifest.json is not JSON: ${e.message}", e)
                        throw IllegalStateException("backup-manifest.json is not JSON: ${e.message}", e)
                    }
                    continue
                }
                val rootName = BackupContents.rootOf(entry.path)
                    ?: throw IllegalStateException("entry outside the four roots: ${entry.path}")
                val root = roots.first { it.name == rootName }
                val rel = entry.path.substring(rootName.length + 1)
                val dest = destination(root.staged, rel, entry.path)
                if (entry.directory) {
                    if (!dest.isDirectory && !dest.mkdirs()) {
                        throw IllegalStateException("could not create ${dest.path}")
                    }
                    brought.add(entry.path)
                } else {
                    dest.parentFile?.let { parent ->
                        if (!parent.isDirectory && !parent.mkdirs()) {
                            throw IllegalStateException("could not create ${parent.path}")
                        }
                    }
                    FileOutputStream(dest).use { reader.readBodyTo(entry, it) }
                    brought.add(entry.path)
                    entries++
                }
                app.dbState.progress.value = entry.path
            }
            carryOver(roots, brought)
            commitId = UUID.randomUUID().toString()
            writeMarker(commitId, roots)
            committed = true
            writeImport(
                importJson(
                    result = "ok",
                    message = null,
                    manifest = manifest,
                    entries = entries,
                    commitId = commitId,
                    applied = false,
                ),
            )
            end.closeOk()
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            if (committed) {
                Log.e(TAG, "import committed; restarting after ${t.message}", t)
                if (!end.closed) end.closeOk()
                restart()
                return
            }
            abortBeforeCommit(
                gate,
                end,
                t.message?.ifBlank { null } ?: t.javaClass.simpleName,
                manifest,
            )
            return
        }
        restart()
    }

    private fun abortBeforeCommit(
        gate: Importing,
        end: DataTransferProvider.PipeEnd,
        reason: String,
        manifest: JSONObject?,
    ) {
        val dir = DataTransferProvider.statusDir(context)
        try {
            Files.deleteIfExists(File(dir, "$MARKER.tmp").toPath())
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Throwable) {
            Log.e(TAG, "commit temp delete after a failed import", e)
        }
        try {
            deleteStagingDirs()
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Throwable) {
            Log.e(TAG, "staging delete after a failed import", e)
        }
        try {
            writeImport(
                importJson(
                    result = "error",
                    message = reason,
                    manifest = manifest,
                    entries = null,
                    commitId = null,
                    applied = null,
                ),
            )
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Throwable) {
            Log.e(TAG, "import status write failed", e)
        }
        val message = if (reason.startsWith("datatransfer:")) reason else "datatransfer: $reason"
        end.closeError(message)
        if (app.casLogged(gate, gate.prior)) {
            app.resumeAfterImport(gate.prior)
        }
        app.dbState.progress.value = ""
    }

    private fun startupSwap(marker: File) {
        val parsed = try {
            JSONObject(marker.readText(Charsets.UTF_8))
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            failSwap(marker, commitId = null, reason = "commit marker doesn't parse: ${t.message}")
            return
        }
        val commitId = parsed.optString("commitId", "").ifBlank { null }
        val roots = parsed.optJSONArray("roots")
        if (roots == null) {
            failSwap(marker, commitId, "commit marker has no roots")
            return
        }
        val needsExternal = (0 until roots.length()).any {
            roots.getJSONObject(it).optString("parent") == "external"
        }
        if (needsExternal && context.externalCacheDir?.parentFile == null) {
            failSwap(marker, commitId, "external storage unavailable")
            return
        }
        var applied = false
        try {
            for (i in 0 until roots.length()) {
                val obj = roots.getJSONObject(i)
                val staged = File(obj.getString("staged"))
                val live = File(obj.getString("live"))
                if (!staged.exists()) continue
                if (obj.optString("root") == BackupContents.ROOT_DATABASES) {
                    val db = File(staged, BackupContents.DB_FILE_NAME)
                    if (db.isFile && db.length() >= 64L && readSqliteUserVersion(db) == null) {
                        failSwap(marker, commitId, "staged database version unreadable")
                        return
                    }
                }
                deleteRecursivelyOrThrow(live)
                moveAtomic(staged, live)
            }
            deleteStagingDirs()
            markApplied(commitId)
            applied = true
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            if (!applied) {
                Log.e(TAG, "startup swap failed: ${t.message}", t)
                failSwap(marker, commitId, t.message ?: t.javaClass.simpleName)
            }
        }
        if (!applied) return
        try {
            deleteMarker(marker)
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            Log.e(TAG, "commit marker delete failed after apply: ${t.message}", t)
        }
    }

    private fun failSwap(marker: File, commitId: String?, reason: String) {
        try {
            deleteMarker(marker)
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            val message = "$reason; marker delete failed: ${t.message}"
            Log.e(TAG, message, t)
            app.casLogged(
                Unchecked,
                Failed(null, APP_DB_SCHEMA_VERSION, IllegalStateException(message, t)),
            )
            return
        }
        try {
            deleteStagingDirs()
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            val path = (t as? StagingDeleteException)?.path ?: marker.path
            val message = "stale transfer staging couldn't be removed: $path: ${t.message}"
            Log.e(TAG, message, t)
            app.casLogged(
                Unchecked,
                Failed(null, APP_DB_SCHEMA_VERSION, IllegalStateException(message, t)),
            )
            return
        }
        try {
            writeImport(
                importJson(
                    result = "error",
                    message = reason,
                    manifest = null,
                    entries = null,
                    commitId = commitId,
                    applied = null,
                ),
            )
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            Log.e(TAG, "could not write import error after swap failure", t)
        }
    }

    private fun deleteStaleStaging() {
        try {
            val internal = internalStaging()
            if (internal.exists()) deleteRecursivelyOrThrow(internal)
            val status = DataTransferProvider.statusDir(context)
            status.listFiles()?.forEach { child ->
                if (child.name.startsWith("export-")) deleteRecursivelyOrThrow(child)
            }
            val parent = context.externalCacheDir?.parentFile ?: return
            val external = File(parent, STAGING)
            if (external.exists()) deleteRecursivelyOrThrow(external)
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            val path = (t as? StagingDeleteException)?.path ?: t.message.orEmpty()
            val message = "stale transfer staging couldn't be removed: $path: ${t.message}"
            Log.e(TAG, message, t)
            app.casLogged(
                Unchecked,
                Failed(null, APP_DB_SCHEMA_VERSION, IllegalStateException(message, t)),
            )
        }
    }

    private fun discardPendingCommit() {
        val marker = markerFile()
        if (!marker.exists()) return
        deleteMarker(marker)
    }

    private fun carryOver(roots: List<StagedRoot>, brought: Set<String>) {
        val copies = mutableListOf<Carry>()
        for (root in roots) {
            if (!root.live.isDirectory) continue
            collectCarry(root.live, root.live, root.name, root, brought, copies)
        }
        var internalNeed = 0L
        var externalNeed = 0L
        for (copy in copies) {
            if (copy.root.external) externalNeed += copy.bytes else internalNeed += copy.bytes
        }
        ensureSpace(context.noBackupFilesDir, internalNeed, "internal")
        if (externalNeed > 0L) {
            val parent = context.externalCacheDir?.parentFile
                ?: throw IllegalStateException("external storage unavailable")
            ensureSpace(parent, externalNeed, "external")
        }
        for (copy in copies) {
            if (copy.dest.exists()) continue
            copy.dest.parentFile?.mkdirs()
            copy.source.copyTo(copy.dest, overwrite = false)
        }
    }

    private fun collectCarry(
        dir: File,
        top: File,
        tarRoot: String,
        root: StagedRoot,
        brought: Set<String>,
        out: MutableList<Carry>,
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (Files.isSymbolicLink(child.toPath())) continue
            val rel = child.relativeTo(top).path.replace('\\', '/')
            val tarPath = "$tarRoot/$rel"
            if (child.isDirectory) {
                if (BackupContents.carryOver(tarPath) || BackupContents.carryOver("$tarPath/")) {
                    collectCarry(child, top, tarRoot, root, brought, out)
                } else if (tarPath.startsWith("$tarRoot/")) {
                    collectCarry(child, top, tarRoot, root, brought, out)
                }
            } else if (child.isFile && BackupContents.carryOver(tarPath) && tarPath !in brought) {
                val dest = File(root.staged, rel)
                out.add(Carry(child, dest, child.length(), root))
            }
        }
    }

    private fun ensureSpace(parent: File, need: Long, label: String) {
        if (need <= 0L) return
        val free = StatFs(parent.path).availableBytes
        if (need > free) {
            val more = (need - free + MB - 1) / MB
            throw IllegalStateException(
                "not enough free space to import: need $more MB more on $label storage",
            )
        }
    }

    private fun writeMarker(commitId: String, roots: List<StagedRoot>) {
        val body = JSONObject().apply {
            put("commitId", commitId)
            put("createdUtc", utcNow())
            put("roots", JSONArray().apply {
                roots.forEach { root ->
                    put(JSONObject().apply {
                        put("root", root.name)
                        put("parent", if (root.external) "external" else "internal")
                        put("live", root.live.absolutePath)
                        put("staged", root.staged.absolutePath)
                    })
                }
            })
        }
        val json = body.toString()
        try {
            DataTransferProvider.writeStatusAtomic(context, MARKER, json)
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            // Rename is the commit. A later directory fsync must not abort.
            when (markerRenameLanded(commitId)) {
                false -> throw t
                null -> {
                    Log.e(TAG, "import marker status unreadable", t)
                    app.dbState.progress.value = "import marker status unreadable"
                    return
                }
                true -> Log.e(TAG, "commit marker renamed; directory fsync failed", t)
            }
        }
    }

    private fun markerRenameLanded(commitId: String): Boolean? {
        val marker = markerFile()
        if (!marker.isFile) return false
        return try {
            JSONObject(marker.readText(Charsets.UTF_8)).optString("commitId") == commitId
        } catch (t: Throwable) {
            Log.e(TAG, "import marker status unreadable", t)
            null
        }
    }

    private fun markApplied(commitId: String?) {
        val file = File(DataTransferProvider.statusDir(context), DataTransferProvider.LAST_IMPORT)
        val existing = if (file.isFile) {
            try {
                JSONObject(file.readText(Charsets.UTF_8))
            // kept: failure stays on this call so the screen or caller can continue
            } catch (e: Exception) {
                Log.e(TAG, "import status unreadable; writing a fresh record", e)
                null
            }
        } else {
            null
        }
        val out = if (existing != null && commitId != null && existing.optString("commitId") == commitId) {
            existing.put("applied", true)
            existing.put("appliedUtc", utcNow())
            existing
        } else {
            JSONObject().apply {
                put("kind", "import")
                put("result", "ok")
                put("applied", true)
                if (commitId != null) put("commitId", commitId)
                put("appliedUtc", utcNow())
            }
        }
        DataTransferProvider.writeStatusAtomic(context, DataTransferProvider.LAST_IMPORT, out.toString())
    }

    private fun requireExternalParent(): File {
        val parent = context.externalCacheDir?.parentFile
            ?: throw IllegalStateException("external storage unavailable")
        if (!parent.isDirectory || !parent.canWrite()) {
            throw IllegalStateException("external storage unavailable: ${parent.path}")
        }
        return parent
    }

    private fun stagingRoots(externalParent: File): List<StagedRoot> {
        val internal = internalStaging()
        return listOf(
            StagedRoot("files", File(context.dataDir, "files"), File(internal, "files"), external = false),
            StagedRoot("databases", File(context.dataDir, "databases"), File(internal, "databases"), external = false),
            StagedRoot("shared_prefs", File(context.dataDir, "shared_prefs"), File(internal, "shared_prefs"), external = false),
            StagedRoot("external", File(externalParent, "files"), File(externalParent, "$STAGING/files"), external = true),
        )
    }

    private fun deleteStagingDirs() {
        val internal = internalStaging()
        if (internal.exists()) deleteRecursivelyOrThrow(internal)
        val parent = context.externalCacheDir?.parentFile ?: return
        val external = File(parent, STAGING)
        if (external.exists()) deleteRecursivelyOrThrow(external)
    }

    private fun markerFile(): File = File(DataTransferProvider.statusDir(context), MARKER)

    private fun internalStaging(): File = File(context.noBackupFilesDir, STAGING)

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
                throw IllegalStateException("workers still running after 30 s; import aborted")
            }
            Thread.sleep(200)
        }
    }

    private fun restart() {
        try {
            val intent = Intent.makeRestartActivityTask(ComponentName(context, MainActivity::class.java))
            context.startActivity(intent)
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: Throwable) {
            Log.e(TAG, "restart activity blocked; process will still exit", t)
        }
        Process.killProcess(Process.myPid())
    }

    private fun writeImport(json: String) {
        DataTransferProvider.writeStatusAtomic(context, DataTransferProvider.LAST_IMPORT, json)
    }

    private fun importJson(
        result: String,
        message: String?,
        manifest: JSONObject?,
        entries: Int?,
        commitId: String?,
        applied: Boolean?,
    ): String = JSONObject().apply {
        put("kind", "import")
        put("result", result)
        if (message != null) put("message", message)
        if (applied != null) put("applied", applied)
        if (commitId != null) put("commitId", commitId)
        if (entries != null) put("entries", entries)
        when (manifest) {
            null -> if (result == "ok") put("manifest", JSONObject.NULL)
            is JSONObject -> put("manifest", manifest)
        }
    }.toString()

    private fun utcNow(): String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()

    private data class StagedRoot(
        val name: String,
        val live: File,
        val staged: File,
        val external: Boolean,
    )

    private data class Carry(
        val source: File,
        val dest: File,
        val bytes: Long,
        val root: StagedRoot,
    )

    companion object {
        private const val TAG = "DataTransfer"
        private const val STAGING = "datatransfer-staging"
        private const val MARKER = "import-commit.json"
        private const val MB = 1024L * 1024L

        fun applyStartup(app: VehicleExpensesApplication) {
            BackupImporter(app).applyStartup()
        }

        private fun isManifest(path: String): Boolean =
            path == BackupContents.MANIFEST_ENTRY || path.endsWith("/${BackupContents.MANIFEST_ENTRY}")

        private fun destination(stagedRoot: File, relative: String, tarPath: String): File {
            val dest = File(stagedRoot, relative)
            val rootPath = stagedRoot.canonicalPath
            val destPath = dest.canonicalPath
            if (destPath != rootPath && !destPath.startsWith(rootPath + File.separator)) {
                throw IllegalStateException("entry outside the four roots: $tarPath")
            }
            return dest
        }

        private fun deleteMarker(marker: File) {
            val dir = marker.parentFile
            try {
                Files.deleteIfExists(marker.toPath())
            // kept: failure stays on this call so the screen or caller can continue
            } catch (_: NoSuchFileException) {
                return
            }
            if (dir != null && dir.isDirectory) DataTransferProvider.fsyncDir(dir)
        }

        private fun moveAtomic(staged: File, live: File) {
            live.parentFile?.let { parent ->
                if (!parent.isDirectory && !parent.mkdirs()) {
                    throw IllegalStateException("could not create ${parent.path}")
                }
            }
            try {
                Files.move(staged.toPath(), live.toPath(), StandardCopyOption.ATOMIC_MOVE)
            // kept: failure stays on this call so the screen or caller can continue
            } catch (_: AtomicMoveNotSupportedException) {
                throw IllegalStateException("could not rename staged to live")
            }
        }
    }
}

private class StagingDeleteException(val path: String, cause: Throwable) : Exception(cause.message, cause)

internal fun deleteRecursivelyOrThrow(path: File) {
    try {
        deleteRecursively(path)
    // kept: failure stays on this call so the screen or caller can continue
    } catch (t: Throwable) {
        throw StagingDeleteException(path.path, t)
    }
}
