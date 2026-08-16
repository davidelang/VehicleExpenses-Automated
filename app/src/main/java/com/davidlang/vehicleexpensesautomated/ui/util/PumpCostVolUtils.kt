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
    val digitsProbs: List<String> = emptyList(),
    /** JPEG preview of the 48×W crop actually fed to recognize (same crop for asis and digits). */
    val recB64: List<String> = emptyList(),
    val recW: List<Int> = emptyList(),
    val recH: List<Int> = emptyList(),
)

data class RedBoxOcrCandidate(
    val label: String,
    val asis: String,
    val digits: String,
    val asisProbs: String = "",
    val digitsProbs: String = "",
    val rect: Rect? = null,
    val recB64: String = "",
    val recW: Int = 0,
    val recH: Int = 0,
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
 * G = k=8 0-loss keep. G- = k=6. G-- = k=4 (experiment product-det column). Horiz 50%.
 */
val SET_G_VERT_FACTORS: List<Float> = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.6f, 1.1f, 1.3f, 1.7f)
/** Set G- = shared reduce k=6; phone loss 2 / emu loss 2 vs full cand */
val SET_G_MINUS_VERT_FACTORS: List<Float> = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.6f, 1.3f)
/** Set G-- = shared reduce k=4; experiment product-det column; phone loss 4 / emu loss 5 vs full cand */
val SET_G_MINUS_MINUS_VERT_FACTORS: List<Float> = listOf(0.1f, 0.3f, 0.4f, 1.1f)
/**
 * Experiment-only dense vertical expansion for Set G-dense / Set K.
 * v → blue height multiple (1+2v).
 * **Below 1.0:** fine grid 0…0.8 step 0.1, plus 0.25 and 0.75 (continuity with prior runs).
 * **≥1.0:** keep previous coarse steps (1.0…2.5 / 0.25) so large-pad cases are not dropped.
 * Not for Quick Fill.
 */
val SET_G_DENSE_VERT_FACTORS: List<Float> = listOf(
    // fine low band
    0.0f, 0.1f, 0.2f, 0.25f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.75f, 0.8f,
    // keep prior high band
    1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f,
)
/**
 * Set G4 live verts. After v0.98-212 167×2 combo cover of the 0–0.3 union:
 * 0+0.1+0.3 misses 1 phone / 2 tablet vs that union; 0.2 and 0.25 add almost
 * nothing on top. Half-glyph leftovers still need v>0.3 and are not targeted.
 */
val SET_G4_VERT_FACTORS: List<Float> = listOf(0.0f, 0.1f, 0.3f)
/**
 * Energy G-on-cap verts (m65 / gx / xycut / P4-rot / Prod-rot). v0.98-230
 * sweep first-unique clustered at 0.00 / 0.05 / 0.15. Used only when energy
 * hits maxFrac — not a per-seed sweep. P4-jump still uses G4 0/0.1/0.3.
 */
val SET_M65_CAP_VERT_FACTORS: List<Float> = listOf(0.0f, 0.05f, 0.15f)
/** Production / G-- horizontal pad as fraction of *expanded blue height* (each side). */
const val SET_G_HORIZ_FACTOR: Float = 0.5f
/**
 * Experiment G-dense / K: **2×** [SET_G_HORIZ_FACTOR] (each side = full expanded blue height).
 * May pull in display bezel noise (often OCR'd as a leading/trailing **1**); watch false extras.
 */
const val SET_G_DENSE_HORIZ_FACTOR: Float = 1.0f

/**
 * Horiz-reach A/B campaign: each side pad = factor × expanded blue height.
 * Fixed verts ([SET_G_MINUS_MINUS_VERT_FACTORS]) so only horiz varies.
 * Source analysis: phone G-dense horiz 0.5 (07-23-32) vs 1.0 (09-25-33) on shared verts.
 */
val SET_HORIZ_REACH_FACTORS: List<Float> = listOf(
    0.0f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f,
)

/**
 * Photos where exact-pool min_v changed between horiz 0.5 and 1.0 (helped or regressed),
 * phone full-run pair 2026-08-08 (shared verts only). Used by Experiment Pump
 * "Horiz-affected" subset button (~76 images).
 * See dev-ai-interaction/latest-report/horiz_reach_affected_images_20260808.json.
 */
