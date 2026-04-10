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
        // TODO: Implement chained inference: Det -> Cls -> Rec
        // 1. Run Detector (Get Bounding Boxes)
        // 2. Crop/Rotate via Classifier
        // 3. Run Recognizer (Decode with dictionary)
        OcrResult(engineName = name, debugText = "Placeholder: PaddleOCR Recognition TBD")
    }

    fun close() {
        detInterpreter?.close()
        recInterpreter?.close()
        clsInterpreter?.close()
    }
}
