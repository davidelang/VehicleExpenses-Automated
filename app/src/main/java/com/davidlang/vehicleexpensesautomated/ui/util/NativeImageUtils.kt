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
        System.loadLibrary("native_ocr")
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
     * High-performance ingestion from ARGB Bitmap to BufferSet primary Mat.
     * Uses cv::cvtColor for optimized SIMD conversion.
     */
    fun ingestArgbToYuv(src: Bitmap, target: BufferSet.Slice) {
        if (target is BufferSet.Instance) {
            nativeIngestArgbToYuv(src, target.nativePtr)
        } else {
            // Fallback for crops if ever needed, but usually we ingest to full buffer
            syncMatFromArgb(src, target.mat)
        }
    }

    /**
     * High-performance YUV annotation utility using standard OpenCV drawing.
     * Operates directly on the Luma (8UC1) and interleaved Chroma (8UC2) planes.
     */
    fun drawYuvAnnotations(handle: BufferSet.YuvHandle, annotations: List<SnapshotAnnotation>) {
        if (annotations.isEmpty()) return

        // Create temporary Mat wrappers that respect the native memory stride
        val yPlane = handle.planes[0]
        val uvPlane = handle.planes[2] // V plane is start of interleaved UV in NV21
        
        val yMat = Mat(handle.height, handle.width, CvType.CV_8UC1, yPlane.buffer, yPlane.rowStride.toLong())
        val uvMat = Mat(handle.height / 2, handle.width / 2, CvType.CV_8UC2, uvPlane.buffer, uvPlane.rowStride.toLong())

        annotations.forEach { ann ->
            // Map ARGB colors to YUV scalars
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
                Imgproc.rectangle(yMat, p1, p2, Scalar(yVal), thickness)
                Imgproc.rectangle(uvMat, Point(p1.x / 2.0, p1.y / 2.0), Point(p2.x / 2.0, p2.y / 2.0), Scalar(vVal, uVal), thickness / 2)
            } else {
                Imgproc.line(yMat, p1, p2, Scalar(yVal), thickness)
                Imgproc.line(uvMat, Point(p1.x / 2.0, p1.y / 2.0), Point(p2.x / 2.0, p2.y / 2.0), Scalar(vVal, uVal), thickness / 2)
            }
        }

        yMat.release()
        uvMat.release()
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

    /**
     * High-performance ingestion from JPEG file directly to BufferSet YUV planes.
     * Bypasses Java heap Bitmaps.
     */
    fun ingestJpegToYuv(path: String, target: BufferSet.Slice) {
        if (target is BufferSet.Instance) {
            if (!nativeIngestJpegToYuv(path, target.nativePtr)) {
                throw Exception("Native JPEG ingestion failed for $path")
            }
        } else {
            throw IllegalArgumentException("Native ingestion required an Instance handle")
        }
    }

    /**
     * Diagnostic: Probe image dimensions using native imread.
     */
    fun testImread(path: String): String {
        return nativeTestImread(path)
    }

    /**
     * Diagnostic: Probe DNG dimensions using LibRaw.
     */
    fun probeDngResolution(path: String): String {
        return nativeProbeDngResolution(path)
    }

    /**
     * High-performance ingestion from DNG file directly to BufferSet YUV planes using LibRaw.
     * Bypasses Java heap Bitmaps.
     */
    fun ingestDngToYuv(path: String, target: BufferSet.Slice) {
        if (target is BufferSet.Instance) {
            if (!nativeIngestDngToYuv(path, target.nativePtr)) {
                throw Exception("Native DNG ingestion failed for $path")
            }
        } else {
            throw IllegalArgumentException("Native ingestion required an Instance handle")
        }
    }

    /**
     * High-performance population of a mono (1-channel) float tensor from an OpenCV Mat.
     * Enforces strict dimension parity.
     */
    fun populateMonoTensor(src: Mat, dst: FloatArray, tensorW: Int, tensorH: Int, mean: Float, std: Float) {
        nativePopulateMonoTensor(src.nativeObj, dst, tensorW, tensorH, mean, std)
    }

    /**
     * Offloads the entire Valley Expansion algorithm to C++.
     * Reduces thousands of JNI calls to a single call.
     */
    fun expandByValley(mat: Mat, rect: android.graphics.Rect, thresholdFactor: Float = 0.40f): android.graphics.Rect {
        val res = nativeExpandByValley(mat.nativeObj, rect.left, rect.top, rect.right, rect.bottom, thresholdFactor)
        return if (res != null && res.size == 4) {
            android.graphics.Rect(res[0], res[1], res[2], res[3])
        } else rect
    }

    private external fun nativeSyncMatFromArgb(bitmap: Bitmap, matPtr: Long)
    private external fun nativeSyncMatToArgb(matPtr: Long, bitmap: Bitmap)
    private external fun nativeIngestArgbToYuv(bitmap: Bitmap, handlePtr: Long)
    private external fun nativeTestImread(path: String): String
    private external fun nativeProbeDngResolution(path: String): String
    private external fun nativeIngestJpegToYuv(path: String, handlePtr: Long): Boolean
    private external fun nativeIngestDngToYuv(path: String, handlePtr: Long): Boolean
    private external fun nativeCompressYuvToBase64(yBuf: ByteBuffer, uBuf: ByteBuffer, vBuf: ByteBuffer, w: Int, h: Int, stride: Int, quality: Int): String
    private external fun nativePopulateMonoTensor(srcMatPtr: Long, dstTensor: FloatArray, tensorW: Int, tensorH: Int, mean: Float, std: Float)
    private external fun nativeExpandByValley(matPtr: Long, l: Int, t: Int, r: Int, b: Int, threshold: Float): IntArray?

    external fun nativeHeatmapToAngle(heatmap: FloatArray, w: Int, h: Int, threshold: Float): Float
}
