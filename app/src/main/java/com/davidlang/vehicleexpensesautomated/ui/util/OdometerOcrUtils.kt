package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.Collections

object OdometerOcrUtils {

    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("OdometerOcr", "OpenCV initialization failed!")
        } else {
            Log.i("OdometerOcr", "OpenCV initialized successfully")
        }
    }

    data class EngineResult(val angle: Float, val timesMs: List<Long>, val blocks: List<TextBlock> = emptyList(), val metadata: Map<String, String> = emptyMap())
    data class DeskewResult(
        val angle: Float, 
        val mlAngle: Float, 
        val mlTimeMs: Long, 
        val paddleTimeMs: Long, 
        val paddleCppAngle: Float = 0f,
        val mlBlocks: List<TextBlock> = emptyList(), 
        val paddleBlocks: List<TextBlock> = emptyList(), 
        
        val engines: Map<String, EngineResult> = emptyMap(),
        val metadata: Map<String, String> = emptyMap()
    )

    suspend fun calculateAverageTextAngle(input: Any): DeskewResult {
        val t0 = System.currentTimeMillis()
        
        // 1. Unified Preparation (Bitmap or BufferSet.Slice)
        val pTargetSize = 2048
        val bufferSet = NativePaddleEngine.deskewBufferSet2048
        
        val srcW = if (input is Bitmap) input.width else (input as BufferSet.Slice).width
        val srcH = if (input is Bitmap) input.height else (input as BufferSet.Slice).height
        
        val pScale = Math.min(pTargetSize.toFloat() / srcW, pTargetSize.toFloat() / srcH)
        val pWidth = (srcW * pScale).toInt()
        val pHeight = (srcH * pScale).toInt()

        bufferSet.p.clear()
        val cropId = bufferSet.createCrop(0, 0, pWidth, pHeight)
        val workspaceCrop = bufferSet.c[cropId]

        // 2. Native Resize into workspace
        if (input is Bitmap) {
            val argbMat = Mat()
            org.opencv.android.Utils.bitmapToMat(input, argbMat)
            val gray = Mat()
            Imgproc.cvtColor(argbMat, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.resize(gray, workspaceCrop.mat, Size(pWidth.toDouble(), pHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            argbMat.release(); gray.release()
        } else {
            Imgproc.resize((input as BufferSet.Slice).mat, workspaceCrop.mat, Size(pWidth.toDouble(), pHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        }
        
        val tPrep = System.currentTimeMillis() - t0
        val results = mutableMapOf<String, EngineResult>()
        
        // 3. ML Kit Path
        val tMl0 = System.currentTimeMillis()
        val mlRes = deskewMlKit(bufferSet.p.nv21, bufferSet.p.width, bufferSet.p.height, pScale)
        val tMl = System.currentTimeMillis() - tMl0
        results["ML Kit"] = mlRes.copy(timesMs = listOf(tPrep, tMl))

        // 4. Paddle Path (Combined V3 Kotlin + C++ Native)
        val tPd0 = System.currentTimeMillis()
        val (pdV3, pdCpp) = deskewPaddleDual(workspaceCrop.mat, workspaceCrop.width, workspaceCrop.height, pScale)
        val tPd = System.currentTimeMillis() - tPd0
        results["Paddle V3"] = pdV3.copy(timesMs = listOf(tPrep, tPd))
        results["Paddle C++"] = pdCpp.copy(timesMs = listOf(tPrep, tPd))
        
        workspaceCrop.release()
        
        return DeskewResult(
            angle = mlRes.angle.coerceIn(-20f, 20f), 
            mlAngle = mlRes.angle,
            mlTimeMs = results["ML Kit"]?.timesMs?.sum() ?: 0L,
            paddleTimeMs = results["Paddle V3"]?.timesMs?.sum() ?: 0L,
            paddleCppAngle = pdCpp.angle,
            mlBlocks = mlRes.blocks,
            paddleBlocks = pdV3.blocks,
            engines = results,
            metadata = mapOf("t_prep_ms" to tPrep.toString())
        )
    }

    private suspend fun deskewMlKit(nv21: ByteBuffer, width: Int, height: Int, pScale: Float): EngineResult {
        val tStart = System.currentTimeMillis()
        val img = InputImage.fromByteBuffer(nv21, width, height, 0, InputImage.IMAGE_FORMAT_NV21)
        val res = extractFromPhotoBitmapRaw(img)
        val tDetect = System.currentTimeMillis() - tStart
        
        val invScale = 1.0f / pScale
        val scaledBlocks = res.textBlocks.map { block ->
            val b = block.boundingBox
            block.copy(boundingBox = android.graphics.Rect((b.left * invScale).toInt(), (b.top * invScale).toInt(), (b.right * invScale).toInt(), (b.bottom * invScale).toInt()))
        }
        val srcH = (height * invScale).toInt()
        return EngineResult(calculateWeightedAverage(scaledBlocks, srcH), listOf(tDetect), scaledBlocks)
    }

    private suspend fun deskewPaddleDual(resizedMat: Mat, pWidth: Int, pHeight: Int, pScale: Float): Pair<EngineResult, EngineResult> {
        val paddleEngine = VehicleExpensesApplication.anchoredEngineV3 ?: return Pair(EngineResult(0f, emptyList()), EngineResult(0f, emptyList()))
        val det = paddleEngine.detect(resizedMat, pWidth, pHeight) ?: return Pair(EngineResult(0f, emptyList()), EngineResult(0f, emptyList()))
        
        // 1. Paddle V3 (Legacy Kotlin Math on new fast blocks)
        val (blocks, chks) = processPaddleHeatmap(det.heatmap, det.width, det.height, pScale, "None")
        val srcH = (pHeight / pScale).toInt()
        val angleV3 = calculateWeightedAverage(blocks, srcH)
        val resV3 = EngineResult(angleV3, emptyList(), blocks, det.metadata + chks)

        // 2. Paddle C++ (New Native Math)
        val angleCpp = NativeImageUtils.nativeHeatmapToAngle(det.heatmap, det.width, det.height, 0.20f)
        val resCpp = EngineResult(angleCpp, emptyList(), emptyList(), det.metadata + chks)

        return Pair(resV3, resCpp)
    }

    private fun prepDeskewBuffer(input: Any, targetBitmap: Bitmap): Triple<Int, Int, Float> {
        val pTargetSize = 2048
        val srcW: Int
        val srcH: Int
        when (input) {
            is Bitmap -> {
                srcW = input.width
                srcH = input.height
            }
            is BufferSet.Slice -> {
                srcW = input.width
                srcH = input.height
            }
            else -> throw IllegalArgumentException("Unsupported input type for deskew: ${input.javaClass.name}")
        }
        
        val pScale = Math.min(pTargetSize.toFloat() / srcW, pTargetSize.toFloat() / srcH)
        val pWidth = (srcW * pScale).toInt()
        val pHeight = (srcH * pScale).toInt()
        
        val grayMat = when (input) {
            is Bitmap -> {
                val argbMat = Mat()
                org.opencv.android.Utils.bitmapToMat(input, argbMat)
                val g = Mat()
                Imgproc.cvtColor(argbMat, g, Imgproc.COLOR_RGBA2GRAY)
                argbMat.release()
                g
            }
            is BufferSet.Slice -> input.mat
            else -> throw IllegalStateException()
        }

        val targetSize = org.opencv.core.Size(pWidth.toDouble(), pHeight.toDouble())
        val resizedGray = Mat(pTargetSize, pTargetSize, org.opencv.core.CvType.CV_8U, org.opencv.core.Scalar(0.0))
        val roiMat = Mat(resizedGray, org.opencv.core.Rect(0, 0, pWidth, pHeight))
        
        Imgproc.resize(grayMat, roiMat, roiMat.size(), 0.0, 0.0, Imgproc.INTER_AREA)
        
        val resizedArgb = Mat()
        Imgproc.cvtColor(resizedGray, resizedArgb, Imgproc.COLOR_GRAY2RGBA)
        org.opencv.android.Utils.matToBitmap(resizedArgb, targetBitmap)
        
        resizedGray.release(); resizedArgb.release(); roiMat.release()
        if (input is Bitmap) grayMat.release()
        
        return Triple(pWidth, pHeight, pScale)
    }

    suspend fun extractFromPhotoBitmapRaw(image: com.google.mlkit.vision.common.InputImage): OcrResult {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val visionText = recognizer.process(image).await()
            val blocks = mutableListOf<TextBlock>()
            val text = StringBuilder()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val hunk = element.text
                        val box = element.boundingBox
                        if (box != null) {
                            blocks.add(TextBlock(hunk, box, line.angle))
                            text.append(hunk).append(" ")
                        }
                    }
                }
            }
            OcrResult(engineName = "ML Kit", debugText = text.toString().trim(), textBlocks = blocks, imageWidth = image.width, imageHeight = image.height)
        } catch (e: Exception) {
            Log.e("OdometerOcr", "ML Kit failed", e)
            OcrResult(engineName = "ML Kit", debugText = "(ML Kit error: ${e.message})", imageWidth = image.width, imageHeight = image.height)
        }
    }

    

    private fun normalizeAngle(angle: Float): Float {
        // Maps angle to [-45, 45] range using 90-degree rotational symmetry
        var a = (angle + 45f) % 90f
        if (a < 0) a += 90f
        return a - 45f
    }

    private fun calculateWeightedAverage(candidates: List<TextBlock>, imgHeight: Int): Float {
        val validCandidates = candidates.filter { 
            it.boundingBox.height() > 0 && it.boundingBox.width() > 0 && it.angle.isFinite()
        }
        if (validCandidates.isEmpty()) return 0f

        // Thickness (Filter Basis): Use the shortest side to identify the text line thickness regardless of rotation.
        val thicknesses = validCandidates.map { 
            kotlin.math.min(it.boundingBox.width(), it.boundingBox.height()).toFloat() / imgHeight.toFloat() 
        }
        val roundedThicknesses = thicknesses.map { Math.round(it / 0.005f) * 0.005f }
        val counts = roundedThicknesses.groupingBy { it }.eachCount()
        val threshold = Math.max(2, (validCandidates.size * 0.15).toInt())
        val peaks = counts.filter { it.value >= threshold }.keys
        
        val floor = if (peaks.isNotEmpty()) {
            peaks.minOrNull()!! * 0.7f
        } else {
            val sortedT = thicknesses.sorted()
            sortedT[sortedT.size / 2] * 0.5f
        }

        val ceiling = if (peaks.isNotEmpty()) {
            peaks.maxOrNull()!! * 1.5f
        } else {
            val sortedT = thicknesses.sorted()
            sortedT[sortedT.size / 2] * 2.0f
        }

        val filtered = validCandidates.filter { 
            val t = kotlin.math.min(it.boundingBox.width(), it.boundingBox.height()).toFloat() / imgHeight.toFloat()
            t >= floor && t <= ceiling
        }

        if (filtered.isEmpty()) return 0f

        // RANSAC-lite: Pick the most frequent angle (the consensus)
        // Group angles by +/- 0.5 degree bucket, normalized to [-45, 45]
        val buckets = filtered.groupBy { Math.round(normalizeAngle(it.angle) * 2) / 2.0f }
        
        // Weight by sqrt(Length) where Length is the longest side of the detection.
        // sqrt dampens single large outliers while allowing multiple text blocks to outvote dust.
        val bestBucket = buckets.maxByOrNull { bucket -> 
            bucket.value.sumOf { 
                kotlin.math.sqrt(kotlin.math.max(it.boundingBox.width(), it.boundingBox.height()).toDouble()) 
            }
        }
        
        val finalAngle = bestBucket?.key ?: 0f
        return if (finalAngle.isFinite()) finalAngle else 0f
    }

    fun matToBase64(mat: Mat, quality: Int = 80): String {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(mat, bitmap)
        return OcrUtils.bitmapToBase64(bitmap, quality)
    }

    fun cleanLandmarkString(text: String): String {
        val filtered = text.filter { it.code in 32..126 }
        val charsToTrim = charArrayOf(' ', '-', '.', '_', ',', '*')
        return filtered.trim { it in charsToTrim }
    }

    fun processRawLandmarks(
        allBlocks: List<TextBlock>,
        odometerCrop: android.graphics.RectF? = null,
        otherTextCrop: android.graphics.RectF? = null,
        imgWidth: Int,
        imgHeight: Int
    ): List<TextBlock> {
        val filtered = allBlocks.filter { block ->
            !OcrUtils.isBlockInCrop(block, odometerCrop, imgWidth, imgHeight) && 
            !OcrUtils.isBlockInCrop(block, otherTextCrop, imgWidth, imgHeight)
        }
        return filtered.map { block ->
            block.copy(text = cleanLandmarkString(block.text))
        }.filter { it.text.length > 1 }.sortedBy { it.text }
    }

    fun applyGrayscaleInPlace(bitmap: Bitmap) {
        if (bitmap.config == Bitmap.Config.ALPHA_8) return
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)
        // Convert back to ARGB_8888 in-place
        Imgproc.cvtColor(gray, mat, Imgproc.COLOR_GRAY2RGBA)
        org.opencv.android.Utils.matToBitmap(mat, bitmap)
        mat.release(); gray.release()
    }

    fun applyBilateralInPlace(bitmap: Bitmap, scratchBmp: Bitmap) {
        val src = if (bitmap.config == Bitmap.Config.ALPHA_8) bitmapToMat(bitmap) else {
            val m = Mat(); org.opencv.android.Utils.bitmapToMat(bitmap, m); m
        }
        val gray = Mat()
        if (src.channels() > 1) Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY) else src.copyTo(gray)
        val filtered = Mat()
        Imgproc.bilateralFilter(gray, filtered, 5, 75.0, 75.0)
        
        if (bitmap.config == Bitmap.Config.ALPHA_8) {
            matToBitmap(filtered, bitmap)
        } else {
            val outMat = Mat(); Imgproc.cvtColor(filtered, outMat, Imgproc.COLOR_GRAY2RGBA)
            org.opencv.android.Utils.matToBitmap(outMat, scratchBmp)
            Canvas(bitmap).drawBitmap(scratchBmp, 0f, 0f, null)
            outMat.release()
        }
        src.release(); gray.release(); filtered.release()
    }

    fun applyGrayscale(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ALPHA_8) return bitmap
        val mat = Mat(); org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val gray = Mat(); Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)
        val out = Bitmap.createBitmap(gray.cols(), gray.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(gray, out)
        mat.release(); gray.release(); return out
    }

    fun applyBilateral(bitmap: Bitmap, argbScratch: Bitmap? = null): Bitmap {
        val src = if (bitmap.config == Bitmap.Config.ALPHA_8) bitmapToMat(bitmap) else {
            val m = Mat(); org.opencv.android.Utils.bitmapToMat(bitmap, m); m
        }
        val gray = Mat()
        if (src.channels() > 1) Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY) else src.copyTo(gray)
        val filtered = Mat()
        Imgproc.bilateralFilter(gray, filtered, 5, 75.0, 75.0)

        // Phase 115: In-place update
        if (bitmap.config == Bitmap.Config.ALPHA_8) {
            matToBitmap(filtered, bitmap)
        } else {
            org.opencv.android.Utils.matToBitmap(filtered, bitmap)
        }
        src.release(); gray.release(); filtered.release(); return bitmap
    }
    fun applyContrastStretch(mat: Mat, floorPercentile: Float) {
        val hist = Mat()
        Imgproc.calcHist(java.util.Collections.singletonList(mat), MatOfInt(0), Mat(), hist, MatOfInt(256), MatOfFloat(0f, 256f))
        val totalPixels = mat.rows() * mat.cols(); var floorBin = 0; var ceilingBin = 255; var sum = 0.0
        for (i in 0..255) { sum += hist.get(i, 0)[0]; if (sum >= totalPixels * floorPercentile) { floorBin = i; break } }
        sum = 0.0; for (i in 0..255) { sum += hist.get(i, 0)[0]; if (sum >= totalPixels * 0.98) { ceilingBin = i; break } }
        val alpha = if (ceilingBin > floorBin) 255.0 / (ceilingBin - floorBin) else 1.0; val beta = -floorBin * alpha
        mat.convertTo(mat, CvType.CV_8U, alpha, beta)
        hist.release()
    }

    fun applyContrastStretch(bitmap: Bitmap, floorPercentile: Int): Bitmap {
        val src = if (bitmap.config == Bitmap.Config.ALPHA_8) bitmapToMat(bitmap) else {
            val m = Mat(); org.opencv.android.Utils.bitmapToMat(bitmap, m); m
        }
        val gray = Mat()
        if (src.channels() > 1) Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY) else src.copyTo(gray)

        val hist = Mat()
        Imgproc.calcHist(java.util.Collections.singletonList(gray), MatOfInt(0), Mat(), hist, MatOfInt(256), MatOfFloat(0f, 256f))
        val totalPixels = gray.rows() * gray.cols(); var floorBin = 0; var ceilingBin = 255; var sum = 0.0
        for (i in 0..255) { sum += hist.get(i, 0)[0]; if (sum >= totalPixels * (floorPercentile / 100.0)) { floorBin = i; break } }
        sum = 0.0; for (i in 0..255) { sum += hist.get(i, 0)[0]; if (sum >= totalPixels * 0.98) { ceilingBin = i; break } }

        val dst = Mat(); val alpha = if (ceilingBin > floorBin) 255.0 / (ceilingBin - floorBin) else 1.0; val beta = -floorBin * alpha
        gray.convertTo(dst, CvType.CV_8U, alpha, beta)

        // Phase 115: In-place update to long-lived buffer
        if (bitmap.config == Bitmap.Config.ALPHA_8) {
            matToBitmap(dst, bitmap)
        } else {
            org.opencv.android.Utils.matToBitmap(dst, bitmap)
        }
        src.release(); gray.release(); hist.release(); dst.release(); return bitmap
    }
    // Phase 115: CV_8UC1 Monochrome Bridge (Zero-Allocation)
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8U)
        val capacity = bitmap.width * bitmap.height
        val buffer = java.nio.ByteBuffer.allocateDirect(capacity).order(java.nio.ByteOrder.nativeOrder())
        val bytes = ByteArray(capacity)
        buffer.rewind()
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
        buffer.get(bytes, 0, capacity)
        mat.put(0, 0, bytes)
        return mat
    }

    fun matToBitmap(mat: Mat, bitmap: Bitmap) {
        val capacity = bitmap.width * bitmap.height
        val bytes = ByteArray(capacity)
        mat.get(0, 0, bytes)
        val buffer = java.nio.ByteBuffer.allocateDirect(capacity).order(java.nio.ByteOrder.nativeOrder())
        buffer.rewind()
        buffer.put(bytes, 0, capacity)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
    }
        

    fun applyClahe(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        if (mat.channels() > 1) Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY) else mat.copyTo(gray)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val outMat = Mat()
        clahe.apply(gray, outMat)
        val out = Bitmap.createBitmap(outMat.cols(), outMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(outMat, out)
        mat.release(); gray.release(); outMat.release()
        return out
    }

    fun applyOtsu(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        if (mat.channels() > 1) Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY) else mat.copyTo(gray)
        val threshed = Mat()
        Imgproc.threshold(gray, threshed, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        val out = Bitmap.createBitmap(threshed.cols(), threshed.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(threshed, out)
        mat.release(); gray.release(); threshed.release()
        return out
    }

    fun isBlockInCrop(block: TextBlock, crop: android.graphics.RectF?, w: Int, h: Int): Boolean {
        return OcrUtils.isBlockInCrop(block, crop, w, h)
    }

    fun decodeBitmapSafely(context: Context, path: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inMutable = true }
            if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(path))?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } else {
                BitmapFactory.decodeFile(path, options)
            }
        } catch (e: Exception) {
            Log.e("OdometerOcr", "Failed to decode bitmap: $path", e)
            null
        }
    }

    fun rotateImageIfRequired(bitmap: Bitmap, path: String): Bitmap {
        try {
            val exif = if (path.startsWith("content://")) null else android.media.ExifInterface(path)
            val orientation = exif?.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bitmap
            return rotateBitmap(bitmap, degrees)
        } catch (e: Exception) {
            return bitmap
        }
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    suspend fun extractFromPhotoBitmap(bitmap: Bitmap): OcrResult {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val visionText = recognizer.process(image).await()
            val blocks = mutableListOf<TextBlock>()
            val text = StringBuilder()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val hunk = element.text
                        val box = element.boundingBox
                        if (box != null) {
                            blocks.add(TextBlock(hunk, box, line.angle))
                            text.append(hunk).append(" ")
                        }
                    }
                }
            }
            OcrResult(engineName = "ML Kit", debugText = text.toString().trim(), textBlocks = blocks, imageWidth = image.width, imageHeight = image.height)
        } catch (e: Exception) {
            Log.e("OdometerOcr", "ML Kit failed", e)
            OcrResult(engineName = "ML Kit", debugText = "(ML Kit error: ${e.message})", imageWidth = image.width, imageHeight = image.height)
        }
    }

    fun addPadding(bitmap: Bitmap, padding: Int, color: Int = Color.BLACK): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width + 2 * padding, bitmap.height + 2 * padding, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(color)
        canvas.drawBitmap(bitmap, padding.toFloat(), padding.toFloat(), null)
        return out
    }

    /**
     * Phase 117 Patch 3: Parallel Execution Harness with Checksums
     */
    fun processPaddleHeatmap(
        heatmap: FloatArray, w: Int, h: Int, scale: Float, 
        sourceBuffer: Any, algorithm: String = "Native"
    ): Pair<List<TextBlock>, Map<String, String>> {
        val invScale = 1.0f / scale
        
        // 1. Legacy Kotlin Path (Instrumented)
        val (blocksKt, chksKt) = processPaddleHeatmapLegacy(heatmap, w, h, scale)
        
        // 2. New Native C++ Path (Instrumented)
        val (blocksCpp, chksCpp) = try {
            val rawData = NativeImageUtils.nativeHeatmapToTextAreas(heatmap, w, h, 0.20f, invScale)
            if (rawData.isEmpty()) Pair(emptyList<TextBlock>(), floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f))
            else {
                val chks = floatArrayOf(rawData[0], rawData[1], rawData[2], rawData[3], rawData[4], rawData[5], rawData[6])
                val count = rawData[7].toInt()
                val res = mutableListOf<TextBlock>()
                for (i in 0 until count) {
                    val base = 8 + i * 10
                    val p1 = org.opencv.core.Point(rawData[base + 0].toDouble(), rawData[base + 1].toDouble())
                    val p2 = org.opencv.core.Point(rawData[base + 2].toDouble(), rawData[base + 3].toDouble())
                    val p3 = org.opencv.core.Point(rawData[base + 4].toDouble(), rawData[base + 5].toDouble())
                    val p4 = org.opencv.core.Point(rawData[base + 6].toDouble(), rawData[base + 7].toDouble())
                    val angle = rawData[base + 8]
                    val confidence = rawData[base + 9]

                    val minX = minOf(p1.x, p2.x, p3.x, p4.x).toInt()
                    val minY = minOf(p1.y, p2.y, p3.y, p4.y).toInt()
                    val maxX = maxOf(p1.x, p2.x, p3.x, p4.x).toInt()
                    val maxY = maxOf(p1.y, p2.y, p3.y, p4.y).toInt()

                    res.add(TextBlock(
                        text = "",
                        boundingBox = android.graphics.Rect(minX, minY, maxX, maxY),
                        angle = angle,
                        points = listOf(p1, p2, p3, p4),
                        confidence = confidence
                    ))
                }
                Pair(res, chks)
            }
        } catch (e: Exception) {
            Log.e("PaddlePost", "Native call failed", e)
            Pair(emptyList<TextBlock>(), floatArrayOf(-1f, -1f, -1f, -1f, -1f, -1f, -1f))
        }

        // 3. Collate metadata
        val meta = mutableMapOf<String, String>()
        meta["kt_chk_mask"] = "%.1f".format(chksKt[0])
        meta["kt_chk_mask_pos"] = "%.1f".format(chksKt[1])
        meta["kt_byte_0"] = "%.1f".format(chksKt[2])
        meta["kt_byte_mid"] = "%.1f".format(chksKt[3])
        meta["kt_chk_rawc"] = "%.1f".format(chksKt[4])
        meta["kt_chk_validc"] = "%.1f".format(chksKt[5])
        meta["kt_chk_geom"] = "%.1f".format(chksKt[6])
        meta["kt_count"] = blocksKt.size.toString()
        
        meta["cpp_chk_mask"] = "%.1f".format(chksCpp[0])
        meta["cpp_chk_mask_pos"] = "%.1f".format(chksCpp[1])
        meta["cpp_byte_0"] = "%.1f".format(chksCpp[2])
        meta["cpp_byte_mid"] = "%.1f".format(chksCpp[3])
        meta["cpp_chk_rawc"] = "%.1f".format(chksCpp[4])
        meta["cpp_chk_validc"] = "%.1f".format(chksCpp[5])
        meta["cpp_chk_geom"] = "%.1f".format(chksCpp[6])
        meta["cpp_count"] = blocksCpp.size.toString()

        // 4. Return Legacy blocks to guarantee experiment success
        return Pair(blocksKt, meta)
    }

    private fun processPaddleHeatmapLegacy(
        heatmap: FloatArray, w: Int, h: Int, scale: Float
    ): Pair<List<TextBlock>, FloatArray> {
        val invScale = 1.0 / scale.toDouble()
        val maskThreshold = 0.20f
        val mask = Mat(h, w, CvType.CV_8U)
        val data = ByteArray(heatmap.size)
        var chkMask = 0f
        var chkMaskPos = 0.0
        for (i in heatmap.indices) {
            val v = if (heatmap[i] > maskThreshold) 255.toByte() else 0.toByte()
            data[i] = v
            if (v != 0.toByte()) {
                chkMask += 1.0f
                val x = i % w; val y = i / w
                chkMaskPos += x.toDouble() * y.toDouble()
            }
        }
        mask.put(0, 0, data)

        val byte0 = data[0].toFloat()
        val byteMid = data[(h / 2) * w + (w / 2)].toFloat()

        val contours = mutableListOf<org.opencv.core.MatOfPoint>()
        val hierarchy = Mat()
        val results = mutableListOf<TextBlock>()
        
        var chkRawC = 0f; var chkValidC = 0f; var chkGeom = 0f

        try {
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            for (contour in contours) {
                val pts = contour.toArray()
                for (p in pts) chkRawC += (p.x + p.y).toFloat()

                if (Imgproc.contourArea(contour) < 10) continue
                for (p in pts) chkValidC += (p.x + p.y).toFloat()

                val p2f = org.opencv.core.MatOfPoint2f(*pts)
                val rotatedRect = Imgproc.minAreaRect(p2f)
                val points = arrayOf(org.opencv.core.Point(), org.opencv.core.Point(), org.opencv.core.Point(), org.opencv.core.Point())
                rotatedRect.points(points)
                p2f.release() 
                
                for (p in points) chkGeom += (p.x + p.y).toFloat()

                val bounds = android.graphics.Rect(
                    (rotatedRect.boundingRect().x * invScale).toInt(),
                    (rotatedRect.boundingRect().y * invScale).toInt(),
                    ((rotatedRect.boundingRect().x + rotatedRect.boundingRect().width) * invScale).toInt(),
                    ((rotatedRect.boundingRect().y + rotatedRect.boundingRect().height) * invScale).toInt()
                )
                
                val normalizedPoints = points.map { org.opencv.core.Point(it.x * invScale, it.y * invScale) }
                results.add(TextBlock("", bounds, rotatedRect.angle.toFloat(), points = normalizedPoints))
            }
        } finally {
            mask.release(); hierarchy.release(); contours.forEach { it.release() }
        }
        return Pair(results, floatArrayOf(chkMask, chkMaskPos.toFloat(), byte0, byteMid, chkRawC, chkValidC, chkGeom))
    }

    fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val width = rect.width().coerceAtMost(bitmap.width - left)
        val height = rect.height().coerceAtMost(bitmap.height - top)
        if (width <= 0 || height <= 0) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    fun pickBestOdometer(results: List<OcrStepResult>): String? {
        val candidates = results.mapNotNull { it.text }.filter { it.length in 4..7 && it.all { c -> c.isDigit() } }
        if (candidates.isEmpty()) return null
        return candidates.groupBy { it }.maxByOrNull { it.value.size }?.key ?: candidates.maxByOrNull { it.length }
    }

    fun refineNumericResult(result: OcrResult): OcrResult {
        val refinedBlocks = result.textBlocks.map { block ->
            val isFlipped = Math.abs(block.angle) > 165f
            val cleaned = clean7SegmentDigits(block.text, isFlipped)
            block.copy(text = cleaned)
        }
        return result.copy(textBlocks = refinedBlocks, debugText = refinedBlocks.joinToString(" ") { it.text })
    }

    fun clean7SegmentDigits(text: String, isFlipped: Boolean): String {
        val standardMap = mapOf('O' to '0', 'D' to '0', 'Q' to '0', 'U' to '0', 'Z' to '2', 'S' to '5', 'G' to '6', 'B' to '8', '!' to '1', 'I' to '1', 'l' to '1', '|' to '1')
        val flipMap = mapOf('O' to '0', 'D' to '0', 'L' to '7', 'V' to '7', 'h' to '4', 'H' to '4', 'E' to '3', 'G' to '9', 'B' to '8', 'S' to '5', '!' to '1', 'I' to '1', 'l' to '1', '|' to '1', 'A' to 'V')
        val workingText = if (isFlipped) text.reversed() else text
        val activeMap = if (isFlipped) flipMap else standardMap
        return workingText.map { char -> activeMap[char] ?: (activeMap[char.uppercaseChar()] ?: char) }.joinToString("")
    }

    fun serializeLandmarks(landmarks: List<TextBlock>, imgW: Int, imgH: Int): String {
        val array = JSONArray()
        landmarks.forEach { block ->
            val cleaned = cleanLandmarkString(block.text)
            if (cleaned.length > 1) {
                val obj = JSONObject()
                obj.put("text", cleaned)
                val box = block.boundingBox
                val icrs = IcrsMath.pixelToIcrs(box.centerX().toFloat(), box.centerY().toFloat(), imgW, imgH)
                val s = minOf(imgW, imgH).toDouble()
                obj.put("cx", icrs.x.toDouble())
                obj.put("cy", icrs.y.toDouble())
                obj.put("w", box.width().toDouble() / s)
                obj.put("h", box.height().toDouble() / s)
                obj.put("is_icrs", true)
                array.put(obj)
            }
        }
        return array.toString()
    }

    fun serializeMultiEngineLandmarks(results: Map<String, OcrResult>): String {
        val t0 = System.currentTimeMillis()
        val root = JSONObject()
        results.forEach { (engineName, res) ->
            Log.e("LandmarkSerialization", "Serializing landmarks for engine: $engineName")
            val array = JSONArray()
            
            // Pass 1: Count total occurrences for detectable landmarks ONLY (Phase 109 Refined)
            // Manual landmarks (width=0) are excluded from this uniqueness tally.
            val globalCounts = res.textBlocks
                .filter { it.boundingBox.width() > 0 }
                .groupBy { cleanLandmarkString(it.text) }
                .mapValues { it.value.size }
            
            val runningCounts = mutableMapOf<String, Int>()
            res.textBlocks.forEach { block ->
                val cleaned = cleanLandmarkString(block.text)
                if (cleaned.length > 1) {
                    val isManual = block.boundingBox.width() == 0
                    val total = globalCounts[cleaned] ?: 0
                    
                    val instance = if (isManual) {
                        -2 // Tag as Manual (Invisible to alignment, visible to Veto)
                    } else if (total == 1) {
                        0  // Globally Unique Detectable Landmark
                    } else {
                        val count = (runningCounts[cleaned] ?: 0) + 1
                        runningCounts[cleaned] = count
                        count // Specific instance of duplicate (1, 2, ...)
                    }
                    
                    val obj = JSONObject()
                    obj.put("text", cleaned)
                    val box = block.boundingBox
                    val icrs = IcrsMath.pixelToIcrs(box.centerX().toFloat(), box.centerY().toFloat(), res.imageWidth, res.imageHeight)
                    val s = minOf(res.imageWidth, res.imageHeight).toDouble()
                    obj.put("cx", icrs.x.toDouble())
                    obj.put("cy", icrs.y.toDouble())
                    obj.put("w", box.width().toDouble() / s)
                    obj.put("h", box.height().toDouble() / s)
                    obj.put("instance", instance)
                    obj.put("is_icrs", true)
                    array.put(obj)
                }
            }
            root.put(engineName, array)
        }
        Log.i("OCR_PERF", "serializeMultiEngineLandmarks took ${System.currentTimeMillis() - t0}ms")
        return root.toString()
    }

    fun deserializeMultiEngineLandmarks(json: String?, imgW: Int, imgH: Int): Map<String, OcrResult> {
        if (json.isNullOrEmpty()) return emptyMap()
        val results = mutableMapOf<String, OcrResult>()
        try {
            val root = JSONObject(json)
            val engines = root.keys()
            while (engines.hasNext()) {
                val name = engines.next()
                val array = root.getJSONArray(name)
                val blocks = mutableListOf<TextBlock>()
                for (i in 0 until array.length()) {
                    try {
                        val obj = array.getJSONObject(i)
                        var cx = obj.getDouble("cx")
                        var cy = obj.getDouble("cy")
                        var w = obj.getDouble("w")
                        var h = obj.getDouble("h")
                        val instanceId = if (obj.has("instance")) obj.getInt("instance") else -1
                        val isIcrs = obj.optBoolean("is_icrs", false)

                        val centerPix = if (isIcrs) {
                            IcrsMath.icrsToPixel(cx.toFloat(), cy.toFloat(), imgW, imgH)
                        } else {
                            android.graphics.PointF((cx * imgW).toFloat(), (cy * imgH).toFloat())
                        }

                        val shortEdge = minOf(imgW, imgH).toDouble()
                        val pixW = if (isIcrs) (w * shortEdge) else (w * imgW)
                        val pixH = if (isIcrs) (h * shortEdge) else (h * imgH)

                        val left = (centerPix.x - pixW / 2.0).toInt()
                        val top = (centerPix.y - pixH / 2.0).toInt()
                        val right = (centerPix.x + pixW / 2.0).toInt()
                        val bottom = (centerPix.y + pixH / 2.0).toInt()
                        blocks.add(TextBlock(obj.getString("text"), android.graphics.Rect(left, top, right, bottom), instanceId = instanceId))
                    } catch (e: Exception) {
                        Log.w("OdometerOcr", "Skipping malformed landmark entry in JSON: ${e.message}")
                    }
                }
                results[name] = OcrResult(engineName = name, textBlocks = blocks, imageWidth = imgW, imageHeight = imgH, debugText = blocks.joinToString(" ") { it.text })
            }
        } catch (e: Exception) { Log.e("OdometerOcr", "Deserialization failed", e) }
        return results
    }

    suspend fun extractFromPhoto(photoPath: String, cropRect: RectF? = null, context: Context? = null): OcrResult = withContext(Dispatchers.IO) {
        val rawBitmap = if (context != null) decodeBitmapSafely(context, photoPath) else BitmapFactory.decodeFile(photoPath)
        if (rawBitmap == null) return@withContext OcrResult(debugText = "Failed decode", originalPhotoPath = photoPath)
        val rotated = rotateImageIfRequired(rawBitmap, photoPath)
        val processed = applyBilateral(applyGrayscale(rotated))
        var bitmap = processed
        if (cropRect != null) {
            val left = (cropRect.left * processed.width).toInt().coerceIn(0, processed.width)
            val top = (cropRect.top * processed.height).toInt().coerceIn(0, processed.height)
            val right = (cropRect.right * processed.width).toInt().coerceAtMost(processed.width)
            val bottom = (cropRect.bottom * processed.height).toInt().coerceAtMost(processed.height)
            if (right > left && bottom > top) bitmap = Bitmap.createBitmap(processed, left, top, right - left, bottom - top)
        }
        val res = extractFromPhotoBitmap(bitmap)
        res.copy(originalPhotoPath = photoPath)
    }

    suspend fun discoverLandmarks(photoPath: String, odometerCrop: RectF? = null, otherTextCrop: RectF? = null): List<TextBlock> = withContext(Dispatchers.IO) {
        val rawBitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext emptyList()
        val rotated = rotateImageIfRequired(rawBitmap, photoPath)
        val processed = applyBilateral(rotated)
        val ocrResult = extractFromPhotoBitmap(processed)
        val landmarks = processRawLandmarks(ocrResult.textBlocks, odometerCrop, otherTextCrop, processed.width, processed.height)
        landmarks
    }
}
