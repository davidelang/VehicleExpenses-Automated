package com.davidlang.vehicleexpensesautomated.ui.util

import android.media.ExifInterface
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

/**
 * EXIF capture time + GPS for batch import (and any file-path photo).
 *
 * Time: prefer [ExifInterface.TAG_DATETIME_ORIGINAL], then [ExifInterface.TAG_DATETIME].
 * EXIF datetimes have no timezone; we parse as **local timezone of the device**
 * (same convention as common Android ExifInterface samples) → epoch ms.
 * Filename fallback: `PXL_yyyyMMdd_HHmmss` only when no EXIF datetime.
 */
data class PhotoExifMeta(
    val timestampMs: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val source: String,
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
            return PhotoExifMeta(null, null, null, "missing")
        }
        return try {
            val exif = ExifInterface(path)
            val ts = parseExifDateTime(exif) ?: parseFilenameTimestamp(file.name)
            val source = when {
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) != null -> "exif_original"
                exif.getAttribute(ExifInterface.TAG_DATETIME) != null -> "exif_datetime"
                ts != null -> "filename"
                else -> "none"
            }
            val latLong = FloatArray(2)
            val hasGps = exif.getLatLong(latLong)
            PhotoExifMeta(
                timestampMs = ts,
                latitude = if (hasGps) latLong[0].toDouble() else null,
                longitude = if (hasGps) latLong[1].toDouble() else null,
                source = source,
            )
        } catch (e: Exception) {
            Log.w(TAG, "EXIF read failed for $path: ${e.message}")
            val ts = parseFilenameTimestamp(file.name)
            PhotoExifMeta(ts, null, null, if (ts != null) "filename" else "error")
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
