package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect

object PhotoAlignmentUtils {

    /**
     * Stage 1 placeholder for automatic alignment of fill-up photo to reference.
     * Will be expanded with real rotation/scale logic in the next patch.
     */
    fun alignToReference(
        fillupBitmap: Bitmap,
        referenceCrop: Rect?
    ): Pair<Bitmap, RectF?> {
        if (referenceCrop == null) {
            return Pair(fillupBitmap, null)
        }

        // Placeholder: return original for now. Real alignment (rotation + scale) will be added next.
        val adjustedCrop = RectF(
            referenceCrop.left * fillupBitmap.width,
            referenceCrop.top * fillupBitmap.height,
            referenceCrop.right * fillupBitmap.width,
            referenceCrop.bottom * fillupBitmap.height
        )

        return Pair(fillupBitmap, adjustedCrop)
    }
}
