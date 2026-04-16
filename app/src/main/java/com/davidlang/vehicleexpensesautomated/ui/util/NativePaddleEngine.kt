package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.baidu.paddle.lite.PaddlePredictor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.os.Build

/**
 * Native Paddle-Lite 2.14rc OCR Engine.
 * Forensic Build (Stage 2): Force Resize and Query Shape.
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
        } catch (e: Throwable) {
            isAvailable = false
            initError = e.message
            Log.e("PaddleLite", "Forensic Init Failed: ${e.message}", e)
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
        val config = com.baidu.paddle.lite.MobileConfig()
        config.setModelFromFile(modelPath)
        config.setThreads(1)
        config.setPowerMode(com.baidu.paddle.lite.PowerMode.LITE_POWER_NO_BIND)
        return PaddlePredictor.createPaddlePredictor(config)
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Forensic Mode: $initError")
        
        Log.i("PaddleLite", "Starting FORENSIC SHAPE COMMIT...")
        
        try {
            // 1. Force Detector Resize
            sharedDetector?.let { p ->
                val tensor = p.getInput(0)
                Log.i("PaddleLite", "FORENSIC: Resizing Detector to [1, 3, 1280, 1280]...")
                tensor.resize(longArrayOf(1, 3, 1280, 1280))
                val shape = tensor.shape()
                Log.i("PaddleLite", "FORENSIC: Detector Shape After Resize: ${shape.joinToString(", ") { it.toString() }}")
            }

            // 2. Force Recognizer Resize
            sharedRecognizer?.let { p ->
                val tensor = p.getInput(0)
                Log.i("PaddleLite", "FORENSIC: Resizing Recognizer to [1, 3, 48, 640]...")
                tensor.resize(longArrayOf(1, 3, 48, 640))
                val shape = tensor.shape()
                Log.i("PaddleLite", "FORENSIC: Recognizer Shape After Resize: ${shape.joinToString(", ") { it.toString() }}")
            }
        } catch (e: Exception) {
            Log.e("PaddleLite", "FORENSIC: Resize Failed: ${e.message}", e)
        }
        
        return@withContext OcrResult(
            engineName = name,
            executionTimeMs = 0,
            debugText = "FORENSIC MODE: SHAPE COMMIT COMPLETE",
            textBlocks = emptyList(),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }
}
