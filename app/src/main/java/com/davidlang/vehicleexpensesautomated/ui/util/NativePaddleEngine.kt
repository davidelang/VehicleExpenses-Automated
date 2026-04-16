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
    
    companion object {
        private var sharedDetector: PaddlePredictor? = null
        private var sharedRecognizer: PaddlePredictor? = null
        private var isNativeLibLoaded = false
        private var initError: String? = null
        
        // DEBUG FLAG: Set to true to test model kernels without loading real data
        private const val DEBUG_DRY_RUN = true
        // DEBUG FLAG: Set to true to bypass the Detection model and only test Recognition
        private const val DEBUG_BYPASS_DETECTION = true
    }

    init {
        try {
            val arch = detectArch()
            
            if (arch == "x86_64") {
                isAvailable = false
                initError = "Disabled on amd64 due to AVX2/FMA build incompatibility."
                Log.w("PaddleLite", "Native engine DISABLED for architecture: $arch")
            } else {
                if (sharedDetector == null || sharedRecognizer == null) {
                    Log.i("PaddleLite", "Initializing Shared Predictors for architecture: $arch (DRY_RUN=$DEBUG_DRY_RUN)")
                    
                    if (!isNativeLibLoaded) {
                        loadNativeLibrary()
                        isNativeLibLoaded = true
                    }
                    
                    val detPath = copyAssetToInternal("paddle/det_v4_1280_$arch.nb")
                    val recPath = copyAssetToInternal("paddle/rec_v3_$arch.nb")
                    
                    sharedDetector = createPredictor(detPath)
                    sharedRecognizer = createPredictor(recPath)
                }
                
                loadDictionary("paddle/en_dict.txt")
                isAvailable = true
                Log.i("PaddleLite", "Native engine $name ready on $arch")
            }
        } catch (e: Throwable) {
            isAvailable = false
            initError = e.message
            Log.e("PaddleLite", "Failed to initialize shared predictors: ${e.message}", e)
        }
    }

    private fun loadNativeLibrary() {
        val abi = Build.SUPPORTED_ABIS[0]
        val libName = "libpaddle_lite_jni.so"
        val assetPath = "libs_backup/${abi}_$libName"
        
        try {
            val internalLibPath = copyAssetToInternal(assetPath)
            System.load(internalLibPath)
            Log.i("PaddleLite", "Loaded isolated JNI: $internalLibPath")
        } catch (e: Exception) {
            Log.e("PaddleLite", "Failed to load isolated JNI from $assetPath", e)
            System.loadLibrary("paddle_lite_jni")
        }
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
            context.assets.open(assetPath).use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        }
        return file.absolutePath
    }

    private fun loadDictionary(path: String) {
        if (dictionary.isNotEmpty()) return
        context.assets.open(path).bufferedReader().useLines { lines ->
            lines.forEach { dictionary.add(it) }
        }
    }

    private fun createPredictor(modelPath: String): PaddlePredictor {
        val config = MobileConfig()
        config.setModelFromFile(modelPath)
        config.setThreads(1) 
        config.setPowerMode(PowerMode.LITE_POWER_NO_BIND)
        return PaddlePredictor.createPaddlePredictor(config)
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Not Available: $initError")
        
        val t0 = System.currentTimeMillis()
        return@withContext if (isConstrained) {
            recognizeConstrained(bitmap, t0)
        } else {
            recognizeDiscovery(bitmap, t0)
        }
    }

    private suspend fun recognizeConstrained(bitmap: Bitmap, t0: Long): OcrResult {
        val stage1 = runRecognitionStage(bitmap, 48)
        val digits = stage1.text.filter { it.isDigit() }
        val finalResult = if (digits.length >= 2) stage1 else runRecognitionStage(bitmap, 640)

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

    private suspend fun recognizeDiscovery(bitmap: Bitmap, t0: Long): OcrResult {
        val textBlocks = mutableListOf<TextBlock>()
        val sb = StringBuilder()

        if (DEBUG_BYPASS_DETECTION) {
            Log.i("PaddleLite", "DEBUG: Bypassing Detection, running single Recognition sweep...")
            val res = runRecognitionStage(bitmap, 48)
            sb.append("REC_ONLY: ").append(res.text)
            textBlocks.add(TextBlock(res.text, Rect(0,0,bitmap.width, bitmap.height), 0f, mapOf("Resolution" to "48px")))
        } else {
            val boxes = runDetection(bitmap)
            for (detectedBox in boxes) {
                val box = detectedBox.boundingBox
                if (box.width() < 1 || box.height() < 1) continue
                val crop = cropBitmap(bitmap, box)
                val res48 = runRecognitionStage(crop, 48)
                val res128 = runRecognitionStage(crop, 128)
                val resNative = runRecognitionStage(crop, crop.height)
                crop.recycle()

                val sweepMeta = mapOf(
                    "sweep_48" to "${res48.text} (${res48.timeMs}ms)",
                    "sweep_128" to "${res128.text} (${res128.timeMs}ms)",
                    "sweep_native" to "${resNative.text} (${resNative.timeMs}ms)"
                )

                if (resNative.text.isNotBlank()) {
                    sb.append(resNative.text).append(" ")
                    textBlocks.add(TextBlock(resNative.text, box, detectedBox.angle, sweepMeta))
                }
            }
        }

        return OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = if (DEBUG_DRY_RUN) "DRY RUN SUCCESSFUL (${sb.toString().trim()})" else sb.toString().trim(),
            textBlocks = textBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }

    private fun runDetection(bitmap: Bitmap): List<DetectedBox> {
        val predictor = sharedDetector ?: return emptyList()
        val t0 = System.currentTimeMillis()
        
        val inputSize = 1280
        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()))
        
        val totalElements = 1 * 3 * inputSize * inputSize
        val floatData = FloatArray(totalElements)
        
        if (!DEBUG_DRY_RUN) {
            val scale = min(inputSize.toFloat() / bitmap.width, inputSize.toFloat() / bitmap.height)
            val sw = (bitmap.width * scale).toInt()
            val sh = (bitmap.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
            val padded = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(padded)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(scaled, 0f, 0f, null)
            
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)
            
            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    val px = padded.getPixel(x, y)
                    floatData[0 * inputSize * inputSize + y * inputSize + x] = ((px shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
                    floatData[1 * inputSize * inputSize + y * inputSize + x] = ((px shr 8 and 0xFF) / 255.0f - mean[1]) / std[1]
                    floatData[2 * inputSize * inputSize + y * inputSize + x] = ((px and 0xFF) / 255.0f - mean[2]) / std[2]
                }
            }
            scaled.recycle()
            padded.recycle()
        }
        
        try {
            inputTensor.setData(floatData)
            Log.i("PaddleLite", "Starting Detection run...")
            predictor.run()
            Log.i("PaddleLite", "Detection run success.")
            
            val outputTensor = predictor.getOutput(0)
            return TfLiteOcrUtils.processDbNetOutput(outputTensor.floatData, inputSize, inputSize, thresh = 0.3f)
        } catch (t: Throwable) {
            Log.e("PaddleLite", "FATAL CRASH in runDetection", t)
            throw t
        }
    }

    private data class RecStageResult(val text: String, val timeMs: Long, val height: Int)

    private fun runRecognitionStage(bitmap: Bitmap, targetHeight: Int): RecStageResult {
        val tStart = System.currentTimeMillis()
        val predictor = sharedRecognizer ?: return RecStageResult("", 0, targetHeight)
        val targetWidth = 640
        
        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()))
        
        val totalElements = 1 * 3 * targetHeight * targetWidth
        val floatData = FloatArray(totalElements)
        
        if (!DEBUG_DRY_RUN) {
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
        }

        try {
            inputTensor.setData(floatData)
            Log.i("PaddleLite", "Starting Recognition run (H=$targetHeight)...")
            predictor.run()
            Log.i("PaddleLite", "Recognition run success.")
            
            val outputTensor = predictor.getOutput(0)
            val dims = outputTensor.shape()
            val seqLen = dims[1].toInt()
            val dictSize = dims[2].toInt()
            val data = outputTensor.floatData
            
            val result = StringBuilder()
            var lastIdx = -1
            for (i in 0 until seqLen) {
                var maxIdx = 0; var maxVal = -1f
                for (j in 0 until dictSize) {
                    val v = data[i * dictSize + j]
                    if (v > maxVal) { maxVal = v; maxIdx = j }
                }
                if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) {
                    result.append(dictionary[maxIdx - 1])
                }
                lastIdx = maxIdx
            }
            return RecStageResult(result.toString(), System.currentTimeMillis() - tStart, targetHeight)
        } catch (t: Throwable) {
            Log.e("PaddleLite", "FATAL CRASH in runRecognitionStage (H=$targetHeight)", t)
            throw t
        }
    }

    private fun cropBitmap(bmp: Bitmap, rect: Rect): Bitmap {
        val left = max(0, rect.left); val top = max(0, rect.top)
        val width = min(rect.width(), bmp.width - left); val height = min(rect.height(), bmp.height - top)
        return Bitmap.createBitmap(bmp, left, top, width, height)
    }
}
