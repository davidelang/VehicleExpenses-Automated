package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AutoFillResult(
    val vehicleId: Int? = null,
    /**
     * Tracking odometer digits for [com.davidlang.vehicleexpensesautomated.data.model.FuelEntry.odometer]
     * (includes rollover encoding). Null if OCR failed.
     */
    val odometer: String? = null,
    /** Updated [com.davidlang.vehicleexpensesautomated.data.model.Vehicle.odometerRolloverCount] if wrap detected. */
    val newRolloverCount: Int? = null,
    val error: String? = null,
    val debugJson: String? = null,
    val detJson: String? = null,
    val recJson: String? = null,
    /** `veto.json` body when [runAutoFillPipeline] debug is on. Null when debug is off. */
    val vetoJson: String? = null,
    /** Not-vetoed ids when identification failed. Null on every other result. */
    val survivorIds: List<Int>? = null,
)

data class PumpCostVolResult(
    val cost: String? = null,
    val volume: String? = null,
    val error: String? = null,
    val debugJson: String? = null,
    val classifyJson: String? = null,
    /** `scales.json` body when pump debug is on. One raw box per row. Null when debug is off. */
    val scalesJson: String? = null,
)

/** Fill detect: empty original, discovery JPEG, blue quads (n×8), blue AABBs (n×4), raw boxes. */
class QfRotGmmDetectOut(
    @JvmField val originalJpeg: ByteArray,
    @JvmField val discoveryJpeg: ByteArray,
    @JvmField val bluePts: FloatArray,
    @JvmField val blueAabb: IntArray,
    @JvmField val rawScale: IntArray,
    @JvmField val rawCorners: FloatArray,
    @JvmField val rawScore: FloatArray,
)

/** Fill recognize: one string row per blue. No crop ids. */
class QfRotGmmRecOut(
    @JvmField val asis: Array<String>,
    @JvmField val asisProbs: Array<String>,
    @JvmField val digits: Array<String>,
    @JvmField val digitsProbs: Array<String>,
)

/**
 * Live / production odometer red→orange expand.
 * Alignment experiment: Set J = [CHAR_AWARE], Set V = [VALLEY].
 */
enum class OdoExpandKind {
    CHAR_AWARE,
    VALLEY,
}

/**
 * Orchestrates OCR pipelines for automated data entry.
 */
object OcrHarness {

    /** Fill max red boxes. Not read from PumpOcrSettings. */
    private const val FILL_MAX_RED_BOXES = 8

    /** Debug dump of the veto call that already ran. Does not re-run or re-decide. */
    private fun tier1VetoDebugJson(
        engine: String,
        forcedVehicleId: Int?,
        winnerId: Int?,
        allVehicles: List<Vehicle>,
        vetoResults: Map<Int, VetoResult>?,
    ): JsonObject {
        val obj = JsonObject()
        obj.addProperty("engine", engine)
        if (forcedVehicleId != null) obj.addProperty("forced_vehicle_id", forcedVehicleId)
        else obj.add("forced_vehicle_id", JsonNull.INSTANCE)
        if (winnerId != null) obj.addProperty("winner_id", winnerId)
        else obj.add("winner_id", JsonNull.INSTANCE)
        obj.addProperty("vehicle_count", allVehicles.size)
        if (forcedVehicleId != null) {
            obj.addProperty("note", "veto skipped")
            obj.add("query_words", JsonArray())
            obj.add("vehicles", JsonArray())
            return obj
        }
        val results = vetoResults ?: emptyMap()
        val queryWords = JsonArray()
        (results.values.firstOrNull()?.queryWords ?: emptyList()).forEach { queryWords.add(it) }
        obj.add("query_words", queryWords)
        val vehiclesArr = JsonArray()
        for (v in allVehicles) {
            val vr = results[v.id]
            val vo = JsonObject()
            vo.addProperty("id", v.id)
            vo.addProperty("name", v.name)
            vo.addProperty("is_vetoed", vr?.isVetoed ?: false)
            vo.addProperty("reason_word", vr?.reasonWord ?: "")
            val man = JsonArray()
            (vr?.myManifest ?: emptyList()).forEach { man.add(it) }
            vo.add("my_manifest", man)
            val pool = JsonArray()
            (vr?.vetoPool ?: emptyList()).forEach { pool.add(it) }
            vo.add("veto_pool", pool)
            vehiclesArr.add(vo)
        }
        obj.add("vehicles", vehiclesArr)
        return obj
    }

