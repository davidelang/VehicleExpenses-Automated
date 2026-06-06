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

    fun expandByValleyDiagnostic(mat: Mat, rect: android.graphics.Rect, thresholdFactor: Float = 0.40f): Pair<android.graphics.Rect, Map<String, String>> {
        val res = nativeExpandByValleyDiagnostic(mat.nativeObj, rect.left, rect.top, rect.right, rect.bottom, thresholdFactor)
        if (res != null && res.size == 2) {
            val summary = res[0] as IntArray
            val trace = res[1] as String
            val finalRect = android.graphics.Rect(summary[8], summary[9], summary[10], summary[11])
            val meta = mapOf(
                "valley_start" to "${summary[0]},${summary[1]}-${summary[2]},${summary[3]}",
                "valley_probed" to "${summary[4]},${summary[5]}-${summary[6]},${summary[7]}",
                "valley_retracted" to "${summary[8]},${summary[9]}-${summary[10]},${summary[11]}",
                "valley_threshold" to summary[12].toString(),
                "valley_run" to summary[13].toString(),
                "valley_lookahead" to (summary[14] / 100f).toString(),
                "valley_image_width" to summary[15].toString(),
                "valley_trace" to trace
            )
            return Pair(finalRect, meta)
        }
        return Pair(rect, emptyMap())
    }

    fun expandByCharacterAwareDiagnostic(mat: Mat, rect: android.graphics.Rect, thresholdFactor: Float = 0.40f): Pair<android.graphics.Rect, Map<String, String>> {
        val res = nativeExpandByCharacterAwareDiagnostic(mat.nativeObj, rect.left, rect.top, rect.right, rect.bottom, thresholdFactor)
        if (res != null && res.size == 6) {
            val summary = res[0] as IntArray
            val trace = res[1] as String
            val hArr = res[2] as IntArray
            val vArr = res[3] as IntArray
            val matchedSlots = res[4] as IntArray
            val failedSlots = res[5] as IntArray
            
            val finalRect = android.graphics.Rect(summary[8], summary[9], summary[10], summary[11])
            val meta = mutableMapOf(
                "charaware_start" to "${summary[0]},${summary[1]}-${summary[2]},${summary[3]}",
                "charaware_strict" to "${summary[4]},${summary[5]}-${summary[6]},${summary[7]}",
                "charaware_final" to "${summary[8]},${summary[9]}-${summary[10]},${summary[11]}",
                "charaware_threshold" to summary[12].toString(),
                "charaware_v_stroke" to summary[13].toString(),
                "charaware_h_stroke" to summary[14].toString(),
                "charaware_pitch" to summary[15].toString(),
                "charaware_trace" to trace,
                "charaware_h_hist" to hArr.joinToString(","),
                "charaware_v_hist" to vArr.joinToString(","),
                "charaware_matched_slots" to matchedSlots.joinToString(","),
                "charaware_failed_slots" to failedSlots.joinToString(",")
            )
            return Pair(finalRect, meta)
        }
        return Pair(rect, emptyMap())
    }

    fun calculateHistograms(mat: Mat, rects: List<android.graphics.Rect>): Pair<Pair<IntArray, IntArray>, IntArray>? {
        if (rects.isEmpty()) return null
        val flatRects = IntArray(rects.size * 4)
        rects.forEachIndexed { i, r ->
            flatRects[i * 4] = r.left
            flatRects[i * 4 + 1] = r.top
            flatRects[i * 4 + 2] = r.right
            flatRects[i * 4 + 3] = r.bottom
        }
        val res = nativeCalculateHistogramB64(mat.nativeObj, flatRects)
        if (res != null && res.size == 3) {
            return Pair(Pair(res[0] as IntArray, res[1] as IntArray), res[2] as IntArray)
        }
        return null
    }

    private external fun nativeCalculateHistogramB64(matPtr: Long, rects: IntArray): Array<Any>?

    fun expandByCharacterAware(mat: Mat, rect: android.graphics.Rect, thresholdFactor: Float = 0.40f): android.graphics.Rect {
        val res = nativeExpandByCharacterAware(mat.nativeObj, rect.left, rect.top, rect.right, rect.bottom, thresholdFactor)
        return if (res != null && res.size == 4) {
            android.graphics.Rect(res[0], res[1], res[2], res[3])
        } else rect
    }

    fun expandByUniformity(mat: Mat, rect: android.graphics.Rect, thresholdFactor: Float = 0.40f): Pair<android.graphics.Rect, android.graphics.Rect> {
        val res = nativeExpandByUniformity(mat.nativeObj, rect.left, rect.top, rect.right, rect.bottom, thresholdFactor)
        return if (res != null && res.size == 8) {
            Pair(
                android.graphics.Rect(res[0], res[1], res[2], res[3]),
                android.graphics.Rect(res[4], res[5], res[6], res[7])
            )
        } else Pair(rect, rect)
    }

    /**
     * Offload Paddle heatmap post-processing (threshold, contours, geometry) to C++.
     * Returns a flattened array of bounding boxes: [x1, y1, x2, y2, x3, y3, x4, y4, confidence, ...]
     */
    fun processHeatmap(tensor: Any, threshold: Float, minArea: Float): FloatArray? {
        return nativeProcessHeatmap(tensor, threshold, minArea)
    }

    fun heatmapToAngle(tensor: Any, threshold: Float): Float {
        return nativeHeatmapToAngle(tensor, threshold)
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
    external fun nativeExpandByValley(matPtr: Long, l: Int, t: Int, r: Int, b: Int, threshold: Float): IntArray?
    external fun nativeExpandByValleyDiagnostic(matPtr: Long, l: Int, t: Int, r: Int, b: Int, threshold: Float): Array<Any>?
    private external fun nativeExpandByCharacterAware(matPtr: Long, l: Int, t: Int, r: Int, b: Int, threshold: Float): IntArray?
    private external fun nativeExpandByCharacterAwareDiagnostic(matPtr: Long, l: Int, t: Int, r: Int, b: Int, threshold: Float): Array<Any>?
    private external fun nativeExpandByUniformity(matPtr: Long, l: Int, t: Int, r: Int, b: Int, threshold: Float): IntArray?
    private external fun nativeProcessHeatmap(tensor: Any, threshold: Float, minArea: Float): FloatArray?
    private external fun nativeHeatmapToAngle(tensor: Any, threshold: Float): Float

    // --------------------------------------------------------
    // SET H MODULAR PIPELINE — New granular JNI bindings.
    // No existing functions were modified.
    // --------------------------------------------------------

    /** Filters connected components in-place on a binary Mat (CV_8UC1).
     *  mode 0=PassA, 1=PassB, 2=PassC. Pass odoBuffer.s.mat after binarizing from .p. */
    fun filterComponents(mat: Mat, vSW: Float, hSW: Float, mode: Int) {
        nativeFilterComponents(mat.nativeObj, vSW, hSW, mode)
    }

    /** Histogram + vSW/hSW peak estimation with explicit thresholdFactor on odoBuffer.p.mat.
     *  Returns Pair(Pair(hArr, vArr), metaArr) where metaArr=[vSW,hSW,0,contentThreshold]. */
    fun calculateHistogramWithThreshold(mat: Mat, rects: List<android.graphics.Rect>, thresholdFactor: Float): Pair<Pair<IntArray, IntArray>, IntArray>? {
        if (rects.isEmpty()) return null
        val flatRects = IntArray(rects.size * 4)
        rects.forEachIndexed { i, r -> flatRects[i*4]=r.left; flatRects[i*4+1]=r.top; flatRects[i*4+2]=r.right; flatRects[i*4+3]=r.bottom }
        val res = nativeCalculateHistogramWithThreshold(mat.nativeObj, flatRects, thresholdFactor)
        if (res != null && res.size == 3) return Pair(Pair(res[0] as IntArray, res[1] as IntArray), res[2] as IntArray)
        return null
    }

    /** Vertical walk + bidirectional snapping on odoBuffer.p.mat.
     *  Returns the initial bounds rect for use by calculatePitch. */
    fun expandBounds(mat: Mat, rect: android.graphics.Rect, thresholdFactor: Float): android.graphics.Rect {
        val res = nativeExpandBounds(mat.nativeObj, rect.left, rect.top, rect.right, rect.bottom, thresholdFactor)
        return if (res != null && res.size == 4) android.graphics.Rect(res[0], res[1], res[2], res[3]) else rect
    }

    /** Decoupled H-variants for Set H with stroke-width aware logic */
    fun calculateHistogramWithThresholdH(mat: Mat, rects: List<android.graphics.Rect>, thresholdFactor: Float): Pair<Pair<IntArray, IntArray>, IntArray>? {
        if (rects.isEmpty()) return null
        val flatRects = IntArray(rects.size * 4)
        rects.forEachIndexed { i, r -> flatRects[i*4]=r.left; flatRects[i*4+1]=r.top; flatRects[i*4+2]=r.right; flatRects[i*4+3]=r.bottom }
        val res = nativeCalculateHistogramWithThresholdH(mat.nativeObj, flatRects, thresholdFactor)
        if (res != null && res.size == 3) return Pair(Pair(res[0] as IntArray, res[1] as IntArray), res[2] as IntArray)
        return null
    }

    fun expandBoundsH(mat: Mat, rect: android.graphics.Rect, thresholdFactor: Float, vSW: Float, hSW: Float): android.graphics.Rect {
        val res = nativeExpandBoundsH(mat.nativeObj, rect.left, rect.top, rect.right, rect.bottom, thresholdFactor, vSW, hSW)
        return if (res != null && res.size == 4) android.graphics.Rect(res[0], res[1], res[2], res[3]) else rect
    }

    fun findAllComponentsH(mat: Mat, vSW: Float, hSW: Float): List<android.graphics.Rect> {
        val res = nativeFindAllComponentsH(mat.nativeObj, vSW, hSW) ?: return emptyList()
        val list = mutableListOf<android.graphics.Rect>()
        for (i in 0 until res.size step 4) {
            if (i + 3 < res.size) {
                list.add(android.graphics.Rect(res[i], res[i+1], res[i+2], res[i+3]))
            }
        }
        return list
    }

    fun blackOutLargeComponentsH(mat: Mat, maxWidth: Float) {
        nativeBlackOutLargeComponentsH(mat.nativeObj, maxWidth)
    }

    fun blackOutLargeAndSmallComponentsH(mat: Mat, vSW: Float, hSW: Float, maxWidth: Float) {
        nativeBlackOutLargeAndSmallComponentsH(mat.nativeObj, vSW, hSW, maxWidth)
    }

    fun calculatePitchH(mat: Mat, bounds: android.graphics.Rect, thresholdFactor: Float, vSW: Float, hSW: Float): IntArray? {
        return nativeCalculatePitchH(mat.nativeObj, bounds.left, bounds.top, bounds.right, bounds.bottom, thresholdFactor, vSW, hSW)
    }

    fun alignGridH(mat: Mat, bounds: android.graphics.Rect, pitch: Int, bestShift: Int, anchorMode: Int, vSW: Float, hSW: Float, thresholdFactor: Float): Triple<android.graphics.Rect, IntArray, IntArray>? {
        val res = nativeAlignGridH(mat.nativeObj, bounds.left, bounds.top, bounds.right, bounds.bottom, pitch, bestShift, anchorMode, vSW, hSW, thresholdFactor)
        if (res != null && res.size == 3) {
            val fb = res[0] as IntArray
            return Triple(android.graphics.Rect(fb[0], fb[1], fb[2], fb[3]), res[1] as IntArray, res[2] as IntArray)
        }
        return null
    }

    /** Valley detection + pitch/anchorMode/bestShift on odoBuffer.p.mat within the given bounds.
     *  Returns IntArray[3] = [pitch, anchorMode(1=right/0=center), bestShift], or null. */
    fun calculatePitch(mat: Mat, bounds: android.graphics.Rect, thresholdFactor: Float): IntArray? {
        return nativeCalculatePitch(mat.nativeObj, bounds.left, bounds.top, bounds.right, bounds.bottom, thresholdFactor)
    }

    /** Character-aware horizontal expansion using pitch + vSW mass check on odoBuffer.p.mat.
     *  Returns Triple(finalBoundsRect, matchedSlots flat IntArray, failedSlots flat IntArray), or null. */
    fun alignGrid(mat: Mat, bounds: android.graphics.Rect, pitch: Int, bestShift: Int, anchorMode: Int, vSW: Float, hSW: Float, thresholdFactor: Float): Triple<android.graphics.Rect, IntArray, IntArray>? {
        val res = nativeAlignGrid(mat.nativeObj, bounds.left, bounds.top, bounds.right, bounds.bottom, pitch, bestShift, anchorMode, vSW, hSW, thresholdFactor)
        if (res != null && res.size == 3) {
            val fb = res[0] as IntArray
            return Triple(android.graphics.Rect(fb[0], fb[1], fb[2], fb[3]), res[1] as IntArray, res[2] as IntArray)
        }
        return null
    }

    private external fun nativeFilterComponents(matPtr: Long, vSW: Float, hSW: Float, mode: Int)
    private external fun nativeCalculateHistogramWithThreshold(matPtr: Long, rects: IntArray, thresholdFactor: Float): Array<Any>?
    private external fun nativeExpandBounds(matPtr: Long, l: Int, t: Int, r: Int, b: Int, thresholdFactor: Float): IntArray?
    private external fun nativeCalculatePitch(matPtr: Long, minX: Int, minY: Int, maxX: Int, maxY: Int, thresholdFactor: Float): IntArray?
    private external fun nativeAlignGrid(matPtr: Long, minX: Int, minY: Int, maxX: Int, maxY: Int, pitch: Int, bestShift: Int, anchorMode: Int, vSW: Float, hSW: Float, thresholdFactor: Float): Array<Any>?

    private external fun nativeCalculateHistogramWithThresholdH(matPtr: Long, rects: IntArray, thresholdFactor: Float): Array<Any>?
    private external fun nativeExpandBoundsH(matPtr: Long, l: Int, t: Int, r: Int, b: Int, thresholdFactor: Float, vSW: Float, hSW: Float): IntArray?
    private external fun nativeFindAllComponentsH(matPtr: Long, vSW: Float, hSW: Float): IntArray?
    private external fun nativeCalculatePitchH(matPtr: Long, minX: Int, minY: Int, maxX: Int, maxY: Int, thresholdFactor: Float, vSW: Float, hSW: Float): IntArray?
    private external fun nativeAlignGridH(matPtr: Long, minX: Int, minY: Int, maxX: Int, maxY: Int, pitch: Int, bestShift: Int, anchorMode: Int, vSW: Float, hSW: Float, thresholdFactor: Float): Array<Any>?
    private external fun nativeBlackOutLargeComponentsH(matPtr: Long, maxWidth: Float)
    private external fun nativeBlackOutLargeAndSmallComponentsH(matPtr: Long, vSW: Float, hSW: Float, maxWidth: Float)

}

