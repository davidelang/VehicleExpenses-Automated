package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
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
    val refinedBoxes: List<DetectedBox>
)

/**
 * Mandated: Normalized Rectangle (0.0 to 1.0)
 */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Represents a single hunk of text found by an OCR engine.
 * Linked to its discovery history for visualization.
 */
data class TextBlock(
    val text: String,
    val boundingBox: Rect, // Final Crop Pixel coordinates
    val angle: Float = 0f,
    val rawDiscoveryBox: RectF? = null,    // RED tier
    val refinedDiscoveryBox: RectF? = null, // ORANGE tier
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Encapsulates the results of an OCR operation, including extracted text,
 * metadata, and debug information.
 */
data class OcrResult(
    val engineName: String = "Unknown",
    val executionTimeMs: Long = 0,
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
        // COORDINATE ALIGNMENT: 
        // We filter based on normalized coordinates (0.0 to 1.0) to ensure consistency.
        val filteredBlocks = textBlocks.filter { block ->
            if (imageWidth <= 0 || imageHeight <= 0) return@filter true
            val cx = block.boundingBox.centerX().toFloat() / imageWidth
            val cy = block.boundingBox.centerY().toFloat() / imageHeight
            
            val inOdo = odoCrop?.let { cx >= it.left && cx <= it.right && cy >= it.top && cy <= it.bottom } ?: false
            val inOther = otherCrop?.let { cx >= it.left && cx <= it.right && cy >= it.top && cy <= it.bottom } ?: false
            
            !inOdo && !inOther
        }
        val diff = textBlocks.size - filteredBlocks.size
        Log.i("OcrResult", "Filtered $diff blocks via normalized crops for $engineName. Remaining: ${filteredBlocks.size}")
        
        return this.copy(
            textBlocks = filteredBlocks,
            debugText = filteredBlocks.filter { it.text.isNotBlank() }.joinToString(" ") { it.text }
        )
    }
}

/**
 * Represents an intermediate step in a multi-stage OCR process.
 */
data class OcrStepResult(
    val stageName: String,
    val bitmap: Bitmap,
    val text: String?
)

/**
 * Common interface for all OCR engines used in the experiment.
 */
interface OcrEngine {
    val name: String
    suspend fun recognize(bitmap: Bitmap): OcrResult

    companion object {
        fun getDiscoveryEngineNames(): List<String> {
            return listOf("ML Kit", "Native TFLite", "Paddle-TFLite", "Paddle-Lite")
        }
    }
}

class TesseractEngine : OcrEngine {
    override val name = "Tesseract"
    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val (text, blocks) = OdometerOcrUtils.runRawOcr(bitmap, "0123456789")
        OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            odometer = text.takeIf { it.length in 4..6 },
            debugText = text,
            textBlocks = blocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }
}

class MlKitEngine : OcrEngine {
    override val name = "ML Kit"
    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val res = OdometerOcrUtils.extractFromPhotoBitmap(bitmap)
        res.copy(engineName = name, executionTimeMs = System.currentTimeMillis() - t0)
    }
}

class NativeTfliteEngine(private val context: Context) : OcrEngine {
    override val name = "Native TFLite"
    
    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val engine = TfLiteOcrEngine(context)
        val inputSize = 1280 // FIXED STABLE SIZE
        
        val detInterpreter = try {
            val file = File(context.cacheDir, "tflite_paddle_det_model.tflite")
            if (!file.exists()) {
                context.assets.open("tflite/paddle/det_model.tflite").use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
            }
            val interp = Interpreter(file)
            interp.resizeInput(0, intArrayOf(1, inputSize, inputSize, 3))
            interp.allocateTensors()
            interp
        } catch (e: Exception) { 
            Log.e("NativeTflite", "Failed to load detector", e)
            null 
        }
        
        val textBlocks = mutableListOf<TextBlock>()
        val debugText = StringBuilder()
        
