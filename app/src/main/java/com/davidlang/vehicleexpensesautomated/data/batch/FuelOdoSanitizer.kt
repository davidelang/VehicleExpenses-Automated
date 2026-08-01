package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * Odometer **detector** (not a healer).
 *
 * Enqueues [BatchPendingKind.ODO_SUSPECT] only — **never** mutates rows.
 *
 * **Chain merge:** flagged edges (reverse | digit_jump | gap) form connected
 * components → one pending item per component (not one card per overlapping edge).
 *
 * **Simple fix (phase 1):** length-OCR guesses (trailing 0, missing lead digit, …)
 * → `extra.mode=simple` + `suggestedOdo`; short UI (1 image + Save).
 *
 * **Complex (phase 2):** residual components without a simple guess.
 */
object FuelOdoSanitizer {

    const val CLEAN_MPG_MIN: Double = 5.0
    const val CLEAN_MPG_MAX: Double = 80.0

    data class SanitizerResult(
        val updates: List<FuelEntry> = emptyList(),
        val newPending: List<BatchPendingItem> = emptyList(),
    ) {
        fun isEmpty() = updates.isEmpty() && newPending.isEmpty()
    }

    private data class FlaggedEdge(
        val prev: FuelEntry,
        val cur: FuelEntry,
        val next: FuelEntry?,
        val reason: String,
        val extra: Map<String, String>,
    )

