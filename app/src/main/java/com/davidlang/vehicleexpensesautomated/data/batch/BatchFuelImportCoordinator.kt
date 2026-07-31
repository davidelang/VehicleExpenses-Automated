package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupScheduler
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
// FuelLocationJson same package
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.ui.experiment.AlignmentSetJRunner
import com.davidlang.vehicleexpensesautomated.ui.experiment.PumpSetIRunner
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoExifMetaReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class BatchImportProgress(
    val phase: String,
    val current: Int,
    val total: Int,
    val message: String,
    val dashInserted: Int = 0,
    val pumpInserted: Int = 0,
    val pendingCount: Int = 0,
    val errors: Int = 0,
)

data class BatchImportResult(
    val dashInserted: Int,
    val pumpInserted: Int,
    val pending: List<BatchPendingItem>,
    val errors: List<String>,
    val cancelled: Boolean,
)

/** Result of applying [FuelRowMergeEngine.planMerge] to the live fuel table. */
data class MergeApplyResult(
    val updated: Int,
    val deleted: Int,
    val pendingAdded: Int,
    val totalPending: Int,
    val message: String,
)

/**
 * Stage A batch ingest: walk hard-coded experiment photo dirs, OCR, insert partials.
 * Merge (Stage B) is a separate call / button.
 *
 * **Photo paths:** batch references **source files in place** (`experiment_photos`,
 * `pump_photos`). It does **not** copy into `batch_import_photos` or invent new
 * basenames. If a source file is later deleted, local thumbs may break until
 * re-supplied — accepted vs filling disk with mirrors. Cloud photo backup may
 * still upload remote copies; that is separate from local batch mirrors.
 */
