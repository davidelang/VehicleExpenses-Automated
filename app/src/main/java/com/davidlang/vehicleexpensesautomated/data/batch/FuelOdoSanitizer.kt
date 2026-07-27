package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import kotlin.math.log10

/**
 * Odometer reverse / unreasonable-gap sanitizer (Stage B follow-on).
 *
 * **Order:** gap demotion first (digit-gain inflation), then reverse re-check.
 * Demotes **suspect** row to [FuelEntry.isPartialFill]=true (keeps odo for edit);
 * enqueues [BatchPendingKind.ODO_SUSPECT] with reason in extra.
 *
 * Gap rule: `Δodo > maxVol(v) * mpg(v) * [FuelRowMergeEngine.ODO_GAP_FACTOR]`.
 * mpg(v) = median of clean full-fill legs for **that vehicle only**; **no** constant
 * fallback — skip gap rule when &lt;3 clean legs or maxVol unknown.
 *
 * Reverse: `cur.odo < prev.odo` in time order → reliability score, **bias later** row.
 */
object FuelOdoSanitizer {

    data class SanitizerResult(
        val updates: List<FuelEntry> = emptyList(),
        val newPending: List<BatchPendingItem> = emptyList(),
    ) {
        fun isEmpty() = updates.isEmpty() && newPending.isEmpty()
    }

    fun sanitize(entries: List<FuelEntry>): SanitizerResult {
        val live = entries.filter { !it.deleted && it.vehicleId > 0 }
        if (live.isEmpty()) return SanitizerResult()

        val updatesById = mutableMapOf<Long, FuelEntry>()
        val pending = mutableListOf<BatchPendingItem>()
        val demotedIds = mutableSetOf<Long>()

        fun current(e: FuelEntry): FuelEntry = updatesById[e.id] ?: e

        for ((vid, vRows) in live.groupBy { it.vehicleId }) {
            val mpg = robustMpg(vRows)
            val maxVol = vRows.filter { it.gallons > 0 }.maxOfOrNull { it.gallons }

            // Pass 1: unreasonable forward gaps
            if (mpg != null && maxVol != null && maxVol > 0) {
                val limitMiles = maxVol * mpg * FuelRowMergeEngine.ODO_GAP_FACTOR
                val odoRows = vRows
                    .map { current(it) }
                    .filter { it.odometer > 0 && it.id !in demotedIds }
                    .sortedWith(compareBy({ it.timestamp }, { it.id }))
                for (i in 1 until odoRows.size) {
                    val prev = current(odoRows[i - 1])
                    val cur = current(odoRows[i])
                    if (prev.id in demotedIds || cur.id in demotedIds) continue
                    if (cur.odometer <= prev.odometer) continue
                    val delta = cur.odometer - prev.odometer
                    if (delta > limitMiles) {
                        val next = odoRows.getOrNull(i + 1)?.let { current(it) }
                        val suspect = pickSuspect(prev, cur, next, gapDelta = delta, limitMiles = limitMiles)
                        if (suspect.id !in demotedIds) {
                            val demoted = suspect.copy(isPartialFill = true)
                            updatesById[demoted.id] = demoted
                            demotedIds.add(demoted.id)
                            pending += odoSuspectPending(
                                suspect = demoted,
                                reason = "gap",
                                message = "Unreasonable odo gap Δ=$delta > limit ${"%.0f".format(limitMiles)} " +
                                    "(maxVol=${"%.2f".format(maxVol)} × mpg=${"%.1f".format(mpg)} × 3) " +
                                    "vehicle=$vid; demoted id=${suspect.id}",
                                prev = prev,
                                cur = cur,
                                next = next,
                                extraFields = mapOf(
                                    "limitMiles" to limitMiles.toString(),
                                    "delta" to delta.toString(),
                                    "maxVol" to maxVol.toString(),
                                    "mpg" to mpg.toString(),
                                ),
                            )
                        }
                    }
                }
            }

            // Pass 2: reverse odo
            val odoRows2 = vRows
                .map { current(it) }
                .filter { it.odometer > 0 && it.id !in demotedIds }
                .sortedWith(compareBy({ it.timestamp }, { it.id }))
            for (i in 1 until odoRows2.size) {
                val prev = current(odoRows2[i - 1])
                val cur = current(odoRows2[i])
                if (prev.id in demotedIds || cur.id in demotedIds) continue
                if (cur.odometer < prev.odometer) {
                    val next = odoRows2.getOrNull(i + 1)?.let { current(it) }
                    val suspect = pickSuspect(prev, cur, next, gapDelta = null, limitMiles = null)
                    if (suspect.id !in demotedIds) {
                        val demoted = suspect.copy(isPartialFill = true)
                        updatesById[demoted.id] = demoted
                        demotedIds.add(demoted.id)
                        pending += odoSuspectPending(
                            suspect = demoted,
                            reason = "reverse",
                            message = "Odometer reverse in time: prev=${prev.odometer} → cur=${cur.odometer} " +
                                "vehicle=$vid; demoted id=${suspect.id} (bias later when tied)",
                            prev = prev,
                            cur = cur,
                            next = next,
                            extraFields = emptyMap(),
                        )
                    }
                }
            }
        }

        return SanitizerResult(
            updates = updatesById.values.toList(),
            newPending = pending,
        )
    }

