package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import org.opencv.core.Mat

/**
 * NDK Bridge for true zero-copy access to Android Bitmap buffers from OpenCV.
 */
object OpenCvBridge {

    init {
        System.loadLibrary("opencv_bridge")
    }

    /**
     * Locks the bitmap pixels and returns a pointer to a newly created cv::Mat
     * that wraps the pixels directly.
     */
    @PublishedApi
    internal external fun lockBitmapToMat(bitmap: Bitmap): Long

    /**
     * Unlocks the bitmap pixels and deletes the native Mat header.
     */
    @PublishedApi
    internal external fun unlockBitmap(bitmap: Bitmap, matPtr: Long)

    /**
     * Safely executes an OpenCV block with a Mat that wraps the Bitmap pixels directly.
     * Guaranteed zero-copy. Supports ALPHA_8 and ARGB_8888.
     */
    inline fun <T> useBitmapAsMat(bitmap: Bitmap, block: (Mat) -> T): T {
        val t0 = System.nanoTime()
        val matPtr = lockBitmapToMat(bitmap)
        if (matPtr == 0L) throw IllegalStateException("Failed to lock bitmap memory for OpenCV")
        
        // Wrap the native Mat pointer in a Java Mat object
        val mat = Mat(matPtr)
        val t1 = System.nanoTime()
        return try {
            block(mat)
        } finally {
            val t2 = System.nanoTime()
            unlockBitmap(bitmap, matPtr)
            val t3 = System.nanoTime()
            val total = (t3 - t0) / 1e6
            if (total > 50) { // Log slow locks
                android.util.Log.w("PERF_NDK", "Slow NDK Lock: Lock=%.2fms, Unlock=%.2fms, Work=%.2fms".format((t1-t0)/1e6, (t3-t2)/1e6, (t2-t1)/1e6))
            }
        }
    }
}
