package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure merge planner for **any** live fuel partials — batch import, Quick Fill,
 * and **sync-sourced** rows (no filter on `location` / batch tags).
 *
 * **Cross-device product:** Device A writes odo-only; device B writes cost/vol;
 * after spreadsheet pull both rows sit in Room; [planMerge] pairs within
 * [MERGE_WINDOW_MS] (GPS optional — time-first; lat/long never required).
 *
 * **Cluster algorithm (documented):** per known `vehicleId` (>0), sort by `timestamp`
 * ascending (tie-break `id`). Greedy grow: start a cluster at seed; append next
 * row while `timestamp - seed.timestamp <= windowMs` **and** gap from previous
 * in cluster ≤ windowMs. (Seed-window + consecutive-gap both enforced.)
 *
 * **Window:** [MERGE_WINDOW_MS] = **15 minutes**. Two fills ~44 min apart must not
 * share a cluster.
 *
 * **Multi dash + multi pump (tight pairs):** when a greedy cluster contains ≥2
 * dash-like **and** ≥2 pump-like rows, [splitTightDashPumpPairs] matches each
 * dash↔pump by minimum |Δt| (each used once, |Δt| ≤ window), then
 * [mergeOneCluster] runs **per sub-cluster**.
 *
 * Unassigned pumps (`vehicleId == 0`) pair first by **same-stop location + time**
 * ([FuelStopMatch]) to a unique vehicle-known fill; else tank maxFill+slack then
 * nearest dash odo in window. Unpaired pumps stay out of clusters.
 *
 * **Dual pump same stop (silent):** ≥2 pump-amount rows + dash/odo anchor sharing
 * location within window → earlier pump (timestamp, id) is partial / non-full-fill;
 * later is full-fill candidate. Must not leave vehicleId=0 or phase-4 ASSIGN_UNKNOWN
 * when correlation is unique.
 *
 * Multi-pump: within [COST_VOL_REL_TOL] → re-shot; beyond + abs floor → sequence;
 * **never sum**. Location blob (coords+place): [FuelLocationJson.mergeBlobs] in mergeFields;
 * backfill when survivor empty (batch→non-batch).
 */
object FuelRowMergeEngine {

    /** Cluster / pair max gap — **15 minutes** (was 45; over-merged distinct fills). */
    const val MERGE_WINDOW_MS: Long = 15L * 60L * 1000L
    const val COST_VOL_REL_TOL: Double = 0.05
    const val COST_ABS_FLOOR: Double = 1.0
    /**
     * Tank / max-fill slack in **preferred volume unit** (same as [FuelEntry.gallons]).
     * Pump volume &gt; maxFill(vehicle) + this eliminates that vehicle for pairing.
     */
    const val TANK_SLACK_GAL: Double = 5.0
    /** Context window for unknown-vehicle neighbor lists (±) — independent of merge window. */
    const val UNKNOWN_CONTEXT_WINDOW_MS: Long = 2L * 60L * 60L * 1000L
    /**
     * Unreasonable odo gap: `Δodo > maxVol(vehicle) * mpg(vehicle) * ODO_GAP_FACTOR`.
     * No constant mpg fallback — skip gap rule if vehicle mpg unavailable.
     */
    const val ODO_GAP_FACTOR: Double = 3.0

    data class MergePlan(
        val updates: List<FuelEntry> = emptyList(),
        val inserts: List<FuelEntry> = emptyList(),
        val hardDeletes: List<FuelEntry> = emptyList(),
        val newPending: List<BatchPendingItem> = emptyList(),
    ) {
        fun isEmpty(): Boolean =
            updates.isEmpty() && inserts.isEmpty() && hardDeletes.isEmpty() && newPending.isEmpty()
    }

