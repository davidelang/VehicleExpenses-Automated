package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
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

/**
 * Represents a single hunk of text found by an OCR engine.
 */
data class TextBlock(
    val text: String,
    val boundingBox: Rect,
    val angle: Float = 0f,
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
    val originalPhotoPath: String? = null,
    val croppedBitmap: Bitmap? = null,
    val openCvProcessedBitmap: Bitmap? = null,
    val heatmap: FloatArray? = null,
    val textBlocks: List<TextBlock> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun filterByCrops(odoCrop: RectF?, otherCrop: RectF?): OcrResult {
        // ONLY filter by physical location
        val filteredBlocks = textBlocks.filter { block ->
            !OcrUtils.isBlockInCrop(block, odoCrop, imageWidth, imageHeight) &&
            !OcrUtils.isBlockInCrop(block, otherCrop, imageWidth, imageHeight)
        }
        return this.copy(
            textBlocks = filteredBlocks,
            debugText = filteredBlocks.joinToString(" ") { it.text }
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
        val inputSize = 1280
        
        // 1. Detection Stage
        val detInterpreter = try {
            val file = File(context.cacheDir, "tflite_paddle_det_model.tflite")
            if (!file.exists()) {
                context.assets.open("tflite/paddle/det_model.tflite").use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
            }
            Interpreter(file)
        } catch (e: Exception) { 
            Log.e("NativeTflite", "Failed to load detector", e)
            null 
        }
        
        val textBlocks = mutableListOf<TextBlock>()
        val debugText = StringBuilder()
        var flatHeatmap: FloatArray? = null
        
        if (detInterpreter != null) {
            // Fit-Inside Resize
            val scale = min(inputSize.toFloat() / bitmap.width, inputSize.toFloat() / bitmap.height)
            val sw = (bitmap.width * scale).toInt()
            val sh = (bitmap.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
            val padded = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(padded)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(scaled, 0f, 0f, null)
            
            val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * inputSize * inputSize * 4).order(ByteOrder.nativeOrder())
            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    val px = padded.getPixel(x, y)
                    inputBuffer.putFloat(((px shr 16 and 0xFF) / 255.0f - 0.485f) / 0.229f)
                    inputBuffer.putFloat(((px shr 8 and 0xFF) / 255.0f - 0.456f) / 0.224f)
                    inputBuffer.putFloat(((px and 0xFF) / 255.0f - 0.406f) / 0.225f)
                }
            }
            
            val outputBuffer = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(1) } } }
            detInterpreter.run(inputBuffer, outputBuffer)
            scaled.recycle(); padded.recycle()

            flatHeatmap = FloatArray(inputSize * inputSize)
            var maxProb = 0f
            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    val prob = outputBuffer[0][y][x][0]
                    flatHeatmap[y * inputSize + x] = prob
                    if (prob > maxProb) maxProb = prob
                }
            }
            Log.i("NativeTflite", "Detection Heatmap Max Probability: $maxProb")
            
            // ADAPTIVE THRESHOLDING
            val boxes = TfLiteOcrUtils.processDbNetOutput(flatHeatmap, inputSize, inputSize, thresh = 0.3f, unclipRatio = 1.5f)
            
            val invScale = 1.0f / scale
            for (detectedBox in boxes) {
                val box = detectedBox.boundingBox
                val left = (box.left * invScale).toInt().coerceIn(0, bitmap.width)
                val top = (box.top * invScale).toInt().coerceIn(0, bitmap.height)
                val right = (box.right * invScale).toInt().coerceIn(0, bitmap.width)
                val bottom = (box.bottom * invScale).toInt().coerceIn(0, bitmap.height)
                
                if (right <= left || bottom <= top) continue
                val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                val text = engine.runInference(crop)
                crop.recycle()
                
                if (text.isNotBlank()) {
                    debugText.append("$text ")
                    textBlocks.add(TextBlock(text, Rect(left, top, right, bottom), detectedBox.angle))
                }
            }
            detInterpreter.close()
        }

        engine.close()
        OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = debugText.toString().trim(),
            textBlocks = textBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            heatmap = flatHeatmap
        )
    }
}

object OcrUtils {
    fun isBlockInCrop(block: TextBlock, crop: RectF?, w: Int, h: Int): Boolean {
        if (crop == null || w == 0 || h == 0) return false
        val cx = block.boundingBox.centerX().toFloat() / w
        val cy = block.boundingBox.centerY().toFloat() / h
        return cx >= crop.left && cx <= crop.right && cy >= crop.top && cy <= crop.bottom
    }
}
