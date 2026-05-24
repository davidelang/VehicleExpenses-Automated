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

class NativePaddleEngine(private val context: Context, private val variant: String = "V3", val useMono: Boolean = false) : OcrEngine {
    override val name = "Paddle $variant Greedy" + if (useMono) " Mono" else ""
    fun isV3() = variant == "V3"
    
    data class DetectionResult(val heatmap: FloatArray, val width: Int, val height: Int, val metadata: Map<String, String> = emptyMap())
    private val dictionary = mutableListOf<String>()
    private var initError: String? = null
    var isAvailable = false
        private set

    // Phase 115: Anchored Instance Predictors (Ensures native handles are pinned to the instance object graph)
    private var detectorLarge: PaddlePredictor? = null
    private var detectorSmall: PaddlePredictor? = null
    private var recognizer: PaddlePredictor? = null

    companion object {
        var isAvailableGlobally = false; private set
        private var sharedDetectorLarge: PaddlePredictor? = null
        private var sharedDetectorSmall: PaddlePredictor? = null
        private var sharedRecognizerV3: PaddlePredictor? = null
        private var sharedRecognizerNumeric: PaddlePredictor? = null
        
        // Mono Models
        private var sharedDetectorLargeMono: PaddlePredictor? = null
        private var sharedDetectorSmallMono: PaddlePredictor? = null
        private var sharedRecognizerV3Mono: PaddlePredictor? = null
        private var sharedRecognizerNumericMono: PaddlePredictor? = null

        private var isNativeLibLoaded = false

        // Phase 115: Safe Rigid Backing Fields (Eliminates background JNI locks)
        private var _bufferSetA: BufferSet? = null
        private var _bufferSetB: BufferSet? = null
        private var _deskewBufferSet2048: BufferSet? = null
        private var _bufferLarge: FloatArray? = null
        private var _bufferLargeMono: FloatArray? = null
        private var _sharedBmp2048: Bitmap? = null
        private var _sharedCanvas2048: Canvas? = null
        private var _sharedBmp2048Mono: Bitmap? = null
        private var _sharedCanvas2048Mono: Canvas? = null
        private var _bufferSmall: FloatArray? = null
        private var _bufferSmallMono: FloatArray? = null
        private var _sharedBmpSmall: Bitmap? = null
        private var _sharedCanvasSmall: Canvas? = null
        private var _bufferRec: FloatArray? = null
        private var _bufferRecMono: FloatArray? = null
        private var _sharedBmpRec: Bitmap? = null
        private var _sharedCanvasRec: Canvas? = null
        private var _sharedNv21Buffer: ByteArray? = null
        private var _sharedBmpOdoScratch: Bitmap? = null
        private var _sharedCanvasOdoScratch: Canvas? = null
        private var _redPaint: Paint? = null
        private var _orangePaint: Paint? = null
        private var _grayToAlphaPaint: Paint? = null
        private var _alphaToGrayPaint: Paint? = null
        private var _sharedMonoBuffer: java.nio.ByteBuffer? = null
        private var _sharedMonoBytes: ByteArray? = null

        // Public Non-Null Accessors (API Stability)
        val bufferSetA: BufferSet get() = _bufferSetA!!
        val bufferSetB: BufferSet get() = _bufferSetB!!
        val fullBufferSet: BufferSet get() = _bufferSetA!! // Alias for backward compatibility during transition
        val deskewBufferSet2048: BufferSet get() = _deskewBufferSet2048!!
        private val bufferLarge: FloatArray get() = _bufferLarge!!
        private val bufferLargeMono: FloatArray get() = _bufferLargeMono!!
        val sharedBmp2048: Bitmap get() = _sharedBmp2048!!
        val sharedCanvas2048: Canvas get() = _sharedCanvas2048!!
        val sharedBmp2048Mono: Bitmap get() = _sharedBmp2048Mono!!
        val sharedCanvas2048Mono: Canvas get() = _sharedCanvas2048Mono!!
        private val bufferSmall: FloatArray get() = _bufferSmall!!
        private val bufferSmallMono: FloatArray get() = _bufferSmallMono!!
        val sharedBmpSmall: Bitmap get() = _sharedBmpSmall!!
        val sharedCanvasSmall: Canvas get() = _sharedCanvasSmall!!
        private val bufferRec: FloatArray get() = _bufferRec!!
        private val bufferRecMono: FloatArray get() = _bufferRecMono!!
        val sharedBmpRec: Bitmap get() = _sharedBmpRec!!
        val sharedCanvasRec: Canvas get() = _sharedCanvasRec!!
        val sharedNv21Buffer: ByteArray get() = _sharedNv21Buffer!!
        val sharedBmpOdoScratch: Bitmap get() = _sharedBmpOdoScratch!!
        val sharedCanvasOdoScratch: Canvas get() = _sharedCanvasOdoScratch!!
        val redPaint: Paint get() = _redPaint!!
        val orangePaint: Paint get() = _orangePaint!!
        val grayToAlphaPaint: Paint get() = _grayToAlphaPaint!!
        val alphaToGrayPaint: Paint get() = _alphaToGrayPaint!!
        val srcPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC) }
        val sharedMonoBuffer: java.nio.ByteBuffer get() = _sharedMonoBuffer!!
        val sharedMonoBytes: ByteArray get() = _sharedMonoBytes!!
        val sharedMatrix = android.graphics.Matrix()

        // Static Histogram Parameters (Zero-Allocation)
        private val _histChannels = org.opencv.core.MatOfInt(0)
        private val _histSize = org.opencv.core.MatOfInt(256)
        private val _histRanges = org.opencv.core.MatOfFloat(0f, 256f)
        private val _histMask = org.opencv.core.Mat()
        private val _histResult = org.opencv.core.Mat()

        val histChannels: org.opencv.core.MatOfInt get() = _histChannels
        val histSize: org.opencv.core.MatOfInt get() = _histSize
        val histRanges: org.opencv.core.MatOfFloat get() = _histRanges
        val histMask: org.opencv.core.Mat get() = _histMask
        val histResult: org.opencv.core.Mat get() = _histResult

        fun initializeGlobalBuffers(context: Context) {
            if (isAvailableGlobally) return
            Log.i("PaddleLite", "Initializing Global Rigid Buffers on thread: ${Thread.currentThread().name}")

            _bufferSetA = BufferSet(4000, 3072)
            _bufferSetB = BufferSet(4000, 3072)
            _deskewBufferSet2048 = BufferSet(2048, 2048)
            _deskewBufferSet2048!!.p.clearChroma() // Init to neutral grayscale
            _deskewBufferSet2048!!.s.clearChroma()

            _bufferLarge = FloatArray(3 * 2048 * 2048); _bufferLargeMono = FloatArray(1 * 2048 * 2048)
            _sharedBmp2048 = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888); _sharedCanvas2048 = Canvas(_sharedBmp2048!!)
            _sharedBmp2048Mono = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ALPHA_8); _sharedCanvas2048Mono = Canvas(_sharedBmp2048Mono!!)

            _bufferSmall = FloatArray(3 * 512 * 128)
            _bufferSmallMono = FloatArray(1 * 512 * 128)
            _sharedBmpSmall = Bitmap.createBitmap(512, 128, Bitmap.Config.ARGB_8888); _sharedCanvasSmall = Canvas(_sharedBmpSmall!!)

            _bufferRec = FloatArray(3 * 320 * 48)
            _bufferRecMono = FloatArray(1 * 320 * 48)
            _sharedBmpRec = Bitmap.createBitmap(320, 48, Bitmap.Config.ARGB_8888); _sharedCanvasRec = Canvas(_sharedBmpRec!!)
            
            _sharedNv21Buffer = ByteArray(4000 * 3072 * 3 / 2)

            _sharedBmpOdoScratch = Bitmap.createBitmap(512, 128, Bitmap.Config.ARGB_8888); _sharedCanvasOdoScratch = Canvas(_sharedBmpOdoScratch!!)

            _redPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL; alpha = 120 }
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

            _sharedMonoBuffer = java.nio.ByteBuffer.allocateDirect(512 * 128).order(java.nio.ByteOrder.nativeOrder())
            _sharedMonoBytes = ByteArray(512 * 128)

            try {
                System.loadLibrary("paddle_lite_jni")
                val arch = if (Build.SUPPORTED_ABIS[0].contains("arm")) "armv8" else "x86_64"
                fun copy(p: String): String {
                    val f = File(context.filesDir, p.replace("/", "_"))
                    context.assets.open(p).use { it.copyTo(FileOutputStream(f)) }
                    return f.absolutePath
                }
                val detPath = copy("paddle/det_v4_4000_$arch.nb"); val detPathMono = copy("paddle/det_v4_4000_mono_$arch.nb")
                val config = MobileConfig()
                config.setThreads(4)
                config.setPowerMode(com.baidu.paddle.lite.PowerMode.LITE_POWER_HIGH)
                config.setModelFromFile(detPath); sharedDetectorLarge = PaddlePredictor.createPaddlePredictor(config); sharedDetectorLarge!!.getInput(0).resize(longArrayOf(1, 3, 2048, 2048))
                config.setModelFromFile(detPath); sharedDetectorSmall = PaddlePredictor.createPaddlePredictor(config); sharedDetectorSmall!!.getInput(0).resize(longArrayOf(1, 3, 128, 512))
                config.setModelFromFile(detPathMono); sharedDetectorLargeMono = PaddlePredictor.createPaddlePredictor(config); sharedDetectorLargeMono!!.getInput(0).resize(longArrayOf(1, 1, 2048, 2048))
                config.setModelFromFile(detPathMono); sharedDetectorSmallMono = PaddlePredictor.createPaddlePredictor(config); sharedDetectorSmallMono!!.getInput(0).resize(longArrayOf(1, 1, 128, 512))
                config.setModelFromFile(copy("paddle/rec_v3_$arch.nb")); sharedRecognizerV3 = PaddlePredictor.createPaddlePredictor(config); sharedRecognizerV3!!.getInput(0).resize(longArrayOf(1, 3, 48, 320))
                config.setModelFromFile(copy("paddle/rec_v3_mono_$arch.nb")); sharedRecognizerV3Mono = PaddlePredictor.createPaddlePredictor(config); sharedRecognizerV3Mono!!.getInput(0).resize(longArrayOf(1, 1, 48, 320))
                config.setModelFromFile(copy("paddle/rec_numeric_$arch.nb")); sharedRecognizerNumeric = PaddlePredictor.createPaddlePredictor(config); sharedRecognizerNumeric!!.getInput(0).resize(longArrayOf(1, 3, 48, 320))
                config.setModelFromFile(copy("paddle/rec_numeric_mono_$arch.nb")); sharedRecognizerNumericMono = PaddlePredictor.createPaddlePredictor(config); sharedRecognizerNumericMono!!.getInput(0).resize(longArrayOf(1, 1, 48, 320))
                
                isAvailableGlobally = true
            } catch (e: Exception) { Log.e("PaddleLite", "Failed Global Init", e) }
        }

        /**
         * Implementation of high-quality Area-Averaging resize using OpenCV.
         * Bypasses background-thread Canvas/Paint JNI locks.
         */
        fun performHighQualityResize(sourceBmp: Bitmap, target: Any) {
            val buffer = java.nio.ByteBuffer.allocateDirect(sourceBmp.byteCount)
            sourceBmp.copyPixelsToBuffer(buffer)
            buffer.rewind()
            
            // Bypass Utils.bitmapToMat, wrap ALPHA_8 directly into a 1-channel Mat
            val tempMat = Mat(sourceBmp.height, sourceBmp.width, org.opencv.core.CvType.CV_8UC1, buffer)
            try {
                val targetMat = when (target) {
                    is BufferSet.Slice -> target.mat
                    else -> throw IllegalArgumentException("Unsupported target type")
                }
                Imgproc.resize(tempMat, targetMat, Size(targetMat.cols().toDouble(), targetMat.rows().toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            } finally {
                tempMat.release()
            }
        }
    }
    
    init {
        try {
            if (isAvailableGlobally) {
                // Anchor Predictors to this instance
                if (useMono) {
                    detectorLarge = sharedDetectorLargeMono
                    detectorSmall = sharedDetectorSmallMono
                    recognizer = if (variant == "V3") sharedRecognizerV3Mono else sharedRecognizerNumericMono
                } else {
                    detectorLarge = sharedDetectorLarge
                    detectorSmall = sharedDetectorSmall
                    recognizer = if (variant == "V3") sharedRecognizerV3 else sharedRecognizerNumeric
                }
                
                isAvailable = true
                loadDictionary("paddle/en_dict.txt")
            }
        } catch (e: Throwable) {
            isAvailable = false
            initError = e.message
            Log.e("PaddleLite", "Failed to initialize engine instance", e)
        }
    }

    private fun detectArch(): String = when (Build.SUPPORTED_ABIS[0]) { 
        "arm64-v8a" -> "armv8"
        "armeabi-v7a" -> "armv7"
        else -> "x86_64" 
    }

    private fun copyAssetToInternal(assetPath: String): String {
        val file = File(context.filesDir, assetPath.replace("/", "_"))
        context.assets.open(assetPath).use { input -> 
            FileOutputStream(file).use { output -> 
                input.copyTo(output) 
            } 
        }
        return file.absolutePath
    }

    private fun loadDictionary(assetPath: String) {
        dictionary.clear()
        context.assets.open(assetPath).bufferedReader().use { reader -> 
            reader.forEachLine { dictionary.add(it) } 
        }
    }
    
    fun getDictionary(): List<String> = dictionary

    private fun createPredictor(modelPath: String): PaddlePredictor {
        val config = MobileConfig()
        config.setModelFromFile(modelPath)
        config.setThreads(4)
        config.setPowerMode(PowerMode.LITE_POWER_HIGH)
        return PaddlePredictor.createPaddlePredictor(config)
    }

    fun detect(input: Any, targetWidth: Int = 512, targetHeight: Int = 128): DetectionResult? {
        val isLarge = targetWidth > 512 || targetHeight > 128
        val predictor = if (isLarge) detectorLarge else detectorSmall
        if (predictor == null) return null

        val tensorWidth = if (isLarge) 2048 else 512
        val tensorHeight = if (isLarge) 2048 else 128
        val planeStride = tensorWidth * tensorHeight
        
        val floatData = if (isLarge) {
            if (useMono) bufferLargeMono else bufferLarge
        } else {
            if (useMono) bufferSmallMono else bufferSmall
        }
        floatData.fill(0.0f)

        val tPop0 = System.nanoTime()
        when (input) {
            is Bitmap -> {
                // Use the passed targetWidth/targetHeight. DO NOT shadow or hardcode.
                val fitW = targetWidth.coerceAtMost(tensorWidth)
                val fitH = targetHeight.coerceAtMost(tensorHeight)

                val scaled = if (input.width != fitW || input.height != fitH) {
                    Bitmap.createScaledBitmap(input, fitW, fitH, true)
                } else input

                if (useMono) {
                    val m = Mat(scaled.height, scaled.width, org.opencv.core.CvType.CV_8UC1)
                    NativeImageUtils.syncMatFromArgb(scaled, m)
                    if (m.cols() <= tensorWidth && m.rows() <= tensorHeight) {
                        NativeImageUtils.populateMonoTensor(m, floatData, tensorWidth, tensorHeight, 0.485f, 0.229f)
                    }
                    m.release()
                } else {
                    for (y in 0 until fitH) {
                        for (x in 0 until fitW) {
                            val px = scaled.getPixel(x, y)
                            floatData[0 * planeStride + y * tensorWidth + x] = ((px shr 16 and 0xFF) / 255.0f - 0.485f) / 0.229f
                            floatData[1 * planeStride + y * tensorWidth + x] = ((px shr 8 and 0xFF) / 255.0f - 0.456f) / 0.224f
                            floatData[2 * planeStride + y * tensorWidth + x] = ((px and 0xFF) / 255.0f - 0.406f) / 0.225f
                        }
                    }
                }
            }
            is java.nio.ByteBuffer -> {
                // Direct NV21/CV_8UC1 to floatData parsing (Assumes input fits in tensor corner)
                input.rewind()
                val fitW = targetWidth.coerceAtMost(tensorWidth)
                val fitH = targetHeight.coerceAtMost(tensorHeight)
                if (useMono) {
                    val m = Mat(fitH, fitW, org.opencv.core.CvType.CV_8UC1, input)
                    if (m.cols() <= tensorWidth && m.rows() <= tensorHeight) {
                        NativeImageUtils.populateMonoTensor(m, floatData, tensorWidth, tensorHeight, 0.485f, 0.229f)
                    }
                } else {
                    for (y in 0 until fitH) {
                        for (x in 0 until fitW) {
                            floatData[y * tensorWidth + x] = ((input.get().toInt() and 0xFF) / 255.0f - 0.485f) / 0.229f
                        }
                    }
                }
            }
            else -> {
                Log.e("PaddleDetect", "Unsupported input type for detection")
                return null
            }
        }
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
        } catch (t: Throwable) { Log.e("PaddleDetect", "Detection failed", t); return null }
    }

    fun detectMono(input: Any, targetW: Int? = null, targetH: Int? = null): DetectionResult? {
        val tPop0 = System.nanoTime()
        val w: Int; val h: Int; val srcMat: Mat
        when (input) {
            is BufferSet.Slice -> { w = input.width; h = input.height; srcMat = input.mat }
            is Mat -> { w = targetW ?: input.cols(); h = targetH ?: input.rows(); srcMat = input }
            else -> throw IllegalArgumentException("Unsupported input type for detectMono")
        }

        val isLarge = w > 512 || h > 128
        val predictor = if (isLarge) detectorLarge else detectorSmall
        if (predictor == null) return null

        val tensorWidth = if (isLarge) 2048 else 512
        val tensorHeight = if (isLarge) 2048 else 128
        val floatData = if (isLarge) bufferLargeMono else bufferSmallMono
        
        floatData.fill(0.0f)
        
        val mean = 0.485f; val std = 0.229f
        if (srcMat.cols() <= tensorWidth && srcMat.rows() <= tensorHeight) {
            NativeImageUtils.populateMonoTensor(srcMat, floatData, tensorWidth, tensorHeight, mean, std)
        } else {
            Log.e("PaddleDetect", "Source Mat (%dx%d) exceeds Tensor capacity (%dx%d)".format(srcMat.cols(), srcMat.rows(), tensorWidth, tensorHeight))
            return null
        }
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
            Log.e("PaddleDetect", "Mono detection failed", t)
            return null 
        }
    }

    suspend fun runConstrainedStatic(input: Any, targetHeight: Int, dictionary: List<String>, isV3: Boolean): RecStageResult = withContext(Dispatchers.IO) {
        if (recognizer == null) return@withContext RecStageResult("", 0, 0f, null)
        if (android.os.Debug.getNativeHeapAllocatedSize() > 2.4 * 1024 * 1024 * 1024) return@withContext RecStageResult("(Skipped: Memory)", 0, 0f, null)
        runRecognitionStageStatic(input, targetHeight, dictionary, recognizer!!)
    }

    private fun runRecognitionStageStatic(input: Any, ignoredHeight: Int, dictionary: List<String>, predictor: PaddlePredictor): RecStageResult {
        val tStart = System.currentTimeMillis()
        
        // Phase 115: Size-Agnostic Pathway Split
        val w: Int; val h: Int
        val floatData: FloatArray = if (useMono) bufferRecMono else bufferRec
        floatData.fill(0.0f)

        val tPop0 = System.nanoTime()
        when (input) {
            is Bitmap -> {
                w = input.width; h = input.height; val area = w * h
                Log.d("OCR_DEBUG", "START RECOGNITION (Bitmap): engine=$name, dims=${w}x${h}")
                if (useMono) {
                    val m = Mat(h, w, org.opencv.core.CvType.CV_8UC1)
                    NativeImageUtils.syncMatFromArgb(input, m)
                    NativeImageUtils.populateMonoTensor(m, floatData, w, h, 0.5f, 0.5f)
                    m.release()
                } else {
                    val mean = 0.5f; val std = 0.5f
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            val px = input.getPixel(x, y)
                            floatData[0 * area + y * w + x] = ((px shr 16 and 0xFF) / 255.0f - mean) / std
                            floatData[1 * area + y * w + x] = ((px shr 8 and 0xFF) / 255.0f - mean) / std
                            floatData[2 * area + y * w + x] = ((px and 0xFF) / 255.0f - mean) / std
                        }
                    }
                }
            }
            else -> return RecStageResult("(Unsupported Input)", 0, 0f, null)
        }
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0
        
        val tJniIn0 = System.nanoTime()
        predictor.getInput(0).setData(floatData)
        val tJniIn = (System.nanoTime() - tJniIn0) / 1_000_000.0

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

        val seqLen = dims[1].toInt(); val dictSize = dims[2].toInt(); val result = StringBuilder()
        var lastIdx = -1; var totalConf = 0f; var charCount = 0; var lastConf = 1.0f
        
        Log.d("OCR_DEBUG", "START: seqLen=$seqLen, dictSize=$dictSize")
        for (i in 0 until seqLen) {
            var maxIdx = 0; var maxVal = -1f; val searchLimit = 11.coerceAtMost(dictSize)
            for (j in 0 until searchLimit) { val v = data[i * dictSize + j]; if (v > maxVal) { maxVal = v; maxIdx = j } }
            if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) {
                if (result.length < 4 || maxVal >= (0.60f * lastConf)) {
                    result.append(dictionary[maxIdx - 1]); totalConf += maxVal; charCount++; lastConf = maxVal
                } else break
            }
            lastIdx = maxIdx
        }
        val finalStr = result.toString(); val finalConf = if (charCount > 0) totalConf / charCount else 0f
        Log.d("OCR_DEBUG", "END: text='$finalStr', avgConf=$finalConf")
        return RecStageResult(finalStr, System.currentTimeMillis() - tStart, finalConf, null)
    }

    suspend fun runConstrainedStaticMono(input: Any, dictionary: List<String>): RecStageResult = withContext(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        if (recognizer == null || !useMono) return@withContext RecStageResult("(Engine Error)", 0, 0f, null)

        val w: Int
        val h: Int
        val srcMat: Mat
        when (input) {
            is BufferSet.Slice -> {
                w = input.width
                h = input.height
                srcMat = input.mat
            }
            is Mat -> {
                w = input.cols()
                h = input.rows()
                srcMat = input
            }
            else -> throw IllegalArgumentException("Unsupported input type for runConstrainedStaticMono")
        }

        val area = w * h
        if (area > 320 * 48) {
             Log.e("PaddleDetect", "Bridge dimensions (${w}x${h}) exceed pre-allocated mono rec tensor capacity.")
             return@withContext RecStageResult("(Size Error)", 0, 0f, null)
        }

        val tPop0 = System.nanoTime()
        bufferRecMono.fill(0.0f)
        val mean = 0.5f; val std = 0.5f
        NativeImageUtils.populateMonoTensor(srcMat, bufferRecMono, 320, 48, mean, std)
        val tPop = (System.nanoTime() - tPop0) / 1_000_000.0

        try {
            val tJniIn0 = System.nanoTime()
            recognizer!!.getInput(0).setData(bufferRecMono)
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
                var maxIdx = 0; var maxVal = -1f; val searchLimit = 11.coerceAtMost(dictSize)
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

        val res = runRecognitionStageStatic(input, 48, dictionary, predictor)
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
