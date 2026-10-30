package com.davidlang.vehicleexpensesautomated.ui.util

import android.util.Base64
import com.davidlang.vehicleexpensesautomated.ui.ocr.ocrPumpFeedOriented40
import org.opencv.imgproc.Imgproc

/** Union of paddle seeds overlapping the red odometer quad, padded at [seedVert], clipped, Raw + Bin-Trials. */
object PaddleSeedOdo {

    const val SEED_VERT_PRODUCTION: Float = 0.40f

    data class SeedRecRun(
        val harness: OcrHarnessResult?,
        val trace: RefinementTrace,
        val odometerValue: String?,
    )

    fun orientedRedQuad(quad: List<Pair<Float, Float>>): ContentExpandUtils.OrientedQuad {
        val p = FloatArray(8)
        for (i in 0 until 4) {
            p[i * 2] = quad[i].first
            p[i * 2 + 1] = quad[i].second
        }
        return ContentExpandUtils.OrientedQuad(p)
    }

    /** Bare seed. ML Kit blocks with no points use the four corners of the AABB. */
    fun bareSeedQuad(block: TextBlock): ContentExpandUtils.OrientedQuad {
        if (block.points.size >= 4) {
            val p = FloatArray(8)
            for (i in 0 until 4) {
                p[i * 2] = block.points[i].x.toFloat()
                p[i * 2 + 1] = block.points[i].y.toFloat()
            }
            return ContentExpandUtils.orientedFromPoints8(p)
        }
        return ContentExpandUtils.orientedFromAabb(block.boundingBox)
    }

