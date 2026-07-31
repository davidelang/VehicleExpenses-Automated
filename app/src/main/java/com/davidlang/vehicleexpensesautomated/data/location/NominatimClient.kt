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
 * OSM Nominatim reverse geocode (address only). Keyless; polite User-Agent required.
 * Never throws to callers — returns null on miss/timeout/error.
 */
object NominatimClient {
    private const val TAG = "NominatimClient"
    private const val ENDPOINT = "https://nominatim.openstreetmap.org/reverse"
    const val UI_TIMEOUT_MS = 4_000L
    const val WORKER_TIMEOUT_MS = 12_000L
    private const val USER_AGENT = "VehicleExpensesAutomated/1.0 (android; location lookup)"

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