    fun planMerge(
        entries: List<FuelEntry>,
        windowMs: Long = MERGE_WINDOW_MS,
        /**
         * Live MERGE_EXEMPT member sets (fuel syncIds). If any set is a subset of
         * a sub-cluster's syncIds, that sub-cluster is left as-is (no absorb,
         * no CONFLICT_ODO pending).
         */
        mergeExemptSets: List<Set<String>> = emptyList(),
    ): MergePlan {
        val live = entries.filter { !it.deleted }
        if (live.isEmpty()) return MergePlan()

        val assigned = live.filter { it.vehicleId > 0 }
        val unassignedPumps = live.filter {
            it.vehicleId == BatchFuelImportCoordinator.UNASSIGNED_VEHICLE_ID && isPumpLike(it)
        }

        // Max fill volume per vehicle (tank estimate) — include all positive vols
        val maxFillByVehicle = live
            .filter { it.vehicleId > 0 && it.gallons > 0 }
            .groupBy { it.vehicleId }
            .mapValues { (_, rows) -> rows.maxOf { it.gallons } }

        // Pair unassigned pumps: tank elimination, then nearest dash odo in window
        val reassignedById = mutableMapOf<Long, FuelEntry>()
        for (pump in unassignedPumps) {
            val vehicleId = assignUnassignedPumpVehicle(
                pump = pump,
                assigned = assigned,
                maxFillByVehicle = maxFillByVehicle,
                activeVehicleIds = assigned.map { it.vehicleId }.toSet(),
                windowMs = windowMs,
            )
            if (vehicleId != null && vehicleId > 0) {
                reassignedById[pump.id] = pump.copy(vehicleId = vehicleId)
            }
        }

        val workingEntries = assigned + reassignedById.values
        val allUpdates = mutableListOf<FuelEntry>()
        val allDeletes = mutableListOf<FuelEntry>()
        val allPending = mutableListOf<BatchPendingItem>()

        for ((_, vehicleEntries) in workingEntries.groupBy { it.vehicleId }) {
            val sorted = vehicleEntries.sortedWith(compareBy({ it.timestamp }, { it.id }))
            val clusters = greedyClusters(sorted, windowMs)
            for (cluster in clusters) {
                if (cluster.size < 2) {
                    // Single-row cluster that was a reassigned vehicleId=0 pump: still persist vehicle
                    val only = cluster.singleOrNull() ?: continue
                    if (only.id in reassignedById) {
                        allUpdates += only
                    }
                    continue
                }
                val split = splitTightDashPumpPairs(cluster, windowMs)
                allPending += split.ambiguousPending
                for (sub in split.subClusters) {
                    if (sub.size < 2) {
                        val only = sub.singleOrNull() ?: continue
                        if (only.id in reassignedById) allUpdates += only
                        continue
                    }
                    val subSyncIds = sub.map { it.syncId }.filter { it.isNotBlank() }.toSet()
                    if (isClusterMergeExempt(subSyncIds, mergeExemptSets)) {
                        // User acked keep-separate / looks-correct — no absorb, no CONFLICT
                        continue
                    }
                    val plan = mergeOneCluster(sub)
                    allUpdates += plan.updates
                    allDeletes += plan.hardDeletes
                    allPending += plan.newPending
                }
            }
        }

        val deletesById = allDeletes.associateBy { it.id }
        val deleteIds = deletesById.keys
        val updatesById = allUpdates
            .filter { it.id !in deleteIds }
            .associateBy { it.id }
            .values
            .toList()

        return MergePlan(
            updates = updatesById,
            hardDeletes = deletesById.values.toList(),
            newPending = allPending,
        )
    }

    /**
     * Assign a vehicleId=0 pump:
     * 1. **Same location + time window** against vehicle-known fills (prefer odo/dash-like):
     *    unique vehicleId → assign; multiple vehicles → leave unassigned (no silent pick).
     * 2. Eliminate vehicles where pump vol &gt; maxFill + [TANK_SLACK_GAL]
     * 3. If exactly one vehicle remains among active → auto-assign that vehicle
     * 4. Else nearest in-window dash odo among remaining (or all if none eliminated)
     * 5. If zero remain after tank elimination → leave unassigned
     */
    internal fun assignUnassignedPumpVehicle(
        pump: FuelEntry,
        assigned: List<FuelEntry>,
        maxFillByVehicle: Map<Int, Double>,
        activeVehicleIds: Set<Int>,
        windowMs: Long = MERGE_WINDOW_MS,
        tankSlack: Double = TANK_SLACK_GAL,
    ): Int? {
        // 1) Place/time: unique vehicle at same stop
        val locPartners = assigned.filter {
            it.vehicleId > 0 &&
                abs(it.timestamp - pump.timestamp) <= windowMs &&
                FuelStopMatch.locationsMatch(pump, it)
        }
        if (locPartners.isNotEmpty()) {
            val vids = locPartners.map { it.vehicleId }.distinct()
            if (vids.size == 1) return vids.single()
            // Ambiguous multi-vehicle same stop — do not tank-guess past location conflict
            return null
        }

        val candidates = tankEligibleVehicles(
            pumpVol = pump.gallons,
            activeVehicleIds = activeVehicleIds,
            maxFillByVehicle = maxFillByVehicle,
            tankSlack = tankSlack,
        )
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.single()

        val partner = assigned
            .filter {
                it.vehicleId in candidates &&
                    hasPositiveOdo(it) &&
                    abs(it.timestamp - pump.timestamp) <= windowMs
            }
            .minByOrNull { abs(it.timestamp - pump.timestamp) }
        return partner?.vehicleId
    }

