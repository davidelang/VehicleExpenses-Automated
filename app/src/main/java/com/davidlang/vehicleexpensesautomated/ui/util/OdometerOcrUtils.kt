package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
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
    val gallons: String?,
    val cost: String?
)

object OdometerOcrUtils {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractFromPhoto(photoPath: String): OcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext OcrResult(null, null, null)

        val bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, null, null)
        val image = InputImage.fromBitmap(bitmap, 0)

        val visionText = try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result -> continuation.resume(result.text) }
                    .addOnFailureListener { e -> continuation.resumeWithException(e) }
                    .addOnCanceledListener { continuation.cancel() }
            }
        } catch (e: Exception) {
            bitmap.recycle()
            return@withContext OcrResult(null, null, null)
        }

        val odoRegex = "\\b\\d{4,8}\\b".toRegex()
        val gallonsRegex = "\\b(\\d{1,2}\\.\\d{1,3})\\s*(?:gal|gallons)\\b".toRegex(RegexOption.IGNORE_CASE)
        val costRegex = "\\$?(\\d{1,3}\\.\\d{2})".toRegex()

        val odometer = odoRegex.findAll(visionText).maxByOrNull { it.value.length }?.value
        val gallons = gallonsRegex.find(visionText)?.groupValues?.get(1)
        val cost = costRegex.find(visionText)?.groupValues?.get(1)

        bitmap.recycle()
        OcrResult(odometer, gallons, cost)
    }
}