    /**
     * Median mpg of clean full-fill legs for this vehicle only.
     * Requires ≥3 legs; else null (gap rule skipped).
     */
    fun robustMpg(vehicleEntries: List<FuelEntry>): Double? {
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
            val sumVol = between.filter { it.gallons > 0 }.sumOf { it.gallons }
            if (sumVol <= 0) continue
            legs.add((cur.odometer - prev.odometer) / sumVol)
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

    /**
     * Lower score = more suspect. Bias toward [cur] (later) when scores close.
     */
    internal fun pickSuspect(
        prev: FuelEntry,
        cur: FuelEntry,
        next: FuelEntry?,
        gapDelta: Int?,
        limitMiles: Double?,
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
            if (d == typicalDigits + 1 || e.odometer >= other.odometer * 8) s += 4 // extra digit
            if (d == typicalDigits - 1 || (other.odometer >= e.odometer * 8 && e.odometer > 0)) s += 4
            if (e.photoUrl.isNullOrBlank()) s += 1
            if (e.location?.contains("blank") == true) s += 1
            if (e.isPartialFill) s += 1
            return s
        }

        val sp = score(prev, cur)
        val sc = score(cur, prev)
        // Bias later (cur) when tied or within 1 point
        return if (sc >= sp - 1) cur else prev
    }

    private fun digitLen(odo: Int): Int {
        if (odo <= 0) return 0
        return log10(odo.toDouble()).toInt() + 1
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
        val peers = listOfNotNull(prev, cur, next)
        val photos = peers.flatMap { FuelPhotoJson.parse(it.photoUrl).map { p -> p.uri } }
            .let { dedupePhotoPaths(it) }
        return BatchPendingItem(
            kind = BatchPendingKind.ODO_SUSPECT,
            message = message,
            photoPath = photos.firstOrNull()
                ?: FuelPhotoJson.parse(suspect.photoUrl).firstOrNull()?.uri,
            durablePhotoPath = FuelPhotoJson.parse(suspect.photoUrl).firstOrNull()?.uri,
            timestampMs = suspect.timestamp,
            fuelEntryId = suspect.id,
            suggestedVehicleId = suspect.vehicleId.takeIf { it > 0 },
            extra = mapOf(
                "reason" to reason,
                "entryIds" to peers.map { it.id }.joinToString(","),
                "photoPaths" to photos.joinToString("|"),
                "suspectId" to suspect.id.toString(),
                "parsedOdo" to suspect.odometer.toString(),
                "parsedCost" to suspect.cost.toString(),
                "parsedVol" to suspect.gallons.toString(),
                "prevOdo" to prev.odometer.toString(),
                "curOdo" to cur.odometer.toString(),
            ) + extraFields,
        )
    }
}