@Singleton
class BatchFuelImportCoordinator @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val fuelEntryRepository: FuelEntryRepository,
    private val mergeAckStore: MergeAckStore,
) {
    companion object {
        private const val TAG = "BatchFuelImport"
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "dng")

        /** Limited import size (like experiment Golden/Problem subset buttons). */
        const val LIMITED_IMPORT_COUNT = 20

        /**
         * Fuel row with no vehicle yet (pump-only batch ingest).
         * Merge later pairs by time/location with dash rows and assigns a real vehicleId.
         * No Room FK; 0 is not a real [Vehicle.id].
         */
        const val UNASSIGNED_VEHICLE_ID = 0

        fun dashPhotoDir(context: Context): File =
            File(context.filesDir, "experiment_photos").also { it.mkdirs() }

        fun pumpPhotoDir(context: Context): File =
            File(context.getExternalFilesDir(null), "pump_photos").also { it.mkdirs() }

        /**
         * Legacy local mirror dir (no longer written by batch).
         * Kept for migration / cleanup only — does **not** mkdirs.
         */
        fun durablePhotoDir(context: Context): File =
            File(context.filesDir, "batch_import_photos")

        /** `dash_123_PXL_….jpg` / `pump_123_…` durable basenames from old copyToDurable. */
        private val DURABLE_BASENAME =
            Regex("""^(dash|pump)_(\d+)_(.+)$""", RegexOption.IGNORE_CASE)
    }

    private val cancelFlag = AtomicBoolean(false)

    fun requestCancel() {
        cancelFlag.set(true)
    }

    fun clearCancel() {
        cancelFlag.set(false)
    }

    private fun listImages(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f ->
            f.isFile && f.extension.lowercase() in IMAGE_EXTS
        }?.sortedBy { it.name } ?: emptyList()
    }

    /**
     * Dash odometer from Set J is already [pickBestOdometer]-filtered (4–7 pure digits) or null.
     * Do **not** digit-concatenate arbitrary OCR soup.
     */
    private fun parseSetJOdometer(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        if (raw.length !in 4..7 || !raw.all { it.isDigit() }) return null
        return raw.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun parseMoneyOrVol(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(Regex("[^0-9.]"), "")
        if (cleaned.isBlank() || cleaned == ".") return null
        return cleaned.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    /**
     * Stage A: ingest dash + pump archives into fuel_entries partials / pending.
     * Does not merge.
     *
     * @param maxDash if non-null, only the first N dash images (sorted by name) are processed.
     * @param maxPump if non-null, only the first N pump images (sorted by name) are processed.
     */
    suspend fun runIngest(
        vehicles: List<Vehicle>,
        onProgress: (BatchImportProgress) -> Unit = {},
        maxDash: Int? = null,
        maxPump: Int? = null,
    ): BatchImportResult = withContext(Dispatchers.Default) {
        clearCancel()
        val pending = BatchImportPendingStore.load(appContext).toMutableList()
        val errors = mutableListOf<String>()
        var dashInserted = 0
        var pumpInserted = 0
        var errCount = 0

        val allDash = listImages(dashPhotoDir(appContext))
        val allPump = listImages(pumpPhotoDir(appContext))
        val dashFiles = if (maxDash != null) allDash.take(maxDash.coerceAtLeast(0)) else allDash
        val pumpFiles = if (maxPump != null) allPump.take(maxPump.coerceAtLeast(0)) else allPump
        val total = dashFiles.size + pumpFiles.size
        var done = 0

        fun report(phase: String, msg: String) {
            onProgress(
                BatchImportProgress(
                    phase = phase,
                    current = done,
                    total = total,
                    message = msg,
                    dashInserted = dashInserted,
                    pumpInserted = pumpInserted,
                    pendingCount = pending.size,
                    errors = errCount,
                ),
            )
        }

        val limitNote = when {
            maxDash != null || maxPump != null ->
                " (limited: dash ${dashFiles.size}/${allDash.size}, pump ${pumpFiles.size}/${allPump.size})"
            else -> ""
        }
        report("init", "Dash ${dashFiles.size} · pump ${pumpFiles.size}$limitNote")

        // One-shot-ish: rewrite old durable mirrors → source paths; free disk
        try {
            val mig = migrateDurablePhotoRefsToSource()
            if (mig.rewroteRows > 0 || mig.deletedFiles > 0) {
                Log.i(TAG, "durable migrate: $mig")
                report("init", "Migrated ${mig.rewroteRows} photo refs, deleted ${mig.deletedFiles} mirrors")
            }
        } catch (e: Exception) {
            Log.w(TAG, "durable migrate skipped: ${e.message}")
        }

        // --- Dash (Set J via AlignmentSetJRunner) ---
        for (file in dashFiles) {
            coroutineContext.ensureActive()
            if (cancelFlag.get()) {
                BatchImportPendingStore.save(appContext, pending)
                return@withContext BatchImportResult(
                    dashInserted, pumpInserted, pending, errors, cancelled = true,
                )
            }
            done++
            report("dash", "Dash OCR ${file.name} ($done/$total)")
            try {
                processDash(file, vehicles, pending)?.let { dashInserted++ }
            } catch (e: Exception) {
                errCount++
                val m = "Dash ${file.name}: ${e.message}"
                Log.e(TAG, m, e)
                errors.add(m)
            }
        }

        // --- Pump (Set I): always OCR + insert; vehicleId left unassigned (0) until merge ---
        for (file in pumpFiles) {
            coroutineContext.ensureActive()
            if (cancelFlag.get()) {
                BatchImportPendingStore.save(appContext, pending)
                return@withContext BatchImportResult(
                    dashInserted, pumpInserted, pending, errors, cancelled = true,
                )
            }
            done++
            report("pump", "Pump Set I ${file.name} ($done/$total)")
            try {
                val inserted = processPump(file, pending)
                if (inserted) pumpInserted++
            } catch (e: Exception) {
                errCount++
                val m = "Pump ${file.name}: ${e.message}"
                Log.e(TAG, m, e)
                errors.add(m)
            }
        }

        BatchImportPendingStore.save(appContext, pending)
        report("done", "Finished: dash=$dashInserted pump=$pumpInserted pending=${pending.size}")
        BatchImportResult(dashInserted, pumpInserted, pending, errors, cancelled = false)
    }

    /**
     * Field-merge all live partials + **phase-scoped** pending rebuild only.
     * Pending store holds at most the current phase’s kinds (not full multi-phase backlog).
     */
    suspend fun applyMerge(
        onProgress: (String) -> Unit = {},
    ): MergeApplyResult = withContext(Dispatchers.IO) {
        val phase = StageCPhaseStore.currentPhase(appContext)
        runFieldMerge(onProgress) + rebuildPendingForPhase(phase, onProgress)
    }

    /**
     * Advance Stage C to [newPhase] (or current+1), field-merge, generate **only**
     * that phase’s questions from live Room.
     */
    suspend fun advancePhaseAndRebuild(
        onProgress: (String) -> Unit = {},
    ): MergeApplyResult = withContext(Dispatchers.IO) {
        val next = StageCPhaseStore.advance(appContext)
        onProgress("Next phase $next…")
        val merge = runFieldMerge(onProgress)
        val rebuild = rebuildPendingForPhase(next, onProgress)
        val combined = merge + rebuild
        val msg = "Phase $next: ${combined.totalPending} questions · ${combined.message}"
        onProgress(msg)
        combined.copy(message = msg)
    }

    data class FieldMergeStats(
        val updated: Int,
        val deleted: Int,
        val liveCount: Int = 0,
        val pendingFromMerge: Int = 0,
        val secondPass: Boolean = false,
    )

    /**
     * Field-merge only (no Stage C rebuild). Used after fuel LWW **before** sheet
     * write-back so survivors + tombstones leave on the same Sync.
     * Re-runs once if first pass absorbed nothing but unmatched odo/pump pairs remain.
     */
    suspend fun fieldMergeForSync(
        onProgress: (String) -> Unit = {},
    ): FieldMergeStats = withContext(Dispatchers.IO) {
        val first = runFieldMerge(onProgress)
        val live = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
        val unmatched = FuelRowMergeEngine.hasUnmatchedPartials(live)
        Log.i(
            TAG,
            "fieldMergeForSync pass1 live=${first.liveCount} updates=${first.updated} " +
                "deletes=${first.deleted} pending=${first.pendingFromMerge} unmatched=$unmatched",
        )
        if (first.deleted == 0 && unmatched) {
            onProgress("Second-pass field merge (unmatched partials remain)…")
            Log.i(TAG, "fieldMergeForSync second-pass merge")
            val second = runFieldMerge(onProgress)
            return@withContext FieldMergeStats(
                updated = first.updated + second.updated,
                deleted = first.deleted + second.deleted,
                liveCount = second.liveCount,
                pendingFromMerge = second.pendingFromMerge,
                secondPass = true,
            )
        }
        first
    }

    private suspend fun runFieldMerge(onProgress: (String) -> Unit): FieldMergeStats {
        onProgress("Photo path migrate (if needed)…")
        try {
            val mig = migrateDurablePhotoRefsToSource()
            if (mig.rewroteRows > 0 || mig.deletedFiles > 0) {
                Log.i(TAG, "durable migrate before merge: $mig")
            }
        } catch (e: Exception) {
            Log.w(TAG, "durable migrate before merge: ${e.message}")
        }
        onProgress("Loading fuel entries…")
        val all = fuelEntryRepository.getAllIncludingDeleted()
        val live = all.filter { !it.deleted }
        onProgress("Planning merge (${live.size} live / ${all.size} incl deleted)…")
        val exemptSets = try {
            mergeAckStore.liveMergeExemptSets()
        } catch (e: Exception) {
            Log.w(TAG, "load merge exempt sets failed: ${e.message}")
            emptyList()
        }
        val plan = FuelRowMergeEngine.planMerge(live, mergeExemptSets = exemptSets)
        Log.i(
            TAG,
            "planMerge live=${live.size} updates=${plan.updates.size} " +
                "hardDeletes=${plan.hardDeletes.size} newPending=${plan.newPending.size} " +
                "exemptSets=${exemptSets.size}",
        )
        var soft = 0
        var hard = 0
        if (!plan.isEmpty()) {
            onProgress("Applying ${plan.updates.size} updates…")
            for (u in plan.updates) {
                fuelEntryRepository.updateFuelEntry(u)
            }
            onProgress("Absorbing ${plan.hardDeletes.size} rows…")
            for (d in plan.hardDeletes) {
                if (absorbMergedRow(d)) soft++ else hard++
            }
            Log.i(TAG, "merge absorb soft=$soft hard=$hard")
        }
        // Stash merge-side pending for phase filter in rebuild
        lastMergePending = plan.newPending
        return FieldMergeStats(
            updated = plan.updates.size,
            deleted = plan.hardDeletes.size,
            liveCount = live.size,
            pendingFromMerge = plan.newPending.size,
        )
    }

    @Volatile
    private var lastMergePending: List<BatchPendingItem> = emptyList()

    /**
     * Generate pending for **one** phase only; apply skip ledger; save store.
     */
    private suspend fun rebuildPendingForPhase(
        phase: Int,
        onProgress: (String) -> Unit,
    ): MergeApplyResult {
        onProgress("Rebuilding phase $phase questions…")
        val afterLive = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
        val candidates = mutableListOf<BatchPendingItem>()
        fun append(p: BatchPendingItem) {
            if (!StageCPhaseStore.belongsToPhase(p, phase)) return
            if (isPendingDup(candidates, p)) return
            candidates.add(p)
        }

        when (phase) {
            StageCPhase.SIMPLE_ODO.number -> {
                val san = FuelOdoSanitizer.sanitize(afterLive)
                for (p in san.newPending) {
                    if (p.extra["mode"] == "simple") append(p)
                }
            }
            StageCPhase.COMPLEX_ODO.number -> {
                val san = FuelOdoSanitizer.sanitize(afterLive)
                for (p in san.newPending) {
                    if (p.extra["mode"] != "simple") append(p)
                }
                for (p in lastMergePending) {
                    if (p.kind == BatchPendingKind.CONFLICT_ODO) append(p)
                }
            }
            StageCPhase.BAD_PUMP.number -> {
                for (p in FuelEconomyOutliers.detectBadPumpRatios(afterLive)) append(p)
            }
            StageCPhase.UNASSIGNED.number -> {
                for (e in afterLive.filter { it.vehicleId == UNASSIGNED_VEHICLE_ID }) {
                    append(unknownVehiclePendingWithSuggest(e, afterLive))
                }
                for (p in lastMergePending) {
                    if (p.kind == BatchPendingKind.ASSIGN_VEHICLE ||
                        p.kind == BatchPendingKind.SKIP_OR_ASSIGN_VEHICLE
                    ) {
                        append(p)
                    }
                }
            }
            StageCPhase.UNREADABLE.number -> {
                for (p in lastMergePending) {
                    if (p.kind == BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE ||
                        p.kind == BatchPendingKind.UNREADABLE_PUMP ||
                        p.kind == BatchPendingKind.AMBIGUOUS_MULTI_PUMP
                    ) {
                        append(p)
                    }
                }
                // Preserve unreadable still on disk pending from batch ingest this session
                for (p in BatchImportPendingStore.load(appContext)) {
                    if (p.kind == BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE ||
                        p.kind == BatchPendingKind.UNREADABLE_PUMP ||
                        p.kind == BatchPendingKind.AMBIGUOUS_MULTI_PUMP
                    ) {
                        append(p)
                    }
                }
            }
            StageCPhase.MPG.number -> {
                for (leg in FuelEconomyOutliers.detectOutliers(afterLive)) {
                    append(FuelEconomyOutliers.toPending(leg))
                }
                for (e in afterLive.filter { it.economyIgnored }) {
                    append(FuelEconomyOutliers.economyIgnoredPending(e))
                }
            }
        }

        val skipped = StageCSkipLedger.load(appContext, phase)
        val afterSkip = StageCSkipLedger.filterOut(candidates, skipped)
        val rebuilt = try {
            // Enrich member syncIds from Room when only entryIds present
            val liveById = afterLive.associateBy { it.id }
            val enriched = afterSkip.map { item ->
                val existing = mergeAckStore.pendingMemberSyncIds(item)
                if (existing.isNotEmpty()) return@map item
                val ids = item.extra["entryIds"]
                    ?.split(',')
                    ?.mapNotNull { it.trim().toLongOrNull() }
                    .orEmpty()
                if (ids.isEmpty()) return@map item
                val syncIds = ids.mapNotNull { liveById[it]?.syncId?.takeIf { s -> s.isNotBlank() } }
                if (syncIds.isEmpty()) return@map item
                item.copy(
                    extra = item.extra + mapOf(
                        "entrySyncIds" to syncIds.joinToString(","),
                        "memberSyncIds" to syncIds.sorted().joinToString(","),
                    ),
                )
            }
            mergeAckStore.filterPending(enriched)
        } catch (e: Exception) {
            Log.w(TAG, "mergeAck filterPending failed: ${e.message}")
            afterSkip
        }
        BatchImportPendingStore.save(appContext, rebuilt)

        val msg =
            "phase $phase: ${rebuilt.size} questions " +
                "(skippedLedger=${skipped.size}, candidates=${candidates.size}, " +
                "afterAckFilter=${rebuilt.size})"
        Log.i(TAG, "rebuildPendingForPhase $msg")
        onProgress("Done: $msg")
        return MergeApplyResult(
            updated = 0,
            deleted = 0,
            pendingAdded = rebuilt.size,
            totalPending = rebuilt.size,
            message = msg,
        )
    }

    private operator fun FieldMergeStats.plus(r: MergeApplyResult): MergeApplyResult =
        MergeApplyResult(
            updated = updated + r.updated,
            deleted = deleted + r.deleted,
            pendingAdded = r.pendingAdded,
            totalPending = r.totalPending,
            message = "updated=$updated deleted=$deleted · ${r.message}",
        )

    /**
     * ASSIGN_UNKNOWN with optional tank/time suggest (phase 4).
     */
    private fun unknownVehiclePendingWithSuggest(
        e: FuelEntry,
        allLive: List<FuelEntry>,
    ): BatchPendingItem {
        val base = FuelEconomyOutliers.unknownVehiclePending(e)
        val suggest = suggestVehicleForUnassigned(e, allLive) ?: return base
        return base.copy(
            suggestedVehicleId = suggest.first,
            message = base.message + " · suggest vehicle=${suggest.first} (${suggest.second})",
            extra = base.extra + mapOf(
                "suggestedVehicleId" to suggest.first.toString(),
                "suggestReason" to suggest.second,
            ),
        )
    }

    /**
     * Unique tank fit → time-nearest known-vehicle fill. Null if ambiguous.
     */
    private fun suggestVehicleForUnassigned(
        pump: FuelEntry,
        allLive: List<FuelEntry>,
    ): Pair<Int, String>? {
        val known = allLive.filter { it.vehicleId > 0 && !it.deleted }
        if (known.isEmpty()) return null
        val maxFillByVehicle = known
            .filter { it.gallons > 0 }
            .groupBy { it.vehicleId }
            .mapValues { (_, rows) -> rows.maxOf { it.gallons } }
        val vol = pump.gallons
        if (vol > 0) {
            val tankOk = FuelRowMergeEngine.tankEligibleVehicles(
                pumpVol = vol,
                activeVehicleIds = known.map { it.vehicleId }.toSet(),
                maxFillByVehicle = maxFillByVehicle,
            )
            if (tankOk.size == 1) {
                return tankOk.first() to "tank fit"
            }
            if (tankOk.isNotEmpty()) {
                val nearest = known
                    .filter { it.vehicleId in tankOk && it.odometer > 0 }
                    .minByOrNull { kotlin.math.abs(it.timestamp - pump.timestamp) }
                if (nearest != null) {
                    val competitors = known.filter {
                        it.vehicleId != nearest.vehicleId &&
                            it.vehicleId in tankOk &&
                            kotlin.math.abs(it.timestamp - pump.timestamp) <
                            kotlin.math.abs(nearest.timestamp - pump.timestamp) + 60_000
                    }
                    if (competitors.isEmpty()) {
                        return nearest.vehicleId to "tank + nearest in time"
                    }
                }
            }
        }
        val nearestAny = known
            .filter { it.odometer > 0 || it.cost > 0 }
            .minByOrNull { kotlin.math.abs(it.timestamp - pump.timestamp) }
            ?: return null
        val window = FuelRowMergeEngine.MERGE_WINDOW_MS * 2
        val nearSame = known.filter {
            kotlin.math.abs(it.timestamp - pump.timestamp) <= window &&
                it.vehicleId != nearestAny.vehicleId
        }
        if (nearSame.isEmpty() &&
            kotlin.math.abs(nearestAny.timestamp - pump.timestamp) <= window
        ) {
            return nearestAny.vehicleId to "nearest fill in time"
        }
        return null
    }

    /**
     * After fuel sync: reset phase 1 + skip ledger when fuel changed; rebuild phase 1 only.
     */
    suspend fun postSyncRescanResetPhase(
        fuelRowsChanged: Boolean,
        onProgress: (String) -> Unit = {},
    ): MergeApplyResult {
        if (fuelRowsChanged) {
            StageCPhaseStore.resetToPhase1(appContext)
            onProgress("Sync updated fuel — review questions restarted (phase 1)")
            Log.i(TAG, "postSync: reset Stage C to phase 1 (remote=$fuelRowsChanged)")
        }
        val result = applyMerge(onProgress)
        if (!fuelRowsChanged && (result.updated > 0 || result.deleted > 0)) {
            StageCPhaseStore.resetToPhase1(appContext)
            onProgress("Sync field-merge changed rows — phase 1")
            return applyMerge(onProgress)
        }
        return result
    }

    /**
     * Absorb a merge loser. Soft-delete when the row may need a sync tombstone
     * (photo cloudManifest set, or not a pure local batch_import path). Hard-delete
     * pure local batch rows that were never pushed.
     *
     * @return true if soft-deleted, false if hard-deleted
     */
    private suspend fun absorbMergedRow(entry: FuelEntry): Boolean {
        return if (shouldSoftDeleteOnAbsorb(entry)) {
            fuelEntryRepository.markFuelDeleted(entry)
            Log.i(TAG, "merge softDelete id=${entry.id} vehicle=${entry.vehicleId} loc=${entry.location}")
            true
        } else {
            fuelEntryRepository.hardDeleteFuelEntry(entry)
            Log.i(TAG, "merge hardDelete id=${entry.id} vehicle=${entry.vehicleId} loc=${entry.location}")
            false
        }
    }

    /**
     * Soft-delete when: cloud photo manifest present, **or** not a pure local batch
     * provenance tag (notes preferred; location for legacy). Quick Fill / sheet rows
     * often have null or free text / station JSON → soft-delete.
     */
    internal fun shouldSoftDeleteOnAbsorb(e: FuelEntry): Boolean {
        if (!e.cloudManifest.isNullOrBlank()) return true
        val tag = e.notes?.takeIf { it.isNotBlank() } ?: e.location.orEmpty()
        if (tag.startsWith("batch_import") ||
            tag.startsWith("batch_manual") ||
            tag.startsWith("batch_gap")
        ) {
            return false
        }
        return true
    }

    /** Detect odo-only + pump-only pairs in window (post-sync CTA). */
    suspend fun hasUnmatchedPartials(): Boolean = withContext(Dispatchers.IO) {
        val live = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
        FuelRowMergeEngine.hasUnmatchedPartials(live)
    }

    /**
     * Dedupe: primary `(kind, fuelEntryId)`; when no id, durable/photo stem.
     */
    private fun isPendingDup(existing: List<BatchPendingItem>, p: BatchPendingItem): Boolean {
        return existing.any { e ->
            if (e.kind != p.kind) return@any false
            if (p.fuelEntryId != null && p.fuelEntryId > 0) {
                e.fuelEntryId == p.fuelEntryId
            } else {
                val stemP = pendingDedupeStem(p)
                val stemE = pendingDedupeStem(e)
                stemP.isNotBlank() && stemP == stemE
            }
        }
    }

    private fun pendingDedupeStem(p: BatchPendingItem): String {
        val path = p.durablePhotoPath ?: p.photoPath
            ?: p.extra["photoPaths"]?.split('|')?.firstOrNull()
            ?: return ""
        return photoStem(path)
    }

    /** Drop every pending item that references [fuelEntryId] (all kinds). */
    private fun removePendingForFuelEntry(fuelEntryId: Long?) {
        if (fuelEntryId == null || fuelEntryId <= 0) return
        val items = BatchImportPendingStore.load(appContext).filterNot { e ->
            e.fuelEntryId == fuelEntryId ||
                e.extra["suspectId"] == fuelEntryId.toString() ||
                e.extra["endEntryId"] == fuelEntryId.toString() ||
                e.extra["entryIds"]
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.contains(fuelEntryId.toString()) == true
        }
        BatchImportPendingStore.save(appContext, items)
    }

    /**
     * Clear all pending and re-run [applyMerge] (human purge / rebuild button).
     * Restarts Stage C at phase 1.
     */
    suspend fun clearPendingAndRescan(
        onProgress: (String) -> Unit = {},
    ): MergeApplyResult {
        BatchImportPendingStore.clear(appContext)
        StageCPhaseStore.resetToPhase1(appContext)
        onProgress("Pending cleared; phase 1; running merge + re-scan…")
        return applyMerge(onProgress)
    }

    /**
     * Apply a pending answer from the Import questions UI.
     * Caller should [applyMerge] when [PendingAnswerResult.remerge] is true.
     */
    suspend fun applyPendingAnswer(
        item: BatchPendingItem,
        vehicles: List<Vehicle>,
        action: PendingAnswerAction,
    ): PendingAnswerResult = withContext(Dispatchers.Default) {
        val phase = StageCPhaseStore.currentPhase(appContext)
        val entryBefore = item.fuelEntryId?.let { id ->
            fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
        } ?: item.extra["suspectId"]?.toLongOrNull()?.let { id ->
            fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
        }
        val result = when (action) {
            is PendingAnswerAction.Skip -> {
                StageCSkipLedger.add(appContext, phase, item)
                BatchImportPendingStore.remove(appContext, item.id)
                PendingAnswerResult("Skipped pending item", remerge = false)
            }
            is PendingAnswerAction.AcknowledgeLooksCorrect -> {
                val kind = action.kind?.takeIf { it.isNotBlank() }
                    ?: when (item.kind) {
                        BatchPendingKind.MPG_OUTLIER ->
                            com.davidlang.vehicleexpensesautomated.data.model.MergeAck.KIND_MPG_OUTLIER
                        BatchPendingKind.AMBIGUOUS_MULTI_PUMP ->
                            com.davidlang.vehicleexpensesautomated.data.model.MergeAck.KIND_AMBIGUOUS_MULTI_PUMP
                        BatchPendingKind.CONFLICT_ODO ->
                            com.davidlang.vehicleexpensesautomated.data.model.MergeAck.KIND_CONFLICT_ODO
                        else -> item.kind.name
                    }
                val alsoExempt = kind ==
                    com.davidlang.vehicleexpensesautomated.data.model.MergeAck.KIND_CONFLICT_ODO ||
                    kind ==
                    com.davidlang.vehicleexpensesautomated.data.model.MergeAck.KIND_AMBIGUOUS_MULTI_PUMP ||
                    kind ==
                    com.davidlang.vehicleexpensesautomated.data.model.MergeAck.KIND_MERGE_EXEMPT
                durableAckLooksCorrect(item, kind, alsoMergeExempt = alsoExempt)
            }
            is PendingAnswerAction.AssignVehicle -> {
                when (item.kind) {
                    BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE,
                    BatchPendingKind.SKIP_OR_ASSIGN_VEHICLE,
                    -> {
                        val path = item.photoPath ?: item.durablePhotoPath
                            ?: return@withContext PendingAnswerResult(
                                "No photo path on pending item",
                                success = false,
                            )
                        val file = File(path)
                        if (!file.isFile) {
                            return@withContext PendingAnswerResult(
                                "Photo missing: $path",
                                success = false,
                            )
                        }
                        NativePaddleEngine.initializeGlobalBuffers(appContext)
                        val ok = processDash(
                            file = file,
                            vehicles = vehicles,
                            pending = mutableListOf(),
                            forcedVehicleId = action.vehicleId,
                            enqueuePendingOnFail = false,
                        )
                        if (ok) {
                            clearAnsweredPending(item, item.fuelEntryId)
                            PendingAnswerResult(
                                "Reprocessed dash with vehicle ${action.vehicleId}",
                                remerge = true,
                            )
                        } else {
                            PendingAnswerResult(
                                "Dash reprocess failed for vehicle ${action.vehicleId}",
                                success = false,
                            )
                        }
                    }
                    BatchPendingKind.ASSIGN_VEHICLE -> {
                        val path = item.photoPath ?: item.durablePhotoPath
                            ?: return@withContext PendingAnswerResult(
                                "No photo path on pending item",
                                success = false,
                            )
                        val file = File(path)
                        if (!file.isFile) {
                            return@withContext PendingAnswerResult(
                                "Photo missing: $path",
                                success = false,
                            )
                        }
                        NativePaddleEngine.initializeGlobalBuffers(appContext)
                        val ok = processPump(
                            file = file,
                            pending = mutableListOf(),
                            forcedVehicleId = action.vehicleId,
                            enqueuePendingOnFail = false,
                        )
                        if (ok) {
                            clearAnsweredPending(item, item.fuelEntryId)
                            PendingAnswerResult(
                                "Pump processed for vehicle ${action.vehicleId}",
                                remerge = true,
                            )
                        } else {
                            PendingAnswerResult("Pump reprocess failed", success = false)
                        }
                    }
                    else -> {
                        clearAnsweredPending(item, item.fuelEntryId)
                        PendingAnswerResult(
                            "Removed pending (assign not applicable to ${item.kind})",
                            remerge = false,
                        )
                    }
                }
            }
            is PendingAnswerAction.RetryPump -> {
                val path = item.photoPath ?: item.durablePhotoPath
                    ?: return@withContext PendingAnswerResult("No photo path", success = false)
                val file = File(path)
                if (!file.isFile) {
                    return@withContext PendingAnswerResult("Photo missing", success = false)
                }
                NativePaddleEngine.initializeGlobalBuffers(appContext)
                val ok = processPump(
                    file = file,
                    pending = mutableListOf(),
                    forcedVehicleId = null,
                    enqueuePendingOnFail = false,
                )
                if (ok) {
                    clearAnsweredPending(item, item.fuelEntryId)
                    PendingAnswerResult(
                        "Pump retry inserted (vehicleId=0 until merge)",
                        remerge = true,
                    )
                } else {
                    PendingAnswerResult("Pump retry still unreadable", success = false)
                }
            }
            is PendingAnswerAction.ResolveConflictOdo -> {
                resolveConflictOdo(item, action.chosenOdo)
            }
            is PendingAnswerAction.KeepBothNoMerge -> {
                durableAckLooksCorrect(
                    item = item,
                    kind = when (item.kind) {
                        BatchPendingKind.AMBIGUOUS_MULTI_PUMP ->
                            com.davidlang.vehicleexpensesautomated.data.model.MergeAck.KIND_AMBIGUOUS_MULTI_PUMP
                        BatchPendingKind.MPG_OUTLIER ->
                            com.davidlang.vehicleexpensesautomated.data.model.MergeAck.KIND_MPG_OUTLIER
                        else ->
                            com.davidlang.vehicleexpensesautomated.data.model.MergeAck.KIND_CONFLICT_ODO
                    },
                    alsoMergeExempt = item.kind == BatchPendingKind.CONFLICT_ODO ||
                        item.kind == BatchPendingKind.AMBIGUOUS_MULTI_PUMP,
                )
            }
            is PendingAnswerAction.ManualPumpEntry -> {
                manualPumpEntry(item, action.cost, action.volume)
            }
            is PendingAnswerAction.ManualDashEntry -> {
                manualDashEntry(item, action.odometer, action.vehicleId)
            }
            is PendingAnswerAction.ManualEditFuelFields -> {
                manualEditFuelFields(item, action)
            }
            is PendingAnswerAction.SetEconomyIgnored -> {
                setEconomyIgnored(item, action.ignored, action.entryId)
            }
            is PendingAnswerAction.AssignUnknownVehicle -> {
                assignUnknownVehicle(item, action.vehicleId)
            }
            is PendingAnswerAction.FlagPartial -> {
                // Legacy one-way button → set true (UI now uses SetPartialFill checkbox)
                setPartialFill(item, action.entryId, partial = true)
            }
            is PendingAnswerAction.SetPartialFill -> {
                setPartialFill(item, action.entryId, partial = action.partial)
            }
            is PendingAnswerAction.MarkAsGap -> {
                markAsGap(item, action.entryId)
            }
            is PendingAnswerAction.SaveOdoPeers -> {
                saveOdoPeers(item, action)
            }
        }
        StageCAnswerJournal.append(
            context = appContext,
            phase = phase,
            item = item,
            action = action,
            result = result,
            entryBefore = entryBefore,
        )
        result
    }

    /**
     * Update odometer on each peer that changed (odo > 0). Clears economyIgnored on
     * edited rows; removes pending for all touched ids; remerge.
     */
    private suspend fun saveOdoPeers(
        item: BatchPendingItem,
        action: PendingAnswerAction.SaveOdoPeers,
    ): PendingAnswerResult {
        val pairs = listOfNotNull(
            action.prevId?.let { id -> action.prevOdo?.takeIf { it > 0 }?.let { id to it } },
            action.curId?.let { id -> action.curOdo?.takeIf { it > 0 }?.let { id to it } },
            action.nextId?.let { id -> action.nextOdo?.takeIf { it > 0 }?.let { id to it } },
        )
        if (pairs.isEmpty()) {
            return PendingAnswerResult("No odometer changes to save", success = false)
        }
        val live = fuelEntryRepository.getAllIncludingDeleted()
            .filter { !it.deleted }
            .associateBy { it.id }
        val written = mutableListOf<String>()
        for ((id, newOdo) in pairs) {
            val e = live[id] ?: continue
            if (e.odometer == newOdo) continue
            fuelEntryRepository.updateFuelEntry(
                e.copy(odometer = newOdo, economyIgnored = false),
            )
            written += "id=$id odo $newOdo"
            removePendingForFuelEntry(id)
        }
        if (written.isEmpty()) {
            clearAnsweredPending(item, item.fuelEntryId)
            return PendingAnswerResult("No odometer differed from DB", remerge = false)
        }
        clearAnsweredPending(item, item.fuelEntryId)
        action.prevId?.let { removePendingForFuelEntry(it) }
        action.curId?.let { removePendingForFuelEntry(it) }
        action.nextId?.let { removePendingForFuelEntry(it) }
        val msg = "Saved odometers: ${written.joinToString("; ")}"
        Log.i(TAG, msg)
        return PendingAnswerResult(msg, remerge = true)
    }

    /**
     * Explicit partial override. Only legal when odo, cost, and volume are all present.
     * [partial]=false clears the flag. Incomplete rows always store false.
     */
    private suspend fun setPartialFill(
        item: BatchPendingItem,
        entryId: Long?,
        partial: Boolean,
    ): PendingAnswerResult {
        val id = entryId
            ?: item.fuelEntryId
            ?: item.extra["suspectId"]?.toLongOrNull()
            ?: item.extra["endEntryId"]?.toLongOrNull()
            ?: return PendingAnswerResult("No entry id for partial flag", success = false)
        val live = fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
            ?: return PendingAnswerResult("Fuel row $id not found", success = false)
        val complete = live.odometer > 0 && live.cost > 0 && live.gallons > 0
        if (partial && !complete) {
            return PendingAnswerResult(
                "Partial only when odo, cost, and volume are all present",
                success = false,
            )
        }
        val value = partial && complete
        fuelEntryRepository.updateFuelEntry(live.copy(isPartialFill = value))
        clearAnsweredPending(item, id)
        Log.i(TAG, "setPartialFill id=$id partial=$value")
        return PendingAnswerResult(
            if (value) "id=$id treat as partial (not full-fill anchor)"
            else "id=$id partial cleared",
            remerge = true,
        )
    }

    /**
     * One-shot repair: clear illegal or auto-set partial flags.
     * Incomplete rows → false; optionally clear all complete flags too ([allComplete]).
     */
    suspend fun clearAutoPartialFlags(allComplete: Boolean = true): Int =
        withContext(Dispatchers.IO) {
            val live = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
            var n = 0
            for (e in live) {
                val complete = e.odometer > 0 && e.cost > 0 && e.gallons > 0
                val shouldClear = e.isPartialFill && (!complete || allComplete)
                if (shouldClear) {
                    fuelEntryRepository.updateFuelEntry(e.copy(isPartialFill = false))
                    n++
                }
            }
            Log.i(TAG, "clearAutoPartialFlags cleared=$n allComplete=$allComplete")
            n
        }

    /**
     * Blank chain-breaker (missed fill / gap): odo=cost=vol=0, isPartialFill=false.
     *
     * **MPG_OUTLIER:** insert a new mid-leg blank between last & this anchors
     * (does **not** zero either full fill). If a breaker already exists in the
     * window, just dismiss the pending and re-detect.
     *
     * **Other kinds:** blank existing row by id, or insert blank at item timestamp.
     */
    private suspend fun markAsGap(
        item: BatchPendingItem,
        entryId: Long?,
    ): PendingAnswerResult {
        if (item.kind == BatchPendingKind.MPG_OUTLIER) {
            return markAsGapMpgOutlier(item)
        }

        val path = item.durablePhotoPath ?: item.photoPath
        val ts = item.timestampMs ?: System.currentTimeMillis()
        val id = entryId
            ?: item.fuelEntryId
            ?: item.extra["suspectId"]?.toLongOrNull()
            ?: item.extra["endEntryId"]?.toLongOrNull()

        if (id != null && id > 0) {
            val live = fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
            if (live != null) {
                fuelEntryRepository.updateFuelEntry(
                    live.copy(
                        odometer = 0,
                        cost = 0.0,
                        gallons = 0.0,
                        isPartialFill = false,
                        economyIgnored = false,
                        notes = live.notes?.takeIf { it.isNotBlank() }
                            ?: "batch_gap_marker",
                    ),
                )
                clearAnsweredPending(item, id)
                Log.i(TAG, "markAsGap updated id=$id to blank breaker")
                return PendingAnswerResult(
                    "Marked id=$id as gap (blank odo/cost/vol chain-breaker)",
                    remerge = true,
                )
            }
        }

        // No row yet (e.g. unreadable pump with no insert): insert blank gap marker
        val photoJson = path?.let { FuelPhotoJson.single("pump", it, ts) }
        val locJson = locationBlobFromPending(item)
        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = item.suggestedVehicleId?.takeIf { it > 0 } ?: UNASSIGNED_VEHICLE_ID,
                odometer = 0,
                gallons = 0.0,
                cost = 0.0,
                currency = "USD",
                timestamp = ts,
                photoUrl = photoJson,
                isPartialFill = false,
                location = locJson,
                notes = "batch_gap_marker",
            ),
        )
        maybeEnqueueLocationLookup(locJson)
        clearAnsweredPending(item, null)
        Log.i(TAG, "markAsGap inserted blank gap marker")
        return PendingAnswerResult("Inserted gap marker (blank chain-breaker)", remerge = true)
    }

    /**
     * Insert mid-leg blank between prev and end full fills. Anchors unchanged.
     */
    private suspend fun markAsGapMpgOutlier(item: BatchPendingItem): PendingAnswerResult {
        val prevId = item.extra["prevEntryId"]?.toLongOrNull()
            ?: item.extra["lastEntryId"]?.toLongOrNull()
        val endId = item.extra["endEntryId"]?.toLongOrNull()
            ?: item.extra["thisEntryId"]?.toLongOrNull()
            ?: item.fuelEntryId
        val prevTs = item.extra["prevTs"]?.toLongOrNull()
        val endTs = item.extra["endTs"]?.toLongOrNull()
            ?: item.timestampMs
        val liveAll = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
        val prev = prevId?.let { id -> liveAll.find { it.id == id } }
        val end = endId?.let { id -> liveAll.find { it.id == id } }
        val vehicleId = item.suggestedVehicleId?.takeIf { it > 0 }
            ?: end?.vehicleId?.takeIf { it > 0 }
            ?: prev?.vehicleId?.takeIf { it > 0 }
            ?: 0
        val pTs = prevTs ?: prev?.timestamp
        val eTs = endTs ?: end?.timestamp
        if (vehicleId <= 0 || pTs == null || eTs == null) {
            // Fall back to blanking focused/end row only if we lack leg metadata
            val id = endId ?: item.fuelEntryId
            if (id != null && id > 0) {
                val live = liveAll.find { it.id == id }
                if (live != null) {
                    fuelEntryRepository.updateFuelEntry(
                        live.copy(
                            odometer = 0,
                            cost = 0.0,
                            gallons = 0.0,
                            isPartialFill = false,
                            economyIgnored = false,
                            notes = live.notes?.takeIf { it.isNotBlank() }
                                ?: "batch_gap_marker",
                        ),
                    )
                    clearAnsweredPending(item, id)
                    return PendingAnswerResult(
                        "Marked id=$id as gap (missing leg bounds)",
                        remerge = true,
                    )
                }
            }
            return PendingAnswerResult("Cannot place mid-leg gap (no vehicle/timestamps)", success = false)
        }

        val window = FuelEconomyChains.windowContributors(liveAll, pTs, eTs)
        if (FuelEconomyChains.windowHasMpgBreaker(window)) {
            // Already broken — dismiss question; re-detect will not re-enqueue
            clearAnsweredPending(item, endId)
            prevId?.let { removePendingForFuelEntry(it) }
            endId?.let { removePendingForFuelEntry(it) }
            Log.i(TAG, "markAsGap MPG: breaker already in window; dismissed pending")
            return PendingAnswerResult(
                "Gap already in leg window — question dismissed",
                remerge = true,
            )
        }

        val gapTs = pTs + maxOf(1L, (eTs - pTs) / 2L)
        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = vehicleId,
                odometer = 0,
                gallons = 0.0,
                cost = 0.0,
                currency = end?.currency?.ifBlank { "USD" } ?: "USD",
                timestamp = gapTs,
                photoUrl = null,
                isPartialFill = false,
                economyIgnored = false,
                notes = "batch_gap_marker",
            ),
        )
        clearAnsweredPending(item, endId)
        prevId?.let { removePendingForFuelEntry(it) }
        endId?.let { removePendingForFuelEntry(it) }
        Log.i(
            TAG,
            "markAsGap MPG: inserted mid-leg blank vehicle=$vehicleId ts=$gapTs " +
                "between prev=$prevId end=$endId (anchors preserved)",
        )
        return PendingAnswerResult(
            "Inserted gap between last & this (anchors kept)",
            remerge = true,
        )
    }

    private fun clearAnsweredPending(item: BatchPendingItem, fuelEntryId: Long?) {
        removePendingForFuelEntry(fuelEntryId)
        removePendingForFuelEntry(item.fuelEntryId)
        removePendingForFuelEntry(item.extra["suspectId"]?.toLongOrNull())
        removePendingForFuelEntry(item.extra["endEntryId"]?.toLongOrNull())
        BatchImportPendingStore.remove(appContext, item.id)
    }

    private suspend fun manualPumpEntry(
        item: BatchPendingItem,
        cost: Double,
        volume: Double,
    ): PendingAnswerResult {
        if (cost <= 0 && volume <= 0) {
            return PendingAnswerResult(
                "Enter cost and/or volume > 0 (or use Mark as gap)",
                success = false,
            )
        }
        val path = item.durablePhotoPath ?: item.photoPath
        val ts = item.timestampMs ?: System.currentTimeMillis()
        val existingId = item.fuelEntryId
        if (existingId != null && existingId > 0) {
            val live = fuelEntryRepository.getAllIncludingDeleted().find { it.id == existingId }
            if (live != null && !live.deleted) {
                val next = live.copy(
                    cost = if (cost > 0) cost else live.cost,
                    gallons = if (volume > 0) volume else live.gallons,
                    economyIgnored = false,
                )
                // Preserve explicit partial only if still complete; else false
                val complete = next.odometer > 0 && next.cost > 0 && next.gallons > 0
                fuelEntryRepository.updateFuelEntry(
                    next.copy(isPartialFill = complete && live.isPartialFill),
                )
                clearAnsweredPending(item, existingId)
                return PendingAnswerResult("Updated pump fields on id=$existingId", remerge = true)
            }
        }
        val photoJson = path?.let { FuelPhotoJson.single("pump", it, ts) }
        val locJson = locationBlobFromPending(item)
        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = UNASSIGNED_VEHICLE_ID,
                odometer = 0,
                gallons = volume.coerceAtLeast(0.0),
                cost = cost.coerceAtLeast(0.0),
                currency = "USD",
                timestamp = ts,
                photoUrl = photoJson,
                isPartialFill = false,
                location = locJson,
                notes = "batch_manual_pump",
            ),
        )
        maybeEnqueueLocationLookup(locJson)
        clearAnsweredPending(item, null)
        return PendingAnswerResult("Manual pump entry saved", remerge = true)
    }

    private suspend fun manualDashEntry(
        item: BatchPendingItem,
        odometer: Int,
        vehicleId: Int?,
    ): PendingAnswerResult {
        if (odometer <= 0) {
            return PendingAnswerResult("Odometer must be > 0", success = false)
        }
        val path = item.durablePhotoPath ?: item.photoPath
        val ts = item.timestampMs ?: System.currentTimeMillis()
        val vid = vehicleId
            ?: item.suggestedVehicleId
            ?: return PendingAnswerResult("Pick a vehicle for dash entry", success = false)
        if (vid <= 0) {
            return PendingAnswerResult("Pick a vehicle for dash entry", success = false)
        }
        val existingId = item.fuelEntryId
        if (existingId != null && existingId > 0) {
            val live = fuelEntryRepository.getAllIncludingDeleted().find { it.id == existingId }
            if (live != null && !live.deleted) {
                val next = live.copy(
                    odometer = odometer,
                    vehicleId = vid,
                    economyIgnored = false,
                )
                val complete = next.odometer > 0 && next.cost > 0 && next.gallons > 0
                fuelEntryRepository.updateFuelEntry(
                    next.copy(isPartialFill = complete && live.isPartialFill),
                )
                clearAnsweredPending(item, existingId)
                return PendingAnswerResult("Updated dash odo=$odometer vehicle=$vid", remerge = true)
            }
        }
        val photoJson = path?.let { FuelPhotoJson.single("dash", it, ts) }
        val locJson = locationBlobFromPending(item)
        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = vid,
                odometer = odometer,
                gallons = 0.0,
                cost = 0.0,
                currency = "USD",
                timestamp = ts,
                photoUrl = photoJson,
                isPartialFill = false,
                location = locJson,
                notes = "batch_manual_dash",
            ),
        )
        maybeEnqueueLocationLookup(locJson)
        clearAnsweredPending(item, null)
        return PendingAnswerResult("Manual dash odo=$odometer vehicle=$vid", remerge = true)
    }

    private suspend fun manualEditFuelFields(
        item: BatchPendingItem,
        action: PendingAnswerAction.ManualEditFuelFields,
    ): PendingAnswerResult {
        val id = action.entryId
            ?: item.fuelEntryId
            ?: item.extra["suspectId"]?.toLongOrNull()
            ?: item.extra["endEntryId"]?.toLongOrNull()
            ?: return PendingAnswerResult("No fuelEntryId", success = false)
        val live = fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
            ?: return PendingAnswerResult("Fuel row $id not found", success = false)
        val updated = live.copy(
            odometer = action.odometer?.takeIf { it > 0 } ?: live.odometer,
            cost = action.cost?.takeIf { it > 0 } ?: live.cost,
            gallons = action.volume?.takeIf { it > 0 } ?: live.gallons,
            economyIgnored = false,
        ).let { e ->
            val complete = e.odometer > 0 && e.cost > 0 && e.gallons > 0
            // Preserve explicit partial only if still complete; never invent true
            e.copy(isPartialFill = complete && live.isPartialFill)
        }
        fuelEntryRepository.updateFuelEntry(updated)
        clearAnsweredPending(item, id)
        return PendingAnswerResult("Edited fuel id=$id (ignore cleared)", remerge = true)
    }

    private suspend fun setEconomyIgnored(
        item: BatchPendingItem,
        ignored: Boolean,
        entryId: Long? = null,
    ): PendingAnswerResult {
        val id = entryId
            ?: item.fuelEntryId
            ?: item.extra["endEntryId"]?.toLongOrNull()
            ?: return PendingAnswerResult("No fuelEntryId", success = false)
        val live = fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
            ?: return PendingAnswerResult("Fuel row $id not found", success = false)
        fuelEntryRepository.updateFuelEntry(live.copy(economyIgnored = ignored))
        clearAnsweredPending(item, id)
        return PendingAnswerResult(
            if (ignored) "Marked economyIgnored on id=$id" else "Unignored id=$id",
            remerge = true,
        )
    }

    private suspend fun assignUnknownVehicle(
        item: BatchPendingItem,
        vehicleId: Int,
    ): PendingAnswerResult {
        if (vehicleId <= 0) {
            return PendingAnswerResult("Invalid vehicle", success = false)
        }
        val id = item.fuelEntryId
            ?: return PendingAnswerResult("No fuelEntryId", success = false)
        val live = fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
            ?: return PendingAnswerResult("Fuel row $id not found", success = false)
        fuelEntryRepository.updateFuelEntry(live.copy(vehicleId = vehicleId))
        clearAnsweredPending(item, id)
        return PendingAnswerResult("Assigned unknown → vehicle $vehicleId", remerge = true)
    }

    /**
     * Neighbor fills for same-vehicle chronological context (outliers, odo suspect).
     */
    suspend fun neighborContext(
        fuelEntryId: Long?,
        timestampMs: Long?,
        vehicleIdHint: Int?,
        expandExtra: Int = 0,
        allVehicles: Boolean = false,
    ): List<FuelEntry> = withContext(Dispatchers.IO) {
        val live = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
        val around = fuelEntryId?.let { id -> live.find { it.id == id } }
        if (around != null && !allVehicles && around.vehicleId > 0) {
            val pool = live.filter { it.vehicleId == around.vehicleId }
            val sorted = pool.sortedWith(compareBy({ it.timestamp }, { it.id }))
            val idx = sorted.indexOfFirst { it.id == around.id }
            if (idx >= 0) {
                val beforeN = 1 + expandExtra * 3
                val afterN = 1 + expandExtra * 3
                val from = (idx - beforeN).coerceAtLeast(0)
                val to = (idx + afterN).coerceAtMost(sorted.lastIndex)
                return@withContext sorted.subList(from, to + 1)
            }
        }
        val ts = around?.timestamp ?: timestampMs ?: return@withContext emptyList()
        val window = FuelRowMergeEngine.UNKNOWN_CONTEXT_WINDOW_MS * (1L + expandExtra)
        val pool = when {
            allVehicles || (vehicleIdHint ?: 0) == 0 || around?.vehicleId == 0 -> live
            vehicleIdHint != null && vehicleIdHint > 0 -> live.filter { it.vehicleId == vehicleIdHint }
            else -> live
        }
        pool.filter { kotlin.math.abs(it.timestamp - ts) <= window }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
    }

    /**
     * Unknown-vehicle context: for **each** active vehicle, nearest fill strictly
     * before and after [timestampMs] (or the unknown row's timestamp).
     * [expandExtra] adds 2nd/3rd nearest per side.
     */
    data class PerVehicleNeighbor(
        val vehicleId: Int,
        val vehicleName: String,
        val before: List<FuelEntry>,
        val after: List<FuelEntry>,
    )

    suspend fun nearestNeighborsPerVehicle(
        timestampMs: Long,
        vehicles: List<Vehicle>,
        expandExtra: Int = 0,
        excludeEntryId: Long? = null,
    ): List<PerVehicleNeighbor> = withContext(Dispatchers.IO) {
        val live = fuelEntryRepository.getAllIncludingDeleted().filter {
            !it.deleted && it.vehicleId > 0 && it.id != excludeEntryId
        }
        val perSide = 1 + expandExtra
        vehicles.filter { !it.deleted }.map { v ->
            val rows = live.filter { it.vehicleId == v.id }
                .sortedWith(compareBy({ it.timestamp }, { it.id }))
            val before = rows.filter { it.timestamp < timestampMs }
                .takeLast(perSide)
                .asReversed() // nearest first
            val after = rows.filter { it.timestamp > timestampMs }
                .take(perSide)
            PerVehicleNeighbor(
                vehicleId = v.id,
                vehicleName = v.name.ifBlank { "Vehicle ${v.id}" },
                before = before,
                after = after,
            )
        }
    }

    /** Load fuel row for pre-filling edit fields. */
    suspend fun getFuelEntry(id: Long): FuelEntry? = withContext(Dispatchers.IO) {
        fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
    }

    /**
     * Resolve member fuel syncIds from pending extra (entrySyncIds / entryIds → Room)
     * and write durable ack; dismiss pending. MERGE_EXEMPT when [alsoMergeExempt].
     */
    private suspend fun durableAckLooksCorrect(
        item: BatchPendingItem,
        kind: String,
        alsoMergeExempt: Boolean,
    ): PendingAnswerResult {
        val syncIds = resolveMemberSyncIds(item)
        if (syncIds.isEmpty()) {
            BatchImportPendingStore.remove(appContext, item.id)
            return PendingAnswerResult(
                "Acknowledged (no syncIds resolved; pending removed only)",
                remerge = false,
            )
        }
        mergeAckStore.acknowledge(
            kind = kind,
            memberFuelSyncIds = syncIds,
            alsoMergeExempt = alsoMergeExempt,
        )
        BatchImportPendingStore.remove(appContext, item.id)
        Log.i(TAG, "durable ack kind=$kind members=${syncIds.sorted()} exempt=$alsoMergeExempt")
        return PendingAnswerResult(
            "Looks correct — won't ask again ($kind)",
            remerge = true,
        )
    }

    /** Member fuel syncIds from extra keys or entryIds → live fuel rows. */
    private suspend fun resolveMemberSyncIds(item: BatchPendingItem): Set<String> {
        val fromExtra = mergeAckStore.pendingMemberSyncIds(item)
        if (fromExtra.isNotEmpty()) return fromExtra
        val entryIds = buildList {
            item.fuelEntryId?.let { add(it) }
            item.extra["entryIds"]
                ?.split(',')
                ?.mapNotNull { it.trim().toLongOrNull() }
                ?.let { addAll(it) }
            item.extra["prevEntryId"]?.toLongOrNull()?.let { add(it) }
            item.extra["endEntryId"]?.toLongOrNull()?.let { add(it) }
            item.extra["lastEntryId"]?.toLongOrNull()?.let { add(it) }
            item.extra["thisEntryId"]?.toLongOrNull()?.let { add(it) }
            item.extra["suspectId"]?.toLongOrNull()?.let { add(it) }
        }.distinct()
        if (entryIds.isEmpty()) return emptySet()
        val live = fuelEntryRepository.getAllIncludingDeleted()
            .filter { !it.deleted }
            .associateBy { it.id }
        return entryIds.mapNotNull { live[it]?.syncId?.takeIf { s -> s.isNotBlank() } }.toSet()
    }

    /**
     * MPG_OUTLIER context lines: one fill immediately before [lastTs], one after [thisTs],
     * same vehicle only. Does not walk generic ±N that can skip the true leg start.
     */
    suspend fun nearestFillBefore(
        vehicleId: Int,
        beforeTimestampMs: Long,
        excludeIds: Set<Long> = emptySet(),
    ): FuelEntry? = withContext(Dispatchers.IO) {
        fuelEntryRepository.getAllIncludingDeleted()
            .filter {
                !it.deleted && it.vehicleId == vehicleId &&
                    it.id !in excludeIds && it.timestamp < beforeTimestampMs
            }
            .maxWithOrNull(compareBy({ it.timestamp }, { it.id }))
    }

    suspend fun nearestFillAfter(
        vehicleId: Int,
        afterTimestampMs: Long,
        excludeIds: Set<Long> = emptySet(),
    ): FuelEntry? = withContext(Dispatchers.IO) {
        fuelEntryRepository.getAllIncludingDeleted()
            .filter {
                !it.deleted && it.vehicleId == vehicleId &&
                    it.id !in excludeIds && it.timestamp > afterTimestampMs
            }
            .minWithOrNull(compareBy({ it.timestamp }, { it.id }))
    }

    /**
     * Keep [chosenOdo] as the only positive odometer among cluster entryIds.
     * Pure odo-only rows with a different odo are hard-deleted; rows that still
     * have cost/vol keep those fields with odo cleared for re-merge pairing.
     */
    private suspend fun resolveConflictOdo(
        item: BatchPendingItem,
        chosenOdo: Int,
    ): PendingAnswerResult {
        if (item.kind != BatchPendingKind.CONFLICT_ODO) {
            return PendingAnswerResult("Not a CONFLICT_ODO item", success = false)
        }
        if (chosenOdo <= 0) {
            return PendingAnswerResult("Invalid odometer $chosenOdo", success = false)
        }
        val entryIds = item.extra["entryIds"]
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it > 0 }
            .orEmpty()
        if (entryIds.isEmpty()) {
            clearAnsweredPending(item, null)
            return PendingAnswerResult("No entryIds on conflict; pending removed", remerge = false)
        }
        val live = fuelEntryRepository.getAllIncludingDeleted()
            .filter { !it.deleted }
            .associateBy { it.id }
            .toMutableMap()
        var deleted = 0
        var updated = 0
        var kept = 0
        // Free-typed odo: if no row already has it, write onto first cluster entry
        if (entryIds.none { live[it]?.odometer == chosenOdo }) {
            val first = entryIds.firstOrNull { live[it] != null }?.let { live[it] }
            if (first != null) {
                val rewritten = first.copy(odometer = chosenOdo, economyIgnored = false)
                fuelEntryRepository.updateFuelEntry(rewritten)
                live[first.id] = rewritten
                updated++
                kept++
            }
        }
        for (id in entryIds) {
            val e = live[id] ?: continue
            when {
                e.odometer == chosenOdo -> {
                    kept++
                }
                e.odometer > 0 && e.odometer != chosenOdo -> {
                    val hasPump = e.cost > 0 || e.gallons > 0
                    if (hasPump) {
                        fuelEntryRepository.updateFuelEntry(
                            e.copy(odometer = 0, isPartialFill = false),
                        )
                        updated++
                    } else {
                        if (absorbMergedRow(e)) {
                            // soft
                        }
                        deleted++
                        Log.i(TAG, "conflict resolve absorb id=${e.id} odo=${e.odometer}")
                    }
                }
                else -> {
                    // no odo or already zero — leave for re-merge
                }
            }
        }
        entryIds.forEach { removePendingForFuelEntry(it) }
        clearAnsweredPending(item, entryIds.firstOrNull())
        val msg = "Kept odo=$chosenOdo (keptRows≈$kept updated=$updated deleted=$deleted)"
        Log.i(TAG, "resolveConflictOdo $msg")
        return PendingAnswerResult(msg, remerge = true)
    }

    /** Paths for UI: item fields + photoUrl from related fuel rows when ids present. */
    suspend fun resolvePendingPhotoUris(item: BatchPendingItem): List<String> =
        withContext(Dispatchers.IO) {
            val entryIds = buildList {
                item.fuelEntryId?.let { add(it) }
                item.extra["entryIds"]
                    ?.split(',')
                    ?.mapNotNull { it.trim().toLongOrNull() }
                    ?.let { addAll(it) }
            }.distinct()
            val urls = if (entryIds.isEmpty()) {
                emptyList()
            } else {
                val byId = fuelEntryRepository.getAllIncludingDeleted()
                    .filter { it.id in entryIds }
                    .associateBy { it.id }
                entryIds.mapNotNull { byId[it]?.photoUrl }
            }
            pendingPhotoUris(item, urls)
        }

    /**
     * Lat/lon/accuracy from pending item, re-reading EXIF from photo path when any field is missing.
     * Prefer already-set item fields over re-read.
     */
    private fun resolvePendingGeo(item: BatchPendingItem): Triple<Double?, Double?, Double?> {
        var lat = item.latitude
        var lon = item.longitude
        var acc = item.accuracyM
        if (lat != null && lon != null && acc != null) {
            return Triple(lat, lon, acc)
        }
        val path = item.durablePhotoPath ?: item.photoPath
        if (!path.isNullOrBlank()) {
            val f = File(path)
            if (f.isFile) {
                try {
                    val meta = PhotoExifMetaReader.read(f.absolutePath)
                    if (lat == null) lat = meta.latitude
                    if (lon == null) lon = meta.longitude
                    if (acc == null) acc = meta.accuracyM
                } catch (e: Exception) {
                    Log.w(TAG, "EXIF re-read for pending failed: ${e.message}")
                }
            }
        }
        return Triple(lat, lon, acc)
    }

    private fun locationBlobFromPending(item: BatchPendingItem): String? {
        val (lat, lon, acc) = resolvePendingGeo(item)
        return FuelLocationJson.encode(
            FuelLocationJson.fromCoords(lat, lon, acc, source = "exif"),
        )
    }

    /** One-shot deferred POI when insert left coords without place (non-blocking). */
    private fun maybeEnqueueLocationLookup(locationJson: String?) {
        if (FuelLocationJson.hasCoordsWithoutPlace(locationJson)) {
            LocationLookupScheduler.enqueueSoon(appContext)
        }
    }

    /**
     * Dash: alignment **experiment Set J** pipeline via [AlignmentSetJRunner]
     * (not [com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness.runAutoFillPipeline]).
     *
     * Photo path = **source file in place** (no copy into batch_import_photos).
     */
    private suspend fun processDash(
        file: File,
        vehicles: List<Vehicle>,
        pending: MutableList<BatchPendingItem>,
        forcedVehicleId: Int? = null,
        enqueuePendingOnFail: Boolean = true,
    ): Boolean {
        val meta = PhotoExifMetaReader.read(file.absolutePath)
        val ts = meta.timestampMs ?: System.currentTimeMillis()
        // Reference source path only — never copyToDurable
        val sourcePath = file.absolutePath

        val activeVehicles = vehicles.filter { !it.deleted }
        val result = AlignmentSetJRunner.runOnePhoto(
            context = appContext,
            photoFile = file,
            vehicles = activeVehicles,
            forcedVehicleId = forcedVehicleId,
        )

        if (result.vehicleId == null) {
            if (enqueuePendingOnFail) {
                pending.add(
                    BatchPendingItem(
                        kind = BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE,
                        message = "Could not identify vehicle for ${file.name}" +
                            (result.error?.let { ": $it" } ?: ""),
                        photoPath = sourcePath,
                        durablePhotoPath = sourcePath, // same as source (no mirror)
                        timestampMs = ts,
                        latitude = meta.latitude,
                        longitude = meta.longitude,
                        accuracyM = meta.accuracyM,
                    ),
                )
            }
            return false
        }

        val odo = parseSetJOdometer(result.odometer)
        val photoJson = FuelPhotoJson.single("dash", sourcePath, ts)

        if (odo == null) {
            val locJson = FuelLocationJson.encode(
                FuelLocationJson.fromCoords(meta.latitude, meta.longitude, meta.accuracyM, source = "exif"),
            )
            fuelEntryRepository.insertFuelEntry(
                FuelEntry(
                    vehicleId = result.vehicleId,
                    odometer = 0,
                    gallons = 0.0,
                    cost = 0.0,
                    currency = "USD",
                    timestamp = ts,
                    photoUrl = photoJson,
                    isPartialFill = false,
                    location = locJson,
                    notes = "batch_import_dash_blank:${file.name}",
                ),
            )
            maybeEnqueueLocationLookup(locJson)
            Log.i(TAG, "Inserted blank dash marker vehicle=${result.vehicleId} ${file.name}")
            return true
        }

        val locJson = FuelLocationJson.encode(
            FuelLocationJson.fromCoords(meta.latitude, meta.longitude, meta.accuracyM, source = "exif"),
        )
        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = result.vehicleId,
                odometer = odo,
                gallons = 0.0,
                cost = 0.0,
                currency = "USD",
                timestamp = ts,
                photoUrl = photoJson,
                isPartialFill = false, // incomplete by fields only
                location = locJson,
                notes = "batch_import_dash:${file.name}",
            ),
        )
        maybeEnqueueLocationLookup(locJson)
        Log.i(TAG, "Inserted odo-only vehicle=${result.vehicleId} odo=$odo ${file.name}")
        return true
    }

    /**
     * Pump photos: always Set I cost/vol OCR and insert as partial.
     * Default vehicleId is [UNASSIGNED_VEHICLE_ID] (0) until merge; optional
     * [forcedVehicleId] for legacy ASSIGN_VEHICLE pending answers.
     *
     * Photo path = **source file in place** (no copy into batch_import_photos).
     */
    private suspend fun processPump(
        file: File,
        pending: MutableList<BatchPendingItem>,
        forcedVehicleId: Int? = null,
        enqueuePendingOnFail: Boolean = true,
    ): Boolean {
        val meta = PhotoExifMetaReader.read(file.absolutePath)
        val ts = meta.timestampMs ?: System.currentTimeMillis()
        val sourcePath = file.absolutePath
        val vehicleId = forcedVehicleId ?: UNASSIGNED_VEHICLE_ID

        // Experiment Set I path (not OcrHarness.runPumpCostVolPipelineSetI / Quick Fill G--)
        val result = PumpSetIRunner.runOnePhoto(appContext, file)
        val cost = parseMoneyOrVol(result.cost)
        val vol = parseMoneyOrVol(result.volume)

        if (cost == null && vol == null) {
            if (enqueuePendingOnFail) {
                pending.add(
                    BatchPendingItem(
                        kind = BatchPendingKind.UNREADABLE_PUMP,
                        message = "Unreadable pump ${file.name}" +
                            (result.error?.let { ": $it" } ?: ""),
                        photoPath = sourcePath,
                        durablePhotoPath = sourcePath,
                        timestampMs = ts,
                        latitude = meta.latitude,
                        longitude = meta.longitude,
                        accuracyM = meta.accuracyM,
                    ),
                )
            }
            return false
        }

        val photoJson = FuelPhotoJson.single("pump", sourcePath, ts)
        val locJson = FuelLocationJson.encode(
            FuelLocationJson.fromCoords(meta.latitude, meta.longitude, meta.accuracyM, source = "exif"),
        )
        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = vehicleId,
                odometer = 0,
                gallons = vol ?: 0.0,
                cost = cost ?: 0.0,
                currency = "USD",
                timestamp = ts,
                photoUrl = photoJson,
                isPartialFill = false, // incomplete by fields only
                location = locJson,
                notes = "batch_import_pump:${file.name}",
            ),
        )
        maybeEnqueueLocationLookup(locJson)
        Log.i(
            TAG,
            "Inserted pump cost/vol vehicleId=$vehicleId " +
                "cost=$cost vol=$vol ${file.name}",
        )
        return true
    }

    data class DurableMigrateResult(
        val rewroteRows: Int,
        val rewrotePending: Int,
        val deletedFiles: Int,
    ) {
        override fun toString(): String =
            "rewroteRows=$rewroteRows rewrotePending=$rewrotePending deletedFiles=$deletedFiles"
    }

    /**
     * Optional cleanup: rewrite `batch_import_photos/(dash|pump)_ts_NAME` URIs to
     * `experiment_photos/NAME` or `pump_photos/NAME` when the source file exists,
     * then delete unreferenced files under the legacy durable dir.
     * Does **not** bump [FuelEntry.updatedAt] (photo bookkeeping only).
     */
    suspend fun migrateDurablePhotoRefsToSource(): DurableMigrateResult =
        withContext(Dispatchers.IO) {
            val dashDir = dashPhotoDir(appContext)
            val pumpDir = pumpPhotoDir(appContext)
            val durableDir = durablePhotoDir(appContext)

            fun resolveSource(path: String): String? {
                if (!path.contains("batch_import_photos")) return null
                val base = path.trim().substringAfterLast('/').substringAfterLast('\\')
                val m = DURABLE_BASENAME.matchEntire(base) ?: return null
                val kind = m.groupValues[1].lowercase()
                val originalName = m.groupValues[3]
                val dir = when (kind) {
                    "dash" -> dashDir
                    "pump" -> pumpDir
                    else -> return null
                }
                val target = File(dir, originalName)
                return if (target.isFile) target.absolutePath else null
            }

            fun rewriteUri(uri: String): String {
                return resolveSource(uri) ?: uri
            }

            var rewroteRows = 0
            val all = fuelEntryRepository.getAllIncludingDeleted()
            for (e in all) {
                val url = e.photoUrl ?: continue
                if (!url.contains("batch_import_photos")) continue
                val refs = FuelPhotoJson.parse(url)
                var changed = false
                val next = refs.map { ref ->
                    val r = rewriteUri(ref.uri)
                    if (r != ref.uri) {
                        changed = true
                        ref.copy(uri = r)
                    } else ref
                }
                if (!changed) continue
                val serialized = FuelPhotoJson.serialize(next)
                fuelEntryRepository.updateFuelEntryPreservingTimestamp(
                    e.copy(photoUrl = serialized),
                )
                rewroteRows++
            }

            var rewrotePending = 0
            val pending = BatchImportPendingStore.load(appContext)
            if (pending.isNotEmpty()) {
                val updated = pending.map { item ->
                    var ch = false
                    fun fix(p: String?): String? {
                        if (p.isNullOrBlank()) return p
                        val r = rewriteUri(p)
                        if (r != p) ch = true
                        return r
                    }
                    val photo = fix(item.photoPath)
                    val durable = fix(item.durablePhotoPath)
                    val extra = item.extra.toMutableMap()
                    for (key in listOf(
                        "photoPaths", "thisPhotoPaths", "lastPhotoPaths",
                        "prevPhotoPaths", "prevDashPaths", "curDashPaths", "nextDashPaths",
                    )) {
                        val v = extra[key] ?: continue
                        if (!v.contains("batch_import_photos")) continue
                        val rewritten = v.split('|').joinToString("|") { part ->
                            val t = part.trim()
                            if (t.isEmpty()) t else rewriteUri(t)
                        }
                        if (rewritten != v) {
                            extra[key] = rewritten
                            ch = true
                        }
                    }
                    if (!ch) item
                    else {
                        rewrotePending++
                        item.copy(
                            photoPath = photo,
                            durablePhotoPath = durable,
                            extra = extra,
                        )
                    }
                }
                if (rewrotePending > 0) {
                    BatchImportPendingStore.save(appContext, updated)
                }
            }

            // Collect still-referenced durable basenames (after rewrite)
            val stillReferenced = mutableSetOf<String>()
            fun noteRef(path: String?) {
                if (path.isNullOrBlank()) return
                if (!path.contains("batch_import_photos")) return
                stillReferenced.add(path.substringAfterLast('/').substringAfterLast('\\'))
            }
            for (e in fuelEntryRepository.getAllIncludingDeleted()) {
                for (ref in FuelPhotoJson.parse(e.photoUrl)) noteRef(ref.uri)
            }
            for (item in BatchImportPendingStore.load(appContext)) {
                noteRef(item.photoPath)
                noteRef(item.durablePhotoPath)
                item.extra.values.forEach { v ->
                    v.split('|').forEach { noteRef(it.trim()) }
                }
            }

            var deletedFiles = 0
            if (durableDir.isDirectory) {
                durableDir.listFiles()?.forEach { f ->
                    if (!f.isFile) return@forEach
                    if (f.name in stillReferenced) return@forEach
                    if (f.delete()) deletedFiles++
                }
            }

            DurableMigrateResult(rewroteRows, rewrotePending, deletedFiles)
        }
}

