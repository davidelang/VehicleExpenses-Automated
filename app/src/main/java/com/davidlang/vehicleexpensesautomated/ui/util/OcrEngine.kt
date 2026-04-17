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
    val rawHeatmap: FloatArray? = null,
    val discoveryHeatmap: FloatArray? = null,
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
        val diff = textBlocks.size - filteredBlocks.size
        Log.i("OcrResult", "Filtered $diff blocks via crops for $engineName. Remaining: ${filteredBlocks.size}")
        
        return this.copy(
            textBlocks = filteredBlocks,
            debugText = filteredBlocks.joinToString(" ") { it.text },
            rawHeatmap = this.rawHeatmap,
            discoveryHeatmap = this.discoveryHeatmap
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
            // Centered Fit-Inside Resize
            val scale = min(inputSize.toFloat() / bitmap.width, inputSize.toFloat() / bitmap.height)
            val sw = (bitmap.width * scale).toInt()
            val sh = (bitmap.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
            val padded = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(padded)
            canvas.drawColor(Color.BLACK)
            
            val offsetX = (inputSize - sw) / 2f
            val offsetY = (inputSize - sh) / 2f
            canvas.drawBitmap(scaled, offsetX, offsetY, null)
            scaled.recycle()
            
            val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * inputSize * inputSize * 4).order(ByteOrder.nativeOrder())
            
            // Scaled Integrity Fix: Explicit NCHW mapping to prevent blotching
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)
            val pixels = IntArray(inputSize * inputSize)
            padded.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

            for (c in 0 until 3) {
                for (i in pixels.indices) {
                    val px = pixels[i]
                    val v = when (c) {
                        0 -> (px shr 16 and 0xFF)
                        1 -> (px shr 8 and 0xFF)
                        else -> (px and 0xFF)
                    }
                    inputBuffer.putFloat((v / 255.0f - mean[c]) / std[c])
                }
            }
            
            val outputBuffer = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(1) } } }
            detInterpreter.run(inputBuffer, outputBuffer)

            val rawHeatmap = FloatArray(inputSize * inputSize)
            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    rawHeatmap[y * inputSize + x] = outputBuffer[0][y][x][0]
                }
            }
            
            // Use Algorithm C: Edge-Stop Expansion (Researcher)
            val discoveryHeatmap = rawHeatmap.copyOf()
            val boxes = TfLiteOcrUtils.processDbNetOutput(
                discoveryHeatmap, 
                inputSize, 
                inputSize, 
                sourceBitmap = padded,
                algorithm = "C"
            )
            
            val invScale = 1.0f / scale
            for (detectedBox in boxes) {
                val box = detectedBox.boundingBox
                val left = ((box.left - offsetX) * invScale).toInt().coerceIn(0, bitmap.width)
                val top = ((box.top - offsetY) * invScale).toInt().coerceIn(0, bitmap.height)
                val right = ((box.right - offsetX) * invScale).toInt().coerceIn(0, bitmap.width)
                val bottom = ((box.bottom - offsetY) * invScale).toInt().coerceIn(0, bitmap.height)
                
                if (right <= left || bottom <= top) continue
                val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                val text = engine.runInference(crop)
                crop.recycle()
                
                if (text.isNotBlank()) {
                    debugText.append("$text ")
                    textBlocks.add(TextBlock(text, Rect(left, top, right, bottom), detectedBox.angle))
                }
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
                discoveryHeatmap = discoveryHeatmap
            )
        }

        engine.close()
        OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = debugText.toString().trim(),
            textBlocks = textBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            rawHeatmap = null,
            discoveryHeatmap = null
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
