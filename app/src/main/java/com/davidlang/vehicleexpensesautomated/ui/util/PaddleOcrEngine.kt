package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.davidlang.vehicleexpensesautomated.ui.util.TextBlock
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import com.davidlang.vehicleexpensesautomated.ui.util.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class PaddleOcrEngine(context: Context) : OcrEngine {
    override val name = "PaddleOCR"
    
    private var detInterpreter: Interpreter? = null
    private var recInterpreter: Interpreter? = null
    private var clsInterpreter: Interpreter? = null
    private val dictionary = mutableListOf<String>()

    init {
        try {
            // Load models
            detInterpreter = Interpreter(loadModelFile(context, "tflite/paddle/det_model.pdmodel"))
            recInterpreter = Interpreter(loadModelFile(context, "tflite/paddle/rec_model.pdmodel"))
            clsInterpreter = Interpreter(loadModelFile(context, "tflite/paddle/cls_model.pdmodel"))
            
            // Load dictionary
            val dictFile = context.assets.open("tflite/paddle/paddle_en_dict.txt")
            dictFile.bufferedReader().useLines { lines ->
                lines.forEach { dictionary.add(it) }
            }
            
            Log.i("PaddleOcr", "PaddleOCR models and dictionary loaded successfully")
        } catch (e: Exception) {
            Log.e("PaddleOcr", "Failed to initialize PaddleOCR", e)
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.length
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        
        // 1. Run Detector (640x640 input)
        val resizedDet = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * 640 * 640 * 4).apply {
            order(ByteOrder.nativeOrder())
            // Normalize: (pixel - mean) / std. Assuming standard PP-OCR normalization.
            for (y in 0 until 640) {
                for (x in 0 until 640) {
                    val px = resizedDet.getPixel(x, y)
                    putFloat(((px shr 16 and 0xFF) / 255.0f - 0.485f) / 0.229f)
                    putFloat(((px shr 8 and 0xFF) / 255.0f - 0.456f) / 0.224f)
                    putFloat(((px and 0xFF) / 255.0f - 0.406f) / 0.225f)
                }
            }
        }
        
        // Output tensor shape for Det: [1, 1, 640, 640]
        val outputBuffer = Array(1) { Array(1) { Array(640) { FloatArray(640) } } }
        detInterpreter?.run(inputBuffer, outputBuffer)
        
        // Simple thresholding to find detections
        val boxes = mutableListOf<android.graphics.Rect>()
        for (y in 0 until 640 step 8) {
            for (x in 0 until 640 step 8) {
                if (outputBuffer[0][0][y][x] > 0.5f) {
                    boxes.add(android.graphics.Rect(x - 16, y - 16, x + 16, y + 16))
                }
            }
        }
        
        // 2. Run Classifier & Recognize
        val results = StringBuilder()
        for (box in boxes) {
            val clampedBox = android.graphics.Rect(
                box.left.coerceAtLeast(0), box.top.coerceAtLeast(0),
                box.right.coerceAtMost(640), box.bottom.coerceAtMost(640)
            )
            val crop = Bitmap.createBitmap(resizedDet, clampedBox.left, clampedBox.top, clampedBox.width(), clampedBox.height())
            
            // Run Classifier (Mocking full inference for now)
            val clsInput = Bitmap.createScaledBitmap(crop, 192, 48, true)
            // ... (clsInterpreter?.run(clsInput, ...) logic)
            
            // 3. Run Recognizer
            val recInput = Bitmap.createScaledBitmap(crop, 320, 32, true)
            val recBuffer = ByteBuffer.allocateDirect(1 * 32 * 320 * 1 * 4).apply {
                order(ByteOrder.nativeOrder())
                for (y in 0 until 32) {
                    for (x in 0 until 320) {
                        val px = recInput.getPixel(x, y)
                        putFloat(((px shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                    }
                }
            }
            // Recognize: [1, 1, 32, 320] -> Output [1, 40, 38] (e.g. chars x dict size)
            val recOutput = Array(1) { Array(40) { FloatArray(38) } }
            recInterpreter?.run(recBuffer, recOutput)
            
            // Greedy Decode
            val decoded = StringBuilder()
            for (i in 0 until 40) {
                var maxIdx = 0
                var maxVal = -1f
                for (j in 0 until 38) {
                    if (recOutput[0][i][j] > maxVal) {
                        maxVal = recOutput[0][i][j]
                        maxIdx = j
                    }
                }
                if (maxIdx > 0 && maxIdx <= dictionary.size) {
                    decoded.append(dictionary[maxIdx - 1])
                }
            }
            
            results.append("${decoded.toString()} ")
            crop.recycle()
            clsInput.recycle()
            recInput.recycle()
        }
        
        resizedDet.recycle()
        OcrResult(engineName = name, executionTimeMs = System.currentTimeMillis() - t0, debugText = results.toString())
    }

    fun close() {
        detInterpreter?.close()
        recInterpreter?.close()
        clsInterpreter?.close()
    }
}
