package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Rect
import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared recognition-buffer feed: 40 px of content in a 48-high buffer, one isotropic scale.
 * Left and top margin are 4 dest px of real source. Slack is the bottom and right.
 * Pixel op is [scaleIsotropicNoPartial]. Geom integer-k and mild feeds stay separate.
 */
object RecBufferFeed {
    const val DEFAULT_BORDER_PX = 4
    const val DEFAULT_REC_H = 48
    /** One rec canvas width; clip only here (not a squeeze). */
    const val DEFAULT_MAX_W = NativePaddleEngine.REC_CANVAS_W

    data class Result(
        val contentScale: Float,
        val sourcePadPx: Int,
        val recCropId: Int,
        val targetW: Int,
        val targetH: Int,
        val integerK: Int = 0,
        val mildS: Float = 0f,
    )

    /**
     * 40 px of content in a 48-high canvas. One scale. Left/top margin is [borderPx] dest px
     * of source. Slack is the bottom and right. Caller releases [Result.recCropId].
     */
    fun feedSourceBorderLetterbox(
        srcMat: Mat,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        borderPx: Int = DEFAULT_BORDER_PX,
    ): Result = feedContent40(
        srcMat, srcLeft, srcTop, srcRight, srcBottom, recBuffer,
        DEFAULT_REC_H, recBuffer.p.width.coerceAtMost(DEFAULT_MAX_W), borderPx,
    )

    /**
     * 40 px of content in a 48-high strip ([targetH] clamped to 48). One scale.
     * [destW] is even, `border + round(cropW * s)`, capped by [maxW]. A width miss
     * lowers [s] (content shorter than 40) and logs CropScale. No center crop.
     *
     * @return [Result] including [Result.recCropId] that caller must [BufferSet.Slice.release]
     */
    fun feedSourceBorderHeightStrip(
        srcMat: Mat,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        targetH: Int = DEFAULT_REC_H,
        maxW: Int = DEFAULT_MAX_W,
        borderPx: Int = DEFAULT_BORDER_PX,
    ): Result = feedContent40(
        srcMat, srcLeft, srcTop, srcRight, srcBottom, recBuffer,
        targetH, maxW, borderPx,
    )

    fun feedSourceBorderHeightStrip(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        targetH: Int = DEFAULT_REC_H,
        maxW: Int = DEFAULT_MAX_W,
        borderPx: Int = DEFAULT_BORDER_PX,
    ): Result {
        val l = rect.left.coerceIn(0, imgW - 1)
        val t = rect.top.coerceIn(0, imgH - 1)
        val rr = rect.right.coerceIn(l + 1, imgW)
        val bb = rect.bottom.coerceIn(t + 1, imgH)
        return feedSourceBorderHeightStrip(
            workspace.p.mat,
            l, t, rr, bb,
            recBuffer,
            targetH, maxW, borderPx,
        )
    }

    /**
     * Geom-only integer-k downsample: source is k×48 by k×destW, dest is destW×48.
     * k = max(1, round(boxH/40)); 4k source pad above and left of the blue box;
     * remainder pad right/bottom. Origin clamps to 0. Drop k until k×48 fits.
     * BufferSet crops only. Dest is the full 48 (no 40-tall hole).
     * [extraLeftDestPx] grows only the left (another dest px × k of source);
     * [extraRightDestPx] grows only the right (another dest px × k of source);
     * height and the opposite side of the stored crop stay put.
     * Does not change [feedSourceBorderHeightStrip].
     */
    fun feedIntegerKContent40(
        srcMat: Mat,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
    ): Result? = downsampleIntegerKSource(srcMat, recBuffer, maxW)

    fun feedIntegerKContent40(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
        extraLeftDestPx: Int = 0,
        extraRightDestPx: Int = 0,
    ): Result? {
        val l = rect.left.coerceIn(0, imgW - 1)
        val t = rect.top.coerceIn(0, imgH - 1)
        val rr = rect.right.coerceIn(l + 1, imgW)
        val bb = rect.bottom.coerceIn(t + 1, imgH)
        return feedIntegerKFromWorkspace(
            workspace, l, t, rr, bb, recBuffer, maxW, extraLeftDestPx, extraRightDestPx,
        )
    }

