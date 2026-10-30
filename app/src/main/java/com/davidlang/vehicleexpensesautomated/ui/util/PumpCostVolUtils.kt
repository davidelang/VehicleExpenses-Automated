package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

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
/** Set G-- (deskew): best-3 verts {0.1, 0.2, 0.4}; UV expand + L/R seed-height pads. Parked in pump flows. */
val SET_G_MINUS_MINUS_VERT_FACTORS: List<Float> = listOf(0.1f, 0.2f, 0.4f)
val SET_G_ROT_VERT_FACTORS: List<Float> = listOf(0.34f)
/**
 * Experiment-only dense vertical expansion for Set G-dense / Set K.
 * v → blue height multiple (1+2v).
 * **Below 1.0:** fine grid 0…0.8 step 0.1, plus 0.25 and 0.75 (continuity with prior runs).
 * **≥1.0:** keep previous coarse steps (1.0…2.5 / 0.25) so large-pad cases are not dropped.
 * Not for Fill.
 */
val SET_G_DENSE_VERT_FACTORS: List<Float> = listOf(
    // fine low band
    0.0f, 0.1f, 0.2f, 0.25f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.75f, 0.8f,
    // keep prior high band
    1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f,
)
/**
 * Energy G-on-cap verts (m65 / gx / xycut / P4-rot / Prod-rot). v0.98-230
 * sweep first-unique clustered at 0.00 / 0.05 / 0.15. Used only when energy
 * hits maxFrac — not a per-seed sweep.
 */
val SET_M65_CAP_VERT_FACTORS: List<Float> = listOf(0.0f, 0.05f, 0.15f)
/** Legacy symmetric horiz pad (fraction of *expanded* blue height); keep for G-dense / horiz-reach. */
const val SET_G_HORIZ_FACTOR: Float = 0.5f
/**
 * Default UV L/R pads as fraction of *seed short-axis height* (bh).
 * Chosen ≈ ideal-ink overhang p90 on 04-41-33 (med L/R ≈0.39/0.41; p90 ≈0.54/0.57).
 * Used by deskew G-- (parked) and as the single rot-G-- pad (via [SET_G_ROT_UV_PAD_VARIANTS]).
 */
const val SET_G_UV_LEFT_FRAC: Float = 0.54f
const val SET_G_UV_RIGHT_FRAC: Float = 0.55f

/** One UV L/R pad pair (fraction of seed short-axis height). */
data class UvPadVariant(val id: String, val left: Float, val right: Float)

/**
 * rot-G-- single pad at fixed G=0.34 (candidate nest: seed × pad × vert).
 * p54 = SET_G_UV_* — balanced vs ideal-ink (w/ink ≈1.05; side shortfalls ~11–13%).
 * Multi-pad A/B (p48/p60) parked after 04-41-33 size check.
 */
val SET_G_ROT_UV_PAD_VARIANTS: List<UvPadVariant> = listOf(
    UvPadVariant("p54", SET_G_UV_LEFT_FRAC, SET_G_UV_RIGHT_FRAC),
)
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
 * Photos where ink-prod or rot-ink-prod is not exact (relax=fail) on a trusted
 * cost/vol field at **k=0** (`scaleVariants` kind=ink s=0). Union of Pixel 6 Pro
 * + Pixel Tablet start-113 runs `pump_results_2026-08-25_14-45-50` and
 * `14-46-20`. Excludes 15 photos no scheduled column pool-exacts. Used by
 * Experiment Pump "Prod-ink fail" subset button (68 images). Source:
 * `dev-ai-interaction/research/pump-red-runlength/prod_ink_fail_photos_k0_1445.json`
 * key `union_k0`.
 */
