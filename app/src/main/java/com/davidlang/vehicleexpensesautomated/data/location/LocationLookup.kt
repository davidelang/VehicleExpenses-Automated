package com.davidlang.vehicleexpensesautomated.data.location

import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.model.KnownStation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.max

/**
 * Non-blocking-friendly facade: known-stations table, then POI, then address fallback.
 * Never throws.
 */
object LocationLookup {
    private const val TAG = "LocationLookup"
    const val UNNAMED_FUEL = "(fuel)"
    const val AUTO_MAX_D1_M = 150.0

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
        val photonTimeout = if (uiTimeout) PhotonClient.UI_TIMEOUT_MS else PhotonClient.WORKER_TIMEOUT_MS
        return try {
            when (kind) {
                LocationLookupKind.FUEL_STATION -> {
                    suggestFuelFromFreeNetwork(lat, lon, photonTimeout, nominatimTimeout)
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
     * Nearby POIs for Wrong-station picker, nearest first.
     * Fuel: known stations + Photon + Nominatim fuel + optional Overpass; unnamed = "(fuel)".
     * ADDRESS_ONLY → empty (no picker).
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
        val photonTimeout = if (uiTimeout) PhotonClient.UI_TIMEOUT_MS else PhotonClient.WORKER_TIMEOUT_MS
        return try {
            when (kind) {
                LocationLookupKind.FUEL_STATION -> {
                    val network = listFuelNetwork(
                        lat = lat,
                        lon = lon,
                        radiusM = radiusM,
                        photonTimeout = photonTimeout,
                        nominatimTimeout = nominatimTimeout,
                        overpassTimeout = overpassTimeout,
                    )
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

    /**
     * Photon + Nominatim fuel list; auto only when [acceptAuto] ratio holds.
     * Reverse street is never a station name. Overpass not required.
     */
    private suspend fun suggestFuelFromFreeNetwork(
        lat: Double,
        lon: Double,
        photonTimeout: Long,
        nominatimTimeout: Long,
    ): LocationLookupResult? {
        val merged = coroutineScope {
            val photon = async { PhotonClient.listFuel(lat, lon, photonTimeout) }
            val nomi = async { NominatimClient.searchFuel(lat, lon, nominatimTimeout) }
            mergeTableAndNetwork(photon.await(), nomi.await())
        }
        return acceptAuto(merged)
    }

    private suspend fun listFuelNetwork(
        lat: Double,
        lon: Double,
        radiusM: Double,
        photonTimeout: Long,
        nominatimTimeout: Long,
        overpassTimeout: Long,
    ): List<LocationLookupResult> = coroutineScope {
        val photon = async { PhotonClient.listFuel(lat, lon, photonTimeout) }
        val nomi = async { NominatimClient.searchFuel(lat, lon, nominatimTimeout) }
        val overpass = async {
            try {
                OverpassClient.listFuelStations(lat, lon, radiusM, overpassTimeout)
            } catch (e: Exception) {
                Log.w(TAG, "optional Overpass list: ${e.message}")
                emptyList()
            }
        }
        val free = mergeTableAndNetwork(photon.await(), nomi.await())
            .filter { (it.distanceM ?: Double.MAX_VALUE) <= radiusM }
        val extra = overpass.await()
            .map { if (it.name.isBlank()) it.copy(name = UNNAMED_FUEL) else it }
            .filter { (it.distanceM ?: Double.MAX_VALUE) <= radiusM }
        mergeTableAndNetwork(free, extra)
    }

    /**
     * Auto-use nearest **named** station when d1 ≤ 150 m and
     * d2 ≥ max(d1+40, 2×d1) (or there is no second hit).
     */
    fun acceptAuto(results: List<LocationLookupResult>): LocationLookupResult? {
        val named = results
            .filter { isNamedStation(it.name) }
            .sortedBy { it.distanceM ?: Double.MAX_VALUE }
        val first = named.firstOrNull() ?: return null
        val d1 = first.distanceM ?: return null
        if (d1 > AUTO_MAX_D1_M) return null
        val d2 = named.getOrNull(1)?.distanceM ?: Double.POSITIVE_INFINITY
        return if (d2 >= max(d1 + 40.0, 2.0 * d1)) first else null
    }

    fun isNamedStation(name: String): Boolean {
        val n = name.trim()
        return n.isNotBlank() && n != UNNAMED_FUEL && !n.equals("(unnamed)", ignoreCase = true)
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
