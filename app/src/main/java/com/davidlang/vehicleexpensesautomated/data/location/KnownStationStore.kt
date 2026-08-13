package com.davidlang.vehicleexpensesautomated.data.location

import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.dao.KnownStationDao
import com.davidlang.vehicleexpensesautomated.data.model.KnownStation
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdGenerator
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed class StationMatch {
    data class Unique(val station: KnownStation, val distanceM: Double) : StationMatch()
    data class Ambiguous(val stations: List<Pair<KnownStation, Double>>) : StationMatch()
    data object None : StationMatch()
}

/**
 * Room directory of known stations: one-shot seed from confirmed fuels,
 * nearest-within-R match, and upsert on user confirm.
 */
@Singleton
class KnownStationStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: KnownStationDao,
    private val fuelRepository: FuelEntryRepository,
) {
    companion object {
        private const val TAG = "KnownStationStore"
        const val SEED_CLUSTER_M = 75.0
        const val QF_MATCH_M = 100.0
        const val UPSERT_CLUSTER_M = 75.0
    }

    private val seedMutex = Mutex()

    suspend fun seedFromConfirmedFuelsIfEmpty() {
        seedMutex.withLock {
            if (dao.countAll() > 0) return
            val points = collectSeedPoints()
            if (points.isEmpty()) {
                Log.i(TAG, "seed skip: no confirmed fuel places")
                return
            }
            val clusters = clusterSingleLinkage(points, SEED_CLUSTER_M)
            val deviceId = SyncIdentity.getOrCreateDeviceId(context)
            val now = System.currentTimeMillis()
            val stations = clusters.map { cluster ->
                val medoid = medoidOf(cluster)
                val named = pickNameMember(cluster)
                KnownStation(
                    syncId = SyncIdGenerator.randomSyncId(),
                    name = named.name,
                    address = named.address,
                    lat = medoid.lat,
                    lon = medoid.lon,
                    accuracyM = medoid.accuracyM,
                    kind = KnownStation.KIND_FUEL_STATION,
                    source = KnownStation.SOURCE_SEED,
                    originDeviceId = deviceId,
                    updatedAt = now,
                    deleted = false,
                    deletedAt = null,
                )
            }
            dao.insertAll(stations)
            Log.i(TAG, "seed inserted ${stations.size} stations from ${points.size} confirmed fills")
        }
    }

    suspend fun getAllLive(): List<KnownStation> {
        seedFromConfirmedFuelsIfEmpty()
        return dao.getAllLive()
    }

    suspend fun getAllIncludingDeleted(): List<KnownStation> {
        seedFromConfirmedFuelsIfEmpty()
        return dao.getAllIncludingDeleted()
    }

    /** Live stations within [radiusM], nearest first. */
    suspend fun nearestWithin(lat: Double, lon: Double, radiusM: Double): List<Pair<KnownStation, Double>> {
        seedFromConfirmedFuelsIfEmpty()
        return dao.getAllLive()
            .map { it to GeoMath.haversineM(lat, lon, it.lat, it.lon) }
            .filter { it.second <= radiusM }
            .sortedBy { it.second }
    }

    suspend fun matchNearest(lat: Double, lon: Double, radiusM: Double = QF_MATCH_M): StationMatch {
        val hits = nearestWithin(lat, lon, radiusM)
        return when {
            hits.isEmpty() -> StationMatch.None
            hits.size == 1 -> StationMatch.Unique(hits[0].first, hits[0].second)
            else -> StationMatch.Ambiguous(hits)
        }
    }

    /**
     * Update existing live station within 75 m, else insert.
     * Bumps [KnownStation.updatedAt]. Does not change fuel-row coords.
     */
    suspend fun upsertFromConfirm(
        name: String,
        address: String,
        lat: Double,
        lon: Double,
        accuracyM: Double? = null,
        source: String = KnownStation.SOURCE_USER,
        kind: String = KnownStation.KIND_FUEL_STATION,
    ): KnownStation? {
        val n = name.trim()
        val a = address.trim()
        if (n.isBlank() && a.isBlank()) return null
        seedFromConfirmedFuelsIfEmpty()
        val now = System.currentTimeMillis()
        val deviceId = SyncIdentity.getOrCreateDeviceId(context)
        val existing = nearestWithin(lat, lon, UPSERT_CLUSTER_M).firstOrNull()?.first
        return if (existing != null) {
            val updated = existing.copy(
                name = n.ifBlank { existing.name },
                address = a.ifBlank { existing.address },
                accuracyM = accuracyM ?: existing.accuracyM,
                kind = kind.ifBlank { existing.kind },
                source = source.ifBlank { existing.source },
                originDeviceId = existing.originDeviceId.ifBlank { deviceId },
                updatedAt = now,
                deleted = false,
                deletedAt = null,
            )
            dao.update(updated)
            Log.i(TAG, "upsert update syncId=${updated.syncId} name=${updated.name}")
            updated
        } else {
            val created = KnownStation(
                syncId = SyncIdGenerator.randomSyncId(),
                name = n,
                address = a,
                lat = lat,
                lon = lon,
                accuracyM = accuracyM,
                kind = kind.ifBlank { KnownStation.KIND_FUEL_STATION },
                source = source.ifBlank { KnownStation.SOURCE_USER },
                originDeviceId = deviceId,
                updatedAt = now,
                deleted = false,
                deletedAt = null,
            )
            dao.insert(created)
            Log.i(TAG, "upsert insert syncId=${created.syncId} name=${created.name}")
            created
        }
    }

    suspend fun upsertFromSync(station: KnownStation) {
        if (station.syncId.isBlank()) {
            Log.w(TAG, "upsertFromSync skip blank syncId")
            return
        }
        val existing = dao.findBySyncId(station.syncId)
        if (existing != null) {
            dao.update(station.copy(syncId = existing.syncId))
        } else {
            dao.insert(station)
        }
    }

    private suspend fun collectSeedPoints(): List<SeedPoint> {
        val fuels = fuelRepository.getAllIncludingDeleted()
        val out = ArrayList<SeedPoint>()
        for (fuel in fuels) {
            val blob = FuelLocationJson.parseBlob(fuel.location) ?: continue
            if (!blob.hasCoords()) continue
            val lat = blob.lat ?: continue
            val lon = blob.lon ?: continue
            val userPlace = blob.source == KnownStation.SOURCE_USER && blob.hasPlace()
            if (!blob.confirmed && !userPlace) continue
            if (!blob.hasPlace()) continue
            out.add(
                SeedPoint(
                    lat = lat,
                    lon = lon,
                    name = blob.name.trim(),
                    address = blob.address.trim(),
                    accuracyM = blob.accuracyM,
                    confirmed = blob.confirmed,
                    lookedUpAt = blob.lookedUpAt,
                    timestamp = fuel.timestamp,
                ),
            )
        }
        return out
    }

    private fun clusterSingleLinkage(points: List<SeedPoint>, radiusM: Double): List<List<SeedPoint>> {
        val n = points.size
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var i = x
            while (parent[i] != i) {
                parent[i] = parent[parent[i]]
                i = parent[i]
            }
            return i
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (GeoMath.haversineM(points[i].lat, points[i].lon, points[j].lat, points[j].lon) <= radiusM) {
                    union(i, j)
                }
            }
        }
        val groups = LinkedHashMap<Int, MutableList<SeedPoint>>()
        for (i in 0 until n) {
            groups.getOrPut(find(i)) { mutableListOf() }.add(points[i])
        }
        return groups.values.toList()
    }

    private fun medoidOf(cluster: List<SeedPoint>): SeedPoint {
        if (cluster.size == 1) return cluster[0]
        return cluster.minBy { a ->
            cluster.sumOf { b -> GeoMath.haversineM(a.lat, a.lon, b.lat, b.lon) }
        }
    }

    /** Prefer non-blank name; last confirmed (latest lookedUpAt / fill timestamp) wins. */
    private fun pickNameMember(cluster: List<SeedPoint>): SeedPoint {
        val named = cluster.filter { it.name.isNotBlank() }.ifEmpty { cluster }
        return named.maxWith(
            compareBy<SeedPoint> { it.confirmed }
                .thenBy { it.lookedUpAt ?: 0L }
                .thenBy { it.timestamp },
        )
    }

    private data class SeedPoint(
        val lat: Double,
        val lon: Double,
        val name: String,
        val address: String,
        val accuracyM: Double?,
        val confirmed: Boolean,
        val lookedUpAt: Long?,
        val timestamp: Long,
    )
}
