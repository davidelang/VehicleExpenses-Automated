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
    private val labels = " 0123456789.km/hMPH"
    
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
            val inputShape = interp.getInputTensor(0).shape() // Expected [1, 200, 50, 1] but usually transposed in Paddle
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
        
        // 1. ASPECT-CORRECT PADDING
        val targetH = inputHeight
        val targetW = inputWidth
        
        val scale = targetH.toFloat() / bitmap.height.toFloat()
        val sw = (bitmap.width * scale).toInt().coerceAtMost(targetW)
        val sh = targetH
        
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val padded = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        scaled.recycle()

        val inputBuffer = ByteBuffer.allocateDirect(1 * targetH * targetW * (if (isGrayscale) 1 else 3) * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        // Pass 2: Row-Major Tensor Loop with [0, 1] normalization
        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                val px = padded.getPixel(x, y)
                if (isGrayscale) {
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b)
                    inputBuffer.putFloat(gray / 255.0f)
                } else {
                    inputBuffer.putFloat(((px shr 16 and 0xFF) / 255.0f))
                    inputBuffer.putFloat(((px shr 8 and 0xFF) / 255.0f))
                    inputBuffer.putFloat(((px and 0xFF) / 255.0f))
                }
            }
        }
        padded.recycle()

        // 2. Adaptive Output Handling
        try {
            val numClasses = interp.getOutputTensor(0).shape().last()
            val timeSteps = kotlin.math.max(targetW, targetH) / 4
            val outputBuffer = ByteBuffer.allocateDirect(1 * timeSteps * numClasses * 4)
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
