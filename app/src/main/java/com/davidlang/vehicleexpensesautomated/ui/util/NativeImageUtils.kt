package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Standalone high-performance image synchronization and processing utilities.
 * Decoupled from BufferSetLegacy/MemoryBridge life-cycles.
 */
object NativeImageUtils {
    init {
        System.loadLibrary("native_ocr")
    }

    // Long-lived reusable buffers for run-length histogram output (H and legacy paths).
    // 8192 bins * 4 bytes = 32 kB per array. Two arrays (h + v) for horiz/vert run counts.
    // NOTE: 8k entries sufficient for run lengths on images up to ~4G pixels in theory.
    // In practice images are << 4k×4k so 24 bits would suffice, but 32 kB is not large
    // enough to justify the complication of using a 24-bit packed format.
    // Same storage can back brightness histograms using uint8 interpretation if a
    // native brightness path is added (or for zero-copy views). Brightness currently
    // uses OpenCV calcHist (small 64/256-bin FloatArrays in getHistStats / generateGatedHistogramB64).
    const val HIST_BIN_COUNT = 8192

    val longLivedRunHistH = IntArray(HIST_BIN_COUNT)
    val longLivedRunHistV = IntArray(HIST_BIN_COUNT)
    val longLivedHistMeta = IntArray(4)
    // Optional brightness reuse (uint8); native fast path may use this later instead of calcHist.
    val longLivedBrightness = ByteArray(HIST_BIN_COUNT)
    @Volatile var runHistReuseCount: Long = 0

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
    fun drawYuvAnnotations(handle: BufferSet.YuvHandle, annotations: List<SnapshotAnnotation>) {
        if (annotations.isEmpty()) return

        // Create temporary Mat wrappers that respect the native memory stride
        val yPlane = handle.planes[0]
        val uvPlane = handle.planes[2] // V plane is start of interleaved UV in NV21

        val yMat = Mat(handle.height, handle.width, CvType.CV_8UC1, yPlane.buffer, yPlane.rowStride.toLong())
        val uvMat = Mat(handle.height / 2, handle.width / 2, CvType.CV_8UC2, uvPlane.buffer, uvPlane.rowStride.toLong())

        annotations.forEach { ann ->
            val (yVal, uVal, vVal) = when (ann.color) {
                AnnYuv.RED -> Triple(AnnYuv.RED.y, AnnYuv.RED.u, AnnYuv.RED.v)
                AnnYuv.ORANGE -> Triple(AnnYuv.ORANGE.y, AnnYuv.ORANGE.u, AnnYuv.ORANGE.v)
                AnnYuv.BLUE -> Triple(AnnYuv.BLUE.y, AnnYuv.BLUE.u, AnnYuv.BLUE.v)
                AnnYuv.CYAN -> Triple(AnnYuv.CYAN.y, AnnYuv.CYAN.u, AnnYuv.CYAN.v)
                AnnYuv.YELLOW -> Triple(AnnYuv.YELLOW.y, AnnYuv.YELLOW.u, AnnYuv.YELLOW.v)
                AnnYuv.WHITE -> Triple(AnnYuv.WHITE.y, AnnYuv.WHITE.u, AnnYuv.WHITE.v)
            }

            val p1 = Point(ann.x1.toDouble(), ann.y1.toDouble())
            val p2 = Point(ann.x2.toDouble(), ann.y2.toDouble())
            val yTh = max(1, ann.strokeWidth)
            val uvTh = max(1, (ann.strokeWidth + 1) / 2)

            if (ann.shape == Shape.RECTANGLE) {
                Imgproc.rectangle(yMat, p1, p2, Scalar(yVal), yTh)
                Imgproc.rectangle(uvMat, Point(p1.x / 2.0, p1.y / 2.0), Point(p2.x / 2.0, p2.y / 2.0), Scalar(vVal, uVal), uvTh)
            } else {
                Imgproc.line(yMat, p1, p2, Scalar(yVal), yTh)
                Imgproc.line(uvMat, Point(p1.x / 2.0, p1.y / 2.0), Point(p2.x / 2.0, p2.y / 2.0), Scalar(vVal, uVal), uvTh)
            }
        }

        yMat.release()
        uvMat.release()
    }