        if (detInterpreter != null) {
            // ZERO-ANCHOR (0,0) PREPROCESSING
            val scale = min(inputSize.toFloat() / bitmap.width, inputSize.toFloat() / bitmap.height)
            val sw = (bitmap.width * scale).toInt(); val sh = (bitmap.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
            val padded = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(padded); canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(scaled, 0f, 0f, null) // Anchor at top-left
            scaled.recycle()
            
            val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)
            
            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    val px = padded.getPixel(x, y)
                    inputBuffer.putFloat(((px shr 16 and 0xFF) / 255.0f - mean[0]) / std[0])
                    inputBuffer.putFloat(((px shr 8 and 0xFF) / 255.0f - mean[1]) / std[1])
                    inputBuffer.putFloat(((px and 0xFF) / 255.0f - mean[2]) / std[2])
                }
            }
            
            val outputBuffer = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(1) } } }
            detInterpreter.run(inputBuffer, outputBuffer)

            val rawHeatmap = FloatArray(inputSize * inputSize)
            for (i in 0 until (inputSize * inputSize)) {
                rawHeatmap[i] = outputBuffer[0][i / inputSize][i % inputSize][0]
            }
            
            val discoveryHeatmap = rawHeatmap.copyOf()
            val dbRes = TfLiteOcrUtils.processDbNetOutput(
                discoveryHeatmap, 
                inputSize, 
                inputSize, 
                scale = scale,
                sourceBitmap = bitmap,
                algorithm = "A" // TFLite uses Projection-Based (Density)
            )
            
            // LINK THE TIERS: Capture every suspicion, including those without text
            for (i in dbRes.rawBoxes.indices) {
                val rawBox = dbRes.rawBoxes[i]
                val refinedBox = dbRes.refinedBoxes.getOrNull(i)
                val nb = refinedBox?.boundingBox ?: rawBox.boundingBox
                
                val left = (nb.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
                val top = (nb.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
                val right = (nb.right * bitmap.width).toInt().coerceIn(0, bitmap.width)
                val bottom = (nb.bottom * bitmap.height).toInt().coerceIn(0, bitmap.height)
                
                if (right <= left || bottom <= top) {
                    textBlocks.add(TextBlock(text = "", boundingBox = Rect(left, top, right, bottom), rawDiscoveryBox = rawBox.boundingBox, refinedDiscoveryBox = refinedBox?.boundingBox))
                    continue
                }

                val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                val text = engine.runInference(crop)
                crop.recycle()
                
                if (text.isNotBlank()) {
                    debugText.append("$text ")
                }
                
                textBlocks.add(TextBlock(
                    text = text, 
                    boundingBox = Rect(left, top, right, bottom), 
                    angle = refinedBox?.angle ?: 0f,
                    rawDiscoveryBox = rawBox.boundingBox,
                    refinedDiscoveryBox = refinedBox?.boundingBox
                ))
            }
            padded.recycle()
            detInterpreter.close()
            engine.close()
            return@withContext OcrResult(
                engineName = name,
                executionTimeMs = System.currentTimeMillis() - t0,
                debugText = debugText.toString().trim(),
                textBlocks = textBlocks,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                rawHeatmap = rawHeatmap,
                discoveryHeatmap = discoveryHeatmap,
                rawDiscoveryBoxes = dbRes.rawBoxes.map { it.boundingBox },
                scaleFactor = scale
            )
        }

        engine.close()
        return@withContext OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = debugText.toString().trim(),
            textBlocks = textBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }
}

object OcrUtils {
    fun isBlockInCrop(block: TextBlock, crop: android.graphics.RectF?, w: Int, h: Int): Boolean {
        if (crop == null || w == 0 || h == 0) return false
        val cx = block.boundingBox.centerX().toFloat() / w
        val cy = block.boundingBox.centerY().toFloat() / h
        return cx >= crop.left && cx <= crop.right && cy >= crop.top && cy <= crop.bottom
    }
}