val PROD_INK_FAIL_FILENAMES: List<String> = listOf(
    "PXL_20220701_020625793.dng",
    "PXL_20221020_220049868.dng",
    "PXL_20221121_021250335.dng",
    "PXL_20221121_195449335.jpg",
    "PXL_20221126_210421897.dng",
    "PXL_20221126_210421897.jpg",
    "PXL_20221128_172956178.jpg",
    "PXL_20221222_211812872.jpg",
    "PXL_20221230_182006230.dng",
    "PXL_20230101_055935720.dng",
    "PXL_20230128_022551423.dng",
    "PXL_20230206_014546051.dng",
    "PXL_20230318_232827961.jpg",
    "PXL_20230411_173710801.dng",
    "PXL_20230520_194221428.dng",
    "PXL_20230520_194628805.dng",
    "PXL_20230612_180633478.jpg",
    "PXL_20230625_225655795.dng",
    "PXL_20230705_105304742.dng",
    "PXL_20230827_000357771.jpg",
    "PXL_20230902_175948030.jpg",
    "PXL_20230924_205918100.jpg",
    "PXL_20231120_002742785.dng",
    "PXL_20231120_002920554.jpg",
    "PXL_20231127_200208050.jpg",
    "PXL_20231211_004047524.jpg",
    "PXL_20231221_210417588.jpg",
    "PXL_20231223_075001042.jpg",
    "PXL_20240521_025057693.jpg",
    "PXL_20240708_222637707.jpg",
    "PXL_20240718_000403216.jpg",
    "PXL_20240722_200504113.jpg",
    "PXL_20240808_211542775.jpg",
    "PXL_20241130_183108905.jpg",
    "PXL_20241213_220345190.jpg",
    "PXL_20241222_024130766.jpg",
    "PXL_20250101_020218807.jpg",
    "PXL_20250408_223113314.jpg",
    "PXL_20250426_042852976.jpg",
    "PXL_20250426_084222634.jpg",
    "PXL_20250501_084603153.jpg",
    "PXL_20250501_160616426.jpg",
    "PXL_20250626_205528017.jpg",
    "PXL_20250703_032207597.jpg",
    "PXL_20250706_213056077.jpg",
    "PXL_20250726_211343400.jpg",
    "PXL_20250814_231431109.jpg",
    "PXL_20250830_221843009.jpg",
    "PXL_20250906_001113787.jpg",
    "PXL_20250911_214550967.jpg",
    "PXL_20250926_022815882.jpg",
    "PXL_20250930_065746276.jpg",
    "PXL_20251004_023935813.jpg",
    "PXL_20251107_064636287.jpg",
    "PXL_20251120_015319617.jpg",
    "PXL_20260114_020053675.jpg",
    "PXL_20260131_023636224.jpg",
    "PXL_20260202_204443784.jpg",
    "PXL_20260214_204206758.jpg",
    "PXL_20260220_043453305.jpg",
    "PXL_20260302_000113349.jpg",
    "PXL_20260311_180433036.jpg",
    "PXL_20260425_044615955.jpg",
    "PXL_20260531_181110716.jpg",
    "PXL_20260617_033252085.jpg",
    "PXL_20260626_225456569.jpg",
    "PXL_20260702_220704854.jpg",
    "PXL_20260706_190516439.jpg",
)

/**
 * Mixed-fail / readable pump photos: trusted GT and **not** all-column exact
 * (exact only; relaxed = fail) on `pump_results_2026-08-30_08-45-44.json`
 * (13 columns). Skip no-GT (2), untrusted both fields (7), all-trusted-exact (54),
 * nobody exact (29). 76 names, phone `file` order.
 * Source: `dev-ai-interaction/research/mixed_fail_readable_084544.json`
 * key `mixed_fail_readable`. Pump grid “Mixed fail (76)”; `auto=mixedfail`.
 */
