package com.davidlang.vehicleexpensesautomated.ui.import

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.batch.isDngPath
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

private const val TAG = "PendingPhotoPreview"

/**
 * Decode jpg/png/dng for Stage C thumbnails and fullscreen.
 * DNG: OpenCV imread (LibRaw-backed where available) → downsample → Bitmap.
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
    val mat = Imgcodecs.imread(filePath)
    if (mat.empty()) {
        mat.release()
        return null
    }
    try {
        val w = mat.cols()
        val h = mat.rows()
        if (w <= 0 || h <= 0) return null
        val scale = maxSide.toDouble() / maxOf(w, h).toDouble()
        val work = if (scale < 1.0) {
            val resized = Mat()
            Imgproc.resize(
                mat,
                resized,
                Size(w * scale, h * scale),
                0.0,
                0.0,
                Imgproc.INTER_AREA,
            )
            mat.release()
            resized
        } else {
            mat
        }
        // OpenCV BGR → ARGB bitmap
        val rgba = Mat()
        Imgproc.cvtColor(work, rgba, Imgproc.COLOR_BGR2RGBA)
        work.release()
        val bmp = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bmp)
        rgba.release()
        return bmp
    } catch (e: Exception) {
        Log.w(TAG, "DNG OpenCV decode: ${e.message}")
        if (!mat.empty()) mat.release()
        return null
    }
}
