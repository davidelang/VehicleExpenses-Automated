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
        debug: Boolean,
        onStage: (suspend (String, Bitmap) -> Unit)? = null
    ): AutoFillResult {
        val t0 = System.currentTimeMillis()
        val jsonDebug = if (debug) JsonObject() else null
        
        try {
            // 0. Initial Snapshot
            onStage?.invoke("Original", masterBuffer.p.toBitmap())

            // 1. Deskew (Paddle C++) - Optimized Version
            val (optAngle, optTime) = OdometerOcrUtils.calculatePaddleAngleOptimized(masterBuffer.p)
            OdometerOcrUtils.rotate(masterBuffer, optAngle)
            
            jsonDebug?.apply {
                addProperty("deskew_angle", optAngle)
                addProperty("deskew_time_ms", optTime)
            }
            onStage?.invoke("Deskewed", masterBuffer.p.toBitmap())

            // 2. Landmark Discovery (ML Kit)
            val (ocrDisc, queryLandmarks) = performLandmarkDiscovery(masterBuffer.p, context)
            
            jsonDebug?.apply {
                addProperty("discovery_text", ocrDisc.debugText)
                addProperty("discovery_time_ms", ocrDisc.executionTimeMs)
                val landmarksJson = JsonArray()
                queryLandmarks.forEach { mark ->
                    val obj = JsonObject()
                    obj.addProperty("text", mark.text)
                    obj.addProperty("conf", mark.confidence)
                    val icrs = IcrsMath.pixelToIcrs(mark.boundingBox.centerX().toFloat(), mark.boundingBox.centerY().toFloat(), masterBuffer.width, masterBuffer.height)
                    obj.addProperty("cx", icrs.x)
                    obj.addProperty("cy", icrs.y)
                    landmarksJson.add(obj)
                }
                add("discovery_landmarks", landmarksJson)
            }

            // 3. Identification (Tier 1 Veto)
            val vetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarks, allVehicles, "ML Kit")
            val winnerId = vetoResults.entries.find { !it.value.isVetoed }?.key
            val winningVehicle = allVehicles.find { it.id == winnerId }

            if (winningVehicle == null) {
                val errorMsg = "Vehicle not identified"
                jsonDebug?.addProperty("error", errorMsg)
                return AutoFillResult(error = errorMsg, debugJson = jsonDebug?.toString())
            }

            jsonDebug?.apply {
                addProperty("matched_vehicle_id", winningVehicle.id)
                addProperty("matched_vehicle_name", winningVehicle.name)
            }

            // 4. Extraction (Set J)
            val referenceLandmarks = winningVehicle.landmarkTextBlocksJson?.let {
                OdometerOcrUtils.getFullLandmarksFromJson(it, "ML Kit", 4000, 3072) // Refs are 4k
            } ?: emptyList()

            val setJResult = runSetJPipeline(context, masterBuffer, winningVehicle, referenceLandmarks, debug, onStage)
            
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
     */
    suspend fun runSetJPipeline(
        context: Context,
        masterBuffer: BufferSet,
        vehicle: Vehicle,
        referenceLandmarks: List<TextBlock>,
        debug: Boolean = false,
        onStage: (suspend (String, Bitmap) -> Unit)? = null
    ): OcrHarnessResult {
        val t0 = System.currentTimeMillis()
        val jsonDebug = if (debug) JsonObject() else null
        val trialJsonArray = if (debug) JsonArray() else null

        val imgW = masterBuffer.width
        val imgH = masterBuffer.height

        // 1. Alignment (into bufferSetA.s, then flip)
        val (ocrDisc, queryLandmarks) = performLandmarkDiscovery(masterBuffer.p, context)
        val disambiguated = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarks, referenceLandmarks)
        
        // Audit Check: queW/queH must match current buffer dimensions (likely 2048x1536)
        val alignRes = ImageAlignmentUtils.anchorAlign(masterBuffer, referenceLandmarks, disambiguated, vehicle, 4000, 3072, imgW, imgH)
        
        jsonDebug?.apply {
            val alignMeta = JsonObject()
            alignRes.metadata.forEach { (k, v) -> alignMeta.addProperty(k, v) }
            add("alignment", alignMeta)
            addProperty("alignment_success", alignRes.success)
            addProperty("alignment_message", alignRes.message)
        }

        if (!alignRes.success) {
            return OcrHarnessResult("Set J failed", "Alignment failed", jsonDebug ?: JsonObject(), null, totalTimeMs = System.currentTimeMillis() - t0)
        }
        onStage?.invoke("Aligned", masterBuffer.p.toBitmap())

        // 2. Odometer Crop
        val l = vehicle.odometerCropLeft ?: 0f; val t = vehicle.odometerCropTop ?: 0f
        val r = vehicle.odometerCropRight ?: 1f; val b = vehicle.odometerCropBottom ?: 1f
        val p1 = IcrsMath.icrsToPixel(l, t, imgW, imgH); val p2 = IcrsMath.icrsToPixel(r, b, imgW, imgH)
        val roi = Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
        
        val odoBuffer = NativePaddleEngine.getOdoBuffer(vehicle)
        odoBuffer.p.clear()
        val interp = if (roi.width() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA else org.opencv.imgproc.Imgproc.INTER_LINEAR
        
        // Safety Clamping
        val safeRoi = Rect(
            roi.left.coerceIn(0, masterBuffer.width - 1),
            roi.top.coerceIn(0, masterBuffer.height - 1),
            roi.right.coerceIn(roi.left + 1, masterBuffer.width),
            roi.bottom.coerceIn(roi.top + 1, masterBuffer.height)
        )
        
        val odoMat = masterBuffer.p.mat.submat(org.opencv.core.Rect(safeRoi.left, safeRoi.top, safeRoi.width(), safeRoi.height()))
        org.opencv.imgproc.Imgproc.resize(odoMat, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
        odoMat.release()
        onStage?.invoke("Odometer Crop", odoBuffer.p.toBitmap())

        // 3. Bin Trials
        val stats = OdometerOcrUtils.getHistStats(odoBuffer.p.mat)
        val midpoints = OdometerOcrUtils.findValleyMidpoints(stats.rawBins)
        
        data class TrialData(val text: String, val sumProb: Float, val minProb: Float, val thresh: Double)
        val trialsList = mutableListOf<TrialData>()
        val paddleEngine = NativePaddleEngine(context, "Numeric")

        midpoints.forEach { binIdx ->
            val threshold = binIdx * 4.0
            odoBuffer.s.clear()
            org.opencv.imgproc.Imgproc.threshold(odoBuffer.p.mat, odoBuffer.s.mat, threshold, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)

            val hRes = NativeImageUtils.calculateHistogramWithThresholdH(odoBuffer.s.mat, listOf(Rect(0, 0, odoBuffer.width, odoBuffer.height)), 128f)
            val vSW = hRes?.second?.get(0)?.toFloat() ?: -1f
            val hSW = hRes?.second?.get(1)?.toFloat() ?: -1f

            if (vSW > 0 && hSW > 0) {
                NativeImageUtils.blackOutLargeAndSmallComponentsH(odoBuffer.s.mat, vSW, hSW, 0.20f * odoBuffer.s.mat.cols())
                NativeImageUtils.connectSegmentsH(odoBuffer.s.mat, vSW, hSW)
                NativeImageUtils.blackOutRollingDigitsH(odoBuffer.s.mat, vSW, hSW)
                val compRects = NativeImageUtils.findAllComponentsH(odoBuffer.s.mat, vSW, hSW)
                
                if (compRects.isNotEmpty()) {
                    val combined = Rect(compRects.minOf { it.left }, compRects.minOf { it.top }, compRects.maxOf { it.right }, compRects.maxOf { it.bottom })
                    val recBuffer = NativePaddleEngine.recBufferSet
                    recBuffer.p.clear()
                    val bRecMat = odoBuffer.s.mat.submat(org.opencv.core.Rect(combined.left, combined.top, combined.width(), combined.height()))
                    org.opencv.imgproc.Imgproc.resize(bRecMat, recBuffer.p.mat, recBuffer.p.mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                    
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
                        tObj.addProperty("vSW", vSW); tObj.addProperty("hSW", hSW)
                        tObj.addProperty("thumb", OcrUtils.takeSnapshot(odoBuffer.s.mat, combined, 320, 48).first)
                        trialJsonArray?.add(tObj)
                    }
                    bRecMat.release()
                }
            }
        }

        val highQual = trialsList.filter { it.minProb >= 0.40f }
        val winner = if (highQual.isNotEmpty()) highQual.maxByOrNull { it.sumProb } else trialsList.maxByOrNull { it.sumProb }
        val winnerOdo = winner?.text

        jsonDebug?.apply {
            addProperty("odometer", winnerOdo)
            addProperty("winner_threshold", winner?.thresh)
            addProperty("valleys_found", midpoints.size)
            add("trials", trialJsonArray)
            addProperty("odo_snapshot", OcrUtils.takeSnapshot(odoBuffer.p.mat, null, 640, 200).first)
        }
        
        return OcrHarnessResult(
            htmlHeader = "Set J Pipeline",
            htmlCell = "Result: $winnerOdo",
            jsonSection = jsonDebug ?: JsonObject().apply { addProperty("odometer", winnerOdo) },
            odometerValue = winnerOdo,
            totalTimeMs = System.currentTimeMillis() - t0
        )
    }

    private fun BufferSet.Slice.toBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        NativeImageUtils.syncMatToArgb(this.mat, bmp)
        return bmp
    }
}
