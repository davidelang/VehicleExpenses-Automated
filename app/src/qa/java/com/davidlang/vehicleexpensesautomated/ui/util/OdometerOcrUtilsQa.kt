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


    data class HistMarker(val value: Double, val color: Int)


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



    private data class DeskewPack(
        val dest: BufferSet,
        val S: Int,
        val innerW: Int,
        val innerH: Int,
        val pScale: Float,
        val srcW: Int,
        val srcH: Int,
    )

    fun OdometerOcrUtils.clusterRects(fragments: List<android.graphics.Rect>): List<android.graphics.Rect> {
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


    /**
     * @param longEdgeTarget long-edge letterbox size for det heatmap used for angle
     *   (alignment + pump experiments: 256; production default maps to the 608 set).
     */
    suspend fun OdometerOcrUtils.calculateAverageTextAngle(input: Any, longEdgeTarget: Int = 2048): DeskewResult {
        val t0 = System.currentTimeMillis()
        val pTargetSize = longEdgeTarget.coerceIn(64, 2048)
        val (optAngle, optTime) = calculatePaddleAngleOptimized(input, pTargetSize)
        val pack = packDeskewLetterbox(input, pTargetSize)
        val tPrep = System.currentTimeMillis() - t0
        val results = mutableMapOf<String, EngineResult>()

        val tPd0 = System.currentTimeMillis()
        val pdRes = deskewPaddleDual(pack)
        val tPd = System.currentTimeMillis() - tPd0
        results["Paddle V3"] = pdRes.copy(timesMs = listOf(tPrep, tPd))

        val paddleCppAngle = pdRes.metadata["paddle_cpp_angle"]?.toFloatOrNull() ?: run {
            Log.w("OdometerOcr", "deskew0:no_cpp_angle_avg")
            0f
        }
        return DeskewResult(
            angle = optAngle,
            mlAngle = 0f,
            mlTimeMs = 0L,
            paddleTimeMs = results["Paddle V3"]?.timesMs?.sum() ?: 0L,
            paddleCppAngle = paddleCppAngle,
            paddleOptimizedAngle = optAngle,
            paddleOptimizedTimeMs = optTime,
            mlBlocks = emptyList(),
            paddleBlocks = pdRes.blocks,
            paddleCppBlocks = pdRes.cppBlocks,
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
    suspend fun OdometerOcrUtils.calculateDeskewAnglePaddleOnly(input: Any, longEdgeTarget: Int = 2048): DeskewResult {
        val t0 = System.currentTimeMillis()
        val pTargetSize = longEdgeTarget.coerceIn(64, 2048)
        val pack = packDeskewLetterbox(input, pTargetSize)
        val tPrep = System.currentTimeMillis() - t0
        val results = mutableMapOf<String, EngineResult>()
        val tPd0 = System.currentTimeMillis()
        val pdRes = deskewPaddleDual(pack)
        val tPd = System.currentTimeMillis() - tPd0
        results["Paddle V3"] = pdRes.copy(timesMs = listOf(tPrep, tPd))
        val paddleCppAngle = pdRes.metadata["paddle_cpp_angle"]?.toFloatOrNull() ?: run {
            Log.w("OdometerOcr", "deskew0:no_cpp_angle_only")
            0f
        }
        return DeskewResult(
            angle = paddleCppAngle,
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


    suspend fun OdometerOcrUtils.calculatePaddleAngleOptimized(input: Any, longEdgeTarget: Int = 2048): Pair<Float, Long> {
        val t0 = System.currentTimeMillis()
        val pack = packDeskewLetterbox(input, longEdgeTarget.coerceIn(64, 2048))
        val paddleEngine = VehicleExpensesApplication.anchoredEngineV3
        if (paddleEngine == null) {
            Log.w("OdometerOcr", "deskew0:opt_no_engine")
            return Pair(0f, 0L)
        }
        val det = paddleEngine.detect(pack.dest, pack.S, pack.S, copyHeatmap = false)
        val cppAngle = if (det == null) {
            Log.w("OdometerOcr", "deskew0:opt_detect_null")
            0f
        } else {
            det.deskewAngleCpp(0.20f)
        }
        return Pair(cppAngle, System.currentTimeMillis() - t0)
    }


    fun OdometerOcrUtils.consolidateRects(fragments: List<android.graphics.Rect>, threshold: Float = 0.75f): List<android.graphics.Rect> {
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


    /** Packed letterbox into the 256 or 608 standing square. S = dest.width. */
    private fun OdometerOcrUtils.packDeskewLetterbox(input: Any, longEdgeTarget: Int): DeskewPack {
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
        NativeImageUtils.scalePackedU8(srcY, rawS, S, innerW, innerH)
        tmpGray?.release()
        tmpArgb?.release()
        return DeskewPack(dest, S, innerW, innerH, pScale, srcW, srcH)
    }


    private suspend fun OdometerOcrUtils.deskewPaddleDual(pack: DeskewPack): EngineResult {
        val paddleEngine = VehicleExpensesApplication.anchoredEngineV3
        if (paddleEngine == null) {
            Log.w("OdometerOcr", "deskew0:dual_no_engine")
            return EngineResult(0f, emptyList())
        }

        val tDet0 = System.currentTimeMillis()
        val det = paddleEngine.detect(pack.dest, pack.S, pack.S, copyHeatmap = false)
        if (det == null) {
            Log.w("OdometerOcr", "deskew0:dual_detect_null")
            return EngineResult(0f, emptyList())
        }
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


    fun OdometerOcrUtils.calculateBoxAngle(points: FloatArray): Float {
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

    fun OdometerOcrUtils.applyContrastStretch(mat: Mat, floorPercentile: Float) {
        val hist = Mat()
        Imgproc.calcHist(java.util.Collections.singletonList(mat), MatOfInt(0), Mat(), hist, MatOfInt(256), MatOfFloat(0f, 256f))
        val totalPixels = mat.rows() * mat.cols(); var floorBin = 0; var ceilingBin = 255; var sum = 0.0
        for (i in 0..255) { sum += hist.get(i, 0)[0]; if (sum >= totalPixels * floorPercentile) { floorBin = i; break } }
        sum = 0.0; for (i in 0..255) { sum += hist.get(i, 0)[0]; if (sum >= totalPixels * 0.98) { ceilingBin = i; break } }
        val alpha = if (ceilingBin > floorBin) 255.0 / (ceilingBin - floorBin) else 1.0; val beta = -floorBin * alpha
        mat.convertTo(mat, CvType.CV_8U, alpha, beta)
        hist.release()
    }


    fun OdometerOcrUtils.applyContrastStretch(mat: Mat, intensityLow: Double, intensityHigh: Double) {
        val alpha = if (intensityHigh > intensityLow) 255.0 / (intensityHigh - intensityLow) else 1.0
        val beta = -intensityLow * alpha
        mat.convertTo(mat, CvType.CV_8U, alpha, beta)
    }


    /**
     * Non-mutating clip-stretch bounds from automaticContrastStretch peak-finding (robust + 2% fallback).
     */
    fun OdometerOcrUtils.getClipStretchLowHigh(mat: Mat): Pair<Double, Double> {
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



    /**
     * Robust peak bin indices from a 64-bin histogram (the logic valley push uses after finding valleys).
     * Uses 3-bin smoothing + left-to-right and right-to-left local-max with drop-off confirmation.
     * Returns sorted list of bin indices (0-63).
     */
    fun OdometerOcrUtils.findPeakBinsFromHistogram(bins: FloatArray, totalPixels: Double = bins.sum().toDouble()): List<Int> {
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
    fun OdometerOcrUtils.getValleyPeakGrays(mat: Mat): Pair<List<Int>, List<Int>> {
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
    fun OdometerOcrUtils.applyValleyPushWithGrays(mat: Mat, valleyGrays: List<Int>, peakGrays: List<Int>): FloatArray {
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


    fun OdometerOcrUtils.applyContrastStretch(bitmap: Bitmap, floorPercentile: Int): Bitmap {
        val src = if (bitmap.config == Bitmap.Config.ALPHA_8) bitmapToMat(bitmap) else {
            val m = Mat(); org.opencv.android.Utils.bitmapToMat(bitmap, m); m
        }
        val gray = Mat()
        if (src.channels() > 1) Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY) else src.copyTo(gray)

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


    fun OdometerOcrUtils.matToBitmap(mat: Mat, bitmap: Bitmap) {
        val capacity = bitmap.width * bitmap.height
        val bytes = ByteArray(capacity)
        mat.get(0, 0, bytes)
        val buffer = java.nio.ByteBuffer.allocateDirect(capacity).order(java.nio.ByteOrder.nativeOrder())
        buffer.rewind()
        buffer.put(bytes, 0, capacity)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
    }




    /** In-place CLAHE 2.0, tile 8×8. For rec H=48 strips. */
    fun OdometerOcrUtils.applyClaheMat(mat: Mat) {
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val out = Mat()
        clahe.apply(mat, out)
        out.copyTo(mat)
        out.release()
    }


    /** Gaussian residual sharpen: 1.5·src − 0.5·gauss3. In-place. */
    fun OdometerOcrUtils.applyUnsharpMat(mat: Mat) {
        val blur = Mat()
        Imgproc.GaussianBlur(mat, blur, Size(3.0, 3.0), 0.0)
        Core.addWeighted(mat, 1.5, blur, -0.5, 0.0, mat)
        blur.release()
    }


    /** Homomorphic-lite: src / (gaussσ + ε), rescale 0–255. In-place. No Otsu. */
    fun OdometerOcrUtils.applyDivBlurMat(mat: Mat, sigma: Double = 8.0) {
        val blur = Mat()
        Imgproc.GaussianBlur(mat, blur, Size(0.0, 0.0), sigma)
        val sf = Mat()
        val bf = Mat()
        val out = Mat()
        mat.convertTo(sf, CvType.CV_32F)
        blur.convertTo(bf, CvType.CV_32F)
        Core.add(bf, org.opencv.core.Scalar(1.0), bf)
        Core.divide(sf, bf, out)
        Core.normalize(out, out, 0.0, 255.0, Core.NORM_MINMAX)
        out.convertTo(mat, CvType.CV_8U)
        blur.release(); sf.release(); bf.release(); out.release()
    }


    /** Logistic LUT around histogram valley (soft split). Not THRESH_OTSU. In-place. */
    fun OdometerOcrUtils.applySigmoidValleyMat(mat: Mat) {
        val (valleys, _) = getValleyPeakGrays(mat)
        val v = if (valleys.isNotEmpty()) valleys.sorted()[valleys.size / 2] else 128
        val k = 0.12
        val lutData = ByteArray(256)
        for (g in 0..255) {
            val s = 255.0 / (1.0 + exp(-k * (g - v)))
            lutData[g] = s.roundToInt().coerceIn(0, 255).toByte()
        }
        val lutMat = Mat(1, 256, CvType.CV_8U)
        lutMat.put(0, 0, lutData)
        Core.LUT(mat, lutMat, mat)
        lutMat.release()
    }

fun OdometerOcrUtils.automaticContrastStretch(mat: Mat): FloatArray {
    val hist = Mat()
    Imgproc.calcHist(java.util.Collections.singletonList(mat), MatOfInt(0), Mat(), hist, MatOfInt(64), MatOfFloat(0f, 256f))
    val bins = FloatArray(64); hist.get(0, 0, bins)
    hist.release()

    val (intensityLow, intensityHigh) = getClipStretchLowHigh(mat)
    applyContrastStretch(mat, intensityLow, intensityHigh)
    return bins
}

fun OdometerOcrUtils.valleyPushToPeaks(mat: Mat): FloatArray {
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

