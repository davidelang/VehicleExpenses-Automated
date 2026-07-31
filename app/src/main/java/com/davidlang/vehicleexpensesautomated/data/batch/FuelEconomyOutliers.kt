package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson

/**
 * MPG outlier detection for Stage C pending enqueue.
 *
 * **Same chain rules as reports** ([FuelEconomyChains] / REPORTS_METRICS):
 * - Full-fill anchors only (`!economyIgnored && !isPartialFill && odo+cost+vol`).
 * - Window = (prev.ts, cur.ts] non-ignored rows; **skip leg** if any MPG chain breaker
 *   (blank or cost-without-vol) is in the window — do not ask “why is MPG bad?” when
 *   the chain is already intentionally broken (e.g. `batch_gap_marker`).
 * - `sumVol` includes partials and incomplete pumps with volume; they never anchor.
 *
 * Leg is outlier if `mpg < ref/3` or `mpg > ref*3`.
 * [ref] = median of all **non-skipped** full-fill leg mpgs for the vehicle.
 * Requires ≥3 usable legs; otherwise no auto-enqueue.
 */
object FuelEconomyOutliers {

    data class OutlierLeg(
        val vehicleId: Int,
        val endEntry: FuelEntry,
        val prevEntry: FuelEntry,
        val mpg: Double,
        val refMpg: Double,
        val odoDelta: Int,
        val sumVol: Double,
        /** All non-ignored rows in the leg window (for inventory). */
        val windowEntries: List<FuelEntry>,
    )

    fun detectOutliers(entries: List<FuelEntry>): List<OutlierLeg> {
        val live = entries.filter { !it.deleted }
        val out = mutableListOf<OutlierLeg>()
        for ((vid, vEntries) in live.filter { it.vehicleId > 0 }.groupBy { it.vehicleId }) {
            out += detectForVehicle(vid, vEntries)
        }
        return out
    }

    private fun detectForVehicle(vehicleId: Int, entries: List<FuelEntry>): List<OutlierLeg> {
        val full = entries
            .filter { FuelEconomyChains.isFullFill(it) }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
        if (full.size < 2) return emptyList()

        data class Leg(
            val prev: FuelEntry,
            val cur: FuelEntry,
            val mpg: Double,
            val sumVol: Double,
            val window: List<FuelEntry>,
        )

        val legs = mutableListOf<Leg>()
        for (i in 1 until full.size) {
            val prev = full[i - 1]
            val cur = full[i]
            if (cur.odometer <= prev.odometer) continue
            val between = FuelEconomyChains.windowContributors(
                entries, prev.timestamp, cur.timestamp,
            )
            // Product: no MPG_OUTLIER when chain already broken (gap markers, cost-no-vol)
            if (FuelEconomyChains.windowHasMpgBreaker(between)) continue
            val sumVol = FuelEconomyChains.sumVol(between)
            if (sumVol <= 0) continue
            val mpg = (cur.odometer - prev.odometer) / sumVol
            legs.add(Leg(prev, cur, mpg, sumVol, between))
        }
        if (legs.size < 3) return emptyList()
        val sortedMpg = legs.map { it.mpg }.sorted()
        val mid = sortedMpg.size / 2
        val ref = if (sortedMpg.size % 2 == 0) {
            (sortedMpg[mid - 1] + sortedMpg[mid]) / 2.0
        } else {
            sortedMpg[mid]
        }
        if (ref <= 0) return emptyList()
        return legs.mapNotNull { leg ->
            if (leg.mpg < ref / 3.0 || leg.mpg > ref * 3.0) {
                OutlierLeg(
                    vehicleId = vehicleId,
                    endEntry = leg.cur,
                    prevEntry = leg.prev,
                    mpg = leg.mpg,
                    refMpg = ref,
                    odoDelta = leg.cur.odometer - leg.prev.odometer,
                    sumVol = leg.sumVol,
                    windowEntries = leg.window,
                )
            } else null
        }
    }

    fun photoPathsForEntry(e: FuelEntry): List<String> =
        dedupePhotoPaths(FuelPhotoJson.parse(e.photoUrl).map { it.uri })