    fun sanitize(entries: List<FuelEntry>): SanitizerResult {
        val live = entries.filter { !it.deleted && it.vehicleId > 0 }
        if (live.isEmpty()) return SanitizerResult()

        val pending = mutableListOf<BatchPendingItem>()

        // Phase 1: odo ≤ 0 with dash photo (missing OCR) → simple fix, independent of chain
        val simpleMissingIds = mutableSetOf<Long>()
        for (e in live) {
            if (!eligibleMissingOdoWithDash(e)) continue
            if (e.id in simpleMissingIds) continue
            simpleMissingIds.add(e.id)
            pending += missingOdoDashPending(e)
        }

        for ((vid, vRows) in live.groupBy { it.vehicleId }) {
            val odoRows = vRows
                .filter { eligibleForOdoDetect(it) }
                .sortedWith(compareBy({ it.timestamp }, { it.id }))
            if (odoRows.size < 2) continue

            val edges = mutableListOf<FlaggedEdge>()

            // Pass 1: reverse
            for (i in 1 until odoRows.size) {
                val prev = odoRows[i - 1]
                val cur = odoRows[i]
                if (cur.odometer < prev.odometer) {
                    val next = odoRows.getOrNull(i + 1)
                    edges += FlaggedEdge(
                        prev, cur, next, "reverse", emptyMap(),
                    )
                }
            }

            // Pass 2: digit jump
            for (i in 1 until odoRows.size) {
                val prev = odoRows[i - 1]
                val cur = odoRows[i]
                if (cur.odometer <= prev.odometer) continue
                if (isDigitJump(prev.odometer, cur.odometer)) {
                    val next = odoRows.getOrNull(i + 1)
                    edges += FlaggedEdge(
                        prev, cur, next, "digit_jump",
                        mapOf(
                            "prevDigits" to digitLen(prev.odometer).toString(),
                            "curDigits" to digitLen(cur.odometer).toString(),
                        ),
                    )
                }
            }

            // Pass 3: gap with clean mpg
            val mpg = robustCleanMpg(vRows)
            val maxVol = vRows.filter { it.gallons > 0 }.maxOfOrNull { it.gallons }
            if (mpg != null && maxVol != null && maxVol > 0) {
                val limitMiles = maxVol * mpg * FuelRowMergeEngine.ODO_GAP_FACTOR
                for (i in 1 until odoRows.size) {
                    val prev = odoRows[i - 1]
                    val cur = odoRows[i]
                    if (cur.odometer <= prev.odometer) continue
                    val delta = cur.odometer - prev.odometer
                    if (delta > limitMiles) {
                        val next = odoRows.getOrNull(i + 1)
                        edges += FlaggedEdge(
                            prev, cur, next, "gap",
                            mapOf(
                                "limitMiles" to limitMiles.toString(),
                                "delta" to delta.toString(),
                                "maxVol" to maxVol.toString(),
                                "mpg" to mpg.toString(),
                            ),
                        )
                    }
                }
            }

            if (edges.isEmpty()) continue

            // Union-find components by entry id
            val parent = mutableMapOf<Long, Long>()
            fun find(x: Long): Long {
                var r = x
                while (parent[r] != null && parent[r] != r) r = parent[r]!!
                parent[x] = r
                return r
            }
            fun union(a: Long, b: Long) {
                val ra = find(a)
                val rb = find(b)
                if (ra != rb) parent[ra] = rb
            }
            for (e in edges) {
                parent.putIfAbsent(e.prev.id, e.prev.id)
                parent.putIfAbsent(e.cur.id, e.cur.id)
                e.next?.let { parent.putIfAbsent(it.id, it.id) }
                union(e.prev.id, e.cur.id)
                e.next?.let { union(e.cur.id, it.id) }
            }

            val byRoot = edges.groupBy { find(it.cur.id) }

            for ((_, compEdges) in byRoot) {
                // Prefer simple fixes for each distinct suspect that has a length guess
                val simpleEmitted = mutableSetOf<Long>()
                val residualEdges = mutableListOf<FlaggedEdge>()

                for (edge in compEdges) {
                    val suspect = pickSuspect(edge.prev, edge.cur, edge.next)
                    val guess = simpleOdoGuess(
                        suspect.odometer,
                        edge.prev.takeIf { it.id != suspect.id }?.odometer
                            ?: odoRows.firstOrNull {
                                it.timestamp < suspect.timestamp && it.id != suspect.id
                            }?.odometer,
                        edge.next?.takeIf { it.id != suspect.id }?.odometer
                            ?: odoRows.firstOrNull {
                                it.timestamp > suspect.timestamp && it.id != suspect.id
                            }?.odometer,
                    )
                    if (guess != null &&
                        suspect.id !in simpleEmitted &&
                        suspect.id !in simpleMissingIds
                    ) {
                        simpleEmitted.add(suspect.id)
                        pending += simpleOdoPending(
                            suspect = suspect,
                            suggestedOdo = guess,
                            reason = edge.reason,
                            vehicleId = vid,
                            prev = edge.prev,
                            cur = edge.cur,
                            next = edge.next,
                            extraFields = edge.extra,
                        )
                    } else if (guess == null && suspect.id !in simpleMissingIds) {
                        residualEdges += edge
                    }
                }

                // Complex chain: residual edges without simple fix (or whole component if none simple)
                val complexEdges = if (simpleEmitted.isEmpty()) compEdges else residualEdges
                if (complexEdges.isEmpty()) continue

                // One card per residual component
                val primary = complexEdges.first()
                val suspect = pickSuspect(primary.prev, primary.cur, primary.next)
                // Expand to component time-order peers for UI
                val ids = linkedSetOf<Long>()
                for (e in complexEdges) {
                    ids.add(e.prev.id)
                    ids.add(e.cur.id)
                    e.next?.let { ids.add(it.id) }
                }
                val peers = odoRows.filter { it.id in ids }
                    .sortedWith(compareBy({ it.timestamp }, { it.id }))
                val prev = peers.getOrNull(peers.indexOfFirst { it.id == suspect.id } - 1)
                    ?: primary.prev
                val cur = peers.find { it.id == suspect.id } ?: primary.cur
                val next = peers.getOrNull(peers.indexOfFirst { it.id == suspect.id } + 1)
                    ?: primary.next
                val reasons = complexEdges.map { it.reason }.distinct().joinToString("+")
                pending += odoSuspectPending(
                    suspect = suspect,
                    reason = reasons,
                    message = "Odo chain ($reasons): ${peers.size} fills vehicle=$vid; " +
                        "suspect id=${suspect.id} (detect only)",
                    prev = prev,
                    cur = cur,
                    next = next,
                    extraFields = mapOf(
                        "mode" to "complex",
                        "chainSize" to peers.size.toString(),
                        "chainIds" to peers.joinToString(",") { it.id.toString() },
                        "edgeCount" to complexEdges.size.toString(),
                    ) + primary.extra,
                )
            }
        }

        return SanitizerResult(updates = emptyList(), newPending = pending)
    }

