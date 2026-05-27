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
     * Legacy Anisotropic Bridge:
     * Converts legacy [0.0 - 1.0] normalized coordinates (where X is /W and Y is /H)
     * into pure ICRS coordinates.
     */
    fun legacyAnisotropicToIcrs(lx: Float, ly: Float, imgW: Int, imgH: Int): PointF {
        val px = lx * imgW
        val py = ly * imgH
        return pixelToIcrs(px, py, imgW, imgH)
    }

    /**
     * Converts a legacy [0.0 - 1.0] top-left RectF into an ICRS centered RectF.
     */
    fun legacyAnisotropicToIcrs(legacy: RectF, imgW: Int, imgH: Int): RectF {
        val p1 = legacyAnisotropicToIcrs(legacy.left, legacy.top, imgW, imgH)
        val p2 = legacyAnisotropicToIcrs(legacy.right, legacy.bottom, imgW, imgH)
        return RectF(p1.x, p1.y, p2.x, p2.y)
    }
}
