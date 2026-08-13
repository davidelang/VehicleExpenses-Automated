package com.davidlang.vehicleexpensesautomated.data.batch

import android.location.Location
import org.json.JSONObject

/**
 * Geo + place data stored in [com.davidlang.vehicleexpensesautomated.data.model.FuelEntry.location]
 * and [com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry.location].
 *
 * Canonical JSON blob:
 * ```
 * {
 *   "lat": 34.05, "lon": -118.24, "accuracyM": 14.5,
 *   "name": "Shell", "address": "123 Main St",
 *   "confirmed": true, "source": "overpass", "kind": "fuel_station",
 *   "lookedUpAt": 1710000000000
 * }
 * ```
 *
 * Legacy: plain text → name only; old `{"name","address"}` only; optional column lat/lon
 * folded at migration. Batch provenance belongs in notes, not location.
 */
object FuelLocationJson {

    data class Blob(
        val lat: Double? = null,
        val lon: Double? = null,
        val accuracyM: Double? = null,
        val name: String = "",
        val address: String = "",
        val confirmed: Boolean = false,
        val source: String? = null,
        val kind: String? = null,
        val lookedUpAt: Long? = null,
    ) {
        fun hasCoords(): Boolean = lat != null && lon != null

        fun hasPlace(): Boolean = name.isNotBlank() || address.isNotBlank()

        /** Pending deferred lookup: coords present, no place payload. */
        fun hasCoordsWithoutPlace(): Boolean = hasCoords() && !hasPlace()

        fun isBlank(): Boolean =
            !hasCoords() && !hasPlace() && accuracyM == null && source.isNullOrBlank() &&
                kind.isNullOrBlank() && lookedUpAt == null

        fun displayLine(): String = when {
            name.isNotBlank() && address.isNotBlank() -> "$name — $address"
            name.isNotBlank() -> name
            address.isNotBlank() -> address
            else -> ""
        }

        /** Coords + accuracy only (clear place / confirmation fields). */
        fun coordsOnly(): Blob = Blob(
            lat = lat,
            lon = lon,
            accuracyM = accuracyM,
        )

        fun withCoords(
            lat: Double?,
            lon: Double?,
            accuracyM: Double? = this.accuracyM,
        ): Blob = copy(lat = lat, lon = lon, accuracyM = accuracyM)

        fun withPlace(
            name: String?,
            address: String?,
            confirmed: Boolean = this.confirmed,
            source: String? = this.source,
            kind: String? = this.kind,
            lookedUpAt: Long? = this.lookedUpAt,
        ): Blob = copy(
            name = name?.trim().orEmpty(),
            address = address?.trim().orEmpty(),
            confirmed = confirmed,
            source = source,
            kind = kind,
            lookedUpAt = lookedUpAt,
        )

        fun clearPlace(): Blob = copy(
            name = "",
            address = "",
            confirmed = false,
            source = null,
            kind = null,
            lookedUpAt = null,
        )
    }

    /** @deprecated Prefer [Blob]; kept for callers that only need place display. */
    data class Place(val name: String = "", val address: String = "") {
        fun isBlank(): Boolean = name.isBlank() && address.isBlank()
        fun displayLine(): String = when {
            name.isNotBlank() && address.isNotBlank() -> "$name — $address"
            name.isNotBlank() -> name
            address.isNotBlank() -> address
            else -> ""
        }
    }

    fun fromLocation(location: Location?): Blob? {
        if (location == null) return null
        val acc = if (location.hasAccuracy()) location.accuracy.toDouble() else null
        return Blob(
            lat = location.latitude,
            lon = location.longitude,
            accuracyM = acc,
            source = "device",
        )
    }

    fun fromCoords(
        lat: Double?,
        lon: Double?,
        accuracyM: Double? = null,
        source: String? = null,
    ): Blob? {
        if (lat == null || lon == null) {
            return if (accuracyM != null) Blob(accuracyM = accuracyM, source = source) else null
        }
        return Blob(lat = lat, lon = lon, accuracyM = accuracyM, source = source)
    }

    fun format(name: String?, address: String?): String? {
        val n = name?.trim().orEmpty()
        val a = address?.trim().orEmpty()
        if (n.isEmpty() && a.isEmpty()) return null
        return encode(Blob(name = n, address = a))
    }

    fun encode(blob: Blob?): String? {
        if (blob == null || blob.isBlank()) return null
        return JSONObject().apply {
            blob.lat?.let { put("lat", it) }
            blob.lon?.let { put("lon", it) }
            blob.accuracyM?.let { put("accuracyM", it) }
            if (blob.name.isNotBlank()) put("name", blob.name)
            if (blob.address.isNotBlank()) put("address", blob.address)
            if (blob.confirmed) put("confirmed", true)
            if (!blob.confirmed && blob.hasPlace()) put("confirmed", false)
            blob.source?.takeIf { it.isNotBlank() }?.let { put("source", it) }
            blob.kind?.takeIf { it.isNotBlank() }?.let { put("kind", it) }
            blob.lookedUpAt?.let { put("lookedUpAt", it) }
        }.toString()
    }

