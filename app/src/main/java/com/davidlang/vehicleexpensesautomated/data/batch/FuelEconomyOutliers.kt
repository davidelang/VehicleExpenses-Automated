package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson

/**
 * MPG outlier detection for Stage C pending enqueue.
 *
 * Leg is outlier if `mpg < ref/3` or `mpg > ref*3`.
 * [ref] = median of all usable full-fill leg mpgs for the vehicle.
 * Requires ≥3 legs; otherwise no auto-enqueue.
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
    )

    private fun hasOdo(e: FuelEntry) = e.odometer > 0
    private fun hasCost(e: FuelEntry) = e.cost > 0
    private fun hasVol(e: FuelEntry) = e.gallons > 0
    private fun isFullFill(e: FuelEntry) =
        !e.economyIgnored && !e.isPartialFill && hasOdo(e) && hasCost(e) && hasVol(e)

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
            .filter { isFullFill(it) }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
        if (full.size < 2) return emptyList()
        val legs = mutableListOf<Triple<FuelEntry, FuelEntry, Double>>() // prev, cur, mpg
        for (i in 1 until full.size) {
            val prev = full[i - 1]
            val cur = full[i]
            if (cur.odometer <= prev.odometer) continue
            val between = entries.filter {
                it.timestamp > prev.timestamp && it.timestamp <= cur.timestamp && !it.economyIgnored
            }
            val sumVol = between.filter { hasVol(it) }.sumOf { it.gallons }
            if (sumVol <= 0) continue
            val mpg = (cur.odometer - prev.odometer) / sumVol
            legs.add(Triple(prev, cur, mpg))
        }
        if (legs.size < 3) return emptyList()
        val sortedMpg = legs.map { it.third }.sorted()
        val mid = sortedMpg.size / 2
        val ref = if (sortedMpg.size % 2 == 0) {
            (sortedMpg[mid - 1] + sortedMpg[mid]) / 2.0
        } else {
            sortedMpg[mid]
        }
        if (ref <= 0) return emptyList()
        return legs.mapNotNull { (prev, cur, mpg) ->
            if (mpg < ref / 3.0 || mpg > ref * 3.0) {
                val between = entries.filter {
                    it.timestamp > prev.timestamp && it.timestamp <= cur.timestamp && !it.economyIgnored
                }
                val sumVol = between.filter { hasVol(it) }.sumOf { it.gallons }
                OutlierLeg(
                    vehicleId = vehicleId,
                    endEntry = cur,
                    prevEntry = prev,
                    mpg = mpg,
                    refMpg = ref,
                    odoDelta = cur.odometer - prev.odometer,
                    sumVol = sumVol,
                )
            } else null
        }
    }

    fun photoPathsForEntry(e: FuelEntry): List<String> =
        dedupePhotoPaths(FuelPhotoJson.parse(e.photoUrl).map { it.uri })

    /**
     * Primary photos = **leg end only** (focus default). Prev endpoint ids + metrics
     * live in [BatchPendingItem.extra] so the UI can switch focus without dumping
     * both endpoints into one unlabeled photo strip.
     */
    fun toPending(leg: OutlierLeg): BatchPendingItem {
        val endPhotos = photoPathsForEntry(leg.endEntry)
        val prevPhotos = photoPathsForEntry(leg.prevEntry)
        return BatchPendingItem(
            kind = BatchPendingKind.MPG_OUTLIER,
            message = "MPG outlier ${"%.1f".format(leg.mpg)} vs ref ${"%.1f".format(leg.refMpg)} " +
                "(odoΔ=${leg.odoDelta} vol=${"%.2f".format(leg.sumVol)}) vehicle=${leg.vehicleId}",
            photoPath = endPhotos.firstOrNull(),
            durablePhotoPath = endPhotos.firstOrNull(),
            timestampMs = leg.endEntry.timestamp,
            fuelEntryId = leg.endEntry.id,
            suggestedVehicleId = leg.vehicleId,
            extra = mapOf(
                "entryIds" to "${leg.prevEntry.id},${leg.endEntry.id}",
                // Primary strip paths (end/focus only)
                "photoPaths" to endPhotos.joinToString("|"),
                "prevPhotoPaths" to prevPhotos.joinToString("|"),
                "mpg" to leg.mpg.toString(),
                "refMpg" to leg.refMpg.toString(),
                "odoDelta" to leg.odoDelta.toString(),
                "sumVol" to leg.sumVol.toString(),
                "prevEntryId" to leg.prevEntry.id.toString(),
                "endEntryId" to leg.endEntry.id.toString(),
                "prevTs" to leg.prevEntry.timestamp.toString(),
                "endTs" to leg.endEntry.timestamp.toString(),
                "prevOdo" to leg.prevEntry.odometer.toString(),
                "endOdo" to leg.endEntry.odometer.toString(),
                "prevCost" to leg.prevEntry.cost.toString(),
                "endCost" to leg.endEntry.cost.toString(),
                "prevVol" to leg.prevEntry.gallons.toString(),
                "endVol" to leg.endEntry.gallons.toString(),
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
            latitude = e.latitude,
            longitude = e.longitude,
            extra = mapOf(
                "photoPaths" to photos.joinToString("|"),
            ),
        )
    }
}
