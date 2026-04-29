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
import org.opencv.imgproc.Imgproc

object OdometerOcrUtils {
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("OdometerOcr", "OpenCV initialization failed!")
        } else {
            Log.i("OdometerOcr", "OpenCV initialized successfully")
        }
    }

    data class DeskewResult(val angle: Float, val timeMs: Long, val rawBlocks: List<TextBlock> = emptyList())

    suspend fun calculateAverageTextAngle(bitmap: Bitmap): DeskewResult {
        val t0 = System.currentTimeMillis()
        val scale = 1500f / bitmap.width
        val scaled = Bitmap.createScaledBitmap(bitmap, 1500, (bitmap.height * scale).toInt(), true)
        val ocrResult = extractFromPhotoBitmap(scaled)
        scaled.recycle()
        
        val elapsed = System.currentTimeMillis() - t0
        // Robust Filtering: Ignore very small blocks and noise
        val candidates = ocrResult.textBlocks.filter { it.text.length > 1 && it.boundingBox.width() > 10 }
        if (candidates.isEmpty()) return DeskewResult(0f, elapsed, ocrResult.textBlocks)
        
        // Width-Weighted Median Calculation
        // 1. Sort by angle
        val sorted = candidates.sortedBy { it.angle }
        // 2. Total weight
        val totalWeight = sorted.sumOf { it.boundingBox.width().toDouble() }
        if (totalWeight == 0.0) return DeskewResult(0f, elapsed, candidates)
        
        // 3. Find the angle where cumulative weight reaches 50%
        var cumulativeWeight = 0.0
        var weightedMedianAngle = 0f
        for (block in sorted) {
            cumulativeWeight += block.boundingBox.width().toDouble()
            if (cumulativeWeight >= totalWeight / 2.0) {
                weightedMedianAngle = block.angle
                break
            }
        }
        
        // Phase 62: Rotational Gating. Cap the deskew to reasonable limits to prevent "spinning" on noise.
        val finalAngle = weightedMedianAngle.coerceIn(-20f, 20f)
        return DeskewResult(finalAngle, elapsed, candidates)
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
        steps.add(OcrStepResult("Raw", raw, res1.first, res1.second))

        // 2. Grayscale
        val gray = applyGrayscale(bitmap)
        val res2 = exec(gray)
        if (engineName == "ML Kit") { 
            val canvas = Canvas(gray)
            res2.second.forEach { canvas.drawRect(it, paint) } 
        }
        steps.add(OcrStepResult("Grayscale", gray, res2.first, res2.second))

        // 3. Bilateral
        val bile = applyBilateral(bitmap)
        val res3 = exec(bile)
        if (engineName == "ML Kit") { 
            val canvas = Canvas(bile)
            res3.second.forEach { canvas.drawRect(it, paint) } 
        }
        steps.add(OcrStepResult("Bilateral", bile, res3.first, res3.second))

        // 4. Enhanced (75% Stretch)
        val s75 = applyContrastStretch(bile, 75)
        val res4 = exec(s75)
        if (engineName == "ML Kit") {
            val canvas = Canvas(s75)
            res4.second.forEach { canvas.drawRect(it, paint) }
        }
        steps.add(OcrStepResult("Enhanced (75% Stretch)", s75, res4.first, res4.second))

        // 5. Enhanced (80% Stretch)
        val s80 = applyContrastStretch(bile, 80)
        val res5 = exec(s80)
        if (engineName == "ML Kit") {
            val canvas = Canvas(s80)
            res5.second.forEach { canvas.drawRect(it, paint) }
        }
        steps.add(OcrStepResult("Enhanced (80% Stretch)", s80, res5.first, res5.second))

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
