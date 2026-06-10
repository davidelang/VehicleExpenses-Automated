package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates ML Kit discovery pass.
 */
object OcrHarness {

    suspend fun runDiscovery(input: Any, context: Context): OcrResult {
        val rawResult = MlKitEngine().recognize(input)

        val cleanedBlocks = rawResult.textBlocks.map { block ->
            block.copy(text = OdometerOcrUtils.cleanLandmarkString(block.text))
        }.filter { it.text.length > 1 }

        val sanitizedResult = rawResult.copy(
            textBlocks = cleanedBlocks,
            debugText = cleanedBlocks.joinToString(" ") { it.text }
        )

        return sanitizedResult
    }

    private suspend fun performLandmarkDiscovery(slice: BufferSet.Slice, context: Context): Pair<OcrResult, List<TextBlock>> {
        val ocr = MlKitEngine().recognize(slice)
        val cleaned = ocr.textBlocks.map { it.copy(text = OdometerOcrUtils.cleanLandmarkString(it.text)) }.filter { it.text.length > 2 }
        return Pair(ocr.copy(textBlocks = cleaned), cleaned)
    }

    /**
     * Production implementation of the "Set J" pipeline.
     * Optimized for speed but supports extensive telemetry if [debug] is enabled.
     */
    suspend fun runSetJPipeline(
        context: Context,
        masterBuffer: BufferSet,
        vehicle: Vehicle,
        referenceLandmarks: List<TextBlock>,
        debug: Boolean = false
    ): OcrHarnessResult {
        val t0 = System.currentTimeMillis()
        val jsonDebug = if (debug) JsonObject() else null
        val extraImages = mutableMapOf<String, String>()
        val steps = mutableListOf<OcrStepResult>()

        val imgW = masterBuffer.width
        val imgH = masterBuffer.height

        // 1. Discovery Pass (on Primary)
        val (ocrDisc, queryLandmarks) = performLandmarkDiscovery(masterBuffer.p, context)
        
        // 2. Alignment
        val disambiguated = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarks, referenceLandmarks)
        val alignRes = ImageAlignmentUtils.anchorAlign(masterBuffer, referenceLandmarks, disambiguated, vehicle, 4000, 3072, imgW, imgH)
        
        if (!alignRes.success) {
            return OcrHarnessResult("Set J failed", "Alignment failed", JsonObject(), null, totalTimeMs = System.currentTimeMillis() - t0)
        }

        // 3. Odometer Crop
        val l = vehicle.odometerCropLeft ?: 0f
        val t = vehicle.odometerCropTop ?: 0f
        val r = vehicle.odometerCropRight ?: 1f
        val b = vehicle.odometerCropBottom ?: 1f
        
        val p1 = IcrsMath.icrsToPixel(l, t, imgW, imgH)
        val p2 = IcrsMath.icrsToPixel(r, b, imgW, imgH)
        val roi = Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
        
        // Use bufferSetB from NativePaddleEngine as the work area for crops
        val odoBuffer = NativePaddleEngine.bufferSetB
        odoBuffer.p.clear()
        val interp = if (roi.width() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
        org.opencv.imgproc.Imgproc.resize(masterBuffer.p.mat.submat(org.opencv.core.Rect(roi.left, roi.top, roi.width(), roi.height())), odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)

        // 4. Contrast Stretch
        OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.40f)

        // 5. Bin Trials
        val stats = OdometerOcrUtils.getHistStats(odoBuffer.p.mat)
        val midpoints = OdometerOcrUtils.findValleyMidpoints(stats.rawBins)
        
        val trialResults = mutableListOf<OcrStepResult>()
        val paddleEngine = NativePaddleEngine(context, "Numeric") // Or anchoredEngineNumeric

