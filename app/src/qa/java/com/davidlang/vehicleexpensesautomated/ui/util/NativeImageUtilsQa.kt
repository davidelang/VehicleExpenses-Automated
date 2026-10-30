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

object NativeImageUtilsQa {
    init {
        System.loadLibrary("native_ocr_qa")
    }
    internal external fun nativeIngestArgbToYuv(bitmap: Bitmap, handlePtr: Long)
    internal external fun nativeTestImread(path: String): String
    internal external fun nativeProbeDngResolution(path: String): String
    internal external fun nativeIngestJpegToYuv(path: String, handlePtr: Long): Boolean
    internal external fun nativeIngestDngToYuv(path: String, handlePtr: Long): Boolean
    internal external fun nativeDumpDngDevelopStages(path: String, rgbPath: String, yPath: String): String
    internal external fun nativeWrapPackedU8(matPtr: Long, buf: ByteBuffer, s: Int): Boolean
    internal external fun nativeExpandByValleyDiagnostic(matPtr: Long, l: Int, t: Int, r: Int, b: Int, threshold: Float): Array<Any>?
    internal external fun nativeExpandByCharacterAwareDiagnostic(matPtr: Long, l: Int, t: Int, r: Int, b: Int, threshold: Float): Array<Any>?
    internal external fun nativeExpandByUniformity(matPtr: Long, l: Int, t: Int, r: Int, b: Int, threshold: Float): IntArray?

    internal external fun nativeFillEnergyLookU8(grayPtr: Long, destPtr: Long): Boolean

    internal external fun nativeProbeSeedInk(
        grayPtr: Long,
        uvPtr: Long,
        l: Int,
        t: Int,
        r: Int,
        b: Int,
        rotPts: FloatArray?,
        imgW: Int,
        wantColor: Boolean,
        sheetYPtr: Long,
        whiteYPtr: Long,
        strokeYPtr: Long,
        deskewPYPtr: Long,
        destYPtr: Long,
        destUvPtr: Long,
        destW: Int,
        destH: Int,
    ): Array<Any>?

    internal external fun nativeEnergyAabbTight(
        grayPtr: Long, uvPtr: Long, seeds: IntArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, scratchPtr: Long,
    ): IntArray?
    internal external fun nativeEnergyAabbRetract(
        grayPtr: Long, uvPtr: Long, seeds: IntArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, scratchPtr: Long,
    ): IntArray?
    internal external fun nativeEnergyOrientTight(
        grayPtr: Long, seeds: FloatArray, sweepArr: ShortArray?, scratchPtr: Long,
    ): FloatArray?
    internal external fun nativeEnergyOrientRetract(
        grayPtr: Long, seeds: FloatArray, sweepArr: ShortArray?, scratchPtr: Long,
    ): FloatArray?

    internal external fun nativeEnergyOrientExpand(
        grayPtr: Long, seeds: FloatArray, sweepArr: ShortArray?, scratchPtr: Long,
    ): FloatArray?

    internal external fun nativeEnergyAabbExpand(
        grayPtr: Long, uvPtr: Long, seeds: IntArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, scratchPtr: Long,
    ): IntArray?

    internal external fun nativeGrayAabbTight(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: IntArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        histArr: ShortArray?,
    ): IntArray?
    internal external fun nativeGrayAabbRetract(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: IntArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        histArr: ShortArray?,
    ): IntArray?

    internal external fun nativeGrayAabbExpand(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: IntArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        histArr: ShortArray?,
    ): IntArray?

    internal external fun nativeColorAabbTight(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: IntArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        histArr: ShortArray?,
    ): IntArray?

    internal external fun nativeColorAabbRetract(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: IntArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        histArr: ShortArray?,
    ): IntArray?

    internal external fun nativeColorAabbTightVsp(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: IntArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        histArr: ShortArray?,
    ): IntArray?

    internal external fun nativeColorAabbRetractVsp(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: IntArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        histArr: ShortArray?,
    ): IntArray?

    internal external fun nativeColorAabbExpand(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: IntArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        histArr: ShortArray?,
    ): IntArray?

