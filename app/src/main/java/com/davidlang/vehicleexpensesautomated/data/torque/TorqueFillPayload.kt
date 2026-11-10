package com.davidlang.vehicleexpensesautomated.data.torque

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

/**
 * Wire payload for one fill. Stored gallons and the stored odometer integer stay as they are.
 * Quantity is liters. Odometer is kilometers, omitted when the stored reading is not positive.
 */
object TorqueFillPayload {
    const val SOURCE_EMAIL = "email"
    const val SOURCE_OCR = "ocr"

    private val LITERS_PER_GALLON = BigDecimal("3.785411784")
    private val KM_PER_MILE = BigDecimal("1.609344")
    private val DECIMAL = Regex("(?:0|[1-9][0-9]{0,17})(?:\\.[0-9]{0,8}[1-9])?")
    private val ISO_VIN = Regex("[A-HJ-NPR-Z0-9]{17}")
    private const val MAX_EPOCH = 253402300799999L

    data class Frozen(val canonical: String, val hash: String)

    fun upsert(entry: FuelEntry, vehicle: Vehicle, build: String?): Frozen =
        freeze(body(entry, vehicle, build, operation = "upsert", voidReason = null))

    fun void(entry: FuelEntry, vehicle: Vehicle, voidReason: String, build: String?): Frozen =
        freeze(body(entry, vehicle, build, operation = "void", voidReason = voidReason))

    private fun freeze(body: Map<String, Any?>): Frozen {
        val canonical = TorqueFillJson.canonical(body)
        return Frozen(canonical, TorqueFillJson.sha256(canonical))
    }

    private fun body(
        entry: FuelEntry,
        vehicle: Vehicle,
        build: String?,
        operation: String,
        voidReason: String?,
    ): Map<String, Any?> {
        val voiding = operation == "void"
        val unit = UnitFormat.normalizeOdometerUnit(vehicle.odometerUnit)
        val km = if (voiding) null else kilometers(entry, unit)
        val rawKm = if (voiding || km == null || !km.fromRaw || km.observedAt == null) {
            null
        } else {
            mapOf(
                "kilometers" to km.text,
                "resolutionKm" to "0.1",
                "observedAtEpochMs" to km.observedAt,
            )
        }
        val odometer = if (km == null) {
            null
        } else {
            mapOf(
                "value" to km.text,
                "unit" to "km",
                "source" to wireOdometerSource(entry.odometerSource),
                "observedAtEpochMs" to km.observedAt,
            )
        }
        val quantityText = if (voiding) null else liters(entry.gallons)
        val costText = if (voiding) null else money(entry.cost)
        val quantity = quantityText?.let { mapOf("value" to it, "unit" to "L") }
        val cost = costText?.let { mapOf("value" to it, "currency" to currency(entry.currency)) }
        val fields = LinkedHashMap<String, Any?>()
        if (odometer != null) fields["/odometer"] = field(entry.odometerSource, km?.observedAt)
        if (quantity != null) fields["/quantity"] = field(entry.volumeSource, null)
        if (cost != null) fields["/cost"] = field(entry.costSource, null)
        val kind = sourceKind(fields)
        return mapOf(
            "operation" to operation,
            "vehicle" to mapOf(
                "syncId" to vehicleId(vehicle),
                "vin" to wireVin(vehicle.vin),
                "name" to bound(vehicle.name, 256),
                "make" to bound(vehicle.make, 128),
                "model" to bound(vehicle.model, 128),
                "year" to year(vehicle.year),
                "displayUnits" to displayUnits(unit),
            ),
            "occurredAtEpochMs" to (epochOrNull(entry.timestamp) ?: 0L),
            "quantity" to quantity,
            "cost" to cost,
            "fillExtent" to if (voiding) "unknown" else extent(entry),
            "economyIgnored" to if (voiding) false else entry.economyIgnored,
            "odometer" to odometer,
            "rawTorqueOdometer" to rawKm,
            "provenance" to mapOf(
                "originDeviceId" to origin(entry.originDeviceId),
                "sourceUpdatedAtEpochMs" to updated(entry.updatedAt),
                "sourceKind" to kind,
                "occurrenceTimeQuality" to if (entry.timestamp > 0L) "source_reported" else "unknown",
                "producerBuildAtResolution" to bound(build, 128),
                "fields" to fields,
            ),
            "voidReason" to voidReason,
        )
    }

    /** Full when odometer, cost, and volume are present and the partial checkbox is off. */
    fun extent(entry: FuelEntry): String {
        val complete = entry.odometer > 0 && entry.cost > 0.0 && entry.gallons > 0.0
        if (!complete) return "unknown"
        if (entry.isPartialFill) return "partial"
        return "full"
    }

    private data class Km(val text: String, val fromRaw: Boolean, val observedAt: Long?)

    private fun kilometers(entry: FuelEntry, unit: String): Km? {
        if (entry.odometer <= 0) return null
        val observed = epochOrNull(entry.timestamp)
        if (entry.odometerSource == "torque") {
            val raw = tenthsToKm(entry.torqueOdometerRawKm)
            if (raw != null) return Km(raw, fromRaw = true, observedAt = observed)
        }
        val converted = storedToKm(entry.odometer, unit) ?: return null
        return Km(converted, fromRaw = false, observedAt = observed)
    }

