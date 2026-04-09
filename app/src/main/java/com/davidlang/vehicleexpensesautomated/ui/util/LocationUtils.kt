package com.davidlang.vehicleexpensesautomated.ui.util

import android.media.ExifInterface
import android.util.Log
import java.io.File

data class ExifLocation(val latitude: Double, val longitude: Double)

object LocationUtils {
    private const val TAG = "LocationUtils"

    fun getLatLongFromExif(filePath: String): ExifLocation? {
        try {
            val exif = ExifInterface(filePath)
            val latLong = FloatArray(2)
            if (exif.getLatLong(latLong)) {
                return ExifLocation(latLong[0].toDouble(), latLong[1].toDouble())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading EXIF location from $filePath", e)
        }
        return null
    }
}