    /**
     * Draw on the Y/UV Mats that JPEG encode uses. Exhaustive [AnnYuv] `when`, no `else`.
     * Y stroke `max(1,stroke)`; UV `max(1,(stroke+1)/2)`; UV [Scalar] is `(v,u)`.
     */
    fun drawYuvAnnotations(yMat: Mat, uvMat: Mat?, annotations: List<SnapshotAnnotation>) {
        if (annotations.isEmpty() || yMat.empty()) return
        val uv = if (uvMat != null && !uvMat.empty()) uvMat else null
        annotations.forEach { ann ->
            val (yVal, uVal, vVal) = when (ann.color) {
                AnnYuv.RED -> Triple(AnnYuv.RED.y, AnnYuv.RED.u, AnnYuv.RED.v)
                AnnYuv.ORANGE -> Triple(AnnYuv.ORANGE.y, AnnYuv.ORANGE.u, AnnYuv.ORANGE.v)
                AnnYuv.BLUE -> Triple(AnnYuv.BLUE.y, AnnYuv.BLUE.u, AnnYuv.BLUE.v)
                AnnYuv.CYAN -> Triple(AnnYuv.CYAN.y, AnnYuv.CYAN.u, AnnYuv.CYAN.v)
                AnnYuv.YELLOW -> Triple(AnnYuv.YELLOW.y, AnnYuv.YELLOW.u, AnnYuv.YELLOW.v)
                AnnYuv.WHITE -> Triple(AnnYuv.WHITE.y, AnnYuv.WHITE.u, AnnYuv.WHITE.v)
            }

            val p1 = Point(ann.x1.toDouble(), ann.y1.toDouble())
            val p2 = Point(ann.x2.toDouble(), ann.y2.toDouble())
            val yTh = max(1, ann.strokeWidth)
            val uvTh = max(1, (ann.strokeWidth + 1) / 2)

            if (ann.shape == Shape.RECTANGLE) {
                Imgproc.rectangle(yMat, p1, p2, Scalar(yVal), yTh)
                if (uv != null) {
                    Imgproc.rectangle(
                        uv,
                        Point(p1.x / 2.0, p1.y / 2.0),
                        Point(p2.x / 2.0, p2.y / 2.0),
                        Scalar(vVal, uVal),
                        uvTh,
                    )
                }
            } else {
                Imgproc.line(yMat, p1, p2, Scalar(yVal), yTh)
                if (uv != null) {
                    Imgproc.line(
                        uv,
                        Point(p1.x / 2.0, p1.y / 2.0),
                        Point(p2.x / 2.0, p2.y / 2.0),
                        Scalar(vVal, uVal),
                        uvTh,
                    )
                }
            }
        }
    }


    /** Packed S×S scale of src Y into dest DirectByteBuffer[0, S²] (inner top-left; rest 0). */
    fun scalePackedU8(srcY: Mat, destRaw: ByteBuffer, s: Int, innerW: Int, innerH: Int): Boolean {
        if (srcY.empty() || s < 1) return false
        destRaw.clear()
        return nativeScalePackedU8(srcY.nativeObj, destRaw, s, innerW, innerH)
    }

    /** Scale src Y/UV ROI into dest crop (already sized). No third-plane alloc. */
    fun scaleYuvRoi(srcY: Mat, srcUv: Mat?, roi: android.graphics.Rect, dstY: Mat, dstUv: Mat): Boolean {
        if (srcY.empty() || dstY.empty()) return false
        return nativeScaleYuvRoi(
            srcY.nativeObj, srcUv?.nativeObj ?: 0L,
            roi.left, roi.top, roi.right, roi.bottom,
            dstY.nativeObj, dstUv.nativeObj,
        )
    }

    /**
     * Area-scale [src] into the top-left [writeW]×[writeH] of [dest] at scale [s].
     * Same kernel as [com.davidlang.vehicleexpensesautomated.ui.util.RecBufferFeed.scaleIsotropicNoPartial].
     * Does not clear outside that rectangle. Pointers are used only for this call.
     */
    fun scaleIsotropicArea(src: Mat, dest: Mat, s: Float, writeW: Int, writeH: Int): Boolean {
        if (src.empty() || dest.empty() || s <= 0f || writeW < 1 || writeH < 1) return false
        return nativeScaleIsotropicArea(src.nativeObj, dest.nativeObj, s, writeW, writeH)
    }

    /** JPEG bytes of dest-crop YUV. Native fail → empty. */
    fun encodeYuvMatJpeg(y: Mat, uv: Mat?, quality: Int): ByteArray {
        if (y.empty()) return ByteArray(0)
        return nativeEncodeYuvMatJpeg(y.nativeObj, uv?.nativeObj ?: 0L, quality) ?: ByteArray(0)
    }


    /**
     * One grow cell is 4 pixels on the output heat array (`packHeatmapBoxes` `cell = gc * 4`).
     * That is one stride-4 det cell. Metadata `heatmap_cell_px` matches that native grow.
     */
    const val PADDLE_DET_HEAT_CELL_PX: Int = 4

