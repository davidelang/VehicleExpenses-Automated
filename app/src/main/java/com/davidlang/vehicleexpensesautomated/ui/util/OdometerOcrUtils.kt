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
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.googlecode.tesseract.android.TessBaseAPI
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.floor

data class OcrResult(
    val odometer: String?,
    val possibleOdometers: List<String>,
    val gallons: String?,
    val cost: String?,
    val debugText: String,
    val originalPhotoPath: String? = null,
    val croppedBitmap: Bitmap? = null
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

        Log.d("OCRStage", "$stage - $label - Starting PaddleOCR (2-stage)")
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

            // Stage 1: OpenCV text detection
            val mat = Mat()
            org.opencv.android.Utils.bitmapToMat(bitmap, mat)
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)
            val thresh = Mat()
            Imgproc.threshold(gray, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            var bestRect: Rect? = null
            var bestArea = 0.0
            for (contour in contours) {
                val rect = Imgproc.boundingRect(contour)
                val aspect = rect.width.toDouble() / rect.height
                val area = rect.area()
                if (aspect in 1.5..8.0 && area > 200 && area > bestArea) {
                    bestArea = area
                    bestRect = rect
                }
            }

            val finalBitmap = if (bestRect != null && bestRect.width > 20 && bestRect.height > 20) {
                Log.d("OdometerOcr", "PaddleOCR Stage 1: detected text box ${bestRect.width}x${bestRect.height}")
                val croppedMat = Mat(thresh, bestRect)
                val croppedBmp = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888)
                org.opencv.android.Utils.matToBitmap(croppedMat, croppedBmp)
                croppedBmp
            } else {
                Log.w("OdometerOcr", "PaddleOCR Stage 1: no good contour found, using full image")
                bitmap
            }

            // Stage 2: Recognition — fixed 224x224 (model requirement)
            val resized = Bitmap.createScaledBitmap(finalBitmap, 224, 224, true)

            val shape = longArrayOf(1, 3, 224, 224)
            val floatArray = FloatArray(shape.reduce { a, b -> a * b }.toInt()) { 0.0f }
            var idx = 0
            for (y in 0 until 224) {
                for (x in 0 until 224) {
                    val pixel = resized.getPixel(x, y)
                    val r = ((pixel shr 16) and 0xFF) / 255.0f
                    val g = ((pixel shr 8) and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f
                    floatArray[idx++] = r
                    floatArray[idx++] = g
                    floatArray[idx++] = b
                }
            }

            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArray), shape)
            val inputName = session.inputNames.iterator().next()
            val outputs = session.run(mapOf(inputName to inputTensor))
            val outputTensor = outputs[0].value as Array<*>

            // Heavy debugging
            val vocab = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz.- "
            val blank = 0
            val decoded = StringBuilder()
            var previous = -1

            for (t in 0 until outputTensor.size) {
                val probs = outputTensor[t] as FloatArray
                val top5 = probs.indices.sortedByDescending { probs[it] }.take(5)
                val sb = StringBuilder("PaddleOCR timestep $t top5: ")
                for (i in top5) {
                    val ch = if (i < vocab.length) vocab[i] else '?'
                    sb.append("[$i='$ch' p=${"%.4f".format(probs[i])}] ")
                }
                Log.d("OdometerOcr", sb.toString())

                val maxIndex = top5[0]
                if (maxIndex != blank && maxIndex != previous) {
                    if (maxIndex < vocab.length) decoded.append(vocab[maxIndex])
                }
                previous = maxIndex
            }
            val result = decoded.toString().trim()

            Log.d("OdometerOcr", "PaddleOCR Stage 2 raw decoded: '$result'")

            session.close()
            resized.recycle()
            if (finalBitmap !== bitmap) finalBitmap.recycle()
            "PaddleOCR real result: $result"
        } catch (e: Exception) {
            Log.e("OdometerOcr", "PaddleOCR failed", e)
            "(PaddleOCR error: ${e.message})"
        }
    }

    suspend fun extractFromPhoto(photoPath: String, cropRect: RectF? = null): OcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext OcrResult(null, emptyList(), null, null, "Photo file not found", photoPath, null)

        var bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, emptyList(), null, null, "Failed to decode bitmap", photoPath, null)

        Log.d("OCRStage", "Stage 0 - Full image loaded, size ${bitmap.width}x${bitmap.height}")

        var croppedBitmap: Bitmap? = null
        if (cropRect != null) {
            val origW = bitmap.width
            val origH = bitmap.height
            val left = floor(cropRect.left * origW).toInt().coerceAtLeast(0)
            val top = floor(cropRect.top * origH).toInt().coerceAtLeast(0)
            val right = ceil(cropRect.right * origW).toInt().coerceAtMost(origW)
            val bottom = ceil(cropRect.bottom * origH).toInt().coerceAtMost(origH)

            Log.d("CropDebug", "Crop rect applied → left=$left top=$top right=$right bottom=$bottom (original ${origW}x${origH})")

            if (right > left && bottom > top) {
                croppedBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                bitmap.recycle()
                bitmap = croppedBitmap
                Log.d("OCRStage", "Stage 1 - Crop applied, new size ${bitmap.width}x${bitmap.height}")
            } else {
                Log.w("CropDebug", "Invalid crop rect after calculation — using full image")
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
            return@withContext OcrResult(null, emptyList(), null, null, "ML Kit error", photoPath, null)
        }

        visionText.textBlocks.forEach { block ->
            val blockText = block.text
            gallonsRegex.find(blockText)?.groupValues?.get(1)?.let { gallons = it }
            costRegex.find(blockText)?.groupValues?.get(1)?.let { cost = it }
        }

        if (croppedBitmap == null) {
            bitmap.recycle()
        }

        val debugText = buildString {
            appendLine("=== OCR DEBUG ===")
            appendLine("ML Kit: $ml")
            appendLine("Tesseract: $tess")
            appendLine("PaddleOCR: $paddle")
            appendLine("Final odometer: ${odometer ?: "NONE"}")
            appendLine("Candidates: ${possibleOdometers.joinToString()}")
        }

        OcrResult(odometer, possibleOdometers.distinct().sortedByDescending { it.length }, gallons, cost, debugText, photoPath, croppedBitmap)
    }
}
