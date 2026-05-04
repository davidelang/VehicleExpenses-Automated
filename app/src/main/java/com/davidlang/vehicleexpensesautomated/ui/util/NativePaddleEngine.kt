package com.davidlang.vehicleexpensesautomated.ui.util
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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

class NativePaddleEngine(private val context: Context, private val variant: String = "V3", val useMono: Boolean = false) : OcrEngine {
    override val name = "Paddle $variant Greedy" + if (useMono) " Mono" else ""
    fun isV3() = variant == "V3"
    
    data class DetectionResult(val heatmap: FloatArray, val width: Int, val height: Int, val scale: Float)
    private val dictionary = mutableListOf<String>()
    private var initError: String? = null
    var isAvailable = false
        private set

    companion object {
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

        // Phase 63: Rigid 6-Buffer Static Pool (Zero-Allocation)
        // 1. Discovery Forensic (2048x2048)
        private val bufferLarge by lazy { FloatArray(3 * 2048 * 2048) }
        private val bufferLargeMono by lazy { FloatArray(1 * 2048 * 2048) }
        val sharedBmp2048 by lazy { Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888) }
        val sharedCanvas2048 by lazy { Canvas(sharedBmp2048) }
        val sharedBmp2048Mono by lazy { Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888) }
        val sharedCanvas2048Mono by lazy { Canvas(sharedBmp2048Mono) }

        // 2. Discovery Standard (320x128)
        private val bufferSmall by lazy { FloatArray(3 * 320 * 128) }
        private val bufferSmallMono by lazy { FloatArray(1 * 320 * 128) }
        val sharedBmpSmall by lazy { Bitmap.createBitmap(320, 128, Bitmap.Config.ARGB_8888) }
        val sharedCanvasSmall by lazy { Canvas(sharedBmpSmall) }
        val sharedBmpSmallMono by lazy { Bitmap.createBitmap(320, 128, Bitmap.Config.ARGB_8888) }
        val sharedCanvasSmallMono by lazy { Canvas(sharedBmpSmallMono) }

        // 3. Recognition (320x48)
        private val bufferRec by lazy { FloatArray(3 * 320 * 48) }
        private val bufferRecMono by lazy { FloatArray(1 * 320 * 48) }
        val sharedBmpRec by lazy { Bitmap.createBitmap(320, 48, Bitmap.Config.ARGB_8888) }
        val sharedCanvasRec by lazy { Canvas(sharedBmpRec) }
        val sharedBmpRecMono by lazy { Bitmap.createBitmap(320, 48, Bitmap.Config.ARGB_8888) }
        val sharedCanvasRecMono by lazy { Canvas(sharedBmpRecMono) }
        val sharedNv21Buffer by lazy { ByteArray(320 * 48 * 3 / 2) }

        // Shared Matrix for Zero-Allocation Scaling
        val sharedMatrix = android.graphics.Matrix()

        // Phase 63: Permanent Shared Reporting Buffers
        val sharedReportBitmap: Bitmap by lazy { Bitmap.createBitmap(320, 48, Bitmap.Config.ARGB_8888) }
        val sharedReportCanvas: Canvas by lazy { Canvas(sharedReportBitmap) }
        val redPaint: Paint by lazy { Paint().apply { color = Color.RED; style = Paint.Style.FILL; alpha = 120 } }
        val orangePaint: Paint by lazy { Paint().apply { color = Color.rgb(255, 165, 0); style = Paint.Style.STROKE; strokeWidth = 2f } }
    }
    
    init {
        try {
            val arch = detectArch()
            if (!isNativeLibLoaded) { 
                System.loadLibrary("paddle_lite_jni")
                isNativeLibLoaded = true 
            }
            
            if (useMono) {
                val modelPath = copyAssetToInternal("paddle/det_v4_4000_mono_$arch.nb")
                if (sharedDetectorLargeMono == null) {
                    sharedDetectorLargeMono = createPredictor(modelPath)
                    sharedDetectorLargeMono!!.getInput(0).resize(longArrayOf(1, 1, 2048, 2048))
                }
                if (sharedDetectorSmallMono == null) {
                    sharedDetectorSmallMono = createPredictor(modelPath)
                    sharedDetectorSmallMono!!.getInput(0).resize(longArrayOf(1, 1, 128, 320))
                }
                
                if (variant == "V3" && sharedRecognizerV3Mono == null) {
                    sharedRecognizerV3Mono = createPredictor(copyAssetToInternal("paddle/rec_v3_mono_$arch.nb"))
                    sharedRecognizerV3Mono!!.getInput(0).resize(longArrayOf(1, 1, 48, 320))
                }
                if (variant == "V2" && sharedRecognizerNumericMono == null) {
                    sharedRecognizerNumericMono = createPredictor(copyAssetToInternal("paddle/rec_numeric_mono_$arch.nb"))
                    sharedRecognizerNumericMono!!.getInput(0).resize(longArrayOf(1, 1, 48, 320))
                }
            } else {
                val modelPath = copyAssetToInternal("paddle/det_v4_4000_$arch.nb")
                if (sharedDetectorLarge == null) {
                    sharedDetectorLarge = createPredictor(modelPath)
                    sharedDetectorLarge!!.getInput(0).resize(longArrayOf(1, 3, 2048, 2048))
                }
                if (sharedDetectorSmall == null) {
                    sharedDetectorSmall = createPredictor(modelPath)
                    sharedDetectorSmall!!.getInput(0).resize(longArrayOf(1, 3, 128, 320))
                }
                
                if (variant == "V3" && sharedRecognizerV3 == null) {
                    sharedRecognizerV3 = createPredictor(copyAssetToInternal("paddle/rec_v3_$arch.nb"))
                    sharedRecognizerV3!!.getInput(0).resize(longArrayOf(1, 3, 48, 320))
                }
                if (variant == "V2" && sharedRecognizerNumeric == null) {
                    sharedRecognizerNumeric = createPredictor(copyAssetToInternal("paddle/rec_numeric_$arch.nb"))
                    sharedRecognizerNumeric!!.getInput(0).resize(longArrayOf(1, 3, 48, 320))
                }
            }
            loadDictionary("paddle/en_dict.txt")
            isAvailable = true
        } catch (e: Throwable) {
            isAvailable = false
            initError = e.message
            Log.e("PaddleLite", "Failed to initialize predictors", e)
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

    fun detect(bitmap: Bitmap, targetWidth: Int = 320, targetHeight: Int = 128): DetectionResult? {
        val predictor = if (targetWidth >= 2048) {
            if (useMono) sharedDetectorLargeMono else sharedDetectorLarge
        } else {
            if (useMono) sharedDetectorSmallMono else sharedDetectorSmall
        }
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

        val scaleW = targetWidth.toFloat() / bitmap.width
        val scaleH = targetHeight.toFloat() / bitmap.height
        val scale = min(scaleW, scaleH)
        val sw = (bitmap.width * scale).toInt()
        val sh = (bitmap.height * scale).toInt()

        // Zero-Allocation Scaling via Canvas
        synchronized(targetBmp) {
            targetCanvas.drawColor(Color.BLACK)
            sharedMatrix.reset()
            sharedMatrix.postScale(scale, scale)
            targetCanvas.drawBitmap(bitmap, sharedMatrix, null)

            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)

            if (useMono) {
                for (y in 0 until targetHeight) {
                    for (x in 0 until targetWidth) {
                        val px = targetBmp.getPixel(x, y)
                        floatData[y * targetWidth + x] = ((px shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
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

        try {
            val inputTensor = predictor.getInput(0)
            inputTensor.setData(floatData)
            predictor.run()
            val outputTensor = predictor.getOutput(0)
            val dims = outputTensor.shape()
            return DetectionResult(outputTensor.floatData, dims[3].toInt(), dims[2].toInt(), scale)
        } catch (t: Throwable) {
            Log.e("PaddleDetect", "Detection failed", t)
            return null
        }
    }

    suspend fun runConstrainedStatic(bitmap: Bitmap, targetHeight: Int, dictionary: List<String>, isV3: Boolean): RecStageResult = withContext(Dispatchers.IO) {
        val predictor = if (isV3) {
            if (useMono) sharedRecognizerV3Mono else sharedRecognizerV3
        } else {
            if (useMono) sharedRecognizerNumericMono else sharedRecognizerNumeric
        }
        if (predictor == null) return@withContext RecStageResult("", 0, 0f, null)
        
        if (android.os.Debug.getNativeHeapAllocatedSize() > 2.4 * 1024 * 1024 * 1024) {
            return@withContext RecStageResult("(Skipped: Memory)", 0, 0f, null)
        }
        runRecognitionStageStatic(bitmap, targetHeight, dictionary, predictor)
    }

    private fun runRecognitionStageStatic(bitmap: Bitmap, ignoredHeight: Int, dictionary: List<String>, predictor: PaddlePredictor): RecStageResult {
        val tStart = System.currentTimeMillis()
        val targetHeight = 48
        val targetWidth = 320
        val area = targetHeight * targetWidth
        
        val floatData: FloatArray = if (useMono) bufferRecMono else bufferRec
        floatData.fill(0.0f)

        // Phase 63: 312x40 Padded Center-Crop
        val safeW = 312
        val safeH = 40
        val padding = 4

        val scale = safeH.toFloat() / bitmap.height.toFloat()
        
        // Zero-Allocation Scaling via Shared Padded Buffer
        val targetBmp = if (useMono) sharedBmpRecMono else sharedBmpRec
        val targetCanvas = if (useMono) sharedCanvasRecMono else sharedCanvasRec

        synchronized(targetBmp) {
            targetCanvas.drawColor(Color.BLACK)
            sharedMatrix.reset()
            sharedMatrix.postScale(scale, scale)
            sharedMatrix.postTranslate(padding.toFloat(), padding.toFloat())
            targetCanvas.drawBitmap(bitmap, sharedMatrix, null)

            val mean = 0.5f
            val std = 0.5f
            
            if (useMono) {
                for (y in 0 until targetHeight) {
                    for (x in 0 until targetWidth) {
                        val px = targetBmp.getPixel(x, y)
                        floatData[y * targetWidth + x] = ((px shr 16 and 0xFF) / 255.0f - mean) / std
                    }
                }
            } else {
                for (y in 0 until targetHeight) {
                    for (x in 0 until targetWidth) {
                        val px = targetBmp.getPixel(x, y)
                        floatData[0 * area + y * targetWidth + x] = ((px shr 16 and 0xFF) / 255.0f - mean) / std
                        floatData[1 * area + y * targetWidth + x] = ((px shr 8 and 0xFF) / 255.0f - mean) / std
                        floatData[2 * area + y * targetWidth + x] = ((px and 0xFF) / 255.0f - mean) / std
                    }
                }
            }
            
            // Phase 63: Capture Exact OCR Input for Diagnostics (Reconstruct from floatData)
            val snapshot = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val r: Int; val g: Int; val b: Int
                    if (useMono) {
                        val v = ((floatData[y * targetWidth + x] * std + mean) * 255f).toInt().coerceIn(0, 255)
                        r = v; g = v; b = v
                    } else {
                        r = ((floatData[0 * area + y * targetWidth + x] * std + mean) * 255f).toInt().coerceIn(0, 255)
                        g = ((floatData[1 * area + y * targetWidth + x] * std + mean) * 255f).toInt().coerceIn(0, 255)
                        b = ((floatData[2 * area + y * targetWidth + x] * std + mean) * 255f).toInt().coerceIn(0, 255)
                    }
                    snapshot.setPixel(x, y, Color.rgb(r, g, b))
                }
            }
            val ocrInputB64 = OcrUtils.bitmapToBase64(snapshot, 60)
            snapshot.recycle()
            
            predictor.getInput(0).setData(floatData)
            predictor.run()
            
            val outputTensor = predictor.getOutput(0)
            val dims = outputTensor.shape()
            val seqLen = dims[1].toInt()
            val dictSize = dims[2].toInt()
            val data = outputTensor.floatData
            val result = StringBuilder()
            var lastIdx = -1
            var totalConf = 0f
            var charCount = 0
            var lastConf = 1.0f
            
            for (i in 0 until seqLen) {
                var maxIdx = 0
                var maxVal = -1f 
                val searchLimit = 11.coerceAtMost(dictSize)
                for (j in 0 until searchLimit) { 
                    val v = data[i * dictSize + j]
                    if (v > maxVal) { 
                        maxVal = v
                        maxIdx = j 
                    } 
                }
                
                val char = if (maxIdx > 0 && maxIdx <= dictionary.size) dictionary[maxIdx - 1] else "BLANK/UNK"

                // Phase 69: Extreme floor (0.00) to rescue faint leading digits
                // The first 4 digits are immune to the relative drop rule.
                if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) {
                    val isImmune = result.length < 4
                    if (isImmune || maxVal >= (0.60f * lastConf)) {
                        val appendedChar = dictionary[maxIdx - 1]
                        result.append(appendedChar)
                        totalConf += maxVal
                        charCount++
                        lastConf = maxVal
                    } else {
                        break // Stop search
                    }
                } else if (maxIdx > 0 && maxIdx == lastIdx) {
                }
                lastIdx = maxIdx
            }
            val finalStr = result.toString()
            val finalConf = if (charCount > 0) totalConf / charCount else 0f
            return RecStageResult(finalStr, System.currentTimeMillis() - tStart, finalConf, ocrInputB64)
        }
    }

    data class RecStageResult(val text: String, val timeMs: Long, val confidence: Float, val ocrInputB64: String? = null)

    override suspend fun recognize(bitmap: Bitmap): OcrResult = recognize(bitmap, false)
    
    suspend fun recognize(bitmap: Bitmap, isRecursive: Boolean): OcrResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Not Available: $initError", imageWidth = bitmap.width, imageHeight = bitmap.height)
        val t0 = System.currentTimeMillis()
        val predictor = if (variant == "V3") sharedRecognizerV3 else sharedRecognizerNumeric
        if (predictor == null) return@withContext OcrResult(engineName = name, debugText = "Predictor null", imageWidth = bitmap.width, imageHeight = bitmap.height)
        
        val finalResult = runRecognitionStageStatic(bitmap, 48, dictionary, predictor)
        OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = finalResult.text,
            textBlocks = listOf(TextBlock(finalResult.text, Rect(0,0,bitmap.width, bitmap.height))),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            metadata = mapOf("ocrInput" to (finalResult.ocrInputB64 ?: ""))
        )
    }

    suspend fun runDetectionOnly(bitmap: Bitmap, targetWidth: Int = 1280, targetHeight: Int = 1280): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val det = detect(bitmap, targetWidth, targetHeight) ?: return@withContext OcrResult(engineName = name, debugText = "Detection failed", imageWidth = bitmap.width, imageHeight = bitmap.height)
        
        val blocks = OdometerOcrUtils.processPaddleHeatmap(det.heatmap, det.width, det.height, det.scale, bitmap, "Native")
        
        OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            debugText = "(Detection Only)",
            textBlocks = blocks,
            rawHeatmap = det.heatmap,
            heatmapWidth = det.width,
            heatmapHeight = det.height,
            scaleFactor = det.scale,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }
}