    fun parseBlob(location: String?): Blob? {
        if (location.isNullOrBlank()) return null
        val t = location.trim()
        if (t.startsWith("{")) {
            return try {
                val o = JSONObject(t)
                val lat = if (o.has("lat") && !o.isNull("lat")) o.getDouble("lat") else null
                val lon = if (o.has("lon") && !o.isNull("lon")) o.getDouble("lon") else null
                // Legacy keys from older dual-column + place experiments
                val lat2 = lat ?: if (o.has("latitude") && !o.isNull("latitude")) o.getDouble("latitude") else null
                val lon2 = lon ?: if (o.has("longitude") && !o.isNull("longitude")) o.getDouble("longitude") else null
                val acc = if (o.has("accuracyM") && !o.isNull("accuracyM")) o.getDouble("accuracyM") else null
                val name = o.optString("name", "").trim()
                val address = o.optString("address", "").trim()
                val confirmed = when {
                    o.has("confirmed") -> o.optBoolean("confirmed", false)
                    else -> false
                }
                val source = o.optString("source", "").trim().ifBlank { null }
                val kind = o.optString("kind", "").trim().ifBlank { null }
                val lookedUpAt = if (o.has("lookedUpAt") && !o.isNull("lookedUpAt")) {
                    o.getLong("lookedUpAt")
                } else {
                    null
                }
                Blob(
                    lat = lat2,
                    lon = lon2,
                    accuracyM = acc,
                    name = name,
                    address = address,
                    confirmed = confirmed,
                    source = source,
                    kind = kind,
                    lookedUpAt = lookedUpAt,
                ).takeUnless { it.isBlank() }
            } catch (_: Exception) {
                Blob(name = t).takeUnless { it.isBlank() }
            }
        }
        if (t.startsWith("batch_")) return null
        return Blob(name = t)
    }

    fun parse(location: String?): Place? {
        val b = parseBlob(location) ?: return null
        return Place(name = b.name, address = b.address).takeUnless { it.isBlank() }
    }

    fun displayLine(location: String?): String =
        parseBlob(location)?.displayLine().orEmpty()

    fun hasCoords(location: String?): Boolean =
        parseBlob(location)?.hasCoords() == true

    fun hasCoordsWithoutPlace(location: String?): Boolean =
        parseBlob(location)?.hasCoordsWithoutPlace() == true

    fun hasPlace(location: String?): Boolean =
        parseBlob(location)?.hasPlace() == true

    fun lat(location: String?): Double? = parseBlob(location)?.lat
    fun lon(location: String?): Double? = parseBlob(location)?.lon
    fun accuracyM(location: String?): Double? = parseBlob(location)?.accuracyM

    /**
     * Merge two location blobs for sync (same entity identity).
     * 1) Prefer side with place data if the other has none.
     * 2) Confirmed place trumps unconfirmed.
     * 3) Same confirmed-ness → later lookedUpAt, else later [updatedAtA]/[updatedAtB].
     * Winner takes entire blob.
     */
    fun mergeBlobs(
        aRaw: String?,
        bRaw: String?,
        updatedAtA: Long = 0L,
        updatedAtB: Long = 0L,
    ): String? {
        val a = parseBlob(aRaw)
        val b = parseBlob(bRaw)
        if (a == null) return encode(b) ?: bRaw
        if (b == null) return encode(a) ?: aRaw
        val winner = when {
            a.hasPlace() && !b.hasPlace() -> a
            b.hasPlace() && !a.hasPlace() -> b
            a.hasPlace() && b.hasPlace() -> {
                when {
                    a.confirmed && !b.confirmed -> a
                    b.confirmed && !a.confirmed -> b
                    else -> {
                        val la = a.lookedUpAt
                        val lb = b.lookedUpAt
                        when {
                            la != null && lb != null -> if (la >= lb) a else b
                            la != null && lb == null -> a
                            la == null && lb != null -> b
                            else -> if (updatedAtA >= updatedAtB) a else b
                        }
                    }
                }
            }
            // neither has place — prefer coords if one side only, else newer updatedAt
            a.hasCoords() && !b.hasCoords() -> a
            b.hasCoords() && !a.hasCoords() -> b
            else -> if (updatedAtA >= updatedAtB) a else b
        }
        return encode(winner)
    }

    /**
     * Place provenance for confirm-save: keep Overpass/Nominatim source when user confirms
     * without editing name/address; otherwise `"user"`.
     */
    fun placeSourceForConfirm(
        name: String,
        address: String,
        lookupName: String?,
        lookupAddress: String?,
        lookupSource: String?,
    ): String {
        val n = name.trim()
        val a = address.trim()
        val ln = lookupName?.trim().orEmpty()
        val la = lookupAddress?.trim().orEmpty()
        val src = lookupSource?.trim().orEmpty()
        if (src.isNotBlank() && n == ln && a == la) return src
        return "user"
    }

    /**
     * Fold legacy **Room column** lat/lon + existing location text into one blob.
     * **Device DB upgrade only** ([MIGRATION_17_18]) — not for sheet/CSV import.
     */
    fun foldLegacy(
        columnLat: Double?,
        columnLon: Double?,
        existingLocation: String?,
    ): String? {
        val existing = parseBlob(existingLocation)
        val lat = columnLat ?: existing?.lat
        val lon = columnLon ?: existing?.lon
        if (lat == null && lon == null && existing == null) return null
        val merged = (existing ?: Blob()).copy(
            lat = lat,
            lon = lon,
            // place without confirmed flag stays confirmed=false
        )
        return encode(merged)
    }
}
