package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry

/**
 * Detect time-vs-odometer order mismatches and compute reorder strategies.
 *
 * Strategy **A** (preferred): permute existing timestamps onto odo-sorted order
 * (does not invent times). Strategies **B** / **C** act on reverse steps when
 * walking chronological order (odo decreases).
 *
 * Anchors = live non-deleted rows with [FuelEntry.odometer] > 0.
 * Odo≤0 rows are left untouched (not sort anchors).
 */
object FuelOdoReorder {

    enum class Strategy {
        /** A: assign time-sorted timestamps to odo-sorted rows. */
        PERMUTE_TIMESTAMPS,
        /** B: set economyIgnored on later-by-time reverse offenders. */
        ECONOMY_IGNORE,
        /** C: soft-delete later-by-time reverse offenders. */
        DELETE_OFFENDERS,
    }

    data class ReverseStep(
        val vehicleId: Int,
        /** Earlier timestamp; higher odo. */
        val earlier: FuelEntry,
        /** Later timestamp; lower odo (offender for B/C). */
        val later: FuelEntry,
    )

    data class VehicleDisorder(
        val vehicleId: Int,
        val anchorCount: Int,
        val reverseSteps: List<ReverseStep>,
        /** True when time order of anchors ≠ odo order. */
        val needsTimestampPermute: Boolean,
    ) {
        val hasWork: Boolean
            get() = reverseSteps.isNotEmpty() || needsTimestampPermute
    }

    fun anchorsForVehicle(live: List<FuelEntry>, vehicleId: Int): List<FuelEntry> =
        live.filter { !it.deleted && it.vehicleId == vehicleId && it.odometer > 0 }

    fun analyzeVehicle(anchors: List<FuelEntry>): VehicleDisorder {
        if (anchors.isEmpty()) {
            val vid = 0
            return VehicleDisorder(vid, 0, emptyList(), false)
        }
        val vehicleId = anchors.first().vehicleId
        val byTime = anchors.sortedWith(compareBy({ it.timestamp }, { it.id }))
        val byOdo = anchors.sortedWith(compareBy({ it.odometer }, { it.id }))
        val needsPermute = byTime.map { it.id } != byOdo.map { it.id }
        val steps = mutableListOf<ReverseStep>()
        for (i in 1 until byTime.size) {
            val prev = byTime[i - 1]
            val cur = byTime[i]
            if (cur.odometer < prev.odometer) {
                steps += ReverseStep(vehicleId, earlier = prev, later = cur)
            }
        }
        return VehicleDisorder(
            vehicleId = vehicleId,
            anchorCount = anchors.size,
            reverseSteps = steps,
            needsTimestampPermute = needsPermute,
        )
    }

    fun analyzeAll(live: List<FuelEntry>): List<VehicleDisorder> {
        val ids = live.filter { !it.deleted && it.odometer > 0 }
            .map { it.vehicleId }
            .distinct()
            .sorted()
        return ids.map { vid ->
            analyzeVehicle(anchorsForVehicle(live, vid))
        }.filter { it.anchorCount >= 2 }
    }

    /**
     * Reassign the sorted-by-time timestamps onto sorted-by-odo rows (stable id ties).
     * Returns updated entry copies (timestamp only); odo/cost/vol unchanged.
     * Empty when already in odo order.
     */
    fun permuteTimestampsByOdo(anchors: List<FuelEntry>): List<FuelEntry> {
        if (anchors.size < 2) return emptyList()
        val byTime = anchors.sortedWith(compareBy({ it.timestamp }, { it.id }))
        val byOdo = anchors.sortedWith(compareBy({ it.odometer }, { it.id }))
        if (byTime.map { it.id } == byOdo.map { it.id }) return emptyList()
        val times = byTime.map { it.timestamp }
        return byOdo.mapIndexedNotNull { i, e ->
            val nt = times[i]
            if (e.timestamp != nt) e.copy(timestamp = nt) else null
        }
    }

    fun eligibleForReorderGate(
        stagePhase: Int,
        pending: List<BatchPendingItem>,
        disorders: List<VehicleDisorder>,
    ): Boolean {
        val hasOdoPending = pending.any {
            it.kind == BatchPendingKind.ODO_SUSPECT ||
                it.kind == BatchPendingKind.CONFLICT_ODO
        }
        // Phase 1–2 with remaining odo questions: hide. After odo cleared or phase > 2: show.
        val phaseOk = stagePhase > StageCPhase.COMPLEX_ODO.number ||
            (stagePhase >= StageCPhase.COMPLEX_ODO.number && !hasOdoPending)
        if (!phaseOk) return false
        return disorders.any { it.anchorCount >= 2 }
    }
}
