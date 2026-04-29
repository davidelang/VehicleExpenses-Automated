package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Phase 63 Increment 2: Isolated Discovery scaffold.
 * This class provides a duplicate, isolated pipeline for Paddle Discovery experiments.
 * For this increment, it is functionally identical to the standard Greedy pipeline.
 */
object DiscoveryOcrUtils {

    suspend fun runDiscoveryMultiStepOcr(
        bitmap: Bitmap,
        context: Context,
        engineName: String,
        targetHeight: Int? = null,
        paddleEngine: NativePaddleEngine? = null
    ): List<OcrStepResult> {
        val steps = mutableListOf<OcrStepResult>()
        
        // Use a dedicated local executor for this pipeline
        suspend fun exec(bmp: Bitmap, stageName: String): OcrStepResult {
            val res = when (engineName) {
                "Paddle V2 Disc (Padded)", "Paddle V3 Disc (Padded)" -> {
                    paddleEngine?.let {
                        val ocrText = NativePaddleEngine.runConstrainedStatic(
                            bmp, 
                            targetHeight ?: bmp.height, 
                            it.getDictionary(), 
                            it.isV3()
                        )
                        Pair(ocrText, emptyList<Rect>())
                    } ?: Pair(null, emptyList<Rect>())
                }
                else -> Pair(null, emptyList<Rect>())
            }
            return OcrStepResult(stageName, bmp.copy(Bitmap.Config.ARGB_8888, true), res.first, res.second)
        }

        // 1. Raw
        steps.add(exec(bitmap, "Raw"))

        // 2. Grayscale
        val gray = OdometerOcrUtils.applyGrayscale(bitmap)
        steps.add(exec(gray, "Grayscale"))
        gray.recycle()

        // 3. Bilateral
        val bile = OdometerOcrUtils.applyBilateral(bitmap)
        steps.add(exec(bile, "Bilateral"))

        // 4. Enhanced (75% Stretch)
        val s75 = OdometerOcrUtils.applyContrastStretch(bile, 75)
        steps.add(exec(s75, "Enhanced (75% Stretch)"))
        s75.recycle()

        // 5. Enhanced (80% Stretch)
        val s80 = OdometerOcrUtils.applyContrastStretch(bile, 80)
        steps.add(exec(s80, "Enhanced (80% Stretch)"))
        s80.recycle()

        bile.recycle()
        return steps
    }
}