val MIXED_FAIL_READABLE_FILENAMES: List<String> = listOf(
    "PXL_20221121_195449335.jpg",
    "PXL_20221126_021759797.jpg",
    "PXL_20221221_210212750.dng",
    "PXL_20221222_211812872.jpg",
    "PXL_20221228_165217774.dng",
    "PXL_20230101_055935720.dng",
    "PXL_20230128_022551423.dng",
    "PXL_20230206_014546051.dng",
    "PXL_20230225_040459673.dng",
    "PXL_20230318_232827961.jpg",
    "PXL_20230411_173710801.dng",
    "PXL_20230414_023123861.dng",
    "PXL_20230520_194628805.dng",
    "PXL_20230612_180633478.jpg",
    "PXL_20230621_073220076.dng",
    "PXL_20230705_105304742.dng",
    "PXL_20230714_203504848.jpg",
    "PXL_20230806_235553394.jpg",
    "PXL_20231120_002742785.dng",
    "PXL_20231120_002920554.jpg",
    "PXL_20231120_210527402.dng",
    "PXL_20231211_004047524.jpg",
    "PXL_20231221_210417588.jpg",
    "PXL_20231221_213627643.jpg",
    "PXL_20231223_075001042.jpg",
    "PXL_20231226_184548623.jpg",
    "PXL_20240131_013430374.jpg",
    "PXL_20240325_035731504.jpg",
    "PXL_20240408_003215875.jpg",
    "PXL_20240521_025057693.jpg",
    "PXL_20240630_011346680.jpg",
    "PXL_20240708_222637707.jpg",
    "PXL_20240718_000403216.jpg",
    "PXL_20240722_200504113.jpg",
    "PXL_20240808_211542775.jpg",
    "PXL_20241007_023801663.jpg",
    "PXL_20241123_194900843.jpg",
    "PXL_20241202_143338144.jpg",
    "PXL_20250202_042340658.jpg",
    "PXL_20250426_024053319.jpg",
    "PXL_20250426_084222634.jpg",
    "PXL_20250501_160616426.jpg",
    "PXL_20250503_222128862.jpg",
    "PXL_20250528_213707194.jpg",
    "PXL_20250617_052502070.jpg",
    "PXL_20250626_205528017.jpg",
    "PXL_20250703_032207597.jpg",
    "PXL_20250706_185716830.jpg",
    "PXL_20250706_213056077.jpg",
    "PXL_20250726_211343400.jpg",
    "PXL_20250811_211835846.jpg",
    "PXL_20250814_231431109.jpg",
    "PXL_20250906_001113787.jpg",
    "PXL_20250926_022815882.jpg",
    "PXL_20250930_065746276.jpg",
    "PXL_20251004_023935813.jpg",
    "PXL_20251107_064636287.jpg",
    "PXL_20251108_025727627.jpg",
    "PXL_20251108_025912900.jpg",
    "PXL_20251120_015319617.jpg",
    "PXL_20251220_040853040.jpg",
    "PXL_20251223_044233818.jpg",
    "PXL_20260131_023636224.jpg",
    "PXL_20260131_030237621.jpg",
    "PXL_20260202_204443784.jpg",
    "PXL_20260219_050715169.jpg",
    "PXL_20260220_043453305.jpg",
    "PXL_20260411_201506380.jpg",
    "PXL_20260617_033252085.jpg",
    "PXL_20260626_225456569.jpg",
    "PXL_20260702_220704854.jpg",
    "PXL_20260706_190516439.jpg",
    "PXL_20260706_214711042.jpg",
    "PXL_20260707_041630487.jpg",
    "PXL_20260710_005548570.jpg",
    "fuel_1783829858019.jpg",
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


    fun prepareScale(buffer: BufferSet, targetLongEdge: Int): Pair<Int, Int> {
        val dest = NativePaddleEngine.deskewSetFor(targetLongEdge)
        val S = dest.width
        val letterEdge = min(targetLongEdge, S)
        val srcW = buffer.p.width
        val srcH = buffer.p.height
        val currentLongEdge = max(srcW, srcH)
        val scale = if (currentLongEdge <= letterEdge) 1.0f else letterEdge.toFloat() / currentLongEdge
        val targetW = (srcW * scale).toInt().coerceAtLeast(1)
        val targetH = (srcH * scale).toInt().coerceAtLeast(1)
        val alignedW = ((targetW + 31) / 32) * 32
        val alignedH = ((targetH + 31) / 32) * 32
        Log.d(TAG, "prepareScale: request=$targetLongEdge destS=$S -> ${targetW}x${targetH} (Aligned: ${alignedW}x${alignedH})")
        if (alignedW > S || alignedH > S) {
            Log.e(TAG, "prepareScale: aligned ${alignedW}x${alignedH} exceeds packed S=$S")
            return Pair(0, 0)
        }
        val raw = (dest.s as BufferSet.Instance).tensorBindRaw()
        if (!NativeImageUtils.scalePackedU8(buffer.p.mat, raw, S, targetW, targetH)) {
            Log.e(TAG, "prepareScale: packed scale failed S=$S inner=${targetW}x$targetH")
            return Pair(0, 0)
        }
        return Pair(targetW, targetH)
    }

    suspend fun snapRecCrop(
        recBuffer: BufferSet,
        recCropId: Int,
        targetW: Int,
        targetH: Int,
    ): String {
        val dest = recBuffer.c[recCropId]
        val cw = targetW.coerceAtLeast(2)
        val ch = targetH.coerceAtLeast(2)
        if (cw > dest.width || ch > dest.height) {
            throw IllegalStateException("snapRecCrop: ${cw}x$ch exceeds ${dest.width}x${dest.height}")
        }
        val roi = android.graphics.Rect(0, 0, cw, ch)
        return OcrUtils.takeSnapshot(
            dest,
            roi,
            0,
            0,
            emptyList(),
            null,
            recBuffer,
        ).first
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
     * Set I (D+E+G hybrid, calculated) — batch import only.
     * Same stages as experiment `procI`: deskew once → G verts → clip stretch +
     * adjusted valley/peak → D verts → valley push → E verts → one combined classify.
     * Does not change Fill G4 path.
     */
    // Experiment Set I vert lists (shared dual-device hybrid).
    val SET_I_G_VERT: List<Float> = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.6f, 1.1f, 1.5f)
    val SET_I_D_VERT: List<Float> = listOf(0.1f, 0.2f)
    val SET_I_E_VERT: List<Float> = listOf(0.3f, 0.7f)
}