/** User answer on a pending batch question. */
sealed class PendingAnswerAction {
    data object Skip : PendingAnswerAction()
    data class AssignVehicle(val vehicleId: Int) : PendingAnswerAction()
    data object RetryPump : PendingAnswerAction()

    /**
     * [CONFLICT_ODO]: keep [chosenOdo] as authoritative for the cluster in
     * [BatchPendingItem.extra] `entryIds`. Other rows with a different positive
     * odo: pure odo-only → [FuelEntryRepository.hardDeleteFuelEntry]; rows with
     * cost/vol keep data with odo zeroed so re-merge can pair. Then remove pending.
     */
    data class ResolveConflictOdo(val chosenOdo: Int) : PendingAnswerAction()

    /**
     * Durable keep-both / looks-correct for CONFLICT / AMBIGUOUS (acks + MERGE_EXEMPT).
     * Toast should say it will not re-ask while the ack holds.
     */
    data object KeepBothNoMerge : PendingAnswerAction()

    /**
     * Durable "Looks correct — don't ask again" for Stage C cards.
     * [kind] defaults from item; MPG_OUTLIER does not set MERGE_EXEMPT.
     */
    data class AcknowledgeLooksCorrect(val kind: String? = null) : PendingAnswerAction()

    /** Manual cost/volume for unreadable pump (insert or update). */
    data class ManualPumpEntry(val cost: Double, val volume: Double) : PendingAnswerAction()

