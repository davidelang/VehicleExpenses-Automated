package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Rect
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
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
     * Geom-only integer-k downsample to content 40 in canvas 48.
     * k = max(1, round(boxH/40)); source 40k tall; dest content 40.
     * Extra source rows: odd → bottom; even → split. Extra cols: odd → right; even → split.
     * destW even; srcW = destW*k. INTER_AREA k×k (copy if k=1).
     * Does not change [feedSourceBorderHeightStrip].
     */
    fun feedIntegerKContent40(
        srcMat: Mat,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
    ): Result {
        val matW = srcMat.cols()
        val matH = srcMat.rows()
        val pW = (srcRight - srcLeft).coerceAtLeast(1)
        val pH = (srcBottom - srcTop).coerceAtLeast(1)
        val contentH = 40
        val canvasH = DEFAULT_REC_H.coerceAtMost(recBuffer.p.height)
        val k = maxOf(1, (pH / 40f).roundToInt())
        val srcHWant = contentH * k
        val destW = evenDown((pW.toFloat() / k).roundToInt().coerceAtLeast(2)).coerceAtMost(maxW)
        val srcWWant = destW * k
        val (sL, sR) = expandAxis(srcLeft, srcRight, srcWWant, 0, matW)
        val (sT, sB) = expandAxis(srcTop, srcBottom, srcHWant, 0, matH)
        val sub = srcMat.submat(
            org.opencv.core.Rect(sL, sT, (sR - sL).coerceAtLeast(1), (sB - sT).coerceAtLeast(1)),
        )
        recBuffer.p.clear()
        val recCropId = recBuffer.createCrop(0, 0, destW, canvasH)
        val y0 = ((canvasH - contentH) / 2).coerceAtLeast(0)
        val destContent = recBuffer.c[recCropId].mat.submat(y0, y0 + contentH, 0, destW)
        if (k == 1 && sub.cols() == destW && sub.rows() == contentH) {
            sub.copyTo(destContent)
        } else {
            Imgproc.resize(
                sub, destContent,
                Size(destW.toDouble(), contentH.toDouble()),
                0.0, 0.0, Imgproc.INTER_AREA,
            )
        }
        destContent.release()
        sub.release()
        return Result(1f / k, 0, recCropId, destW, canvasH, integerK = k)
    }

    fun feedIntegerKContent40(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
    ): Result {
        val l = rect.left.coerceIn(0, imgW - 1)
        val t = rect.top.coerceIn(0, imgH - 1)
        val rr = rect.right.coerceIn(l + 1, imgW)
        val bb = rect.bottom.coerceIn(t + 1, imgH)
        return feedIntegerKContent40(workspace.p.mat, l, t, rr, bb, recBuffer, maxW)
    }

    /**
     * Geom-only isotropic s toward content ~40. If source×s is not an integer dest size,
     * size source as dest N+1 at the same s, then drop dest pixel N+1 (partial).
     * Extra source: bottom / right. Dest content typically 39–41 in canvas 48.
     */
    fun feedMildNoPartial(
        srcMat: Mat,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
    ): Result {
        val matW = srcMat.cols()
        val matH = srcMat.rows()
        val pW = (srcRight - srcLeft).coerceAtLeast(1)
        val pH = (srcBottom - srcTop).coerceAtLeast(1)
        val canvasH = DEFAULT_REC_H.coerceAtMost(recBuffer.p.height)
        val s = 40f / pH
        val (srcH, destH) = mildAxis(pH, s, canvasH)
        val (srcW, destW) = mildAxis(pW, s, maxW)
        val sL = srcLeft.coerceAtLeast(0)
        val sT = srcTop.coerceAtLeast(0)
        val sR = (srcLeft + srcW).coerceAtMost(matW)
        val sB = (srcTop + srcH).coerceAtMost(matH)
        val sub = srcMat.submat(
            org.opencv.core.Rect(sL, sT, (sR - sL).coerceAtLeast(1), (sB - sT).coerceAtLeast(1)),
        )
        recBuffer.p.clear()
        val recCropId = recBuffer.createCrop(0, 0, destW, canvasH)
        val y0 = ((canvasH - destH) / 2).coerceAtLeast(0)
        val destContent = recBuffer.c[recCropId].mat.submat(
            y0, (y0 + destH).coerceAtMost(canvasH), 0, destW,
        )
        Imgproc.resize(
            sub, destContent,
            Size(destW.toDouble(), destH.toDouble()),
            0.0, 0.0, Imgproc.INTER_AREA,
        )
        destContent.release()
        sub.release()
        return Result(s, 0, recCropId, destW, canvasH, mildS = s)
    }

    fun feedMildNoPartial(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
    ): Result {
        val l = rect.left.coerceIn(0, imgW - 1)
        val t = rect.top.coerceIn(0, imgH - 1)
        val rr = rect.right.coerceIn(l + 1, imgW)
        val bb = rect.bottom.coerceIn(t + 1, imgH)
        return feedMildNoPartial(workspace.p.mat, l, t, rr, bb, recBuffer, maxW)
    }

    private fun evenDown(n: Int): Int {
        val e = if (n % 2 == 0) n else n - 1
        return e.coerceAtLeast(2)
    }

    /** Extra to reach [want]: odd → end (bottom/right); even → split. Negative extra crops. */
    private fun expandAxis(
        a0: Int, a1: Int, want: Int, lo: Int, hi: Int,
    ): Pair<Int, Int> {
        val have = (a1 - a0).coerceAtLeast(1)
        val extra = want - have
        val startPad: Int
        val endPad: Int
        if (extra == 0) {
            startPad = 0
            endPad = 0
        } else if (extra > 0) {
            if (extra % 2 == 1) {
                startPad = 0
                endPad = extra
            } else {
                startPad = extra / 2
                endPad = extra - startPad
            }
        } else {
            val crop = -extra
            if (crop % 2 == 1) {
                startPad = 0
                endPad = extra
            } else {
                startPad = extra / 2
                endPad = extra - startPad
            }
        }
        val s0 = (a0 - startPad).coerceAtLeast(lo)
        val s1 = (a1 + endPad).coerceAtMost(hi)
        return s0 to s1.coerceAtLeast(s0 + 1)
    }

    /** Dest size from source×s; if not integer, source as N+1 then drop dest N+1. */
    private fun mildAxis(src: Int, s: Float, maxDest: Int): Pair<Int, Int> {
        val ideal = src * s
        val nearest = ideal.roundToInt()
        if (abs(ideal - nearest) < 1e-3f) {
            val d = nearest.coerceIn(2, maxDest)
            return src to d
        }
        val n = floor(ideal.toDouble()).toInt().coerceAtLeast(1)
        val srcN1 = ceil((n + 1) / s.toDouble()).toInt().coerceAtLeast(src)
        val d = n.coerceIn(2, maxDest)
        return srcN1 to d
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
