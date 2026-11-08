package com.davidlang.vehicleexpensesautomated.ui.fuel

import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.isSystemUnassignedVehicle
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets

/**
 * VIN blob and mode 01 A6 from Torque `sendCommandGetResponse` text.
 * No bus calls. A live reading is not passed through face-rollover tracking.
 */
object TorqueLiveDecode {
    const val SOURCE_TORQUE = "torque"
    const val SOURCE_PHOTO = "photo"
    const val SOURCE_MANUAL = "manual"

    data class Reading(val vinBlob: String, val tenthsKm: Long, val tenthsText: String)

    enum class PrefillKind { NONE, MISMATCH, FILL }

    data class Prefill(
        val kind: PrefillKind,
        val vehicleId: Int = -1,
        val odometer: Int = 0,
        val rawTenths: String = "",
    )

    data class SavedMark(val source: String, val rawTenthsKm: String?)

    fun reading(vinParts: Array<out String>?, a6Parts: Array<out String>?): Reading? {
        val blob = vinBlob(vinParts) ?: return null
        val tenths = a6Tenths(a6Parts) ?: return null
        return Reading(blob, tenths, tenths.toString())
    }

    /** Bytes after `4902` and the mode-09 count byte. No length, alphabet, or check-digit rule. */
    fun vinBlob(parts: Array<out String>?): String? {
        val lines = dataLines(parts) ?: return null
        val blobs = ArrayList<String>()
        val anon = ArrayList<String>()
        val can = LinkedHashMap<String, ArrayList<List<Int>>>()
        for (line in lines) {
            if (NUMBERED.matches(line) || LENGTH_ONLY.matches(line)) {
                anon.add(line)
                continue
            }
            val frame = canFrame(line)
            if (frame != null) {
                can.getOrPut(frame.first) { ArrayList() }.add(frame.second)
                continue
            }
            val compact = line.replace(" ", "")
            if (compact == "0902") continue
            if (compact.startsWith("4902") && compact.length % 2 == 0) {
                blobs.add(blobFrom4902(compact) ?: return null)
                continue
            }
            return null
        }
        if (anon.isNotEmpty()) {
            val messages = elmMessages(anon) ?: return null
            for (message in messages) {
                blobs.add(blobFrom4902(message) ?: return null)
            }
        }
        for (frames in can.values) {
            val message = assembleCan(frames) ?: return null
            if (!message.startsWith("4902")) continue
            blobs.add(blobFrom4902(message) ?: return null)
        }
        if (blobs.isEmpty()) return null
        val distinct = blobs.distinct()
        if (distinct.size != 1) return null
        return distinct.single()
    }

    /** One unambiguous positive four-byte `41A6`. Zero, short, or disagreeing responders fail. */
    fun a6Tenths(parts: Array<out String>?): Long? {
        val lines = dataLines(parts) ?: return null
        val values = ArrayList<Long>()
        for (line in lines) {
            if (line.replace(" ", "") == "01A6") continue
            val parsed = a6FromLine(line) ?: return null
            if (parsed == A6_SKIP) continue
            values.add(parsed)
        }
        if (values.isEmpty()) return null
        if (values.distinct().size != 1) return null
        val value = values.single()
        if (value <= 0L) return null
        return value
    }

