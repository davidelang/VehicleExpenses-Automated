package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Same-stop correlation for batch merge and unassigned-pump assignment.
 *
 * Place match via [FuelLocationJson] blob name/address; else lat/lon within
 * [LOCATION_MATCH_METERS] from the same blob (Room v18 — no lat/lon columns).
 * Used by [FuelRowMergeEngine] at merge time and [BatchFuelImportCoordinator] residual suggest.
 */
object FuelStopMatch {

    /** Geo fallback when place name/address is missing on one or both sides. */
    const val LOCATION_MATCH_METERS: Double = 150.0

    /**
     * True when both rows share a station place (normalized name or address)
     * or are within [LOCATION_MATCH_METERS] of each other.
     * False when neither side has usable location data (caller should use tank/time only).
     */
    fun locationsMatch(a: FuelEntry, b: FuelEntry): Boolean {
        val ba = FuelLocationJson.parseBlob(a.location)
        val bb = FuelLocationJson.parseBlob(b.location)
        if (ba != null && bb != null) {
            val na = ba.name.trim().lowercase()
            val nb = bb.name.trim().lowercase()
            if (na.isNotEmpty() && na == nb) return true
            val aa = ba.address.trim().lowercase()
            val ab = bb.address.trim().lowercase()
            if (aa.isNotEmpty() && aa == ab) return true
        }
        val latA = ba?.lat
        val lonA = ba?.lon
        val latB = bb?.lat
        val lonB = bb?.lon
        if (latA != null && lonA != null && latB != null && lonB != null) {
            return haversineMeters(latA, lonA, latB, lonB) <= LOCATION_MATCH_METERS
        }
        return false
    }

    /** True if entry has place text and/or lat/lon in the location blob. */
    fun hasLocationData(e: FuelEntry): Boolean {
        val b = FuelLocationJson.parseBlob(e.location) ?: return false
        return b.hasPlace() || b.hasCoords()
    }

    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).let { it * it } +
            cos(p1) * cos(p2) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
