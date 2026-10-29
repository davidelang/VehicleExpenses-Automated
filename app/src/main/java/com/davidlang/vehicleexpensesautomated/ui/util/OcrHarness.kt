package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val debugJson: String? = null
)

data class PumpCostVolResult(
    val cost: String? = null,
    val volume: String? = null,
    val error: String? = null,
    val debugJson: String? = null
)

/** Quick Fill detect: original JPEG, discovery JPEG, blue quads (n×8), blue AABBs (n×4). */
class QfRotGmmDetectOut(
    @JvmField val originalJpeg: ByteArray,
    @JvmField val discoveryJpeg: ByteArray,
    @JvmField val bluePts: FloatArray,
    @JvmField val blueAabb: IntArray,
)

/** Quick Fill recognize: one string row per blue. No crop ids. */
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

    /**
     * Unified entry point for Quick Fill, Start trip, and batch dash OCR.
     * Camera rotation only → product_det landmarks at 0.40 → Tier-1 ID → red-quad paddle seed read.
     */
    suspend fun runAutoFillPipeline(
        context: Context,
        masterBuffer: BufferSet,
        allVehicles: List<Vehicle>,
        debug: Boolean,
        cameraRotationDegrees: Int = 0,
        onStage: (suspend (String, Bitmap) -> Unit)? = null,
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
        @Suppress("UNUSED_PARAMETER") odoExpand: OdoExpandKind = OdoExpandKind.VALLEY,
    ): AutoFillResult {
        val t0 = System.currentTimeMillis()
        val jsonDebug = if (debug) JsonObject() else null

        try {
            onStage?.invoke("Original", masterBuffer.p.toBitmap())

            val rotTime = if (cameraRotationDegrees != 0) {
                OdometerOcrUtils.rotate(masterBuffer, cameraRotationDegrees.toFloat())
            } else {
                0L
            }
            jsonDebug?.apply {
                addProperty("camera_rotation", cameraRotationDegrees)
                addProperty("rotate_time_ms", rotTime)
            }

            val imgW = masterBuffer.width
            val imgH = masterBuffer.height
            val tDisc0 = System.currentTimeMillis()
            val detBlocks = MultiScaleDetRunner.productDetLandmarks2048(
                context, masterBuffer.p.mat, PaddleSeedOdo.SEED_VERT_PRODUCTION,
            )
            val queryLandmarks = OdometerOcrUtils.processRawLandmarks(
                detBlocks, null, imgW, imgH,
            )
            val tDiscovery = System.currentTimeMillis() - tDisc0
            jsonDebug?.apply {
                addProperty("discovery_text", queryLandmarks.joinToString(" ") { it.text })
                addProperty("discovery_time_ms", tDiscovery)
                addProperty("paddle_seed_vert", PaddleSeedOdo.SEED_VERT_PRODUCTION)
            }

            val engineName = "ML Kit"
            var vetoResults: Map<Int, VetoResult>? = null
            val winningVehicle = if (forcedVehicleId != null) {
                allVehicles.find { it.id == forcedVehicleId && !it.deleted }
            } else {
                vetoResults = ImageAlignmentUtils.performTier1Veto(queryLandmarks, allVehicles, engineName)
                val winnerId = vetoResults.entries.find { !it.value.isVetoed }?.key
                allVehicles.find { it.id == winnerId }
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

            val (refW, refH) = if (!winningVehicle.referenceDashPhotoUrl.isNullOrEmpty()) {
                NativePaddleEngine.getReferenceDimensions(context, winningVehicle.referenceDashPhotoUrl)
            } else {
                Pair(NativePaddleEngine.DEFAULT_REF_DASH_W, NativePaddleEngine.DEFAULT_REF_DASH_H)
            }
            val referenceLandmarks = winningVehicle.landmarkTextBlocksJson?.let {
                OdometerOcrUtils.getFullLandmarksFromJson(it, engineName, refW, refH)
            } ?: emptyList()

            val lastTrack = lastTrackingOdometer
                ?: lastTrackingByVehicleId[winningVehicle.id]
            val dc = OdometerTracking.digitCount(winningVehicle)
            val allowExtra = OdometerTracking.lastFillStartsWithNine(lastTrack, dc)

            val queryPrimary = ImageAlignmentUtils.disambiguateLandmarks(queryLandmarks, referenceLandmarks)
            val sim = ImageAlignmentUtils.rawSimilarity(
                referenceLandmarks,
                queryPrimary,
                winningVehicle,
                refW,
                refH,
                imgW,
                imgH,
            )
            jsonDebug?.apply {
                addProperty("raw_similarity_success", sim.success)
                sim.metadata.forEach { (k, v) -> addProperty("sim_$k", v) }
            }

            val paddleEngine = NativePaddleEngine(context, "Numeric")
            val recBuffer = NativePaddleEngine.recBufferSet
            val quad = ImageAlignmentUtils.odometerQuadQueryPixels(sim, winningVehicle, imgW, imgH)
            val rawOdo = if (quad != null) {
                PaddleSeedOdo.readSeedRec(
                    seeds = detBlocks,
                    crop = PaddleSeedOdo.orientedRedQuad(quad),
                    gray = masterBuffer.p.mat,
                    recBuffer = recBuffer,
                    paddleEngine = paddleEngine,
                    displayName = "paddle_seed",
                    seedVert = PaddleSeedOdo.SEED_VERT_PRODUCTION,
                    preferredDigitLen = dc,
                    allowLenPlusOne = allowExtra,
                )?.odometerValue
            } else {
                null
            }

            val resolved = rawOdo?.let {
                OdometerTracking.resolveFromOcr(it, winningVehicle, lastTrack)
            }

            jsonDebug?.apply {
                addProperty("ref_dash_w", refW)
                addProperty("ref_dash_h", refH)
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
            )
        } catch (e: Exception) {
            Log.e("OcrHarness", "AutoFill Pipeline failed", e)
            jsonDebug?.addProperty("exception", e.message)
            return AutoFillResult(error = "Pipeline Error: ${e.message}", debugJson = jsonDebug?.toString())
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
     * Quick Fill pump: unrotated-photo rot-G-- copy (v=0.34, pad p54).
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
            val det = qfRotGmmDetect(
                masterBuffer,
                PumpOcrSettings.maxRedBoxes(context),
                context,
            )
            if (det.originalJpeg.isNotEmpty()) onStage?.invoke("original", det.originalJpeg)
            if (det.discoveryJpeg.isNotEmpty()) onStage?.invoke("discovery", det.discoveryJpeg)
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
            val weighted = QuickFillPumpCostVolChooser.classifyWeighted(
                cands, quads, masterBuffer.width,
            )
            val linear = QuickFillPumpCostVolChooser.classify(cands)
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
            return PumpCostVolResult(
                cost = cost,
                volume = volume,
                error = errorMsg,
                debugJson = debugJson,
            )
        } catch (e: Exception) {
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

    /**
     * Batch-import pump cost/volume via **Set I** (D+E+G hybrid).
     * Does not change Quick Fill [runPumpCostVolPipeline] (rot-G--, vert 0.34, pad p54).
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
