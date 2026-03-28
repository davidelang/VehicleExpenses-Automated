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
        if (!file.exists()) {
            Log.w("OdometerOcr", "Photo file does not exist: $photoPath")
            return@withContext OcrResult(null, emptyList(), null, null)
        }

        var bitmap = BitmapFactory.decodeFile(photoPath) ?: run {
            Log.w("OdometerOcr", "Failed to decode bitmap from $photoPath")
            return@withContext OcrResult(null, emptyList(), null, null)
        }

        if (cropRect != null && cropRect.width() > 0 && cropRect.height() > 0) {
            val origW = bitmap.width
            val origH = bitmap.height

            val left = (cropRect.left * origW).toInt().coerceAtLeast(0)
            val top = (cropRect.top * origH).toInt().coerceAtLeast(0)
            val right = (cropRect.right * origW).toInt().coerceAtMost(origW)
            val bottom = (cropRect.bottom * origH).toInt().coerceAtMost(origH)

            var cropW = right - left
            var cropH = bottom - top

            if (cropW <= 0 || cropH <= 0) {
                Log.w("OdometerOcr", "Invalid crop after scaling: ${cropW}x${cropH}")
                bitmap.recycle()
                return@withContext OcrResult(null, emptyList(), null, null)
            }

            // More generous padding for real dashboard photos (25% + larger minimum)
            val padW = (cropW * 0.25f).toInt().coerceAtLeast(120)
            val padH = (cropH * 0.25f).toInt().coerceAtLeast(60)

            val paddedLeft = (left - padW).coerceAtLeast(0)
            val paddedTop = (top - padH).coerceAtLeast(0)
            val paddedRight = (right + padW).coerceAtMost(origW)
            val paddedBottom = (bottom + padH).coerceAtMost(origH)

            val finalW = paddedRight - paddedLeft
            val finalH = paddedBottom - paddedTop

            Log.d("OdometerOcr", "Crop applied - normalized: $cropRect | original: ${origW}x${origH} | raw crop: ${cropW}x${cropH} | final padded: ${finalW}x${finalH} (pad ${padW}x${padH})")

            if (finalW >= 100 && finalH >= 50) {
                val cropped = Bitmap.createBitmap(bitmap, paddedLeft, paddedTop, finalW, finalH)
                bitmap.recycle()
                bitmap = cropped
                Log.d("OdometerOcr", "Using cropped region for OCR")
            } else {
                Log.w("OdometerOcr", "Final region too small (${finalW}x${finalH}) — falling back to full image")
                // keep full bitmap
            }
        } else {
            Log.d("OdometerOcr", "No crop - using full image")
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
            Log.e("OdometerOcr", "ML Kit error", e)
            bitmap.recycle()
            return@withContext OcrResult(null, emptyList(), null, null)
        }

        val odoRegex = "\\b\\d{4,8}\\b".toRegex()
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
                if (odometer == null || value.length > (odometer?.length ?: 0)) {
                    odometer = value
                }
            }
            gallonsRegex.find(blockText)?.groupValues?.get(1)?.let { gallons = it }
            costRegex.find(blockText)?.groupValues?.get(1)?.let { cost = it }
        }

        bitmap.recycle()

        Log.d("OdometerOcr", "OCR complete - blocks: ${visionText.textBlocks.size}, odometer: $odometer, candidates: ${possibleOdometers.size}")

        OcrResult(
            odometer = odometer,
            possibleOdometers = possibleOdometers.distinct().sortedByDescending { it.length },
            gallons = gallons,
            cost = cost
        )
    }
}
