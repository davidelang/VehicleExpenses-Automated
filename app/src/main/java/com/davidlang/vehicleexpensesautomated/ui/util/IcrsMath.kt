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
     * Reverse Bridge:
     * Converts pure ICRS coordinates back into legacy [0.0 - 1.0] anisotropic floats.
     * Useful for feeding legacy pipeline stages during transition.
     */
    fun icrsToLegacyAnisotropic(icrsX: Float, icrsY: Float, imgW: Int, imgH: Int): PointF {
        val pix = icrsToPixel(icrsX, icrsY, imgW, imgH)
        return PointF(pix.x / imgW.toFloat(), pix.y / imgH.toFloat())
    }
}
