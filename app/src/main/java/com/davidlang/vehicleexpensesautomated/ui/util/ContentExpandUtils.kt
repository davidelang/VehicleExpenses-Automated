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
object ContentExpandUtils {

    enum class Mode {
        /** Dual-polarity adaptive ink mask + strip grow. */
        DUAL_SAUVOLA,
        /** Grow while strip gradient energy stays near seed interior (Set P / multi-scale). */
        INTERIOR_ENERGY,
        /** Grow while strip edge density stays near seed boundary. */
        EDGE_RING,
        /** Blue-style v=0.25 then dual residual (cap 0.5·h). */
        V025_THEN_DUAL,
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

    /** After a valley pull, give this × seedH back toward the energy tip. */
    const val COUNT_CLEAR_FRAC = 0.10f

    /** Energy-stop + tip run-count still ≥ thr: grow that tip by this × seedH. */
    const val COUNT_GROW_FRAC = 0.08f

    /**
     * Do not grow when the energy box is already this × seed height
     * (two LCD rows + gap; further height shrinks digits in the 48 px rec).
     */
    const val COUNT_GROW_MAX_H_FRAC = 2.4f

    /**
     * After vertical grow stops, keep sampling at least this many px past the
     * stop (or seedH if larger) so valleys / second peaks are visible.
     */
    const val VERT_ENERGY_LOOKAHEAD_MIN_PX = 120

    /**
     * Raw gray+Sobel patch: this × seedH past each tip, plus 0.25×seedW
     * left/right. Generous so offline stop tests do not miss the field edge.
     */
    const val VERT_ENERGY_RAW_LOOK_FRAC = 1.5f

    const val VERT_ENERGY_RAW_HPAD_FRAC = 0.25f

    /** stored_u16 = round(sobel * this), little-endian, zlib. */
    const val VERT_ENERGY_RAW_SCALE = 10f

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

    fun expand(
        gray: Mat,
        seed: Rect,
        mode: Mode,
        maxFrac: Float = 1.0f,
        enableJump: Boolean = false,
        jumpFrac: Float = 0.40f,
    ): Rect = expand(
        gray,
        seed,
        mode,
        ExpandOptions(maxFrac = maxFrac, enableJump = enableJump, jumpFrac = jumpFrac),
    )

    /**
     * Oriented seed: 4 corners in image space (x0,y0,x1,y1,x2,y2,x3,y3), any winding.
     * Expand happens in the box local frame (u along longer side, v across).
     */
    data class OrientedQuad(
        val pts: FloatArray, // length 8
    ) {
        init {
            require(pts.size >= 8) { "OrientedQuad needs 8 floats" }
        }

        fun copyPts(): FloatArray = pts.copyOf(8)

        fun uvRasterSize(): Pair<Int, Int> {
            val box = OrientedBox.fromQuad(this) ?: return 1 to 1
            val w = kotlin.math.round(box.u1).toInt() - kotlin.math.round(box.u0).toInt()
            val h = kotlin.math.round(box.v1).toInt() - kotlin.math.round(box.v0).toInt()
            return w.coerceAtLeast(1) to h.coerceAtLeast(1)
        }

        /**
         * Rec margin: expand u0/u1/v0/v1 by [padPx] source pixels. No per-corner
         * image clamp (warp uses BORDER_CONSTANT black).
         */
        fun padUv(padPx: Int): OrientedQuad {
            if (padPx <= 0) return this
            val box = OrientedBox.fromQuad(this) ?: return this
            val p = padPx.toFloat()
            return OrientedBox(
                box.cx, box.cy, box.ux, box.uy, box.vx, box.vy,
                box.u0 - p, box.u1 + p, box.v0 - p, box.v1 + p,
            ).toQuad()
        }

        /**
         * Expand each corner away from the centroid by [padPx] source pixels (for source-border rec).
         * Clamps to [0, imgW)×[0, imgH).
         */
        fun inflate(padPx: Int, imgW: Int, imgH: Int): OrientedQuad {
            if (padPx <= 0) return this
            var cx = 0f
            var cy = 0f
            for (i in 0 until 4) {
                cx += pts[i * 2]
                cy += pts[i * 2 + 1]
            }
            cx /= 4f
            cy /= 4f
            val out = FloatArray(8)
            for (i in 0 until 4) {
                val x = pts[i * 2]
                val y = pts[i * 2 + 1]
                val dx = x - cx
                val dy = y - cy
                val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1e-3f)
                out[i * 2] = (x + padPx * dx / len).coerceIn(0f, (imgW - 1).toFloat())
                out[i * 2 + 1] = (y + padPx * dy / len).coerceIn(0f, (imgH - 1).toFloat())
            }
            return OrientedQuad(out)
        }

        /**
         * Scale only the short axis (text height) about the centroid. Width along
         * the long side is unchanged — S must not widen the line.
         */
        fun scaleHeightAboutCentroid(s: Float): OrientedQuad {
            if (abs(s - 1f) < 1e-4f) return this
            val rr = minAreaFromQuad(this) ?: return this
            var cx = rr.center.x.toFloat()
            var cy = rr.center.y.toFloat()
            var bw = rr.size.width.toFloat().coerceAtLeast(2f)
            var bh = rr.size.height.toFloat().coerceAtLeast(2f)
            var ang = rr.angle.toFloat()
            if (bw < bh) {
                val tmp = bw
                bw = bh
                bh = tmp
                ang += 90f
            }
            bh = (bh * s).coerceAtLeast(2f)
            return orientedFromCenter(cx, cy, bw, bh, ang)
        }

        /** Uniform scale about the centroid. Does not clamp to the image. */
        fun scaleAboutCentroid(s: Float): OrientedQuad {
            if (abs(s - 1f) < 1e-4f) return this
            var cx = 0f
            var cy = 0f
            for (i in 0 until 4) {
                cx += pts[i * 2]
                cy += pts[i * 2 + 1]
            }
            cx /= 4f
            cy /= 4f
            val out = FloatArray(8)
            for (i in 0 until 4) {
                out[i * 2] = cx + s * (pts[i * 2] - cx)
                out[i * 2 + 1] = cy + s * (pts[i * 2 + 1] - cy)
            }
            return OrientedQuad(out)
        }

        fun toAabb(): Rect {
            val xs = floatArrayOf(pts[0], pts[2], pts[4], pts[6])
            val ys = floatArrayOf(pts[1], pts[3], pts[5], pts[7])
            return Rect(
                xs.min().toInt(),
                ys.min().toInt(),
                xs.max().toInt().coerceAtLeast(xs.min().toInt() + 1),
                ys.max().toInt().coerceAtLeast(ys.min().toInt() + 1),
            )
        }

        /** Short-axis text height (`vSpan` / warp `hSrc`), not AABB height. */
        fun shortAxisBh(): Float =
            OrientedBox.fromQuad(this)?.vSpan()?.coerceAtLeast(1f)
                ?: toAabb().height().coerceAtLeast(1).toFloat()

        /** Long-axis span (`uSpan`), not AABB width. */
        fun longAxisBw(): Float =
            OrientedBox.fromQuad(this)?.uSpan()?.coerceAtLeast(1f) ?: 0f

        /** u-axis angle after flatter-edge pick, folded to [-90, 90]. */
        fun uAngleDeg(): Float =
            OrientedBox.fromQuad(this)?.longAngleDeg() ?: 0f

        fun imageYAtV(v: Float): Int {
            val b = OrientedBox.fromQuad(this) ?: return v.roundToInt()
            val u = (b.u0 + b.u1) * 0.5f
            return (b.cy + u * b.uy + v * b.vy).roundToInt()
        }