    /**
     * Vehicles not eliminated by tank max-fill rule.
     * No maxFill known → keep vehicle (cannot eliminate).
     */
    fun tankEligibleVehicles(
        pumpVol: Double,
        activeVehicleIds: Set<Int>,
        maxFillByVehicle: Map<Int, Double>,
        tankSlack: Double = TANK_SLACK_GAL,
    ): Set<Int> {
        if (pumpVol <= 0 || activeVehicleIds.isEmpty()) return activeVehicleIds
        return activeVehicleIds.filter { vid ->
            val maxFill = maxFillByVehicle[vid]
            maxFill == null || pumpVol <= maxFill + tankSlack
        }.toSet()
    }

    /** Greedy seed-window + consecutive-gap clusters. */
    internal fun greedyClusters(sorted: List<FuelEntry>, windowMs: Long): List<List<FuelEntry>> {
        if (sorted.isEmpty()) return emptyList()
        val out = mutableListOf<MutableList<FuelEntry>>()
        var cur = mutableListOf(sorted[0])
        var seedTs = sorted[0].timestamp
        for (i in 1 until sorted.size) {
            val e = sorted[i]
            val gapPrev = e.timestamp - cur.last().timestamp
            val fromSeed = e.timestamp - seedTs
            if (gapPrev <= windowMs && fromSeed <= windowMs) {
                cur.add(e)
            } else {
                out.add(cur)
                cur = mutableListOf(e)
                seedTs = e.timestamp
            }
        }
        out.add(cur)
        return out
    }

    data class TightPairSplit(
        val subClusters: List<List<FuelEntry>>,
        val ambiguousPending: List<BatchPendingItem> = emptyList(),
    )

    /**
     * When a greedy cluster has **≥2 dash-like** and **≥2 pump-like** rows, split into
     * tight time pairs so distinct fills never cross-merge into one CONFLICT_ODO /
     * `pump`+`pump_2` row.
     *
     * **Pairing:** greedy match dash↔pump by ascending |Δt|, each row used at most
     * once, only if `|Δt| ≤ windowMs`. Leftovers attach to nearest pair if within
     * window, else singleton sub-clusters. Near-ties (two pumps within 1 min of same
     * dash) still take nearest but enqueue [BatchPendingKind.AMBIGUOUS_MULTI_PUMP].
     */
    internal fun splitTightDashPumpPairs(
        cluster: List<FuelEntry>,
        windowMs: Long = MERGE_WINDOW_MS,
    ): TightPairSplit {
        val dashes = cluster.filter { isDashLike(it) }
        val pumps = cluster.filter { isPumpLikeForSplit(it) }
        if (dashes.size < 2 || pumps.size < 2) {
            return TightPairSplit(listOf(cluster))
        }

        data class Edge(val dash: FuelEntry, val pump: FuelEntry, val dt: Long)

        val edges = mutableListOf<Edge>()
        for (d in dashes) {
            for (p in pumps) {
                val dt = abs(d.timestamp - p.timestamp)
                if (dt <= windowMs) edges.add(Edge(d, p, dt))
            }
        }
        if (edges.isEmpty()) return TightPairSplit(listOf(cluster))
        edges.sortWith(compareBy({ it.dt }, { it.dash.id }, { it.pump.id }))

        val usedDash = mutableSetOf<Long>()
        val usedPump = mutableSetOf<Long>()
        val pairs = mutableListOf<MutableList<FuelEntry>>()
        val ambiguousPending = mutableListOf<BatchPendingItem>()

        for (e in edges) {
            if (e.dash.id in usedDash || e.pump.id in usedPump) continue
            val rivals = edges.filter {
                it.dash.id == e.dash.id &&
                    it.pump.id != e.pump.id &&
                    it.pump.id !in usedPump &&
                    abs(it.dt - e.dt) <= 60_000L
            }
            if (rivals.isNotEmpty()) {
                ambiguousPending += BatchPendingItem(
                    kind = BatchPendingKind.AMBIGUOUS_MULTI_PUMP,
                    message = "Ambiguous dash–pump pairing near ts=${e.dash.timestamp} " +
                        "(Δt=${e.dt}ms rivals=${rivals.size})",
                    photoPath = FuelPhotoJson.parse(e.dash.photoUrl).firstOrNull()?.uri
                        ?: FuelPhotoJson.parse(e.pump.photoUrl).firstOrNull()?.uri,
                    timestampMs = e.dash.timestamp,
                    fuelEntryId = e.dash.id,
                    suggestedVehicleId = e.dash.vehicleId.takeIf { it > 0 },
                    extra = mapOf(
                        "entryIds" to listOf(e.dash.id, e.pump.id).joinToString(","),
                        "entrySyncIds" to listOf(e.dash.syncId, e.pump.syncId)
                            .filter { it.isNotBlank() }
                            .joinToString(","),
                        "memberSyncIds" to listOf(e.dash.syncId, e.pump.syncId)
                            .filter { it.isNotBlank() }
                            .sorted()
                            .joinToString(","),
                        "photoPaths" to allPhotoUris(listOf(e.dash, e.pump)).joinToString("|"),
                    ),
                )
            }
            usedDash.add(e.dash.id)
            usedPump.add(e.pump.id)
            pairs.add(mutableListOf(e.dash, e.pump))
        }

        val pairedIds = pairs.flatten().map { it.id }.toSet()
        val leftovers = cluster.filter { it.id !in pairedIds }
        for (left in leftovers) {
            val host = pairs.minByOrNull { pair ->
                abs(pair.map { it.timestamp }.average() - left.timestamp)
            }
            if (host != null) {
                val hostTs = host.map { it.timestamp }.average().toLong()
                if (abs(left.timestamp - hostTs) <= windowMs) {
                    host.add(left)
                    continue
                }
            }
            pairs.add(mutableListOf(left))
        }

        return TightPairSplit(
            subClusters = pairs.map { it.sortedWith(compareBy({ it.timestamp }, { it.id })) },
            ambiguousPending = ambiguousPending,
        )
    }

