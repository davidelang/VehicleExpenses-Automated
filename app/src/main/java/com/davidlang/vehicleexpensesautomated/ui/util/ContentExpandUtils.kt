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
    init {
        System.loadLibrary("native_ocr")
    }

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
     * G-- / rot-G-- UV expand via [OrientedBox.fromQuad] (u ≈ horizontal, +u right, +v down).
     * Height × (1+2v) along ±v; independent L/R pads along ±u as
     * [leftFrac]/[rightFrac] × **seed** vSpan (text height), not expanded height.
     * Does not re-run minAreaRect / force long-axis = u (that broke tall seeds and L/R).
     * Orange = same with a small extra on both U ends (caller).
     */
    fun calculatedOrientedUv(
        seed: OrientedQuad,
        v: Float,
        leftFrac: Float,
        rightFrac: Float,
    ): OrientedQuad {
        val box = OrientedBox.fromQuad(seed) ?: return seed
        val bh = box.vSpan().coerceAtLeast(2f)
        val leftPad = leftFrac * bh
        val rightPad = rightFrac * bh
        val newH = bh * (1f + 2f * v)
        val vMid = 0.5f * (box.v0 + box.v1)
        val halfNewH = 0.5f * newH
        return OrientedBox(
            box.cx, box.cy, box.ux, box.uy, box.vx, box.vy,
            box.u0 - leftPad,
            box.u1 + rightPad,
            vMid - halfNewH,
            vMid + halfNewH,
        ).toQuad()
    }

    /**
     * Clip one oriented [seed] to one convex [crop]. Both are four corners.
     * Intersect them. The result keeps the seed u/v axes and uses only the u and v
     * span of that intersection, so it is still four corners. A seed already inside
     * the crop is unchanged. No overlap returns null. Does not reorder with [quadPtsTlTrBrBl].
     */
    fun clipOrientedSeedToConvexQuad(seed: OrientedQuad, crop: OrientedQuad): OrientedQuad? {
        val seedPts = seed.copyPts()
        val cropPts = crop.copyPts()
        if (quadCornersInside(cropPts, seedPts)) return seed
        val box = OrientedBox.fromQuad(seed) ?: return null
        val inter = convexQuadIntersect(seedPts, cropPts)
        if (inter.size < 3) return null
        var u0 = Float.POSITIVE_INFINITY
        var u1 = Float.NEGATIVE_INFINITY
        var v0 = Float.POSITIVE_INFINITY
        var v1 = Float.NEGATIVE_INFINITY
        for (i in inter.indices) {
            val (u, v) = box.proj(inter[i].first, inter[i].second)
            if (u < u0) u0 = u
            if (u > u1) u1 = u
            if (v < v0) v0 = v
            if (v > v1) v1 = v
        }
        if (u1 - u0 < 1e-2f || v1 - v0 < 1e-2f) return null
        fun c(u: Float, v: Float): FloatArray = floatArrayOf(
            box.cx + u * box.ux + v * box.vx,
            box.cy + u * box.uy + v * box.vy,
        )
        val a = c(u0, v0)
        val b = c(u1, v0)
        val d = c(u1, v1)
        val e = c(u0, v1)
        return OrientedQuad(floatArrayOf(a[0], a[1], b[0], b[1], d[0], d[1], e[0], e[1]))
    }

    /**
     * One oriented quad in [crop]'s u/v frame that covers every seed clipped to [crop].
     * Each [seeds] entry is clipped with [clipOrientedSeedToConvexQuad]; null clips are skipped.
     * No overlap with [crop] returns null.
     */
    fun unionOrientedSeedsClippedToCrop(seeds: List<OrientedQuad>, crop: OrientedQuad): OrientedQuad? {
        val cropBox = OrientedBox.fromQuad(crop) ?: return null
        var u0 = Float.POSITIVE_INFINITY
        var u1 = Float.NEGATIVE_INFINITY
        var v0 = Float.POSITIVE_INFINITY
        var v1 = Float.NEGATIVE_INFINITY
        var any = false
        for (seed in seeds) {
            val clip = clipOrientedSeedToConvexQuad(seed, crop) ?: continue
            any = true
            for (i in 0 until 4) {
                val (u, v) = cropBox.proj(clip.pts[i * 2], clip.pts[i * 2 + 1])
                if (u < u0) u0 = u
                if (u > u1) u1 = u
                if (v < v0) v0 = v
                if (v > v1) v1 = v
            }
        }
        if (!any) return null
        u0 = max(u0, cropBox.u0)
        u1 = min(u1, cropBox.u1)
        v0 = max(v0, cropBox.v0)
        v1 = min(v1, cropBox.v1)
        if (u1 - u0 < 1e-2f || v1 - v0 < 1e-2f) return null
        return OrientedBox(
            cropBox.cx, cropBox.cy, cropBox.ux, cropBox.uy, cropBox.vx, cropBox.vy,
            u0, u1, v0, v1,
        ).toQuad()
    }

    private fun quadPairs(pts: FloatArray): Array<Pair<Float, Float>> =
        Array(4) { i -> pts[i * 2] to pts[i * 2 + 1] }

    private fun polyPositive(poly: Array<Pair<Float, Float>>): Boolean {
        var a = 0.0
        for (i in poly.indices) {
            val j = (i + 1) % poly.size
            a += poly[i].first.toDouble() * poly[j].second - poly[j].first.toDouble() * poly[i].second
        }
        return a >= 0.0
    }

    /** Signed distance to the left of directed edge AB. [positive] crop winding keeps the interior on the left. */
    private fun halfPlaneInside(
        ax: Float, ay: Float, bx: Float, by: Float,
        px: Float, py: Float, positive: Boolean,
    ): Boolean {
        val ex = bx - ax
        val ey = by - ay
        val len = hypot(ex.toDouble(), ey.toDouble()).toFloat().coerceAtLeast(1e-6f)
        val signed = (ex * (py - ay) - ey * (px - ax)) / len
        return if (positive) signed >= -0.05f else signed <= 0.05f
    }

    private fun quadCornersInside(crop: FloatArray, seed: FloatArray): Boolean {
        val clip = quadPairs(crop)
        val positive = polyPositive(clip)
        for (i in 0 until 4) {
            val x = seed[i * 2]
            val y = seed[i * 2 + 1]
            for (e in clip.indices) {
                val a = clip[e]
                val b = clip[(e + 1) % clip.size]
                if (!halfPlaneInside(a.first, a.second, b.first, b.second, x, y, positive)) return false
            }
        }
        return true
    }

    /** Sutherland–Hodgman. Vertices are seed corners inside the crop and edge crossings. */
    private fun convexQuadIntersect(subject: FloatArray, clipPts: FloatArray): List<Pair<Float, Float>> {
        val clip = quadPairs(clipPts)
        val positive = polyPositive(clip)
        var output = quadPairs(subject).toMutableList()
        for (i in clip.indices) {
            if (output.isEmpty()) return emptyList()
            val input = output
            output = ArrayList()
            val ax = clip[i].first
            val ay = clip[i].second
            val bx = clip[(i + 1) % clip.size].first
            val by = clip[(i + 1) % clip.size].second
            var sx = input.last().first
            var sy = input.last().second
            for (e in input) {
                val ex = e.first
                val ey = e.second
                val eIn = halfPlaneInside(ax, ay, bx, by, ex, ey, positive)
                val sIn = halfPlaneInside(ax, ay, bx, by, sx, sy, positive)
                if (eIn) {
                    if (!sIn) edgeCross(sx, sy, ex, ey, ax, ay, bx, by)?.let { output.add(it) }
                    output.add(ex to ey)
                } else if (sIn) {
                    edgeCross(sx, sy, ex, ey, ax, ay, bx, by)?.let { output.add(it) }
                }
                sx = ex
                sy = ey
            }
        }
        return output
    }

    private fun edgeCross(
        x1: Float, y1: Float, x2: Float, y2: Float,
        x3: Float, y3: Float, x4: Float, y4: Float,
    ): Pair<Float, Float>? {
        val den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(den) < 1e-6f) return null
        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den
        return (x1 + t * (x2 - x1)) to (y1 + t * (y2 - y1))
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
     * Warp [quad] into [dest] at **native** u/v size (no dest-height floor).
     * Pivot BL, flatten BL→BR to +x. INTER_CUBIC, BORDER_CONSTANT black.
     * If [dest] is already sized, that size is used.
     */
    fun warpQuadToHorizontalStrip(
        gray: Mat,
        quad: OrientedQuad,
        dest: Mat,
        targetH: Int = 0,
        maxW: Int = 0,
    ): Boolean {
        if (gray.empty()) return false
        val order = quad.pts
        if (order.size < 8) return false
        val wSrc = hypot(
            (order[2] - order[0]).toDouble(),
            (order[3] - order[1]).toDouble(),
        ).toFloat().coerceAtLeast(1f)
        val hSrc = hypot(
            (order[6] - order[0]).toDouble(),
            (order[7] - order[1]).toDouble(),
        ).toFloat().coerceAtLeast(1f)
        val nativeW = wSrc.roundToInt().coerceAtLeast(1).let { if (maxW > 0) it.coerceAtMost(maxW) else it }
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

    /**
     * Canonical image-y-down order TL,TR,BR,BL.
     * BL = two smallest-x, then largest y; BR = cyclic neighbor with larger x.
     */
    fun quadPtsTlTrBrBl(p: FloatArray): FloatArray? {
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

    /** OCR horiz_pad extra width per side as a fraction of walked AABB height. */
    const val SEG7_HORIZ_PAD_H_FRAC = 0.05f

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

    /**
     * Oriented box in a fixed u/v frame (u = edge closest to horizontal; +u right, +v down).
     * Extending u0/u1/v0/v1 translates that side along its normal and leaves the opposite side put.
     */
    internal class OrientedBox(
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

        fun toQuad(): OrientedQuad {
            fun c(u: Float, v: Float) = floatArrayOf(
                cx + u * ux + v * vx,
                cy + u * uy + v * vy,
            )
            val a = c(u0, v0)
            val b = c(u1, v0)
            val d = c(u1, v1)
            val e = c(u0, v1)
            val raw = floatArrayOf(a[0], a[1], b[0], b[1], d[0], d[1], e[0], e[1])
            return OrientedQuad(quadPtsTlTrBrBl(raw) ?: raw)
        }

        companion object {
            fun fromQuad(q: OrientedQuad): OrientedBox? {
                val p = q.pts
                if (p.size < 8) return null
                // u = edge closest to horizontal (|atan2| folded to [0, 90°]); tie → longer.
                // v = +90° from u, flipped so vy ≥ 0 (v1 = lower flatter side).
                // Then if ux < 0: flip u and swap-negate u extents so ux ≥ 0 (no L-R mirror).
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
                if (ux < 0f) {
                    ux = -ux
                    uy = -uy
                    val t = u0
                    u0 = -u1
                    u1 = -t
                }
                return OrientedBox(cx, cy, ux, uy, vx, vy, u0, u1, v0, v1)
            }
        }
    }

    internal fun clip(r: Rect, w: Int, h: Int): Rect {
        val l = r.left.coerceIn(0, w - 1)
        val t = r.top.coerceIn(0, h - 1)
        val rr = r.right.coerceIn(l + 1, w)
        val b = r.bottom.coerceIn(t + 1, h)
        return Rect(l, t, rr, b)
    }
}
