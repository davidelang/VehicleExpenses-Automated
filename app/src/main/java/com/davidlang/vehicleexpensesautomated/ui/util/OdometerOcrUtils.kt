package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
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

    fun annotateImageWithBoxes(original: Bitmap, blocks: List<TextBlock>): Bitmap {
        val annotated = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.RED
        }
        for (block in blocks) {
            val rect = block.boundingBox
            canvas.drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), paint)
        }
        return annotated
    }

    /** Shared helper — identical to manualCropOdometer so blue box and OCR crop are pixel-identical */
    private fun cropBitmap(bitmap: Bitmap, cropRect: RectF): Bitmap? {
        val origW = bitmap.width
        val origH = bitmap.height
        val left = (cropRect.left * origW).toInt().coerceAtLeast(0)
        val top = (cropRect.top * origH).toInt().coerceAtLeast(0)
        val right = (cropRect.right * origW).toInt().coerceAtMost(origW)
        val bottom = (cropRect.bottom * origH).toInt().coerceAtMost(origH)
        if (right <= left || bottom <= top) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    fun extractFromPhotoForDebug(bitmap: Bitmap): Pair<String, List<TextBlock>> = runRawOcr(bitmap)

    suspend fun extractFromPhoto(photoPath: String, cropRect: RectF? = null): OcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext OcrResult(null, emptyList(), null, null, "Photo file not found", photoPath, null, null, emptyList())
        var bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, emptyList(), null, null, "Failed to decode bitmap", photoPath, null, null, emptyList())
        var croppedBitmap: Bitmap? = null
        if (cropRect != null) {
            croppedBitmap = cropBitmap(bitmap, cropRect)
            if (croppedBitmap != null) {
                bitmap.recycle()
                bitmap = croppedBitmap
            }
        }

        val (rawTesseract, textBlocks) = runRawOcr(bitmap)

        val debugText = buildString {
            appendLine("=== OCR DEBUG (ML Kit reverted) ===\n")
            appendLine("Tesseract: $rawTesseract")
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
            openCvProcessedBitmap = null,
            textBlocks = textBlocks
        )
    }

    suspend fun extractFullImageOcr(photoPath: String, deduplicateWith: List<TextBlock>? = null): OcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext OcrResult(null, emptyList(), null, null, "Photo file not found", photoPath, null, null, emptyList())
        val bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, emptyList(), null, null, "Failed to decode bitmap", photoPath, null, null, emptyList())
        val (rawTesseract, _) = runRawOcr(bitmap)
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
