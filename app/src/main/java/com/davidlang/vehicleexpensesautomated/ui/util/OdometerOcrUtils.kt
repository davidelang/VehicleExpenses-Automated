package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.BitmapFactory
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
    val possibleOdometers: List<String>, // new: all candidate matches for user confirmation
    val gallons: String?,
    val cost: String?
)

object OdometerOcrUtils {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractFromPhoto(photoPath: String): OcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext OcrResult(null, emptyList(), null, null)

        val bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, emptyList(), null, null)
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
                if (odometer == null || value.length > odometer!!.length) {
                    odometer = value
                }
            }

            gallonsRegex.find(blockText)?.groupValues?.get(1)?.let {
                gallons = it
            }

            costRegex.find(blockText)?.groupValues?.get(1)?.let {
                cost = it
            }
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
