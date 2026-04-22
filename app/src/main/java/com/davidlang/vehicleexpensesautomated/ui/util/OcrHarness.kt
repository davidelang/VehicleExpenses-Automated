package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap

/**
 * Orchestrates ML Kit discovery pass.
 */
object OcrHarness {

    suspend fun runDiscovery(bitmap: Bitmap, context: Context): OcrResult {
        // MANDATE: Apply Grayscale and Bilateral filter GLOBALLY before discovery pass
        val gray = OdometerOcrUtils.applyGrayscale(bitmap)
        val filtered = OdometerOcrUtils.applyBilateral(gray)
        gray.recycle()

        // Phase 55: ML Kit is the sole discovery engine
        val rawResult = MlKitEngine().recognize(filtered)
        
        // Phase 32: MANDATORY Discovery-Stage Sanitization
        val cleanedBlocks = rawResult.textBlocks.map { block ->
            block.copy(text = OdometerOcrUtils.cleanLandmarkString(block.text))
        }.filter { it.text.length > 1 }
        
        val sanitizedResult = rawResult.copy(
            textBlocks = cleanedBlocks,
            debugText = cleanedBlocks.joinToString(" ") { it.text }
        )
        
        filtered.recycle()
        return sanitizedResult
    }

    suspend fun runRefinement(bitmap: Bitmap, context: Context): Map<String, OcrResult> {
        // Refinement also benefits from the same clean input
        val gray = OdometerOcrUtils.applyGrayscale(bitmap)
        val filtered = OdometerOcrUtils.applyBilateral(gray)
        gray.recycle()

        val enginesList = mutableListOf<OcrEngine>(TesseractEngine(), MlKitEngine())
        
        // Add Paddle-Lite for refinement if available
        val paddle = NativePaddleEngine(context, isConstrained = true)
        if (paddle.isAvailable) enginesList.add(paddle)

        val results = enginesList.associate { engine ->
            engine.name to engine.recognize(filtered)
        }
        
        filtered.recycle()
        return results
    }
}
