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
 * Phase 57: Hybrid Engine.
 * Uses Paddle-Lite for initial discovery (Red/Orange boxes)
 * and ML Kit Word-Level Extraction to split fused text blocks.
 */
class HybridOcrEngine(private val context: Context) : OcrEngine {
    override val name = "Paddle-ML-Hybrid"
    private val paddleDiscovery = NativePaddleEngine(context, isConstrained = false)
    private val mlKitRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        
        // 1. DISCOVERY: Run primary Paddle DBNet pass
        val discoveryResult = paddleDiscovery.recognize(bitmap)
        val finalBlocks = mutableListOf<TextBlock>()
        val sb = StringBuilder()

        // 2. REFINEMENT: Iterate through every box found by Paddle
        for (originalBlock in discoveryResult.textBlocks) {
            val zone = originalBlock.boundingBox
            if (zone.width() < 1 || zone.height() < 1) continue

            // Fixed 10px Padding on each edge for ML Kit comfort
            val padL = max(0, zone.left - 10)
            val padT = max(0, zone.top - 10)
            val padR = min(bitmap.width, zone.right + 10)
            val padB = min(bitmap.height, zone.bottom + 10)
            
            val paddedCrop = Bitmap.createBitmap(bitmap, padL, padT, padR - padL, padB - padT)
            val image = InputImage.fromBitmap(paddedCrop, 0)
            
            try {
                val mlResult = mlKitRecognizer.process(image).await()
                
                // 3. WORD EXTRACTION: Look inside the crop for individual words/elements
                var foundAnyWord = false
                for (line in mlResult.textBlocks.flatMap { it.lines }) {
                    for (element in line.elements) {
                        val elementBox = element.boundingBox ?: continue
                        
                        // Map local crop coordinates back to global dashboard space
                        val globalRect = Rect(
                            padL + elementBox.left,
                            padT + elementBox.top,
                            padL + elementBox.right,
                            padT + elementBox.bottom
                        )
                        
                        val detectedText = element.text.trim()
                        if (detectedText.isNotBlank()) {
                            finalBlocks.add(TextBlock(
                                text = detectedText,
                                boundingBox = globalRect,
                                rawDiscoveryBox = originalBlock.rawDiscoveryBox, // Link to original suspicion
                                refinedDiscoveryBox = RectF(
                                    globalRect.left.toFloat() / bitmap.width,
                                    globalRect.top.toFloat() / bitmap.height,
                                    globalRect.right.toFloat() / bitmap.width,
                                    globalRect.bottom.toFloat() / bitmap.height
                                )
                            ))
                            sb.append(detectedText).append(" ")
                            foundAnyWord = true
                            
                            Log.i("HYBRID_REFINED", "Extracted Word: '$detectedText' at $globalRect")
                        }
                    }
                }
                
                // Fallback: If ML Kit found nothing, keep the original Paddle box
                if (!foundAnyWord) {
                    finalBlocks.add(originalBlock)
                }

            } catch (e: Exception) {
                finalBlocks.add(originalBlock)
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