    internal external fun nativeGrayOrientTight(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: FloatArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        histArr: ShortArray?,
    ): FloatArray?
    internal external fun nativeGrayOrientRetract(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: FloatArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        histArr: ShortArray?,
    ): FloatArray?
    internal external fun nativeColorOrientTight(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: FloatArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        tintPtr: Long,
        histArr: ShortArray?,
    ): FloatArray?
    internal external fun nativeColorOrientRetract(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: FloatArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        tintPtr: Long,
        histArr: ShortArray?,
    ): FloatArray?

    internal external fun nativeColorOrientTightVsp(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: FloatArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        tintPtr: Long,
        histArr: ShortArray?,
    ): FloatArray?
    internal external fun nativeColorOrientRetractVsp(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: FloatArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        tintPtr: Long,
        histArr: ShortArray?,
    ): FloatArray?

    internal external fun nativeGrayOrientExpand(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: FloatArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        histArr: ShortArray?,
    ): FloatArray?
    internal external fun nativeColorOrientExpand(
        grayPtr: Long, uvPtr: Long, scratchPtr: Long, seeds: FloatArray,
        teleArr: FloatArray?, sweepArr: ShortArray?, dumpPtr: Long,
        overlayYPtr: Long, overlayUvPtr: Long, poisonArr: IntArray?,
        tintPtr: Long,
        histArr: ShortArray?,
    ): FloatArray?

    /** Packed n×8 quads. Jump-retract along ±u. scratch = BufferSet.s. */
    internal external fun nativeCountPullbackOriented(
        matPtr: Long,
        seedPts: FloatArray,
        existPts: FloatArray,
        extraLook: Int,
        stopUpEnergy: Boolean,
        stopDownEnergy: Boolean,
        clearFrac: Float,
        growFrac: Float,
        maxHFrac: Float,
    ): FloatArray?
    internal external fun nativeHeatmapToAngle(tensor: Any, threshold: Float, scratchPtr: Long): Float
    internal external fun nativeHeatmapToAngleU8(
        heatU8: ByteArray,
        width: Int,
        height: Int,
        threshold: Float,
        scratchPtr: Long,
    ): Float
    internal external fun nativeVeRssSetPath(path: String)

