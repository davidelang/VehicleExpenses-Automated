package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.min

/**
 * Native TFLite Engine optimized for numeric_ocr.tflite [1, 50, 200, 1]
 * Note: Input shape is [batch, height, width, channels]
 */
class TfLiteOcrEngine(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val labels = "0123456789"
    
    private var inputHeight = 50
    private var inputWidth = 200
    private var isGrayscale = true

    companion object {
        var debugCounter = 0
    }

    init {
        try {
            val model = loadModelFile(context, "tflite/numeric_ocr.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                useNNAPI = false
            }
            val interp = Interpreter(model, options)
            interpreter = interp
            
            // DYNAMIC SHAPE DISCOVERY
            val inputShape = interp.getInputTensor(0).shape() // Expected [1, 50, 200, 1]
            // Enforce Width > Height to handle model export transpositions
            inputHeight = kotlin.math.min(inputShape[1], inputShape[2])
            inputWidth = kotlin.math.max(inputShape[1], inputShape[2])
            isGrayscale = inputShape[3] == 1
            
            Log.i("TfLiteOcr", "Model loaded. Shape: ${inputShape.joinToString(",")}, Gray=$isGrayscale")
        } catch (e: Exception) {
            Log.e("TfLiteOcr", "Failed to load model", e)
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

    fun runInference(bitmap: Bitmap): String {
        val interp = interpreter ?: return "(Model not loaded)"
        
        // 1. ASPECT-CORRECT PADDING: Avoid horizontal stretching
        val scale = inputHeight.toFloat() / bitmap.height.toFloat()
        val sw = (bitmap.width * scale).toInt().coerceAtMost(inputWidth)
        val sh = inputHeight
        
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val padded = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        scaled.recycle()

        val inputBuffer = ByteBuffer.allocateDirect(1 * inputHeight * inputWidth * (if (isGrayscale) 1 else 3) * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        // Transpose writing to match the model's unexpected [1, 200, 50, 1] tensor shape 
        // (Outer loop = Width/200, Inner loop = Height/50)
        for (x in 0 until inputWidth) {
            for (y in 0 until inputHeight) {
                val px = padded.getPixel(x, y)
                if (isGrayscale) {
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
                    inputBuffer.putFloat((gray - 0.5f) / 0.5f)
                } else {
                    val r = (px shr 16 and 0xFF) / 255.0f
                    val g = (px shr 8 and 0xFF) / 255.0f
                    val b = (px and 0xFF) / 255.0f
                    inputBuffer.putFloat((r - 0.5f) / 0.5f)
                    inputBuffer.putFloat((g - 0.5f) / 0.5f)
                    inputBuffer.putFloat((b - 0.5f) / 0.5f)
                }
            }
        }
        padded.recycle()

        // 2. Adaptive Output Handling
        try {
            // Allocate a flat byte buffer to capture dynamic sequence lengths (e.g. [1, 50, 20])
            val numClasses = interp.getOutputTensor(0).shape().last()
            val maxTimeSteps = inputWidth // Model cannot output more timesteps than input width
            val outputBuffer = ByteBuffer.allocateDirect(1 * maxTimeSteps * numClasses * 4)
            outputBuffer.order(ByteOrder.nativeOrder())
            
            // DUMP BUFFER TO DISK
            try {
                inputBuffer.rewind()
                val f = java.io.File(context.cacheDir, "tflite_in_${debugCounter++}.raw")
                val arr = ByteArray(inputBuffer.capacity())
                inputBuffer.get(arr)
                f.writeBytes(arr)
                inputBuffer.rewind()
            } catch (e: Exception) {
                Log.e("TfLiteOcr", "Failed to dump buffer", e)
            }

            interp.run(inputBuffer, outputBuffer)
            
            val finalShape = interp.getOutputTensor(0).shape()
            Log.i("TfLiteOcr", "Inference Final Shape: ${finalShape.joinToString(",")}")
            
            // The Java TFLite API stubbornly reports [1, 1, 20] even after inference.
            // We mathematically know the sequence length is width / 4 (e.g., 200 / 4 = 50).
            val timeSteps = inputWidth / 4
            val actualClasses = finalShape.last()
            
            outputBuffer.rewind()
            val sequence = Array(1) { Array(timeSteps) { FloatArray(actualClasses) } }
            val rawIndices = mutableListOf<Int>()
            for (t in 0 until timeSteps) {
                var maxVal = -Float.MAX_VALUE
                var maxIdx = -1
                for (c in 0 until actualClasses) {
                    val v = outputBuffer.float
                    sequence[0][t][c] = v
                    if (v > maxVal) {
                        maxVal = v
                        maxIdx = c
                    }
                }
                rawIndices.add(maxIdx)
            }
            Log.i("TfLiteOcr", "Raw CTC Indices: ${rawIndices.joinToString(",")}")
            
            // Model outputs probabilities. Index 19 is the blank token (verified via Python).
            // Indices 0..9 map directly to labels "0".."9".
            val (text, _) = TfLiteOcrUtils.decodeCtcGreedy(sequence, labels.map { it.toString() }, blankIndex = actualClasses - 1)
            return text
        } catch (e: Exception) {
            Log.e("TfLiteOcr", "Inference error", e)
            return "(Error)"
        }
    }

    fun close() {
        interpreter?.close()
    }
}