    /** One landmark as it entered or left processRawLandmarks. No ICRS. */
    private fun landmarkDebugBlock(block: TextBlock, reason: String? = null): JsonObject {
        val o = JsonObject()
        o.addProperty("text", block.text)
        o.addProperty("confidence", block.confidence)
        val box = JsonObject()
        box.addProperty("left", block.boundingBox.left)
        box.addProperty("top", block.boundingBox.top)
        box.addProperty("right", block.boundingBox.right)
        box.addProperty("bottom", block.boundingBox.bottom)
        o.add("boundingBox", box)
        val pts = JsonArray()
        for (p in block.points) {
            val pt = JsonObject()
            pt.addProperty("x", p.x)
            pt.addProperty("y", p.y)
            pts.add(pt)
        }
        o.add("points", pts)
        if (reason != null) o.addProperty("reason", reason)
        return o
    }

    /**
     * veto.json for a debug odometer capture. Does not re-run the veto or change the pick.
     * A forced id skips the veto: vehicles is empty, winner_rule is forced.
     */
    private fun vetoSessionJson(
        detected: List<TextBlock>,
        kept: List<TextBlock>,
        forcedVehicleId: Int?,
        vetoResults: Map<Int, VetoResult>?,
        allVehicles: List<Vehicle>,
    ): JsonObject {
        val obj = JsonObject()
        val detectedArr = JsonArray()
        detected.forEach { detectedArr.add(landmarkDebugBlock(it)) }
        obj.add("detected", detectedArr)
        val keptArr = JsonArray()
        kept.forEach { keptArr.add(landmarkDebugBlock(it)) }
        obj.add("kept", keptArr)
        val eliminated = JsonArray()
        for (block in detected) {
            if (OdometerOcrUtils.cleanLandmarkString(block.text).isEmpty()) {
                eliminated.add(landmarkDebugBlock(block, reason = "empty_after_clean"))
            }
        }
        obj.add("eliminated", eliminated)
        if (forcedVehicleId != null) obj.addProperty("forced_vehicle_id", forcedVehicleId)
        else obj.add("forced_vehicle_id", JsonNull.INSTANCE)

        val survivorIdList = ArrayList<Int>()
        if (vetoResults != null) {
            for (v in allVehicles) {
                if (vetoResults[v.id]?.isVetoed == false) survivorIdList.add(v.id)
            }
        }
        val winnerId: Int?
        val winnerRule: String
        if (forcedVehicleId != null) {
            winnerId = forcedVehicleId
            winnerRule = "forced"
        } else if (survivorIdList.size == 1) {
            winnerId = survivorIdList[0]
            winnerRule = "sole_not_vetoed"
        } else {
            winnerId = null
            winnerRule = "none"
        }
        if (winnerId != null) obj.addProperty("winner_id", winnerId)
        else obj.add("winner_id", JsonNull.INSTANCE)
        obj.addProperty("winner_rule", winnerRule)

        val survivors = JsonArray()
        survivorIdList.forEach { survivors.add(it) }
        obj.add("survivor_ids", survivors)

        val vehiclesArr = JsonArray()
        if (forcedVehicleId == null) {
            val results = vetoResults ?: emptyMap()
            for (v in allVehicles) {
                val vr = results[v.id]
                val vo = JsonObject()
                vo.addProperty("id", v.id)
                vo.addProperty("name", v.name)
                vo.addProperty("is_vetoed", vr?.isVetoed ?: false)
                vo.addProperty("reason_word", vr?.reasonWord ?: "")
                vo.addProperty("tierReached", vr?.tierReached ?: 0)
                val man = JsonArray()
                (vr?.myManifest ?: emptyList()).forEach { man.add(it) }
                vo.add("my_manifest", man)
                val pool = JsonArray()
                (vr?.vetoPool ?: emptyList()).forEach { pool.add(it) }
                vo.add("veto_pool", pool)
                val stored = v.landmarkTextBlocksJson
                if (stored != null) vo.addProperty("landmarkTextBlocksJson", stored)
                else vo.add("landmarkTextBlocksJson", JsonNull.INSTANCE)
                vehiclesArr.add(vo)
            }
        }
        obj.add("vehicles", vehiclesArr)
        return obj
    }

