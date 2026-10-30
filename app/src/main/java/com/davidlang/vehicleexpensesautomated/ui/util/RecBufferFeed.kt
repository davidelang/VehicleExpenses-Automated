package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Rect
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Shared recognition-buffer feed: 40 px of content in a 48-high buffer, one isotropic scale.
 * Left and top margin are 4 dest px of real source. Slack is the bottom and right.
 * Pixel op is [scaleIsotropicNoPartial]. Geom integer-k and mild feeds stay separate.
 */
object RecBufferFeed {
    const val DEFAULT_BORDER_PX = 4
    const val RIGHT_BORDER_PX = 4
    const val DEFAULT_REC_H = 48
    /** One rec canvas width; clip only here (not a squeeze). */
    const val DEFAULT_MAX_W = 4096

    data class Result(
        val contentScale: Float,
        val sourcePadPx: Int,
        val recCropId: Int,
        val targetW: Int,
        val targetH: Int,
        val integerK: Int = 0,
        val mildS: Float = 0f,
    )


    internal fun evenAtLeast2(n: Int): Int {
        val v = n.coerceAtLeast(2)
        return if (v % 2 == 0) v else v + 1
    }

    internal fun evenDown(n: Int): Int {
        val e = if (n % 2 == 0) n else n - 1
        return e.coerceAtLeast(2)
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
     * s is 40/cropH. destW is even, border + round(cropW * s). It is not reduced to fit a buffer.
     * Source window is ceil((dest+1)/s), rounded up to an even size so the pixel past the buffer is kept.
     */
    fun planContent40In48(
        cropW: Float,
        cropH: Float,
        destH: Int,
        borderPx: Int = DEFAULT_BORDER_PX,
    ): Content40Plan {
        val h = cropH.coerceAtLeast(1f)
        val w = cropW.coerceAtLeast(1f)
        val canvasH = destH.coerceAtLeast(2)
        val border = borderPx.coerceAtLeast(0)
        val s = 40f / h
        val destW = evenAtLeast2(border + RIGHT_BORDER_PX + (w * s).roundToInt())
        val srcW = evenAtLeast2(ceil((destW + 1.0) / s).toInt())
        val srcH = evenAtLeast2(ceil((canvasH + 1.0) / s).toInt())
        return Content40Plan(s, destW, canvasH, srcW, srcH, border.toFloat() / s)
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
        NativeImageUtils.scaleIsotropicArea(src, dest, s, dw, dh)
    }
}
