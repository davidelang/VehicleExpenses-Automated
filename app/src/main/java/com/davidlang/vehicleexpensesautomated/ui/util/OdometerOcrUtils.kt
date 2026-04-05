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
data class TextBlock(
    val text: String,
    val boundingBox: android.graphics.Rect
)
data class OcrResult(
    val odometer: String?,
    val possibleOdometers: List<String>,
    val gallons: String?,
    val cost: String?,
    val debugText: String,
    val originalPhotoPath: String? = null,
    val croppedBitmap: Bitmap? = null,
    val openCvProcessedBitmap: Bitmap? = null,
    val textBlocks: List<TextBlock> = emptyList()
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
    // Lighter pipeline for cropped OCR (no heavy morphology)
    private fun runLightCropOcr(bitmap: Bitmap): String {
        val grayMat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, grayMat)
        Imgproc.cvtColor(grayMat, grayMat, Imgproc.COLOR_RGB2GRAY)
        val thresh = Mat()
        Imgproc.adaptiveThreshold(grayMat, thresh, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2.0)
        val resultBmp = Bitmap.createBitmap(thresh.cols(), thresh.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(thresh, resultBmp)
        grayMat.release()
        thresh.release()
        return runTesseract(resultBmp)
    }
    suspend fun extractFromPhoto(photoPath: String, cropRect: RectF? = null): OcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext OcrResult(null, emptyList(), null, null, "Photo file not found", photoPath, null, null, emptyList())
        var bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, emptyList(), null, null, "Failed to decode bitmap", photoPath, null, null, emptyList())
        var croppedBitmap: Bitmap? = null
        if (cropRect != null) {
            val origW = bitmap.width
            val origH = bitmap.height
            val left = floor(cropRect.left * origW).toInt().coerceAtLeast(0)
            val top = floor(cropRect.top * origH).toInt().coerceAtLeast(0)
            val right = ceil(cropRect.right * origW).toInt().coerceAtMost(origW)
            val bottom = ceil(cropRect.bottom * origH).toInt().coerceAtMost(origH)
            if (right > left && bottom > top) {
                croppedBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                bitmap.recycle()
                bitmap = croppedBitmap
            }
        }
        var openCvProcessedBitmap: Bitmap? = null
        var textBlocks = mutableListOf<TextBlock>()
        val debugText = buildString {
            appendLine("=== OCR DEBUG (multi-stage) ===\n")
            append(runBothEngines(bitmap, "Raw Cropped"))
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val visionText: Text = suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
                visionText.textBlocks.forEach { block ->
                    block.boundingBox?.let { box ->
                        textBlocks.add(TextBlock(block.text, box))
                    }
                }
            } catch (e: Exception) {
                Log.e("OdometerOcr", "ML Kit text blocks failed", e)
            }
            val grayMat = Mat()
            org.opencv.android.Utils.bitmapToMat(bitmap, grayMat)
            Imgproc.cvtColor(grayMat, grayMat, Imgproc.COLOR_RGB2GRAY)
            val grayBmp = Bitmap.createBitmap(grayMat.cols(), grayMat.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(grayMat, grayBmp)
            append(runBothEngines(grayBmp, "Grayscale"))
            grayMat.release()
            // Light crop OCR (adaptive threshold only)
            val lightOcr = runLightCropOcr(bitmap)
            appendLine("--- Light Crop OCR (adaptive threshold) ---")
            appendLine("Tesseract: $lightOcr")
            appendLine()
        }
        val rawTesseract = runTesseract(bitmap)
        val cleanRaw = rawTesseract.replace("I", "1").replace("l", "1").replace("O", "0").replace("S", "5").replace("B", "8").trim()
        val possible = mutableListOf<String>()
        if (cleanRaw.matches(Regex("\\d{4,6}"))) possible.add(cleanRaw)
        OcrResult(
            odometer = cleanRaw.takeIf { it.length in 4..6 },
            possibleOdometers = possible,
            gallons = null,
            cost = null,
            debugText = debugText.toString(),
            originalPhotoPath = photoPath,
            croppedBitmap = croppedBitmap,
            openCvProcessedBitmap = openCvProcessedBitmap,
            textBlocks = textBlocks
        )
    }
    suspend fun extractFullImageOcr(photoPath: String, deduplicateWith: List<TextBlock>? = null): OcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext OcrResult(null, emptyList(), null, null, "Photo file not found", photoPath, null, null, emptyList())
        val bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, emptyList(), null, null, "Failed to decode bitmap", photoPath, null, null, emptyList())
        val debugText = buildString {
            appendLine("=== FULL IMAGE OCR (light) ===\n")
            append(runBothEngines(bitmap, "Full Aligned Image"))
        }
        val rawTesseract = runTesseract(bitmap)
        val cleanRaw = rawTesseract.replace("I", "1").replace("l", "1").replace("O", "0").replace("S", "5").replace("B", "8").trim()
        val possible = mutableListOf<String>()
        if (cleanRaw.matches(Regex("\\d{4,6}"))) possible.add(cleanRaw)
        val textBlocks = mutableListOf<TextBlock>()
        val cleaningTexts = deduplicateWith?.map { it.text }?.toSet() ?: emptySet()
        if (cleanRaw.isNotEmpty() && !cleaningTexts.contains(cleanRaw)) {
            textBlocks.add(TextBlock(cleanRaw, android.graphics.Rect(0, 0, bitmap.width, bitmap.height)))
        }
        OcrResult(
            odometer = cleanRaw.takeIf { it.length in 4..6 },
            possibleOdometers = possible,
            gallons = null,
            cost = null,
            debugText = debugText.toString(),
            originalPhotoPath = photoPath,
            croppedBitmap = null,
            openCvProcessedBitmap = null,
            textBlocks = textBlocks
        )
    }
}
