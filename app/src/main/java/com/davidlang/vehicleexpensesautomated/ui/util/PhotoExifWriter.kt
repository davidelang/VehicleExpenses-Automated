package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.location.Location
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Write GPS + orientation + capture datetime EXIF onto CameraX-saved JPEGs
 * (file path or MediaStore content URI). Errors are logged only — photo is already saved.
 */
object PhotoExifWriter {
    private const val TAG = "PhotoExifWriter"

    private val EXIF_DT = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    fun writeGpsAndOrientation(
        context: Context,
        uriOrPath: String,
        location: Location?,
        rotationDegrees: Int,
        timestampMs: Long? = System.currentTimeMillis(),
        userComment: String? = null,
    ) {
        try {
            if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
                writeUri(
                    context,
                    Uri.parse(uriOrPath),
                    location,
                    rotationDegrees,
                    timestampMs,
                    userComment,
                )
            } else if (uriOrPath.startsWith("/")) {
                writePath(uriOrPath, location, rotationDegrees, timestampMs, userComment)
            } else {
                val asUri = Uri.parse(uriOrPath)
                if (asUri.scheme != null) {
                    writeUri(context, asUri, location, rotationDegrees, timestampMs, userComment)
                } else {
                    writePath(uriOrPath, location, rotationDegrees, timestampMs, userComment)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "writeGpsAndOrientation failed for $uriOrPath: ${e.message}")
        }
    }

    fun writeGpsAndOrientation(
        context: Context,
        uri: Uri,
        location: Location?,
        rotationDegrees: Int,
        timestampMs: Long? = System.currentTimeMillis(),
        userComment: String? = null,
    ) {
        try {
            writeUri(context, uri, location, rotationDegrees, timestampMs, userComment)
        } catch (e: Exception) {
            Log.w(TAG, "writeGpsAndOrientation failed for $uri: ${e.message}")
        }
    }

    private fun writePath(
        path: String,
        location: Location?,
        rotationDegrees: Int,
        timestampMs: Long?,
        userComment: String?,
    ) {
        val file = File(path)
        if (!file.isFile) {
            Log.w(TAG, "Not a file: $path")
            return
        }
        val exif = ExifInterface(path)
        apply(exif, location, rotationDegrees, timestampMs, userComment)
        exif.saveAttributes()
        Log.i(
            TAG,
            "EXIF written path=$path gps=${location != null} " +
                "acc=${location?.takeIf { it.hasAccuracy() }?.accuracy} " +
                "orient=$rotationDegrees ts=$timestampMs",
        )
    }

    private fun writeUri(
        context: Context,
        uri: Uri,
        location: Location?,
        rotationDegrees: Int,
        timestampMs: Long?,
        userComment: String?,
    ) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
        if (pfd == null) {
            Log.w(TAG, "openFileDescriptor failed for $uri")
            return
        }
        pfd.use { descriptor ->
            val exif = ExifInterface(descriptor.fileDescriptor)
            apply(exif, location, rotationDegrees, timestampMs, userComment)
            exif.saveAttributes()
        }
        Log.i(
            TAG,
            "EXIF written uri=$uri gps=${location != null} " +
                "acc=${location?.takeIf { it.hasAccuracy() }?.accuracy} " +
                "orient=$rotationDegrees ts=$timestampMs",
        )
    }

    private fun apply(
        exif: ExifInterface,
        location: Location?,
        rotationDegrees: Int,
        timestampMs: Long?,
        userComment: String?,
    ) {
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientationTag(rotationDegrees).toString())
        if (location != null) {
            setGpsAttributes(exif, location.latitude, location.longitude)
            if (location.hasAccuracy()) {
                // Horizontal error in meters (matches PhotoExifMetaReader.parseGpsHorizontalError).
                val meters = location.accuracy.toDouble().coerceAtLeast(0.0)
                val milli = (meters * 1000.0).toLong().coerceAtLeast(0L)
                exif.setAttribute("GPSHPositioningError", "$milli/1000")
            }
        }
        if (timestampMs != null) {
            val wall = EXIF_DT.format(Date(timestampMs))
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, wall)
            exif.setAttribute(ExifInterface.TAG_DATETIME, wall)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, wall)
            val offset = formatOffset(timestampMs)
            try {
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
            } catch (e: Exception) {
                // Older platform builds may lack offset tags — still keep DateTime*.
                Log.w(TAG, "OffsetTime tags not set: ${e.message}")
            }
        }
        if (!userComment.isNullOrBlank()) {
            try {
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, userComment)
            } catch (_: Exception) {
                // optional
            }
        }
    }

    /** Device zone offset at [timestampMs], e.g. `-07:00` or `+05:30`. */
    fun formatOffset(timestampMs: Long): String {
        val tz = TimeZone.getDefault()
        val raw = tz.getOffset(timestampMs)
        val totalMin = TimeUnit.MILLISECONDS.toMinutes(raw.toLong()).toInt()
        val sign = if (totalMin >= 0) "+" else "-"
        val abs = kotlin.math.abs(totalMin)
        val h = abs / 60
        val m = abs % 60
        return String.format(Locale.US, "%s%02d:%02d", sign, h, m)
    }

    /** Map display rotation degrees → EXIF orientation constant. */
    fun orientationTag(rotationDegrees: Int): Int {
        val norm = ((rotationDegrees % 360) + 360) % 360
        return when (norm) {
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * Platform [ExifInterface] has [ExifInterface.getLatLong] but no setLatLong on all API
     * levels / compile targets — write GPS tags as rational DMS strings.
     */
    private fun setGpsAttributes(exif: ExifInterface, latitude: Double, longitude: Double) {
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, toDmsRational(latitude))
        exif.setAttribute(
            ExifInterface.TAG_GPS_LATITUDE_REF,
            if (latitude >= 0.0) "N" else "S",
        )
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, toDmsRational(longitude))
        exif.setAttribute(
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            if (longitude >= 0.0) "E" else "W",
        )
    }

    /** Absolute degrees → "deg/1,min/1,sec*10000/10000" EXIF rational string. */
    private fun toDmsRational(coord: Double): String {
        val abs = kotlin.math.abs(coord)
        val deg = abs.toInt()
        val minFloat = (abs - deg) * 60.0
        val min = minFloat.toInt()
        val sec = (minFloat - min) * 60.0
        val secScaled = (sec * 10000.0).toInt()
        return "$deg/1,$min/1,$secScaled/10000"
    }
}
