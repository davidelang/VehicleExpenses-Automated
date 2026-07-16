package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/** Pixel coordinates in full workspace/photo space for pump OCR regions. */
data class PumpHunk(val text: String, val rect: RectF)

data class PumpRectOcrLists(
    val asis: List<String>,
    val digits: List<String>,
    val asisProbs: List<String> = emptyList(),
    val digitsProbs: List<String> = emptyList()
)

data class RedBoxOcrCandidate(
    val label: String,
    val asis: String,
    val digits: String,
    val asisProbs: String = "",
    val digitsProbs: String = "",
    val rect: Rect? = null
)

data class CostVolClassifyResult(
    val cost: String,
    val vol: String,
    val costCand: RedBoxOcrCandidate,
    val volCand: RedBoxOcrCandidate
)

data class PathResult(val cost: String, val vol: String, val costB64: String, val volB64: String)

/**
 * Set G / G- / G-- calculated blue expansion vert-factor lists.
 * From 2026-07-11 dual-device shared G reduce chart (Phone+Emulator cand valid).
 * G = k=8 0-loss keep. G- = k=6. G-- = k=4 (also Quick Fill live path). Horiz 50%.
 */
val SET_G_VERT_FACTORS: List<Float> = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.6f, 1.1f, 1.3f, 1.7f)
/** Set G- = shared reduce k=6; phone loss 2 / emu loss 2 vs full cand */
val SET_G_MINUS_VERT_FACTORS: List<Float> = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.6f, 1.3f)
/** Set G-- = shared reduce k=4; Quick Fill uses this; phone loss 4 / emu loss 5 vs full cand */
val SET_G_MINUS_MINUS_VERT_FACTORS: List<Float> = listOf(0.1f, 0.3f, 0.4f, 1.1f)
const val SET_G_HORIZ_FACTOR: Float = 0.5f

private const val TAG = "PumpCostVolUtils"

object PumpCostVolUtils {

    fun pumpOcrCleanAndProbs(debugText: String, perCharProbs: String): Pair<String, String> {
        val cleanText = debugText
        val probStr = if (perCharProbs.isNotEmpty()) perCharProbs else ""
        return cleanText to probStr
    }

    fun cleanDecimal(s: String): String {
        var t = s.trim()
        while (t.startsWith(".")) t = t.substring(1)
        while (t.endsWith(".")) t = t.substring(0, t.length - 1)
        return t
    }

    fun hasBadInternalDecimals(s: String): Boolean = cleanDecimal(s).count { it == '.' } >= 2

    fun probCorrectness(p: String): Float {
        if (p.isEmpty()) return 0.5f
        val vals = p.split(",").mapNotNull { part ->
            val colon = part.indexOf(':')
            if (colon < 0) null else part.substring(colon + 1).trim().toFloatOrNull()
        }
        return if (vals.isEmpty()) 0.5f else vals.average().toFloat()
    }

    fun yOverlapHeight(a: Rect, b: Rect): Int {
        val interTop = maxOf(a.top, b.top)
        val interBottom = minOf(a.bottom, b.bottom)
        return maxOf(0, interBottom - interTop)
    }

    fun significantYOverlap(preferred: Rect, other: Rect): Boolean {
        val overlap = yOverlapHeight(preferred, other)
        val prefH = preferred.height().coerceAtLeast(1)
        return overlap > prefH * 0.5f
    }

    fun repairDecimalForRole(clean: String, role: String): String {
        if ("." in clean) return clean
        val dstr = clean.filter { it.isDigit() }
        if (role == "cost" && dstr.length >= 3) {
            val n = dstr.length
            return dstr.substring(0, n - 2) + "." + dstr.substring(n - 2)
        }
        if (role == "vol" && dstr.length >= 4) {
            val n = dstr.length
            return dstr.substring(0, n - 3) + "." + dstr.substring(n - 3)
        }
        return clean
    }

