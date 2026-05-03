package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap

/**
 * Orchestrates ML Kit discovery pass.
 */
object OcrHarness {

    suspend fun runDiscovery(bitmap: Bitmap, context: Context, paddleEngine: NativePaddleEngine? = null, useMono: Boolean = false): OcrResult {
        // MANDATE: Use existing shared forensic buffer to avoid LMK crashes
        val pTargetSize = 2048
        val pScale = pTargetSize.toFloat() / bitmap.width
        val forensicBmp = NativePaddleEngine.sharedBmp2048

        val canvas = NativePaddleEngine.sharedCanvas2048
        canvas.drawColor(android.graphics.Color.BLACK)
        NativePaddleEngine.sharedMatrix.reset()
        NativePaddleEngine.sharedMatrix.postScale(pScale, pScale)
        canvas.drawBitmap(bitmap, NativePaddleEngine.sharedMatrix, null)

        // Apply Bilateral filter to the scaled forensic view rather than the full dash photo
        val filtered = OdometerOcrUtils.applyBilateral(forensicBmp)

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

        filtered.recycle() // This is a temporary buffer from applyBilateral, safe to recycle
        return sanitizedResult
    }

    suspend fun runRefinement(bitmap: Bitmap, context: Context): Map<String, OcrResult> {
        // Refinement also benefits from the same clean input
        val filtered = OdometerOcrUtils.applyBilateral(bitmap)

        val enginesList = mutableListOf<OcrEngine>(MlKitEngine())
        
        // Add Paddle-Lite for refinement if available
        val paddle = NativePaddleEngine(context, variant = "V3")
        if (paddle.isAvailable) enginesList.add(paddle)

        val results = enginesList.associate { engine ->
            engine.name to engine.recognize(filtered)
        }
        
        filtered.recycle()
        return results
    }
}
