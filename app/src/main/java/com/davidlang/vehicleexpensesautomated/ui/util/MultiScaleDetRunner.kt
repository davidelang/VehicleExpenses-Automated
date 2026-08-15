package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Base64
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.davidlang.vehicleexpensesautomated.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/**
 * Multi-scale × multi-det gallery with sparse report fill.
 *
 * Memory rules:
 * - Long-lived [BufferSet]s are allocated once at **max** size ([MAX_SIDE]²). Resize
 *   only grows capacity (by design never shrinks allocation) — so we never start
 *   tiny and grow mid-run (avoids holes).
 * - **One predictor** loaded at a time; model sets are **light→heavy** (product,
 *   mobile, server) so we learn which graphs fit before the heavy crash (if any).
 * - Scales within a set are **small→large** (quick results first; heavy outers last).
 * - Feed tensors use **preallocated** per-tier byte planes (no per-cell Java alloc).
 * - DNG/JPEG decode uses a **short-lived** BufferSet; mono lands in long-lived master.
 * - Per-cell: publish tray + drop crops/overlay temps only — do not “shrink” BufferSets.
 *
 * Collapser fills HTML/JSON in file order (row-major); ids follow compute order.
 */
object MultiScaleDetRunner {
    private const val TAG = "MultiScaleDet"

    /** Max long edge / square side for long-lived BufferSets and feed tiers. */
    private const val MAX_SIDE = 4096

    /**
     * Outer long-edge sizes (letterbox / host canvas). File/HTML ascending.
     * **One gallery row per outer** — feed strategy (single vs H/V tile) is chosen at
     * run time per model×domain (see [resolveFeedStrategy]). No separate square/hspan/vspan
     * rows (avoids blank multi-tile columns).
     */
    val OUTER_SCALES: List<Int> = listOf(64, 96, 256, 384, 512, 768, 1024, 2048, 4096)

    /**
     * @deprecated Intermediate square ladder removed; maxLite + strategy rows only.
     * Kept empty-ish list for any UI that still prints “tiles=…”.
     */
    val TILE_SIZES: List<Int> = listOf(1024, 1504, 2048)

    /** @deprecated use [OUTER_SCALES]; kept for UI that listed long-edge only. */
    val SCALES: List<Int> get() = OUTER_SCALES

    /** How Lite is fed for a cell (resolved at run time for [AUTO] matrix rows). */
    enum class TileStrategy {
        /** Full outer×outer single feed (only if outer ≤ model maxLite). */
        SINGLE,
        /** Square tiles of side S = model maxLite. (Not used in current domain policy.) */
        SQUARE,
        /**
         * Full-width bands: H×outer with H·outer ≈ S²; H-span can ShareExternal (no gather).
         * Band **height H is a multiple of 32** (round up). That also satisfies heat ~1/4 H
         * (multiple of 4). H=1104 (only ×4) crashed Lite (not ÷32); keep ×32 always.
         */
        HSPAN,
        /**
         * Full-height strips: outer×W with outer·W ≈ S²; densify copy required.
         * Strip **width W is a multiple of 32** (round up) — Lite / prepareScale alignment.
         */
        VSPAN,
        /**
         * Matrix placeholder: resolve to SINGLE / HSPAN / VSPAN at run time from
         * model maxLite + domain policy (pump/dash H-tile; expense H/V by orientation).
         */
        AUTO,
    }

    /** One HTML/JSON row: outer canvas; [strategy] is usually [TileStrategy.AUTO]. */
    data class ScaleTileRow(
        val outer: Int,
        val strategy: TileStrategy,
        val label: String,
    ) {
        val isSingle: Boolean get() = strategy == TileStrategy.SINGLE
        /** Sort key for compute order. */
        val strategyOrd: Int get() = strategy.ordinal
    }

    /**
     * Gallery rows: **one row per outer** (AUTO feed). Domain/model skips blank cells
     * that cannot run; no multi-strategy tile columns.
     */
    fun buildScaleTileMatrix(): List<ScaleTileRow> =
        OUTER_SCALES.map { outer ->
            val label = if (outer < 768) "≤$outer" else "≤$outer"
            ScaleTileRow(outer, TileStrategy.AUTO, label)
        }

    /**
     * Compute passes (**smallest outer first**): ≤1024, then 2048, then 4096.
     * Early collapser fills small/cheap cells while large tiled outers run last.
     * Each pass tears down predictors; mono cache uses pass max outer.
     */
    fun buildScaleTilePasses(matrix: List<ScaleTileRow>): List<List<ScaleTileRow>> {
        val pMain = matrix.filter { it.outer < 2048 }
        val p2048 = matrix.filter { it.outer == 2048 }
        val p4096 = matrix.filter { it.outer == 4096 }
        return listOf(pMain, p2048, p4096).filter { it.isNotEmpty() }
    }

    /** @deprecated replaced by [buildScaleTilePasses]. */
    val SCALE_SETS: List<List<Int>> = listOf(
        listOf(1024, 768, 512, 384, 256, 96, 64),
        listOf(2048),
        listOf(4096),
    )

    /**
     * HTML / availableModels — product + v4 **mobile** only.
     * - **v5 mobile** removed: too many zero-box cells (poor det); see docs/obsolete.
     * - **v4/v5 server** never scheduled: ~1/10 speed, tens of seconds/cell — not real-time.
     */
    val DET_MODELS: List<String> = listOf(
        "PP-OCRv4_mobile_det",
        "product_det",
    )

    /** Compute order: lightest graph first (fit-probe before heavy LMK). */
    private val MODEL_COMPUTE_RANK: Map<String, Int> = mapOf(
        "product_det" to 0,
        "PP-OCRv4_mobile_det" to 1,
    )

    /**
     * If non-null, skip this model for the current pass and publish that reason on cells.
     * Server models are no longer in [DET_MODELS]; gate kept as a no-op for safety.
     */
    private fun computeSkipReason(mName: String): String? = null

    /** Default tile when docs refer to a single tile size (smallest in matrix). */
    private const val DET_TILE = 768

    /**
     * Default tile overlap (non-pump): 3/10 → ≥30%.
     * Pump uses [PUMP_TILE_OVERLAP_*] = 50%.
     */
    private const val DET_TILE_OVERLAP_NUM = 3
    private const val DET_TILE_OVERLAP_DEN = 10
    private const val PUMP_TILE_OVERLAP_NUM = 1
    private const val PUMP_TILE_OVERLAP_DEN = 2

    private const val PREVIEW_MAX_W = 360
    private const val EXPENSE_ASSET = "experiment/receipt/PXL_20260809_094107925.jpg"
    private const val EXPENSE_BASENAME = "PXL_20260809_094107925.jpg"
    private const val ASSET_DIR = "paddle/exp_det_ab"
    private const val COLLAPSE_PERIOD_MS = 60_000L

    private fun isServerModel(name: String): Boolean =
        name.contains("server", ignoreCase = true)

    /**
     * Max **Lite feed** side we will actually Run for this graph.
     * Cells requesting a larger tile are **skipped** (blank grid cell + status=skip) — never
     * clamped and refilled with a different tile (that lied about the matrix point).
     *
     * Peak process PSS (5554, multi-scale, max of MEM DURING samples) guided these caps:
     * - product @2048 tile ~2.3–2.4 GB; @4096 single ~8.2 GB
     * - v4 mobile @1504 ~3.0 GB; @2048 ~5.2 GB
     * - v5 mobile @1024 ~3.1 GB; @1504 ~6.3 GB
     *
     * **Current (~keep heaviest allowed cell ≤ ~3 GB):** product 2048 / v4 1504 / v5 1024.
     *
     * Alternatives (change the return values only — do not invent a third path):
     * - keep peak **&lt; ~2 GB**: product **1504** / v4 **1024** / v5 **768**
     * - allow up to **~6 GB**: product **2048** / v4 **2048** / v5 **1504**
     */
    private fun maxLiteSideForModel(name: String): Int = when {
        name.contains("product", ignoreCase = true) -> 2048
        name.contains("v5", ignoreCase = true) && name.contains("mobile", ignoreCase = true) -> 1024
        name.contains("mobile", ignoreCase = true) -> 1504 // v4
        name.contains("server", ignoreCase = true) -> 640
        else -> 1024
    }

    /**
     * Domain outer limits + feed strategy for this model×domain×outer.
     * - **pump:** outer &lt; 4096; tile = **HSPAN only**, 50% overlap
     * - **dash:** outer ≥ 512; tile = **HSPAN only**
     * - **expense:** outer ≥ 1024; tile = HSPAN (portrait) or VSPAN (landscape)
     * - single when outer ≤ maxLite; else the domain tile strategy
     *
     * @return resolved strategy, or null + skip reason
     */
    private fun resolveFeedStrategy(
        modelName: String,
        domain: String,
        outer: Int,
        contentW: Int = 0,
        contentH: Int = 0,
    ): Pair<TileStrategy?, String?> {
        when (domain) {
            "pump" -> if (outer >= 4096) return null to "skip: pump disables outer≥4096"
            "dash" -> if (outer < 512) return null to "skip: dash disables outer<512"
            "expense" -> if (outer < 1024) return null to "skip: expense disables outer<1024"
        }
        val s = maxLiteSideForModel(modelName)
        if (outer <= s) return TileStrategy.SINGLE to null
        // Must tile: domain chooses H or V only (no square).
        val tileStrat = when (domain) {
            "expense" -> {
                val w = contentW
                val h = contentH
                when {
                    w > 0 && h > 0 && w > h -> TileStrategy.VSPAN // landscape: scan L→R
                    else -> TileStrategy.HSPAN // portrait / unknown: scan top→bottom
                }
            }
            // pump + dash (+ default): H tiles only
            else -> TileStrategy.HSPAN
        }
        return tileStrat to null
    }

    /** Skip if [resolveFeedStrategy] cannot run this cell. */
    private fun cellSkipReason(
        modelName: String,
        domain: String,
        strategy: TileStrategy,
        outer: Int,
        contentW: Int = 0,
        contentH: Int = 0,
    ): String? {
        val (resolved, skip) = resolveFeedStrategy(modelName, domain, outer, contentW, contentH)
        if (skip != null) return skip
        if (resolved == null) return "skip: no feed strategy"
        // Legacy multi-strategy rows: if matrix still has a concrete strategy, require match.
        if (strategy != TileStrategy.AUTO && strategy != resolved) {
            return "skip: matrix ${strategy.name.lowercase()} ≠ resolved ${resolved.name.lowercase()}"
        }
        return null
    }

    /** Pump 50% tile overlap; others default 30%. */
    private fun overlapNumDenForDomain(domain: String): Pair<Int, Int> =
        if (domain == "pump") {
            PUMP_TILE_OVERLAP_NUM to PUMP_TILE_OVERLAP_DEN
        } else {
            DET_TILE_OVERLAP_NUM to DET_TILE_OVERLAP_DEN
        }

    /** Align [x] up to a multiple of [mult] (odd sizes always round up). */
    private fun alignUp(x: Int, mult: Int): Int {
        val m = mult.coerceAtLeast(1)
        val v = x.coerceAtLeast(1)
        return ((v + m - 1) / m) * m
    }

    /**
     * Short-axis length for H/V span so area ≈ S² with long axis = [outer].
     *
     * Alignment (round **up** for odd sizes):
     * - **HSPAN band height** and **VSPAN strip width** — both multiples of **32**.
     *   DB/Lite backbones need H,W divisible by 32 through the feature pyramid.
     *   Multiple of 32 also implies multiple of 4 (heat ≈ 1/4 input height).
     *   Regression: H=1104 (×4 only, not ×32) → SIGSEGV in ElementwiseAdd on
     *   v4 HSPAN 1104×2048 (emu + phone); H=1088 (×32) was fine on the same photo.
     *
     * Short axis stays &lt; outer so multi-band tiling applies.
     *
     * @param mult 32 for both HSPAN height and VSPAN width
     * @param minAxis minimum short axis (default 32)
     */
    private fun spanShortAxis(s: Int, outer: Int, mult: Int = 32, minAxis: Int = 32): Int {
        val o = outer.coerceAtLeast(1)
        val m = mult.coerceAtLeast(32) // never weaker than Lite spatial align
        val area = s.toLong() * s
        val raw = (area / o).toInt().coerceAtLeast(minAxis)
        var a = alignUp(raw, m)
        // Must leave room for at least two bands/strips when outer is large enough.
        val maxShort = (o - m).coerceAtLeast(minAxis)
        if (a >= o) a = alignUp(maxShort.coerceAtMost(o - 1), m).coerceAtMost(maxShort)
        // Prefer not to exceed S² area by much; step down by [m] if overshoot is large.
        while (a.toLong() * o > area + m && a > minAxis) a -= m
        if (a < minAxis) a = minAxis
        // Final enforce: always multiple of 32 (and thus of 4).
        a = alignUp(a, 32)
        if (a > maxShort) a = (maxShort / 32).coerceAtLeast(1) * 32
        return a.coerceAtMost(maxShort).coerceAtLeast(minAxis).let { v ->
            // Guarantee %32==0 even after coerce.
            (v / 32).coerceAtLeast(1) * 32
        }
    }