    fun buildRedBoxCandidates(
        boxRects: List<Rect>,
        asisList: List<String>,
        digitsList: List<String>,
        asisProbsList: List<String> = emptyList(),
        digitsProbsList: List<String> = emptyList(),
        /** Label prefix: "Blue" / "Orange" / "Red" (legacy). */
        labelPrefix: String = "Red",
    ): List<RedBoxOcrCandidate> {
        val n = minOf(boxRects.size, asisList.size, digitsList.size)
        return (0 until n).map { i ->
            RedBoxOcrCandidate(
                "$labelPrefix${i + 1}",
                asisList[i],
                digitsList[i],
                asisProbsList.getOrElse(i) { "" },
                digitsProbsList.getOrElse(i) { "" },
                boxRects[i]
            )
        }
    }

    /** Band-gated role pools (role_band); falls back to global pair when bands do not lock. */
    fun classifyCostVolFromBoxOcr(
        candidates: List<RedBoxOcrCandidate>,
        ratioBandLo: Float = PumpOcrSettings.DEFAULT_RATIO_BAND_LO,
        ratioBandHi: Float = PumpOcrSettings.DEFAULT_RATIO_BAND_HI,
        labelYBandExtraFraction: Float = PumpOcrSettings.DEFAULT_LABEL_Y_BAND_EXTRA_FRACTION,
    ): CostVolClassifyResult = PumpRoleBandClassifier.classify(
        candidates,
        ratioBandLo,
        ratioBandHi,
        labelYBandExtraFraction,
    )

    fun classifyCostVolFromBoxOcr(
        context: Context,
        candidates: List<RedBoxOcrCandidate>,
    ): CostVolClassifyResult = classifyCostVolFromBoxOcr(
        candidates,
        PumpOcrSettings.ratioBandLo(context),
        PumpOcrSettings.ratioBandHi(context),
        PumpOcrSettings.labelYBandExtraFraction(context),
    )

    fun createBlueAndOrangeHunksFromReds(
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

    fun rectToJson(r: Rect): JSONObject =
        JSONObject().put("l", r.left).put("t", r.top).put("r", r.right).put("b", r.bottom)

    fun redBoxOcrCandidateToJson(c: RedBoxOcrCandidate): JSONObject {
        val j = JSONObject()
            .put("label", c.label)
            .put("asis", c.asis)
            .put("digits", c.digits)
            .put("asisProbs", c.asisProbs)
            .put("digitsProbs", c.digitsProbs)
        c.rect?.let { j.put("rect", rectToJson(it)) }
        return j
    }

    fun buildCostVolDecisionDataJson(
        reds: List<Rect>,
        ocrSourceRects: List<Rect>,
        candidates: List<RedBoxOcrCandidate>,
        costCand: RedBoxOcrCandidate,
        volCand: RedBoxOcrCandidate,
        finalCost: String,
        finalVol: String,
        assembly: Map<String, Any?> = emptyMap(),
        oranges: List<Rect> = emptyList()
    ): String {
        val redsArr = JSONArray()
        reds.forEach { redsArr.put(rectToJson(it)) }
        val ocrArr = JSONArray()
        ocrSourceRects.forEach { ocrArr.put(rectToJson(it)) }
        val candsArr = JSONArray()
        candidates.forEach { candsArr.put(redBoxOcrCandidateToJson(it)) }
        val chosen = JSONObject()
            .put("cost", redBoxOcrCandidateToJson(costCand))
            .put("vol", redBoxOcrCandidateToJson(volCand))
        val finalObj = JSONObject()
            .put("cost", finalCost)
            .put("vol", finalVol)
        val assemblyObj = JSONObject()
        assembly.forEach { (k, v) ->
            when (v) {
                is List<*> -> {
                    val arr = JSONArray()
                    v.forEach { item -> arr.put(item) }
                    assemblyObj.put(k, arr)
                }
                else -> assemblyObj.put(k, v)
            }
        }
        val orangesArr = JSONArray()
        oranges.forEach { orangesArr.put(rectToJson(it)) }
        return JSONObject()
            .put("reds", redsArr)
            .put("ocrSourceRects", ocrArr)
            .put("candidates", candsArr)
            .put("chosen", chosen)
            .put("final", finalObj)
            .put("assembly", assemblyObj)
            .put("oranges", orangesArr)
            .toString()
    }

    fun hunkToRect(h: PumpHunk): Rect =
        Rect(h.rect.left.toInt(), h.rect.top.toInt(), h.rect.right.toInt(), h.rect.bottom.toInt())

    fun hunksToRects(hunks: List<PumpHunk>): List<Rect> = hunks.map { hunkToRect(it) }

    fun rectsToHunks(rects: List<Rect>): List<PumpHunk> =
        rects.map { r -> PumpHunk("", RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())) }

