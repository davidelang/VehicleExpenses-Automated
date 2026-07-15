package com.davidlang.vehicleexpensesautomated.ui.util

import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionSelector
import kotlin.math.abs

enum class CameraCaptureProfile {
    /** Quick Fill / vehicle OCR: pick native size closest to ~2040px width. */
    OCR_MEDIUM,
    /** Expense receipts: largest native resolution available. */
    RECEIPT_MAX,
}

object CameraResolutionPicker {

    private const val OCR_TARGET_WIDTH = 2040

    fun resolutionSelector(profile: CameraCaptureProfile): ResolutionSelector {
        return ResolutionSelector.Builder()
            .setResolutionFilter { supportedSizes, _ ->
                val chosen = when (profile) {
                    CameraCaptureProfile.OCR_MEDIUM ->
                        pickClosestWidth(supportedSizes, OCR_TARGET_WIDTH)
                    CameraCaptureProfile.RECEIPT_MAX ->
                        supportedSizes.maxByOrNull { it.width.toLong() * it.height } ?: supportedSizes.first()
                }
                listOf(chosen)
            }
            .build()
    }

    private fun pickClosestWidth(supportedSizes: List<Size>, targetWidth: Int): Size {
        if (supportedSizes.isEmpty()) return Size(targetWidth, targetWidth * 3 / 4)
        return supportedSizes.minByOrNull { abs(it.width - targetWidth) } ?: supportedSizes.first()
    }
}