    /**
     * Length-based OCR guesses. Pre-fill only — never write without user Save.
     */
    fun simpleOdoGuess(o: Int, prev: Int?, next: Int?): Int? {
        if (o <= 0) return null
        val s = o.toString()
        val neighbors = listOfNotNull(prev, next).filter { it > 0 }

        fun near(a: Int, b: Int): Boolean {
            val d = abs(a - b)
            if (d <= 2_000) return true
            val m = max(a, b).toDouble()
            return m > 0 && d / m < 0.08
        }

        // 7 digits ending 0 → drop trailing zero / take first 6
        if (s.length == 7 && s.endsWith('0')) {
            val core = s.substring(0, 6).toIntOrNull()
            if (core != null && (neighbors.isEmpty() || neighbors.any { near(core, it) })) {
                return core
            }
            val half = o / 10
            if (neighbors.any { near(half, it) }) return half
        }

        // 5 digits → prepend 1 or 2 (prefer neighbor leading digit)
        if (s.length == 5) {
            val leadCandidates = buildList {
                neighbors.forEach { n ->
                    val ns = n.toString()
                    if (ns.isNotEmpty()) add(ns[0])
                }
                add('1')
                add('2')
            }.distinct()
            for (lead in leadCandidates) {
                val g = (lead + s).toIntOrNull() ?: continue
                if (neighbors.isEmpty() || neighbors.any { near(g, it) }) return g
            }
        }

        // 4 digits → prepend 2-digit prefix from prev
        if (s.length == 4 && prev != null && prev > 0) {
            val ps = prev.toString()
            if (ps.length >= 2) {
                val pref = ps.take(2)
                val g = (pref + s).toIntOrNull()
                if (g != null && near(g, prev)) return g
            }
            if (ps.length >= 6) {
                val pref = ps.take(ps.length - 4)
                val g = (pref + s).toIntOrNull()
                if (g != null && near(g, prev)) return g
            }
        }

        return null
    }

    private fun eligibleForOdoDetect(e: FuelEntry): Boolean {
        val blank = e.odometer <= 0 && e.cost <= 0 && e.gallons <= 0
        if (blank) return false
        return e.odometer > 0
    }

    /**
     * Phase 1: live assigned row with odo missing/0, has dash photo, not pure gap marker.
     * vehicleId must be > 0 (unassigned pumps stay other phases).
     */
    internal fun eligibleMissingOdoWithDash(e: FuelEntry): Boolean {
        if (e.deleted || e.vehicleId <= 0) return false
        if (e.odometer > 0) return false
        if (dashPhotoPaths(e).isEmpty()) return false
        // Pure gap markers without real fill content — skip if explicitly gap-tagged and no dash would have already returned
        val notes = e.notes.orEmpty()
        if (notes.contains("batch_gap_marker") && dashPhotoPaths(e).isEmpty()) return false
        // blank gap-only: no dash (already excluded); dash blanks like batch_import_dash_blank **do** qualify
        return true
    }

