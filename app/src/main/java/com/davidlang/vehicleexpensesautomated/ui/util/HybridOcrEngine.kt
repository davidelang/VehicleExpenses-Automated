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
 * Phase 26: Transparent Hybrid Engine (64px Normalized).
 * Scales discovery crops to 64px height for testing recognition quality.
 */
class HybridOcrEngine(private val context: Context) : OcrEngine {
    override val name = "Paddle-ML-Hybrid"
    private val paddleDiscovery = NativePaddleEngine(context, variant = "V3")
    private val mlKitRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(input: Any): OcrResult = withContext(Dispatchers.IO) {
        val bitmap = input as Bitmap
        val t0 = System.currentTimeMillis()
        
        // 1. DISCOVERY: Run primary Paddle pass
        val discoveryResult = paddleDiscovery.recognize(bitmap)
        val finalBlocks = mutableListOf<TextBlock>()
        val sb = StringBuilder()

        for (originalBlock in discoveryResult.textBlocks) {
            val zone = originalBlock.boundingBox
            if (zone.width() < 1 || zone.height() < 1) continue

            // 2. CONTEXT ZONE: Grow by 10px on each side
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
            
            val originalCrop = Bitmap.createBitmap(bitmap, padL, padT, contextRect.width(), contextRect.height())
            
            // 3. NORMALIZATION: Scale to 64px height
            val targetH = 64
            val scaleTo64 = targetH.toFloat() / originalCrop.height.toFloat()
            val invScale = originalCrop.height.toDouble() / targetH.toDouble()
            
            val normalizedCrop = Bitmap.createScaledBitmap(
                originalCrop, 
                max(1, (originalCrop.width * scaleTo64).toInt()), 
                targetH, 
                true
            )
            originalCrop.recycle()

            val image = InputImage.fromBitmap(normalizedCrop, 0)
            
            try {
                val mlResult = mlKitRecognizer.process(image).await()
                val elements = mlResult.textBlocks.flatMap { it.lines }.flatMap { it.elements }.filter { it.text.isNotBlank() }
                
                if (elements.isEmpty()) {
                    finalBlocks.add(originalBlock.copy(refinedDiscoveryBox = normContextRect))
                } else if (elements.size == 1) {
                    val element = elements[0]
                    val eBox = element.boundingBox ?: Rect(0,0,0,0)
                    
                    // Map from 64px space back to Original space, then to Global space
                    val globalPrecisionRect = Rect(
                        padL + (eBox.left * invScale).toInt(),
                        padT + (eBox.top * invScale).toInt(),
                        padL + (eBox.right * invScale).toInt(),
                        padT + (eBox.bottom * invScale).toInt()
                    )
                    
                    val text = element.text.trim()
                    
                    // Phase 33: Restore diagnostic tracing for Hybrid engine
                    Log.i("OCR_TRACE", "Engine: $name | Source: ${bitmap.width}x${bitmap.height} | Text: '$text' | ORANGE: [W=${contextRect.width()}, H=${contextRect.height()}, L=$padL, T=$padT] | YELLOW: [W=${globalPrecisionRect.width()}, H=${globalPrecisionRect.height()}, L=${globalPrecisionRect.left}, T=${globalPrecisionRect.top}]")

                    finalBlocks.add(TextBlock(
                        text = text,
                        boundingBox = globalPrecisionRect,
                        rawDiscoveryBox = originalBlock.rawDiscoveryBox,
                        refinedDiscoveryBox = normContextRect
                    ))
                    sb.append(text).append(" ")
                } else {
                    // Multi-Word Parent (Partial cleanup/logic same as above for logging)
                    finalBlocks.add(TextBlock(
                        text = "",
                        boundingBox = Rect(0,0,0,0),
                        rawDiscoveryBox = originalBlock.rawDiscoveryBox,
                        refinedDiscoveryBox = normContextRect
                    ))
                    
                    for (element in elements) {
                        val eBox = element.boundingBox ?: continue
                        val globalPrecisionRect = Rect(
                            padL + (eBox.left * invScale).toInt(),
                            padT + (eBox.top * invScale).toInt(),
                            padL + (eBox.right * invScale).toInt(),
                            padT + (eBox.bottom * invScale).toInt()
                        )
                        
                        val text = element.text.trim()
                        Log.i("OCR_TRACE", "Engine: $name (MW) | Text: '$text' | YELLOW: [W=${globalPrecisionRect.width()}, H=${globalPrecisionRect.height()}, L=${globalPrecisionRect.left}, T=${globalPrecisionRect.top}]")

                        finalBlocks.add(TextBlock(
                            text = text,
                            boundingBox = globalPrecisionRect,
                            rawDiscoveryBox = null,
                            refinedDiscoveryBox = null
                        ))
                        sb.append(text).append(" ")
                    }
                }

            } catch (e: Exception) {
                finalBlocks.add(originalBlock.copy(refinedDiscoveryBox = normContextRect))
            } finally {
                normalizedCrop.recycle()
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
