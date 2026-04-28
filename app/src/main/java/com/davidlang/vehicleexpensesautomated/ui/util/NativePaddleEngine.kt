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
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativePaddleEngine(private val context: Context, private val variant: String = "V3") : OcrEngine {
    override val name = "Paddle $variant Greedy"
    fun isV3() = variant == "V3"
    
    private val dictionary = mutableListOf<String>()
    private var initError: String? = null
    var isAvailable = false
        private set

    companion object {
        private var sharedDetector: PaddlePredictor? = null
        private var sharedRecognizerV3: PaddlePredictor? = null
        private var sharedRecognizerNumeric: PaddlePredictor? = null
        private var isNativeLibLoaded = false
        private var detectionInputBuffer: FloatArray? = null
        private var lastUsedInputSize = 0
        
        suspend fun runConstrainedStatic(bitmap: Bitmap, targetHeight: Int, dictionary: List<String>, isV3: Boolean): String = withContext(Dispatchers.IO) {
            val predictor = if (isV3) sharedRecognizerV3 else sharedRecognizerNumeric
            if (predictor == null) return@withContext ""
            runRecognitionStageStatic(bitmap, targetHeight, dictionary, predictor).text
        }

        private fun runRecognitionStageStatic(bitmap: Bitmap, ignoredHeight: Int, dictionary: List<String>, predictor: PaddlePredictor): RecStageResult {
            val tStart = System.currentTimeMillis()
            val targetHeight = 48
            val scale = targetHeight.toFloat() / bitmap.height.toFloat()
            val sw = (bitmap.width * scale).toInt()
            val targetWidth = ((sw + 31) / 32) * 32
            
            val inputTensor = predictor.getInput(0)
            inputTensor.resize(longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()))
            val floatData = FloatArray(1 * 3 * targetHeight * targetWidth)
            val scaled = Bitmap.createScaledBitmap(bitmap, sw, targetHeight, true)
            val padded = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(padded)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(scaled, 0f, 0f, null)
            scaled.recycle()

            val mean = 0.5f
            val std = 0.5f
            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val px = padded.getPixel(x, y)
                    floatData[0 * targetHeight * targetWidth + y * targetWidth + x] = ((px shr 16 and 0xFF) / 255.0f - mean) / std
                    floatData[1 * targetHeight * targetWidth + y * targetWidth + x] = ((px shr 8 and 0xFF) / 255.0f - mean) / std
                    floatData[2 * targetHeight * targetWidth + y * targetWidth + x] = ((px and 0xFF) / 255.0f - mean) / std
                }
            }
            padded.recycle()
            try {
                inputTensor.setData(floatData)
                predictor.run()
                val outputTensor = predictor.getOutput(0)
                val dims = outputTensor.shape()
                val seqLen = dims[1].toInt()
                val dictSize = dims[2].toInt()
                val data = outputTensor.floatData
                val result = StringBuilder()
                var lastIdx = -1
                var totalConf = 0f
                var charCount = 0
                for (i in 0 until seqLen) {
                    var maxIdx = 0
                    var maxVal = -1f 
                    // GREEDY DIGITS: Only consider indices 1..10 (0 is blank)
                    val searchLimit = 11.coerceAtMost(dictSize)
                    for (j in 0 until searchLimit) { 
                        val v = data[i * dictSize + j]
                        if (v > maxVal) { 
                            maxVal = v
                            maxIdx = j 
                        } 
                    }
                    if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) { 
                        result.append(dictionary[maxIdx - 1])
                        totalConf += maxVal
                        charCount++ 
                    }
                    lastIdx = maxIdx
                }
                return RecStageResult(result.toString(), System.currentTimeMillis() - tStart, if (charCount > 0) totalConf / charCount else 0f)
            } catch (t: Throwable) { 
                return RecStageResult("", 0, 0f) 
            }
        }

        private data class RecStageResult(val text: String, val timeMs: Long, val confidence: Float)
    }

    init {
        try {
            val arch = detectArch()
            if (!isNativeLibLoaded) { 
                loadNativeLibrary()
                isNativeLibLoaded = true 
            }
            if (sharedDetector == null) {
                sharedDetector = createPredictor(copyAssetToInternal("paddle/det_v4_dynamic_$arch.nb"))
            }
            if (variant == "V3" && sharedRecognizerV3 == null) {
                sharedRecognizerV3 = createPredictor(copyAssetToInternal("paddle/rec_v3_$arch.nb"))
            }
            if (variant == "V2" && sharedRecognizerNumeric == null) {
                sharedRecognizerNumeric = createPredictor(copyAssetToInternal("paddle/rec_numeric_$arch.nb"))
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
        val abi = Build.SUPPORTED_ABIS[0]
        val libName = "libpaddle_lite_jni.so"
        val assetPath = "libs_backup/${abi}_$libName"
        try { 
            val internalLibPath = copyAssetToInternal(assetPath)
            System.load(internalLibPath) 
        } catch (e: Exception) { 
            System.loadLibrary("paddle_lite_jni") 
        }
    }

    private fun detectArch(): String = when (Build.SUPPORTED_ABIS[0]) { 
        "arm64-v8a" -> "armv8"
        "armeabi-v7a" -> "armv7"
        else -> "x86_64" 
    }

    private fun copyAssetToInternal(assetPath: String): String {
        val file = File(context.filesDir, assetPath.replace("/", "_"))
        context.assets.open(assetPath).use { input -> 
            FileOutputStream(file).use { output -> 
                input.copyTo(output) 
            } 
        }
        return file.absolutePath
    }

    private fun loadDictionary(assetPath: String) {
        dictionary.clear()
        context.assets.open(assetPath).bufferedReader().use { reader -> 
            reader.forEachLine { dictionary.add(it) } 
        }
    }
    
    fun getDictionary(): List<String> = dictionary

    private fun createPredictor(modelPath: String): PaddlePredictor {
        val config = MobileConfig()
        config.setModelFromFile(modelPath)
        config.setThreads(4)
        config.setPowerMode(PowerMode.LITE_POWER_HIGH)
        return PaddlePredictor.createPaddlePredictor(config)
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = recognize(bitmap, false)

    suspend fun recognize(bitmap: Bitmap, isRecursive: Boolean): OcrResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Not Available: $initError", imageWidth = bitmap.width, imageHeight = bitmap.height)
        val t0 = System.currentTimeMillis()
        val predictor = if (variant == "V3") sharedRecognizerV3 else sharedRecognizerNumeric
        if (predictor == null) return@withContext OcrResult(engineName = name, debugText = "Predictor null", imageWidth = bitmap.width, imageHeight = bitmap.height)
        
        val finalResult = runRecognitionStageStatic(bitmap, 48, dictionary, predictor)
        OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            odometer = finalResult.text,
            debugText = finalResult.text,
            textBlocks = listOf(TextBlock(finalResult.text, Rect(0,0,bitmap.width, bitmap.height))),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }
}
