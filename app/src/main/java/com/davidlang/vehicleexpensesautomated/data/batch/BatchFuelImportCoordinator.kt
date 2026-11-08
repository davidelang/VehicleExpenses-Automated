package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.testOnlyBuild
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupScheduler
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
// FuelLocationJson same package
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.repository.forUserPicker
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
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
    @param:ApplicationContext internal val appContext: Context,
    internal val fuelEntryRepository: FuelEntryRepository,
    internal val vehicleRepository: VehicleRepository,
    internal val mergeAckStore: MergeAckStore,
) {
    companion object {
        internal const val TAG = "BatchFuelImport"
        internal val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "dng")

        /** Limited import size (like experiment Golden/Problem subset buttons). */
        const val LIMITED_IMPORT_COUNT = 20

        /** Min gap between outbound location lookup kickoffs (fair-use pacing). */
        const val MIN_LOCATION_LOOKUP_GAP_MS: Long = 1000L

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

    /**
     * Raw OCR face digits from Set J → **tracking** odometer for the fill table.
     * Applies vehicle digit width + rollover encoding.
     */
    private suspend fun resolveTrackingOdometer(
        raw: String?,
        vehicleId: Int,
        vehicles: List<Vehicle>,
    ): Pair<Int, Int?>? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val vehicle = vehicles.find { it.id == vehicleId }
            ?: vehicleRepository.getVehicleById(vehicleId)
        val last = fuelEntryRepository.getLastOdometerForVehicle(vehicleId)
        val resolved = OdometerTracking.resolveFromOcr(digits, vehicle, last) ?: return null
        if (vehicle != null && resolved.newRolloverCount > vehicle.odometerRolloverCount) {
            vehicleRepository.updateVehicle(
                vehicle.copy(odometerRolloverCount = resolved.newRolloverCount),
            )
        }
        return resolved.trackingOdometer to resolved.newRolloverCount
    }

    data class FieldMergeStats(
        val updated: Int,
        val deleted: Int,
        val liveCount: Int = 0,
        val pendingFromMerge: Int = 0,
        val secondPass: Boolean = false,
        /** Rows whose update or absorb threw. Other writes in the plan stay applied. */
        val failed: Int = 0,
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
                failed = first.failed + second.failed,
            )
        }
        first
    }

    internal suspend fun runFieldMerge(onProgress: (String) -> Unit): FieldMergeStats {
        onProgress("Photo path migrate (if needed)…")
        try {
            val mig = migrateDurablePhotoRefsToSource()
            if (mig.rewroteRows > 0 || mig.deletedFiles > 0) {
                Log.i(TAG, "durable migrate before merge: $mig")
            }
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            Log.w(TAG, "durable migrate before merge: ${e.message}")
        }
        onProgress("Loading fuel entries…")
        val all = fuelEntryRepository.getAllIncludingDeleted()
        val live = all.filter { !it.deleted }
        onProgress("Planning merge (${live.size} live / ${all.size} incl deleted)…")
        val exemptSets = try {
            mergeAckStore.liveMergeExemptSets()
        // kept: failure stays on this call so the screen or caller can continue
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
        var updated = 0
        var deleted = 0
        var failed = 0
        if (!plan.isEmpty()) {
            onProgress("Applying ${plan.updates.size} updates…")
            val paired = plan.updates.zip(plan.hardDeletes)
            for ((u, d) in paired) {
                try {
                    val softDeleted = shouldSoftDeleteOnAbsorb(d)
                    fuelEntryRepository.applyMerge(u, d, hardDeleteDonor = !softDeleted)
                    updated++
                    if (softDeleted) soft++ else hard++
                    deleted++
                // kept: failure stays on this call so the screen or caller can continue
                } catch (e: CancellationException) {
                    throw e
                // kept: failure stays on this call so the screen or caller can continue
                } catch (e: Exception) {
                    failed++
                    Log.w(TAG, "field-merge pair failed id=${u.id}/${d.id}", e)
                }
            }
            for (u in plan.updates.drop(paired.size)) {
                try {
                    fuelEntryRepository.updateFuelEntry(u)
                    updated++
                // kept: failure stays on this call so the screen or caller can continue
                } catch (e: CancellationException) {
                    throw e
                // kept: failure stays on this call so the screen or caller can continue
                } catch (e: Exception) {
                    failed++
                    Log.w(TAG, "field-merge update failed id=${u.id}", e)
                }
            }
            plan.hardDeletes.drop(paired.size).forEach { d ->
                try {
                    val softDeleted = absorbMergedRow(d)
                    if (softDeleted) soft++ else hard++
                    deleted++
                // kept: failure stays on this call so the screen or caller can continue
                } catch (e: CancellationException) {
                    throw e
                // kept: failure stays on this call so the screen or caller can continue
                } catch (e: Exception) {
                    failed++
                    Log.w(TAG, "field-merge absorb failed id=${d.id}", e)
                }
            }
            if (failed > 0) onProgress("$failed merges failed")
            Log.i(TAG, "merge absorb soft=$soft hard=$hard failed=$failed")
        }
        // Stash merge-side pending for phase filter in rebuild.
        // MergePlan has no pair key: a committed survivor update is not undone
        // when a later absorb throws.
        lastMergePending = plan.newPending
        return FieldMergeStats(
            updated = updated,
            deleted = deleted,
            liveCount = live.size,
            pendingFromMerge = plan.newPending.size,
            failed = failed,
        )
    }

    @Volatile
    private var lastMergePending: List<BatchPendingItem> = emptyList()

    /**
     * Generate pending for **one** phase only; apply skip ledger; save store.
     * A standard build (TEST_BUILD false) does not write [BatchImportPendingStore].
     */
    internal suspend fun rebuildPendingForPhase(
        phase: Int,
        onProgress: (String) -> Unit,
    ): MergeApplyResult {
        if (!testOnlyBuild()) {
            val msg = "phase $phase: pending review not saved"
            Log.i(TAG, "rebuildPendingForPhase $msg")
            onProgress("Done: $msg")
            return MergeApplyResult(
                updated = 0,
                deleted = 0,
                pendingAdded = 0,
                totalPending = 0,
                message = msg,
            )
        }
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
        // kept: failure stays on this call so the screen or caller can continue
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
     * Suggest vehicle for unassigned pump.
     *
     * Priority:
     * 1. **Time + location** within [FuelRowMergeEngine.MERGE_WINDOW_MS] against a
     *    vehicle-known fill (dash/odo-bearing preferred): unique vehicle → assign.
     *    Same stop → treat dash side as partial odo and pump as full cost/vol on that vehicle
     *    (merge engine field rules; not a third fill).
     * 2. Unique tank fit → tank-only suggest.
     * 3. Tank + nearest in time (existing).
     * 4. Nearest fill in 2× window if unique vehicle.
     *
     * Place match via [FuelStopMatch.locationsMatch] (name/address or lat/lon within
     * [FuelStopMatch.LOCATION_MATCH_METERS]). No location data → skip step 1 (tank/time only).
     */
    private fun suggestVehicleForUnassigned(
        pump: FuelEntry,
        allLive: List<FuelEntry>,
    ): Pair<Int, String>? {
        val known = allLive.filter { it.vehicleId > 0 && !it.deleted }
        if (known.isEmpty()) return null
        val window = FuelRowMergeEngine.MERGE_WINDOW_MS

        // 1) Time + location correlation (phase 4 residual; merge engine assigns first)
        val locMatch = known.filter { other ->
            kotlin.math.abs(other.timestamp - pump.timestamp) <= window &&
                FuelStopMatch.locationsMatch(pump, other)
        }
        if (locMatch.isNotEmpty()) {
            val vehicleIds = locMatch.map { it.vehicleId }.distinct()
            if (vehicleIds.size == 1) {
                val vid = vehicleIds.first()
                val dt = locMatch.minOf { kotlin.math.abs(it.timestamp - pump.timestamp) }
                return vid to "same location · Δt ${dt / 1000}s"
            }
        }

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
        val window2 = window * 2
        val nearSame = known.filter {
            kotlin.math.abs(it.timestamp - pump.timestamp) <= window2 &&
                it.vehicleId != nearestAny.vehicleId
        }
        if (nearSame.isEmpty() &&
            kotlin.math.abs(nearestAny.timestamp - pump.timestamp) <= window2
        ) {
            return nearestAny.vehicleId to "nearest fill in time"
        }
        return null
    }

    /**
     * After fuel sync: phase reset when fuel changed, then pending rebuild only.
     * Does not call [runFieldMerge]. Field-merge for a sync session is separate.
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
        val phase = StageCPhaseStore.currentPhase(appContext)
        return rebuildPendingForPhase(phase, onProgress)
    }

    /**
     * Absorb a merge loser. Soft-delete when the row may need a sync tombstone
     * (photo cloudManifest set, or not a pure local batch_import path). Hard-delete
     * pure local batch rows that were never pushed.
     *
     * @return true if soft-deleted, false if hard-deleted
     */
    internal suspend fun absorbMergedRow(entry: FuelEntry): Boolean {
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
     * provenance tag (notes preferred; location for legacy). Fill / sheet rows
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
