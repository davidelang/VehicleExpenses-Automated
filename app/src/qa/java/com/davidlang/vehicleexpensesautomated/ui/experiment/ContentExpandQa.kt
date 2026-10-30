package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.graphics.Rect
import com.davidlang.vehicleexpensesautomated.ui.util.ContentExpandUtils
import com.davidlang.vehicleexpensesautomated.ui.util.NativeImageUtils
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.AabbExpand
import com.davidlang.vehicleexpensesautomated.ui.util.CountPullInfo
import com.davidlang.vehicleexpensesautomated.ui.util.ExpandOptions
import com.davidlang.vehicleexpensesautomated.ui.util.ContentExpandUtils.Mode
import com.davidlang.vehicleexpensesautomated.ui.util.ContentExpandUtils.OrientedBox
import com.davidlang.vehicleexpensesautomated.ui.util.OrientedExpand
import com.davidlang.vehicleexpensesautomated.ui.util.ContentExpandUtils.OrientedQuad
import com.davidlang.vehicleexpensesautomated.ui.util.SeedInkProbe
import com.davidlang.vehicleexpensesautomated.ui.util.SeedInkThr
import com.davidlang.vehicleexpensesautomated.ui.util.Seg7Expand
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import com.davidlang.vehicleexpensesautomated.ui.util.countPullbackOrientedNative
import com.davidlang.vehicleexpensesautomated.ui.util.energyAabbRetractNative
import com.davidlang.vehicleexpensesautomated.ui.util.energyAabbTightNative
import com.davidlang.vehicleexpensesautomated.ui.util.energyOrientExpandNative
import com.davidlang.vehicleexpensesautomated.ui.util.energyOrientRetractNative
import com.davidlang.vehicleexpensesautomated.ui.util.energyOrientTightNative
import com.davidlang.vehicleexpensesautomated.ui.util.grayAabbRetractNative
import com.davidlang.vehicleexpensesautomated.ui.util.grayAabbTightNative
import com.davidlang.vehicleexpensesautomated.ui.util.probeSeedInk
import com.davidlang.vehicleexpensesautomated.ui.util.withPts
import com.davidlang.vehicleexpensesautomated.ui.util.includeBox
import com.davidlang.vehicleexpensesautomated.ui.util.localAabb
import com.davidlang.vehicleexpensesautomated.ui.util.maxPoke
import com.davidlang.vehicleexpensesautomated.ui.util.cornersInside
import com.davidlang.vehicleexpensesautomated.ui.util.inkSweepBuf
import com.davidlang.vehicleexpensesautomated.ui.util.parseInkSweeps
import com.davidlang.vehicleexpensesautomated.ui.util.expandAabb7seg
import com.davidlang.vehicleexpensesautomated.ui.util.expandEnergyAabbMany
import com.davidlang.vehicleexpensesautomated.ui.util.expandEnergyAabbExpand
import com.davidlang.vehicleexpensesautomated.ui.util.OrientedExpandNative

