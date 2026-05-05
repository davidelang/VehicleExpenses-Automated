package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap

/**
 * Orchestrates ML Kit discovery pass.
 */
object OcrHarness {

    suspend fun runDiscovery(bitmap: Bitmap, context: Context): OcrResult {
        // MANDATE: Apply Bilateral filter GLOBALLY before discovery pass
        // To avoid allocation, we use the shared working buffer
        val workingBmp = NativePaddleEngine.sharedBmp2048
        val workingCanvas = NativePaddleEngine.sharedCanvas2048
        
        val filtered = synchronized(workingBmp) {
            workingBmp.eraseColor(0)
            workingCanvas.drawBitmap(bitmap, 0f, 0f, null)
            OdometerOcrUtils.applyBilateralInPlace(workingBmp)
            workingBmp
        }

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
        
        return sanitizedResult
    }

    suspend fun runRefinement(bitmap: Bitmap, context: Context): Map<String, OcrResult> {
        // Refinement also benefits from the same clean input
        val workingBmp = NativePaddleEngine.sharedBmpSmall // Small is enough for odometer crops
        val workingCanvas = NativePaddleEngine.sharedCanvasSmall
        
        val filtered = synchronized(workingBmp) {
            workingBmp.eraseColor(0)
            workingCanvas.drawBitmap(bitmap, 0f, 0f, null)
            OdometerOcrUtils.applyBilateralInPlace(workingBmp)
            workingBmp
        }

        val enginesList = mutableListOf<OcrEngine>(MlKitEngine())
        
        // Add Paddle-Lite for refinement if available
        val paddle = NativePaddleEngine(context, variant = "V3")
        if (paddle.isAvailable) enginesList.add(paddle)

        val results = enginesList.associate { engine ->
            engine.name to engine.recognize(filtered)
        }
        
        return results
    }
}