    fun qualifiesFor3SidesNearExtend(cR: Rect, oR: Rect): Boolean {
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

    fun doCrossScaleRedboxFilterPixel(redRects: MutableList<Rect>) {
        if (redRects.isEmpty()) return
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

    fun doCrossScaleRedboxFilter(pdHunksRawTotal: MutableList<PumpHunk>, imgW: Int, imgH: Int) {
        if (pdHunksRawTotal.isEmpty()) return
        val kept = mutableListOf<PumpHunk>()
        for (h1 in pdHunksRawTotal) {
            val r1 = hunkToRect(h1)
            val isContained = kept.any { h2 ->
                h1 !== h2 && run {
                    val r2 = hunkToRect(h2)
                    r2.contains(r1.left, r1.top, r1.right, r1.bottom)
                }
            }
            if (!isContained) kept.add(h1)
        }
        val toProcess = kept.toMutableList()
        val extended = mutableListOf<PumpHunk>()
        for (i in toProcess.indices) {
            var cur = toProcess[i]
            for (j in toProcess.indices) {
                if (i == j) continue
                val oth = toProcess[j]
                val cR = hunkToRect(cur)
                val oR = hunkToRect(oth)
                val insides = listOf(oR.left >= cR.left, oR.top >= cR.top, oR.right <= cR.right, oR.bottom <= cR.bottom)
                if (qualifiesFor3SidesNearExtend(cR, oR)) {
                    val newL = if (!insides[0]) min(cur.rect.left, oth.rect.left) else cur.rect.left
                    val newT = if (!insides[1]) min(cur.rect.top, oth.rect.top) else cur.rect.top
                    val newR = if (!insides[2]) max(cur.rect.right, oth.rect.right) else cur.rect.right
                    val newB = if (!insides[3]) max(cur.rect.bottom, oth.rect.bottom) else cur.rect.bottom
                    var nl = newL; var nr = newR; var nt = newT; var nb = newB
                    if (nl > nr) { val t = nl; nl = nr; nr = t }
                    if (nt > nb) { val t = nt; nt = nb; nb = t }
                    cur = PumpHunk(cur.text, RectF(nl, nt, nr, nb))
                }
            }
            if (extended.none { it.rect == cur.rect }) extended.add(cur)
        }
        val cleaned = extended.filter { b ->
            val bR = hunkToRect(b)
            !extended.any { o ->
                if (o === b) false else {
                    val oR = hunkToRect(o)
                    oR.contains(bR)
                }
            }
        }.toMutableList()
        pdHunksRawTotal.clear()
        pdHunksRawTotal.addAll(cleaned)
    }

    fun pruneRectsToTopN(
        rects: MutableList<Rect>,
        maxCount: Int = PumpOcrSettings.DEFAULT_MAX_RED_BOXES,
    ) {
        doCrossScaleRedboxFilterPixel(rects)
        if (rects.size > maxCount) {
            rects.sortByDescending { r -> (r.right - r.left) * (r.bottom - r.top) }
            rects.subList(maxCount, rects.size).clear()
        }
    }

    fun prepareScale(buffer: BufferSet, targetLongEdge: Int): Pair<Int, Int> {
        val srcW = buffer.p.width
        val srcH = buffer.p.height
        val currentLongEdge = max(srcW, srcH)
        val scale = if (currentLongEdge <= targetLongEdge) 1.0f else targetLongEdge.toFloat() / currentLongEdge
        val targetW = (srcW * scale).toInt()
        val targetH = (srcH * scale).toInt()
        val alignedW = ((targetW + 31) / 32) * 32
        val alignedH = ((targetH + 31) / 32) * 32
        Log.d(TAG, "prepareScale: target=$targetLongEdge -> ${targetW}x${targetH} (Aligned: ${alignedW}x${alignedH})")
        val outerId = buffer.s.createCrop(0, 0, alignedW, alignedH)
        buffer.c[outerId].clear()
        val innerId = buffer.s.createCrop(0, 0, targetW, targetH)
        Imgproc.resize(buffer.p.mat, buffer.c[innerId].mat, buffer.c[innerId].mat.size(), 0.0, 0.0, Imgproc.INTER_AREA)
        return Pair(outerId, innerId)
    }

    suspend fun runDiscoveryPaddle(
        buffer: BufferSet,
        id: Int,
        paddleEngine: NativePaddleEngine,
        contentW: Int,
        contentH: Int,
        scale: Int,
        metadata: MutableMap<String, String>? = null
    ): List<List<PumpHunk>> {
        // copyHeatmap=false: campaign only needs boxes; floatData/getFloatData crashes on uint8 heatmaps
        val res = paddleEngine.detect(buffer.c[id], copyHeatmap = false)
            ?: return listOf(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        if (metadata != null) {
            metadata["t_pd_native_post_${scale}"] = res.metadata["t_native_post_ms"] ?: "0"
            metadata["t_pd_inference_${scale}"] = res.metadata["t_inference_ms"] ?: "0"
        }
        val masterW = buffer.c[id].width
        val masterH = buffer.c[id].height
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
        val fullW = buffer.p.width
        val fullH = buffer.p.height
        rawRects.forEach { r ->
            val ml = r.left.toInt().coerceIn(0, masterW - 1)
            val mt = r.top.toInt().coerceIn(0, masterH - 1)
            val mr = r.right.toInt().coerceIn(0, masterW - 1)
            val mb = r.bottom.toInt().coerceIn(0, masterH - 1)
            val fl = ml * fullW.toFloat() / contentW
            val ft = mt * fullH.toFloat() / contentH
            val fr = mr * fullW.toFloat() / contentW
            val fb = mb * fullH.toFloat() / contentH
            hunksDetected.add(PumpHunk("", RectF(fl, ft, fr, fb)))
        }
        val expandedRects = rawRects.map { r ->
            Rect(
                (r.left - 1).coerceAtLeast(0),
                (r.top - 1).coerceAtLeast(0),
                (r.right + 1).coerceAtMost(masterW - 1),
                (r.bottom + 1).coerceAtMost(masterH - 1)
            )
        }
        val nonNestedRects = expandedRects.filter { r1 ->
            expandedRects.none { r2 -> r1 != r2 && r2.contains(r1.left + 5, r1.top + 5, r1.right - 5, r1.bottom - 5) }
        }
        val consolidated = OdometerOcrUtils.consolidateRects(nonNestedRects, 0.75f)
        val hunksRaw = mutableListOf<PumpHunk>()
        val hunksExpanded = mutableListOf<PumpHunk>()
        val hunksMaxExtent = mutableListOf<PumpHunk>()
        val hunksNative = mutableListOf<PumpHunk>()
        nonNestedRects.forEach { rect ->
            val ml = rect.left.toInt().coerceIn(0, masterW - 1)
            val mt = rect.top.toInt().coerceIn(0, masterH - 1)
            val mr = rect.right.toInt().coerceIn(0, masterW - 1)
            val mb = rect.bottom.toInt().coerceIn(0, masterH - 1)
            val fl = ml * fullW.toFloat() / contentW
            val ft = mt * fullH.toFloat() / contentH
            val fr = mr * fullW.toFloat() / contentW
            val fb = mb * fullH.toFloat() / contentH
            hunksRaw.add(PumpHunk("", RectF(fl, ft, fr, fb)))
        }
        consolidated.forEach { rect ->
            val ml = rect.left.toInt().coerceIn(0, masterW - 1)
            val mt = rect.top.toInt().coerceIn(0, masterH - 1)
            val mr = rect.right.toInt().coerceIn(0, masterW - 1)
            val mb = rect.bottom.toInt().coerceIn(0, masterH - 1)
            val rawRect = Rect(ml, mt, mr, mb)
            val (retractedRect, maxExtentRect) = NativeImageUtils.expandByUniformity(buffer.c[id].mat, rawRect)
            val fl = retractedRect.left * fullW.toFloat() / contentW
            val ft = retractedRect.top * fullH.toFloat() / contentH
            val fr = retractedRect.right * fullW.toFloat() / contentW
            val fb = retractedRect.bottom * fullH.toFloat() / contentH
            hunksExpanded.add(PumpHunk("", RectF(fl, ft, fr, fb)))
            val yfl = maxExtentRect.left * fullW.toFloat() / contentW
            val yft = maxExtentRect.top * fullH.toFloat() / contentH
            val yfr = maxExtentRect.right * fullW.toFloat() / contentW
            val yfb = maxExtentRect.bottom * fullH.toFloat() / contentH
            hunksMaxExtent.add(PumpHunk("", RectF(yfl, yft, yfr, yfb)))
        }
        res.nativeBoxes.forEach { box ->
            val scaleX = fullW.toFloat() / contentW
            val scaleY = fullH.toFloat() / contentH
            var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
            box.points.toList().chunked(2).forEach { (px, py) ->
                val sx = px * scaleX; val sy = py * scaleY
                if (sx < minX) minX = sx; if (sx > maxX) maxX = sx
                if (sy < minY) minY = sy; if (sy > maxY) maxY = sy
            }
            hunksNative.add(PumpHunk("Conf: %.2f".format(box.confidence), RectF(minX, minY, maxX, maxY)))
        }
        return listOf(hunksDetected, hunksRaw, hunksExpanded, hunksMaxExtent, hunksNative)
    }

    suspend fun ocrPumpRectsAsisAndDigits(
        workspace: BufferSet,
        paddleEngine: NativePaddleEngine,
        recBuffer: BufferSet,
        rects: List<Rect>,
        imgW: Int,
        imgH: Int
    ): PumpRectOcrLists {
        val asisPairs = rects.map { r ->
            val pW = r.width(); val pH = r.height()
            if (pW < 2 || pH < 2) "?" to "" else {
                val l = r.left.coerceIn(0, imgW - 1)
                val t = r.top.coerceIn(0, imgH - 1)
                val rr = r.right.coerceIn(l + 1, imgW)
                val bb = r.bottom.coerceIn(t + 1, imgH)
                val cropId = workspace.createCrop(l, t, rr - l, bb - t)
                val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                recBuffer.p.clear()
                val recCropId = recBuffer.createCrop(4, 4, targetW, targetH)
                val interp = if (pW > targetW) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
                Imgproc.resize(workspace.c[cropId].mat, recBuffer.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                val res = paddleEngine.recognize(recBuffer.c[recCropId])
                recBuffer.c[recCropId].release(); workspace.c[cropId].release()
                pumpOcrCleanAndProbs(res.debugText, res.perCharProbs)
            }
        }
        val digitsPairs = rects.map { rp ->
            val pW = rp.width(); val pH = rp.height()
            if (pW < 2 || pH < 2) "?" to "" else {
                val l = rp.left.coerceIn(0, imgW - 1)
                val t = rp.top.coerceIn(0, imgH - 1)
                val rr = rp.right.coerceIn(l + 1, imgW)
                val bb = rp.bottom.coerceIn(t + 1, imgH)
                val cropId = workspace.createCrop(l, t, rr - l, bb - t)
                val targetH = 48; val scale = 48f / pH; val rawW = (pW * scale).toInt()
                val targetW = ((rawW + 31) / 32 * 32).coerceAtMost(320)
                recBuffer.p.clear()
                val recCropId = recBuffer.createCrop(4, 4, targetW, targetH)
                val interp = if (pW > targetW) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
                Imgproc.resize(workspace.c[cropId].mat, recBuffer.c[recCropId].mat, org.opencv.core.Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, interp)
                val res = paddleEngine.recognizeNumericDecimal(recBuffer.c[recCropId])
                recBuffer.c[recCropId].release(); workspace.c[cropId].release()
                pumpOcrCleanAndProbs(res.debugText, res.perCharProbs)
            }
        }
        return PumpRectOcrLists(
            asis = asisPairs.map { it.first },
            digits = digitsPairs.map { it.first },
            asisProbs = asisPairs.map { it.second },
            digitsProbs = digitsPairs.map { it.second }
        )
    }

    data class SetGRunDetail(
        val result: CostVolClassifyResult,
        val angleDeg: Float,
        val redBoxes: List<Rect>,
        val blueBoxes: List<Rect>,
        /** Orange calculated boxes (horizontal expand of blues); OCR logged for analysis. */
        val orangeBoxes: List<Rect> = emptyList(),
        /** t_deskew_ms, t_redbox_ms, t_rec_ms, t_rec_blue_ms, t_rec_orange_ms, t_total_ms */
        val timingsMs: Map<String, Long>,
        /**
         * Blue OCR candidates used for cost/vol classification (labels Blue1..).
         */
        val ocrCandidates: List<RedBoxOcrCandidate> = emptyList(),
        /**
         * Orange OCR candidates (labels Orange1..) — logged so we can see if GT
         * strings appear on orange crops even when blue classification misses.
         */
        val ocrOrangeCandidates: List<RedBoxOcrCandidate> = emptyList(),
    )

    /**
     * Set G ("none, calculated") cost/volume extraction: multi-scale red discovery,
     * cross-scale filter, prune to top N red boxes (see [PumpOcrSettings.DEFAULT_MAX_RED_BOXES]),
     * calculated blue expansion, OCR, classify.
     */
    suspend fun runSetGCostVolExtraction(
        workspace: BufferSet,
        paddleEngine: NativePaddleEngine,
        recBuffer: BufferSet,
        imgW: Int,
        imgH: Int
    ): CostVolClassifyResult = runSetGCostVolExtractionDetailed(workspace, paddleEngine, recBuffer, imgW, imgH).result

    suspend fun runSetGCostVolExtractionDetailed(
        workspace: BufferSet,
        paddleEngine: NativePaddleEngine,
        recBuffer: BufferSet,
        imgW: Int,
        imgH: Int
    ): SetGRunDetail {
        val na = CostVolClassifyResult("N/A", "N/A", RedBoxOcrCandidate("", "", ""), RedBoxOcrCandidate("", "", ""))
        val tTotal0 = System.currentTimeMillis()

        NativePaddleEngine.heartbeat("deskew_begin ${imgW}x$imgH")
        val tDeskew0 = System.currentTimeMillis()
        val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p)
        val tilt = -deskewRes.paddleCppAngle
        OdometerOcrUtils.rotate(workspace, tilt)
        val tDeskew = System.currentTimeMillis() - tDeskew0
        NativePaddleEngine.heartbeat("deskew_done ms=$tDeskew angle=$tilt")

        val tRed0 = System.currentTimeMillis()
        val scales = listOf(224, 608, 1024)
        val pdHunksRawTotal = mutableListOf<PumpHunk>()
        val pdHunksExpTotal = mutableListOf<PumpHunk>()
        val pdHunksMaxTotal = mutableListOf<PumpHunk>()

        scales.forEach { scale ->
            NativePaddleEngine.heartbeat("det_scale_begin scale=$scale")
            val srcW = workspace.p.width
            val srcH = workspace.p.height
            val currentLongEdge = max(srcW, srcH)
            val scaleFactor = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
            val targetW = (srcW * scaleFactor).toInt()
            val targetH = (srcH * scaleFactor).toInt()
            val (outerId, innerId) = prepareScale(workspace, scale)
            val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH, scale)
            pdHunksRawTotal.addAll(paddleResults[1])
            pdHunksExpTotal.addAll(paddleResults[2])
            pdHunksMaxTotal.addAll(paddleResults[3])
            workspace.c[innerId].release()
            workspace.c[outerId].release()
            NativePaddleEngine.heartbeat("det_scale_done scale=$scale")
        }

        NativePaddleEngine.heartbeat("redbox_filter_begin")
        doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
        doCrossScaleRedboxFilter(pdHunksExpTotal, imgW, imgH)
        doCrossScaleRedboxFilter(pdHunksMaxTotal, imgW, imgH)

        val redPixelList = hunksToRects(pdHunksRawTotal).toMutableList()
        pruneRectsToTopN(redPixelList, PumpOcrSettings.DEFAULT_MAX_RED_BOXES)
        pdHunksRawTotal.clear()
        pdHunksRawTotal.addAll(rectsToHunks(redPixelList))
        val tRed = System.currentTimeMillis() - tRed0

        if (pdHunksRawTotal.isEmpty()) {
            return SetGRunDetail(
                na, tilt, emptyList(), emptyList(), emptyList(),
                mapOf(
                    "t_deskew_ms" to tDeskew, "t_redbox_ms" to tRed,
                    "t_rec_ms" to 0L, "t_rec_blue_ms" to 0L, "t_rec_orange_ms" to 0L,
                    "t_total_ms" to (System.currentTimeMillis() - tTotal0),
                ),
                ocrCandidates = emptyList(),
                ocrOrangeCandidates = emptyList(),
            )
        }

        val (customBlueGPre, customOrangeGPre) =
            createBlueAndOrangeHunksFromReds(pdHunksRawTotal, imgW, imgH)
        val customBluePixelG = hunksToRects(customBlueGPre)
        val customOrangePixelG = hunksToRects(customOrangeGPre)
        if (customBluePixelG.isEmpty()) {
            return SetGRunDetail(
                na, tilt, redPixelList, emptyList(), customOrangePixelG,
                mapOf(
                    "t_deskew_ms" to tDeskew, "t_redbox_ms" to tRed,
                    "t_rec_ms" to 0L, "t_rec_blue_ms" to 0L, "t_rec_orange_ms" to 0L,
                    "t_total_ms" to (System.currentTimeMillis() - tTotal0),
                ),
                ocrCandidates = emptyList(),
                ocrOrangeCandidates = emptyList(),
            )
        }

        // Dual OCR: blue crops drive classification (parity with prior path);
        // orange crops are logged so analysis can see if GT digits appear there.
        NativePaddleEngine.heartbeat(
            "rec_begin n_blue=${customBluePixelG.size} n_orange=${customOrangePixelG.size}"
        )
        val tRec0 = System.currentTimeMillis()
        val ocrBlue = ocrPumpRectsAsisAndDigits(
            workspace, paddleEngine, recBuffer, customBluePixelG, imgW, imgH
        )
        val blueCands = buildRedBoxCandidates(
            customBluePixelG, ocrBlue.asis, ocrBlue.digits,
            ocrBlue.asisProbs, ocrBlue.digitsProbs,
            labelPrefix = "Blue",
        )
        val tRecBlue = System.currentTimeMillis() - tRec0
        val tOrange0 = System.currentTimeMillis()
        val ocrOrange = if (customOrangePixelG.isNotEmpty()) {
            ocrPumpRectsAsisAndDigits(
                workspace, paddleEngine, recBuffer, customOrangePixelG, imgW, imgH
            )
        } else {
            PumpRectOcrLists(emptyList(), emptyList(), emptyList(), emptyList())
        }
        val orangeCands = buildRedBoxCandidates(
            customOrangePixelG, ocrOrange.asis, ocrOrange.digits,
            ocrOrange.asisProbs, ocrOrange.digitsProbs,
            labelPrefix = "Orange",
        )
        val tRecOrange = System.currentTimeMillis() - tOrange0
        // Classification unchanged: blue OCR only.
        val result = classifyCostVolFromBoxOcr(blueCands)
        val tRec = System.currentTimeMillis() - tRec0
        NativePaddleEngine.heartbeat(
            "rec_done ms=$tRec blue_ms=$tRecBlue orange_ms=$tRecOrange"
        )

        return SetGRunDetail(
            result = result,
            angleDeg = tilt,
            redBoxes = redPixelList,
            blueBoxes = customBluePixelG,
            orangeBoxes = customOrangePixelG,
            timingsMs = mapOf(
                "t_deskew_ms" to tDeskew,
                "t_redbox_ms" to tRed,
                "t_rec_ms" to tRec,
                "t_rec_blue_ms" to tRecBlue,
                "t_rec_orange_ms" to tRecOrange,
                "t_total_ms" to (System.currentTimeMillis() - tTotal0),
            ),
            ocrCandidates = blueCands,
            ocrOrangeCandidates = orangeCands,
        )
    }
}