    /** Half-up integer in the vehicle unit. Miles divide by [VolumeUnits.KILOMETERS_PER_MILE]. */
    fun storedOdometer(tenthsKm: Long, odometerUnit: String): Int? {
        if (tenthsKm <= 0L) return null
        val rounded = if (UnitFormat.normalizeOdometerUnit(odometerUnit) == UnitFormat.KM) {
            BigDecimal.valueOf(tenthsKm).divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP)
        } else {
            BigDecimal.valueOf(tenthsKm).divide(BigDecimal("16.09344"), 0, RoundingMode.HALF_UP)
        }
        if (rounded.signum() <= 0 || rounded.compareTo(BigDecimal(Int.MAX_VALUE)) > 0) return null
        return rounded.toInt()
    }

    fun prefill(
        reading: Reading,
        vehicles: List<Vehicle>,
        selectedVehicleId: Int?,
        userPicked: Boolean,
    ): Prefill {
        val blob = reading.vinBlob
        if (blob.isEmpty()) return Prefill(PrefillKind.NONE)
        val selected = vehicles.find { it.id == selectedVehicleId }
        if (userPicked && selected != null) {
            val opted = selected.integrateWithTorque && !isSystemUnassignedVehicle(selected)
            if (selected.vin == blob && opted) {
                val odo = storedOdometer(reading.tenthsKm, selected.odometerUnit) ?: return Prefill(PrefillKind.NONE)
                return Prefill(PrefillKind.FILL, selected.id, odo, reading.tenthsText)
            }
            if (selected.vin != blob) return Prefill(PrefillKind.MISMATCH)
            return Prefill(PrefillKind.NONE)
        }
        val matches = vehicles.filter { vehicle ->
            vehicle.integrateWithTorque &&
                !isSystemUnassignedVehicle(vehicle) &&
                !vehicle.vin.isNullOrBlank() &&
                vehicle.vin == blob
        }
        val one = matches.singleOrNull() ?: return Prefill(PrefillKind.NONE)
        val odo = storedOdometer(reading.tenthsKm, one.odometerUnit) ?: return Prefill(PrefillKind.NONE)
        return Prefill(PrefillKind.FILL, one.id, odo, reading.tenthsText)
    }

    fun savedMark(
        odometerText: String,
        dashPhoto: Boolean,
        userTyped: Boolean,
        torqueVehicleId: Int,
        saveVehicleId: Int,
        torqueRaw: String,
    ): SavedMark {
        if (dashPhoto) return SavedMark(SOURCE_PHOTO, null)
        val hasOdo = odometerText.isNotBlank()
        if (userTyped && hasOdo) return SavedMark(SOURCE_MANUAL, null)
        if (hasOdo && torqueVehicleId == saveVehicleId && torqueRaw.isNotEmpty()) {
            return SavedMark(SOURCE_TORQUE, torqueRaw)
        }
        return SavedMark("", null)
    }

    private const val A6_SKIP = -1L

    private val NUMBERED = Regex("^([0-9A-F]{1,2}):\\s*([0-9A-F ]+)$")
    private val LENGTH_ONLY = Regex("^[0-9A-F]{3}$")
    private val HEX_BODY = Regex("^[0-9A-F ]+$")

    private fun dataLines(parts: Array<out String>?): List<String>? {
        if (parts == null || parts.isEmpty()) return null
        val lines = parts.flatMap { part -> part.split('\n', '\r') }
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty() || lines.size > 64 || lines.any { it.length > 512 }) return null
        val data = lines.filter { line ->
            line != ">" &&
                line != "OK" &&
                line != "?" &&
                line != "STOPPED" &&
                line != "NO DATA" &&
                !line.contains("SEARCHING") &&
                !line.startsWith("ERROR") &&
                !line.contains("UNABLE")
        }
        if (data.isEmpty()) return null
        return data
    }

    private fun blobFrom4902(messageHex: String): String? {
        if (!messageHex.startsWith("4902")) return null
        val rest = messageHex.substring(4)
        if (rest.length < 4 || rest.length % 2 != 0) return null
        val bytes = ByteArray(rest.length / 2)
        for (i in bytes.indices) {
            val v = rest.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
            bytes[i] = v.toByte()
        }
        if (bytes.size < 2) return null
        return String(bytes, 1, bytes.size - 1, StandardCharsets.ISO_8859_1)
    }

    private fun canFrame(line: String): Pair<String, List<Int>>? {
        if (!HEX_BODY.matches(line) || ':' in line) return null
        val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size < 2) return null
        val header = tokens[0]
        if (header.length != 3 && header.length != 8) return null
        if (tokens.drop(1).any { it.length != 2 }) return null
        val bytes = tokens.drop(1).map { it.toInt(16) }
        return header to bytes
    }

    private fun assembleCan(frames: List<List<Int>>): String? {
        if (frames.isEmpty()) return null
        val first = frames[0]
        if (first.isEmpty()) return null
        val pci = first[0]
        val kind = pci shr 4
        if (kind == 0) {
            if (frames.size != 1) return null
            val n = pci and 0x0F
            if (n < 1 || first.size < 1 + n) return null
            return hexBytes(first.subList(1, 1 + n))
        }
        if (kind != 1) return null
        if (first.size < 2) return null
        val total = ((pci and 0x0F) shl 8) or first[1]
        if (total !in 3..512) return null
        val buf = ArrayList<Int>()
        buf.addAll(first.drop(2))
        var expect = 1
        for (frame in frames.drop(1)) {
            if (frame.isEmpty()) return null
            val p = frame[0]
            if ((p shr 4) != 2) return null
            if ((p and 0x0F) != expect) return null
            expect = (expect + 1) and 0x0F
            buf.addAll(frame.drop(1))
        }
        if (buf.size < total) return null
        return hexBytes(buf.subList(0, total))
    }

    /** One ELM numbered block. Empty or inconsistent framing fails. */
    private fun elmMessages(lines: List<String>): List<String>? {
        if (lines.any { ':' in it }) {
            val lengths = lines.filter { LENGTH_ONLY.matches(it) }
            if (lengths.size != 1) return null
            val size = lengths.single().toIntOrNull(16) ?: return null
            if (size !in 3..512) return null
            val chunks = linkedMapOf<Int, String>()
            for (line in lines) {
                if (line == lengths.single()) continue
                val m = NUMBERED.matchEntire(line) ?: return null
                val index = m.groupValues[1].toInt(16)
                val data = m.groupValues[2].replace(" ", "")
                if (data.length % 2 != 0 || data.isEmpty() || chunks.put(index, data) != null) return null
            }
            if (chunks.isEmpty() || chunks.keys.sorted() != (0 until chunks.size).toList()) return null
            for ((index, data) in chunks) {
                val expected = if (index == 0) 12 else 14
                if (data.length > expected || (index < chunks.size - 1 && data.length != expected)) return null
            }
            val hex = chunks.toSortedMap().values.joinToString("")
            if (hex.length < size * 2 || hex.length - size * 2 > 14) return null
            if (hex.drop(size * 2).chunked(2).any { it !in setOf("00", "55", "AA", "CC") }) return null
            val message = hex.take(size * 2)
            return if (message.startsWith("4902")) listOf(message) else null
        }
        return null
    }

    private fun a6FromLine(line: String): Long? {
        if (!HEX_BODY.matches(line)) return null
        val hex = line.replace(" ", "")
        val payload = payloadAfter(hex, "41A6") ?: return A6_SKIP
        if (payload.length != 8) return null
        return payload.toLongOrNull(16)
    }

    private val OFFSETS = intArrayOf(0, 2, 3, 5, 8, 10)

    /** Hex data after [marker], cut to the single-frame PCI length when a PCI byte is present. */
    private fun payloadAfter(hex: String, marker: String): String? {
        for (off in OFFSETS) {
            val pciPos = when (off) {
                2 -> 0
                5 -> 3
                10 -> 8
                else -> -1
            }
            if (pciPos >= 0 && !pciAt(hex, pciPos)) continue
            if (!hex.startsWith(marker, off)) continue
            val rest = hex.substring(off + marker.length)
            if (pciPos < 0) {
                val padded = stripPadding(rest)
                return padded
            }
            val pci = hex.substring(pciPos, pciPos + 2).toInt(16)
            val n = pci - marker.length / 2
            if (n < 0) return null
            if (rest.length < n * 2) return rest
            return rest.take(n * 2)
        }
        return null
    }

    private fun stripPadding(hex: String): String {
        val b = hex.chunked(2)
        var n = b.size
        val pad = setOf("55", "AA", "00", "CC")
        if (n >= 3 && b[n - 1] in pad) {
            var k = n - 1
            while (k > 0 && b[k - 1] == b[n - 1]) k--
            if (n - k >= 2 && k >= 1) n = k
        }
        return b.take(n).joinToString("")
    }

    private fun pciAt(hex: String, i: Int): Boolean {
        if (i + 2 > hex.length) return false
        val b = hex.substring(i, i + 2).toIntOrNull(16) ?: return false
        return b in 1..7
    }

    private fun hexBytes(bytes: List<Int>): String =
        bytes.joinToString("") { "%02X".format(it and 0xFF) }
}