    internal external fun nativeConnectSegmentsH(matPtr: Long, vSW: Float, hSW: Float): Int
    internal external fun nativeFilterComponents(matPtr: Long, vSW: Float, hSW: Float, mode: Int)
    internal external fun nativeCalculateHistogramWithThresholdH(
        matPtr: Long, rects: IntArray, thresholdFactor: Float,
        outH: IntArray, outV: IntArray, outMeta: IntArray
    ): Boolean
    internal external fun nativeFindAllComponentsH(matPtr: Long, vSW: Float, hSW: Float): IntArray?
    internal external fun nativeBlackOutLargeAndSmallComponentsH(matPtr: Long, vSW: Float, hSW: Float, maxWidth: Float): IntArray?
    internal external fun nativeBlackOutRollingDigitsH(matPtr: Long, vSW: Float, hSW: Float)
}


    data class OrientedExpandNative(
        val cx: Float,
        val cy: Float,
        val bw: Float,
        val bh: Float,
        val angDeg: Float,
        val stepsVNeg: Int,
        val stepsVPos: Int,
        val padV: Int,
        val hitVertCap: Boolean,
        val stopEnergyUp: Float,
        val stopEnergyDown: Float,
        val base: Float,
        val thr: Float,
    )


    data class OrientedCountNative(
        val pulledTop: Boolean,
        val pulledBot: Boolean,
        val grewTop: Boolean,
        val grewBot: Boolean,
        val cSeed: Double,
        val countThr: Double,
        val gxThr: Double,
        val vNegBefore: Double,
        val vPosBefore: Double,
        val vNegAfter: Double,
        val vPosAfter: Double,
        val padTop: Int,
        val padBot: Int,
        val y0: Int,
        val counts: DoubleArray,
        val seedCx: Float,
        val seedCy: Float,
        val existCu: Float,
        val existBw: Float,
        val angDeg: Float,
        val seedBh: Float,
    )


    /**
     * High-performance ingestion from ARGB Bitmap to BufferSet primary Mat.
     * Uses cv::cvtColor for optimized SIMD conversion.
     */
    fun NativeImageUtils.ingestArgbToYuv(src: Bitmap, target: BufferSet.Slice) {
        if (target is BufferSet.Instance) {
            NativeImageUtilsQa.nativeIngestArgbToYuv(src, target.nativePtr)
        } else {
            // Fallback for crops if ever needed, but usually we ingest to full buffer
            syncMatFromArgb(src, target.mat)
        }
    }


    /** Header Mat on packed s.raw (S×S, step S). Does not own pixels. */
    fun NativeImageUtils.wrapPackedU8(destRaw: ByteBuffer, s: Int): Mat {
        val m = Mat()
        destRaw.clear()
        if (!NativeImageUtilsQa.nativeWrapPackedU8(m.nativeObj, destRaw, s)) {
            m.release()
            return Mat()
        }
        return m
    }


    /**
     * High-performance ingestion from JPEG file directly to BufferSet YUV planes.
     * Bypasses Java heap Bitmaps.
     */
    fun NativeImageUtils.ingestJpegToYuv(path: String, target: BufferSet.Slice) {
        if (target is BufferSet.Instance) {
            if (!NativeImageUtilsQa.nativeIngestJpegToYuv(path, target.nativePtr)) {
                throw Exception("Native JPEG ingestion failed for $path")
            }
        } else {
            throw IllegalArgumentException("Native ingestion required an Instance handle")
        }
    }


    /**
     * Diagnostic: Probe image dimensions using native imread.
     */
    fun NativeImageUtils.testImread(path: String): String {
        return NativeImageUtilsQa.nativeTestImread(path)
    }


    /**
     * Diagnostic: Probe DNG dimensions using LibRaw.
     */
    fun NativeImageUtils.probeDngResolution(path: String): String {
        return NativeImageUtilsQa.nativeProbeDngResolution(path)
    }


    /**
     * High-performance ingestion from DNG file directly to BufferSet YUV planes using LibRaw.
     * Bypasses Java heap Bitmaps.
     */
    fun NativeImageUtils.ingestDngToYuv(path: String, target: BufferSet.Slice) {
        if (target is BufferSet.Instance) {
            if (!NativeImageUtilsQa.nativeIngestDngToYuv(path, target.nativePtr)) {
                throw Exception("Native DNG ingestion failed for $path")
            }
        } else {
            throw IllegalArgumentException("Native ingestion required an Instance handle")
        }
    }


    /**
     * Triage dump: LibRaw unpack CRC + developed RGB + Y after RGB2YUV.
     * Writes [rgbPath] (W*H*3 RGB u8) and [yPath] (W*H Y u8).
     * Returns JSON: ok, w, h, raw_crc32, rgb_crc32, rgb_sum, y_crc32, y_sum, …
     */
    fun NativeImageUtils.dumpDngDevelopStages(path: String, rgbPath: String, yPath: String): String {
        return NativeImageUtilsQa.nativeDumpDngDevelopStages(path, rgbPath, yPath)
    }




    fun NativeImageUtils.expandByValleyDiagnostic(mat: Mat, rect: android.graphics.Rect, thresholdFactor: Float = 0.40f): Pair<android.graphics.Rect, Map<String, String>> {
        val res = NativeImageUtilsQa.nativeExpandByValleyDiagnostic(mat.nativeObj, rect.left, rect.top, rect.right, rect.bottom, thresholdFactor)
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


    fun NativeImageUtils.expandByCharacterAwareDiagnostic(mat: Mat, rect: android.graphics.Rect, thresholdFactor: Float = 0.40f): Pair<android.graphics.Rect, Map<String, String>> {
        val res = NativeImageUtilsQa.nativeExpandByCharacterAwareDiagnostic(mat.nativeObj, rect.left, rect.top, rect.right, rect.bottom, thresholdFactor)
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





    fun NativeImageUtils.expandByUniformity(mat: Mat, rect: android.graphics.Rect, thresholdFactor: Float = 0.40f): Pair<android.graphics.Rect, android.graphics.Rect> {
        val res = NativeImageUtilsQa.nativeExpandByUniformity(mat.nativeObj, rect.left, rect.top, rect.right, rect.bottom, thresholdFactor)
        return if (res != null && res.size == 8) {
            Pair(
                android.graphics.Rect(res[0], res[1], res[2], res[3]),
                android.graphics.Rect(res[4], res[5], res[6], res[7])
            )
        } else Pair(rect, rect)
    }


    /**
     * Deskew angle from det heatmap (degrees, ~[-45, 45]).
     * Production: Hough lines on thresholded heat (phase-2 GT winner `hough_thr0.2`);
     * falls back to legacy CC + minAreaRect 0.5° buckets if no lines.
     */
    fun NativeImageUtils.heatmapToAngle(tensor: Any, threshold: Float, scratchY: Mat? = null): Float {
        return NativeImageUtilsQa.nativeHeatmapToAngle(tensor, threshold, scratchY?.nativeObj ?: 0L)
    }


    /** Deskew angle from host u8 heat plane (tiled det). thr is float on heat in [0,1] (u8/255). */
    fun NativeImageUtils.heatmapToAngleU8(
        heatU8: ByteArray,
        width: Int,
        height: Int,
        threshold: Float,
        scratchY: Mat? = null,
    ): Float {
        return NativeImageUtilsQa.nativeHeatmapToAngleU8(heatU8, width, height, threshold, scratchY?.nativeObj ?: 0L)
    }


    fun NativeImageUtils.veRssSetPath(path: String) {
        NativeImageUtilsQa.nativeVeRssSetPath(path)
    }


    fun NativeImageUtils.fillEnergyLookU8(gray: Mat, dest: Mat): Boolean {
        if (gray.empty() || dest.empty()) return false
        return NativeImageUtilsQa.nativeFillEnergyLookU8(gray.nativeObj, dest.nativeObj)
    }


    fun NativeImageUtils.probeSeedInk(
        grayPtr: Long,
        uvPtr: Long,
        l: Int,
        t: Int,
        r: Int,
        b: Int,
        rotPts: FloatArray?,
        imgW: Int,
        wantColor: Boolean,
        sheetYPtr: Long,
        whiteYPtr: Long,
        strokeYPtr: Long,
        deskewPYPtr: Long,
        destYPtr: Long,
        destUvPtr: Long,
        destW: Int,
        destH: Int,
    ): Array<Any>? = NativeImageUtilsQa.nativeProbeSeedInk(
        grayPtr, uvPtr, l, t, r, b, rotPts, imgW, wantColor,
        sheetYPtr, whiteYPtr, strokeYPtr, deskewPYPtr, destYPtr, destUvPtr, destW, destH,
    )


    fun NativeImageUtils.energyAabbTightNative(
        gray: Mat, uv: Mat?, seeds: IntArray,
        tele: FloatArray?, sweep: ShortArray?, scratch: Mat?,
    ): IntArray? {
        if (gray.empty()) return IntArray(0)
        return NativeImageUtilsQa.nativeEnergyAabbTight(
            gray.nativeObj, uv?.nativeObj ?: 0L, seeds, tele, sweep, scratch?.nativeObj ?: 0L,
        )
    }


    fun NativeImageUtils.energyOrientTightNative(
        gray: Mat, seeds: FloatArray, sweep: ShortArray?, scratch: Mat?,
    ): FloatArray? {
        if (gray.empty() || seeds.isEmpty()) return FloatArray(0)
        return NativeImageUtilsQa.nativeEnergyOrientTight(gray.nativeObj, seeds, sweep, scratch?.nativeObj ?: 0L)
    }

    fun NativeImageUtils.energyOrientRetractNative(
        gray: Mat, seeds: FloatArray, sweep: ShortArray?, scratch: Mat?,
    ): FloatArray? {
        if (gray.empty() || seeds.isEmpty()) return FloatArray(0)
        return NativeImageUtilsQa.nativeEnergyOrientRetract(gray.nativeObj, seeds, sweep, scratch?.nativeObj ?: 0L)
    }


    fun NativeImageUtils.energyOrientExpandNative(
        gray: Mat, seeds: FloatArray, sweep: ShortArray?, scratch: Mat?,
    ): FloatArray? {
        if (gray.empty() || seeds.isEmpty()) return FloatArray(0)
        return NativeImageUtilsQa.nativeEnergyOrientExpand(gray.nativeObj, seeds, sweep, scratch?.nativeObj ?: 0L)
    }


    fun NativeImageUtils.energyAabbRetractNative(
        gray: Mat, uv: Mat?, seeds: IntArray,
        tele: FloatArray?, sweep: ShortArray?, scratch: Mat?,
    ): IntArray? {
        if (gray.empty()) return IntArray(0)
        return NativeImageUtilsQa.nativeEnergyAabbRetract(
            gray.nativeObj, uv?.nativeObj ?: 0L, seeds, tele, sweep, scratch?.nativeObj ?: 0L,
        )
    }


    fun NativeImageUtils.energyAabbExpandNative(
        gray: Mat, uv: Mat?, seeds: IntArray,
        tele: FloatArray?, sweep: ShortArray?, scratch: Mat?,
    ): IntArray? {
        if (gray.empty()) return IntArray(0)
        return NativeImageUtilsQa.nativeEnergyAabbExpand(
            gray.nativeObj, uv?.nativeObj ?: 0L, seeds, tele, sweep, scratch?.nativeObj ?: 0L,
        )
    }


    private fun NativeImageUtils.aabbPtrs(
        gray: Mat, uv: Mat?, scratch: Mat?, combine: Mat?, overlayY: Mat?, overlayUv: Mat?,
    ): LongArray = longArrayOf(
        gray.nativeObj,
        uv?.nativeObj ?: 0L,
        scratch?.nativeObj ?: 0L,
        combine?.nativeObj ?: 0L,
        overlayY?.nativeObj ?: 0L,
        overlayUv?.nativeObj ?: 0L,
    )


    fun NativeImageUtils.grayAabbTightNative(
        gray: Mat, uv: Mat?, seeds: IntArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        hist: ShortArray? = null,
    ): IntArray? {
        if (gray.empty()) return IntArray(0)
        val p = aabbPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeGrayAabbTight(p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats, hist)
    }

    fun NativeImageUtils.grayAabbRetractNative(
        gray: Mat, uv: Mat?, seeds: IntArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        hist: ShortArray? = null,
    ): IntArray? {
        if (gray.empty()) return IntArray(0)
        val p = aabbPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeGrayAabbRetract(p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats, hist)
    }


    fun NativeImageUtils.grayAabbExpandNative(
        gray: Mat, uv: Mat?, seeds: IntArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        hist: ShortArray? = null,
    ): IntArray? {
        if (gray.empty()) return IntArray(0)
        val p = aabbPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeGrayAabbExpand(p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats, hist)
    }


    fun NativeImageUtils.colorAabbTightNative(
        gray: Mat, uv: Mat?, seeds: IntArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        hist: ShortArray? = null,
    ): IntArray? {
        if (gray.empty()) return IntArray(0)
        val p = aabbPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeColorAabbTight(p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats, hist)
    }


    fun NativeImageUtils.colorAabbRetractNative(
        gray: Mat, uv: Mat?, seeds: IntArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        hist: ShortArray? = null,
    ): IntArray? {
        if (gray.empty()) return IntArray(0)
        val p = aabbPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeColorAabbRetract(p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats, hist)
    }


    fun NativeImageUtils.colorAabbTightVspNative(
        gray: Mat, uv: Mat?, seeds: IntArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        hist: ShortArray? = null,
    ): IntArray? {
        if (gray.empty()) return IntArray(0)
        val p = aabbPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeColorAabbTightVsp(p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats, hist)
    }


    fun NativeImageUtils.colorAabbRetractVspNative(
        gray: Mat, uv: Mat?, seeds: IntArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        hist: ShortArray? = null,
    ): IntArray? {
        if (gray.empty()) return IntArray(0)
        val p = aabbPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeColorAabbRetractVsp(p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats, hist)
    }


    fun NativeImageUtils.colorAabbExpandNative(
        gray: Mat, uv: Mat?, seeds: IntArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        hist: ShortArray? = null,
    ): IntArray? {
        if (gray.empty()) return IntArray(0)
        val p = aabbPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeColorAabbExpand(p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats, hist)
    }


    private fun NativeImageUtils.orientPtrs(
        gray: Mat, uv: Mat?, scratch: Mat?, combine: Mat?, overlayY: Mat?, overlayUv: Mat?,
    ): LongArray = aabbPtrs(gray, uv, scratch, combine, overlayY, overlayUv)


    fun NativeImageUtils.grayOrientTightNative(
        gray: Mat, uv: Mat?, seeds: FloatArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        hist: ShortArray? = null,
    ): FloatArray? {
        if (gray.empty() || seeds.isEmpty()) return FloatArray(0)
        val p = orientPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeGrayOrientTight(p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats, hist)
    }

    fun NativeImageUtils.grayOrientRetractNative(
        gray: Mat, uv: Mat?, seeds: FloatArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        hist: ShortArray? = null,
    ): FloatArray? {
        if (gray.empty() || seeds.isEmpty()) return FloatArray(0)
        val p = orientPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeGrayOrientRetract(p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats, hist)
    }

    fun NativeImageUtils.colorOrientTightNative(
        gray: Mat, uv: Mat?, seeds: FloatArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        tint: Mat? = null,
        hist: ShortArray? = null,
    ): FloatArray? {
        if (gray.empty() || seeds.isEmpty()) return FloatArray(0)
        val p = orientPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeColorOrientTight(
            p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats,
            tint?.nativeObj ?: 0L, hist,
        )
    }

    fun NativeImageUtils.colorOrientRetractNative(
        gray: Mat, uv: Mat?, seeds: FloatArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        tint: Mat? = null,
        hist: ShortArray? = null,
    ): FloatArray? {
        if (gray.empty() || seeds.isEmpty()) return FloatArray(0)
        val p = orientPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeColorOrientRetract(
            p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats,
            tint?.nativeObj ?: 0L, hist,
        )
    }


    fun NativeImageUtils.colorOrientTightVspNative(
        gray: Mat, uv: Mat?, seeds: FloatArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        tint: Mat? = null,
        hist: ShortArray? = null,
    ): FloatArray? {
        if (gray.empty() || seeds.isEmpty()) return FloatArray(0)
        val p = orientPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeColorOrientTightVsp(
            p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats,
            tint?.nativeObj ?: 0L, hist,
        )
    }

    fun NativeImageUtils.colorOrientRetractVspNative(
        gray: Mat, uv: Mat?, seeds: FloatArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        tint: Mat? = null,
        hist: ShortArray? = null,
    ): FloatArray? {
        if (gray.empty() || seeds.isEmpty()) return FloatArray(0)
        val p = orientPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeColorOrientRetractVsp(
            p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats,
            tint?.nativeObj ?: 0L, hist,
        )
    }


    fun NativeImageUtils.grayOrientExpandNative(
        gray: Mat, uv: Mat?, seeds: FloatArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        hist: ShortArray? = null,
    ): FloatArray? {
        if (gray.empty() || seeds.isEmpty()) return FloatArray(0)
        val p = orientPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeGrayOrientExpand(p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats, hist)
    }

    fun NativeImageUtils.colorOrientExpandNative(
        gray: Mat, uv: Mat?, seeds: FloatArray,
        scratch: Mat?, tele: FloatArray?, sweep: ShortArray?,
        combine: Mat?, overlayY: Mat?, overlayUv: Mat?, poisonStats: IntArray?,
        tint: Mat? = null,
        hist: ShortArray? = null,
    ): FloatArray? {
        if (gray.empty() || seeds.isEmpty()) return FloatArray(0)
        val p = orientPtrs(gray, uv, scratch, combine, overlayY, overlayUv)
        return NativeImageUtilsQa.nativeColorOrientExpand(
            p[0], p[1], p[2], seeds, tele, sweep, p[3], p[4], p[5], poisonStats,
            tint?.nativeObj ?: 0L, hist,
        )
    }


    fun NativeImageUtils.countPullbackOrientedNative(
        gray: Mat,
        seedPts: FloatArray,
        existPts: FloatArray,
        extraLook: Int,
        stopUpEnergy: Boolean,
        stopDownEnergy: Boolean,
        clearFrac: Float,
        growFrac: Float,
        maxHFrac: Float,
    ): OrientedCountNative? {
        val r = NativeImageUtilsQa.nativeCountPullbackOriented(
            gray.nativeObj, seedPts, existPts, extraLook,
            stopUpEnergy, stopDownEnergy, clearFrac, growFrac, maxHFrac,
        ) ?: return null
        if (r.size < 24) return null
        val n = r[14].toInt().coerceAtLeast(0)
        if (r.size < 24 + n) return null
        val counts = DoubleArray(n) { i -> r[24 + i].toDouble() }
        return OrientedCountNative(
            pulledTop = r[0] >= 0.5f,
            pulledBot = r[1] >= 0.5f,
            grewTop = r[2] >= 0.5f,
            grewBot = r[3] >= 0.5f,
            cSeed = r[4].toDouble(),
            countThr = r[5].toDouble(),
            gxThr = r[6].toDouble(),
            vNegBefore = r[7].toDouble(),
            vPosBefore = r[8].toDouble(),
            vNegAfter = r[9].toDouble(),
            vPosAfter = r[10].toDouble(),
            padTop = r[11].toInt(),
            padBot = r[12].toInt(),
            y0 = r[13].toInt(),
            counts = counts,
            seedCx = r[15],
            seedCy = r[16],
            existCu = r[17],
            existBw = r[18],
            angDeg = r[19],
            seedBh = r[20],
        )
    }


    // --------------------------------------------------------
    // SET H MODULAR PIPELINE — New granular JNI bindings.
    // No existing functions were modified.
    // --------------------------------------------------------

    /** Filters connected components in-place on a binary Mat (CV_8UC1).
     *  mode 0=PassA, 1=PassB, 2=PassC. Pass odoBuffer.s.mat after binarizing from .p. */
    fun NativeImageUtils.filterComponents(mat: Mat, vSW: Float, hSW: Float, mode: Int) {
        NativeImageUtilsQa.nativeFilterComponents(mat.nativeObj, vSW, hSW, mode)
    }


    /** Decoupled H-variants for Set H with stroke-width aware logic */
    fun NativeImageUtils.calculateHistogramWithThresholdH(mat: Mat, rects: List<android.graphics.Rect>, thresholdFactor: Float): Pair<Pair<IntArray, IntArray>, IntArray>? {
        if (mat.empty() || rects.isEmpty()) return null
        val valid = rects.filter { it.width() > 0 && it.height() > 0 }
        if (valid.isEmpty()) return null
        if (runHistReuseCount == 0L) {
            android.util.Log.i("MAT_HEADER", "using longLivedRunHistH/V buffers (${HIST_BIN_COUNT} bins)")
        }
        logMatHeader(mat, "kotlin_pre_hist_H")
        val flatRects = IntArray(valid.size * 4)
        valid.forEachIndexed { i, r -> flatRects[i*4]=r.left; flatRects[i*4+1]=r.top; flatRects[i*4+2]=r.right; flatRects[i*4+3]=r.bottom }
        if (!NativeImageUtilsQa.nativeCalculateHistogramWithThresholdH(
                mat.nativeObj, flatRects, thresholdFactor,
                longLivedRunHistH, longLivedRunHistV, longLivedHistMeta
            )) return null
        runHistReuseCount++
        android.util.Log.i("MAT_HEADER", "long-lived H hist path used (count=$runHistReuseCount)")
        return Pair(Pair(longLivedRunHistH, longLivedRunHistV), longLivedHistMeta)
    }



    fun NativeImageUtils.findAllComponentsH(mat: Mat, vSW: Float, hSW: Float): List<android.graphics.Rect> {
        val res = NativeImageUtilsQa.nativeFindAllComponentsH(mat.nativeObj, vSW, hSW) ?: return emptyList()
        val list = mutableListOf<android.graphics.Rect>()
        for (i in 0 until res.size step 4) {
            if (i + 3 < res.size) {
                list.add(android.graphics.Rect(res[i], res[i+1], res[i+2], res[i+3]))
            }
        }
        return list
    }


    fun NativeImageUtils.blackOutLargeAndSmallComponentsH(mat: Mat, vSW: Float, hSW: Float, maxWidth: Float) {
        NativeImageUtilsQa.nativeBlackOutLargeAndSmallComponentsH(mat.nativeObj, vSW, hSW, maxWidth)
    }





    fun NativeImageUtils.connectSegmentsH(mat: Mat, vSW: Float, hSW: Float): Int {
        return NativeImageUtilsQa.nativeConnectSegmentsH(mat.nativeObj, vSW, hSW)
    }


    fun NativeImageUtils.blackOutRollingDigitsH(mat: Mat, vSW: Float, hSW: Float) {
        NativeImageUtilsQa.nativeBlackOutRollingDigitsH(mat.nativeObj, vSW, hSW)
    }

