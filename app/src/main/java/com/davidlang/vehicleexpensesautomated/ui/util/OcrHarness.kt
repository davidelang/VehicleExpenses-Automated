package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap

/**
 * Orchestrates ML Kit discovery pass.
 */
object OcrHarness {

    suspend fun runDiscovery(bitmap: Bitmap, context: Context): OcrResult {
        // Phase 115: Use the already-filtered sharedBmpFull directly. 
        // No redundant applyBilateral() call here.
        val rawResult = MlKitEngine().recognize(bitmap)
        
        // Phase 32: MANDATORY Discovery-Stage Sanitization
        val cleanedBlocks = rawResult.textBlocks.map { block ->
            block.copy(text = OdometerOcrUtils.cleanLandmarkString(block.text))
        }.filter { it.text.length > 1 }
        
        val sanitizedResult = rawResult.copy(
            textBlocks = cleanedBlocks,
            debugText = cleanedBlocks.joinToString(" ") { it.text }
            )

            return sanitizedResult
            }

            suspend fun runRefinement(bitmap: Bitmap, context: Context): Map<String, OcrResult> {
            // Phase 115: Refinement uses the provided cropped bitmap (already filtered or raw depending on strategy)
            // No redundant bilateral filter here.
            val enginesList = mutableListOf<OcrEngine>(MlKitEngine())

            // Add Paddle-Lite for refinement if available
            val paddle = NativePaddleEngine(context, variant = "V3")
            if (paddle.isAvailable) enginesList.add(paddle)

            val results = enginesList.associate { engine ->
            engine.name to engine.recognize(bitmap)
            }

            return results
            }

}
