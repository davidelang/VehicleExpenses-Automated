package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle

/**
 * Common interface for all image alignment algorithms.
 */
interface AlignmentEngine {
    val name: String

    /**
     * Aligns the query image to the reference image.
     * @return AlignmentResult containing the warped bitmap and transformation metadata.
     */
    suspend fun align(
        reference: Bitmap,
        query: Bitmap,
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle
    ): AlignmentResult
}

/**
 * ORB-based 4-DOF Affine alignment.
 */
class OrbAffineEngine : AlignmentEngine {
    override val name: String = "ORB"

    override suspend fun align(
        reference: Bitmap,
        query: Bitmap,
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle
    ): AlignmentResult {
        return ImageAlignmentUtils.alignImages(reference, query, refLandmarks, queryLandmarks, vehicle)
    }
}

/**
 * Landmark-based triangulation alignment.
 */
class AnchorTriangulationEngine : AlignmentEngine {
    override val name: String = "Anchor-Tri"

    override suspend fun align(
        reference: Bitmap,
        query: Bitmap,
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle
    ): AlignmentResult {
        val res = ImageAlignmentUtils.anchorAlign(reference, query, refLandmarks, queryLandmarks, vehicle)
        // Convert AnchorResult to AlignmentResult for interface parity
        return AlignmentResult(
            success = res.success,
            alignedImage = res.alignedImage,
            confidence = res.confidence,
            message = res.message,
            method = "anchor",
            timeMs = res.timeMs,
            metadata = res.metadata
        )
    }
}

/**
 * Hough Circles based alignment (Hub discovery).
 */
class HubEngine : AlignmentEngine {
    override val name: String = "Hub"

    override suspend fun align(
        reference: Bitmap,
        query: Bitmap,
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle
    ): AlignmentResult {
        return ImageAlignmentUtils.hubAlign(reference, query, refLandmarks, queryLandmarks, vehicle)
    }
}
