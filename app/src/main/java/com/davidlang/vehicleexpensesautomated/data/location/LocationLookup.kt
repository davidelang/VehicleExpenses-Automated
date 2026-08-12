package com.davidlang.vehicleexpensesautomated.data.location

import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.model.KnownStation

/**
 * Non-blocking-friendly facade: known-stations table, then POI, then address fallback.
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
        stationStore: KnownStationStore? = null,
    ): LocationLookupResult? {
        if (kind == LocationLookupKind.FUEL_STATION && stationStore != null) {
            when (val match = stationStore.matchNearest(lat, lon)) {
                is StationMatch.Unique -> return fromKnownStation(match.station, match.distanceM)
                is StationMatch.Ambiguous -> return null
                is StationMatch.None -> Unit
            }
        }
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

    /**
     * Nearby POIs for Wrong-station picker, sorted by distance ascending.
     * ADDRESS_ONLY → empty (no picker). Overpass first; Nominatim reverse as last-resort single row if empty.
     */
    suspend fun listNearby(
        lat: Double,
        lon: Double,
        kind: LocationLookupKind,
        radiusM: Double,
        uiTimeout: Boolean = true,
        stationStore: KnownStationStore? = null,
    ): List<LocationLookupResult> {
        val fromTable = if (kind == LocationLookupKind.FUEL_STATION && stationStore != null) {
            stationStore.nearestWithin(lat, lon, radiusM).map { (s, d) -> fromKnownStation(s, d) }
        } else {
            emptyList()
        }
        val overpassTimeout = if (uiTimeout) OverpassClient.UI_TIMEOUT_MS else OverpassClient.WORKER_TIMEOUT_MS
        val nominatimTimeout = if (uiTimeout) NominatimClient.UI_TIMEOUT_MS else NominatimClient.WORKER_TIMEOUT_MS
        return try {
            when (kind) {
                LocationLookupKind.FUEL_STATION -> {
                    val list = OverpassClient.listFuelStations(lat, lon, radiusM, overpassTimeout)
                    val network = if (list.isNotEmpty()) {
                        list
                    } else {
                        NominatimClient.reverseAddress(lat, lon, nominatimTimeout)
                            ?.copy(kind = LocationLookupKind.FUEL_STATION)
                            ?.let { listOf(it) }
                            .orEmpty()
                    }
                    mergeTableAndNetwork(fromTable, network)
                }
                LocationLookupKind.AUTO_SERVICE -> {
                    val list = OverpassClient.listAutoService(lat, lon, radiusM, overpassTimeout)
                    if (list.isNotEmpty()) list
                    else {
                        NominatimClient.reverseAddress(lat, lon, nominatimTimeout)
                            ?.copy(kind = LocationLookupKind.AUTO_SERVICE)
                            ?.let { listOf(it) }
                            .orEmpty()
                    }
                }
                LocationLookupKind.ADDRESS_ONLY -> emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "listNearby failed: ${e.message}")
            fromTable
        }
    }

    fun fromKnownStation(station: KnownStation, distanceM: Double? = null): LocationLookupResult =
        LocationLookupResult(
            name = station.name,
            address = station.address,
            source = KnownStation.SOURCE_STATIONS,
            kind = LocationLookupKind.FUEL_STATION,
            distanceM = distanceM,
            poiLat = station.lat,
            poiLon = station.lon,
        )

    /** Table rows first; drop network hits within 75 m of a known station. */
    fun mergeTableAndNetwork(
        fromTable: List<LocationLookupResult>,
        fromNetwork: List<LocationLookupResult>,
    ): List<LocationLookupResult> {
        if (fromTable.isEmpty()) return fromNetwork
        if (fromNetwork.isEmpty()) return fromTable
        val kept = fromTable.toMutableList()
        for (n in fromNetwork) {
            val nLat = n.poiLat
            val nLon = n.poiLon
            val nearTable = if (nLat != null && nLon != null) {
                fromTable.any { t ->
                    val tLat = t.poiLat
                    val tLon = t.poiLon
                    tLat != null && tLon != null &&
                        GeoMath.haversineM(tLat, tLon, nLat, nLon) <= KnownStationStore.UPSERT_CLUSTER_M
                }
            } else {
                false
            }
            if (!nearTable) kept.add(n)
        }
        return kept.sortedBy { it.distanceM ?: Double.MAX_VALUE }
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
