package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 60: Transparent Hybrid Engine.
 * Implements parent-child traceability between Paddle (Discovery) and ML Kit (Refinement).
 * Corrects coordinate projection bug and disables internal Paddle recursion for cleanliness.
 */
class HybridOcrEngine(private val context: Context) : OcrEngine {
    override val name = "Paddle-ML-Hybrid"
    private val paddleDiscovery = NativePaddleEngine(context, isConstrained = false)
    private val mlKitRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        
        // 1. DISCOVERY: Run primary Paddle pass.
        // We pass isRecursive=true to ensure Paddle only performs the fast 320x320 pass.
        // ML Kit will handle all word-level splitting.
        val discoveryResult = paddleDiscovery.recognize(bitmap, isRecursive = true)
        val finalBlocks = mutableListOf<TextBlock>()
        val sb = StringBuilder()

        for (originalBlock in discoveryResult.textBlocks) {
            val zone = originalBlock.boundingBox
            if (zone.width() < 1 || zone.height() < 1) continue

            // 2. CONTEXT ZONE: Grow by 10px on each side for ML Kit
            val padL = max(0, zone.left - 10)
            val padT = max(0, zone.top - 10)
            val padR = min(bitmap.width, zone.right + 10)
            val padB = min(bitmap.height, zone.bottom + 10)
            
            val contextRect = Rect(padL, padT, padR, padB)
            val normContextRect = RectF(
                contextRect.left.toFloat() / bitmap.width,
                contextRect.top.toFloat() / bitmap.height,
                contextRect.right.toFloat() / bitmap.width,
                contextRect.bottom.toFloat() / bitmap.height
            )
            
            val paddedCrop = Bitmap.createBitmap(bitmap, padL, padT, contextRect.width(), contextRect.height())
            val image = InputImage.fromBitmap(paddedCrop, 0)
            
            try {
                val mlResult = mlKitRecognizer.process(image).await()
                val elements = mlResult.textBlocks.flatMap { it.lines }.flatMap { it.elements }.filter { it.text.isNotBlank() }
                
                if (elements.isEmpty()) {
                    // CASE: ML Kit found nothing. Keep original Paddle suspicion with the padded context zone.
                    finalBlocks.add(originalBlock.copy(refinedDiscoveryBox = normContextRect))
                } else if (elements.size == 1) {
                    // CASE: Single Word. Unified card with all three chips.
                    val element = elements[0]
                    val eBox = element.boundingBox ?: Rect(0,0,0,0)
                    val globalPrecisionRect = Rect(padL + eBox.left, padT + eBox.top, padL + eBox.right, padT + eBox.bottom)
                    
                    finalBlocks.add(TextBlock(
                        text = element.text.trim(),
                        boundingBox = globalPrecisionRect, // YELLOW CHIP
                        rawDiscoveryBox = originalBlock.rawDiscoveryBox, // RED CHIP
                        refinedDiscoveryBox = normContextRect // ORANGE CHIP (10px padded context)
                    ))
                    sb.append(element.text).append(" ")
                } else {
                    // CASE: Multiple Words. Parent (Context) + Children (Word-Level).
                    // Add Parent Container card (RED and ORANGE chips only)
                    finalBlocks.add(TextBlock(
                        text = "",
                        boundingBox = Rect(0,0,0,0), // No Yellow
                        rawDiscoveryBox = originalBlock.rawDiscoveryBox, // RED
                        refinedDiscoveryBox = normContextRect // ORANGE
                    ))
                    
                    for (element in elements) {
                        val eBox = element.boundingBox ?: continue
                        val globalPrecisionRect = Rect(padL + eBox.left, padT + eBox.top, padL + eBox.right, padT + eBox.bottom)
                        
                        // Add Child Card (YELLOW chip only)
                        finalBlocks.add(TextBlock(
                            text = element.text.trim(),
                            boundingBox = globalPrecisionRect, // YELLOW
                            rawDiscoveryBox = null,
                            refinedDiscoveryBox = null
                        ))
                        sb.append(element.text).append(" ")
                    }
                }

            } catch (e: Exception) {
                finalBlocks.add(originalBlock.copy(refinedDiscoveryBox = normContextRect))
            } finally {
                paddedCrop.recycle()
            }
        }

        return@withContext OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            discoveryTimeMs = discoveryResult.discoveryTimeMs,
            debugText = sb.toString().trim(),
            textBlocks = finalBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            rawDiscoveryBoxes = discoveryResult.rawDiscoveryBoxes
        )
    }
}