    /** Manual odometer (+ vehicle) for unreadable dash. */
    data class ManualDashEntry(val odometer: Int, val vehicleId: Int?) : PendingAnswerAction()

    /** Edit odo/cost/vol on an existing fuel row (clears economyIgnored). */
    data class ManualEditFuelFields(
        val odometer: Int? = null,
        val cost: Double? = null,
        val volume: Double? = null,
        /** Explicit target row (e.g. MPG focus = leg end or leg start). */
        val entryId: Long? = null,
    ) : PendingAnswerAction()

    data class SetEconomyIgnored(
        val ignored: Boolean,
        val entryId: Long? = null,
    ) : PendingAnswerAction()

    data class AssignUnknownVehicle(val vehicleId: Int) : PendingAnswerAction()

    /**
     * Legacy one-way flag → [SetPartialFill] with partial=true.
     * Prefer [SetPartialFill] checkbox for check/uncheck.
     */
    data class FlagPartial(val entryId: Long? = null) : PendingAnswerAction()

    /**
     * Explicit partial override when odo+cost+vol all present.
     * [partial]=false clears the flag.
     */
    data class SetPartialFill(
        val partial: Boolean,
        val entryId: Long? = null,
    ) : PendingAnswerAction()

    /**
     * Blank chain-breaker: odo=cost=vol=0, isPartialFill=false.
     * Explicit zeros allowed (unlike manual pump entry).
     */
    data class MarkAsGap(val entryId: Long? = null) : PendingAnswerAction()

    /**
     * Multi-peer odo save for [BatchPendingKind.ODO_SUSPECT] (prev / cur / next).
     * Only values &gt; 0 that differ from DB are written.
     */
    data class SaveOdoPeers(
        val prevId: Long? = null,
        val prevOdo: Int? = null,
        val curId: Long? = null,
        val curOdo: Int? = null,
        val nextId: Long? = null,
        val nextOdo: Int? = null,
    ) : PendingAnswerAction()
}

/** Result of [BatchFuelImportCoordinator.applyPendingAnswer]. */
data class PendingAnswerResult(
    val message: String,
    /** True when fuel rows changed and Stage B re-merge should run. */
    val remerge: Boolean = false,
    val success: Boolean = true,
)