    private fun isCompleteFull(e: FuelEntry): Boolean =
        e.odometer > 0 && e.cost > 0 && e.gallons > 0 &&
            !e.isPartialFill && !e.economyIgnored

    private fun conflictOdoPending(
        sorted: List<FuelEntry>,
        positiveOdos: List<Int>,
        sameOdoFulls: Boolean,
    ): MergePlan {
        val photos = allPhotoUris(sorted)
        val msg = if (sameOdoFulls) {
            "Two complete fills same odo=${positiveOdos.firstOrNull()} " +
                "vehicle=${sorted.first().vehicleId} " +
                "ts≈${sorted.minOf { it.timestamp }}–${sorted.maxOf { it.timestamp}} " +
                "(will not auto-merge; Keep both / Looks correct to ack)"
        } else {
            "Conflicting odometers ${positiveOdos.joinToString()} " +
                "in cluster vehicle=${sorted.first().vehicleId} " +
                "ts≈${sorted.minOf { it.timestamp }}–${sorted.maxOf { it.timestamp }}"
        }
        return MergePlan(
            newPending = listOf(
                BatchPendingItem(
                    kind = BatchPendingKind.CONFLICT_ODO,
                    message = msg,
                    photoPath = photos.firstOrNull(),
                    durablePhotoPath = photos.firstOrNull(),
                    timestampMs = sorted.maxOf { it.timestamp },
                    suggestedVehicleId = sorted.first().vehicleId,
                    fuelEntryId = sorted.firstOrNull()?.id,
                    extra = mapOf(
                        "entryIds" to sorted.map { it.id }.joinToString(","),
                        "entrySyncIds" to sorted.map { it.syncId }.filter { it.isNotBlank() }
                            .joinToString(","),
                        "memberSyncIds" to sorted.map { it.syncId }.filter { it.isNotBlank() }
                            .sorted()
                            .joinToString(","),
                        "odos" to positiveOdos.joinToString(","),
                        "photoPaths" to photos.joinToString("|"),
                        "sameOdoCompleteFulls" to sameOdoFulls.toString(),
                    ),
                ),
            ),
        )
    }