    /** Production: rotated min-area rect on supra-threshold heat pixels. */
    const val HEATMAP_BOX_MIN_AREA_RECT: Int = 0
    /** Experiment Set K: axis-aligned min/max of the same on-mask connected component. */
    const val HEATMAP_BOX_AABB: Int = 1

    /**
     * Offload Paddle heatmap post-processing (threshold, contours, geometry) to C++.
     * Returns a flattened array of bounding boxes: [x1, y1, x2, y2, x3, y3, x4, y4, confidence, ...]
     * then 100 histogram bins.
     *
     * When the det tensor is **kUInt8** (product heatmaps), thr / CC / AABB|minAreaRect / hist
     * run entirely on the u8 plane — **no full fp32 heat buffer**. float/fp16 still convert once.
     *
     * @param boxMode [HEATMAP_BOX_MIN_AREA_RECT] (default) or [HEATMAP_BOX_AABB]
     */
    fun processHeatmap(
        tensor: Any,
        threshold: Float,
        minArea: Float,
        boxMode: Int = HEATMAP_BOX_MIN_AREA_RECT,
        /** 3x3 morph dilate passes on binary thr mask before CC (merges nearby heat). 0 = off. */
        maskDilatePasses: Int = 0,
        /** 0 = no box cap. */
        maxBoxes: Int = 0,
        /** 0 = CC AABB as-is; 1 = grow one 4×4 det cell (production default). */
        growCells: Int = 1,
        /** A.p Y: mask + CC labels + edges headers. Null = JNI fail (no heap). */
        scratchY: Mat? = null,
        /** >0: pack vertices in photo px (max(photo)/max(content)). 0 = heat space. */
        heatToPhoto: Float = 0f,
        photoW: Int = 0,
        photoH: Int = 0,
    ): FloatArray {
        return nativeProcessHeatmap(
            tensor, threshold, minArea, boxMode, maskDilatePasses, maxBoxes, growCells,
            scratchY?.nativeObj ?: 0L, heatToPhoto, photoW, photoH,
        ) ?: throw IllegalStateException("processHeatmap")
    }

    /**
     * Product u8 heatmap post from a host plane (e.g. tiled max-merge). Same thr/CC/box path as tensor u8.
     */
    fun processHeatmapU8(
        heatU8: ByteArray,
        width: Int,
        height: Int,
        threshold: Float,
        minArea: Float,
        boxMode: Int = HEATMAP_BOX_MIN_AREA_RECT,
        maskDilatePasses: Int = 0,
        maxBoxes: Int = 0,
        growCells: Int = 1,
        scratchY: Mat? = null,
        heatToPhoto: Float = 0f,
        photoW: Int = 0,
        photoH: Int = 0,
    ): FloatArray {
        return nativeProcessHeatmapU8(
            heatU8, width, height, threshold, minArea, boxMode, maskDilatePasses, maxBoxes,
            growCells, scratchY?.nativeObj ?: 0L, heatToPhoto, photoW, photoH,
        ) ?: throw IllegalStateException("processHeatmapU8")
    }

    /**
     * Path taken by the most recent [processHeatmap] on this process: `"u8"` or `"float"`.
     * Used for speed/accuracy experiment metadata (not a concurrency-safe multi-engine API).
     */
    fun lastHeatmapPostPath(): String = nativeLastHeatmapPostPath() ?: "unknown"

    /**
     * Safe float heatmap plane from det output (float32 / uint8 / fp16).
     * Do not use Tensor.getFloatData on non-float tensors — that crashes native.
     */
    fun heatmapToFloatArray(tensor: Any): FloatArray {
        return nativeHeatmapToFloatArray(tensor) ?: throw IllegalStateException("heatmapToFloatArray")
    }

    /**
     * Raw product heatmap as uint8 plane (row-major w*h). Prefers native u8 tensor copy;
     * float/fp16 tensors are quantized via round(x*255).
     */
    fun heatmapToUInt8Array(tensor: Any): ByteArray {
        return nativeHeatmapToUInt8Array(tensor) ?: throw IllegalStateException("heatmapToUInt8Array")
    }

    @JvmStatic
    external fun nativePredictorGuardEnter()

    @JvmStatic
    external fun nativePredictorGuardExit()

    @JvmStatic
    external fun nativeDevelopDngToJpeg(inFd: Int, outPath: String, quality: Int): IntArray

    @JvmStatic
    external fun nativeDevelopDngToBitmap(inFd: Int): android.graphics.Bitmap

