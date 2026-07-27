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

        val existing = BatchImportPendingStore.load(appContext)
        var added = 0
        fun appendPending(p: BatchPendingItem) {
            val dup = existing.any { e ->
                e.kind == p.kind && (
                    (p.fuelEntryId != null && e.fuelEntryId == p.fuelEntryId) ||
                        (p.photoPath != null && e.photoPath == p.photoPath) ||
                        e.message == p.message
                    )
            }
            if (!dup) {
                existing.add(p)
                added++
            }
        }
        for (p in plan.newPending) appendPending(p)

        // After merge (or no-op): enqueue unknown vehicle + economy/outlier questions
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

        BatchImportPendingStore.save(appContext, existing)

        val msg = "updated=${plan.updates.size} deleted=${plan.hardDeletes.size} pending+=$added"
        Log.i(TAG, "applyMerge $msg")
        onProgress("Done: $msg")
        MergeApplyResult(
            updated = plan.updates.size,
            deleted = plan.hardDeletes.size,
            pendingAdded = added,
            totalPending = existing.size,
            message = msg,
        )
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
                            BatchImportPendingStore.remove(appContext, item.id)
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
                            BatchImportPendingStore.remove(appContext, item.id)
                            PendingAnswerResult(
                                "Pump processed for vehicle ${action.vehicleId}",
                                remerge = true,
                            )
                        } else {
                            PendingAnswerResult("Pump reprocess failed", success = false)
                        }
                    }
                    else -> {
                        BatchImportPendingStore.remove(appContext, item.id)
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
                    BatchImportPendingStore.remove(appContext, item.id)
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
        }
    }

    private suspend fun manualPumpEntry(
        item: BatchPendingItem,
        cost: Double,
        volume: Double,
    ): PendingAnswerResult {
        if (cost <= 0 && volume <= 0) {
            return PendingAnswerResult("Enter cost and/or volume > 0", success = false)
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
                BatchImportPendingStore.remove(appContext, item.id)
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
        BatchImportPendingStore.remove(appContext, item.id)
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
                BatchImportPendingStore.remove(appContext, item.id)
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
        BatchImportPendingStore.remove(appContext, item.id)
        return PendingAnswerResult("Manual dash odo=$odometer vehicle=$vid", remerge = true)
    }

    private suspend fun manualEditFuelFields(
        item: BatchPendingItem,
        action: PendingAnswerAction.ManualEditFuelFields,
    ): PendingAnswerResult {
        val id = item.fuelEntryId
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
        BatchImportPendingStore.remove(appContext, item.id)
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
            BatchImportPendingStore.remove(appContext, item.id)
        } else {
            // Keep/refresh ECONOMY_IGNORED pending; remove MPG_OUTLIER for same id
            val pending = BatchImportPendingStore.load(appContext)
            val kept = pending.filterNot {
                it.id == item.id ||
                    (it.kind == BatchPendingKind.MPG_OUTLIER && it.fuelEntryId == id)
            }.toMutableList()
            val fresh = FuelEconomyOutliers.economyIgnoredPending(live.copy(economyIgnored = true))
            if (kept.none { it.kind == BatchPendingKind.ECONOMY_IGNORED && it.fuelEntryId == id }) {
                kept.add(fresh)
            }
            BatchImportPendingStore.save(appContext, kept)
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
        BatchImportPendingStore.remove(appContext, item.id)
        return PendingAnswerResult("Assigned unknown → vehicle $vehicleId", remerge = true)
    }

    /**
     * Neighbor fills for context UI.
     * Prefer [fuelEntryId] lookup; else filter by timestamp window.
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
        if (around != null) {
            val useAll = allVehicles || around.vehicleId == 0
            val pool = if (useAll) live else live.filter { it.vehicleId == around.vehicleId }
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
        val useAll = allVehicles || (vehicleIdHint ?: 0) == 0 || around?.vehicleId == 0
        val pool = when {
            useAll -> live
            vehicleIdHint != null && vehicleIdHint > 0 -> live.filter { it.vehicleId == vehicleIdHint }
            else -> live
        }
        pool.filter { kotlin.math.abs(it.timestamp - ts) <= window }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
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
            BatchImportPendingStore.remove(appContext, item.id)
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
        BatchImportPendingStore.remove(appContext, item.id)
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
}

/** Result of [BatchFuelImportCoordinator.applyPendingAnswer]. */
data class PendingAnswerResult(
    val message: String,
    /** True when fuel rows changed and Stage B re-merge should run. */
    val remerge: Boolean = false,
    val success: Boolean = true,
)
