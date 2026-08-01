package com.davidlang.vehicleexpensesautomated.data.location

import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson

/**
 * Non-blocking-friendly facade: POI then address fallback as required by [LocationLookupKind].
 * Never throws.
 */
object LocationLookup {
    private const val TAG = "LocationLookup"

    /**
     * Expense category → AUTO_SERVICE when repair/parts-like; else ADDRESS_ONLY.
     */
    fun kindForExpenseCategory(category: String?): LocationLookupKind {
        val c = category.orEmpty().lowercase()
        val tokens = listOf(
            "repair", "parts", "maintenance", "service", "auto", "tire", "tyre", "oil",
        )
        return if (tokens.any { c.contains(it) }) {
            LocationLookupKind.AUTO_SERVICE
        } else {
            LocationLookupKind.ADDRESS_ONLY
        }
    }

    suspend fun lookup(
        lat: Double,
        lon: Double,
        kind: LocationLookupKind,
        accuracyM: Double? = null,
        uiTimeout: Boolean = true,
    ): LocationLookupResult? {
        val overpassTimeout = if (uiTimeout) OverpassClient.UI_TIMEOUT_MS else OverpassClient.WORKER_TIMEOUT_MS
        val nominatimTimeout = if (uiTimeout) NominatimClient.UI_TIMEOUT_MS else NominatimClient.WORKER_TIMEOUT_MS
        return try {
            when (kind) {
                LocationLookupKind.FUEL_STATION -> {
                    OverpassClient.nearestFuelStation(lat, lon, accuracyM, overpassTimeout)
                        ?: NominatimClient.reverseAddress(lat, lon, nominatimTimeout)
                            ?.copy(kind = LocationLookupKind.FUEL_STATION)
                }
                LocationLookupKind.AUTO_SERVICE -> {
                    OverpassClient.nearestAutoService(lat, lon, accuracyM, overpassTimeout)
                        ?: NominatimClient.reverseAddress(lat, lon, nominatimTimeout)
                            ?.copy(kind = LocationLookupKind.AUTO_SERVICE)
                }
                LocationLookupKind.ADDRESS_ONLY -> {
                    NominatimClient.reverseAddress(lat, lon, nominatimTimeout)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "lookup failed: ${e.message}")
            null
        }
    }

    /** Merge lookup place into existing coords blob; [confirmed] default false for silent/worker. */
    fun mergePlaceIntoBlob(
        existingLocationJson: String?,
        result: LocationLookupResult,
        confirmed: Boolean = false,
    ): String? {
        val base = FuelLocationJson.parseBlob(existingLocationJson) ?: FuelLocationJson.Blob()
        if (!base.hasCoords() && result.hasPlace().not()) return existingLocationJson
        val updated = base.withPlace(
            name = result.name,
            address = result.address,
            confirmed = confirmed,
            source = result.source,
            kind = result.kind.blobKindTag(),
            lookedUpAt = result.lookedUpAt,
        )
        return FuelLocationJson.encode(updated)
    }
}
