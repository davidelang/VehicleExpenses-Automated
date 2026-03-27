package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(
    val odometer: String?,
    val possibleOdometers: List<String>,
    val gallons: String?,
    val cost: String?
)

object OdometerOcrUtils {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractFromPhoto(photoPath: String, cropRect: RectF? = null): OcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext OcrResult(null, emptyList(), null, null)

        var bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, emptyList(), null, null)

        // Crop to user-defined odometer region if provided
        if (cropRect != null && cropRect.width() > 0 && cropRect.height() > 0) {
            val left = (cropRect.left * bitmap.width).toInt().coerceAtLeast(0)
            val top = (cropRect.top * bitmap.height).toInt().coerceAtLeast(0)
            val right = (cropRect.right * bitmap.width).toInt().coerceAtMost(bitmap.width)
            val bottom = (cropRect.bottom * bitmap.height).toInt().coerceAtMost(bitmap.height)

            val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            bitmap.recycle()
            bitmap = cropped
        }

        val image = InputImage.fromBitmap(bitmap, 0)

        val visionText: Text = try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { e -> continuation.resumeWithException(e) }
                    .addOnCanceledListener { continuation.cancel() }
            }
        } catch (e: Exception) {
            bitmap.recycle()
            return@withContext OcrResult(null, emptyList(), null, null)
        }

        val odoRegex = "\\b\\d{1,8}\\b".toRegex()
        val gallonsRegex = "\\b(\\d{1,2}\\.\\d{1,3})\\s*(?:gal|gallons)\\b".toRegex(RegexOption.IGNORE_CASE)
        val costRegex = "\\$?(\\d{1,3}\\.\\d{2})".toRegex()

        val possibleOdometers = mutableListOf<String>()
        var odometer: String? = null
        var gallons: String? = null
        var cost: String? = null

        visionText.textBlocks.forEach { block ->
            val blockText = block.text

            odoRegex.findAll(blockText).forEach { match ->
                val value = match.value
                possibleOdometers.add(value)
                if (odometer == null || value.length > odometer!!.length) {
                    odometer = value
                }
            }

            gallonsRegex.find(blockText)?.groupValues?.get(1)?.let { gallons = it }
            costRegex.find(blockText)?.groupValues?.get(1)?.let { cost = it }
        }

        bitmap.recycle()

        OcrResult(
            odometer = odometer,
            possibleOdometers = possibleOdometers.distinct().sortedByDescending { it.length },
            gallons = gallons,
            cost = cost
        )
    }
}