    private fun mergeOneCluster(cluster: List<FuelEntry>): MergePlan {
        val sorted = cluster.sortedWith(compareBy({ it.timestamp }, { it.id }))

        val pumpLike = sorted.filter { isPumpAmountRow(it) }
        val dashLike = sorted.filter { isDashLike(it) }

        // Dual+ pumps at same stop as a dash/odo anchor → earlier partial, later full (sequence).
        // Prefer this over CONFLICT_ODO when correlation is clear by location/time.
        if (pumpLike.size >= 2 && isSameStopMultiPump(sorted, pumpLike, dashLike)) {
            val byTime = pumpLike.sortedWith(compareBy({ it.timestamp }, { it.id }))
            val anyReshot = byTime.indices.any { i ->
                (i + 1 until byTime.size).any { j -> amountsWithinTol(byTime[i], byTime[j]) }
            }
            if (!anyReshot) {
                return mergeSequenceCluster(sorted, byTime, markEarlierPartial = true)
            }
        }

        // ≥2 complete fulls: never silent-absorb (same or distinct odo)
        val completeFulls = sorted.filter { isCompleteFull(it) }
        if (completeFulls.size >= 2) {
            val fullOdos = completeFulls.map { it.odometer }.distinct()
            if (fullOdos.size > 1) {
                // Distinct odos among complete fulls → silent keep-both
                return MergePlan()
            }
            // Same odo (or single odo value) among ≥2 complete fulls → CONFLICT, no absorb
            return conflictOdoPending(
                sorted = sorted,
                positiveOdos = fullOdos,
                sameOdoFulls = true,
            )
        }

        val positiveOdos = sorted.map { it.odometer }.filter { it > 0 }.distinct()
        if (positiveOdos.size > 1) {
            // Partial/incomplete multi-odo conflict (not two complete fulls)
            return conflictOdoPending(
                sorted = sorted,
                positiveOdos = positiveOdos,
                sameOdoFulls = false,
            )
        }

        if (pumpLike.size >= 2 && isAmountSequence(pumpLike)) {
            // If any pair is re-shot (within tol), field-complete instead
            val byTime = pumpLike.sortedWith(compareBy({ it.timestamp }, { it.id }))
            val anyReshot = byTime.indices.any { i ->
                (i + 1 until byTime.size).any { j -> amountsWithinTol(byTime[i], byTime[j]) }
            }
            if (!anyReshot) {
                return mergeSequenceCluster(sorted, byTime, markEarlierPartial = true)
            }
        }

        return mergeFieldComplete(sorted)
    }

    /**
     * True when ≥2 pump-amount rows share a stop with each other and/or a dash-like
     * anchor (place match or geo), within the cluster already time-windowed.
     */
    private fun isSameStopMultiPump(
        sorted: List<FuelEntry>,
        pumpLike: List<FuelEntry>,
        dashLike: List<FuelEntry>,
    ): Boolean {
        if (pumpLike.size < 2) return false
        val anchors = if (dashLike.isNotEmpty()) dashLike else sorted.filter { hasPositiveOdo(it) }
        if (anchors.isEmpty()) {
            // All pumps: any pair same location
            return pumpLike.indices.any { i ->
                (i + 1 until pumpLike.size).any { j ->
                    FuelStopMatch.locationsMatch(pumpLike[i], pumpLike[j])
                }
            }
        }
        return pumpLike.all { p ->
            anchors.any { a -> FuelStopMatch.locationsMatch(p, a) } ||
                pumpLike.any { o -> o.id != p.id && FuelStopMatch.locationsMatch(p, o) }
        }
    }

    /**
     * Sequence: keep each distinct-amount pump as its own row (never sum).
     * Earlier rows: non-full-fill / explicit partial when complete; last gets odo if a
     * pure odo donor exists (full-fill candidate).
     *
     * @param markEarlierPartial when true (same-stop dual pump), set [FuelEntry.isPartialFill]
     * on earlier complete pumps so they never act as full-fill anchors.
     */
    private fun mergeSequenceCluster(
        sorted: List<FuelEntry>,
        pumpLike: List<FuelEntry>,
        markEarlierPartial: Boolean = false,
    ): MergePlan {
        val odoDonor = sorted
            .filter { hasPositiveOdo(it) && !isPumpAmountRow(it) }
            .maxByOrNull { it.timestamp }
            ?: sorted.filter { hasPositiveOdo(it) }.maxByOrNull { it.timestamp }

        val updates = mutableListOf<FuelEntry>()
        val deletes = mutableListOf<FuelEntry>()
        val usedOdoIds = mutableSetOf<Long>()

        pumpLike.forEachIndexed { idx, p ->
            var row = p
            val isLast = idx == pumpLike.lastIndex
            if (isLast && odoDonor != null && odoDonor.id != p.id) {
                row = mergeFields(p, odoDonor, preferLatestTs = true)
                deletes.add(odoDonor)
                usedOdoIds.add(odoDonor.id)
            }
            if (!isLast && markEarlierPartial) {
                // Earlier at same stop: partial if complete fields, else leave incomplete (not full fill)
                val complete = hasPositiveOdo(row) && hasCost(row) && hasVol(row)
                row = if (complete) {
                    row.copy(isPartialFill = true)
                } else {
                    finalizePartialFlag(row)
                }
            } else {
                row = finalizePartialFlag(row)
            }
            updates.add(row)
        }

        // Absorb other pure odo companions (same cluster) into last if not already used
        val keepIds = updates.map { it.id }.toSet()
        for (e in sorted) {
            if (e.id in keepIds || e.id in usedOdoIds) continue
            if (isPumpAmountRow(e)) continue
            if (hasPositiveOdo(e) && !hasCost(e) && !hasVol(e) && updates.isNotEmpty()) {
                val lastIdx = updates.lastIndex
                updates[lastIdx] = finalizePartialFlag(
                    mergeFields(updates[lastIdx], e, preferLatestTs = true),
                )
                deletes.add(e)
                usedOdoIds.add(e.id)
            }
        }

        // Photo-stem duplicates among leftovers
        val leftover = sorted.filter {
            it.id !in keepIds && it.id !in deletes.map { d -> d.id }
        }
        val dups = absorbPhotoDuplicates(leftover)

        return MergePlan(
            updates = updates + dups.updates,
            hardDeletes = (deletes + dups.hardDeletes).distinctBy { it.id },
            newPending = dups.newPending,
        )
    }

