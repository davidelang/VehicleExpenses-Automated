package com.davidlang.vehicleexpensesautomated.ui.import

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.batch.isDngPath
import java.io.File

private const val TAG = "PendingPhotoPreview"

/**
 * Decode jpg/png/dng for Stage C thumbnails and fullscreen.
 * DNG: BitmapFactory downsample (same as JPEG) or fail. No OpenCV imread.
 */
fun decodePendingPreview(path: String, maxSide: Int): Bitmap? {
    val filePath = when {
        path.startsWith("file://") -> path.removePrefix("file://")
        else -> path
    }
    val f = File(filePath)
    if (!f.isFile) return null
    return try {
        if (isDngPath(filePath)) {
            decodeDngViaOpenCv(filePath, maxSide)
        } else {
            decodeRaster(filePath, maxSide)
        }
    } catch (e: Exception) {
        Log.w(TAG, "decode failed $filePath: ${e.message}")
        null
    }
}

private fun decodeRaster(filePath: String, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(filePath, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (w / sample > maxSide || h / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeFile(filePath, opts)
}

private fun decodeDngViaOpenCv(filePath: String, maxSide: Int): Bitmap? {
    return decodeRaster(filePath, maxSide)
}
