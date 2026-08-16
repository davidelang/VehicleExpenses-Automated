package com.davidlang.vehicleexpensesautomated.ui.util
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class NativePaddleEngine(private val context: Context, private val variant: String = "V3") : OcrEngine {
    override val name = if (variant == "V3") "Paddle V3 Greedy" else "Paddle Numeric Greedy"
    fun isV3() = variant == "V3"

    data class DetectionBox(val points: FloatArray, val confidence: Float)
    data class DetectionResult(
        val heatmap: FloatArray? = null,
        val width: Int,
        val height: Int,
        val metadata: Map<String, String> = emptyMap(),
        val nativeBoxes: List<DetectionBox> = emptyList(),
        val outputTensor: Any? = null,
        val heatmapHist: IntArray? = null,
        /** Host u8 heat plane (tiled max-merge). Deskew angle uses this when [outputTensor] is null. */
        val heatU8: ByteArray? = null,
    ) {
        /** Deskew / Hough angle from tensor or tiled host heat. */
        fun deskewAngleCpp(threshold: Float = 0.20f): Float {
            outputTensor?.let { return NativeImageUtils.heatmapToAngle(it, threshold) }
            heatU8?.let { return NativeImageUtils.heatmapToAngleU8(it, width, height, threshold) }
            return 0f
        }
    }
    private val dictionary = mutableListOf<String>()
    private var initError: String? = null
    var isAvailable = false
        private set

    // Predictors anchored to instance lifecycle
    private var detectorLarge: PaddlePredictor? = null
    private var detectorSmall: PaddlePredictor? = null

    companion object {
        var isAvailableGlobally = false; private set
        private var sharedDetectorLarge: PaddlePredictor? = null
        private var sharedDetectorSmall: PaddlePredictor? = null
        private var sharedRecognizerV3: PaddlePredictor? = null
        private var sharedRecognizerNumeric: PaddlePredictor? = null

        private var isNativeLibLoaded = false
        private val dictionaryV3 = mutableListOf<String>()

        // Constrained argmax index sets for numeric recognition (1-based; 0 = CTC blank)
        // Indices into en_dict.txt: 1-10 = "0"-"9", 93 = "."
        val ALLOWED_DIGITS: Set<Int> = (1..10).toSet()
        val ALLOWED_DIGITS_DECIMAL: Set<Int> = (1..10).toSet() + setOf(93)

        // Det predictors: one Lite instance per square tier (weights + activation pool).
        // Heatmap-stage A/B 2026-08-09: **full-square 2048** (useTiledLargeDet=false) so we can
        // compare heat/CRC/t_det_ms against the prior tiled gallery (det_mode=tiled_3x3_1024).
        // When useTiledLargeDet=true: maxEdge>1024 uses 3×3×1024 max-merge (no 2048 predictor).
        // Multi-scale experiment uses its own MonoDetPredictor, not this map.
        // Must stay inside det opt dynamic range (currently mono 64…4096).
        val TIER_SCALES = listOf(224, 608, 1024, 2048)
        /** Outer letterbox side when content long-edge exceeds max single-tier / tiled outer. */
        const val DET_LARGE_OUTER = 2048
        const val DET_TILE = 1024
        /** 3 positions: 0, 512, 1024 → 9 tiles with 512 px overlap. */
        const val DET_TILE_STRIDE = 512
        /**
         * When true, maxEdge > max single-tier uses tiled 1024 det.
         * **false for heatmap full-2048 A/B build** (load real 2048 predictor in [TIER_SCALES]).
         */
        @Volatile
        var useTiledLargeDet: Boolean = false
        val sharedTiers = mutableMapOf<Int, PaddlePredictor>()
        val sharedTierBuffers = mutableMapOf<Int, FloatArray>()
        val sharedTiersInt8 = mutableMapOf<Int, ByteArray>()
        /** QF G4 det only. Never swap [sharedTiers] (odo + experiment stay on product). */
        val g4Tiers = mutableMapOf<Int, PaddlePredictor>()
        val g4TiersInt8 = mutableMapOf<Int, ByteArray>()
        const val G4_DET_ASSET_BASE = "PP-OCRv4_mobile_det"
        /** Host letterbox + max-merged heat for tiled large det (not Lite tensors). */
        private var largeDetCanvasU8: ByteArray? = null
        private var largeDetHeatU8: ByteArray? = null

        /** Production path id for HW fp16 (uint8 feed → fp16 compute + uint8 heatmap). */
        const val PROD_PATH_ID = "uint8_fp16_u8"

        /** Production path for fp32 mid-graph (uint8 feed → fp32 compute + uint8 heatmap). */
        const val PROD_PATH_ID_FP32 = "uint8_fp32_u8"

        /** @deprecated Use [PROD_PATH_ID_FP32]; kept for call sites that named the v7 product path. */
        const val PROD_PATH_ID_ARMV7 = PROD_PATH_ID_FP32

        /**
         * Last loaded product path id (set by [loadProductionModels]).
         * Defaults from [productArchAndDir] before first load.
         */
        @Volatile
        var activeProductPathId: String = PROD_PATH_ID
            private set

        @Volatile
        var activeProductDir: String = "prod_u8fp16"
            private set

        /** Model file arch suffix for the device primary ABI (armv8 / armv7 / x86_64). */
        fun modelArchForPrimaryAbi(): String {
            val primary = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            return when {
                primary == "armeabi-v7a" || primary.startsWith("armeabi") -> "armv7"
                primary.contains("64") && primary.contains("arm") -> "armv8"
                primary.contains("x86") -> "x86_64"
                primary.contains("arm") ->
                    if (Build.SUPPORTED_ABIS.any { it.contains("arm64") }) "armv8" else "armv7"
                else -> "x86_64"
            }
        }

        /**
         * Primary ABI → default model arch suffix + assets directory.
         * armv7 / x86_64: [PROD_PATH_ID_FP32] / prod_u8fp32_u8
         * arm64: [PROD_PATH_ID] / prod_u8fp16
         * ABI-split APKs ship only that pack. Precision A/B may override via [loadProductionModels].
         */
        fun productArchAndDir(): Pair<String, String> {
            val arch = modelArchForPrimaryAbi()
            val dir = when (arch) {
                "armv7", "x86_64" -> "prod_u8fp32_u8"
                else -> "prod_u8fp16"
            }
            return arch to dir
        }

        fun productPathIdForDir(prodDir: String): String =
            if (prodDir == "prod_u8fp32_u8") PROD_PATH_ID_FP32 else PROD_PATH_ID

        /**
         * Last pipeline stage name for hang diagnosis (ingest / deskew / det / rec / …).
         * Updated by [heartbeat].
         */
        @Volatile
        var lastStage: String = "idle"
            private set

        @Volatile
        var lastStageMs: Long = 0L
            private set

        /** Log + record pipeline stage so stalls pin the hung step. */
        fun heartbeat(stage: String) {
            lastStage = stage
            lastStageMs = System.currentTimeMillis()
            Log.i("PaddleLite", "STAGE $stage")
        }

        /**
         * Explicit native free for [PaddlePredictor].
         * Public API only exposes protected clear()/finalize(); use reflection so path
         * switches do not wait on GC (native RAM leak → soft hang after hundreds of images).
         */
        fun releasePredictor(p: PaddlePredictor?, label: String) {
            if (p == null) return
            try {
                val m = PaddlePredictor::class.java.getDeclaredMethod("clear")
                m.isAccessible = true
                val ok = m.invoke(p) as? Boolean ?: false
                Log.i("PaddleLite", "releasePredictor $label ok=$ok")
            } catch (t: Throwable) {
                Log.w("PaddleLite", "releasePredictor $label failed: ${t.message}")
            }
        }

        /** Release all shared det tiers + rec predictors (call before reload / after TIMEOUT). */
        fun releaseAllPredictors(reason: String) {
            Log.i("PaddleLite", "releaseAllPredictors reason=$reason count_tiers=${sharedTiers.size}")
            val tiers = sharedTiers.toMap()
            sharedTiers.clear()
            for ((scale, pred) in tiers) {
                releasePredictor(pred, "det_tier_$scale")
            }
            releasePredictor(sharedRecognizerV3, "rec_v3")
            releasePredictor(sharedRecognizerNumeric, "rec_numeric")
            sharedRecognizerV3 = null
            sharedRecognizerNumeric = null
            sharedDetectorLarge = null
            sharedDetectorSmall = null
            // Drop large host buffers so next load reallocates clean sizes for the path.
            sharedTierBuffers.clear()
            sharedTiersInt8.clear()
            val g4 = g4Tiers.toMap()
            g4Tiers.clear()
            for ((scale, pred) in g4) {
                releasePredictor(pred, "g4_det_tier_$scale")
            }
            g4TiersInt8.clear()
            largeDetCanvasU8 = null
            largeDetHeatU8 = null
            System.gc()
        }

        /**
         * Lazy-load v4 mobile det into [g4Tiers] without touching [sharedTiers] or
         * [activeProductPathId]. Returns false if the ABI asset is missing (e.g. armv7).
         */
        @Synchronized
        fun ensureG4DetTiers(context: Context): Boolean {
            if (g4Tiers.isNotEmpty() && TIER_SCALES.all { g4Tiers.containsKey(it) }) return true
            val arch = modelArchForPrimaryAbi()
            val asset = "paddle/exp_det_ab/${G4_DET_ASSET_BASE}_$arch.nb"
            try {
                context.assets.open(asset).close()
            } catch (_: Throwable) {
                Log.w("PaddleLite", "ensureG4DetTiers: no asset $asset — QF will use product det")
                return false
            }
            val f = File(context.filesDir, "g4_" + asset.replace("/", "_"))
            context.assets.open(asset).use { inp -> FileOutputStream(f).use { out -> inp.copyTo(out) } }
            val detPath = f.absolutePath
            Log.i("PaddleLite", "ensureG4DetTiers arch=$arch → $detPath")
            for ((scale, p) in g4Tiers.toList()) {
                releasePredictor(p, "g4_reload_$scale")
                g4Tiers.remove(scale)
            }
            g4TiersInt8.clear()
            val config = MobileConfig()
            config.setThreads(4)
            config.setPowerMode(PowerMode.LITE_POWER_HIGH)
            TIER_SCALES.forEach { scale ->
                config.setModelFromFile(detPath)
                val p = PaddlePredictor.createPaddlePredictor(config)
                    ?: throw IllegalStateException("createPaddlePredictor G4 det null scale=$scale")
                p.getInput(0).resize(longArrayOf(1, 1, scale.toLong(), scale.toLong()))
                g4Tiers[scale] = p
                g4TiersInt8[scale] = ByteArray(1 * scale * scale)
            }
            return true
        }

        // Phase 116: Unified Rigid Backing Fields
        private var _bufferSetA: BufferSet? = null
        private var _bufferSetB: BufferSet? = null
        private var _deskewBufferSetLarge: BufferSet? = null
        private var _detBufferSet: BufferSet? = null
        private var _recBufferSet: BufferSet? = null
        private val vehicleOdoBuffers = mutableMapOf<Int, BufferSet>()
        private var _bufferLarge: FloatArray? = null
        private var _sharedBmp2048: Bitmap? = null
        private var _sharedCanvas2048: Canvas? = null
        private var _bufferSmall: FloatArray? = null
        private var _bufferRec: FloatArray? = null
        private var _bufferRecInt8: ByteArray? = null
        private val bufferRecInt8: ByteArray get() = _bufferRecInt8!!
        private var _sharedNv21Buffer: ByteArray? = null
        private var _sharedBmpOdoScratch: Bitmap? = null
        private var _sharedCanvasOdoScratch: Canvas? = null
        private var _redPaint: Paint? = null
        private var _bluePaint4: Paint? = null
        private var _yellowPaint2: Paint? = null
        private var _orangePaint: Paint? = null
        private var _grayToAlphaPaint: Paint? = null
        private var _alphaToGrayPaint: Paint? = null
        private var _sharedBuffer: java.nio.ByteBuffer? = null
        private var _sharedBytes: ByteArray? = null

        // Public Non-Null Accessors
        val bufferSetA: BufferSet get() = _bufferSetA!!
        val bufferSetB: BufferSet get() = _bufferSetB!!
        val deskewBufferSetLarge: BufferSet get() = _deskewBufferSetLarge!!
        val detBufferSet: BufferSet get() = _detBufferSet!!
        val recBufferSet: BufferSet get() = _recBufferSet!!
        private val bufferLarge: FloatArray get() = _bufferLarge!!
        val sharedBmp2048: Bitmap get() = _sharedBmp2048!!
        val sharedCanvas2048: Canvas get() = _sharedCanvas2048!!
        private val bufferSmall: FloatArray get() = _bufferSmall!!
        private val bufferRec: FloatArray get() = _bufferRec!!
        val sharedNv21Buffer: ByteArray get() = _sharedNv21Buffer!!
        val sharedBmpOdoScratch: Bitmap get() = _sharedBmpOdoScratch!!
        val sharedCanvasOdoScratch: Canvas get() = _sharedCanvasOdoScratch!!
        val redPaint: Paint get() = _redPaint!!
        val bluePaint4: Paint get() = _bluePaint4!!
        val yellowPaint2: Paint get() = _yellowPaint2!!
        val orangePaint: Paint get() = _orangePaint!!
        val grayToAlphaPaint: Paint get() = _grayToAlphaPaint!!
        val alphaToGrayPaint: Paint get() = _alphaToGrayPaint!!
        val srcPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC) }
        val sharedBuffer: java.nio.ByteBuffer get() = _sharedBuffer!!
        val sharedBytes: ByteArray get() = _sharedBytes!!
        val sharedMatrix = android.graphics.Matrix()

        /** Default reference dash size when probe fails (matches shared buffer / typical 12MP refs). Not 4000. */
        const val DEFAULT_REF_DASH_W = 4080
        const val DEFAULT_REF_DASH_H = 3072

        /**
         * Bounds-only probe of a vehicle reference dash photo (file path or content://).
         * Used by production Set J / auto-fill for landmark decode + anchorAlign ref size.
         */
        fun getReferenceDimensions(context: Context, path: String): Pair<Int, Int> {
            return try {
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (path.startsWith("content://")) {
                    context.contentResolver.openInputStream(android.net.Uri.parse(path))?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, options)
                    }
                } else {
                    android.graphics.BitmapFactory.decodeFile(path, options)
                }
                if (options.outWidth > 0 && options.outHeight > 0) {
                    Pair(options.outWidth, options.outHeight)
                } else {
                    Pair(DEFAULT_REF_DASH_W, DEFAULT_REF_DASH_H)
                }
            } catch (e: Exception) {
                Log.w("PaddleLite", "Failed to decode reference bounds: $path", e)
                Pair(DEFAULT_REF_DASH_W, DEFAULT_REF_DASH_H)
            }
        }

        fun getOdoBuffer(context: Context, vehicle: com.davidlang.vehicleexpensesautomated.data.model.Vehicle): BufferSet {
            val (refW, refH) = if (!vehicle.referenceDashPhotoUrl.isNullOrEmpty()) {
                getReferenceDimensions(context, vehicle.referenceDashPhotoUrl)
            } else {
                Pair(DEFAULT_REF_DASH_W, DEFAULT_REF_DASH_H)
            }

            val icrsRect = if (vehicle.odometerCropLeft != null && vehicle.odometerCropTop != null && vehicle.odometerCropRight != null && vehicle.odometerCropBottom != null) {
                RectF(vehicle.odometerCropLeft, vehicle.odometerCropTop, vehicle.odometerCropRight, vehicle.odometerCropBottom)
            } else {
                IcrsMath.fullImageIcrsRect(refW, refH)
            }
            val p1 = IcrsMath.icrsToPixel(icrsRect.left, icrsRect.top, refW, refH)
            val p2 = IcrsMath.icrsToPixel(icrsRect.right, icrsRect.bottom, refW, refH)
            val srcW = (p2.x - p1.x).toInt()
            val srcH = (p2.y - p1.y).toInt()

            // Align to 32-pixel boundaries for efficient native processing
            val targetW = if (srcW % 32 == 0) srcW else (srcW / 32 + 1) * 32
            val targetH = if (srcH % 2 == 0) srcH else (srcH / 2 + 1) * 2
            
            return vehicleOdoBuffers.getOrPut(vehicle.id) {
                Log.i("PaddleLite", "Creating persistent Odo Buffer for vehicle ${vehicle.id}: ${targetW}x${targetH}")
                BufferSet(targetW, targetH)
            }
        }

        fun releaseOdoBuffer(vehicleId: Int) {
            vehicleOdoBuffers.remove(vehicleId)?.let { buffer ->
                Log.i("PaddleLite", "Releasing persistent Odo Buffer for vehicle $vehicleId")
                buffer.release()
            }
        }

        fun releaseAllOdoBuffers() {
            val iterator = vehicleOdoBuffers.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                Log.i("PaddleLite", "Releasing persistent Odo Buffer for vehicle ${entry.key}")
                entry.value.release()
                iterator.remove()
            }
        }

        fun initializeGlobalBuffers(context: Context) {
            if (isAvailableGlobally) return
            val tStart = System.currentTimeMillis()
            Log.i("PaddleLite", "Initializing Global Rigid Buffers")

            // Square 4096: landscape + portrait (expense receipts); 32-aligned pad for det tiers.
            _bufferSetA = BufferSet(4096, 4096)
            _bufferSetB = BufferSet(4096, 4096)
            _deskewBufferSetLarge = BufferSet(2048, 2048)
            _deskewBufferSetLarge!!.p.clearChroma()
            _deskewBufferSetLarge!!.s.clearChroma()

            _detBufferSet = BufferSet(512, 128)
            _recBufferSet = BufferSet(320, 48)

            _bufferLarge = FloatArray(1 * 2048 * 2048) // Native is now exclusively 1-channel (Mono)
            _sharedBmp2048 = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ALPHA_8); _sharedCanvas2048 = Canvas(_sharedBmp2048!!)

            _bufferSmall = FloatArray(1 * 512 * 128)
            _bufferRec = FloatArray(1 * 320 * 48)
            // Exact numel for Tensor.setData(byte[]): JNI requires length == product(shape).
            // (Pad was only needed for ShareExternal + unpatched int8_to_fp32 overread.)
            _bufferRecInt8 = ByteArray(1 * 320 * 48)

            _sharedNv21Buffer = ByteArray(4096 * 4096 * 3 / 2)
            _sharedBmpOdoScratch = Bitmap.createBitmap(512, 128, Bitmap.Config.ARGB_8888); _sharedCanvasOdoScratch = Canvas(_sharedBmpOdoScratch!!)

            _redPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL; alpha = 120 }
            _bluePaint4 = Paint().apply { color = Color.BLUE; style = Paint.Style.STROKE; strokeWidth = 4f }
            _yellowPaint2 = Paint().apply { color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 2f }
            _orangePaint = Paint().apply { color = Color.rgb(255, 165, 0); style = Paint.Style.STROKE; strokeWidth = 2f }

            _grayToAlphaPaint = Paint().apply {
                val matrix = ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f))
                colorFilter = ColorMatrixColorFilter(matrix)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC)
            }
            _alphaToGrayPaint = Paint().apply {
                val matrix = ColorMatrix(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 255f))
                colorFilter = ColorMatrixColorFilter(matrix)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC)
            }

            _sharedBuffer = java.nio.ByteBuffer.allocateDirect(512 * 128).order(java.nio.ByteOrder.nativeOrder())
            _sharedBytes = ByteArray(512 * 128)

            val tBuffers = System.currentTimeMillis() - tStart
            Log.i("PaddleLite", "Buffer Allocation took ${tBuffers}ms")

            try {
                System.loadLibrary("paddle_lite_jni")
                isNativeLibLoaded = true
                loadProductionModels(context)
                loadDictionary(context, "paddle/en_dict.txt", dictionaryV3)
                Log.i("PaddleLite", "Total Global Init: ${System.currentTimeMillis() - tStart}ms")
                isAvailableGlobally = true
            } catch (e: Exception) { Log.e("PaddleLite", "Failed Global Init", e) }
        }

        private fun loadDictionary(context: Context, assetPath: String, target: MutableList<String>) {
            target.clear()
            context.assets.open(assetPath).bufferedReader().use { reader ->
                reader.forEachLine { target.add(it) }
            }
        }

        /** Java Tensor.setData returns false on size mismatch (no exception) — fail loud. */
        private fun requireSetData(ok: Boolean, where: String) {
            if (!ok) {
                throw IllegalStateException(
                    "Tensor.setData failed ($where): array length must equal product(shape); " +
                        "do not pad for setData (pad is only for ShareExternal on unpatched SO)"
                )
            }
        }

        /**
         * Load production det/rec models.
         * @param forceArch model suffix override (e.g. armv8); default from [modelArchForPrimaryAbi]
         * @param forceProdDir assets pack override (prod_u8fp16 or prod_u8fp32_u8)
         */
        fun loadProductionModels(
            context: Context,
            forceArch: String? = null,
            forceProdDir: String? = null,
        ) {
            val (defaultArch, defaultDir) = productArchAndDir()
            val arch = forceArch ?: defaultArch
            val prodDir = forceProdDir ?: defaultDir
            val pathId = productPathIdForDir(prodDir)
            activeProductPathId = pathId
            activeProductDir = prodDir
            Log.i(
                "PaddleLite",
                "loadProductionModels arch=$arch path=$pathId dir=$prodDir abis=${Build.SUPPORTED_ABIS.joinToString()}"
            )
            heartbeat("load_models_begin path=$pathId")
            releaseAllPredictors("loadProductionModels")

            fun copyAsset(p: String): String {
                val f = File(context.filesDir, p.replace("/", "_"))
                context.assets.open(p).use { inp -> FileOutputStream(f).use { out -> inp.copyTo(out) } }
                return f.absolutePath
            }

            fun resolveModel(baseName: String): String {
                val prodAsset = "paddle/$prodDir/${baseName}_$arch.nb"
                try {
                    context.assets.open(prodAsset).close()
                    Log.i("PaddleLite", "Using production asset $prodAsset")
                    return copyAsset(prodAsset)
                } catch (_: Exception) {
                    throw IllegalStateException("Missing production model $prodAsset arch=$arch")
                }
            }

            val detPath = resolveModel("det")
            val recV3Path = resolveModel("rec_v3")
            val recNumPath = resolveModel("rec_numeric")
            Log.i(
                "PaddleLite",
                "models path=$pathId detU8=true recU8=true det=$detPath rec_v3=$recV3Path rec_num=$recNumPath"
            )

            val config = MobileConfig()
            // Match pin-era First 10 goldens (b8449343): threads=4.
            // MEM A/B 2026-08-09 on 5554: threads=1 vs 4 — first tier=2048 still ~+1.9GB
            // and warm PSS ~3GB; no material RAM win (keep 4 for latency/accuracy).
            config.setThreads(4)
            config.setPowerMode(PowerMode.LITE_POWER_HIGH)
            Log.i("PaddleLite", "predictor config threads=4 power=HIGH path=$pathId")

            TIER_SCALES.forEach { scale ->
                val t0 = System.currentTimeMillis()
                config.setModelFromFile(detPath)
                val p = PaddlePredictor.createPaddlePredictor(config)
                    ?: throw IllegalStateException("createPaddlePredictor det null scale=$scale file=$detPath")
                p.getInput(0).resize(longArrayOf(1, 1, scale.toLong(), scale.toLong()))
                sharedTiers[scale] = p
                sharedTierBuffers[scale] = FloatArray(1 * scale * scale)
                sharedTiersInt8[scale] = ByteArray(1 * scale * scale) // exact numel for setData
                Log.i("PaddleLite", "Tier $scale Init: ${System.currentTimeMillis() - t0}ms")
            }
            if (useTiledLargeDet) {
                val n = DET_LARGE_OUTER * DET_LARGE_OUTER
                largeDetCanvasU8 = ByteArray(n)
                largeDetHeatU8 = ByteArray(n)
                Log.i(
                    "PaddleLite",
                    "tiled large det: outer=$DET_LARGE_OUTER tile=$DET_TILE stride=$DET_TILE_STRIDE " +
                        "(no ${DET_LARGE_OUTER} predictor)",
                )
            } else {
                Log.i(
                    "PaddleLite",
                    "full-square large det: useTiledLargeDet=false tiers=$TIER_SCALES " +
                        "(heatmap A/B vs tiled gallery)",
                )
            }

            config.setModelFromFile(recV3Path)
            sharedRecognizerV3 = PaddlePredictor.createPaddlePredictor(config)
                ?: throw IllegalStateException("createPaddlePredictor rec_v3 null file=$recV3Path")
            sharedRecognizerV3!!.getInput(0).resize(longArrayOf(1, 1, 48, 320))
            config.setModelFromFile(recNumPath)
            sharedRecognizerNumeric = PaddlePredictor.createPaddlePredictor(config)
                ?: throw IllegalStateException("createPaddlePredictor rec_numeric null file=$recNumPath")
            sharedRecognizerNumeric!!.getInput(0).resize(longArrayOf(1, 1, 48, 320))
            heartbeat("load_models_done path=$pathId tiers=${sharedTiers.size}")
        }

        /**
         * Replace **det tiers only** with an experiment nb (e.g. exp_det_ab PP-OCRv4_mobile_det).
         * Rec models stay production. Call [restoreProductionDetTiers] after the experiment column.
         *
         * @param assetBase name without arch/`.nb`, e.g. `PP-OCRv4_mobile_det`
         */
        fun loadExperimentDetTiers(context: Context, assetBase: String) {
            val arch = modelArchForPrimaryAbi()
            val asset = "paddle/exp_det_ab/${assetBase}_$arch.nb"
            val f = File(context.filesDir, asset.replace("/", "_"))
            context.assets.open(asset).use { inp -> FileOutputStream(f).use { out -> inp.copyTo(out) } }
            val detPath = f.absolutePath
            Log.i("PaddleLite", "loadExperimentDetTiers base=$assetBase arch=$arch → $detPath")
            for ((scale, p) in sharedTiers.toList()) {
                releasePredictor(p, "swap_det_tier_$scale")
                sharedTiers.remove(scale)
            }
            val config = MobileConfig()
            config.setThreads(4)
            config.setPowerMode(PowerMode.LITE_POWER_HIGH)
            TIER_SCALES.forEach { scale ->
                config.setModelFromFile(detPath)
                val p = PaddlePredictor.createPaddlePredictor(config)
                    ?: throw IllegalStateException("createPaddlePredictor exp det null scale=$scale")
                p.getInput(0).resize(longArrayOf(1, 1, scale.toLong(), scale.toLong()))
                sharedTiers[scale] = p
                sharedTiersInt8.getOrPut(scale) { ByteArray(1 * scale * scale) }
            }
            activeProductPathId = "exp_det_$assetBase"
            // activeProductDir left as production rec pack
        }

        /** Reload production det tiers (keeps current [activeProductDir] rec pack). */
        fun restoreProductionDetTiers(context: Context) {
            val arch = modelArchForPrimaryAbi()
            val prodDir = activeProductDir
            val prodAsset = "paddle/$prodDir/det_$arch.nb"
            val f = File(context.filesDir, prodAsset.replace("/", "_"))
            context.assets.open(prodAsset).use { inp -> FileOutputStream(f).use { out -> inp.copyTo(out) } }
            val detPath = f.absolutePath
            Log.i("PaddleLite", "restoreProductionDetTiers dir=$prodDir → $detPath")
            for ((scale, p) in sharedTiers.toList()) {
                releasePredictor(p, "restore_det_tier_$scale")
                sharedTiers.remove(scale)
            }
            val config = MobileConfig()
            config.setThreads(4)
            config.setPowerMode(PowerMode.LITE_POWER_HIGH)
            TIER_SCALES.forEach { scale ->
                config.setModelFromFile(detPath)
                val p = PaddlePredictor.createPaddlePredictor(config)
                    ?: throw IllegalStateException("createPaddlePredictor prod det null scale=$scale")
                p.getInput(0).resize(longArrayOf(1, 1, scale.toLong(), scale.toLong()))
                sharedTiers[scale] = p
                sharedTiersInt8.getOrPut(scale) { ByteArray(1 * scale * scale) }
            }
            activeProductPathId = productPathIdForDir(prodDir)
        }
    }

    init {
        try {
            if (isAvailableGlobally) {
                detectorLarge = sharedDetectorLarge
                detectorSmall = sharedDetectorSmall
                isAvailable = true
            }
        } catch (e: Throwable) {
            isAvailable = false; initError = e.message
            Log.e("PaddleLite", "Failed to initialize engine instance", e)
        }
    }

    private val recognizer: PaddlePredictor? get() = if (variant == "V3") sharedRecognizerV3 else sharedRecognizerNumeric

    /**
     * @param boxMode [NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT] or [NativeImageUtils.HEATMAP_BOX_AABB]
     * @param heatDumpU8z if non-null, write raw product u8 heat (zlib) to this path after post
     */
    fun detect(
        input: Any,
        targetW: Int? = null,
        targetH: Int? = null,
        copyHeatmap: Boolean = true,
        boxMode: Int = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
        heatDumpU8z: java.io.File? = null,
        /** Float thr on heat in [0,1]; product u8 path: thr*255 is OpenCV thresh (on if u > thr*255). */
        hmThresh: Float = 0.0f,
        /** 3x3 mask dilate passes before CC (experiment L/M). 0 = off. */
        maskDilatePasses: Int = 0,
        /** Null = product [sharedTiers]. QF G4 passes [g4Tiers] / [g4TiersInt8]. */
        detTiers: Map<Int, PaddlePredictor>? = null,
        detTiersInt8: Map<Int, ByteArray>? = null,
    ): DetectionResult? {
        val tPop0 = System.nanoTime()
        val w: Int; val h: Int; val srcMat: Mat
        when (input) {
            is BufferSet.Slice -> { w = input.width; h = input.height; srcMat = input.mat }
            is Mat -> { w = targetW ?: input.cols(); h = targetH ?: input.rows(); srcMat = input }
            else -> throw IllegalArgumentException("Unsupported input type for detect")
        }
        val tiers = detTiers ?: sharedTiers
        val tiersInt8 = detTiersInt8 ?: sharedTiersInt8

        // Automatic Tier Selection - respect explicit targets
        val maxEdge = max(targetW ?: w, targetH ?: h)
        // Smallest single-tier that fits; if content exceeds max tier and tiling is on → 3×3×1024.
        val singleTier = TIER_SCALES.filter { it >= maxEdge }.minOrNull()
        if (singleTier == null && useTiledLargeDet) {
            return detectTiledLarge(
                srcMat = srcMat,
                contentW = w,
                contentH = h,
                tPop0 = tPop0,
                copyHeatmap = copyHeatmap,
                boxMode = boxMode,
                heatDumpU8z = heatDumpU8z,
                hmThresh = hmThresh,
                maskDilatePasses = maskDilatePasses,
                detTiers = tiers,
                detTiersInt8 = tiersInt8,
            )
        }
        val tierScale = singleTier ?: TIER_SCALES.maxOrNull()!!
        heartbeat("det_begin tier=$tierScale ${w}x$h path=$activeProductPathId")
        val predictor = tiers[tierScale] ?: return null
        val int8Data = tiersInt8[tierScale] ?: return null
        java.util.Arrays.fill(int8Data, 0.toByte())
        NativeImageUtils.populateMonoUInt8(srcMat, int8Data, tierScale, tierScale)

        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        // Sample RAM/swap around Lite run (pump + multi-scale share this path via product det).
        return ProcessMemProbe.withSampling(
            tag = "paddle_det tier=$tierScale ${w}x$h path=$activeProductPathId",
            intervalMs = 250L,
        ) {
            try {
                val tJniIn0 = System.nanoTime()
                val inputTensor = predictor.getInput(0)
                requireSetData(inputTensor.setData(int8Data), "det uint8 tier=$tierScale")
                val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

                val tInfer0 = System.nanoTime()
                heartbeat("det_run tier=$tierScale")
                predictor.run()
                val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0

                val tJniOut0 = System.nanoTime()
                val outputTensor = predictor.getOutput(0)
                val dims = outputTensor.shape()
                val heatW = dims[3].toInt()
                val heatH = dims[2].toInt()

                // Zero-Copy Native Post-Processing (Phase 2) — MUST run before floatData
                // to avoid tensor pointer invalidation from Java-side copy
                val tNativePost0 = System.nanoTime()
                heartbeat("det_post tier=$tierScale")
                // Product u8: thr 0 → on if u8≥1; thr 1/255 → on if u8≥2 (Set K A/B).
                val nativeRes = NativeImageUtils.processHeatmap(
                    outputTensor, hmThresh, 10f, boxMode, maskDilatePasses,
                )
                val tNativePost = (System.nanoTime() - tNativePost0) / 1_000_000.0
                val heatmapPostPath = NativeImageUtils.lastHeatmapPostPath()

                val nativeBoxes = mutableListOf<DetectionBox>()
                var hist: IntArray? = null
                if (nativeRes != null) {
                    val boxFloats = nativeRes.size - 100
                    val nboxes = boxFloats / 9
                    for (i in 0 until nboxes) {
                        val offset = i * 9
                        val matPixels = FloatArray(8)
                        for (p in 0 until 4) {
                            matPixels[p * 2] = nativeRes[offset + p * 2]
                            matPixels[p * 2 + 1] = nativeRes[offset + p * 2 + 1]
                        }
                        val conf = nativeRes[offset + 8]
                        nativeBoxes.add(DetectionBox(matPixels, conf))
                    }
                    hist = IntArray(100)
                    for (i in 0 until 100) hist[i] = nativeRes[boxFloats + i].toInt()
                }

                // Optional raw u8 dump (before optional float copy). Prefer product u8 plane.
                if (heatDumpU8z != null) {
                    val u8 = NativeImageUtils.heatmapToUInt8Array(outputTensor)
                    if (u8 != null) {
                        HeatmapU8Dump.writeU8z(
                            heatDumpU8z,
                            u8,
                            heatW,
                            heatH,
                            mapOf(
                                "path" to activeProductPathId,
                                "product_dir" to activeProductDir,
                                "tier" to tierScale,
                                "content_w" to w,
                                "content_h" to h,
                                "box_mode" to boxMode,
                                "hm_thresh" to hmThresh,
                                "mask_dilate_passes" to maskDilatePasses,
                                "heatmap_post_path" to heatmapPostPath,
                                "n_boxes" to nativeBoxes.size,
                            ),
                        )
                    }
                }

                // Never Tensor.floatData on uint8 heatmaps (getFloatData SEGV).
                // Safe path: native heatmapToFloatArray (uint8/fp16/float). Campaign uses copyHeatmap=false.
                val tCopy0 = System.nanoTime()
                val heatmap: FloatArray? =
                    if (copyHeatmap) NativeImageUtils.heatmapToFloatArray(outputTensor) else null
                val tCopy = (System.nanoTime() - tCopy0) / 1_000_000.0

                val tJniOut = (System.nanoTime() - tJniOut0) / 1_000_000.0

                val meta = mapOf(
                    "t_pop_tensor_ms" to "%.3f".format(tPop),
                    "t_jni_in_ms" to "%.3f".format(tJniIn),
                    "t_inference_ms" to "%.3f".format(tInfer),
                    "t_jni_out_ms" to "%.3f".format(tJniOut),
                    "t_native_post_ms" to "%.3f".format(tNativePost),
                    "t_copy_tensor_ms" to "%.3f".format(tCopy),
                    "dynamic_shape" to "%dx%d".format(w, h),
                    "path" to activeProductPathId,
                    "tier" to tierScale.toString(),
                    "det_mode" to "single",
                    "box_mode" to boxMode.toString(),
                    "hm_thresh" to hmThresh.toString(),
                    "mask_dilate_passes" to maskDilatePasses.toString(),
                    "heatmap_post_path" to heatmapPostPath,
                )
                DetectionResult(heatmap, heatW, heatH, meta, nativeBoxes, outputTensor, hist)
            } catch (t: Throwable) {
                Log.e("PaddleDetect", "Detection failed", t)
                null
            }
        }
    }

    /**
     * Large det without a 2048 Lite arena: letterbox to [DET_LARGE_OUTER], run 9 overlapping
     * [DET_TILE] predicts on the 1024 predictor, max-merge heatmaps, then normal post.
     */
    private fun detectTiledLarge(
        srcMat: Mat,
        contentW: Int,
        contentH: Int,
        tPop0: Long,
        copyHeatmap: Boolean,
        boxMode: Int,
        heatDumpU8z: java.io.File?,
        hmThresh: Float,
        maskDilatePasses: Int,
        detTiers: Map<Int, PaddlePredictor> = sharedTiers,
        detTiersInt8: Map<Int, ByteArray> = sharedTiersInt8,
    ): DetectionResult? {
        val outer = DET_LARGE_OUTER
        val tile = DET_TILE
        val stride = DET_TILE_STRIDE
        val predictor = detTiers[tile] ?: return null
        val tileFeed = detTiersInt8[tile] ?: return null
        val canvas = largeDetCanvasU8 ?: ByteArray(outer * outer).also { largeDetCanvasU8 = it }
        val combined = largeDetHeatU8 ?: ByteArray(outer * outer).also { largeDetHeatU8 = it }

        heartbeat("det_begin tiled outer=$outer tile=$tile ${contentW}x$contentH path=$activeProductPathId")
        java.util.Arrays.fill(canvas, 0.toByte())
        java.util.Arrays.fill(combined, 0.toByte())
        NativeImageUtils.populateMonoUInt8(srcMat, canvas, outer, outer)
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        val origins = ArrayList<Int>(3)
        var o = 0
        while (o + tile <= outer) {
            origins.add(o)
            o += stride
        }
        // Ensure last tile flush-right/bottom if stride does not land on outer-tile.
        if (origins.isEmpty() || origins.last() != outer - tile) {
            if (outer >= tile) origins.add(outer - tile)
        }
        val nTiles = origins.size * origins.size

        return ProcessMemProbe.withSampling(
            tag = "paddle_det tiled ${nTiles}x${tile} outer=$outer ${contentW}x$contentH path=$activeProductPathId",
            intervalMs = 250L,
        ) {
            try {
                var tJniIn = 0.0
                var tInfer = 0.0
                var tHeatCopy = 0.0
                var tilesOk = 0
                heartbeat("det_run tiled n=$nTiles tile=$tile")
                for (oy in origins) {
                    for (ox in origins) {
                        // Copy tile from letterbox canvas into 1024 feed (exact numel for setData).
                        for (ty in 0 until tile) {
                            System.arraycopy(
                                canvas,
                                (oy + ty) * outer + ox,
                                tileFeed,
                                ty * tile,
                                tile,
                            )
                        }
                        val tIn0 = System.nanoTime()
                        val inputTensor = predictor.getInput(0)
                        // Keep 1024 shape (predictor was resized at load).
                        requireSetData(inputTensor.setData(tileFeed), "det uint8 tiled tile=$tile")
                        tJniIn += (System.nanoTime() - tIn0) / 1_000_000.0

                        val tInf0 = System.nanoTime()
                        predictor.run()
                        tInfer += (System.nanoTime() - tInf0) / 1_000_000.0

                        val tH0 = System.nanoTime()
                        val outputTensor = predictor.getOutput(0)
                        val dims = outputTensor.shape()
                        val heatH = dims[2].toInt()
                        val heatW = dims[3].toInt()
                        if (heatW != tile || heatH != tile) {
                            Log.e(
                                "PaddleDetect",
                                "tiled heat size ${heatW}x$heatH != tile $tile — abort tile",
                            )
                            continue
                        }
                        val tileHeat = NativeImageUtils.heatmapToUInt8Array(outputTensor)
                            ?: continue
                        // Max-merge into outer heat at (ox, oy).
                        for (ty in 0 until tile) {
                            val dstRow = (oy + ty) * outer + ox
                            val srcRow = ty * tile
                            for (tx in 0 until tile) {
                                val v = tileHeat[srcRow + tx].toInt() and 0xff
                                val u = combined[dstRow + tx].toInt() and 0xff
                                if (v > u) combined[dstRow + tx] = tileHeat[srcRow + tx]
                            }
                        }
                        tHeatCopy += (System.nanoTime() - tH0) / 1_000_000.0
                        tilesOk++
                    }
                }
                heartbeat("det_post tiled tiles_ok=$tilesOk/$nTiles")

                val tNativePost0 = System.nanoTime()
                val nativeRes = NativeImageUtils.processHeatmapU8(
                    combined, outer, outer, hmThresh, 10f, boxMode, maskDilatePasses,
                )
                val tNativePost = (System.nanoTime() - tNativePost0) / 1_000_000.0
                val heatmapPostPath = NativeImageUtils.lastHeatmapPostPath()

                val nativeBoxes = mutableListOf<DetectionBox>()
                var hist: IntArray? = null
                if (nativeRes != null) {
                    val boxFloats = nativeRes.size - 100
                    val nboxes = boxFloats / 9
                    for (i in 0 until nboxes) {
                        val offset = i * 9
                        val matPixels = FloatArray(8)
                        for (p in 0 until 4) {
                            matPixels[p * 2] = nativeRes[offset + p * 2]
                            matPixels[p * 2 + 1] = nativeRes[offset + p * 2 + 1]
                        }
                        nativeBoxes.add(DetectionBox(matPixels, nativeRes[offset + 8]))
                    }
                    hist = IntArray(100)
                    for (i in 0 until 100) hist[i] = nativeRes[boxFloats + i].toInt()
                }

                if (heatDumpU8z != null) {
                    HeatmapU8Dump.writeU8z(
                        heatDumpU8z,
                        combined,
                        outer,
                        outer,
                        mapOf(
                            "path" to activeProductPathId,
                            "product_dir" to activeProductDir,
                            "tier" to outer,
                            "det_mode" to "tiled_3x3_$tile",
                            "tiles_ok" to tilesOk,
                            "content_w" to contentW,
                            "content_h" to contentH,
                            "box_mode" to boxMode,
                            "hm_thresh" to hmThresh,
                            "mask_dilate_passes" to maskDilatePasses,
                            "heatmap_post_path" to heatmapPostPath,
                            "n_boxes" to nativeBoxes.size,
                        ),
                    )
                }

                val tCopy0 = System.nanoTime()
                val heatmap: FloatArray? = if (copyHeatmap) {
                    FloatArray(outer * outer) { i ->
                        (combined[i].toInt() and 0xff) / 255f
                    }
                } else {
                    null
                }
                val tCopy = (System.nanoTime() - tCopy0) / 1_000_000.0

                // Snapshot heat for deskew angle (combined is reused next call).
                val heatSnap = combined.copyOf()

                val meta = mapOf(
                    "t_pop_tensor_ms" to "%.3f".format(tPop),
                    "t_jni_in_ms" to "%.3f".format(tJniIn),
                    "t_inference_ms" to "%.3f".format(tInfer),
                    "t_tile_heat_merge_ms" to "%.3f".format(tHeatCopy),
                    "t_native_post_ms" to "%.3f".format(tNativePost),
                    "t_copy_tensor_ms" to "%.3f".format(tCopy),
                    "dynamic_shape" to "%dx%d".format(contentW, contentH),
                    "path" to activeProductPathId,
                    "tier" to outer.toString(),
                    "det_mode" to "tiled_3x3_$tile",
                    "tiles_ok" to tilesOk.toString(),
                    "n_tiles" to nTiles.toString(),
                    "box_mode" to boxMode.toString(),
                    "hm_thresh" to hmThresh.toString(),
                    "mask_dilate_passes" to maskDilatePasses.toString(),
                    "heatmap_post_path" to heatmapPostPath,
                )
                Log.i(
                    "PaddleLite",
                    "det_tiled done tiles=$tilesOk/$nTiles infer_ms=${"%.1f".format(tInfer)} " +
                        "post_ms=${"%.1f".format(tNativePost)} boxes=${nativeBoxes.size}",
                )
                DetectionResult(
                    heatmap = heatmap,
                    width = outer,
                    height = outer,
                    metadata = meta,
                    nativeBoxes = nativeBoxes,
                    outputTensor = null,
                    heatmapHist = hist,
                    heatU8 = heatSnap,
                )
            } catch (t: Throwable) {
                Log.e("PaddleDetect", "Tiled detection failed", t)
                null
            }
        }
    }

    /**
     * Dynamic-sized detection for arbitrary Mat crops (e.g. Pump Experiment).
     * Resizes the internal Paddle predictor to match the input Mat dimensions exactly.
     */
    fun detectMat(
        srcMat: Mat,
        copyHeatmap: Boolean = true,
        boxMode: Int = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
        heatDumpU8z: java.io.File? = null,
        hmThresh: Float = 0.0f,
        maskDilatePasses: Int = 0,
    ): DetectionResult? {
        if (!isAvailable) return null
        val predictor = detectorLarge ?: return null
        val t0 = System.currentTimeMillis()
        val w = srcMat.cols(); val h = srcMat.rows()
        val tPop0 = System.nanoTime()
        val byteData = ByteArray(w * h)
        NativeImageUtils.populateMonoUInt8(srcMat, byteData, w, h)
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            val inputTensor = predictor.getInput(0)
            inputTensor.resize(longArrayOf(1, 1, h.toLong(), w.toLong()))
            requireSetData(inputTensor.setData(byteData), "detectMat uint8 ${w}x$h")
            val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

            val tInfer0 = System.nanoTime()
            predictor.run()
            val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0

            val tJniOut0 = System.nanoTime()
            val outputTensor = predictor.getOutput(0)
            val dims = outputTensor.shape()
            val heatW = dims[3].toInt()
            val heatH = dims[2].toInt()

            // Zero-Copy Native Post-Processing — MUST run before any floatData copy
            val tNativePost0 = System.nanoTime()
            val nativeRes = NativeImageUtils.processHeatmap(
                outputTensor, hmThresh, 10f, boxMode, maskDilatePasses,
            )
            val tNativePost = (System.nanoTime() - tNativePost0) / 1_000_000.0
            val heatmapPostPath = NativeImageUtils.lastHeatmapPostPath()

            val nativeBoxes = mutableListOf<DetectionBox>()
            var hist: IntArray? = null
            if (nativeRes != null) {
                val boxFloats = nativeRes.size - 100
                val nboxes = boxFloats / 9
                for (i in 0 until nboxes) {
                    val offset = i * 9
                    val matPixels = FloatArray(8)
                    for (p in 0 until 4) {
                        matPixels[p * 2] = nativeRes[offset + p * 2]
                        matPixels[p * 2 + 1] = nativeRes[offset + p * 2 + 1]
                    }
                    val conf = nativeRes[offset + 8]
                    nativeBoxes.add(DetectionBox(matPixels, conf))
                }
                hist = IntArray(100)
                for (i in 0 until 100) hist[i] = nativeRes[boxFloats + i].toInt()
            }
            if (heatDumpU8z != null) {
                val u8 = NativeImageUtils.heatmapToUInt8Array(outputTensor)
                if (u8 != null) {
                    HeatmapU8Dump.writeU8z(
                        heatDumpU8z,
                        u8,
                        heatW,
                        heatH,
                        mapOf(
                            "path" to activeProductPathId,
                            "product_dir" to activeProductDir,
                            "box_mode" to boxMode,
                            "hm_thresh" to hmThresh,
                            "mask_dilate_passes" to maskDilatePasses,
                            "heatmap_post_path" to heatmapPostPath,
                            "n_boxes" to nativeBoxes.size,
                            "dynamic" to true,
                        ),
                    )
                }
            }
            // Never floatData on uint8 heatmaps (getFloatData SEGV). Campaign: copyHeatmap=false.
            val tCopy0 = System.nanoTime()
            val heatmap: FloatArray? =
                if (copyHeatmap) NativeImageUtils.heatmapToFloatArray(outputTensor) else null
            val tCopy = (System.nanoTime() - tCopy0) / 1_000_000.0

            val tJniOut = (System.nanoTime() - tJniOut0) / 1_000_000.0

            val meta = mapOf(
                "t_pop_tensor_ms" to "%.3f".format(tPop),
                "t_jni_in_ms" to "%.3f".format(tJniIn),
                "t_inference_ms" to "%.3f".format(tInfer),
                "t_jni_out_ms" to "%.3f".format(tJniOut),
                "t_native_post_ms" to "%.3f".format(tNativePost),
                "t_copy_tensor_ms" to "%.3f".format(tCopy),
                "dynamic_shape" to "%dx%d".format(w, h),
                "path" to activeProductPathId,
                "box_mode" to boxMode.toString(),
                "hm_thresh" to hmThresh.toString(),
                "mask_dilate_passes" to maskDilatePasses.toString(),
                "heatmap_post_path" to heatmapPostPath,
            )
            return DetectionResult(heatmap, heatW, heatH, meta, nativeBoxes, outputTensor, hist)
        } catch (t: Throwable) {
            Log.e("PaddleDetect", "Dynamic detection failed", t)
            return null
        }
    }

    private suspend fun processOcr(input: Any, predictor: PaddlePredictor?, dictionary: List<String>): RecStageResult = withContext(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        if (predictor == null) return@withContext RecStageResult("(Engine Error)", 0, 0f, null)

        val w: Int; val h: Int; val srcMat: Mat
        when (input) {
            is BufferSet.Slice -> { w = input.width; h = input.height; srcMat = input.mat }
            is Mat -> { w = input.cols(); h = input.rows(); srcMat = input }
            else -> throw IllegalArgumentException("Unsupported input type for processOcr")
        }
        heartbeat("rec_v3_begin ${w}x$h path=$activeProductPathId")

        if (w * h > 320 * 48) {
            Log.e("PaddleDetect", "Bridge dimensions (${w}x${h}) exceed pre-allocated rec tensor capacity.")
            return@withContext RecStageResult("(Size Error)", 0, 0f, null)
        }

        val tPop0 = System.nanoTime()
        java.util.Arrays.fill(bufferRecInt8, 0.toByte())
        NativeImageUtils.populateMonoUInt8(srcMat, bufferRecInt8, 320, 48)
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            requireSetData(predictor.getInput(0).setData(bufferRecInt8), "rec_v3 uint8")
            val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

            val tInfer0 = System.nanoTime()
            heartbeat("rec_v3_run")
            predictor.run()
            val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0

            val tJniOut0 = System.nanoTime()
            val outputTensor = predictor.getOutput(0); val data = outputTensor.floatData; val dims = outputTensor.shape()
            val tJniOut = (System.nanoTime() - tJniOut0) / 1_000_000.0

            val meta = mapOf(
                "t_pop_tensor_ms" to "%.3f".format(tPop),
                "t_jni_in_ms" to "%.3f".format(tJniIn),
                "t_inference_ms" to "%.3f".format(tInfer),
                "t_jni_out_ms" to "%.3f".format(tJniOut)
            )

            val seqLen = dims[1].toInt(); val dictSize = dims[2].toInt(); val result = StringBuilder(); val probs = StringBuilder(); val perCharProbsBuilder = StringBuilder()
            var lastIdx = -1; var totalConf = 0f; var charCount = 0; var lastConf = 1.0f

            for (i in 0 until seqLen) {
                var maxIdx = 0; var maxVal = -1f; val searchLimit = dictSize
                for (j in 0 until searchLimit) { val v = data[i * dictSize + j]; if (v > maxVal) { maxVal = v; maxIdx = j } }
                if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) {
                    val ch = dictionary[maxIdx - 1]
                    result.append(ch); totalConf += maxVal; charCount++; lastConf = maxVal
                    if (perCharProbsBuilder.isNotEmpty()) perCharProbsBuilder.append(",")
                    perCharProbsBuilder.append("${ch}:${"%.2f".format(maxVal)}")
                }
                lastIdx = maxIdx
            }
            val finalStr = result.toString(); val finalConf = if (charCount > 0) totalConf / charCount else 0f
            return@withContext RecStageResult(finalStr, System.currentTimeMillis() - tStart, finalConf, null, mapOf("ocr_probs" to probs.toString()), perCharProbs = perCharProbsBuilder.toString())
        } catch (t: Throwable) {
            return@withContext RecStageResult("(Inference Error)", 0, 0f, null)
        }
    }

    private suspend fun processOcrNumeric(input: Any, predictor: PaddlePredictor?, dictionary: List<String>, allowedIndices: Set<Int>): RecStageResult = withContext(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        if (predictor == null) return@withContext RecStageResult("(Engine Error)", 0, 0f, null)

        val w: Int; val h: Int; val srcMat: Mat
        when (input) {
            is BufferSet.Slice -> { w = input.width; h = input.height; srcMat = input.mat }
            is Mat -> { w = input.cols(); h = input.rows(); srcMat = input }
            else -> throw IllegalArgumentException("Unsupported input type for processOcr")
        }
        heartbeat("rec_num_begin ${w}x$h path=$activeProductPathId")

        if (w * h > 320 * 48) {
            Log.e("PaddleDetect", "Bridge dimensions (${w}x${h}) exceed pre-allocated rec tensor capacity.")
            return@withContext RecStageResult("(Size Error)", 0, 0f, null)
        }

        val tPop0 = System.nanoTime()
        java.util.Arrays.fill(bufferRecInt8, 0.toByte())
        NativeImageUtils.populateMonoUInt8(srcMat, bufferRecInt8, 320, 48)
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            requireSetData(predictor.getInput(0).setData(bufferRecInt8), "rec_numeric uint8")
            val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

            val tInfer0 = System.nanoTime()
            heartbeat("rec_num_run")
            predictor.run()
            val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0

            val tJniOut0 = System.nanoTime()
            val outputTensor = predictor.getOutput(0); val data = outputTensor.floatData; val dims = outputTensor.shape()
            val tJniOut = (System.nanoTime() - tJniOut0) / 1_000_000.0

            val meta = mapOf(
                "t_pop_tensor_ms" to "%.3f".format(tPop),
                "t_jni_in_ms" to "%.3f".format(tJniIn),
                "t_inference_ms" to "%.3f".format(tInfer),
                "t_jni_out_ms" to "%.3f".format(tJniOut)
            )

            val seqLen = dims[1].toInt(); val dictSize = dims[2].toInt(); val result = StringBuilder(); val probs = StringBuilder(); val perCharProbsBuilder = StringBuilder()
            var lastIdx = -1; var totalConf = 0f; var charCount = 0; var lastConf = 1.0f

            for (i in 0 until seqLen) {
                // Constrained argmax: only consider CTC blank (0) + explicitly allowed indices.
                // This causes genuine character collapse — e.g. "q","g","9" all map to "9"
                // because index 10 ("9") has the highest probability among the allowed set.
                var maxIdx = 0; var maxVal = data[i * dictSize + 0] // start with blank
                for (j in allowedIndices) {
                    if (j >= dictSize) continue
                    val v = data[i * dictSize + j]
                    if (v > maxVal) { maxVal = v; maxIdx = j }
                }
                if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) {
                    val char = dictionary[maxIdx - 1]
                    val ratioThr = 0.30f * lastConf
                    val isSafe = result.length < 5
                    val pass = isSafe || maxVal >= ratioThr
                    Log.i("PaddleOCR", "Decoded: '$char' Len: %d Conf: %.3f Thr: %.3f Safe: $isSafe Pass: $pass".format(result.length, maxVal, ratioThr))
                    if (probs.isNotEmpty()) probs.append(" ")
                    if (pass) {
                        result.append(char); totalConf += maxVal; charCount++; lastConf = maxVal
                        probs.append("%s(%.3f)".format(char, maxVal))
                        if (perCharProbsBuilder.isNotEmpty()) perCharProbsBuilder.append(",")
                        perCharProbsBuilder.append("${char}:${"%.2f".format(maxVal)}")
                    } else {
                        Log.w("PaddleOCR", "Pruning: Confidence drop too high for '$char' (Ratio: %.3f < %.3f)".format(maxVal, ratioThr))
                        probs.append("%s(%.3f!)".format(char, maxVal))
                        break
                    }
                }
                lastIdx = maxIdx
            }
            val finalStr = result.toString(); val finalConf = if (charCount > 0) totalConf / charCount else 0f
            return@withContext RecStageResult(finalStr, System.currentTimeMillis() - tStart, finalConf, null, mapOf("ocr_probs" to probs.toString()), perCharProbs = perCharProbsBuilder.toString())
        } catch (t: Throwable) {
            return@withContext RecStageResult("(Inference Error)", 0, 0f, null)
        }
    }

    data class RecStageResult(val text: String, val timeMs: Long, val confidence: Float, val ocrInputB64: String? = null, val metadata: Map<String, String> = emptyMap(), val perCharProbs: String = "")

    override suspend fun recognize(input: Any): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val w: Int; val h: Int
        when (input) {
            is Bitmap -> { w = input.width; h = input.height }
            is BufferSet.Slice -> { w = input.width; h = input.height }
            is Mat -> { w = input.cols(); h = input.rows() }
            else -> throw IllegalArgumentException("Unsupported input type for recognize")
        }

        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Not Available", imageWidth = w, imageHeight = h)

        val res = processOcr(input, sharedRecognizerV3, dictionaryV3)
        OcrResult(
            engineName = "Paddle V3 Greedy",
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = res.text,
            textBlocks = listOf(TextBlock(res.text, Rect(0, 0, w, h), confidence = res.confidence)),
            imageWidth = w,
            imageHeight = h,
            metadata = res.metadata,
            perCharProbs = res.perCharProbs
        )
    }

    suspend fun recognizeNumeric(input: Any): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val w: Int; val h: Int
        when (input) {
            is Bitmap -> { w = input.width; h = input.height }
            is BufferSet.Slice -> { w = input.width; h = input.height }
            is Mat -> { w = input.cols(); h = input.rows() }
            else -> throw IllegalArgumentException("Unsupported input type for recognizeNumeric")
        }

        if (!isAvailable) return@withContext OcrResult(engineName = "Paddle Numeric Greedy", debugText = "Not Available", imageWidth = w, imageHeight = h)

        val res = processOcrNumeric(input, sharedRecognizerV3, dictionaryV3, ALLOWED_DIGITS)
        OcrResult(
            engineName = "Paddle Numeric Greedy",
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = res.text,
            textBlocks = listOf(TextBlock(res.text, Rect(0, 0, w, h), confidence = res.confidence)),
            imageWidth = w,
            imageHeight = h,
            metadata = res.metadata,
            perCharProbs = res.perCharProbs
        )
    }

    /**
     * Decimal-aware numeric recognition for pump cost/volume (includes '.').
     * Uses the same numeric model but the ALLOWED_DIGITS_DECIMAL constrained set.
     */
    suspend fun recognizeNumericDecimal(input: Any): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val w: Int; val h: Int
        when (input) {
            is Bitmap -> { w = input.width; h = input.height }
            is BufferSet.Slice -> { w = input.width; h = input.height }
            is Mat -> { w = input.cols(); h = input.rows() }
            else -> throw IllegalArgumentException("Unsupported input type for recognizeNumericDecimal")
        }

        if (!isAvailable) return@withContext OcrResult(engineName = "Paddle Numeric Decimal Greedy", debugText = "Not Available", imageWidth = w, imageHeight = h)

        val res = processOcrNumeric(input, sharedRecognizerV3, dictionaryV3, ALLOWED_DIGITS_DECIMAL)
        OcrResult(
            engineName = "Paddle Numeric Decimal Greedy",
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = res.text,
            textBlocks = listOf(TextBlock(res.text, Rect(0, 0, w, h), confidence = res.confidence)),
            imageWidth = w,
            imageHeight = h,
            metadata = res.metadata,
            perCharProbs = res.perCharProbs
        )
    }
}