    fun feedIntegerKContent40LeftPad(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
    ): Result? = feedIntegerKContent40(
        workspace, rect, imgW, imgH, recBuffer, maxW, extraLeftDestPx = 4,
    )

    fun feedIntegerKContent40RightPad(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
    ): Result? = feedIntegerKContent40(
        workspace, rect, imgW, imgH, recBuffer, maxW, extraRightDestPx = 4,
    )

    /**
     * Geom-only isotropic s = 40/boxH. Dest canvas 48. Source sized as dest 49 / W+1
     * at the same s; custom sampler writes dest rows 0..47 and destW columns only.
     * Extra source: bottom/right. BufferSet crops only.
     * [extraLeftDestPx] grows only the left (another dest px / s of source);
     * [extraRightDestPx] grows only the right (another dest px / s of source);
     * height and the opposite side of the stored crop stay put.
     */
    fun feedMildNoPartial(
        srcMat: Mat,
        recBuffer: BufferSet,
        s: Float,
        destWHint: Int,
        maxW: Int = DEFAULT_MAX_W,
    ): Result? = downsampleMildSource(srcMat, recBuffer, s, destWHint, maxW)

    fun feedMildNoPartial(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
        extraLeftDestPx: Int = 0,
        extraRightDestPx: Int = 0,
    ): Result? {
        val l = rect.left.coerceIn(0, imgW - 1)
        val t = rect.top.coerceIn(0, imgH - 1)
        val rr = rect.right.coerceIn(l + 1, imgW)
        val bb = rect.bottom.coerceIn(t + 1, imgH)
        return feedMildFromWorkspace(
            workspace, l, t, rr, bb, recBuffer, maxW, extraLeftDestPx, extraRightDestPx,
        )
    }

    fun feedMildNoPartialLeftPad(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
    ): Result? = feedMildNoPartial(
        workspace, rect, imgW, imgH, recBuffer, maxW, extraLeftDestPx = 4,
    )

    fun feedMildNoPartialRightPad(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
    ): Result? = feedMildNoPartial(
        workspace, rect, imgW, imgH, recBuffer, maxW, extraRightDestPx = 4,
    )

    private fun evenAtLeast2(n: Int): Int {
        val v = n.coerceAtLeast(2)
        return if (v % 2 == 0) v else v + 1
    }

    private fun evenDown(n: Int): Int {
        val e = if (n % 2 == 0) n else n - 1
        return e.coerceAtLeast(2)
    }

    private fun evenOrigin(v: Int): Int = (v.coerceAtLeast(0) / 2) * 2

