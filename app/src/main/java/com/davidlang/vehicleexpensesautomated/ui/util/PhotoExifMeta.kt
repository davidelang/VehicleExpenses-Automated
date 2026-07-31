package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

/**
 * EXIF capture time + GPS for batch import (file path) and gallery pick (content URI).
 *
 * Time: prefer [ExifInterface.TAG_DATETIME_ORIGINAL], then [ExifInterface.TAG_DATETIME].
 * EXIF datetimes have no timezone; we parse as **local timezone of the device**
 * (same convention as common Android ExifInterface samples) → epoch ms.
 * Filename fallback: `PXL_yyyyMMdd_HHmmss` only when no EXIF datetime (path reads only).
 * Missing GPS is not an error — lat/lon null.
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
    private val EXIF_DT = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
        // EXIF has no zone; interpret as device default local wall time.
        timeZone = TimeZone.getDefault()
    }
    private val PXL_NAME = Pattern.compile(
        """PXL_(\d{8})_(\d{6})(?:\D|$)""",
        Pattern.CASE_INSENSITIVE,
    )
    private val PXL_DT = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    fun read(path: String): PhotoExifMeta {
        val file = File(path)
        if (!file.isFile) {
            return PhotoExifMeta(null, null, null, "missing", null)
        }
        return try {
            val exif = ExifInterface(path)
            metaFromExif(exif, file.name)
        } catch (e: Exception) {
            Log.w(TAG, "EXIF read failed for $path: ${e.message}")
            val ts = parseFilenameTimestamp(file.name)
            PhotoExifMeta(ts, null, null, if (ts != null) "filename" else "error", null)
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
                        metaFromExif(exif, displayName)
                    } ?: PhotoExifMeta(null, null, null, "missing", null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "EXIF read failed for $uri: ${e.message}")
            PhotoExifMeta(null, null, null, "error", null)
        }
    }

    private fun metaFromExif(exif: ExifInterface, nameForFilenameFallback: String?): PhotoExifMeta {
        val ts = parseExifDateTime(exif)
            ?: nameForFilenameFallback?.let { parseFilenameTimestamp(it) }
        val source = when {
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) != null -> "exif_original"
            exif.getAttribute(ExifInterface.TAG_DATETIME) != null -> "exif_datetime"
            ts != null && nameForFilenameFallback != null -> "filename"
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
            // API 24+ may expose as attribute string; rational "n/d"
            // Platform constant name varies; use attribute string key.
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

    private fun parseFilenameTimestamp(name: String): Long? {
        val m = PXL_NAME.matcher(name)
        if (!m.find()) return null
        val d = m.group(1) ?: return null
        val t = m.group(2) ?: return null
        val compact = d + t
        return try {
            PXL_DT.parse(compact)?.time
        } catch (_: Exception) {
            null
        }
    }
}
