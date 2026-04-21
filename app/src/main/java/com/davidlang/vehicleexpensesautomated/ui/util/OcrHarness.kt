package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap

/**
 * Orchestrates multi-engine discovery passes for side-by-side comparison.
 * Uses SERIAL execution to prevent memory-induced SIGSEGV on high-res devices.
 */
object OcrHarness {

    suspend fun runDiscovery(bitmap: Bitmap, context: Context): Map<String, OcrResult> {
        // MANDATE: Apply Grayscale and Bilateral filter GLOBALLY before discovery pass
        val gray = OdometerOcrUtils.applyGrayscale(bitmap)
        val filtered = OdometerOcrUtils.applyBilateral(gray)
        gray.recycle()

        val paddleOcrTflite = PaddleOcrEngine(context, isConstrained = false)
        val nativePaddle = NativePaddleEngine(context, isConstrained = false)
        val hybridEngine = HybridOcrEngine(context)
        
        // Phase 54: All Engines + Hybrid
        val enginesList = mutableListOf<OcrEngine>(MlKitEngine(), NativeTfliteEngine(context), paddleOcrTflite)
        if (nativePaddle.isAvailable) enginesList.add(nativePaddle)
        enginesList.add(hybridEngine)

        val rawResults = enginesList.associate { engine ->
            engine.name to engine.recognize(filtered)
        }
        
        // Phase 32: MANDATORY Discovery-Stage Sanitization
        val sanitizedResults = rawResults.mapValues { (_, res) ->
            val cleanedBlocks = res.textBlocks.map { block ->
                block.copy(text = OdometerOcrUtils.cleanLandmarkString(block.text))
            }.filter { it.text.length > 1 }
            
            res.copy(
                textBlocks = cleanedBlocks,
                debugText = cleanedBlocks.joinToString(" ") { it.text }
            )
        }
        
        filtered.recycle()
        return sanitizedResults
    }

    suspend fun runRefinement(bitmap: Bitmap, context: Context): Map<String, OcrResult> {
        // Refinement also benefits from the same clean input
        val gray = OdometerOcrUtils.applyGrayscale(bitmap)
        val filtered = OdometerOcrUtils.applyBilateral(gray)
        gray.recycle()

        val paddleOcrTflite = PaddleOcrEngine(context, isConstrained = true)
        val nativePaddle = NativePaddleEngine(context, isConstrained = true)
        val enginesList = mutableListOf<OcrEngine>(TesseractEngine(), MlKitEngine(), NativeTfliteEngine(context), paddleOcrTflite)
        if (nativePaddle.isAvailable) enginesList.add(nativePaddle)

        val results = enginesList.associate { engine ->
            engine.name to engine.recognize(filtered)
        }
        
        filtered.recycle()
        return results
    }

    fun getDiscoveryEngineNames(context: Context): List<String> {
        val list = mutableListOf("ML Kit", "Native TFLite", "Paddle-TFLite")
        if (NativePaddleEngine(context).isAvailable) {
            list.add("Paddle-Lite")
            list.add("Paddle-ML-Hybrid")
        }
        return list
    }

    fun getRefinementEngineNames(context: Context): List<String> {
        val list = mutableListOf("Tesseract", "ML Kit", "Native TFLite", "Paddle-TFLite (Odo)")
        if (NativePaddleEngine(context, isConstrained = true).isAvailable) list.add("Paddle-Lite (Odo)")
        return list
    }
}