    /** Tenth-kilometer integer text, as kilometers. Zero is not a reading. */
    fun tenthsToKm(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.any { it !in '0'..'9' }) return null
        val km = BigDecimal(text).movePointLeft(1)
        return positiveDecimal(km)
    }

    fun storedToKm(odometer: Int, unit: String): String? {
        if (odometer <= 0) return null
        val n = BigDecimal(odometer)
        val km = if (UnitFormat.normalizeOdometerUnit(unit) == UnitFormat.KM) n else n.multiply(KM_PER_MILE)
        return positiveDecimal(km)
    }

    fun liters(gallons: Double): String? {
        if (gallons <= 0.0) return null
        return positiveDecimal(BigDecimal(gallons.toString()).multiply(LITERS_PER_GALLON))
    }

    private fun money(cost: Double): String? {
        if (cost <= 0.0) return null
        return positiveDecimal(BigDecimal(cost.toString()))
    }

    private fun positiveDecimal(value: BigDecimal): String? {
        if (value.signum() <= 0) return null
        val scaled = value.setScale(9, RoundingMode.HALF_UP)
        if (scaled.signum() <= 0) return null
        val plain = scaled.stripTrailingZeros().toPlainString()
        if (!DECIMAL.matches(plain)) return null
        return plain
    }

    private fun field(source: String, observedAt: Long?): Map<String, Any?> = mapOf(
        "source" to provenanceSource(source),
        "sourceRef" to null,
        "observedAtEpochMs" to observedAt,
        "confirmed" to null,
    )

    private fun sourceKind(fields: Map<String, Any?>): String {
        val kinds = fields.values.mapNotNull { row ->
            val source = (row as? Map<*, *>)?.get("source") as? String
            source?.takeIf { it != "legacy_unknown" }
        }.distinct()
        return when (kinds.size) {
            0 -> "legacy_unknown"
            1 -> kinds.single()
            else -> "mixed"
        }
    }

    private fun provenanceSource(source: String): String = when (source) {
        "photo" -> "ocr"
        "torque", "email", "ocr", "manual", "sync", "mixed", "legacy_unknown" -> source
        else -> "legacy_unknown"
    }

    private fun wireOdometerSource(source: String): String = when (source) {
        "torque", "photo", "manual" -> source
        else -> "unknown"
    }

    private fun currency(raw: String): String {
        val code = raw.trim().uppercase()
        return if (code.length == 3 && code.all { it in 'A'..'Z' }) code else "USD"
    }

    private fun year(year: Int?): Long? = year?.toLong()?.takeIf { it in 1000L..9999L }

    private fun epochOrNull(ms: Long): Long? = ms.takeIf { it in 0L..MAX_EPOCH }

    private fun updated(ms: Long): Long? = ms.takeIf { it in 1L..MAX_EPOCH }

    private fun origin(raw: String): String? {
        if (raw.isEmpty() || raw.length > 256) return null
        if (raw.any { it < '!' || it > '~' }) return null
        return raw
    }

    private fun vehicleId(vehicle: Vehicle): String {
        val sync = vehicle.syncId
        if (sync.length in 1..256 && sync.all { it in '!'..'~' }) return sync
        return "v${vehicle.id}"
    }

    /**
     * VIN the installed plugin will accept: 17 ISO characters, plus the North-American
     * check digit when the WMI starts with 1-5. Other stored text is omitted so that
     * receiver does not reject the whole event. Blank is null.
     */
    fun wireVin(raw: String?): String? {
        val text = opaqueVin(raw) ?: return null
        val vin = text.uppercase(Locale.US)
        if (!ISO_VIN.matches(vin)) return null
        if (vin[0] in '1'..'5' && !northAmericanCheckDigit(vin)) return null
        return vin
    }

    /** Stored characters, not a check digit. Blank is null. */
    fun opaqueVin(raw: String?): String? = bound(raw, 256)

    private fun displayUnits(unit: String): String =
        if (UnitFormat.normalizeOdometerUnit(unit) == UnitFormat.KM) "metric" else "imperial"

    private fun northAmericanCheckDigit(vin: String): Boolean {
        val letters = "ABCDEFGHJKLMNPRSTUVWXYZ"
        val values = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 7, 9, 2, 3, 4, 5, 6, 7, 8, 9)
        val weights = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)
        val sum = vin.indices.sumOf { i ->
            val value = if (vin[i] in '0'..'9') vin[i] - '0' else values[letters.indexOf(vin[i])]
            value * weights[i]
        }
        val expected = if (sum % 11 == 10) 'X' else ('0'.code + sum % 11).toChar()
        return vin[8] == expected
    }

    private fun bound(raw: String?, maxBytes: Int): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
        if (nfc.toByteArray(Charsets.UTF_8).size <= maxBytes) return nfc
        var n = nfc.length
        while (n > 0 && nfc.substring(0, n).toByteArray(Charsets.UTF_8).size > maxBytes) n--
        return nfc.substring(0, n).ifBlank { null }
    }
}
