package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Rect
import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared recognition-buffer feed: 40 px of content in a 48-high buffer, one isotropic scale.
 * Left and top margin are 4 dest px of real source. Slack is the bottom and right.
 * Pixel op is [scaleIsotropicNoPartial]. Geom integer-k and mild feeds stay separate.
 */
import com.davidlang.vehicleexpensesautomated.ui.util.RecBufferFeed.Result


    private fun evenOrigin(v: Int): Int = (v.coerceAtLeast(0) / 2) * 2


    /**
     * 40 px of content in a 48-high canvas. One scale. Left/top margin is [borderPx] dest px
     * of source. Slack is the bottom and right. Caller releases [Result.recCropId].
     */
    fun RecBufferFeed.feedSourceBorderLetterbox(
        srcMat: Mat,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        borderPx: Int = DEFAULT_BORDER_PX,
    ): Result = feedContent40(
        srcMat, srcLeft, srcTop, srcRight, srcBottom, recBuffer,
        DEFAULT_REC_H, borderPx,
    )


    /**
     * 40 px of content in a 48-high strip ([targetH] clamped to 48). One scale.
     * [destW] is even, border plus the scaled crop. Scale stays 40/cropH.
     * [maxW] is not a scale cap.
     *
     * @return [Result] including [Result.recCropId] that caller must [BufferSet.Slice.release]
     */
    fun RecBufferFeed.feedSourceBorderHeightStrip(
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
        targetH, borderPx,
    )


    fun RecBufferFeed.feedSourceBorderHeightStrip(
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
    fun RecBufferFeed.feedIntegerKContent40(
        srcMat: Mat,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
    ): Result? = downsampleIntegerKSource(srcMat, recBuffer, maxW)


    fun RecBufferFeed.feedIntegerKContent40(
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




    /**
     * Geom-only isotropic s = 40/boxH. Dest canvas 48. Source height is the dest-49
     * window; width is left pad + blue + right pad. Sampler writes dest rows 0..47.
     * Extra source: bottom. BufferSet crops only.
     * Both sides are 4 dest px outside the blue box (round(4/s) source).
     * [growLeft] / [growRight] add round(0.2×boxH) source px on that side only.
     * The other side stays 4 dest px. Top stays 4 dest px. No ink scan.
     */
    fun RecBufferFeed.feedMildNoPartial(
        srcMat: Mat,
        recBuffer: BufferSet,
        s: Float,
        destWHint: Int,
        maxW: Int = DEFAULT_MAX_W,
    ): Result? = downsampleMildSource(srcMat, recBuffer, s, destWHint, maxW)


    fun RecBufferFeed.feedMildNoPartial(
        workspace: BufferSet,
        rect: Rect,
        imgW: Int,
        imgH: Int,
        recBuffer: BufferSet,
        maxW: Int = DEFAULT_MAX_W,
        growLeft: Boolean = false,
        growRight: Boolean = false,
    ): Result? {
        val l = rect.left.coerceIn(0, imgW - 1)
        val t = rect.top.coerceIn(0, imgH - 1)
        val rr = rect.right.coerceIn(l + 1, imgW)
        val bb = rect.bottom.coerceIn(t + 1, imgH)
        return feedMildFromWorkspace(
            workspace, l, t, rr, bb, recBuffer, maxW, growLeft, growRight,
        )
    }


    private fun RecBufferFeed.tryCreateCrop(bs: BufferSet, x: Int, y: Int, w: Int, h: Int): Int? {
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


    private fun RecBufferFeed.downsampleIntegerKSource(
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


    private fun RecBufferFeed.feedIntegerKFromWorkspace(
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


    private fun RecBufferFeed.downsampleMildSource(
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
            val limit = maxW.coerceAtMost(recBuffer.p.width)
            var destW = destWHint.coerceAtLeast(2).coerceAtMost(limit)
            if (destW % 2 != 0) {
                destW = if (destW + 1 <= limit) destW + 1 else (destW - 1).coerceAtLeast(2)
            }
            recBuffer.p.clear()
            val recCropId = tryCreateCrop(recBuffer, 0, 0, destW, canvasH) ?: return null
            val dest = recBuffer.c[recCropId]
            scaleIsotropicNoPartial(srcMat, dest.mat, s)
            Result(s, (4f / s).roundToInt().coerceAtLeast(0), recCropId, dest.width, dest.height, mildS = s)
        } catch (_: Exception) {
            null
        }
    }


    private fun RecBufferFeed.feedMildFromWorkspace(
        workspace: BufferSet,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        maxW: Int,
        growLeft: Boolean = false,
        growRight: Boolean = false,
    ): Result? {
        return try {
            val matW = workspace.width
            val matH = workspace.height
            val boxW = (srcRight - srcLeft).coerceAtLeast(1)
            val boxH = (srcBottom - srcTop).coerceAtLeast(1)
            val s = 40f / boxH
            if (s <= 0f || matW < 2 || matH < 2) return null
            val frameSrc = (4f / s).roundToInt().coerceAtLeast(0)
            val growSrc = (boxH * 0.2).roundToInt().coerceAtLeast(0)
            var leftPad = frameSrc + if (growLeft) growSrc else 0
            var rightPad = frameSrc + if (growRight) growSrc else 0
            val topPad = frameSrc
            if (srcLeft - leftPad < 0) leftPad = srcLeft.coerceAtLeast(0)
            if (srcRight + rightPad > matW) rightPad = (matW - srcRight).coerceAtLeast(0)
            var sL = (srcLeft - leftPad).coerceAtLeast(0)
            sL = (sL / 2) * 2
            leftPad = (srcLeft - sL).coerceAtLeast(0)
            val maxDest = maxW.coerceAtMost(recBuffer.p.width).coerceAtLeast(2)
            fun destForPads(): Int {
                val w = if (!growRight && rightPad >= frameSrc) {
                    ((leftPad + boxW) * s).roundToInt() + 4
                } else if (!growLeft && leftPad >= frameSrc) {
                    4 + ((boxW + rightPad) * s).roundToInt()
                } else {
                    ((leftPad + boxW + rightPad) * s).roundToInt()
                }
                return w.coerceIn(2, maxDest)
            }
            var destW = destForPads()
            if (destW % 2 != 0 && growLeft != growRight) {
                val addSrc = (1.0 / s).roundToInt().coerceAtLeast(1)
                if (growLeft) {
                    val room = sL - (sL % 2)
                    var add = addSrc.coerceAtMost(room)
                    if (add % 2 != 0) add = (add + 1).coerceAtMost(room)
                    if (add % 2 != 0) add -= 1
                    if (add > 0) {
                        sL -= add
                        leftPad += add
                    }
                } else {
                    val room = (matW - srcRight - rightPad).coerceAtLeast(0)
                    rightPad += addSrc.coerceAtMost(room)
                }
                destW = destForPads()
            }
            // BufferSet crops are even. Add one px; do not evenDown a locked side.
            if (destW % 2 != 0) {
                destW = if (destW < maxDest) destW + 1 else (destW - 1).coerceAtLeast(2)
            }
            var sT = (srcTop - topPad).coerceAtLeast(0)
            sT = (sT / 2) * 2
            var srcW = (leftPad + boxW + rightPad).coerceAtLeast(2)
            if (sL + srcW > matW) {
                srcW = (matW - sL).coerceAtLeast(2)
                rightPad = (srcW - leftPad - boxW).coerceAtLeast(0)
                destW = destForPads()
                if (destW % 2 != 0) {
                    destW = if (destW < maxDest) destW + 1 else (destW - 1).coerceAtLeast(2)
                }
            }
            if (srcW % 2 != 0) {
                if (sL + srcW + 1 <= matW) srcW += 1 else srcW -= 1
            }
            val cover = ceil(destW / s.toDouble()).toInt()
            if (srcW < cover) {
                val want = if (cover % 2 == 0) cover else cover + 1
                if (sL + want <= matW) srcW = want
            }
            var srcH = ceil(49.0 / s).toInt().coerceAtLeast(2)
            if (sT + srcH > matH) srcH = matH - sT
            if (srcH % 2 != 0) {
                if (sT + srcH + 1 <= matH) srcH += 1 else srcH -= 1
            }
            if (srcW < 2 || srcH < 2 || sL + srcW > matW || sT + srcH > matH) return null
            val srcId = tryCreateCrop(workspace, sL, sT, srcW, srcH) ?: return null
            val src = workspace.c[srcId]
            val fed = downsampleMildSource(src.mat, recBuffer, s, destW, maxW)
            src.release()
            fed
        } catch (_: Exception) {
            null
        }
    }


    private fun RecBufferFeed.feedContent40(
        srcMat: Mat,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        recBuffer: BufferSet,
        targetH: Int,
        borderPx: Int,
    ): Result {
        val cropW = (srcRight - srcLeft).coerceAtLeast(1)
        val cropH = (srcBottom - srcTop).coerceAtLeast(1)
        var destH = targetH.coerceAtLeast(2).coerceAtMost(DEFAULT_REC_H).coerceAtMost(recBuffer.p.height)
        if (destH % 2 != 0) destH -= 1
        val plan = planContent40In48(cropW.toFloat(), cropH.toFloat(), destH, borderPx)
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
        val window = srcMat.submat(sT, sT + winH, sL, sL + winW)
        try {
            scaleIsotropicNoPartial(window, recBuffer.c[recCropId].mat, plan.s)
        } finally {
            window.release()
        }
        val dest = recBuffer.c[recCropId]
        return Result(plan.s, plan.marginSrc.roundToInt().coerceAtLeast(0), recCropId, dest.width, dest.height)
    }


    /**
     * Aspect-fit [src] into [dest] at one scale. Clears [dest] first. Writes the largest
     * top-left rectangle whose +1 source window still fits in [src]. Outside that rectangle
     * stays clear. Logs CropScale when the buffer is narrower than the crop aspect.
     */
    fun RecBufferFeed.scaleCropToFitBuffer(src: Mat, dest: Mat) {
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

