package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.baidu.paddle.lite.Tensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Native Paddle-Lite 2.14rc OCR Engine.
 * Supports both full-image discovery (Detect + Recognize) and constrained odometer reading.
 */
class NativePaddleEngine(
    private val context: Context,
    private val isConstrained: Boolean = false
) : OcrEngine {
    override val name = if (isConstrained) "Paddle-Lite (Odo)" else "Paddle-Lite"

    var isAvailable: Boolean = false
        private set

    private val dictionary = mutableListOf<String>()
    
    // Memory-safe reusable buffers to prevent SIGSEGV fragmentation
    private var detectionInputBuffer: FloatArray? = null
    private var lastUsedInputSize: Int = 0
    
    companion object {
        private var sharedDetector: PaddlePredictor? = null
        private var sharedRecognizer: PaddlePredictor? = null
        private var isNativeLibLoaded = false
        private var initError: String? = null
    }

    init {
        try {
            val arch = detectArch()
            if (arch == "x86_64") {
                isAvailable = false
                initError = "Native Paddle disabled on amd64."
            } else {
                if (sharedDetector == null || sharedRecognizer == null) {
                    if (!isNativeLibLoaded) { loadNativeLibrary(); isNativeLibLoaded = true }
                    val detPath = copyAssetToInternal("paddle/det_v4_1280_$arch.nb")
                    val recPath = copyAssetToInternal("paddle/rec_v3_$arch.nb")
                    sharedDetector = createPredictor(detPath)
                    sharedRecognizer = createPredictor(recPath)
                }
                loadDictionary("paddle/en_dict.txt")
                isAvailable = true
            }
        } catch (e: Throwable) {
            isAvailable = false
            initError = e.message
            Log.e("PaddleLite", "Failed to initialize predictors", e)
        }
    }

    private fun loadNativeLibrary() {
        val abi = Build.SUPPORTED_ABIS[0]
        val libName = "libpaddle_lite_jni.so"
        val assetPath = "libs_backup/${abi}_$libName"
        try {
            val internalLibPath = copyAssetToInternal(assetPath)
            System.load(internalLibPath)
        } catch (e: Exception) { System.loadLibrary("paddle_lite_jni") }
    }

    private fun detectArch(): String {
        val abi = Build.SUPPORTED_ABIS[0]
        return when {
            abi.contains("arm64") -> "armv8"
            abi.contains("armeabi-v7a") -> "armv7"
            else -> "x86_64"
        }
    }

    private fun copyAssetToInternal(assetPath: String): String {
        val file = File(context.cacheDir, assetPath.replace("/", "_"))
        if (!file.exists()) {
            context.assets.open(assetPath).use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
        }
        return file.absolutePath
    }

    private fun loadDictionary(path: String) {
        if (dictionary.isNotEmpty()) return
        context.assets.open(path).bufferedReader().useLines { lines -> lines.forEach { dictionary.add(it) } }
    }

    private fun createPredictor(modelPath: String): PaddlePredictor {
        val config = MobileConfig()
        config.setModelFromFile(modelPath)
        config.setThreads(4) 
        config.setPowerMode(PowerMode.LITE_POWER_HIGH)
        return PaddlePredictor.createPaddlePredictor(config)
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Not Available: $initError")
        val t0 = System.currentTimeMillis()
        if (isConstrained) recognizeConstrained(bitmap, t0) else recognizeDiscovery(bitmap, t0)
    }

    private suspend fun recognizeConstrained(bitmap: Bitmap, t0: Long): OcrResult {
        val finalResult = runRecognitionStage(bitmap, 48)
        return OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            odometer = finalResult.text,
            debugText = finalResult.text,
            textBlocks = listOf(TextBlock(finalResult.text, Rect(0,0,bitmap.width, bitmap.height))),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }

    private suspend fun recognizeDiscovery(bitmap: Bitmap, t0: Long): OcrResult {
        val textBlocks = mutableListOf<TextBlock>()
        val sb = StringBuilder()
        val boxesRes = runDetection(bitmap)
        val boxes = boxesRes.first
        val flatHeatmap = boxesRes.second

        for (detectedBox in boxes) {
            val box = detectedBox.boundingBox
            if (box.width() < 1 || box.height() < 1) continue
            val crop = cropBitmap(bitmap, box)
            val res = runRecognitionStage(crop, 48)
            crop.recycle()
            if (res.text.isNotBlank()) {
                sb.append(res.text).append(" ")
                textBlocks.add(TextBlock(res.text, box, detectedBox.angle))
            }
        }

        return OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = sb.toString().trim(),
            textBlocks = textBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            heatmap = flatHeatmap
        )
    }

    private fun runDetection(bitmap: Bitmap): Pair<List<DetectedBox>, FloatArray> {
        val predictor = sharedDetector ?: return emptyList<DetectedBox>() to floatArrayOf()
        val inputSize = 1280
        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()))
        
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
        
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f); val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val px = padded.getPixel(x, y)
                floatData[0 * inputSize * inputSize + y * inputSize + x] = ((px shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
                floatData[1 * inputSize * inputSize + y * inputSize + x] = ((px shr 8 and 0xFF) / 255.0f - mean[1]) / std[1]
                floatData[2 * inputSize * inputSize + y * inputSize + x] = ((px and 0xFF) / 255.0f - mean[2]) / std[2]
            }
        }
        padded.recycle()
        
        try {
            inputTensor.setData(floatData)
            predictor.run()
            val outputTensor = predictor.getOutput(0)
            val outputData = outputTensor.floatData
            
            // Use Algorithm B: Perimeter Pixel Check
            val boxes = TfLiteOcrUtils.processDbNetOutput(
                outputData, 
                inputSize, 
                inputSize, 
                sourceBitmap = bitmap,
                algorithm = "B"
            )
            
            val invScale = 1.0f / scale
            val scaledBoxes = boxes.map { db ->
                val b = db.boundingBox
                db.copy(boundingBox = Rect(
                    ((b.left - offsetX) * invScale).toInt().coerceIn(0, bitmap.width), 
                    ((b.top - offsetY) * invScale).toInt().coerceIn(0, bitmap.height),
                    ((b.right - offsetX) * invScale).toInt().coerceIn(0, bitmap.width), 
                    ((b.bottom - offsetY) * invScale).toInt().coerceIn(0, bitmap.height)
                ))
            }
            return scaledBoxes to outputData
        } catch (t: Throwable) { return emptyList<DetectedBox>() to floatArrayOf() }
    }

    private data class RecStageResult(val text: String, val timeMs: Long, val confidence: Float)

    private fun runRecognitionStage(bitmap: Bitmap, targetHeight: Int): RecStageResult {
        val tStart = System.currentTimeMillis()
        val predictor = sharedRecognizer ?: return RecStageResult("", 0, 0f)
        val targetWidth = 640
        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()))
        val floatData = FloatArray(1 * 3 * targetHeight * targetWidth)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val px = scaled.getPixel(x, y)
                floatData[0 * targetHeight * targetWidth + y * targetWidth + x] = ((px shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f
                floatData[1 * targetHeight * targetWidth + y * targetWidth + x] = ((px shr 8 and 0xFF) / 255.0f - 0.5f) / 0.5f
                floatData[2 * targetHeight * targetWidth + y * targetWidth + x] = ((px and 0xFF) / 255.0f - 0.5f) / 0.5f
            }
        }
        scaled.recycle()
        try {
            inputTensor.setData(floatData); predictor.run()
            val outputTensor = predictor.getOutput(0); val dims = outputTensor.shape()
            val seqLen = dims[1].toInt(); val dictSize = dims[2].toInt(); val data = outputTensor.floatData
            val result = StringBuilder(); var lastIdx = -1; var totalConf = 0f; var charCount = 0
            for (i in 0 until seqLen) {
                var maxIdx = 0; var maxVal = -1f
                for (j in 0 until dictSize) { val v = data[i * dictSize + j]; if (v > maxVal) { maxVal = v; maxIdx = j } }
                if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) {
                    result.append(dictionary[maxIdx - 1]); totalConf += maxVal; charCount++
                }
                lastIdx = maxIdx
            }
            val avgConf = if (charCount > 0) totalConf / charCount else 0f
            return RecStageResult(result.toString(), System.currentTimeMillis() - tStart, avgConf)
        } catch (t: Throwable) { return RecStageResult("", 0, 0f) }
    }

    private fun cropBitmap(bmp: Bitmap, rect: Rect): Bitmap {
        val left = max(0, rect.left); val top = max(0, rect.top)
        val width = min(rect.width(), bmp.width - left); val height = min(rect.height(), bmp.height - top)
        return Bitmap.createBitmap(bmp, left, top, width, height)
    }
}
