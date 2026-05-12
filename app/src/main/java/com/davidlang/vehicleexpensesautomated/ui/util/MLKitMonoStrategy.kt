package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import com.google.gson.JsonObject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import android.util.Base64

class MLKitMonoStrategy(
    override val displayName: String
) : OcrEngineStrategy {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun execute(
        masterBuffer: Any,
        masterW: Int,
        masterH: Int,
        report: ReportCollector
    ): OcrHarnessResult {
        // Minimum implementation to satisfy interface without using deprecated models
        return OcrHarnessResult("", "", JsonObject(), null)
    }
}
