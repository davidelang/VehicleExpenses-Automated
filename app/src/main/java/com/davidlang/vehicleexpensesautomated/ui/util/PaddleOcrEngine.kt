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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class PaddleOcrEngine(context: Context) : OcrEngine {
    override val name = "PaddleOCR"
    
    private var detInterpreter: Interpreter? = null
    private var recInterpreter: Interpreter? = null
    private var clsInterpreter: Interpreter? = null
    private val dictionary = mutableListOf<String>()
    
    var isAvailable = false
        private set

    init {
        try {
            // Load models from internal storage to avoid compression issues
            val detPath = copyAssetToInternal(context, "tflite/paddle/det_model.tflite")
            val recPath = copyAssetToInternal(context, "tflite/paddle/rec_model.tflite")
            // val clsPath = copyAssetToInternal(context, "tflite/paddle/cls_model.tflite") // Skip if not found

            detInterpreter = Interpreter(File(detPath))
            recInterpreter = Interpreter(File(recPath))
            // clsInterpreter = Interpreter(File(clsPath))
            
            // Load dictionary
            val dictFile = context.assets.open("tflite/paddle/paddle_en_dict.txt")
            dictFile.bufferedReader().useLines { lines ->
                lines.forEach { dictionary.add(it) }
            }
            
            isAvailable = true
            Log.i("PaddleOcr", "PaddleOCR models and dictionary loaded successfully from cache")
        } catch (e: Throwable) {
            isAvailable = false
            Log.e("PaddleOcr", "Failed to initialize PaddleOCR: ${e.message}")
        }
    }

    private fun copyAssetToInternal(context: Context, assetPath: String): String {
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

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        
        // 1. Run Detector (640x640 input)
        val resizedDet = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * 640 * 640 * 4).apply {
            order(ByteOrder.nativeOrder())
            // Normalize: (pixel - mean) / std.
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
        
        // --- Robust DB-PostProcess ---
        val flatHeatmap = FloatArray(640 * 640)
        for (y in 0 until 640) {
            for (x in 0 until 640) {
                flatHeatmap[y * 640 + x] = outputBuffer[0][0][y][x]
            }
        }
        val boxes = TfLiteOcrUtils.processDbNetOutput(flatHeatmap, 640, 640, thresh = 0.3f)
        
        // 2. Run Classifier & Recognize
        val results = StringBuilder()
        for (box in boxes) {
            val crop = Bitmap.createBitmap(resizedDet, box.left, box.top, box.width(), box.height())
            
            // Run Classifier (Temporarily skipped until .tflite model provided)
            /*
            val clsInput = Bitmap.createScaledBitmap(crop, 192, 48, true)
            val clsBuffer = ByteBuffer.allocateDirect(1 * 48 * 192 * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
                for (y in 0 until 48) {
                    for (x in 0 until 192) {
                        val px = clsInput.getPixel(x, y)
                        putFloat(((px shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                        putFloat(((px shr 8 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                        putFloat(((px and 0xFF) / 255.0f - 0.5f) / 0.5f)
                    }
                }
            }
            val clsOutput = Array(1) { FloatArray(4) }
            clsInterpreter?.run(clsBuffer, clsOutput)
            */
            
            // 3. Run Recognizer
            val recInput = Bitmap.createScaledBitmap(crop, 320, 32, true)
            val recBuffer = ByteBuffer.allocateDirect(1 * 32 * 320 * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
                for (y in 0 until 32) {
                    for (x in 0 until 320) {
                        val px = recInput.getPixel(x, y)
                        putFloat(((px shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                        putFloat(((px shr 8 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                        putFloat(((px and 0xFF) / 255.0f - 0.5f) / 0.5f)
                    }
                }
            }
            val recOutput = Array(1) { Array(40) { FloatArray(38) } }
            recInterpreter?.run(recBuffer, recOutput)
            
            // Robust CTC Decode
            val decoded = TfLiteOcrUtils.decodeCtcGreedy(recOutput, dictionary, blankIndex = 0)
            
            results.append("$decoded ")
            crop.recycle()
            // clsInput.recycle()
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
