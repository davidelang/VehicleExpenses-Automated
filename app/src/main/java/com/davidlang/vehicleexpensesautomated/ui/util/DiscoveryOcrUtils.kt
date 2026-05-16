package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Performs detection and expansion visualization.
 */
object DiscoveryOcrUtils {

    private fun unclipRect(rect: Rect, ratio: Float): Rect {
        val area = rect.width().toDouble() * rect.height().toDouble()
        val perimeter = 2.0 * (rect.width() + rect.height())
        if (perimeter <= 0) return rect
        val delta = (area * ratio / perimeter).toInt()
        return Rect(rect.left - delta, rect.top - delta, rect.right + delta, rect.bottom + delta)
    }

    private fun expandByValleyStop(rect: Rect, bmp: Bitmap): Rect {
        val dx = (rect.width() * 0.15f).toInt(); val dy = (rect.height() * 0.15f).toInt()
        return Rect(rect.left - dx, rect.top - dy, rect.right + dx, rect.bottom + dy)
    }
}
