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
import com.googlecode.tesseract.android.TessBaseAPI
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
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("OdometerOcr", "OpenCV initialization failed!")
        } else {
            Log.i("OdometerOcr", "OpenCV initialized successfully")
        }
    }

    data class DeskewResult(val angle: Float, val timeMs: Long, val mlBlocks: List<TextBlock> = emptyList(), val paddleBlocks: List<TextBlock> = emptyList())

    suspend fun calculateAverageTextAngle(bitmap: Bitmap, paddleEngine: NativePaddleEngine? = null): DeskewResult {
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
        val pTargetSize = 2048
        val pScale = pTargetSize.toFloat() / bitmap.width
        val pHeight = (bitmap.height * pScale).toInt()
        val baselineBmp = Bitmap.createScaledBitmap(bitmap, pTargetSize, pHeight, true)

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

        // 2. ML Kit Recognition at 1500px (Stable fallback)
        val mScale = 1500f / bitmap.width
        val mScaled = Bitmap.createScaledBitmap(bitmap, 1500, (bitmap.height * mScale).toInt(), true)
        
        // --- ML KIT INDEPENDENT ALGORITHM (Fallback) ---
        val mlResult = extractFromPhotoBitmap(mScaled)
        mScaled.recycle()
        
        val mlCandidates = mlResult.textBlocks.filter { it.text.length > 1 }.map { block ->
            var a = block.angle
            if (a > 90f) a -= 180f
            else if (a < -90f) a += 180f
            block.copy(angle = a)
        }
        
        val mHeight = (bitmap.height * mScale).toInt()
        val mlAngle = calculateWeightedAverage(mlCandidates, mHeight)

        baselineBmp.recycle()
        val elapsed = System.currentTimeMillis() - t0
        // Trust ML Kit by default while Paddle detection is being refined
        val finalAngle = mlAngle
        
        // Return both lists explicitly so they can be logged without overwriting
        return DeskewResult(finalAngle.coerceIn(-20f, 20f), elapsed, mlCandidates, pdCandidates)
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

    fun runRawOcr(bitmap: Bitmap, whitelist: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"): Pair<String, List<TextBlock>> {
        val tess = TessBaseAPI()
        val blocks = mutableListOf<TextBlock>()
        try {
            val tessDataPath = "/data/user/0/com.davidlang.vehicleexpensesautomated/files"
            if (!tess.init(tessDataPath, "eng")) {
                tess.clear()
                return "(Tesseract init failed)" to emptyList()
            }
            tess.setVariable("tessedit_char_whitelist", whitelist)
            tess.setImage(bitmap)
            val text = tess.utF8Text ?: ""
            val resultIterator = tess.resultIterator
            if (resultIterator != null) {
                resultIterator.begin()
                do {
                    val hunk = resultIterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    val box = resultIterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    if (hunk != null && box != null) {
                        blocks.add(TextBlock(hunk, box))
                    }
                } while (resultIterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            }
            tess.clear()
            return text to blocks
        } catch (e: Exception) {
            Log.e("OdometerOcr", "Tesseract failed", e)
            return "(Tesseract error: ${e.message})" to emptyList()
        } finally {
            tess.recycle()
        }
    }

    fun runOcr(bitmap: Bitmap): String {
        return runRawOcr(bitmap, "0123456789").first
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
        val processed = applyBilateral(applyGrayscale(bitmap))
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
        val mlKitClient = if (engineName == "ML Kit") TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) else null

        suspend fun exec(bmp: Bitmap): Pair<String?, List<Rect>> {
            return when (engineName) {
                "ML Kit" -> {
                    val scale = if (targetHeight != null) targetHeight.toFloat() / bmp.height.toFloat() else 1.0f
                    val resized = if (targetHeight != null) {
                        Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), targetHeight, true)
                    } else bmp
                    val image = InputImage.fromBitmap(resized, 0)
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
                        val res = resBuilder.toString()
                        val boxes = visionText.textBlocks.flatMap { block ->
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
                        Pair(if (res.isNotBlank()) res else null, boxes)
                    } catch (e: Exception) { Pair(null, emptyList()) }
                }
                "Paddle-Lite", "Paddle V2 Greedy", "Paddle V3 Greedy" -> {
                    paddleEngine?.let {
                        val res = NativePaddleEngine.runConstrainedStatic(bmp, targetHeight ?: bmp.height, it.getDictionary(), it.isV3())
                        Pair(res, emptyList())
                    } ?: Pair(null, emptyList())
                }
                else -> Pair(runOcr(bmp), emptyList())
            }
        }

        val paint = Paint().apply { 
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 2f 
        }

        // 1. Raw (Fresh Copy)
        val raw = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val res1 = exec(raw)
        if (engineName == "ML Kit") { 
            val canvas = Canvas(raw)
            res1.second.forEach { canvas.drawRect(it, paint) } 
        }
        val box1 = res1.second.firstOrNull() ?: Rect(0,0,raw.width,raw.height)
        steps.add(OcrStepResult("Raw", raw, res1.first, res1.second, rawBox = box1, refinedBox = box1))

        // 2. Grayscale
        val gray = applyGrayscale(bitmap)
        val res2 = exec(gray)
        if (engineName == "ML Kit") { 
            val canvas = Canvas(gray)
            res2.second.forEach { canvas.drawRect(it, paint) } 
        }
        val box2 = res2.second.firstOrNull() ?: Rect(0,0,gray.width,gray.height)
        steps.add(OcrStepResult("Grayscale", gray, res2.first, res2.second, rawBox = box2, refinedBox = box2))

        // 3. Bilateral
        val bile = applyBilateral(bitmap)
        val res3 = exec(bile)
        if (engineName == "ML Kit") { 
            val canvas = Canvas(bile)
            res3.second.forEach { canvas.drawRect(it, paint) } 
        }
        val box3 = res3.second.firstOrNull() ?: Rect(0,0,bile.width,bile.height)
        steps.add(OcrStepResult("Bilateral", bile, res3.first, res3.second, rawBox = box3, refinedBox = box3))

        // 4. Enhanced (75% Stretch)
        val s75 = applyContrastStretch(bile, 75)
        val res4 = exec(s75)
        if (engineName == "ML Kit") {
            val canvas = Canvas(s75)
            res4.second.forEach { canvas.drawRect(it, paint) }
        }
        val box4 = res4.second.firstOrNull() ?: Rect(0,0,s75.width,s75.height)
        steps.add(OcrStepResult("Enhanced (75% Stretch)", s75, res4.first, res4.second, rawBox = box4, refinedBox = box4))

        // 5. Enhanced (80% Stretch)
        val s80 = applyContrastStretch(bile, 80)
        val res5 = exec(s80)
        if (engineName == "ML Kit") {
            val canvas = Canvas(s80)
            res5.second.forEach { canvas.drawRect(it, paint) }
        }
        val box5 = res5.second.firstOrNull() ?: Rect(0,0,s80.width,s80.height)
        steps.add(OcrStepResult("Enhanced (80% Stretch)", s80, res5.first, res5.second, rawBox = box5, refinedBox = box5))

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
                p2f.release() // CRITICAL: Prevent native memory leak
                
                val bounds = android.graphics.Rect(
                    (rotatedRect.boundingRect().x * invScale).toInt(),
                    (rotatedRect.boundingRect().y * invScale).toInt(),
                    ((rotatedRect.boundingRect().x + rotatedRect.boundingRect().width) * invScale).toInt(),
                    ((rotatedRect.boundingRect().y + rotatedRect.boundingRect().height) * invScale).toInt()
                )
                
                results.add(TextBlock("", bounds, rotatedRect.angle.toFloat()))
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
            res.textBlocks.forEach { block ->
                val cleaned = cleanLandmarkString(block.text)
                if (cleaned.length > 1) {
                    val obj = JSONObject()
                    obj.put("text", cleaned)
                    obj.put("cx", block.boundingBox.centerX().toDouble() / res.imageWidth.toDouble())
                    obj.put("cy", block.boundingBox.centerY().toDouble() / res.imageHeight.toDouble())
                    obj.put("w", block.boundingBox.width().toDouble() / res.imageWidth.toDouble())
                    obj.put("h", block.boundingBox.height().toDouble() / res.imageHeight.toDouble())
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
                    val left = ((cx - w / 2.0) * imgW).toInt()
                    val top = ((cy - h / 2.0) * imgH).toInt()
                    val right = ((cx + w / 2.0) * imgW).toInt()
                    val bottom = ((cy + h / 2.0) * imgH).toInt()
                    blocks.add(TextBlock(obj.getString("text"), android.graphics.Rect(left, top, right, bottom)))
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
        val processed = applyBilateral(applyGrayscale(rotated))
        val ocrResult = extractFromPhotoBitmap(processed)
        val landmarks = processRawLandmarks(ocrResult.textBlocks, odometerCrop, otherTextCrop, processed.width, processed.height)
        processed.recycle(); if (rotated != rawBitmap) rotated.recycle(); rawBitmap.recycle(); landmarks
    }
}
