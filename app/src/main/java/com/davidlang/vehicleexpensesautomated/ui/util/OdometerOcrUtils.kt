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
import kotlin.math.ceil
import kotlin.math.floor

data class OcrResult(
    val odometer: String?,
    val possibleOdometers: List<String>,
    val gallons: String?,
    val cost: String?,
    val debugText: String,
    val originalPhotoPath: String? = null,
    val croppedBitmap: Bitmap? = null,
    val openCvProcessedBitmap: Bitmap? = null
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

    private suspend fun runBothEngines(bitmap: Bitmap, stageName: String): String {
        val mlResult = try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val text: Text = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            text.text.ifEmpty { "(no text)" }
        } catch (e: Exception) {
            Log.e("OdometerOcr", "ML Kit failed on $stageName", e)
            "(ML Kit error)"
        }

        val tessResult = runTesseract(bitmap)

        return buildString {
            appendLine("--- $stageName ---")
            appendLine("ML Kit: $mlResult")
            appendLine("Tesseract: $tessResult")
            appendLine()
        }
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

    private fun runOpenCvPreprocessingStages(bitmap: Bitmap): Pair<String, Bitmap?> {
        return try {
            val mat = Mat()
            org.opencv.android.Utils.bitmapToMat(bitmap, mat)

            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

            val thresh = Mat()
            Imgproc.threshold(gray, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            val morph = Mat()
            Imgproc.morphologyEx(thresh, morph, Imgproc.MORPH_OPEN, kernel)
            Imgproc.morphologyEx(morph, morph, Imgproc.MORPH_CLOSE, kernel)

            val resultBmp = Bitmap.createBitmap(morph.cols(), morph.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(morph, resultBmp)

            mat.release()
            gray.release()
            thresh.release()
            morph.release()
            kernel.release()

            "OpenCV processed (morphology)" to resultBmp
        } catch (e: Exception) {
            Log.e("OdometerOcr", "OpenCV preprocessing failed", e)
            "(OpenCV error)" to null
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

        val debugText = buildString {
            appendLine("=== OCR DEBUG (multi-stage) ===\n")

            // Stage 1: Raw cropped
            append(runBothEngines(bitmap, "Raw Cropped"))

            // Stage 2: Grayscale
            val grayMat = Mat()
            org.opencv.android.Utils.bitmapToMat(bitmap, grayMat)
            Imgproc.cvtColor(grayMat, grayMat, Imgproc.COLOR_RGB2GRAY)
            val grayBmp = Bitmap.createBitmap(grayMat.cols(), grayMat.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(grayMat, grayBmp)
            append(runBothEngines(grayBmp, "Grayscale"))
            grayMat.release()

            // Stage 3: Binary threshold
            val threshMat = Mat()
            org.opencv.android.Utils.bitmapToMat(bitmap, threshMat)
            val gray2 = Mat()
            Imgproc.cvtColor(threshMat, gray2, Imgproc.COLOR_RGB2GRAY)
            Imgproc.threshold(gray2, threshMat, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            val threshBmp = Bitmap.createBitmap(threshMat.cols(), threshMat.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(threshMat, threshBmp)
            append(runBothEngines(threshBmp, "Binary Threshold"))
            gray2.release()
            threshMat.release()

            // Stage 4: Morphology (OpenCV cleaning)
            val (openCvResult, openCvProcessedBitmap) = runOpenCvPreprocessingStages(bitmap)
            append(openCvResult)
        }

        val rawText = debugText
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

        OcrResult(
            odometer = odometer,   // Default = Tesseract on raw cropped image
            possibleOdometers = possibleOdometers.distinct().sortedByDescending { it.length },
            gallons = gallons,
            cost = cost,
            debugText = debugText.toString(),
            originalPhotoPath = photoPath,
            croppedBitmap = croppedBitmap,
            openCvProcessedBitmap = openCvProcessedBitmap
        )
    }
}
