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
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.Collections

object OdometerOcrUtils {
    private val odoGrayU8 = Mat()
    private val odoFiltU8 = Mat()

    private fun standingU8(m: Mat, h: Int, w: Int): Mat? {
        if (h < 1 || w < 1) return null
        if (m.empty() || m.type() != CvType.CV_8UC1 || m.rows() < h || m.cols() < w) {
            m.create(h, w, CvType.CV_8UC1)
        }
        if (m.empty()) return null
        return m.submat(0, h, 0, w)
    }

    private fun wrapBitmapToGrayU8(bitmap: Bitmap, dest: Mat): Boolean {
        val h = bitmap.height
        val w = bitmap.width
        val crop = standingU8(dest, h, w) ?: return false
        try {
            val gray = ByteArray(w * h)
            if (bitmap.config == Bitmap.Config.ALPHA_8) {
                bitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(gray))
            } else {
                val argb = IntArray(w * h)
                bitmap.getPixels(argb, 0, w, 0, 0, w, h)
                for (i in argb.indices) {
                    val p = argb[i]
                    val r = (p ushr 16) and 0xff
                    val g = (p ushr 8) and 0xff
                    val b = p and 0xff
                    gray[i] = ((77 * r + 150 * g + 29 * b) shr 8).toByte()
                }
            }
            crop.put(0, 0, gray)
            return true
        } finally {
            crop.release()
        }
    }

    private fun grayU8ToBitmap(src: Mat, bitmap: Bitmap) {
        val h = bitmap.height
        val w = bitmap.width
        val crop = src.submat(0, h, 0, w)
        val gray = ByteArray(w * h)
        crop.get(0, 0, gray)
        crop.release()
        if (bitmap.config == Bitmap.Config.ALPHA_8) {
            bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(gray))
        } else {
            val argb = IntArray(w * h)
            for (i in gray.indices) {
                val g = gray[i].toInt() and 0xff
                argb[i] = (0xff shl 24) or (g shl 16) or (g shl 8) or g
            }
            bitmap.setPixels(argb, 0, w, 0, 0, w, h)
        }
    }
    data class HistStats(val intensityLow: Double, val intensityHigh: Double, val p80: Double, val rawBins: FloatArray)


    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("OdometerOcr", "OpenCV initialization failed!")
        } else {
            Log.i("OdometerOcr", "OpenCV initialized successfully")
        }
    }


    fun cleanLandmarkString(text: String): String {
        val filtered = text.filter { it.code in 32..126 }
        val charsToTrim = charArrayOf(' ', '-', '.', '_', ',', '*')
        return filtered.trim { it in charsToTrim }
    }

    fun processRawLandmarks(
        allBlocks: List<TextBlock>,
        odometerCrop: android.graphics.RectF? = null,
        imgWidth: Int,
        imgHeight: Int
    ): List<TextBlock> {
        val filtered = allBlocks.filter { block ->
            !OcrUtils.isBlockInCrop(block, odometerCrop, imgWidth, imgHeight)
        }
        return filtered.map { block ->
            block.copy(text = cleanLandmarkString(block.text))
        }.filter { it.text.isNotEmpty() }.sortedBy { it.text }
    }


    fun applyGrayscale(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ALPHA_8) return bitmap
        if (!wrapBitmapToGrayU8(bitmap, odoGrayU8)) return bitmap
        grayU8ToBitmap(odoGrayU8, bitmap)
        return bitmap
    }

    @Suppress("UNUSED_PARAMETER")
    fun applyBilateral(bitmap: Bitmap, argbScratch: Bitmap? = null): Bitmap {
        val h = bitmap.height
        val w = bitmap.width
        if (!wrapBitmapToGrayU8(bitmap, odoGrayU8)) return bitmap
        val src = standingU8(odoGrayU8, h, w) ?: return bitmap
        val dst = standingU8(odoFiltU8, h, w) ?: run {
            src.release()
            return bitmap
        }
        Imgproc.bilateralFilter(src, dst, 5, 75.0, 75.0)
        src.release()
        grayU8ToBitmap(odoFiltU8, bitmap)
        dst.release()
        return bitmap
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


    fun isBlockInCrop(block: TextBlock, crop: android.graphics.RectF?, w: Int, h: Int): Boolean {
        return OcrUtils.isBlockInCrop(block, crop, w, h)
    }

    fun rotateImageIfRequired(context: Context, bitmap: Bitmap, path: String): Bitmap {
        try {
            val orientation = if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(path))?.use { stream ->
                    android.media.ExifInterface(stream).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            } else {
                android.media.ExifInterface(path).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL,
                )
            }
            val degrees = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bitmap
            return rotateBitmap(bitmap, degrees)
        // kept: failure stays on this call so the screen or caller can continue
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


    /**
     * Choose among stage texts (Raw / Bin-Trials) for a pure-digit odometer (face digits).
     *
     * @param preferredLen face width from [Vehicle.odometerDigitCount] (default 6)
     * @param allowLenPlusOne allow preferredLen+1 (alignment always; Fill when last face starts with 9)
     * @param preferBin when true, within the chosen length pool prefer texts that appear on a Bin stage
     */
    fun pickBestOdometer(
        results: List<OcrStepResult>,
        preferredLen: Int = OdometerTracking.DEFAULT_DIGIT_COUNT,
        allowLenPlusOne: Boolean = false,
        preferBin: Boolean = true,
    ): String? {
        data class Cand(val text: String, val fromBin: Boolean)
        val pref = OdometerTracking.preferredLength(preferredLen)
        val band = OdometerTracking.allowedLengths(pref, allowLenPlusOne)
        val tagged = results.mapNotNull { step ->
            val t = step.text?.trim().orEmpty()
            if (t.isEmpty() || !t.all { it.isDigit() }) return@mapNotNull null
            val fromBin = step.stageName.contains("Bin", ignoreCase = true)
            Cand(t, fromBin)
        }
        if (tagged.isEmpty()) return null

        fun pool(minL: Int, maxL: Int): List<Cand> {
            val p = tagged.filter { it.text.length in minL..maxL }
            if (!preferBin) return p
            val binOnly = p.filter { it.fromBin }
            return if (binOnly.isNotEmpty()) binOnly else p
        }

        fun majority(list: List<Cand>): String? {
            if (list.isEmpty()) return null
            return list.groupBy { it.text }.maxByOrNull { it.value.size }?.key
                ?: list.maxByOrNull { it.text.length }?.text
        }

        // Prefer exact face length; then face+1 if allowed; then band; then any 4–7.
        majority(pool(pref, pref))?.let { return it }
        if (allowLenPlusOne) {
            majority(pool(pref + 1, pref + 1))?.let { return it }
        }
        majority(pool(band.first, band.last))?.let { return it }
        return majority(pool(4, 7))
    }

    fun findValleyMidpoints(bins: FloatArray): List<Int> {
        if (bins.isEmpty()) return emptyList()
        val binCount = bins.size
        val smoothed = FloatArray(binCount)
        for (i in 0 until binCount) {
            val start = (i - 1).coerceAtLeast(0)
            val end = (i + 1).coerceAtMost(binCount - 1)
            smoothed[i] = (start..end).map { bins[it] }.average().toFloat()
        }

        val midpoints = mutableListOf<Int>()
        var i = 1
        while (i < binCount - 1) {
            if (smoothed[i] <= smoothed[i - 1] && smoothed[i] <= smoothed[i + 1]) {
                val startIdx = i
                while (i < binCount - 1 && smoothed[i + 1] == smoothed[startIdx]) { i++ }
                val endIdx = i

                val risesLeft = smoothed[startIdx - 1] > smoothed[startIdx]
                val risesRight = if (endIdx < binCount - 1) smoothed[endIdx + 1] > smoothed[endIdx] else false

                if (risesLeft && risesRight) {
                    midpoints.add((startIdx + endIdx) / 2)
                }
            }
            i++
        }
        return midpoints.distinct()
    }

    fun getHistStats(mat: org.opencv.core.Mat): HistStats {
        val hist = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(mat), org.opencv.core.MatOfInt(0), org.opencv.core.Mat(), hist, org.opencv.core.MatOfInt(64), org.opencv.core.MatOfFloat(0f, 256f))
        val bins = FloatArray(64); hist.get(0, 0, bins)
        val totalPixels = mat.rows() * mat.cols()

        val smoothed = FloatArray(64)
        for (i in 0..63) {
            val start = (i - 2).coerceAtLeast(0)
            val end = (i + 2).coerceAtMost(63)
            smoothed[i] = (start..end).map { bins[it] }.average().toFloat()
        }

        // Low Limit: Climb to first peak, then drop to valley
        var lowPeakIdx = 0
        while (lowPeakIdx < 62 && smoothed[lowPeakIdx + 1] >= smoothed[lowPeakIdx]) lowPeakIdx++
        var lowIdx = lowPeakIdx
        while (lowIdx < 63 && smoothed[lowIdx + 1] <= smoothed[lowIdx]) lowIdx++

        // High Limit: Climb to first peak from right, then drop to valley
        var highPeakIdx = 63
        while (highPeakIdx > 1 && smoothed[highPeakIdx - 1] >= smoothed[highPeakIdx]) highPeakIdx--
        var highIdx = highPeakIdx
        while (highIdx > 0 && smoothed[highIdx - 1] <= smoothed[highIdx]) highIdx--

        val intensityLow = lowIdx * 4.0
        val intensityHigh = highIdx * 4.0

        var p80 = 0.0
        var sum = 0.0
        for (i in 0..63) {
            sum += bins[i]
            if (sum >= totalPixels * 0.80) { p80 = i * 4.0; break }
        }
        hist.release()
        return HistStats(intensityLow, intensityHigh, p80, bins)
    }

    suspend fun rotate(set: BufferSet, angle: Float): Long = withContext(Dispatchers.IO) {
        val tRot0 = System.currentTimeMillis()

        val matrixLocal = android.graphics.Matrix()
        matrixLocal.postTranslate(-set.p.mat.cols() / 2f, -set.p.mat.rows() / 2f)
        matrixLocal.postRotate(angle)
        matrixLocal.postTranslate(set.p.mat.cols() / 2f, set.p.mat.rows() / 2f)
        val values = FloatArray(9)
        matrixLocal.getValues(values)

        val rotMat = org.opencv.core.Mat(2, 3, org.opencv.core.CvType.CV_64F)
        rotMat.put(0, 0, values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
        rotMat.put(1, 0, values[3].toDouble(), values[4].toDouble(), values[5].toDouble())

        org.opencv.imgproc.Imgproc.warpAffine(
            set.p.mat, set.s.mat, rotMat, set.p.mat.size(),
            org.opencv.imgproc.Imgproc.INTER_LINEAR,
            org.opencv.core.Core.BORDER_CONSTANT,
            org.opencv.core.Scalar(0.0),
        )

        val uvScaleMat = rotMat.clone()
        uvScaleMat.put(0, 2, rotMat.get(0, 2)[0] / 2.0)
        uvScaleMat.put(1, 2, rotMat.get(1, 2)[0] / 2.0)
        org.opencv.imgproc.Imgproc.warpAffine(
            set.p.uvMat, set.s.uvMat, uvScaleMat, set.p.uvMat.size(),
            org.opencv.imgproc.Imgproc.INTER_LINEAR,
            org.opencv.core.Core.BORDER_CONSTANT,
            org.opencv.core.Scalar(128.0, 128.0),
        )

        set.flip()
        rotMat.release()
        uvScaleMat.release()

        System.currentTimeMillis() - tRot0
    }

    fun getIcrsLandmarksFromJson(json: String?, engineName: String): List<IcrsLandmark> {
        if (json.isNullOrBlank()) return emptyList()
        try {
            val trimmed = json.trim()
            val array = when {
                trimmed.startsWith("[") -> org.json.JSONArray(trimmed)
                else -> {
                    val root = org.json.JSONObject(trimmed)
                    if (!root.has(engineName)) return emptyList()
                    root.getJSONArray(engineName)
                }
            }
            val list = mutableListOf<IcrsLandmark>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    IcrsLandmark(
                        text = cleanLandmarkString(obj.getString("text")),
                        instanceId = if (obj.has("instance")) obj.getInt("instance") else -1,
                        cx = obj.optDouble("cx", 0.0).toFloat(),
                        cy = obj.optDouble("cy", 0.0).toFloat(),
                        w = obj.optDouble("w", 0.0).toFloat(),
                        h = obj.optDouble("h", 0.0).toFloat(),
                    ),
                )
            }
            return list
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            throw IllegalStateException("getIcrsLandmarksFromJson: ${e.message}", e)
        }
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
                if (cleaned.isNotEmpty()) {
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
                        val centerPix = IcrsMath.icrsToPixel(cx.toFloat(), cy.toFloat(), imgW, imgH)

                        val shortEdge = minOf(imgW, imgH).toDouble()
                        val pixW = (w * shortEdge)
                        val pixH = (h * shortEdge)

                        val left = (centerPix.x - pixW / 2.0).toInt()
                        val top = (centerPix.y - pixH / 2.0).toInt()
                        val right = (centerPix.x + pixW / 2.0).toInt()
                        val bottom = (centerPix.y + pixH / 2.0).toInt()
                        blocks.add(TextBlock(obj.getString("text"), android.graphics.Rect(left, top, right, bottom), instanceId = instanceId))
                    // kept: failure stays on this call so the screen or caller can continue
                    } catch (e: Exception) {
                        Log.w("OdometerOcr", "Skipping malformed landmark entry in JSON: ${e.message}")
                    }
                }
                results[name] = OcrResult(engineName = name, textBlocks = blocks, imageWidth = imgW, imageHeight = imgH, debugText = blocks.joinToString(" ") { it.text })
            }
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) { Log.e("OdometerOcr", "Deserialization failed", e) }
        return results
    }

}
