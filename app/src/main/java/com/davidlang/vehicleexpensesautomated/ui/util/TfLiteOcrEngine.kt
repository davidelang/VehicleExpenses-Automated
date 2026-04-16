package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Native TFLite Engine optimized for numeric_ocr.tflite [1, 200, 50, 1]
 */
class TfLiteOcrEngine(context: Context) {
    private var interpreter: Interpreter? = null
    private val labels = "0123456789"
    
    private var inputHeight = 32
    private var inputWidth = 128
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
            val inputShape = interp.getInputTensor(0).shape() // e.g. [1, 200, 50, 1]
            inputHeight = inputShape[1]
            inputWidth = inputShape[2]
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
        
        // 1. Pre-process based on discovered shape
        val scaled = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputHeight * inputWidth * (if (isGrayscale) 1 else 3) * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        for (y in 0 until inputHeight) {
            for (x in 0 until inputWidth) {
                val px = scaled.getPixel(x, y)
                if (isGrayscale) {
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    inputBuffer.putFloat((0.299f * r + 0.587f * g + 0.114f * b) / 255.0f)
                } else {
                    inputBuffer.putFloat(((px shr 16 and 0xFF) / 255.0f))
                    inputBuffer.putFloat(((px shr 8 and 0xFF) / 255.0f))
                    inputBuffer.putFloat(((px and 0xFF) / 255.0f))
                }
            }
        }
        scaled.recycle()

        // 2. Adaptive Output Handling
        val outputShape = interp.getOutputTensor(0).shape()
        
        if (outputShape.contentEquals(intArrayOf(1, 1, 20))) {
            // Specialized numeric extractor for [1, 1, 20]
            val outputBuffer = Array(1) { Array(1) { FloatArray(20) } }
            try {
                interp.run(inputBuffer, outputBuffer)
                val data = outputBuffer[0][0]
                // For this specific model, assume it returns digit probabilities or values
                // Placeholder: extract top categories
                return data.take(10).mapIndexed { i, v -> if (v > 0.5f) i.toString() else "" }.joinToString("").trim()
            } catch (e: Exception) {
                return "(Inference Error: ${e.message})"
            }
        } else {
            // Fallback to CTC for standard CRNN [1, Steps, Classes]
            val timeSteps = outputShape[1]
            val numClasses = outputShape[2]
            val outputBuffer = Array(1) { Array(timeSteps) { FloatArray(numClasses) } }
            try {
                interp.run(inputBuffer, outputBuffer)
                val (text, _) = TfLiteOcrUtils.decodeCtcGreedy(outputBuffer, labels.map { it.toString() }, blankIndex = 10)
                return if (text.isEmpty()) "(no digits)" else text
            } catch (e: Exception) {
                return "(Inference Error: ${e.message})"
            }
        }
    }

    fun close() {
        interpreter?.close()
    }
}
