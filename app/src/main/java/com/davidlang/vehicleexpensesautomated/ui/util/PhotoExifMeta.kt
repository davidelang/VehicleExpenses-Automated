package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * EXIF capture time + GPS for batch import (file path) and gallery pick (content URI).
 *
 * Time priority: EXIF DateTimeOriginal/DateTime → filename heuristics → sane file mtime → null.
 * EXIF datetimes have no timezone; we parse as **device local** wall time → epoch ms
 * (OffsetTime* when present is not yet used for parse; writer stamps it for future-proofing).
 */
data class PhotoExifMeta(
    val timestampMs: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val source: String,
    /** Horizontal positioning error in meters when EXIF provides it; else null. */
    val accuracyM: Double? = null,
)

object PhotoExifMetaReader {
    private const val TAG = "PhotoExifMeta"
    private const val YEAR_MIN = 2000
    private const val YEAR_MAX = 2100

    private val EXIF_DT = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }
    // yyyyMMdd + optional sep + HHmmss (optional fractional)
    private val DATE_TIME_COMPACT = Pattern.compile(
        """(?<!\d)(\d{4})(\d{2})(\d{2})[_T\-]?(\d{2})(\d{2})(\d{2})(?:\d{0,3})?(?!\d)""",
    )
    // yyyy-MM-dd or yyyy_MM_dd + space/T + HH:mm:ss or HHmmss
    private val DATE_TIME_SEP = Pattern.compile(
        """(?<!\d)(\d{4})[-_](\d{2})[-_](\d{2})[ T_](\d{2}):?(\d{2}):?(\d{2})(?!\d)""",
    )
    // Date-only yyyyMMdd / yyyy-MM-dd
    private val DATE_ONLY_COMPACT = Pattern.compile(
        """(?<!\d)(\d{4})(\d{2})(\d{2})(?!\d)""",
    )
    private val DATE_ONLY_SEP = Pattern.compile(
        """(?<!\d)(\d{4})[-_](\d{2})[-_](\d{2})(?!\d)""",
    )
    // 8-digit ambiguous run
    private val EIGHT_DIGITS = Pattern.compile("""(?<!\d)(\d{8})(?!\d)""")
    // Epoch: prefer long digit runs
    private val DIGIT_RUN = Pattern.compile("""(?<!\d)(\d{10,14})(?!\d)""")

    fun read(path: String): PhotoExifMeta {
        val file = File(path)
        if (!file.isFile) {
            return PhotoExifMeta(null, null, null, "missing", null)
        }
        return try {
            val exif = ExifInterface(path)
            metaFromExif(exif, file.name, file.lastModified().takeIf { it > 0L })
        } catch (e: Exception) {
            Log.w(TAG, "EXIF read failed for $path: ${e.message}")
            val mtime = file.lastModified().takeIf { it > 0L }
            val guessed = guessTimeFromFilename(file.name, mtime)
                ?: mtime?.takeIf { yearSane(it) }?.let { it to "file_mtime" }
            if (guessed == null) {
                PhotoExifMeta(null, null, null, "error", null)
            } else {
                PhotoExifMeta(guessed.first, null, null, guessed.second, null)
            }
        }
    }

    /**
     * Gallery / SAF pick: open via ContentResolver stream (or file scheme → path).
     * GPS only when EXIF contains it; otherwise lat/lon null (not an error).
     */
    fun read(context: Context, uri: Uri): PhotoExifMeta {
        return try {
            when (uri.scheme?.lowercase(Locale.US)) {
                "file" -> {
                    val path = uri.path
                    if (path.isNullOrBlank()) {
                        PhotoExifMeta(null, null, null, "missing", null)
                    } else {
                        read(path)
                    }
                }
                else -> {
                    val displayName = uri.lastPathSegment
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val exif = ExifInterface(stream)
                        metaFromExif(exif, displayName, mtimeHint = null)
                    } ?: PhotoExifMeta(null, null, null, "missing", null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "EXIF read failed for $uri: ${e.message}")
            val name = uri.lastPathSegment
            val guessed = name?.let { guessTimeFromFilename(it, null) }
            if (guessed != null) {
                PhotoExifMeta(guessed.first, null, null, guessed.second, null)
            } else {
                PhotoExifMeta(null, null, null, "error", null)
            }
        }
    }

    private fun metaFromExif(
        exif: ExifInterface,
        nameForFilenameFallback: String?,
        mtimeHint: Long?,
    ): PhotoExifMeta {
        val exifTs = parseExifDateTime(exif)
        val fileGuess = if (exifTs == null && nameForFilenameFallback != null) {
            guessTimeFromFilename(nameForFilenameFallback, mtimeHint)
        } else {
            null
        }
        val mtimeTs = if (exifTs == null && fileGuess == null) {
            mtimeHint?.takeIf { yearSane(it) }
        } else {
            null
        }
        val ts = exifTs ?: fileGuess?.first ?: mtimeTs
        val source = when {
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) != null -> "exif_original"
            exif.getAttribute(ExifInterface.TAG_DATETIME) != null -> "exif_datetime"
            fileGuess != null -> fileGuess.second
            mtimeTs != null -> "file_mtime"
            else -> "none"
        }
        val latLong = FloatArray(2)
        val hasGps = exif.getLatLong(latLong)
        val accuracyM = parseGpsHorizontalError(exif)
        return PhotoExifMeta(
            timestampMs = ts,
            latitude = if (hasGps) latLong[0].toDouble() else null,
            longitude = if (hasGps) latLong[1].toDouble() else null,
            source = source,
            accuracyM = accuracyM,
        )
    }

    /**
     * EXIF GPSHPositioningError (meters) when present. Tag may be absent on many cameras.
     */
    private fun parseGpsHorizontalError(exif: ExifInterface): Double? {
        return try {
            val raw = exif.getAttribute("GPSHPositioningError") ?: return null
            parseExifRational(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseExifRational(raw: String): Double? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val parts = t.split("/")
        return if (parts.size == 2) {
            val n = parts[0].toDoubleOrNull() ?: return null
            val d = parts[1].toDoubleOrNull() ?: return null
            if (d == 0.0) null else n / d
        } else {
            t.toDoubleOrNull()
        }
    }

    private fun parseExifDateTime(exif: ExifInterface): Long? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        return try {
            EXIF_DT.parse(raw)?.time
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Aggressive filename time guess. Returns (epochMs, sourceTag) or null.
     * Pure heuristics — not an OEM prefix catalog.
     */
    fun guessTimeFromFilename(
        name: String,
        mtimeHint: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): Pair<Long, String>? {
        val base = name.substringAfterLast('/').substringBeforeLast('.', name)
        if (base.isBlank()) return null
        val candidates = mutableListOf<Candidate>()

        // 1) Date + 24h time compact / separated
        fun addDateTime(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, scoreBoost: Int) {
            val ms = calendarMs(y, mo, d, h, mi, s) ?: return
            candidates += Candidate(ms, hasTime = true, score = 100 + scoreBoost, len = 14)
        }
        var m = DATE_TIME_COMPACT.matcher(base)
        while (m.find()) {
            addDateTime(
                m.group(1)!!.toInt(), m.group(2)!!.toInt(), m.group(3)!!.toInt(),
                m.group(4)!!.toInt(), m.group(5)!!.toInt(), m.group(6)!!.toInt(),
                scoreBoost = 10,
            )
        }
        m = DATE_TIME_SEP.matcher(base)
        while (m.find()) {
            addDateTime(
                m.group(1)!!.toInt(), m.group(2)!!.toInt(), m.group(3)!!.toInt(),
                m.group(4)!!.toInt(), m.group(5)!!.toInt(), m.group(6)!!.toInt(),
                scoreBoost = 10,
            )
        }

        // 2) Date-only → local midnight
        m = DATE_ONLY_COMPACT.matcher(base)
        while (m.find()) {
            val y = m.group(1)!!.toInt()
            val mo = m.group(2)!!.toInt()
            val d = m.group(3)!!.toInt()
            // Skip if this 8-digit run is the date prefix of a date+time already matched
            val ms = calendarMs(y, mo, d, 0, 0, 0) ?: continue
            candidates += Candidate(ms, hasTime = false, score = 60, len = 8)
        }
        m = DATE_ONLY_SEP.matcher(base)
        while (m.find()) {
            val ms = calendarMs(
                m.group(1)!!.toInt(), m.group(2)!!.toInt(), m.group(3)!!.toInt(),
                0, 0, 0,
            ) ?: continue
            candidates += Candidate(ms, hasTime = false, score = 60, len = 8)
        }

        // 3) Ambiguous 8-digit calendar orders
        m = EIGHT_DIGITS.matcher(base)
        while (m.find()) {
            val digs = m.group(1)!!
            for (parsed in parseAmbiguousEightDigits(digs)) {
                candidates += Candidate(parsed, hasTime = false, score = 50, len = 8)
            }
        }

        // 4) Epoch ms (13–14) / seconds (10)
        m = DIGIT_RUN.matcher(base)
        while (m.find()) {
            val digs = m.group(1)!!
            when (digs.length) {
                13, 14 -> {
                    val ms = digs.toLongOrNull() ?: continue
                    // 14 digits: take first 13 as ms
                    val epoch = if (digs.length == 14) digs.take(13).toLongOrNull() ?: continue else ms
                    if (yearSane(epoch)) {
                        candidates += Candidate(epoch, hasTime = true, score = 80, len = digs.length)
                    }
                }
                10 -> {
                    val sec = digs.toLongOrNull() ?: continue
                    val epoch = sec * 1000L
                    if (yearSane(epoch)) {
                        candidates += Candidate(epoch, hasTime = true, score = 75, len = 10)
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null
        val ref = mtimeHint?.takeIf { yearSane(it) } ?: nowMs
        val best = candidates.maxWithOrNull(
            compareBy<Candidate> { it.hasTime }
                .thenBy { it.score }
                .thenBy { -abs(it.ms - ref) }
                .thenBy { it.len },
        ) ?: return null
        return best.ms to "filename"
    }

    private data class Candidate(
        val ms: Long,
        val hasTime: Boolean,
        val score: Int,
        val len: Int,
    )

    private fun parseAmbiguousEightDigits(digs: String): List<Long> {
        if (digs.length != 8) return emptyList()
        val out = mutableListOf<Long>()
        // yyyyMMdd
        digs.substring(0, 4).toIntOrNull()?.let { y ->
            digs.substring(4, 6).toIntOrNull()?.let { mo ->
                digs.substring(6, 8).toIntOrNull()?.let { d ->
                    calendarMs(y, mo, d, 0, 0, 0)?.let { out += it }
                }
            }
        }
        // ddMMyyyy
        digs.substring(0, 2).toIntOrNull()?.let { d ->
            digs.substring(2, 4).toIntOrNull()?.let { mo ->
                digs.substring(4, 8).toIntOrNull()?.let { y ->
                    calendarMs(y, mo, d, 0, 0, 0)?.let { out += it }
                }
            }
        }
        // MMddyyyy
        digs.substring(0, 2).toIntOrNull()?.let { mo ->
            digs.substring(2, 4).toIntOrNull()?.let { d ->
                digs.substring(4, 8).toIntOrNull()?.let { y ->
                    calendarMs(y, mo, d, 0, 0, 0)?.let { out += it }
                }
            }
        }
        return out.distinct()
    }

    private fun calendarMs(year: Int, month: Int, day: Int, h: Int, mi: Int, s: Int): Long? {
        if (year !in YEAR_MIN..YEAR_MAX) return null
        if (month !in 1..12) return null
        if (day !in 1..31) return null
        if (h !in 0..23 || mi !in 0..59 || s !in 0..59) return null
        return try {
            val cal = Calendar.getInstance()
            cal.isLenient = false
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month - 1)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, mi)
            cal.set(Calendar.SECOND, s)
            cal.set(Calendar.MILLISECOND, 0)
            val t = cal.timeInMillis
            // Round-trip check (rejects day 32 etc. when non-lenient throws — caught below)
            if (cal.get(Calendar.YEAR) != year ||
                cal.get(Calendar.MONTH) != month - 1 ||
                cal.get(Calendar.DAY_OF_MONTH) != day
            ) {
                null
            } else {
                t
            }
        } catch (_: Exception) {
            null
        }
    }

    fun yearSane(ms: Long): Boolean {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ms
        val y = cal.get(Calendar.YEAR)
        return y in YEAR_MIN..YEAR_MAX
    }
}