    private data class AutoFillFrame(val imgW: Int, val imgH: Int)

    private data class AutoFillDiscovery(
        val detBlocks: List<TextBlock>,
        val queryLandmarks: List<TextBlock>,
    )

    private data class AutoFillVehiclePick(
        val vehicle: Vehicle?,
        val vetoResults: Map<Int, VetoResult>?,
        val vetoJson: String?,
        val early: AutoFillResult? = null,
    )

    private suspend fun prepareAutoFillFrame(
        masterBuffer: BufferSet,
        cameraRotationDegrees: Int,
        jsonDebug: JsonObject?,
        onStage: (suspend (String) -> Unit)?,
        onOriginalFrame: (suspend (Bitmap) -> Unit)?,
    ): AutoFillFrame {
        onStage?.invoke("Original")
        onOriginalFrame?.invoke(masterBuffer.p.toBitmap())
        val rotTime = if (cameraRotationDegrees != 0) {
            OdometerOcrUtils.rotate(masterBuffer, cameraRotationDegrees.toFloat())
        } else {
            0L
        }
        jsonDebug?.apply {
            addProperty("camera_rotation", cameraRotationDegrees)
            addProperty("rotate_time_ms", rotTime)
        }
        return AutoFillFrame(masterBuffer.width, masterBuffer.height)
    }

    private suspend fun discoverAutoFillLandmarks(
        context: Context,
        masterBuffer: BufferSet,
        imgW: Int,
        imgH: Int,
        debug: Boolean,
        jsonDebug: JsonObject?,
        onDebug: (String, String) -> Unit,
    ): AutoFillDiscovery {
        val tDisc0 = System.currentTimeMillis()
        val detBlocks = MultiScaleDetRunner.productDetLandmarks2048(
            context,
            masterBuffer.p.mat,
            PaddleSeedOdo.SEED_VERT_PRODUCTION,
            debug = if (debug) {
                { detJson, recJson -> onDebug(detJson, recJson) }
            } else {
                null
            },
        )
        NativePaddleEngine.heartbeat("odo_decode")
        val queryLandmarks = OdometerOcrUtils.processRawLandmarks(
            detBlocks, null, imgW, imgH,
        )
        val tDiscovery = System.currentTimeMillis() - tDisc0
        jsonDebug?.apply {
            addProperty("discovery_text", queryLandmarks.joinToString(" ") { it.text })
            addProperty("discovery_time_ms", tDiscovery)
            addProperty("paddle_seed_vert", PaddleSeedOdo.SEED_VERT_PRODUCTION)
        }
        return AutoFillDiscovery(detBlocks, queryLandmarks)
    }

