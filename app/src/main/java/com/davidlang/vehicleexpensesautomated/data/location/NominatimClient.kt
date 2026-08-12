package com.davidlang.vehicleexpensesautomated.data.location

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * OSM Nominatim reverse geocode (address only). Keyless; polite User-Agent required.
 * Never throws to callers — returns null on miss/timeout/error.
 */
object NominatimClient {
    private const val TAG = "NominatimClient"
    private const val ENDPOINT = "https://nominatim.openstreetmap.org/reverse"
    private const val SEARCH_ENDPOINT = "https://nominatim.openstreetmap.org/search"
    const val UI_TIMEOUT_MS = 4_000L
    const val WORKER_TIMEOUT_MS = 12_000L
    private const val USER_AGENT = "VehicleExpensesAutomated/1.0 (android; location lookup)"

    /**
     * Fuel amenity search (not reverse). Reverse street is never a station name.
     */
    suspend fun searchFuel(
        lat: Double,
        lon: Double,
        timeoutMs: Long = UI_TIMEOUT_MS,
        limit: Int = 15,
    ): List<LocationLookupResult> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            try {
                val q = URLEncoder.encode("fuel", StandardCharsets.UTF_8.name())
                val url = URL(
                    "$SEARCH_ENDPOINT?format=jsonv2&q=$q&lat=$lat&lon=$lon&limit=$limit" +
                        "&addressdetails=1&dedupe=1",
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs.toInt().coerceAtMost(15_000)
                    readTimeout = timeoutMs.toInt().coerceAtMost(15_000)
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        Log.w(TAG, "HTTP $code nominatim fuel $lat,$lon")
                        return@withTimeoutOrNull emptyList()
                    }
                    val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    parseSearchFuel(body, lat, lon)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "search fuel failed: ${e.message}")
                emptyList()
            }
        } ?: emptyList()
    }

    suspend fun reverseAddress(
        lat: Double,
        lon: Double,
        timeoutMs: Long = UI_TIMEOUT_MS,
    ): LocationLookupResult? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            try {
                val q = "format=json&lat=${lat}&lon=${lon}&zoom=18&addressdetails=1"
                val url = URL("$ENDPOINT?$q")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs.toInt().coerceAtMost(15_000)
                    readTimeout = timeoutMs.toInt().coerceAtMost(15_000)
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        Log.w(TAG, "HTTP $code for reverse $lat,$lon")
                        return@withTimeoutOrNull null
                    }
                    val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    parseReverse(body)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "reverse failed: ${e.message}")
                null
            }
        }
    }

    private fun parseSearchFuel(
        body: String,
        originLat: Double,
        originLon: Double,
    ): List<LocationLookupResult> {
        return try {
            val arr = JSONArray(body)
            val out = ArrayList<LocationLookupResult>()
            val seen = HashSet<String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val lat = o.optDouble("lat", Double.NaN)
                val lon = o.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                val type = o.optString("type", "").lowercase()
                val cls = o.optString("class", "").lowercase()
                val isFuel = type == "fuel" || cls == "amenity"
                if (!isFuel) continue
                val name = o.optString("name", "").trim()
                val addr = o.optJSONObject("address")
                val address = buildStreetLine(addr).orEmpty()
                val d = GeoMath.haversineM(originLat, originLon, lat, lon)
                val displayName = name.ifBlank { LocationLookup.UNNAMED_FUEL }
                val key = "${displayName.lowercase()}|${"%.5f".format(lat)}|${"%.5f".format(lon)}"
                if (!seen.add(key)) continue
                out.add(
                    LocationLookupResult(
                        name = displayName,
                        address = address,
                        source = "nominatim",
                        kind = LocationLookupKind.FUEL_STATION,
                        distanceM = d,
                        poiLat = lat,
                        poiLon = lon,
                    ),
                )
            }
            out.sortedBy { it.distanceM ?: Double.MAX_VALUE }
        } catch (e: Exception) {
            Log.w(TAG, "parse search fuel: ${e.message}")
            emptyList()
        }
    }

    private fun parseReverse(body: String): LocationLookupResult? {
        return try {
            val o = JSONObject(body)
            val display = o.optString("display_name", "").trim()
            val addr = o.optJSONObject("address")
            val line = buildStreetLine(addr) ?: display
            if (line.isBlank()) return null
            LocationLookupResult(
                name = "",
                address = line,
                source = "nominatim",
                kind = LocationLookupKind.ADDRESS_ONLY,
            )
        } catch (e: Exception) {
            Log.w(TAG, "parse reverse: ${e.message}")
            null
        }
    }

    private fun buildStreetLine(addr: JSONObject?): String? {
        if (addr == null) return null
        val num = addr.optString("house_number", "").trim()
        val road = addr.optString("road", "").ifBlank {
            addr.optString("pedestrian", "").ifBlank {
                addr.optString("residential", "")
            }
        }.trim()
        val city = addr.optString("city", "").ifBlank {
            addr.optString("town", "").ifBlank {
                addr.optString("village", "").ifBlank {
                    addr.optString("municipality", "")
                }
            }
        }.trim()
        val state = addr.optString("state", "").trim()
        val street = when {
            num.isNotBlank() && road.isNotBlank() -> "$num $road"
            road.isNotBlank() -> road
            else -> ""
        }
        val parts = listOf(street, city, state).filter { it.isNotBlank() }
        return parts.joinToString(", ").ifBlank { null }
    }
}
