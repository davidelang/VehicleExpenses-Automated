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
 * Forensic Build: Inference Disabled to capture Tensor Shapes.
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
    }

    init {
        try {
            val arch = detectArch()
            if (sharedDetector == null || sharedRecognizer == null) {
                Log.i("PaddleLite", "Initializing Forensic Predictors for: $arch")
                
                if (!isNativeLibLoaded) {
                    loadNativeLibrary()
                    isNativeLibLoaded = true
                }
                
                val detPath = copyAssetToInternal("paddle/det_v4_1280_$arch.nb")
                val recPath = copyAssetToInternal("paddle/rec_v3_$arch.nb")
                
                sharedDetector = createPredictor(detPath)
                sharedRecognizer = createPredictor(recPath)

                // FORENSIC: Capture shapes before any inference attempt
                logForensicShape("Detector", sharedDetector)
                logForensicShape("Recognizer", sharedRecognizer)
            }
            
            loadDictionary("paddle/en_dict.txt")
            isAvailable = true
        } catch (e: Throwable) {
            isAvailable = false
            initError = e.message
            Log.e("PaddleLite", "Forensic Init Failed: ${e.message}", e)
        }
    }

    private fun logForensicShape(label: String, predictor: PaddlePredictor?) {
        predictor?.let { p ->
            try {
                val inputTensor = p.getInput(0)
                val shape = inputTensor.shape()
                Log.i("PaddleLite", "FORENSIC ($label): Input[0] Shape: ${shape.joinToString(", ")}")
            } catch (e: Exception) {
                Log.e("PaddleLite", "FORENSIC: Failed to query $label input 0", e)
            }
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
            Log.e("PaddleLite", "Failed JNI load: $assetPath", e)
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
        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Forensic Mode: $initError")
        
        Log.i("PaddleLite", "FORENSIC: recognize() called, skipping inference kernels...")
        
        return@withContext OcrResult(
            engineName = name,
            executionTimeMs = 0,
            debugText = "FORENSIC MODE: INFERENCE SKIPPED",
            textBlocks = emptyList(),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }
}
