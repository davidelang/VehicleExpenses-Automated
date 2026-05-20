package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

/**
 * Standalone high-performance image synchronization and processing utilities.
 * Decoupled from BufferSetLegacy/MemoryBridge life-cycles.
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
            throw IllegalArgumentException("Dimension mismatch: Mat=${srcMat.cols() + 0}x${srcMat.rows() + 0}, Bitmap=${dst.width}x${dst.height}")
        }
        nativeSyncMatToArgb(srcMat.nativeObj, dst)
    }

    /**
     * High-performance YUV annotation utility using standard OpenCV drawing.
     * Operates directly on the Luma (8UC1) and interleaved Chroma (8UC2) planes.
     */
    fun drawYuvAnnotations(yMat: Mat, uvMat: Mat, annotations: List<SnapshotAnnotation>) {
        if (annotations.isEmpty()) return

        annotations.forEach { ann ->
            // Map ARGB colors to YUV scalars
            // (Standard ITU-R BT.601 conversion: Y=0.299R+0.587G+0.114B, U=-0.147R-0.289G+0.436B, V=0.615R-0.515G-0.100B)
            // Values used here match existing legacy JNI implementation for consistency
            val (yVal, uVal, vVal) = when (ann.color and 0x00FFFFFF) {
                0xFF0000 -> Triple(76.0, 84.0, 255.0)   // Red
                0xFFA500 -> Triple(173.0, 42.0, 191.0)  // Orange
                0x0000FF -> Triple(29.0, 255.0, 107.0)  // Blue
                else -> Triple(255.0, 128.0, 128.0)     // Default White
            }

            val p1 = Point(ann.x1.toDouble(), ann.y1.toDouble())
            val p2 = Point(ann.x2.toDouble(), ann.y2.toDouble())
            val thickness = ann.strokeWidth
            
            if (ann.shape == Shape.RECTANGLE) {
                // Draw on Luma
                Imgproc.rectangle(yMat, p1, p2, Scalar(yVal), thickness)
                // Draw on Chroma (Half coordinates)
                Imgproc.rectangle(uvMat, Point(p1.x / 2.0, p1.y / 2.0), Point(p2.x / 2.0, p2.y / 2.0), Scalar(vVal, uVal), thickness / 2)
            } else {
                Imgproc.line(yMat, p1, p2, Scalar(yVal), thickness)
                Imgproc.line(uvMat, Point(p1.x / 2.0, p1.y / 2.0), Point(p2.x / 2.0, p2.y / 2.0), Scalar(vVal, uVal), thickness / 2)
            }
        }
    }

    /**
     * Encodes a YuvHandle directly to a Base64 JPEG string using high-performance JNI merge.
     */
    fun compressYuvToBase64(handle: BufferSet.YuvHandle, quality: Int): String {
        return nativeCompressYuvToBase64(
            handle.planes[0].buffer,
            handle.planes[1].buffer,
            handle.planes[2].buffer,
            handle.width,
            handle.height,
            handle.planes[0].rowStride,
            quality
        )
    }

    private external fun nativeSyncMatFromArgb(bitmap: Bitmap, matPtr: Long)
    private external fun nativeSyncMatToArgb(matPtr: Long, bitmap: Bitmap)
    private external fun nativeCompressYuvToBase64(yBuf: ByteBuffer, uBuf: ByteBuffer, vBuf: ByteBuffer, w: Int, h: Int, stride: Int, quality: Int): String
}
