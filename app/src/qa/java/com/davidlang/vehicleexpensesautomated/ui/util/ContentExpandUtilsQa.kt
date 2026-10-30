package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Rect
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Residual content-aware expand from a seed rect on full-res gray (experiment P / multi-scale).
 *
 * ## Tunables (what callers can pass)
 *
 * | Param | Default | Role |
 * |-------|---------|------|
 * | [mode] | — | Grow rule: dual ink, interior energy (P), edge ring, or v0.25+dual |
 * | [maxFrac] | 1.0 | Cap pad each side as fraction of **seed height** (walk budget) |
 * | [enableJump] | false | After grow stops: **horizontal** jump, then retract to ink edge |
 * | [jumpFrac] | 0.40 | Horizontal jump distance = this × **expanded** height (alignment-style) |
 * | [retractClearFrac] | 0.30 | After retract hits ink, pad **left/right only** by this × H |
 * | [energyRatio] | 0.45 | INTERIOR_ENERGY: strip must keep ≥ this × seed-interior mean energy |
 * | [vertEnergy] | MAGNITUDE | MAGNITUDE = |∇|; GX = |∂I/∂x|; XYCUT_GX = peak-isolate on gx; CHI2 = 16-bin row hist vs seed |
 * | [freezeHorzDuringVert] | false | If true, first grow is top/bottom only (seed width frozen) |
 * | [vertPadFrac] | 0 | After vertical stop, pad each tip by this × seedH (one scale, not a G list) |
 * | count pullback | post | Additive: first run-count valley below 0.45×seed median; then one-direction pad (clear after pull / grow if energy-stop + tip still in digits); AABB = image y / Sobel-x; rot = ±v / \|∇I·û\| |
 * | [minFrac] | 0.02 | DUAL_SAUVOLA / mask: min ink fraction in 1px strip to keep growing |
 * | [minEdgeRatio] | 0.35 | EDGE_RING: strip edge density vs seed perimeter |
 *
 * Dual-adaptive / energy thresholds also embed OpenCV constants (block 31, C=5, Canny 40/120)
 * that are not caller-facing yet.
 *
 * ## Jump + retract (optional, mirrors dash [expandByUniformity] intent)
 *
 * Alignment path: grow to text edge → jump out by ~0.4×height → if still “in text” keep
 * expanding logic; if not (usual), retract until the content edge. Here for INTERIOR_ENERGY /
 * mask modes we: (1) jump **L/R only** by [jumpFrac]×H; (2) if the jumped
 * L/R boundary strip is still above thr, grow **L/R only** from there (same cap;
 * no top/bottom); (3) otherwise retract L/R until a strip meets thr (or seed
 * floor); (4) pad **left/right only** by [retractClearFrac]×H. Height is the
 * first energy-grow only.
 */
import com.davidlang.vehicleexpensesautomated.ui.util.ContentExpandUtils.OrientedQuad
import com.davidlang.vehicleexpensesautomated.ui.util.ContentExpandUtils.OrientedBox

object ContentExpandUtilsQa {
    init {
        System.loadLibrary("native_ocr_qa")
    }