    private fun mergeFieldComplete(sorted: List<FuelEntry>): MergePlan {
        val deduped = absorbPhotoDuplicates(sorted)
        val deleteIds0 = deduped.hardDeletes.map { it.id }.toSet()
        val remaining = sorted
            .filter { it.id !in deleteIds0 }
            .map { e -> deduped.updates.find { it.id == e.id } ?: e }

        if (remaining.size < 2) {
            return deduped
        }

        val base = remaining.maxWithOrNull(
            compareBy<FuelEntry> { scoreStrength(it) }
                .thenBy { it.timestamp }
                .thenBy { it.id },
        )!!

        var survivor = base
        val deletes = mutableListOf<FuelEntry>()
        for (other in remaining) {
            if (other.id == base.id) continue
            survivor = mergeFields(survivor, other, preferLatestTs = true)
            deletes.add(other)
        }
        survivor = finalizePartialFlag(survivor)

        return MergePlan(
            updates = listOf(survivor),
            hardDeletes = (deletes + deduped.hardDeletes).distinctBy { it.id },
            newPending = deduped.newPending,
        )
    }

    private fun absorbPhotoDuplicates(entries: List<FuelEntry>): MergePlan {
        if (entries.size < 2) return MergePlan()
        fun stem(e: FuelEntry): String {
            // Prefer notes (new batch tags); fall back to location (legacy).
            val tag = listOf(e.notes, e.location).firstOrNull { !it.isNullOrBlank() } ?: ""
            return tag.substringAfter("batch_import_dash:", "")
                .ifBlank { tag.substringAfter("batch_import_dash_blank:", "") }
                .ifBlank { tag.substringAfter("batch_import_pump:", "") }
                .ifBlank {
                    FuelPhotoJson.parse(e.photoUrl).firstOrNull()?.uri?.substringAfterLast('/')
                        ?: ""
                }
        }
        val groups = entries.groupBy { stem(it) }
            .filter { it.key.isNotBlank() && it.value.size > 1 }
        if (groups.isEmpty()) return MergePlan()

        val updates = mutableListOf<FuelEntry>()
        val deletes = mutableListOf<FuelEntry>()
        for ((_, group) in groups) {
            val ranked = group.sortedWith(
                compareByDescending<FuelEntry> { scoreStrength(it) }
                    .thenByDescending { it.id },
            )
            var s = ranked.first()
            for (o in ranked.drop(1)) {
                s = mergeFields(s, o, preferLatestTs = true)
                deletes.add(o)
            }
            updates.add(finalizePartialFlag(s))
        }
        return MergePlan(updates = updates, hardDeletes = deletes)
    }

