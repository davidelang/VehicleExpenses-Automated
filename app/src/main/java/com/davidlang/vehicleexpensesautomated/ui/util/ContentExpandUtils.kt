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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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
 * | [vertEnergy] | MAGNITUDE | MAGNITUDE = |∇|; GX = |∂I/∂x| only; XYCUT_GX = peak-isolate on gx |
 * | [freezeHorzDuringVert] | false | If true, first grow is top/bottom only (seed width frozen) |
 * | [vertPadFrac] | 0 | After vertical stop, pad each tip by this × seedH (one scale, not a G list) |
 * | count pullback | post | Additive: first run-count valley below 0.45×seed median; AABB = image y / Sobel-x; rot = ±v / \|∇I·û\| |
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
    }

    data class VertEnergySample(
        val dy: Int,
        val energy: Double,
        val ratio: Double,
        val width: Int,
        val count: Double = 0.0,
    )

    /**
     * Additive count-valley pullback (never grows, never retracts into the seed).
     *
     * AABB: [axis] `image_y`; [tAfter] ≥ [tBefore], [bAfter] ≤ [bBefore] (image y).
     * Oriented: [axis] `v`; [pulledTop] is the −v tip (normal to the long edge),
     * [pulledBot] is +v. [y0] is then the v of [counts][0] in the seed frame.
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
    ) {
        val pulled: Boolean get() = pulledTop || pulledBot
    }

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
    )
    data class OrientedExpand(
        val quad: OrientedQuad,
        val hitVertCap: Boolean,
        val energyTrace: VertEnergyTrace? = null,
        val countQuad: OrientedQuad = quad,
        val countPull: CountPullInfo? = null,
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
    ): OrientedExpand {
        if (gray.empty() || gray.type() != CvType.CV_8UC1) return OrientedExpand(seed, false)
        val imgW = gray.cols()
        val imgH = gray.rows()
        val rr = minAreaFromQuad(seed) ?: return OrientedExpand(seed, false)
        var cx = rr.center.x.toFloat()
        var cy = rr.center.y.toFloat()
        // OpenCV size: width along angle, height perpendicular
        var bw = rr.size.width.toFloat().coerceAtLeast(2f)
        var bh = rr.size.height.toFloat().coerceAtLeast(2f)
        var ang = rr.angle.toFloat() // degrees
        // Prefer long side as u (text direction)
        if (bw < bh) {
            val t = bw; bw = bh; bh = t
            ang += 90f
        }
        val rad = Math.toRadians(ang.toDouble())
        val ux = cos(rad).toFloat()
        val uy = sin(rad).toFloat()
        val vx = -uy
        val vy = ux

        val gx = Mat(); val gy = Mat(); val eng = Mat()
        Imgproc.Sobel(gray, gx, CvType.CV_32F, 1, 0, 3)
        Imgproc.Sobel(gray, gy, CvType.CV_32F, 0, 1, 3)
        Core.magnitude(gx, gy, eng)
        gx.release(); gy.release()

        fun sampleEnergy(px: Float, py: Float): Double {
            val x = px.roundToInt().coerceIn(0, imgW - 1)
            val y = py.roundToInt().coerceIn(0, imgH - 1)
            val buf = FloatArray(1)
            eng.get(y, x, buf)
            return buf[0].toDouble()
        }

        fun stripEnergy(duSign: Float, dvSign: Float, alongU: Boolean): Double {
            // Strip just outside edge: if alongU, outside ±u face; else outside ±v face
            val halfU = bw * 0.5f
            val halfV = bh * 0.5f
            val n = max(4, (if (alongU) bh else bw).roundToInt())
            var sum = 0.0
            var cnt = 0
            for (i in 0 until n) {
                val t = (i + 0.5f) / n - 0.5f // [-0.5,0.5]
                val u: Float
                val v: Float
                if (alongU) {
                    u = duSign * (halfU + 0.5f)
                    v = t * bh
                } else {
                    u = t * bw
                    v = dvSign * (halfV + 0.5f)
                }
                val px = cx + u * ux + v * vx
                val py = cy + u * uy + v * vy
                if (px < 0 || py < 0 || px >= imgW || py >= imgH) continue
                sum += sampleEnergy(px, py)
                cnt++
            }
            return if (cnt > 0) sum / cnt else 0.0
        }

        // Interior baseline energy
        var baseSum = 0.0
        var baseN = 0
        val nu = max(4, (bw * 0.6f).roundToInt())
        val nv = max(3, (bh * 0.6f).roundToInt())
        for (iu in 0 until nu) {
            for (iv in 0 until nv) {
                val u = ((iu + 0.5f) / nu - 0.5f) * bw * 0.7f
                val v = ((iv + 0.5f) / nv - 0.5f) * bh * 0.7f
                val px = cx + u * ux + v * vx
                val py = cy + u * uy + v * vy
                if (px < 0 || py < 0 || px >= imgW || py >= imgH) continue
                baseSum += sampleEnergy(px, py)
                baseN++
            }
        }
        val thr = opts.energyRatio * max(baseSum / max(baseN, 1), 1e-3)
        val seedCx = cx
        val seedCy = cy
        val seedBw = bw
        val seedBh = bh
        val seedAabb = seed.toAabb()
        val cap = max(1, (opts.maxFrac * seedBh).roundToInt())
        var stepsVNeg = 0
        var stepsVPos = 0
        val freezeHorz = opts.freezeHorzDuringVert

        fun growOnce() {
            repeat(cap) {
                var grew = false
                // ±u (ends) — skipped while walking height if width is frozen
                if (!freezeHorz) {
                    if (stripEnergy(-1f, 0f, alongU = true) >= thr) {
                        cx -= 0.5f * ux
                        cy -= 0.5f * uy
                        bw += 1f
                        grew = true
                    }
                    if (stripEnergy(+1f, 0f, alongU = true) >= thr) {
                        cx += 0.5f * ux
                        cy += 0.5f * uy
                        bw += 1f
                        grew = true
                    }
                }
                // ±v (above/below)
                if (stripEnergy(0f, -1f, alongU = false) >= thr) {
                    cx -= 0.5f * vx
                    cy -= 0.5f * vy
                    bh += 1f
                    stepsVNeg++
                    grew = true
                }
                if (stripEnergy(0f, +1f, alongU = false) >= thr) {
                    cx += 0.5f * vx
                    cy += 0.5f * vy
                    bh += 1f
                    stepsVPos++
                    grew = true
                }
                if (!grew) return
            }
        }
        /** Post-jump: continue along the long axis only. Height stays at first-grow. */
        fun growHorizontalOnce() {
            repeat(cap) {
                var grew = false
                if (stripEnergy(-1f, 0f, alongU = true) >= thr) {
                    cx -= 0.5f * ux
                    cy -= 0.5f * uy
                    bw += 1f
                    grew = true
                }
                if (stripEnergy(+1f, 0f, alongU = true) >= thr) {
                    cx += 0.5f * ux
                    cy += 0.5f * uy
                    bw += 1f
                    grew = true
                }
                if (!grew) return
            }
        }

        growOnce()
        var padV = 0
        if (opts.vertPadFrac > 0f) {
            padV = max(1, (opts.vertPadFrac * seedBh).roundToInt())
            bh += 2f * padV
        }

        val recordVertEnergy = opts.recordVertEnergy
        val upSamples = ArrayList<VertEnergySample>()
        val downSamples = ArrayList<VertEnergySample>()
        val inside = ArrayList<VertEnergySample>()
        val scanUp = ArrayList<VertEnergySample>()
        val scanDown = ArrayList<VertEnergySample>()
        val afterUp = ArrayList<VertEnergySample>()
        val afterDown = ArrayList<VertEnergySample>()
        val lookAhead = max(VERT_ENERGY_LOOKAHEAD_MIN_PX, max(1, seedBh.roundToInt()))
        fun stripAtSeedV(offsetFromCenterV: Float): Double {
            val n = max(4, seedBw.roundToInt())
            var sum = 0.0
            var cnt = 0
            for (i in 0 until n) {
                val t = (i + 0.5f) / n - 0.5f
                val u = t * seedBw
                val px = seedCx + u * ux + offsetFromCenterV * vx
                val py = seedCy + u * uy + offsetFromCenterV * vy
                if (px < 0 || py < 0 || px >= imgW || py >= imgH) continue
                sum += sampleEnergy(px, py)
                cnt++
            }
            return if (cnt > 0) sum / cnt else 0.0
        }
        if (recordVertEnergy) {
            val w = max(1, seedBw.roundToInt())
            val denom = max(baseSum / max(baseN, 1), 1e-3)
            val half = seedBh * 0.5f
            var y = 0
            while (y < seedBh) {
                val e = stripAtSeedV(-half + y + 0.5f)
                inside.add(VertEnergySample(y, e, e / denom, w))
                y++
            }
            var k = 1
            while (k <= stepsVNeg + lookAhead) {
                val e = stripAtSeedV(-half - k)
                val s = VertEnergySample(k, e, e / denom, w)
                scanUp.add(s)
                if (k <= stepsVNeg) upSamples.add(s)
                if (k > stepsVNeg) afterUp.add(s)
                k++
            }
            k = 1
            while (k <= stepsVPos + lookAhead) {
                val e = stripAtSeedV(half + k)
                val s = VertEnergySample(k, e, e / denom, w)
                scanDown.add(s)
                if (k <= stepsVPos) downSamples.add(s)
                if (k > stepsVPos) afterDown.add(s)
                k++
            }
        }
        val stopEnergyUp = stripAtSeedV(-seedBh * 0.5f - stepsVNeg - 1f)
        val stopEnergyDown = stripAtSeedV(seedBh * 0.5f + stepsVPos + 1f)
        val stopUp = if (stepsVNeg >= cap) "cap" else "energy"
        val stopDown = if (stepsVPos >= cap) "cap" else "energy"

        if (opts.enableJump) {
            val floorBw = bw
            val floorCx = cx
            val floorCy = cy
            val j = max(1f, opts.jumpFrac * bh)
            // Jump both ends along u (length grows by 2j, center fixed).
            bw += 2f * j
            val stillText =
                stripEnergy(-1f, 0f, alongU = true) >= thr ||
                    stripEnergy(+1f, 0f, alongU = true) >= thr
            if (stillText) {
                growHorizontalOnce()
            } else {
                // Retract until boundary strip is on ink (meanE >= thr).
                while (bw > floorBw + 0.5f && stripEnergy(-1f, 0f, alongU = true) < thr) {
                    cx += 0.5f * ux
                    cy += 0.5f * uy
                    bw -= 1f
                }
                while (bw > floorBw + 0.5f && stripEnergy(+1f, 0f, alongU = true) < thr) {
                    cx -= 0.5f * ux
                    cy -= 0.5f * uy
                    bw -= 1f
                }
                if (bw < floorBw) {
                    bw = floorBw; cx = floorCx; cy = floorCy
                }
                // Pad outward so box is clear of ink edge (not sitting on first ink pixel).
                val clear = max(1f, opts.retractClearFrac * bh)
                bw += 2f * clear
            }
        }
        val hitVertCap = stepsVNeg + padV >= cap || stepsVPos + padV >= cap
        val finalQuad = orientedFromCenter(cx, cy, bw, bh, ang)
        val trace = if (recordVertEnergy) {
            VertEnergyTrace(
                seed = seedAabb,
                base = baseSum / max(baseN, 1),
                thr = thr,
                energyRatio = opts.energyRatio,
                up = upSamples,
                down = downSamples,
                inside = inside,
                scanUp = scanUp,
                scanDown = scanDown,
                afterUp = afterUp,
                afterDown = afterDown,
                lookAhead = lookAhead,
                rawRoi = try {
                    captureEnergyRoi(gray, eng, seedAabb, imgW, imgH)
                } catch (_: Throwable) {
                    null
                },
                stopUp = stopUp,
                stopDown = stopDown,
                stopEnergyUp = stopEnergyUp,
                stopEnergyDown = stopEnergyDown,
                final = finalQuad.toAabb(),
            )
        } else {
            null
        }
        eng.release()
        val extraLook = if (recordVertEnergy) lookAhead else 0
        val (countQuad, countInfo) = countPullbackOriented(
            gray, seed, finalQuad, extraLook,
        )
        val traceOut = if (trace != null) {
            attachCountToTraceV(trace, seedBh, countInfo, countQuad.toAabb())
        } else {
            null
        }
        return OrientedExpand(finalQuad, hitVertCap, traceOut, countQuad, countInfo)
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
     * Warp [quad] to a horizontal strip of height [targetH] (recognition buffer),
     * using lowest corner as pivot: flattest (longest) side becomes horizontal.
     * Returns filled rec mat size targetW×targetH (caller owns dest content via [dest]).
     */
    fun warpQuadToHorizontalStrip(
        gray: Mat,
        quad: OrientedQuad,
        dest: Mat,
        targetH: Int = 48,
        maxW: Int = 320,
    ): Boolean {
        if (gray.empty() || targetH < 8) return false
        val order = orderQuadForWarp(quad) ?: return false
        // order: TL, TR, BR, BL after making long side horizontal with lowest pivot
        val wSrc = hypot(
            (order[2] - order[0]).toDouble(),
            (order[3] - order[1]).toDouble(),
        ).toFloat().coerceAtLeast(2f)
        val hSrc = hypot(
            (order[6] - order[0]).toDouble(),
            (order[7] - order[1]).toDouble(),
        ).toFloat().coerceAtLeast(2f)
        val scale = targetH / hSrc
        val rawW = (wSrc * scale).roundToInt().coerceAtLeast(8)
        val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(maxW).coerceAtLeast(32)
        val src = MatOfPoint2f(
            Point(order[0].toDouble(), order[1].toDouble()),
            Point(order[2].toDouble(), order[3].toDouble()),
            Point(order[4].toDouble(), order[5].toDouble()),
            Point(order[6].toDouble(), order[7].toDouble()),
        )
        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(targetW - 1.0, 0.0),
            Point(targetW - 1.0, targetH - 1.0),
            Point(0.0, targetH - 1.0),
        )
        val m = Imgproc.getPerspectiveTransform(src, dst)
        Imgproc.warpPerspective(
            gray, dest, m, Size(targetW.toDouble(), targetH.toDouble()),
            Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, Scalar(0.0),
        )
        m.release(); src.release(); dst.release()
        return !dest.empty() && dest.cols() >= 8 && dest.rows() >= 8
    }

    /**
     * Order corners TL,TR,BR,BL for an upright rec strip: the two smallest-y
     * points become the top edge (left-to-right). The old lowest-corner pivot
     * mapped the LCD bottom onto the strip top and inverted digits.
     */
    fun orderQuadForWarp(quad: OrientedQuad): FloatArray? {
        val p = Array(4) { i -> Point(quad.pts[i * 2].toDouble(), quad.pts[i * 2 + 1].toDouble()) }
        val byY = p.sortedBy { it.y }
        if (byY.size < 4) return null
        val top = byY.take(2).sortedBy { it.x }
        val bot = byY.drop(2).sortedBy { it.x }
        return floatArrayOf(
            top[0].x.toFloat(), top[0].y.toFloat(),
            top[1].x.toFloat(), top[1].y.toFloat(),
            bot[1].x.toFloat(), bot[1].y.toFloat(),
            bot[0].x.toFloat(), bot[0].y.toFloat(),
        )
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
        return when (mode) {
            Mode.INTERIOR_ENERGY -> growOnEnergy(gray, s, opts)
            else -> AabbExpand(expand(gray, seed, mode, opts), false)
        }
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
            Mode.DUAL_SAUVOLA -> growOnMask(
                gray, s, dualInkMask(gray), opts.maxFrac, opts.minInkFrac,
                opts.enableJump, opts.jumpFrac, opts.jumpVertPadPx, opts.retractClearFrac,
            )
            Mode.INTERIOR_ENERGY -> growOnEnergy(gray, s, opts).rect
            Mode.EDGE_RING -> growOnEdges(
                gray, s, opts.maxFrac, opts.minEdgeRatio,
                opts.enableJump, opts.jumpFrac, opts.jumpVertPadPx, opts.retractClearFrac,
            )
            Mode.V025_THEN_DUAL -> {
                val blue = ratioExpand(s, v = 0.25f, horiz = 0.5f, imgW, imgH)
                growOnMask(
                    gray, blue, dualInkMask(gray), maxFrac = 0.5f, minFrac = opts.minInkFrac,
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

    private fun dualInkMask(gray: Mat): Mat {
        val dark = Mat()
        val light = Mat()
        Imgproc.adaptiveThreshold(
            gray, dark, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV, 31, 5.0
        )
        Imgproc.adaptiveThreshold(
            gray, light, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY, 31, 5.0
        )
        val ink = Mat()
        Core.max(dark, light, ink)
        dark.release(); light.release()
        val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(ink, ink, Imgproc.MORPH_OPEN, k)
        k.release()
        return ink
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

    private fun smooth1d(a: DoubleArray, sigma: Double): DoubleArray {
        val s = max(0.6, sigma)
        val rad = max(1, (s * 3.0).roundToInt())
        val k = DoubleArray(2 * rad + 1)
        var sum = 0.0
        for (i in k.indices) {
            val x = (i - rad) / s
            k[i] = exp(-0.5 * x * x)
            sum += k[i]
        }
        for (i in k.indices) k[i] /= sum
        val out = DoubleArray(a.size)
        for (i in a.indices) {
            var acc = 0.0
            for (j in k.indices) {
                val ii = (i + j - rad).coerceIn(0, a.lastIndex)
                acc += a[ii] * k[j]
            }
            out[i] = acc
        }
        return out
    }

    /** Local maxima (or minima if [maxima] is false) with a simple prominence + min-distance prune. */
    private fun extrema1d(
        a: DoubleArray,
        prominence: Double,
        minDist: Int,
        maxima: Boolean,
    ): IntArray {
        val sign = if (maxima) 1.0 else -1.0
        val cand = ArrayList<Int>()
        for (i in 1 until a.lastIndex) {
            val v = sign * a[i]
            if (v < sign * a[i - 1] || v < sign * a[i + 1]) continue
            var leftMin = v
            var j = i - 1
            while (j >= 0 && sign * a[j] <= v + 1e-12) {
                leftMin = min(leftMin, sign * a[j])
                j--
            }
            var rightMin = v
            j = i + 1
            while (j < a.size && sign * a[j] <= v + 1e-12) {
                rightMin = min(rightMin, sign * a[j])
                j++
            }
            val prom = v - max(leftMin, rightMin)
            if (prom + 1e-12 >= prominence) cand.add(i)
        }
        val kept = ArrayList<Int>()
        for (i in cand.sortedByDescending { sign * a[it] }) {
            if (kept.none { abs(it - i) < minDist }) kept.add(i)
        }
        return kept.sorted().toIntArray()
    }

    /**
     * Isolate the profile peak that owns [seedT, seedB) on frozen [l, r).
     * Returns [top, bottom) in image Y. Never retracts into the seed.
     */
    private fun xycutOnProfile(
        eng: Mat,
        l: Int,
        seedT: Int,
        r: Int,
        seedB: Int,
        imgW: Int,
        imgH: Int,
        look: Int,
    ): IntArray {
        val y0 = (seedT - look).coerceAtLeast(0)
        val y1 = (seedB + look).coerceAtMost(imgH)
        if (y1 - y0 < 4 || r <= l) return intArrayOf(seedT, seedB)
        val n = y1 - y0
        val raw = DoubleArray(n)
        for (i in 0 until n) {
            val roi = eng.submat(y0 + i, y0 + i + 1, l.coerceAtLeast(0), r.coerceAtMost(imgW))
            raw[i] = if (roi.width() > 0 && roi.height() > 0) Core.mean(roi).`val`[0] else 0.0
            roi.release()
        }
        val sh = max(1, seedB - seedT)
        val sm = smooth1d(raw, sigma = max(1.0, 0.04 * sh))
        val st = (seedT - y0).coerceIn(0, n - 1)
        val sb = (seedB - y0).coerceIn(st + 1, n)
        var seedMin = sm[st]
        var seedMax = sm[st]
        for (i in st until sb) {
            if (sm[i] < seedMin) seedMin = sm[i]
            if (sm[i] > seedMax) seedMax = sm[i]
        }
        val prom = max(1e-8, 0.12 * (seedMax - seedMin + 1e-6))
        val minDist = max(3, sh / 6)
        val peaks = extrema1d(sm, prom, minDist, maxima = true)
        val valleys = extrema1d(sm, prom, max(2, sh / 10), maxima = false)
        val mid = 0.5 * (st + sb)
        val own = if (peaks.isNotEmpty()) {
            peaks.minBy { abs(it - mid) }
        } else {
            var best = st
            var bestV = sm[st]
            for (i in st until sb) {
                if (sm[i] > bestV) {
                    bestV = sm[i]
                    best = i
                }
            }
            best
        }
        var top = y0
        var bot = y1
        for (v in valleys.reversed()) {
            if (v < own) {
                top = y0 + v
                break
            }
        }
        for (v in valleys) {
            if (v > own) {
                bot = y0 + v
                break
            }
        }
        top = min(top, seedT)
        bot = max(bot, seedB)
        return intArrayOf(top.coerceAtLeast(0), bot.coerceAtMost(imgH))
    }

    private fun runCount(vals: FloatArray, thr: Float, minRun: Int = 3): Int {
        var n = 0
        var run = 0
        for (v in vals) {
            if (v >= thr) {
                run++
            } else {
                if (run >= minRun) n++
                run = 0
            }
        }
        if (run >= minRun) n++
        return n
    }

    private fun gxAbsRow(gx: Mat, y: Int, l: Int, r: Int, imgW: Int, imgH: Int): FloatArray {
        val ya = y.coerceIn(0, imgH - 1)
        val xa = l.coerceAtLeast(0)
        val xb = r.coerceAtMost(imgW)
        val w = (xb - xa).coerceAtLeast(0)
        if (w <= 0) return FloatArray(0)
        val out = FloatArray(w)
        gx.get(ya, xa, out)
        return out
    }

    private fun seedGxP90(gx: Mat, seed: Rect, imgW: Int, imgH: Int): Double {
        val c = clip(seed, imgW, imgH)
        if (c.width() < 2 || c.height() < 2) return 8.0
        val n = c.width() * c.height()
        val buf = FloatArray(n)
        var i = 0
        val row = FloatArray(c.width())
        for (y in c.top until c.bottom) {
            gx.get(y, c.left, row)
            for (v in row) buf[i++] = v
        }
        buf.sort()
        val idx = ((n - 1) * 0.90).toInt().coerceIn(0, n - 1)
        return buf[idx].toDouble()
    }

    /**
     * Walk from the seed toward the existing energy box; first local min of
     * gx-run-count below 0.45 × seed-median pulls that tip back. Never grows,
     * never retracts into the seed. Width is the seed's (frozen).
     */
    fun countPullbackVertical(
        gray: Mat,
        seed: Rect,
        exist: Rect,
        extraLook: Int = 0,
    ): Pair<Rect, CountPullInfo> {
        val imgW = gray.cols()
        val imgH = gray.rows()
        val s = clip(seed, imgW, imgH)
        val e = clip(exist, imgW, imgH)
        val gx = Mat()
        Imgproc.Sobel(gray, gx, CvType.CV_32F, 1, 0, 3)
        Core.absdiff(gx, Scalar(0.0), gx)
        val p90 = seedGxP90(gx, s, imgW, imgH)
        val gxThr = max(8.0, 0.55 * p90)
        val look = extraLook.coerceAtLeast(0)
        val y0 = (min(s.top, e.top) - look).coerceAtLeast(0)
        val y1 = (max(s.bottom, e.bottom) + look).coerceAtMost(imgH)
        val n = (y1 - y0).coerceAtLeast(1)
        val raw = DoubleArray(n)
        for (i in 0 until n) {
            raw[i] = runCount(gxAbsRow(gx, y0 + i, s.left, s.right, imgW, imgH), gxThr.toFloat()).toDouble()
        }
        gx.release()
        val sh = max(1, s.height())
        val sm = smooth1d(raw, sigma = max(1.0, 0.04 * sh))
        val st = (s.top - y0).coerceIn(0, n - 2)
        val sb = (s.bottom - y0).coerceIn(st + 1, n)
        val seedVals = sm.copyOfRange(st, sb)
        seedVals.sort()
        val cSeed = seedVals[seedVals.size / 2]
        val te = (e.top - y0).coerceIn(0, st)
        val be = (e.bottom - y0).coerceIn(sb, n)
        var pulledT = false
        var pulledB = false
        var top = te
        var bot = be
        if (cSeed >= 1.0) {
            val cThr = 0.45 * cSeed
            for (i in (st - 1) downTo te) {
                val left = if (i == 0) sm[i] else sm[i - 1]
                val right = if (i == n - 1) sm[i] else sm[i + 1]
                if (sm[i] < cThr && sm[i] <= left && sm[i] <= right) {
                    top = i
                    pulledT = true
                    break
                }
            }
            for (i in sb until be) {
                val left = if (i == 0) sm[i] else sm[i - 1]
                val right = if (i == n - 1) sm[i] else sm[i + 1]
                if (sm[i] < cThr && sm[i] <= left && sm[i] <= right) {
                    bot = i
                    pulledB = true
                    break
                }
            }
        }
        top = min(top, st)
        bot = max(bot, sb)
        val tA = y0 + top
        val bA = y0 + bot
        val info = CountPullInfo(
            pulledTop = pulledT,
            pulledBot = pulledB,
            cSeed = cSeed,
            countThr = 0.45 * cSeed,
            gxThr = gxThr,
            tBefore = e.top,
            bBefore = e.bottom,
            tAfter = tA,
            bAfter = bA,
            y0 = y0,
            counts = sm,
        )
        val out = Rect(e.left, tA, e.right, bA)
        return clip(out, imgW, imgH) to info
    }

    /**
     * Same valley rule as [countPullbackVertical], but the walk is along ±v
     * (normal to the long edges) and the strip is |∇I·û| along seed width.
     * Output keeps the exist quad's u-extent; only the v-tips pull back.
     */
    fun countPullbackOriented(
        gray: Mat,
        seed: OrientedQuad,
        exist: OrientedQuad,
        extraLook: Int = 0,
    ): Pair<OrientedQuad, CountPullInfo> {
        val imgW = gray.cols()
        val imgH = gray.rows()
        val seedRr = minAreaFromQuad(seed)
        if (seedRr == null || gray.empty()) {
            val aabb = exist.toAabb()
            return exist to CountPullInfo(
                pulledTop = false, pulledBot = false,
                cSeed = 0.0, countThr = 0.0, gxThr = 8.0,
                tBefore = aabb.top, bBefore = aabb.bottom,
                tAfter = aabb.top, bAfter = aabb.bottom,
                axis = "v",
            )
        }
        var seedCx = seedRr.center.x.toFloat()
        var seedCy = seedRr.center.y.toFloat()
        var seedBw = seedRr.size.width.toFloat().coerceAtLeast(2f)
        var seedBh = seedRr.size.height.toFloat().coerceAtLeast(2f)
        var ang = seedRr.angle.toFloat()
        if (seedBw < seedBh) {
            val tmp = seedBw
            seedBw = seedBh
            seedBh = tmp
            ang += 90f
        }
        val rad = Math.toRadians(ang.toDouble())
        val ux = cos(rad).toFloat()
        val uy = sin(rad).toFloat()
        val vx = -uy
        val vy = ux
        fun uOf(px: Float, py: Float) = (px - seedCx) * ux + (py - seedCy) * uy
        fun vOf(px: Float, py: Float) = (px - seedCx) * vx + (py - seedCy) * vy
        val ePts = exist.pts
        var eU0 = Float.POSITIVE_INFINITY
        var eU1 = Float.NEGATIVE_INFINITY
        var eV0 = Float.POSITIVE_INFINITY
        var eV1 = Float.NEGATIVE_INFINITY
        var pi = 0
        while (pi + 1 < ePts.size) {
            val u = uOf(ePts[pi], ePts[pi + 1])
            val v = vOf(ePts[pi], ePts[pi + 1])
            if (u < eU0) eU0 = u
            if (u > eU1) eU1 = u
            if (v < eV0) eV0 = v
            if (v > eV1) eV1 = v
            pi += 2
        }
        val existBw = (eU1 - eU0).coerceAtLeast(2f)
        val existCu = (eU0 + eU1) * 0.5f
        val vSeedNeg = -seedBh * 0.5f
        val vSeedPos = seedBh * 0.5f
        val vExistNeg = eV0
        val vExistPos = eV1
        val gx = Mat()
        val gy = Mat()
        Imgproc.Sobel(gray, gx, CvType.CV_32F, 1, 0, 3)
        Imgproc.Sobel(gray, gy, CvType.CV_32F, 0, 1, 3)
        val gxv = FloatArray(1)
        val gyv = FloatArray(1)
        fun duAbs(px: Float, py: Float): Float {
            val x = px.roundToInt().coerceIn(0, imgW - 1)
            val y = py.roundToInt().coerceIn(0, imgH - 1)
            gx.get(y, x, gxv)
            gy.get(y, x, gyv)
            return abs(gxv[0] * ux + gyv[0] * uy)
        }
        val nU = max(4, seedBw.roundToInt())
        val seedBuf = ArrayList<Float>(max(8, nU * max(1, seedBh.roundToInt())))
        var sv = vSeedNeg
        while (sv <= vSeedPos) {
            for (i in 0 until nU) {
                val t = (i + 0.5f) / nU - 0.5f
                val u = t * seedBw
                seedBuf.add(duAbs(seedCx + u * ux + sv * vx, seedCy + u * uy + sv * vy))
            }
            sv += 1f
        }
        val p90 = if (seedBuf.size < 2) {
            8.0
        } else {
            seedBuf.sort()
            seedBuf[((seedBuf.size - 1) * 0.90).toInt().coerceIn(0, seedBuf.lastIndex)].toDouble()
        }
        val gxThr = max(8.0, 0.55 * p90)
        val look = extraLook.coerceAtLeast(0)
        val v0 = kotlin.math.floor(min(vSeedNeg, vExistNeg) - look).toInt()
        val v1 = kotlin.math.ceil(max(vSeedPos, vExistPos) + look).toInt()
        val n = v1 - v0
        if (n < 2) {
            gx.release()
            gy.release()
            val aabb = exist.toAabb()
            return exist to CountPullInfo(
                pulledTop = false, pulledBot = false,
                cSeed = 0.0, countThr = 0.0, gxThr = gxThr,
                tBefore = aabb.top, bBefore = aabb.bottom,
                tAfter = aabb.top, bAfter = aabb.bottom,
                axis = "v",
                vNegBefore = vExistNeg.toDouble(),
                vPosBefore = vExistPos.toDouble(),
                vNegAfter = vExistNeg.toDouble(),
                vPosAfter = vExistPos.toDouble(),
            )
        }
        val raw = DoubleArray(n)
        val row = FloatArray(nU)
        for (i in 0 until n) {
            val vv = (v0 + i).toFloat()
            for (k in 0 until nU) {
                val t = (k + 0.5f) / nU - 0.5f
                val u = t * seedBw
                row[k] = duAbs(seedCx + u * ux + vv * vx, seedCy + u * uy + vv * vy)
            }
            raw[i] = runCount(row, gxThr.toFloat()).toDouble()
        }
        gx.release()
        gy.release()
        val sh = max(1, seedBh.roundToInt())
        val sm = smooth1d(raw, sigma = max(1.0, 0.04 * sh))
        val st = (vSeedNeg - v0).roundToInt().coerceIn(0, n - 2)
        val sb = (vSeedPos - v0).roundToInt().coerceIn(st + 1, n)
        val seedVals = sm.copyOfRange(st, sb)
        seedVals.sort()
        val cSeed = seedVals[seedVals.size / 2]
        val te = (vExistNeg - v0).roundToInt().coerceIn(0, st)
        val be = (vExistPos - v0).roundToInt().coerceIn(sb, n)
        var pulledT = false
        var pulledB = false
        var top = te
        var bot = be
        if (cSeed >= 1.0) {
            val cThr = 0.45 * cSeed
            for (i in (st - 1) downTo te) {
                val left = if (i == 0) sm[i] else sm[i - 1]
                val right = if (i == n - 1) sm[i] else sm[i + 1]
                if (sm[i] < cThr && sm[i] <= left && sm[i] <= right) {
                    top = i
                    pulledT = true
                    break
                }
            }
            for (i in sb until be) {
                val left = if (i == 0) sm[i] else sm[i - 1]
                val right = if (i == n - 1) sm[i] else sm[i + 1]
                if (sm[i] < cThr && sm[i] <= left && sm[i] <= right) {
                    bot = i
                    pulledB = true
                    break
                }
            }
        }
        top = min(top, st)
        bot = max(bot, sb)
        val vNegOut = (v0 + top).toDouble()
        val vPosOut = (v0 + bot).toDouble()
        val newV = ((vNegOut + vPosOut) * 0.5).toFloat()
        val newBh = (vPosOut - vNegOut).toFloat().coerceAtLeast(2f)
        val newCx = seedCx + existCu * ux + newV * vx
        val newCy = seedCy + existCu * uy + newV * vy
        val countQuad = orientedFromCenter(newCx, newCy, existBw, newBh, ang)
        val existAabb = exist.toAabb()
        val countAabb = countQuad.toAabb()
        val info = CountPullInfo(
            pulledTop = pulledT,
            pulledBot = pulledB,
            cSeed = cSeed,
            countThr = 0.45 * cSeed,
            gxThr = gxThr,
            tBefore = existAabb.top,
            bBefore = existAabb.bottom,
            tAfter = countAabb.top,
            bAfter = countAabb.bottom,
            y0 = v0,
            counts = sm,
            axis = "v",
            vNegBefore = vExistNeg.toDouble(),
            vPosBefore = vExistPos.toDouble(),
            vNegAfter = vNegOut,
            vPosAfter = vPosOut,
        )
        return countQuad to info
    }

    private fun fillSampleCounts(
        samples: List<VertEnergySample>,
        yOf: (VertEnergySample) -> Int,
        y0: Int,
        counts: DoubleArray,
    ): List<VertEnergySample> {
        if (counts.isEmpty() || samples.isEmpty()) return samples
        return samples.map { s ->
            val i = yOf(s) - y0
            val c = if (i in counts.indices) counts[i] else 0.0
            if (s.count == c) s else s.copy(count = c)
        }
    }

    private fun attachCountToTrace(
        trace: VertEnergyTrace,
        seed: Rect,
        countInfo: CountPullInfo,
        countRect: Rect,
    ): VertEnergyTrace {
        val y0 = countInfo.y0
        val counts = countInfo.counts
        return trace.copy(
            countPull = countInfo,
            finalCount = countRect,
            inside = fillSampleCounts(trace.inside, { seed.top + it.dy }, y0, counts),
            up = fillSampleCounts(trace.up, { seed.top - it.dy }, y0, counts),
            down = fillSampleCounts(trace.down, { seed.bottom + it.dy - 1 }, y0, counts),
            scanUp = fillSampleCounts(trace.scanUp, { seed.top - it.dy }, y0, counts),
            scanDown = fillSampleCounts(trace.scanDown, { seed.bottom + it.dy - 1 }, y0, counts),
            afterUp = fillSampleCounts(trace.afterUp, { seed.top - it.dy }, y0, counts),
            afterDown = fillSampleCounts(trace.afterDown, { seed.bottom + it.dy - 1 }, y0, counts),
        )
    }

    /** Attach count samples using v-offset from the seed center (oriented traces). */
    private fun attachCountToTraceV(
        trace: VertEnergyTrace,
        seedBh: Float,
        countInfo: CountPullInfo,
        countRect: Rect,
    ): VertEnergyTrace {
        val y0 = countInfo.y0
        val counts = countInfo.counts
        val half = seedBh * 0.5f
        return trace.copy(
            countPull = countInfo,
            finalCount = countRect,
            inside = fillSampleCounts(
                trace.inside, { (-half + it.dy + 0.5f).roundToInt() }, y0, counts,
            ),
            up = fillSampleCounts(
                trace.up, { (-half - it.dy).roundToInt() }, y0, counts,
            ),
            down = fillSampleCounts(
                trace.down, { (half + it.dy).roundToInt() }, y0, counts,
            ),
            scanUp = fillSampleCounts(
                trace.scanUp, { (-half - it.dy).roundToInt() }, y0, counts,
            ),
            scanDown = fillSampleCounts(
                trace.scanDown, { (half + it.dy).roundToInt() }, y0, counts,
            ),
            afterUp = fillSampleCounts(
                trace.afterUp, { (-half - it.dy).roundToInt() }, y0, counts,
            ),
            afterDown = fillSampleCounts(
                trace.afterDown, { (half + it.dy).roundToInt() }, y0, counts,
            ),
        )
    }

    private fun growOnEnergy(
        gray: Mat,
        seed: Rect,
        opts: ExpandOptions,
    ): AabbExpand {
        val maxFrac = opts.maxFrac
        val energyRatio = opts.energyRatio
        val enableJump = opts.enableJump
        val jumpFrac = opts.jumpFrac
        val retractClearFrac = opts.retractClearFrac
        val recordVertEnergy = opts.recordVertEnergy
        val freezeHorz = opts.freezeHorzDuringVert
        val vertKind = opts.vertEnergy
        val imgW = gray.cols(); val imgH = gray.rows()
        var l = seed.left; var t = seed.top; var r = seed.right; var b = seed.bottom
        val cap = max(1, (maxFrac * max(1, seed.height())).roundToInt())
        val gx = Mat(); val gy = Mat(); val eng = Mat()
        Imgproc.Sobel(gray, gx, CvType.CV_32F, 1, 0, 3)
        Imgproc.Sobel(gray, gy, CvType.CV_32F, 0, 1, 3)
        when (vertKind) {
            VertEnergyKind.MAGNITUDE -> Core.magnitude(gx, gy, eng)
            VertEnergyKind.GX, VertEnergyKind.XYCUT_GX ->
                Core.absdiff(gx, Scalar(0.0), eng)
        }
        gx.release(); gy.release()
        fun meanE(sl: Rect): Double {
            val c = clip(sl, imgW, imgH)
            if (c.width() <= 0 || c.height() <= 0) return 0.0
            val roi = eng.submat(c.top, c.bottom, c.left, c.right)
            val m = Core.mean(roi).`val`[0]
            roi.release()
            return m
        }
        val il = l + 2; val it = t + 2; val ir = r - 2; val ib = b - 2
        val base = if (ir > il && ib > it) meanE(Rect(il, it, ir, ib)) else meanE(seed)
        val thr = energyRatio * max(base, 1e-3)
        val upSamples = ArrayList<VertEnergySample>()
        val downSamples = ArrayList<VertEnergySample>()
        fun keepSample(dy: Int) = dy <= 80 || dy % 2 == 0
        fun record(into: ArrayList<VertEnergySample>, dy: Int, e: Double) {
            if (!recordVertEnergy || !keepSample(dy)) return
            val w = (r - l).coerceAtLeast(1)
            into.add(VertEnergySample(dy, e, e / max(base, 1e-3), w))
        }
        fun growOnce() {
            repeat(cap) {
                var grew = false
                if (t > 0) {
                    val e = meanE(Rect(l, t - 1, r, t))
                    if (e >= thr) {
                        t--
                        record(upSamples, seed.top - t, e)
                        grew = true
                    }
                }
                if (b < imgH) {
                    val e = meanE(Rect(l, b, r, b + 1))
                    if (e >= thr) {
                        b++
                        record(downSamples, b - seed.bottom, e)
                        grew = true
                    }
                }
                if (!freezeHorz) {
                    if (l > 0 && meanE(Rect(l - 1, t, l, b)) >= thr) { l--; grew = true }
                    if (r < imgW && meanE(Rect(r, t, r + 1, b)) >= thr) { r++; grew = true }
                }
                if (!grew) return
            }
        }
        fun growHorizontalOnce() {
            repeat(cap) {
                var grew = false
                if (l > 0 && meanE(Rect(l - 1, t, l, b)) >= thr) { l--; grew = true }
                if (r < imgW && meanE(Rect(r, t, r + 1, b)) >= thr) { r++; grew = true }
                if (!grew) return
            }
        }
        if (vertKind == VertEnergyKind.XYCUT_GX) {
            val cut = xycutOnProfile(eng, l, t, r, b, imgW, imgH, cap)
            t = cut[0]
            b = cut[1]
        } else {
            growOnce()
        }
        if (opts.vertPadFrac > 0f) {
            val extra = max(1, (opts.vertPadFrac * max(1, seed.height())).roundToInt())
            t = (t - extra).coerceAtLeast(0)
            b = (b + extra).coerceAtMost(imgH)
        }
        val padT = seed.top - t
        val padB = b - seed.bottom
        val hitVertCap = padT >= cap || padB >= cap
        val stopUp = if (vertKind == VertEnergyKind.XYCUT_GX) {
            "xycut"
        } else if (padT >= cap) {
            "cap"
        } else {
            "energy"
        }
        val stopDown = if (vertKind == VertEnergyKind.XYCUT_GX) {
            "xycut"
        } else if (padB >= cap) {
            "cap"
        } else {
            "energy"
        }
        val stopEnergyUp = if (t > 0) meanE(Rect(l, t - 1, r, t)) else 0.0
        val stopEnergyDown = if (b < imgH) meanE(Rect(l, b, r, b + 1)) else 0.0
        val lookAhead = max(VERT_ENERGY_LOOKAHEAD_MIN_PX, max(1, seed.height()))
        val inside = ArrayList<VertEnergySample>()
        val scanUp = ArrayList<VertEnergySample>()
        val scanDown = ArrayList<VertEnergySample>()
        val afterUp = ArrayList<VertEnergySample>()
        val afterDown = ArrayList<VertEnergySample>()
        if (recordVertEnergy) {
            // Stop-time width (before L/R jump). Every row, no 0.45 gate, no decimation.
            val w = (r - l).coerceAtLeast(1)
            val denom = max(base, 1e-3)
            fun strip(y: Int, dy: Int): VertEnergySample {
                val e = meanE(Rect(l, y, r, y + 1))
                return VertEnergySample(dy, e, e / denom, w)
            }
            var y = seed.top
            while (y < seed.bottom) {
                inside.add(strip(y, y - seed.top))
                y++
            }
            val upN = padT + lookAhead
            var k = 1
            y = seed.top - 1
            while (y >= 0 && k <= upN) {
                val s = strip(y, k)
                scanUp.add(s)
                if (k > padT) afterUp.add(s)
                y--
                k++
            }
            val downN = padB + lookAhead
            k = 1
            y = seed.bottom
            while (y < imgH && k <= downN) {
                val s = strip(y, k)
                scanDown.add(s)
                if (k > padB) afterDown.add(s)
                y++
                k++
            }
        }
        if (enableJump) {
            val floorL = l; val floorR = r
            val hgt = max(1, b - t)
            val jx = max(1, (jumpFrac * hgt).roundToInt())
            l = (l - jx).coerceAtLeast(0)
            r = (r + jx).coerceAtMost(imgW)
            val inText =
                (l < floorL && meanE(Rect(l, t, min(l + 1, r), b)) >= thr) ||
                    (r > floorR && meanE(Rect(max(r - 1, l), t, r, b)) >= thr)
            if (inText) {
                growHorizontalOnce()
            } else {
                // Retract L/R until boundary strip is on ink.
                while (l < floorL && meanE(Rect(l, t, min(l + 1, r), b)) < thr) l++
                while (r > floorR && meanE(Rect(max(r - 1, l), t, r, b)) < thr) r--
                // Pad left/right only so the box is clear of ink edge (not on first ink pixel).
                val clear = max(1, (retractClearFrac * max(1, b - t)).roundToInt())
                l = (l - clear).coerceAtLeast(0)
                r = (r + clear).coerceAtMost(imgW)
            }
        }
        val finalRect = clip(Rect(l, t, r, b), imgW, imgH)
        val trace = if (recordVertEnergy) {
            VertEnergyTrace(
                seed = Rect(seed),
                base = base,
                thr = thr,
                energyRatio = energyRatio,
                up = upSamples,
                down = downSamples,
                inside = inside,
                scanUp = scanUp,
                scanDown = scanDown,
                afterUp = afterUp,
                afterDown = afterDown,
                lookAhead = lookAhead,
                rawRoi = try {
                    captureEnergyRoi(gray, eng, seed, imgW, imgH)
                } catch (_: Throwable) {
                    null
                },
                stopUp = stopUp,
                stopDown = stopDown,
                stopEnergyUp = stopEnergyUp,
                stopEnergyDown = stopEnergyDown,
                final = Rect(finalRect),
            )
        } else {
            null
        }
        eng.release()
        val extraLook = if (recordVertEnergy) lookAhead else 0
        val (countRect, countInfo) = countPullbackVertical(gray, seed, finalRect, extraLook)
        val traceOut = if (trace != null) {
            attachCountToTrace(trace, seed, countInfo, countRect)
        } else {
            null
        }
        return AabbExpand(finalRect, hitVertCap, traceOut, countRect, countInfo)
    }

    /**
     * Copy deskewed gray + Sobel mag around the seed. Width = seed ± 25% seedW;
     * height = seed ± 1.5 seedH (clipped to the image).
     */
    private fun captureEnergyRoi(
        gray: Mat,
        eng: Mat,
        seed: Rect,
        imgW: Int,
        imgH: Int,
    ): EnergyPixelRoi? {
        val seedH = max(1, seed.height())
        val seedW = max(1, seed.width())
        val look = max(VERT_ENERGY_LOOKAHEAD_MIN_PX, (VERT_ENERGY_RAW_LOOK_FRAC * seedH).roundToInt())
        val hPad = max(16, (VERT_ENERGY_RAW_HPAD_FRAC * seedW).roundToInt())
        val l = (seed.left - hPad).coerceAtLeast(0)
        val r = (seed.right + hPad).coerceAtMost(imgW)
        val t = (seed.top - look).coerceAtLeast(0)
        val b = (seed.bottom + look).coerceAtMost(imgH)
        val w = r - l
        val h = b - t
        if (w < 2 || h < 2) return null
        val grayU8 = ByteArray(w * h)
        val sobelU16 = ByteArray(w * h * 2)
        val gpix = ByteArray(1)
        val epix = FloatArray(1)
        var gi = 0
        var si = 0
        for (y in t until b) {
            for (x in l until r) {
                gray.get(y, x, gpix)
                grayU8[gi++] = gpix[0]
                eng.get(y, x, epix)
                val v = (epix[0] * VERT_ENERGY_RAW_SCALE).roundToInt().coerceIn(0, 65535)
                sobelU16[si++] = (v and 0xff).toByte()
                sobelU16[si++] = ((v shr 8) and 0xff).toByte()
            }
        }
        return EnergyPixelRoi(
            l = l,
            t = t,
            w = w,
            h = h,
            look = look,
            hPad = hPad,
            sobelScale = VERT_ENERGY_RAW_SCALE,
            grayU8Zlib = HeatmapU8Dump.zlibCompress(grayU8),
            sobelU16leZlib = HeatmapU8Dump.zlibCompress(sobelU16),
        )
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
        val eq = Mat()
        Imgproc.equalizeHist(gray, eq)
        val edges = Mat()
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
                var best = 0.0
                var ux = 1f
                var uy = 0f
                for (i in 0 until 4) {
                    val j = (i + 1) % 4
                    val dx = (p[j * 2] - p[i * 2]).toDouble()
                    val dy = (p[j * 2 + 1] - p[i * 2 + 1]).toDouble()
                    val len = hypot(dx, dy)
                    if (len > best) {
                        best = len
                        if (len > 1e-3) {
                            ux = (dx / len).toFloat()
                            uy = (dy / len).toFloat()
                        }
                    }
                }
                if (best < 2.0) return null
                val vx = -uy
                val vy = ux
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
