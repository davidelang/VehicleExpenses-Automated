package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import kotlin.math.log10

/**
 * Odometer **detector** (not a healer).
 *
 * Enqueues [BatchPendingKind.ODO_SUSPECT] only — **never** mutates rows
 * (no demote-to-partial, no auto-zero odo).
 *
 * **Order:** reverse pairs first, then forward digit-jump, then optional gap
 * with mpg only if computed from legs already in absolute band 5–80.
 * No constant mpg fallback.
 */
object FuelOdoSanitizer {

    /** Display/clean mpg band for gap mpg estimate (not row mutation). */
    const val CLEAN_MPG_MIN: Double = 5.0
    const val CLEAN_MPG_MAX: Double = 80.0

    data class SanitizerResult(
        /** Always empty — detect only. Kept for call-site compatibility. */
        val updates: List<FuelEntry> = emptyList(),
        val newPending: List<BatchPendingItem> = emptyList(),
    ) {
        fun isEmpty() = updates.isEmpty() && newPending.isEmpty()
    }

    fun sanitize(entries: List<FuelEntry>): SanitizerResult {
        val live = entries.filter { !it.deleted && it.vehicleId > 0 }
        if (live.isEmpty()) return SanitizerResult()

        val pending = mutableListOf<BatchPendingItem>()
        val flaggedIds = mutableSetOf<Long>() // avoid double-enqueue same suspect

        for ((vid, vRows) in live.groupBy { it.vehicleId }) {
            val odoRows = vRows
                .filter { eligibleForOdoDetect(it) }
                .sortedWith(compareBy({ it.timestamp }, { it.id }))

            // Pass 1: reverse odo (before gap)
            for (i in 1 until odoRows.size) {
                val prev = odoRows[i - 1]
                val cur = odoRows[i]
                if (cur.odometer < prev.odometer) {
                    val next = odoRows.getOrNull(i + 1)
                    val suspect = pickSuspect(prev, cur, next)
                    if (suspect.id !in flaggedIds) {
                        flaggedIds.add(suspect.id)
                        pending += odoSuspectPending(
                            suspect = suspect,
                            reason = "reverse",
                            message = "Odometer reverse in time: prev=${prev.odometer} → " +
                                "cur=${cur.odometer} vehicle=$vid; suspect id=${suspect.id} " +
                                "(detect only — edit or checkbox partial)",
                            prev = prev,
                            cur = cur,
                            next = next,
                            extraFields = emptyMap(),
                        )
                    }
                }
            }

            // Pass 2: forward digit-length / ×8–×12 jump
            for (i in 1 until odoRows.size) {
                val prev = odoRows[i - 1]
                val cur = odoRows[i]
                if (cur.odometer <= prev.odometer) continue
                if (isDigitJump(prev.odometer, cur.odometer)) {
                    val next = odoRows.getOrNull(i + 1)
                    val suspect = pickSuspect(prev, cur, next)
                    if (suspect.id !in flaggedIds) {
                        flaggedIds.add(suspect.id)
                        pending += odoSuspectPending(
                            suspect = suspect,
                            reason = "digit_jump",
                            message = "Odometer digit jump: prev=${prev.odometer} → " +
                                "cur=${cur.odometer} vehicle=$vid; suspect id=${suspect.id}",
                            prev = prev,
                            cur = cur,
                            next = next,
                            extraFields = mapOf(
                                "prevDigits" to digitLen(prev.odometer).toString(),
                                "curDigits" to digitLen(cur.odometer).toString(),
                            ),
                        )
                    }
                }
            }

            // Pass 3: gap with clean mpg only (5–80 band legs)
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
                        val suspect = pickSuspect(prev, cur, next)
                        if (suspect.id !in flaggedIds) {
                            flaggedIds.add(suspect.id)
                            pending += odoSuspectPending(
                                suspect = suspect,
                                reason = "gap",
                                message = "Unreasonable odo gap Δ=$delta > limit " +
                                    "${"%.0f".format(limitMiles)} " +
                                    "(maxVol=${"%.2f".format(maxVol)} × cleanMpg=${"%.1f".format(mpg)} × 3) " +
                                    "vehicle=$vid; suspect id=${suspect.id} (detect only)",
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
        }

        return SanitizerResult(updates = emptyList(), newPending = pending)
    }

    /** Skip blank markers; still scan complete and incomplete odo-bearing rows. */
    private fun eligibleForOdoDetect(e: FuelEntry): Boolean {
        val blank = e.odometer <= 0 && e.cost <= 0 && e.gallons <= 0
        if (blank) return false
        return e.odometer > 0
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
        // Same digit length but huge multiplier still suspicious
        if (cur >= prev * 10 || prev >= cur * 10) return true
        return false
    }

    /**
     * Median mpg of full-fill legs that already land in [CLEAN_MPG_MIN, CLEAN_MPG_MAX].
     * Requires ≥3 such legs; else null (gap rule skipped — no fallback).
     */
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

    /** Bias toward later row when scores close. */
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
        // Primary photo for list cards: suspect dash only (not all peers' pumps)
        val primary = suspectDash.firstOrNull()
            ?: curDash.firstOrNull()
            ?: prevDash.firstOrNull()
        return BatchPendingItem(
            kind = BatchPendingKind.ODO_SUSPECT,
            message = message,
            photoPath = primary,
            durablePhotoPath = primary,
            timestampMs = suspect.timestamp,
            fuelEntryId = suspect.id,
            suggestedVehicleId = suspect.vehicleId.takeIf { it > 0 },
            extra = mapOf(
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
