package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.davidlang.vehicleexpensesautomated.ui.util.TextBlock
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import com.davidlang.vehicleexpensesautomated.ui.util.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PaddleOCR Engine implemented via TFLite models.
 * Note: Recognition model is fixed at 48px height. Sweep/Staging disabled for TFLite.
 */
class PaddleOcrEngine(
    private val context: android.content.Context,
    private val isConstrained: Boolean = false
) : OcrEngine {
    override val name = if (isConstrained) "Paddle-TFLite (Odo)" else "Paddle-TFLite"
    
    private var detInterpreter: Interpreter? = null
    private var recInterpreter: Interpreter? = null
    private val dictionary = mutableListOf<String>()
    
    var isAvailable = false
        private set

    init {
        try {
            val detPath = copyAssetToInternal(context, "tflite/paddle/det_model.tflite")
            val recPath = copyAssetToInternal(context, "tflite/paddle/rec_model.tflite")

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                useNNAPI = false 
            }

            detInterpreter = Interpreter(File(detPath), options)
            recInterpreter = Interpreter(File(recPath), options)
            
            context.assets.open("tflite/paddle/paddle_en_dict.txt").bufferedReader().useLines { lines ->
                lines.forEach { dictionary.add(it) }
            }
            
            isAvailable = true
        } catch (e: Throwable) {
            isAvailable = false
            Log.e("PaddleOcr", "Failed to initialize: ${e.message}")
        }
    }

    private fun copyAssetToInternal(context: android.content.Context, assetPath: String): String {
        val file = File(context.cacheDir, assetPath.replace("/", "_"))
        context.assets.open(assetPath).use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file.absolutePath
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        if (isConstrained) {
            recognizeConstrained(bitmap, t0)
        } else {
            recognizeDiscovery(bitmap, t0)
        }
    }

    private suspend fun recognizeConstrained(bitmap: Bitmap, t0: Long): OcrResult {
        // TFLite model is fixed at 48px. Multi-stage disabled here.
        val res = runRecognitionStage(bitmap)

        return OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            odometer = res.text,
            debugText = res.text,
            textBlocks = listOf(TextBlock(res.text, Rect(0,0,bitmap.width, bitmap.height))),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            metadata = mapOf("Resolution" to "48px (Fixed)")
        )
    }

    private suspend fun recognizeDiscovery(bitmap: Bitmap, t0: Long): OcrResult {
        val textBlocks = mutableListOf<TextBlock>()
        val inputSize = 1280
        val resizedDet = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        
        // 1. Detection
        val inputBuffer = prepareDetectionBuffer(resizedDet, inputSize)
        val outputBuffer = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(1) } } }
        detInterpreter?.run(inputBuffer, outputBuffer)
        resizedDet.recycle()

        val flatHeatmap = FloatArray(inputSize * inputSize)
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                flatHeatmap[y * inputSize + x] = outputBuffer[0][y][x][0]
            }
        }
        val boxes = TfLiteOcrUtils.processDbNetOutput(flatHeatmap, inputSize, inputSize, thresh = 0.3f)
        
        // 2. Recognition
        val results = StringBuilder()
        val scaleX = bitmap.width.toFloat() / inputSize
        val scaleY = bitmap.height.toFloat() / inputSize

        for (detectedBox in boxes) {
            val box = detectedBox.boundingBox
            val origBox = Rect(
                (box.left * scaleX).toInt().coerceAtLeast(0),
                (box.top * scaleY).toInt().coerceAtLeast(0),
                (box.right * scaleX).toInt().coerceAtMost(bitmap.width),
                (box.bottom * scaleY).toInt().coerceAtMost(bitmap.height)
            )
            
            if (origBox.width() < 1 || origBox.height() < 1) continue
            val crop = Bitmap.createBitmap(bitmap, origBox.left, origBox.top, origBox.width(), origBox.height())
            
            // TFLite requires exactly 48px height. No sweep possible.
            val res = runRecognitionStage(crop)
            crop.recycle()

            if (res.text.isNotBlank()) {
                results.append("${res.text} ")
                textBlocks.add(TextBlock(res.text, origBox, detectedBox.angle, mapOf("Resolution" to "48px")))
            }
        }

        return OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = results.toString().trim(),
            textBlocks = textBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }

    private data class RecStageResult(val text: String, val timeMs: Long)

    private fun runRecognitionStage(bitmap: Bitmap): RecStageResult {
        val tStart = System.currentTimeMillis()
        val targetHeight = 48
        val targetWidth = 640
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        
        val inputBuffer = ByteBuffer.allocateDirect(1 * targetHeight * targetWidth * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val px = scaled.getPixel(x, y)
                    putFloat(((px shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                    putFloat(((px shr 8 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                    putFloat(((px and 0xFF) / 255.0f - 0.5f) / 0.5f)
                }
            }
        }
        scaled.recycle()

        val timeSteps = 80
        val outputBuffer = Array(1) { Array(timeSteps) { FloatArray(97) } }
        recInterpreter?.run(inputBuffer, outputBuffer)
        
        val decoded = TfLiteOcrUtils.decodeCtcGreedy(outputBuffer, dictionary, blankIndex = 0)
        return RecStageResult(decoded, System.currentTimeMillis() - tStart)
    }

    private fun prepareDetectionBuffer(bitmap: Bitmap, size: Int): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(1 * 3 * size * size * 4).apply {
            order(ByteOrder.nativeOrder())
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val px = bitmap.getPixel(x, y)
                    putFloat(((px shr 16 and 0xFF) / 255.0f - 0.485f) / 0.229f)
                    putFloat(((px shr 8 and 0xFF) / 255.0f - 0.456f) / 0.224f)
                    putFloat(((px and 0xFF) / 255.0f - 0.406f) / 0.225f)
                }
            }
        }
        return buf
    }

    fun close() {
        detInterpreter?.close()
        recInterpreter?.close()
    }
}