val HORIZ_REACH_AFFECTED_FILENAMES: List<String> = listOf(
    "PXL_20220701_020625793.dng",
    "PXL_20221121_195449335.jpg",
    "PXL_20221126_210421897.jpg",
    "PXL_20221128_172956178.jpg",
    "PXL_20221221_210212750.dng",
    "PXL_20230101_055935720.dng",
    "PXL_20230113_231616307.dng",
    "PXL_20230225_040459673.dng",
    "PXL_20230318_232827961.jpg",
    "PXL_20230414_023123861.dng",
    "PXL_20230520_194628805.dng",
    "PXL_20230625_225655795.dng",
    "PXL_20231008_022308667.jpg",
    "PXL_20231120_002742785.dng",
    "PXL_20231120_210527402.dng",
    "PXL_20231211_004047524.jpg",
    "PXL_20231221_210417588.jpg",
    "PXL_20231221_213627643.jpg",
    "PXL_20231226_184548623.jpg",
    "PXL_20240131_013430374.jpg",
    "PXL_20240228_211544792.jpg",
    "PXL_20240325_035731504.jpg",
    "PXL_20240326_014922448.jpg",
    "PXL_20240521_025057693.jpg",
    "PXL_20240608_005034875.jpg",
    "PXL_20240708_222637707.jpg",
    "PXL_20240718_000403216.jpg",
    "PXL_20240808_211542775.jpg",
    "PXL_20241104_014027473.jpg",
    "PXL_20241130_183108905.jpg",
    "PXL_20241202_143338144.jpg",
    "PXL_20241213_220345190.jpg",
    "PXL_20250101_020218807.jpg",
    "PXL_20250224_001547856.jpg",
    "PXL_20250303_172259346.jpg",
    "PXL_20250408_223113314.jpg",
    "PXL_20250415_213030478.jpg",
    "PXL_20250425_030838626.jpg",
    "PXL_20250426_024053319.jpg",
    "PXL_20250426_042852976.jpg",
    "PXL_20250426_084222634.jpg",
    "PXL_20250501_160616426.jpg",
    "PXL_20250516_042722105.jpg",
    "PXL_20250528_213707194.jpg",
    "PXL_20250617_052502070.jpg",
    "PXL_20250726_211343400.jpg",
    "PXL_20250808_234426044.jpg",
    "PXL_20250822_062416579.jpg",
    "PXL_20250823_031728508.jpg",
    "PXL_20250830_221843009.jpg",
    "PXL_20250906_001113787.jpg",
    "PXL_20250911_214550967.jpg",
    "PXL_20250926_031353327.jpg",
    "PXL_20251016_032043998.jpg",
    "PXL_20251103_024204090.jpg",
    "PXL_20251107_064636287.jpg",
    "PXL_20251108_025727627.jpg",
    "PXL_20251111_013744030.jpg",
    "PXL_20251111_071903596.jpg",
    "PXL_20251220_040853040.jpg",
    "PXL_20251223_044233818.jpg",
    "PXL_20260131_023636224.jpg",
    "PXL_20260202_204443784.jpg",
    "PXL_20260202_225555167.jpg",
    "PXL_20260214_204206758.jpg",
    "PXL_20260219_050715169.jpg",
    "PXL_20260220_043453305.jpg",
    "PXL_20260220_061303418.jpg",
    "PXL_20260311_180433036.jpg",
    "PXL_20260411_201506380.jpg",
    "PXL_20260426_081506806.jpg",
    "PXL_20260626_042657943.jpg",
    "PXL_20260626_225456569.jpg",
    "PXL_20260703_031558607.jpg",
    "PXL_20260706_190516439.jpg",
    "PXL_20260706_214711042.jpg",
)

/**
 * Det heatmap thr for product **kUInt8** heat (OpenCV THRESH_BINARY uses `>`).
 * - [HEAT_THR_U8_GE1]: float 0 → u8 &gt; 0 → on if **u8 ≥ 1** (production / G-dense).
 * - [HEAT_THR_U8_GE2]: float 1/255 → u8 &gt; 1 → on if **u8 ≥ 2** (Set K A/B only).
 * Not previously A/B'd systematically; K matches G-dense except this thr.
 */