    internal fun mergeFields(a: FuelEntry, b: FuelEntry, preferLatestTs: Boolean): FuelEntry {
        val later = if (a.timestamp >= b.timestamp) a else b
        val earlier = if (a.timestamp >= b.timestamp) b else a
        val ts = if (preferLatestTs) later.timestamp else max(a.timestamp, b.timestamp)

        val odo = when {
            a.odometer > 0 && b.odometer > 0 ->
                if (a.odometer == b.odometer) a.odometer else a.odometer // conflict path avoids this
            a.odometer > 0 -> a.odometer
            else -> b.odometer
        }
        val costF = when {
            later.cost > 0 -> later.cost
            earlier.cost > 0 -> earlier.cost
            else -> 0.0
        }
        val galF = when {
            later.gallons > 0 -> later.gallons
            earlier.gallons > 0 -> earlier.gallons
            else -> 0.0
        }
        val vehicleId = when {
            a.vehicleId > 0 -> a.vehicleId
            b.vehicleId > 0 -> b.vehicleId
            else -> 0
        }
        val currency = later.currency.ifBlank { earlier.currency }.ifBlank { "USD" }
        val photo = FuelPhotoJson.unionPhotos(a.photoUrl, b.photoUrl)
        // Blob merge: place/coords LWW + backfill when one side empty (batch→non-batch)
        val loc = FuelLocationJson.mergeBlobs(
            a.location,
            b.location,
            updatedAtA = a.updatedAt.takeIf { it > 0 } ?: a.timestamp,
            updatedAtB = b.updatedAt.takeIf { it > 0 } ?: b.timestamp,
        ) ?: preferLocation(a.location, b.location)
        val notes = preferNotes(a.notes, b.notes)
        val idKeep = later.id
        val complete = (if (odo > 0) odo else 0) > 0 && costF > 0 && galF > 0
        // Explicit partial only when complete and either side already had user override
        val preservePartial = complete && (a.isPartialFill || b.isPartialFill)
        return later.copy(
            id = idKeep,
            vehicleId = vehicleId,
            odometer = if (odo > 0) odo else 0,
            gallons = galF,
            cost = costF,
            currency = currency,
            timestamp = ts,
            photoUrl = photo,
            location = loc,
            notes = notes,
            isPartialFill = preservePartial,
        )
    }

    /**
     * Prefer real station place (JSON / free text) over blank; batch tags score lower
     * so a station name wins when present. Legacy batch tags may still live here.
     */
    private fun preferLocation(a: String?, b: String?): String? {
        fun score(s: String?): Int {
            if (s.isNullOrBlank()) return 0
            if (s.startsWith("batch_")) return 1
            return 3
        }
        return when {
            score(a) > score(b) -> a
            score(b) > score(a) -> b
            else -> a ?: b
        }
    }

    /**
     * Prefer non-blank notes; keep batch tags when merging partials (do not drop provenance).
     * When both non-blank and differ, prefer longer (more informative) side.
     */
    private fun preferNotes(a: String?, b: String?): String? {
        val aa = a?.takeIf { it.isNotBlank() }
        val bb = b?.takeIf { it.isNotBlank() }
        return when {
            aa == null -> bb
            bb == null -> aa
            aa == bb -> aa
            else -> if (aa.length >= bb.length) aa else bb
        }
    }

    /** True if [notes] or legacy [location] contains the batch token (case-sensitive). */
    private fun hasBatchToken(e: FuelEntry, token: String): Boolean =
        e.notes?.contains(token) == true || e.location?.contains(token) == true

    /**
     * Partial flag is **explicit only**. Incomplete → force false.
     * Complete → preserve existing user override (never invent true).
     */
    private fun finalizePartialFlag(e: FuelEntry): FuelEntry {
        val complete = e.odometer > 0 && e.cost > 0 && e.gallons > 0
        return e.copy(isPartialFill = complete && e.isPartialFill)
    }

    private fun scoreStrength(e: FuelEntry): Int {
        var s = 0
        if (e.odometer > 0) s += 4
        if (e.cost > 0) s += 2
        if (e.gallons > 0) s += 2
        if (e.vehicleId > 0) s += 1
        if (!e.isPartialFill) s += 3
        return s
    }

    private fun allPhotoUris(entries: List<FuelEntry>): List<String> {
        val seen = LinkedHashSet<String>()
        for (e in entries) {
            for (p in FuelPhotoJson.parse(e.photoUrl)) {
                if (p.uri.isNotBlank()) seen.add(p.uri)
            }
        }
        return seen.toList()
    }

    private fun hasPositiveOdo(e: FuelEntry) = e.odometer > 0
    private fun hasCost(e: FuelEntry) = e.cost > 0
    private fun hasVol(e: FuelEntry) = e.gallons > 0

    /**
     * True when any exempt set is non-empty and entirely contained in [clusterSyncIds].
     * Empty cluster syncIds never match (cannot identify members).
     */
    internal fun isClusterMergeExempt(
        clusterSyncIds: Set<String>,
        mergeExemptSets: List<Set<String>>,
    ): Boolean {
        if (clusterSyncIds.isEmpty() || mergeExemptSets.isEmpty()) return false
        return mergeExemptSets.any { exempt ->
            exempt.isNotEmpty() && exempt.all { it in clusterSyncIds }
        }
    }

