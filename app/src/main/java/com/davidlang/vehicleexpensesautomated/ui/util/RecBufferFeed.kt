package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Rect
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared recognition-buffer feed: scale a source ROI into [BufferSet] for Paddle rec.
 *
 * **Source-border (default):** inflate the source rect by ~[borderPx]/scale so that after
 * scaling, the margin is real image pixels (same idea as alignment odo Raw/Bin feed).
 * No black letterbox from `createCrop(borderPx, borderPx, …)`.
 *
 * **Legacy black pad:** scale exact ROI into `createCrop(borderPx, borderPx, ew, eh)` on a
 * cleared buffer (kept only for A/B if needed).
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
     * Odo-style letterbox: fit content into (recW-2b)×(recH-2b) conceptually, with source-border
     * expanding the crop so the full rec canvas is filled without a black inset.
     */
    fun feedSourceBorderLetterbox(
        srcMat: Mat,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        borderPx: Int = DEFAULT_BORDER_PX,
    ): Result {
        val matW = srcMat.cols()
        val matH = srcMat.rows()
        val recW = recBuffer.p.width
        val recH = recBuffer.p.height
        val w0 = (srcRight - srcLeft).coerceAtLeast(1)
        val h0 = (srcBottom - srcTop).coerceAtLeast(1)
        val contentW = (recW - 2 * borderPx).coerceAtLeast(1)
        val contentH = (recH - 2 * borderPx).coerceAtLeast(1)
        val rScContent = min(contentW.toFloat() / w0, contentH.toFloat() / h0)
        val pad = ceil(borderPx.toDouble() / rScContent.toDouble()).toInt().coerceAtLeast(1)
        val sL = (srcLeft - pad).coerceAtLeast(0)
        val sT = (srcTop - pad).coerceAtLeast(0)
        val sR = (srcRight + pad).coerceAtMost(matW)
        val sB = (srcBottom + pad).coerceAtMost(matH)
        val sub = srcMat.submat(org.opencv.core.Rect(sL, sT, (sR - sL).coerceAtLeast(1), (sB - sT).coerceAtLeast(1)))
        recBuffer.p.clear()
        val rSc = min(recW.toFloat() / sub.cols(), recH.toFloat() / sub.rows())
        val ew = ((sub.cols() * rSc + 1).toInt() / 2 * 2).coerceAtLeast(2).coerceAtMost(recW)
        val eh = ((sub.rows() * rSc + 1).toInt() / 2 * 2).coerceAtLeast(2).coerceAtMost(recH)
        val recCropId = recBuffer.createCrop(0, 0, ew, eh)
        Imgproc.resize(
            sub,
            recBuffer.c[recCropId].mat,
            recBuffer.c[recCropId].mat.size(),
            0.0,
            0.0,
            Imgproc.INTER_AREA,
        )
        sub.release()
        return Result(rScContent, pad, recCropId, ew, eh)
    }

    /**
     * Pump-style height-locked strip: scale so height → [targetH] (usually 48),
     * width isotropic (`subW/subH`). 32-align is empty canvas to the right, not a scale.
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
    ): Result {
        val matW = srcMat.cols()
        val matH = srcMat.rows()
        val pW = (srcRight - srcLeft).coerceAtLeast(1)
        val pH = (srcBottom - srcTop).coerceAtLeast(1)
        // Content height for pad math ≈ targetH (full strip height after source-border).
        val rScContent = targetH.toFloat() / pH
        val pad = ceil(borderPx.toDouble() / rScContent.toDouble()).toInt().coerceAtLeast(1)
        val sL = (srcLeft - pad).coerceAtLeast(0)
        val sT = (srcTop - pad).coerceAtLeast(0)
        val sR = (srcRight + pad).coerceAtMost(matW)
        val sB = (srcBottom + pad).coerceAtMost(matH)
        val subW = (sR - sL).coerceAtLeast(1)
        val subH = (sB - sT).coerceAtLeast(1)
        val sub = srcMat.submat(org.opencv.core.Rect(sL, sT, subW, subH))
        val scale = targetH.toFloat() / subH
        fun even(n: Int): Int = if (n % 2 == 0) n else n + 1
        val ch = targetH.coerceAtMost(recBuffer.p.height)
        val maxContentW = recBuffer.p.width.coerceAtMost(maxW)
        var srcW = subW
        var srcL = 0
        var cw = even((srcW * scale).toInt().coerceAtLeast(1))
        if (cw > maxContentW) {
            srcW = (maxContentW / scale).toInt().coerceAtLeast(1).coerceAtMost(subW)
            srcL = ((subW - srcW) / 2).coerceAtLeast(0)
            cw = even((srcW * scale).toInt().coerceAtLeast(2)).coerceAtMost(maxContentW)
            if (cw % 2 != 0) cw = (cw - 1).coerceAtLeast(2)
        }
        val srcRoi = sub.submat(0, subH, srcL, srcL + srcW)
        recBuffer.p.clear()
        val canvasW = ((cw + 31) / 32 * 32).coerceAtMost(recBuffer.p.width).coerceAtLeast(cw)
        val recCropId = recBuffer.createCrop(0, 0, canvasW, ch)
        val destContent = recBuffer.c[recCropId].mat.submat(0, ch, 0, cw)
        val interp = if (srcW > cw) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
        Imgproc.resize(
            srcRoi,
            destContent,
            Size(cw.toDouble(), ch.toDouble()),
            0.0,
            0.0,
            interp,
        )
        destContent.release()
        srcRoi.release()
        sub.release()
        return Result(rScContent, pad, recCropId, cw, ch)
    }

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
    ): Result? {
        val l = rect.left.coerceIn(0, imgW - 1)
        val t = rect.top.coerceIn(0, imgH - 1)
        val rr = rect.right.coerceIn(l + 1, imgW)
        val bb = rect.bottom.coerceIn(t + 1, imgH)
        return feedIntegerKFromWorkspace(workspace, l, t, rr, bb, recBuffer, maxW)
    }

    /**
     * Geom-only isotropic s = 40/boxH. Dest canvas 48. Source sized as dest 49 / W+1
     * at the same s; custom sampler writes dest rows 0..47 and destW columns only.
     * Extra source: bottom/right. BufferSet crops only.
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
    ): Result? {
        val l = rect.left.coerceIn(0, imgW - 1)
        val t = rect.top.coerceIn(0, imgH - 1)
        val rr = rect.right.coerceIn(l + 1, imgW)
        val bb = rect.bottom.coerceIn(t + 1, imgH)
        return feedMildFromWorkspace(workspace, l, t, rr, bb, recBuffer, maxW)
    }

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
    ): Result? {
        return try {
            val matW = workspace.width
            val matH = workspace.height
            val boxW = (srcRight - srcLeft).coerceAtLeast(1)
            val boxH = (srcBottom - srcTop).coerceAtLeast(1)
            val canvasH = DEFAULT_REC_H.coerceAtMost(recBuffer.p.height)
            if (matW < 2 || matH < 2 || canvasH < 2) return null
            var k = maxOf(1, (boxH / 40f).roundToInt())
            var srcId: Int? = null
            while (k >= 1) {
                var destW = evenAtLeast2(4 + (boxW.toFloat() / k).roundToInt())
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
                var sL = evenOrigin((srcLeft - 4 * k).coerceAtLeast(0))
                var sT = evenOrigin((srcTop - 4 * k).coerceAtLeast(0))
                if (sL + srcW > matW) sL = 0
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
    ): Result? {
        return try {
            val matW = workspace.width
            val matH = workspace.height
            val boxW = (srcRight - srcLeft).coerceAtLeast(1)
            val boxH = (srcBottom - srcTop).coerceAtLeast(1)
            val s = 40f / boxH
            if (s <= 0f || matW < 2 || matH < 2) return null
            val destW = evenDown(
                evenAtLeast2(4 + (boxW * s).roundToInt())
                    .coerceAtMost(maxW.coerceAtMost(recBuffer.p.width)),
            )
            var srcH = ceil(49.0 / s).toInt().coerceAtLeast(2)
            var srcW = ceil((destW + 1.0) / s).toInt().coerceAtLeast(2)
            val leftPad = (4f / s).roundToInt().coerceAtLeast(0)
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

    /** Area sample at scale [s]; write only dest.cols × dest.rows (drop dest W+1 / row 48). */
    private fun scaleIsotropicNoPartial(src: Mat, dest: Mat, s: Float) {
        val dw = dest.cols()
        val dh = dest.rows()
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
     * Unpadded blue → [contentH] px of text; dest strip is [canvasH].
     * Source pad each side is `ceil(recBorder × pH / contentH)` (clamped).
     * Resize the padded sub to [canvasH] (isotropic W, even). Packed crop is
     * the rec image (`targetW`×`canvasH`), not a 4096 letterbox.
     *
     * 48-in-56: contentH=48, canvasH=56, recBorder=4.
     * 48-in-64: contentH=48, canvasH=64, recBorder=8.
     * Control 40-in-48 stays [feedSourceBorderHeightStrip] (48, 4).
     */
    fun feedContentInCanvas(
        srcMat: Mat,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        contentH: Int,
        canvasH: Int,
        recBorder: Int,
        maxW: Int = DEFAULT_MAX_W,
    ): Result {
        val matW = srcMat.cols()
        val matH = srcMat.rows()
        val pH = (srcBottom - srcTop).coerceAtLeast(1)
        val cH = contentH.coerceAtLeast(1)
        val destH = canvasH.coerceAtLeast(2).coerceAtMost(recBuffer.p.height)
        val rScContent = cH.toFloat() / pH
        val pad = ceil(recBorder.toDouble() * pH.toDouble() / cH.toDouble()).toInt().coerceAtLeast(0)
        val sL = (srcLeft - pad).coerceAtLeast(0)
        val sT = (srcTop - pad).coerceAtLeast(0)
        val sR = (srcRight + pad).coerceAtMost(matW)
        val sB = (srcBottom + pad).coerceAtMost(matH)
        val subW = (sR - sL).coerceAtLeast(1)
        val subH = (sB - sT).coerceAtLeast(1)
        val sub = srcMat.submat(org.opencv.core.Rect(sL, sT, subW, subH))
        val scale = destH.toFloat() / subH
        fun even(n: Int): Int = if (n % 2 == 0) n else n + 1
        val maxContentW = recBuffer.p.width.coerceAtMost(maxW)
        var srcW = subW
        var srcL = 0
        var cw = even((srcW * scale).toInt().coerceAtLeast(1))
        if (cw > maxContentW) {
            srcW = (maxContentW / scale).toInt().coerceAtLeast(1).coerceAtMost(subW)
            srcL = ((subW - srcW) / 2).coerceAtLeast(0)
            cw = even((srcW * scale).toInt().coerceAtLeast(2)).coerceAtMost(maxContentW)
            if (cw % 2 != 0) cw = (cw - 1).coerceAtLeast(2)
        }
        val srcRoi = sub.submat(0, subH, srcL, srcL + srcW)
        recBuffer.p.clear()
        val recCropId = recBuffer.createCrop(0, 0, cw, destH)
        val interp = if (srcW > cw) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
        Imgproc.resize(
            srcRoi,
            recBuffer.c[recCropId].mat,
            Size(cw.toDouble(), destH.toDouble()),
            0.0,
            0.0,
            interp,
        )
        srcRoi.release()
        sub.release()
        return Result(rScContent, pad, recCropId, cw, destH)
    }

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
     * Place an already-prepared horizontal strip [strip] (e.g. warped quad) into rec without
     * black inset. Caller owns [strip] release. Inflating source before warp is preferred;
     * this only drops the black pad when the strip is already built.
     */
    fun feedPreparedStripNoBlackPad(
        strip: Mat,
        recBuffer: BufferSet,
    ): Result {
        val tw = strip.cols().coerceAtLeast(2).coerceAtMost(recBuffer.p.width)
        val th = strip.rows().coerceAtLeast(2).coerceAtMost(recBuffer.p.height)
        recBuffer.p.clear()
        val recCropId = recBuffer.createCrop(0, 0, tw, th)
        if (strip.cols() == tw && strip.rows() == th) {
            strip.copyTo(recBuffer.c[recCropId].mat)
        } else {
            Imgproc.resize(
                strip,
                recBuffer.c[recCropId].mat,
                Size(tw.toDouble(), th.toDouble()),
                0.0,
                0.0,
                Imgproc.INTER_AREA,
            )
        }
        return Result(1f, 0, recCropId, tw, th)
    }
}
