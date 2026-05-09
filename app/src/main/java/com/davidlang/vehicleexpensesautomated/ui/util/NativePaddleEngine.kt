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
    
    data class DetectionResult(val heatmap: FloatArray, val width: Int, val height: Int, val scale: Float)
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
        private var _sharedBmpFull: Bitmap? = null
        private var _sharedCanvasFull: Canvas? = null
        private var _sharedBmpFullMono: Bitmap? = null
        private var _sharedCanvasFullMono: Canvas? = null
        private var _sharedBmpScratch: Bitmap? = null
        private var _sharedCanvasScratch: Canvas? = null
        private var _sharedBmpScratchMono: Bitmap? = null
        private var _sharedCanvasScratchMono: Canvas? = null
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
        private var _sharedBmpSmallMono: Bitmap? = null
        private var _sharedCanvasSmallMono: Canvas? = null
        private var _bufferRec: FloatArray? = null
        private var _bufferRecMono: FloatArray? = null
        private var _sharedBmpRec: Bitmap? = null
        private var _sharedCanvasRec: Canvas? = null
        private var _sharedBmpRecMono: Bitmap? = null
        private var _sharedCanvasRecMono: Canvas? = null
        private var _sharedNv21Buffer: ByteArray? = null
        private var _sharedBmpOdoScratch: Bitmap? = null
        private var _sharedCanvasOdoScratch: Canvas? = null
        private var _sharedBmpOdoScratchMono: Bitmap? = null
        private var _sharedCanvasOdoScratchMono: Canvas? = null
        private var _sharedReportBitmap: Bitmap? = null
        private var _sharedReportCanvas: Canvas? = null
        private var _redPaint: Paint? = null
        private var _orangePaint: Paint? = null
        private var _grayToAlphaPaint: Paint? = null
        private var _alphaToGrayPaint: Paint? = null
        private var _sharedMonoBuffer: java.nio.ByteBuffer? = null
        private var _sharedMonoBytes: ByteArray? = null

        // Public Non-Null Accessors (API Stability)
        val sharedBmpFull: Bitmap get() = _sharedBmpFull!!
        val sharedCanvasFull: Canvas get() = _sharedCanvasFull!!
        val sharedBmpFullMono: Bitmap get() = _sharedBmpFullMono!!
        val sharedCanvasFullMono: Canvas get() = _sharedCanvasFullMono!!
        val sharedBmpScratch: Bitmap get() = _sharedBmpScratch!!
        val sharedCanvasScratch: Canvas get() = _sharedCanvasScratch!!
        val sharedBmpScratchMono: Bitmap get() = _sharedBmpScratchMono!!
        val sharedCanvasScratchMono: Canvas get() = _sharedCanvasScratchMono!!
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
        val sharedBmpSmallMono: Bitmap get() = _sharedBmpSmallMono!!
        val sharedCanvasSmallMono: Canvas get() = _sharedCanvasSmallMono!!
        private val bufferRec: FloatArray get() = _bufferRec!!
        private val bufferRecMono: FloatArray get() = _bufferRecMono!!
        val sharedBmpRec: Bitmap get() = _sharedBmpRec!!
        val sharedCanvasRec: Canvas get() = _sharedCanvasRec!!
        val sharedBmpRecMono: Bitmap get() = _sharedBmpRecMono!!
        val sharedCanvasRecMono: Canvas get() = _sharedCanvasRecMono!!
        val sharedNv21Buffer: ByteArray get() = _sharedNv21Buffer!!
        val sharedBmpOdoScratch: Bitmap get() = _sharedBmpOdoScratch!!
        val sharedCanvasOdoScratch: Canvas get() = _sharedCanvasOdoScratch!!
        val sharedBmpOdoScratchMono: Bitmap get() = _sharedBmpOdoScratchMono!!
        val sharedCanvasOdoScratchMono: Canvas get() = _sharedCanvasOdoScratchMono!!
        val sharedReportBitmap: Bitmap get() = _sharedReportBitmap!!
        val sharedReportCanvas: Canvas get() = _sharedReportCanvas!!
        val redPaint: Paint get() = _redPaint!!
        val orangePaint: Paint get() = _orangePaint!!
        val grayToAlphaPaint: Paint get() = _grayToAlphaPaint!!
        val alphaToGrayPaint: Paint get() = _alphaToGrayPaint!!
        val srcPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC) }
        val sharedMonoBuffer: java.nio.ByteBuffer get() = _sharedMonoBuffer!!
        val sharedMonoBytes: ByteArray get() = _sharedMonoBytes!!
        val sharedMatrix = android.graphics.Matrix()

        fun initializeGlobalBuffers(context: Context) {
            if (isAvailableGlobally) return
            Log.i("PaddleLite", "Initializing Global Rigid Buffers on thread: ${Thread.currentThread().name}")

            _sharedBmpFull = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888); _sharedCanvasFull = Canvas(_sharedBmpFull!!)
            _sharedBmpFullMono = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ALPHA_8); _sharedCanvasFullMono = Canvas(_sharedBmpFullMono!!)
            _sharedBmpScratch = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888); _sharedCanvasScratch = Canvas(_sharedBmpScratch!!)
            _sharedBmpScratchMono = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ALPHA_8); _sharedCanvasScratchMono = Canvas(_sharedBmpScratchMono!!)

            _bufferLarge = FloatArray(3 * 2048 * 2048); _bufferLargeMono = FloatArray(1 * 2048 * 2048)
            _sharedBmp2048 = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888); _sharedCanvas2048 = Canvas(_sharedBmp2048!!)
            _sharedBmp2048Mono = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ALPHA_8); _sharedCanvas2048Mono = Canvas(_sharedBmp2048Mono!!)

            _bufferSmall = FloatArray(3 * 512 * 128)
            _bufferSmallMono = FloatArray(1 * 512 * 128)
            _sharedBmpSmall = Bitmap.createBitmap(512, 128, Bitmap.Config.ARGB_8888); _sharedCanvasSmall = Canvas(_sharedBmpSmall!!)
            
            // Anchor Mono Bitmaps
            _sharedBmpSmallMono = MemoryBridge.pool512x128!!.getBitmap()
            _sharedCanvasSmallMono = Canvas(_sharedBmpSmallMono!!)

            _bufferRec = FloatArray(3 * 320 * 48)
            _bufferRecMono = FloatArray(1 * 320 * 48)
            _sharedBmpRec = Bitmap.createBitmap(320, 48, Bitmap.Config.ARGB_8888); _sharedCanvasRec = Canvas(_sharedBmpRec!!)
            
            // Anchor Mono Bitmaps
            _sharedBmpRecMono = MemoryBridge.pool320x48!!.getBitmap()
            _sharedCanvasRecMono = Canvas(_sharedBmpRecMono!!)
            
            _sharedNv21Buffer = ByteArray(4000 * 3000 * 3 / 2)

            _sharedBmpOdoScratch = Bitmap.createBitmap(512, 128, Bitmap.Config.ARGB_8888); _sharedCanvasOdoScratch = Canvas(_sharedBmpOdoScratch!!)
            _sharedBmpOdoScratchMono = Bitmap.createBitmap(512, 128, Bitmap.Config.ALPHA_8); _sharedCanvasOdoScratchMono = Canvas(_sharedBmpOdoScratchMono!!)

            _sharedReportBitmap = Bitmap.createBitmap(320, 48, Bitmap.Config.ARGB_8888); _sharedReportCanvas = Canvas(_sharedReportBitmap!!)
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
        fun performHighQualityResize(sourceBmp: Bitmap, targetBridge: MemoryBridge) {
            val buffer = java.nio.ByteBuffer.allocateDirect(sourceBmp.byteCount)
            sourceBmp.copyPixelsToBuffer(buffer)
            buffer.rewind()
            
            // Bypass Utils.bitmapToMat, wrap ALPHA_8 directly into a 1-channel Mat
            val tempMat = Mat(sourceBmp.height, sourceBmp.width, org.opencv.core.CvType.CV_8UC1, buffer)
            try {
                Imgproc.resize(tempMat, targetBridge.getMat(), Size(targetBridge.width.toDouble(), targetBridge.height.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                targetBridge.syncToBitmap() // <--- Forces Java Bitmap to reflect Native Mat changes
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

    fun detect(bitmap: Bitmap, targetWidth: Int = 512, targetHeight: Int = 128): DetectionResult? {
        val predictor = if (targetWidth >= 2048) detectorLarge else detectorSmall
        if (predictor == null) return null

        val area = targetWidth * targetHeight
        val floatData: FloatArray
        val targetBmp: Bitmap
        val targetCanvas: Canvas
        
        if (targetWidth >= 2048) {
            floatData = if (useMono) bufferLargeMono else bufferLarge
            targetBmp = if (useMono) sharedBmp2048Mono else sharedBmp2048
            targetCanvas = if (useMono) sharedCanvas2048Mono else sharedCanvas2048
        } else {
            floatData = if (useMono) bufferSmallMono else bufferSmall
            targetBmp = if (useMono) sharedBmpSmallMono else sharedBmpSmall
            targetCanvas = if (useMono) sharedCanvasSmallMono else sharedCanvasSmall
        }
        floatData.fill(0.0f)

        if (useMono && targetWidth <= 512) {
            // High-Quality OpenCV Area Resize (Requested)
            val bridge = if (targetWidth == 512) MemoryBridge.pool512x128!! else MemoryBridge.pool320x48!!
            performHighQualityResize(bitmap, bridge)
            
            // Post-resize data population for Mono tensor (1 channel)
            val mean = 0.485f // Paddle standard normalization (can be adjusted to 0.5f if needed for Mono)
            val std = 0.229f
            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val px = targetBmp.getPixel(x, y)
                    floatData[y * targetWidth + x] = (((px ushr 24) and 0xFF) / 255.0f - mean) / std
                }
            }
        } else {
            val scaleW = targetWidth.toFloat() / bitmap.width
            val scaleH = targetHeight.toFloat() / bitmap.height
            val scale = min(scaleW, scaleH)
            synchronized(targetBmp) {
                targetCanvas.drawColor(if (useMono) Color.TRANSPARENT else Color.BLACK, if (useMono) android.graphics.PorterDuff.Mode.CLEAR else android.graphics.PorterDuff.Mode.SRC)
                sharedMatrix.reset()
                sharedMatrix.postScale(scale, scale)
                targetCanvas.drawBitmap(bitmap, sharedMatrix, if (useMono) srcPaint else null)

                val mean = floatArrayOf(0.485f, 0.456f, 0.406f); val std = floatArrayOf(0.229f, 0.224f, 0.225f)
                if (useMono) {
                    for (y in 0 until targetHeight) {
                        for (x in 0 until targetWidth) {
                            val px = targetBmp.getPixel(x, y)
                            floatData[y * targetWidth + x] = (((px ushr 24) and 0xFF) / 255.0f - mean[0]) / std[0]
                        }
                    }
                } else {
                    for (y in 0 until targetHeight) {
                        for (x in 0 until targetWidth) {
                            val px = targetBmp.getPixel(x, y)
                            floatData[0 * area + y * targetWidth + x] = ((px shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
                            floatData[1 * area + y * targetWidth + x] = ((px shr 8 and 0xFF) / 255.0f - mean[1]) / std[1]
                            floatData[2 * area + y * targetWidth + x] = ((px and 0xFF) / 255.0f - mean[2]) / std[2]
                        }
                    }
                }
            }
        }

        try {
            val inputTensor = predictor.getInput(0); inputTensor.setData(floatData); predictor.run()
            val outputTensor = predictor.getOutput(0); val dims = outputTensor.shape()
            return DetectionResult(outputTensor.floatData, dims[3].toInt(), dims[2].toInt(), targetWidth.toFloat() / bitmap.width)
        } catch (t: Throwable) { Log.e("PaddleDetect", "Detection failed", t); return null }
    }

    suspend fun runConstrainedStatic(bitmap: Bitmap, targetHeight: Int, dictionary: List<String>, isV3: Boolean): RecStageResult = withContext(Dispatchers.IO) {
        if (recognizer == null) return@withContext RecStageResult("", 0, 0f, null)
        if (android.os.Debug.getNativeHeapAllocatedSize() > 2.4 * 1024 * 1024 * 1024) return@withContext RecStageResult("(Skipped: Memory)", 0, 0f, null)
        runRecognitionStageStatic(bitmap, targetHeight, dictionary, recognizer!!)
    }

    private fun runRecognitionStageStatic(bitmap: Bitmap, ignoredHeight: Int, dictionary: List<String>, predictor: PaddlePredictor): RecStageResult {
        val tStart = System.currentTimeMillis()
        val targetHeight = 48; val targetWidth = 320; val area = targetHeight * targetWidth
        Log.d("OCR_DEBUG", "START RECOGNITION: engine=$name, dims=${bitmap.width}x${bitmap.height}")
        val floatData: FloatArray = if (useMono) bufferRecMono else bufferRec
        floatData.fill(0.0f)

        if (useMono) {
            val targetBmp = sharedBmpRecMono
            performHighQualityResize(bitmap, MemoryBridge.pool320x48!!)
            
            // Post-resize data population for Mono tensor (1 channel)
            val mean = 0.5f 
            val std = 0.5f
            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val px = targetBmp.getPixel(x, y)
                    floatData[y * targetWidth + x] = (((px ushr 24) and 0xFF) / 255.0f - mean) / std
                }
            }
        } else {
            val targetBmp = sharedBmpRec; val targetCanvas = sharedCanvasRec
            val scale = 40f / bitmap.height.toFloat(); val padding = 4
            synchronized(targetBmp) {
                targetCanvas.drawColor(Color.BLACK); sharedMatrix.reset(); sharedMatrix.postScale(scale, scale); sharedMatrix.postTranslate(padding.toFloat(), padding.toFloat())
                targetCanvas.drawBitmap(bitmap, sharedMatrix, null)
                val mean = 0.5f; val std = 0.5f
                for (y in 0 until targetHeight) {
                    for (x in 0 until targetWidth) {
                        val px = targetBmp.getPixel(x, y)
                        floatData[0 * area + y * targetWidth + x] = ((px shr 16 and 0xFF) / 255.0f - mean) / std
                        floatData[1 * area + y * targetWidth + x] = ((px shr 8 and 0xFF) / 255.0f - mean) / std
                        floatData[2 * area + y * targetWidth + x] = ((px and 0xFF) / 255.0f - mean) / std
                    }
                }
            }
        }
        
        predictor.getInput(0).setData(floatData); predictor.run()
        val outputTensor = predictor.getOutput(0); val data = outputTensor.floatData; val dims = outputTensor.shape()
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

    data class RecStageResult(val text: String, val timeMs: Long, val confidence: Float, val ocrInputB64: String? = null)
    override suspend fun recognize(bitmap: Bitmap): OcrResult = recognize(bitmap, false)
    suspend fun recognize(bitmap: Bitmap, isRecursive: Boolean): OcrResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Not Available", imageWidth = bitmap.width, imageHeight = bitmap.height)
        val t0 = System.currentTimeMillis()
        val predictor = recognizer ?: return@withContext OcrResult(engineName = name, debugText = "Predictor null")
        val res = runRecognitionStageStatic(bitmap, 48, dictionary, predictor)
        OcrResult(engineName = name, executionTimeMs = System.currentTimeMillis() - t0, debugText = res.text, textBlocks = listOf(TextBlock(res.text, Rect(0,0,bitmap.width, bitmap.height))), imageWidth = bitmap.width, imageHeight = bitmap.height)
    }

    suspend fun runDetectionOnly(bitmap: Bitmap, targetWidth: Int = 1280, targetHeight: Int = 1280): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis(); val det = detect(bitmap, targetWidth, targetHeight) ?: return@withContext OcrResult(engineName = name, debugText = "Detection failed")
        val blocks = OdometerOcrUtils.processPaddleHeatmap(det.heatmap, det.width, det.height, det.scale, bitmap, "Native")
        OcrResult(engineName = name, executionTimeMs = System.currentTimeMillis() - t0, debugText = "(Detection Only)", textBlocks = blocks, rawHeatmap = det.heatmap, heatmapWidth = det.width, heatmapHeight = det.height, scaleFactor = det.scale, imageWidth = bitmap.width, imageHeight = bitmap.height)
    }
}
