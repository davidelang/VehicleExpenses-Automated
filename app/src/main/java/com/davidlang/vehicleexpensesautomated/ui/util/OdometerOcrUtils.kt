package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import com.googlecode.tesseract.android.TessBaseAPI

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
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("OdometerOcr", "OpenCV initialization failed!")
        } else {
            Log.i("OdometerOcr", "OpenCV initialized successfully")
        }
    }

    // Pure raw Tesseract (no preprocessing, alphanumeric whitelist only) — used for every debug step
    fun runRawOcr(bitmap: Bitmap): Pair<String, List<TextBlock>> {
        val tess = TessBaseAPI()
        val blocks = mutableListOf<TextBlock>()
        try {
            val tessDataPath = "/data/user/0/com.davidlang.vehicleexpensesautomated/files"
            if (!tess.init(tessDataPath, "eng")) {
                tess.clear()
                return "(Tesseract init failed)" to emptyList()
            }

            tess.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")

            tess.setImage(bitmap)

            val fullText = tess.getUTF8Text()?.trim() ?: "(no text)"

            val iterator = tess.getResultIterator()
            if (iterator != null) {
                do {
                    val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD) ?: continue
                    val rect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    if (rect != null && text.isNotBlank()) {
                        blocks.add(TextBlock(text.trim(), rect))
                    }
                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            }

            tess.clear()
            return fullText to blocks
        } catch (e: Exception) {
            Log.e("OdometerOcr", "Raw Tesseract failed", e)
            return "(Raw Tesseract error: ${e.message})" to emptyList()
        } finally {
            tess.clear()
        }
    }

    // Strong preprocessing + Tesseract (used only by the normal cleaning flow)
    private fun runTesseractWithBlocks(bitmap: Bitmap): Pair<String, List<TextBlock>> {
        val tess = TessBaseAPI()
        val blocks = mutableListOf<TextBlock>()
        try {
            val tessDataPath = "/data/user/0/com.davidlang.vehicleexpensesautomated/files"
            if (!tess.init(tessDataPath, "eng")) {
                tess.clear()
                return "(Tesseract init failed)" to emptyList()
            }

            val mat = Mat()
            org.opencv.android.Utils.bitmapToMat(bitmap, mat)
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

            val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            val enhanced = Mat()
            clahe.apply(gray, enhanced)

            val thresh = Mat()
            Imgproc.adaptiveThreshold(enhanced, thresh, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2.0)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
            Imgproc.dilate(thresh, thresh, kernel)

            Core.bitwise_not(thresh, thresh)

            val preprocessedBmp = Bitmap.createBitmap(thresh.cols(), thresh.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(thresh, preprocessedBmp)

            mat.release()
            gray.release()
            enhanced.release()
            thresh.release()
            kernel.release()

            tess.setImage(preprocessedBmp)

            val fullText = tess.getUTF8Text()?.trim() ?: "(no text)"

            val iterator = tess.getResultIterator()
            if (iterator != null) {
                do {
                    val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD) ?: continue
                    val rect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    if (rect != null && text.isNotBlank()) {
                        blocks.add(TextBlock(text.trim(), rect))
                    }
                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            }

            tess.clear()
            preprocessedBmp.recycle()
            return fullText to blocks
        } catch (e: Exception) {
            Log.e("OdometerOcr", "Tesseract failed", e)
            return "(Tesseract error: ${e.message})" to emptyList()
        } finally {
            tess.clear()
        }
    }

    fun extractFromPhotoForDebug(bitmap: Bitmap): Pair<String, List<TextBlock>> = runRawOcr(bitmap)

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
        return runTesseractWithBlocks(resultBmp).first
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
        val (rawTesseract, textBlocks) = runTesseractWithBlocks(bitmap)

        val debugText = buildString {
            appendLine("=== OCR DEBUG (multi-stage) ===\n")
            appendLine("--- Raw Cropped ---")
            appendLine("Tesseract: $rawTesseract")
            appendLine()

            val grayMat = Mat()
            org.opencv.android.Utils.bitmapToMat(bitmap, grayMat)
            Imgproc.cvtColor(grayMat, grayMat, Imgproc.COLOR_RGB2GRAY)
            val grayBmp = Bitmap.createBitmap(grayMat.cols(), grayMat.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(grayMat, grayBmp)
            val (grayText, _) = runTesseractWithBlocks(grayBmp)
            appendLine("--- Grayscale ---")
            appendLine("Tesseract: $grayText")
            appendLine()
            grayMat.release()

            val lightOcr = runLightCropOcr(bitmap)
            appendLine("--- Light Crop OCR (adaptive threshold) ---")
            appendLine("Tesseract: $lightOcr")
            appendLine()
        }

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
        val (rawTesseract, _) = runTesseractWithBlocks(bitmap)
        val debugText = buildString {
            appendLine("=== FULL IMAGE OCR (light) ===\n")
            appendLine("Tesseract: $rawTesseract")
        }
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
