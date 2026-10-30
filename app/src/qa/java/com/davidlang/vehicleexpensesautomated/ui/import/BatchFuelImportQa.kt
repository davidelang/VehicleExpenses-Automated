package com.davidlang.vehicleexpensesautomated.ui.import

import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator
import com.davidlang.vehicleexpensesautomated.data.batch.BatchFuelImportCoordinator.Companion.UNASSIGNED_VEHICLE_ID
import com.davidlang.vehicleexpensesautomated.data.batch.PerVehicleNeighbor
import com.davidlang.vehicleexpensesautomated.data.batch.BatchImportPendingStore
import com.davidlang.vehicleexpensesautomated.data.batch.BatchPendingItem
import com.davidlang.vehicleexpensesautomated.data.batch.BatchPendingKind
import com.davidlang.vehicleexpensesautomated.data.batch.FuelEconomyChains
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.batch.FuelRowMergeEngine
import com.davidlang.vehicleexpensesautomated.data.batch.MergeApplyResult
import com.davidlang.vehicleexpensesautomated.data.batch.PendingAnswerAction
import com.davidlang.vehicleexpensesautomated.data.batch.PendingAnswerResult
import com.davidlang.vehicleexpensesautomated.data.batch.StageCAnswerJournal
import com.davidlang.vehicleexpensesautomated.data.batch.StageCPhaseStore
import com.davidlang.vehicleexpensesautomated.data.batch.StageCSkipLedger
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoExifMetaReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.davidlang.vehicleexpensesautomated.data.batch.acknowledge
import com.davidlang.vehicleexpensesautomated.data.batch.applyMerge
import com.davidlang.vehicleexpensesautomated.data.batch.maybeEnqueueLocationLookup
import com.davidlang.vehicleexpensesautomated.data.batch.processDash
import com.davidlang.vehicleexpensesautomated.data.batch.processPump

