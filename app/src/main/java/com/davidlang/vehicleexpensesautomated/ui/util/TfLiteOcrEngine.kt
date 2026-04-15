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
            options.setNumThreads(4)
            options.useNNAPI = false // Disabled for stability on emulators
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
                // Standard Grayscale conversion: 0.299R + 0.587G + 0.114B
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
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

        // 3. Robust CTC Greedy Decoder
        val decoded = TfLiteOcrUtils.decodeCtcGreedy(
            outputBuffer, 
            labels.map { it.toString() }, 
            blankIndex = 10
        )

        return if (decoded.isEmpty()) "(no digits)" else decoded
    }

    fun close() {
        interpreter?.close()
    }
}
