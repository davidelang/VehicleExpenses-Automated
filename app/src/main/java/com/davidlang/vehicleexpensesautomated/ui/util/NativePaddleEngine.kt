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

    private var detector: PaddlePredictor? = null
    private var recognizer: PaddlePredictor? = null
    private val dictionary = mutableListOf<String>()
    
    private var isInitialized = false

    init {
        try {
            val arch = detectArch()
            val detModelPath = copyAssetToInternal("paddle/det_v4_1280_$arch.nb")
            val recModelPath = copyAssetToInternal("paddle/rec_v3_$arch.nb")
            
            val dictName = if (isConstrained) "digits_only.txt" else "en_dict.txt"
            loadDictionary("paddle/$dictName")

            if (!isConstrained) {
                detector = createPredictor(detModelPath)
            }
            recognizer = createPredictor(recModelPath)
            
            isInitialized = true
            Log.i("PaddleLite", "Initialized $name for arch $arch")
        } catch (e: Exception) {
            Log.e("PaddleLite", "Failed to initialize $name", e)
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
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.absolutePath
    }

    private fun loadDictionary(path: String) {
        context.assets.open(path).bufferedReader().useLines { lines ->
            lines.forEach { dictionary.add(it) }
        }
    }

    private fun createPredictor(modelPath: String): PaddlePredictor {
        val config = MobileConfig()
        config.setModelFromFile(modelPath)
        config.setPowerMode(PowerMode.LITE_POWER_HIGH)
        config.setThreads(4)
        return PaddlePredictor.createPaddlePredictor(config)
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext OcrResult(engineName = name, debugText = "Not Initialized")
        
        val t0 = System.currentTimeMillis()
        val textBlocks = mutableListOf<TextBlock>()
        
        if (isConstrained) {
            val resultText = runRecognition(bitmap)
            OcrResult(
                engineName = name,
                executionTimeMs = System.currentTimeMillis() - t0,
                debugText = resultText,
                textBlocks = listOf(TextBlock(resultText, Rect(0, 0, bitmap.width, bitmap.height))),
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        } else {
            val boxes = runDetection(bitmap)
            val sb = StringBuilder()
            for (box in boxes) {
                val crop = cropBitmap(bitmap, box)
                val text = runRecognition(crop)
                textBlocks.add(TextBlock(text, box))
                sb.append(text).append(" ")
                crop.recycle()
            }
            OcrResult(
                engineName = name,
                executionTimeMs = System.currentTimeMillis() - t0,
                debugText = sb.toString().trim(),
                textBlocks = textBlocks,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        }
    }

    private fun runDetection(bitmap: Bitmap): List<Rect> {
        val predictor = detector ?: return emptyList()
        
        val inputSize = 1280
        val scale = min(inputSize.toFloat() / bitmap.width, inputSize.toFloat() / bitmap.height)
        val sw = (bitmap.width * scale).toInt()
        val sh = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        
        val padded = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        
        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()))
        val floatData = FloatArray(1 * 3 * inputSize * inputSize)
        
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val px = padded.getPixel(x, y)
                val r = (px shr 16 and 0xFF) / 255.0f
                val g = (px shr 8 and 0xFF) / 255.0f
                val b = (px and 0xFF) / 255.0f
                
                floatData[0 * inputSize * inputSize + y * inputSize + x] = (r - mean[0]) / std[0]
                floatData[1 * inputSize * inputSize + y * inputSize + x] = (g - mean[1]) / std[1]
                floatData[2 * inputSize * inputSize + y * inputSize + x] = (b - mean[2]) / std[2]
            }
        }
        inputTensor.setData(floatData)
        predictor.run()
        
        val outputTensor = predictor.getOutput(0)
        val outData = outputTensor.floatData
        
        // --- DB-PostProcess using OpenCV ---
        val probMap = org.opencv.core.Mat(inputSize, inputSize, org.opencv.core.CvType.CV_32F)
        probMap.put(0, 0, outData)
        
        // 1. Threshold
        val binaryMap = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.threshold(probMap, binaryMap, 0.3, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
        binaryMap.convertTo(binaryMap, org.opencv.core.CvType.CV_8U)
        
        // 2. Find Contours
        val contours = mutableListOf<org.opencv.core.MatOfPoint>()
        val hierarchy = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.findContours(binaryMap, contours, hierarchy, org.opencv.imgproc.Imgproc.RETR_EXTERNAL, org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE)
        
        val detectedBoxes = mutableListOf<Rect>()
        val invScale = 1.0f / scale
        
        for (contour in contours) {
            val rect = org.opencv.imgproc.Imgproc.boundingRect(contour)
            if (rect.width > 10 && rect.height > 10) {
                // Map back to original image dimensions
                val originalRect = Rect(
                    (rect.x * invScale).toInt(),
                    (rect.y * invScale).toInt(),
                    ((rect.x + rect.width) * invScale).toInt(),
                    ((rect.y + rect.height) * invScale).toInt()
                )
                detectedBoxes.add(originalRect)
            }
        }
        
        probMap.release(); binaryMap.release(); hierarchy.release()
        scaled.recycle()
        padded.recycle()
        return detectedBoxes
    }

    private fun runRecognition(bitmap: Bitmap): String {
        val predictor = recognizer ?: return ""
        
        val targetH = 640
        val scale = targetH.toFloat() / bitmap.height
        val targetW = (bitmap.width * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        
        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, targetH.toLong(), targetW.toLong()))
        val floatData = FloatArray(1 * 3 * targetH * targetW)
        
        val mean = 0.5f
        val std = 0.5f
        
        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                val px = scaled.getPixel(x, y)
                floatData[0 * targetH * targetW + y * targetW + x] = ((px shr 16 and 0xFF) / 255.0f - mean) / std
                floatData[1 * targetH * targetW + y * targetW + x] = ((px shr 8 and 0xFF) / 255.0f - mean) / std
                floatData[2 * targetH * targetW + y * targetW + x] = ((px and 0xFF) / 255.0f - mean) / std
            }
        }
        inputTensor.setData(floatData)
        predictor.run()
        
        val outputTensor = predictor.getOutput(0)
        val dims = outputTensor.shape()
        val seqLen = dims[1].toInt()
        val dictSize = dims[2].toInt()
        val data = outputTensor.floatData
        
        val result = StringBuilder()
        var lastIdx = -1
        for (i in 0 until seqLen) {
            var maxIdx = 0
            var maxVal = -1f
            for (j in 0 until dictSize) {
                val v = data[i * dictSize + j]
                if (v > maxVal) {
                    maxVal = v
                    maxIdx = j
                }
            }
            if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) {
                result.append(dictionary[maxIdx - 1])
            }
            lastIdx = maxIdx
        }
        
        scaled.recycle()
        return result.toString()
    }

    private fun cropBitmap(bmp: Bitmap, rect: Rect): Bitmap {
        val left = max(0, rect.left); val top = max(0, rect.top)
        val width = min(rect.width(), bmp.width - left); val height = min(rect.height(), bmp.height - top)
        return Bitmap.createBitmap(bmp, left, top, width, height)
    }
}
