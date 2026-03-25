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

object OdometerOcrUtils {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Automatically extracts the most likely odometer reading (longest run of 4-8 digits).
     * Runs instantly when a new dash photo is captured.
     */
    suspend fun extractOdometerFromPhoto(photoPath: String): String? = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext null

        val bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext null
        val image = InputImage.fromBitmap(bitmap, 0)

        try {
            val visionText = suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        continuation.resume(result.text)
                    }
                    .addOnFailureListener { e ->
                        continuation.resumeWithException(e)
                    }
                    .addOnCanceledListener {
                        continuation.cancel()
                    }
            }

            // Find longest sequence of digits (typical odometer format)
            val odometerRegex = "\\b\\d{4,8}\\b".toRegex()
            val matches = odometerRegex.findAll(visionText)
            val bestMatch = matches.maxByOrNull { it.value.length }?.value

            bestMatch
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }
}
