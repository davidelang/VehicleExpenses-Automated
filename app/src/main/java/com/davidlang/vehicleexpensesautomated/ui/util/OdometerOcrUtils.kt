package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.googlecode.tesseract.android.TessBaseAPI
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer

data class OcrResult(
    val odometer: String?,
    val possibleOdometers: List<String>,
    val gallons: String?,
    val cost: String?,
    val debugText: String
)

object OdometerOcrUtils {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("OdometerOcr", "OpenCV initialization failed!")
        } else {
            Log.i("OdometerOcr", "OpenCV initialized successfully")
        }
    }

    private suspend fun runAllThreeEngines(bitmap: Bitmap, label: String, stage: String): Triple<String, String, String> {
        Log.d("OCRStage", "$stage - $label - Starting ML Kit")
        val mlResult = try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val text: Text = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            text.text.ifEmpty { "(no text)" }
        } catch (e: Exception) {
            Log.e("OdometerOcr", "ML Kit failed for $label", e)
            "(ML Kit error)"
        }
        Log.d("OCRStage", "$stage - $label - ML Kit: $mlResult")

        Log.d("OCRStage", "$stage - $label - Starting Tesseract")
        val tessResult = runTesseract(bitmap)
        Log.d("OCRStage", "$stage - $label - Tesseract: $tessResult")

        Log.d("OCRStage", "$stage - $label - Starting PaddleOCR")
        val paddleResult = runPaddleOcr(bitmap)
        Log.d("OCRStage", "$stage - $label - PaddleOCR: $paddleResult")

        return Triple(mlResult, tessResult, paddleResult)
    }

    private fun runTesseract(bitmap: Bitmap): String {
        return try {
            val tess = TessBaseAPI()
            val tessDataPath = "/data/user/0/com.davidlang.vehicleexpensesautomated/files"
            if (!tess.init(tessDataPath, "eng")) {
                tess.clear()
                Log.e("OdometerOcr", "Tesseract init failed")
                return "(Tesseract init failed)"
            }
            tess.setImage(bitmap)
            val result = tess.getUTF8Text()?.trim() ?: "(no text)"
            tess.clear()
            result
        } catch (e: Exception) {
            Log.e("OdometerOcr", "Tesseract failed", e)
            "(Tesseract error: ${e.message})"
        }
    }

    private fun runPaddleOcr(bitmap: Bitmap): String {
        val modelFile = File("/data/user/0/com.davidlang.vehicleexpensesautomated/files/paddleocr.onnx")
        return try {
            if (!modelFile.exists()) return "(PaddleOCR model not found on device)"
            val env = OrtEnvironment.getEnvironment()
            val modelBytes = modelFile.readBytes()
            val session = env.createSession(modelBytes)
            Log.i("OdometerOcr", "PaddleOCR ONNX session created successfully")

            val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

            val shape = longArrayOf(1, 3, 224, 224)
            val floatArray = FloatArray(shape.reduce { a, b -> a * b }.toInt()) { 0.0f }
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArray), shape)

            val inputName = session.inputNames.iterator().next()
            val outputs = session.run(mapOf(inputName to inputTensor))
            val outputTensor = outputs[0].value as Array<*>

            val vocab = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz.- "
            val decoded = StringBuilder()
            for (t in 0 until outputTensor.size) {
                val probs = outputTensor[t] as FloatArray
                val maxIndex = probs.indices.maxByOrNull { probs[it] } ?: 0
                if (maxIndex < vocab.length) decoded.append(vocab[maxIndex])
            }
            val result = decoded.toString().trim()

            session.close()
            resized.recycle()
            "PaddleOCR real result: $result"
        } catch (e: Exception) {
            Log.e("OdometerOcr", "PaddleOCR failed", e)
            "(PaddleOCR error: ${e.message})"
        }
    }

    suspend fun extractFromPhoto(photoPath: String, cropRect: RectF? = null): OcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext OcrResult(null, emptyList(), null, null, "Photo file not found")

        var bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, emptyList(), null, null, "Failed to decode bitmap")

        Log.d("OCRStage", "Stage 0 - Full image loaded, size ${bitmap.width}x${bitmap.height}")

        if (cropRect != null) {
            val origW = bitmap.width
            val origH = bitmap.height
            val left = (cropRect.left * origW).toInt().coerceAtLeast(0)
            val top = (cropRect.top * origH).toInt().coerceAtLeast(0)
            val right = (cropRect.right * origW).toInt().coerceAtMost(origW)
            val bottom = (cropRect.bottom * origH).toInt().coerceAtMost(origH)

            if (right > left && bottom > top) {
                val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                bitmap.recycle()
                bitmap = cropped
                Log.d("OCRStage", "Stage 1 - Crop applied, new size ${bitmap.width}x${bitmap.height}")
            }
        }

        val (ml, tess, paddle) = runAllThreeEngines(bitmap, "final", "Stage 2")

        val rawText = ml
        val cleanText = rawText.replace("I", "1").replace("l", "1").replace("O", "0").replace("B", "8").replace("S", "5").replace("Z", "2").replace("L", "1").replace(" ", "").replace("\n", "").replace("\r", "")

        val odoRegex = "\\b\\d{4,8}\\b".toRegex()
        val possibleOdometers = mutableListOf<String>()
        var odometer: String? = null
        odoRegex.findAll(cleanText).forEach { match ->
            val value = match.value
            possibleOdometers.add(value)
            if (odometer == null || value.length > (odometer?.length ?: 0)) odometer = value
        }

        val gallonsRegex = "\\b(\\d{1,2}\\.\\d{1,3})\\s*(?:gal|gallons)\\b".toRegex(RegexOption.IGNORE_CASE)
        val costRegex = "\\$?(\\d{1,3}\\.\\d{2})".toRegex()
        var gallons: String? = null
        var cost: String? = null

        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText: Text = try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { e -> continuation.resumeWithException(e) }
            }
        } catch (e: Exception) {
            Log.e("OdometerOcr", "ML Kit error", e)
            bitmap.recycle()
            return@withContext OcrResult(null, emptyList(), null, null, "ML Kit error")
        }

        visionText.textBlocks.forEach { block ->
            val blockText = block.text
            gallonsRegex.find(blockText)?.groupValues?.get(1)?.let { gallons = it }
            costRegex.find(blockText)?.groupValues?.get(1)?.let { cost = it }
        }

        bitmap.recycle()

        val debugText = buildString {
            appendLine("=== OCR DEBUG ===")
            appendLine("ML Kit: $ml")
            appendLine("Tesseract: $tess")
            appendLine("PaddleOCR: $paddle")
            appendLine("Final odometer: ${odometer ?: "NONE"}")
            appendLine("Candidates: ${possibleOdometers.joinToString()}")
        }

        OcrResult(odometer, possibleOdometers.distinct().sortedByDescending { it.length }, gallons, cost, debugText)
    }
}
