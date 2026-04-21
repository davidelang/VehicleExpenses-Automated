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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import com.googlecode.tesseract.android.TessBaseAPI
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.Interpreter
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject

object OdometerOcrUtils {
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("OdometerOcr", "OpenCV initialization failed!")
        } else {
            Log.i("OdometerOcr", "OpenCV initialized successfully")
        }
    }

    data class DeskewResult(val angle: Float, val timeMs: Long)

    suspend fun calculateAverageTextAngle(bitmap: Bitmap): DeskewResult {
        val t0 = System.currentTimeMillis()
        val scale = 1500f / bitmap.width
        val scaled = Bitmap.createScaledBitmap(bitmap, 1500, (bitmap.height * scale).toInt(), true)
        val ocrResult = extractFromPhotoBitmap(scaled)
        scaled.recycle()
        val angles = ocrResult.textBlocks.map { it.angle }
        val elapsed = System.currentTimeMillis() - t0
        if (angles.isEmpty()) return DeskewResult(0f, elapsed)
        val sortedAngles = angles.sorted()
        return DeskewResult(sortedAngles[sortedAngles.size / 2], elapsed)
    }

    fun cleanLandmarkString(text: String): String {
        // Phase 32: Replace o/O with 0 and perform leading/trailing multi-char trim
        val mapped = text.replace('o', '0').replace('O', '0')
        val charsToTrim = charArrayOf(' ', '-', '.', '_', ',')
        return mapped.trim { it in charsToTrim }
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
        // MANDATE: Always apply Grayscale + Bilateral before any discovery/OCR
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
            OcrResult(
                engineName = "ML Kit",
                debugText = text.toString().trim(),
                textBlocks = blocks,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        } catch (e: Exception) {
            Log.e("OdometerOcr", "ML Kit failed", e)
            OcrResult(engineName = "ML Kit", debugText = "(ML Kit error: ${e.message})", imageWidth = bitmap.width, imageHeight = bitmap.height)
        } finally {
            processed.recycle()
        }
    }

    fun runMultiStepOcr(bitmap: Bitmap, context: Context): List<OcrStepResult> {
        val steps = mutableListOf<OcrStepResult>()
        
        // 1. Raw
        steps.add(OcrStepResult("Raw", bitmap, runOcr(bitmap)))

        // 2. Grayscale
        val gray = applyGrayscale(bitmap)
        steps.add(OcrStepResult("Grayscale", gray, runOcr(gray)))

        // 3. Bilateral
        val bile = applyBilateral(bitmap)
        steps.add(OcrStepResult("Bilateral", bile, runOcr(bile)))

        // 4. CLAHE
        val clahe = applyClahe(bitmap)
        steps.add(OcrStepResult("CLAHE", clahe, runOcr(clahe)))

        // 5. Otsu
        val otsu = applyOtsu(bitmap)
        steps.add(OcrStepResult("Otsu", otsu, runOcr(otsu)))

        return steps
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
            if (isFlipped) {
                Log.i("OdometerOcr", "v45_FLIP_RECOVERED: '${block.text}' -> '$cleaned' (Angle: ${block.angle})")
            }
            block.copy(text = cleaned)
        }
        return result.copy(
            textBlocks = refinedBlocks,
            debugText = refinedBlocks.joinToString(" ") { it.text }
        )
    }

    private fun clean7SegmentDigits(text: String, isFlipped: Boolean): String {
        val standardMap = mapOf('O' to '0', 'D' to '0', 'Q' to '0', 'U' to '0', 'Z' to '2', 'S' to '5', 'G' to '6', 'B' to '8', '!' to '1', 'I' to '1', 'l' to '1', '|' to '1')
        val flipMap = mapOf('O' to '0', 'D' to '0', 'L' to '7', 'V' to '7', 'h' to '4', 'H' to '4', 'E' to '3', 'G' to '9', 'B' to '8', 'S' to '5', '!' to '1', 'I' to '1', 'l' to '1', '|' to '1', 'A' to 'V')
        val workingText = if (isFlipped) text.reversed() else text
        val activeMap = if (isFlipped) flipMap else standardMap
        return workingText.map { char -> activeMap[char] ?: (activeMap[char.uppercaseChar()] ?: char) }.joinToString("")
    }

    // MANDATED: Normalized JSON Serialization
    fun serializeLandmarks(landmarks: List<TextBlock>, imgW: Int, imgH: Int): String {
        val array = JSONArray()
        // Filter: > 1 character as requested
        landmarks.forEach { block ->
            val cleaned = cleanLandmarkString(block.text)
            if (cleaned.length > 1) {
                val obj = JSONObject()
                obj.put("text", cleaned)
                // Save as NORMALIZED coordinates (0.0 to 1.0) explicitly as Double to prevent truncation
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

    /**
     * Consolidates landmarks from all 5 engines into a single JSON manifest.
     * Keyed by engine name.
     */
    fun serializeMultiEngineLandmarks(results: Map<String, OcrResult>): String {
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
        return root.toString()
    }

    // COMPATIBILITY WRAPPERS
    suspend fun extractFromPhoto(photoPath: String, cropRect: RectF? = null, context: Context? = null): OcrResult = withContext(Dispatchers.IO) {
        val rawBitmap = if (context != null) decodeBitmapSafely(context, photoPath) else BitmapFactory.decodeFile(photoPath)
        if (rawBitmap == null) return@withContext OcrResult(debugText = "Failed decode", originalPhotoPath = photoPath)
        val rotated = rotateImageIfRequired(rawBitmap, photoPath)
        
        // EXPERIMENT: Process FULL image after rotation
        val processed = applyBilateral(applyGrayscale(rotated))
        
        var bitmap = processed
        if (cropRect != null) {
            val left = (cropRect.left * processed.width).toInt().coerceIn(0, processed.width)
            val top = (cropRect.top * processed.height).toInt().coerceIn(0, processed.height)
            val right = (cropRect.right * processed.width).toInt().coerceAtMost(processed.width)
            val bottom = (cropRect.bottom * processed.height).toInt().coerceAtMost(processed.height)
            if (right > left && bottom > top) {
                bitmap = Bitmap.createBitmap(processed, left, top, right - left, bottom - top)
            }
        }
        val res = extractFromPhotoBitmap(bitmap)
        if (bitmap != processed) bitmap.recycle()
        processed.recycle()
        rotated.recycle()
        res.copy(originalPhotoPath = photoPath)
    }

    suspend fun discoverLandmarks(photoPath: String, odometerCrop: RectF? = null, otherTextCrop: RectF? = null): List<TextBlock> = withContext(Dispatchers.IO) {
        val rawBitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext emptyList()
        val rotated = rotateImageIfRequired(rawBitmap, photoPath)
        
        // EXPERIMENT: Process FULL image before landmark discovery
        val processed = applyBilateral(applyGrayscale(rotated))
        
        val ocrResult = extractFromPhotoBitmap(processed)
        val landmarks = processRawLandmarks(ocrResult.textBlocks, odometerCrop, otherTextCrop, processed.width, processed.height)
        
        processed.recycle()
        if (rotated != rawBitmap) rotated.recycle()
        rawBitmap.recycle()
        landmarks
    }
}