    /** Lite feed (H, W) for this model strategy at [outer]. */
    private fun feedHw(modelName: String, strategy: TileStrategy, outer: Int): Pair<Int, Int> {
        val s = maxLiteSideForModel(modelName)
        val o = outer.coerceAtLeast(1)
        return when (strategy) {
            TileStrategy.SINGLE -> o to o
            TileStrategy.SQUARE -> s to s
            TileStrategy.HSPAN -> {
                // Multiple of 32 (implies ×4 for heat); never use ×4-only (see 1104 crash).
                val h = spanShortAxis(s, o, mult = 32, minAxis = 32)
                h to o
            }
            TileStrategy.VSPAN -> {
                val w = spanShortAxis(s, o, mult = 32, minAxis = 32)
                o to w
            }
            TileStrategy.AUTO ->
                throw IllegalArgumentException("feedHw requires resolved strategy, not AUTO")
        }
    }

    /** Round long edge up to 32 (product prepareScale alignment). */
    private fun tierFor(longEdge: Int): Int =
        ((longEdge.coerceAtLeast(1) + 31) / 32) * 32

    /**
     * Host canvas side for a multi-scale cell: always the **requested scale** [le]
     * (preallocated in [feedByTier]), never tierFor(content) which can be 4032 when
     * the photo long edge is slightly under 4096 and miss the le=4096 canvas.
     */
    private fun outerSideForScale(le: Int): Int = le.coerceAtLeast(1).coerceAtMost(MAX_SIDE)

    /**
     * Pick a preallocated feed plane: exact [want], else smallest available ≥ want,
     * else largest available (caller must still letterbox into that square).
     */
    private fun resolveFeedSide(feedByTier: Map<Int, ByteArray>, want: Int): Int {
        if (feedByTier.containsKey(want)) return want
        val ge = feedByTier.keys.filter { it >= want }.minOrNull()
        if (ge != null) return ge
        return feedByTier.keys.maxOrNull()
            ?: throw IllegalStateException("feedByTier empty (want=$want)")
    }

    /** ceil(tile * num / den) so overlap is never under the requested fraction. */
    private fun tileOverlapPx(tile: Int, num: Int, den: Int): Int {
        val n = num.coerceAtLeast(1)
        val d = den.coerceAtLeast(1)
        return ((tile * n) + d - 1) / d
    }

    private fun tileStride(tile: Int, num: Int, den: Int): Int =
        (tile - tileOverlapPx(tile, num, den)).coerceAtLeast(1)

    /**
     * Tile top-left origins on square [outer] with fixed stride for requested overlap.
     * Last origin is always flush to outer−tile so coverage is complete.
     */
    private fun tileOrigins(outer: Int, tile: Int, num: Int, den: Int): List<Int> {
        if (outer <= tile) return listOf(0)
        val stride = tileStride(tile, num, den)
        val last = outer - tile
        val out = ArrayList<Int>()
        var x = 0
        while (true) {
            out.add(x)
            if (x >= last) break
            val next = (x + stride).coerceAtMost(last)
            if (next == x) break
            x = next
        }
        return out
    }

    private fun tileGridForOuter(outer: Int, tile: Int, num: Int, den: Int): Int =
        tileOrigins(outer, tile, num, den).size

    /** Models sorted light→heavy for compute; [models] keeps display indices. */
    private fun modelComputeOrder(models: List<String>): List<String> =
        models.sortedWith(
            compareBy<String> { MODEL_COMPUTE_RANK[it] ?: 99 }.thenBy { it },
        )

    /** One logical det cell; [id] is 1-based compute order. */
    private data class CellRef(
        val id: Int,
        val mi: Int,
        val pi: Int,
        /** Index into [buildScaleTileMatrix] (HTML row). */
        val row: Int,
        val outer: Int,
        val strategy: TileStrategy,
        val pass: Int,
        val rowLabel: String,
    )

    /** Process + system RAM/swap — shared with pump via [ProcessMemProbe]. */
    private fun logMem(label: String, onLog: (String) -> Unit) {
        ProcessMemProbe.log(label, onLog)
    }

    private fun logBuffer(label: String, buf: BufferSet, onLog: (String) -> Unit) {
        onLog("BUF $label logical=${buf.width}x${buf.height}")
    }

    private fun <T> withDetectMemSampling(
        id: Int,
        onLog: (String) -> Unit,
        intervalMs: Long = 250L,
        block: () -> T,
    ): T = ProcessMemProbe.withSampling(
        tag = "msd_id=$id",
        intervalMs = intervalMs,
        onLog = onLog,
        block = block,
    )

    data class PhotoRef(
        val file: File,
        val domain: String,
        val displayName: String,
        /** Probed native pixels (0 if unknown); used for expense H/V span orientation. */
        val nativeW: Int = 0,
        val nativeH: Int = 0,
    ) {
        val isPortrait: Boolean get() = nativeH > 0 && nativeW > 0 && nativeH >= nativeW
        val isLandscape: Boolean get() = nativeH > 0 && nativeW > 0 && nativeW > nativeH
    }

    data class Result(
        val reportDir: File,
        val jsonFile: File,
        val nPhotos: Int,
        val models: List<String>,
        val arch: String,
        val message: String,
    )

    fun modelArchForDevice(): String = NativePaddleEngine.modelArchForPrimaryAbi()

    fun pumpPhotoDir(context: Context): File =
        File(context.getExternalFilesDir(null), "pump_photos").also { it.mkdirs() }

    fun dashPhotoDir(context: Context): File =
        File(context.getExternalFilesDir(null), "dash_photos").also { it.mkdirs() }

    fun expensePhotoDir(context: Context): File =
        File(context.getExternalFilesDir(null), "expense_photos").also { it.mkdirs() }

    fun reportDir(context: Context): File =
        File(context.getExternalFilesDir(null), "multi_scale_det_reports").also { it.mkdirs() }

    fun seedExpenseReceipt(context: Context): File? {
        val dest = File(expensePhotoDir(context), EXPENSE_BASENAME)
        if (dest.exists() && dest.length() > 1000L) return dest
        return try {
            context.assets.open(EXPENSE_ASSET).use { inp ->
                FileOutputStream(dest).use { out -> inp.copyTo(out) }
            }
            Log.i(TAG, "seeded expense → ${dest.absolutePath}")
            dest
        } catch (t: Throwable) {
            Log.w(TAG, "seed expense failed: ${t.message}")
            null
        }
    }

    fun collectPhotos(context: Context): List<PhotoRef> {
        seedExpenseReceipt(context)
        val exts = setOf("jpg", "jpeg", "png", "dng")
        fun listDomain(dir: File, domain: String): List<PhotoRef> {
            val files = dir.listFiles { f ->
                f.isFile && f.extension.lowercase() in exts
            }?.sortedBy { it.name } ?: emptyList()
            return files.map { f ->
                val (nw, nh) = try {
                    ImageIngestionProvider.probeDimensions(context, f.absolutePath)
                } catch (_: Throwable) {
                    0 to 0
                }
                PhotoRef(f, domain, f.name, nw, nh)
            }
        }
        return listDomain(pumpPhotoDir(context), "pump") +
            listDomain(dashPhotoDir(context), "dash") +
            listDomain(expensePhotoDir(context), "expense")
    }

    fun countByDomain(photos: List<PhotoRef>): Map<String, Int> =
        photos.groupingBy { it.domain }.eachCount()