    /**
     * True if there exists at least one odo-only + pump-only pair within [windowMs]
     * that could merge (same vehicle or unassigned pump). Used for post-sync CTA.
     * Does not mutate rows.
     */
    fun hasUnmatchedPartials(
        entries: List<FuelEntry>,
        windowMs: Long = MERGE_WINDOW_MS,
    ): Boolean {
        val live = entries.filter { !it.deleted }
        val odoOnly = live.filter {
            hasPositiveOdo(it) && !hasCost(it) && !hasVol(it)
        }
        val pumpOnly = live.filter {
            (hasCost(it) || hasVol(it)) && !hasPositiveOdo(it)
        }
        if (odoOnly.isEmpty() || pumpOnly.isEmpty()) return false
        for (o in odoOnly) {
            for (p in pumpOnly) {
                if (abs(o.timestamp - p.timestamp) > windowMs) continue
                val sameVehicle = o.vehicleId > 0 && p.vehicleId == o.vehicleId
                val unassignedPump = o.vehicleId > 0 && p.vehicleId == 0
                val unassignedOdo = p.vehicleId > 0 && o.vehicleId == 0
                if (sameVehicle || unassignedPump || unassignedOdo) return true
            }
        }
        return false
    }

    /** Pump-amount row: has cost or volume (may or may not have odo after sequence attach). */
    private fun isPumpAmountRow(e: FuelEntry): Boolean =
        hasCost(e) || hasVol(e) || hasBatchToken(e, "batch_import_pump")

    /**
     * Pump-like for pairing vehicleId=0: cost/vol without odo, or batch pump tag
     * (notes preferred; location for legacy rows).
     */
    private fun isPumpLike(e: FuelEntry): Boolean {
        if (hasBatchToken(e, "batch_import_pump")) return true
        return (hasCost(e) || hasVol(e)) && !hasPositiveOdo(e)
    }

    /** Dash-like for tight-pair split: odo-only (or batch dash tag in notes/location). */
    internal fun isDashLike(e: FuelEntry): Boolean {
        if (hasBatchToken(e, "batch_import_dash")) return true
        val hasDashPhoto = FuelPhotoJson.parse(e.photoUrl).any {
            it.tag == "dash" || it.tag.startsWith("dash")
        }
        if (hasDashPhoto && hasPositiveOdo(e) && !hasCost(e) && !hasVol(e)) return true
        return hasPositiveOdo(e) && !hasCost(e) && !hasVol(e)
    }

    /** Pump-like for tight-pair split: cost/vol without odo (or batch pump). */
    internal fun isPumpLikeForSplit(e: FuelEntry): Boolean {
        if (hasBatchToken(e, "batch_import_pump")) return true
        val hasPumpPhoto = FuelPhotoJson.parse(e.photoUrl).any { it.tag.startsWith("pump") }
        if (hasPumpPhoto && (hasCost(e) || hasVol(e)) && !hasPositiveOdo(e)) return true
        return (hasCost(e) || hasVol(e)) && !hasPositiveOdo(e)
    }

    private fun amountsWithinTol(a: FuelEntry, b: FuelEntry): Boolean {
        val ca = a.cost
        val cb = b.cost
        val va = a.gallons
        val vb = b.gallons
        val costOk = when {
            ca <= 0 || cb <= 0 -> true
            else -> {
                val rel = abs(ca - cb) / max(ca, cb)
                rel <= COST_VOL_REL_TOL || abs(ca - cb) <= COST_ABS_FLOOR
            }
        }
        val volOk = when {
            va <= 0 || vb <= 0 -> true
            else -> {
                val rel = abs(va - vb) / max(va, vb)
                rel <= COST_VOL_REL_TOL
            }
        }
        return costOk && volOk
    }

    private fun isAmountSequence(pumps: List<FuelEntry>): Boolean {
        val sorted = pumps.sortedBy { it.timestamp }
        for (i in 0 until sorted.size - 1) {
            for (j in i + 1 until sorted.size) {
                val a = sorted[i]
                val b = sorted[j]
                if (a.cost > 0 && b.cost > 0) {
                    val rel = abs(a.cost - b.cost) / max(a.cost, b.cost)
                    if (rel > COST_VOL_REL_TOL && abs(a.cost - b.cost) > COST_ABS_FLOOR) {
                        return true
                    }
                } else if (a.gallons > 0 && b.gallons > 0) {
                    val rel = abs(a.gallons - b.gallons) / max(a.gallons, b.gallons)
                    if (rel > COST_VOL_REL_TOL) return true
                }
            }
        }
        return false
    }
}
