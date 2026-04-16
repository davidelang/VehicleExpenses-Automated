package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
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
    val textBlocks: List<TextBlock> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun filterByCrops(odoCrop: RectF?, otherCrop: RectF?): OcrResult {
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
        // Fixed 1280px resolution for TFLite compatibility
        val inputSize = 1280
        
        // 1. Detection Stage (DBNet TFLite)
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
        
        if (detInterpreter != null) {
            val resizedDet = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * inputSize * inputSize * 4).order(ByteOrder.nativeOrder())
            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    val px = resizedDet.getPixel(x, y)
                    // ImageNet normalization
                    inputBuffer.putFloat(((px shr 16 and 0xFF) / 255.0f - 0.485f) / 0.229f)
                    inputBuffer.putFloat(((px shr 8 and 0xFF) / 255.0f - 0.456f) / 0.224f)
                    inputBuffer.putFloat(((px and 0xFF) / 255.0f - 0.406f) / 0.225f)
                }
            }
            
            // Output shape [1, 1280, 1280, 1]
            val outputBuffer = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(1) } } }
            detInterpreter.run(inputBuffer, outputBuffer)
            resizedDet.recycle()

            val flatHeatmap = FloatArray(inputSize * inputSize)
            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    flatHeatmap[y * inputSize + x] = outputBuffer[0][y][x][0]
                }
            }
            
            // Use 0.2 threshold for higher sensitivity
            val boxes = TfLiteOcrUtils.processDbNetOutput(flatHeatmap, inputSize, inputSize, thresh = 0.2f, unclipRatio = 1.5f)
            
            val scaleX = bitmap.width.toFloat() / inputSize
            val scaleY = bitmap.height.toFloat() / inputSize
            
            for (detectedBox in boxes) {
                val box = detectedBox.boundingBox
                val left = (box.left * scaleX).toInt().coerceAtLeast(0)
                val top = (box.top * scaleY).toInt().coerceAtLeast(0)
                val right = (box.right * scaleX).toInt().coerceAtMost(bitmap.width)
                val bottom = (box.bottom * scaleY).toInt().coerceAtMost(bitmap.height)
                
                if (right <= left || bottom <= top) continue
                val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                val text = engine.runInference(crop)
                crop.recycle()
                
                if (text.isNotBlank() && !text.contains("(no digits)")) {
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
            imageHeight = bitmap.height
        )
    }
}

object OcrUtils {
    /**
     * Determines if a text block's center point falls within a normalized crop region.
     */
    fun isBlockInCrop(block: TextBlock, crop: RectF?, w: Int, h: Int): Boolean {
        if (crop == null || w == 0 || h == 0) return false
        val cx = block.boundingBox.centerX().toFloat() / w
        val cy = block.boundingBox.centerY().toFloat() / h
        return cx >= crop.left && cx <= crop.right && cy >= crop.top && cy <= crop.bottom
    }
}