    internal external fun nativeStrokeWidthInSeed(
        matPtr: Long, l: Int, t: Int, r: Int, b: Int,
    ): IntArray?
}


    /** Which 1-D energy the vertical walk / XY-cut uses. */
    enum class VertEnergyKind {
        /** |∇I| (current P4-jump). */
        MAGNITUDE,
        /** |∂I/∂x| only — stems; ignores horizontal bezels / glare sheets. */
        GX,
        /** Document XY-cut: isolate the gx-profile peak that owns the seed. */
        XYCUT_GX,
        /** 16-bin row-gray hist χ² vs seed (AABB only). Jump still uses MAGNITUDE. */
        CHI2,
    }


    data class VertEnergySample(
        val dy: Int,
        val energy: Double,
        val ratio: Double,
        val width: Int,
        val count: Double = 0.0,
    )


    /**
     * Additive count-valley pullback, then a one-direction pad.
     *
     * Valley never retracts into the seed. After the valley:
     * - pulled tip: step back toward the energy box by [COUNT_CLEAR_FRAC]×seedH
     *   (not past energy);
     * - energy-stop tip whose run-count is still ≥ thr: grow that tip only by
     *   [COUNT_GROW_FRAC]×seedH. Cap-stops do not grow. Grow is skipped when
     *   the energy box is already taller than [COUNT_GROW_MAX_H_FRAC]×seedH
     *   so the 48 px rec crop does not shrink the digits.
     *
     * AABB: [axis] `image_y`. Oriented: [axis] `v`; [pulledTop] is the −v tip.
     */
    data class CountPullInfo(
        val pulledTop: Boolean,
        val pulledBot: Boolean,
        val cSeed: Double,
        val countThr: Double,
        val gxThr: Double,
        val tBefore: Int,
        val bBefore: Int,
        val tAfter: Int,
        val bAfter: Int,
        /** Origin of [counts]: image y (AABB) or rounded v from seed center (oriented). */
        val y0: Int = 0,
        /** Smoothed run-count per step at seed width (includes optional look-ahead). */
        val counts: DoubleArray = DoubleArray(0),
        /** `image_y` (AABB Sobel-x rows) or `v` (oriented |∇I·û| strips). */
        val axis: String = "image_y",
        val vNegBefore: Double = 0.0,
        val vPosBefore: Double = 0.0,
        val vNegAfter: Double = 0.0,
        val vPosAfter: Double = 0.0,
        /** True if this tip grew past the energy box (energy-stop + still in digits). */
        val grewTop: Boolean = false,
        val grewBot: Boolean = false,
        /** Signed step after the valley (image y or v index): negative = toward −y/−v. */
        val padTop: Int = 0,
        val padBot: Int = 0,
    ) {
        val pulled: Boolean get() = pulledTop || pulledBot
    }


    /**
     * Deskewed gray + Sobel-magnitude crop used by expand, for offline
     * iteration (means, L/C/R, p20, morph, projection, …) without a new deploy.
     */
    data class EnergyPixelRoi(
        val l: Int,
        val t: Int,
        val w: Int,
        val h: Int,
        val look: Int,
        val hPad: Int,
        val sobelScale: Float,
        val grayU8Zlib: ByteArray,
        val sobelU16leZlib: ByteArray,
    )


    /** Per-seed vertical grow trace (P4-jump / P4-rot, every photo). */
    data class VertEnergyTrace(
        val seed: Rect,
        val base: Double,
        val thr: Double,
        val energyRatio: Float,
        val up: List<VertEnergySample>,
        val down: List<VertEnergySample>,
        val inside: List<VertEnergySample>,
        val scanUp: List<VertEnergySample>,
        val scanDown: List<VertEnergySample>,
        val afterUp: List<VertEnergySample>,
        val afterDown: List<VertEnergySample>,
        val lookAhead: Int,
        val rawRoi: EnergyPixelRoi?,
        val stopUp: String,
        val stopDown: String,
        val stopEnergyUp: Double,
        val stopEnergyDown: Double,
        val final: Rect,
        val countPull: CountPullInfo? = null,
        val finalCount: Rect? = null,
    )


    data class AabbExpand(
        val rect: Rect,
        val hitVertCap: Boolean,
        val energyTrace: VertEnergyTrace? = null,
        val rectCount: Rect = rect,
        val countPull: CountPullInfo? = null,
        val tele: Seg7Telemetry? = null,
        val sweep: InkSweep? = null,
    )

    data class OrientedExpand(
        val quad: OrientedQuad,
        val hitVertCap: Boolean,
        val energyTrace: VertEnergyTrace? = null,
        val countQuad: OrientedQuad = quad,
        val countPull: CountPullInfo? = null,
        val sweep: InkSweep? = null,
    )


    data class ExpandOptions(
        val maxFrac: Float = 1.0f,
        val enableJump: Boolean = false,
        /** Horizontal jump as fraction of current box height after first grow. */
        val jumpFrac: Float = 0.40f,
        /**
         * After jump-retract lands on the ink edge, pad **left/right only** by this ×
         * current box height so OCR does not clip the outer digit (default 0.30).
         * Never applied to top/bottom.
         */
        val retractClearFrac: Float = 0.30f,
        /** INTERIOR_ENERGY only. */
        val energyRatio: Float = 0.45f,
        val vertEnergy: VertEnergyKind = VertEnergyKind.MAGNITUDE,
        /**
         * CHI2 vertical stop: stop when row χ² > this × seed-median χ².
         * Unused on MAGNITUDE / GX / XYCUT. Jump still uses [energyRatio].
         */
        val chi2K: Float = 3.5f,
        /** First grow is top/bottom only (do not widen while walking vertically). */
        val freezeHorzDuringVert: Boolean = false,
        /**
         * After the vertical stop, pad each tip by this × **seed** height.
         * One fixed scale — not a G-style vert list.
         */
        val vertPadFrac: Float = 0.0f,
        /** Mask modes: min ink density in strip. */
        val minInkFrac: Float = 0.02f,
        /** EDGE_RING only. */
        val minEdgeRatio: Float = 0.35f,
        /**
         * Unused on the pump AABB path: jump is horizontal only. Kept so existing
         * [ExpandOptions] call sites still compile.
         */
        val jumpVertPadPx: Int = 0,
        /** When true, [AabbExpand.energyTrace] records per-px vertical strip energy. */
        val recordVertEnergy: Boolean = false,
        /** 0 expand from seed edge, 2 edge-retract 1px. */
        val boundStrategy: Int = 0,
    )



    /**
     * Seed-local 7-seg stroke width. Otsu on the red ROI only (default dark ink);
     * flip to bright if dark is not the minority ([SEG7_INK_FLIP_FRAC]).
     * Drop CCs wider than 11× first-pass `s` (glare sheets) then odo H-path:
     * horiz runs, discard exact-span, peak k≥4 capped max(35, 0.5×seedH) = vSW = `s`.
     * Fallback [SEG7_FALLBACK_H_FRAC]×seedH if peak is the floor 4 or ink_frac ≳ 0.45.
     */
    data class StrokeWidthInSeed(
        val sPx: Int,
        val vSW: Int,
        val hSW: Int,
        val inkFrac: Float,
        val darkInk: Boolean,
        val usedFallback: Boolean,
        val droppedGlare: Int,
        val otsuThr: Int,
        val seed: Rect,
        /** Fraction of non-full-width horiz runs whose length is in 0.7–1.3×vSW. */
        val strokeShare: Float = 0f,
        /** Longest non-full-width horiz run / seedW. */
        val maxRunOverW: Float = 0f,
        /** JSON-only. True iff vSW>4, hSW>4, seedH ≥ 6×vSW, |Δ|/max(vSW,hSW) ≤ 0.25. Never a reject. */
        val vhAgree: Boolean = false,
    )


    data class InkSweep(
        val thr: Float,
        val sPx: Float,
        val minRun: Int,
        val energyRatio: Float,
        val vOrigin: Int,
        val hOrigin: Int,
        val v0: Int,
        val v1: Int,
        val h0: Int,
        val h1: Int,
        val vScores: IntArray,
        val hScores: IntArray,
        val walkT: Int = -1,
        val walkB: Int = -1,
        val jumpL: Int = -1,
        val jumpR: Int = -1,
        val threshJpeg: ByteArray? = null,
    ) {
        fun withOfficial(r: Rect): InkSweep {
            val vs = vScores.size
            val hs = hScores.size
            return copy(
                walkT = (r.top - vOrigin).coerceIn(0, vs),
                walkB = (r.bottom - vOrigin).coerceIn(0, vs),
                jumpL = (r.left - hOrigin).coerceIn(0, hs),
                jumpR = (r.right - hOrigin).coerceIn(0, hs),
            )
        }

        fun withOfficialUv(ov0: Float, ov1: Float, ou0: Float, ou1: Float): InkSweep {
            val vs = vScores.size
            val hs = hScores.size
            return copy(
                walkT = (ov0.roundToInt() - vOrigin).coerceIn(0, vs),
                walkB = (ov1.roundToInt() - vOrigin).coerceIn(0, vs),
                jumpL = (ou0.roundToInt() - hOrigin).coerceIn(0, hs),
                jumpR = (ou1.roundToInt() - hOrigin).coerceIn(0, hs),
            )
        }

        fun withOfficialQuad(seed: OrientedQuad, official: OrientedQuad): InkSweep {
            val sb = OrientedBox.fromQuad(seed) ?: return this
            val ext = sb.withPts(official.pts)
            return withOfficialUv(ext.v0, ext.v1, ext.u0, ext.u1)
        }
    }


    data class Seg7Telemetry(
        val method: String,
        val yInk: Float,
        val yBg: Float,
        val dInk: Float,
        val meanChroma: Float,
        val uInkX: Float,
        val uInkY: Float,
        val otsuThr: Float,
        val sPx: Float,
        val deltaTop: Float,
        val deltaBot: Float,
        val deltaLeft: Float,
        val deltaRight: Float,
        val flagTop: String,
        val flagBot: String,
        val flagLeft: String,
        val flagRight: String,
        val gapJumpTop: Boolean = false,
        val gapJumpBot: Boolean = false,
        val landTop: Float = 0f,
        val landBot: Float = 0f,
        val farL: Float = 0f,
        val farR: Float = 0f,
        val nInkSeed: Float = 0f,
        val nInkBlue: Float = 0f,
        val nInkYellow: Float = 0f,
        val histH: IntArray,
        val histV: IntArray,
        val histGapH: IntArray = intArrayOf(),
        val histGapV: IntArray = intArrayOf(),
        val nLookBinSeed: Float = 0f,
        val nRecoveredSeed: Float = 0f,
        val fill: Float = 0f,
        val nRetry: Float = 0f,
        val retryWhy: Float = 0f,
        val nValley: Float = 0f,
        val nAttempts: Float = 0f,
        val nKeep: Float = 0f,
        val nPoison: Float = 0f,
        val firstThr: Float = 0f,
        val attempts: List<Seg7FillAttempt> = emptyList(),
        val deltaTop1: Float = 0f,
        val deltaBot1: Float = 0f,
        val flagTop1: String = "UNCHANGED",
        val flagBot1: String = "UNCHANGED",
        val gapJumpBot1: Boolean = false,
        val nDropTall: Float = 0f,
        val maxCcH: Float = 0f,
        val dispKind: String = "",
        val poisonHMul: Float = 0f,
        val poisonVMul: Float = 0f,
        val gapDriftP90S: Float = 0f,
    )


    data class Seg7FillAttempt(
        val kind: Float = 0f,
        val firstThr: Float = 0f,
        val thr: Float = 0f,
        val dark: Float = 0f,
        val flip: Float = 0f,
        val nLookBin: Float = 0f,
        val nRecovered: Float = 0f,
        val fill: Float = 0f,
        val nKeep: Float = 0f,
        val nValley: Float = 0f,
        val nPoison: Float = 0f,
        val sPx: Float = 0f,
        val acceptedSpx: Float = 0f,
        val inBand: Float = 0f,
        val kept: Float = 0f,
        val inkFracDark: Float = 0f,
        val inverted: Float = 0f,
        val cleanDark: Float = 0f,
        val cleanInkFrac: Float = 0f,
        val vSW: Float = 0f,
        val strokeShare: Float = 0f,
        val maxRunOverW: Float = 0f,
        val needVsw: Float = 0f,
        val needInkFrac: Float = 0f,
        val needShare: Float = 0f,
        val needMaxRun: Float = 0f,
        val histRaw: IntArray = IntArray(0),
        val histRawTail: Float = 0f,
        val valleys: IntArray = IntArray(0),
        val skipped: IntArray = IntArray(0),
        val stop: Float = 0f,
    )


    data class PoisonCc(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val noPeak: Boolean,
        val thr: Int,
        val nInk: Int,
    )


    data class PoisonDump(
        val bandTop: Boolean,
        val bandBot: Boolean,
        val bandH: Int,
        val ccs: List<PoisonCc>,
        val inkLo: Int = 0,
        val nextNonInk: Int = 0,
        val seedIndex: Int = 0,
        val nSeeds: Int = 0,
        val classChange: Boolean = false,
        val phase: String = "",
        val vspSkip: Map<String, String> = emptyMap(),
    )


    data class Seg7Expand(
        val rect: Rect,
        val stroke: StrokeWidthInSeed,
        val k: Float = ContentExpandUtils.SEG7_K,
        val j: Float = ContentExpandUtils.SEG7_J,
        val tele: Seg7Telemetry? = null,
        val sweep: InkSweep? = null,
        val poison: PoisonDump? = null,
        val rectPad: Rect = rect,
    )



    data class Seg7OrientedExpand(
        val quad: OrientedQuad,
        val stroke: StrokeWidthInSeed,
        val tele: Seg7Telemetry? = null,
        val sweep: InkSweep? = null,
        val poison: PoisonDump? = null,
        val quadPad: OrientedQuad = quad,
    )


    data class SeedInkThr(
        val t: Int,
        val sPx: Int,
        val jpeg: ByteArray,
        val kind: String,
        val tLo: Int,
        val tHi: Int,
        val seedW: Int,
        val seedH: Int,
        val skipTint: Boolean = false,
        val nBand: Int = 0,
        val score: Int = 0,
    )


    data class SeedInkProbe(
        val nThr: Int,
        val w: Int,
        val h: Int,
        val thrs: List<SeedInkThr>,
    )


        fun OrientedQuad.uvRasterSize(): Pair<Int, Int> {
            val box = ContentExpandUtils.OrientedBox.fromQuad(this) ?: return 1 to 1
            val w = kotlin.math.round(box.u1).toInt() - kotlin.math.round(box.u0).toInt()
            val h = kotlin.math.round(box.v1).toInt() - kotlin.math.round(box.v0).toInt()
            return w.coerceAtLeast(1) to h.coerceAtLeast(1)
        }


    fun ContentExpandUtils.strokeWidthInSeed(gray: Mat, seed: Rect): StrokeWidthInSeed {
        val imgW = gray.cols()
        val imgH = gray.rows()
        val s = if (gray.empty() || gray.type() != CvType.CV_8UC1) {
            seed
        } else {
            clip(seed, imgW, imgH)
        }
        val seedH = max(1, s.height())
        val seedW = max(1, s.width())
        val fallback = max(2, (SEG7_FALLBACK_H_FRAC * seedH).roundToInt())
        fun fail(thr: Int = 0, dark: Boolean = true, ink: Float = 0f, dropped: Int = 0) =
            StrokeWidthInSeed(
                sPx = fallback, vSW = SEG7_MIN_STROKE, hSW = SEG7_MIN_STROKE,
                inkFrac = ink, darkInk = dark, usedFallback = true,
                droppedGlare = dropped, otsuThr = thr, seed = s,
                strokeShare = 0f, maxRunOverW = 0f, vhAgree = false,
            )
        if (gray.empty() || gray.type() != CvType.CV_8UC1) return fail()
        if (seedH < 4 || seedW < 4) return fail()

        try {
            val nPix = seedW * seedH
            val packed = ContentExpandUtilsQa.nativeStrokeWidthInSeed(gray.nativeObj, s.left, s.top, s.right, s.bottom)
                ?: return fail()
            val hhLen = seedW + 1
            val vhLen = seedH + 1
            if (packed.size < 4 + hhLen + vhLen) return fail()
            val thr = packed[0]
            var nz = packed[1]
            val nNonSpan = packed[2]
            val maxRun = packed[3]
            val hhHist = IntArray(hhLen) { packed[4 + it] }
            val vhHist = IntArray(vhLen) { packed[4 + hhLen + it] }
            var inkFrac = if (nPix > 0) nz.toFloat() / nPix else 0f
            var darkInk = true
            if (inkFrac >= SEG7_INK_FLIP_FRAC) {
                darkInk = false
                nz = nPix - nz
                inkFrac = if (nPix > 0) nz.toFloat() / nPix else 0f
            }
            val maxV = max(35, (seedH * 0.50f).toInt())
            val maxH = max(20, (seedH * 0.40f).toInt())
            val vSW = peakCapped(hhHist, SEG7_MIN_STROKE, maxV)
            val hSW = peakCapped(vhHist, SEG7_MIN_STROKE, maxH)
            val dropped = 0
            val lo = max(1, (0.7f * vSW).roundToInt())
            val hi = max(lo, (1.3f * vSW).roundToInt())
            var band = 0
            val hiClamp = min(hi, hhHist.size - 1)
            for (k in lo..hiClamp) band += hhHist[k]
            val strokeShare = if (nNonSpan > 0) band.toFloat() / nNonSpan else 0f
            val maxRunOverW = maxRun.toFloat() / seedW
            val needFallback = vSW <= SEG7_MIN_STROKE ||
                inkFrac >= SEG7_INK_FLIP_FRAC
            val sPx = if (needFallback) fallback else vSW
            val vhAgree = vSW > SEG7_MIN_STROKE &&
                hSW > SEG7_MIN_STROKE &&
                seedH >= 6 * vSW &&
                abs(vSW - hSW).toFloat() <= 0.25f * max(vSW, hSW)
            return StrokeWidthInSeed(
                sPx = sPx,
                vSW = vSW,
                hSW = hSW,
                inkFrac = inkFrac,
                darkInk = darkInk,
                usedFallback = needFallback,
                droppedGlare = dropped,
                otsuThr = thr,
                seed = s,
                strokeShare = strokeShare,
                maxRunOverW = maxRunOverW,
                vhAgree = vhAgree,
            )
        } catch (_: Throwable) {
            return fail()
        }
    }


    fun ContentExpandUtils.inkSweepBuf(n: Int, imgW: Int, imgH: Int): ShortArray {
        val axis = min(4096, max(imgW.coerceAtLeast(1), imgH.coerceAtLeast(1)))
        val span = axis * 2
        val per = 12 + span + 256
        return ShortArray((1 + n.coerceAtLeast(0) * per).coerceAtLeast(1))
    }



    fun ContentExpandUtils.parsePoisonStats(a: IntArray?, n: Int): List<PoisonDump?> {
        val out = MutableList<PoisonDump?>(n) { null }
        if (a == null || a.isEmpty() || n <= 0) return out
        var p = 0
        val nBox = a[p++]
        for (i in 0 until nBox) {
            if (p + 4 > a.size) break
            val bandTop = a[p++] != 0
            val bandBot = a[p++] != 0
            val bandH = a[p++]
            val nCc = a[p++]
            val ccs = ArrayList<PoisonCc>(nCc.coerceAtLeast(0))
            var ok = true
            for (j in 0 until nCc) {
                if (p + 7 > a.size) {
                    ok = false
                    break
                }
                ccs.add(
                    PoisonCc(
                        a[p++], a[p++], a[p++], a[p++],
                        a[p++] != 0, a[p++], a[p++],
                    ),
                )
            }
            if (!ok) break
            var inkLo = 0
            var nextNon = 0
            var seedIndex = 0
            var nSeeds = 0
            var classChange = false
            var phase = ""
            val vis = ArrayList<PoisonCc>(ccs.size)
            val vspSkip = linkedMapOf<String, String>()
            for (cc in ccs) {
                if (cc.x == -1) {
                    inkLo = cc.y
                    nextNon = cc.w
                    seedIndex = cc.h
                    nSeeds = if (cc.noPeak) 1 else 0
                    classChange = cc.thr != 0
                    phase = when (cc.nInk) {
                        1 -> "ink"
                        2 -> "non-ink"
                        3 -> "plus-ROI"
                        4 -> "poison"
                        5 -> "walk"
                        6 -> "scratch"
                        else -> ""
                    }
                } else if (cc.x == -2) {
                    val pck = cc.y
                    fun slot(shift: Int, a: String, b: String): String? = when ((pck shr shift) and 0xf) {
                        1 -> a
                        2 -> b
                        else -> null
                    }
                    slot(0, "skip_keep_raw_fallback", "skip_fallback_raw_keep")?.let { vspSkip["strokeNeedFb"] = it }
                    slot(4, "skip_7seg_raw_not", "skip_not_raw_7seg")?.let { vspSkip["dispKind"] = it }
                    slot(8, "skip_yes_raw_no", "skip_no_raw_yes")?.let { vspSkip["hasBarT"] = it }
                    slot(12, "skip_yes_raw_no", "skip_no_raw_yes")?.let { vspSkip["hasBarB"] = it }
                    slot(16, "skip_ink_raw_miss", "skip_miss_raw_ink")?.let { vspSkip["jumpL"] = it }
                    slot(20, "skip_ink_raw_miss", "skip_miss_raw_ink")?.let { vspSkip["jumpR"] = it }
                } else {
                    vis.add(cc)
                }
            }
            if (i < n) {
                out[i] = PoisonDump(
                    bandTop, bandBot, bandH, vis,
                    inkLo, nextNon, seedIndex, nSeeds, classChange, phase, vspSkip,
                )
            }
        }
        return out
    }


    fun ContentExpandUtils.parseInkSweeps(a: ShortArray?, n: Int): List<InkSweep?> {
        val out = MutableList<InkSweep?>(n) { null }
        if (a == null || a.isEmpty() || n <= 0) return out
        var p = 0
        val nBox = a[p++].toInt()
        for (i in 0 until nBox) {
            if (p + 12 > a.size) break
            val thr = a[p++].toInt() / 1000f
            val sPx = a[p++].toFloat()
            val minRun = a[p++].toInt()
            val energyRatio = a[p++].toInt() / 1000f
            val vOrigin = a[p++].toInt()
            val hOrigin = a[p++].toInt()
            val v0 = a[p++].toInt()
            val v1 = a[p++].toInt()
            val nV = a[p++].toInt()
            val h0 = a[p++].toInt()
            val h1 = a[p++].toInt()
            val nH = a[p++].toInt()
            if (nV < 0 || nH < 0 || p + nV + nH > a.size) break
            val vScores = IntArray(nV) { a[p++].toInt() }
            val hScores = IntArray(nH) { a[p++].toInt() }
            var jpeg: ByteArray? = null
            if (p < a.size) {
                val nJ = a[p++].toInt()
                if (nJ < 0 || p + nJ > a.size) break
                if (nJ > 0) {
                    jpeg = ByteArray(nJ) { a[p++].toByte() }
                }
            }
            if (i < n) {
                out[i] = InkSweep(
                    thr, sPx, minRun, energyRatio,
                    vOrigin, hOrigin, v0, v1, h0, h1,
                    vScores, hScores,
                    threshJpeg = jpeg,
                )
            }
        }
        return out
    }


    fun ContentExpandUtils.boundFlagName(v: Float): String = when (kotlin.math.round(v).toInt()) {
        1 -> "NORMAL_EXPAND"
        2 -> "NORMAL_RETRACT"
        3 -> "BLOCKED_RETRACT_LIMIT"
        4 -> "BLOCKED_GAP"
        else -> "UNCHANGED"
    }


    fun ContentExpandUtils.teleMethodName(v: Float): String = when (kotlin.math.round(v).toInt()) {
        1 -> "energy"
        4 -> "color_adaptive"
        else -> "gray"
    }


    fun ContentExpandUtils.dispKindName(v: Float): String = when (kotlin.math.round(v).toInt()) {
        0 -> "7seg"
        1 -> "not"
        else -> "unknown"
    }


    fun ContentExpandUtils.parseSeg7Tele(a: FloatArray, i: Int, overlap: ShortArray? = null): Seg7Telemetry? {
        val n = SEG7_TELE_N_PACK
        val energyBins = NativeImageUtils.SEG7_ENERGY_HIST_BINS
        val bins = NativeImageUtils.SEG7_HIST_BINS
        val o = i * n
        if (o + n > a.size) return null
        val ovOff = i * bins * 4
        val histH: IntArray
        val histV: IntArray
        val histGapH: IntArray
        val histGapV: IntArray
        if (overlap != null && ovOff + bins * 4 <= overlap.size) {
            fun u16(p: Int) = overlap[p].toInt() and 0xFFFF
            histH = IntArray(bins) { b -> u16(ovOff + b) }
            histV = IntArray(bins) { b -> u16(ovOff + bins + b) }
            histGapH = IntArray(bins) { b -> u16(ovOff + bins * 2 + b) }
            histGapV = IntArray(bins) { b -> u16(ovOff + bins * 3 + b) }
        } else {
            histH = IntArray(energyBins) { b -> a[o + 26 + b].toInt() }
            histV = IntArray(energyBins) { b -> a[o + 26 + energyBins + b].toInt() }
            histGapH = IntArray(0)
            histGapV = IntArray(0)
        }
        return Seg7Telemetry(
            method = teleMethodName(a[o]),
            yInk = a[o + 1],
            yBg = a[o + 2],
            dInk = a[o + 3],
            meanChroma = a[o + 4],
            uInkX = a[o + 5],
            uInkY = a[o + 6],
            otsuThr = a[o + 7],
            sPx = a[o + 8],
            deltaTop = a[o + 9],
            deltaBot = a[o + 10],
            deltaLeft = a[o + 11],
            deltaRight = a[o + 12],
            flagTop = boundFlagName(a[o + 13]),
            flagBot = boundFlagName(a[o + 14]),
            flagLeft = boundFlagName(a[o + 15]),
            flagRight = boundFlagName(a[o + 16]),
            gapJumpTop = a[o + 17] >= 0.5f,
            gapJumpBot = a[o + 18] >= 0.5f,
            landTop = a[o + 19],
            landBot = a[o + 20],
            farL = a[o + 21],
            farR = a[o + 22],
            nInkSeed = a[o + 23],
            nInkBlue = a[o + 24],
            nInkYellow = a[o + 25],
            histH = histH,
            histV = histV,
            histGapH = histGapH,
            histGapV = histGapV,
            nLookBinSeed = a[o + 26 + 2 * energyBins],
            nRecoveredSeed = a[o + 26 + 2 * energyBins + 1],
            fill = a[o + 26 + 2 * energyBins + 2],
            nRetry = a[o + 26 + 2 * energyBins + 3],
            retryWhy = a[o + 26 + 2 * energyBins + 4],
            nValley = a[o + 26 + 2 * energyBins + 5],
            nAttempts = a[o + 26 + 2 * energyBins + 6],
            nKeep = 0f,
            nPoison = 0f,
            firstThr = 0f,
            attempts = emptyList(),
        ).let { raw ->
            val histEnd = 26 + 2 * energyBins
            val nAtt = raw.nAttempts.roundToInt().coerceIn(0, NativeImageUtils.SEG7_ATTEMPT_MAX)
            val f = SEG7_ATTEMPT_F_PACK
            val atts = List(nAtt) { ai ->
                val b = o + histEnd + 7 + ai * f
                val nVal = a[b + 28].roundToInt().coerceIn(0, SEG7_VALLEY_PACK_N)
                val nSkip = a[b + 29].roundToInt().coerceIn(0, SEG7_VALLEY_PACK_N)
                val histOff = b + 30
                val valOff = histOff + SEG7_HIST_RAW_N
                val skipOff = valOff + SEG7_VALLEY_PACK_N
                Seg7FillAttempt(
                    kind = a[b],
                    firstThr = a[b + 1],
                    thr = a[b + 2],
                    dark = a[b + 3],
                    flip = a[b + 4],
                    nLookBin = a[b + 5],
                    nRecovered = a[b + 6],
                    fill = a[b + 7],
                    nKeep = a[b + 8],
                    nValley = a[b + 9],
                    nPoison = a[b + 10],
                    sPx = a[b + 11],
                    acceptedSpx = a[b + 12],
                    inBand = a[b + 13],
                    kept = a[b + 14],
                    inkFracDark = a[b + 15],
                    inverted = a[b + 16],
                    cleanDark = a[b + 17],
                    cleanInkFrac = a[b + 18],
                    vSW = a[b + 19],
                    strokeShare = a[b + 20],
                    maxRunOverW = a[b + 21],
                    needVsw = a[b + 22],
                    needInkFrac = a[b + 23],
                    needShare = a[b + 24],
                    needMaxRun = a[b + 25],
                    histRaw = IntArray(SEG7_HIST_RAW_N) { h -> a[histOff + h].roundToInt() },
                    histRawTail = a[b + 26],
                    valleys = IntArray(nVal) { v -> a[valOff + v].roundToInt() },
                    skipped = IntArray(nSkip) { s -> a[skipOff + s].roundToInt() },
                    stop = a[b + 27],
                )
            }
            val chosen = atts.firstOrNull {
                kotlin.math.abs(it.fill - raw.fill) < 1e-5f &&
                    it.nLookBin == raw.nLookBinSeed
            } ?: atts.firstOrNull()
            val tail = histEnd + 7 + NativeImageUtils.SEG7_ATTEMPT_MAX * f
            raw.copy(
                attempts = atts,
                nKeep = chosen?.nKeep ?: 0f,
                nPoison = chosen?.nPoison ?: 0f,
                firstThr = chosen?.firstThr ?: 0f,
                deltaTop1 = a[o + tail],
                deltaBot1 = a[o + tail + 1],
                flagTop1 = boundFlagName(a[o + tail + 2]),
                flagBot1 = boundFlagName(a[o + tail + 3]),
                gapJumpBot1 = a[o + tail + 4] >= 0.5f,
                nDropTall = a[o + tail + 5],
                maxCcH = a[o + tail + 6],
                dispKind = if (a[o + tail + 8] > 0f) dispKindName(a[o + tail + 7]) else "",
                poisonHMul = a[o + tail + 8],
                poisonVMul = a[o + tail + 9],
                gapDriftP90S = a[o + tail + 10],
            )
        }
    }


    private fun ContentExpandUtils.unpackAabb7seg(
        seeds: List<Rect>,
        r: IntArray?,
        tele: FloatArray,
        sweepBuf: ShortArray,
        poisonStats: IntArray?,
        imgW: Int,
        imgH: Int,
        k: Float,
        j: Float,
        overlap: ShortArray? = null,
    ): List<Seg7Expand> {
        if (r == null || r.size < seeds.size * 12) {
            return seeds.map { Seg7Expand(clip(it, imgW, imgH), strokeWidthInSeed(Mat(), it), k, j) }
        }
        val sweeps = parseInkSweeps(sweepBuf, seeds.size)
        val poisons = parsePoisonStats(poisonStats, seeds.size)
        return seeds.indices.map { i ->
            val o = i * 12
            val rect = clip(Rect(r[o], r[o + 1], r[o + 2], r[o + 3]), imgW, imgH)
            val rectPad = clip(Rect(r[o + 4], r[o + 5], r[o + 6], r[o + 7]), imgW, imgH)
            val sPx = r[o + 8]
            val vSW = r[o + 9]
            val hSW = r[o + 10]
            val fb = sPx < 1
            val seed = clip(seeds[i], imgW, imgH)
            Seg7Expand(
                rect,
                StrokeWidthInSeed(
                    sPx = max(1, sPx), vSW = vSW, hSW = hSW,
                    inkFrac = 0f, darkInk = true, usedFallback = fb,
                    droppedGlare = 0, otsuThr = 0, seed = seed,
                ),
                k, j,
                parseSeg7Tele(tele, i, overlap),
                sweeps.getOrNull(i),
                poisons.getOrNull(i),
                rectPad,
            )
        }
    }


    internal fun ContentExpandUtils.expandAabb7seg(
        gray: Mat,
        uv: Mat?,
        seeds: List<Rect>,
        scratch: Mat?,
        combine: Mat?,
        overlayY: Mat?,
        overlayUv: Mat?,
        poisonStats: IntArray?,
        native: (
            Mat, Mat?, IntArray, Mat?, FloatArray, ShortArray, Mat?, Mat?, Mat?, IntArray?, ShortArray?,
        ) -> IntArray?,
        k: Float = SEG7_K,
        j: Float = SEG7_J,
    ): List<Seg7Expand> {
        if (gray.empty() || gray.type() != CvType.CV_8UC1) {
            return seeds.map { Seg7Expand(it, strokeWidthInSeed(gray, it), k, j) }
        }
        if (seeds.isEmpty()) return emptyList()
        val imgW = gray.cols()
        val imgH = gray.rows()
        val packed = IntArray(seeds.size * 4)
        seeds.forEachIndexed { i, s0 ->
            val s = clip(s0, imgW, imgH)
            packed[i * 4] = s.left
            packed[i * 4 + 1] = s.top
            packed[i * 4 + 2] = s.right
            packed[i * 4 + 3] = s.bottom
        }
        val tele = FloatArray(seeds.size * SEG7_TELE_N_PACK)
        val sweepBuf = inkSweepBuf(seeds.size, imgW, imgH)
        val overlap = ShortArray(seeds.size * NativeImageUtils.SEG7_HIST_BINS * 4)
        val r = try {
            native(gray, uv, packed, scratch, tele, sweepBuf, combine, overlayY, overlayUv, poisonStats, overlap)
        } catch (_: Throwable) {
            null
        }
        return unpackAabb7seg(seeds, r, tele, sweepBuf, poisonStats, imgW, imgH, k, j, overlap)
    }


    fun ContentExpandUtils.expandColorAabbTight(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorAabbTightNative,
    )


    fun ContentExpandUtils.expandColorAabbRetract(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorAabbRetractNative,
    )


    fun ContentExpandUtils.expandColorAabbTightVsp(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorAabbTightVspNative,
    )


    fun ContentExpandUtils.expandColorAabbRetractVsp(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorAabbRetractVspNative,
    )


    fun ContentExpandUtils.expandGrayAabbExpand(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::grayAabbExpandNative,
    )


    fun ContentExpandUtils.expandColorAabbExpand(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorAabbExpandNative,
    )


    /**
     * 7seg walk along the red **normals** (`±v`) in source pixels. Frozen `u` span.
     * Does not pad or jump; caller uses [padOrientedByStrokes] then [jumpRetractOrientedU].
     */
    private fun ContentExpandUtils.expandOrient7seg(
        gray: Mat,
        uv: Mat?,
        seeds: List<OrientedQuad>,
        scratch: Mat?,
        combine: Mat?,
        overlayY: Mat?,
        overlayUv: Mat?,
        poisonStats: IntArray?,
        native: (
            Mat, Mat?, FloatArray, Mat?, FloatArray, ShortArray, Mat?, Mat?, Mat?, IntArray?, Mat?, ShortArray?,
        ) -> FloatArray?,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> {
        if (seeds.isEmpty()) return emptyList()
        val packed = FloatArray(seeds.size * 8)
        seeds.forEachIndexed { i, q ->
            val p = q.pts
            val o = i * 8
            for (k in 0 until 8) packed[o + k] = p[k]
        }
        val tele = FloatArray(seeds.size * SEG7_TELE_N_PACK)
        val imgW = gray.cols()
        val imgH = gray.rows()
        val sweepBuf = inkSweepBuf(seeds.size, imgW, imgH)
        val overlap = ShortArray(seeds.size * NativeImageUtils.SEG7_HIST_BINS * 4)
        val r = try {
            native(
                gray, uv, packed, scratch, tele, sweepBuf, combine, overlayY, overlayUv,
                poisonStats, tint, overlap,
            )
        } catch (_: Throwable) {
            null
        }
        if (r == null || r.size < seeds.size * 18) {
            return seeds.map { Seg7OrientedExpand(it, strokeWidthInSeed(gray, it.toAabb())) }
        }
        val sweeps = parseInkSweeps(sweepBuf, seeds.size)
        val poisons = parsePoisonStats(poisonStats, seeds.size)
        return seeds.indices.map { i ->
            val o = i * 18
            val pts = FloatArray(8) { k -> r[o + k] }
            val padPts = FloatArray(8) { k -> r[o + 8 + k] }
            val sPx = max(1, r[o + 16].roundToInt())
            Seg7OrientedExpand(
                OrientedQuad(pts),
                StrokeWidthInSeed(
                    sPx = sPx, vSW = SEG7_MIN_STROKE, hSW = SEG7_MIN_STROKE,
                    inkFrac = 0f, darkInk = true, usedFallback = false,
                    droppedGlare = 0, otsuThr = 0, seed = Rect(),
                ),
                parseSeg7Tele(tele, i, overlap),
                sweeps.getOrNull(i),
                poisons.getOrNull(i),
                OrientedQuad(padPts),
            )
        }
    }


    fun ContentExpandUtils.expandGrayOrientTight(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        { g, u, pk, sc, te, sw, comb, oy, ouv, ps, _, hist ->
            NativeImageUtils.grayOrientTightNative(g, u, pk, sc, te, sw, comb, oy, ouv, ps, hist)
        },
        tint,
    )

    fun ContentExpandUtils.expandGrayOrientRetract(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        { g, u, pk, sc, te, sw, comb, oy, ouv, ps, _, hist ->
            NativeImageUtils.grayOrientRetractNative(g, u, pk, sc, te, sw, comb, oy, ouv, ps, hist)
        },
        tint,
    )

    fun ContentExpandUtils.expandColorOrientTight(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorOrientTightNative, tint,
    )

    fun ContentExpandUtils.expandColorOrientRetract(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorOrientRetractNative, tint,
    )

    fun ContentExpandUtils.expandColorOrientTightVsp(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorOrientTightVspNative, tint,
    )

    fun ContentExpandUtils.expandColorOrientRetractVsp(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorOrientRetractVspNative, tint,
    )


    fun ContentExpandUtils.expandGrayOrientExpand(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        { g, u, pk, sc, te, sw, comb, oy, ouv, ps, _, hist ->
            NativeImageUtils.grayOrientExpandNative(g, u, pk, sc, te, sw, comb, oy, ouv, ps, hist)
        },
        tint,
    )

    fun ContentExpandUtils.expandColorOrientExpand(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorOrientExpandNative, tint,
    )


    private fun ContentExpandUtils.peakCapped(hist: IntArray, minK: Int, maxK: Int): Int {
        var bestK = minK
        var bestV = -1
        val hi = min(maxK, hist.size - 1)
        for (k in minK..hi) {
            if (hist[k] > bestV) {
                bestV = hist[k]
                bestK = k
            }
        }
        return if (bestV > 0) bestK else minK
    }


    /**
     * Many-seed AABB energy U8 look in A.s. Native fail → seed boxes.
     */
    internal fun ContentExpandUtils.expandEnergyAabbMany(
        gray: Mat,
        uv: Mat?,
        seeds: List<Rect>,
        native: (Mat, Mat?, IntArray, FloatArray?, ShortArray?, Mat?) -> IntArray?,
    ): List<AabbExpand> {
        if (gray.empty() || gray.type() != CvType.CV_8UC1) {
            return seeds.map { AabbExpand(it, false) }
        }
        val imgW = gray.cols()
        val imgH = gray.rows()
        if (seeds.isEmpty()) return emptyList()
        val packed = IntArray(seeds.size * 4)
        seeds.forEachIndexed { i, s ->
            val c = clip(s, imgW, imgH)
            packed[i * 4] = c.left
            packed[i * 4 + 1] = c.top
            packed[i * 4 + 2] = c.right
            packed[i * 4 + 3] = c.bottom
        }
        val tele = FloatArray(seeds.size * SEG7_TELE_N_PACK)
        val sweepBuf = inkSweepBuf(seeds.size, imgW, imgH)
        val r = try {
            native(
                gray, uv, packed, tele, sweepBuf,
                try { NativePaddleEngine.bufferSetA.s.mat } catch (_: Throwable) { null },
            )
        } catch (_: Throwable) {
            null
        } ?: return seeds.map { AabbExpand(clip(it, imgW, imgH), false) }
        if (r.size < seeds.size * 11) {
            return seeds.map { AabbExpand(clip(it, imgW, imgH), false) }
        }
        val sweeps = parseInkSweeps(sweepBuf, seeds.size)
        return seeds.indices.map { i ->
            val o = i * 11
            val rect = clip(Rect(r[o], r[o + 1], r[o + 2], r[o + 3]), imgW, imgH)
            val cr = clip(Rect(r[o + 4], r[o + 5], r[o + 6], r[o + 7]), imgW, imgH)
            AabbExpand(
                rect,
                r[o + 8] != 0,
                null,
                cr,
                CountPullInfo(
                    pulledTop = r[o + 9] != 0,
                    pulledBot = r[o + 10] != 0,
                    cSeed = 0.0,
                    countThr = 0.0,
                    gxThr = 0.0,
                    tBefore = rect.top,
                    bBefore = rect.bottom,
                    tAfter = cr.top,
                    bAfter = cr.bottom,
                ),
                parseSeg7Tele(tele, i),
                sweeps.getOrNull(i),
            )
        }
    }


    fun ContentExpandUtils.expandEnergyAabbExpand(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
    ): List<AabbExpand> = expandEnergyAabbMany(gray, uv, seeds, NativeImageUtils::energyAabbExpandNative)


        /** Project [pts] (n×2) into this seed frame; keep ux,uy,vx,vy. */
        internal fun OrientedBox.withPts(pts: FloatArray): OrientedBox {
            var nu0 = Float.POSITIVE_INFINITY
            var nu1 = Float.NEGATIVE_INFINITY
            var nv0 = Float.POSITIVE_INFINITY
            var nv1 = Float.NEGATIVE_INFINITY
            val n = min(4, pts.size / 2)
            for (i in 0 until n) {
                val (u, v) = proj(pts[i * 2], pts[i * 2 + 1])
                if (u < nu0) nu0 = u
                if (u > nu1) nu1 = u
                if (v < nv0) nv0 = v
                if (v > nv1) nv1 = v
            }
            return OrientedBox(cx, cy, ux, uy, vx, vy, nu0, nu1, nv0, nv1)
        }


        internal fun OrientedBox.cornerImage(i: Int): Pair<Float, Float> {
            val u = if (i == 0 || i == 3) u0 else u1
            val v = if (i == 0 || i == 1) v0 else v1
            return (cx + u * ux + v * vx) to (cy + u * uy + v * vy)
        }


        internal fun OrientedBox.includeImagePoint(px: Float, py: Float) {
            val (u, v) = proj(px, py)
            if (u < u0) u0 = u
            if (u > u1) u1 = u
            if (v < v0) v0 = v
            if (v > v1) v1 = v
        }


        internal fun OrientedBox.includeBox(other: OrientedBox) {
            for (i in 0 until 4) {
                val (px, py) = other.cornerImage(i)
                includeImagePoint(px, py)
            }
        }


        internal fun OrientedBox.localAabb(other: OrientedBox): FloatArray {
            var bu0 = Float.POSITIVE_INFINITY
            var bu1 = Float.NEGATIVE_INFINITY
            var bv0 = Float.POSITIVE_INFINITY
            var bv1 = Float.NEGATIVE_INFINITY
            for (i in 0 until 4) {
                val (px, py) = other.cornerImage(i)
                val (u, v) = proj(px, py)
                if (u < bu0) bu0 = u
                if (u > bu1) bu1 = u
                if (v < bv0) bv0 = v
                if (v > bv1) bv1 = v
            }
            return floatArrayOf(bu0, bu1, bv0, bv1)
        }


        internal fun OrientedBox.maxPoke(other: OrientedBox): Float {
            var m = 0f
            for (i in 0 until 4) {
                val (px, py) = other.cornerImage(i)
                val (u, v) = proj(px, py)
                if (u > u1) m = max(m, u - u1)
                if (u < u0) m = max(m, u0 - u)
                if (v > v1) m = max(m, v - v1)
                if (v < v0) m = max(m, v0 - v)
            }
            return m
        }


        internal fun OrientedBox.cornersInside(other: OrientedBox, eps: Float = 0.5f): Int {
            var n = 0
            for (i in 0 until 4) {
                val (px, py) = other.cornerImage(i)
                val (u, v) = proj(px, py)
                if (u >= u0 - eps && u <= u1 + eps && v >= v0 - eps && v <= v1 + eps) n++
            }
            return n
        }

