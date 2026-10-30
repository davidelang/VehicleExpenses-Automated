package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min


/** Pixel coordinates in full workspace/photo space for pump OCR regions. */
data class PumpHunk(val text: String, val rect: RectF)


data class PumpRectOcrLists(
    val asis: List<String>,
    val digits: List<String>,
    val asisProbs: List<String> = emptyList(),
    val digitsProbs: List<String> = emptyList(),
    /** JPEG preview of the 48×W crop actually fed to recognize (same crop for asis and digits). */
    val recB64: List<String> = emptyList(),
    val recW: List<Int> = emptyList(),
    val recH: List<Int> = emptyList(),
)


    fun PumpCostVolUtils.pumpOcrCleanAndProbs(debugText: String, perCharProbs: String): Pair<String, String> {
        val cleanText = debugText
        val probStr = if (perCharProbs.isNotEmpty()) perCharProbs else ""
        return cleanText to probStr
    }


    fun PumpCostVolUtils.createBlueAndOrangeHunksFromReds(
        reds: List<PumpHunk>,
        imgW: Int,
        imgH: Int,
        vertFactors: List<Float> = SET_G_VERT_FACTORS,
        horizFactor: Float = SET_G_HORIZ_FACTOR
    ): Pair<List<PumpHunk>, List<PumpHunk>> {
        val blues = mutableListOf<PumpHunk>()
        val oranges = mutableListOf<PumpHunk>()
        reds.forEach { h ->
            val r = Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())
            val hgt = r.height()
            vertFactors.forEach { v ->
                var nt = (r.top - (v * hgt)).toInt().coerceIn(0, imgH - 1)
                var nb = (r.bottom + (v * hgt)).toInt().coerceIn(nt + 1, imgH)
                val newH = nb - nt
                val horiz = (horizFactor * newH).toInt()
                var nl = (r.left - horiz).toInt().coerceIn(0, imgW - 1)
                var nr = (r.right + horiz).toInt().coerceIn(nl + 1, imgW)
                val bRect = Rect(nl, nt, nr, nb)
                val oExt = (0.1 * newH).toInt()
                val ol = (nl - oExt).coerceIn(0, imgW - 1)
                val orr = (nr + oExt).coerceIn(0, imgW)
                val oRect = Rect(ol, nt, orr, nb)
                blues.add(PumpHunk("", RectF(bRect.left.toFloat(), bRect.top.toFloat(), bRect.right.toFloat(), bRect.bottom.toFloat())))
                oranges.add(PumpHunk("", RectF(oRect.left.toFloat(), oRect.top.toFloat(), oRect.right.toFloat(), oRect.bottom.toFloat())))
            }
        }
        return blues to oranges
    }


    fun PumpCostVolUtils.rectToJson(r: Rect): JSONObject =
        JSONObject().put("l", r.left).put("t", r.top).put("r", r.right).put("b", r.bottom)


    fun PumpCostVolUtils.redBoxOcrCandidateToJson(c: RedBoxOcrCandidate): JSONObject {
        val j = JSONObject()
            .put("label", c.label)
            .put("asis", c.asis)
            .put("digits", c.digits)
            .put("asisProbs", c.asisProbs)
            .put("digitsProbs", c.digitsProbs)
        if (c.recB64.isNotEmpty()) j.put("_htmlRec", c.recB64)
        if (c.recW > 0) j.put("recW", c.recW)
        if (c.recH > 0) j.put("recH", c.recH)
        c.rect?.let { j.put("rect", rectToJson(it)) }
        return j
    }


    fun PumpCostVolUtils.hunkToRect(h: PumpHunk): Rect =
        Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())

    fun PumpCostVolUtils.hunksToRects(hunks: List<PumpHunk>): List<Rect> = hunks.map { hunkToRect(it) }


    fun PumpCostVolUtils.rectsToHunks(rects: List<Rect>): List<PumpHunk> =
        rects.map { r -> PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())) }


    fun PumpCostVolUtils.qualifiesFor3SidesNearExtend(cR: Rect, oR: Rect): Boolean {
        val insides = listOf(oR.left >= cR.left, oR.top >= cR.top, oR.right <= cR.right, oR.bottom <= cR.bottom)
        if (insides.count { it } != 3) return false
        val (protrPx, hasOverlap) = when {
            !insides[0] -> (cR.left - oR.left) to (oR.right > cR.left)
            !insides[2] -> (oR.right - cR.right) to (oR.left < cR.right)
            !insides[1] -> (cR.top - oR.top) to (oR.bottom > cR.top)
            !insides[3] -> (oR.bottom - cR.bottom) to (oR.top < cR.bottom)
            else -> 0 to true
        }
        return protrPx <= 40 && hasOverlap
    }


    /**
     * Collapse near-duplicate reds (multi-scale copies of the same field) into one AABB.
     *
     * Exact-contain + 3-sides-≤40px miss the common case: two similar-size boxes with
     * high overlap but the inner one sticks out more than 40px. Those stay drawn as
     * stacked reds. Union only when the pair is similar height so a one-line cost
     * is not swallowed into a cost∪vol wrapper.
     */
    fun PumpCostVolUtils.mergeSimilarOverlapRects(rects: MutableList<Rect>) {
        if (rects.size < 2) return
        fun area(r: Rect) = r.width().coerceAtLeast(0) * r.height().coerceAtLeast(0)
        fun inter(a: Rect, b: Rect): Int {
            val l = max(a.left, b.left)
            val t = max(a.top, b.top)
            val rr = min(a.right, b.right)
            val btm = min(a.bottom, b.bottom)
            return max(0, rr - l) * max(0, btm - t)
        }
        var changed = true
        var guard = 0
        while (changed && guard++ < 16) {
            changed = false
            rects.sortByDescending { area(it) }
            val used = BooleanArray(rects.size)
            val out = ArrayList<Rect>(rects.size)
            for (i in rects.indices) {
                if (used[i]) continue
                val cur = Rect(rects[i])
                for (j in i + 1 until rects.size) {
                    if (used[j]) continue
                    val o = rects[j]
                    val interA = inter(cur, o)
                    if (interA <= 0) continue
                    val aCur = area(cur).toFloat()
                    val aO = area(o).toFloat()
                    val unionA = aCur + aO - interA
                    val iou = if (unionA > 0f) interA / unionA else 0f
                    val coverMin = interA / min(aCur, aO).coerceAtLeast(1f)
                    val hRatio = min(cur.height(), o.height()).toFloat() /
                        max(cur.height(), o.height()).coerceAtLeast(1)
                    if (iou >= 0.50f || (coverMin >= 0.80f && hRatio >= 0.70f)) {
                        cur.union(o)
                        used[j] = true
                        changed = true
                    }
                }
                out.add(cur)
            }
            rects.clear()
            rects.addAll(out)
        }
    }


    fun PumpCostVolUtils.doCrossScaleRedboxFilterPixel(redRects: MutableList<Rect>) {
        if (redRects.isEmpty()) return
        mergeSimilarOverlapRects(redRects)
        redRects.sortByDescending { it.width() * it.height() }
        val kept = mutableListOf<Rect>()
        for (r1 in redRects) {
            val isContained = kept.any { r2 ->
                r2.contains(r1.left, r1.top, r1.right, r1.bottom)
            }
            if (!isContained) kept.add(r1)
        }
        data class Iv(val s: Int, val e: Int, val idx: Int)
        val xIvs = kept.withIndex().map { (i, r) -> Iv(r.left, r.right, i) }.sortedBy { it.s }
        val xOver = mutableSetOf<Pair<Int, Int>>()
        val activeX = mutableListOf<Iv>()
        for (iv in xIvs) {
            activeX.removeAll { it.e < iv.s }
            for (a in activeX) {
                val lo = minOf(a.idx, iv.idx); val hi = maxOf(a.idx, iv.idx)
                xOver.add(lo to hi)
            }
            activeX.add(iv)
        }
        val yIvs = kept.withIndex().map { (i, r) -> Iv(r.top, r.bottom, i) }.sortedBy { it.s }
        val yOver = mutableSetOf<Pair<Int, Int>>()
        val activeY = mutableListOf<Iv>()
        for (iv in yIvs) {
            activeY.removeAll { it.e < iv.s }
            for (a in activeY) {
                val lo = minOf(a.idx, iv.idx); val hi = maxOf(a.idx, iv.idx)
                yOver.add(lo to hi)
            }
            activeY.add(iv)
        }
        val candidates = xOver intersect yOver
        val toProcess = kept.toMutableList()
        val extended = mutableListOf<Rect>()
        for (i in toProcess.indices) {
            var cur = toProcess[i]
            for (j in toProcess.indices) {
                if (i == j) continue
                val p = minOf(i, j) to maxOf(i, j)
                if (p !in candidates) continue
                val oth = toProcess[j]
                if (qualifiesFor3SidesNearExtend(cur, oth)) {
                    val insides = listOf(oth.left >= cur.left, oth.top >= cur.top, oth.right <= cur.right, oth.bottom <= cur.bottom)
                    val newL = if (!insides[0]) min(cur.left, oth.left) else cur.left
                    val newT = if (!insides[1]) min(cur.top, oth.top) else cur.top
                    val newR = if (!insides[2]) max(cur.right, oth.right) else cur.right
                    val newB = if (!insides[3]) max(cur.bottom, oth.bottom) else cur.bottom
                    var nl = newL; var nr = newR; var nt = newT; var nb = newB
                    if (nl > nr) { val t = nl; nl = nr; nr = t }
                    if (nt > nb) { val t = nt; nt = nb; nb = t }
                    cur = Rect(nl, nt, nr, nb)
                }
            }
            if (extended.none { it == cur }) extended.add(cur)
        }
        val cleaned = extended.filter { b ->
            !extended.any { o -> o != b && o.contains(b) }
        }.filter { it.width() > 0 && it.height() > 0 }.toMutableList()
        redRects.clear()
        redRects.addAll(cleaned)
    }


    fun PumpCostVolUtils.doCrossScaleRedboxFilter(pdHunksRawTotal: MutableList<PumpHunk>, imgW: Int, imgH: Int) {
        if (pdHunksRawTotal.isEmpty()) return
        val rects = hunksToRects(pdHunksRawTotal).toMutableList()
        doCrossScaleRedboxFilterPixel(rects)
        pdHunksRawTotal.clear()
        pdHunksRawTotal.addAll(rectsToHunks(rects))
    }


    fun PumpCostVolUtils.pruneRectsToTopN(
        rects: MutableList<Rect>,
        maxCount: Int = PumpOcrSettings.DEFAULT_MAX_RED_BOXES,
        @Suppress("UNUSED_PARAMETER") imgH: Int = 0,
    ) {
        doCrossScaleRedboxFilterPixel(rects)
        if (rects.size > maxCount) {
            rects.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
            rects.subList(maxCount, rects.size).clear()
        }
    }


    suspend fun PumpCostVolUtils.runDiscoveryPaddle(
        buffer: BufferSet,
        paddleEngine: NativePaddleEngine,
        contentW: Int,
        contentH: Int,
        scale: Int,
        metadata: MutableMap<String, String>? = null,
        boxMode: Int = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
        heatDumpU8z: java.io.File? = null,
        hmThresh: Float = HEAT_THR_U8_GE1,
        maskDilatePasses: Int = 0,
        detTiers: Map<Int, com.baidu.paddle.lite.PaddlePredictor>? = null,
        detTiersInt8: Map<Int, ByteArray>? = null,
    ): List<List<PumpHunk>> {
        val dest = NativePaddleEngine.deskewSetFor(scale)
        val S = dest.width
        val fullW = buffer.p.width
        val fullH = buffer.p.height
        val heatToPhoto =
            max(fullW, fullH).toFloat() / max(contentW, contentH).coerceAtLeast(1).toFloat()
        // copyHeatmap=false: campaign only needs boxes; floatData/getFloatData crashes on uint8 heatmaps
        val res = paddleEngine.detect(
            dest,
            targetW = S,
            targetH = S,
            copyHeatmap = false,
            boxMode = boxMode,
            heatDumpU8z = heatDumpU8z,
            hmThresh = hmThresh,
            maskDilatePasses = maskDilatePasses,
            detTiers = detTiers,
            detTiersInt8 = detTiersInt8,
            heatToPhoto = heatToPhoto,
            photoW = fullW,
            photoH = fullH,
        ) ?: return listOf(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        if (metadata != null) {
            metadata["t_pd_native_post_${scale}"] = res.metadata["t_native_post_ms"] ?: "0"
            metadata["t_pd_inference_${scale}"] = res.metadata["t_inference_ms"] ?: "0"
            // "u8" = thr/CC/AABB|minAreaRect without float heat buffer; "float" = converted path
            metadata["heatmap_post_path_${scale}"] = res.metadata["heatmap_post_path"] ?: "unknown"
            metadata["heatmap_box_mode_${scale}"] = res.metadata["box_mode"] ?: boxMode.toString()
            metadata["hm_thresh_${scale}"] = res.metadata["hm_thresh"] ?: hmThresh.toString()
            metadata["mask_dilate_passes_${scale}"] = res.metadata["mask_dilate_passes"] ?: maskDilatePasses.toString()
            metadata["heatmap_cell_px_${scale}"] = NativeImageUtils.PADDLE_DET_HEAT_CELL_PX.toString()
        }
        val hist = res.heatmapHist ?: IntArray(0)
        if (metadata != null && hist.isNotEmpty()) metadata["heatmap_hist_${scale}"] = JSONArray(hist.toList()).toString()
        val rawRects = res.nativeBoxes.map { box ->
            val p = box.points
            val minX = minOf(p[0], p[2], p[4], p[6]).toInt()
            val minY = minOf(p[1], p[3], p[5], p[7]).toInt()
            val maxX = maxOf(p[0], p[2], p[4], p[6]).toInt()
            val maxY = maxOf(p[1], p[3], p[5], p[7]).toInt()
            Rect(minX, minY, maxX, maxY)
        }
        val hunksDetected = mutableListOf<PumpHunk>()
        rawRects.forEach { r ->
            hunksDetected.add(
                PumpHunk(
                    "",
                    RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()),
                ),
            )
        }
        val nonNestedRects = rawRects.filter { r1 ->
            rawRects.none { r2 -> r1 != r2 && r2.contains(r1.left + 5, r1.top + 5, r1.right - 5, r1.bottom - 5) }
        }
        val consolidated = OdometerOcrUtils.consolidateRects(nonNestedRects, 0.75f)
        val hunksRaw = mutableListOf<PumpHunk>()
        val hunksExpanded = mutableListOf<PumpHunk>()
        val hunksMaxExtent = mutableListOf<PumpHunk>()
        val hunksNative = mutableListOf<PumpHunk>()
        nonNestedRects.forEach { rect ->
            hunksRaw.add(
                PumpHunk(
                    "",
                    RectF(
                        rect.left.toFloat(), rect.top.toFloat(),
                        rect.right.toFloat(), rect.bottom.toFloat(),
                    ),
                ),
            )
        }
        val inv = if (heatToPhoto > 0f) 1f / heatToPhoto else 1f
        consolidated.forEach { rect ->
            val heatRect = Rect(
                (rect.left * inv).toInt(),
                (rect.top * inv).toInt(),
                (rect.right * inv).toInt(),
                (rect.bottom * inv).toInt(),
            )
            val packed = NativeImageUtils.wrapPackedU8(
                (dest.s as BufferSet.Instance).tensorBindRaw(),
                S,
            )
            val (retractedRect, maxExtentRect) = try {
                NativeImageUtils.expandByUniformity(packed, heatRect)
            } finally {
                packed.release()
            }
            hunksExpanded.add(
                PumpHunk(
                    "",
                    RectF(
                        retractedRect.left * heatToPhoto,
                        retractedRect.top * heatToPhoto,
                        retractedRect.right * heatToPhoto,
                        retractedRect.bottom * heatToPhoto,
                    ),
                ),
            )
            hunksMaxExtent.add(
                PumpHunk(
                    "",
                    RectF(
                        maxExtentRect.left * heatToPhoto,
                        maxExtentRect.top * heatToPhoto,
                        maxExtentRect.right * heatToPhoto,
                        maxExtentRect.bottom * heatToPhoto,
                    ),
                ),
            )
        }
        res.nativeBoxes.forEach { box ->
            val p = box.points
            val minX = minOf(p[0], p[2], p[4], p[6])
            val minY = minOf(p[1], p[3], p[5], p[7])
            val maxX = maxOf(p[0], p[2], p[4], p[6])
            val maxY = maxOf(p[1], p[3], p[5], p[7])
            hunksNative.add(
                PumpHunk("Conf: %.2f".format(box.confidence), RectF(minX, minY, maxX, maxY)),
            )
        }
        return listOf(hunksDetected, hunksRaw, hunksExpanded, hunksMaxExtent, hunksNative)
    }


    suspend fun PumpCostVolUtils.ocrPumpRectsAsisAndDigits(
        workspace: BufferSet,
        paddleEngine: NativePaddleEngine,
        recBuffer: BufferSet,
        rects: List<Rect>,
        imgW: Int,
        imgH: Int,
        onRectDone: (suspend (Int, Rect) -> Unit)? = null,
    ): PumpRectOcrLists {
        val asis = ArrayList<String>(rects.size)
        val digits = ArrayList<String>(rects.size)
        val asisProbs = ArrayList<String>(rects.size)
        val digitsProbs = ArrayList<String>(rects.size)
        val recB64 = ArrayList<String>(rects.size)
        val recW = ArrayList<Int>(rects.size)
        val recH = ArrayList<Int>(rects.size)
        for (r in rects) {
            val pW = r.width(); val pH = r.height()
            if (pW < 2 || pH < 2) {
                asis.add("?"); digits.add("?")
                asisProbs.add(""); digitsProbs.add(""); recB64.add("")
                recW.add(0); recH.add(0)
                onRectDone?.invoke(asis.lastIndex, r)
                continue
            }
            val fed = RecBufferFeed.feedSourceBorderHeightStrip(
                workspace, r, imgW, imgH, recBuffer,
                targetH = 48,
                borderPx = RecBufferFeed.DEFAULT_BORDER_PX,
            )
            val snap = snapRecCrop(recBuffer, fed.recCropId, fed.targetW, fed.targetH)
            val asisRes = paddleEngine.recognize(recBuffer.c[fed.recCropId])
            val asisPair = pumpOcrCleanAndProbs(asisRes.debugText, asisRes.perCharProbs)
            val digitsRes = paddleEngine.recognizeNumericDecimal(recBuffer.c[fed.recCropId])
            val digitsPair = pumpOcrCleanAndProbs(digitsRes.debugText, digitsRes.perCharProbs)
            recBuffer.c[fed.recCropId].release()
            asis.add(asisPair.first); asisProbs.add(asisPair.second)
            digits.add(digitsPair.first); digitsProbs.add(digitsPair.second)
            recB64.add(snap)
            recW.add(fed.targetW)
            recH.add(fed.targetH)
            onRectDone?.invoke(asis.lastIndex, r)
        }
        return PumpRectOcrLists(
            asis = asis,
            digits = digits,
            asisProbs = asisProbs,
            digitsProbs = digitsProbs,
            recB64 = recB64,
            recW = recW,
            recH = recH,
        )
    }


    private suspend fun PumpCostVolUtils.setIDiscoveryReds(
        workspace: BufferSet,
        paddleEngine: NativePaddleEngine,
        imgW: Int,
        imgH: Int,
    ): List<PumpHunk> {
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
            prepareScale(workspace, scale)
            val paddleResults = runDiscoveryPaddle(workspace, paddleEngine, targetW, targetH, scale)
            pdHunksRawTotal.addAll(paddleResults[1])
            pdHunksExpTotal.addAll(paddleResults[2])
            pdHunksMaxTotal.addAll(paddleResults[3])
        }
        doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
        doCrossScaleRedboxFilter(pdHunksExpTotal, imgW, imgH)
        doCrossScaleRedboxFilter(pdHunksMaxTotal, imgW, imgH)
        val redPixelList = hunksToRects(pdHunksRawTotal).toMutableList()
        doCrossScaleRedboxFilterPixel(redPixelList)
        pruneRectsToTopN(redPixelList, PumpOcrSettings.DEFAULT_MAX_RED_BOXES, imgH)
        return rectsToHunks(redPixelList)
    }


    private suspend fun PumpCostVolUtils.setIAppendStageOcr(
        workspace: BufferSet,
        paddleEngine: NativePaddleEngine,
        recBuffer: BufferSet,
        reds: List<PumpHunk>,
        vertFactors: List<Float>,
        combinedBluePixel: MutableList<Rect>,
        combinedAsis: MutableList<String>,
        combinedDigits: MutableList<String>,
        combinedAsisProbs: MutableList<String>,
        combinedDigitsProbs: MutableList<String>,
        imgW: Int,
        imgH: Int,
    ) {
        val (customBlue, _) = createBlueAndOrangeHunksFromReds(
            reds, imgW, imgH, vertFactors, SET_G_HORIZ_FACTOR,
        )
        val bluePixel = hunksToRects(customBlue)
        if (bluePixel.isEmpty()) return
        val ocr = ocrPumpRectsAsisAndDigits(
            workspace, paddleEngine, recBuffer, bluePixel, imgW, imgH,
        )
        combinedBluePixel.addAll(bluePixel)
        combinedAsis.addAll(ocr.asis)
        combinedDigits.addAll(ocr.digits)
        combinedAsisProbs.addAll(ocr.asisProbs)
        combinedDigitsProbs.addAll(ocr.digitsProbs)
    }


    suspend fun PumpCostVolUtils.runSetICostVolExtraction(
        workspace: BufferSet,
        paddleEngine: NativePaddleEngine,
        recBuffer: BufferSet,
        imgW: Int,
        imgH: Int,
    ): CostVolClassifyResult {
        val na = CostVolClassifyResult("N/A", "N/A", RedBoxOcrCandidate("", "", ""), RedBoxOcrCandidate("", "", ""))
        val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p)
        val tilt = -deskewRes.paddleCppAngle
        OdometerOcrUtils.rotate(workspace, tilt)

        val combinedBluePixel = mutableListOf<Rect>()
        val combinedAsis = mutableListOf<String>()
        val combinedDigits = mutableListOf<String>()
        val combinedAsisProbs = mutableListOf<String>()
        val combinedDigitsProbs = mutableListOf<String>()

        // G stage on post-deskew raw
        var lastReds = setIDiscoveryReds(workspace, paddleEngine, imgW, imgH)
        setIAppendStageOcr(
            workspace, paddleEngine, recBuffer, lastReds, SET_I_G_VERT,
            combinedBluePixel, combinedAsis, combinedDigits, combinedAsisProbs, combinedDigitsProbs,
            imgW, imgH,
        )

        // Clip stretch + adjusted valley/peak grays
        val (valleyGrays, peakGrays) = OdometerOcrUtils.getValleyPeakGrays(workspace.p.mat)
        val (intensityLow, intensityHigh) = OdometerOcrUtils.getClipStretchLowHigh(workspace.p.mat)
        OdometerOcrUtils.applyContrastStretch(workspace.p.mat, intensityLow, intensityHigh)
        val stretchSpan = intensityHigh - intensityLow
        fun adjustGrayForStretch(g: Int): Int =
            if (stretchSpan > 0) ((g - intensityLow) * 255.0 / stretchSpan).toInt().coerceIn(0, 255) else g
        val adjustedValleyGrays = valleyGrays.map { adjustGrayForStretch(it) }
        val adjustedPeakGrays = peakGrays.map { adjustGrayForStretch(it) }

        // D stage
        lastReds = setIDiscoveryReds(workspace, paddleEngine, imgW, imgH)
        setIAppendStageOcr(
            workspace, paddleEngine, recBuffer, lastReds, SET_I_D_VERT,
            combinedBluePixel, combinedAsis, combinedDigits, combinedAsisProbs, combinedDigitsProbs,
            imgW, imgH,
        )

        // Valley push with adjusted grays
        OdometerOcrUtils.applyValleyPushWithGrays(workspace.p.mat, adjustedValleyGrays, adjustedPeakGrays)

        // E stage
        lastReds = setIDiscoveryReds(workspace, paddleEngine, imgW, imgH)
        setIAppendStageOcr(
            workspace, paddleEngine, recBuffer, lastReds, SET_I_E_VERT,
            combinedBluePixel, combinedAsis, combinedDigits, combinedAsisProbs, combinedDigitsProbs,
            imgW, imgH,
        )

        if (combinedBluePixel.isEmpty()) return na
        val allCands = buildRedBoxCandidates(
            combinedBluePixel, combinedAsis, combinedDigits,
            combinedAsisProbs, combinedDigitsProbs,
        )
        return classifyCostVolFromBoxOcr(allCands)
    }

