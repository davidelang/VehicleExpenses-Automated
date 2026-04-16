package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.davidlang.vehicleexpensesautomated.ui.util.TextBlock
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import com.davidlang.vehicleexpensesautomated.ui.util.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PaddleOcrEngine(context: Context) : OcrEngine {
    override val name = "PaddleOCR"
    
    private var detInterpreter: Interpreter? = null
    private var recInterpreter: Interpreter? = null
    private val dictionary = mutableListOf<String>()
    
    var isAvailable = false
        private set

    init {
        Log.i("PaddleOcr", "--- PaddleOcrEngine Initialization Started ---")
        try {
            // Load models from internal storage to avoid compression issues
            val detPath = copyAssetToInternal(context, "tflite/paddle/det_model.tflite")
            val recPath = copyAssetToInternal(context, "tflite/paddle/rec_model.tflite")
            Log.i("PaddleOcr", "Models copied to internal: $detPath, $recPath")

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                useNNAPI = false // AMD64 stability
            }

            detInterpreter = Interpreter(File(detPath), options)
            recInterpreter = Interpreter(File(recPath), options)
            Log.i("PaddleOcr", "Interpreters created successfully")
            
            val detInputShape = detInterpreter?.getInputTensor(0)?.shape()
            val detOutputShape = detInterpreter?.getOutputTensor(0)?.shape()
            Log.i("PaddleOcr", "Det Input Shape: ${detInputShape?.contentToString()}, Output Shape: ${detOutputShape?.contentToString()}")
            
            val recInputShape = recInterpreter?.getInputTensor(0)?.shape()
            val recOutputShape = recInterpreter?.getOutputTensor(0)?.shape()
            Log.i("PaddleOcr", "Rec Input Shape: ${recInputShape?.contentToString()}, Output Shape: ${recOutputShape?.contentToString()}")
            
            // Load dictionary
            val dictFile = context.assets.open("tflite/paddle/paddle_en_dict.txt")
            dictFile.bufferedReader().useLines { lines ->
                lines.forEach { dictionary.add(it) }
            }
            Log.i("PaddleOcr", "Dictionary loaded: ${dictionary.size} words")
            
            isAvailable = true
            Log.i("PaddleOcr", "PaddleOCR high-res models loaded successfully")
        } catch (e: Throwable) {
            isAvailable = false
            Log.e("PaddleOcr", "CRITICAL FAILURE in PaddleOcrEngine init: ${e.message}", e)
        }
    }

    private fun copyAssetToInternal(context: Context, assetPath: String): String {
        val file = File(context.cacheDir, assetPath.replace("/", "_"))
        // FORCE OVERWRITE to ensure high-res upgrade is applied
        context.assets.open(assetPath).use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val textBlocks = mutableListOf<TextBlock>()
        try {
            // 1. Run Detector (1280x1280 input as per instructions)
            val inputSize = 1280
            val resizedDet = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * inputSize * inputSize * 4).apply {
                order(ByteOrder.nativeOrder())
                for (y in 0 until inputSize) {
                    for (x in 0 until inputSize) {
                        val px = resizedDet.getPixel(x, y)
                        putFloat(((px shr 16 and 0xFF) / 255.0f - 0.485f) / 0.229f)
                        putFloat(((px shr 8 and 0xFF) / 255.0f - 0.456f) / 0.224f)
                        putFloat(((px and 0xFF) / 255.0f - 0.406f) / 0.225f)
                    }
                }
            }
            
            val outputBuffer = Array(1) { Array(1) { Array(inputSize) { FloatArray(inputSize) } } }
            detInterpreter?.run(inputBuffer, outputBuffer)
            
            // --- Robust DB-PostProcess (1280px) ---
            val flatHeatmap = FloatArray(inputSize * inputSize)
            var maxProb = 0f
            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    val p = outputBuffer[0][0][y][x]
                    flatHeatmap[y * inputSize + x] = p
                    if (p > maxProb) maxProb = p
                }
            }
            Log.i("PaddleOcr", "Detection Heatmap Max Probability: $maxProb")
            val boxes = TfLiteOcrUtils.processDbNetOutput(flatHeatmap, inputSize, inputSize, thresh = 0.3f)
            
            // 2. Recognition Pass (640x48 input / 80 steps / 97 classes)
            val results = StringBuilder()
            val scaleX = bitmap.width.toFloat() / inputSize
            val scaleY = bitmap.height.toFloat() / inputSize

            for (detectedBox in boxes) {
                val box = detectedBox.boundingBox
                // Scale box back to original image size for cropping
                val origBox = Rect(
                    (box.left * scaleX).toInt(),
                    (box.top * scaleY).toInt(),
                    (box.right * scaleX).toInt(),
                    (box.bottom * scaleY).toInt()
                )
                
                val cropWidth = origBox.width().coerceAtLeast(1)
                val cropHeight = origBox.height().coerceAtLeast(1)
                val crop = Bitmap.createBitmap(bitmap, origBox.left.coerceIn(0, bitmap.width-1), origBox.top.coerceIn(0, bitmap.height-1), 
                                             cropWidth.coerceAtMost(bitmap.width - origBox.left), cropHeight.coerceAtMost(bitmap.height - origBox.top))
                
                val recInput = Bitmap.createScaledBitmap(crop, 640, 48, true)
                val recBuffer = ByteBuffer.allocateDirect(1 * 48 * 640 * 3 * 4).apply {
                    order(ByteOrder.nativeOrder())
                    for (y in 0 until 48) {
                        for (x in 0 until 640) {
                            val px = recInput.getPixel(x, y)
                            putFloat(((px shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                            putFloat(((px shr 8 and 0xFF) / 255.0f - 0.5f) / 0.5f)
                            putFloat(((px and 0xFF) / 255.0f - 0.5f) / 0.5f)
                        }
                    }
                }
                
                val recOutput = Array(1) { Array(80) { FloatArray(97) } }
                recInterpreter?.run(recBuffer, recOutput)
                
                val decoded = TfLiteOcrUtils.decodeCtcGreedy(recOutput, dictionary, blankIndex = 0)
                if (decoded.isNotBlank()) {
                    results.append("$decoded ")
                    textBlocks.add(TextBlock(decoded, origBox))
                }
                
                crop.recycle()
                recInput.recycle()
            }
            
            resizedDet.recycle()
            OcrResult(
                engineName = name, 
                executionTimeMs = System.currentTimeMillis() - t0, 
                debugText = results.toString().trim(),
                textBlocks = textBlocks,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        } catch (e: Exception) {
            Log.e("PaddleOcr", "High-res inference failed", e)
            OcrResult(engineName = name, executionTimeMs = System.currentTimeMillis() - t0, debugText = "(TFLite Error: ${e.message})")
        }
    }

    fun close() {
        detInterpreter?.close()
        recInterpreter?.close()
    }
}
