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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.Collections

object OdometerOcrUtils {
    // Zero-Allocation Buffers for ML Kit Mono (NV21) Conversion
    val reusablePixelArray = IntArray(4000 * 3072)
    val reusableByteStaging = ByteArray(4000 * 3072)


    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("OdometerOcr", "OpenCV initialization failed!")
        } else {
            Log.i("OdometerOcr", "OpenCV initialized successfully")
        }
    }

    data class EngineResult(val angle: Float, val timesMs: List<Long>, val blocks: List<TextBlock> = emptyList())
    data class DeskewResult(
        val angle: Float, 
        val mlAngle: Float, 
        val mlTimeMs: Long, 
        val paddleTimeMs: Long, 
        val mlBlocks: List<TextBlock> = emptyList(), 
        val paddleBlocks: List<TextBlock> = emptyList(), 
        
        val engines: Map<String, EngineResult> = emptyMap()
    )

    suspend fun calculateAverageTextAngle(input: Any): DeskewResult {
        val t0 = System.currentTimeMillis()
        
        val enabledEngines = mapOf(
            "ML Kit" to ::deskewMlKit,
            "Paddle V3" to ::deskewPaddle
        )
        
        val results = mutableMapOf<String, EngineResult>()
        enabledEngines.forEach { (name, func) ->
            results[name] = func(input)
        }
        
        val mlRes = results["ML Kit"]
        val pdRes = results["Paddle V3"]
        
        val finalAngle = mlRes?.angle ?: 0.0f
        
        return DeskewResult(
            angle = finalAngle.coerceIn(-20f, 20f), 
            mlAngle = finalAngle,
            mlTimeMs = mlRes?.timesMs?.sum() ?: 0L,
            paddleTimeMs = pdRes?.timesMs?.sum() ?: 0L,
            mlBlocks = mlRes?.blocks ?: emptyList(),
            paddleBlocks = pdRes?.blocks ?: emptyList(),
            engines = results
        )
    }

    private suspend fun deskewMlKit(input: Any): EngineResult {
        val tStart = System.currentTimeMillis()
        val targetBitmap = NativePaddleEngine.sharedBmp2048
        
        val (pWidth, pHeight, pScale) = prepDeskewBuffer(input, targetBitmap)
        val tPrep = System.currentTimeMillis() - tStart

        val t1 = System.currentTimeMillis()
        val res = extractFromPhotoBitmapRaw(com.google.mlkit.vision.common.InputImage.fromBitmap(targetBitmap, 0))
        val tDetect = System.currentTimeMillis() - t1
        
        val invScale = 1.0f / pScale
        val scaledBlocks = res.textBlocks.map { block ->
            val b = block.boundingBox
            val scaledRect = android.graphics.Rect(
                (b.left * invScale).toInt(),
                (b.top * invScale).toInt(),
                (b.right * invScale).toInt(),
                (b.bottom * invScale).toInt()
            )
            block.copy(boundingBox = scaledRect)
        }

        val srcH = (pHeight * invScale).toInt()
        val angle = calculateWeightedAverage(scaledBlocks, srcH)
        return EngineResult(angle, listOf(tPrep, tDetect), scaledBlocks)
    }

    private suspend fun deskewPaddle(input: Any): EngineResult {
        val tStart = System.currentTimeMillis()
        val paddleEngine = VehicleExpensesApplication.anchoredEngineV3 ?: return EngineResult(0f, listOf(0L))
        val targetBitmap = NativePaddleEngine.sharedBmp2048

        val (pWidth, pHeight, pScale) = prepDeskewBuffer(input, targetBitmap)
        val tPrep = System.currentTimeMillis() - tStart

        val t1 = System.currentTimeMillis()
        val det = paddleEngine.detect(targetBitmap, pWidth, pHeight)
        val tDetect = System.currentTimeMillis() - t1
        
        var angle = 0f
        var blocks = emptyList<TextBlock>()
        if (det != null) {
            blocks = processPaddleHeatmap(det.heatmap, det.width, det.height, pScale, targetBitmap, "Paddle")
            val srcH = (pHeight / pScale).toInt()
            angle = calculateWeightedAverage(blocks, srcH)
        }
        return EngineResult(angle, listOf(tPrep, tDetect), blocks)
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
        val validCandidates = candidates.filter { it.boundingBox.height() > 0 }
        if (validCandidates.isEmpty()) return 0f

        val heights = validCandidates.map { it.boundingBox.height().toFloat() / imgHeight.toFloat() }
        val roundedHeights = heights.map { Math.round(it / 0.005f) * 0.005f }
        val counts = roundedHeights.groupingBy { it }.eachCount()
        val threshold = Math.max(2, (validCandidates.size * 0.15).toInt())
        val peaks = counts.filter { it.value >= threshold }.keys
        
        val floor = if (peaks.isNotEmpty()) {
            peaks.minOrNull()!! / 2.0f
        } else {
            val sortedH = heights.sorted()
            sortedH[sortedH.size / 2] / 2.0f
        }

        val heightFiltered = validCandidates.filter { 
            (it.boundingBox.height().toFloat() / imgHeight.toFloat()) >= floor 
        }

        if (heightFiltered.isEmpty()) return 0f

        // RANSAC: Pick the most frequent angle (the consensus)
        // Group angles by +/- 0.5 degree bucket, normalized to [-45, 45]
        val buckets = heightFiltered.groupBy { Math.round(normalizeAngle(it.angle) * 2) / 2.0f }
        
        // Find bucket with the most support
        val bestBucket = buckets.maxByOrNull { it.value.size }
        
        // Return consensus angle from largest bucket
        return bestBucket?.key ?: 0f
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
        val src = if (bitmap.config == Bitmap.Config.ALPHA_8) bitmapToMatMono(bitmap) else {
            val m = Mat(); org.opencv.android.Utils.bitmapToMat(bitmap, m); m
        }
        val gray = Mat()
        if (src.channels() > 1) Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY) else src.copyTo(gray)
        val filtered = Mat()
        Imgproc.bilateralFilter(gray, filtered, 5, 75.0, 75.0)
        
        if (bitmap.config == Bitmap.Config.ALPHA_8) {
            matToBitmapMono(filtered, bitmap)
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
        val src = if (bitmap.config == Bitmap.Config.ALPHA_8) bitmapToMatMono(bitmap) else {
            val m = Mat(); org.opencv.android.Utils.bitmapToMat(bitmap, m); m
        }
        val gray = Mat()
        if (src.channels() > 1) Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY) else src.copyTo(gray)
        val filtered = Mat()
        Imgproc.bilateralFilter(gray, filtered, 5, 75.0, 75.0)

        // Phase 115: In-place update
        if (bitmap.config == Bitmap.Config.ALPHA_8) {
            matToBitmapMono(filtered, bitmap)
        } else {
            org.opencv.android.Utils.matToBitmap(filtered, bitmap)
        }
        src.release(); gray.release(); filtered.release(); return bitmap
    }
    fun applyContrastStretch(bitmap: Bitmap, floorPercentile: Int): Bitmap {
        val src = if (bitmap.config == Bitmap.Config.ALPHA_8) bitmapToMatMono(bitmap) else {
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
            matToBitmapMono(dst, bitmap)
        } else {
            org.opencv.android.Utils.matToBitmap(dst, bitmap)
        }
        src.release(); gray.release(); hist.release(); dst.release(); return bitmap
    }
    // Phase 115: CV_8UC1 Monochrome Bridge (Zero-Allocation)
    fun bitmapToMatMono(bitmap: Bitmap): Mat {
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

    fun matToBitmapMono(mat: Mat, bitmap: Bitmap) {
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

    /**
     * Phase 58: Multi-Column OCR Refinement.
     */
    private suspend fun runMlKitMonoNew(bmp: Bitmap, stageName: String): OcrStepResult {
        val targetW = 320; val targetH = 48
        
        // 1. Force-scale input to recognition dimensions
        val scaledBmp = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
        
        val meta = mutableMapOf<String, String>()
        meta["inputW"] = bmp.width.toString()
        meta["inputH"] = bmp.height.toString()
        meta["targetW"] = targetW.toString()
        meta["targetH"] = targetH.toString()
        
        // 2. NV21 Construction
        val frameSize = targetW * targetH
        val nv21 = ByteArray(frameSize * 3 / 2)
        val pixels = IntArray(frameSize)
        scaledBmp.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        
        for (i in 0 until frameSize) {
            nv21[i] = ((pixels[i] shr 16) and 0xFF).toByte()
        }
        for (i in frameSize until nv21.size) nv21[i] = 128.toByte()
        
        // 3. Diagnostic: Capture base64 of NV21 buffer
        meta["rawBufferBase64"] = android.util.Base64.encodeToString(nv21, android.util.Base64.NO_WRAP)
        
        return OcrStepResult(
            stageName = stageName,
            thumbB64 = OcrUtils.bitmapToBase64(scaledBmp),
            ocrInputB64 = meta["rawBufferBase64"],
            text = "DIAGNOSTIC",
            boxes = emptyList(),
            normalizedBoxes = emptyList(),
            rawBox = Rect(0,0,targetW,targetH),
            refinedBox = Rect(0,0,targetW,targetH),
            metadata = meta
        )
    }

    fun addPadding(bitmap: Bitmap, padding: Int, color: Int = Color.BLACK): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width + 2 * padding, bitmap.height + 2 * padding, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(color)
        canvas.drawBitmap(bitmap, padding.toFloat(), padding.toFloat(), null)
        return out
    }

    /**
     * Phase 63: Heatmap Post-Processing
     */
    fun processPaddleHeatmap(
        heatmap: FloatArray, w: Int, h: Int, scale: Float, 
        sourceBuffer: Any, algorithm: String = "Native"
    ): List<TextBlock> {
        val invScale = 1.0 / scale.toDouble()

        var maxHeat = 0f
        for (v in heatmap) { if (v > maxHeat) maxHeat = v }
        val maskThreshold = 0.20f
        Log.e("PADDLE_HEATMAP_DIAG", "Heatmap Max: $maxHeat, Threshold: $maskThreshold")

        val mask = Mat(h, w, CvType.CV_8U)
        val data = ByteArray(heatmap.size)
        for (i in heatmap.indices) {
            data[i] = if (heatmap[i] > maskThreshold) 255.toByte() else 0.toByte()
        }
        mask.put(0, 0, data)

        val contours = mutableListOf<org.opencv.core.MatOfPoint>()
        val hierarchy = Mat()
        val results = mutableListOf<TextBlock>()

        try {
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            for (contour in contours) {
                if (Imgproc.contourArea(contour) < 10) continue
                val p2f = org.opencv.core.MatOfPoint2f(*contour.toArray())
                val rotatedRect = Imgproc.minAreaRect(p2f)
                val points = arrayOf(org.opencv.core.Point(), org.opencv.core.Point(), org.opencv.core.Point(), org.opencv.core.Point())
                rotatedRect.points(points)
                p2f.release() 
                
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
            mask.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
        return results
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
                obj.put("cx", box.centerX().toDouble() / imgW.toDouble())
                obj.put("cy", box.centerY().toDouble() / imgH.toDouble())
                obj.put("w", box.width().toDouble() / imgW.toDouble())
                obj.put("h", box.height().toDouble() / imgH.toDouble())
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
                    obj.put("cx", block.boundingBox.centerX().toDouble() / res.imageWidth.toDouble())
                    obj.put("cy", block.boundingBox.centerY().toDouble() / res.imageHeight.toDouble())
                    obj.put("w", block.boundingBox.width().toDouble() / res.imageWidth.toDouble())
                    obj.put("h", block.boundingBox.height().toDouble() / res.imageHeight.toDouble())
                    obj.put("instance", instance)
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
                        val cx = obj.getDouble("cx")
                        val cy = obj.getDouble("cy")
                        val w = obj.getDouble("w")
                        val h = obj.getDouble("h")
                        val instanceId = if (obj.has("instance")) obj.getInt("instance") else -1
                        val left = ((cx - w / 2.0) * imgW).toInt()
                        val top = ((cy - h / 2.0) * imgH).toInt()
                        val right = ((cx + w / 2.0) * imgW).toInt()
                        val bottom = ((cy + h / 2.0) * imgH).toInt()
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