    /**
     * Union of seeds overlapping [crop], clipped to the red odometer quad.
     * Luma is [graySet].p.mat at the feed call (not a Mat held across [ocrPumpFeedOriented40]).
     * [seedVert] null: bare seeds only (ML Kit). Non-null: each overlapping bare seed is rot-G-- padded
     * (side fractions 0.54 / 0.55), then the padded quads are unioned and clipped.
     * No overlap returns null (empty odometer for production callers).
     */
    suspend fun readSeedRec(
        seeds: List<TextBlock>,
        crop: ContentExpandUtils.OrientedQuad,
        graySet: BufferSet,
        recBuffer: BufferSet,
        paddleEngine: NativePaddleEngine,
        displayName: String,
        seedVert: Float?,
        preferredDigitLen: Int,
        allowLenPlusOne: Boolean,
        collectHarness: Boolean,
    ): SeedRecRun? {
        val bare = seeds.map { bareSeedQuad(it) }
        val clipped = if (seedVert == null) {
            ContentExpandUtils.unionOrientedSeedsClippedToCrop(bare, crop)
        } else {
            val overlapping = bare.filter { ContentExpandUtils.clipOrientedSeedToConvexQuad(it, crop) != null }
            if (overlapping.isEmpty()) {
                null
            } else {
                val padded = overlapping.map { seed ->
                    ContentExpandUtils.calculatedOrientedUv(
                        seed,
                        seedVert,
                        SET_G_UV_LEFT_FRAC,
                        SET_G_UV_RIGHT_FRAC,
                    )
                }
                ContentExpandUtils.unionOrientedSeedsClippedToCrop(padded, crop)
            }
        } ?: return null
        val fed = ocrPumpFeedOriented40(
            graySet.p.mat, clipped, NativePaddleEngine.bufferSetA, recBuffer,
        ) ?: return null
        val recId = fed.recCropId
        val tH0 = System.currentTimeMillis()
        var trialCropId = -1
        try {
            val rawSnap = if (collectHarness) {
                PumpCostVolUtils.snapRecCrop(recBuffer, recId, fed.targetW, fed.targetH)
            } else {
                ""
            }

            val tRaw0 = System.currentTimeMillis()
            val rawDigits = paddleEngine.recognizeNumericDecimal(recBuffer.c[recId])
            val rawMs = System.currentTimeMillis() - tRaw0
            val rawText = digitText(rawDigits.debugText)

            val tBin0 = System.currentTimeMillis()
            val stats = OdometerOcrUtils.getHistStats(recBuffer.c[recId].mat)
            val midpoints = OdometerOcrUtils.findValleyMidpoints(stats.rawBins)
            data class BinTrial(val text: String, val sumProb: Float, val minProb: Float, val snap: String)
            val trials = mutableListOf<BinTrial>()
            val srcSlice = recBuffer.c[recId]
            trialCropId = recBuffer.s.createCrop(0, 0, srcSlice.width, srcSlice.height)
            val trialMat = recBuffer.c[trialCropId].mat
            midpoints.forEach { binIdx ->
                val threshold = binIdx * 4.0
                Imgproc.threshold(
                    recBuffer.c[recId].mat, trialMat, threshold, 255.0, Imgproc.THRESH_BINARY,
                )
                val dig = paddleEngine.recognizeNumericDecimal(trialMat)
                val text = digitText(dig.debugText)
                if (text.isEmpty()) return@forEach
                val (sumP, minP) = probStats(dig.perCharProbs)
                val snap = if (collectHarness) matJpegB64(trialMat) else ""
                trials.add(BinTrial(text, sumP, minP, snap))
            }
            val binMs = System.currentTimeMillis() - tBin0

            val highQual = trials.filter { it.minProb >= 0.40f }
            val binWinner =
                if (highQual.isNotEmpty()) highQual.maxByOrNull { it.sumProb }
                else trials.maxByOrNull { it.sumProb }
            val binText = binWinner?.text ?: ""
            val binSnap = binWinner?.snap ?: rawSnap

            val steps = listOf(
                OcrStepResult("Raw", rawSnap, null, rawText),
                OcrStepResult("Bin-Trials", binSnap, null, binText),
            )
            val bestOdo = OdometerOcrUtils.pickBestOdometer(
                steps,
                preferredLen = preferredDigitLen,
                allowLenPlusOne = allowLenPlusOne,
                preferBin = true,
            )
            val totalMs = System.currentTimeMillis() - tH0

            val harness = if (collectHarness) {
                val recImgRaw = if (rawSnap.isNotEmpty()) "<img src='data:image/jpeg;base64,$rawSnap'><br>" else ""
                val recImgBin = if (binSnap.isNotEmpty()) "<img src='data:image/jpeg;base64,$binSnap'><br>" else ""
                val htmlCell = buildString {
                    append("<div class='ocr-step'><b>Raw:</b> ($rawMs ms)<br>${rawText.ifEmpty { "---" }}<br>$recImgRaw</div>")
                    append("<div class='ocr-step'><b>Bin-Trials:</b> ($binMs ms)<br>${binText.ifEmpty { "---" }}<br>$recImgBin</div>")
                }
                val jsonStages = com.google.gson.JsonObject()
                jsonStages.add(
                    "Raw",
                    com.google.gson.JsonObject().apply {
                        addProperty("text", rawText)
                        addProperty("time", rawMs)
                    },
                )
                jsonStages.add(
                    "Bin-Trials",
                    com.google.gson.JsonObject().apply {
                        addProperty("text", binText)
                        addProperty("time", binMs)
                    },
                )
                OcrHarnessResult(
                    htmlHeader = displayName,
                    htmlCell = htmlCell,
                    jsonSection = com.google.gson.JsonObject().apply {
                        addProperty("odometer", bestOdo ?: "")
                        add("stages", jsonStages)
                    },
                    odometerValue = bestOdo,
                    thumbB64 = rawSnap.ifEmpty { null },
                    totalTimeMs = totalMs,
                )
            } else {
                null
            }
            return SeedRecRun(harness, RefinementTrace(displayName, totalMs, steps), bestOdo)
        } finally {
            recBuffer.c[recId].release()
            if (trialCropId >= 0) recBuffer.c[trialCropId].release()
        }
    }

    private fun digitText(debugText: String): String = debugText.trim().filter { it.isDigit() }

    private fun probStats(perCharProbs: String): Pair<Float, Float> {
        val vals = perCharProbs.split(",").mapNotNull { part ->
            val colon = part.indexOf(':')
            if (colon < 0) null else part.substring(colon + 1).trim().toFloatOrNull()
        }
        if (vals.isEmpty()) return 0f to 0f
        return vals.sum() to (vals.minOrNull() ?: 0f)
    }

    private fun matJpegB64(mat: org.opencv.core.Mat): String {
        val buf = org.opencv.core.MatOfByte()
        return try {
            if (!org.opencv.imgcodecs.Imgcodecs.imencode(".jpg", mat, buf)) {
                throw IllegalStateException("imencode jpg failed")
            } else {
                Base64.encodeToString(buf.toArray(), Base64.NO_WRAP)
            }
        } finally {
            buf.release()
        }
    }
}