    fun availableModels(context: Context, arch: String = modelArchForDevice()): List<String> {
        return DET_MODELS.filter { name ->
            try {
                context.assets.open("$ASSET_DIR/${name}_$arch.nb").close()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Outer long edges (not full row count). */
    fun effectiveScales(): List<Int> = OUTER_SCALES

    /** HTML row count (outer × tile pairs). */
    fun effectiveRowCount(): Int = buildScaleTileMatrix().size

    // ── id / compute geometry ────────────────────────────────────────────────
    // File/HTML: row-major (photo, matrix row ascending, model display order).
    // Compute ids 1..N: pass (small outer first) → model light→heavy → photo →
    //   rows in pass with outer asc, then strategy SINGLE → SQUARE → HSPAN → VSPAN.

    private fun buildCellOrder(
        models: List<String>,
        nPhotos: Int,
        matrix: List<ScaleTileRow>,
        passes: List<List<ScaleTileRow>>,
    ): List<CellRef> {
        val rowIndex = matrix.withIndex().associate { it.value to it.index }
        val out = ArrayList<CellRef>(nPhotos * models.size * matrix.size)
        var id = 1
        passes.forEachIndexed { pass, passRows ->
            // Outer small→large; strategy order SINGLE → SQUARE → HSPAN → VSPAN.
            val rowsOrdered = passRows.sortedWith(
                compareBy<ScaleTileRow> { it.outer }.thenBy { it.strategyOrd },
            )
            for (mName in modelComputeOrder(models)) {
                val mi = models.indexOf(mName)
                if (mi < 0) continue
                for (pi in 0 until nPhotos) {
                    for (cfg in rowsOrdered) {
                        val ri = rowIndex[cfg] ?: continue
                        out.add(
                            CellRef(
                                id = id++,
                                mi = mi,
                                pi = pi,
                                row = ri,
                                outer = cfg.outer,
                                strategy = cfg.strategy,
                                pass = pass,
                                rowLabel = cfg.label,
                            ),
                        )
                    }
                }
            }
        }
        return out
    }

    private fun idTag(id: Int): String = "%07d".format(Locale.US, id)

    private fun htmlBegin(id: Int) = "<!--C:B:${idTag(id)}-->"
    private fun htmlEnd(id: Int) = "<!--C:E:${idTag(id)}-->"
    private fun jsonBegin(id: Int) = "<!--JC:B:${idTag(id)}-->"
    private fun jsonEnd(id: Int) = "<!--JC:E:${idTag(id)}-->"

    private fun copyModelAsset(context: Context, name: String, arch: String): File {
        val asset = "$ASSET_DIR/${name}_$arch.nb"
        val dest = File(context.filesDir, "exp_det_${name}_$arch.nb")
        context.assets.open(asset).use { inp ->
            FileOutputStream(dest).use { out -> inp.copyTo(out) }
        }
        return dest
    }

    /**
     * One Lite det graph. [feedByTier] is run-lifetime host canvas/feed (exact numel).
     * Lite input side is per-cell [tile] (up to [MAX_SIDE]=4096 for product/mobile opt).
     *
     * **Shape changes:** Paddle Lite (esp. x86) can SIGSEGV if the same predictor’s
     * input is resized to a new H×W after `Run`. We reload the predictor when the
     * Lite feed side changes.
     */
    private class MonoDetPredictor(
        val name: String,
        private val nbFile: File,
        private val feedByTier: Map<Int, ByteArray>,
        private val maxLiteSide: Int = maxLiteSideForModel(name),
    ) {
        private var pred: PaddlePredictor? = null
        private val heatByOuter = HashMap<Int, ByteArray>()
        /** Densify scratch for square / vspan (area ≤ S²). */
        private val tileFeed = ByteArray(maxLiteSide * maxLiteSide)
        private var lastFeedH: Int = -1
        private var lastFeedW: Int = -1

        init {
            openPredictor(maxLiteSide.coerceAtMost(DET_TILE), maxLiteSide.coerceAtMost(DET_TILE))
        }

        fun modelMaxLiteSide(): Int = maxLiteSide

        private fun openPredictor(feedH: Int, feedW: Int) {
            val config = MobileConfig()
            config.setThreads(4)
            config.setPowerMode(PowerMode.LITE_POWER_NO_BIND)
            config.setModelFromFile(nbFile.absolutePath)
            val p = PaddlePredictor.createPaddlePredictor(config)
                ?: throw IllegalStateException("createPaddlePredictor null for ${nbFile.name}")
            val h = feedH.coerceAtLeast(1)
            val w = feedW.coerceAtLeast(1)
            p.getInput(0).resize(longArrayOf(1, 1, h.toLong(), w.toLong()))
            pred = p
            lastFeedH = h
            lastFeedW = w
        }

        /** Ensure Lite input is [h]×[w]; reload predictor on shape change. */
        private fun ensureLiteShape(h: Int, w: Int): PaddlePredictor {
            val cur = pred
            if (cur != null && lastFeedH == h && lastFeedW == w) return cur
            if (cur != null) {
                Log.i(
                    TAG,
                    "reload $name Lite input ${lastFeedH}x$lastFeedW → ${h}x$w (shape-change safety)",
                )
                pred = null
                lastFeedH = -1
                lastFeedW = -1
                NativePaddleEngine.releasePredictor(cur, "exp_det_${name}_reshape")
                System.gc()
            }
            openPredictor(h, w)
            return pred!!
        }

        fun close() {
            val p = pred ?: return
            pred = null
            lastFeedH = -1
            lastFeedW = -1
            heatByOuter.clear()
            NativeImageUtils.releaseSharedTensorInputU8()
            NativePaddleEngine.releasePredictor(p, "exp_det_$name")
        }

        /**
         * @param strategy matrix row strategy (single / square / hspan / vspan)
         * @param overlapNum/Den tile overlap (pump 1/2=50%; default 3/10)
         * Heat is always product **u8**.
         */
        fun detect(
            monoMat: Mat,
            contentW: Int,
            contentH: Int,
            outerSide: Int,
            strategy: TileStrategy,
            maxBoxes: Int,
            overlapNum: Int = DET_TILE_OVERLAP_NUM,
            overlapDen: Int = DET_TILE_OVERLAP_DEN,
        ): DetOut? {
            val want = outerSideForScale(outerSide)
            val outer = resolveFeedSide(feedByTier, want)
            val (feedH, feedW) = feedHw(name, strategy, outer)
            val on = overlapNum.coerceAtLeast(1)
            val od = overlapDen.coerceAtLeast(1)
            return when (strategy) {
                TileStrategy.SINGLE -> detectSingle(monoMat, outer, maxBoxes)
                TileStrategy.SQUARE -> detectTiledSquare(monoMat, outer, feedH, maxBoxes, on, od)
                TileStrategy.HSPAN -> detectTiledHspan(monoMat, outer, feedH, maxBoxes, on, od)
                TileStrategy.VSPAN -> detectTiledVspan(monoMat, outer, feedW, maxBoxes, on, od)
                TileStrategy.AUTO ->
                    throw IllegalArgumentException("detect requires resolved strategy, not AUTO")
            }
        }

        private fun detectSingle(
            monoMat: Mat,
            tier: Int,
            maxBoxes: Int,
        ): DetOut? {
            require(tier <= maxLiteSide) {
                "detectSingle tier=$tier > maxLiteSide=$maxLiteSide"
            }
            val buf = feedByTier[tier]
                ?: throw IllegalStateException(
                    "no preallocated feed for tier=$tier keys=${feedByTier.keys.sorted()}",
                )
            java.util.Arrays.fill(buf, 0.toByte())
            NativeImageUtils.populateMonoUInt8(monoMat, buf, tier, tier)
            val p = ensureLiteShape(tier, tier)
            val input = p.getInput(0)
            input.resize(longArrayOf(1, 1, tier.toLong(), tier.toLong()))
            if (!input.setData(buf)) return null
            p.run()
            return postFromTensor(
                p, tier, detMode = "single", maxBoxes = maxBoxes,
                liteH = tier, liteW = tier, inputNocopy = false,
            )
        }

        private fun detectTiledSquare(
            monoMat: Mat,
            outer: Int,
            tile: Int,
            maxBoxes: Int,
            overlapNum: Int,
            overlapDen: Int,
        ): DetOut? {
            val canvas = feedByTier[outer]
                ?: throw IllegalStateException("no canvas outer=$outer")
            val combined = heatByOuter.getOrPut(outer) { ByteArray(outer * outer) }
            java.util.Arrays.fill(canvas, 0.toByte())
            java.util.Arrays.fill(combined, 0.toByte())
            NativeImageUtils.populateMonoUInt8(monoMat, canvas, outer, outer)

            val origins = tileOrigins(outer, tile, overlapNum, overlapDen)
            val grid = origins.size
            val p = ensureLiteShape(tile, tile)
            val input = p.getInput(0)
            input.resize(longArrayOf(1, 1, tile.toLong(), tile.toLong()))
            val need = tile * tile

            var tilesOk = 0
            for (oy in origins) {
                for (ox in origins) {
                    for (ty in 0 until tile) {
                        System.arraycopy(
                            canvas, (oy + ty) * outer + ox,
                            tileFeed, ty * tile, tile,
                        )
                    }
                    if (!input.setData(tileFeed.copyOf(need))) continue
                    p.run()
                    if (!maxMergeHeat(p, combined, outer, tile, tile, ox, oy)) continue
                    tilesOk++
                }
            }
            Log.i(
                TAG,
                "tiled_square $name outer=$outer grid=${grid}x$grid tile=$tile " +
                    "overlap_px=${tileOverlapPx(tile, overlapNum, overlapDen)} " +
                    "stride=${tileStride(tile, overlapNum, overlapDen)} " +
                    "ov=$overlapNum/$overlapDen " +
                    "tiles_ok=$tilesOk/${grid * grid} merge=max input=copy",
            )
            return finishTiled(combined, outer, maxBoxes, "tiled_sq_${grid}x${grid}_$tile", tile, tile, false)
        }

        /**
         * Full-width horizontal bands: feed H×outer contiguous in canvas → ShareExternal
         * (no densify). Falls back to setData of the band if share fails.
         */
        private fun detectTiledHspan(
            monoMat: Mat,
            outer: Int,
            bandH: Int,
            maxBoxes: Int,
            overlapNum: Int,
            overlapDen: Int,
        ): DetOut? {
            val canvas = feedByTier[outer]
                ?: throw IllegalStateException("no canvas outer=$outer")
            val combined = heatByOuter.getOrPut(outer) { ByteArray(outer * outer) }
            java.util.Arrays.fill(canvas, 0.toByte())
            java.util.Arrays.fill(combined, 0.toByte())
            NativeImageUtils.populateMonoUInt8(monoMat, canvas, outer, outer)

            val origins = tileOrigins(outer, bandH, overlapNum, overlapDen)
            val p = ensureLiteShape(bandH, outer)
            val input = p.getInput(0)
            input.resize(longArrayOf(1, 1, bandH.toLong(), outer.toLong()))
            val bandBytes = bandH * outer
            var tilesOk = 0
            var nocopyOk = 0
            var copyFallback = 0
            for (oy in origins) {
                val off = oy * outer
                val shareRc = NativeImageUtils.shareTensorInputU8(input, canvas, off, bandBytes)
                val fed = if (shareRc > 0) {
                    if (shareRc == 1) nocopyOk++ else copyFallback++
                    true
                } else {
                    // Densify-free still: one contiguous setData of the band.
                    val band = canvas.copyOfRange(off, off + bandBytes)
                    if (!input.setData(band)) {
                        NativeImageUtils.releaseSharedTensorInputU8()
                        continue
                    }
                    copyFallback++
                    true
                }
                if (!fed) continue
                try {
                    p.run()
                } finally {
                    NativeImageUtils.releaseSharedTensorInputU8()
                }
                if (!maxMergeHeat(p, combined, outer, bandH, outer, 0, oy)) continue
                tilesOk++
            }
            Log.i(
                TAG,
                "tiled_hspan $name outer=$outer bandH=$bandH bands=${origins.size} " +
                    "tiles_ok=$tilesOk nocopy=$nocopyOk copy_fb=$copyFallback " +
                    "overlap_px=${tileOverlapPx(bandH, overlapNum, overlapDen)} " +
                    "stride=${tileStride(bandH, overlapNum, overlapDen)} ov=$overlapNum/$overlapDen",
            )
            return finishTiled(
                combined, outer, maxBoxes,
                "tiled_hspan_${origins.size}x${bandH}x$outer",
                bandH, outer,
                inputNocopy = nocopyOk > 0 && copyFallback == 0,
            )
        }

        /** Full-height vertical strips: densify (stride = outer). */
        private fun detectTiledVspan(
            monoMat: Mat,
            outer: Int,
            stripW: Int,
            maxBoxes: Int,
            overlapNum: Int,
            overlapDen: Int,
        ): DetOut? {
            val canvas = feedByTier[outer]
                ?: throw IllegalStateException("no canvas outer=$outer")
            val combined = heatByOuter.getOrPut(outer) { ByteArray(outer * outer) }
            java.util.Arrays.fill(canvas, 0.toByte())
            java.util.Arrays.fill(combined, 0.toByte())
            NativeImageUtils.populateMonoUInt8(monoMat, canvas, outer, outer)

            val origins = tileOrigins(outer, stripW, overlapNum, overlapDen)
            val p = ensureLiteShape(outer, stripW)
            val input = p.getInput(0)
            input.resize(longArrayOf(1, 1, outer.toLong(), stripW.toLong()))
            val need = outer * stripW
            require(need <= tileFeed.size) { "vspan need=$need > tileFeed ${tileFeed.size}" }

            var tilesOk = 0
            for (ox in origins) {
                for (ty in 0 until outer) {
                    System.arraycopy(canvas, ty * outer + ox, tileFeed, ty * stripW, stripW)
                }
                if (!input.setData(tileFeed.copyOf(need))) continue
                p.run()
                if (!maxMergeHeat(p, combined, outer, outer, stripW, ox, 0)) continue
                tilesOk++
            }
            Log.i(
                TAG,
                "tiled_vspan $name outer=$outer stripW=$stripW strips=${origins.size} " +
                    "tiles_ok=$tilesOk input=copy " +
                    "overlap_px=${tileOverlapPx(stripW, overlapNum, overlapDen)} " +
                    "stride=${tileStride(stripW, overlapNum, overlapDen)} ov=$overlapNum/$overlapDen",
            )
            return finishTiled(
                combined, outer, maxBoxes,
                "tiled_vspan_${origins.size}x${outer}x$stripW",
                outer, stripW, false,
            )
        }

        private fun maxMergeHeat(
            p: PaddlePredictor,
            combined: ByteArray,
            outer: Int,
            tileH: Int,
            tileW: Int,
            ox: Int,
            oy: Int,
        ): Boolean {
            val output = p.getOutput(0)
            val dims = output.shape()
            val heatH = dims[2].toInt()
            val heatW = dims[3].toInt()
            if (heatW != tileW || heatH != tileH) {
                Log.w(TAG, "heat ${heatW}x$heatH != feed ${tileW}x$tileH")
                return false
            }
            val tileHeat = NativeImageUtils.heatmapToUInt8Array(output) ?: return false
            for (ty in 0 until tileH) {
                val dstRow = (oy + ty) * outer + ox
                val srcRow = ty * tileW
                for (tx in 0 until tileW) {
                    val v = tileHeat[srcRow + tx].toInt() and 0xff
                    val u = combined[dstRow + tx].toInt() and 0xff
                    if (v > u) combined[dstRow + tx] = tileHeat[srcRow + tx]
                }
            }
            return true
        }

        private fun finishTiled(
            combined: ByteArray,
            outer: Int,
            maxBoxes: Int,
            detMode: String,
            liteH: Int,
            liteW: Int,
            inputNocopy: Boolean,
        ): DetOut? {
            val nativeRes = NativeImageUtils.processHeatmapU8(
                combined, outer, outer,
                HEAT_THR_U8_GE1, 10f,
                NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT, 0, maxBoxes,
            )
            val boxes = mutableListOf<Rect>()
            val hist = IntArray(100)
            if (nativeRes != null) {
                val boxFloats = nativeRes.size - 100
                val nboxes = boxFloats / 9
                for (i in 0 until nboxes) {
                    val o = i * 9
                    val xs = floatArrayOf(nativeRes[o], nativeRes[o + 2], nativeRes[o + 4], nativeRes[o + 6])
                    val ys = floatArrayOf(nativeRes[o + 1], nativeRes[o + 3], nativeRes[o + 5], nativeRes[o + 7])
                    boxes.add(
                        Rect(xs.min().toInt(), ys.min().toInt(), xs.max().toInt(), ys.max().toInt()),
                    )
                }
                for (i in 0 until 100) hist[i] = nativeRes[boxFloats + i].toInt()
            }
            return DetOut(
                boxesHeat = boxes,
                heatW = outer,
                heatH = outer,
                tier = outer,
                hist = hist,
                detMode = detMode,
                heatU8 = combined.copyOf(),
                liteH = liteH,
                liteW = liteW,
                inputNocopy = inputNocopy,
            )
        }

        private fun postFromTensor(
            p: PaddlePredictor,
            tier: Int,
            detMode: String,
            maxBoxes: Int,
            liteH: Int,
            liteW: Int,
            inputNocopy: Boolean,
        ): DetOut? {
            val output = p.getOutput(0)
            val dims = output.shape()
            val heatH = dims[2].toInt()
            val heatW = dims[3].toInt()
            val nativeRes = NativeImageUtils.processHeatmap(
                output,
                HEAT_THR_U8_GE1,
                10f,
                NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                0,
                maxBoxes,
            )
            val boxes = mutableListOf<Rect>()
            if (nativeRes != null) {
                val boxFloats = nativeRes.size - 100
                val nboxes = boxFloats / 9
                for (i in 0 until nboxes) {
                    val o = i * 9
                    val xs = floatArrayOf(nativeRes[o], nativeRes[o + 2], nativeRes[o + 4], nativeRes[o + 6])
                    val ys = floatArrayOf(nativeRes[o + 1], nativeRes[o + 3], nativeRes[o + 5], nativeRes[o + 7])
                    boxes.add(
                        Rect(xs.min().toInt(), ys.min().toInt(), xs.max().toInt(), ys.max().toInt()),
                    )
                }
            }
            val hist = IntArray(100)
            if (nativeRes != null) {
                val boxFloats = nativeRes.size - 100
                for (i in 0 until 100) hist[i] = nativeRes[boxFloats + i].toInt()
            }
            val heatU8 = NativeImageUtils.heatmapToUInt8Array(output)
            return DetOut(
                boxes, heatW, heatH, tier, hist, detMode, heatU8,
                liteH = liteH, liteW = liteW, inputNocopy = inputNocopy,
            )
        }
    }

    /** Det result: product u8 heat only (no float heatmaps). */
    private data class DetOut(
        val boxesHeat: List<Rect>,
        val heatW: Int,
        val heatH: Int,
        val tier: Int,
        val hist: IntArray,
        val detMode: String = "single",
        /** Full outer×outer u8 heat (single feed or max-merged tiles). */
        val heatU8: ByteArray? = null,
        val liteH: Int = 0,
        val liteW: Int = 0,
        /** True when H-span used ShareExternal with no JVM array copy. */
        val inputNocopy: Boolean = false,
    ) {
        val liteTile: Int get() = max(liteH, liteW)
    }

    suspend fun run(
        context: Context,
        maxPhotos: Int? = null,
        /**
         * If non-null, only these basenames (exact name match).
         * Use [SelectedSamplePhotos.MULTI_SCALE] for coverage sample (pump+dash+expense).
         */
        allowNames: Collection<String>? = null,
        /** If non-null, only these domains (e.g. setOf("pump","dash")). */
        allowDomains: Set<String>? = null,
        onLog: (String) -> Unit = {},
        onProgress: (done: Int, total: Int, name: String) -> Unit = { _, _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val arch = modelArchForDevice()
        val models = availableModels(context, arch)
        if (models.isEmpty()) {
            throw IllegalStateException("No exp_det_ab models for arch=$arch under assets/$ASSET_DIR")
        }
        val allow = allowNames?.toSet()
        val photosAll = collectPhotos(context).filter { ref ->
            (allowDomains == null || ref.domain in allowDomains) &&
                (allow == null || ref.displayName in allow || ref.file.name in allow)
        }
        val photos = if (maxPhotos != null) photosAll.take(maxPhotos) else photosAll
        if (photos.isEmpty()) {
            throw IllegalStateException(
                "No photos in pump/dash/expense dirs" +
                    (if (allow != null) " matching allowlist (${allow.size})" else ""),
            )
        }
        val nPhotos = photos.size
        val matrix = buildScaleTileMatrix()
        val passes = buildScaleTilePasses(matrix)
        val nRows = matrix.size
        val nModels = models.size
        val modelsCompute = modelComputeOrder(models)
        val cellOrder = buildCellOrder(models, nPhotos, matrix, passes)
        val nCells = cellOrder.size
        // Key: model, photo, matrix-row index
        val idByKey = HashMap<Triple<Int, Int, Int>, Int>(nCells)
        for (c in cellOrder) idByKey[Triple(c.mi, c.pi, c.row)] = c.id
        val counts = countByDomain(photos)
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        // Flat publishable layout under multi_scale_det_reports/ (timestamp in basenames).
        // Only tray is a subdirectory: cells/ holds pre-merge fragments, then is purged.
        val outDir = reportDir(context)
        val cellsDir = File(outDir, "cells").also { it.mkdirs() }
        val htmlFile = File(outDir, "multi_scale_det_report_$ts.html")
        val sparseFile = File(outDir, "multi_scale_det_results_$ts.sparse")
        val statusFile = File(outDir, "multi_scale_det_status_$ts.json")
        val manifestFile = File(outDir, "multi_scale_det_manifest_$ts.json")
        val cursorFile = File(outDir, "multi_scale_det_cursor_$ts.txt")
        // Run-scoped mono only — never reuse flat mono_* or another run's cache dir.
        // Prior runs used outDir/mono_S*_pNNN_c*.png keyed only by index; different
        // photo lists made dash/expense 4096 load leftover pump monochromes.
        val monoCacheDir = File(outDir, "mono_cache_$ts").also { it.mkdirs() }
        val purgedLegacy = purgeLegacyMonoCaches(outDir, keepDirName = monoCacheDir.name)

        val mem0 = ProcessMemProbe.snapshot()
        onLog(
            "START arch=$arch modelsDisplay=$models modelsCompute=$modelsCompute " +
                "nPhotos=$nPhotos nRows=$nRows nCells=$nCells " +
                "pump=${counts["pump"] ?: 0} dash=${counts["dash"] ?: 0} " +
                "expense=${counts["expense"] ?: 0} " +
                "strategies=AUTO→single|hspan|vspan outers=${OUTER_SCALES} " +
                "maxLite=" + modelsCompute.joinToString { "$it=${maxLiteSideForModel(it)}" } + " " +
                "order=pass×model×photo×row 1pred collapse=${COLLAPSE_PERIOD_MS}ms " +
                "sys_avail_kb=${mem0.memAvailableKb} sys_effective_kb=${mem0.effectiveAvailKb()} " +
                "self_pss_kb=${mem0.pssKb} sys_total_kb=${mem0.memTotalKb} " +
                "out=${outDir.name} ts=$ts monoCache=${monoCacheDir.name} " +
                "purged_legacy_mono=$purgedLegacy",
        )
        logMem("before_releaseAll", onLog)

        NativePaddleEngine.releaseAllPredictors("multi_scale_det_begin")
        logMem("after_releaseAll_predictors", onLog)

        // ── Stage 1: sparse publishable reports ──────────────────────────────
        writeManifest(
            manifestFile, ts, arch, models, photos, counts, nCells, matrix, passes,
            cellOrder, modelsCompute,
        )
        writeSparseHtml(htmlFile, ts, arch, models, photos, counts, matrix, idByKey)
        writeSparseResults(sparseFile, ts, arch, models, photos, counts, cellOrder, matrix)
        // Pre-publish over-maxLite + domain-policy blanks so stage-1 matches compute.
        var preSkip = 0
        for (c in cellOrder) {
            val mName = models.getOrNull(c.mi) ?: continue
            val ref = photos.getOrNull(c.pi) ?: continue
            val reason = cellSkipReason(
                mName, ref.domain, c.strategy, c.outer,
                contentW = ref.nativeW, contentH = ref.nativeH,
            ) ?: continue
            publishCell(
                cellsDir, c.id,
                htmlBody = "",
                jsonBody = skipJson(c, models, photos, reason),
            )
            preSkip++
        }
        cursorFile.writeText("0")
        writeStatus(statusFile, ts, "stage1_done", preSkip, nCells, 0, "pre_skip=$preSkip")

        onLog(
            "STAGE1 html=${htmlFile.name} sparse=${sparseFile.name} cells=$nCells " +
                "pre_skip_blank=$preSkip maxLite=" +
                models.joinToString { "$it=${maxLiteSideForModel(it)}" },
        )
        logMem("after_stage1", onLog)

        val collapser = ReportCollapser(
            htmlFile = htmlFile,
            sparseFile = sparseFile,
            cellsDir = cellsDir,
            cursorFile = cursorFile,
            statusFile = statusFile,
            ts = ts,
            nCells = nCells,
            cellOrder = cellOrder,
            onLog = onLog,
        )
        val collapseJob = collapser.start(this)

        // Prefer production global BufferSets A/B (4096×4096) so we do not double-allocate.
        var slot: MonoDetPredictor? = null
        val ownBuffers: Boolean
        val master: BufferSet
        val workspace: BufferSet
        if (NativePaddleEngine.isAvailableGlobally) {
            master = NativePaddleEngine.bufferSetA
            workspace = NativePaddleEngine.bufferSetB
            ownBuffers = false
            onLog(
                "BUF reuse global bufferSetA/B logical=" +
                    "${master.width}x${master.height} / ${workspace.width}x${workspace.height} " +
                    "(deskew=${NativePaddleEngine.deskewBufferSetLarge.width}x" +
                    "${NativePaddleEngine.deskewBufferSetLarge.height} not used here)",
            )
            master.resize(MAX_SIDE, MAX_SIDE)
            workspace.resize(MAX_SIDE, MAX_SIDE)
            logBuffer("master_after_ensure", master, onLog)
            logBuffer("workspace_after_ensure", workspace, onLog)
        } else {
            master = BufferSet(MAX_SIDE, MAX_SIDE)
            workspace = BufferSet(MAX_SIDE, MAX_SIDE)
            ownBuffers = true
            onLog("BUF allocate private master+workspace ${MAX_SIDE}x$MAX_SIDE (globals not ready)")
        }
        logMem("after_buffers_ready", onLog)
        // preSkip tray cells already published at stage-1 (blank over-maxLite slots).
        var cellsDone = preSkip

        fun teardownPredictors(reason: String) {
            slot?.close()
            slot = null
            NativePaddleEngine.releaseAllPredictors(reason)
            try {
                master.clearCrops()
            } catch (_: Throwable) {
            }
            try {
                workspace.clearCrops()
            } catch (_: Throwable) {
            }
            System.gc()
            onLog("TEARDOWN $reason (predictors closed; BufferSets keep capacity)")
            logMem("after_teardown_$reason", onLog)
        }

        /** End of cell: crops + overlay temps only. */
        fun flushCellScratch() {
            try {
                workspace.clearCrops()
            } catch (_: Throwable) {
            }
        }

        /**
         * One pass: mono cache for max outer in pass; models light→heavy; one predictor;
         * feed buffers for outers + all Lite tile sides used in this pass.
         */
        suspend fun runPass(
            pass: Int,
            passRows: List<ScaleTileRow>,
            passLabel: String,
        ) {
            if (passRows.isEmpty()) return
            val passCells = cellOrder.filter { it.pass == pass }
            val ingestCap = passRows.maxOf { it.outer }.coerceAtMost(MAX_SIDE)
            val feedByTier: MutableMap<Int, ByteArray> = LinkedHashMap()
            for (cfg in passRows) {
                val side = outerSideForScale(cfg.outer)
                feedByTier.getOrPut(side) { ByteArray(side * side) }
            }
            // AUTO: also ensure a plane at each model maxLite (single @ that side / tile S).
            for (mName in models) {
                val s = maxLiteSideForModel(mName)
                feedByTier.getOrPut(s) { ByteArray(s * s) }
            }
            onLog(
                "PASS $passLabel rows=${passRows.map { it.label }} cells=${passCells.size} " +
                    "ingestCap=$ingestCap feedTiers=${feedByTier.keys.sorted()} " +
                    "models=${modelComputeOrder(models)}",
            )
            logMem("pass_begin_$passLabel", onLog)

            // Per-pass mono files under this run's monoCacheDir only. Always re-decode
            // for the pass (share across models in-pass via these files — never across runs).
            val monoCacheFiles = ArrayList<File>(nPhotos)
            val cacheOk = BooleanArray(nPhotos)
            for (pi in photos.indices) {
                val ref = photos[pi]
                val cacheFile = File(
                    monoCacheDir,
                    "mono_${passLabel}_p%03d_%s_c%d.png".format(
                        Locale.US,
                        pi,
                        safeMonoCacheToken(ref.displayName),
                        ingestCap,
                    ),
                )
                // Drop any same-path leftover from a crashed earlier attempt of *this* run.
                if (cacheFile.isFile) cacheFile.delete()
                monoCacheFiles.add(cacheFile)
                onLog(
                    "CACHE $passLabel photo=${pi + 1}/$nPhotos ${ref.displayName} → ${cacheFile.name}",
                )
                logMem("before_decode_${passLabel}_p$pi", onLog)
                if (!ingestPhotoToMaster(context, ref, master, ingestCap, onLog)) {
                    cacheOk[pi] = false
                    continue
                }
                logMem("after_decode_${passLabel}_p$pi", onLog)
                try {
                    val ok = Imgcodecs.imwrite(cacheFile.absolutePath, master.p.mat)
                    cacheOk[pi] = ok && cacheFile.isFile && cacheFile.length() > 100
                    if (!ok) onLog("cache write fail ${cacheFile.name}")
                } catch (t: Throwable) {
                    cacheOk[pi] = false
                    onLog("cache write err: ${t.message}")
                }
                master.clearCrops()
                System.gc()
            }
            logMem("after_cache_$passLabel", onLog)

            for (mName in modelComputeOrder(models)) {
                val mi = models.indexOf(mName)
                if (mi < 0) continue
                val skipReason = computeSkipReason(mName)
                if (skipReason != null) {
                    onLog("SKIP model=$mName $passLabel: $skipReason")
                    val toSkip = passCells.filter { it.mi == mi }.sortedBy { it.id }
                    for (c in toSkip) {
                        publishCell(
                            cellsDir, c.id,
                            htmlBody = "<span class='stat' style='color:#fa0'>${esc(skipReason)}</span>",
                            jsonBody = errorJson(c, models, photos, skipReason),
                        )
                        cellsDone++
                        onProgress(cellsDone, nCells, "$passLabel SKIP $mName id=${c.id}")
                    }
                    continue
                }
                slot?.close()
                slot = null
                NativePaddleEngine.releaseAllPredictors("before_model_${mName}_$passLabel")
                // Drop large heat caches from prior model; encourage reclaim before wider nets.
                System.gc()
                logMem("before_load_${mName}_$passLabel", onLog)

                val nb = copyModelAsset(context, mName, arch)
                val maxLite = maxLiteSideForModel(mName)
                onLog(
                    "load $mName ← ${nb.name} (${nb.length() / 1024} KB) " +
                        "[$passLabel only_pred mi=$mi maxLite=$maxLite]",
                )
                val tLoad0 = System.currentTimeMillis()
                slot = MonoDetPredictor(mName, nb, feedByTier, maxLiteSide = maxLite)
                onLog(
                    "load_done $mName t=${System.currentTimeMillis() - tLoad0}ms " +
                        "maxLite=${slot!!.modelMaxLiteSide()}",
                )
                logMem("after_load_${mName}_$passLabel", onLog)

                for (pi in photos.indices) {
                    val ref = photos[pi]
                    val photoCells = passCells
                        .filter { it.mi == mi && it.pi == pi }
                        .sortedWith(
                            // Match compute order: small outer first, then strategy.
                            compareBy<CellRef> { it.outer }
                                .thenBy { it.strategy.ordinal },
                        )
                    if (photoCells.isEmpty()) continue
                    onProgress(cellsDone, nCells, "$passLabel $mName ${ref.domain}/${ref.displayName}")
                    onLog("WORK $passLabel model=$mName photo=${pi + 1}/$nPhotos ${ref.displayName}")

                    val cacheFile = monoCacheFiles[pi]
                    if (!cacheOk[pi] || !loadMonoCache(cacheFile, master)) {
                        for (c in photoCells) {
                            if (cellSkipReason(
                                    mName, ref.domain, c.strategy, c.outer,
                                    ref.nativeW, ref.nativeH,
                                ) != null
                            ) {
                                continue
                            }
                            publishCell(
                                cellsDir, c.id,
                                htmlBody = "<span class='stat' style='color:#f66'>ingest_fail</span>",
                                jsonBody = errorJson(c, models, photos, "ingest_fail"),
                            )
                            cellsDone++
                        }
                        master.clearCrops()
                        continue
                    }
                    logBuffer("master_photo$pi", master, onLog)
                    val fullW = master.p.width
                    val fullH = master.p.height
                    val maxBoxes = if (ref.domain == "expense") {
                        NativeImageUtils.HEATMAP_MAX_BOXES_EXPENSE
                    } else {
                        NativeImageUtils.HEATMAP_MAX_BOXES_DEFAULT
                    }

                    for (c in photoCells) {
                        // Stage-1 blanks + re-check with mono size for expense H/V.
                        val skipNow = cellSkipReason(
                            mName, ref.domain, c.strategy, c.outer,
                            contentW = fullW, contentH = fullH,
                        )
                        if (skipNow != null) continue

                        val le = c.outer
                        val (resolvedStrat, _) = resolveFeedStrategy(
                            mName, ref.domain, le, fullW, fullH,
                        )
                        val feedStrat = resolvedStrat
                            ?: continue // should not happen after skip check
                        val (feedH, feedW) = feedHw(mName, feedStrat, le)
                        val srcW = master.p.width
                        val srcH = master.p.height
                        val currentLong = max(srcW, srcH)
                        val scaleFactor =
                            if (currentLong <= le) 1.0f else le.toFloat() / currentLong
                        val contentW = (srcW * scaleFactor).toInt().coerceAtLeast(1)
                        val contentH = (srcH * scaleFactor).toInt().coerceAtLeast(1)
                        val (ovNum, ovDen) = overlapNumDenForDomain(ref.domain)

                        var outerId = -1
                        var innerId = -1
                        try {
                            workspace.resize(contentW, contentH)
                            workspace.clearCrops()
                            Imgproc.resize(
                                master.p.mat,
                                workspace.p.mat,
                                Size(contentW.toDouble(), contentH.toDouble()),
                                0.0,
                                0.0,
                                Imgproc.INTER_AREA,
                            )
                            val ids = PumpCostVolUtils.prepareScale(workspace, le)
                            outerId = ids.first
                            innerId = ids.second
                            val outer = workspace.c[outerId]
                            val outerSide = outerSideForScale(le)
                            onLog(
                                "DETECT_BEGIN id=${c.id} $mName outer=$le strategy=$feedStrat " +
                                    "feed=${feedH}x$feedW content=${contentW}x$contentH " +
                                    "feedOuter=$outerSide maxBoxes=$maxBoxes native=${fullW}x$fullH " +
                                    "ov=$ovNum/$ovDen domain=${ref.domain}",
                            )
                            val t0 = System.currentTimeMillis()
                            val det = try {
                                withDetectMemSampling(c.id, onLog, intervalMs = 250L) {
                                    slot!!.detect(
                                        outer.mat,
                                        contentW,
                                        contentH,
                                        outerSide = outerSide,
                                        strategy = feedStrat,
                                        maxBoxes = maxBoxes,
                                        overlapNum = ovNum,
                                        overlapDen = ovDen,
                                    )
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "detect id=${c.id}: ${t.message}", t)
                                onLog("DETECT_FAIL id=${c.id} ${t.javaClass.simpleName}: ${t.message}")
                                logMem("after_detect_fail_id${c.id}", onLog)
                                publishCell(
                                    cellsDir, c.id,
                                    htmlBody = "<span class='stat' style='color:#f66'>${esc(t.message ?: "err")}</span>",
                                    jsonBody = errorJson(c, models, photos, t.message ?: "err"),
                                )
                                cellsDone++
                                continue
                            }
                            val tDet = System.currentTimeMillis() - t0
                            // Deskew angles from u8 heat (thr u≥1 / u≥2). Native path stays on
                            // u8 mask — no 4096² float expansion (that OOMed multi-scale on emu).
                            var angleGt0 = 0f
                            var angleGt1 = 0f
                            val hu8 = det?.heatU8
                            if (hu8 != null && det.heatW > 0 && det.heatH > 0 &&
                                hu8.size >= det.heatW * det.heatH
                            ) {
                                try {
                                    angleGt0 = NativeImageUtils.heatmapToAngleU8(
                                        hu8, det.heatW, det.heatH, HEAT_THR_U8_GE1,
                                    )
                                    angleGt1 = NativeImageUtils.heatmapToAngleU8(
                                        hu8, det.heatW, det.heatH, HEAT_THR_U8_GE2,
                                    )
                                } catch (t: Throwable) {
                                    Log.w(TAG, "deskew angle skip id=${c.id}: ${t.message}")
                                }
                            }
                            onLog(
                                "DETECT_OK id=${c.id} t=${tDet}ms boxes=${det?.boxesHeat?.size ?: -1} " +
                                    "mode=${det?.detMode ?: "?"} feed=${det?.liteH}x${det?.liteW} " +
                                    "nocopy=${det?.inputNocopy} ang0=$angleGt0 ang1=$angleGt1",
                            )
                            if (det == null) {
                                publishCell(
                                    cellsDir, c.id,
                                    htmlBody = "<span class='stat' style='color:#f66'>detect_null</span>",
                                    jsonBody = errorJson(c, models, photos, "detect_null"),
                                )
                            } else {
                                val mass = if (det.hist.isNotEmpty()) det.hist.drop(1).sum() else 0
                                val contentGray = workspace.p.mat
                                val redsContent = det.boxesHeat
                                // Blue = interior-energy expand (no jump); orange = same + jump.
                                val blueOpts = ContentExpandUtils.ExpandOptions(
                                    maxFrac = 1.0f,
                                    enableJump = false,
                                )
                                val orangeOpts = ContentExpandUtils.ExpandOptions(
                                    maxFrac = 1.0f,
                                    enableJump = true,
                                    jumpFrac = 0.40f,
                                )
                                val bluesContent = redsContent.map { seed ->
                                    ContentExpandUtils.expand(
                                        contentGray, seed,
                                        ContentExpandUtils.Mode.INTERIOR_ENERGY, blueOpts,
                                    )
                                }
                                val orangesContent = redsContent.map { seed ->
                                    ContentExpandUtils.expand(
                                        contentGray, seed,
                                        ContentExpandUtils.Mode.INTERIOR_ENERGY, orangeOpts,
                                    )
                                }
                                val redsFull = ArrayList<Rect>(redsContent.size)
                                val bluesFull = ArrayList<Rect>(redsContent.size)
                                val orangesFull = ArrayList<Rect>(redsContent.size)
                                for (i in redsContent.indices) {
                                    val rf = mapBox(redsContent[i], contentW, contentH, fullW, fullH)
                                        ?: continue
                                    val bf = mapBox(bluesContent[i], contentW, contentH, fullW, fullH)
                                        ?: rf
                                    val of = mapBox(orangesContent[i], contentW, contentH, fullW, fullH)
                                        ?: bf
                                    redsFull.add(rf)
                                    bluesFull.add(bf)
                                    orangesFull.add(of)
                                }
                                val overlayB64 = renderOverlayB64(
                                    contentGray,
                                    det.heatU8,
                                    det.heatW,
                                    det.heatH,
                                    contentW,
                                    contentH,
                                    contentW,
                                    contentH,
                                    redsContent,
                                    bluesContent,
                                    orangesContent,
                                )
                                val angDiff = kotlin.math.abs(angleGt0 - angleGt1)
                                val htmlBody = buildString {
                                    if (overlayB64.isNotEmpty()) {
                                        append("<img src='data:image/jpeg;base64,$overlayB64' loading='lazy'/>")
                                    }
                                    val delta = if (angDiff > 0.01f) " <b>Δ</b>" else ""
                                    append(
                                        "<div class='stat'>boxes=${redsFull.size} mass=$mass " +
                                            "t=${tDet}ms ${feedStrat.name.lowercase()} " +
                                            "feed=${det?.liteH}x${det?.liteW}" +
                                            (if (det?.inputNocopy == true) " nocopy" else "") +
                                            " ov=$ovNum/$ovDen" +
                                            "<br>" +
                                            "ang≥1=${"%.2f".format(Locale.US, angleGt0)}° " +
                                            "ang≥2=${"%.2f".format(Locale.US, angleGt1)}°" +
                                            delta +
                                            " · blue=P orange=P+jump" +
                                            "</div>",
                                    )
                                }
                                val jsonBody = successJson(
                                    c, models, photos, tDet, det, mass,
                                    redsFull, bluesFull, orangesFull,
                                    contentW, contentH, fullW, fullH,
                                    angleGt0, angleGt1, maxBoxes,
                                    overlapNum = ovNum, overlapDen = ovDen,
                                    feedStrategy = feedStrat,
                                )
                                publishCell(cellsDir, c.id, htmlBody, jsonBody)
                            }
                        } finally {
                            if (innerId >= 0) {
                                try {
                                    workspace.c[innerId].release()
                                } catch (_: Throwable) {
                                }
                            }
                            if (outerId >= 0) {
                                try {
                                    workspace.c[outerId].release()
                                } catch (_: Throwable) {
                                }
                            }
                            flushCellScratch()
                        }
                        cellsDone++
                        onProgress(
                            cellsDone, nCells,
                            "$passLabel $mName id=${c.id} ${c.rowLabel}",
                        )
                    }
                    master.clearCrops()
                }
                slot?.close()
                slot = null
                NativePaddleEngine.releaseAllPredictors("after_model_${mName}_$passLabel")
                System.gc()
                logMem("after_model_${mName}_$passLabel", onLog)
            }
            feedByTier.clear()
            System.gc()
            try {
                for (f in monoCacheFiles) f.delete()
            } catch (_: Throwable) {
            }
            logMem("pass_end_$passLabel", onLog)
        }

        var runError: Throwable? = null
        try {
            passes.forEachIndexed { pass, passRows ->
                val maxO = passRows.maxOfOrNull { it.outer } ?: 0
                val label = when {
                    maxO >= 4096 -> "S4096"
                    maxO >= 2048 -> "S2048"
                    else -> "S_main"
                }
                if (pass > 0) teardownPredictors("between_$label")
                runPass(pass, passRows, passLabel = label)
            }
        } catch (t: Throwable) {
            runError = t
            onLog("RUN_ERROR ${t.javaClass.simpleName}: ${t.message}")
            Log.e(TAG, "runPass failed", t)
        } finally {
            teardownPredictors("multi_scale_det_end")
            // Wake collapser out of the 60s schedule delay and merge all tray cells now.
            collapser.requestFinal()
            try {
                collapseJob.join()
            } catch (_: CancellationException) {
            }
            collapser.drainAll()
            try {
                monoCacheDir.listFiles()?.forEach { it.delete() }
                monoCacheDir.delete()
            } catch (_: Throwable) {
            }
            if (ownBuffers) {
                master.release()
                workspace.release()
            } else {
                master.clearCrops()
                workspace.clearCrops()
            }
            val phase = if (runError == null) "done" else "failed"
            writeStatus(
                statusFile, ts, phase, collapser.cursor, nCells, collapser.cursor,
                runError?.message ?: "complete",
            )
            logMem("run_finally", onLog)
        }
        if (runError != null) throw runError

        val msg = "DONE arch=$arch nPhotos=$nPhotos nCells=$nCells cursor=${collapser.cursor} ts=$ts"
        onLog(msg)
        Result(outDir, sparseFile, nPhotos, models, arch, msg)
    }

    /**
     * Sanitize photo basename for mono cache filenames (index alone is not identity).
     */
    private fun safeMonoCacheToken(displayName: String): String =
        displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifEmpty { "photo" }

    /**
     * Remove cross-run mono leftovers in [outDir]: flat `mono_*.png` and any prior
     * `mono_cache_*` directories except [keepDirName] (this run). Returns delete count.
     */
    private fun purgeLegacyMonoCaches(outDir: File, keepDirName: String): Int {
        var n = 0
        val children = outDir.listFiles() ?: return 0
        for (f in children) {
            val name = f.name
            when {
                f.isFile && name.startsWith("mono_") && name.endsWith(".png") -> {
                    if (f.delete()) n++
                }
                f.isDirectory && name.startsWith("mono_cache_") && name != keepDirName -> {
                    f.listFiles()?.forEach { child ->
                        if (child.delete()) n++
                    }
                    if (f.delete()) n++
                }
            }
        }
        return n
    }

    private fun loadMonoCache(cacheFile: File, master: BufferSet): Boolean {
        if (!cacheFile.isFile) return false
        return try {
            val m = Imgcodecs.imread(cacheFile.absolutePath, Imgcodecs.IMREAD_GRAYSCALE)
            if (m.empty()) {
                m.release()
                return false
            }
            val w = m.cols()
            val h = m.rows()
            if (w > MAX_SIDE || h > MAX_SIDE) {
                m.release()
                Log.w(TAG, "loadMonoCache too large ${w}x$h")
                return false
            }
            // Logical size only — master already has MAX_SIDE capacity.
            master.resize(w, h)
            master.clearCrops()
            m.copyTo(master.p.mat)
            m.release()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "loadMonoCache ${cacheFile.name}: ${t.message}")
            false
        }
    }

    /**
     * Decode DNG/JPEG into a **short-lived** BufferSet (may need full RGB/native size),
     * then mono downscale into long-lived [master] (pre-sized to [MAX_SIDE]).
     */
    private suspend fun ingestPhotoToMaster(
        context: Context,
        ref: PhotoRef,
        master: BufferSet,
        ingestCap: Int,
        onLog: (String) -> Unit,
    ): Boolean {
        val (imgW, imgH) = try {
            ImageIngestionProvider.probeDimensions(context, ref.file.absolutePath)
        } catch (t: Throwable) {
            onLog("probe fail ${ref.displayName}: ${t.message}")
            return false
        }
        if (imgW <= 0 || imgH <= 0) return false

        val decode = BufferSet(imgW, imgH)
        try {
            try {
                ImageIngestionProvider.ingestFromFile(context, ref.file.absolutePath, decode.p)
            } catch (t: Throwable) {
                onLog("ingest fail ${ref.displayName}: ${t.message}")
                return false
            }
            val srcW = decode.p.width
            val srcH = decode.p.height
            val srcLong = max(srcW, srcH)
            val cap = ingestCap.coerceAtMost(MAX_SIDE)
            val scaleFactor = if (srcLong <= cap) 1.0f else cap.toFloat() / srcLong
            val nw = (srcW * scaleFactor).toInt().coerceAtLeast(1)
            val nh = (srcH * scaleFactor).toInt().coerceAtLeast(1)
            master.resize(nw, nh)
            master.clearCrops()
            Imgproc.resize(
                decode.p.mat,
                master.p.mat,
                Size(nw.toDouble(), nh.toDouble()),
                0.0,
                0.0,
                Imgproc.INTER_AREA,
            )
            return true
        } finally {
            decode.release()
        }
    }

    private fun mapBox(r: Rect, contentW: Int, contentH: Int, fullW: Int, fullH: Int): Rect? {
        if (contentW <= 0 || contentH <= 0 || fullW <= 1 || fullH <= 1) return null
        val fl = (r.left * fullW.toFloat() / contentW).roundToInt().coerceIn(0, fullW - 1)
        val ft = (r.top * fullH.toFloat() / contentH).roundToInt().coerceIn(0, fullH - 1)
        // fr/fb must stay >= fl+1 / ft+1; empty coerceIn ranges throw IllegalArgumentException.
        val fr = (r.right * fullW.toFloat() / contentW).roundToInt().coerceIn(0, fullW).coerceAtLeast(fl + 1)
        val fb = (r.bottom * fullH.toFloat() / contentH).roundToInt().coerceIn(0, fullH).coerceAtLeast(ft + 1)
        val rr = Rect(fl, ft, fr.coerceAtMost(fullW), fb.coerceAtMost(fullH))
        return if (rr.width() > 1 && rr.height() > 1) rr else null
    }

    /** Atomic tray publish: write .part (fsync), renameTo final. Caller must drop body refs after. */
    private fun publishCell(
        cellsDir: File,
        id: Int,
        htmlBody: String,
        jsonBody: String,
    ) {
        val tag = idTag(id)
        val htmlPart = File(cellsDir, "$tag.html.part")
        val htmlFinal = File(cellsDir, "$tag.html")
        val jsonPart = File(cellsDir, "$tag.json.part")
        val jsonFinal = File(cellsDir, "$tag.json")
        writeTextSynced(htmlPart, htmlBody)
        writeTextSynced(jsonPart, jsonBody)
        if (!htmlPart.renameTo(htmlFinal)) {
            htmlFinal.delete()
            htmlPart.renameTo(htmlFinal)
        }
        if (!jsonPart.renameTo(jsonFinal)) {
            jsonFinal.delete()
            jsonPart.renameTo(jsonFinal)
        }
    }

    private fun writeTextSynced(file: File, text: String) {
        FileOutputStream(file).use { fos ->
            fos.write(text.toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
    }

    private fun errorJson(
        c: CellRef,
        models: List<String>,
        photos: List<PhotoRef>,
        err: String,
    ): String = JSONObject()
        .put("id", c.id)
        .put("id_tag", idTag(c.id))
        .put("model", models[c.mi])
        .put("model_idx", c.mi)
        .put("photo_idx", c.pi)
        .put("photo", photos[c.pi].displayName)
        .put("domain", photos[c.pi].domain)
        .put("outer", c.outer)
        .put("strategy", c.strategy.name)
        .put("row", c.row)
        .put("row_label", c.rowLabel)
        .put("scale", c.outer)
        .put("status", "error")
        .put("error", err)
        .toString()

    /** Over-maxLite / wrong strategy: blank HTML; status=skip so the grid is not a false fill. */
    private fun skipJson(
        c: CellRef,
        models: List<String>,
        photos: List<PhotoRef>,
        reason: String,
    ): String {
        val mName = models.getOrElse(c.mi) { "?" }
        return JSONObject()
            .put("id", c.id)
            .put("id_tag", idTag(c.id))
            .put("model", mName)
            .put("model_idx", c.mi)
            .put("photo_idx", c.pi)
            .put("photo", photos.getOrNull(c.pi)?.displayName ?: "?")
            .put("domain", photos.getOrNull(c.pi)?.domain ?: "?")
            .put("outer", c.outer)
            .put("strategy", c.strategy.name)
            .put("row", c.row)
            .put("row_label", c.rowLabel)
            .put("scale", c.outer)
            .put("status", "skip")
            .put("error", reason)
            .put("lite_tile_max", maxLiteSideForModel(mName))
            .put("lite_tile_clamped", false)
            .toString()
    }

    private fun successJson(
        c: CellRef,
        models: List<String>,
        photos: List<PhotoRef>,
        tDetMs: Long,
        det: DetOut,
        mass: Int,
        reds: List<Rect>,
        blues: List<Rect>,
        oranges: List<Rect>,
        contentW: Int,
        contentH: Int,
        fullW: Int,
        fullH: Int,
        deskewAngleGt0: Float,
        deskewAngleGt1: Float,
        maxBoxes: Int,
        overlapNum: Int,
        overlapDen: Int,
        feedStrategy: TileStrategy,
    ): String {
        val boxesJa = JSONArray()
        for (i in reds.indices) {
            val r = reds[i]
            val b = blues.getOrNull(i) ?: r
            val o = oranges.getOrNull(i) ?: b
            boxesJa.put(
                JSONObject()
                    .put("red", JSONArray(listOf(r.left, r.top, r.right, r.bottom)))
                    .put("blue", JSONArray(listOf(b.left, b.top, b.right, b.bottom)))
                    .put("orange", JSONArray(listOf(o.left, o.top, o.right, o.bottom)))
                    .put("blue_expand", "INTERIOR_ENERGY maxFrac=1.0 jump=off")
                    .put("orange_expand", "INTERIOR_ENERGY maxFrac=1.0 jump=on jumpFrac=0.40"),
            )
        }
        return JSONObject()
            .put("id", c.id)
            .put("id_tag", idTag(c.id))
            .put("model", models[c.mi])
            .put("model_idx", c.mi)
            .put("photo_idx", c.pi)
            .put("photo", photos[c.pi].displayName)
            .put("domain", photos[c.pi].domain)
            .put("outer", c.outer)
            .put("strategy", feedStrategy.name)
            .put("matrix_strategy", c.strategy.name)
            .put("row", c.row)
            .put("row_label", c.rowLabel)
            .put("scale", c.outer)
            .put("status", "ok")
            .put("t_det_ms", tDetMs)
            .put("tier", det.tier)
            .put("lite_h", det.liteH)
            .put("lite_w", det.liteW)
            .put("lite_tile", det.liteTile)
            .put("input_nocopy", det.inputNocopy)
            .put("lite_tile_clamped", false)
            .put("det_mode", det.detMode)
            .put("tile_overlap_num", overlapNum)
            .put("tile_overlap_den", overlapDen)
            .put("content_w", contentW)
            .put("content_h", contentH)
            .put("native_w", fullW)
            .put("native_h", fullH)
            .put("heat_w", det.heatW)
            .put("heat_h", det.heatH)
            .put("n_boxes", reds.size)
            .put("max_boxes_cap", maxBoxes)
            .put("heatmap_mass_bins1_99", mass)
            .put("boxes", boxesJa)
            .put(
                "box_geometry",
                "aabb_of_minAreaRect_vertices (display axis-aligned; not deskewed); " +
                    "blue=P expand; orange=P+jump",
            )
            .put("hist", JSONArray(det.hist.toList()))
            .put("heat_encoding", "u8_only_in_html_overlay")
            .put("deskew_angle_gt0_deg", deskewAngleGt0.toDouble())
            .put("deskew_angle_gt1_deg", deskewAngleGt1.toDouble())
            .put("deskew_thr_gt0", HEAT_THR_U8_GE1.toDouble())
            .put("deskew_thr_gt1", HEAT_THR_U8_GE2.toDouble())
            .put(
                "deskew_note",
                "heatmapToAngleU8 on composite/single u8 heat; compare to ground_truth angles later",
            )
            .toString()
    }

    private fun writeManifest(
        f: File,
        ts: String,
        arch: String,
        models: List<String>,
        photos: List<PhotoRef>,
        counts: Map<String, Int>,
        nCells: Int,
        matrix: List<ScaleTileRow>,
        passes: List<List<ScaleTileRow>>,
        cellOrder: List<CellRef>,
        modelsCompute: List<String>,
    ) {
        val photosJa = JSONArray()
        photos.forEachIndexed { i, p ->
            photosJa.put(
                JSONObject()
                    .put("idx", i)
                    .put("domain", p.domain)
                    .put("file", p.displayName)
                    .put("native_w", p.nativeW)
                    .put("native_h", p.nativeH)
                    .put(
                        "orientation",
                        when {
                            p.nativeW <= 0 || p.nativeH <= 0 -> "unknown"
                            p.nativeH >= p.nativeW -> "portrait"
                            else -> "landscape"
                        },
                    ),
            )
        }
        val matrixJa = JSONArray()
        matrix.forEachIndexed { i, r ->
            matrixJa.put(
                JSONObject()
                    .put("row", i)
                    .put("outer", r.outer)
                    .put("strategy", r.strategy.name)
                    .put("label", r.label)
                    .put("single", r.isSingle),
            )
        }
        val orderJa = JSONArray()
        for (c in cellOrder) {
            orderJa.put(
                JSONObject()
                    .put("id", c.id)
                    .put("mi", c.mi)
                    .put("pi", c.pi)
                    .put("row", c.row)
                    .put("outer", c.outer)
                    .put("strategy", c.strategy.name)
                    .put("label", c.rowLabel)
                    .put("pass", c.pass),
            )
        }
        val passesJa = JSONArray()
        for (p in passes) {
            val arr = JSONArray()
            for (r in p) arr.put(r.label)
            passesJa.put(arr)
        }
        f.writeText(
            JSONObject()
                .put("timestamp", ts)
                .put("arch", arch)
                .put("models", JSONArray(models))
                .put("models_compute", JSONArray(modelsCompute))
                .put("outer_scales", JSONArray(OUTER_SCALES))
                .put("tile_sizes", JSONArray(TILE_SIZES))
                .put("matrix", matrixJa)
                .put("n_rows", matrix.size)
                .put("passes", passesJa)
                .put("max_side", MAX_SIDE)
                .put("max_lite_side_product", maxLiteSideForModel("product_det"))
                .put("max_lite_side_v4_mobile", maxLiteSideForModel("PP-OCRv4_mobile_det"))
                .put(
                    "max_lite_note",
                    "product maxLite=${maxLiteSideForModel("product_det")}; " +
                        "v4 mobile maxLite=${maxLiteSideForModel("PP-OCRv4_mobile_det")}; " +
                        "v5 mobile not scheduled",
                )
                .put("det_tile_min_overlap_frac", DET_TILE_OVERLAP_NUM.toDouble() / DET_TILE_OVERLAP_DEN)
                .put("pump_tile_overlap_frac", PUMP_TILE_OVERLAP_NUM.toDouble() / PUMP_TILE_OVERLAP_DEN)
                .put(
                    "domain_policy",
                    JSONObject()
                        .put("pump", "no outer≥4096; H-span tiles only; tile overlap 50%")
                        .put("dash", "outer≥512; H-span tiles only")
                        .put(
                            "expense",
                            "outer≥1024; portrait→HSPAN landscape→VSPAN; one matrix row per outer",
                        )
                        .put("matrix", "one row per outer (AUTO); resolve single vs H/V at run"),
                )
                .put(
                    "expand_boxes",
                    JSONObject()
                        .put("red", "det seed")
                        .put("blue", "INTERIOR_ENERGY jump=off")
                        .put("orange", "INTERIOR_ENERGY jump=on jumpFrac=0.40"),
                )
                .put("n_photos", photos.size)
                .put("n_models", models.size)
                .put("n_cells", nCells)
                .put(
                    "compute_order",
                    "pass outer small→large; models product→v4; domain policy skips; " +
                        "tile overlap default 30% pump 50%; blue vs orange expand in JSON",
                )
                .put("file_order", "row_major_photo_matrixRow_model")
                .put("id_order", "compute_order_dense_1_to_N")
                .put(
                    "deskew",
                    "per cell heatmapToAngleU8 thr 0 and 1/255 (u≥1 / u≥2) on heatU8",
                )
                .put(
                    "box_cap",
                    "default ${NativeImageUtils.HEATMAP_MAX_BOXES_DEFAULT}; " +
                        "expense ${NativeImageUtils.HEATMAP_MAX_BOXES_EXPENSE}",
                )
                .put(
                    "memory",
                    "global bufferSetA/B ${MAX_SIDE}²; feed planes per outer+tile; " +
                        "Lite reload on shape change; MEM logs for pss",
                )
                .put("counts", countsToJson(counts))
                .put("photos", photosJa)
                .put("cell_order", orderJa)
                .put("collapse_period_ms", COLLAPSE_PERIOD_MS)
                .toString(2),
        )
    }

    private fun countsToJson(counts: Map<String, Int>): JSONObject {
        val o = JSONObject()
        for ((k, v) in counts) o.put(k, v)
        return o
    }

    private fun writeSparseHtml(
        f: File,
        ts: String,
        arch: String,
        models: List<String>,
        photos: List<PhotoRef>,
        counts: Map<String, Int>,
        matrix: List<ScaleTileRow>,
        idByKey: Map<Triple<Int, Int, Int>, Int>,
    ) {
        val nRows = matrix.size
        f.bufferedWriter().use { w ->
            w.appendLine("<!DOCTYPE html><html><head><meta charset='utf-8'/>")
            w.appendLine("<title>Multi-scale det $ts</title>")
            w.appendLine(
                "<style>" +
                    "body{font-family:sans-serif;font-size:13px;background:#111;color:#eee}" +
                    "table{border-collapse:collapse;width:100%}" +
                    "th,td{border:1px solid #444;padding:3px;vertical-align:top;text-align:center}" +
                    "th{background:#222}" +
                    "img{max-width:100%;height:auto}" +
                    ".meta{color:#9cf;font-size:12px}" +
                    ".domain-pump{color:#8f8}.domain-dash{color:#fc8}.domain-expense{color:#f8f}" +
                    ".stat{font-size:10px;color:#bbb}.pending{color:#666}" +
                    ".scale{color:#90caf9;font-weight:600;font-size:11px}" +
                    "</style></head><body>",
            )
            w.appendLine("<h1>Multi-scale det × multi-model + expand P</h1>")
            w.appendLine(
                "<p class='meta'><b>Run</b> $ts · <b>Device</b> ${Build.MODEL} · " +
                    "<b>Version</b> ${BuildConfig.VERSION_NAME} · <b>arch</b> $arch · " +
                    "<b>threads</b> 4 · <b>photos</b> ${photos.size} · " +
                    "pump=${counts["pump"] ?: 0} dash=${counts["dash"] ?: 0} " +
                    "expense=${counts["expense"] ?: 0} · <b>rows</b> $nRows</p>",
            )
            w.appendLine(
                "<p class='meta'>Matrix: <b>one row per outer</b> ($nRows). " +
                    "Feed = single if outer≤maxLite else H-span (pump/dash) or H/V by orientation (expense). " +
                    "maxLite product ${maxLiteSideForModel("product_det")} / " +
                    "v4 ${maxLiteSideForModel("PP-OCRv4_mobile_det")}. " +
                    "pump: no 4096, H-tile 50% ov. dash: ≥512 H-tile. expense: ≥1024. " +
                    "<b>Boxes:</b> red=seed, blue=P, <span style='color:#fa0'>orange=P+jump</span>. " +
                    "Blank = domain skip.</p>",
            )
            w.append("<table><tr><th style='width:140px'># / photo</th><th class='scale'>outer / strategy</th>")
            for (m in models) {
                val short = m.removePrefix("PP-OCR").removeSuffix("_det")
                w.append("<th>$short<br><span class='stat'>maxLite=${maxLiteSideForModel(m)}</span></th>")
            }
            w.appendLine("</tr>")

            for (pi in photos.indices) {
                val ref = photos[pi]
                for (ri in matrix.indices) {
                    val row = matrix[ri]
                    w.append("<tr>")
                    if (ri == 0) {
                        w.append(
                            "<td rowspan='$nRows'><b>#${pi + 1}</b><br>" +
                                "<span class='domain-${ref.domain}'>${ref.domain}</span><br>" +
                                "<small>${esc(ref.displayName)}</small></td>",
                        )
                    }
                    w.append("<th class='scale'>${esc(row.label)}</th>")
                    for (mi in models.indices) {
                        val id = idByKey[Triple(mi, pi, ri)]
                            ?: error("missing id for mi=$mi pi=$pi row=$ri")
                        val mName = models[mi]
                        val skip = cellSkipReason(
                            mName, ref.domain, row.strategy, row.outer,
                            ref.nativeW, ref.nativeH,
                        )
                        w.append("<td id='c${idTag(id)}'>")
                        w.append(htmlBegin(id))
                        if (skip != null) {
                            w.append("")
                        } else {
                            w.append("<span class='stat pending'>… ${idTag(id)}</span>")
                        }
                        w.append(htmlEnd(id))
                        w.append("</td>")
                    }
                    w.appendLine("</tr>")
                }
            }
            w.appendLine("</table></body></html>")
        }
    }

    private fun writeSparseResults(
        f: File,
        ts: String,
        arch: String,
        models: List<String>,
        photos: List<PhotoRef>,
        counts: Map<String, Int>,
        cellOrder: List<CellRef>,
        matrix: List<ScaleTileRow>,
    ) {
        f.bufferedWriter().use { w ->
            w.appendLine("# multi_scale_det sparse results — one record per cell id")
            w.appendLine("# id order = compute order (pass; one model at a time)")
            w.appendLine(
                JSONObject()
                    .put("_meta", true)
                    .put("timestamp", ts)
                    .put("arch", arch)
                    .put("models", JSONArray(models))
                    .put("strategies", JSONArray(listOf("AUTO", "SINGLE", "HSPAN", "VSPAN")))
                    .put("outer_scales", JSONArray(OUTER_SCALES))
                    .put("n_rows", matrix.size)
                    .put("n_cells", cellOrder.size)
                    .put("n_photos", photos.size)
                    .put("counts", countsToJson(counts))
                    .toString(),
            )
            for (c in cellOrder.sortedBy { it.id }) {
                val mName = models.getOrElse(c.mi) { "?" }
                val pref = photos.getOrNull(c.pi)
                val skip = cellSkipReason(
                    mName, pref?.domain ?: "?", c.strategy, c.outer,
                    pref?.nativeW ?: 0, pref?.nativeH ?: 0,
                )
                w.append(jsonBegin(c.id))
                w.append(
                    JSONObject()
                        .put("id", c.id)
                        .put("status", if (skip != null) "skip" else "pending")
                        .put("model", mName)
                        .put("photo", pref?.displayName ?: "?")
                        .put("domain", pref?.domain ?: "?")
                        .put("outer", c.outer)
                        .put("strategy", c.strategy.name)
                        .put("row_label", c.rowLabel)
                        .put("pass", c.pass)
                        .put("lite_tile_max", maxLiteSideForModel(mName))
                        .apply {
                            if (skip != null) put("error", skip)
                        }
                        .toString(),
                )
                w.append(jsonEnd(c.id))
                w.append('\n')
            }
        }
    }

    private fun writeStatus(
        f: File,
        ts: String,
        phase: String,
        done: Int,
        total: Int,
        cursor: Int,
        detail: String,
    ) {
        f.writeText(
            JSONObject()
                .put("timestamp", ts)
                .put("phase", phase)
                .put("cells_done", done)
                .put("cells_total", total)
                .put("collapse_cursor", cursor)
                .put("detail", detail)
                .put("updated_ms", System.currentTimeMillis())
                .toString(2),
        )
    }

    // ── Collapser ────────────────────────────────────────────────────────────

    private class ReportCollapser(
        private val htmlFile: File,
        private val sparseFile: File,
        private val cellsDir: File,
        private val cursorFile: File,
        private val statusFile: File,
        private val ts: String,
        private val nCells: Int,
        private val cellOrder: List<CellRef>,
        private val onLog: (String) -> Unit,
    ) {
        private val byId: Map<Int, CellRef> = cellOrder.associateBy { it.id }
        @Volatile var cursor: Int = 0
            private set
        @Volatile private var finalRequested = false
        private var job: Job? = null

        fun start(scope: CoroutineScope): Job {
            cursor = cursorFile.readText().trim().toIntOrNull() ?: 0
            val j = scope.launch(Dispatchers.IO) {
                try {
                    while (isActive) {
                        val n = collapseOnce(force = false)
                        if (finalRequested) {
                            // End-of-run: drain contiguous ready tray cells without 60s wait.
                            if (!hasReadyBeyond(cursor) && n == 0) break
                            // Keep merging tightly until the tray is empty from cursor forward.
                            if (n == 0) delay(50) else continue
                        } else {
                            delay(COLLAPSE_PERIOD_MS)
                        }
                    }
                    drainAll()
                } catch (c: CancellationException) {
                    // requestFinal() cancels sleep so we consolidate immediately.
                    drainAll()
                    throw c
                }
            }
            job = j
            return j
        }

        /**
         * Signal end-of-run consolidation: cancel the 60s schedule delay so the collapser
         * job wakes and drains, then [drainAll] from the run finally as a backstop.
         */
        fun requestFinal() {
            finalRequested = true
            job?.cancel(CancellationException("multi_scale_det final collapse"))
        }

        private fun hasReadyBeyond(c: Int): Boolean {
            var i = c + 1
            while (i <= nCells) {
                if (cellReady(i)) return true
                i++
            }
            return false
        }

        private fun cellReady(id: Int): Boolean {
            val tag = idTag(id)
            return File(cellsDir, "$tag.html").isFile && File(cellsDir, "$tag.json").isFile
        }

        /**
         * Merge all contiguous ready cells from [cursor] until a gap or [nCells].
         * Used at end of run so we do not wait for the next [COLLAPSE_PERIOD_MS] tick.
         */
        fun drainAll() {
            var total = 0
            var rounds = 0
            while (rounds < nCells + 2) {
                val n = collapseOnce(force = true)
                total += n
                rounds++
                if (n == 0) break
            }
            onLog(
                "COLLAPSE drainAll merged=$total cursor=$cursor/$nCells " +
                    "ready_beyond=${hasReadyBeyond(cursor)}",
            )
        }

        /**
         * Collapse contiguous ready ids (cursor+1…). Multi-splice in file (row-major) order.
         * @return number of cells merged
         */
        fun collapseOnce(force: Boolean): Int {
            // Extend contiguous run of ready cells
            val batch = ArrayList<Int>()
            var id = cursor + 1
            while (id <= nCells && cellReady(id)) {
                batch.add(id)
                id++
            }
            if (batch.isEmpty()) {
                if (force && finalRequested) {
                    // Quiet when mid-run force is rare; end-of-run drain logs via drainAll.
                } else if (force) {
                    onLog("COLLAPSE cursor=$cursor (no new cells)")
                }
                return 0
            }

            // File order: sort by (photo, matrix-row, model) = row-major walk of HTML table
            val fileOrder = batch.sortedWith(
                compareBy(
                    { byId[it]?.pi ?: 0 },
                    { byId[it]?.row ?: 0 },
                    { byId[it]?.mi ?: 0 },
                ),
            )

            onLog("COLLAPSE ids ${batch.first()}..${batch.last()} (n=${batch.size}) fileOrder=${fileOrder.size} stream")

            // Tray stays on disk — collapser streams body files; never loads report or batch into RAM.
            multiSpliceStream(
                src = htmlFile,
                orderedIds = fileOrder,
                begin = { htmlBegin(it) },
                end = { htmlEnd(it) },
                bodyFile = { File(cellsDir, "${idTag(it)}.html") },
            )
            // Sparse is one record per line — rewrite line-by-line (still O(1) cell body RAM).
            multiSpliceSparseLines(
                src = sparseFile,
                orderedIds = batch, // any order; match by marker on line
                bodyFile = { File(cellsDir, "${idTag(it)}.json") },
            )

            // Purge tray after successful splice
            for (cid in batch) {
                val tag = idTag(cid)
                File(cellsDir, "$tag.html").delete()
                File(cellsDir, "$tag.json").delete()
                File(cellsDir, "$tag.html.part").delete()
                File(cellsDir, "$tag.json.part").delete()
            }

            cursor = batch.last()
            cursorFile.writeText(cursor.toString())
            writeStatus(statusFile, ts, "collapsing", cursor, nCells, cursor, "merged ${batch.size}")
            return batch.size
        }

        /**
         * results.sparse: each cell is one line `BEGIN{json}END`. Replace matching lines
         * by streaming the tray JSON (one cell at a time).
         */
        private fun multiSpliceSparseLines(
            src: File,
            orderedIds: List<Int>,
            bodyFile: (Int) -> File,
        ) {
            if (orderedIds.isEmpty()) return
            val want = orderedIds.toHashSet()
            val begins = orderedIds.associateWith { jsonBegin(it) }
            val tmp = File(src.parentFile, src.name + ".tmp")
            tmp.delete()
            src.bufferedReader().use { reader ->
                tmp.bufferedWriter().use { writer ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val ln = line!!
                        var replaced = false
                        for (cid in orderedIds) {
                            val b = begins[cid]!!
                            if (ln.contains(b)) {
                                val body = bodyFile(cid)
                                if (body.isFile) {
                                    // Stream body without loading siblings
                                    writer.append(jsonBegin(cid))
                                    body.bufferedReader().use { br ->
                                        var chunk: String?
                                        while (br.readLine().also { chunk = it } != null) {
                                            writer.append(chunk)
                                        }
                                    }
                                    // body file is single-line JSON; if multi-line, lines joined without \n is ok for our writers
                                    writer.append(jsonEnd(cid))
                                    writer.append('\n')
                                } else {
                                    writer.append(ln)
                                    writer.append('\n')
                                }
                                replaced = true
                                break
                            }
                        }
                        if (!replaced) {
                            writer.append(ln)
                            writer.append('\n')
                        }
                    }
                }
            }
            val bak = File(src.parentFile, src.name + ".bak")
            bak.delete()
            if (src.exists()) src.renameTo(bak)
            if (!tmp.renameTo(src)) {
                tmp.copyTo(src, overwrite = true)
                tmp.delete()
            }
            bak.delete()
        }

        /**
         * Stream multi-splice: O(marker) memory.
         * 1) Scan src once with a rolling window → offsets of BEGIN/END for each id (file order).
         * 2) Copy gaps via FileChannel.transferTo; stream each tray body file into the gap.
         * 3) Atomic mv of .tmp → src.
         */
        private fun multiSpliceStream(
            src: File,
            orderedIds: List<Int>,
            begin: (Int) -> String,
            end: (Int) -> String,
            bodyFile: (Int) -> File,
        ) {
            if (orderedIds.isEmpty()) return
            val begins = orderedIds.map { begin(it).toByteArray(Charsets.UTF_8) }
            val ends = orderedIds.map { end(it).toByteArray(Charsets.UTF_8) }
            val spans = findMarkerSpans(src, begins, ends) ?: run {
                Log.e(TAG, "multiSpliceStream: marker scan failed for ${src.name}")
                return
            }

            val tmp = File(src.parentFile, src.name + ".tmp")
            tmp.delete()
            java.io.RandomAccessFile(src, "r").use { rafIn ->
                val inCh = rafIn.channel
                FileOutputStream(tmp).use { fos ->
                    val outCh = fos.channel
                    var pos = 0L
                    for (i in orderedIds.indices) {
                        val cid = orderedIds[i]
                        val bAt = spans[i * 2]
                        val eAt = spans[i * 2 + 1]
                        val bMark = begins[i]
                        val eMark = ends[i]
                        // copy [pos, bAt)
                        transferRange(inCh, outCh, pos, bAt - pos)
                        // write BEGIN + body file + END
                        writeFully(outCh, bMark)
                        val body = bodyFile(cid)
                        if (body.isFile) {
                            FileInputStream(body).use { bin ->
                                val bch = bin.channel
                                var off = 0L
                                var rem = bch.size()
                                while (rem > 0) {
                                    val n = bch.transferTo(off, rem, outCh)
                                    if (n <= 0) break
                                    off += n
                                    rem -= n
                                }
                            }
                        }
                        writeFully(outCh, eMark)
                        pos = eAt + eMark.size
                    }
                    // tail
                    val len = inCh.size()
                    if (pos < len) transferRange(inCh, outCh, pos, len - pos)
                    outCh.force(true)
                }
            }

            val bak = File(src.parentFile, src.name + ".bak")
            bak.delete()
            if (src.exists() && !src.renameTo(bak)) {
                Log.w(TAG, "rename src→bak failed ${src.name}")
            }
            if (!tmp.renameTo(src)) {
                tmp.inputStream().use { inp ->
                    FileOutputStream(src).use { out -> inp.copyTo(out, 64 * 1024) }
                }
                tmp.delete()
            }
            bak.delete()
        }

        private fun transferRange(
            from: java.nio.channels.FileChannel,
            to: java.nio.channels.FileChannel,
            position: Long,
            count: Long,
        ) {
            var pos = position
            var rem = count
            while (rem > 0) {
                val n = from.transferTo(pos, rem, to)
                if (n <= 0) break
                pos += n
                rem -= n
            }
        }

        private fun writeFully(ch: java.nio.channels.FileChannel, bytes: ByteArray) {
            val buf = java.nio.ByteBuffer.wrap(bytes)
            while (buf.hasRemaining()) ch.write(buf)
        }

        /**
         * Single forward scan; needles alternate BEGIN_i, END_i for ordered ids.
         * Returns flat [b0,e0,b1,e1,…] file offsets, or null if any marker missing.
         * Uses a fixed-size rolling window (no full-file buffer).
         */
        private fun findMarkerSpans(
            src: File,
            begins: List<ByteArray>,
            ends: List<ByteArray>,
        ): LongArray? {
            val n = begins.size
            if (n == 0) return LongArray(0)
            val needles = ArrayList<ByteArray>(n * 2)
            for (i in 0 until n) {
                needles.add(begins[i])
                needles.add(ends[i])
            }
            val maxNeedle = needles.maxOf { it.size }.coerceAtLeast(1)
            val chunk = 64 * 1024
            val window = ByteArray(chunk + maxNeedle)
            val positions = LongArray(needles.size)
            var needleIdx = 0
            var fileBase = 0L // file offset of window[0]
            var winLen = 0

            FileInputStream(src).use { fis ->
                while (needleIdx < needles.size) {
                    val needle = needles[needleIdx]
                    // Need more data if window too small
                    if (winLen < needle.size) {
                        val nread = fis.read(window, winLen, window.size - winLen)
                        if (nread <= 0) {
                            Log.e(TAG, "EOF seeking marker #$needleIdx in ${src.name}")
                            return null
                        }
                        winLen += nread
                        continue
                    }
                    val at = indexOfBytes(window, 0, winLen, needle)
                    if (at >= 0) {
                        positions[needleIdx] = fileBase + at
                        needleIdx++
                        // Consume through end of this match
                        val consume = at + needle.size
                        val remain = winLen - consume
                        if (remain > 0) {
                            System.arraycopy(window, consume, window, 0, remain)
                        }
                        fileBase += consume
                        winLen = remain
                    } else {
                        // Keep last (maxNeedle-1) bytes as overlap; drop the rest
                        val keep = maxNeedle - 1
                        if (winLen > keep) {
                            val drop = winLen - keep
                            System.arraycopy(window, drop, window, 0, keep)
                            fileBase += drop
                            winLen = keep
                        }
                        val nread = fis.read(window, winLen, window.size - winLen)
                        if (nread <= 0) {
                            Log.e(TAG, "EOF seeking marker #$needleIdx in ${src.name}")
                            return null
                        }
                        winLen += nread
                    }
                }
            }
            return positions
        }

        private fun indexOfBytes(hay: ByteArray, from: Int, to: Int, needle: ByteArray): Int {
            if (needle.isEmpty()) return from
            val last = to - needle.size
            outer@ for (i in from..last) {
                for (j in needle.indices) {
                    if (hay[i + j] != needle[j]) continue@outer
                }
                return i
            }
            return -1
        }
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /**
     * Red-tint u8 heat + red seed + blue expand (P) + orange expand (P+jump).
     * Display-sized JPEG base64. Uses product **heatU8** only.
     */
    private fun renderOverlayB64(
        gray: Mat,
        heatU8: ByteArray?,
        heatW: Int,
        heatH: Int,
        contentW: Int,
        contentH: Int,
        fullW: Int,
        fullH: Int,
        reds: List<Rect>,
        blues: List<Rect>,
        oranges: List<Rect> = emptyList(),
    ): String {
        val scale = if (fullW > PREVIEW_MAX_W) PREVIEW_MAX_W.toFloat() / fullW else 1.0f
        val pw = max(1, (fullW * scale).roundToInt())
        val ph = max(1, (fullH * scale).roundToInt())
        val graySmall = Mat()
        Imgproc.resize(gray, graySmall, Size(pw.toDouble(), ph.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        val bgr = Mat()
        Imgproc.cvtColor(graySmall, bgr, Imgproc.COLOR_GRAY2BGR)
        graySmall.release()

        if (heatU8 != null && heatW > 0 && heatH > 0 && contentW > 0 && contentH > 0 &&
            heatU8.size >= heatW * heatH
        ) {
            try {
                // Content heat → preview only (never materialize fullW×fullH float).
                val heatMat = Mat(heatH, heatW, CvType.CV_8UC1)
                heatMat.put(0, 0, heatU8)
                val cW = min(contentW, heatW)
                val cH = min(contentH, heatH)
                val contentHeat = heatMat.submat(0, cH, 0, cW)
                val heatPrev = Mat()
                Imgproc.resize(
                    contentHeat,
                    heatPrev,
                    Size(pw.toDouble(), ph.toDouble()),
                    0.0,
                    0.0,
                    Imgproc.INTER_LINEAR,
                )
                contentHeat.release()
                heatMat.release()
                // u8 heat: on if value > 0 (matches HEAT_THR_U8_GE1 / production G-dense).
                val mask = Mat()
                Core.compare(heatPrev, Scalar(0.0), mask, Core.CMP_GT)
                heatPrev.release()
                val redLayer = Mat(ph, pw, CvType.CV_8UC3, Scalar(0.0, 0.0, 220.0))
                val blended = Mat()
                Core.addWeighted(bgr, 0.55, redLayer, 0.45, 0.0, blended)
                blended.copyTo(bgr, mask)
                blended.release()
                redLayer.release()
                mask.release()
            } catch (t: Throwable) {
                Log.w(TAG, "heat fill skip: ${t.message}")
            }
        }

        fun mapR(r: Rect): org.opencv.core.Rect {
            val l = (r.left * scale).roundToInt().coerceIn(0, pw - 1)
            val t = (r.top * scale).roundToInt().coerceIn(0, ph - 1)
            val rr = (r.right * scale).roundToInt().coerceIn(l + 1, pw)
            val b = (r.bottom * scale).roundToInt().coerceIn(t + 1, ph)
            return org.opencv.core.Rect(l, t, rr - l, b - t)
        }
        for (r in reds) {
            val o = mapR(r)
            Imgproc.rectangle(
                bgr,
                Point(o.x.toDouble(), o.y.toDouble()),
                Point((o.x + o.width).toDouble(), (o.y + o.height).toDouble()),
                Scalar(0.0, 0.0, 255.0),
                2,
            )
        }
        for (b in blues) {
            val o = mapR(b)
            Imgproc.rectangle(
                bgr,
                Point(o.x.toDouble(), o.y.toDouble()),
                Point((o.x + o.width).toDouble(), (o.y + o.height).toDouble()),
                Scalar(255.0, 0.0, 0.0), // BGR blue
                2,
            )
        }
        for (or in oranges) {
            val o = mapR(or)
            Imgproc.rectangle(
                bgr,
                Point(o.x.toDouble(), o.y.toDouble()),
                Point((o.x + o.width).toDouble(), (o.y + o.height).toDouble()),
                Scalar(0.0, 165.0, 255.0), // BGR orange
                2,
            )
        }

        val rgb = Mat()
        Imgproc.cvtColor(bgr, rgb, Imgproc.COLOR_BGR2RGB)
        bgr.release()
        val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgb, bmp)
        rgb.release()
        val b64 = OcrUtils.bitmapToBase64(bmp, 72)
        bmp.recycle()
        return b64
    }
}
