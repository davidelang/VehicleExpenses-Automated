package com.davidlang.vehicleexpensesautomated.ui.util
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
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
    override val name = "Paddle $variant Greedy"
    fun isV3() = variant == "V3"
    
    data class DetectionResult(val heatmap: FloatArray, val width: Int, val height: Int, val metadata: Map<String, String> = emptyMap())
    private val dictionary = mutableListOf<String>()
    private var initError: String? = null
    var isAvailable = false
        private set

    // Predictors anchored to instance lifecycle
    private var detectorLarge: PaddlePredictor? = null
    private var detectorSmall: PaddlePredictor? = null
    private var recognizer: PaddlePredictor? = null

    companion object {
        var isAvailableGlobally = false; private set
        private var sharedDetectorLarge: PaddlePredictor? = null
        private var sharedDetectorSmall: PaddlePredictor? = null
        private var sharedRecognizerV3: PaddlePredictor? = null
        private var sharedRecognizerNumeric: PaddlePredictor? = null
        
        private var isNativeLibLoaded = false

        // Phase 125: Multi-Tier Predictor Array
        val TIER_SCALES = listOf(224, 608, 1024, 2560)
        val sharedTiers = mutableMapOf<Int, PaddlePredictor>()
        val sharedTierBuffers = mutableMapOf<Int, FloatArray>()

        // Phase 116: Unified Rigid Backing Fields
        private var _bufferSetA: BufferSet? = null
        private var _bufferSetB: BufferSet? = null
        private var _deskewBufferSetLarge: BufferSet? = null
        private var _bufferLarge: FloatArray? = null
        private var _sharedBmp2560: Bitmap? = null
        private var _sharedCanvas2560: Canvas? = null
        private var _bufferSmall: FloatArray? = null
        private var _bufferRec: FloatArray? = null
        private var _sharedNv21Buffer: ByteArray? = null
        private var _sharedBmpOdoScratch: Bitmap? = null
        private var _sharedCanvasOdoScratch: Canvas? = null
        private var _redPaint: Paint? = null
        private var _bluePaint4: Paint? = null
        private var _orangePaint: Paint? = null
        private var _grayToAlphaPaint: Paint? = null
        private var _alphaToGrayPaint: Paint? = null
        private var _sharedBuffer: java.nio.ByteBuffer? = null
        private var _sharedBytes: ByteArray? = null

        // Public Non-Null Accessors
        val bufferSetA: BufferSet get() = _bufferSetA!!
        val bufferSetB: BufferSet get() = _bufferSetB!!
        val deskewBufferSetLarge: BufferSet get() = _deskewBufferSetLarge!!
        private val bufferLarge: FloatArray get() = _bufferLarge!!
        val sharedBmp2560: Bitmap get() = _sharedBmp2560!!
        val sharedCanvas2560: Canvas get() = _sharedCanvas2560!!
        private val bufferSmall: FloatArray get() = _bufferSmall!!
        private val bufferRec: FloatArray get() = _bufferRec!!
        val sharedNv21Buffer: ByteArray get() = _sharedNv21Buffer!!
        val sharedBmpOdoScratch: Bitmap get() = _sharedBmpOdoScratch!!
        val sharedCanvasOdoScratch: Canvas get() = _sharedCanvasOdoScratch!!
        val redPaint: Paint get() = _redPaint!!
        val bluePaint4: Paint get() = _bluePaint4!!
        val orangePaint: Paint get() = _orangePaint!!
        val grayToAlphaPaint: Paint get() = _grayToAlphaPaint!!
        val alphaToGrayPaint: Paint get() = _alphaToGrayPaint!!
        val srcPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC) }
        val sharedBuffer: java.nio.ByteBuffer get() = _sharedBuffer!!
        val sharedBytes: ByteArray get() = _sharedBytes!!
        val sharedMatrix = android.graphics.Matrix()

        fun initializeGlobalBuffers(context: Context) {
            if (isAvailableGlobally) return
            val tStart = System.currentTimeMillis()
            Log.i("PaddleLite", "Initializing Global Rigid Buffers")

            _bufferSetA = BufferSet(4000, 3072)
            _bufferSetB = BufferSet(4000, 3072)
            _deskewBufferSetLarge = BufferSet(2560, 2560)
            _deskewBufferSetLarge!!.p.clearChroma()
            _deskewBufferSetLarge!!.s.clearChroma()

            _bufferLarge = FloatArray(1 * 2560 * 2560) // Native is now exclusively 1-channel (Mono)
            _sharedBmp2560 = Bitmap.createBitmap(2560, 2560, Bitmap.Config.ALPHA_8); _sharedCanvas2560 = Canvas(_sharedBmp2560!!)

            _bufferSmall = FloatArray(1 * 512 * 128)
            _bufferRec = FloatArray(1 * 320 * 48)
            
            _sharedNv21Buffer = ByteArray(4000 * 3072 * 3 / 2)
            _sharedBmpOdoScratch = Bitmap.createBitmap(512, 128, Bitmap.Config.ARGB_8888); _sharedCanvasOdoScratch = Canvas(_sharedBmpOdoScratch!!)

            _redPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL; alpha = 120 }
            _bluePaint4 = Paint().apply { color = Color.BLUE; style = Paint.Style.STROKE; strokeWidth = 4f }
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
                val tLib = System.currentTimeMillis() - (tStart + tBuffers)
                
                fun copy(p: String): String {
                    val f = File(context.filesDir, p.replace("/", "_"))
                    context.assets.open(p).use { it.copyTo(FileOutputStream(f)) }
                    return f.absolutePath
                }
                
                val tCopy0 = System.currentTimeMillis()
                val detPath = copy("paddle/det_v4_4000_mono_$arch.nb")
                val tCopy = System.currentTimeMillis() - tCopy0
                Log.i("PaddleLite", "Model copying took ${tCopy}ms")
                
                val config = MobileConfig()
                config.setThreads(4); config.setPowerMode(com.baidu.paddle.lite.PowerMode.LITE_POWER_HIGH)
                
                // Initialize Tiers
                TIER_SCALES.forEach { scale ->
                    val t0 = System.currentTimeMillis()
                    config.setModelFromFile(detPath)
                    val p = PaddlePredictor.createPaddlePredictor(config)
                    p.getInput(0).resize(longArrayOf(1, 1, scale.toLong(), scale.toLong()))
                    sharedTiers[scale] = p
                    sharedTierBuffers[scale] = FloatArray(1 * scale * scale)
                    Log.i("PaddleLite", "Tier $scale Init: ${System.currentTimeMillis() - t0}ms")
                }
                
                config.setModelFromFile(copy("paddle/rec_v3_mono_$arch.nb")); sharedRecognizerV3 = PaddlePredictor.createPaddlePredictor(config); sharedRecognizerV3!!.getInput(0).resize(longArrayOf(1, 1, 48, 320))
                config.setModelFromFile(copy("paddle/rec_numeric_mono_$arch.nb")); sharedRecognizerNumeric = PaddlePredictor.createPaddlePredictor(config); sharedRecognizerNumeric!!.getInput(0).resize(longArrayOf(1, 1, 48, 320))
                
                Log.i("PaddleLite", "Total Global Init: ${System.currentTimeMillis() - tStart}ms")
                
                isAvailableGlobally = true
            } catch (e: Exception) { Log.e("PaddleLite", "Failed Global Init", e) }
        }
    }
    
    init {
        try {
            if (isAvailableGlobally) {
                detectorLarge = sharedDetectorLarge
                detectorSmall = sharedDetectorSmall
                recognizer = if (variant == "V3") sharedRecognizerV3 else sharedRecognizerNumeric
                isAvailable = true
                loadDictionary("paddle/en_dict.txt")
            }
        } catch (e: Throwable) {
            isAvailable = false; initError = e.message
            Log.e("PaddleLite", "Failed to initialize engine instance", e)
        }
    }

    private fun loadDictionary(assetPath: String) {
        dictionary.clear()
        context.assets.open(assetPath).bufferedReader().use { reader -> 
            reader.forEachLine { dictionary.add(it) } 
        }
    }
    
    fun getDictionary(): List<String> = dictionary

    fun detect(input: Any, targetW: Int? = null, targetH: Int? = null): DetectionResult? {
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
        val predictor = sharedTiers[tierScale] ?: return null
        val floatData = sharedTierBuffers[tierScale] ?: return null
        
        floatData.fill(0.0f)
        
        val mean = 0.485f; val std = 0.229f
        // Populate the tier-sized buffer (only the Mat region)
        NativeImageUtils.populateMonoTensor(srcMat, floatData, tierScale, tierScale, mean, std)
        
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            val inputTensor = predictor.getInput(0); inputTensor.setData(floatData)
            val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

            val tInfer0 = System.nanoTime()
            predictor.run()
            val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0

            val tJniOut0 = System.nanoTime()
            val outputTensor = predictor.getOutput(0); val dims = outputTensor.shape()
            val heatmap = outputTensor.floatData
            val tJniOut = (System.nanoTime() - tJniOut0) / 1_000_000.0

            val meta = mapOf(
                "t_pop_tensor_ms" to "%.3f".format(tPop),
                "t_jni_in_ms" to "%.3f".format(tJniIn),
                "t_inference_ms" to "%.3f".format(tInfer),
                "t_jni_out_ms" to "%.3f".format(tJniOut)
            )
            return DetectionResult(heatmap, dims[3].toInt(), dims[2].toInt(), meta)
        } catch (t: Throwable) { 
            Log.e("PaddleDetect", "Detection failed", t); return null 
        }
    }

    /**
     * Dynamic-sized detection for arbitrary Mat crops (e.g. Pump Experiment).
     * Resizes the internal Paddle predictor to match the input Mat dimensions exactly.
     */
    fun detectMat(srcMat: Mat): DetectionResult? {
        if (!isAvailable) return null
        val predictor = detectorLarge ?: return null
        
        val w = srcMat.cols()
        val h = srcMat.rows()
        
        val tPop0 = System.nanoTime()
        val floatData = FloatArray(w * h)
        val mean = 0.485f
        val std = 0.229f
        
        NativeImageUtils.populateMonoTensor(srcMat, floatData, w, h, mean, std)
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0
        
        try {
            val tJniIn0 = System.nanoTime()
            val inputTensor = predictor.getInput(0)
            inputTensor.resize(longArrayOf(1, 1, h.toLong(), w.toLong()))
            inputTensor.setData(floatData)
            val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

            val tInfer0 = System.nanoTime()
            predictor.run()
            val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0

            val tJniOut0 = System.nanoTime()
            val outputTensor = predictor.getOutput(0)
            val dims = outputTensor.shape()
            val heatmap = outputTensor.floatData
            
            var minVal = Float.MAX_VALUE
            var maxVal = Float.MIN_VALUE
            for (v in heatmap) {
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
            }
            Log.i("PaddleDetect", "detectMat Out: dims=${dims.joinToString("x")} size=${heatmap.size} min=$minVal max=$maxVal")
            
            val tJniOut = (System.nanoTime() - tJniOut0) / 1_000_000.0

            val meta = mapOf(
                "t_pop_tensor_ms" to "%.3f".format(tPop),
                "t_jni_in_ms" to "%.3f".format(tJniIn),
                "t_inference_ms" to "%.3f".format(tInfer),
                "t_jni_out_ms" to "%.3f".format(tJniOut),
                "dynamic_shape" to "%dx%d".format(w, h)
            )
            return DetectionResult(heatmap, dims[3].toInt(), dims[2].toInt(), meta)
        } catch (t: Throwable) {
            Log.e("PaddleDetect", "Dynamic detection failed", t)
            return null
        }
    }

    suspend fun runConstrainedStatic(input: Any, dictionary: List<String>): RecStageResult = withContext(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        if (recognizer == null) return@withContext RecStageResult("(Engine Error)", 0, 0f, null)

        val w: Int; val h: Int; val srcMat: Mat
        when (input) {
            is BufferSet.Slice -> { w = input.width; h = input.height; srcMat = input.mat }
            is Mat -> { w = input.cols(); h = input.rows(); srcMat = input }
            else -> throw IllegalArgumentException("Unsupported input type for runConstrainedStatic")
        }

        if (w * h > 320 * 48) {
             Log.e("PaddleDetect", "Bridge dimensions (${w}x${h}) exceed pre-allocated rec tensor capacity.")
             return@withContext RecStageResult("(Size Error)", 0, 0f, null)
        }

        val tPop0 = System.nanoTime()
        bufferRec.fill(0.0f)
        val mean = 0.5f; val std = 0.5f
        NativeImageUtils.populateMonoTensor(srcMat, bufferRec, 320, 48, mean, std)
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            recognizer!!.getInput(0).setData(bufferRec)
            val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

            val tInfer0 = System.nanoTime()
            recognizer!!.run()
            val tInfer = (System.nanoTime() - tInfer0) / 1_000_000.0

            val tJniOut0 = System.nanoTime()
            val outputTensor = recognizer!!.getOutput(0); val data = outputTensor.floatData; val dims = outputTensor.shape()
            val tJniOut = (System.nanoTime() - tJniOut0) / 1_000_000.0

            val meta = mapOf(
                "t_pop_tensor_ms" to "%.3f".format(tPop),
                "t_jni_in_ms" to "%.3f".format(tJniIn),
                "t_inference_ms" to "%.3f".format(tInfer),
                "t_jni_out_ms" to "%.3f".format(tJniOut)
            )

            val seqLen = dims[1].toInt(); val dictSize = dims[2].toInt(); val result = StringBuilder()
            var lastIdx = -1; var totalConf = 0f; var charCount = 0; var lastConf = 1.0f

            for (i in 0 until seqLen) {
                var maxIdx = 0; var maxVal = -1f; val searchLimit = dictSize
                for (j in 0 until searchLimit) { val v = data[i * dictSize + j]; if (v > maxVal) { maxVal = v; maxIdx = j } }
                if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) {

                    if (result.length < 4 || maxVal >= (0.60f * lastConf)) {
                        result.append(dictionary[maxIdx - 1]); totalConf += maxVal; charCount++; lastConf = maxVal
                    } else break
                }
                lastIdx = maxIdx
            }
            val finalStr = result.toString(); val finalConf = if (charCount > 0) totalConf / charCount else 0f
            return@withContext RecStageResult(finalStr, System.currentTimeMillis() - tStart, finalConf, null, meta)
        } catch (t: Throwable) { 
            return@withContext RecStageResult("(Inference Error)", 0, 0f, null)
        }
    }

    data class RecStageResult(val text: String, val timeMs: Long, val confidence: Float, val ocrInputB64: String? = null, val metadata: Map<String, String> = emptyMap())
    override suspend fun recognize(input: Any): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val predictor = recognizer ?: return@withContext OcrResult(engineName = name, debugText = "Predictor null")

        val w: Int; val h: Int
        when (input) {
            is Bitmap -> { w = input.width; h = input.height }
            is BufferSet.Slice -> { w = input.width; h = input.height }
            is Mat -> { w = input.cols(); h = input.rows() }
            else -> throw IllegalArgumentException("Unsupported input type for recognize")
        }

        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Not Available", imageWidth = w, imageHeight = h)

        val res = runConstrainedStatic(input, dictionary)
        OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = res.text,
            textBlocks = listOf(TextBlock(res.text, Rect(0, 0, w, h))),
            imageWidth = w,
            imageHeight = h,
            metadata = res.metadata
        )
    }
}
