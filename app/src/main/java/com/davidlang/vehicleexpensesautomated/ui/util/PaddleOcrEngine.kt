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
import kotlin.math.max

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

        // 1. Fit-Inside Resize with Zero-Anchor (0,0)
        val scale = min(inputSize.toFloat() / bitmap.width, inputSize.toFloat() / bitmap.height)
        val sw = (bitmap.width * scale).toInt(); val sh = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val padded = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded); canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(scaled, 0f, 0f, null) // Anchor top-left
        scaled.recycle()
        
        val inputBuffer = prepareDetectionBuffer(padded, inputSize, floatData)
        val outputBuffer = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(1) } } }
        detInterpreter?.run(inputBuffer, outputBuffer)

        val rawHeatmap = FloatArray(inputSize * inputSize)
        for (i in 0 until (inputSize * inputSize)) {
            rawHeatmap[i] = outputBuffer[0][i / inputSize][i % inputSize][0]
        }
        
        // ZERO-ANCHOR SCALE math
        val discoveryHeatmap = rawHeatmap.copyOf()
        val dbRes = TfLiteOcrUtils.processDbNetOutput(
            discoveryHeatmap, inputSize, inputSize, scale = scale,
            sourceBitmap = bitmap, algorithm = "B" // STANDARDIZED B
        )
        
        val results = StringBuilder()
        // LINK THE TIERS: Capture every suspicion, including those without text
        for (i in dbRes.rawBoxes.indices) {
            val rawBox = dbRes.rawBoxes[i]
            val refinedBox = dbRes.refinedBoxes.getOrNull(i)
            val orange = refinedBox?.boundingBox ?: rawBox.boundingBox
            
            // YELLOW: Final crop with +8px padding in source space
            val left = (orange.left * bitmap.width).toInt() - 8
            val top = (orange.top * bitmap.height).toInt() - 8
            val right = (orange.right * bitmap.width).toInt() + 8
            val bottom = (orange.bottom * bitmap.height).toInt() + 8
            
            val cropRect = Rect(max(0, left), max(0, top), min(bitmap.width, right), min(bitmap.height, bottom))
            
            if (cropRect.width() <= 0 || cropRect.height() <= 0) {
                textBlocks.add(TextBlock(text = "", boundingBox = cropRect, rawDiscoveryBox = rawBox.boundingBox, refinedDiscoveryBox = refinedBox?.boundingBox))
                continue
            }

            val crop = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
            val res = runRecognitionStage(crop, 48)
            crop.recycle()

            if (res.text.isNotBlank()) results.append("${res.text} ")
            
            textBlocks.add(TextBlock(
                text = res.text, 
                boundingBox = cropRect, 
                angle = refinedBox?.angle ?: 0f,
                rawDiscoveryBox = rawBox.boundingBox,
                refinedDiscoveryBox = refinedBox?.boundingBox
            ))
        }

        padded.recycle()

        return OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            discoveryTimeMs = dbRes.discoveryTimeMs,
            debugText = results.toString().trim(),
            textBlocks = textBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            rawHeatmap = rawHeatmap,
            discoveryHeatmap = discoveryHeatmap,
            rawDiscoveryBoxes = dbRes.rawBoxes.map { it.boundingBox },
            scaleFactor = scale
        )
    }

    private data class RecStageResult(val text: String, val timeMs: Long, val confidence: Float)

    private fun runRecognitionStage(bitmap: Bitmap, targetHeight: Int): RecStageResult {
        val tStart = System.currentTimeMillis()
        val targetWidth = 640
        
        // ASPECT-CORRECT PADDING: Avoid horizontal stretching
        val scale = targetHeight.toFloat() / bitmap.height.toFloat()
        val sw = (bitmap.width * scale).toInt().coerceAtMost(targetWidth)
        val sh = targetHeight
        
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val padded = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        scaled.recycle()
        
        val inputBuffer = ByteBuffer.allocateDirect(1 * targetHeight * targetWidth * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val px = padded.getPixel(x, y)
                    putFloat(((px shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                    putFloat(((px shr 8 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                    putFloat(((px and 0xFF) / 255.0f - 0.5f) / 0.5f)
                }
            }
        }
        padded.recycle()

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