/** Drop every pending item that references [fuelEntryId] (all kinds). */
private fun BatchFuelImportCoordinator.removePendingForFuelEntry(fuelEntryId: Long?) {
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
suspend fun BatchFuelImportCoordinator.clearPendingAndRescan(
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
suspend fun BatchFuelImportCoordinator.applyPendingAnswer(
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
                    BatchPendingKind.ODO_SUSPECT ->
                        com.davidlang.vehicleexpensesautomated.data.model.MergeAck.KIND_ODO_SUSPECT
                    else -> item.kind.name
                }
            // ODO_SUSPECT / MPG: durable re-ask suppress only — not field-merge exempt.
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
private suspend fun BatchFuelImportCoordinator.saveOdoPeers(
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
    Log.i(BatchFuelImportCoordinator.TAG, msg)
    return PendingAnswerResult(msg, remerge = true)
}

/**
 * Explicit partial override. Only legal when odo, cost, and volume are all present.
 * [partial]=false clears the flag. Incomplete rows always store false.
 */
private suspend fun BatchFuelImportCoordinator.setPartialFill(
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
    Log.i(BatchFuelImportCoordinator.TAG, "setPartialFill id=$id partial=$value")
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
suspend fun BatchFuelImportCoordinator.clearAutoPartialFlags(allComplete: Boolean = true): Int =
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
        Log.i(BatchFuelImportCoordinator.TAG, "clearAutoPartialFlags cleared=$n allComplete=$allComplete")
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
private suspend fun BatchFuelImportCoordinator.markAsGap(
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
            Log.i(BatchFuelImportCoordinator.TAG, "markAsGap updated id=$id to blank breaker")
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
    Log.i(BatchFuelImportCoordinator.TAG, "markAsGap inserted blank gap marker")
    return PendingAnswerResult("Inserted gap marker (blank chain-breaker)", remerge = true)
}

/**
 * Insert mid-leg blank between prev and end full fills. Anchors unchanged.
 */
private suspend fun BatchFuelImportCoordinator.markAsGapMpgOutlier(item: BatchPendingItem): PendingAnswerResult {
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
        Log.i(BatchFuelImportCoordinator.TAG, "markAsGap MPG: breaker already in window; dismissed pending")
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
        BatchFuelImportCoordinator.TAG,
        "markAsGap MPG: inserted mid-leg blank vehicle=$vehicleId ts=$gapTs " +
            "between prev=$prevId end=$endId (anchors preserved)",
    )
    return PendingAnswerResult(
        "Inserted gap between last & this (anchors kept)",
        remerge = true,
    )
}

private fun BatchFuelImportCoordinator.clearAnsweredPending(item: BatchPendingItem, fuelEntryId: Long?) {
    removePendingForFuelEntry(fuelEntryId)
    removePendingForFuelEntry(item.fuelEntryId)
    removePendingForFuelEntry(item.extra["suspectId"]?.toLongOrNull())
    removePendingForFuelEntry(item.extra["endEntryId"]?.toLongOrNull())
    BatchImportPendingStore.remove(appContext, item.id)
}

private suspend fun BatchFuelImportCoordinator.manualPumpEntry(
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

private suspend fun BatchFuelImportCoordinator.manualDashEntry(
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

private suspend fun BatchFuelImportCoordinator.manualEditFuelFields(
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

private suspend fun BatchFuelImportCoordinator.setEconomyIgnored(
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

private suspend fun BatchFuelImportCoordinator.assignUnknownVehicle(
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
suspend fun BatchFuelImportCoordinator.neighborContext(
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

suspend fun BatchFuelImportCoordinator.nearestNeighborsPerVehicle(
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

/**
 * Resolve member fuel syncIds from pending extra (entrySyncIds / entryIds → Room)
 * and write durable ack; dismiss pending. MERGE_EXEMPT when [alsoMergeExempt].
 */
private suspend fun BatchFuelImportCoordinator.durableAckLooksCorrect(
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
    Log.i(BatchFuelImportCoordinator.TAG, "durable ack kind=$kind members=${syncIds.sorted()} exempt=$alsoMergeExempt")
    return PendingAnswerResult(
        "Looks correct — won't ask again ($kind)",
        remerge = true,
    )
}

/** Member fuel syncIds from extra keys or entryIds → live fuel rows. */
private suspend fun BatchFuelImportCoordinator.resolveMemberSyncIds(item: BatchPendingItem): Set<String> {
    val fromExtra = mergeAckStore.pendingMemberSyncIds(item)
    if (fromExtra.isNotEmpty()) return fromExtra
    val entryIds = buildList {
        item.fuelEntryId?.let { add(it) }
        item.extra["entryIds"]
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.let { addAll(it) }
        // Complex ODO peers (prev/cur/next) + MPG/simple keys
        item.extra["prevEntryId"]?.toLongOrNull()?.let { add(it) }
        item.extra["curEntryId"]?.toLongOrNull()?.let { add(it) }
        item.extra["nextEntryId"]?.toLongOrNull()?.let { add(it) }
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
 * Keep [chosenOdo] as the only positive odometer among cluster entryIds.
 * Pure odo-only rows with a different odo are hard-deleted; rows that still
 * have cost/vol keep those fields with odo cleared for re-merge pairing.
 */
private suspend fun BatchFuelImportCoordinator.resolveConflictOdo(
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
                    Log.i(BatchFuelImportCoordinator.TAG, "conflict resolve absorb id=${e.id} odo=${e.odometer}")
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
    Log.i(BatchFuelImportCoordinator.TAG, "resolveConflictOdo $msg")
    return PendingAnswerResult(msg, remerge = true)
}

/**
 * Lat/lon/accuracy from pending item, re-reading EXIF from photo path when any field is missing.
 * Prefer already-set item fields over re-read.
 */
private fun BatchFuelImportCoordinator.resolvePendingGeo(item: BatchPendingItem): Triple<Double?, Double?, Double?> {
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
                Log.w(BatchFuelImportCoordinator.TAG, "EXIF re-read for pending failed: ${e.message}")
            }
        }
    }
    return Triple(lat, lon, acc)
}

private fun BatchFuelImportCoordinator.locationBlobFromPending(item: BatchPendingItem): String? {
    val (lat, lon, acc) = resolvePendingGeo(item)
    return FuelLocationJson.encode(
        FuelLocationJson.fromCoords(lat, lon, acc, source = "exif"),
    )
}
