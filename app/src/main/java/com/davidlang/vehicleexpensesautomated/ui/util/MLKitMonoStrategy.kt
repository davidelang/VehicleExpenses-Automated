package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.Rect
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
        val targetW = 320; val targetH = 48

        // 1. Simple Extraction (Assuming Bitmap for legacy compatibility)
        val sourceBmp = if (masterBuffer is Bitmap) masterBuffer else throw IllegalArgumentException("Legacy MLKitMonoStrategy expects Bitmap")

        // 2. Force-scale to recognition dimensions
        val scaledBmp = Bitmap.createScaledBitmap(sourceBmp, targetW, targetH, true)

        // 3. NV21 Construction
        val frameSize = targetW * targetH
        val nv21 = ByteArray(frameSize * 3 / 2)
        val pixels = IntArray(frameSize)
        scaledBmp.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        for (i in 0 until frameSize) {
            nv21[i] = ((pixels[i] shr 16) and 0xFF).toByte()
        }
        for (i in frameSize until nv21.size) nv21[i] = 128.toByte()

        // 4. ML Kit Execution
        val img = InputImage.fromByteArray(nv21, targetW, targetH, 0, InputImage.IMAGE_FORMAT_NV21)
        val visionText = recognizer.process(img).await()

        // 5. Diagnostic Metadata
        val meta = JsonObject()
        meta.addProperty("inputW", masterW)
        meta.addProperty("inputH", masterH)
        meta.addProperty("rawBufferBase64", Base64.encodeToString(nv21, Base64.NO_WRAP))

        val result = OcrHarnessResult(
            htmlHeader = "<th>$displayName</th>",
            htmlCell = "<td>${visionText.text.take(10)}</td>",
            jsonSection = meta,
            odometerValue = visionText.text.filter { it.isDigit() }
        )

        report.add(displayName, result)
        return result
    }
}
