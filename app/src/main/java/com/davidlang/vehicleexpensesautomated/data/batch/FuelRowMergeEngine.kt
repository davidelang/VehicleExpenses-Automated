package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import kotlin.math.abs
import kotlin.math.max

/**
 * Stage B pure merge planner for batch fuel partials.
 *
 * **Cluster algorithm (documented):** per known `vehicleId` (>0), sort by `timestamp`
 * ascending (tie-break `id`). Greedy grow: start a cluster at seed; append next
 * row while `timestamp - seed.timestamp <= windowMs` **and** gap from previous
 * in cluster ≤ windowMs. (Seed-window + consecutive-gap both enforced.)
 *
 * Unassigned pumps (`vehicleId == 0`) are first **paired** to the nearest dash odo
 * row of a known vehicle within the window; then that vehicle is applied. Unpaired
 * pumps stay out of vehicle clusters.
 *
 * Multi-pump: amounts within [COST_VOL_REL_TOL] (and not over abs floor for cost)
 * → re-shot (one survivor). Beyond tol with abs > [COST_ABS_FLOOR] → sequence
 * (partial then full-capable); **never sum**.
 */
object FuelRowMergeEngine {

    const val MERGE_WINDOW_MS: Long = 45L * 60L * 1000L
    const val COST_VOL_REL_TOL: Double = 0.05
    const val COST_ABS_FLOOR: Double = 1.0
    /**
     * Tank / max-fill slack in **preferred volume unit** (same as [FuelEntry.gallons]).
     * Pump volume &gt; maxFill(vehicle) + this eliminates that vehicle for pairing.
     * Spec: five US gallons; if preferred unit is liters, convert at call sites that know prefs.
     * Default treats stored volumes as gallons-compatible (user preferred unit).
     */
    const val TANK_SLACK_GAL: Double = 5.0
    /** Context window for unknown-vehicle neighbor lists (±). */
    const val UNKNOWN_CONTEXT_WINDOW_MS: Long = 2L * 60L * 60L * 1000L

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
                val plan = mergeOneCluster(cluster)
                allUpdates += plan.updates
                allDeletes += plan.hardDeletes
                allPending += plan.newPending
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
     * 1. Eliminate vehicles where pump vol &gt; maxFill + [TANK_SLACK_GAL]
     * 2. If exactly one vehicle remains among active → auto-assign that vehicle
     * 3. Else nearest in-window dash odo among remaining (or all if none eliminated)
     * 4. If zero remain after tank elimination → leave unassigned
     */
    internal fun assignUnassignedPumpVehicle(
        pump: FuelEntry,
        assigned: List<FuelEntry>,
        maxFillByVehicle: Map<Int, Double>,
        activeVehicleIds: Set<Int>,
        windowMs: Long = MERGE_WINDOW_MS,
        tankSlack: Double = TANK_SLACK_GAL,
    ): Int? {
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

    private fun mergeOneCluster(cluster: List<FuelEntry>): MergePlan {
        val sorted = cluster.sortedWith(compareBy({ it.timestamp }, { it.id }))

        val positiveOdos = sorted.map { it.odometer }.filter { it > 0 }.distinct()
        if (positiveOdos.size > 1) {
            val photos = allPhotoUris(sorted)
            return MergePlan(
                newPending = listOf(
                    BatchPendingItem(
                        kind = BatchPendingKind.CONFLICT_ODO,
                        message = "Conflicting odometers ${positiveOdos.joinToString()} " +
                            "in cluster vehicle=${sorted.first().vehicleId} " +
                            "ts≈${sorted.minOf { it.timestamp }}–${sorted.maxOf { it.timestamp }}",
                        photoPath = photos.firstOrNull(),
                        durablePhotoPath = photos.firstOrNull(),
                        timestampMs = sorted.maxOf { it.timestamp },
                        suggestedVehicleId = sorted.first().vehicleId,
                        fuelEntryId = sorted.firstOrNull()?.id,
                        extra = mapOf(
                            "entryIds" to sorted.map { it.id }.joinToString(","),
                            "odos" to positiveOdos.joinToString(","),
                            "photoPaths" to photos.joinToString("|"),
                        ),
                    ),
                ),
            )
        }

        val pumpLike = sorted.filter { isPumpAmountRow(it) }
        if (pumpLike.size >= 2 && isAmountSequence(pumpLike)) {
            // If any pair is re-shot (within tol), field-complete instead
            val byTime = pumpLike.sortedWith(compareBy({ it.timestamp }, { it.id }))
            val anyReshot = byTime.indices.any { i ->
                (i + 1 until byTime.size).any { j -> amountsWithinTol(byTime[i], byTime[j]) }
            }
            if (!anyReshot) {
                return mergeSequenceCluster(sorted, byTime)
            }
        }

        return mergeFieldComplete(sorted)
    }

    /**
     * Sequence: keep each distinct-amount pump as its own row (never sum).
     * Earlier rows stay partial; last gets odo if a pure odo donor exists.
     */
    private fun mergeSequenceCluster(
        sorted: List<FuelEntry>,
        pumpLike: List<FuelEntry>,
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
            row = if (!isLast) {
                finalizePartialFlag(row.copy(isPartialFill = true))
            } else {
                finalizePartialFlag(row)
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
            val loc = e.location ?: ""
            return loc.substringAfter("batch_import_dash:", "")
                .ifBlank { loc.substringAfter("batch_import_dash_blank:", "") }
                .ifBlank { loc.substringAfter("batch_import_pump:", "") }
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
        val lat = a.latitude ?: b.latitude
        val lon = a.longitude ?: b.longitude
        val loc = preferLocation(a.location, b.location)
        val idKeep = later.id
        return later.copy(
            id = idKeep,
            vehicleId = vehicleId,
            odometer = if (odo > 0) odo else 0,
            gallons = galF,
            cost = costF,
            currency = currency,
            timestamp = ts,
            photoUrl = photo,
            latitude = lat,
            longitude = lon,
            location = loc,
            isPartialFill = true,
        )
    }

    private fun preferLocation(a: String?, b: String?): String? {
        fun score(s: String?): Int {
            if (s.isNullOrBlank()) return 0
            if (!s.startsWith("batch_import")) return 3
            return 1
        }
        return when {
            score(a) > score(b) -> a
            score(b) > score(a) -> b
            else -> a ?: b
        }
    }

    private fun finalizePartialFlag(e: FuelEntry): FuelEntry {
        val full = e.vehicleId > 0 && e.odometer > 0 && e.cost > 0 && e.gallons > 0
        return e.copy(isPartialFill = !full)
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

    /** Pump-amount row: has cost or volume (may or may not have odo after sequence attach). */
    private fun isPumpAmountRow(e: FuelEntry): Boolean =
        hasCost(e) || hasVol(e) || (e.location?.contains("batch_import_pump") == true)

    /**
     * Pump-like for pairing vehicleId=0: cost/vol without odo, or batch pump location.
     */
    private fun isPumpLike(e: FuelEntry): Boolean {
        if (e.location?.contains("batch_import_pump") == true) return true
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
