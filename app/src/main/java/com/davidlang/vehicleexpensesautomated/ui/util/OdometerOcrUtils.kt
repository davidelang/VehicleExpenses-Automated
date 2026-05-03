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

    data class DeskewResult(
        val angle: Float, 
        val mlAngle: Float,
        val mlTimeMs: Long, 
        val paddleTimeMs: Long, 
        val mlBlocks: List<TextBlock> = emptyList(), 
        val paddleBlocks: List<TextBlock> = emptyList()
    )

    suspend fun calculateAverageTextAngle(bitmap: Bitmap, paddleEngine: NativePaddleEngine? = null, useMono: Boolean = false): DeskewResult {
        val t0 = System.currentTimeMillis()
        
        fun calculateWeightedAverage(candidates: List<TextBlock>, imgHeight: Int): Float {
            if (candidates.isEmpty()) return 0f
            
            // 1. Height Spike Filter
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

            // 2. Outlier Removal (Median Deviation)
            val angles = heightFiltered.map { it.angle }.sorted()
            val medianAngle = angles[angles.size / 2]
            
            val outlierFiltered = heightFiltered.filter { Math.abs(it.angle - medianAngle) <= 5.0f }
            
            if (outlierFiltered.isEmpty()) return medianAngle

            // 3. True Width-Weighted Average
            var sumAW = 0.0
            var sumW = 0.0
            for (b in outlierFiltered) {
                val w = b.boundingBox.width().toDouble()
                sumAW += b.angle * w
                sumW += w
            }
            return if (sumW > 0) (sumAW / sumW).toFloat() else medianAngle
        }

        // 1. Paddle Detection at 2048px (Maximum precision for forensic alignment)
        val tPaddleStart = System.currentTimeMillis()
        val pTargetSize = 2048
        val pScale = pTargetSize.toFloat() / bitmap.width
        val pHeight = (bitmap.height * pScale).toInt()
        
        // Zero-Allocation Scaling onto Shared 2048 buffer (Standard or Mono)
        val baselineBmp = if (useMono) NativePaddleEngine.sharedBmp2048Mono else NativePaddleEngine.sharedBmp2048
        val canvas = if (useMono) NativePaddleEngine.sharedCanvas2048Mono else NativePaddleEngine.sharedCanvas2048
        
        canvas.drawColor(Color.BLACK)
        NativePaddleEngine.sharedMatrix.reset()
        NativePaddleEngine.sharedMatrix.postScale(pScale, pScale)
        canvas.drawBitmap(bitmap, NativePaddleEngine.sharedMatrix, null)

        // --- PADDLE INDEPENDENT WEIGHTED AVERAGE ---
        val paddleResult = paddleEngine?.runDetectionOnly(baselineBmp, pTargetSize, pTargetSize)
        val pdCandidates = mutableListOf<TextBlock>()
        paddleResult?.textBlocks?.forEach { block ->
            var a = block.angle
            // Normalize: Map sides-as-bottom back to relative tilt
            if (Math.abs(a - 90f) < 45f) a -= 90f
            else if (Math.abs(a + 90f) < 45f) a += 90f
            else if (Math.abs(a - 180f) < 45f) a -= 180f
            else if (Math.abs(a + 180f) < 45f) a += 180f
            
            pdCandidates.add(block.copy(angle = a))
        }

        val paddleAngle = calculateWeightedAverage(pdCandidates, pHeight)
        val paddleTimeMs = System.currentTimeMillis() - tPaddleStart

        // 2. ML Kit Parallel Deskew
        val tMlStart = System.currentTimeMillis()
        val mlOcr = extractFromPhotoBitmap(baselineBmp)
        val mlCandidates = mlOcr.textBlocks
        val mlAngle = calculateWeightedAverage(mlCandidates, pHeight)
        val mlTimeMs = System.currentTimeMillis() - tMlStart

        baselineBmp.recycle()
        // Final result: Trust ML Kit for deskew
        val finalAngle = mlAngle
        
        // Return results for benchmarking
        return DeskewResult(finalAngle.coerceIn(-20f, 20f), mlAngle, mlTimeMs, paddleTimeMs, mlCandidates, pdCandidates)
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

    fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)
        val out = Bitmap.createBitmap(gray.cols(), gray.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(gray, out)
        mat.release(); gray.release()
        return out
    }

    fun applyBilateral(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        if (mat.channels() > 1) Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY) else mat.copyTo(gray)
        val filtered = Mat()
        Imgproc.bilateralFilter(gray, filtered, 5, 75.0, 75.0)
        val out = Bitmap.createBitmap(filtered.cols(), filtered.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(filtered, out)
        mat.release(); gray.release(); filtered.release()
        return out
    }

    fun applyContrastStretch(bitmap: Bitmap, floorPercentile: Int): Bitmap {
        val src = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, src)
        val gray = Mat()
        if (src.channels() > 1) Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        else src.copyTo(gray)
        
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
        
        val dst = Mat()
        val alpha = if (ceilingBin > floorBin) 255.0 / (ceilingBin - floorBin) else 1.0
        val beta = -floorBin * alpha
        gray.convertTo(dst, CvType.CV_8U, alpha, beta)
        
        val outBmp = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(dst, outBmp)
        
        src.release(); gray.release(); hist.release(); dst.release()
        return outBmp
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
        val processed = applyBilateral(bitmap)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(processed, 0)
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
            OcrResult(engineName = "ML Kit", debugText = text.toString().trim(), textBlocks = blocks, imageWidth = bitmap.width, imageHeight = bitmap.height)
        } catch (e: Exception) {
            Log.e("OdometerOcr", "ML Kit failed", e)
            OcrResult(engineName = "ML Kit", debugText = "(ML Kit error: ${e.message})", imageWidth = bitmap.width, imageHeight = bitmap.height)
        } finally {
            processed.recycle()
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
                        val frameSize = w * h
                        val nv21Size = frameSize * 3 / 2
                        
                        if (sharedPixelsBuffer == null || sharedPixelsBuffer!!.size < frameSize) {
                            sharedPixelsBuffer = IntArray(frameSize)
                        }
                        if (sharedNv21Buffer == null || sharedNv21Buffer!!.size < nv21Size) {
                            sharedNv21Buffer = ByteArray(nv21Size)
                        }
                        
                        val pixels = sharedPixelsBuffer!!
                        val nv21 = sharedNv21Buffer!!
                        
                        resized.getPixels(pixels, 0, w, 0, 0, w, h)
                        
                        // Extract Luminance (Y) channel. Input is grayscale, so R=G=B. We take R.
                        for (i in 0 until frameSize) {
                            val r = (pixels[i] shr 16) and 0xFF
                            nv21[i] = r.toByte()
                        }
                        
                        // Fill U/V channels with neutral chroma (128)
                        for (i in frameSize until nv21Size) {
                            nv21[i] = 128.toByte()
                        }
                        
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

        // Preprocessing Overhaul: Test filter combinations on Monochrome Baseline
        
        // 1. Raw (Monochrome Baseline)
        steps.add(exec(bitmap, "Raw"))
        
        // 2. 80% Stretch Only
        val s80Only = applyContrastStretch(bitmap, 80)
        steps.add(exec(s80Only, "80% Stretch Only"))
        
        // 3. Bile -> 80% Stretch
        val bileBase = applyBilateral(bitmap)
        val bileThen80 = applyContrastStretch(bileBase, 80)
        steps.add(exec(bileThen80, "Bile -> 80% Stretch"))
        
        // 4. 80% Stretch -> Bile
        val stretchBase = applyContrastStretch(bitmap, 80)
        val stretchThenBile = applyBilateral(stretchBase)
        steps.add(exec(stretchThenBile, "80% Stretch -> Bile"))
        
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
        val sourceMat = Mat()
        val results = mutableListOf<TextBlock>()

        try {
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            org.opencv.android.Utils.bitmapToMat(sourceBitmap, sourceMat)

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
            sourceMat.release()
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
            val textCounts = mutableMapOf<String, Int>()
            res.textBlocks.forEach { block ->
                val cleaned = cleanLandmarkString(block.text)
                if (cleaned.length > 1) {
                    val instance = (textCounts[cleaned] ?: 0) + 1
                    textCounts[cleaned] = instance

                    val obj = JSONObject()
                    obj.put("text", cleaned)
                    obj.put("cx", block.boundingBox.centerX().toDouble() / res.imageWidth.toDouble())
                    obj.put("cy", block.boundingBox.centerY().toDouble() / res.imageHeight.toDouble())
                    obj.put("w", block.boundingBox.width().toDouble() / res.imageWidth.toDouble())
                    obj.put("h", block.boundingBox.height().toDouble() / res.imageHeight.toDouble())
                    obj.put("instance", instance) // Phase 71: Disambiguation ID
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
                    val obj = array.getJSONObject(i)
                    val cx = obj.getDouble("cx")
                    val cy = obj.getDouble("cy")
                    val w = obj.getDouble("w")
                    val h = obj.getDouble("h")
                    val instanceId = obj.optInt("instance", 0) // Phase 71
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
}