    private suspend fun selectAutoFillVehicle(
        queryLandmarks: List<TextBlock>,
        detBlocks: List<TextBlock>,
        allVehicles: List<Vehicle>,
        forcedVehicleId: Int?,
        debug: Boolean,
        engineName: String,
        jsonDebug: JsonObject?,
        onUnresolvedVehicle: (suspend (survivorIds: List<Int>, vetoDebugJson: String) -> Int?)?,
    ): AutoFillVehiclePick {
        var vetoResults: Map<Int, VetoResult>? = null
        val winningVehicle: Vehicle? = if (forcedVehicleId != null) {
            allVehicles.find { it.id == forcedVehicleId && !it.deleted }
        } else {
            vetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarks, allVehicles, engineName)
            val survivorIds = ArrayList<Int>(allVehicles.size)
            for (v in allVehicles) {
                if (vetoResults[v.id]?.isVetoed == false) survivorIds.add(v.id)
            }
            if (survivorIds.size == 1) {
                allVehicles.find { it.id == survivorIds[0] }
            } else {
                val vetoObj = tier1VetoDebugJson(
                    engine = engineName,
                    forcedVehicleId = null,
                    winnerId = null,
                    allVehicles = allVehicles,
                    vetoResults = vetoResults,
                )
                val idsJson = JsonArray()
                survivorIds.forEach { idsJson.add(it) }
                vetoObj.add("survivor_ids", idsJson)
                val vetoDebugJson = vetoObj.toString()
                val picked = if (allVehicles.isEmpty()) {
                    null
                } else {
                    onUnresolvedVehicle?.invoke(survivorIds, vetoDebugJson)
                }
                if (picked == null) {
                    return AutoFillVehiclePick(
                        vehicle = null,
                        vetoResults = vetoResults,
                        vetoJson = null,
                        early = AutoFillResult(
                            error = S("Vehicle not identified"),
                            debugJson = vetoDebugJson,
                            survivorIds = survivorIds,
                        ),
                    )
                }
                allVehicles.find { it.id == picked } ?: return AutoFillVehiclePick(
                    vehicle = null,
                    vetoResults = vetoResults,
                    vetoJson = null,
                    early = AutoFillResult(
                        error = S("Vehicle not identified"),
                        debugJson = vetoDebugJson,
                        survivorIds = survivorIds,
                    ),
                )
            }
        }
        jsonDebug?.add(
            "tier1_veto",
            tier1VetoDebugJson(
                engine = engineName,
                forcedVehicleId = forcedVehicleId,
                winnerId = winningVehicle?.id,
                allVehicles = allVehicles,
                vetoResults = vetoResults,
            ),
        )
        val vetoJson = if (debug) {
            vetoSessionJson(
                detected = detBlocks,
                kept = queryLandmarks,
                forcedVehicleId = forcedVehicleId,
                vetoResults = vetoResults,
                allVehicles = allVehicles,
            ).toString()
        } else {
            null
        }
        return AutoFillVehiclePick(winningVehicle, vetoResults, vetoJson)
    }

    private fun recordMatchedVehicle(
        jsonDebug: JsonObject?,
        winningVehicle: Vehicle,
        forcedVehicleId: Int?,
    ) {
        jsonDebug?.apply {
            addProperty("matched_vehicle_id", winningVehicle.id)
            addProperty("matched_vehicle_name", winningVehicle.name)
            if (forcedVehicleId != null) addProperty("forced_vehicle", true)
        }
    }

    private fun alignAutoFillLandmarks(
        queryLandmarks: List<TextBlock>,
        referenceLandmarks: List<IcrsLandmark>,
        winningVehicle: Vehicle,
        imgW: Int,
        imgH: Int,
        jsonDebug: JsonObject?,
    ): RawSimilarity {
        val queryPrimary = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarks, referenceLandmarks)
        NativePaddleEngine.heartbeat("odo_align")
        val sim = ImageAlignmentUtils.rawSimilarity(
            referenceLandmarks,
            queryPrimary,
            winningVehicle,
            imgW,
            imgH,
        )
        jsonDebug?.apply {
            addProperty("raw_similarity_success", sim.success)
            sim.metadata.forEach { (k, v) -> addProperty("sim_$k", v) }
        }
        return sim
    }

    private suspend fun readAutoFillOdometer(
        context: Context,
        masterBuffer: BufferSet,
        detBlocks: List<TextBlock>,
        sim: RawSimilarity,
        winningVehicle: Vehicle,
        imgW: Int,
        imgH: Int,
        dc: Int,
        allowExtra: Boolean,
        lastTrack: Int?,
        jsonDebug: JsonObject?,
        t0: Long,
        capturedDet: String?,
        capturedRec: String?,
        capturedVeto: String?,
    ): AutoFillResult {
        val paddleEngine = NativePaddleEngine(context, "Numeric")
        val recBuffer = NativePaddleEngine.recBufferSet
        val quad = ImageAlignmentUtils.odometerQuadQueryPixels(sim, winningVehicle, imgW, imgH)
        val rawOdo = if (quad != null) {
            PaddleSeedOdo.readSeedRec(
                seeds = detBlocks,
                crop = PaddleSeedOdo.orientedRedQuad(quad),
                graySet = masterBuffer,
                recBuffer = recBuffer,
                paddleEngine = paddleEngine,
                displayName = "paddle_seed",
                seedVert = PaddleSeedOdo.SEED_VERT_PRODUCTION,
                preferredDigitLen = dc,
                allowLenPlusOne = allowExtra,
                collectHarness = false,
            )?.odometerValue
        } else {
            null
        }
        val resolved = rawOdo?.let {
            OdometerTracking.resolveFromOcr(it, winningVehicle, lastTrack)
        }
        jsonDebug?.apply {
            addProperty("odo_digit_count", dc)
            addProperty("odo_allow_len_plus_one", allowExtra)
            addProperty("odo_last_tracking", lastTrack ?: -1)
            addProperty("odo_raw_ocr", rawOdo)
            addProperty("odo_tracking", resolved?.trackingOdometer)
            addProperty("odo_new_rollover", resolved?.newRolloverCount)
            addProperty("total_pipeline_time_ms", System.currentTimeMillis() - t0)
        }
        return AutoFillResult(
            vehicleId = winningVehicle.id,
            odometer = resolved?.trackingOdometer?.toString(),
            newRolloverCount = resolved?.newRolloverCount,
            debugJson = jsonDebug?.toString(),
            detJson = capturedDet,
            recJson = capturedRec,
            vetoJson = capturedVeto,
        )
    }

    /**
     * Unified entry point for Fill, Start trip, and batch dash OCR.
     * Camera rotation only → product_det landmarks at 0.40 → Tier-1 ID → red-quad paddle seed read.
     */
    suspend fun runAutoFillPipeline(
        context: Context,
        masterBuffer: BufferSet,
        allVehicles: List<Vehicle>,
        debug: Boolean,
        cameraRotationDegrees: Int = 0,
        onStage: (suspend (String) -> Unit)? = null,
        onOriginalFrame: (suspend (Bitmap) -> Unit)? = null,
        /** When set, skip Tier-1 vehicle ID and use this vehicle. */
        forcedVehicleId: Int? = null,
        /**
         * Last fill’s **tracking** odometer for the winning vehicle (from fuel_entries).
         * Used for PreferLen / rollover (+1 digit when last face started with 9).
         */
        lastTrackingOdometer: Int? = null,
        /**
         * Optional map vehicleId → last tracking odo when winner is unknown until after ID.
         * [lastTrackingOdometer] wins if both set for the winner.
         */
        lastTrackingByVehicleId: Map<Int, Int?> = emptyMap(),
        /**
         * Veto left zero or more than one vehicle. Return the chosen id, or null to stop.
         * Not called when [allVehicles] is empty. Landmarks from this call stay in scope.
         */
        onUnresolvedVehicle: (suspend (survivorIds: List<Int>, vetoDebugJson: String) -> Int?)? = null,
        @Suppress("UNUSED_PARAMETER") odoExpand: OdoExpandKind = OdoExpandKind.VALLEY,
    ): AutoFillResult {
        val t0 = System.currentTimeMillis()
        val jsonDebug = if (debug) JsonObject() else null
        var capturedDet: String? = null
        var capturedRec: String? = null
        var capturedVeto: String? = null

        try {
            val frame = prepareAutoFillFrame(
                masterBuffer,
                cameraRotationDegrees,
                jsonDebug,
                onStage,
                onOriginalFrame,
            )
            val discovery = discoverAutoFillLandmarks(
                context,
                masterBuffer,
                frame.imgW,
                frame.imgH,
                debug,
                jsonDebug,
            ) { detJson, recJson ->
                capturedDet = detJson
                capturedRec = recJson
            }
            val engineName = "ML Kit"
            val picked = selectAutoFillVehicle(
                queryLandmarks = discovery.queryLandmarks,
                detBlocks = discovery.detBlocks,
                allVehicles = allVehicles,
                forcedVehicleId = forcedVehicleId,
                debug = debug,
                engineName = engineName,
                jsonDebug = jsonDebug,
                onUnresolvedVehicle = onUnresolvedVehicle,
            )
            if (picked.early != null) return picked.early
            val winningVehicle = picked.vehicle
            capturedVeto = picked.vetoJson

            if (winningVehicle == null) {
                val errorMsg = if (forcedVehicleId != null) {
                    "Forced vehicle $forcedVehicleId not found"
                } else {
                    S("Vehicle not identified")
                }
                jsonDebug?.addProperty("error", errorMsg)
                return AutoFillResult(
                    error = errorMsg,
                    debugJson = jsonDebug?.toString(),
                    detJson = capturedDet,
                    recJson = capturedRec,
                    vetoJson = capturedVeto,
                )
            }

            recordMatchedVehicle(jsonDebug, winningVehicle, forcedVehicleId)

            val referenceLandmarks = winningVehicle.landmarkTextBlocksJson?.let {
                OdometerOcrUtils.getIcrsLandmarksFromJson(it, engineName)
            } ?: emptyList()

            val lastTrack = lastTrackingOdometer
                ?: lastTrackingByVehicleId[winningVehicle.id]
            val dc = OdometerTracking.digitCount(winningVehicle)
            val allowExtra = OdometerTracking.lastFillStartsWithNine(lastTrack, dc)

            val sim = alignAutoFillLandmarks(
                discovery.queryLandmarks,
                referenceLandmarks,
                winningVehicle,
                frame.imgW,
                frame.imgH,
                jsonDebug,
            )
            return readAutoFillOdometer(
                context,
                masterBuffer,
                discovery.detBlocks,
                sim,
                winningVehicle,
                frame.imgW,
                frame.imgH,
                dc,
                allowExtra,
                lastTrack,
                jsonDebug,
                t0,
                capturedDet,
                capturedRec,
                capturedVeto,
            )
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: CancellationException) {
            throw e
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            // UI landing zone: Fill and Trip show Pipeline Error and do not fill or save.
            Log.e("OcrHarness", "AutoFill Pipeline failed", e)
            jsonDebug?.addProperty("exception", e.message)
            return AutoFillResult(
                error = "Pipeline Error: ${e.message}",
                debugJson = jsonDebug?.toString(),
                detJson = capturedDet,
                recJson = capturedRec,
                vetoJson = capturedVeto,
            )
        }
    }

    private external fun qfRotGmmDetect(
        buffer: BufferSet,
        maxRedBoxes: Int,
        context: Context,
    ): QfRotGmmDetectOut

    private external fun qfRotGmmRecognize(
        buffer: BufferSet,
        bluePts: FloatArray,
    ): QfRotGmmRecOut

    private external fun qfRotGmmStackJpeg(
        buffer: BufferSet,
        costL: Int,
        costT: Int,
        costR: Int,
        costB: Int,
        volL: Int,
        volT: Int,
        volR: Int,
        volB: Int,
    ): ByteArray

    /**
     * Fill pump: unrotated-photo rot-G-- copy (v=0.34, pad p54).
     * Stages are quality-80 YUV JPEGs. Classifier stays in Kotlin.
     */
    suspend fun runPumpCostVolPipeline(
        context: Context,
        masterBuffer: BufferSet,
        debug: Boolean,
        onStage: (suspend (String, ByteArray) -> Unit)? = null
    ): PumpCostVolResult {
        val t0 = System.currentTimeMillis()
        try {
            check(NativeImageUtils.longLivedHistMeta.isNotEmpty())
            NativePaddleEngine.heartbeat("pump_det")
            val det = qfRotGmmDetect(
                masterBuffer,
                FILL_MAX_RED_BOXES,
                context,
            )
            if (det.discoveryJpeg.isNotEmpty()) onStage?.invoke("discovery", det.discoveryJpeg)
            NativePaddleEngine.heartbeat("pump_rec")
            val rec = qfRotGmmRecognize(masterBuffer, det.bluePts)
            val n = minOf(det.blueAabb.size / 4, rec.asis.size, rec.digits.size)
            val rects = ArrayList<Rect>(n)
            for (i in 0 until n) {
                val o = i * 4
                rects.add(Rect(det.blueAabb[o], det.blueAabb[o + 1], det.blueAabb[o + 2], det.blueAabb[o + 3]))
            }
            val cands = PumpCostVolUtils.buildRedBoxCandidates(
                rects,
                rec.asis.take(n),
                rec.digits.take(n),
                rec.asisProbs.take(n),
                rec.digitsProbs.take(n),
            )
            val quads = ArrayList<ContentExpandUtils.OrientedQuad>(n)
            for (i in 0 until n) {
                val o = i * 8
                quads.add(ContentExpandUtils.OrientedQuad(det.bluePts.copyOfRange(o, o + 8)))
            }
            val weighted = FillPumpCostVolChooser.classifyWeighted(
                cands, quads, masterBuffer.width,
            )
            val linear = if (debug) FillPumpCostVolChooser.classify(cands) else null
            fun aabbOrEmpty(text: String, cand: RedBoxOcrCandidate): IntArray {
                val r = cand.rect
                if (r == null || text == "N/A" || text.isBlank()) return intArrayOf(-1, -1, -1, -1)
                return intArrayOf(r.left, r.top, r.right, r.bottom)
            }
            val costBox = aabbOrEmpty(weighted.cost, weighted.costCand)
            val volBox = aabbOrEmpty(weighted.vol, weighted.volCand)
            val stacked = qfRotGmmStackJpeg(
                masterBuffer,
                costBox[0], costBox[1], costBox[2], costBox[3],
                volBox[0], volBox[1], volBox[2], volBox[3],
            )
            if (stacked.isNotEmpty()) onStage?.invoke("final", stacked)

            val cost = weighted.cost.takeIf { it != "N/A" && it.isNotBlank() }
            val volume = weighted.vol.takeIf { it != "N/A" && it.isNotBlank() }
            val errorMsg = if (cost == null && volume == null) "Could not read pump display" else null
            val debugJson = if (debug) {
                check(linear != null)
                JsonObject().apply {
                    addProperty("pipeline", "rot-G--")
                    addProperty("vert", 0.34)
                    addProperty("pad", "p54")
                    addProperty("cost", weighted.cost)
                    addProperty("volume", weighted.vol)
                    addProperty("costLinear", linear.cost)
                    addProperty("volumeLinear", linear.vol)
                    addProperty("n_blue", n)
                    addProperty("pipeline_time_ms", System.currentTimeMillis() - t0)
                    if (errorMsg != null) addProperty("error", errorMsg)
                }.toString()
            } else null
            val classifyJson = if (debug) {
                check(linear != null)
                val blues = JSONArray()
                val nBlue = det.bluePts.size / 8
                for (i in 0 until nBlue) {
                    val quad = JSONArray()
                    val o = i * 8
                    for (k in 0 until 8) quad.put(det.bluePts[o + k].toDouble())
                    blues.put(quad)
                }
                val candidates = JSONArray()
                for (cand in cands) {
                    val rect = cand.rect
                    val rectJson = if (rect == null) {
                        JSONObject.NULL
                    } else {
                        JSONObject()
                            .put("left", rect.left)
                            .put("top", rect.top)
                            .put("right", rect.right)
                            .put("bottom", rect.bottom)
                    }
                    candidates.put(
                        JSONObject()
                            .put("label", cand.label)
                            .put("asis", cand.asis)
                            .put("digits", cand.digits)
                            .put("asisProbs", cand.asisProbs)
                            .put("digitsProbs", cand.digitsProbs)
                            .put("rect", rectJson)
                            .put("recW", cand.recW)
                            .put("recH", cand.recH),
                    )
                }
                JSONObject()
                    .put("imgW", masterBuffer.width)
                    .put("blues", blues)
                    .put("candidates", candidates)
                    .put("weighted", JSONObject(weighted.json))
                    .put("linear", JSONObject().put("cost", linear.cost).put("vol", linear.vol))
                    .toString()
            } else null
            val scalesJson = if (debug) {
                val rows = JSONArray()
                val nRaw = minOf(det.rawScale.size, det.rawScore.size, det.rawCorners.size / 8)
                for (i in 0 until nRaw) {
                    val corners = JSONArray()
                    val o = i * 8
                    for (k in 0 until 8) corners.put(det.rawCorners[o + k].toDouble())
                    rows.put(
                        JSONObject()
                            .put("scale", det.rawScale[i])
                            .put("corners", corners)
                            .put("score", det.rawScore[i].toDouble()),
                    )
                }
                rows.toString()
            } else null
            return PumpCostVolResult(
                cost = cost,
                volume = volume,
                error = errorMsg,
                debugJson = debugJson,
                classifyJson = classifyJson,
                scalesJson = scalesJson,
            )
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: CancellationException) {
            throw e
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            // UI landing zone: Fill and Trip show Pump OCR failed and do not fill or save.
            Log.e("OcrHarness", "Pump cost/vol pipeline failed", e)
            val err = "Pump OCR failed: ${e.message ?: "Unknown error"}"
            val debugJson = if (debug) {
                JsonObject().apply {
                    addProperty("pipeline", "rot-G--")
                    addProperty("vert", 0.34)
                    addProperty("pad", "p54")
                    addProperty("error", err)
                    addProperty("exception", e.message)
                    addProperty("pipeline_time_ms", System.currentTimeMillis() - t0)
                }.toString()
            } else null
            return PumpCostVolResult(error = err, debugJson = debugJson)
        }
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
