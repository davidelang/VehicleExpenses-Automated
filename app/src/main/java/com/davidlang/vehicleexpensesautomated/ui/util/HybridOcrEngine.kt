package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 54: Hybrid Engine.
 * Uses Paddle-Lite for high-quality discovery (Red/Orange boxes)
 * and ML Kit for robust recognition (reading text within those boxes).
 */
class HybridOcrEngine(private val context: Context) : OcrEngine {
    override val name = "Paddle-ML-Hybrid"
    private val paddleDiscovery = NativePaddleEngine(context, isConstrained = false)
    private val mlKitRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        
        // 1. DISCOVERY: Use Paddle's DBNet
        val discoveryResult = paddleDiscovery.recognize(bitmap)
        val textBlocks = mutableListOf<TextBlock>()
        val sb = StringBuilder()

        // 2. RECOGNITION: Use ML Kit on every discovered zone
        for (originalBlock in discoveryResult.textBlocks) {
            val zone = originalBlock.boundingBox
            if (zone.width() < 1 || zone.height() < 1) continue

            val crop = Bitmap.createBitmap(bitmap, zone.left, zone.top, zone.width(), zone.height())
            val image = InputImage.fromBitmap(crop, 0)
            
            try {
                val mlResult = mlKitRecognizer.process(image).await()
                val detectedText = mlResult.text.replace("\n", " ").trim()
                
                if (detectedText.isNotBlank()) {
                    sb.append(detectedText).append(" ")
                }
                
                textBlocks.add(TextBlock(
                    text = detectedText,
                    boundingBox = zone,
                    rawDiscoveryBox = originalBlock.rawDiscoveryBox,
                    refinedDiscoveryBox = originalBlock.refinedDiscoveryBox
                ))
                
                // DIAGNOSTIC LOG
                android.util.Log.i("HYBRID_CHECK", "Source: ${bitmap.width}x${bitmap.height} | Paddle: '${originalBlock.text}' | ML-Kit: '$detectedText' | Zone: $zone")
                
            } catch (e: Exception) {
                textBlocks.add(originalBlock)
            } finally {
                crop.recycle()
            }
        }

        return@withContext OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = sb.toString().trim(),
            textBlocks = textBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            rawDiscoveryBoxes = discoveryResult.rawDiscoveryBoxes
        )
    }
}
