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

        // Detector heatmap int8 output scale (matches ARM int8 model quant scale)
        private const val DET_HEATMAP_INT8_SCALE = 0.00787f
        // u_val != 0 filter (threshold=1): 256-bucket hist shows mass at 0 and signal at higher
        // buckets but no gradual ramp in buckets 1-20 (f≈0.004–0.078), so any positive uint8 counts.
        private const val DET_HEATMAP_INT8_U_THRESHOLD = 1
        private val DET_HEATMAP_INT8_FLOAT_THRESHOLD =
            DET_HEATMAP_INT8_U_THRESHOLD * DET_HEATMAP_INT8_SCALE

        // Phase 125: Multi-Tier Predictor Array
        val TIER_SCALES = listOf(224, 608, 1024, 2048, 2560)
        val sharedTiers = mutableMapOf<Int, PaddlePredictor>()
        val sharedTierBuffers = mutableMapOf<Int, java.nio.ByteBuffer>()
        val sharedTierFloatBuffers = mutableMapOf<Int, FloatArray>()
        private var sharedMaxInt8Buffer: java.nio.ByteBuffer? = null
        private var sharedRecInt8Buffer: java.nio.ByteBuffer? = null

        // Phase 116: Unified Rigid Backing Fields
        private var _bufferSetA: BufferSet? = null
        private var _bufferSetB: BufferSet? = null
        private var _deskewBufferSetLarge: BufferSet? = null
        private var _detBufferSet: BufferSet? = null
        private var _recBufferSet: BufferSet? = null
        private val vehicleOdoBuffers = mutableMapOf<Int, BufferSet>()
        private var _sharedBmp2048: Bitmap? = null
        private var _sharedCanvas2048: Canvas? = null
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
        val sharedBmp2048: Bitmap get() = _sharedBmp2048!!
        val sharedCanvas2048: Canvas get() = _sharedCanvas2048!!
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
            _recBufferSet = BufferSet(1024, 48)

            _sharedBmp2048 = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ALPHA_8); _sharedCanvas2048 = Canvas(_sharedBmp2048!!)
            sharedRecInt8Buffer = java.nio.ByteBuffer.allocateDirect(1024 * 48).order(java.nio.ByteOrder.nativeOrder())

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
                val arch = if (Build.SUPPORTED_ABIS[0].contains("arm")) "armv8" else "x86_64"
                Log.i("PaddleDiag", "arch=$arch isX86=${!Build.SUPPORTED_ABIS[0].contains("arm")}")

                fun copy(p: String): String {
                    val f = File(context.filesDir, p.replace("/", "_"))
                    context.assets.open(p).use { it.copyTo(FileOutputStream(f)) }
                    return f.absolutePath
                }

                val tCopy0 = System.currentTimeMillis()
                val detPath = copy(
                    if (Build.SUPPORTED_ABIS[0].contains("arm")) "paddle/det_v4_4000_mono_int8_$arch.nb"
                    else "paddle/det_v4_4000_mono_$arch.nb"
                )
                Log.i("PaddleDiag", "detPath=$detPath modelKind=${if (Build.SUPPORTED_ABIS[0].contains("arm")) "int8" else "float"}")
                val tCopy = System.currentTimeMillis() - tCopy0
                Log.i("PaddleLite", "Model copying took ${tCopy}ms")

                val config = MobileConfig()
                config.setThreads(4); config.setPowerMode(com.baidu.paddle.lite.PowerMode.LITE_POWER_HIGH)
                config.setKeepQuantizedWeights(true)

                // Initialize Tiers
                TIER_SCALES.forEach { scale ->
                    val t0 = System.currentTimeMillis()
                    config.setModelFromFile(detPath)
                    val p = PaddlePredictor.createPaddlePredictor(config)
                    p.getInput(0).resize(longArrayOf(1, 1, scale.toLong(), scale.toLong()))
                    sharedTiers[scale] = p
                    sharedTierBuffers[scale] = java.nio.ByteBuffer.allocateDirect(1 * scale * scale).order(java.nio.ByteOrder.nativeOrder())
                    if (!Build.SUPPORTED_ABIS[0].contains("arm")) {
                        sharedTierFloatBuffers[scale] = FloatArray(scale * scale)
                    }
                    Log.i("PaddleLite", "Tier $scale Init: ${System.currentTimeMillis() - t0}ms")
                }
                val maxTier = TIER_SCALES.maxOrNull() ?: 2560
                sharedMaxInt8Buffer = java.nio.ByteBuffer.allocateDirect(maxTier * maxTier).order(java.nio.ByteOrder.nativeOrder())
                Log.i("PaddleDiag", "sharedMaxInt8Buffer allocated cap=${maxTier * maxTier}")

                config.setModelFromFile(copy("paddle/rec_v3_mono_int8_$arch.nb")); sharedRecognizerV3 = PaddlePredictor.createPaddlePredictor(config); sharedRecognizerV3!!.getInput(0).resize(longArrayOf(1, 1, 48, 1024))
                config.setModelFromFile(copy("paddle/rec_numeric_mono_int8_$arch.nb")); sharedRecognizerNumeric = PaddlePredictor.createPaddlePredictor(config); sharedRecognizerNumeric!!.getInput(0).resize(longArrayOf(1, 1, 48, 1024))

                loadDictionary(context, "paddle/en_dict.txt", dictionaryV3)
                // digits_only.txt kept as asset but not loaded; numeric pipeline uses dictionaryV3 with ALLOWED_DIGITS

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

    /** Temp diag: log hist bucket counts 1-20 to verify no low-bucket noise ramp (remove after confirm). */
    private fun logLowBucketHistDiag(site: String, hist: IntArray) {
        if (hist.size < 21) return
        val counts = (1..20).joinToString(" ") { hist[it].toString() }
        Log.i("PaddleDiag", "$site low-buckets-1-20: $counts (temp diag: no-ramp check)")
    }

    /** x86_64 only: short-lived float output → direct uint8 (f*255) into long-lived buf (side-effect only). */
    private fun wrapX86DetectorOutputAsInt8(
        outputTensor: Any,
        dims: LongArray,
        site: String,
        tierScale: Int? = null,
        useMaxInt8Buffer: Boolean = false,
    ) {
        val w = dims[3].toInt()
        val h = dims[2].toInt()
        val floatData = (outputTensor as com.baidu.paddle.lite.Tensor).floatData
        val expected = w * h
        var fMin = Float.MAX_VALUE
        var fMax = -Float.MAX_VALUE
        for (f in floatData) {
            if (f < fMin) fMin = f
            if (f > fMax) fMax = f
        }
        Log.i(
            "PaddleDiag",
            "$site float tensor FULL min=%.6f max=%.6f count=${floatData.size} w=$w h=$h (any negatives?)".format(fMin, fMax),
        )
        Log.i(
            "PaddleDiag",
            "$site before wrapper outputPrec=float floatCount=${floatData.size} w=$w h=$h expected=$expected",
        )
        if (floatData.isEmpty() || w <= 0 || h <= 0) {
            Log.e("PaddleDiag", "$site wrapper skipped: invalid float output count=${floatData.size} w=$w h=$h")
            return
        }
        if (floatData.size != expected) {
            Log.e("PaddleDiag", "$site wrapper size mismatch: floatCount=${floatData.size} expected=$expected (w=$w h=$h)")
            return
        }
        val int8DestCap = when {
            tierScale != null -> sharedTierBuffers[tierScale]?.capacity() ?: 0
            useMaxInt8Buffer -> sharedMaxInt8Buffer?.capacity() ?: 0
            else -> 0
        }
        if (int8DestCap > 0 && int8DestCap < expected) {
            Log.e("PaddleDiag", "$site wrapper int8 dest too small: cap=$int8DestCap expected=$expected")
            return
        }
        Log.i(
            "PaddleDiag",
            "$site using long-lived int8 buf as dest (cap=$int8DestCap w*h=$expected)",
        )
        val int8Buf = when {
            tierScale != null -> sharedTierBuffers[tierScale]
            useMaxInt8Buffer -> sharedMaxInt8Buffer
            else -> null
        }
        if (int8Buf != null) {
            if (useMaxInt8Buffer) {
                Log.i(
                    "PaddleDiag",
                    "detectMat x86 using long-lived int8 dest for ${w}x$h crop cap=${int8Buf.capacity()} w*h=$expected",
                )
            }
            int8Buf.clear()
            NativeImageUtils.populateUint8FromFloat(floatData, int8Buf, expected)
            int8Buf.position(0)
            Log.i(
                "PaddleDiag",
                "$site populated uint8 (f*255) to long-lived buf; dest cap=${int8Buf.capacity()} (w*h=$expected)",
            )
        }
    }

    fun detect(input: Any, targetW: Int? = null, targetH: Int? = null, copyHeatmap: Boolean = true): DetectionResult? {
        val tPop0 = System.nanoTime()
        val w: Int; val h: Int; val srcMat: Mat
        when (input) {
            is BufferSet.Slice -> {
                w = input.width; h = input.height; srcMat = input.mat
            }
            is Mat -> { w = targetW ?: input.cols(); h = targetH ?: input.rows(); srcMat = input }
            else -> throw IllegalArgumentException("Unsupported input type for detect")
        }

        // Automatic Tier Selection - respect explicit targets
        val maxEdge = max(targetW ?: w, targetH ?: h)
        val tierScale = TIER_SCALES.filter { it >= maxEdge }.minOrNull() ?: 2560
        val predictor = sharedTiers[tierScale] ?: return null
        val isArm = Build.SUPPORTED_ABIS[0].contains("arm")
        val armInputBuf = if (isArm) {
            java.nio.ByteBuffer.allocateDirect(tierScale * tierScale)
                .order(java.nio.ByteOrder.nativeOrder())
        } else null
        if (isArm) {
            Log.i(
                "PaddleDiag",
                "detect tier=$tierScale: ARM input on temp buf; output forced kInt8 then copied post-run",
            )
        } else {
            Log.i("PaddleDiag", "detect tier=$tierScale: x86 float temp I/O; helper populates uint8 (f*255) after run")
        }

        if (isArm) {
            armInputBuf!!.clear()
            NativeImageUtils.quantizeMonoToInt8(srcMat, armInputBuf, tierScale, tierScale, w, h)
            armInputBuf.position(0)
        } else {
            val floatData = sharedTierFloatBuffers[tierScale] ?: return null
            floatData.fill(0f)
            NativeImageUtils.populateMonoTensor(srcMat, floatData, tierScale, tierScale, 0.485f, 0.229f)
        }

        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            if (isArm) {
                Log.i("PaddleDiag", "before bindInput int8 tier=$tierScale (temp input buf)")
                NativeImageUtils.bindInputInt8(predictor.getInput(0), armInputBuf!!, tierScale, tierScale)
                Log.i("PaddleDiag", "after bindInput int8 tier=$tierScale")
            } else {
                val floatData = sharedTierFloatBuffers[tierScale]!!
                Log.i("PaddleDiag", "before bindInput float tier=$tierScale x86=true")
                predictor.getInput(0).setData(floatData)
                Log.i("PaddleDiag", "after bindInput float tier=$tierScale")
            }
            val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

            val outputTensor = predictor.getOutput(0)
            if (isArm) {
                NativeImageUtils.forceOutputTensorInt8Precision(outputTensor)
            }

            val tInfer0 = System.nanoTime()
            predictor.run()
            Log.i("PaddleDiag", "after run tier=$tierScale")
            val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0

            val tJniOut0 = System.nanoTime()
            val dims = outputTensor.shape()

            val outW = dims[3].toInt()
            val outH = dims[2].toInt()
            val expected = outW * outH
            if (!isArm) {
                wrapX86DetectorOutputAsInt8(outputTensor, dims, "detect tier=$tierScale", tierScale = tierScale)
            }
            val int8Buf = sharedTierBuffers[tierScale]!!
            int8Buf.position(0)
            if (isArm) {
                int8Buf.clear()
                val copied = NativeImageUtils.copyTensorInt8ToBuffer(
                    outputTensor, int8Buf, expected, DET_HEATMAP_INT8_SCALE,
                )
                if (!copied) {
                    Log.e("PaddleDiag", "detect tier=$tierScale: ARM output copy/quantize failed")
                    return null
                }
                int8Buf.position(0)
            }

            val tNativePost0 = System.nanoTime()
            Log.i(
                "PaddleDiag",
                "detect tier=$tierScale post-process from long-lived uint8 buf cap=${int8Buf.capacity()} w=$outW h=$outH",
            )
            val nativeRes = NativeImageUtils.processHeatmapFromInt8Buffer(
                int8Buf, outW, outH, DET_HEATMAP_INT8_U_THRESHOLD, 10f,
            )
            val tNativePost = (System.nanoTime() - tNativePost0) / 1_000_000.0

            val nativeBoxes = mutableListOf<DetectionBox>()
            var hist: IntArray? = null
            if (nativeRes != null && nativeRes.size >= 256) {
                val boxFloats = nativeRes.size - 256
                val nboxes = boxFloats / 9
                if (nboxes == 0) {
                    Log.i("PaddleDiag", "detect tier=$tierScale: zero detector boxes (safe empty post-process)")
                }
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
                hist = IntArray(256)
                for (i in 0 until 256) hist[i] = nativeRes[boxFloats + i].toInt()
                logLowBucketHistDiag("detect tier=$tierScale", hist)
            }

            val tCopy0 = System.nanoTime()
            val heatmap = if (copyHeatmap) {
                NativeImageUtils.dequantHeatmapInt8ToFloat(int8Buf, expected, DET_HEATMAP_INT8_SCALE)
            } else null
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
        val w = srcMat.cols(); val h = srcMat.rows()

        val isArm = Build.SUPPORTED_ABIS[0].contains("arm")
        if (isArm) {
            Log.i(
                "PaddleDiag",
                "detectMat ${w}x$h: ARM input on temp buf; output forced kInt8 then copied post-run",
            )
        } else {
            Log.i("PaddleDiag", "detectMat ${w}x$h: x86 float temp I/O; helper populates uint8 (f*255) after run")
        }
        val tPop0 = System.nanoTime()
        val armInputBuf = if (isArm) {
            java.nio.ByteBuffer.allocateDirect(w * h).order(java.nio.ByteOrder.nativeOrder())
        } else null
        val floatData = if (isArm) null else FloatArray(w * h)
        if (isArm) {
            NativeImageUtils.quantizeMonoToInt8(srcMat, armInputBuf!!, w, h)
            armInputBuf.position(0)
        } else {
            NativeImageUtils.populateMonoTensor(srcMat, floatData!!, w, h, 0.485f, 0.229f)
        }
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            val inputTensor = predictor.getInput(0)
            if (isArm) {
                Log.i("PaddleDiag", "detectMat before bindInput int8 ${w}x$h (temp input buf)")
                NativeImageUtils.bindInputInt8(inputTensor, armInputBuf!!, w, h)
                Log.i("PaddleDiag", "detectMat after bindInput int8")
            } else {
                Log.i("PaddleDiag", "detectMat before bindInput float ${w}x$h x86=true")
                inputTensor.resize(longArrayOf(1, 1, h.toLong(), w.toLong()))
                inputTensor.setData(floatData!!)
                Log.i("PaddleDiag", "detectMat after bindInput float")
            }
            val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

            val outputTensor = predictor.getOutput(0)
            if (isArm) {
                NativeImageUtils.forceOutputTensorInt8Precision(outputTensor)
            }

            val tInfer0 = System.nanoTime()
            predictor.run()
            Log.i("PaddleDiag", "detectMat after run ${w}x$h")
            val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0

            val tJniOut0 = System.nanoTime()
            val dims = outputTensor.shape()

            val expected = w * h
            if (!isArm) {
                Log.i("PaddleDiag", "detectMat before wrapper ${w}x$h")
                wrapX86DetectorOutputAsInt8(
                    outputTensor, dims, "detectMat ${w}x$h", useMaxInt8Buffer = true,
                )
                Log.i("PaddleDiag", "detectMat after wrapper ${w}x$h")
            }
            val int8Buf = sharedMaxInt8Buffer!!
            int8Buf.position(0)
            if (isArm) {
                int8Buf.clear()
                val copied = NativeImageUtils.copyTensorInt8ToBuffer(
                    outputTensor, int8Buf, expected, DET_HEATMAP_INT8_SCALE,
                )
                if (!copied) {
                    Log.e("PaddleDiag", "detectMat ${w}x$h: ARM output copy/quantize failed")
                    return null
                }
                int8Buf.position(0)
            }

            val tNativePost0 = System.nanoTime()
            Log.i(
                "PaddleDiag",
                "detectMat ${w}x$h post-process from long-lived uint8 buf cap=${int8Buf.capacity()} w*h=${w * h}",
            )
            val nativeRes = NativeImageUtils.processHeatmapFromInt8Buffer(
                int8Buf, w, h, DET_HEATMAP_INT8_U_THRESHOLD, 10f,
            )
            val tNativePost = (System.nanoTime() - tNativePost0) / 1_000_000.0

            val nativeBoxes = mutableListOf<DetectionBox>()
            var hist: IntArray? = null
            if (nativeRes != null && nativeRes.size >= 256) {
                val boxFloats = nativeRes.size - 256
                val nboxes = boxFloats / 9
                if (nboxes == 0) {
                    Log.i("PaddleDiag", "detectMat ${w}x$h: zero detector boxes (safe empty post-process)")
                }
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
                hist = IntArray(256)
                for (i in 0 until 256) hist[i] = nativeRes[boxFloats + i].toInt()
                logLowBucketHistDiag("detectMat ${w}x$h", hist)
            }
            val tCopy0 = System.nanoTime()
            val heatmap = if (copyHeatmap) {
                NativeImageUtils.dequantHeatmapInt8ToFloat(int8Buf, expected, DET_HEATMAP_INT8_SCALE)
            } else null
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

    private fun recBindDimensions(useRecSetPs: Boolean, recSet: BufferSet?): Pair<Int, Int> {
        return if (useRecSetPs && recSet != null) {
            recSet.width to recSet.height
        } else {
            1024 to 48
        }
    }

    private suspend fun processOcr(input: Any, predictor: PaddlePredictor?, dictionary: List<String>, recSet: BufferSet? = null): RecStageResult = withContext(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        if (predictor == null) return@withContext RecStageResult("(Engine Error)", 0, 0f, null)

        val w: Int; val h: Int; val srcMat: Mat; val useRecSetPs: Boolean
        when (input) {
            is BufferSet.Slice -> {
                w = input.width; h = input.height; srcMat = input.mat
                useRecSetPs = recSet != null && input is BufferSet.Instance && input === recSet.p
            }
            is Mat -> { w = input.cols(); h = input.rows(); srcMat = input; useRecSetPs = false }
            else -> throw IllegalArgumentException("Unsupported input type for processOcr")
        }

        val (bindW, bindH) = recBindDimensions(useRecSetPs, recSet)
        if (w * h > bindW * bindH) {
            Log.e("PaddleDetect", "Bridge dimensions (${w}x${h}) exceed rec tensor capacity (${bindW}x${bindH}).")
            return@withContext RecStageResult("(Size Error)", 0, 0f, null)
        }

        val tPop0 = System.nanoTime()
        val recBuf = sharedRecInt8Buffer ?: return@withContext RecStageResult("(Buffer Error)", 0, 0f, null)
        recBuf.clear()
        val bindBuf: java.nio.ByteBuffer = if (useRecSetPs && recSet != null) {
            Log.i("PaddleDiag", "processOcr useRecSetPs uint8 zero-copy bind=${bindW}x${bindH}")
            val buf = recSet.p.raw
            NativeImageUtils.bindInputUInt8(predictor.getInput(0), buf, bindW, bindH)
            buf
        } else if (input is BufferSet.Slice) {
            Log.i("PaddleDiag", "processOcr uint8 zero-copy crop bind=${w}x${h}")
            val buf = input.raw
            NativeImageUtils.bindInputUInt8(predictor.getInput(0), buf, w, h)
            buf
        } else {
            NativeImageUtils.quantizeMonoToInt8(srcMat, recBuf, bindW, bindH, w, h)
            recBuf.position(0)
            NativeImageUtils.bindInputInt8(predictor.getInput(0), recBuf, bindW, bindH)
            recBuf
        }
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn = 0.0

            val tInfer0 = System.nanoTime()
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

    private suspend fun processOcrNumeric(input: Any, predictor: PaddlePredictor?, dictionary: List<String>, allowedIndices: Set<Int>, recSet: BufferSet? = null): RecStageResult = withContext(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        if (predictor == null) return@withContext RecStageResult("(Engine Error)", 0, 0f, null)

        val w: Int; val h: Int; val srcMat: Mat; val useRecSetPs: Boolean
        when (input) {
            is BufferSet.Slice -> {
                w = input.width; h = input.height; srcMat = input.mat
                useRecSetPs = recSet != null && input is BufferSet.Instance && input === recSet.p
            }
            is Mat -> { w = input.cols(); h = input.rows(); srcMat = input; useRecSetPs = false }
            else -> throw IllegalArgumentException("Unsupported input type for processOcr")
        }

        val (bindW, bindH) = recBindDimensions(useRecSetPs, recSet)
        if (w * h > bindW * bindH) {
            Log.e("PaddleDetect", "Bridge dimensions (${w}x${h}) exceed rec tensor capacity (${bindW}x${bindH}).")
            return@withContext RecStageResult("(Size Error)", 0, 0f, null)
        }

        val tPop0 = System.nanoTime()
        val recBuf = sharedRecInt8Buffer ?: return@withContext RecStageResult("(Buffer Error)", 0, 0f, null)
        recBuf.clear()
        val bindBuf: java.nio.ByteBuffer = if (useRecSetPs && recSet != null) {
            Log.i("PaddleDiag", "processOcrNumeric useRecSetPs uint8 zero-copy bind=${bindW}x${bindH}")
            val buf = recSet.p.raw
            NativeImageUtils.bindInputUInt8(predictor.getInput(0), buf, bindW, bindH)
            buf
        } else if (input is BufferSet.Slice) {
            Log.i("PaddleDiag", "processOcrNumeric uint8 zero-copy crop bind=${w}x${h}")
            val buf = input.raw
            NativeImageUtils.bindInputUInt8(predictor.getInput(0), buf, w, h)
            buf
        } else {
            NativeImageUtils.quantizeMonoToInt8(srcMat, recBuf, bindW, bindH, w, h)
            recBuf.position(0)
            NativeImageUtils.bindInputInt8(predictor.getInput(0), recBuf, bindW, bindH)
            recBuf
        }
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn = 0.0

            val tInfer0 = System.nanoTime()
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

    override suspend fun recognize(input: Any): OcrResult = doRecognize(input, null)

    suspend fun recognize(input: Any, recSet: BufferSet): OcrResult = doRecognize(input, recSet)

    private suspend fun doRecognize(input: Any, recSet: BufferSet?): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val w: Int; val h: Int
        when (input) {
            is Bitmap -> { w = input.width; h = input.height }
            is BufferSet.Slice -> { w = input.width; h = input.height }
            is Mat -> { w = input.cols(); h = input.rows() }
            else -> throw IllegalArgumentException("Unsupported input type for recognize")
        }

        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Not Available", imageWidth = w, imageHeight = h)

        val res = processOcr(input, sharedRecognizerV3, dictionaryV3, recSet ?: recBufferSet)
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

    suspend fun recognizeNumeric(input: Any): OcrResult = doRecognizeNumeric(input, null)

    suspend fun recognizeNumeric(input: Any, recSet: BufferSet): OcrResult = doRecognizeNumeric(input, recSet)

    private suspend fun doRecognizeNumeric(input: Any, recSet: BufferSet?): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val w: Int; val h: Int
        when (input) {
            is Bitmap -> { w = input.width; h = input.height }
            is BufferSet.Slice -> { w = input.width; h = input.height }
            is Mat -> { w = input.cols(); h = input.rows() }
            else -> throw IllegalArgumentException("Unsupported input type for recognizeNumeric")
        }

        if (!isAvailable) return@withContext OcrResult(engineName = "Paddle Numeric Greedy", debugText = "Not Available", imageWidth = w, imageHeight = h)

        val res = processOcrNumeric(input, sharedRecognizerV3, dictionaryV3, ALLOWED_DIGITS, recSet ?: recBufferSet)
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
    suspend fun recognizeNumericDecimal(input: Any): OcrResult = doRecognizeNumericDecimal(input, null)

    suspend fun recognizeNumericDecimal(input: Any, recSet: BufferSet): OcrResult =
        doRecognizeNumericDecimal(input, recSet)

    private suspend fun doRecognizeNumericDecimal(input: Any, recSet: BufferSet?): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val w: Int; val h: Int
        when (input) {
            is Bitmap -> { w = input.width; h = input.height }
            is BufferSet.Slice -> { w = input.width; h = input.height }
            is Mat -> { w = input.cols(); h = input.rows() }
            else -> throw IllegalArgumentException("Unsupported input type for recognizeNumericDecimal")
        }

        if (!isAvailable) return@withContext OcrResult(engineName = "Paddle Numeric Decimal Greedy", debugText = "Not Available", imageWidth = w, imageHeight = h)

        val res = processOcrNumeric(input, sharedRecognizerV3, dictionaryV3, ALLOWED_DIGITS_DECIMAL, recSet)
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