fun ContentExpandUtils.expand(
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
 * P-style interior-energy expand that does **not** require axis-aligned boxes.
 * Grows ±u (text ends) and ±v (above/below) in the minAreaRect frame of [seed].
 */
fun ContentExpandUtils.expandOrientedDiagnose(
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
                    OrientedExpandNative(
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
                    OrientedExpandNative(
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

/**
 * G-style calculated pad on an oriented seed: height × (1+2v), each long-side
 * end padded by [horiz] × new height. Same geometry as AABB blues.
 */
fun ContentExpandUtils.calculatedOriented(seed: OrientedQuad, v: Float, horiz: Float = 0.5f): OrientedQuad {
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
fun ContentExpandUtils.pruneOrientedQuads(
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

private fun ContentExpandUtils.minAreaFromQuad(q: OrientedQuad): RotatedRect? {
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

private fun ContentExpandUtils.orientedFromCenter(
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
    val raw = floatArrayOf(
        c00[0], c00[1], c10[0], c10[1], c11[0], c11[1], c01[0], c01[1],
    )
    return OrientedQuad(quadPtsTlTrBrBl(raw) ?: raw)
}

/** G-style calculated AABB: height × (1+2v), each side horiz × newH. */
fun ContentExpandUtils.calculatedAabb(seed: Rect, v: Float, horiz: Float, imgW: Int, imgH: Int): Rect =
    ratioExpand(seed, v, horiz, imgW, imgH)

fun ContentExpandUtils.poisonStatsBuf(n: Int): IntArray = IntArray(1 + n.coerceAtLeast(0) * 256)

fun ContentExpandUtils.expandGrayAabbTight(
    gray: Mat, uv: Mat?, seeds: List<Rect>,
    scratch: Mat? = null, combine: Mat? = null,
    overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
): List<Seg7Expand> = expandAabb7seg(
    gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
    NativeImageUtils::grayAabbTightNative,
)

fun ContentExpandUtils.expandGrayAabbRetract(
    gray: Mat, uv: Mat?, seeds: List<Rect>,
    scratch: Mat? = null, combine: Mat? = null,
    overlayY: Mat? = null, overlayUv: Mat? = null, poisonStats: IntArray? = null,
): List<Seg7Expand> = expandAabb7seg(
    gray, uv, seeds, scratch, combine, overlayY, overlayUv, poisonStats,
    NativeImageUtils::grayAabbRetractNative,
)

/**
 * Pad [box] by `k`×`s` on each tip, clamped to remaining
 * [SEG7_VERT_CAP_FRAC]×original-[seed] height per side (walk already used
 * some of that budget). Frozen sides (no walk) still get the pad.
 * k=0 official: [k] ≤ 0 → 0 pad (walk-stop T/B). k>0 extra: k>0 keeps
 * `max(1, (k×s).roundToInt())`.
 */
fun ContentExpandUtils.padVertByStrokes(
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

private fun ContentExpandUtils.expandEnergyOrientMany(
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
        val nativeExp = OrientedExpandNative(
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

fun ContentExpandUtils.expandEnergyOrientTight(
    gray: Mat, seeds: List<OrientedQuad>,
): List<OrientedExpand> = expandEnergyOrientMany(
    gray, seeds, NativeImageUtils::energyOrientTightNative,
)

fun ContentExpandUtils.expandEnergyOrientRetract(
    gray: Mat, seeds: List<OrientedQuad>,
): List<OrientedExpand> = expandEnergyOrientMany(
    gray, seeds, NativeImageUtils::energyOrientRetractNative,
)

/** k-pad along `±v`, remaining [SEG7_VERT_CAP_FRAC]×seed `bh` per side. Frozen sides still pad. */
fun ContentExpandUtils.padOrientedByStrokes(
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
fun ContentExpandUtils.lookInkJumpFarQuad(
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
fun ContentExpandUtils.padOrientedU(q: OrientedQuad, horizFrac: Float, seed: OrientedQuad? = null): OrientedQuad {
    val frame = OrientedBox.fromQuad(seed ?: q) ?: return q
    val box = if (seed != null) frame.withPts(q.pts) else frame
    val pad = horizFrac * frame.vSpan().coerceAtLeast(1f)
    return OrientedBox(
        frame.cx, frame.cy, frame.ux, frame.uy, frame.vx, frame.vy,
        box.u0 - pad, box.u1 + pad, box.v0, box.v1,
    ).toQuad()
}

fun ContentExpandUtils.expandDiagnose(
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
fun ContentExpandUtils.expandDiagnoseChroma(
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

fun ContentExpandUtils.expandEnergyAabbTight(
    gray: Mat, uv: Mat?, seeds: List<Rect>,
): List<AabbExpand> = expandEnergyAabbMany(gray, uv, seeds, NativeImageUtils::energyAabbTightNative)

fun ContentExpandUtils.expandEnergyAabbRetract(
    gray: Mat, uv: Mat?, seeds: List<Rect>,
): List<AabbExpand> = expandEnergyAabbMany(gray, uv, seeds, NativeImageUtils::energyAabbRetractNative)

/**
 * Many-seed AABB energy. INTERIOR_ENERGY live path uses Expand recipe.
 * Null when traces are on (skip JNI). Native fail → seed boxes.
 */
fun ContentExpandUtils.expandDiagnoseMany(
    gray: Mat,
    uv: Mat?,
    seeds: List<Rect>,
    mode: Mode,
    opts: ExpandOptions,
): List<AabbExpand>? {
    if (mode != Mode.INTERIOR_ENERGY || opts.recordVertEnergy) return null
    return expandEnergyAabbExpand(gray, uv, seeds)
}

fun ContentExpandUtils.expand(
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

/** horiz = fraction of newH (walked height when v=0) per side. */
private fun ContentExpandUtils.ratioExpand(seed: Rect, v: Float, horiz: Float, imgW: Int, imgH: Int): Rect {
    val hgt = max(1, seed.height())
    val nt = (seed.top - v * hgt).roundToInt().coerceIn(0, imgH - 1)
    val nb = (seed.bottom + v * hgt).roundToInt().coerceIn(nt + 1, imgH)
    val newH = nb - nt
    val hp = (horiz * newH).roundToInt()
    val nl = (seed.left - hp).coerceIn(0, imgW - 1)
    val nr = (seed.right + hp).coerceIn(nl + 1, imgW)
    return Rect(nl, nt, nr, nb)
}

private fun ContentExpandUtils.u8Crop(src: Mat?, h: Int, w: Int): Mat? {
    if (src == null || src.empty() || src.type() != CvType.CV_8UC1) return null
    if (src.rows() < h || src.cols() < w) return null
    return src.submat(0, h, 0, w)
}

private fun ContentExpandUtils.dualInkMask(gray: Mat): Mat? {
    val h = gray.rows()
    val w = gray.cols()
    val bSet = NativePaddleEngine.bufferSetB
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

private fun ContentExpandUtils.growOnMask(
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

private fun ContentExpandUtils.growOnEdges(
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
    val bSet = NativePaddleEngine.bufferSetB
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

private val seedInkKindNames = arrayOf(
    "gt", "band", "union", "cband", "cunion", "pick", "flood", "cpick", "cflood",
    "flaty", "fpick", "fflood", "gpick", "gflood", "tpick", "tflood",
)

private fun ContentExpandUtils.unpackSeedInkProbe(
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
        if (meta == null || meta.size < 12 || jpeg == null) continue
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
                score = meta[11],
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
fun ContentExpandUtils.probeSeedInk(
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
fun ContentExpandUtils.probeSeedInkUv(
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

private fun ContentExpandUtils.longAngleDeltaDeg(a: Float, b: Float): Float {
    var d = abs(a - b) % 180f
    if (d > 90f) d = 180f - d
    return d
}

private fun ContentExpandUtils.localInterArea(a: OrientedBox, loc: FloatArray): Float {
    val iu0 = max(a.u0, loc[0])
    val iu1 = min(a.u1, loc[1])
    val iv0 = max(a.v0, loc[2])
    val iv1 = min(a.v1, loc[3])
    val w = (iu1 - iu0).coerceAtLeast(0f)
    val h = (iv1 - iv0).coerceAtLeast(0f)
    return w * h
}

private fun ContentExpandUtils.shouldGrowOriented(a: OrientedBox, b: OrientedBox, pokePx: Float): Boolean {
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

private fun ContentExpandUtils.mergeOrientedSimilarAndPoke(boxes: MutableList<OrientedBox>, pokePx: Float) {
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
