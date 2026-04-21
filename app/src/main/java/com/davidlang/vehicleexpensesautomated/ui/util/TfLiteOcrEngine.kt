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

/**
 * Native TFLite Engine for alphanumeric and numeric-only OCR.
 * Supports:
 * - Alphanumeric: alphanumeric_ocr.tflite [1, 1, 32, 100] (NCHW)
 * - Numeric Only: numeric_only_ocr.tflite [1, 31, 200, 1] (NHWC)
 */
class TfLiteOcrEngine(private val context: Context, mode: ModelMode = ModelMode.ALPHANUMERIC) {
    private var interpreter: Interpreter? = null
    
    // Alphanumeric alphabet (37 classes: blank + 36 chars)
    private val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
    
    private var inputHeight = 32
    private var inputWidth = 100
    private var isGrayscale = true
    private var isNCHW = false

    enum class ModelMode {
        ALPHANUMERIC,
        NUMERIC_ONLY
    }

    companion object {
        var debugCounter = 0
    }

    init {
        try {
            val modelPath = when (mode) {
                ModelMode.ALPHANUMERIC -> "tflite/alphanumeric_ocr.tflite"
                ModelMode.NUMERIC_ONLY -> "tflite/numeric_only_ocr.tflite"
            }
            
            val model = loadModelFile(context, modelPath)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                useNNAPI = false
            }
            val interp = Interpreter(model, options)
            interpreter = interp
            
            // DYNAMIC SHAPE DISCOVERY
            val inputShape = interp.getInputTensor(0).shape()
            // Identify format: [1, 1, H, W] (NCHW) or [1, H, W, 1] (NHWC)
            if (inputShape[1] == 1 || inputShape[1] == 3) {
                isNCHW = true
                inputHeight = inputShape[2]
                inputWidth = inputShape[3]
                isGrayscale = inputShape[1] == 1
            } else {
                isNCHW = false
                inputHeight = inputShape[1]
                inputWidth = inputShape[2]
                isGrayscale = inputShape[3] == 1
            }
            
            Log.i("TfLiteOcr", "Model $mode loaded. Shape: ${inputShape.joinToString(",")}, NCHW=$isNCHW")
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
        
        val padded = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        
        val scale = targetH.toFloat() / bitmap.height.toFloat()
        val sw = (bitmap.width * scale).toInt().coerceAtMost(targetW)
        val sh = targetH
        
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        scaled.recycle()

        val channels = if (isGrayscale) 1 else 3
        val inputBuffer = ByteBuffer.allocateDirect(1 * targetH * targetW * channels * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        // Pass 2: Tensor Filling
        if (isNCHW) {
            // [Batch, Channels, Height, Width]
            for (c in 0 until channels) {
                for (y in 0 until targetH) {
                    for (x in 0 until targetW) {
                        val px = padded.getPixel(x, y)
                        val gray = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px))
                        inputBuffer.putFloat((gray / 255.0f - 0.5f) / 0.5f)
                    }
                }
            }
        } else {
            // [Batch, Height, Width, Channels]
            for (y in 0 until targetH) {
                for (x in 0 until targetW) {
                    val px = padded.getPixel(x, y)
                    if (isGrayscale) {
                        val gray = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px))
                        inputBuffer.putFloat((gray / 255.0f - 0.5f) / 0.5f)
                    } else {
                        inputBuffer.putFloat((Color.red(px) / 255.0f - 0.5f) / 0.5f)
                        inputBuffer.putFloat((Color.green(px) / 255.0f - 0.5f) / 0.5f)
                        inputBuffer.putFloat((Color.blue(px) / 255.0f - 0.5f) / 0.5f)
                    }
                }
            }
        }
        
        // MANDATED RESEARCH DUMP
        try {
            val f = java.io.File(context.cacheDir, "tflite_in_${debugCounter++}.raw")
            inputBuffer.rewind()
            val bytes = ByteArray(inputBuffer.capacity())
            inputBuffer.get(bytes)
            f.writeBytes(bytes)
            inputBuffer.rewind()
        } catch (e: Exception) { Log.e("TfLiteOcr", "Dump failed", e) }

        padded.recycle()

        // 2. Adaptive Output Handling
        try {
            val outputTensor = interp.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            val timeSteps = outputShape[1]
            val numClasses = outputShape[2]
            
            val outputBuffer = ByteBuffer.allocateDirect(1 * timeSteps * numClasses * 4)
            outputBuffer.order(ByteOrder.nativeOrder())
            
            interp.run(inputBuffer, outputBuffer)
            
            outputBuffer.rewind()
            val sequence = Array(1) { Array(timeSteps) { FloatArray(numClasses) } }
            for (t in 0 until timeSteps) {
                for (c in 0 until numClasses) {
                    sequence[0][t][c] = outputBuffer.float
                }
            }
            
            // MAP TO DICTIONARY (Index 0 is blank)
            val dictionary = mutableListOf("blank")
            for (char in alphabet) dictionary.add(char.toString())
            
            val (text, _) = TfLiteOcrUtils.decodeCtcGreedy(sequence, dictionary, blankIndex = 0)
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
