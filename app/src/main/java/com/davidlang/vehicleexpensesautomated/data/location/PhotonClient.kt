package com.davidlang.vehicleexpensesautomated.data.location

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Komoot Photon keyless fuel POI search. Never throws; empty on miss/timeout.
 */
object PhotonClient {
    private const val TAG = "PhotonClient"
    private const val ENDPOINT = "https://photon.komoot.io/api/"
    private const val USER_AGENT = "VehicleExpensesAutomated/1.0 (android; location lookup)"
    const val UI_TIMEOUT_MS = 4_000L
    const val WORKER_TIMEOUT_MS = 12_000L

    suspend fun listFuel(
        lat: Double,
        lon: Double,
        timeoutMs: Long = UI_TIMEOUT_MS,
        limit: Int = 15,
    ): List<LocationLookupResult> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            try {
                val q = URLEncoder.encode("fuel", StandardCharsets.UTF_8.name())
                val url = URL(
                    "${ENDPOINT}?q=$q&lat=$lat&lon=$lon&limit=$limit&lang=en" +
                        "&osm_tag=amenity:fuel",
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
                        Log.w(TAG, "HTTP $code photon fuel $lat,$lon")
                        return@withTimeoutOrNull emptyList()
                    }
                    val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    parseFeatures(body, lat, lon)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "photon fuel failed: ${e.message}")
                emptyList()
            }
        } ?: emptyList()
    }

    private fun parseFeatures(
        body: String,
        originLat: Double,
        originLon: Double,
    ): List<LocationLookupResult> {
        return try {
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: return emptyList()
            val out = ArrayList<LocationLookupResult>()
            val seen = HashSet<String>()
            for (i in 0 until features.length()) {
                val f = features.optJSONObject(i) ?: continue
                val geom = f.optJSONObject("geometry") ?: continue
                val coords = geom.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue
                val lon = coords.optDouble(0, Double.NaN)
                val lat = coords.optDouble(1, Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                val props = f.optJSONObject("properties") ?: JSONObject()
                val osmValue = props.optString("osm_value", "")
                val osmKey = props.optString("osm_key", "")
                if (osmKey.isNotBlank() && osmValue.isNotBlank() &&
                    !(osmKey == "amenity" && osmValue == "fuel")
                ) {
                    continue
                }
                val name = props.optString("name", "").ifBlank {
                    props.optString("osm_value", "")
                }.trim()
                val address = buildAddr(props)
                val d = GeoMath.haversineM(originLat, originLon, lat, lon)
                val displayName = name.ifBlank { LocationLookup.UNNAMED_FUEL }
                val key = "${displayName.lowercase()}|${"%.5f".format(lat)}|${"%.5f".format(lon)}"
                if (!seen.add(key)) continue
                out.add(
                    LocationLookupResult(
                        name = displayName,
                        address = address,
                        source = "photon",
                        kind = LocationLookupKind.FUEL_STATION,
                        distanceM = d,
                        poiLat = lat,
                        poiLon = lon,
                    ),
                )
            }
            out.sortedBy { it.distanceM ?: Double.MAX_VALUE }
        } catch (e: Exception) {
            Log.w(TAG, "parse photon: ${e.message}")
            emptyList()
        }
    }

    private fun buildAddr(props: JSONObject): String {
        val num = props.optString("housenumber", "").trim()
        val street = props.optString("street", "").trim()
        val city = props.optString("city", "").trim()
        val streetPart = when {
            num.isNotBlank() && street.isNotBlank() -> "$num $street"
            street.isNotBlank() -> street
            else -> ""
        }
        return listOf(streetPart, city).filter { it.isNotBlank() }.joinToString(", ")
    }
}
