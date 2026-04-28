package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class NativePaddleEngine(private val context: Context, private val variant: String = "V3") : OcrEngine {
    override val name = "Paddle $variant Greedy"
    fun isV3() = variant == "V3"
    
    private val dictionary = mutableListOf<String>()
    private var initError: String? = null
    var isAvailable = false
        private set

    companion object {
        private var sharedDetector: PaddlePredictor? = null
        private var sharedRecognizerV3: PaddlePredictor? = null
        private var sharedRecognizerNumeric: PaddlePredictor? = null
        private var isNativeLibLoaded = false
        private var detectionInputBuffer: FloatArray? = null
        private var lastUsedInputSize = 0
        
        /**
         * Phase 58: Static-like helper for experimental refinement without re-initializing.
         */
        suspend fun runConstrainedStatic(bitmap: Bitmap, targetHeight: Int, dictionary: List<String>, isV3: Boolean): String = withContext(Dispatchers.IO) {
            val predictor = if (isV3) sharedRecognizerV3 else sharedRecognizerNumeric
            if (predictor == null) return@withContext ""
            runRecognitionStageStatic(bitmap, targetHeight, dictionary, predictor).text
        }

        private fun runRecognitionStageStatic(bitmap: Bitmap, ignoredHeight: Int, dictionary: List<String>, predictor: PaddlePredictor): RecStageResult {
            val tStart = System.currentTimeMillis()
            val targetHeight = 48; val targetWidth = 640
            val inputTensor = predictor.getInput(0); inputTensor.resize(longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()))
            val floatData = FloatArray(1 * 3 * targetHeight * targetWidth)
            val scale = targetHeight.toFloat() / bitmap.height.toFloat()
            val sw = (bitmap.width * scale).toInt().coerceAtMost(targetWidth)
            val sh = targetHeight
            val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
            val padded = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(padded); canvas.drawColor(Color.BLACK); canvas.drawBitmap(scaled, 0f, 0f, null)
            scaled.recycle()

            val mean = floatArrayOf(0.485f, 0.456f, 0.406f); val std = floatArrayOf(0.229f, 0.224f, 0.225f)
            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val px = padded.getPixel(x, y)
                    floatData[0 * targetHeight * targetWidth + y * targetWidth + x] = ((px shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
                    floatData[1 * targetHeight * targetWidth + y * targetWidth + x] = ((px shr 8 and 0xFF) / 255.0f - mean[1]) / std[1]
                    floatData[2 * targetHeight * targetWidth + y * targetWidth + x] = ((px and 0xFF) / 255.0f - mean[2]) / std[2]
                }
            }
            padded.recycle()
            try {
                inputTensor.setData(floatData); predictor.run()
                val outputTensor = predictor.getOutput(0); val dims = outputTensor.shape(); val seqLen = dims[1].toInt(); val dictSize = dims[2].toInt(); val data = outputTensor.floatData
                val result = StringBuilder(); var lastIdx = -1; var totalConf = 0f; var charCount = 0
                for (i in 0 until seqLen) {
                    var maxIdx = 0; var maxVal = -1f; 
                    // GREEDY DIGITS: Only consider indices 1..10 (0 is blank)
                    val searchLimit = 11.coerceAtMost(dictSize)
                    for (j in 0 until searchLimit) { val v = data[i * dictSize + j]; if (v > maxVal) { maxVal = v; maxIdx = j } }
                    if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) { result.append(dictionary[maxIdx - 1]); totalConf += maxVal; charCount++ }
                    lastIdx = maxIdx
                }
                return RecStageResult(result.toString(), System.currentTimeMillis() - tStart, if (charCount > 0) totalConf / charCount else 0f)
            } catch (t: Throwable) { return RecStageResult("", 0, 0f) }
        }

        private data class RecStageResult(val text: String, val timeMs: Long, val confidence: Float)
    }

    init {
        try {
            val arch = detectArch()
            if (sharedDetector == null) {
                if (!isNativeLibLoaded) { loadNativeLibrary(); isNativeLibLoaded = true }
                val detPath = copyAssetToInternal("paddle/det_v4_dynamic_$arch.nb")
                sharedDetector = createPredictor(detPath)
            }
            if (variant == "V3" && sharedRecognizerV3 == null) {
                if (!isNativeLibLoaded) { loadNativeLibrary(); isNativeLibLoaded = true }
                sharedRecognizerV3 = createPredictor(copyAssetToInternal("paddle/rec_v3_$arch.nb"))
            }
            if (variant == "V2" && sharedRecognizerNumeric == null) {
                if (!isNativeLibLoaded) { loadNativeLibrary(); isNativeLibLoaded = true }
                sharedRecognizerNumeric = createPredictor(copyAssetToInternal("paddle/rec_numeric_$arch.nb"))
            }
            loadDictionary("paddle/en_dict.txt")
            isAvailable = true
        } catch (e: Throwable) {
            isAvailable = false
            initError = e.message
            Log.e("PaddleLite", "Failed to initialize predictors", e)
        }
    }

    private fun loadNativeLibrary() {
        val abi = Build.SUPPORTED_ABIS[0]; val libName = "libpaddle_lite_jni.so"; val assetPath = "libs_backup/${abi}_$libName"
        try { val internalLibPath = copyAssetToInternal(assetPath); System.load(internalLibPath) } catch (e: Exception) { System.loadLibrary("paddle_lite_jni") }
    }

    private fun detectArch(): String = when (Build.SUPPORTED_ABIS[0]) { "arm64-v8a" -> "armv8"; "armeabi-v7a" -> "armv7"; else -> "x86_64" }

    private fun copyAssetToInternal(assetPath: String): String {
        val file = File(context.filesDir, assetPath.replace("/", "_"))
        if (!context.assets.list(File(assetPath).parent ?: "")!!.contains(File(assetPath).name)) {
            Log.e("Paddle", "Asset not found: $assetPath")
            throw Exception("Asset not found: $assetPath")
        }
        context.assets.open(assetPath).use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
        return file.absolutePath
    }

    private fun loadDictionary(assetPath: String) {
        dictionary.clear()
        context.assets.open(assetPath).bufferedReader().use { reader -> reader.forEachLine { dictionary.add(it) } }
    }
    
    fun getDictionary(): List<String> = dictionary

    private fun createPredictor(modelPath: String): PaddlePredictor {
        val config = MobileConfig(); config.setModelFromFile(modelPath); config.setThreads(4); config.setPowerMode(PowerMode.LITE_POWER_HIGH)
        return PaddlePredictor.createPaddlePredictor(config)
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext OcrResult(engineName = name, debugText = "Not Available: $initError", imageWidth = bitmap.width, imageHeight = bitmap.height)
        val t0 = System.currentTimeMillis()
        val predictor = if (variant == "V3") sharedRecognizerV3 else sharedRecognizerNumeric
        if (predictor == null) return@withContext OcrResult(engineName = name, debugText = "Predictor null", imageWidth = bitmap.width, imageHeight = bitmap.height)
        
        val finalResult = runRecognitionStageStatic(bitmap, 48, dictionary, predictor)
        OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            odometer = finalResult.text,
            debugText = finalResult.text,
            textBlocks = listOf(TextBlock(finalResult.text, Rect(0,0,bitmap.width, bitmap.height))),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }

    private suspend fun recognizeDiscovery(bitmap: Bitmap, t0: Long, isRecursive: Boolean): OcrResult {
        val textBlocks = mutableListOf<TextBlock>()
        val sb = StringBuilder()
        val detectionRes = runDetection(bitmap, isRecursive)
        
        for (i in detectionRes.rawBoxes.indices) {
            val rawBox = detectionRes.rawBoxes[i]
            val refinedBox = detectionRes.refinedBoxes.getOrNull(i)
            val orange = refinedBox?.boundingBox ?: rawBox.boundingBox
            
            val left = (orange.left * bitmap.width).toInt()
            val top = (orange.top * bitmap.height).toInt()
            val right = (orange.right * bitmap.width).toInt()
            val bottom = (orange.bottom * bitmap.height).toInt()
            val cropRect = Rect(max(0, left), max(0, top), min(bitmap.width, right), min(bitmap.height, bottom))

            if (cropRect.width() < 1 || cropRect.height() < 1) {
                textBlocks.add(TextBlock(text = "", boundingBox = cropRect, rawDiscoveryBox = rawBox.boundingBox, refinedDiscoveryBox = refinedBox?.boundingBox))
                continue
            }

            val crop = cropBitmap(bitmap, cropRect)
            val res = runRecognitionStage(crop, 48)
            crop.recycle()

            // SELF-CONTAINED DIAGNOSTIC LOG
            val rawW = ((rawBox.boundingBox.right - rawBox.boundingBox.left) * bitmap.width).toInt()
            val rawH = ((rawBox.boundingBox.bottom - rawBox.boundingBox.top) * bitmap.height).toInt()
            val rawL = (rawBox.boundingBox.left * bitmap.width).toInt()
            val rawT = (rawBox.boundingBox.top * bitmap.height).toInt()
            val refW = ((orange.right - orange.left) * bitmap.width).toInt()
            val refH = ((orange.bottom - orange.top) * bitmap.height).toInt()
            val refL = (orange.left * bitmap.width).toInt()
            val refT = (orange.top * bitmap.height).toInt()
            android.util.Log.i("OCR_TRACE", "Engine: $name | Source: ${bitmap.width}x${bitmap.height} | Text: '${res.text}' | RED: [W=$rawW, H=$rawH, L=$rawL, T=$rawT] | ORANGE: [W=$refW, H=$refH, L=$refL, T=$refT] | YELLOW: [W=${cropRect.width()}, H=${cropRect.height()}, L=${cropRect.left}, T=${cropRect.top}]")

            if (res.text.isNotBlank()) sb.append(res.text).append(" ")            
            textBlocks.add(TextBlock(
                text = res.text, 
                boundingBox = cropRect, 
                angle = refinedBox?.angle ?: 0f,
                rawDiscoveryBox = rawBox.boundingBox,
                refinedDiscoveryBox = refinedBox?.boundingBox
            ))
        }

        return OcrResult(
            engineName = name,
            executionTimeMs = System.currentTimeMillis() - t0,
            discoveryTimeMs = detectionRes.discoveryTimeMs,
            debugText = sb.toString().trim(),
            rawDiscoveryBoxes = detectionRes.rawBoxes.map { it.boundingBox },
            textBlocks = textBlocks,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            scaleFactor = detectionRes.scale
        )
    }

    private data class DetectionResult(val rawBoxes: List<DetectedBox>, val refinedBoxes: List<DetectedBox>, val scale: Float, val discoveryTimeMs: Long)

    private fun runDetection(bitmap: Bitmap, isRecursive: Boolean): DetectionResult {
        val predictor = sharedDetector ?: return DetectionResult(emptyList(), emptyList(), 1f, 0)
        val inputSize = 1280 // REVERTED FROM 2560 FOR STABILITY
        val inputTensor = predictor.getInput(0); inputTensor.resize(longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()))
        if (detectionInputBuffer == null || lastUsedInputSize != inputSize) { detectionInputBuffer = FloatArray(1 * 3 * inputSize * inputSize); lastUsedInputSize = inputSize }
        val floatData = detectionInputBuffer!!

        val scale = min(inputSize.toFloat() / bitmap.width, inputSize.toFloat() / bitmap.height)
        val sw = (bitmap.width * scale).toInt(); val sh = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val padded = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded); canvas.drawColor(Color.BLACK); canvas.drawBitmap(scaled, 0f, 0f, null)
        scaled.recycle()

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f); val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val px = padded.getPixel(x, y)
                floatData[0 * inputSize * inputSize + y * inputSize + x] = ((px shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
                floatData[1 * inputSize * inputSize + y * inputSize + x] = ((px shr 8 and 0xFF) / 255.0f - mean[1]) / std[1]
                floatData[2 * inputSize * inputSize + y * inputSize + x] = ((px and 0xFF) / 255.0f - mean[2]) / std[2]
            }
        }

        try {
            inputTensor.setData(floatData); predictor.run()
            val outputTensor = predictor.getOutput(0); val dims = outputTensor.shape(); val outH = dims[2].toInt(); val outW = dims[3].toInt(); val outputData = outputTensor.floatData
            val dbRes = TfLiteOcrUtils.processDbNetOutput(outputData, outW, outH, scale = scale, sourceBitmap = bitmap, algorithm = "C", recursive = isRecursive)
            
            android.util.Log.i("OcrFlow", "Primary Pass: ${dbRes.rawBoxes.size} boxes, ${dbRes.suspectCrops.size} suspects")
            
            val finalRaw = dbRes.rawBoxes.toMutableList()
            val finalRefined = dbRes.refinedBoxes.toMutableList()

            // Phase 45: High-Res Sub-Windowing for Outliers
            for (cropRectF in dbRes.suspectCrops) {
                val cropLeft = (cropRectF.left * bitmap.width).toInt()
                val cropTop = (cropRectF.top * bitmap.height).toInt()
                val cropWidth = ((cropRectF.right - cropRectF.left) * bitmap.width).toInt()
                val cropHeight = ((cropRectF.bottom - cropRectF.top) * bitmap.height).toInt()
                
                if (cropWidth > 0 && cropHeight > 0) {
                    val subBitmap = cropBitmap(bitmap, android.graphics.Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight))
                    val subScale = min(inputSize.toFloat() / subBitmap.width, inputSize.toFloat() / subBitmap.height)
                    val subSw = (subBitmap.width * subScale).toInt(); val subSh = (subBitmap.height * subScale).toInt()
                    val subScaled = Bitmap.createScaledBitmap(subBitmap, subSw, subSh, true)
                    val subPadded = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
                    val subCanvas = Canvas(subPadded); subCanvas.drawColor(Color.BLACK); subCanvas.drawBitmap(subScaled, 0f, 0f, null)
                    subScaled.recycle()

                    for (y in 0 until inputSize) {
                        for (x in 0 until inputSize) {
                            val px = subPadded.getPixel(x, y)
                            floatData[0 * inputSize * inputSize + y * inputSize + x] = ((px shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
                            floatData[1 * inputSize * inputSize + y * inputSize + x] = ((px shr 8 and 0xFF) / 255.0f - mean[1]) / std[1]
                            floatData[2 * inputSize * inputSize + y * inputSize + x] = ((px and 0xFF) / 255.0f - mean[2]) / std[2]
                        }
                    }
                    inputTensor.setData(floatData); predictor.run()
                    val subOutputTensor = predictor.getOutput(0); val subOutputData = subOutputTensor.floatData
                    val subDbRes = TfLiteOcrUtils.processDbNetOutput(subOutputData, outW, outH, scale = subScale, sourceBitmap = subBitmap, algorithm = "C", recursive = true)
                    
                    android.util.Log.i("OCR_RECURSE", "Sub-Pass for Crop $cropRectF (px=${subBitmap.width}x${subBitmap.height}) found ${subDbRes.rawBoxes.size} items")
                    
                    val cw = cropRectF.right - cropRectF.left
                    val ch = cropRectF.bottom - cropRectF.top
                    for (j in subDbRes.rawBoxes.indices) {
                        val sr = subDbRes.rawBoxes[j].boundingBox
                        val globalRaw = RectF(cropRectF.left + sr.left * cw, cropRectF.top + sr.top * ch, cropRectF.left + sr.right * cw, cropRectF.top + sr.bottom * ch)
                        finalRaw.add(DetectedBox(emptyList(), globalRaw, subDbRes.rawBoxes[j].angle))
                        
                        val srf = subDbRes.refinedBoxes.getOrNull(j)?.boundingBox
                        if (srf != null) {
                            val globalRefined = RectF(cropRectF.left + srf.left * cw, cropRectF.top + srf.top * ch, cropRectF.left + srf.right * cw, cropRectF.top + srf.bottom * ch)
                            finalRefined.add(DetectedBox(emptyList(), globalRefined, subDbRes.refinedBoxes[j].angle))
                        }
                    }
                    subBitmap.recycle(); subPadded.recycle()
                }
            }

            padded.recycle()
            return DetectionResult(finalRaw, finalRefined, scale, dbRes.discoveryTimeMs)
        } catch (t: Throwable) { padded.recycle(); return DetectionResult(emptyList(), emptyList(), 1f, 0) }
    }

    private fun runRecognitionStage(bitmap: Bitmap, ignoredHeight: Int): RecStageResult {
        val tStart = System.currentTimeMillis(); val predictor = sharedRecognizer ?: return RecStageResult("", 0, 0f)
        val targetHeight = 48; val targetWidth = 640
        val inputTensor = predictor.getInput(0); inputTensor.resize(longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()))
        val floatData = FloatArray(1 * 3 * targetHeight * targetWidth)
        val scale = targetHeight.toFloat() / bitmap.height.toFloat()
        val sw = (bitmap.width * scale).toInt().coerceAtMost(targetWidth)
        val sh = targetHeight
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val padded = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded); canvas.drawColor(Color.BLACK); canvas.drawBitmap(scaled, 0f, 0f, null)
        scaled.recycle()

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f); val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val px = padded.getPixel(x, y)
                floatData[0 * targetHeight * targetWidth + y * targetWidth + x] = ((px shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
                floatData[1 * targetHeight * targetWidth + y * targetWidth + x] = ((px shr 8 and 0xFF) / 255.0f - mean[1]) / std[1]
                floatData[2 * targetHeight * targetWidth + y * targetWidth + x] = ((px and 0xFF) / 255.0f - mean[2]) / std[2]
            }
        }
        padded.recycle()
        try {
            inputTensor.setData(floatData); predictor.run()
            val outputTensor = predictor.getOutput(0); val dims = outputTensor.shape(); val seqLen = dims[1].toInt(); val dictSize = dims[2].toInt(); val data = outputTensor.floatData
            val result = StringBuilder(); var lastIdx = -1; var totalConf = 0f; var charCount = 0
            for (i in 0 until seqLen) {
                var maxIdx = 0; var maxVal = -1f; for (j in 0 until dictSize) { val v = data[i * dictSize + j]; if (v > maxVal) { maxVal = v; maxIdx = j } }
                if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= dictionary.size) { result.append(dictionary[maxIdx - 1]); totalConf += maxVal; charCount++ }
                lastIdx = maxIdx
            }
            return RecStageResult(result.toString(), System.currentTimeMillis() - tStart, if (charCount > 0) totalConf / charCount else 0f)
        } catch (t: Throwable) { return RecStageResult("", 0, 0f) }
    }

    private fun cropBitmap(bmp: Bitmap, rect: Rect): Bitmap {
        val left = max(0, rect.left); val top = max(0, rect.top); val width = min(rect.width(), bmp.width - left); val height = min(rect.height(), bmp.height - top)
        return Bitmap.createBitmap(bmp, left, top, width, height)
    }
}