        fun area(): Float {
            // shoelace
            var a = 0f
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                a += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1]
            }
            return abs(a) * 0.5f
        }
    }

    /**
     * P-style interior-energy expand that does **not** require axis-aligned boxes.
     * Grows ±u (text ends) and ±v (above/below) in the minAreaRect frame of [seed].
     */
    fun expandOrientedDiagnose(
        gray: Mat,
        seed: OrientedQuad,
        opts: ExpandOptions = ExpandOptions(),
        energyNative: ((Mat, FloatArray, ShortArray?, Mat?) -> FloatArray?)? = null,
    ): OrientedExpand {
        if (gray.empty() || gray.type() != CvType.CV_8UC1) return OrientedExpand(seed, false)
        val imgW = gray.cols()
        val imgH = gray.rows()
        if (!opts.recordVertEnergy) {
            val sweepBuf = inkSweepBuf(1, imgW, imgH)
            val nativeExp = try {
                if (energyNative != null) {
                    val many = energyNative(
                        gray, seed.pts, sweepBuf,
                        try { NativePaddleEngine.bufferSetA.s.mat } catch (_: Throwable) { null },
                    )
                    if (many != null && many.size >= 13) {
                        NativeImageUtils.OrientedExpandNative(
                            cx = many[0], cy = many[1], bw = many[2], bh = many[3],
                            angDeg = many[4],
                            stepsVNeg = many[5].toInt(), stepsVPos = many[6].toInt(),
                            padV = many[7].toInt(),
                            hitVertCap = many[8] >= 0.5f,
                            stopEnergyUp = many[9], stopEnergyDown = many[10],
                            base = many[11], thr = many[12],
                        )
                    } else {
                        null
                    }
                } else {
                    val many = NativeImageUtils.energyOrientExpandNative(
                        gray, seed.pts, sweepBuf,
                        try { NativePaddleEngine.bufferSetA.s.mat } catch (_: Throwable) { null },
                    )
                    if (many != null && many.size >= 13) {
                        NativeImageUtils.OrientedExpandNative(
                            cx = many[0], cy = many[1], bw = many[2], bh = many[3],
                            angDeg = many[4],
                            stepsVNeg = many[5].toInt(), stepsVPos = many[6].toInt(),
                            padV = many[7].toInt(),
                            hitVertCap = many[8] >= 0.5f,
                            stopEnergyUp = many[9], stopEnergyDown = many[10],
                            base = many[11], thr = many[12],
                        )
                    } else {
                        null
                    }
                }
            } catch (_: Throwable) {
                null
            }
            if (nativeExp != null) {
                val finalQuad = orientedFromCenter(
                    nativeExp.cx, nativeExp.cy, nativeExp.bw, nativeExp.bh, nativeExp.angDeg,
                )
                val seedRr = minAreaFromQuad(seed)
                var seedBh = nativeExp.bh
                if (seedRr != null) {
                    var bw = seedRr.size.width.toFloat()
                    var bh = seedRr.size.height.toFloat()
                    if (bw < bh) {
                        val tmp = bw; bw = bh; bh = tmp
                    }
                    seedBh = bh.coerceAtLeast(2f)
                }
                val cap = max(1, (opts.maxFrac * seedBh).roundToInt())
                val stopUp = if (nativeExp.stepsVNeg >= cap) "cap" else "energy"
                val stopDown = if (nativeExp.stepsVPos >= cap) "cap" else "energy"
                val nativeCnt = try {
                    NativeImageUtils.countPullbackOrientedNative(
                        gray, seed.pts, finalQuad.pts, 0,
                        stopUp == "energy", stopDown == "energy",
                        COUNT_CLEAR_FRAC, COUNT_GROW_FRAC, COUNT_GROW_MAX_H_FRAC,
                    )
                } catch (_: Throwable) {
                    null
                }
                val (countQuad, countInfo) = if (nativeCnt != null) {
                    val newV = ((nativeCnt.vNegAfter + nativeCnt.vPosAfter) * 0.5).toFloat()
                    val newBh = (nativeCnt.vPosAfter - nativeCnt.vNegAfter).toFloat().coerceAtLeast(2f)
                    val rad = Math.toRadians(nativeCnt.angDeg.toDouble())
                    val ux = cos(rad).toFloat()
                    val uy = sin(rad).toFloat()
                    val vx = -uy
                    val vy = ux
                    val newCx = nativeCnt.seedCx + nativeCnt.existCu * ux + newV * vx
                    val newCy = nativeCnt.seedCy + nativeCnt.existCu * uy + newV * vy
                    val cq = orientedFromCenter(newCx, newCy, nativeCnt.existBw, newBh, nativeCnt.angDeg)
                    val existAabb = finalQuad.toAabb()
                    val countAabb = cq.toAabb()
                    cq to CountPullInfo(
                        pulledTop = nativeCnt.pulledTop,
                        pulledBot = nativeCnt.pulledBot,
                        cSeed = nativeCnt.cSeed,
                        countThr = nativeCnt.countThr,
                        gxThr = nativeCnt.gxThr,
                        tBefore = existAabb.top,
                        bBefore = existAabb.bottom,
                        tAfter = countAabb.top,
                        bAfter = countAabb.bottom,
                        y0 = nativeCnt.y0,
                        counts = nativeCnt.counts,
                        axis = "v",
                        vNegBefore = nativeCnt.vNegBefore,
                        vPosBefore = nativeCnt.vPosBefore,
                        vNegAfter = nativeCnt.vNegAfter,
                        vPosAfter = nativeCnt.vPosAfter,
                        grewTop = nativeCnt.grewTop,
                        grewBot = nativeCnt.grewBot,
                        padTop = nativeCnt.padTop,
                        padBot = nativeCnt.padBot,
                    )
                } else {
                    val existAabb = finalQuad.toAabb()
                    finalQuad to CountPullInfo(
                        pulledTop = false, pulledBot = false,
                        cSeed = 0.0, countThr = 0.0, gxThr = 0.0,
                        tBefore = existAabb.top, bBefore = existAabb.bottom,
                        tAfter = existAabb.top, bAfter = existAabb.bottom,
                        axis = "v",
                    )
                }
                return OrientedExpand(
                    finalQuad, nativeExp.hitVertCap, null, countQuad, countInfo,
                    parseInkSweeps(sweepBuf, 1).getOrNull(0),
                )
            }
        }
        return OrientedExpand(seed, false)
    }

    fun expandOriented(
        gray: Mat,
        seed: OrientedQuad,
        opts: ExpandOptions = ExpandOptions(),
    ): OrientedQuad = expandOrientedDiagnose(gray, seed, opts).quad

    /**
     * G-style calculated pad on an oriented seed: height × (1+2v), each long-side
     * end padded by [horiz] × new height. Same geometry as AABB blues.
     */
    fun calculatedOriented(seed: OrientedQuad, v: Float, horiz: Float = 0.5f): OrientedQuad {
        val rr = minAreaFromQuad(seed) ?: return seed
        var cx = rr.center.x.toFloat()
        var cy = rr.center.y.toFloat()
        var bw = rr.size.width.toFloat().coerceAtLeast(2f)
        var bh = rr.size.height.toFloat().coerceAtLeast(2f)
        var ang = rr.angle.toFloat()
        if (bw < bh) {
            val tmp = bw
            bw = bh
            bh = tmp
            ang += 90f
        }
        val newH = bh * (1f + 2f * v)
        val pad = horiz * newH
        return orientedFromCenter(cx, cy, bw + 2f * pad, newH, ang)
    }

    fun orientedFromAabb(r: Rect): OrientedQuad {
        val l = r.left.toFloat()
        val t = r.top.toFloat()
        val rr = r.right.toFloat()
        val b = r.bottom.toFloat()
        return OrientedQuad(floatArrayOf(l, t, rr, t, rr, b, l, b))
    }

    fun orientedFromPoints8(p: FloatArray): OrientedQuad = OrientedQuad(p.copyOf(8))

    /**
     * Merge nested / slightly-poking oriented reds **without** converting them
     * to an AABB. The keeper keeps its tilt; any side that must move to cover
     * a smaller box translates along its own normal (not both opposite sides,
     * not an upright union).
     *
     * Same intent as [PumpCostVolUtils.pruneRectsToTopN] (contain, 40px poke,
     * similar-overlap, top-N) but in the keeper's u/v frame.
     */
    @Suppress("UNUSED_PARAMETER")
    fun pruneOrientedQuads(
        quads: List<OrientedQuad>,
        maxCount: Int,
        imgH: Int,
        pokePx: Float = 40f,
    ): List<OrientedQuad> {
        val boxes = quads.mapNotNull { OrientedBox.fromQuad(it) }.toMutableList()
        if (boxes.isEmpty()) return emptyList()
        mergeOrientedSimilarAndPoke(boxes, pokePx)
        if (boxes.size > maxCount) {
            boxes.sortByDescending { it.area() }
            while (boxes.size > maxCount) boxes.removeAt(boxes.lastIndex)
        }
        return boxes.map { it.toQuad() }
    }

    /**
     * Warp [quad] into [dest] at **native** u/v size (no dest-height floor).
     * Pivot BL, flatten BL→BR to +x. INTER_CUBIC, BORDER_CONSTANT black.
     * If [dest] is already sized, that size is used. Caller INTER_AREA downscales to rec 48.
     */
    fun warpQuadToHorizontalStrip(
        gray: Mat,
        quad: OrientedQuad,
        dest: Mat,
        targetH: Int = 0,
        maxW: Int = NativePaddleEngine.REC_CANVAS_W,
    ): Boolean {
        if (gray.empty()) return false
        val order = orderQuadForWarp(quad) ?: return false
        val wSrc = hypot(
            (order[2] - order[0]).toDouble(),
            (order[3] - order[1]).toDouble(),
        ).toFloat().coerceAtLeast(1f)
        val hSrc = hypot(
            (order[6] - order[0]).toDouble(),
            (order[7] - order[1]).toDouble(),
        ).toFloat().coerceAtLeast(1f)
        val nativeW = wSrc.roundToInt().coerceAtLeast(1).coerceAtMost(maxW)
        val nativeH = hSrc.roundToInt().coerceAtLeast(1)
        val outW: Int
        val outH: Int
        if (!dest.empty() && dest.cols() >= 1 && dest.rows() >= 1 && targetH <= 0) {
            outW = dest.cols()
            outH = dest.rows()
        } else {
            outW = nativeW
            outH = nativeH
        }
        val src = MatOfPoint2f(
            Point(order[0].toDouble(), order[1].toDouble()),
            Point(order[2].toDouble(), order[3].toDouble()),
            Point(order[4].toDouble(), order[5].toDouble()),
            Point(order[6].toDouble(), order[7].toDouble()),
        )
        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((outW - 1).toDouble().coerceAtLeast(0.0), 0.0),
            Point((outW - 1).toDouble().coerceAtLeast(0.0), (outH - 1).toDouble().coerceAtLeast(0.0)),
            Point(0.0, (outH - 1).toDouble().coerceAtLeast(0.0)),
        )
        val m = Imgproc.getPerspectiveTransform(src, dst)
        if (!dest.empty()) dest.setTo(Scalar(0.0))
        Imgproc.warpPerspective(
            gray, dest, m, Size(outW.toDouble(), outH.toDouble()),
            Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT, Scalar(0.0),
        )
        m.release(); src.release(); dst.release()
        return !dest.empty() && dest.cols() >= 1 && dest.rows() >= 1
    }

    /** INTER_AREA [src] into [dest] sized [targetW]×[targetH] (rec 48). */
    fun downscaleStripArea(src: Mat, dest: Mat, targetW: Int, targetH: Int): Boolean {
        if (src.empty() || targetW < 1 || targetH < 1) return false
        Imgproc.resize(
            src, dest, Size(targetW.toDouble(), targetH.toDouble()),
            0.0, 0.0, Imgproc.INTER_AREA,
        )
        return !dest.empty()
    }

    /**
     * Order corners TL,TR,BR,BL. BL = two smallest-x, then largest y (left short side).
     * BR = cycle neighbor with larger x (long baseline to the right). Other neighbor is TL.
     * Dest maps TL→(0,0) so BL→BR flattens to +x. Not Y-first (that picks the right end on a droop).
     */
    fun orderQuadForWarp(quad: OrientedQuad): FloatArray? {
        val p = quad.pts
        if (p.size < 8) return null
        data class C(val i: Int, val x: Float, val y: Float)
        val c = Array(4) { i -> C(i, p[i * 2], p[i * 2 + 1]) }
        val twoLeft = c.sortedBy { it.x }.take(2)
        val bl = if (twoLeft[0].y >= twoLeft[1].y) twoLeft[0] else twoLeft[1]
        val n0 = c[(bl.i + 3) % 4]
        val n1 = c[(bl.i + 1) % 4]
        val br = if (n0.x >= n1.x) n0 else n1
        val tl = if (br.i == n0.i) n1 else n0
        val tr = c.first { it.i != bl.i && it.i != br.i && it.i != tl.i }
        return floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
    }

    private fun minAreaFromQuad(q: OrientedQuad): RotatedRect? {
        val mat = MatOfPoint2f(
            Point(q.pts[0].toDouble(), q.pts[1].toDouble()),
            Point(q.pts[2].toDouble(), q.pts[3].toDouble()),
            Point(q.pts[4].toDouble(), q.pts[5].toDouble()),
            Point(q.pts[6].toDouble(), q.pts[7].toDouble()),
        )
        val rr = try {
            Imgproc.minAreaRect(mat)
        } catch (_: Throwable) {
            null
        }
        mat.release()
        return rr
    }

    private fun orientedFromCenter(
        cx: Float, cy: Float, bw: Float, bh: Float, angDeg: Float,
    ): OrientedQuad {
        val rad = Math.toRadians(angDeg.toDouble())
        val ux = cos(rad).toFloat()
        val uy = sin(rad).toFloat()
        val vx = -uy
        val vy = ux
        val hu = bw * 0.5f
        val hv = bh * 0.5f
        // corners: ±u ±v
        fun corner(su: Float, sv: Float) = floatArrayOf(
            cx + su * hu * ux + sv * hv * vx,
            cy + su * hu * uy + sv * hv * vy,
        )
        val c00 = corner(-1f, -1f)
        val c10 = corner(+1f, -1f)
        val c11 = corner(+1f, +1f)
        val c01 = corner(-1f, +1f)
        return OrientedQuad(
            floatArrayOf(
                c00[0], c00[1], c10[0], c10[1], c11[0], c11[1], c01[0], c01[1],
            ),
        )
    }


    /** Uniform scale of an AABB about its center, clamped to the image. */
    fun scaleAabbAboutCenter(r: Rect, s: Float, imgW: Int, imgH: Int): Rect {
        if (abs(s - 1f) < 1e-4f) return r
        val cx = (r.left + r.right) * 0.5f
        val cy = (r.top + r.bottom) * 0.5f
        val nl = (cx + s * (r.left - cx)).roundToInt().coerceIn(0, (imgW - 1).coerceAtLeast(0))
        val nr = (cx + s * (r.right - cx)).roundToInt().coerceIn(nl + 1, imgW.coerceAtLeast(nl + 1))
        val nt = (cy + s * (r.top - cy)).roundToInt().coerceIn(0, (imgH - 1).coerceAtLeast(0))
        val nb = (cy + s * (r.bottom - cy)).roundToInt().coerceIn(nt + 1, imgH.coerceAtLeast(nt + 1))
        return Rect(nl, nt, nr, nb)
    }

    /** Scale AABB height about its vertical center. Left/right unchanged. */
    fun scaleAabbHeightAboutCenter(r: Rect, s: Float, imgW: Int, imgH: Int): Rect {
        if (abs(s - 1f) < 1e-4f) return r
        val cy = (r.top + r.bottom) * 0.5f
        val nt = (cy + s * (r.top - cy)).roundToInt().coerceIn(0, (imgH - 1).coerceAtLeast(0))
        val nb = (cy + s * (r.bottom - cy)).roundToInt().coerceIn(nt + 1, imgH.coerceAtLeast(nt + 1))
        val l = r.left.coerceIn(0, (imgW - 1).coerceAtLeast(0))
        val rr = r.right.coerceIn(l + 1, imgW.coerceAtLeast(l + 1))
        return Rect(l, nt, rr, nb)
    }

    /** G-style calculated AABB: height × (1+2v), each side horiz × newH. */
    fun calculatedAabb(seed: Rect, v: Float, horiz: Float, imgW: Int, imgH: Int): Rect =
        ratioExpand(seed, v, horiz, imgW, imgH)

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

    const val SEG7_INK_FLIP_FRAC = 0.45f
    const val SEG7_GLARE_WIDTH_MULT = 11
    const val SEG7_MIN_STROKE = 4
    const val SEG7_HIST_RAW_N = 256
    const val SEG7_VALLEY_PACK_N = 64
    const val SEG7_ATTEMPT_F_PACK = 30 + SEG7_HIST_RAW_N + SEG7_VALLEY_PACK_N + SEG7_VALLEY_PACK_N
    const val SEG7_TELE_N_PACK =
        26 + NativeImageUtils.SEG7_ENERGY_HIST_BINS * 2 + 6 + 1 +
            NativeImageUtils.SEG7_ATTEMPT_MAX * SEG7_ATTEMPT_F_PACK + 11
    const val SEG7_FALLBACK_H_FRAC = 0.08f
    /** Official ink pad (k=0: 0 pad). Walk itself does not pad; [padVertByStrokes] applies k=0 official / k>0 extra. */
    const val SEG7_K = 0f
    /** Horizontal jump as this × `s` (not used by Set ink-p4; width is jump-retract). */
    const val SEG7_J = 2f
    /** Empty-row skip and start-peek, as a fraction of `s`. */
    const val SEG7_GAP_FRAC = 0.5f
    /** Safety cap only: walk + k-pad share this × original-red H per side. */
    const val SEG7_VERT_CAP_FRAC = 2.5f
    const val SEG7_HORZ_CAP_S = 20
    const val SEG7_BAR_RUN_FRAC = 0.5f

    fun strokeWidthInSeed(gray: Mat, seed: Rect): StrokeWidthInSeed {
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

        val roi = gray.submat(s.top, s.bottom, s.left, s.right)
        try {
            val nPix = seedW * seedH
            val rows = Array(seedH) { ByteArray(seedW) }
            val hist = IntArray(256)
            for (y in 0 until seedH) {
                roi.get(y, 0, rows[y])
                for (x in 0 until seedW) {
                    hist[rows[y][x].toInt() and 0xff]++
                }
            }
            var sum = 0.0
            for (i in 0..255) sum += i * hist[i].toDouble()
            var sumB = 0.0
            var wB = 0
            var maxVar = -1.0
            var thr = 0
            for (t in 0..255) {
                wB += hist[t]
                if (wB == 0) continue
                val wF = nPix - wB
                if (wF == 0) break
                sumB += t * hist[t].toDouble()
                val mB = sumB / wB
                val mF = (sum - sumB) / wF
                val d = mB - mF
                val vr = wB.toDouble() * wF * d * d
                if (vr >= maxVar) {
                    maxVar = vr
                    thr = t
                }
            }
            var nz = 0
            for (i in 0..thr) nz += hist[i]
            var inkFrac = if (nPix > 0) nz.toFloat() / nPix else 0f
            var darkInk = true
            if (inkFrac >= SEG7_INK_FLIP_FRAC) {
                darkInk = false
                nz = nPix - nz
                inkFrac = if (nPix > 0) nz.toFloat() / nPix else 0f
            }
            fun inkAt(y: Int, x: Int): Boolean {
                val v = rows[y][x].toInt() and 0xff
                return if (darkInk) v <= thr else v > thr
            }
            val hhHist = IntArray(seedW + 1)
            var nNonSpan = 0
            var maxRun = 0
            for (y in 0 until seedH) {
                var run = 0
                fun close() {
                    if (run <= 0) return
                    if (run != seedW) {
                        hhHist[run]++
                        nNonSpan++
                        if (run > maxRun) maxRun = run
                    }
                }
                for (x in 0 until seedW) {
                    if (inkAt(y, x)) run++
                    else if (run > 0) {
                        close()
                        run = 0
                    }
                }
                if (run > 0) close()
            }
            val vhHist = IntArray(seedH + 1)
            for (x in 0 until seedW) {
                var run = 0
                for (y in 0 until seedH) {
                    if (inkAt(y, x)) {
                        run++
                    } else if (run > 0) {
                        if (run != seedH) vhHist[run]++
                        run = 0
                    }
                }
                if (run > 0 && run != seedH) vhHist[run]++
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
        } finally {
            roi.release()
        }
    }

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

    fun inkSweepBuf(n: Int, imgW: Int, imgH: Int): ShortArray {
        val axis = min(4096, max(imgW.coerceAtLeast(1), imgH.coerceAtLeast(1)))
        val span = axis * 2
        val per = 12 + span + 256
        return ShortArray((1 + n.coerceAtLeast(0) * per).coerceAtLeast(1))
    }

    fun poisonStatsBuf(n: Int): IntArray = IntArray(1 + n.coerceAtLeast(0) * 256)

    fun parsePoisonStats(a: IntArray?, n: Int): List<PoisonDump?> {
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

    fun parseInkSweeps(a: ShortArray?, n: Int): List<InkSweep?> {
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

    fun boundFlagName(v: Float): String = when (kotlin.math.round(v).toInt()) {
        1 -> "NORMAL_EXPAND"
        2 -> "NORMAL_RETRACT"
        3 -> "BLOCKED_RETRACT_LIMIT"
        4 -> "BLOCKED_GAP"
        else -> "UNCHANGED"
    }

    fun teleMethodName(v: Float): String = when (kotlin.math.round(v).toInt()) {
        1 -> "energy"
        4 -> "color_adaptive"
        else -> "gray"
    }

    fun dispKindName(v: Float): String = when (kotlin.math.round(v).toInt()) {
        0 -> "7seg"
        1 -> "not"
        else -> "unknown"
    }

    fun parseSeg7Tele(a: FloatArray, i: Int, overlap: ShortArray? = null): Seg7Telemetry? {
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
        val k: Float = SEG7_K,
        val j: Float = SEG7_J,
        val tele: Seg7Telemetry? = null,
        val sweep: InkSweep? = null,
        val poison: PoisonDump? = null,
        val rectPad: Rect = rect,
    )

    private fun unpackAabb7seg(
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

    private fun expandAabb7seg(
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

    fun expandGrayAabbTight(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::grayAabbTightNative,
    )

    fun expandGrayAabbRetract(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::grayAabbRetractNative,
    )

    fun expandColorAabbTight(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorAabbTightNative,
    )

    fun expandColorAabbRetract(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorAabbRetractNative,
    )

    fun expandColorAabbTightVsp(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorAabbTightVspNative,
    )

    fun expandColorAabbRetractVsp(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorAabbRetractVspNative,
    )

    fun expandGrayAabbExpand(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::grayAabbExpandNative,
    )

    fun expandColorAabbExpand(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
    ): List<Seg7Expand> = expandAabb7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorAabbExpandNative,
    )

    /**
     * Per-luma chroma magnitude, 8UC1, same size as [y]. NV21 [uv] is 8UC2
     * (128=neutral), typically 4:2:0 half-res. Each Y pixel samples the covering
     * even 4:2:0 site: `min(255, hypot(U-128, V-128))`. Caller releases the Mat.
     */
    fun chromaMagU8(y: Mat, uv: Mat): Mat {
        val h = if (y.empty()) 0 else y.rows()
        val w = if (y.empty()) 0 else y.cols()
        val out = Mat.zeros(h.coerceAtLeast(1), w.coerceAtLeast(1), CvType.CV_8UC1)
        if (y.empty() || w <= 0 || h <= 0) return out
        NativeImageUtils.chromaMagNative(y, uv, out)
        return out
    }

    /**
     * Pad [box] by `k`×`s` on each tip, clamped to remaining
     * [SEG7_VERT_CAP_FRAC]×original-[seed] height per side (walk already used
     * some of that budget). Frozen sides (no walk) still get the pad.
     * k=0 official: [k] ≤ 0 → 0 pad (walk-stop T/B). k>0 extra: k>0 keeps
     * `max(1, (k×s).roundToInt())`.
     */
    fun padVertByStrokes(
        box: Rect,
        seed: Rect,
        k: Float,
        sPx: Int,
        imgW: Int,
        imgH: Int,
    ): Rect {
        val seedH = max(1, seed.height())
        val capPx = max(1, (SEG7_VERT_CAP_FRAC * seedH).roundToInt())
        val kPad = if (k <= 0f) 0 else max(1, (k * max(1, sPx)).roundToInt())
        val walkUp = max(0, seed.top - box.top)
        val walkDown = max(0, box.bottom - seed.bottom)
        val padUp = min(kPad, max(0, capPx - walkUp))
        val padDown = min(kPad, max(0, capPx - walkDown))
        return clip(
            Rect(box.left, box.top - padUp, box.right, box.bottom + padDown),
            imgW, imgH,
        )
    }

    data class Seg7OrientedExpand(
        val quad: OrientedQuad,
        val stroke: StrokeWidthInSeed,
        val tele: Seg7Telemetry? = null,
        val sweep: InkSweep? = null,
        val poison: PoisonDump? = null,
        val quadPad: OrientedQuad = quad,
    )

    /**
     * 7seg walk along the red **normals** (`±v`) in source pixels. Frozen `u` span.
     * Does not pad or jump; caller uses [padOrientedByStrokes] then [jumpRetractOrientedU].
     */
    private fun expandOrient7seg(
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

    fun expandGrayOrientTight(
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
    fun expandGrayOrientRetract(
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
    fun expandColorOrientTight(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorOrientTightNative, tint,
    )
    fun expandColorOrientRetract(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorOrientRetractNative, tint,
    )
    fun expandColorOrientTightVsp(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorOrientTightVspNative, tint,
    )
    fun expandColorOrientRetractVsp(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorOrientRetractVspNative, tint,
    )

    private fun expandEnergyOrientMany(
        gray: Mat,
        seeds: List<OrientedQuad>,
        native: (Mat, FloatArray, ShortArray?, Mat?) -> FloatArray?,
    ): List<OrientedExpand> {
        if (gray.empty() || gray.type() != CvType.CV_8UC1 || seeds.isEmpty()) {
            return seeds.map { OrientedExpand(it, false) }
        }
        val imgW = gray.cols()
        val imgH = gray.rows()
        val packed = FloatArray(seeds.size * 8)
        seeds.forEachIndexed { i, q ->
            val p = q.pts
            val o = i * 8
            for (k in 0 until 8) packed[o + k] = p[k]
        }
        val sweepBuf = inkSweepBuf(seeds.size, imgW, imgH)
        val scratch = try { NativePaddleEngine.bufferSetA.s.mat } catch (_: Throwable) { null }
        val many = try {
            native(gray, packed, sweepBuf, scratch)
        } catch (_: Throwable) {
            null
        }
        if (many == null || many.size < seeds.size * 13) {
            return seeds.map { OrientedExpand(it, false) }
        }
        val sweeps = parseInkSweeps(sweepBuf, seeds.size)
        return seeds.indices.map { i ->
            val o = i * 13
            val nativeExp = NativeImageUtils.OrientedExpandNative(
                cx = many[o], cy = many[o + 1], bw = many[o + 2], bh = many[o + 3],
                angDeg = many[o + 4],
                stepsVNeg = many[o + 5].toInt(), stepsVPos = many[o + 6].toInt(),
                padV = many[o + 7].toInt(),
                hitVertCap = many[o + 8] >= 0.5f,
                stopEnergyUp = many[o + 9], stopEnergyDown = many[o + 10],
                base = many[o + 11], thr = many[o + 12],
            )
            val finalQuad = orientedFromCenter(
                nativeExp.cx, nativeExp.cy, nativeExp.bw, nativeExp.bh, nativeExp.angDeg,
            )
            val seed = seeds[i]
            val seedRr = minAreaFromQuad(seed)
            var seedBh = nativeExp.bh
            if (seedRr != null) {
                var bw = seedRr.size.width.toFloat()
                var bh = seedRr.size.height.toFloat()
                if (bw < bh) {
                    val tmp = bw; bw = bh; bh = tmp
                }
                seedBh = bh.coerceAtLeast(2f)
            }
            val cap = max(1, (0.4f * seedBh).roundToInt())
            val stopUp = nativeExp.stepsVNeg < cap
            val stopDown = nativeExp.stepsVPos < cap
            val nativeCnt = try {
                NativeImageUtils.countPullbackOrientedNative(
                    gray, seed.pts, finalQuad.pts, 0,
                    stopUp, stopDown,
                    COUNT_CLEAR_FRAC, COUNT_GROW_FRAC, COUNT_GROW_MAX_H_FRAC,
                )
            } catch (_: Throwable) {
                null
            }
            val (countQuad, countInfo) = if (nativeCnt != null) {
                val newV = ((nativeCnt.vNegAfter + nativeCnt.vPosAfter) * 0.5).toFloat()
                val newBh = (nativeCnt.vPosAfter - nativeCnt.vNegAfter).toFloat().coerceAtLeast(2f)
                val rad = Math.toRadians(nativeCnt.angDeg.toDouble())
                val ux = cos(rad).toFloat()
                val uy = sin(rad).toFloat()
                val vx = -uy
                val vy = ux
                val newCx = nativeCnt.seedCx + nativeCnt.existCu * ux + newV * vx
                val newCy = nativeCnt.seedCy + nativeCnt.existCu * uy + newV * vy
                val cq = orientedFromCenter(newCx, newCy, nativeCnt.existBw, newBh, nativeCnt.angDeg)
                val existAabb = finalQuad.toAabb()
                val countAabb = cq.toAabb()
                cq to CountPullInfo(
                    pulledTop = nativeCnt.pulledTop,
                    pulledBot = nativeCnt.pulledBot,
                    cSeed = nativeCnt.cSeed,
                    countThr = nativeCnt.countThr,
                    gxThr = nativeCnt.gxThr,
                    tBefore = existAabb.top,
                    bBefore = existAabb.bottom,
                    tAfter = countAabb.top,
                    bAfter = countAabb.bottom,
                    y0 = nativeCnt.y0,
                    counts = nativeCnt.counts,
                    axis = "v",
                    vNegBefore = nativeCnt.vNegBefore,
                    vPosBefore = nativeCnt.vPosBefore,
                    vNegAfter = nativeCnt.vNegAfter,
                    vPosAfter = nativeCnt.vPosAfter,
                    grewTop = nativeCnt.grewTop,
                    grewBot = nativeCnt.grewBot,
                    padTop = nativeCnt.padTop,
                    padBot = nativeCnt.padBot,
                )
            } else {
                val existAabb = finalQuad.toAabb()
                finalQuad to CountPullInfo(
                    pulledTop = false, pulledBot = false,
                    cSeed = 0.0, countThr = 0.0, gxThr = 0.0,
                    tBefore = existAabb.top, bBefore = existAabb.bottom,
                    tAfter = existAabb.top, bAfter = existAabb.bottom,
                    axis = "v",
                )
            }
            OrientedExpand(
                finalQuad, nativeExp.hitVertCap, null, countQuad, countInfo,
                sweeps.getOrNull(i),
            )
        }
    }

    fun expandEnergyOrientTight(
        gray: Mat, seeds: List<OrientedQuad>,
    ): List<OrientedExpand> = expandEnergyOrientMany(
        gray, seeds, NativeImageUtils::energyOrientTightNative,
    )

    fun expandEnergyOrientRetract(
        gray: Mat, seeds: List<OrientedQuad>,
    ): List<OrientedExpand> = expandEnergyOrientMany(
        gray, seeds, NativeImageUtils::energyOrientRetractNative,
    )

    fun expandGrayOrientExpand(
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
    fun expandColorOrientExpand(
        gray: Mat, uv: Mat?, seeds: List<OrientedQuad>,
        scratch: Mat? = null, combine: Mat? = null,
        overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
        tint: Mat? = null,
    ): List<Seg7OrientedExpand> = expandOrient7seg(
        gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
        NativeImageUtils::colorOrientExpandNative, tint,
    )

    /** k-pad along `±v`, remaining [SEG7_VERT_CAP_FRAC]×seed `bh` per side. Frozen sides still pad. */
    fun padOrientedByStrokes(
        walked: OrientedQuad,
        seed: OrientedQuad,
        k: Float,
        sPx: Int,
    ): OrientedQuad {
        val sb = OrientedBox.fromQuad(seed) ?: return walked
        val wb = sb.withPts(walked.pts)
        val seedBh = sb.vSpan().coerceAtLeast(1f)
        val capPx = max(1, (SEG7_VERT_CAP_FRAC * seedBh).roundToInt())
        val kPad = if (k <= 0f) 0 else max(1, (k * max(1, sPx)).roundToInt())
        val walkNeg = max(0f, sb.v0 - wb.v0)
        val walkPos = max(0f, wb.v1 - sb.v1)
        val padNeg = min(kPad.toFloat(), max(0f, capPx - walkNeg))
        val padPos = min(kPad.toFloat(), max(0f, capPx - walkPos))
        return OrientedBox(
            sb.cx, sb.cy, sb.ux, sb.uy, sb.vx, sb.vy,
            wb.u0, wb.u1, wb.v0 - padNeg, wb.v1 + padPos,
        ).toQuad()
    }

    /** Look-ink yellow: jump-far `u` and retract-start `v` (union of seed and walk). */
    fun lookInkJumpFarQuad(
        seed: OrientedQuad,
        walked: OrientedQuad,
        farU0: Float,
        farU1: Float,
    ): OrientedQuad {
        val sb = OrientedBox.fromQuad(seed) ?: return walked
        val wb = sb.withPts(walked.pts)
        val u0 = min(farU0, farU1)
        val u1 = max(farU0, farU1)
        return OrientedBox(
            sb.cx, sb.cy, sb.ux, sb.uy, sb.vx, sb.vy,
            u0, u1, min(sb.v0, wb.v0), max(sb.v1, wb.v1),
        ).toQuad()
    }

    /** Extra unofficial L/R pad along `±u` by [horizFrac] × seed short-axis `bh`. T/B (`v`) unchanged. */
    fun padOrientedU(q: OrientedQuad, horizFrac: Float, seed: OrientedQuad? = null): OrientedQuad {
        val frame = OrientedBox.fromQuad(seed ?: q) ?: return q
        val box = if (seed != null) frame.withPts(q.pts) else frame
        val pad = horizFrac * frame.vSpan().coerceAtLeast(1f)
        return OrientedBox(
            frame.cx, frame.cy, frame.ux, frame.uy, frame.vx, frame.vy,
            box.u0 - pad, box.u1 + pad, box.v0, box.v1,
        ).toQuad()
    }
    private fun dropWideComponents(bin: Mat, glareW: Int) {
        if (bin.empty() || glareW <= 0) return
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        try {
            val nLab = Imgproc.connectedComponentsWithStats(bin, labels, stats, centroids, 8)
            if (nLab <= 1) return
            val drop = BooleanArray(nLab)
            var any = false
            for (i in 1 until nLab) {
                val w = stats.get(i, Imgproc.CC_STAT_WIDTH)[0].toInt()
                if (w > glareW) {
                    drop[i] = true
                    any = true
                }
            }
            if (!any) return
            val h = bin.rows()
            val w = bin.cols()
            val labRow = IntArray(w)
            val pixRow = ByteArray(w)
            for (y in 0 until h) {
                labels.get(y, 0, labRow)
                bin.get(y, 0, pixRow)
                for (x in 0 until w) {
                    val id = labRow[x]
                    if (id in drop.indices && drop[id]) pixRow[x] = 0
                }
                bin.put(y, 0, pixRow)
            }
        } finally {
            labels.release()
            stats.release()
            centroids.release()
        }
    }

    /** Per-seed gray/color minRun: never raise 0.5×sPx; if seed has ink, min(halfS, 0.4×max in-seed row run). */
    private fun usedMinRun(sPx: Int, maxInSeedRun: Int): Int {
        val halfS = max(1, (SEG7_BAR_RUN_FRAC * sPx).roundToInt())
        if (maxInSeedRun > 0) {
            return min(halfS, max(1, (0.4f * maxInSeedRun).roundToInt()))
        }
        return halfS
    }

    private fun maxInSeedRunOnRows(bin: Mat, y0: Int, y1: Int, x0: Int = 0, x1: Int = -1): Int {
        var best = 0
        val ya = y0.coerceAtLeast(0)
        val yb = y1.coerceAtMost(bin.rows())
        for (y in ya until yb) {
            val r = maxInkRunOnRow(bin, y, x0, x1)
            if (r > best) best = r
        }
        return best
    }

    private fun maxInkRunOnRow(ink: Mat, y: Int, x0: Int = 0, x1: Int = -1): Int {
        val w = ink.cols()
        if (w <= 0 || y < 0 || y >= ink.rows()) return 0
        val xa = x0.coerceIn(0, w)
        val xb = (if (x1 < 0) w else x1).coerceIn(xa, w)
        if (xb <= xa) return 0
        val row = ByteArray(w)
        ink.get(y, 0, row)
        var run = 0
        var best = 0
        for (x in xa until xb) {
            if (row[x].toInt() and 0xff != 0) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }

    private fun maxInkRunOnCol(ink: Mat, x: Int, y0: Int, y1: Int): Int {
        val h = ink.rows()
        val w = ink.cols()
        if (w <= 0 || h <= 0 || x < 0 || x >= w) return 0
        val ya = y0.coerceIn(0, h)
        val yb = y1.coerceIn(ya, h)
        if (yb <= ya) return 0
        var run = 0
        var best = 0
        val row = ByteArray(w)
        for (y in ya until yb) {
            ink.get(y, 0, row)
            if (row[x].toInt() and 0xff != 0) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }

    private data class HorizRunHist(
        val hist: IntArray,
        val nNonSpan: Int,
        val maxRun: Int,
    )

    private fun horizRunHist(ink: Mat): HorizRunHist {
        val h = ink.rows()
        val w = ink.cols()
        val hist = IntArray(w + 1)
        if (h <= 0 || w <= 0) return HorizRunHist(hist, 0, 0)
        val row = ByteArray(w)
        var nNonSpan = 0
        var maxRun = 0
        for (y in 0 until h) {
            ink.get(y, 0, row)
            var run = 0
            fun close() {
                if (run <= 0) return
                if (run != w) {
                    hist[run]++
                    nNonSpan++
                    if (run > maxRun) maxRun = run
                }
            }
            for (x in 0 until w) {
                if (row[x].toInt() and 0xff != 0) {
                    run++
                } else if (run > 0) {
                    close()
                    run = 0
                }
            }
            if (run > 0) close()
        }
        return HorizRunHist(hist, nNonSpan, maxRun)
    }

    private fun vertRunHist(ink: Mat): IntArray {
        val h = ink.rows()
        val w = ink.cols()
        val hist = IntArray(h + 1)
        if (h <= 0 || w <= 0) return hist
        val rows = Array(h) { ByteArray(w) }
        for (y in 0 until h) ink.get(y, 0, rows[y])
        for (x in 0 until w) {
            var run = 0
            for (y in 0 until h) {
                val on = rows[y][x].toInt() and 0xff != 0
                if (on) {
                    run++
                } else if (run > 0) {
                    if (run != h) hist[run]++
                    run = 0
                }
            }
            if (run > 0 && run != h) hist[run]++
        }
        return hist
    }

    private fun peakCapped(hist: IntArray, minK: Int, maxK: Int): Int {
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

    fun expandDiagnose(
        gray: Mat,
        seed: Rect,
        mode: Mode,
        opts: ExpandOptions,
    ): AabbExpand {
        if (gray.empty() || gray.type() != CvType.CV_8UC1) return AabbExpand(seed, false)
        val imgW = gray.cols()
        val imgH = gray.rows()
        val s = clip(seed, imgW, imgH)
        if (mode == Mode.INTERIOR_ENERGY) {
            if (!opts.recordVertEnergy) {
                val many = expandDiagnoseMany(gray, null, listOf(s), mode, opts)
                if (many != null && many.size == 1) return many[0]
            }
            return AabbExpand(s, false)
        }
        return AabbExpand(expand(gray, seed, mode, opts), false)
    }

    /** AABB energy expand using fused |∇Y|+|∇C| for the vertical walk and L/R jump. */
    fun expandDiagnoseChroma(
        y: Mat,
        uv: Mat,
        seed: Rect,
        mode: Mode,
        opts: ExpandOptions,
    ): AabbExpand {
        if (y.empty() || y.type() != CvType.CV_8UC1) return AabbExpand(seed, false)
        val s = clip(seed, y.cols(), y.rows())
        if (mode == Mode.INTERIOR_ENERGY) {
            if (!opts.recordVertEnergy) {
                val many = expandDiagnoseMany(y, uv, listOf(s), mode, opts)
                if (many != null && many.size == 1) return many[0]
            }
            return AabbExpand(s, false)
        }
        return expandDiagnose(y, seed, mode, opts)
    }

    /**
     * Many-seed AABB energy U8 look in A.s. Native fail → seed boxes.
     */
    private fun expandEnergyAabbMany(
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

    fun expandEnergyAabbTight(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
    ): List<AabbExpand> = expandEnergyAabbMany(gray, uv, seeds, NativeImageUtils::energyAabbTightNative)

    fun expandEnergyAabbRetract(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
    ): List<AabbExpand> = expandEnergyAabbMany(gray, uv, seeds, NativeImageUtils::energyAabbRetractNative)

    fun expandEnergyAabbExpand(
        gray: Mat, uv: Mat?, seeds: List<Rect>,
    ): List<AabbExpand> = expandEnergyAabbMany(gray, uv, seeds, NativeImageUtils::energyAabbExpandNative)

    /**
     * Many-seed AABB energy. INTERIOR_ENERGY live path uses Expand recipe.
     * Null when traces are on (skip JNI). Native fail → seed boxes.
     */
    fun expandDiagnoseMany(
        gray: Mat,
        uv: Mat?,
        seeds: List<Rect>,
        mode: Mode,
        opts: ExpandOptions,
    ): List<AabbExpand>? {
        if (mode != Mode.INTERIOR_ENERGY || opts.recordVertEnergy) return null
        return expandEnergyAabbExpand(gray, uv, seeds)
    }

    fun expand(
        gray: Mat,
        seed: Rect,
        mode: Mode,
        opts: ExpandOptions,
    ): Rect {
        if (gray.empty() || gray.type() != CvType.CV_8UC1) return seed
        val imgW = gray.cols()
        val imgH = gray.rows()
        val s = clip(seed, imgW, imgH)
        return when (mode) {
            Mode.DUAL_SAUVOLA -> {
                val ink = dualInkMask(gray) ?: return s
                growOnMask(
                    gray, s, ink, opts.maxFrac, opts.minInkFrac,
                    opts.enableJump, opts.jumpFrac, opts.jumpVertPadPx, opts.retractClearFrac,
                )
            }
            Mode.INTERIOR_ENERGY -> expandDiagnose(gray, seed, mode, opts).rect
            Mode.EDGE_RING -> growOnEdges(
                gray, s, opts.maxFrac, opts.minEdgeRatio,
                opts.enableJump, opts.jumpFrac, opts.jumpVertPadPx, opts.retractClearFrac,
            )
            Mode.V025_THEN_DUAL -> {
                val blue = ratioExpand(s, v = 0.25f, horiz = 0.5f, imgW, imgH)
                val ink = dualInkMask(gray) ?: return s
                growOnMask(
                    gray, blue, ink, maxFrac = 0.5f, minFrac = opts.minInkFrac,
                    enableJump = opts.enableJump, jumpFrac = opts.jumpFrac,
                    jumpVertPadPx = opts.jumpVertPadPx, retractClearFrac = opts.retractClearFrac,
                )
            }
        }
    }

    private fun ratioExpand(seed: Rect, v: Float, horiz: Float, imgW: Int, imgH: Int): Rect {
        val hgt = max(1, seed.height())
        val nt = (seed.top - v * hgt).roundToInt().coerceIn(0, imgH - 1)
        val nb = (seed.bottom + v * hgt).roundToInt().coerceIn(nt + 1, imgH)
        val newH = nb - nt
        val hp = (horiz * newH).roundToInt()
        val nl = (seed.left - hp).coerceIn(0, imgW - 1)
        val nr = (seed.right + hp).coerceIn(nl + 1, imgW)
        return Rect(nl, nt, nr, nb)
    }

    private fun u8Crop(src: Mat?, h: Int, w: Int): Mat? {
        if (src == null || src.empty() || src.type() != CvType.CV_8UC1) return null
        if (src.rows() < h || src.cols() < w) return null
        return src.submat(0, h, 0, w)
    }

    private fun dualInkMask(gray: Mat): Mat? {
        val h = gray.rows()
        val w = gray.cols()
        val bSet = try { NativePaddleEngine.bufferSetB } catch (_: Throwable) { return null }
        val dark = u8Crop(bSet.p.mat, h, w) ?: return null
        val light = u8Crop(bSet.s.mat, h, w) ?: run {
            dark.release()
            return null
        }
        Imgproc.adaptiveThreshold(
            gray, dark, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV, 31, 5.0
        )
        Imgproc.adaptiveThreshold(
            gray, light, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY, 31, 5.0
        )
        Core.max(dark, light, dark)
        light.release()
        val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(dark, dark, Imgproc.MORPH_OPEN, k)
        k.release()
        return dark
    }

    private fun growOnMask(
        gray: Mat,
        seed: Rect,
        ink: Mat,
        maxFrac: Float,
        minFrac: Float,
        enableJump: Boolean,
        jumpFrac: Float,
        @Suppress("UNUSED_PARAMETER") jumpVertPadPx: Int,
        retractClearFrac: Float = 0.30f,
    ): Rect {
        val imgW = gray.cols(); val imgH = gray.rows()
        var l = seed.left; var t = seed.top; var r = seed.right; var b = seed.bottom
        val cap = max(1, (maxFrac * max(1, seed.height())).roundToInt())
        fun stripFrac(sl: Rect): Float {
            val c = clip(sl, imgW, imgH)
            if (c.width() <= 0 || c.height() <= 0) return 0f
            val roi = ink.submat(c.top, c.bottom, c.left, c.right)
            val nz = Core.countNonZero(roi).toFloat()
            val n = (roi.rows() * roi.cols()).toFloat().coerceAtLeast(1f)
            roi.release()
            return nz / n
        }
        fun growOnce() {
            repeat(cap) {
                var grew = false
                if (t > 0 && stripFrac(Rect(l, t - 1, r, t)) >= minFrac) { t--; grew = true }
                if (b < imgH && stripFrac(Rect(l, b, r, b + 1)) >= minFrac) { b++; grew = true }
                if (l > 0 && stripFrac(Rect(l - 1, t, l, b)) >= minFrac) { l--; grew = true }
                if (r < imgW && stripFrac(Rect(r, t, r + 1, b)) >= minFrac) { r++; grew = true }
                if (!grew) return
            }
        }
        fun growHorizontalOnce() {
            repeat(cap) {
                var grew = false
                if (l > 0 && stripFrac(Rect(l - 1, t, l, b)) >= minFrac) { l--; grew = true }
                if (r < imgW && stripFrac(Rect(r, t, r + 1, b)) >= minFrac) { r++; grew = true }
                if (!grew) return
            }
        }
        growOnce()
        if (enableJump) {
            val floorL = l; val floorR = r
            val hgt = max(1, b - t)
            val jx = max(1, (jumpFrac * hgt).roundToInt())
            l = (l - jx).coerceAtLeast(0)
            r = (r + jx).coerceAtMost(imgW)
            val inText =
                (l < floorL && stripFrac(Rect(l, t, min(l + 1, r), b)) >= minFrac) ||
                    (r > floorR && stripFrac(Rect(max(r - 1, l), t, r, b)) >= minFrac)
            if (inText) {
                growHorizontalOnce()
            } else {
                // Retract L/R until boundary strip is on ink (do not pass seed floor inward).
                while (l < floorL && stripFrac(Rect(l, t, min(l + 1, r), b)) < minFrac) l++
                while (r > floorR && stripFrac(Rect(max(r - 1, l), t, r, b)) < minFrac) r--
                // Pad left/right only so the box is clear of ink (not on first ink pixel).
                val clear = max(1, (retractClearFrac * max(1, b - t)).roundToInt())
                l = (l - clear).coerceAtLeast(0)
                r = (r + clear).coerceAtMost(imgW)
            }
        }
        ink.release()
        return clip(Rect(l, t, r, b), imgW, imgH)
    }
    private fun medianDouble(vals: DoubleArray): Double {
        if (vals.isEmpty()) return 1.0
        val s = vals.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else 0.5 * (s[n / 2 - 1] + s[n / 2])
    }
    private fun growOnEdges(
        gray: Mat,
        seed: Rect,
        maxFrac: Float,
        minEdgeRatio: Float,
        enableJump: Boolean,
        jumpFrac: Float,
        @Suppress("UNUSED_PARAMETER") jumpVertPadPx: Int,
        retractClearFrac: Float = 0.30f,
    ): Rect {
        val imgW = gray.cols(); val imgH = gray.rows()
        var l = seed.left; var t = seed.top; var r = seed.right; var b = seed.bottom
        val cap = max(1, (maxFrac * max(1, seed.height())).roundToInt())
        val bSet = try { NativePaddleEngine.bufferSetB } catch (_: Throwable) { return seed }
        val eq = u8Crop(bSet.p.mat, imgH, imgW) ?: return seed
        val edges = u8Crop(bSet.s.mat, imgH, imgW) ?: run {
            eq.release()
            return seed
        }
        Imgproc.equalizeHist(gray, eq)
        Imgproc.Canny(eq, edges, 40.0, 120.0)
        eq.release()
        fun dens(sl: Rect): Double {
            val c = clip(sl, imgW, imgH)
            if (c.width() <= 0 || c.height() <= 0) return 0.0
            val roi = edges.submat(c.top, c.bottom, c.left, c.right)
            val nz = Core.countNonZero(roi).toDouble()
            val n = (roi.rows() * roi.cols()).toDouble().coerceAtLeast(1.0)
            roi.release()
            return nz / n
        }
        val peri = listOf(
            dens(Rect(l, t, r, min(t + 1, b))),
            dens(Rect(l, max(b - 1, t), r, b)),
            dens(Rect(l, t, min(l + 1, r), b)),
            dens(Rect(max(r - 1, l), t, r, b)),
        ).average().coerceAtLeast(1e-3)
        val thr = minEdgeRatio * peri
        fun growOnce() {
            repeat(cap) {
                var grew = false
                if (t > 0 && dens(Rect(l, t - 1, r, t)) >= thr) { t--; grew = true }
                if (b < imgH && dens(Rect(l, b, r, b + 1)) >= thr) { b++; grew = true }
                if (l > 0 && dens(Rect(l - 1, t, l, b)) >= thr) { l--; grew = true }
                if (r < imgW && dens(Rect(r, t, r + 1, b)) >= thr) { r++; grew = true }
                if (!grew) return
            }
        }
        fun growHorizontalOnce() {
            repeat(cap) {
                var grew = false
                if (l > 0 && dens(Rect(l - 1, t, l, b)) >= thr) { l--; grew = true }
                if (r < imgW && dens(Rect(r, t, r + 1, b)) >= thr) { r++; grew = true }
                if (!grew) return
            }
        }
        growOnce()
        if (enableJump) {
            val floorL = l; val floorR = r
            val hgt = max(1, b - t)
            val jx = max(1, (jumpFrac * hgt).roundToInt())
            l = (l - jx).coerceAtLeast(0)
            r = (r + jx).coerceAtMost(imgW)
            val inText =
                (l < floorL && dens(Rect(l, t, min(l + 1, r), b)) >= thr) ||
                    (r > floorR && dens(Rect(max(r - 1, l), t, r, b)) >= thr)
            if (inText) {
                growHorizontalOnce()
            } else {
                while (l < floorL && dens(Rect(l, t, min(l + 1, r), b)) < thr) l++
                while (r > floorR && dens(Rect(max(r - 1, l), t, r, b)) < thr) r--
                val clear = max(1, (retractClearFrac * max(1, b - t)).roundToInt())
                l = (l - clear).coerceAtLeast(0)
                r = (r + clear).coerceAtMost(imgW)
            }
        }
        edges.release()
        return clip(Rect(l, t, r, b), imgW, imgH)
    }

    /**
     * Oriented box in a fixed u/v frame (u = longest edge). Extending u0/u1/v0/v1
     * translates that side along its normal and leaves the opposite side put.
     */
    private class OrientedBox(
        val cx: Float,
        val cy: Float,
        val ux: Float,
        val uy: Float,
        val vx: Float,
        val vy: Float,
        var u0: Float,
        var u1: Float,
        var v0: Float,
        var v1: Float,
    ) {
        fun area(): Float = (u1 - u0).coerceAtLeast(0f) * (v1 - v0).coerceAtLeast(0f)
        fun uSpan(): Float = (u1 - u0).coerceAtLeast(0f)
        fun vSpan(): Float = (v1 - v0).coerceAtLeast(0f)
        fun longAngleDeg(): Float {
            var a = Math.toDegrees(atan2(uy.toDouble(), ux.toDouble())).toFloat()
            while (a > 90f) a -= 180f
            while (a < -90f) a += 180f
            return a
        }

        fun proj(px: Float, py: Float): Pair<Float, Float> {
            val dx = px - cx
            val dy = py - cy
            return (dx * ux + dy * uy) to (dx * vx + dy * vy)
        }

        /** Project [pts] (n×2) into this seed frame; keep ux,uy,vx,vy. */
        fun withPts(pts: FloatArray): OrientedBox {
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

        fun cornerImage(i: Int): Pair<Float, Float> {
            val u = if (i == 0 || i == 3) u0 else u1
            val v = if (i == 0 || i == 1) v0 else v1
            return (cx + u * ux + v * vx) to (cy + u * uy + v * vy)
        }

        fun includeImagePoint(px: Float, py: Float) {
            val (u, v) = proj(px, py)
            if (u < u0) u0 = u
            if (u > u1) u1 = u
            if (v < v0) v0 = v
            if (v > v1) v1 = v
        }

        fun includeBox(other: OrientedBox) {
            for (i in 0 until 4) {
                val (px, py) = other.cornerImage(i)
                includeImagePoint(px, py)
            }
        }

        fun localAabb(other: OrientedBox): FloatArray {
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

        fun maxPoke(other: OrientedBox): Float {
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

        fun cornersInside(other: OrientedBox, eps: Float = 0.5f): Int {
            var n = 0
            for (i in 0 until 4) {
                val (px, py) = other.cornerImage(i)
                val (u, v) = proj(px, py)
                if (u >= u0 - eps && u <= u1 + eps && v >= v0 - eps && v <= v1 + eps) n++
            }
            return n
        }

        fun toQuad(): OrientedQuad {
            fun c(u: Float, v: Float) = floatArrayOf(
                cx + u * ux + v * vx,
                cy + u * uy + v * vy,
            )
            val a = c(u0, v0)
            val b = c(u1, v0)
            val d = c(u1, v1)
            val e = c(u0, v1)
            return OrientedQuad(
                floatArrayOf(a[0], a[1], b[0], b[1], d[0], d[1], e[0], e[1]),
            )
        }

        companion object {
            fun fromQuad(q: OrientedQuad): OrientedBox? {
                val p = q.pts
                if (p.size < 8) return null
                // u = edge closest to horizontal (|atan2| folded to [0, 90°]); tie → longer.
                // v = +90° from u, flipped so vy ≥ 0 (v1 = lower flatter side).
                var bestAng = 1e9
                var bestLen = 0.0
                var ux = 1f
                var uy = 0f
                for (i in 0 until 4) {
                    val j = (i + 1) % 4
                    val dx = (p[j * 2] - p[i * 2]).toDouble()
                    val dy = (p[j * 2 + 1] - p[i * 2 + 1]).toDouble()
                    val len = hypot(dx, dy)
                    if (len < 1e-3) continue
                    var ang = abs(atan2(dy, dx))
                    if (ang > Math.PI / 2.0) ang = Math.PI - ang
                    val closer = ang < bestAng - 1e-6
                    val tieLonger = abs(ang - bestAng) <= 1e-6 && len > bestLen
                    if (closer || tieLonger) {
                        bestAng = ang
                        bestLen = len
                        ux = (dx / len).toFloat()
                        uy = (dy / len).toFloat()
                    }
                }
                if (bestLen < 2.0) return null
                var vx = -uy
                var vy = ux
                if (vy < 0f) {
                    vx = -vx
                    vy = -vy
                }
                var cx = 0f
                var cy = 0f
                for (i in 0 until 4) {
                    cx += p[i * 2]
                    cy += p[i * 2 + 1]
                }
                cx /= 4f
                cy /= 4f
                var u0 = Float.POSITIVE_INFINITY
                var u1 = Float.NEGATIVE_INFINITY
                var v0 = Float.POSITIVE_INFINITY
                var v1 = Float.NEGATIVE_INFINITY
                for (i in 0 until 4) {
                    val dx = p[i * 2] - cx
                    val dy = p[i * 2 + 1] - cy
                    val u = dx * ux + dy * uy
                    val v = dx * vx + dy * vy
                    if (u < u0) u0 = u
                    if (u > u1) u1 = u
                    if (v < v0) v0 = v
                    if (v > v1) v1 = v
                }
                if (u1 - u0 < 2f || v1 - v0 < 2f) return null
                return OrientedBox(cx, cy, ux, uy, vx, vy, u0, u1, v0, v1)
            }
        }
    }

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
    )

    data class SeedInkProbe(
        val nThr: Int,
        val w: Int,
        val h: Int,
        val thrs: List<SeedInkThr>,
    )

    private val seedInkKindNames = arrayOf(
        "gt", "band", "union", "cband", "cunion", "pick", "flood", "cpick", "cflood",
    )

    private fun unpackSeedInkProbe(
        raw: Array<Any>?,
        fallbackW: Int,
        fallbackH: Int,
    ): SeedInkProbe {
        val fw = fallbackW.coerceAtLeast(1)
        val fh = fallbackH.coerceAtLeast(1)
        if (raw == null || raw.size < 2) return SeedInkProbe(0, fw, fh, emptyList())
        val thrs = ArrayList<SeedInkThr>()
        var nThr = 0
        var w = fw
        var h = fh
        var i = 0
        while (i + 1 < raw.size) {
            val meta = raw[i] as? IntArray
            val jpeg = raw[i + 1] as? ByteArray
            i += 2
            if (meta == null || meta.size < 11 || jpeg == null) continue
            val ki = meta[0]
            val kind = if (ki in seedInkKindNames.indices) seedInkKindNames[ki] else "gt"
            if (kind == "gt") nThr++
            w = meta[9].coerceAtLeast(1)
            h = meta[10].coerceAtLeast(1)
            thrs.add(
                SeedInkThr(
                    t = meta[1],
                    sPx = meta[4],
                    jpeg = jpeg,
                    kind = kind,
                    tLo = meta[2],
                    tHi = meta[3],
                    seedW = meta[5],
                    seedH = meta[6],
                    skipTint = meta[7] != 0,
                    nBand = meta[8],
                ),
            )
        }
        if (thrs.isEmpty()) return SeedInkProbe(0, fw, fh, emptyList())
        return SeedInkProbe(nThr = nThr, w = w, h = h, thrs = thrs)
    }

    /**
     * Per-threshold stroke/gap ink probe on a gray ROI. Exact-span H/V runs
     * (`run==W` / `run==H`) are sheet only — counted, never green.
     * Peek classifies on the full photo run; look raster stays the seed.
     */
    fun probeSeedInk(
        gray: Mat,
        roi: Rect,
        imgW: Int,
        uv: Mat? = null,
        sheetY: Mat,
        whiteY: Mat,
        strokeY: Mat,
        deskewPY: Mat,
        destY: Mat,
        destUv: Mat?,
        destW: Int,
        destH: Int,
    ): SeedInkProbe {
        fun empty(mw: Int, mh: Int): SeedInkProbe {
            val w = mw.coerceAtLeast(1)
            val h = mh.coerceAtLeast(1)
            return SeedInkProbe(0, w, h, emptyList())
        }
        if (gray.empty() || gray.type() != CvType.CV_8UC1) return empty(1, 1)
        val x0 = roi.left.coerceAtLeast(0)
        val y0 = roi.top.coerceAtLeast(0)
        val x1 = roi.right.coerceAtMost(gray.cols())
        val y1 = roi.bottom.coerceAtMost(gray.rows())
        val w = x1 - x0
        val h = y1 - y0
        if (w < 1 || h < 1) return empty(1, 1)
        return unpackSeedInkProbe(
            NativeImageUtils.probeSeedInk(
                gray.nativeObj, uv?.nativeObj ?: 0L, x0, y0, x1, y1, null, imgW, uv != null,
                sheetY.nativeObj, whiteY.nativeObj, strokeY.nativeObj, deskewPY.nativeObj,
                destY.nativeObj, destUv?.nativeObj ?: 0L, destW, destH,
            ),
            w,
            h,
        )
    }

    /** u/v raster on [gray] / [uv]; peek continues in photo u/v. No warp. */
    fun probeSeedInkUv(
        gray: Mat,
        uv: Mat?,
        quad: OrientedQuad,
        imgW: Int,
        sheetY: Mat,
        whiteY: Mat,
        strokeY: Mat,
        deskewPY: Mat,
        destY: Mat,
        destUv: Mat?,
        destW: Int,
        destH: Int,
    ): SeedInkProbe {
        fun empty(mw: Int, mh: Int): SeedInkProbe {
            val w = mw.coerceAtLeast(1)
            val h = mh.coerceAtLeast(1)
            return SeedInkProbe(0, w, h, emptyList())
        }
        if (gray.empty() || gray.type() != CvType.CV_8UC1) return empty(1, 1)
        if (quad.pts.size < 8) return empty(1, 1)
        return unpackSeedInkProbe(
            NativeImageUtils.probeSeedInk(
                gray.nativeObj, uv?.nativeObj ?: 0L, 0, 0, 0, 0, quad.pts, imgW, uv != null,
                sheetY.nativeObj, whiteY.nativeObj, strokeY.nativeObj, deskewPY.nativeObj,
                destY.nativeObj, destUv?.nativeObj ?: 0L, destW, destH,
            ),
            1,
            1,
        )
    }

    /** 64-bin local-min midpoints; 3-bin smooth; rise both sides. Port of native findValleyMidpoints64. */
    fun findValleyMidpoints64(bins: FloatArray): IntArray {
        if (bins.size < 64) return IntArray(0)
        val smoothed = FloatArray(64)
        for (i in 0 until 64) {
            val start = max(0, i - 1)
            val end = min(63, i + 1)
            var s = 0f
            var n = 0
            for (j in start..end) {
                s += bins[j]
                n++
            }
            smoothed[i] = s / max(1, n).toFloat()
        }
        val out = ArrayList<Int>(16)
        val seen = HashSet<Int>()
        var i = 1
        while (i < 63) {
            if (smoothed[i] <= smoothed[i - 1] && smoothed[i] <= smoothed[i + 1]) {
                val startIdx = i
                while (i < 63 && smoothed[i + 1] == smoothed[startIdx]) i++
                val endIdx = i
                val risesLeft = smoothed[startIdx - 1] > smoothed[startIdx]
                val risesRight = endIdx < 63 && smoothed[endIdx + 1] > smoothed[endIdx]
                if (risesLeft && risesRight) {
                    val mid = (startIdx + endIdx) / 2
                    if (seen.add(mid) && out.size < 64) out.add(mid)
                }
            }
            i++
        }
        return out.toIntArray()
    }

    private fun otsuFromHist256(hist: IntArray): Int {
        var total = 0
        var sumAll = 0L
        val n = min(256, hist.size)
        for (i in 0 until n) {
            total += hist[i]
            sumAll += i.toLong() * hist[i]
        }
        if (total <= 0) return 0
        var w0 = 0L
        var sum0 = 0L
        var best = -1.0
        var thr = 0
        for (t in 0 until n - 1) {
            w0 += hist[t]
            if (w0 == 0L) continue
            val w1 = total - w0
            if (w1 == 0L) break
            sum0 += t.toLong() * hist[t]
            val m0 = sum0.toDouble() / w0.toDouble()
            val m1 = (sumAll - sum0).toDouble() / w1.toDouble()
            val d = m0 - m1
            val between = w0.toDouble() * w1.toDouble() * d * d
            if (between > best) {
                best = between
                thr = t
            }
        }
        return thr
    }

    private fun longAngleDeltaDeg(a: Float, b: Float): Float {
        var d = abs(a - b) % 180f
        if (d > 90f) d = 180f - d
        return d
    }

    private fun localInterArea(a: OrientedBox, loc: FloatArray): Float {
        val iu0 = max(a.u0, loc[0])
        val iu1 = min(a.u1, loc[1])
        val iv0 = max(a.v0, loc[2])
        val iv1 = min(a.v1, loc[3])
        val w = (iu1 - iu0).coerceAtLeast(0f)
        val h = (iv1 - iv0).coerceAtLeast(0f)
        return w * h
    }

    private fun shouldGrowOriented(a: OrientedBox, b: OrientedBox, pokePx: Float): Boolean {
        val poke = a.maxPoke(b)
        if (poke <= 0.5f) return true
        val loc = a.localAabb(b)
        val inter = localInterArea(a, loc)
        if (inter <= 0f) return false
        val aArea = a.area().coerceAtLeast(1f)
        val bArea = ((loc[1] - loc[0]).coerceAtLeast(0f) * (loc[3] - loc[2]).coerceAtLeast(0f))
            .coerceAtLeast(1f)
        val union = aArea + bArea - inter
        val iou = if (union > 0f) inter / union else 0f
        val coverMin = inter / min(aArea, bArea)
        val vRatio = min(a.vSpan(), (loc[3] - loc[2]).coerceAtLeast(0f)) /
            max(a.vSpan(), (loc[3] - loc[2]).coerceAtLeast(1f))
        val nIn = a.cornersInside(b)
        if (poke <= pokePx && nIn >= 2) return true
        if (poke <= pokePx && coverMin >= 0.50f) return true
        if (longAngleDeltaDeg(a.longAngleDeg(), b.longAngleDeg()) <= 15f &&
            (iou >= 0.50f || (coverMin >= 0.80f && vRatio >= 0.70f))
        ) {
            return true
        }
        return false
    }

    private fun mergeOrientedSimilarAndPoke(boxes: MutableList<OrientedBox>, pokePx: Float) {
        var changed = true
        var guard = 0
        while (changed && guard++ < 16) {
            changed = false
            boxes.sortByDescending { it.area() }
            val used = BooleanArray(boxes.size)
            val out = ArrayList<OrientedBox>(boxes.size)
            for (i in boxes.indices) {
                if (used[i]) continue
                val cur = boxes[i]
                for (j in i + 1 until boxes.size) {
                    if (used[j]) continue
                    val oth = boxes[j]
                    if (!shouldGrowOriented(cur, oth, pokePx)) continue
                    cur.includeBox(oth)
                    used[j] = true
                    changed = true
                }
                out.add(cur)
            }
            boxes.clear()
            boxes.addAll(out)
        }
    }

    private fun clip(r: Rect, w: Int, h: Int): Rect {
        val l = r.left.coerceIn(0, w - 1)
        val t = r.top.coerceIn(0, h - 1)
        val rr = r.right.coerceIn(l + 1, w)
        val b = r.bottom.coerceIn(t + 1, h)
        return Rect(l, t, rr, b)
    }
}
