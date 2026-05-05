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
    private external fun lockBitmapToMat(bitmap: Bitmap): Long

    /**
     * Unlocks the bitmap pixels and deletes the native Mat header.
     */
    private external fun unlockBitmap(bitmap: Bitmap, matPtr: Long)

    /**
     * Safely executes an OpenCV block with a Mat that wraps the Bitmap pixels directly.
     * Guaranteed zero-copy. Supports ALPHA_8 and ARGB_8888.
     */
    inline fun <T> useBitmapAsMat(bitmap: Bitmap, block: (Mat) -> T): T {
        val matPtr = lockBitmapToMat(bitmap)
        if (matPtr == 0L) throw IllegalStateException("Failed to lock bitmap memory for OpenCV")
        
        // Wrap the native Mat pointer in a Java Mat object
        val mat = Mat(matPtr)
        return try {
            block(mat)
        } finally {
            unlockBitmap(bitmap, matPtr)
            // Note: unlockBitmap deletes the native Mat header, 
            // so we don't call mat.release() which would also try to delete it.
        }
    }
}
