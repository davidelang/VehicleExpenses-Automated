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
        onStage: (suspend (String, Bitmap) -> Unit)? = null,
        /** When set, skip Tier-1 vehicle ID and use this vehicle for Set J (batch pending answers). */
        forcedVehicleId: Int? = null,
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

            // 3. Identification (Tier 1 Veto) — or forced vehicle for batch pending assign
            val winningVehicle = if (forcedVehicleId != null) {
                allVehicles.find { it.id == forcedVehicleId && !it.deleted }
            } else {
                val vetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarks, allVehicles, "ML Kit")
                val winnerId = vetoResults.entries.find { !it.value.isVetoed }?.key
                allVehicles.find { it.id == winnerId }
            }

            if (winningVehicle == null) {
                val errorMsg = if (forcedVehicleId != null) {
                    "Forced vehicle $forcedVehicleId not found"
                } else {
                    "Vehicle not identified"
                }
                jsonDebug?.addProperty("error", errorMsg)
                return AutoFillResult(error = errorMsg, debugJson = jsonDebug?.toString())
            }

            jsonDebug?.apply {
                addProperty("matched_vehicle_id", winningVehicle.id)
                addProperty("matched_vehicle_name", winningVehicle.name)
                if (forcedVehicleId != null) addProperty("forced_vehicle", true)
            }

            // 4. Extraction (Set J) — ref geometry must match real reference dash (probe or 4080×3072)
            val (refW, refH) = if (!winningVehicle.referenceDashPhotoUrl.isNullOrEmpty()) {
                NativePaddleEngine.getReferenceDimensions(context, winningVehicle.referenceDashPhotoUrl)
            } else {
                Pair(NativePaddleEngine.DEFAULT_REF_DASH_W, NativePaddleEngine.DEFAULT_REF_DASH_H)
            }
            jsonDebug?.apply {
                addProperty("ref_dash_w", refW)
                addProperty("ref_dash_h", refH)
            }
            val referenceLandmarks = winningVehicle.landmarkTextBlocksJson?.let {
                OdometerOcrUtils.getFullLandmarksFromJson(it, "ML Kit", refW, refH)
            } ?: emptyList()

            val setJResult = runSetJPipeline(
                context, masterBuffer, winningVehicle, referenceLandmarks, queryLandmarks,
                debug, onStage, refW, refH,
            )
            
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
     * Quick Fill–only cost/volume extraction using G-- vert list (shared reduce k=4).
     * Caller must deskew/rotate workspace first; no second deskew here.
     */
    private suspend fun extractQuickFillSetGCostVol(
        context: Context,
        workspace: BufferSet,
        paddleEngine: NativePaddleEngine,
        recBuffer: BufferSet,
        imgW: Int,
        imgH: Int,
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
        PumpCostVolUtils.pruneRectsToTopN(redPixelList, PumpOcrSettings.maxRedBoxes(context))
        pdHunksRawTotal.clear()
        pdHunksRawTotal.addAll(PumpCostVolUtils.rectsToHunks(redPixelList))
        if (pdHunksRawTotal.isEmpty()) return na

        // Quick Fill uses G-- (shared reduce k=4), not full G k=8
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
        return PumpCostVolUtils.classifyCostVolFromBoxOcr(context, gCands)
    }

    /**
     * Set G pump cost/volume pipeline for Quick Fill pump mode.
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
                context,
                masterBuffer,
                paddleEngine,
                recBuffer,
                masterBuffer.width,
                masterBuffer.height,
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

    /**
     * Batch-import pump cost/volume via **Set I** (D+E+G hybrid).
     * Does not change Quick Fill [runPumpCostVolPipeline] (G--).
     * [masterBuffer] must already hold the full photo in primary (after ingest).
     */
    suspend fun runPumpCostVolPipelineSetI(
        context: Context,
        masterBuffer: BufferSet,
        debug: Boolean = false,
    ): PumpCostVolResult {
        val t0 = System.currentTimeMillis()
        try {
            val paddleEngine = NativePaddleEngine(context, "Numeric")
            val recBuffer = NativePaddleEngine.recBufferSet
            val imgW = masterBuffer.width
            val imgH = masterBuffer.height
            val cv = PumpCostVolUtils.runSetICostVolExtraction(
                masterBuffer,
                paddleEngine,
                recBuffer,
                imgW,
                imgH,
            )
            val cost = cv.cost.takeIf { it != "N/A" && it.isNotBlank() }
            val volume = cv.vol.takeIf { it != "N/A" && it.isNotBlank() }
            if (cost == null && volume == null) {
                return PumpCostVolResult(error = "Could not read pump display (Set I)")
            }
            val debugJson = if (debug) {
                JsonObject().apply {
                    addProperty("cost", cv.cost)
                    addProperty("volume", cv.vol)
                    addProperty("pipeline", "SetI")
                    addProperty("pipeline_time_ms", System.currentTimeMillis() - t0)
                }.toString()
            } else null
            return PumpCostVolResult(cost = cost, volume = volume, debugJson = debugJson)
        } catch (e: Exception) {
            Log.e("OcrHarness", "Set I pump pipeline failed", e)
            return PumpCostVolResult(error = "Set I pump OCR failed: ${e.message ?: "Unknown error"}")
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
     * Production Set J odometer extraction (batch import + Quick Fill).
     *
     * **Independent copy** of alignment experiment Set J (experiment may change / compile-out).
     * Geometry inputs must match experiment at Jul 15 baseline:
     * - [refW]/[refH] = probed reference dash size (fallback 4080×3072, not 4000)
     * - Odo window: **ICRS Float** `createCrop` on aligned master (not pre-converted pixel Int crop)
     * - Raw: detect → expand on **unfiltered** det boxes (no nestFilter on Raw)
     * - Bin-Trials: nestFilter + full-mat hist + connectSegmentsH + pickBestOdometer (4–7 digits)
     */
    suspend fun runSetJPipeline(
        context: Context,
        masterBuffer: BufferSet,
        vehicle: Vehicle,
        referenceLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        debug: Boolean = false,
        onStage: (suspend (String, Bitmap) -> Unit)? = null,
        refW: Int = NativePaddleEngine.DEFAULT_REF_DASH_W,
        refH: Int = NativePaddleEngine.DEFAULT_REF_DASH_H,
    ): OcrHarnessResult {
        val t0 = System.currentTimeMillis()
        val jsonDebug = if (debug) JsonObject() else null
        val trialJsonArray = if (debug) JsonArray() else null
        val steps = mutableListOf<OcrStepResult>()

        val imgW = masterBuffer.width
        val imgH = masterBuffer.height

        // 1. Alignment — refW/refH must be real reference dash dims (probed), not hardcoded 4000
        val disambiguated = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarks, referenceLandmarks)
        val alignRes = ImageAlignmentUtils.anchorAlign(
            masterBuffer, referenceLandmarks, disambiguated, vehicle, refW, refH, imgW, imgH,
        )
        jsonDebug?.apply {
            val alignMeta = JsonObject()
            alignRes.metadata.forEach { (k, v) -> alignMeta.addProperty(k, v) }
            add("alignment", alignMeta)
            addProperty("alignment_success", alignRes.success)
            addProperty("alignment_message", alignRes.message)
            addProperty("ref_w", refW)
            addProperty("ref_h", refH)
        }
        if (!alignRes.success) {
            return OcrHarnessResult(
                "Set J failed", "Alignment failed", jsonDebug ?: JsonObject(), null,
                totalTimeMs = System.currentTimeMillis() - t0,
            )
        }
        onStage?.invoke("Aligned", masterBuffer.p.toBitmap())

        // 2. Odo crop: ICRS Float createCrop (experiment parity) — BufferSet maps ICRS→pixels at refresh
        val icrsRect = if (
            vehicle.odometerCropLeft != null && vehicle.odometerCropTop != null &&
            vehicle.odometerCropRight != null && vehicle.odometerCropBottom != null
        ) {
            RectF(
                vehicle.odometerCropLeft, vehicle.odometerCropTop,
                vehicle.odometerCropRight, vehicle.odometerCropBottom,
            )
        } else {
            IcrsMath.fullImageIcrsRect(imgW, imgH)
        }
        masterBuffer.p.createCrop(
            icrsRect.left, icrsRect.top, icrsRect.width(), icrsRect.height(), id = vehicle.id,
        )

        val odoBuffer = NativePaddleEngine.getOdoBuffer(context, vehicle)
        val detBuffer = NativePaddleEngine.detBufferSet
        val recBuffer = NativePaddleEngine.recBufferSet
        val paddleEngine = NativePaddleEngine(context, "Numeric")

        fun repopulateOdoFromMasterCrop() {
            odoBuffer.p.clear()
            val src = masterBuffer.c[vehicle.id].mat
            val interp =
                if (src.cols() > odoBuffer.p.mat.cols()) org.opencv.imgproc.Imgproc.INTER_AREA
                else org.opencv.imgproc.Imgproc.INTER_LINEAR
            org.opencv.imgproc.Imgproc.resize(src, odoBuffer.p.mat, odoBuffer.p.mat.size(), 0.0, 0.0, interp)
        }

        suspend fun detectBoxesOnOdo(): List<TextBlock> {
            val detSc = kotlin.math.min(512f / odoBuffer.p.mat.cols(), 128f / odoBuffer.p.mat.rows())
            val fw = (odoBuffer.p.mat.cols() * detSc).toInt().coerceAtMost(512)
            val fh = (odoBuffer.p.mat.rows() * detSc).toInt().coerceAtMost(128)
            detBuffer.p.clear()
            val dCrId = detBuffer.createCrop(0, 0, fw, fh)
            org.opencv.imgproc.Imgproc.resize(
                odoBuffer.p.mat, detBuffer.c[dCrId].mat, detBuffer.c[dCrId].mat.size(),
                0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA,
            )
            val detRes = paddleEngine.detect(detBuffer.p, copyHeatmap = false)
            val boxes = if (detRes != null) {
                val invScale = 1.0f / detSc
                detRes.nativeBoxes.map { box ->
                    val points = box.points
                    val minX = Math.floor(
                        (minOf(minOf(points[0], points[2]), minOf(points[4], points[6])) - 8.0) * invScale.toDouble(),
                    ).toInt()
                    val minY = Math.floor(
                        (minOf(minOf(points[1], points[3]), minOf(points[5], points[7])) - 8.0) * invScale.toDouble(),
                    ).toInt()
                    val maxX = Math.ceil(
                        (maxOf(maxOf(points[0], points[2]), maxOf(points[4], points[6])) + 8.0) * invScale.toDouble(),
                    ).toInt()
                    val maxY = Math.ceil(
                        (maxOf(maxOf(points[1], points[3]), maxOf(points[5], points[7])) + 8.0) * invScale.toDouble(),
                    ).toInt()
                    TextBlock("", Rect(minX, minY, maxX, maxY), 0f, confidence = box.confidence)
                }
            } else emptyList()
            detBuffer.c[dCrId].release()
            return boxes
        }

        fun nestFilter(full: List<TextBlock>): List<TextBlock> =
            full.filter { b1 ->
                full.none { b2 ->
                    b1 !== b2 && b2.boundingBox.contains(
                        b1.boundingBox.left + 5, b1.boundingBox.top + 5,
                        b1.boundingBox.right - 5, b1.boundingBox.bottom - 5,
                    )
                }
            }

        suspend fun ocrUnionRects(rects: List<Rect>): Pair<String, Pair<Float, Float>> {
            if (rects.isEmpty()) return "" to (0f to 0f)
            val odoB = StringBuilder()
            val probs = mutableListOf<Float>()
            var confAcc = 0f
            var confLen = 0
            for (tBox in rects) {
                val sL = tBox.left.coerceIn(0, odoBuffer.p.mat.cols() - 1)
                val sT = tBox.top.coerceIn(0, odoBuffer.p.mat.rows() - 1)
                val sR = tBox.right.coerceIn(sL + 1, odoBuffer.p.mat.cols())
                val sB = tBox.bottom.coerceIn(sT + 1, odoBuffer.p.mat.rows())
                if (sR <= sL || sB <= sT) continue
                val bRecMat = odoBuffer.p.mat.submat(
                    org.opencv.core.Rect(sL, sT, sR - sL, sB - sT),
                )
                recBuffer.p.clear()
                val rSc = kotlin.math.min(312f / bRecMat.cols(), 40f / bRecMat.rows())
                val ew = ((bRecMat.cols() * rSc + 1).toInt() / 2) * 2
                val eh = ((bRecMat.rows() * rSc + 1).toInt() / 2) * 2
                val rCrId = recBuffer.createCrop(4, 4, ew, eh)
                org.opencv.imgproc.Imgproc.resize(
                    bRecMat, recBuffer.c[rCrId].mat, recBuffer.c[rCrId].mat.size(),
                    0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA,
                )
                val ocrR = paddleEngine.recognizeNumeric(recBuffer.p)
                if (ocrR.debugText.isNotBlank()) {
                    odoB.append(ocrR.debugText).append(" ")
                    ocrR.metadata["ocr_probs"]?.let { probsStr ->
                        Regex("\\((0\\.\\d+|1\\.0+)\\)").findAll(probsStr).forEach {
                            probs.add(it.groupValues[1].toFloatOrNull() ?: 0f)
                        }
                    }
                    confAcc += (ocrR.textBlocks.firstOrNull()?.confidence ?: 0f) * ocrR.debugText.length
                    confLen += ocrR.debugText.length
                }
                recBuffer.c[rCrId].release()
                bRecMat.release()
            }
            val text = odoB.toString().trim()
            val sumP = probs.sum()
            val minP = probs.minOrNull() ?: 0f
            return text to (sumP to minP)
        }

        // --- Stage Raw (experiment: raw det boxes, no nestFilter before expand) ---
        repopulateOdoFromMasterCrop()
        onStage?.invoke("Odometer Crop", odoBuffer.p.toBitmap())
        val rawFull = detectBoxesOnOdo()
        val rawValley = rawFull.map {
            NativeImageUtils.expandByCharacterAwareDiagnostic(odoBuffer.p.mat, it.boundingBox)
        }
        val rawFrags = rawValley.map { it.first }
        val rawCons = OdometerOcrUtils.clusterRects(rawFrags).sortedBy { it.left }
        val (rawText, _) = ocrUnionRects(rawCons)
        steps.add(
            OcrStepResult(
                stageName = "Raw",
                thumbB64 = "",
                text = rawText,
            ),
        )
        jsonDebug?.addProperty("raw_text", rawText)

        // --- Stage Bin-Trials (experiment set_j branch only) ---
        data class TrialData(
            val text: String,
            val sumProb: Float,
            val minProb: Float,
            val thresh: Double,
        )
        val trialsList = mutableListOf<TrialData>()
        val stats = OdometerOcrUtils.getHistStats(
            // hist on grayscale crop before trials; repopulate fresh each trial
            run {
                repopulateOdoFromMasterCrop()
                odoBuffer.p.mat
            },
        )
        val midpoints = OdometerOcrUtils.findValleyMidpoints(stats.rawBins)
        val thresholdFactor = 128.0f

        midpoints.forEach { binIdx ->
            val threshold = binIdx * 4.0
            // Fresh grayscale from master crop, then binarize (experiment trial start)
            repopulateOdoFromMasterCrop()
            odoBuffer.s.clear()
            org.opencv.imgproc.Imgproc.threshold(
                odoBuffer.p.mat, odoBuffer.s.mat, threshold, 255.0,
                org.opencv.imgproc.Imgproc.THRESH_BINARY,
            )
            odoBuffer.flip() // .p = binary, .s = grayscale

            val tFullB = detectBoxesOnOdo()
            val tRawB = nestFilter(tFullB)
            if (tRawB.isEmpty()) {
                odoBuffer.flip()
                return@forEach
            }

            // Full-mat stroke hist per red (experiment), pick largest red
            var rb = tRawB.maxByOrNull {
                it.boundingBox.width() * it.boundingBox.height()
            } ?: tRawB.first()
            val hResRb = NativeImageUtils.calculateHistogramWithThresholdH(
                odoBuffer.p.mat, listOf(rb.boundingBox), thresholdFactor,
            )
            val vSW = hResRb?.second?.get(0)?.toFloat() ?: -1f
            val hSW = hResRb?.second?.get(1)?.toFloat() ?: -1f
            if (vSW <= 0f || hSW <= 0f) {
                odoBuffer.flip()
                return@forEach
            }

            // set_j: NO filterComponents re-detect path — connectSegmentsH path
            NativeImageUtils.blackOutLargeAndSmallComponentsH(
                odoBuffer.p.mat, vSW, hSW, 0.20f * odoBuffer.p.mat.cols(),
            )
            val connectCount = NativeImageUtils.connectSegmentsH(odoBuffer.p.mat, vSW, hSW)
            Log.d("OcrHarness", "SetJ trial T=$threshold connectSegmentsH count=$connectCount")
            NativeImageUtils.blackOutRollingDigitsH(odoBuffer.p.mat, vSW, hSW)
            val compRects = NativeImageUtils.findAllComponentsH(odoBuffer.p.mat, vSW, hSW)
            // set_j: union of component rects into one orange (experiment tCons for set_j)
            val tCons = if (compRects.isNotEmpty()) {
                listOf(
                    Rect(
                        compRects.minOf { it.left },
                        compRects.minOf { it.top },
                        compRects.maxOf { it.right },
                        compRects.maxOf { it.bottom },
                    ),
                )
            } else emptyList()

            val (tText, sumMin) = ocrUnionRects(tCons)
            if (tText.isNotBlank()) {
                trialsList.add(TrialData(tText, sumMin.first, sumMin.second, threshold))
                if (debug) {
                    trialJsonArray?.add(
                        JsonObject().apply {
                            addProperty("threshold", threshold)
                            addProperty("text", tText)
                            addProperty("sum_prob", sumMin.first)
                            addProperty("min_prob", sumMin.second)
                            addProperty("vSW", vSW)
                            addProperty("hSW", hSW)
                            addProperty("connect_count", connectCount)
                        },
                    )
                }
            }
            odoBuffer.flip() // restore grayscale
        }

        val highQual = trialsList.filter { it.minProb >= 0.40f }
        val binWinner =
            if (highQual.isNotEmpty()) highQual.maxByOrNull { it.sumProb }
            else trialsList.maxByOrNull { it.sumProb }
        val binText = binWinner?.text ?: ""
        steps.add(
            OcrStepResult(
                stageName = "Bin-Trials",
                thumbB64 = "",
                text = binText,
            ),
        )

        // Final filter: 4–7 pure digits only (experiment pickBestOdometer)
        val winnerOdo = OdometerOcrUtils.pickBestOdometer(steps)

        jsonDebug?.apply {
            addProperty("odometer", winnerOdo)
            addProperty("raw_text", rawText)
            addProperty("bin_trials_best", binText)
            addProperty("winner_threshold", binWinner?.thresh)
            addProperty("valleys_found", midpoints.size)
            addProperty("pipeline", "SetJ_experiment_copy")
            add("trials", trialJsonArray)
        }

        return OcrHarnessResult(
            htmlHeader = "Set J Pipeline",
            htmlCell = "Result: $winnerOdo",
            jsonSection = jsonDebug ?: JsonObject().apply { addProperty("odometer", winnerOdo) },
            odometerValue = winnerOdo,
            totalTimeMs = System.currentTimeMillis() - t0,
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
