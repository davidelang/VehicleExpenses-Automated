package com.davidlang.vehicleexpensesautomated.ocr

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.davidlang.vehicleexpensesautomated.ui.util.OcrFunctionalPipeline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/emulator functional gate for production paddle OCR path.
 *
 * Fixture: androidTest assets `ocr_functional/skewed_hello.png` (+ expected.json),
 * canonical copy under `third_party/paddle/tests/ocr_functional/fixtures/`.
 *
 * Stages (VE code): angle → deskew → det → crop box → rec V3.
 */
@RunWith(AndroidJUnit4::class)
class PaddleOcrFunctionalTest {

    @Test
    fun skewedHello_angleDeskewCropOcr() = runBlocking {
        val instr = InstrumentationRegistry.getInstrumentation()
        // Fixtures live in androidTest assets → instrumentation.context, not app assets.
        val testCtx = instr.context
        val appCtx = instr.targetContext
        val img = OcrFunctionalPipeline.materializeAsset(
            assetContext = testCtx,
            assetPath = "ocr_functional/skewed_hello.png",
            writeContext = appCtx,
        )
        val expJson = testCtx.assets.open("ocr_functional/expected.json").bufferedReader().readText()
        val exp = OcrFunctionalPipeline.loadExpectationsFromJson(expJson)

        val result = OcrFunctionalPipeline.runOnFile(appCtx, img.absolutePath)
        val verdict = OcrFunctionalPipeline.evaluate(result, exp)

        val summary = buildString {
            appendLine("angle=${result.angleDeg} boxes=${result.boxCount} ocr='${result.ocrText}' norm='${result.ocrTextNormalized}'")
            appendLine("timings=${result.timings}")
            appendLine("primary=${result.primaryBox}")
            appendLine("debug=${result.debug}")
            if (verdict.failures.isNotEmpty()) {
                appendLine("FAILURES:")
                verdict.failures.forEach { appendLine(" - $it") }
            }
        }
        Log.i(TAG, summary)
        // Also print to instrumentation stdout for adb logcat / host scripts
        println("PaddleOcrFunctionalTest: $summary")

        assertTrue(summary, verdict.pass)
    }

    companion object {
        private const val TAG = "PaddleOcrFunctionalTest"
    }
}
