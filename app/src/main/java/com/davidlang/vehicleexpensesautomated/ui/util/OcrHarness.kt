package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

data class AutoFillResult(
    val vehicleId: Int? = null,
    val odometer: String? = null,
    val error: String? = null,
    val debugJson: String? = null
)

data class PumpCostVolResult(
    val cost: String? = null,
    val volume: String? = null,
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
        cameraRotationDegrees: Int = 0,
        onStage: (suspend (String, Bitmap) -> Unit)? = null
    ): AutoFillResult {
        val t0 = System.currentTimeMillis()
        val jsonDebug = if (debug) JsonObject() else null
        
        try {
            // 0. Initial Snapshot
            onStage?.invoke("Original", masterBuffer.p.toBitmap())

            // 1. Deskew (Paddle C++) - Optimized Version
            val (optAngle, optTime) = OdometerOcrUtils.calculatePaddleAngleOptimized(masterBuffer.p)
            
            val totalAngle = cameraRotationDegrees.toFloat() - optAngle
            val imgW = masterBuffer.width
            val imgH = masterBuffer.height
            val targetW = if (cameraRotationDegrees == 90 || cameraRotationDegrees == 270) imgH else imgW
            val targetH = if (cameraRotationDegrees == 90 || cameraRotationDegrees == 270) imgW else imgH
            
            val rotTime = OdometerOcrUtils.rotate(masterBuffer, totalAngle, targetW, targetH)
            
            jsonDebug?.apply {
                addProperty("camera_rotation", cameraRotationDegrees)
                addProperty("deskew_angle", optAngle)
                addProperty("total_rotation_angle", totalAngle)
                addProperty("deskew_time_ms", optTime + rotTime)
                addProperty("deskew_angle_calc_time_ms", optTime)
                addProperty("deskew_rotate_time_ms", rotTime)
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

            val setJResult = runSetJPipeline(context, masterBuffer, winningVehicle, referenceLandmarks, queryLandmarks, debug, onStage)
            
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

    /**
     * Quick Fill–only copy of Set G-- ("none, calculated", 2-pass vert list).
     * Caller must deskew/rotate workspace first; no second deskew here.
     */
    private suspend fun extractQuickFillSetGCostVol(
        workspace: BufferSet,
        paddleEngine: NativePaddleEngine,
        recBuffer: BufferSet,
        imgW: Int,
        imgH: Int
    ): CostVolClassifyResult {
        val na = CostVolClassifyResult("N/A", "N/A", RedBoxOcrCandidate("", "", ""), RedBoxOcrCandidate("", "", ""))

        val scales = listOf(224, 608, 1024)
        val pdHunksRawTotal = mutableListOf<PumpHunk>()
        val pdHunksExpTotal = mutableListOf<PumpHunk>()
        val pdHunksMaxTotal = mutableListOf<PumpHunk>()

        scales.forEach { scale ->
            val srcW = workspace.p.width
            val srcH = workspace.p.height
            val currentLongEdge = max(srcW, srcH)
            val scaleFactor = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
            val targetW = (srcW * scaleFactor).toInt()
            val targetH = (srcH * scaleFactor).toInt()
            val (outerId, innerId) = PumpCostVolUtils.prepareScale(workspace, scale)
            val paddleResults = PumpCostVolUtils.runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH, scale)
            pdHunksRawTotal.addAll(paddleResults[1])
            pdHunksExpTotal.addAll(paddleResults[2])
            pdHunksMaxTotal.addAll(paddleResults[3])
            workspace.c[innerId].release()
            workspace.c[outerId].release()
        }

        PumpCostVolUtils.doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
        PumpCostVolUtils.doCrossScaleRedboxFilter(pdHunksExpTotal, imgW, imgH)
        PumpCostVolUtils.doCrossScaleRedboxFilter(pdHunksMaxTotal, imgW, imgH)

        val redPixelList = PumpCostVolUtils.hunksToRects(pdHunksRawTotal).toMutableList()
        PumpCostVolUtils.pruneRectsToTopN(redPixelList, 6)
        pdHunksRawTotal.clear()
        pdHunksRawTotal.addAll(PumpCostVolUtils.rectsToHunks(redPixelList))
        if (pdHunksRawTotal.isEmpty()) return na

        val (customBlueGPre, _) = PumpCostVolUtils.createBlueAndOrangeHunksFromReds(
            pdHunksRawTotal, imgW, imgH, SET_G_MINUS_MINUS_VERT_FACTORS, SET_G_HORIZ_FACTOR
        )
        val customBluePixelG = PumpCostVolUtils.hunksToRects(customBlueGPre)
        if (customBluePixelG.isEmpty()) return na

        val ocrG = PumpCostVolUtils.ocrPumpRectsAsisAndDigits(
            workspace, paddleEngine, recBuffer, customBluePixelG, imgW, imgH
        )
        val gCands = PumpCostVolUtils.buildRedBoxCandidates(
            customBluePixelG, ocrG.asis, ocrG.digits, ocrG.asisProbs, ocrG.digitsProbs
        )
        return PumpCostVolUtils.classifyCostVolFromBoxOcr(gCands)
    }

    /**
     * Set G-- (2-pass) pump cost/volume pipeline for Quick Fill pump mode.
     */
    suspend fun runPumpCostVolPipeline(
        context: Context,
        masterBuffer: BufferSet,
        debug: Boolean,
        cameraRotationDegrees: Int = 0,
        onStage: (suspend (String, Bitmap) -> Unit)? = null
    ): PumpCostVolResult {
        val t0 = System.currentTimeMillis()
        try {
            onStage?.invoke("Original", masterBuffer.p.toBitmap())

            val (optAngle, _) = OdometerOcrUtils.calculatePaddleAngleOptimized(masterBuffer.p)
            val totalAngle = cameraRotationDegrees.toFloat() - optAngle
            val imgW = masterBuffer.width
            val imgH = masterBuffer.height
            val targetW = if (cameraRotationDegrees == 90 || cameraRotationDegrees == 270) imgH else imgW
            val targetH = if (cameraRotationDegrees == 90 || cameraRotationDegrees == 270) imgW else imgH
            OdometerOcrUtils.rotate(masterBuffer, totalAngle, targetW, targetH)
            onStage?.invoke("Deskewed", masterBuffer.p.toBitmap())

            val paddleEngine = NativePaddleEngine(context, "Numeric")
            val recBuffer = NativePaddleEngine.recBufferSet
            val cv = extractQuickFillSetGCostVol(
                masterBuffer, paddleEngine, recBuffer, masterBuffer.width, masterBuffer.height
            )

            val cost = cv.cost.takeIf { it != "N/A" && it.isNotBlank() }
            val volume = cv.vol.takeIf { it != "N/A" && it.isNotBlank() }

            if (cost == null && volume == null) {
                return PumpCostVolResult(error = "Could not read pump display")
            }

            val debugJson = if (debug) {
                JsonObject().apply {
                    addProperty("cost", cv.cost)
                    addProperty("volume", cv.vol)
                    addProperty("pipeline_time_ms", System.currentTimeMillis() - t0)
                }.toString()
            } else null

            return PumpCostVolResult(cost = cost, volume = volume, debugJson = debugJson)
        } catch (e: Exception) {
            Log.e("OcrHarness", "Pump cost/vol pipeline failed", e)
            return PumpCostVolResult(error = "Pump OCR failed: ${e.message ?: "Unknown error"}")
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
        queryLandmarks: List<TextBlock>,
        debug: Boolean = false,
        onStage: (suspend (String, Bitmap) -> Unit)? = null
    ): OcrHarnessResult {
        val t0 = System.currentTimeMillis()
        val jsonDebug = if (debug) JsonObject() else null
        val trialJsonArray = if (debug) JsonArray() else null

        val imgW = masterBuffer.width
        val imgH = masterBuffer.height

        // 1. Alignment (into bufferSetA.s, then flip)
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
        val icrsRect = if (vehicle.odometerCropLeft != null && vehicle.odometerCropTop != null && vehicle.odometerCropRight != null && vehicle.odometerCropBottom != null) {
            RectF(vehicle.odometerCropLeft, vehicle.odometerCropTop, vehicle.odometerCropRight, vehicle.odometerCropBottom)
        } else {
            IcrsMath.fullImageIcrsRect(imgW, imgH)
        }
        val p1 = IcrsMath.icrsToPixel(icrsRect.left, icrsRect.top, imgW, imgH); val p2 = IcrsMath.icrsToPixel(icrsRect.right, icrsRect.bottom, imgW, imgH)
        val roi = Rect(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
        
        val odoBuffer = NativePaddleEngine.getOdoBuffer(context, vehicle)
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
            odoBuffer.flip() // now .p = binary, .s = original grayscale

            val detSc = kotlin.math.min(512f / odoBuffer.p.mat.cols(), 128f / odoBuffer.p.mat.rows())
            val fw = (odoBuffer.p.mat.cols() * detSc).toInt().coerceAtMost(512)
            val fh = (odoBuffer.p.mat.rows() * detSc).toInt().coerceAtMost(128)
            
            val experimentDetSet512x128 = NativePaddleEngine.detBufferSet
            experimentDetSet512x128.p.clear()
            val dCrId = experimentDetSet512x128.createCrop(0, 0, fw, fh)
            org.opencv.imgproc.Imgproc.resize(odoBuffer.p.mat, experimentDetSet512x128.c[dCrId].mat, experimentDetSet512x128.c[dCrId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
            val detRes = paddleEngine.detect(experimentDetSet512x128.p, copyHeatmap = false)
            val tFullB = if (detRes != null) {
                val invScale = 1.0f / detSc
                detRes.nativeBoxes.map { box ->
                    val points = box.points
                    val minX = Math.floor((minOf(minOf(points[0], points[2]), minOf(points[4], points[6])) - 8.0) * invScale.toDouble()).toInt()
                    val minY = Math.floor((minOf(minOf(points[1], points[3]), minOf(points[5], points[7])) - 8.0) * invScale.toDouble()).toInt()
                    val maxX = Math.ceil((maxOf(maxOf(points[0], points[2]), maxOf(points[4], points[6])) + 8.0) * invScale.toDouble()).toInt()
                    val maxY = Math.ceil((maxOf(maxOf(points[1], points[3]), maxOf(points[5], points[7])) + 8.0) * invScale.toDouble()).toInt()
                    val bounds = android.graphics.Rect(minX, minY, maxX, maxY)
                    val scaledPoints = FloatArray(8)
                    for (i in 0 until 8) {
                        scaledPoints[i] = points[i] * invScale
                    }
                    val angle = 0f
                    TextBlock("", bounds, angle, confidence = box.confidence)
                }
            } else emptyList<TextBlock>()
            experimentDetSet512x128.c[dCrId].release()
            
            val tRawB = tFullB.filter { b1 ->
                tFullB.none { b2 -> b1 !== b2 && b2.boundingBox.contains(b1.boundingBox.left + 5, b1.boundingBox.top + 5, b1.boundingBox.right - 5, b1.boundingBox.bottom - 5) }
            }

            if (tRawB.isNotEmpty()) {
                val rb = tRawB.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: tRawB.first()
                val redBoxCropId = odoBuffer.createCrop(rb.boundingBox.left, rb.boundingBox.top, rb.boundingBox.width(), rb.boundingBox.height())
                val cropRect = android.graphics.Rect(0, 0, odoBuffer.crop[redBoxCropId].width, odoBuffer.crop[redBoxCropId].height)
                val hRes = NativeImageUtils.calculateHistogramWithThresholdH(odoBuffer.crop[redBoxCropId].mat, listOf(cropRect), 128f)
                val vSW = hRes?.second?.get(0)?.toFloat() ?: -1f
                val hSW = hRes?.second?.get(1)?.toFloat() ?: -1f
                odoBuffer.crop[redBoxCropId].release()

                if (vSW > 0 && hSW > 0) {
                    NativeImageUtils.blackOutLargeAndSmallComponentsH(odoBuffer.p.mat, vSW, hSW, 0.20f * odoBuffer.p.mat.cols())
                    NativeImageUtils.blackOutRollingDigitsH(odoBuffer.p.mat, vSW, hSW)
                    val compRects = NativeImageUtils.findAllComponentsH(odoBuffer.p.mat, vSW, hSW)
                    
                    if (compRects.isNotEmpty()) {
                        val combined = Rect(compRects.minOf { it.left }, compRects.minOf { it.top }, compRects.maxOf { it.right }, compRects.maxOf { it.bottom })
                        val recBuffer = NativePaddleEngine.recBufferSet
                        recBuffer.p.clear()
                        
                        val sL = combined.left.coerceIn(0, odoBuffer.p.mat.cols() - 1)
                        val sT = combined.top.coerceIn(0, odoBuffer.p.mat.rows() - 1)
                        val sR = combined.right.coerceIn(sL + 1, odoBuffer.p.mat.cols())
                        val sB = combined.bottom.coerceIn(sT + 1, odoBuffer.p.mat.rows())
                        
                        if (sR > sL && sB > sT) {
                            val bRecMat = odoBuffer.p.mat.submat(org.opencv.core.Rect(sL, sT, sR - sL, sB - sT))
                            val rSc = kotlin.math.min(312f / bRecMat.cols(), 40f / bRecMat.rows())
                            val ew = ((bRecMat.cols() * rSc + 1).toInt() / 2) * 2
                            val eh = ((bRecMat.rows() * rSc + 1).toInt() / 2) * 2
                            val rCrId = recBuffer.createCrop(4, 4, ew, eh)
                            org.opencv.imgproc.Imgproc.resize(bRecMat, recBuffer.c[rCrId].mat, recBuffer.c[rCrId].mat.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                            
                            val ocrR = paddleEngine.recognizeNumeric(recBuffer.p)
                            val probsStr = ocrR.metadata["ocr_probs"] ?: ""
                            val probs = mutableListOf<Float>()
                            Regex("\\((0\\.\\d+|1\\.0+)\\)").findAll(probsStr).forEach { 
                                probs.add(it.groupValues[1].toFloatOrNull() ?: 0f) 
                            }
                            
                            val trial = TrialData(ocrR.debugText, probs.sum(), probs.minOrNull() ?: 0f, threshold)
                            trialsList.add(trial)

                            if (debug) {
                                val tObj = JsonObject()
                                tObj.addProperty("threshold", threshold)
                                tObj.addProperty("text", trial.text)
                                tObj.addProperty("sum_prob", trial.sumProb)
                                tObj.addProperty("min_prob", trial.minProb)
                                tObj.addProperty("vSW", vSW); tObj.addProperty("hSW", hSW)
                                tObj.addProperty("thumb", OcrUtils.takeSnapshot(odoBuffer.p.mat, combined, 320, 48).first)
                                trialJsonArray?.add(tObj)
                            }
                            recBuffer.c[rCrId].release()
                            bRecMat.release()
                        }
                    }
                }
            }
            odoBuffer.flip() // flip back to restore grayscale
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
