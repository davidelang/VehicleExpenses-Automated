package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
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
 * Supports a timed resolution sweep and optimized 3-stage odometer flow.
 */
class PaddleOcrEngine(
    private val context: Context,
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

    private fun copyAssetToInternal(context: Context, assetPath: String): String {
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

    /**
     * 3-Stage Odometer Flow (48px -> 640px)
     */
    private suspend fun recognizeConstrained(bitmap: Bitmap, t0: Long): OcrResult {
        // Stage 1: Quick Pass (48px height)
        val stage1 = runRecognitionStage(bitmap, 48)
        val digits = stage1.text.filter { it.isDigit() }
        
        val finalResult = if (digits.length >= 2) {
            stage1
        } else {
            // Stage 2: Deep Scan (640px height)
            runRecognitionStage(bitmap, 640)
        }

        return OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            odometer = finalResult.text,
            debugText = finalResult.text,
            textBlocks = listOf(TextBlock(finalResult.text, Rect(0,0,bitmap.width, bitmap.height))),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            metadata = mapOf("Resolution" to "${finalResult.height}px")
        )
    }

    /**
     * Full Dash Discovery with Timed Resolution Sweep
     */
    private suspend fun recognizeDiscovery(bitmap: Bitmap, t0: Long): OcrResult {
        val textBlocks = mutableListOf<TextBlock>()
        val inputSize = 1280
        val resizedDet = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        
        // 1. Detection
        val inputBuffer = prepareDetectionBuffer(resizedDet, inputSize)
        val outputBuffer = Array(1) { Array(1) { Array(inputSize) { FloatArray(inputSize) } } }
        detInterpreter?.run(inputBuffer, outputBuffer)
        resizedDet.recycle()

        val flatHeatmap = FloatArray(inputSize * inputSize)
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                flatHeatmap[y * inputSize + x] = outputBuffer[0][0][y][x]
            }
        }
        val boxes = TfLiteOcrUtils.processDbNetOutput(flatHeatmap, inputSize, inputSize, thresh = 0.3f)
        
        // 2. Recognition Sweep (Timed)
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
            
            // EXECUTE SWEEP: 48px vs 128px vs Native
            val res48 = runRecognitionStage(crop, 48)
            val res128 = runRecognitionStage(crop, 128)
            val resNative = runRecognitionStage(crop, crop.height)
            
            crop.recycle()

            // Pick the best result (heuristically prefer highest resolution for discovery unless confidence is high)
            val best = resNative // For now, use native as primary, but log all
            
            val sweepMeta = mapOf(
                "sweep_48" to "${res48.text} (${res48.timeMs}ms)",
                "sweep_128" to "${res128.text} (${res128.timeMs}ms)",
                "sweep_native" to "${resNative.text} (${resNative.timeMs}ms)"
            )

            if (best.text.isNotBlank()) {
                results.append("${best.text} ")
                textBlocks.add(TextBlock(best.text, origBox, detectedBox.angle, sweepMeta))
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

    private data class RecStageResult(val text: String, val timeMs: Long, val height: Int)

    private fun runRecognitionStage(bitmap: Bitmap, targetHeight: Int): RecStageResult {
        val tStart = System.currentTimeMillis()
        val targetWidth = 640 // Recognition model width is fixed/maxed at 640
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        
        val inputBuffer = ByteBuffer.allocateDirect(1 * targetHeight * targetWidth * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val px = scaled.getPixel(x, y)
                    // Normalization [0.5, 0.5]
                    putFloat(((px shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                    putFloat(((px shr 8 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                    putFloat(((px and 0xFF) / 255.0f - 0.5f) / 0.5f)
                }
            }
        }
        scaled.recycle()

        // Adaptive shape based on height
        val timeSteps = 80 // Fixed for current model
        val outputBuffer = Array(1) { Array(timeSteps) { FloatArray(97) } }
        recInterpreter?.run(inputBuffer, outputBuffer)
        
        val decoded = TfLiteOcrUtils.decodeCtcGreedy(outputBuffer, dictionary, blankIndex = 0)
        return RecStageResult(decoded, System.currentTimeMillis() - tStart, targetHeight)
    }

    private fun prepareDetectionBuffer(bitmap: Bitmap, size: Int): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(1 * 3 * size * size * 4).apply {
            order(ByteOrder.nativeOrder())
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val px = bitmap.getPixel(x, y)
                    // ImageNet normalization
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
