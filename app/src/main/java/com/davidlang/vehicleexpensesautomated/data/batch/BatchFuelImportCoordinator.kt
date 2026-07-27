package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
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
 */
@Singleton
class BatchFuelImportCoordinator @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val fuelEntryRepository: FuelEntryRepository,
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

        fun durablePhotoDir(context: Context): File =
            File(context.filesDir, "batch_import_photos").also { it.mkdirs() }
    }

    private val cancelFlag = AtomicBoolean(false)

    fun requestCancel() {
        cancelFlag.set(true)
    }

    fun clearCancel() {
        cancelFlag.set(false)
    }

    /**
     * Copy source into app-private durable dir; returns durable absolute path.
     */
    fun copyToDurable(source: File, prefix: String): File {
        val dir = durablePhotoDir(appContext)
        val safeName = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dest = File(dir, "${prefix}_${System.currentTimeMillis()}_$safeName")
        source.copyTo(dest, overwrite = true)
        return dest
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
     * Stage B: load non-deleted fuel entries → [FuelRowMergeEngine.planMerge] →
     * update survivors → [FuelEntryRepository.hardDeleteFuelEntry] absorbs →
     * append merge pending (deduped by kind+message/photo).
     */
    suspend fun applyMerge(
        onProgress: (String) -> Unit = {},
    ): MergeApplyResult = withContext(Dispatchers.IO) {
        onProgress("Loading fuel entries…")
        val live = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
        onProgress("Planning merge (${live.size} rows)…")
        val plan = FuelRowMergeEngine.planMerge(live)

        if (!plan.isEmpty()) {
            onProgress("Applying ${plan.updates.size} updates…")
            for (u in plan.updates) {
                fuelEntryRepository.updateFuelEntry(u)
            }
            onProgress("Hard-deleting ${plan.hardDeletes.size} absorbed rows…")
            for (d in plan.hardDeletes) {
                fuelEntryRepository.hardDeleteFuelEntry(d)
                Log.i(TAG, "merge hardDelete id=${d.id} vehicle=${d.vehicleId} loc=${d.location}")
            }
        }

        // Full pending rebuild: wipe regenerable queue so stale pre-15m / pre-pair
        // cards never reappear. Re-scan after sanitizer only.
        onProgress("Rebuilding pending questions…")
        val rebuilt = mutableListOf<BatchPendingItem>()
        var added = 0
        fun appendPending(p: BatchPendingItem) {
            if (isPendingDup(rebuilt, p)) return
            rebuilt.add(p)
            added++
        }
        for (p in plan.newPending) appendPending(p)

        // Odo reverse / unreasonable gap sanitizer (after cluster merges applied)
        onProgress("Odo sanity…")
        var sanitizeUpdates = 0
        val afterMergeLive = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
        val san = FuelOdoSanitizer.sanitize(afterMergeLive)
        for (u in san.updates) {
            fuelEntryRepository.updateFuelEntry(u)
            sanitizeUpdates++
        }
        for (p in san.newPending) appendPending(p)

        val afterLive = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
        onProgress("Scanning unknown vehicles / economy…")
        for (e in afterLive.filter { it.vehicleId == UNASSIGNED_VEHICLE_ID }) {
            appendPending(FuelEconomyOutliers.unknownVehiclePending(e))
        }
        for (e in afterLive.filter { it.economyIgnored }) {
            appendPending(FuelEconomyOutliers.economyIgnoredPending(e))
        }
        for (leg in FuelEconomyOutliers.detectOutliers(afterLive)) {
            appendPending(FuelEconomyOutliers.toPending(leg))
        }

        BatchImportPendingStore.save(appContext, rebuilt)

        val totalUpdated = plan.updates.size + sanitizeUpdates
        val msg =
            "updated=$totalUpdated deleted=${plan.hardDeletes.size} " +
                "pending=$added (rebuild, sanitize=$sanitizeUpdates)"
        Log.i(TAG, "applyMerge $msg")
        onProgress("Done: $msg")
        MergeApplyResult(
            updated = totalUpdated,
            deleted = plan.hardDeletes.size,
            pendingAdded = added,
            totalPending = rebuilt.size,
            message = msg,
        )
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
     */
    suspend fun clearPendingAndRescan(
        onProgress: (String) -> Unit = {},
    ): MergeApplyResult {
        BatchImportPendingStore.clear(appContext)
        onProgress("Pending cleared; running merge + re-scan…")
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
        when (action) {
            is PendingAnswerAction.Skip -> {
                BatchImportPendingStore.remove(appContext, item.id)
                PendingAnswerResult("Skipped pending item", remerge = false)
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
                BatchImportPendingStore.remove(appContext, item.id)
                PendingAnswerResult(
                    "Kept both (no merge); re-merge may re-ask CONFLICT_ODO",
                    remerge = false,
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
                setEconomyIgnored(item, action.ignored)
            }
            is PendingAnswerAction.AssignUnknownVehicle -> {
                assignUnknownVehicle(item, action.vehicleId)
            }
            is PendingAnswerAction.FlagPartial -> {
                flagPartial(item, action.entryId)
            }
            is PendingAnswerAction.MarkAsGap -> {
                markAsGap(item, action.entryId)
            }
        }
    }

    private suspend fun flagPartial(
        item: BatchPendingItem,
        entryId: Long?,
    ): PendingAnswerResult {
        val id = entryId
            ?: item.fuelEntryId
            ?: item.extra["suspectId"]?.toLongOrNull()
            ?: item.extra["endEntryId"]?.toLongOrNull()
            ?: return PendingAnswerResult("No entry id to flag partial", success = false)
        val live = fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
            ?: return PendingAnswerResult("Fuel row $id not found", success = false)
        fuelEntryRepository.updateFuelEntry(live.copy(isPartialFill = true))
        clearAnsweredPending(item, id)
        Log.i(TAG, "flagPartial id=$id")
        return PendingAnswerResult("Flagged id=$id as partial (no longer MPG anchor)", remerge = true)
    }

    /**
     * Mark row as a **blank chain-breaker** (missed fill / gap): odo=cost=vol=0,
     * isPartialFill=false (matches batch_import_dash_blank). Keep vehicle, timestamp, photo.
     * Distinct from [FlagPartial] (keeps cost/vol) and economy ignore.
     */
    private suspend fun markAsGap(
        item: BatchPendingItem,
        entryId: Long?,
    ): PendingAnswerResult {
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
                        location = live.location?.takeIf { it.isNotBlank() }
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
                latitude = item.latitude,
                longitude = item.longitude,
                location = "batch_gap_marker",
            ),
        )
        clearAnsweredPending(item, null)
        Log.i(TAG, "markAsGap inserted blank gap marker")
        return PendingAnswerResult("Inserted gap marker (blank chain-breaker)", remerge = true)
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
                fuelEntryRepository.updateFuelEntry(
                    live.copy(
                        cost = if (cost > 0) cost else live.cost,
                        gallons = if (volume > 0) volume else live.gallons,
                        isPartialFill = true,
                        economyIgnored = false,
                    ),
                )
                clearAnsweredPending(item, existingId)
                return PendingAnswerResult("Updated pump fields on id=$existingId", remerge = true)
            }
        }
        val photoJson = path?.let { FuelPhotoJson.single("pump", it, ts) }
        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = UNASSIGNED_VEHICLE_ID,
                odometer = 0,
                gallons = volume.coerceAtLeast(0.0),
                cost = cost.coerceAtLeast(0.0),
                currency = "USD",
                timestamp = ts,
                photoUrl = photoJson,
                isPartialFill = true,
                latitude = item.latitude,
                longitude = item.longitude,
                location = "batch_manual_pump",
            ),
        )
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
                fuelEntryRepository.updateFuelEntry(
                    live.copy(
                        odometer = odometer,
                        vehicleId = vid,
                        economyIgnored = false,
                        isPartialFill = !(live.cost > 0 && live.gallons > 0),
                    ),
                )
                clearAnsweredPending(item, existingId)
                return PendingAnswerResult("Updated dash odo=$odometer vehicle=$vid", remerge = true)
            }
        }
        val photoJson = path?.let { FuelPhotoJson.single("dash", it, ts) }
        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = vid,
                odometer = odometer,
                gallons = 0.0,
                cost = 0.0,
                currency = "USD",
                timestamp = ts,
                photoUrl = photoJson,
                isPartialFill = true,
                latitude = item.latitude,
                longitude = item.longitude,
                location = "batch_manual_dash",
            ),
        )
        clearAnsweredPending(item, null)
        return PendingAnswerResult("Manual dash odo=$odometer vehicle=$vid", remerge = true)
    }

    private suspend fun manualEditFuelFields(
        item: BatchPendingItem,
        action: PendingAnswerAction.ManualEditFuelFields,
    ): PendingAnswerResult {
        val id = item.fuelEntryId
            ?: item.extra["suspectId"]?.toLongOrNull()
            ?: return PendingAnswerResult("No fuelEntryId", success = false)
        val live = fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
            ?: return PendingAnswerResult("Fuel row $id not found", success = false)
        val updated = live.copy(
            odometer = action.odometer?.takeIf { it > 0 } ?: live.odometer,
            cost = action.cost?.takeIf { it > 0 } ?: live.cost,
            gallons = action.volume?.takeIf { it > 0 } ?: live.gallons,
            economyIgnored = false,
        ).let { e ->
            val full = e.vehicleId > 0 && e.odometer > 0 && e.cost > 0 && e.gallons > 0
            e.copy(isPartialFill = !full)
        }
        fuelEntryRepository.updateFuelEntry(updated)
        clearAnsweredPending(item, id)
        return PendingAnswerResult("Edited fuel id=$id (ignore cleared)", remerge = true)
    }

    private suspend fun setEconomyIgnored(
        item: BatchPendingItem,
        ignored: Boolean,
    ): PendingAnswerResult {
        val id = item.fuelEntryId
            ?: return PendingAnswerResult("No fuelEntryId", success = false)
        val live = fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
            ?: return PendingAnswerResult("Fuel row $id not found", success = false)
        fuelEntryRepository.updateFuelEntry(live.copy(economyIgnored = ignored))
        if (!ignored) {
            clearAnsweredPending(item, id)
        } else {
            // Drop all kinds for this id; re-merge will re-enqueue ECONOMY_IGNORED
            clearAnsweredPending(item, id)
        }
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
                            e.copy(odometer = 0, isPartialFill = true),
                        )
                        updated++
                    } else {
                        fuelEntryRepository.hardDeleteFuelEntry(e)
                        deleted++
                        Log.i(TAG, "conflict resolve hardDelete id=${e.id} odo=${e.odometer}")
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
     * Dash: alignment **experiment Set J** pipeline via [AlignmentSetJRunner]
     * (not [com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness.runAutoFillPipeline]).
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
        val durable = copyToDurable(file, "dash")

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
                        photoPath = file.absolutePath,
                        durablePhotoPath = durable.absolutePath,
                        timestampMs = ts,
                        latitude = meta.latitude,
                        longitude = meta.longitude,
                    ),
                )
            }
            return false
        }

        val odo = parseSetJOdometer(result.odometer)
        val photoJson = FuelPhotoJson.single("dash", durable.absolutePath, ts)

        if (odo == null) {
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
                    latitude = meta.latitude,
                    longitude = meta.longitude,
                    location = "batch_import_dash_blank:${file.name}",
                ),
            )
            Log.i(TAG, "Inserted blank dash marker vehicle=${result.vehicleId} ${file.name}")
            return true
        }

        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = result.vehicleId,
                odometer = odo,
                gallons = 0.0,
                cost = 0.0,
                currency = "USD",
                timestamp = ts,
                photoUrl = photoJson,
                isPartialFill = true,
                latitude = meta.latitude,
                longitude = meta.longitude,
                location = "batch_import_dash:${file.name}",
            ),
        )
        Log.i(TAG, "Inserted odo partial vehicle=${result.vehicleId} odo=$odo ${file.name}")
        return true
    }

    /**
     * Pump photos: always Set I cost/vol OCR and insert as partial.
     * Default vehicleId is [UNASSIGNED_VEHICLE_ID] (0) until merge; optional
     * [forcedVehicleId] for legacy ASSIGN_VEHICLE pending answers.
     */
    private suspend fun processPump(
        file: File,
        pending: MutableList<BatchPendingItem>,
        forcedVehicleId: Int? = null,
        enqueuePendingOnFail: Boolean = true,
    ): Boolean {
        val meta = PhotoExifMetaReader.read(file.absolutePath)
        val ts = meta.timestampMs ?: System.currentTimeMillis()
        val durable = copyToDurable(file, "pump")
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
                        photoPath = file.absolutePath,
                        durablePhotoPath = durable.absolutePath,
                        timestampMs = ts,
                        latitude = meta.latitude,
                        longitude = meta.longitude,
                    ),
                )
            }
            return false
        }

        val photoJson = FuelPhotoJson.single("pump", durable.absolutePath, ts)
        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = vehicleId,
                odometer = 0,
                gallons = vol ?: 0.0,
                cost = cost ?: 0.0,
                currency = "USD",
                timestamp = ts,
                photoUrl = photoJson,
                isPartialFill = true,
                latitude = meta.latitude,
                longitude = meta.longitude,
                location = "batch_import_pump:${file.name}",
            ),
        )
        Log.i(
            TAG,
            "Inserted pump partial vehicleId=$vehicleId " +
                "cost=$cost vol=$vol ${file.name}",
        )
        return true
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

    /** Drop the pending conflict without changing fuel rows (re-merge may re-ask). */
    data object KeepBothNoMerge : PendingAnswerAction()

    /** Manual cost/volume for unreadable pump (insert or update). */
    data class ManualPumpEntry(val cost: Double, val volume: Double) : PendingAnswerAction()

    /** Manual odometer (+ vehicle) for unreadable dash. */
    data class ManualDashEntry(val odometer: Int, val vehicleId: Int?) : PendingAnswerAction()

    /** Edit odo/cost/vol on an existing fuel row (clears economyIgnored). */
    data class ManualEditFuelFields(
        val odometer: Int? = null,
        val cost: Double? = null,
        val volume: Double? = null,
    ) : PendingAnswerAction()

    data class SetEconomyIgnored(val ignored: Boolean) : PendingAnswerAction()

    data class AssignUnknownVehicle(val vehicleId: Int) : PendingAnswerAction()

    /**
     * Set [FuelEntry.isPartialFill]=true on [entryId] (default: pending fuelEntryId /
     * outlier end). Drops full-fill anchor; inventory still counts. Distinct from
     * [SetEconomyIgnored].
     */
    data class FlagPartial(val entryId: Long? = null) : PendingAnswerAction()

    /**
     * Blank chain-breaker: odo=cost=vol=0, isPartialFill=false.
     * Explicit zeros allowed (unlike manual pump entry).
     */
    data class MarkAsGap(val entryId: Long? = null) : PendingAnswerAction()
}

/** Result of [BatchFuelImportCoordinator.applyPendingAnswer]. */
data class PendingAnswerResult(
    val message: String,
    /** True when fuel rows changed and Stage B re-merge should run. */
    val remerge: Boolean = false,
    val success: Boolean = true,
)
