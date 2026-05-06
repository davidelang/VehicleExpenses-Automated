package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    /**
     * Phase 109: Instance tracking for landmark disambiguation.
     * -1: Unmapped/Initial state (default for discovered landmarks).
     *  0: Globally unique landmark (appears only once in the vehicle manifest).
     * 1+: Specific instance of a duplicate landmark (1st, 2nd, etc. occurrence).
     */
    val instanceId: Int = -1
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
        val t0 = System.currentTimeMillis()
        val res = OdometerOcrUtils.extractFromPhotoBitmapRaw(bitmap)
        res.copy(engineName = name, executionTimeMs = System.currentTimeMillis() - t0)
    }
}

object OcrUtils {
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
        // Phase 115: Proportional fit-within scaling
        val scale = kotlin.math.min(320f / source.width.toFloat(), 48f / source.height.toFloat())
        val targetWidth = (source.width * scale).toInt().coerceIn(1, 320)
        val targetHeight = (source.height * scale).toInt().coerceIn(1, 48)
        
        val canvas = NativePaddleEngine.sharedReportCanvas
        val thumb = NativePaddleEngine.sharedReportBitmap
        
        // 1. Clear and Draw thumbnail
        canvas.drawColor(android.graphics.Color.BLACK)
        val destRect = Rect(0, 0, targetWidth, targetHeight)
        
        // Phase 115: Monochrome-Aware Rendering
        val paint = if (source.config == Bitmap.Config.ALPHA_8) NativePaddleEngine.alphaToGrayPaint else null
        canvas.drawBitmap(source, null, destRect, paint)
        
        // 2. Apply Annotations
        rawFragments.forEach { r -> 
            canvas.drawRect(r.left * scale, r.top * scale, r.right * scale, r.bottom * scale, NativePaddleEngine.redPaint)
        }
        consolidatedRows.forEach { r ->
            canvas.drawRect(r.left * scale, r.top * scale, r.right * scale, r.bottom * scale, NativePaddleEngine.orangePaint)
        }
        
        // 3. Create Subset View for Base64 (No allocation)
        val view = Bitmap.createBitmap(thumb, 0, 0, targetWidth, targetHeight)
        bitmapToBase64(view, 60)
    }
}
