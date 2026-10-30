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


    /**
     * Returns the ICRS rect representing the full image (no crop restriction).
     * Computed via pixelToIcrs corners so it is always consistent with the math.
     */
    fun IcrsMath.fullImageIcrsRect(imgW: Int, imgH: Int): RectF {
        val s = minOf(imgW, imgH).toFloat()
        if (s <= 0) return RectF(0f, 0f, 0f, 0f)
        val l = (0f - imgW / 2f) / s
        val t = (0f - imgH / 2f) / s
        val r = (imgW.toFloat() - imgW / 2f) / s
        val b = (imgH.toFloat() - imgH / 2f) / s
        return RectF(l, t, r, b)
    }

