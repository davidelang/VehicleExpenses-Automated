package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object OdometerOcrUtils {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Runs ML Kit OCR on a photo file path and returns the most likely odometer reading
     * (looks for the longest sequence of digits, typically 5-7 digits for mileage).
     */
    suspend fun extractOdometerFromPhoto(photoPath: String): String? = withContext(Dispatchers.IO) {
        val file = File(photoPath)
        if (!file.exists()) return@withContext null

        val bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext null
        val image = InputImage.fromBitmap(bitmap, 0)

        try {
            val result = recognizer.process(image).await()
            val text = result.text

            // Find the best candidate: longest run of digits (odometer style)
            val odometerRegex = "\\b\\d{4,8}\\b".toRegex()
            val matches = odometerRegex.findAll(text)
            val bestMatch = matches.maxByOrNull { it.value.length }?.value

            bitmap.recycle()
            bestMatch
        } catch (e: Exception) {
            null
        }
    }
}