    private fun missingOdoDashPending(e: FuelEntry): BatchPendingItem {
        val dash = dashPhotoPaths(e)
        val primary = dash.firstOrNull()
        return BatchPendingItem(
            kind = BatchPendingKind.ODO_SUSPECT,
            message = "Odo missing (0) — enter from dash · vehicle=${e.vehicleId} id=${e.id}",
            photoPath = primary,
            durablePhotoPath = primary,
            timestampMs = e.timestamp,
            fuelEntryId = e.id,
            suggestedVehicleId = e.vehicleId.takeIf { it > 0 },
            extra = mapOf(
                "mode" to "simple",
                "reason" to "missing_odo_dash",
                // Empty suggested → UI shows blank field; Save requires user odo > 0
                "suggestedOdo" to "",
                "parsedOdo" to "0",
                "suspectId" to e.id.toString(),
                "entryIds" to e.id.toString(),
                "prevEntryId" to e.id.toString(),
                "curEntryId" to e.id.toString(),
                "nextEntryId" to "",
                "prevOdo" to "0",
                "curOdo" to "0",
                "nextOdo" to "",
                "prevDashPaths" to "",
                "curDashPaths" to dash.joinToString("|"),
                "nextDashPaths" to "",
            ),
        )
    }

    private fun isDigitJump(prev: Int, cur: Int): Boolean {
        if (prev <= 0 || cur <= 0) return false
        val dp = digitLen(prev)
        val dc = digitLen(cur)
        if (kotlin.math.abs(dp - dc) >= 1 &&
            (cur >= prev * 8 || prev >= cur * 8)
        ) {
            return true
        }
        if (cur >= prev * 10 || prev >= cur * 10) return true
        return false
    }

