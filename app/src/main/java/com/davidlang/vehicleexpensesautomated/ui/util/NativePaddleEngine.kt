package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class NativePaddleEngine(private val context: Context, private val isConstrained: Boolean = false) : OcrEngine {
    override val name = if (isConstrained) "Paddle-Lite (Odo)" else "Paddle-Lite"
    
    private val dictionary = mutableListOf<String>()
    private var initError: String? = null
    var isAvailable = false
        private set

    companion object {
        private var sharedDetector: PaddlePredictor? = null
        private var sharedRecognizer: PaddlePredictor? = null
        private var isNativeLibLoaded = false
        private var detectionInputBuffer: FloatArray? = null
        private var lastUsedInputSize = 0
    }

    init {
        try {
            val arch = detectArch()
            if (sharedDetector == null || sharedRecognizer == null) {
                if (!isNativeLibLoaded) { loadNativeLibrary(); isNativeLibLoaded = true }
                val detPath = copyAssetToInternal("paddle/det_v4_dynamic_$arch.nb")
                val recPath = copyAssetToInternal("paddle/rec_v3_$arch.nb")
                sharedDetector = createPredictor(detPath)
                sharedRecognizer = createPredictor(recPath)
            }
            loadDictionary("paddle/en_dict.txt")
            isAvailable = true
        } catch (e: Throwable) {
            isAvailable = false
            initError = e.message
            Log.e("PaddleLite", "Failed to initialize predictors", e)
        }
    }

    private fun loadNativeLibrary() {
        val abi = Build.SUPPORTED_ABIS[0]; val libName = "libpaddle_lite_jni.so"; val assetPath = "libs_backup/${abi}_$libName"
        try { val internalLibPath = copyAssetToInternal(assetPath); System.load(internalLibPath) } catch (e: Exception) { System.loadLibrary("paddle_lite_jni") }
    }

    private fun detectArch(): String = when (Build.SUPPORTED_ABIS[0]) { "arm64-v8a" -> "armv8"; "armeabi-v7a" -> "armv7"; else -> "x86_64" }

    private fun copyAssetToInternal(assetPath: String): String {
        val file = File(context.filesDir, assetPath.replace("/", "_"))
        context.assets.open(assetPath).use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
        return file.absolutePath
    }

    private fun loadDictionary(assetPath: String) {
        dictionary.clear()
        context.assets.open(assetPath).bufferedReader().use { reader -> reader.forEachLine { dictionary.add(it) } }
    }

    private fun createPredictor(modelPath: String): PaddlePredictor {
        val config = MobileConfig(); config.setModelFromFile(modelPath); config.setThreads(4); config.setPowerMode(PowerMode.LITE_POWER_HIGH)
        return PaddlePredictor.createPaddlePredictor(config)
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Not Available: $initError", imageWidth = bitmap.width, imageHeight = bitmap.height)
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
        val detectionRes = runDetection(bitmap)
        
        for (i in detectionRes.rawBoxes.indices) {
            val rawBox = detectionRes.rawBoxes[i]
            val refinedBox = detectionRes.refinedBoxes.getOrNull(i)
            val orange = refinedBox?.boundingBox ?: rawBox.boundingBox
            
            val left = (orange.left * bitmap.width).toInt() - 8
            val top = (orange.top * bitmap.height).toInt() - 8
            val right = (orange.right * bitmap.width).toInt() + 8
            val bottom = (orange.bottom * bitmap.height).toInt() + 8
            val cropRect = Rect(max(0, left), max(0, top), min(bitmap.width, right), min(bitmap.height, bottom))

            if (cropRect.width() < 1 || cropRect.height() < 1) {
                textBlocks.add(TextBlock(text = "", boundingBox = cropRect, rawDiscoveryBox = rawBox.boundingBox, refinedDiscoveryBox = refinedBox?.boundingBox))
                continue
            }

            val crop = cropBitmap(bitmap, cropRect)
            val res = runRecognitionStage(crop, 48)
            crop.recycle()
            
            if (res.text.isNotBlank()) sb.append(res.text).append(" ")
            
            textBlocks.add(TextBlock(
                text = res.text, 
                boundingBox = cropRect, 
                angle = refinedBox?.angle ?: 0f,
                rawDiscoveryBox = rawBox.boundingBox,
                refinedDiscoveryBox = refinedBox?.boundingBox
            ))
        }

        return OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            discoveryTimeMs = detectionRes.discoveryTimeMs,
            debugText = sb.toString().trim(),
            rawDiscoveryBoxes = detectionRes.rawBoxes.map { it.boundingBox },
            textBlocks = textBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            scaleFactor = detectionRes.scale
        )
    }

    private data class DetectionResult(val rawBoxes: List<DetectedBox>, val refinedBoxes: List<DetectedBox>, val scale: Float, val discoveryTimeMs: Long)

    private fun runDetection(bitmap: Bitmap): DetectionResult {
        val predictor = sharedDetector ?: return DetectionResult(emptyList(), emptyList(), 1f, 0)
        val inputSize = 1280 
        val inputTensor = predictor.getInput(0); inputTensor.resize(longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()))
        if (detectionInputBuffer == null || lastUsedInputSize != inputSize) { detectionInputBuffer = FloatArray(1 * 3 * inputSize * inputSize); lastUsedInputSize = inputSize }
        val floatData = detectionInputBuffer!!
        
        val scale = min(inputSize.toFloat() / bitmap.width, inputSize.toFloat() / bitmap.height)
        val sw = (bitmap.width * scale).toInt(); val sh = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val padded = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded); canvas.drawColor(Color.BLACK); canvas.drawBitmap(scaled, 0f, 0f, null)
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
        
        try {
            inputTensor.setData(floatData); predictor.run()
            val outputTensor = predictor.getOutput(0); val dims = outputTensor.shape(); val outH = dims[2].toInt(); val outW = dims[3].toInt(); val outputData = outputTensor.floatData
            val dbRes = TfLiteOcrUtils.processDbNetOutput(outputData, outW, outH, scale = scale, sourceBitmap = bitmap, algorithm = "C")
            padded.recycle()
            return DetectionResult(dbRes.rawBoxes, dbRes.refinedBoxes, scale, dbRes.discoveryTimeMs)
        } catch (t: Throwable) { padded.recycle(); return DetectionResult(emptyList(), emptyList(), 1f, 0) }
    }

    private fun runRecognitionStage(bitmap: Bitmap, targetHeight: Int): RecStageResult {
        val tStart = System.currentTimeMillis(); val predictor = sharedRecognizer ?: return RecStageResult("", 0, 0f)
        val targetWidth = 640
        val inputTensor = predictor.getInput(0); inputTensor.resize(longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()))
        val floatData = FloatArray(1 * 3 * targetHeight * targetWidth)
        val scale = targetHeight.toFloat() / bitmap.height.toFloat()
        val sw = (bitmap.width * scale).toInt().coerceAtMost(targetWidth)
        val sh = targetHeight
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val padded = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded); canvas.drawColor(Color.BLACK); canvas.drawBitmap(scaled, 0f, 0f, null)
        scaled.recycle()
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val px = padded.getPixel(x, y)
                floatData[0 * targetHeight * targetWidth + y * targetWidth + x] = ((px shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f
                floatData[1 * targetHeight * targetWidth + y * targetWidth + x] = ((px shr 8 and 0xFF) / 255.0f - 0.5f) / 0.5f
                floatData[2 * targetHeight * targetWidth + y * targetWidth + x] = ((px and 0xFF) / 255.0f - 0.5f) / 0.5f
            }
        }
        padded.recycle()
        try {
            inputTensor.setData(floatData); predictor.run()
            val outputTensor = predictor.getOutput(0); val dims = outputTensor.shape(); val seqLen = dims[1].toInt(); val dictSize = dims[2].toInt(); val data = outputTensor.floatData
            val result = StringBuilder(); var lastIdx = -1; var totalConf = 0f; var charCount = 0
            for (i in 0 until seqLen) {
                var maxIdx = 0; var maxVal = -1f; for (j in 0 until dictSize) { val v = data[i * dictSize + j]; if (v > maxVal) { maxVal = v; maxIdx = j } }
                if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) { result.append(dictionary[maxIdx - 1]); totalConf += maxVal; charCount++ }
                lastIdx = maxIdx
            }
            return RecStageResult(result.toString(), System.currentTimeMillis() - tStart, if (charCount > 0) totalConf / charCount else 0f)
        } catch (t: Throwable) { return RecStageResult("", 0, 0f) }
    }

    private data class RecStageResult(val text: String, val timeMs: Long, val confidence: Float)
    private fun cropBitmap(bmp: Bitmap, rect: Rect): Bitmap {
        val left = max(0, rect.left); val top = max(0, rect.top); val width = min(rect.width(), bmp.width - left); val height = min(rect.height(), bmp.height - top)
        return Bitmap.createBitmap(bmp, left, top, width, height)
    }
}
