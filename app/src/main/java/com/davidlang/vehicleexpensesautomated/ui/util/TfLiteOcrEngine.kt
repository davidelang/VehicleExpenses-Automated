package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class TfLiteOcrEngine(context: Context) {
    private var interpreter: Interpreter? = null
    private val labels = "0123456789"

    init {
        try {
            val model = loadModelFile(context, "tflite/numeric_ocr.tflite")
            val options = Interpreter.Options()
            // Optimization for Pixel devices
            options.setNumThreads(4)
            options.useNNAPI = true 
            interpreter = Interpreter(model, options)
            Log.i("TfLiteOcr", "Model loaded successfully with NNAPI")
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
        
        // 1. Pre-process: Resize to 128x32 (standard CRNN size) and Grayscale
        val resized = Bitmap.createScaledBitmap(bitmap, 128, 32, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 32 * 128 * 1 * 4) // Float32
        inputBuffer.order(ByteOrder.nativeOrder())
        
        for (y in 0 until 32) {
            for (x in 0 until 128) {
                val px = resized.getPixel(x, y)
                // Grayscale normalization (0 to 1.0)
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val gray = (r + g + b) / 3.0f / 255.0f
                inputBuffer.putFloat(gray)
            }
        }

        // 2. Output Buffer: (1, 31, 11) - 31 time steps, 11 classes (0-9 + blank)
        val outputBuffer = Array(1) { Array(31) { FloatArray(11) } }

        try {
            interp.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            return "(Inference Error)"
        }

        // 3. Greedy Decoder
        val sb = StringBuilder()
        var lastChar = -1
        for (t in 0 until 31) {
            val step = outputBuffer[0][t]
            var maxIdx = 0
            var maxVal = step[0]
            for (i in 1 until 11) {
                if (step[i] > maxVal) {
                    maxVal = step[i]; maxIdx = i
                }
            }
            
            // Assume 10 is the CTC blank label
            if (maxIdx != 10 && maxIdx != lastChar) {
                if (maxIdx < labels.length) {
                    sb.append(labels[maxIdx])
                }
            }
            lastChar = maxIdx
        }

        return if (sb.isEmpty()) "(no digits)" else sb.toString()
    }

    fun close() {
        interpreter?.close()
    }
}
