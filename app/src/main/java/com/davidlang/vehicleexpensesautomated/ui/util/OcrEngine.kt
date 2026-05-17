package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
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
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

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
    val refinedBox: Rect? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class Shape { LINE, RECTANGLE }

data class SnapshotAnnotation(
    val x1: Int, val y1: Int, val x2: Int, val y2: Int,
    val shape: Shape,
    val color: Int, // ARGB color
    val strokeWidth: Int
)

enum class DiscoveryExpansion { UNCLIP, VALLEY }

interface OcrEngine {
    val name: String
    suspend fun recognize(input: Any): OcrResult
}

class MlKitEngine : OcrEngine {
    override val name = "ML Kit"
    override suspend fun recognize(input: Any): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        
        val image = when (input) {
            is Bitmap -> com.google.mlkit.vision.common.InputImage.fromBitmap(input, 0)
            is BufferSet.Instance -> com.google.mlkit.vision.common.InputImage.fromByteBuffer(
                input.nv21,
                input.yMat.cols(),
                input.yMat.rows(),
                0,
                com.google.mlkit.vision.common.InputImage.IMAGE_FORMAT_NV21
            )
            else -> throw IllegalArgumentException("Unsupported input type for MlKitEngine: ${input.javaClass.name}")
        }

        val res = OdometerOcrUtils.extractFromPhotoBitmapRaw(image)
        res.copy(engineName = name, executionTimeMs = System.currentTimeMillis() - t0)
    }
}

object OcrUtils {
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
    }

    fun isBlockInCrop(block: TextBlock, crop: android.graphics.RectF?, w: Int, h: Int): Boolean {
        if (crop == null || w == 0 || h == 0) return false
        val cx = block.boundingBox.centerX().toFloat() / w; val cy = block.boundingBox.centerY().toFloat() / h
        return cx >= crop.left && cx <= crop.right && cy >= crop.top && cy <= crop.bottom
    }

    suspend fun takeSnapshot(
        source: Any,
        sourceRect: Rect?,
        targetW: Int,
        targetH: Int,
        annotations: List<SnapshotAnnotation>
    ): String = withContext(Dispatchers.IO) {
        val rowBuffer = NativePaddleEngine.fullBufferSet
        val workspace = rowBuffer.scratch
        
        // Step 1: Geometry Normalization
        val srcW = when (source) {
            is Bitmap -> source.width
            is org.opencv.core.Mat -> source.cols()
            is BufferSet.Instance -> source.yMat.cols()
            else -> 0
        }
        val srcH = when (source) {
            is Bitmap -> source.height
            is org.opencv.core.Mat -> source.rows()
            is BufferSet.Instance -> source.yMat.rows()
            else -> 0
        }
        
        val roi = sourceRect ?: Rect(0, 0, srcW, srcH)
        val roiW = roi.width().coerceAtLeast(1)
        val roiH = roi.height().coerceAtLeast(1)
        val sourceAspect = roiW.toFloat() / roiH.toFloat()
        
        val toEven = { v: Float -> ((v + 1).toInt() / 2) * 2 }

        var finalW: Int
        var finalH: Int
        
        if (targetW > 0 && targetH > 0) {
            if (targetW.toFloat() / targetH > sourceAspect) {
                finalH = targetH; finalW = (targetH * sourceAspect).toInt()
            } else {
                finalW = targetW; finalH = (targetW / sourceAspect).toInt()
            }
        } else if (targetW > 0) {
            finalW = targetW; finalH = (targetW / sourceAspect).toInt()
        } else if (targetH > 0) {
            finalH = targetH; finalW = (targetH * sourceAspect).toInt()
        } else {
            finalW = roiW; finalH = roiH
        }
        
        // Cap to scratch buffer dimensions and ensure 2-pixel alignment
        finalW = toEven(finalW.toFloat().coerceIn(2f, workspace.yMat.cols().toFloat()))
        finalH = toEven(finalH.toFloat().coerceIn(2f, workspace.yMat.rows().toFloat()))

        // Step 2: Normalization & Resize-First
        workspace.clear()
        when (source) {
            is Bitmap -> {
                // Resize ROI directly into row-level scratchBmp
                val scratchBmp = NativePaddleEngine.sharedBmpOdoScratch
                val canvas = Canvas(scratchBmp)
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
                canvas.drawBitmap(source, roi, Rect(0, 0, finalW, finalH), null)
                
                // Sync to workspace.yuv (Uses YUV normalization)
                NativeImageUtils.syncMatFromArgb(scratchBmp, workspace.yMat)
            }
            is org.opencv.core.Mat -> {
                val subY = source.submat(roi)
                Imgproc.resize(subY, workspace.yMat, org.opencv.core.Size(finalW.toDouble(), finalH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                subY.release()
            }
            is BufferSet.Instance -> {
                // Luma Resize
                val subY = source.yMat.submat(roi)
                Imgproc.resize(subY, workspace.yMat, org.opencv.core.Size(finalW.toDouble(), finalH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                
                // Chroma Resize (8UC2 interleaved)
                val roiUV = Rect(roi.left / 2, roi.top / 2, roiW / 2, roiH / 2)
                val subUV = source.uvMat.submat(roiUV)
                Imgproc.resize(subUV, workspace.uvMat, org.opencv.core.Size(finalW / 2.0, finalH / 2.0), 0.0, 0.0, Imgproc.INTER_AREA)
                
                subY.release(); subUV.release()
            }
        }

        // Step 3: Native Annotation (Explicitly target the scratch instance)
        workspace.annotate(annotations, finalW, finalH, roiW, roiH)

        // Step 4: Direct Encoding
        val yuvMat = workspace.getYuvMat()
        val roiYuv = yuvMat.submat(0, finalH * 3 / 2, 0, finalW)
        val bgrMat = org.opencv.core.Mat()
        Imgproc.cvtColor(roiYuv, bgrMat, Imgproc.COLOR_YUV2BGR_NV21)
        
        val matOfByte = MatOfByte()
        Imgcodecs.imencode(".jpg", bgrMat, matOfByte)
        val jpegBytes = matOfByte.toArray()
        
        bgrMat.release(); roiYuv.release(); yuvMat.release()
        
        android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP)
    }
}
