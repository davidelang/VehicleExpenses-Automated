package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import org.opencv.core.Point

/**
 * Simple Rect implementation for Float normalized coordinates.
 */
data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Represents a detected text region with both rotated points and axis-aligned bounds.
 * Mandated: All coordinates (points and boundingBox) are NORMALIZED (0.0 to 1.0).
 */
data class DetectedBox(
    val points: List<Point>,
    val boundingBox: RectF,
    val angle: Float
)

/**
 * Result of a DBNet discovery pass, containing raw suspicion and refined regions.
 */
data class DbNetResult(
    val rawBoxes: List<DetectedBox>,
    val refinedBoxes: List<DetectedBox>,
    val discoveryTimeMs: Long = 0,
    val suspectCrops: List<RectF> = emptyList() // Phase 43: High-Res Sub-Window Targets
)

/**
 * Mandated: Normalized Rectangle (0.0 to 1.0)
 */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Represents a single hunk of text found by an OCR engine.
 */
data class TextBlock(
    val text: String,
    val boundingBox: Rect, // Final Crop Pixel coordinates
    val angle: Float = 0f,
    val points: List<org.opencv.core.Point> = emptyList(),
    val rawDiscoveryBox: RectF? = null,    // RED tier
    val refinedDiscoveryBox: RectF? = null, // ORANGE tier
    val metadata: Map<String, String> = emptyMap(),
    val instanceId: Int = -1 // Phase 91
)

/**
 * Encapsulates the results of an OCR operation.
 */
data class OcrResult(
    val engineName: String = "Unknown",
    val executionTimeMs: Long = 0,
    val discoveryTimeMs: Long = 0, // NEW PROFILING METRIC
    val odometer: String? = null,
    val possibleOdometers: List<String> = emptyList(),
    val gallons: String? = null,
    val cost: String? = null,
    val debugText: String,
    val errorMessage: String? = null,
    val originalPhotoPath: String? = null,
    val croppedBitmap: Bitmap? = null,
    val openCvProcessedBitmap: Bitmap? = null,
    val rawHeatmap: FloatArray? = null,
    val heatmapWidth: Int = 0,
    val heatmapHeight: Int = 0,
    val discoveryHeatmap: FloatArray? = null,
    val rawDiscoveryBoxes: List<RectF> = emptyList(),
    val scaleFactor: Float = 1.0f,
    val textBlocks: List<TextBlock> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun filterByCrops(odoCrop: android.graphics.RectF?, otherCrop: android.graphics.RectF?): OcrResult {
        val filteredBlocks = textBlocks.filter { block ->
            if (imageWidth <= 0 || imageHeight <= 0) return@filter true
            val cx = block.boundingBox.centerX().toFloat() / imageWidth
            val cy = block.boundingBox.centerY().toFloat() / imageHeight
            val inOdo = odoCrop?.let { cx >= it.left && cx <= it.right && cy >= it.top && cy <= it.bottom } ?: false
            val inOther = otherCrop?.let { cx >= it.left && cx <= it.right && cy >= it.top && cy <= it.bottom } ?: false
            !inOdo && !inOther
        }
        return this.copy(
            textBlocks = filteredBlocks,
            debugText = filteredBlocks.filter { it.text.isNotBlank() }.joinToString(" ") { it.text }
        )
    }
}

data class OcrStepResult(
    val stageName: String, 
    val thumbB64: String, 
    val ocrInputB64: String? = null, // Phase 63: Exact 320x48 buffer passed to model
    val text: String?, 
    val boxes: List<Rect> = emptyList(), 
    val normalizedBoxes: List<TextBlock> = emptyList(), 
    val rawBox: Rect? = null, 
    val refinedBox: Rect? = null
)

enum class DiscoveryExpansion { UNCLIP, VALLEY }

interface OcrEngine {
    val name: String
    suspend fun recognize(bitmap: Bitmap): OcrResult
}