const val HEAT_THR_U8_GE1: Float = 0.0f
const val HEAT_THR_U8_GE2: Float = 1.0f / 255.0f

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
        recB64List: List<String> = emptyList(),
        /** Label prefix: "Blue" / "Orange" / "Red" (legacy). */
        labelPrefix: String = "Red",
        recWList: List<Int> = emptyList(),
        recHList: List<Int> = emptyList(),
    ): List<RedBoxOcrCandidate> {
        val n = minOf(boxRects.size, asisList.size, digitsList.size)
        return (0 until n).map { i ->
            RedBoxOcrCandidate(
                "$labelPrefix${i + 1}",
                asisList[i],
                digitsList[i],
                asisProbsList.getOrElse(i) { "" },
                digitsProbsList.getOrElse(i) { "" },
                boxRects[i],
                recB64List.getOrElse(i) { "" },
                recWList.getOrElse(i) { 0 },
                recHList.getOrElse(i) { 0 },
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
        if (c.recB64.isNotEmpty()) j.put("recB64", c.recB64)
        if (c.recW > 0) j.put("recW", c.recW)
        if (c.recH > 0) j.put("recH", c.recH)
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

    /**
     * Collapse near-duplicate reds (multi-scale copies of the same field) into one AABB.
     *
     * Exact-contain + 3-sides-≤40px miss the common case: two similar-size boxes with
     * high overlap but the inner one sticks out more than 40px. Those stay drawn as
     * stacked reds. Union only when the pair is similar height so a one-line cost
     * is not swallowed into a cost∪vol wrapper.
     */
    fun mergeSimilarOverlapRects(rects: MutableList<Rect>) {
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

    fun doCrossScaleRedboxFilterPixel(redRects: MutableList<Rect>) {
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

    fun doCrossScaleRedboxFilter(pdHunksRawTotal: MutableList<PumpHunk>, imgW: Int, imgH: Int) {
        if (pdHunksRawTotal.isEmpty()) return
        val rects = hunksToRects(pdHunksRawTotal).toMutableList()
        doCrossScaleRedboxFilterPixel(rects)
        pdHunksRawTotal.clear()
        pdHunksRawTotal.addAll(rectsToHunks(rects))
    }

    fun pruneRectsToTopN(
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
        metadata: MutableMap<String, String>? = null,
        boxMode: Int = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
        heatDumpU8z: java.io.File? = null,
        hmThresh: Float = HEAT_THR_U8_GE1,
        maskDilatePasses: Int = 0,
        detTiers: Map<Int, com.baidu.paddle.lite.PaddlePredictor>? = null,
        detTiersInt8: Map<Int, ByteArray>? = null,
    ): List<List<PumpHunk>> {
        // copyHeatmap=false: campaign only needs boxes; floatData/getFloatData crashes on uint8 heatmaps
        val res = paddleEngine.detect(
            buffer.c[id],
            copyHeatmap = false,
            boxMode = boxMode,
            heatDumpU8z = heatDumpU8z,
            hmThresh = hmThresh,
            maskDilatePasses = maskDilatePasses,
            detTiers = detTiers,
            detTiersInt8 = detTiersInt8,
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
        val nonNestedRects = rawRects.filter { r1 ->
            rawRects.none { r2 -> r1 != r2 && r2.contains(r1.left + 5, r1.top + 5, r1.right - 5, r1.bottom - 5) }
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

    suspend fun snapRecCrop(
        recBuffer: BufferSet,
        recCropId: Int,
        targetW: Int,
        targetH: Int,
    ): String = try {
        OcrUtils.takeSnapshot(
            recBuffer.c[recCropId],
            null,
            targetW.coerceAtLeast(2),
            targetH.coerceAtLeast(2),
            emptyList(),
            null,
            recBuffer,
        ).first
    } catch (_: Exception) {
        ""
    }

    suspend fun ocrPumpRectsAsisAndDigits(
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
                targetH = 48, maxW = 320,
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
        pruneRectsToTopN(redPixelList, PumpOcrSettings.DEFAULT_MAX_RED_BOXES, imgH)
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
            ocrBlue.recB64,
            labelPrefix = "Blue",
            recWList = ocrBlue.recW,
            recHList = ocrBlue.recH,
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
            ocrOrange.recB64,
            labelPrefix = "Orange",
            recWList = ocrOrange.recW,
            recHList = ocrOrange.recH,
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

    /**
     * Set I (D+E+G hybrid, calculated) — batch import only.
     * Same stages as experiment `procI`: deskew once → G verts → clip stretch +
     * adjusted valley/peak → D verts → valley push → E verts → one combined classify.
     * Does not change Quick Fill G4 path.
     */
    // Experiment Set I vert lists (shared dual-device hybrid).
    val SET_I_G_VERT: List<Float> = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.6f, 1.1f, 1.5f)
    val SET_I_D_VERT: List<Float> = listOf(0.1f, 0.2f)
    val SET_I_E_VERT: List<Float> = listOf(0.3f, 0.7f)

    private suspend fun setIDiscoveryReds(
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
            val (outerId, innerId) = prepareScale(workspace, scale)
            val paddleResults = runDiscoveryPaddle(workspace, outerId, paddleEngine, targetW, targetH, scale)
            pdHunksRawTotal.addAll(paddleResults[1])
            pdHunksExpTotal.addAll(paddleResults[2])
            pdHunksMaxTotal.addAll(paddleResults[3])
            workspace.c[innerId].release()
            workspace.c[outerId].release()
        }
        doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
        doCrossScaleRedboxFilter(pdHunksExpTotal, imgW, imgH)
        doCrossScaleRedboxFilter(pdHunksMaxTotal, imgW, imgH)
        val redPixelList = hunksToRects(pdHunksRawTotal).toMutableList()
        doCrossScaleRedboxFilterPixel(redPixelList)
        pruneRectsToTopN(redPixelList, PumpOcrSettings.DEFAULT_MAX_RED_BOXES, imgH)
        return rectsToHunks(redPixelList)
    }

    private suspend fun setIAppendStageOcr(
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

    suspend fun runSetICostVolExtraction(
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
}