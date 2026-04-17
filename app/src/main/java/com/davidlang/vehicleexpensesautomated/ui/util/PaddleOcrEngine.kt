package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class PaddleOcrEngine(private val context: Context, private val isConstrained: Boolean = false) : OcrEngine {
    override val name = if (isConstrained) "Paddle-TFLite (Odo)" else "Paddle-TFLite"
    
    private var detInterpreter: Interpreter? = null
    private var recInterpreter: Interpreter? = null
    private val dictionary = mutableListOf<String>()
    private var detectionInputBuffer: FloatArray? = null
    private var lastUsedInputSize = 0

    init {
        try {
            detInterpreter = loadInterpreter("tflite/paddle/det_model.tflite")
            recInterpreter = loadInterpreter("tflite/paddle/rec_model.tflite")
            loadDictionary("paddle/en_dict.txt")
        } catch (e: Exception) {
            Log.e("PaddleOcr", "Initialization failed", e)
        }
    }

    private fun loadInterpreter(assetPath: String): Interpreter {
        val file = File(context.cacheDir, assetPath.replace("/", "_"))
        if (!file.exists()) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        }
        return Interpreter(file)
    }

    private fun loadDictionary(assetPath: String) {
        context.assets.open(assetPath).bufferedReader().use { reader ->
            reader.forEachLine { dictionary.add(it) }
        }
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        if (isConstrained) recognizeConstrained(bitmap, t0) else recognizeDiscovery(bitmap, t0)
    }

    private suspend fun recognizeConstrained(bitmap: Bitmap, t0: Long): OcrResult {
        val res = runRecognitionStage(bitmap, 48)
        return OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            odometer = res.text,
            debugText = res.text,
            textBlocks = listOf(TextBlock(res.text, Rect(0, 0, bitmap.width, bitmap.height))),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }

    private suspend fun recognizeDiscovery(bitmap: Bitmap, t0: Long): OcrResult {
        val inputSize = 1280
        val textBlocks = mutableListOf<TextBlock>()
        
        if (detectionInputBuffer == null || lastUsedInputSize != inputSize) {
            detectionInputBuffer = FloatArray(1 * 3 * inputSize * inputSize)
            lastUsedInputSize = inputSize
        }
        val floatData = detectionInputBuffer!!

        // 1. Centered Fit-Inside Resize
        val scale = min(inputSize.toFloat() / bitmap.width, inputSize.toFloat() / bitmap.height)
        val sw = (bitmap.width * scale).toInt(); val sh = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val padded = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded); canvas.drawColor(Color.BLACK)
        val offsetX = (inputSize - sw) / 2f; val offsetY = (inputSize - sh) / 2f
        canvas.drawBitmap(scaled, offsetX, offsetY, null)
        scaled.recycle()
        
        val inputBuffer = prepareDetectionBuffer(padded, inputSize, floatData)
        val outputBuffer = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(1) } } }
        detInterpreter?.run(inputBuffer, outputBuffer)

        val rawHeatmap = FloatArray(inputSize * inputSize)
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                rawHeatmap[y * inputSize + x] = outputBuffer[0][y][x][0]
            }
        }
        
        // Use Algorithm C: Edge-Stop Expansion (Researcher)
        // PASS THE PADDED 1280px BITMAP for 1:1 coordinate alignment
        val discoveryHeatmap = rawHeatmap.copyOf()
        val boxes = TfLiteOcrUtils.processDbNetOutput(
            discoveryHeatmap, 
            inputSize, 
            inputSize, 
            sourceBitmap = padded,
            algorithm = "C"
        )
        
        val results = StringBuilder()
        val invScale = 1.0f / scale

        for (detectedBox in boxes) {
            val box = detectedBox.boundingBox
            // Adjust box coordinates: Subtract padding offset, then scale back to source image
            val left = ((box.left - offsetX) * invScale).toInt().coerceIn(0, bitmap.width)
            val top = ((box.top - offsetY) * invScale).toInt().coerceIn(0, bitmap.height)
            val right = ((box.right - offsetX) * invScale).toInt().coerceIn(0, bitmap.width)
            val bottom = ((box.bottom - offsetY) * invScale).toInt().coerceIn(0, bitmap.height)
            
            if (right <= left || bottom <= top) continue
            val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            val res = runRecognitionStage(crop, 48)
            crop.recycle()

            if (res.text.isNotBlank()) {
                results.append("${res.text} ")
                textBlocks.add(TextBlock(res.text, Rect(left, top, right, bottom), detectedBox.angle))
            }
        }

        padded.recycle()

        return OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = results.toString().trim(),
            textBlocks = textBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            rawHeatmap = rawHeatmap,
            discoveryHeatmap = discoveryHeatmap
        )
    }

    private data class RecStageResult(val text: String, val timeMs: Long, val confidence: Float)

    private fun runRecognitionStage(bitmap: Bitmap, targetHeight: Int): RecStageResult {
        val tStart = System.currentTimeMillis()
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

        val outputBuffer = Array(1) { Array(80) { FloatArray(97) } }
        recInterpreter?.run(inputBuffer, outputBuffer)
        
        val (decoded, confidence) = TfLiteOcrUtils.decodeCtcGreedy(outputBuffer, dictionary, blankIndex = 0)
        return RecStageResult(decoded, System.currentTimeMillis() - tStart, confidence)
    }

    private fun prepareDetectionBuffer(bitmap: Bitmap, size: Int, floatData: FloatArray): ByteBuffer {
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
