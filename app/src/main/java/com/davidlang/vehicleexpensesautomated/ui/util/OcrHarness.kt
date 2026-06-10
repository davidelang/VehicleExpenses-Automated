package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AutoFillResult(
    val vehicleId: Int? = null,
    val odometer: String? = null,
    val error: String? = null,
    val debugJson: String? = null
)

/**
 * Orchestrates OCR pipelines for automated data entry.
 */
object OcrHarness {

    /**
     * Unified entry point for Quick Fill.
     * Executes: Deskew -> Landmark Discovery -> Vehicle Identification -> Odometer Extraction.
     */
    suspend fun runAutoFillPipeline(
        context: Context,
        masterBuffer: BufferSet,
        allVehicles: List<Vehicle>,
        debug: Boolean
    ): AutoFillResult {
        val t0 = System.currentTimeMillis()
        val jsonDebug = if (debug) JsonObject() else null
        
        try {
            // 1. Deskew (Paddle C++)
            val deskewRes = OdometerOcrUtils.calculateAverageTextAngle(masterBuffer.p)
            OdometerOcrUtils.rotate(masterBuffer, deskewRes.paddleCppAngle)
            
            jsonDebug?.apply {
                addProperty("deskew_angle", deskewRes.paddleCppAngle)
                addProperty("deskew_time_ms", System.currentTimeMillis() - t0)
            }

            // 2. Landmark Discovery (ML Kit)
            val (ocrDisc, queryLandmarks) = performLandmarkDiscovery(masterBuffer.p, context)
            
            jsonDebug?.apply {
                addProperty("discovery_text", ocrDisc.debugText)
                addProperty("discovery_time_ms", ocrDisc.executionTimeMs)
            }

            // 3. Identification (Tier 1 Veto)
            val vetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarks, allVehicles, "ML Kit")
            val winnerId = vetoResults.entries.find { !it.value.isVetoed }?.key
            val winningVehicle = allVehicles.find { it.id == winnerId }

            if (winningVehicle == null) {
                return AutoFillResult(error = "Vehicle not identified", debugJson = jsonDebug?.toString())
            }

            jsonDebug?.apply {
                addProperty("matched_vehicle_id", winningVehicle.id)
                addProperty("matched_vehicle_name", winningVehicle.name)
            }

            // 4. Extraction (Set J)
            val referenceLandmarks = winningVehicle.landmarkTextBlocksJson?.let {
                OdometerOcrUtils.getFullLandmarksFromJson(it, "ML Kit", masterBuffer.width, masterBuffer.height)
            } ?: emptyList()

            val setJResult = runSetJPipeline(context, masterBuffer, winningVehicle, referenceLandmarks, debug)
            
            jsonDebug?.apply {
                add("set_j", setJResult.jsonSection)
                addProperty("total_pipeline_time_ms", System.currentTimeMillis() - t0)
            }

            return AutoFillResult(
                vehicleId = winningVehicle.id,
                odometer = setJResult.odometerValue,
                debugJson = jsonDebug?.toString()
            )
            
        } catch (e: Exception) {
            Log.e("OcrHarness", "AutoFill Pipeline failed", e)
            jsonDebug?.addProperty("exception", e.message)
            return AutoFillResult(error = "Pipeline Error: ${e.message}", debugJson = jsonDebug?.toString())
        }
    }

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
        val trialJsonArray = if (debug) JsonArray() else null

        val imgW = masterBuffer.width
        val imgH = masterBuffer.height

        // 1. Alignment
        val (ocrDisc, queryLandmarks) = performLandmarkDiscovery(masterBuffer.p, context)
        val disambiguated = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarks, referenceLandmarks)
        val alignRes = ImageAlignmentUtils.anchorAlign(masterBuffer, referenceLandmarks, disambiguated, vehicle, 4000, 3072, imgW, imgH)
        
        if (!alignRes.success) {
            return OcrHarnessResult("Set J failed", "Alignment failed", JsonObject(), null, totalTimeMs = System.currentTimeMillis() - t0)
        }

        // 2. Odometer Crop
        val l = vehicle.odometerCropLeft ?: 0f
        val t = vehicle.odometerCropTop ?: 0f
        val r = vehicle.odometerCropRight ?: 1f
        val b = vehicle.odometerCropBottom ?: 1f
        
        val p1 = IcrsMath.icrsToPixel(l, t, imgW, imgH)
        val p2 = IcrsMath.icrsToPixel(r, b, imgW, imgH)
        val roi = Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
        
        val odoBuffer = NativePaddleEngine.bufferSetB
        odoBuffer.p.clear()
        val interp = if (roi.width() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
        org.opencv.imgproc.Imgproc.resize(masterBuffer.p.mat.submat(org.opencv.core.Rect(roi.left, roi.top, roi.width(), roi.height())), odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)

        // 3. Bin Trials (No Contrast Stretch for Set J)
        val stats = OdometerOcrUtils.getHistStats(odoBuffer.p.mat)
        val midpoints = OdometerOcrUtils.findValleyMidpoints(stats.rawBins)
        
        data class TrialData(val text: String, val sumProb: Float, val minProb: Float, val thresh: Double)
        val trialsList = mutableListOf<TrialData>()
        val paddleEngine = NativePaddleEngine(context, "Numeric")

        midpoints.forEach { binIdx ->
            val threshold = binIdx * 4.0
            
            // Re-binarize from grayscale
            odoBuffer.s.clear()
            org.opencv.imgproc.Imgproc.threshold(odoBuffer.p.mat, odoBuffer.s.mat, threshold, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
            odoBuffer.flip()

            // Set J Pre-processing (CC Speedup)
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
                    
                    val recBuffer = NativePaddleEngine.bufferSetA
                    recBuffer.p.clear()
                    val bRecMat = odoBuffer.p.mat.submat(org.opencv.core.Rect(combined.left, combined.top, combined.width(), combined.height()))
                    val rSc = kotlin.math.min(312f / bRecMat.cols(), 40f / bRecMat.rows())
                    val ew = ((bRecMat.cols() * rSc + 1).toInt() / 2) * 2
                    val eh = ((bRecMat.rows() * rSc + 1).toInt() / 2) * 2
                    val rCrId = recBuffer.createCrop(4, 4, ew, eh)
                    org.opencv.imgproc.Imgproc.resize(bRecMat, recBuffer.c[rCrId].mat, recBuffer.c[rCrId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                    
                    val ocrR = paddleEngine.recognizeNumeric(recBuffer.p)
                    val probsStr = ocrR.metadata["ocr_probs"] ?: ""
                    val probs = mutableListOf<Float>(); Regex("\\((0\\.\\d+|1\\.0+)\\)").findAll(probsStr).forEach { probs.add(it.groupValues[1].toFloatOrNull() ?: 0f) }
                    
                    val trial = TrialData(ocrR.debugText, probs.sum(), probs.minOrNull() ?: 0f, threshold)
                    trialsList.add(trial)

                    if (debug) {
                        val tObj = JsonObject()
                        tObj.addProperty("threshold", threshold)
                        tObj.addProperty("text", trial.text)
                        tObj.addProperty("sum_prob", trial.sumProb)
                        tObj.addProperty("min_prob", trial.minProb)
                        tObj.addProperty("thumb", OcrUtils.takeSnapshot(odoBuffer.p.mat, null, 320, 48).first)
                        trialJsonArray?.add(tObj)
                    }
                    
                    recBuffer.c[rCrId].release()
                    bRecMat.release()
                }
            }
        }

        // Probabilistic Winner Selection
        val highQual = trialsList.filter { it.minProb >= 0.40f }
        val winner = if (highQual.isNotEmpty()) highQual.maxByOrNull { it.sumProb } else trialsList.maxByOrNull { it.sumProb }
        val winnerOdo = winner?.text

        jsonDebug?.apply {
            addProperty("odometer", winnerOdo)
            addProperty("winner_threshold", winner?.thresh)
            add("trials", trialJsonArray)
        }
        
        return OcrHarnessResult(
            htmlHeader = "Set J Pipeline",
            htmlCell = "Result: $winnerOdo",
            jsonSection = jsonDebug ?: JsonObject().apply { addProperty("odometer", winnerOdo) },
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
