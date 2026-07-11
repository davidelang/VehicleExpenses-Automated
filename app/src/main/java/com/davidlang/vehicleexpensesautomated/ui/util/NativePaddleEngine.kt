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
        val heatmapHist: IntArray? = null
    )
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

        // Phase 125: Multi-Tier Predictor Array
        val TIER_SCALES = listOf(224, 608, 1024, 2048, 2560)
        val sharedTiers = mutableMapOf<Int, PaddlePredictor>()
        val sharedTierBuffers = mutableMapOf<Int, FloatArray>()
        val sharedTiersInt8 = mutableMapOf<Int, ByteArray>()

        @Volatile
        var activePrecisionPath: PrecisionPath = PrecisionPath.BASELINE
            private set

        /**
         * Last pipeline stage name for hang diagnosis (ingest / deskew / det / rec / …).
         * Updated by [heartbeat]; campaign TIMEOUT logs include this.
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
            Log.i("PrecCampaign", "STAGE $stage")
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
            System.gc()
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

        private fun getReferenceDimensions(context: Context, path: String): Pair<Int, Int> {
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
                    Pair(4000, 3072) // Safe fallback
                }
            } catch (e: Exception) {
                Log.w("PaddleLite", "Failed to decode reference bounds: $path", e)
                Pair(4000, 3072) // Safe fallback
            }
        }

        fun getOdoBuffer(context: Context, vehicle: com.davidlang.vehicleexpensesautomated.data.model.Vehicle): BufferSet {
            val (refW, refH) = if (!vehicle.referenceDashPhotoUrl.isNullOrEmpty()) {
                getReferenceDimensions(context, vehicle.referenceDashPhotoUrl)
            } else {
                Pair(4000, 3072)
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

            _bufferSetA = BufferSet(4080, 3072)
            _bufferSetB = BufferSet(4080, 3072)
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

            _sharedNv21Buffer = ByteArray(4080 * 3072 * 3 / 2)
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
                loadModelsForPath(context, PrecisionPath.BASELINE)
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

        fun loadModelsForPath(context: Context, pathIn: PrecisionPath) {
            val isArm = Build.SUPPORTED_ABIS[0].contains("arm")
            // Never silent-remap: wrong models (e.g. float_fp16 → int8_fp32) produced fake results.
            if (!pathIn.isSupportedOnDevice(isArm)) {
                throw IllegalStateException(
                    "PrecisionPath ${pathIn.id} not supported on this ABI " +
                        "(armSupported=${pathIn.armSupported} x86Supported=${pathIn.x86Supported}); " +
                        "no silent remap"
                )
            }
            val path = pathIn
            val arch = if (isArm) "armv8" else "x86_64"
            activePrecisionPath = path
            Log.i("PaddleLite", "loadModelsForPath id=${path.id} arch=$arch requested=${pathIn.id}")
            heartbeat("load_models_begin path=${path.id}")

            // Free native predictors before creating new ones (do not rely on GC finalize).
            releaseAllPredictors("loadModelsForPath→${path.id}")

            fun copyAsset(p: String): String {
                val f = File(context.filesDir, p.replace("/", "_"))
                context.assets.open(p).use { inp -> FileOutputStream(f).use { out -> inp.copyTo(out) } }
                return f.absolutePath
            }

            fun resolveModel(baseName: String, assetFallback: String): String {
                val candidates = listOf(
                    File(context.filesDir, "prec_paths/${path.id}/${baseName}_$arch.nb"),
                    File(context.getExternalFilesDir(null), "prec_paths/${path.id}/${baseName}_$arch.nb"),
                )
                for (external in candidates) {
                    if (external.isFile && external.length() > 1000L) {
                        Log.i("PaddleLite", "Using model ${external.absolutePath} size=${external.length()}")
                        return external.absolutePath
                    }
                }
                if (path != PrecisionPath.BASELINE) {
                    // Do not silently fall back to float assets for non-baseline paths.
                    throw IllegalStateException(
                        "Missing prec_paths model path=${path.id} base=$baseName arch=$arch " +
                            "(looked under filesDir and externalFilesDir)"
                    )
                }
                Log.w("PaddleLite", "baseline fallback to assets $assetFallback")
                return copyAsset(assetFallback)
            }

            val detPath = resolveModel("det", "paddle/det_v4_4000_mono_$arch.nb")
            val recV3Path = resolveModel("rec_v3", "paddle/rec_v3_mono_$arch.nb")
            val recNumPath = resolveModel("rec_numeric", "paddle/rec_numeric_mono_$arch.nb")
            Log.i(
                "PaddleLite",
                "models path=${path.id} detInt8=${path.detInt8Input} recInt8=${path.recInt8Input} " +
                "detU8=${path.detUInt8Input} recU8=${path.recUInt8Input} " +
                    "det=$detPath rec_v3=$recV3Path rec_num=$recNumPath"
            )

            val config = MobileConfig()
            config.setThreads(4)
            config.setPowerMode(PowerMode.LITE_POWER_HIGH)

            TIER_SCALES.forEach { scale ->
                val t0 = System.currentTimeMillis()
                config.setModelFromFile(detPath)
                val p = PaddlePredictor.createPaddlePredictor(config)
                    ?: throw IllegalStateException("createPaddlePredictor det null path=${path.id} scale=$scale file=$detPath")
                p.getInput(0).resize(longArrayOf(1, 1, scale.toLong(), scale.toLong()))
                sharedTiers[scale] = p
                sharedTierBuffers[scale] = FloatArray(1 * scale * scale)
                sharedTiersInt8[scale] = ByteArray(1 * scale * scale) // exact numel for setData
                Log.i("PaddleLite", "Tier $scale Init: ${System.currentTimeMillis() - t0}ms path=${path.id}")
            }

            config.setModelFromFile(recV3Path)
            sharedRecognizerV3 = PaddlePredictor.createPaddlePredictor(config)
                ?: throw IllegalStateException("createPaddlePredictor rec_v3 null path=${path.id} file=$recV3Path")
            sharedRecognizerV3!!.getInput(0).resize(longArrayOf(1, 1, 48, 320))
            config.setModelFromFile(recNumPath)
            sharedRecognizerNumeric = PaddlePredictor.createPaddlePredictor(config)
                ?: throw IllegalStateException("createPaddlePredictor rec_numeric null path=${path.id} file=$recNumPath")
            sharedRecognizerNumeric!!.getInput(0).resize(longArrayOf(1, 1, 48, 320))
            heartbeat("load_models_done path=${path.id} tiers=${sharedTiers.size}")
        }

        fun switchPrecisionPath(context: Context, path: PrecisionPath) {
            if (!isAvailableGlobally) {
                initializeGlobalBuffers(context)
            }
            try { System.loadLibrary("paddle_lite_jni") } catch (_: Throwable) {}
            // Rethrow as Java so campaign PATH_FAIL / PATH_SKIP can handle.
            try {
                loadModelsForPath(context, path)
            } catch (t: Throwable) {
                Log.e("PaddleLite", "switchPrecisionPath failed path=${path.id}", t)
                throw t
            }
            isAvailableGlobally = true
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

    fun detect(input: Any, targetW: Int? = null, targetH: Int? = null, copyHeatmap: Boolean = true): DetectionResult? {
        val tPop0 = System.nanoTime()
        val w: Int; val h: Int; val srcMat: Mat
        when (input) {
            is BufferSet.Slice -> { w = input.width; h = input.height; srcMat = input.mat }
            is Mat -> { w = targetW ?: input.cols(); h = targetH ?: input.rows(); srcMat = input }
            else -> throw IllegalArgumentException("Unsupported input type for detect")
        }

        // Automatic Tier Selection - respect explicit targets
        val maxEdge = max(targetW ?: w, targetH ?: h)
        val tierScale = TIER_SCALES.filter { it >= maxEdge }.minOrNull() ?: 2560
        heartbeat("det_begin tier=$tierScale ${w}x$h path=${activePrecisionPath.id}")
        val predictor = sharedTiers[tierScale] ?: return null
        val floatData = sharedTierBuffers[tierScale] ?: return null

        val int8Data = sharedTiersInt8[tierScale]
        val useInt8 = activePrecisionPath.detInt8Input
        val useUInt8 = activePrecisionPath.detUInt8Input
        if (useInt8) {
            val buf = int8Data ?: return null
            java.util.Arrays.fill(buf, 0.toByte())
            NativeImageUtils.populateMonoInt8Xor(srcMat, buf, tierScale, tierScale)
        } else if (useUInt8) {
            val buf = int8Data ?: return null
            java.util.Arrays.fill(buf, 0.toByte())
            NativeImageUtils.populateMonoUInt8(srcMat, buf, tierScale, tierScale)
        } else {
            floatData.fill(0.0f)
            val mean = 0.485f; val std = 0.229f
            NativeImageUtils.populateMonoTensor(srcMat, floatData, tierScale, tierScale, mean, std)
        }

        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            val inputTensor = predictor.getInput(0)
            if (activePrecisionPath.detInt8Input || activePrecisionPath.detUInt8Input) {
                val tag = if (activePrecisionPath.detUInt8Input) "det uint8" else "det int8"
                requireSetData(inputTensor.setData(sharedTiersInt8[tierScale]!!), "$tag tier=$tierScale")
            } else {
                requireSetData(inputTensor.setData(floatData), "det float tier=$tierScale")
            }
            val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

            val tInfer0 = System.nanoTime()
            heartbeat("det_run tier=$tierScale")
            predictor.run()
            val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0

            val tJniOut0 = System.nanoTime()
            val outputTensor = predictor.getOutput(0); val dims = outputTensor.shape()

            // Zero-Copy Native Post-Processing (Phase 2) — MUST run before floatData
            // to avoid tensor pointer invalidation from Java-side copy
            val tNativePost0 = System.nanoTime()
            heartbeat("det_post tier=$tierScale")
            val nativeRes = NativeImageUtils.processHeatmap(outputTensor, 0.03f, 10f)  // 0.03f so alignment (via shared detect + runPaddleValleyIterative etc) sees the change
            val tNativePost = (System.nanoTime() - tNativePost0) / 1_000_000.0

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

            val tCopy0 = System.nanoTime()
            // Never Tensor.floatData on uint8/fp16 heatmaps (getFloatData SEGV).
            val heatmap = if (copyHeatmap) NativeImageUtils.heatmapToFloatArray(outputTensor) else null
            val tCopy = if (copyHeatmap) (System.nanoTime() - tCopy0) / 1_000_000.0 else 0.0

            val tJniOut = (System.nanoTime() - tJniOut0) / 1_000_000.0

            val meta = mapOf(
                "t_pop_tensor_ms" to "%.3f".format(tPop),
                "t_jni_in_ms" to "%.3f".format(tJniIn),
                "t_inference_ms" to "%.3f".format(tInfer),
                "t_jni_out_ms" to "%.3f".format(tJniOut),
                "t_native_post_ms" to "%.3f".format(tNativePost),
                "t_copy_tensor_ms" to "%.3f".format(tCopy),
                "dynamic_shape" to "%dx%d".format(w, h)
            )
            return DetectionResult(heatmap, dims[3].toInt(), dims[2].toInt(), meta, nativeBoxes, outputTensor, hist)

        } catch (t: Throwable) {
            Log.e("PaddleDetect", "Detection failed", t); return null
        }
    }

    /**
     * Dynamic-sized detection for arbitrary Mat crops (e.g. Pump Experiment).
     * Resizes the internal Paddle predictor to match the input Mat dimensions exactly.
     */
    fun detectMat(srcMat: Mat, copyHeatmap: Boolean = true): DetectionResult? {
        if (!isAvailable) return null
        val predictor = detectorLarge ?: return null
        val t0 = System.currentTimeMillis()
        val w = srcMat.cols(); val h = srcMat.rows()

        val tPop0 = System.nanoTime()
        val floatData = FloatArray(w * h)
        NativeImageUtils.populateMonoTensor(srcMat, floatData, w, h, 0.485f, 0.229f)
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            val inputTensor = predictor.getInput(0)
            inputTensor.resize(longArrayOf(1, 1, h.toLong(), w.toLong()))
            requireSetData(inputTensor.setData(floatData), "detectMat float ${w}x$h")
            val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

            val tInfer0 = System.nanoTime()
            predictor.run()
            val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0

            val tJniOut0 = System.nanoTime()
            val outputTensor = predictor.getOutput(0)
            val dims = outputTensor.shape()

            // Zero-Copy Native Post-Processing (Phase 2) — MUST run before floatData
            // to avoid tensor pointer invalidation from Java-side copy
            val tNativePost0 = System.nanoTime()
            val nativeRes = NativeImageUtils.processHeatmap(outputTensor, 0.03f, 10f)  // 0.03f so alignment (via shared detect + runPaddleValleyIterative etc) sees the change
            val tNativePost = (System.nanoTime() - tNativePost0) / 1_000_000.0

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
            val tCopy0 = System.nanoTime()
            val heatmap = if (copyHeatmap) NativeImageUtils.heatmapToFloatArray(outputTensor) else null
            val tCopy = if (copyHeatmap) (System.nanoTime() - tCopy0) / 1_000_000.0 else 0.0

            val tJniOut = (System.nanoTime() - tJniOut0) / 1_000_000.0

            val meta = mapOf(
                "t_pop_tensor_ms" to "%.3f".format(tPop),
                "t_jni_in_ms" to "%.3f".format(tJniIn),
                "t_inference_ms" to "%.3f".format(tInfer),
                "t_jni_out_ms" to "%.3f".format(tJniOut),
                "t_native_post_ms" to "%.3f".format(tNativePost),
                "t_copy_tensor_ms" to "%.3f".format(tCopy),
                "dynamic_shape" to "%dx%d".format(w, h)
            )
            return DetectionResult(heatmap, dims[3].toInt(), dims[2].toInt(), meta, nativeBoxes, outputTensor, hist)
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
        heartbeat("rec_v3_begin ${w}x$h path=${activePrecisionPath.id}")

        if (w * h > 320 * 48) {
            Log.e("PaddleDetect", "Bridge dimensions (${w}x${h}) exceed pre-allocated rec tensor capacity.")
            return@withContext RecStageResult("(Size Error)", 0, 0f, null)
        }

        val tPop0 = System.nanoTime()
        val useInt8Rec = activePrecisionPath.recInt8Input
        val useUInt8Rec = activePrecisionPath.recUInt8Input
        if (useInt8Rec) {
            java.util.Arrays.fill(bufferRecInt8, 0.toByte())
            NativeImageUtils.populateMonoInt8Xor(srcMat, bufferRecInt8, 320, 48)
        } else if (useUInt8Rec) {
            java.util.Arrays.fill(bufferRecInt8, 0.toByte())
            NativeImageUtils.populateMonoUInt8(srcMat, bufferRecInt8, 320, 48)
        } else {
            bufferRec.fill(0.0f)
            val mean = 0.5f; val std = 0.5f
            NativeImageUtils.populateMonoTensor(srcMat, bufferRec, 320, 48, mean, std)
        }
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            if (useInt8Rec || useUInt8Rec) {
                val tag = if (useUInt8Rec) "rec_v3 uint8" else "rec_v3 int8"
                requireSetData(predictor.getInput(0).setData(bufferRecInt8), tag)
            } else {
                requireSetData(predictor.getInput(0).setData(bufferRec), "rec_v3 float")
            }
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
        heartbeat("rec_num_begin ${w}x$h path=${activePrecisionPath.id}")

        if (w * h > 320 * 48) {
            Log.e("PaddleDetect", "Bridge dimensions (${w}x${h}) exceed pre-allocated rec tensor capacity.")
            return@withContext RecStageResult("(Size Error)", 0, 0f, null)
        }

        val tPop0 = System.nanoTime()
        val useInt8Rec = activePrecisionPath.recInt8Input
        val useUInt8Rec = activePrecisionPath.recUInt8Input
        if (useInt8Rec) {
            java.util.Arrays.fill(bufferRecInt8, 0.toByte())
            NativeImageUtils.populateMonoInt8Xor(srcMat, bufferRecInt8, 320, 48)
        } else if (useUInt8Rec) {
            java.util.Arrays.fill(bufferRecInt8, 0.toByte())
            NativeImageUtils.populateMonoUInt8(srcMat, bufferRecInt8, 320, 48)
        } else {
            bufferRec.fill(0.0f)
            val mean = 0.5f; val std = 0.5f
            NativeImageUtils.populateMonoTensor(srcMat, bufferRec, 320, 48, mean, std)
        }
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            if (useInt8Rec || useUInt8Rec) {
                val tag = if (useUInt8Rec) "rec_numeric uint8" else "rec_numeric int8"
                requireSetData(predictor.getInput(0).setData(bufferRecInt8), tag)
            } else {
                requireSetData(predictor.getInput(0).setData(bufferRec), "rec_numeric float")
            }
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