class MlKitEngine : OcrEngine {
    override val name = "ML Kit"
    
    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        if (bitmap.config == Bitmap.Config.ALPHA_8) {
            return@withContext recognizeMono(bitmap)
        }
        val t0 = System.currentTimeMillis()
        val res = OdometerOcrUtils.extractFromPhotoBitmap(bitmap)
        res.copy(engineName = name, executionTimeMs = System.currentTimeMillis() - t0)
    }

    private suspend fun recognizeMono(bitmap: Bitmap): OcrResult {
        val t0 = System.currentTimeMillis()
        val w = bitmap.width
        val h = bitmap.height
        
        // 1. Convert to NV21 via shared helper
        val nv21 = OcrUtils.bitmapToNv21(bitmap)
        val buffer = java.nio.ByteBuffer.wrap(nv21)
        val image = com.google.mlkit.vision.common.InputImage.fromByteBuffer(
            buffer, w, h, 0, com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21
        )
        
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val visionText = recognizer.process(image).await()
            val blocks = mutableListOf<TextBlock>()
            val sb = StringBuilder()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val box = element.boundingBox
                        if (box != null) {
                            blocks.add(TextBlock(element.text, box, line.angle))
                            sb.append(element.text).append(" ")
                        }
                    }
                }
            }
            OcrResult(
                engineName = "$name Mono",
                executionTimeMs = System.currentTimeMillis() - t0,
                debugText = sb.toString().trim(),
                textBlocks = blocks,
                imageWidth = w,
                imageHeight = h
            )
        } catch (e: Exception) {
            Log.e("MlKitEngine", "Mono OCR failed", e)
            OcrResult(engineName = "$name Mono", debugText = "Error: ${e.message}", imageWidth = w, imageHeight = h)
        }
    }
}

object OcrUtils {
    /**
     * Converts a Bitmap to NV21 format (YUV 4:2:0) suitable for ML Kit.
     * This ensures 1-channel (Grayscale) input is handled efficiently.
     * @param bitmap The source bitmap (ARGB_8888 or ALPHA_8).
     * @return A ByteArray containing NV21 data.
     */
    fun bitmapToNv21(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val frameSize = w * h
        val nv21 = ByteArray(frameSize * 3 / 2)
        
        val pixels = IntArray(frameSize)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        
        if (bitmap.config == Bitmap.Config.ALPHA_8) {
            for (i in 0 until frameSize) {
                nv21[i] = (pixels[i] shr 24 and 0xFF).toByte()
            }
        } else {
            for (i in 0 until frameSize) {
                // Input is grayscale ARGB, so R=G=B. Take Red.
                nv21[i] = (pixels[i] shr 16 and 0xFF).toByte()
            }
        }
        
        // Neutral Chroma (U=128, V=128)
        for (i in frameSize until nv21.size) {
            nv21[i] = 128.toByte()
        }
        return nv21
    }

    fun isBlockInCrop(block: TextBlock, crop: android.graphics.RectF?, w: Int, h: Int): Boolean {
        if (crop == null || w == 0 || h == 0) return false
        val cx = block.boundingBox.centerX().toFloat() / w; val cy = block.boundingBox.centerY().toFloat() / h
        return cx >= crop.left && cx <= crop.right && cy >= crop.top && cy <= crop.bottom
    }

    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
    }

    /**
     * Captures a 48px high thumbnail into the shared reporting buffer and returns its Base64 representation.
     * This is used to implement zero-allocation late-stage annotation.
     */
    fun takeSnapshot(
        source: Bitmap, 
        rawFragments: List<Rect> = emptyList(), 
        consolidatedRows: List<Rect> = emptyList()
    ): String = synchronized(NativePaddleEngine.sharedReportBitmap) {
        val scale = 48f / source.height
        val targetWidth = (source.width * scale).toInt().coerceAtMost(320)
        val canvas = NativePaddleEngine.sharedReportCanvas
        val thumb = NativePaddleEngine.sharedReportBitmap
        
        // 1. Clear and Draw thumbnail
        canvas.drawColor(android.graphics.Color.BLACK)
        val destRect = Rect(0, 0, targetWidth, 48)
        canvas.drawBitmap(source, null, destRect, null)
        
        // 2. Apply Annotations
        rawFragments.forEach { r -> 
            canvas.drawRect(r.left * scale, r.top * scale, r.right * scale, r.bottom * scale, NativePaddleEngine.redPaint)
        }
        consolidatedRows.forEach { r ->
            canvas.drawRect(r.left * scale, r.top * scale, r.right * scale, r.bottom * scale, NativePaddleEngine.orangePaint)
        }
        
        // 3. Create Subset View for Base64 (No allocation)
        val view = Bitmap.createBitmap(thumb, 0, 0, targetWidth, 48)
        bitmapToBase64(view, 60)
    }
}
