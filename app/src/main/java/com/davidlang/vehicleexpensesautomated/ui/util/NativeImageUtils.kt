package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import org.opencv.core.Mat

/**
 * Standalone high-performance image synchronization utilities.
 * Decoupled from BufferSet/MemoryBridge life-cycles.
 */
object NativeImageUtils {
    init {
        System.loadLibrary("memory_bridge")
    }

    /**
     * Fast JNI linear copy from ARGB Bitmap to any 1-channel Mat of matching size.
     * Extracts the Red channel (luminance) directly into native memory.
     */
    fun syncMatFromArgb(src: Bitmap, dstMat: Mat) {
        if (src.width != dstMat.cols() || src.height != dstMat.rows()) {
            throw IllegalArgumentException("Dimension mismatch: Bitmap=${src.width}x${src.height}, Mat=${dstMat.cols()}x${dstMat.rows()}")
        }
        nativeSyncMatFromArgb(src, dstMat.nativeObj)
    }

    /**
     * Fast JNI linear copy from any 1-channel Mat to ARGB Bitmap of matching size.
     * Replicates the single channel into RGB components.
     */
    fun syncMatToArgb(srcMat: Mat, dst: Bitmap) {
        if (dst.width != srcMat.cols() || dst.height != srcMat.rows()) {
            throw IllegalArgumentException("Dimension mismatch: Mat=${srcMat.cols()}x${srcMat.rows()}, Bitmap=${dst.width}x${dst.height}")
        }
        nativeSyncMatToArgb(srcMat.nativeObj, dst)
    }

    private external fun nativeSyncMatFromArgb(bitmap: Bitmap, matPtr: Long)
    private external fun nativeSyncMatToArgb(matPtr: Long, bitmap: Bitmap)
}
