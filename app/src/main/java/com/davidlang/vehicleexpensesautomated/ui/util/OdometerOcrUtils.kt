package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.runBlocking
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
    private var sharedPixelsBuffer: IntArray? = null
    private var sharedNv21Buffer: ByteArray? = null

    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("OdometerOcr", "OpenCV initialization failed!")
        } else {
            Log.i("OdometerOcr", "OpenCV initialized successfully")
        }
    }

    data class DeskewResult(val angle: Float, val mlAngle: Float, val mlTimeMs: Long, val paddleTimeMs: Long, val mlBlocks: List<TextBlock> = emptyList(), val paddleBlocks: List<TextBlock> = emptyList())

    fun calculateAverageTextAngle(
        sourceBitmap: Bitmap,
        targetBitmap: Bitmap,
        paddleEngine: NativePaddleEngine? = null
    ): DeskewResult {
        val t0 = System.currentTimeMillis()
        val pTargetSize = 2048
        val pScale = pTargetSize.toFloat() / sourceBitmap.width
        val pHeight = (sourceBitmap.height * pScale).toInt()

        val isMono = (targetBitmap === NativePaddleEngine.sharedBmp2048Mono)
        val canvas = if (isMono) NativePaddleEngine.sharedCanvas2048Mono else NativePaddleEngine.sharedCanvas2048
        
        synchronized(targetBitmap) {
            targetBitmap.eraseColor(0)
            val matrix = android.graphics.Matrix()
            matrix.postScale(pScale, pScale)
            canvas.drawBitmap(sourceBitmap, matrix, null)
        }
        
        val paddleResult = runBlocking { paddleEngine?.runDetectionOnly(targetBitmap, pTargetSize, pTargetSize) }
        val pdCandidates = mutableListOf<TextBlock>()
        paddleResult?.textBlocks?.forEach { block ->
            var a = block.angle
            if (Math.abs(a - 90f) < 45f) a -= 90f else if (Math.abs(a + 90f) < 45f) a += 90f else if (Math.abs(a - 180f) < 45f) a -= 180f else if (Math.abs(a + 180f) < 45f) a += 180f
            pdCandidates.add(block.copy(angle = a))
        }

        val paddleAngle = calculateWeightedAverage(pdCandidates, pHeight)
        val paddleTimeMs = System.currentTimeMillis() - t0
        return computeFinalDeskewAngle(pdCandidates, paddleAngle, targetBitmap, pHeight, paddleTimeMs)
    }

    private fun computeFinalDeskewAngle(
        pdCandidates: List<TextBlock>,
        paddleAngle: Float,
        bitmap: Bitmap,
        pHeight: Int,
        paddleTimeMs: Long
    ): DeskewResult {
        val tMl = System.currentTimeMillis()
        val mlOcr = extractFromPhotoBitmap(bitmap)
        val mlAngle = calculateWeightedAverage(mlOcr.textBlocks, bitmap.height)
        val mlTimeMs = System.currentTimeMillis() - tMl
        
        val finalAngle = if (pdCandidates.isNotEmpty()) paddleAngle else mlAngle
        return DeskewResult(finalAngle.coerceIn(-20f, 20f), mlAngle, mlTimeMs, paddleTimeMs, mlOcr.textBlocks, pdCandidates)
    }

    private fun calculateWeightedAverage(candidates: List<TextBlock>, imgHeight: Int): Float {
        if (candidates.isEmpty()) return 0f
        
        val heights = candidates.map { it.boundingBox.height().toFloat() / imgHeight.toFloat() }
        val roundedHeights = heights.map { Math.round(it / 0.005f) * 0.005f }
        val counts = roundedHeights.groupingBy { it }.eachCount()
        val threshold = Math.max(2, (candidates.size * 0.15).toInt())
        val peaks = counts.filter { it.value >= threshold }.keys
        
        val floor = if (peaks.isNotEmpty()) {
            peaks.minOrNull()!! / 2.0f
        } else {
            val sortedH = heights.sorted()
            sortedH[sortedH.size / 2] / 2.0f
        }

        val heightFiltered = candidates.filter { 
            (it.boundingBox.height().toFloat() / imgHeight.toFloat()) >= floor 
        }

        if (heightFiltered.isEmpty()) return 0f

        val angles = heightFiltered.map { it.angle }.sorted()
        val medianAngle = angles[angles.size / 2]
        
        val outlierFiltered = heightFiltered.filter { Math.abs(it.angle - medianAngle) <= 5.0f }
        
        if (outlierFiltered.isEmpty()) return medianAngle

        var sumAW = 0.0
        var sumW = 0.0
        for (b in outlierFiltered) {
            val w = b.boundingBox.width().toDouble()
            sumAW += b.angle * w
            sumW += w
        }
        return if (sumW > 0) (sumAW / sumW).toFloat() else medianAngle
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
        OpenCvBridge.useBitmapAsMat(bitmap) { mat ->
            if (mat.channels() > 1) {
                val gray = Mat()
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
                // Write back to the original 4-channel buffer
                Imgproc.cvtColor(gray, mat, Imgproc.COLOR_GRAY2RGBA)
                gray.release()
            }
        }
    }

    fun applyBilateralInPlace(bitmap: Bitmap) {
        OpenCvBridge.useBitmapAsMat(bitmap) { mat ->
            val gray = if (mat.channels() > 1) {
                val g = Mat()
                Imgproc.cvtColor(mat, g, Imgproc.COLOR_RGBA2GRAY)
                g
            } else mat
            
            val filtered = Mat()
            Imgproc.bilateralFilter(gray, filtered, 5, 75.0, 75.0)
            
            if (mat.channels() > 1) {
                Imgproc.cvtColor(filtered, mat, Imgproc.COLOR_GRAY2RGBA)
            } else {
                filtered.copyTo(mat)
            }

            filtered.release()
            if (gray !== mat) gray.release()
        }
    }

    fun applyContrastStretchInPlace(bitmap: Bitmap, floorPercentile: Int) {
        OpenCvBridge.useBitmapAsMat(bitmap) { mat ->
            val gray = if (mat.channels() > 1) {
                val g = Mat()
                Imgproc.cvtColor(mat, g, Imgproc.COLOR_RGBA2GRAY)
                g
            } else mat
            
            val hist = Mat()
            Imgproc.calcHist(Collections.singletonList(gray), MatOfInt(0), Mat(), hist, MatOfInt(256), MatOfFloat(0f, 256f))
            
            val totalPixels = gray.rows() * gray.cols()
            var floorBin = 0
            var ceilingBin = 255
            
            var sum = 0.0
            for (i in 0..255) {
                sum += hist.get(i, 0)[0]
                if (sum >= totalPixels * (floorPercentile / 100.0)) { floorBin = i; break }
            }
            
            sum = 0.0
            for (i in 0..255) {
                sum += hist.get(i, 0)[0]
                if (sum >= totalPixels * 0.98) { ceilingBin = i; break }
            }
            
            val alpha = if (ceilingBin > floorBin) 255.0 / (ceilingBin - floorBin) else 1.0
            val beta = -floorBin * alpha
            
            val stretched = Mat()
            gray.convertTo(stretched, CvType.CV_8U, alpha, beta)

            if (mat.channels() > 1) {
                Imgproc.cvtColor(stretched, mat, Imgproc.COLOR_GRAY2RGBA)
            } else {
                stretched.copyTo(mat)
            }
            
            hist.release()
            stretched.release()
            if (gray !== mat) gray.release()
        }
    }

    fun applyClaheInPlace(bitmap: Bitmap) {
        OpenCvBridge.useBitmapAsMat(bitmap) { mat ->
            val gray = if (mat.channels() > 1) {
                val g = Mat()
                Imgproc.cvtColor(mat, g, Imgproc.COLOR_RGBA2GRAY)
                g
            } else mat
            
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            val processed = Mat()
            clahe.apply(gray, processed)

            if (mat.channels() > 1) {
                Imgproc.cvtColor(processed, mat, Imgproc.COLOR_GRAY2RGBA)
            } else {
                processed.copyTo(mat)
            }
            
            processed.release()
            clahe.collectGarbage()
            if (gray !== mat) gray.release()
        }
    }

    fun applyOtsuInPlace(bitmap: Bitmap) {
        OpenCvBridge.useBitmapAsMat(bitmap) { mat ->
            val gray = if (mat.channels() > 1) {
                val g = Mat()
                Imgproc.cvtColor(mat, g, Imgproc.COLOR_RGBA2GRAY)
                g
            } else mat
            
            val threshed = Mat()
            Imgproc.threshold(gray, threshed, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

            if (mat.channels() > 1) {
                Imgproc.cvtColor(threshed, mat, Imgproc.COLOR_GRAY2RGBA)
            } else {
                threshed.copyTo(mat)
            }

            threshed.release()
            if (gray !== mat) gray.release()
        }
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

    fun extractFromPhotoBitmap(bitmap: Bitmap): OcrResult {
        // Use the shared working buffer to avoid allocation
        val workingBmp = NativePaddleEngine.sharedBmp2048Mono
        val workingCanvas = NativePaddleEngine.sharedCanvas2048Mono
        
        val processed = synchronized(workingBmp) {
            workingBmp.eraseColor(0)
            workingCanvas.drawBitmap(bitmap, 0f, 0f, null)
            applyBilateralInPlace(workingBmp)
            workingBmp
        }
        
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(processed, 0)
        return try {
            val visionText = runBlocking { recognizer.process(image).await() }
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
            OcrResult(engineName = "ML Kit", debugText = text.toString().trim(), textBlocks = blocks, imageWidth = bitmap.width, imageHeight = bitmap.height)
        } catch (e: Exception) {
            Log.e("OdometerOcr", "ML Kit failed", e)
            OcrResult(engineName = "ML Kit", debugText = "(ML Kit error: ${e.message})", imageWidth = bitmap.width, imageHeight = bitmap.height)
        }
    }

    /**
     * Phase 58: Multi-Column OCR Refinement.
     */
    suspend fun runMultiStepOcr(
        bitmap: Bitmap,
        context: Context,
        engineName: String = "ML Kit",
        targetHeight: Int? = null,
        paddleEngine: NativePaddleEngine? = null
    ): List<OcrStepResult> {
        val steps = mutableListOf<OcrStepResult>()
        val mlKitClient = if (engineName.startsWith("ML Kit")) TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) else null
        
        val workingBmp = NativePaddleEngine.sharedBmp2048Mono
        val workingCanvas = NativePaddleEngine.sharedCanvas2048Mono
        val paint = Paint()

        suspend fun prepare(filters: (Bitmap) -> Unit): Bitmap {
            synchronized(workingBmp) {
                workingBmp.eraseColor(0)
                workingCanvas.drawBitmap(bitmap, 0f, 0f, paint)
                filters(workingBmp)
            }
            return workingBmp
        }

        suspend fun exec(bmp: Bitmap, stageName: String, boxes: List<Rect> = emptyList()): OcrStepResult {
            val res = when (engineName) {
                "ML Kit", "ML Kit Mono" -> {
                    val scale = if (targetHeight != null) targetHeight.toFloat() / bmp.height.toFloat() else 1.0f
                    val resized = if (targetHeight != null) {
                        Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), targetHeight, true)
                    } else bmp
                    
                    val image = if (engineName == "ML Kit Mono") {
                        val w = resized.width
                        val h = resized.height
                        val nv21 = OcrUtils.bitmapToNv21(resized)
                        val buffer = java.nio.ByteBuffer.wrap(nv21)
                        InputImage.fromByteBuffer(buffer, w, h, 0, InputImage.IMAGE_FORMAT_NV21)
                    } else {
                        InputImage.fromBitmap(resized, 0)
                    }
                    
                    try {
                        val visionText = mlKitClient!!.process(image).await()
                        val resBuilder = java.lang.StringBuilder()
                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                val isFlipped = Math.abs(line.angle) > 135f
                                val cleanedText = clean7SegmentDigits(line.text, isFlipped).filter { it.isDigit() }
                                if (cleanedText.isNotBlank()) {
                                    resBuilder.append(cleanedText)
                                }
                            }
                        }
                        val resStr = resBuilder.toString()
                        val detBoxes = visionText.textBlocks.flatMap { block ->
                            block.lines.flatMap { line ->
                                line.elements.mapNotNull { element ->
                                    element.boundingBox?.let { b ->
                                        if (targetHeight != null) {
                                            Rect((b.left / scale).toInt(), (b.top / scale).toInt(), (b.right / scale).toInt(), (b.bottom / scale).toInt())
                                        } else b
                                    }
                                }
                            }
                        }
                        if (resized != bmp) resized.recycle()
                        Triple(if (resStr.isNotBlank()) resStr else null, detBoxes, null as String?)
                    } catch (e: Exception) { Triple(null, emptyList(), null as String?) }
                }
                "Paddle-Lite", "Paddle V2 Greedy", "Paddle V3 Greedy" -> {
                    paddleEngine?.let {
                        val ocrRes = paddleEngine.recognize(bmp)
                        Triple(ocrRes.debugText, emptyList<Rect>(), ocrRes.metadata["ocrInput"])
                    } ?: Triple(null, emptyList<Rect>(), null)
                }
                else -> Triple(null, emptyList<Rect>(), null)
            }
            
            // Phase 63: Immediate Snapshot (Zero-Allocation)
            val b64 = OcrUtils.takeSnapshot(bmp, consolidatedRows = res.second)
            val box = res.second.firstOrNull() ?: Rect(0,0,bmp.width,bmp.height)

            return OcrStepResult(
                stageName = stageName,
                thumbB64 = b64,
                ocrInputB64 = res.third,
                text = res.first,
                boxes = res.second,
                rawBox = box,
                refinedBox = box
            )
        }

        // 1. Raw
        steps.add(exec(prepare { }, "Raw"))
        
        // 2. 80% Stretch Only
        steps.add(exec(prepare { applyContrastStretchInPlace(it, 80) }, "80% Stretch Only"))
        
        // 3. Bile -> 80% Stretch
        steps.add(exec(prepare { 
            applyBilateralInPlace(it)
            applyContrastStretchInPlace(it, 80)
        }, "Bile -> 80% Stretch"))
        
        // 4. 80% Stretch -> Bile
        steps.add(exec(prepare {
            applyContrastStretchInPlace(it, 80)
            applyBilateralInPlace(it)
        }, "80% Stretch -> Bile"))
        
        if (mlKitClient != null) mlKitClient.close()
        return steps
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
        sourceBitmap: Bitmap, algorithm: String = "Native"
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
            
            OpenCvBridge.useBitmapAsMat(sourceBitmap) { sourceMat ->
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

    private fun clean7SegmentDigits(text: String, isFlipped: Boolean): String {
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
            val array = JSONArray()
            
            // Pass 1: Count total occurrences to determine uniqueness (Phase 109)
            val globalCounts = res.textBlocks.groupBy { cleanLandmarkString(it.text) }.mapValues { it.value.size }
            
            val runningCounts = mutableMapOf<String, Int>()
            res.textBlocks.forEach { block ->
                val cleaned = cleanLandmarkString(block.text)
                if (cleaned.length > 1) {
                    val total = globalCounts[cleaned] ?: 0
                    val instance = if (total == 1) 0 else {
                        val count = (runningCounts[cleaned] ?: 0) + 1
                        runningCounts[cleaned] = count
                        count
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
                    val obj = array.getJSONObject(i)
                    val cx = obj.getDouble("cx")
                    val cy = obj.getDouble("cy")
                    val w = obj.getDouble("w")
                    val h = obj.getDouble("h")
                    val instanceId = obj.optInt("instance", -1)
                    val left = ((cx - w / 2.0) * imgW).toInt()
                    val top = ((cy - h / 2.0) * imgH).toInt()
                    val right = ((cx + w / 2.0) * imgW).toInt()
                    val bottom = ((cy + h / 2.0) * imgH).toInt()
                    blocks.add(TextBlock(obj.getString("text"), android.graphics.Rect(left, top, right, bottom), instanceId = instanceId))
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
        if (bitmap != processed) bitmap.recycle()
        processed.recycle(); rotated.recycle(); res.copy(originalPhotoPath = photoPath)
    }

    suspend fun discoverLandmarks(photoPath: String, odometerCrop: RectF? = null, otherTextCrop: RectF? = null): List<TextBlock> = withContext(Dispatchers.IO) {
        val rawBitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext emptyList()
        val rotated = rotateImageIfRequired(rawBitmap, photoPath)
        val processed = applyBilateral(rotated)
        val ocrResult = extractFromPhotoBitmap(processed)
        val landmarks = processRawLandmarks(ocrResult.textBlocks, odometerCrop, otherTextCrop, processed.width, processed.height)
        processed.recycle(); if (rotated != rawBitmap) rotated.recycle(); rawBitmap.recycle(); landmarks
    }

    fun rotateBitmapInPlace(bitmap: Bitmap, degrees: Float) {
        if (degrees == 0f) return
        OpenCvBridge.useBitmapAsMat(bitmap) { mat ->
            val center = org.opencv.core.Point(mat.cols() / 2.0, mat.rows() / 2.0)
            val rotMat = Imgproc.getRotationMatrix2D(center, degrees.toDouble(), 1.0)
            val rotated = Mat()
            Imgproc.warpAffine(mat, rotated, rotMat, mat.size(), Imgproc.INTER_LINEAR + Imgproc.WARP_FILL_OUTLIERS)
            rotated.copyTo(mat)
            rotMat.release(); rotated.release()
        }
    }

    suspend fun extractFromPhotoBitmapRaw(bitmap: Bitmap): OcrResult {
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
        return try {
            val visionText = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
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
            OcrResult(engineName = "ML Kit", debugText = text.toString().trim(), textBlocks = blocks, imageWidth = bitmap.width, imageHeight = bitmap.height)
        } catch (e: Exception) {
            OcrResult(engineName = "ML Kit", debugText = "(ML Kit error: ${e.message})", imageWidth = bitmap.width, imageHeight = bitmap.height)
        }
    }

}