    /**
     * Pending for UI: **this fill** = leg end, **last fill** = leg start.
     * Separate photo lists so the card can show photos above each fill button
     * (never one unlabeled 4-image strip).
     * [windowSummary] lists intermediate fills (text inventory).
     */
    fun toPending(leg: OutlierLeg): BatchPendingItem {
        val thisPhotos = photoPathsForEntry(leg.endEntry)
        val lastPhotos = photoPathsForEntry(leg.prevEntry)
        // Window already filtered in detect; encode inventory directly
        val inventory = leg.windowEntries
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
            .let { window ->
                val take = window.take(12)
                val parts = take.map { e ->
                    val gal = "%.2f".format(e.gallons)
                    val cost = "%.2f".format(e.cost)
                    "${e.id}:${e.odometer}:$gal:$cost:${FuelEconomyChains.rowShape(e)}"
                }
                val more = window.size - take.size
                if (more > 0) parts.joinToString("|") + "|+$more more"
                else parts.joinToString("|")
            }
        return BatchPendingItem(
            kind = BatchPendingKind.MPG_OUTLIER,
            message = "MPG outlier ${"%.1f".format(leg.mpg)} vs ref ${"%.1f".format(leg.refMpg)} " +
                "(odoΔ=${leg.odoDelta} vol=${"%.2f".format(leg.sumVol)}) vehicle=${leg.vehicleId}",
            photoPath = thisPhotos.firstOrNull(),
            durablePhotoPath = thisPhotos.firstOrNull(),
            timestampMs = leg.endEntry.timestamp,
            fuelEntryId = leg.endEntry.id,
            suggestedVehicleId = leg.vehicleId,
            extra = mapOf(
                "entryIds" to "${leg.prevEntry.id},${leg.endEntry.id}",
                "entrySyncIds" to listOf(leg.prevEntry.syncId, leg.endEntry.syncId)
                    .filter { it.isNotBlank() }
                    .joinToString(","),
                "memberSyncIds" to listOf(leg.prevEntry.syncId, leg.endEntry.syncId)
                    .filter { it.isNotBlank() }
                    .sorted()
                    .joinToString(","),
                // Separate lists for Last fill / This fill photo blocks
                "thisPhotoPaths" to thisPhotos.joinToString("|"),
                "lastPhotoPaths" to lastPhotos.joinToString("|"),
                // Back-compat aliases
                "photoPaths" to thisPhotos.joinToString("|"),
                "prevPhotoPaths" to lastPhotos.joinToString("|"),
                "mpg" to leg.mpg.toString(),
                "refMpg" to leg.refMpg.toString(),
                "odoDelta" to leg.odoDelta.toString(),
                "sumVol" to leg.sumVol.toString(),
                "prevEntryId" to leg.prevEntry.id.toString(),
                "endEntryId" to leg.endEntry.id.toString(),
                "lastEntryId" to leg.prevEntry.id.toString(),
                "thisEntryId" to leg.endEntry.id.toString(),
                "prevTs" to leg.prevEntry.timestamp.toString(),
                "endTs" to leg.endEntry.timestamp.toString(),
                "prevOdo" to leg.prevEntry.odometer.toString(),
                "endOdo" to leg.endEntry.odometer.toString(),
                "prevCost" to leg.prevEntry.cost.toString(),
                "endCost" to leg.endEntry.cost.toString(),
                "prevVol" to leg.prevEntry.gallons.toString(),
                "endVol" to leg.endEntry.gallons.toString(),
                "windowSummary" to inventory,
            ),
        )
    }

    fun economyIgnoredPending(e: FuelEntry): BatchPendingItem {
        val photos = photoPathsForEntry(e)
        return BatchPendingItem(
            kind = BatchPendingKind.ECONOMY_IGNORED,
            message = "Economy ignored: odo=${e.odometer} cost=${e.cost} vol=${e.gallons} " +
                "vehicle=${if (e.vehicleId == 0) "Unknown" else e.vehicleId}",
            photoPath = photos.firstOrNull(),
            durablePhotoPath = photos.firstOrNull(),
            timestampMs = e.timestamp,
            fuelEntryId = e.id,
            suggestedVehicleId = e.vehicleId.takeIf { it > 0 },
            extra = mapOf(
                "photoPaths" to photos.joinToString("|"),
                "entryIds" to e.id.toString(),
            ),
        )
    }

    fun unknownVehiclePending(e: FuelEntry): BatchPendingItem {
        val photos = photoPathsForEntry(e)
        return BatchPendingItem(
            kind = BatchPendingKind.ASSIGN_UNKNOWN_VEHICLE,
            message = "Unknown vehicle fill: odo=${e.odometer} cost=${e.cost} " +
                "vol=${e.gallons} ts=${e.timestamp}",
            photoPath = photos.firstOrNull(),
            durablePhotoPath = photos.firstOrNull(),
            timestampMs = e.timestamp,
            fuelEntryId = e.id,
            latitude = FuelLocationJson.lat(e.location),
            longitude = FuelLocationJson.lon(e.location),
            accuracyM = FuelLocationJson.accuracyM(e.location),
            extra = mapOf(
                "photoPaths" to photos.joinToString("|"),
            ),
        )
    }

    /** $/preferred-volume band for “normal” pump economics (USD/gal-like). */
    const val PUMP_RATIO_MIN: Double = 2.0
    const val PUMP_RATIO_MAX: Double = 7.0

    /**
     * Phase 3: cost+vol present, ratio absurd (not blank breakers, not odo-only).
     */
    fun detectBadPumpRatios(entries: List<FuelEntry>): List<BatchPendingItem> {
        val out = mutableListOf<BatchPendingItem>()
        for (e in entries.filter { !it.deleted }) {
            if (e.economyIgnored) continue
            if (e.cost <= 0 || e.gallons <= 0) continue
            // Blank-style already handled elsewhere
            val ratio = e.cost / e.gallons
            if (ratio in PUMP_RATIO_MIN..PUMP_RATIO_MAX) continue
            val photos = photoPathsForEntry(e)
            out += BatchPendingItem(
                kind = BatchPendingKind.BAD_PUMP_RATIO,
                message = "Bad pump ratio \$${"%.2f".format(ratio)}/vol " +
                    "(cost=${e.cost} vol=${e.gallons}) id=${e.id} vehicle=${e.vehicleId}",
                photoPath = photos.firstOrNull(),
                durablePhotoPath = photos.firstOrNull(),
                timestampMs = e.timestamp,
                fuelEntryId = e.id,
                suggestedVehicleId = e.vehicleId.takeIf { it > 0 },
                extra = mapOf(
                    "photoPaths" to photos.joinToString("|"),
                    "parsedCost" to e.cost.toString(),
                    "parsedVol" to e.gallons.toString(),
                    "parsedOdo" to e.odometer.toString(),
                    "ratio" to ratio.toString(),
                ),
            )
        }
        return out
    }
}
