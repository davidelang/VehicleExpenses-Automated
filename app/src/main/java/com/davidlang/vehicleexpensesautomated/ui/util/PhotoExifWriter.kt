package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.location.Location
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Write GPS + orientation EXIF onto CameraX-saved JPEGs (file path or MediaStore content URI).
 * Errors are logged only — photo is already saved when this runs.
 */
object PhotoExifWriter {
    private const val TAG = "PhotoExifWriter"

    fun writeGpsAndOrientation(
        context: Context,
        uriOrPath: String,
        location: Location?,
        rotationDegrees: Int,
    ) {
        try {
            if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
                writeUri(context, Uri.parse(uriOrPath), location, rotationDegrees)
            } else if (uriOrPath.startsWith("/")) {
                writePath(uriOrPath, location, rotationDegrees)
            } else {
                // Prefer treating as URI string; fall back to path
                val asUri = Uri.parse(uriOrPath)
                if (asUri.scheme != null) {
                    writeUri(context, asUri, location, rotationDegrees)
                } else {
                    writePath(uriOrPath, location, rotationDegrees)
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
    ) {
        try {
            writeUri(context, uri, location, rotationDegrees)
        } catch (e: Exception) {
            Log.w(TAG, "writeGpsAndOrientation failed for $uri: ${e.message}")
        }
    }

    private fun writePath(path: String, location: Location?, rotationDegrees: Int) {
        val file = File(path)
        if (!file.isFile) {
            Log.w(TAG, "Not a file: $path")
            return
        }
        val exif = ExifInterface(path)
        apply(exif, location, rotationDegrees)
        exif.saveAttributes()
        Log.i(TAG, "EXIF written path=$path gps=${location != null} orient=$rotationDegrees")
    }

    private fun writeUri(
        context: Context,
        uri: Uri,
        location: Location?,
        rotationDegrees: Int,
    ) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
        if (pfd == null) {
            Log.w(TAG, "openFileDescriptor failed for $uri")
            return
        }
        pfd.use { descriptor ->
            val exif = ExifInterface(descriptor.fileDescriptor)
            apply(exif, location, rotationDegrees)
            exif.saveAttributes()
        }
        Log.i(TAG, "EXIF written uri=$uri gps=${location != null} orient=$rotationDegrees")
    }

    private fun apply(exif: ExifInterface, location: Location?, rotationDegrees: Int) {
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientationTag(rotationDegrees).toString())
        if (location != null) {
            setGpsAttributes(exif, location.latitude, location.longitude)
        }
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
