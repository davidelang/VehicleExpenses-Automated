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
    val fullText: String
)

object OdometerOcrUtils {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Returns both the best odometer reading AND the full vision text so the import screen can parse gallons/price.
     */
    suspend fun extractOdometerFromPhoto(photoPath: String): OcrResult = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext OcrResult(null, "")

        val bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext OcrResult(null, "")
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
            return@withContext OcrResult(null, "")
        }

        val odometerRegex = "\\b\\d{4,8}\\b".toRegex()
        val bestOdo = odometerRegex.findAll(visionText).maxByOrNull { it.value.length }?.value

        bitmap.recycle()
        OcrResult(bestOdo, visionText)
    }
}