    private external fun nativeSyncMatFromArgb(bitmap: Bitmap, matPtr: Long)
    private external fun nativeSyncMatToArgb(matPtr: Long, bitmap: Bitmap)
    private external fun nativeScalePackedU8(
        srcYPtr: Long, dstBuf: ByteBuffer, s: Int, innerW: Int, innerH: Int,
    ): Boolean
    private external fun nativeScaleYuvRoi(
        srcYPtr: Long, srcUvPtr: Long,
        sl: Int, st: Int, sr: Int, sb: Int,
        dstYPtr: Long, dstUvPtr: Long,
    ): Boolean
    private external fun nativeScaleIsotropicArea(
        srcPtr: Long, destPtr: Long, s: Float, writeW: Int, writeH: Int,
    ): Boolean
    private external fun nativeEncodeYuvMatJpeg(yPtr: Long, uvPtr: Long, quality: Int): ByteArray?
    private external fun nativePopulateMonoUInt8(srcMatPtr: Long, dstTensor: ByteArray, tensorW: Int, tensorH: Int)


    /** Raw greyscale copy for kUInt8 feed (no XOR). Still copies into [dst] for setData. */
    fun populateMonoUInt8(src: Mat, dst: ByteArray, tensorW: Int, tensorH: Int) {
        nativePopulateMonoUInt8(src.nativeObj, dst, tensorW, tensorH)
    }

    /**
     * Point Lite **input** [tensor] at [data][offset..offset+nbytes) via ShareExternalMemory.
     * Hold pin until [releaseSharedTensorInputU8]. Returns 1 = zero-copy pin, 2 = JVM copied
     * the full array (still usable, not ideal), 0 = fail (caller should densify + setData).
     */
    fun shareTensorInputU8(tensor: Any, data: ByteArray, offset: Int, nbytes: Int): Int =
        nativeShareTensorInputU8(tensor, data, offset, nbytes)

    fun shareTensorInputU8(tensor: Any, data: ByteBuffer, offset: Int, nbytes: Int): Int =
        nativeShareTensorInputU8Direct(tensor, data, offset, nbytes)

    fun releaseSharedTensorInputU8() {
        nativeReleaseSharedTensorInputU8()
    }

    private external fun nativeShareTensorInputU8(
        tensor: Any,
        data: ByteArray,
        offset: Int,
        nbytes: Int,
    ): Int

    private external fun nativeShareTensorInputU8Direct(
        tensor: Any,
        data: ByteBuffer,
        offset: Int,
        nbytes: Int,
    ): Int

    private external fun nativeReleaseSharedTensorInputU8()

    const val SEG7_HIST_BINS: Int = 300
    const val SEG7_ENERGY_HIST_BINS: Int = 64
    const val SEG7_ATTEMPT_MAX: Int = 10
    const val SEG7_ATTEMPT_F: Int = 15
    const val SEG7_TELE_N: Int =
        26 + SEG7_ENERGY_HIST_BINS * 2 + 6 + 1 + SEG7_ATTEMPT_MAX * SEG7_ATTEMPT_F + 11
    private external fun nativeProcessHeatmap(
        tensor: Any,
        threshold: Float,
        minArea: Float,
        boxMode: Int,
        maskDilatePasses: Int,
        maxBoxes: Int,
        growCells: Int,
        scratchPtr: Long,
        heatToPhoto: Float,
        photoW: Int,
        photoH: Int,
    ): FloatArray?
    private external fun nativeProcessHeatmapU8(
        heatU8: ByteArray,
        width: Int,
        height: Int,
        threshold: Float,
        minArea: Float,
        boxMode: Int,
        maskDilatePasses: Int,
        maxBoxes: Int,
        growCells: Int,
        scratchPtr: Long,
        heatToPhoto: Float,
        photoW: Int,
        photoH: Int,
    ): FloatArray?
    private external fun nativeLastHeatmapPostPath(): String?
    private external fun nativeHeatmapToFloatArray(tensor: Any): FloatArray?
    private external fun nativeHeatmapToUInt8Array(tensor: Any): ByteArray?


    /** Dump full native cv::Mat header fields for crash forensics (MAT_HEADER tag). */
    fun logMatHeader(mat: Mat, tag: String) {
        if (mat.empty()) {
            android.util.Log.i("MAT_HEADER", "$tag empty kotlin cols=${mat.cols()} rows=${mat.rows()}")
            return
        }
        nativeLogMatHeader(mat.nativeObj, tag)
    }


    data class ComponentStats(
        val index: Int,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val area: Int
    )

    private external fun nativeLogMatHeader(matPtr: Long, tag: String)

}
