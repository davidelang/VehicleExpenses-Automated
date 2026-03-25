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

data class PumpOcrResult(
    val odometer: String? = null,
    val gallons: String? = null,
    val cost: String? = null
)

object PumpOcrUtils {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractFromPumpPhoto(photoPath: String): PumpOcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext PumpOcrResult()

        val bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext PumpOcrResult()
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
            return@withContext PumpOcrResult()
        }

        // Common pump photo patterns
        val odoRegex = "\\b\\d{4,8}\\b".toRegex()
        val gallonsRegex = "\\b(\\d{1,2}\\.\\d{1,3})\\s*(?:gal|gallons)\\b".toRegex(RegexOption.IGNORE_CASE)
        val costRegex = "\\$?(\\d{1,3}\\.\\d{2})".toRegex()

        val odometer = odoRegex.findAll(visionText).maxByOrNull { it.value.length }?.value
        val gallons = gallonsRegex.find(visionText)?.groupValues?.get(1)
        val cost = costRegex.findAll(visionText).maxByOrNull { it.value.length }?.value

        bitmap.recycle()
        PumpOcrResult(odometer, gallons, cost)
    }
}