        midpoints.forEachIndexed { vIdx, binIdx ->
            val threshold = binIdx * 4.0
            
            // Re-pull grayscale from master to avoid cumulative degradation
            odoBuffer.p.clear()
            org.opencv.imgproc.Imgproc.resize(masterBuffer.p.mat.submat(org.opencv.core.Rect(roi.left, roi.top, roi.width(), roi.height())), odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
            OdometerOcrUtils.applyContrastStretch(odoBuffer.p.mat, 0.40f)

            // Binarize into scratch, then flip
            odoBuffer.s.clear()
            org.opencv.imgproc.Imgproc.threshold(odoBuffer.p.mat, odoBuffer.s.mat, threshold, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
            odoBuffer.flip()

            // Set J Pre-processing
            val hRes = NativeImageUtils.calculateHistogramWithThresholdH(odoBuffer.p.mat, listOf(Rect(0, 0, odoBuffer.width, odoBuffer.height)), 128f)
            val vSW = hRes?.second?.get(0)?.toFloat() ?: -1f
            val hSW = hRes?.second?.get(1)?.toFloat() ?: -1f

            if (vSW > 0 && hSW > 0) {
                NativeImageUtils.blackOutLargeAndSmallComponentsH(odoBuffer.p.mat, vSW, hSW, 0.20f * odoBuffer.p.mat.cols())
                NativeImageUtils.connectSegmentsH(odoBuffer.p.mat, vSW, hSW)
                NativeImageUtils.blackOutRollingDigitsH(odoBuffer.p.mat, vSW, hSW)
                val compRects = NativeImageUtils.findAllComponentsH(odoBuffer.p.mat, vSW, hSW)
                
                if (compRects.isNotEmpty()) {
                    val combined = Rect(compRects.minOf { it.left }, compRects.minOf { it.top }, compRects.maxOf { it.right }, compRects.maxOf { it.bottom })
                    
                    // Recognition
                    val recBuffer = NativePaddleEngine.bufferSetA // Reusing bufferA.p for rec crop
                    recBuffer.p.clear()
                    val bRecMat = odoBuffer.p.mat.submat(org.opencv.core.Rect(combined.left, combined.top, combined.width(), combined.height()))
                    val rSc = kotlin.math.min(312f / bRecMat.cols(), 40f / bRecMat.rows())
                    val ew = ((bRecMat.cols() * rSc + 1).toInt() / 2) * 2
                    val eh = ((bRecMat.rows() * rSc + 1).toInt() / 2) * 2
                    val rCrId = recBuffer.createCrop(4, 4, ew, eh)
                    org.opencv.imgproc.Imgproc.resize(bRecMat, recBuffer.c[rCrId].mat, recBuffer.c[rCrId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                    
                    val ocrR = paddleEngine.recognizeNumeric(recBuffer.p)
                    
                    val step = OcrStepResult(
                        stageName = "Trial $vIdx (th=$threshold)",
                        thumbB64 = if (debug) OcrUtils.takeSnapshot(odoBuffer.p.mat, null, 320, 48).first else "",
                        text = ocrR.debugText,
                        metadata = mapOf("confidence" to (ocrR.textBlocks.firstOrNull()?.confidence ?: 0f).toString())
                    )
                    trialResults.add(step)
                    recBuffer.c[rCrId].release()
                    bRecMat.release()
                }
            }
        }

        val winnerOdo = OdometerOcrUtils.pickBestOdometer(trialResults)
        
        return OcrHarnessResult(
            htmlHeader = "Set J Pipeline",
            htmlCell = "Result: $winnerOdo",
            jsonSection = JsonObject().apply { addProperty("odometer", winnerOdo) },
            odometerValue = winnerOdo,
            totalTimeMs = System.currentTimeMillis() - t0
        )
    }

}

data class OcrHarnessResult(
    val htmlHeader: String,
    val htmlCell: String,
    val jsonSection: JsonObject,
    val odometerValue: String?,
    val thumbB64: String? = null,
    val totalTimeMs: Long = 0,
    val tSnapshotMs: Long = 0,
    val extraImages: Map<String, String> = emptyMap()
)

data class HarnessRunDef(
    val strategy: OcrEngineStrategy,
    val buffer: Any,
    val width: Int,
    val height: Int
)

interface OcrEngineStrategy {
    val displayName: String
    suspend fun execute(
        masterBuffer: Any,
        masterW: Int,
        masterH: Int,
        report: ReportCollector
    ): OcrHarnessResult
}

interface ReportCollector {
    fun add(engineName: String, result: OcrHarnessResult)
}
