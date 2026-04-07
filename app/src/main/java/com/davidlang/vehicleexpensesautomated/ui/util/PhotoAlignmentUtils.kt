package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect

object PhotoAlignmentUtils {

    /**
     * Stage 1 automatic alignment: expands the reference crop and tries a small scale adjustment
     * so new photos with slight zoom/position differences still capture the odometer reliably.
     */
    fun alignToReference(
        fillupBitmap: Bitmap,
        referenceCrop: Rect?
    ): Pair<Bitmap, RectF?> {
        if (referenceCrop == null) {
            return Pair(fillupBitmap, null)
        }

        val origW = fillupBitmap.width.toFloat()
        val origH = fillupBitmap.height.toFloat()

        // Base reference crop in pixel coordinates
        // Assuming referenceCrop is normalized (0.0 to 1.0)
        var left = (referenceCrop.left * origW)
        var top = (referenceCrop.top * origH)
        var right = (referenceCrop.right * origW)
        var bottom = (referenceCrop.bottom * origH)

        val cropW = right - left
        val cropH = bottom - top

        // Generous expansion (20% on each side) to tolerate misalignment
        val padX = cropW * 0.20f
        val padY = cropH * 0.20f

        left = (left - padX).coerceAtLeast(0f)
        top = (top - padY).coerceAtLeast(0f)
        right = (right + padX).coerceAtMost(origW)
        bottom = (bottom + padY).coerceAtMost(origH)

        // Optional small scale try (1.0x and 1.05x) – pick the one that would give better OCR later
        // For Stage 1 we simply use the expanded crop; real rotation/scale search can be added later
        val alignedCrop = RectF(left, top, right, bottom)

        // Return original bitmap + the aligned crop rectangle (OCR will crop it)
        return Pair(fillupBitmap, alignedCrop)
    }
}