    fun robustCleanMpg(vehicleEntries: List<FuelEntry>): Double? {
        val full = vehicleEntries
            .filter {
                !it.deleted && !it.economyIgnored && !it.isPartialFill &&
                    it.odometer > 0 && it.cost > 0 && it.gallons > 0
            }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
        if (full.size < 2) return null
        val legs = mutableListOf<Double>()
        for (i in 1 until full.size) {
            val prev = full[i - 1]
            val cur = full[i]
            if (cur.odometer <= prev.odometer) continue
            val between = vehicleEntries.filter {
                !it.deleted && !it.economyIgnored &&
                    it.timestamp > prev.timestamp && it.timestamp <= cur.timestamp
            }
            if (between.any { FuelEconomyChains.isMpgChainBreaker(it) }) continue
            val sumVol = between.filter { it.gallons > 0 }.sumOf { it.gallons }
            if (sumVol <= 0) continue
            val mpg = (cur.odometer - prev.odometer) / sumVol
            if (mpg in CLEAN_MPG_MIN..CLEAN_MPG_MAX) legs.add(mpg)
        }
        if (legs.size < 3) return null
        val sorted = legs.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    internal fun pickSuspect(
        prev: FuelEntry,
        cur: FuelEntry,
        next: FuelEntry?,
        gapDelta: Int? = null,
        limitMiles: Double? = null,
    ): FuelEntry {
        val peers = listOfNotNull(prev, cur, next).filter { it.odometer > 0 }
        val typicalDigits = peers
            .map { digitLen(it.odometer) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: 6

        fun score(e: FuelEntry, other: FuelEntry): Int {
            var s = 0
            val d = digitLen(e.odometer)
            if (d != typicalDigits) s += 3
            if (d == typicalDigits + 1 || e.odometer >= other.odometer * 8) s += 4
            if (d == typicalDigits - 1 || (other.odometer >= e.odometer * 8 && e.odometer > 0)) s += 4
            if (e.photoUrl.isNullOrBlank()) s += 1
            if (e.location?.contains("blank") == true) s += 1
            return s
        }

        val sp = score(prev, cur)
        val sc = score(cur, prev)
        return if (sc >= sp - 1) cur else prev
    }

    private fun digitLen(odo: Int): Int {
        if (odo <= 0) return 0
        return log10(odo.toDouble()).toInt() + 1
    }

    private fun simpleOdoPending(
        suspect: FuelEntry,
        suggestedOdo: Int,
        reason: String,
        vehicleId: Int,
        prev: FuelEntry,
        cur: FuelEntry,
        next: FuelEntry?,
        extraFields: Map<String, String>,
    ): BatchPendingItem {
        val suspectDash = dashPhotoPaths(suspect)
        val primary = suspectDash.firstOrNull()
        return BatchPendingItem(
            kind = BatchPendingKind.ODO_SUSPECT,
            message = "Simple odo fix: ${suspect.odometer} → $suggestedOdo? " +
                "($reason) vehicle=$vehicleId id=${suspect.id}",
            photoPath = primary,
            durablePhotoPath = primary,
            timestampMs = suspect.timestamp,
            fuelEntryId = suspect.id,
            suggestedVehicleId = suspect.vehicleId.takeIf { it > 0 },
            extra = mapOf(
                "mode" to "simple",
                "reason" to reason,
                "suggestedOdo" to suggestedOdo.toString(),
                "parsedOdo" to suspect.odometer.toString(),
                "suspectId" to suspect.id.toString(),
                "entryIds" to suspect.id.toString(),
                "prevEntryId" to prev.id.toString(),
                "curEntryId" to cur.id.toString(),
                "nextEntryId" to (next?.id?.toString() ?: ""),
                "prevOdo" to prev.odometer.toString(),
                "curOdo" to cur.odometer.toString(),
                "nextOdo" to (next?.odometer?.toString() ?: ""),
                "prevDashPaths" to dashPhotoPaths(prev).joinToString("|"),
                "curDashPaths" to dashPhotoPaths(cur).joinToString("|"),
                "nextDashPaths" to (next?.let { dashPhotoPaths(it) }?.joinToString("|") ?: ""),
            ) + extraFields,
        )
    }

    private fun odoSuspectPending(
        suspect: FuelEntry,
        reason: String,
        message: String,
        prev: FuelEntry,
        cur: FuelEntry,
        next: FuelEntry?,
        extraFields: Map<String, String>,
    ): BatchPendingItem {
        val prevDash = dashPhotoPaths(prev)
        val curDash = dashPhotoPaths(cur)
        val nextDash = next?.let { dashPhotoPaths(it) }.orEmpty()
        val suspectDash = dashPhotoPaths(suspect)
        val primary = suspectDash.firstOrNull()
            ?: curDash.firstOrNull()
            ?: prevDash.firstOrNull()
        val mode = extraFields["mode"] ?: "complex"
        return BatchPendingItem(
            kind = BatchPendingKind.ODO_SUSPECT,
            message = message,
            photoPath = primary,
            durablePhotoPath = primary,
            timestampMs = suspect.timestamp,
            fuelEntryId = suspect.id,
            suggestedVehicleId = suspect.vehicleId.takeIf { it > 0 },
            extra = mapOf(
                "mode" to mode,
                "reason" to reason,
                "entryIds" to listOfNotNull(prev.id, cur.id, next?.id).joinToString(","),
                "suspectId" to suspect.id.toString(),
                "prevEntryId" to prev.id.toString(),
                "curEntryId" to cur.id.toString(),
                "nextEntryId" to (next?.id?.toString() ?: ""),
                "prevOdo" to prev.odometer.toString(),
                "curOdo" to cur.odometer.toString(),
                "nextOdo" to (next?.odometer?.toString() ?: ""),
                "prevDashPaths" to prevDash.joinToString("|"),
                "curDashPaths" to curDash.joinToString("|"),
                "nextDashPaths" to nextDash.joinToString("|"),
                "prevTs" to prev.timestamp.toString(),
                "curTs" to cur.timestamp.toString(),
                "nextTs" to (next?.timestamp?.toString() ?: ""),
                "prevCost" to prev.cost.toString(),
                "curCost" to cur.cost.toString(),
                "nextCost" to (next?.cost?.toString() ?: ""),
                "prevVol" to prev.gallons.toString(),
                "curVol" to cur.gallons.toString(),
                "nextVol" to (next?.gallons?.toString() ?: ""),
                "parsedOdo" to suspect.odometer.toString(),
                "parsedCost" to suspect.cost.toString(),
                "parsedVol" to suspect.gallons.toString(),
            ) + extraFields,
        )
    }
}