    private fun tryCreateCrop(bs: BufferSet, x: Int, y: Int, w: Int, h: Int): Int? {
        if (w < 2 || h < 2) return null
        return try {
            val id = bs.createCrop(x, y, w, h)
            val sl = bs.c[id]
            if (sl.width < 2 || sl.height < 2 || sl.mat.empty()) {
                sl.release()
                null
            } else {
                id
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun downsampleIntegerKSource(
        srcMat: Mat,
        recBuffer: BufferSet,
        maxW: Int,
    ): Result? {
        return try {
            val canvasH = DEFAULT_REC_H.coerceAtMost(recBuffer.p.height)
            val srcH = srcMat.rows()
            val srcW = srcMat.cols()
            if (srcH < 2 || srcW < 2 || canvasH < 2) return null
            var k = if (srcH % canvasH == 0) srcH / canvasH else 0
            if (k < 1) k = maxOf(1, (srcH / canvasH.toFloat()).roundToInt())
            var destW = evenDown(srcW / k).coerceAtMost(maxW).coerceAtMost(recBuffer.p.width)
            recBuffer.p.clear()
            val recCropId = tryCreateCrop(recBuffer, 0, 0, destW, canvasH) ?: return null
            val dest = recBuffer.c[recCropId]
            val outW = dest.width
            val outH = dest.height
            if (k == 1 && srcW == outW && srcH == outH) {
                srcMat.copyTo(dest.mat)
            } else {
                Imgproc.resize(
                    srcMat, dest.mat,
                    Size(outW.toDouble(), outH.toDouble()),
                    0.0, 0.0, Imgproc.INTER_AREA,
                )
            }
            Result(1f / k, 4 * k, recCropId, outW, outH, integerK = k)
        } catch (_: Exception) {
            null
        }
    }

    private fun feedIntegerKFromWorkspace(
        workspace: BufferSet,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        maxW: Int,
        extraLeftDestPx: Int = 0,
        extraRightDestPx: Int = 0,
    ): Result? {
        return try {
            val matW = workspace.width
            val matH = workspace.height
            val boxW = (srcRight - srcLeft).coerceAtLeast(1)
            val boxH = (srcBottom - srcTop).coerceAtLeast(1)
            val canvasH = DEFAULT_REC_H.coerceAtMost(recBuffer.p.height)
            if (matW < 2 || matH < 2 || canvasH < 2) return null
            val extraL = extraLeftDestPx.coerceAtLeast(0)
            val extraR = extraRightDestPx.coerceAtLeast(0)
            var k = maxOf(1, (boxH / 40f).roundToInt())
            var srcId: Int? = null
            while (k >= 1) {
                var destW = evenAtLeast2(4 + extraL + extraR + (boxW.toFloat() / k).roundToInt())
                    .coerceAtMost(maxW.coerceAtMost(recBuffer.p.width))
                destW = evenDown(destW)
                var srcW = k * destW
                val srcH = k * canvasH
                if (srcH > matH) {
                    k--
                    continue
                }
                while (srcW > matW && destW >= 4) {
                    destW -= 2
                    srcW = k * destW
                }
                if (srcW > matW || destW < 2) {
                    k--
                    continue
                }
                var sL = evenOrigin((srcLeft - (4 + extraL) * k).coerceAtLeast(0))
                var sT = evenOrigin((srcTop - 4 * k).coerceAtLeast(0))
                if (extraR == 0 && sL + srcW > matW) sL = 0
                if (sT + srcH > matH) sT = 0
                if (sL + srcW > matW || sT + srcH > matH) {
                    k--
                    continue
                }
                srcId = tryCreateCrop(workspace, sL, sT, srcW, srcH)
                if (srcId != null) break
                k--
            }
            val cropId = srcId ?: return null
            val src = workspace.c[cropId]
            val fed = downsampleIntegerKSource(src.mat, recBuffer, maxW)
            src.release()
            fed
        } catch (_: Exception) {
            null
        }
    }

    private fun downsampleMildSource(
        srcMat: Mat,
        recBuffer: BufferSet,
        s: Float,
        destWHint: Int,
        maxW: Int,
    ): Result? {
        if (s <= 0f) return null
        return try {
            val canvasH = DEFAULT_REC_H.coerceAtMost(recBuffer.p.height)
            if (srcMat.cols() < 2 || srcMat.rows() < 2 || canvasH < 2) return null
            val destW = evenDown(evenAtLeast2(destWHint).coerceAtMost(maxW).coerceAtMost(recBuffer.p.width))
            recBuffer.p.clear()
            val recCropId = tryCreateCrop(recBuffer, 0, 0, destW, canvasH) ?: return null
            val dest = recBuffer.c[recCropId]
            scaleIsotropicNoPartial(srcMat, dest.mat, s)
            Result(s, (4f / s).roundToInt().coerceAtLeast(0), recCropId, dest.width, dest.height, mildS = s)
        } catch (_: Exception) {
            null
        }
    }

    private fun feedMildFromWorkspace(
        workspace: BufferSet,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        maxW: Int,
        extraLeftDestPx: Int = 0,
        extraRightDestPx: Int = 0,
    ): Result? {
        return try {
            val matW = workspace.width
            val matH = workspace.height
            val boxW = (srcRight - srcLeft).coerceAtLeast(1)
            val boxH = (srcBottom - srcTop).coerceAtLeast(1)
            val s = 40f / boxH
            if (s <= 0f || matW < 2 || matH < 2) return null
            val extraL = extraLeftDestPx.coerceAtLeast(0)
            val extraR = extraRightDestPx.coerceAtLeast(0)
            val destW = evenDown(
                evenAtLeast2(4 + extraL + extraR + (boxW * s).roundToInt())
                    .coerceAtMost(maxW.coerceAtMost(recBuffer.p.width)),
            )
            var srcH = ceil(49.0 / s).toInt().coerceAtLeast(2)
            var srcW = ceil((destW + 1.0) / s).toInt().coerceAtLeast(2)
            val leftPad = ((4f + extraL) / s).roundToInt().coerceAtLeast(0)
            val topPad = (4f / s).roundToInt().coerceAtLeast(0)
            val sL = evenOrigin((srcLeft - leftPad).coerceAtLeast(0))
            val sT = evenOrigin((srcTop - topPad).coerceAtLeast(0))
            if (sL + srcW > matW) srcW = evenDown(matW - sL)
            if (sT + srcH > matH) srcH = evenDown(matH - sT)
            if (srcW < 2 || srcH < 2) return null
            val srcId = tryCreateCrop(workspace, sL, sT, srcW, srcH) ?: return null
            val src = workspace.c[srcId]
            val fed = downsampleMildSource(src.mat, recBuffer, s, destW, maxW)
            src.release()
            fed
        } catch (_: Exception) {
            null
        }
    }

    data class Content40Plan(
        val s: Float,
        val destW: Int,
        val destH: Int,
        val srcW: Int,
        val srcH: Int,
        val marginSrc: Float,
    )

    /**
     * s starts at 40/cropH. destW is even, border + round(cropW * s), capped by [maxDestW].
     * If that width does not fit, s drops and CropScale is logged. Source window is
     * ceil((dest+1)/s), rounded up to an even size so the pixel past the buffer is kept.
     */
    fun planContent40In48(
        cropW: Float,
        cropH: Float,
        maxDestW: Int,
        destH: Int,
        borderPx: Int = DEFAULT_BORDER_PX,
    ): Content40Plan {
        val h = cropH.coerceAtLeast(1f)
        val w = cropW.coerceAtLeast(1f)
        val canvasH = destH.coerceAtLeast(2)
        val cap = maxDestW.coerceAtLeast(2)
        val maxEven = if (cap % 2 == 0) cap else (cap - 1).coerceAtLeast(2)
        val border = borderPx.coerceAtLeast(0)
        var s = 40f / h
        fun destWidth(scale: Float): Int = evenAtLeast2(border + (w * scale).roundToInt())
        var destW = destWidth(s)
        if (destW > maxEven) {
            val s0 = s
            val cMax = (maxEven - border).coerceAtLeast(1)
            var chosen = 1f / w
            var c = cMax
            while (c >= 1) {
                var sTry = (c + 0.5f) / w
                var guard = 0
                while (guard < 6 && (w * sTry).roundToInt() > c) {
                    sTry = java.lang.Math.nextDown(sTry.toDouble()).toFloat()
                    guard++
                }
                if (sTry > 0f && (w * sTry).roundToInt() <= c && destWidth(sTry) <= maxEven) {
                    chosen = sTry
                    break
                }
                c--
            }
            s = min(s0, chosen)
            destW = destWidth(s).coerceAtMost(maxEven)
            if (destW % 2 != 0) destW = evenDown(destW)
            Log.i(
                "CropScale",
                "width fit crop=${w}x${h} s0=$s0 s=$s dest=${destW}x${canvasH} cap=$cap",
            )
        }
        val srcW = evenAtLeast2(ceil((destW + 1.0) / s).toInt())
        val srcH = evenAtLeast2(ceil((canvasH + 1.0) / s).toInt())
        return Content40Plan(s, destW, canvasH, srcW, srcH, border.toFloat() / s)
    }

    private fun feedContent40(
        srcMat: Mat,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        targetH: Int,
        maxW: Int,
        borderPx: Int,
    ): Result {
        val cropW = (srcRight - srcLeft).coerceAtLeast(1)
        val cropH = (srcBottom - srcTop).coerceAtLeast(1)
        var destH = targetH.coerceAtLeast(2).coerceAtMost(DEFAULT_REC_H).coerceAtMost(recBuffer.p.height)
        if (destH % 2 != 0) destH -= 1
        val capW = recBuffer.p.width.coerceAtMost(maxW).coerceAtLeast(2)
        val plan = planContent40In48(cropW.toFloat(), cropH.toFloat(), capW, destH, borderPx)
        val matW = srcMat.cols()
        val matH = srcMat.rows()
        val rawL = (srcLeft - plan.marginSrc).roundToInt()
        val rawT = (srcTop - plan.marginSrc).roundToInt()
        var cut = rawL < 0 || rawT < 0 || rawL + plan.srcW > matW || rawT + plan.srcH > matH
        var sL = evenOrigin(rawL)
        var sT = evenOrigin(rawT)
        var winW = plan.srcW
        var winH = plan.srcH
        if (sL + winW > matW) {
            winW = (matW - sL).let { n -> if (n % 2 == 0) n else n - 1 }
            cut = true
        }
        if (sT + winH > matH) {
            winH = (matH - sT).let { n -> if (n % 2 == 0) n else n - 1 }
            cut = true
        }
        if (winW < plan.srcW || winH < plan.srcH) cut = true
        if (cut) {
            Log.i(
                "CropScale",
                "clamp cut ideal=${plan.srcW}x${plan.srcH} at ($rawL,$rawT) got=${winW}x${winH} at ($sL,$sT) parent=${matW}x${matH} crop=${cropW}x${cropH}",
            )
        }
        recBuffer.p.clear()
        val recCropId = recBuffer.createCrop(0, 0, plan.destW, plan.destH)
        if (winW < 2 || winH < 2 || srcMat.empty()) {
            return Result(
                plan.s, plan.marginSrc.roundToInt(), recCropId,
                recBuffer.c[recCropId].width, recBuffer.c[recCropId].height,
            )
        }
        val holder = BufferSet(winW, winH)
        try {
            copyLumaRect(srcMat, sL, sT, holder.p.mat, winW, winH)
            val srcId = holder.createCrop(0, 0, winW, winH)
            scaleIsotropicNoPartial(holder.c[srcId].mat, recBuffer.c[recCropId].mat, plan.s)
        } finally {
            holder.release()
        }
        val dest = recBuffer.c[recCropId]
        return Result(plan.s, plan.marginSrc.roundToInt().coerceAtLeast(0), recCropId, dest.width, dest.height)
    }

    private fun copyLumaRect(src: Mat, x: Int, y: Int, dest: Mat, w: Int, h: Int) {
        val row = ByteArray(w)
        val x0 = x.coerceAtLeast(0)
        val y0 = y.coerceAtLeast(0)
        for (r in 0 until h) {
            java.util.Arrays.fill(row, 0.toByte())
            val sy = y0 + r
            val avail = src.cols() - x0
            if (sy < src.rows() && avail > 0) {
                val need = min(w, avail)
                val got = if (need == w) {
                    src.get(sy, x0, row)
                } else {
                    val tmp = ByteArray(need)
                    val n = src.get(sy, x0, tmp)
                    val copyN = n.coerceIn(0, need)
                    tmp.copyInto(row, 0, 0, copyN)
                    copyN
                }
                if (got < 0) java.util.Arrays.fill(row, 0.toByte())
            }
            dest.put(r, 0, row)
        }
    }

    /**
     * Aspect-fit [src] into [dest] at one scale. Clears [dest] first. Writes the largest
     * top-left rectangle whose +1 source window still fits in [src]. Outside that rectangle
     * stays clear. Logs CropScale when the buffer is narrower than the crop aspect.
     */
    fun scaleCropToFitBuffer(src: Mat, dest: Mat) {
        if (dest.empty()) return
        val srcW = src.cols()
        val srcH = src.rows()
        val destW = dest.cols()
        val destH = dest.rows()
        dest.setTo(Scalar(0.0))
        if (src.empty() || srcW < 1 || srcH < 1 || destW < 1 || destH < 1) return
        val sFitW = destW.toFloat() / srcW
        val sFitH = destH.toFloat() / srcH
        val s = min(sFitW, sFitH)
        if (s <= 0f) return
        if (sFitW < sFitH) {
            Log.i(
                "CropScale",
                "buffer too narrow src=${srcW}x${srcH} dest=${destW}x${destH} s=$s",
            )
        }
        var writeW = destW
        while (writeW > 0 && ceil((writeW + 1.0) / s).toInt() > srcW) writeW--
        var writeH = destH
        while (writeH > 0 && ceil((writeH + 1.0) / s).toInt() > srcH) writeH--
        if (writeW < 1 || writeH < 1) return
        scaleIsotropicNoPartial(src, dest, s, writeW, writeH)
    }

    /** Area sample at scale [s]; write only [writeW]×[writeH] (the pixel past that rect is not written). */
    fun scaleIsotropicNoPartial(
        src: Mat,
        dest: Mat,
        s: Float,
        writeW: Int = dest.cols(),
        writeH: Int = dest.rows(),
    ) {
        val dw = writeW.coerceIn(0, dest.cols())
        val dh = writeH.coerceIn(0, dest.rows())
        val sw = src.cols()
        val sh = src.rows()
        if (dw < 1 || dh < 1 || sw < 1 || sh < 1 || s <= 0f) return
        val srcCache = Array(sh) { ByteArray(sw) }
        for (sy in 0 until sh) {
            src.get(sy, 0, srcCache[sy])
        }
        val destRow = ByteArray(dw)
        for (y in 0 until dh) {
            val fy0 = y / s
            val fy1 = (y + 1) / s
            val sy0 = floor(fy0.toDouble()).toInt().coerceIn(0, sh - 1)
            val sy1e = ceil(fy1.toDouble()).toInt().coerceIn(1, sh)
            for (x in 0 until dw) {
                val fx0 = x / s
                val fx1 = (x + 1) / s
                val sx0 = floor(fx0.toDouble()).toInt().coerceIn(0, sw - 1)
                val sx1e = ceil(fx1.toDouble()).toInt().coerceIn(1, sw)
                var acc = 0.0
                var wsum = 0.0
                for (sy in sy0 until sy1e) {
                    val yA = max(fy0, sy.toFloat())
                    val yB = min(fy1, (sy + 1).toFloat())
                    val wy = (yB - yA).toDouble()
                    if (wy <= 0.0) continue
                    val row = srcCache[sy]
                    for (sx in sx0 until sx1e) {
                        val xA = max(fx0, sx.toFloat())
                        val xB = min(fx1, (sx + 1).toFloat())
                        val wx = (xB - xA).toDouble()
                        if (wx <= 0.0) continue
                        val wt = wx * wy
                        acc += (row[sx].toInt() and 0xFF) * wt
                        wsum += wt
                    }
                }
                destRow[x] = if (wsum > 0.0) {
                    (acc / wsum + 0.5).toInt().coerceIn(0, 255).toByte()
                } else {
                    0
                }
            }
            dest.put(y, 0, destRow)
        }
    }

    /**
     * No callers. Body is the same 40-in-48 feed as [feedSourceBorderHeightStrip]
     * ([contentH], [canvasH], and [recBorder] are not a second scale).
     */
    fun feedContentInCanvas(
        srcMat: Mat,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        @Suppress("UNUSED_PARAMETER") contentH: Int,
        @Suppress("UNUSED_PARAMETER") canvasH: Int,
        @Suppress("UNUSED_PARAMETER") recBorder: Int,
        maxW: Int = DEFAULT_MAX_W,
    ): Result = feedContent40(
        srcMat, srcLeft, srcTop, srcRight, srcBottom, recBuffer,
        DEFAULT_REC_H, maxW, DEFAULT_BORDER_PX,
    )

    fun feedContentInCanvas(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        contentH: Int,
        canvasH: Int,
        recBorder: Int,
        maxW: Int = DEFAULT_MAX_W,
    ): Result {
        val l = rect.left.coerceIn(0, imgW - 1)
        val t = rect.top.coerceIn(0, imgH - 1)
        val rr = rect.right.coerceIn(l + 1, imgW)
        val bb = rect.bottom.coerceIn(t + 1, imgH)
        return feedContentInCanvas(
            workspace.p.mat,
            l, t, rr, bb,
            recBuffer,
            contentH, canvasH, recBorder, maxW,
        )
    }

    /**
     * No callers. Treats [strip] as the crop and runs the same 40-in-48 feed.
     * Caller owns [strip] release.
     */
    fun feedPreparedStripNoBlackPad(
        strip: Mat,
        recBuffer: BufferSet,
    ): Result = feedContent40(
        strip, 0, 0, strip.cols().coerceAtLeast(1), strip.rows().coerceAtLeast(1),
        recBuffer, DEFAULT_REC_H, DEFAULT_MAX_W, DEFAULT_BORDER_PX,
    )
}
