package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.data.db.AppDatabase
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupScheduler
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
// FuelLocationJson same package
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.ui.ocr.PumpSetIRunner
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.repository.forUserPicker
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import com.davidlang.vehicleexpensesautomated.ui.util.ImageIngestionProvider
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerTracking
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoExifMeta
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoExifMetaReader
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoExifWriter
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookup
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupKind
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/** Result of applying [FuelRowMergeEngine.planMerge] to the live fuel table. */
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator.Companion.TAG
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator.Companion.IMAGE_EXTS
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator.Companion.MIN_LOCATION_LOOKUP_GAP_MS
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator.Companion.UNASSIGNED_VEHICLE_ID
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator.Companion.dashPhotoDir
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator.Companion.pumpPhotoDir
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator.FieldMergeStats
import com.davidlang.vehicleexpensesautomated.ui.util.stampIfCaptureIncomplete

private val cancelFlag = AtomicBoolean(false)


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


    /** Per-ingest context for optional fire-and-forget location races. */
    internal data class LocationEnhanceCtx(
        val enabled: Boolean,
        val scope: CoroutineScope,
        val jobs: MutableMap<String, Deferred<LocationLookupResult?>>,
        val lastKickoffMs: AtomicLong,
        val onKickoff: () -> Unit,
    )


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


    fun BatchFuelImportCoordinator.requestCancel() {
        cancelFlag.set(true)
    }


    fun BatchFuelImportCoordinator.clearCancel() {
        cancelFlag.set(false)
    }


    private fun BatchFuelImportCoordinator.listImages(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f ->
            f.isFile && f.extension.lowercase() in IMAGE_EXTS
        }?.sortedBy { it.name } ?: emptyList()
    }


    private fun BatchFuelImportCoordinator.parseMoneyOrVol(raw: String?): Double? {
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
     * @param locationEnhanceWithImport when true, fire-and-forget POI lookup **before** OCR
     *   for each photo with EXIF coords (U4); never awaits lookup for OCR/insert completion.
     *   Default off (U3). Rate-limit paces next photo kickoff if gap &lt; ~1 s (U5).
     */
    suspend fun BatchFuelImportCoordinator.runIngest(
        vehicles: List<Vehicle>,
        onProgress: (BatchImportProgress) -> Unit = {},
        maxDash: Int? = null,
        maxPump: Int? = null,
        locationEnhanceWithImport: Boolean = false,
    ): BatchImportResult = withContext(Dispatchers.Default) {
        clearCancel()
        val pending = BatchImportPendingStore.load(appContext).toMutableList()
        val errors = mutableListOf<String>()
        var dashInserted = 0
        var pumpInserted = 0
        var errCount = 0
        var locationLookupsStarted = 0

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
        val enhanceNote = if (locationEnhanceWithImport) " · location enhance on" else ""
        report("init", "Dash ${dashFiles.size} · pump ${pumpFiles.size}$limitNote$enhanceNote")

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

        var cancelled = false
        supervisorScope {
            val lookupJobs = mutableMapOf<String, Deferred<LocationLookupResult?>>()
            val lastKickoffMs = AtomicLong(0L)
            val enhanceCtx = LocationEnhanceCtx(
                enabled = locationEnhanceWithImport,
                scope = this,
                jobs = lookupJobs,
                lastKickoffMs = lastKickoffMs,
                onKickoff = { locationLookupsStarted++ },
            )

            // --- Dash (paddle seed via OcrHarness.runAutoFillPipeline) ---
            for (file in dashFiles) {
                coroutineContext.ensureActive()
                if (cancelFlag.get()) {
                    cancelled = true
                    break
                }
                done++
                report(
                    "dash",
                    "Dash OCR ${file.name} ($done/$total)" +
                        if (locationEnhanceWithImport) " · loc race" else "",
                )
                try {
                    if (processDash(file, vehicles, pending, enhanceCtx = enhanceCtx)) dashInserted++
                } catch (e: Exception) {
                    errCount++
                    val m = "Dash ${file.name}: ${e.message}"
                    Log.e(TAG, m, e)
                    errors.add(m)
                }
            }

            // --- Pump (Set I): always OCR + insert; vehicleId left unassigned (0) until merge ---
            if (!cancelled) {
                for (file in pumpFiles) {
                    coroutineContext.ensureActive()
                    if (cancelFlag.get()) {
                        cancelled = true
                        break
                    }
                    done++
                    report(
                        "pump",
                        "Pump Set I ${file.name} ($done/$total)" +
                            if (locationEnhanceWithImport) " · loc race" else "",
                    )
                    try {
                        val inserted = processPump(file, pending, enhanceCtx = enhanceCtx)
                        if (inserted) pumpInserted++
                    } catch (e: Exception) {
                        errCount++
                        val m = "Pump ${file.name}: ${e.message}"
                        Log.e(TAG, m, e)
                        errors.add(m)
                    }
                }
            }

            // Cancel unfinished races; worker fills leftovers when enhance was on
            for ((_, job) in lookupJobs) {
                if (!job.isCompleted) job.cancel()
            }
            lookupJobs.clear()
        }

        BatchImportPendingStore.save(appContext, pending)
        if (cancelled) {
            return@withContext BatchImportResult(
                dashInserted, pumpInserted, pending, errors, cancelled = true,
            )
        }
        if (locationEnhanceWithImport && locationLookupsStarted > 0) {
            LocationLookupScheduler.enqueueSoon(appContext)
            report(
                "done",
                "Finished: dash=$dashInserted pump=$pumpInserted pending=${pending.size}" +
                    " · location lookups started=$locationLookupsStarted (queued/in-flight)",
            )
        } else {
            report("done", "Finished: dash=$dashInserted pump=$pumpInserted pending=${pending.size}")
        }
        BatchImportResult(dashInserted, pumpInserted, pending, errors, cancelled = false)
    }


    /**
     * Enqueue background location enhance for fuel rows with coords and no place.
     * Non-blocking — does not wait for Overpass/Nominatim.
     */
    suspend fun BatchFuelImportCoordinator.runLocationEnhance(onProgress: (String) -> Unit = {}): String =
        withContext(Dispatchers.IO) {
            onProgress("Scanning fuel for coords without place…")
            val live = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
            val need = live.count { FuelLocationJson.hasCoordsWithoutPlace(it.location) }
            onProgress("Enqueue location worker ($need candidates)…")
            LocationLookupScheduler.enqueueSoon(appContext)
            val msg = if (need == 0) {
                "No coords-without-place rows; worker still enqueued"
            } else {
                "Location enhance queued for ~$need rows (background, non-blocking)"
            }
            onProgress(msg)
            msg
        }


    /**
     * Field-merge all live partials + **phase-scoped** pending rebuild only.
     * Pending store holds at most the current phase’s kinds (not full multi-phase backlog).
     */
    suspend fun BatchFuelImportCoordinator.applyMerge(
        onProgress: (String) -> Unit = {},
    ): MergeApplyResult = withContext(Dispatchers.IO) {
        val phase = StageCPhaseStore.currentPhase(appContext)
        runFieldMerge(onProgress) + rebuildPendingForPhase(phase, onProgress)
    }


    /**
     * Advance Stage C to [newPhase] (or current+1), field-merge, generate **only**
     * that phase’s questions from live Room.
     */
    suspend fun BatchFuelImportCoordinator.advancePhaseAndRebuild(
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


    /**
     * Time-vs-odo disorder previews for live fuel (anchors = odo > 0).
     * @see FuelOdoReorder
     */
    suspend fun BatchFuelImportCoordinator.analyzeOdoDisorder(): List<FuelOdoReorder.VehicleDisorder> =
        withContext(Dispatchers.IO) {
            val live = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
            FuelOdoReorder.analyzeAll(live)
        }


    /**
     * Apply reorder strategy A/B/C for one vehicle or all vehicles with work, then
     * field-merge + rebuild current Stage C phase.
     *
     * **A** permutes existing timestamps onto odo order (no invented times).
     * **B** marks [FuelEntry.economyIgnored] on later-by-time reverse offenders.
     * **C** soft-deletes those offenders.
     */
    suspend fun BatchFuelImportCoordinator.applyOdoReorder(
        vehicleId: Int?,
        strategy: FuelOdoReorder.Strategy,
        onProgress: (String) -> Unit = {},
    ): MergeApplyResult = withContext(Dispatchers.IO) {
        val live = fuelEntryRepository.getAllIncludingDeleted().filter { !it.deleted }
        val disorders = FuelOdoReorder.analyzeAll(live)
            .filter { d -> vehicleId == null || d.vehicleId == vehicleId }
            .filter { it.hasWork || it.anchorCount >= 2 }
        if (disorders.isEmpty()) {
            val n = BatchImportPendingStore.load(appContext).size
            return@withContext MergeApplyResult(
                updated = 0,
                deleted = 0,
                pendingAdded = 0,
                totalPending = n,
                message = "No vehicles with odometer anchors to reorder",
            )
        }
        var touched = 0
        var ignored = 0
        var deleted = 0
        var permuted = 0
        for (d in disorders) {
            val anchors = FuelOdoReorder.anchorsForVehicle(live, d.vehicleId)
            when (strategy) {
                FuelOdoReorder.Strategy.PERMUTE_TIMESTAMPS -> {
                    val updates = FuelOdoReorder.permuteTimestampsByOdo(anchors)
                    if (updates.isEmpty()) {
                        onProgress("Vehicle ${d.vehicleId}: already in odo order")
                        continue
                    }
                    onProgress("Vehicle ${d.vehicleId}: permuting ${updates.size} timestamps…")
                    for (u in updates) {
                        fuelEntryRepository.updateFuelEntry(u)
                        permuted++
                        touched++
                    }
                }
                FuelOdoReorder.Strategy.ECONOMY_IGNORE -> {
                    if (d.reverseSteps.isEmpty()) {
                        onProgress("Vehicle ${d.vehicleId}: no reverse steps to ignore")
                        continue
                    }
                    val ids = d.reverseSteps.map { it.later.id }.distinct()
                    onProgress("Vehicle ${d.vehicleId}: economyIgnored on ${ids.size} rows…")
                    for (id in ids) {
                        val e = live.find { it.id == id } ?: continue
                        if (e.economyIgnored) continue
                        fuelEntryRepository.updateFuelEntry(e.copy(economyIgnored = true))
                        ignored++
                        touched++
                    }
                }
                FuelOdoReorder.Strategy.DELETE_OFFENDERS -> {
                    if (d.reverseSteps.isEmpty()) {
                        onProgress("Vehicle ${d.vehicleId}: no reverse offenders to delete")
                        continue
                    }
                    val ids = d.reverseSteps.map { it.later.id }.distinct()
                    onProgress("Vehicle ${d.vehicleId}: soft-deleting ${ids.size} rows…")
                    for (id in ids) {
                        val e = live.find { it.id == id } ?: continue
                        if (e.deleted) continue
                        fuelEntryRepository.deleteFuelEntry(e)
                        deleted++
                        touched++
                    }
                }
            }
        }
        if (touched == 0) {
            val msg = when (strategy) {
                FuelOdoReorder.Strategy.PERMUTE_TIMESTAMPS ->
                    "No timestamp changes (already odo-ordered or empty)"
                FuelOdoReorder.Strategy.ECONOMY_IGNORE ->
                    "No reverse steps to mark ignored"
                FuelOdoReorder.Strategy.DELETE_OFFENDERS ->
                    "No reverse offenders to delete"
            }
            val n = BatchImportPendingStore.load(appContext).size
            return@withContext MergeApplyResult(
                updated = 0,
                deleted = 0,
                pendingAdded = 0,
                totalPending = n,
                message = msg,
            )
        }
        onProgress("Rescanning Stage C after reorder…")
        val merge = applyMerge(onProgress)
        val summary = buildString {
            append("Reorder ")
            append(
                when (strategy) {
                    FuelOdoReorder.Strategy.PERMUTE_TIMESTAMPS -> "A(timestamps=$permuted)"
                    FuelOdoReorder.Strategy.ECONOMY_IGNORE -> "B(ignored=$ignored)"
                    FuelOdoReorder.Strategy.DELETE_OFFENDERS -> "C(deleted=$deleted)"
                },
            )
            append(" · ")
            append(merge.message)
        }
        Log.i(TAG, summary)
        merge.copy(message = summary)
    }


    private operator fun FieldMergeStats.plus(r: MergeApplyResult): MergeApplyResult =
        MergeApplyResult(
            updated = updated + r.updated,
            deleted = deleted + r.deleted,
            pendingAdded = r.pendingAdded,
            totalPending = r.totalPending,
            message = "updated=$updated deleted=$deleted · ${r.message}",
        )


    /** Load fuel row for pre-filling edit fields. */
    suspend fun BatchFuelImportCoordinator.getFuelEntry(id: Long): FuelEntry? = withContext(Dispatchers.IO) {
        fuelEntryRepository.getAllIncludingDeleted().find { it.id == id && !it.deleted }
    }


    /**
     * MPG_OUTLIER context lines: one fill immediately before [lastTs], one after [thisTs],
     * same vehicle only. Does not walk generic ±N that can skip the true leg start.
     */
    suspend fun BatchFuelImportCoordinator.nearestFillBefore(
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


    suspend fun BatchFuelImportCoordinator.nearestFillAfter(
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


    /** Paths for UI: item fields + photoUrl from related fuel rows when ids present. */
    suspend fun BatchFuelImportCoordinator.resolvePendingPhotoUris(item: BatchPendingItem): List<String> =
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


    /** One-shot deferred POI when insert left coords without place (non-blocking). */
    internal fun BatchFuelImportCoordinator.maybeEnqueueLocationLookup(locationJson: String?) {
        if (FuelLocationJson.hasCoordsWithoutPlace(locationJson)) {
            LocationLookupScheduler.enqueueSoon(appContext)
        }
    }


    /**
     * Kick off non-blocking POI lookup before OCR when enhance is on and EXIF has coords.
     * Paces kickoffs to ≥ [MIN_LOCATION_LOOKUP_GAP_MS]. Never waits for the result.
     */
    private suspend fun BatchFuelImportCoordinator.kickoffLocationLookupBeforeOcr(
        key: String,
        lat: Double?,
        lon: Double?,
        accuracyM: Double?,
        kind: LocationLookupKind,
        enhanceCtx: LocationEnhanceCtx?,
    ) {
        if (enhanceCtx == null || !enhanceCtx.enabled) return
        if (lat == null || lon == null) return
        if (enhanceCtx.jobs.containsKey(key)) return
        val last = enhanceCtx.lastKickoffMs.get()
        if (last > 0L) {
            val gap = System.currentTimeMillis() - last
            if (gap < MIN_LOCATION_LOOKUP_GAP_MS) {
                delay(MIN_LOCATION_LOOKUP_GAP_MS - gap)
            }
        }
        enhanceCtx.lastKickoffMs.set(System.currentTimeMillis())
        enhanceCtx.onKickoff()
        enhanceCtx.jobs[key] = enhanceCtx.scope.async(Dispatchers.IO) {
            LocationLookup.lookup(
                lat = lat,
                lon = lon,
                kind = kind,
                accuracyM = accuracyM,
                uiTimeout = false,
            )
        }
        Log.i(TAG, "location race started key=${key.substringAfterLast('/')}")
    }


    /**
     * Build location blob from EXIF coords; if a pre-OCR race finished in time, merge place.
     * Does **not** await an in-flight lookup. Enqueues worker only when enhance was on and
     * place still missing.
     */
    private suspend fun BatchFuelImportCoordinator.locationBlobAfterRace(
        lat: Double?,
        lon: Double?,
        accuracyM: Double?,
        key: String,
        enhanceCtx: LocationEnhanceCtx?,
    ): String? {
        var locJson = FuelLocationJson.encode(
            FuelLocationJson.fromCoords(lat, lon, accuracyM, source = "exif"),
        )
        val job = enhanceCtx?.jobs?.remove(key)
        if (job != null) {
            if (job.isCompleted) {
                // Completed race only — never await in-flight (OCR/insert not gated)
                val result = runCatching { job.await() }.getOrNull()
                if (result != null && result.hasPlace()) {
                    locJson = LocationLookup.mergePlaceIntoBlob(locJson, result, confirmed = false)
                        ?: locJson
                }
            } else {
                job.cancel()
            }
        }
        if (enhanceCtx?.enabled == true) {
            maybeEnqueueLocationLookup(locJson)
        }
        return locJson
    }


    /**
     * Read EXIF, stamp datetime+offset when those tags are missing, then re-read.
     * [System.currentTimeMillis] is only for the caller when this still has no time.
     */
    private fun BatchFuelImportCoordinator.readMetaAfterCaptureStamp(file: File): PhotoExifMeta {
        val path = file.absolutePath
        val first = PhotoExifMetaReader.read(path)
        val wrote = PhotoExifWriter.stampIfCaptureIncomplete(path, first)
        return if (wrote) PhotoExifMetaReader.read(path) else first
    }


    /**
     * Dash: same paddle-seed pipeline as Fill ([OcrHarness.runAutoFillPipeline]).
     * EXIF upright is applied at ingest; no extra camera rotation.
     *
     * Photo path = **source file in place** (no copy into batch_import_photos).
     */
    internal suspend fun BatchFuelImportCoordinator.processDash(
        file: File,
        vehicles: List<Vehicle>,
        pending: MutableList<BatchPendingItem>,
        forcedVehicleId: Int? = null,
        enqueuePendingOnFail: Boolean = true,
        enhanceCtx: LocationEnhanceCtx? = null,
    ): Boolean {
        val meta = readMetaAfterCaptureStamp(file)
        val ts = meta.timestampMs ?: System.currentTimeMillis().also {
            Log.w(TAG, "No capture time for ${file.name} after EXIF write (source=${meta.source}); using now")
        }
        // Reference source path only — never copyToDurable
        val sourcePath = file.absolutePath

        // Location race before OCR when enhance on (never await for OCR)
        kickoffLocationLookupBeforeOcr(
            key = sourcePath,
            lat = meta.latitude,
            lon = meta.longitude,
            accuracyM = meta.accuracyM,
            kind = LocationLookupKind.FUEL_STATION,
            enhanceCtx = enhanceCtx,
        )

        val activeVehicles = vehicles.filter { !it.deleted }
        val (imgW, imgH) = ImageIngestionProvider.probeDimensions(appContext, sourcePath)
        if (imgW <= 0 || imgH <= 0) {
            if (enqueuePendingOnFail) {
                pending.add(
                    BatchPendingItem(
                        kind = BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE,
                        message = "Bad image dimensions for ${file.name}",
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
        val master = NativePaddleEngine.bufferSetA
        master.resize(imgW, imgH)
        ImageIngestionProvider.ingestFromFile(appContext, sourcePath, master.p)

        val lastByVehicle = activeVehicles.associate { v ->
            v.id to fuelEntryRepository.getLastOdometerForVehicle(v.id)
        }
        val result = OcrHarness.runAutoFillPipeline(
            context = appContext,
            masterBuffer = master,
            allVehicles = vehicles.forUserPicker(),
            debug = false,
            cameraRotationDegrees = 0,
            forcedVehicleId = forcedVehicleId,
            lastTrackingByVehicleId = lastByVehicle,
        )

        if (result.vehicleId == null) {
            if (enqueuePendingOnFail) {
                val extra = if (result.survivorIds != null) {
                    mapOf("survivorIds" to result.survivorIds.joinToString(","))
                } else {
                    emptyMap()
                }
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
                        extra = extra,
                    ),
                )
            }
            return false
        }

        result.newRolloverCount?.let { newCount ->
            val vehicle = vehicles.find { it.id == result.vehicleId }
                ?: vehicleRepository.getVehicleById(result.vehicleId)
            if (vehicle != null && newCount > vehicle.odometerRolloverCount) {
                vehicleRepository.updateVehicle(
                    vehicle.copy(odometerRolloverCount = newCount),
                )
            }
        }
        val odo = result.odometer?.toIntOrNull()
        val photoJson = FuelPhotoJson.single("dash", sourcePath, ts)

        if (odo == null) {
            val locJson = locationBlobAfterRace(
                meta.latitude, meta.longitude, meta.accuracyM, sourcePath, enhanceCtx,
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
            Log.i(TAG, "Inserted blank dash marker vehicle=${result.vehicleId} ${file.name}")
            return true
        }

        val locJson = locationBlobAfterRace(
            meta.latitude, meta.longitude, meta.accuracyM, sourcePath, enhanceCtx,
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
    internal suspend fun BatchFuelImportCoordinator.processPump(
        file: File,
        pending: MutableList<BatchPendingItem>,
        forcedVehicleId: Int? = null,
        enqueuePendingOnFail: Boolean = true,
        enhanceCtx: LocationEnhanceCtx? = null,
    ): Boolean {
        val meta = readMetaAfterCaptureStamp(file)
        val ts = meta.timestampMs ?: System.currentTimeMillis().also {
            Log.w(TAG, "No capture time for ${file.name} after EXIF write (source=${meta.source}); using now")
        }
        val sourcePath = file.absolutePath
        val vehicleId = forcedVehicleId ?: UNASSIGNED_VEHICLE_ID

        kickoffLocationLookupBeforeOcr(
            key = sourcePath,
            lat = meta.latitude,
            lon = meta.longitude,
            accuracyM = meta.accuracyM,
            kind = LocationLookupKind.FUEL_STATION,
            enhanceCtx = enhanceCtx,
        )

        // Experiment Set I path (not OcrHarness.runPumpCostVolPipelineSetI / Fill G4)
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
        val locJson = locationBlobAfterRace(
            meta.latitude, meta.longitude, meta.accuracyM, sourcePath, enhanceCtx,
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
        Log.i(
            TAG,
            "Inserted pump cost/vol vehicleId=$vehicleId " +
                "cost=$cost vol=$vol ${file.name}",
        )
        return true
    }

