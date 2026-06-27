package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.PointF
import android.graphics.RectF

/**
 * IcrsMath: Implementation of Isotropic Center-Relative Space (ICRS).
 *
 * As defined in docs/specs/ISOTROPIC_COORDINATE_SPEC.md:
 * - Origin (0,0) is the center of the image.
 * - Short edge is scaled to [-0.5, 0.5].
 * - Long edge exceeds [-0.5, 0.5].
 */
object IcrsMath {

    /**
     * Convert absolute pixel coordinates to ICRS.
     */
    fun pixelToIcrs(px: Float, py: Float, imgW: Int, imgH: Int): PointF {
        val s = minOf(imgW, imgH).toFloat()
        if (s <= 0) return PointF(0f, 0f)

        val icrsX = (px - (imgW / 2f)) / s
        val icrsY = (py - (imgH / 2f)) / s
        return PointF(icrsX, icrsY)
    }

    /**
     * Convert ICRS coordinates back to absolute pixels.
     */
    fun icrsToPixel(icrsX: Float, icrsY: Float, imgW: Int, imgH: Int): PointF {
        val s = minOf(imgW, imgH).toFloat()
        if (s <= 0) return PointF(imgW / 2f, imgH / 2f)

        val px = (icrsX * s) + (imgW / 2f)
        val py = (icrsY * s) + (imgH / 2f)
        return PointF(px, py)
    }

    /**
     * Returns the ICRS rect representing the full image (no crop restriction).
     * Computed via pixelToIcrs corners so it is always consistent with the math.
     */
    fun fullImageIcrsRect(imgW: Int, imgH: Int): RectF {
        val s = minOf(imgW, imgH).toFloat()
        if (s <= 0) return RectF(0f, 0f, 0f, 0f)
        val l = (0f - imgW / 2f) / s
        val t = (0f - imgH / 2f) / s
        val r = (imgW.toFloat() - imgW / 2f) / s
        val b = (imgH.toFloat() - imgH / 2f) / s
        return RectF(l, t, r, b)
    }

}
