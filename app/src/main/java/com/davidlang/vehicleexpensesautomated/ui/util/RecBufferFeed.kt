package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Rect
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil
import kotlin.math.min

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
    const val DEFAULT_MAX_W_320 = 320

    data class Result(
        val contentScale: Float,
        val sourcePadPx: Int,
        val recCropId: Int,
        val targetW: Int,
        val targetH: Int,
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
     * Pump-style height-locked strip: scale so height → [targetH] (usually 48), width to
     * 32-aligned value ≤ [maxW], with source-border instead of black 4px inset.
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
        maxW: Int = DEFAULT_MAX_W_320,
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
        val rawW = (subW * scale).toInt()
        val maxContentW = (recBuffer.p.width).coerceAtMost(maxW)
        val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(maxContentW).coerceAtLeast(32)
        val th = targetH.coerceAtMost(recBuffer.p.height)
        recBuffer.p.clear()
        val recCropId = recBuffer.createCrop(0, 0, targetW, th)
        val interp = if (subW > targetW) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
        Imgproc.resize(
            sub,
            recBuffer.c[recCropId].mat,
            Size(targetW.toDouble(), th.toDouble()),
            0.0,
            0.0,
            interp,
        )
        sub.release()
        return Result(rScContent, pad, recCropId, targetW, th)
    }

    fun feedSourceBorderHeightStrip(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        targetH: Int = DEFAULT_REC_H,
        maxW: Int = DEFAULT_MAX_W_320,
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
