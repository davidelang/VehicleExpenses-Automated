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
class TfLiteOcrEngine(context: Context) {
    private var interpreter: Interpreter? = null
    private val labels = "0123456789"
    
    private var inputHeight = 50
    private var inputWidth = 200
    private var isGrayscale = true

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
        val outputShape = interp.getOutputTensor(0).shape()
        Log.i("TfLiteOcr", "Inference Output Shape: ${outputShape.joinToString(",")}")
        
        if (outputShape.contentEquals(intArrayOf(1, 1, 20))) {
            val outputBuffer = Array(1) { Array(1) { FloatArray(20) } }
            try {
                interp.run(inputBuffer, outputBuffer)
                val data = outputBuffer[0][0]
                // The model output is baked argmax indices. Apply CTC decoding.
                // Assuming index 0 is blank, indices 1..10 map to labels 0..9.
                val result = java.lang.StringBuilder()
                var lastIndex = -1
                for (v in data) {
                    val idx = v.toInt()
                    if (idx != 0 && idx != lastIndex) {
                        if (idx - 1 in labels.indices) {
                            result.append(labels[idx - 1])
                        }
                    }
                    lastIndex = idx
                }
                return result.toString()
            } catch (e: Exception) {
                return "(Error)"
            }
        } else {
            val timeSteps = outputShape[1]
            val numClasses = outputShape[2]
            val outputBuffer = Array(1) { Array(timeSteps) { FloatArray(numClasses) } }
            try {
                interp.run(inputBuffer, outputBuffer)
                // We pass blankIndex = numClasses - 1 for standard numeric TFLite models
                val (text, _) = TfLiteOcrUtils.decodeCtcGreedy(outputBuffer, labels.map { it.toString() }, blankIndex = numClasses - 1)
                return text
            } catch (e: Exception) {
                return "(Error)"
            }
        }
    }

    fun close() {
        interpreter?.close()
    }
}
