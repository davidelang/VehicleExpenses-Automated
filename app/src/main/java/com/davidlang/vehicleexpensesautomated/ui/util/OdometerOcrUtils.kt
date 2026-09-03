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

    data class HistMarker(val value: Double, val color: Int)
    data class HistStats(val intensityLow: Double, val intensityHigh: Double, val p80: Double, val rawBins: FloatArray)
    fun clusterRects(fragments: List<android.graphics.Rect>): List<android.graphics.Rect> {
        val clusters = mutableListOf<MutableList<android.graphics.Rect>>()
        for (frag in fragments) {
            val matchingClusters = mutableListOf<Int>()
            for ((idx, cluster) in clusters.withIndex()) {
                if (cluster.any { c ->
                    val overlapTop = kotlin.math.max(frag.top, c.top); val overlapBottom = kotlin.math.min(frag.bottom, c.bottom)
                    val overlapHeight = overlapBottom - overlapTop
                    overlapHeight > 0 && overlapHeight >= kotlin.math.min(frag.height(), c.height()) * 0.20
                }) matchingClusters.add(idx)
            }
            if (matchingClusters.isEmpty()) clusters.add(mutableListOf(frag))
            else {
                val firstIdx = matchingClusters[0]; clusters[firstIdx].add(frag)
                for (k in matchingClusters.size - 1 downTo 1) {
                    clusters[firstIdx].addAll(clusters[matchingClusters[k]])
                    clusters.removeAt(matchingClusters[k])
                }
            }
        }
        return clusters.map { cluster ->
            android.graphics.Rect(cluster.minOf { it.left }, cluster.minOf { it.top }, cluster.maxOf { it.right }, cluster.maxOf { it.bottom })
        }
    }


    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("OdometerOcr", "OpenCV initialization failed!")
        } else {
            Log.i("OdometerOcr", "OpenCV initialized successfully")
        }
    }

    data class EngineResult(
        val angle: Float,
        val timesMs: List<Long>,
        val blocks: List<TextBlock> = emptyList(),
        val metadata: Map<String, String> = emptyMap(),
        val cppBlocks: List<TextBlock> = emptyList()
    )
    data class DeskewResult(
        val angle: Float,
        val mlAngle: Float,
        val mlTimeMs: Long,
        val paddleTimeMs: Long,
        val paddleCppAngle: Float = 0f,
        val paddleOptimizedAngle: Float = 0f,
        val paddleOptimizedTimeMs: Long = 0L,
        val mlBlocks: List<TextBlock> = emptyList(),
        val paddleBlocks: List<TextBlock> = emptyList(),
        val paddleCppBlocks: List<TextBlock> = emptyList(),
        val engines: Map<String, EngineResult> = emptyMap(),
        val metadata: Map<String, String> = emptyMap()
    )

    /**
     * @param longEdgeTarget long-edge letterbox size for det heatmap used for angle
     *   (alignment + pump experiments: 256; production default maps to the 608 set).
     */
    suspend fun calculateAverageTextAngle(input: Any, longEdgeTarget: Int = 2048): DeskewResult {
        val t0 = System.currentTimeMillis()
        val pTargetSize = longEdgeTarget.coerceIn(64, 2048)
        val (optAngle, optTime) = calculatePaddleAngleOptimized(input, pTargetSize)
        val pack = packDeskewLetterbox(input, pTargetSize)
        val tPrep = System.currentTimeMillis() - t0
        val results = mutableMapOf<String, EngineResult>()

        val tMl0 = System.currentTimeMillis()
        val mlRes = deskewMlKit(pack.dest.p.nv21, pack.dest.p.width, pack.dest.p.height, pack.pScale)
        val tMl = System.currentTimeMillis() - tMl0
        results["ML Kit"] = mlRes.copy(timesMs = listOf(tPrep, tMl))

        val tPd0 = System.currentTimeMillis()
        val pdRes = deskewPaddleDual(pack)
        val tPd = System.currentTimeMillis() - tPd0
        results["Paddle V3"] = pdRes.copy(timesMs = listOf(tPrep, tPd))

        val paddleCppAngle = pdRes.metadata["paddle_cpp_angle"]?.toFloatOrNull() ?: 0f
        return DeskewResult(
            angle = mlRes.angle.coerceIn(-20f, 20f),
            mlAngle = mlRes.angle,
            mlTimeMs = results["ML Kit"]?.timesMs?.sum() ?: 0L,
            paddleTimeMs = results["Paddle V3"]?.timesMs?.sum() ?: 0L,
            paddleCppAngle = paddleCppAngle,
            paddleOptimizedAngle = optAngle,
            paddleOptimizedTimeMs = optTime,
            mlBlocks = mlRes.blocks,
            paddleBlocks = pdRes.blocks,
            paddleCppBlocks = pdRes.cppBlocks,
            engines = results,
            metadata = mapOf(
                "t_prep_ms" to tPrep.toString(),
                "deskew_long_edge" to pTargetSize.toString(),
            ),
        )
    }

    suspend fun calculateDeskewAngleMlOnly(input: Any, longEdgeTarget: Int = 2048): DeskewResult {
        val t0 = System.currentTimeMillis()
        val pTargetSize = longEdgeTarget.coerceIn(64, 2048)
        val pack = packDeskewLetterbox(input, pTargetSize)
        val tPrep = System.currentTimeMillis() - t0
        val results = mutableMapOf<String, EngineResult>()
        val tMl0 = System.currentTimeMillis()
        val mlRes = deskewMlKit(pack.dest.p.nv21, pack.dest.p.width, pack.dest.p.height, pack.pScale)
        val tMl = System.currentTimeMillis() - tMl0
        results["ML Kit"] = mlRes.copy(timesMs = listOf(tPrep, tMl))
        return DeskewResult(
            angle = mlRes.angle.coerceIn(-20f, 20f),
            mlAngle = mlRes.angle,
            mlTimeMs = results["ML Kit"]?.timesMs?.sum() ?: 0L,
            paddleTimeMs = 0L,
            paddleCppAngle = 0f,
            paddleOptimizedAngle = 0f,
            paddleOptimizedTimeMs = 0L,
            mlBlocks = mlRes.blocks,
            engines = results,
            metadata = mapOf(
                "t_prep_ms" to tPrep.toString(),
                "deskew_long_edge" to pTargetSize.toString(),
            ),
        )
    }

    /**
     * @param longEdgeTarget long-edge letterbox for paddle det angle
     *   (pump experiment: 256; production default maps to the 608 set).
     */
    suspend fun calculateDeskewAnglePaddleOnly(input: Any, longEdgeTarget: Int = 2048): DeskewResult {
        val t0 = System.currentTimeMillis()
        val pTargetSize = longEdgeTarget.coerceIn(64, 2048)
        val pack = packDeskewLetterbox(input, pTargetSize)
        val tPrep = System.currentTimeMillis() - t0
        val results = mutableMapOf<String, EngineResult>()
        val tPd0 = System.currentTimeMillis()
        val pdRes = deskewPaddleDual(pack)
        val tPd = System.currentTimeMillis() - tPd0
        results["Paddle V3"] = pdRes.copy(timesMs = listOf(tPrep, tPd))
        val paddleCppAngle = pdRes.metadata["paddle_cpp_angle"]?.toFloatOrNull() ?: 0f
        return DeskewResult(
            angle = paddleCppAngle.coerceIn(-20f, 20f),
            mlAngle = 0f,
            mlTimeMs = 0L,
            paddleTimeMs = results["Paddle V3"]?.timesMs?.sum() ?: 0L,
            paddleCppAngle = paddleCppAngle,
            paddleOptimizedAngle = paddleCppAngle,
            paddleOptimizedTimeMs = results["Paddle V3"]?.timesMs?.sum() ?: 0L,
            paddleBlocks = pdRes.blocks,
            paddleCppBlocks = pdRes.cppBlocks,
            engines = results,
            metadata = mapOf(
                "t_prep_ms" to tPrep.toString(),
                "deskew_long_edge" to pTargetSize.toString(),
            ),
        )
    }

    suspend fun calculatePaddleAngleOptimized(input: Any, longEdgeTarget: Int = 2048): Pair<Float, Long> {
        val t0 = System.currentTimeMillis()
        val pack = packDeskewLetterbox(input, longEdgeTarget.coerceIn(64, 2048))
        val paddleEngine = VehicleExpensesApplication.anchoredEngineV3 ?: return Pair(0f, 0L)
        val det = paddleEngine.detect(pack.dest, pack.S, pack.S, copyHeatmap = false)
        val cppAngle = det?.deskewAngleCpp(0.20f) ?: 0f
        return Pair(cppAngle, System.currentTimeMillis() - t0)
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

    fun consolidateRects(fragments: List<android.graphics.Rect>, threshold: Float = 0.75f): List<android.graphics.Rect> {
        if (fragments.isEmpty()) return emptyList()
        val merged = mutableListOf<android.graphics.Rect>()
        val remaining = fragments.toMutableList()

        while (remaining.isNotEmpty()) {
            var current = remaining.removeAt(0)
            var changed = true
            while (changed) {
                changed = false
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()
                    val interL = Math.max(current.left, next.left); val interT = Math.max(current.top, next.top)
                    val interR = Math.min(current.right, next.right); val interB = Math.min(current.bottom, next.bottom)

                    val overlapW = if (interR > interL) interR - interL else 0
                    val overlapH = if (interB > interT) interB - interT else 0
                    val minW = Math.min(current.width(), next.width())
                    val minH = Math.min(current.height(), next.height())

                    val significant = overlapW >= (minW * threshold) && overlapH >= (minH * threshold)

                    if (significant) {
                        current = android.graphics.Rect(
                            Math.min(current.left, next.left),
                            Math.min(current.top, next.top),
                            Math.max(current.right, next.right),
                            Math.max(current.bottom, next.bottom)
                        )
                        iterator.remove()
                        changed = true
                    }
                }
            }
            merged.add(current)
        }
        return merged
    }


    private data class DeskewPack(
        val dest: BufferSet,
        val S: Int,
        val innerW: Int,
        val innerH: Int,
        val pScale: Float,
        val srcW: Int,
        val srcH: Int,
    )

    /** Packed letterbox into the 256 or 608 standing square. S = dest.width. */
    private fun packDeskewLetterbox(input: Any, longEdgeTarget: Int): DeskewPack {
        val dest = NativePaddleEngine.deskewSetFor(longEdgeTarget)
        val S = dest.width
        val letterEdge = minOf(longEdgeTarget.coerceIn(64, 2048), S)
        val srcW: Int
        val srcH: Int
        val srcY: Mat
        var tmpGray: Mat? = null
        var tmpArgb: Mat? = null
        when (input) {
            is Bitmap -> {
                srcW = input.width
                srcH = input.height
                tmpArgb = Mat()
                org.opencv.android.Utils.bitmapToMat(input, tmpArgb)
                tmpGray = Mat()
                Imgproc.cvtColor(tmpArgb, tmpGray, Imgproc.COLOR_RGBA2GRAY)
                srcY = tmpGray
            }
            is BufferSet.Slice -> {
                srcW = input.width
                srcH = input.height
                srcY = input.mat
            }
            else -> throw IllegalArgumentException("Unsupported deskew input: ${input.javaClass.name}")
        }
        val currentLong = maxOf(srcW, srcH)
        val scale = if (currentLong <= letterEdge) 1.0f else letterEdge.toFloat() / currentLong
        val innerW = (srcW * scale).toInt().coerceAtLeast(1)
        val innerH = (srcH * scale).toInt().coerceAtLeast(1)
        val pScale = minOf(innerW.toFloat() / srcW, innerH.toFloat() / srcH)
        val rawS = (dest.s as BufferSet.Instance).tensorBindRaw()
        val rawP = (dest.p as BufferSet.Instance).tensorBindRaw()
        NativeImageUtils.scalePackedU8(srcY, rawS, S, innerW, innerH)
        NativeImageUtils.scalePackedU8(srcY, rawP, S, innerW, innerH)
        tmpGray?.release()
        tmpArgb?.release()
        return DeskewPack(dest, S, innerW, innerH, pScale, srcW, srcH)
    }

    private suspend fun deskewPaddleDual(pack: DeskewPack): EngineResult {
        val paddleEngine = VehicleExpensesApplication.anchoredEngineV3 ?: return EngineResult(0f, emptyList())

        val tDet0 = System.currentTimeMillis()
        val det = paddleEngine.detect(pack.dest, pack.S, pack.S, copyHeatmap = false) ?: return EngineResult(0f, emptyList())
        val tDetOnly = System.currentTimeMillis() - tDet0

        // Parallel Angle Calculation
        val tAngleCpp0 = System.currentTimeMillis()
        val cppAngle = det.deskewAngleCpp(0.20f)
        val tAngleCpp = System.currentTimeMillis() - tAngleCpp0

        val newMeta = det.metadata.toMutableMap()
        newMeta["paddle_cpp_angle"] = cppAngle.toString()

        val invScaleX = pack.srcW.toFloat() / pack.innerW
        val invScaleY = pack.srcH.toFloat() / pack.innerH
        val cppBlocks = det.nativeBoxes.map { box ->
            val points = box.points
            val minX = minOf(minOf(points[0], points[2]), minOf(points[4], points[6])) * invScaleX
            val minY = minOf(minOf(points[1], points[3]), minOf(points[5], points[7])) * invScaleY
            val maxX = maxOf(maxOf(points[0], points[2]), maxOf(points[4], points[6])) * invScaleX
            val maxY = maxOf(maxOf(points[1], points[3]), maxOf(points[5], points[7])) * invScaleY
            val bounds = android.graphics.Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())

            val scaledPoints = FloatArray(8)
            for (i in 0 until 8) {
                scaledPoints[i] = points[i] * if (i % 2 == 0) invScaleX else invScaleY
            }
            val angle = calculateBoxAngle(scaledPoints)
            TextBlock("", bounds, angle, confidence = box.confidence)
        }

        return EngineResult(cppAngle, emptyList(), emptyList(), newMeta, cppBlocks = cppBlocks)
    }

    fun calculateBoxAngle(points: FloatArray): Float {
        if (points.size < 8) return 0f
        var minAbsAngle = 180f
        var resAngle = 0f
        for (i in 0 until 4) {
            val x1 = points[i * 2]
            val y1 = points[i * 2 + 1]
            val x2 = points[((i + 1) % 4) * 2]
            val y2 = points[((i + 1) % 4) * 2 + 1]
            val dx = x2 - x1
            val dy = y2 - y1
            val ang = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
            var normAng = ang
            while (normAng <= -45f) normAng += 90f
            while (normAng > 45f) normAng -= 90f
            if (Math.abs(normAng) < minAbsAngle) {
                minAbsAngle = Math.abs(normAng)
                resAngle = normAng
            }
        }
        return resAngle
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
                            val confidence = element.confidence ?: 0f
                            blocks.add(TextBlock(hunk, box, line.angle, confidence = confidence))
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
        if (!wrapBitmapToGrayU8(bitmap, odoGrayU8)) return
        grayU8ToBitmap(odoGrayU8, bitmap)
    }

    @Suppress("UNUSED_PARAMETER")
    fun applyBilateralInPlace(bitmap: Bitmap, scratchBmp: Bitmap) {
        val h = bitmap.height
        val w = bitmap.width
        if (!wrapBitmapToGrayU8(bitmap, odoGrayU8)) return
        val src = standingU8(odoGrayU8, h, w) ?: return
        val dst = standingU8(odoFiltU8, h, w) ?: run {
            src.release()
            return
        }
        Imgproc.bilateralFilter(src, dst, 5, 75.0, 75.0)
        src.release()
        grayU8ToBitmap(odoFiltU8, bitmap)
        dst.release()
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

    fun applyContrastStretch(mat: Mat, intensityLow: Double, intensityHigh: Double) {
        val alpha = if (intensityHigh > intensityLow) 255.0 / (intensityHigh - intensityLow) else 1.0
        val beta = -intensityLow * alpha
        mat.convertTo(mat, CvType.CV_8U, alpha, beta)
    }

    /**
     * Non-mutating clip-stretch bounds from automaticContrastStretch peak-finding (robust + 2% fallback).
     */
    fun getClipStretchLowHigh(mat: Mat): Pair<Double, Double> {
        val hist = Mat()
        Imgproc.calcHist(java.util.Collections.singletonList(mat), MatOfInt(0), Mat(), hist, MatOfInt(64), MatOfFloat(0f, 256f))

        val bins = FloatArray(64); hist.get(0, 0, bins)
        val smoothed = FloatArray(64)
        for (i in 0..63) {
            val start = (i - 1).coerceAtLeast(0); val end = (i + 1).coerceAtMost(63)
            smoothed[i] = (start..end).map { bins[it] }.average().toFloat()
        }

        val totalPixels = mat.rows() * mat.cols()
        val dropOffThreshold = totalPixels * 0.003 // 0.3% drop-off requirement

        // Find robust peak from left
        var pLow = 0.5
        for (i in 1..61) {
            if (smoothed[i] > smoothed[i-1] && smoothed[i] >= smoothed[i+1]) {
                var peakConfirmed = false
                for (j in i+1..62) {
                    if (smoothed[j] < smoothed[i] - dropOffThreshold) {
                        peakConfirmed = true; break
                    }
                    if (smoothed[j] > smoothed[i]) break
                }
                if (peakConfirmed) { pLow = i.toDouble(); break }
            }
        }

        // Find robust peak from right
        var pHigh = 62.5
        for (i in 62 downTo 2) {
            if (smoothed[i] > smoothed[i+1] && smoothed[i] >= smoothed[i-1]) {
                var peakConfirmed = false
                for (j in i-1 downTo 1) {
                    if (smoothed[j] < smoothed[i] - dropOffThreshold) {
                        peakConfirmed = true; break
                    }
                    if (smoothed[j] > smoothed[i]) break
                }
                if (peakConfirmed) { pHigh = i.toDouble(); break }
            }
        }

        var intensityLow = pLow * 4.0
        var intensityHigh = pHigh * 4.0

        if (intensityHigh - intensityLow < 20.0) {
            var iLow = 0.0; var iHigh = 255.0; var sum = 0.0
            for (i in 0..63) { sum += bins[i]; if (sum >= totalPixels * 0.02) { iLow = i * 4.0; break } }
            sum = 0.0; for (i in 63 downTo 0) { sum += bins[i]; if (sum >= totalPixels * 0.02) { iHigh = i * 4.0; break } }
            intensityLow = iLow
            intensityHigh = iHigh
        }

        hist.release()
        return intensityLow to intensityHigh
    }

    fun automaticContrastStretch(mat: Mat): FloatArray {
        val hist = Mat()
        Imgproc.calcHist(java.util.Collections.singletonList(mat), MatOfInt(0), Mat(), hist, MatOfInt(64), MatOfFloat(0f, 256f))
        val bins = FloatArray(64); hist.get(0, 0, bins)
        hist.release()

        val (intensityLow, intensityHigh) = getClipStretchLowHigh(mat)
        applyContrastStretch(mat, intensityLow, intensityHigh)
        return bins
    }

    fun shoulderContrastStretch(mat: Mat): FloatArray {
        val hist = Mat()
        Imgproc.calcHist(java.util.Collections.singletonList(mat), MatOfInt(0), Mat(), hist, MatOfInt(64), MatOfFloat(0f, 256f))

        val bins = FloatArray(64); hist.get(0, 0, bins)
        val smoothed = FloatArray(64)
        for (i in 0..63) {
            val start = (i - 1).coerceAtLeast(0); val end = (i + 1).coerceAtMost(63)
            smoothed[i] = (start..end).map { bins[it] }.average().toFloat()
        }

        var maxCount = 0f; var maxBin = 32
        for (i in 0..63) { if (smoothed[i] > maxCount) { maxCount = smoothed[i]; maxBin = i } }

        val shoulderThreshold = maxCount * 0.10f
        var sLow = 0.0; var sHigh = 63.0

        // Find Low Shoulder
        for (i in maxBin downTo 0) {
            if (smoothed[i] < shoulderThreshold) { sLow = (i + 1).coerceAtMost(63).toDouble(); break }
        }
        // Find High Shoulder
        for (i in maxBin..63) {
            if (smoothed[i] < shoulderThreshold) { sHigh = (i - 1).coerceAtLeast(0).toDouble(); break }
        }

        val intensityLow = sLow * 4.0
        val intensityHigh = sHigh * 4.0

        if (intensityHigh - intensityLow < 20.0) {
            val totalPixels = mat.rows() * mat.cols()
            var iLow = 0.0; var iHigh = 255.0; var sum = 0.0
            for (i in 0..63) { sum += bins[i]; if (sum >= totalPixels * 0.02) { iLow = i * 4.0; break } }
            sum = 0.0; for (i in 63 downTo 0) { sum += bins[i]; if (sum >= totalPixels * 0.02) { iHigh = i * 4.0; break } }
            val alpha = if (iHigh > iLow) 255.0 / (iHigh - iLow) else 1.0
            val beta = -iLow * alpha
            mat.convertTo(mat, CvType.CV_8U, alpha, beta)
        } else {
            val alpha = 255.0 / (intensityHigh - intensityLow)
            val beta = -intensityLow * alpha
            mat.convertTo(mat, CvType.CV_8U, alpha, beta)
        }

        hist.release()
        return bins
    }

    /**
     * Robust peak bin indices from a 64-bin histogram (the logic valley push uses after finding valleys).
     * Uses 3-bin smoothing + left-to-right and right-to-left local-max with drop-off confirmation.
     * Returns sorted list of bin indices (0-63).
     */
    fun findPeakBinsFromHistogram(bins: FloatArray, totalPixels: Double = bins.sum().toDouble()): List<Int> {
        val smoothed = FloatArray(64)
        for (i in 0..63) {
            val start = (i - 1).coerceAtLeast(0); val end = (i + 1).coerceAtMost(63)
            smoothed[i] = (start..end).map { bins[it] }.average().toFloat()
        }
        val peakBins = mutableListOf<Int>()
        val dropOffThreshold = totalPixels * 0.003
        // left-to-right
        for (i in 1..61) {
            if (smoothed[i] > smoothed[i-1] && smoothed[i] >= smoothed[i+1]) {
                var peakConfirmed = false
                for (j in i+1..62) {
                    if (smoothed[j] < smoothed[i] - dropOffThreshold) { peakConfirmed = true; break }
                    if (smoothed[j] > smoothed[i]) break
                }
                if (peakConfirmed) peakBins.add(i)
            }
        }
        // right-to-left
        for (i in 62 downTo 2) {
            if (smoothed[i] > smoothed[i+1] && smoothed[i] >= smoothed[i-1]) {
                var peakConfirmed = false
                for (j in i-1 downTo 1) {
                    if (smoothed[j] < smoothed[i] - dropOffThreshold) { peakConfirmed = true; break }
                    if (smoothed[j] > smoothed[i]) break
                }
                if (peakConfirmed) peakBins.add(i)
            }
        }
        return peakBins.distinct().sorted()
    }

    /**
     * Non-mutating valley/peak gray lists from histogram (same discovery as valleyPushToPeaks).
     * Returns (valleyGrays, peakGrays); empty lists if insufficient valleys/peaks.
     */
    fun getValleyPeakGrays(mat: Mat): Pair<List<Int>, List<Int>> {
        val hist = Mat()
        Imgproc.calcHist(java.util.Collections.singletonList(mat), MatOfInt(0), Mat(), hist, MatOfInt(64), MatOfFloat(0f, 256f))

        val bins = FloatArray(64); hist.get(0, 0, bins)
        hist.release()

        val valleys = findValleyMidpoints(bins)
        val totalPixels = mat.rows() * mat.cols().toDouble()
        val peakList = findPeakBinsFromHistogram(bins, totalPixels)

        if (peakList.size < 2 || valleys.isEmpty()) {
            return emptyList<Int>() to emptyList()
        }

        val peakGrays = peakList.map { (it * 4 + 2).coerceIn(0, 255) }
        val valleyGrays = valleys.map { (it * 4 + 2).coerceIn(0, 255) }
        return valleyGrays to peakGrays
    }

    /**
     * Apply valley-push LUT using precomputed valley/peak grays. In-place mutate; returns before-bins.
     */
    fun applyValleyPushWithGrays(mat: Mat, valleyGrays: List<Int>, peakGrays: List<Int>): FloatArray {
        val hist = Mat()
        Imgproc.calcHist(java.util.Collections.singletonList(mat), MatOfInt(0), Mat(), hist, MatOfInt(64), MatOfFloat(0f, 256f))
        val bins = FloatArray(64); hist.get(0, 0, bins)
        hist.release()

        if (peakGrays.size < 2 || valleyGrays.isEmpty()) {
            return bins
        }

        val lut = IntArray(256)
        val minPeak = peakGrays.first().toDouble()
        val maxPeak = peakGrays.last().toDouble()
        val peakSpan = maxPeak - minPeak

        for (g in 0..255) {
            val closestValley = valleyGrays.minByOrNull { Math.abs(g - it) }
            val targetPeak = if (closestValley != null) {
                if (g < closestValley) {
                    val leftPeaks = peakGrays.filter { it < closestValley }
                    if (leftPeaks.isNotEmpty()) {
                        leftPeaks.minByOrNull { Math.abs(g - it) }!!
                    } else {
                        peakGrays.first()
                    }
                } else {
                    val rightPeaks = peakGrays.filter { it >= closestValley }
                    if (rightPeaks.isNotEmpty()) {
                        rightPeaks.minByOrNull { Math.abs(g - it) }!!
                    } else {
                        peakGrays.last()
                    }
                }
            } else {
                peakGrays.minByOrNull { Math.abs(g - it) }!!
            }

            val stretched = if (peakSpan > 0.0) {
                Math.round((targetPeak - minPeak) * 255.0 / peakSpan).toInt().coerceIn(0, 255)
            } else {
                targetPeak
            }
            lut[g] = stretched
        }

        val total = mat.total().toInt()
        if (total > 0) {
            val lutMat = org.opencv.core.Mat(1, 256, org.opencv.core.CvType.CV_8U)
            val lutData = ByteArray(256)
            for (g in 0..255) {
                lutData[g] = (lut[g] and 0xFF).toByte()
            }
            lutMat.put(0, 0, lutData)
            org.opencv.core.Core.LUT(mat, lutMat, mat)
            lutMat.release()
        }

        return bins
    }

    fun valleyPushToPeaks(mat: Mat): FloatArray {
        // New for Set C per approved plan: histogram valley centers -> push values outward to peaks.
        // Result: image with only a *small number* of brightness values (quantized to peaks/modes).
        // Not binarization. Reuses findValleyMidpoints + findPeakBinsFromHistogram (same peak discovery as binPeak).
        // In-place mutate like the stretch funcs. Returns before-bins (caller makes after-hist from mutated mat).
        val hist = Mat()
        Imgproc.calcHist(java.util.Collections.singletonList(mat), MatOfInt(0), Mat(), hist, MatOfInt(64), MatOfFloat(0f, 256f))
        val bins = FloatArray(64); hist.get(0, 0, bins)
        hist.release()

        val (valleyGrays, peakGrays) = getValleyPeakGrays(mat)
        if (peakGrays.size < 2 || valleyGrays.isEmpty()) {
            return bins
        }
        return applyValleyPushWithGrays(mat, valleyGrays, peakGrays)
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
     * Evaluates Kotlin and C++ implementations side-by-side to verify speed, box count, and coordinate match.
     * Note: Kotlin (legacyBlocks) will drive the application logic to guarantee safety, while C++ is used for reporting.
     *
     * DOCUMENTATION FOR FUTURE AGENTS:
     * - The "invScale" maps coordinates from the letterboxed/resized target image space (around 2500x2500 max edge)
     *   back to the original photo's full resolution.
     * - C++ bounding boxes (nativeBoxes) are scaled inside NativePaddleEngine.kt from the 608x608 model output
     *   up to the letterboxed/resized space (scaleX = alignedW / 608). Hence, they only require multiplication by invScale.
     * - The legacy Kotlin blocks (legacyBlocks) were previously scaled directly from the 608x608 space by invScale,
     *   which incorrectly bypassed the aligned image scale factor, resulting in boxes 4.15x too small. We fix this
     *   here by dynamically extracting the aligned width/height from the sourceBuffer and applying scaleX/scaleY.
     */
    fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val width = rect.width().coerceAtMost(bitmap.width - left)
        val height = rect.height().coerceAtMost(bitmap.height - top)
        if (width <= 0 || height <= 0) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    /**
     * Choose among stage texts (Raw / Bin-Trials) for a pure-digit odometer (face digits).
     *
     * @param preferredLen face width from [Vehicle.odometerDigitCount] (default 6)
     * @param allowLenPlusOne allow preferredLen+1 (alignment always; Quick Fill when last face starts with 9)
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
        val src = set.p.mat
        val dst = set.s.mat
        val srcUv = set.p.uvMat
        val dstUv = set.s.uvMat

        val matrixLocal = android.graphics.Matrix()
        matrixLocal.postTranslate(-src.cols() / 2f, -src.rows() / 2f)
        matrixLocal.postRotate(angle)
        matrixLocal.postTranslate(src.cols() / 2f, src.rows() / 2f)
        val values = FloatArray(9)
        matrixLocal.getValues(values)

        val rotMat = org.opencv.core.Mat(2, 3, org.opencv.core.CvType.CV_64F)
        rotMat.put(0, 0, values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
        rotMat.put(1, 0, values[3].toDouble(), values[4].toDouble(), values[5].toDouble())

        org.opencv.imgproc.Imgproc.warpAffine(
            src, dst, rotMat, src.size(),
            org.opencv.imgproc.Imgproc.INTER_LINEAR,
            org.opencv.core.Core.BORDER_CONSTANT,
            org.opencv.core.Scalar(0.0),
        )

        val uvScaleMat = rotMat.clone()
        uvScaleMat.put(0, 2, rotMat.get(0, 2)[0] / 2.0)
        uvScaleMat.put(1, 2, rotMat.get(1, 2)[0] / 2.0)
        org.opencv.imgproc.Imgproc.warpAffine(
            srcUv, dstUv, uvScaleMat, srcUv.size(),
            org.opencv.imgproc.Imgproc.INTER_LINEAR,
            org.opencv.core.Core.BORDER_CONSTANT,
            org.opencv.core.Scalar(128.0, 128.0),
        )

        set.flip()
        rotMat.release()
        uvScaleMat.release()

        System.currentTimeMillis() - tRot0
    }

    fun getFullLandmarksFromJson(json: String?, engineName: String, imgW: Int, imgH: Int): List<TextBlock> {
        if (json.isNullOrEmpty()) return emptyList(); val list = mutableListOf<TextBlock>()
        try {
            val root = org.json.JSONObject(json); val array = if (root.has(engineName)) root.getJSONArray(engineName) else if (json.startsWith("[")) org.json.JSONArray(json) else return emptyList()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i); val text = obj.getString("text"); val cx = obj.optDouble("cx", 0.0); val cy = obj.optDouble("cy", 0.0); val w = obj.optDouble("w", 0.0); val h = obj.optDouble("h", 0.0)
                val centerPix = IcrsMath.icrsToPixel(cx.toFloat(), cy.toFloat(), imgW, imgH)
                val sE = kotlin.math.min(imgW, imgH).toDouble(); val pW = (w * sE); val pH = (h * sE)
                val inst = if (obj.has("instance")) obj.getInt("instance") else -1; val cT = OdometerOcrUtils.cleanLandmarkString(text)
                list.add(TextBlock(cT, android.graphics.Rect((centerPix.x - pW/2.0).toInt(), (centerPix.y - pH/2.0).toInt(), (centerPix.x + pW/2.0).toInt(), (centerPix.y + pH/2.0).toInt()), instanceId = inst))
            }
        } catch (e: Exception) { Log.e("OdometerOcrUtils", "Failed to parse landmarks", e) }
        return list
    }

    fun saveImageProxyToFile(imageProxy: androidx.camera.core.ImageProxy, file: java.io.File) {
        val planeProxy = imageProxy.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        // This only works if capture format is JPEG (ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        // If it's YUV, we'd need to encode it.
        // Assuming ImageCapture provides JPEG when not specified otherwise for proxy?
        // Actually, ImageProxy from ImageCapture is usually JPEG.
        file.writeBytes(bytes)
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
                        val centerPix = IcrsMath.icrsToPixel(cx.toFloat(), cy.toFloat(), imgW, imgH)

                        val shortEdge = minOf(imgW, imgH).toDouble()
                        val pixW = (w * shortEdge)
                        val pixH = (h * shortEdge)

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

    suspend fun extractFromPhoto(photoPath: String): OcrResult = withContext(Dispatchers.IO) {
        val rawBitmap = BitmapFactory.decodeFile(photoPath)
        if (rawBitmap == null) return@withContext OcrResult(debugText = "Failed decode", originalPhotoPath = photoPath)
        val rotated = rotateImageIfRequired(rawBitmap, photoPath)
        val processed = applyBilateral(applyGrayscale(rotated))
        val res = extractFromPhotoBitmap(processed)
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
