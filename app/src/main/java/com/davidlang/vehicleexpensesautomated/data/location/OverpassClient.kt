package com.davidlang.vehicleexpensesautomated.data.location

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Overpass POI list/nearest for fuel stations and auto service/parts.
 * Never throws; returns empty/null on miss/timeout.
 */
object OverpassClient {
    private const val TAG = "OverpassClient"
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private const val USER_AGENT = "VehicleExpensesAutomated/1.0 (android; location lookup)"
    const val UI_TIMEOUT_MS = 5_000L
    const val WORKER_TIMEOUT_MS = 15_000L

    const val BASE_RADIUS_FUEL_M = 120.0
    const val MAX_RADIUS_FUEL_M = 300.0
    const val BASE_RADIUS_AUTO_M = 200.0
    const val MAX_RADIUS_AUTO_M = 400.0
    const val ACCURACY_SCALE_K = 1.0

    /** Picker default / max (product). */
    const val PICKER_INITIAL_RADIUS_M = 250.0
    const val PICKER_MAX_RADIUS_M = 2000.0

    /**
     * effectiveRadius = min(maxR, max(baseR, baseR + k * accuracyM))
     * Unknown accuracy → base only.
     */
    fun effectiveRadiusM(baseM: Double, maxM: Double, accuracyM: Double?): Double {
        val acc = accuracyM?.takeIf { it.isFinite() && it > 0 } ?: 0.0
        val scaled = baseM + ACCURACY_SCALE_K * acc
        return min(maxM, maxOf(baseM, scaled))
    }

    suspend fun nearestFuelStation(
        lat: Double,
        lon: Double,
        accuracyM: Double? = null,
        timeoutMs: Long = UI_TIMEOUT_MS,
    ): LocationLookupResult? {
        val r = effectiveRadiusM(BASE_RADIUS_FUEL_M, MAX_RADIUS_FUEL_M, accuracyM)
        return listFuelStations(lat, lon, r, timeoutMs).firstOrNull()
    }

    suspend fun nearestAutoService(
        lat: Double,
        lon: Double,
        accuracyM: Double? = null,
        timeoutMs: Long = UI_TIMEOUT_MS,
    ): LocationLookupResult? {
        val r = effectiveRadiusM(BASE_RADIUS_AUTO_M, MAX_RADIUS_AUTO_M, accuracyM)
        return listAutoService(lat, lon, r, timeoutMs).firstOrNull()
    }

    suspend fun listFuelStations(
        lat: Double,
        lon: Double,
        radiusM: Double,
        timeoutMs: Long = UI_TIMEOUT_MS,
    ): List<LocationLookupResult> {
        val r = radiusM.coerceIn(10.0, PICKER_MAX_RADIUS_M)
        val ql = """
            [out:json][timeout:12];
            (
              node(around:$r,$lat,$lon)["amenity"="fuel"];
              way(around:$r,$lat,$lon)["amenity"="fuel"];
            );
            out center tags;
        """.trimIndent()
        return queryList(ql, lat, lon, LocationLookupKind.FUEL_STATION, timeoutMs)
    }

    suspend fun listAutoService(
        lat: Double,
        lon: Double,
        radiusM: Double,
        timeoutMs: Long = UI_TIMEOUT_MS,
    ): List<LocationLookupResult> {
        val r = radiusM.coerceIn(10.0, PICKER_MAX_RADIUS_M)
        val ql = """
            [out:json][timeout:12];
            (
              node(around:$r,$lat,$lon)["shop"="car_repair"];
              way(around:$r,$lat,$lon)["shop"="car_repair"];
              node(around:$r,$lat,$lon)["shop"="car_parts"];
              way(around:$r,$lat,$lon)["shop"="car_parts"];
              node(around:$r,$lat,$lon)["shop"="tyres"];
              way(around:$r,$lat,$lon)["shop"="tyres"];
            );
            out center tags;
        """.trimIndent()
        return queryList(ql, lat, lon, LocationLookupKind.AUTO_SERVICE, timeoutMs)
    }

    private suspend fun queryList(
        ql: String,
        originLat: Double,
        originLon: Double,
        kind: LocationLookupKind,
        timeoutMs: Long,
    ): List<LocationLookupResult> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            try {
                val url = URL(ENDPOINT)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = timeoutMs.toInt().coerceAtMost(25_000)
                    readTimeout = timeoutMs.toInt().coerceAtMost(25_000)
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    conn.outputStream.use { out ->
                        val body = "data=" + java.net.URLEncoder.encode(ql, "UTF-8")
                        out.write(body.toByteArray(StandardCharsets.UTF_8))
                    }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        Log.w(TAG, "HTTP $code Overpass")
                        return@withTimeoutOrNull emptyList()
                    }
                    val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    parseList(body, originLat, originLon, kind)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Overpass failed: ${e.message}")
                emptyList()
            }
        } ?: emptyList()
    }

    private fun parseList(
        body: String,
        originLat: Double,
        originLon: Double,
        kind: LocationLookupKind,
    ): List<LocationLookupResult> {
        return try {
            val root = JSONObject(body)
            val elements = root.optJSONArray("elements") ?: return emptyList()
            val out = ArrayList<LocationLookupResult>()
            val seen = HashSet<String>()
            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                val lat = el.optDouble("lat", Double.NaN).takeIf { !it.isNaN() }
                    ?: el.optJSONObject("center")?.optDouble("lat", Double.NaN)?.takeIf { !it.isNaN() }
                    ?: continue
                val lon = el.optDouble("lon", Double.NaN).takeIf { !it.isNaN() }
                    ?: el.optJSONObject("center")?.optDouble("lon", Double.NaN)?.takeIf { !it.isNaN() }
                    ?: continue
                val tags = el.optJSONObject("tags") ?: JSONObject()
                val name = tags.optString("brand", "").ifBlank {
                    tags.optString("name", "")
                }.trim()
                val address = buildAddr(tags)
                if (name.isBlank() && address.isBlank()) continue
                val d = haversineM(originLat, originLon, lat, lon)
                val key = "${name.lowercase()}|${"%.5f".format(lat)}|${"%.5f".format(lon)}"
                if (!seen.add(key)) continue
                out.add(
                    LocationLookupResult(
                        name = name,
                        address = address,
                        source = "overpass",
                        kind = kind,
                        distanceM = d,
                        poiLat = lat,
                        poiLon = lon,
                    ),
                )
            }
            out.sortedBy { it.distanceM ?: Double.MAX_VALUE }
        } catch (e: Exception) {
            Log.w(TAG, "parse Overpass list: ${e.message}")
            emptyList()
        }
    }

    private fun buildAddr(tags: JSONObject): String {
        val num = tags.optString("addr:housenumber", "").trim()
        val street = tags.optString("addr:street", "").trim()
        val city = tags.optString("addr:city", "").trim()
        val streetPart = when {
            num.isNotBlank() && street.isNotBlank() -> "$num $street"
            street.isNotBlank() -> street
            else -> ""
        }
        return listOf(streetPart, city).filter { it.isNotBlank() }.joinToString(", ")
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLam = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2) * sin(dPhi / 2) +
            cos(p1) * cos(p2) * sin(dLam / 2) * sin(dLam / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
