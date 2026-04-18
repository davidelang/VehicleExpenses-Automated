package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Represents a detected text region with both rotated points and axis-aligned bounds.
 * Mandated: All coordinates (points and boundingBox) are NORMALIZED (0.0 to 1.0).
 */
data class DetectedBox(
    val points: List<Point>,
    val boundingBox: RectF, // Using RectF for normalized precision
    val angle: Float
)

/**
 * Simple Rect implementation for Float normalized coordinates.
 */
data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Shared utilities for processing TFLite OCR and Detection model outputs.
 * Implementation of Phase 9: Edge-Guided Boundary Discovery.
 */
object TfLiteOcrUtils {

    enum class Polarity { LIGHT_ON_DARK, DARK_ON_LIGHT }

    /**
     * Decodes raw 3D float array (logits) from a TFLite OCR model using CTC Greedy Search.
     */
    fun decodeCtcGreedy(logits: Array<Array<FloatArray>>, dictionary: List<String>, blankIndex: Int = 0): Pair<String, Float> {
        val result = StringBuilder()
        val sequence = logits[0]
        val timeSteps = sequence.size
        val numClasses = sequence[0].size
        var lastIndex = -1; var totalConf = 0f; var count = 0

        for (t in 0 until timeSteps) {
            var maxIndex = 0; var maxVal = sequence[t][0]
            for (c in 1 until numClasses) {
                if (sequence[t][c] > maxVal) { maxVal = sequence[t][c]; maxIndex = c }
            }
            if (maxIndex != blankIndex && maxIndex != lastIndex) {
                val dictIndex = if (blankIndex == 0) maxIndex - 1 else maxIndex
                if (dictIndex >= 0 && dictIndex < dictionary.size) {
                    result.append(dictionary[dictIndex]); totalConf += maxVal; count++
                }
            }
            lastIndex = maxIndex
        }
        return Pair(result.toString(), if (count > 0) totalConf / count else 0f)
    }

    /**
     * Heuristic to determine if image has light text on dark background or vice versa.
     */
    fun detectPolarity(mat: Mat): Polarity {
        val samples = mutableListOf<Double>()
        val w = mat.cols(); val h = mat.rows()
        if (w < 1 || h < 1) return Polarity.DARK_ON_LIGHT
        
        // Corners
        samples.add(mat.get(0, 0)[0])
        samples.add(mat.get(0, (w-1).coerceAtLeast(0))[0])
        samples.add(mat.get((h-1).coerceAtLeast(0), 0)[0])
        samples.add(mat.get((h-1).coerceAtLeast(0), (w-1).coerceAtLeast(0))[0])
        
        val avg = samples.average()
        return if (avg > 127) Polarity.DARK_ON_LIGHT else Polarity.LIGHT_ON_DARK
    }

    /**
     * Processes DBNet detection heatmap into rotated text polygons.
     * Supports Algorithm B (Perimeter Check) and Algorithm C (Edge-Stop).
     */
    fun processDbNetOutput(
        heatmap: FloatArray,
        heatmapW: Int,
        heatmapH: Int,
        sourceBitmap: Bitmap? = null,
        algorithm: String = "C" 
    ): List<DetectedBox> {
        val t0 = System.currentTimeMillis()
        if (heatmapW <= 0 || heatmapH <= 0 || heatmap.size < heatmapW * heatmapH) return emptyList()
        
        val mask = Mat(heatmapH, heatmapW, CvType.CV_8UC1)
        val data = ByteArray(heatmapW * heatmapH)
        for (i in heatmap.indices) {
            data[i] = if (heatmap[i] > 0.2f) 255.toByte() else 0.toByte()
        }
        mask.put(0, 0, data)

        val sourceMat = if (sourceBitmap != null) {
            try {
                val mat = Mat()
                Utils.bitmapToMat(sourceBitmap, mat)
                mat
            } catch (e: Exception) { null }
        } else null

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val results = mutableListOf<DetectedBox>()
        val sourceW = sourceBitmap?.width ?: heatmapW
        val sourceH = sourceBitmap?.height ?: heatmapH
        val scaleX = sourceW.toDouble() / heatmapW
        val scaleY = sourceH.toDouble() / heatmapH

        for (contour in contours) {
            if (Imgproc.contourArea(contour) < 10) continue 
            val rotatedRect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
            
            // Map the low-res rect to high-res source space
            val sourceRect = RotatedRect(
                Point(rotatedRect.center.x * scaleX, rotatedRect.center.y * scaleY),
                Size(rotatedRect.size.width * scaleX, rotatedRect.size.height * scaleY),
                rotatedRect.angle
            )

            val sourcePoints = arrayOf(Point(), Point(), Point(), Point())
            sourceRect.points(sourcePoints)

            var bounds: Rect
            if ((algorithm == "B" || algorithm == "C") && sourceMat != null) {
                // OPTIMIZATION: ROI-Based Expansion
                bounds = expandInRoi(sourceRect, sourceMat, algorithm)
            } else {
                val expandedPoints = unclipBox(sourcePoints, 2.5f)
                var minX = sourceW.toDouble(); var minY = sourceH.toDouble(); var maxX = 0.0; var maxY = 0.0
                for (p in expandedPoints) {
                    minX = min(minX, p.x); minY = min(minY, p.y); maxX = max(maxX, p.x); maxY = max(maxY, p.y)
                }
                bounds = Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(sourceW, maxX.toInt()), min(sourceH, maxY.toInt()))
            }
            
            val normPoints = sourcePoints.map { Point(it.x / sourceW, it.y / sourceH) }
            val normBounds = RectF(
                (bounds.left.toFloat() / sourceW).coerceIn(0f, 1f),
                (bounds.top.toFloat() / sourceH).coerceIn(0f, 1f),
                (bounds.right.toFloat() / sourceW).coerceIn(0f, 1f),
                (bounds.bottom.toFloat() / sourceH).coerceIn(0f, 1f)
            )
            results.add(DetectedBox(normPoints, normBounds, rotatedRect.angle.toFloat()))
        }
        
        // UI Heatmap Sync (Conservative Loop)
        try {
            for (i in heatmap.indices) { heatmap[i] = 0.0f }
            for (res in results) {
                val b = res.boundingBox
                val hL = (b.left * heatmapW).toInt().coerceIn(0, heatmapW - 1)
                val hT = (b.top * heatmapH).toInt().coerceIn(0, heatmapH - 1)
                val hR = (b.right * heatmapW).toInt().coerceIn(0, heatmapW - 1)
                val hB = (b.bottom * heatmapH).toInt().coerceIn(0, heatmapH - 1)
                for (y in hT..hB) {
                    for (x in hL..hR) {
                        val idx = y * heatmapW + x
                        if (idx >= 0 && idx < heatmap.size) heatmap[idx] = 1.0f
                    }
                }
            }
        } catch (e: Exception) { Log.e("TfLiteOcrUtils", "UI Sync error", e) }
        
        mask.release(); hierarchy.release(); sourceMat?.release()
        Log.i("TfLiteOcrUtils", "processDbNetOutput took ${System.currentTimeMillis() - t0}ms for ${results.size} boxes (Algorithm $algorithm)")
        return results
    }

    private fun expandInRoi(rect: RotatedRect, sourceMat: Mat, algorithm: String): Rect {
        val margin = (rect.size.height * 3.0).toInt()
        val roiL = max(0, (rect.center.x - rect.size.width/2 - margin).toInt())
        val roiT = max(0, (rect.center.y - rect.size.height/2 - margin).toInt())
        val roiR = min(sourceMat.cols(), (rect.center.x + rect.size.width/2 + margin).toInt())
        val roiB = min(sourceMat.rows(), (rect.center.y + rect.size.height/2 + margin).toInt())
        
        if (roiR <= roiL || roiB <= roiT) return Rect(rect.center.x.toInt(), rect.center.y.toInt(), rect.center.x.toInt(), rect.center.y.toInt())

        val roiMat = sourceMat.submat(roiT, roiB, roiL, roiR)
        val gray = Mat()
        Imgproc.cvtColor(roiMat, gray, Imgproc.COLOR_RGBA2GRAY)
        
        val expandedRect = if (algorithm == "C") {
            val edges = Mat()
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            val res = expandToEdges(translateToRoi(rect, roiL, roiT), edges, edges.rows(), edges.cols())
            edges.release()
            res
        } else {
            val mask = Mat()
            val polarity = detectPolarity(gray)
            if (polarity == Polarity.LIGHT_ON_DARK) Imgproc.threshold(gray, mask, 100.0, 255.0, Imgproc.THRESH_BINARY)
            else Imgproc.threshold(gray, mask, 150.0, 255.0, Imgproc.THRESH_BINARY_INV)
            val res = expandPerimeter(translateToRoi(rect, roiL, roiT), mask, mask.rows(), mask.cols())
            mask.release()
            res
        }
        
        gray.release(); roiMat.release()
        // Translate back to global space
        return Rect(expandedRect.left + roiL, expandedRect.top + roiT, expandedRect.right + roiL, expandedRect.bottom + roiT)
    }

    private fun translateToRoi(rect: RotatedRect, roiL: Int, roiT: Int): RotatedRect {
        return RotatedRect(Point(rect.center.x - roiL, rect.center.y - roiT), rect.size, rect.angle)
    }

    private fun expandToEdges(rect: RotatedRect, edgeMap: Mat, maxH: Int, maxW: Int): Rect {
        var minX = rect.center.x - rect.size.width/2.0
        var maxX = rect.center.x + rect.size.width/2.0
        var minY = rect.center.y - rect.size.height/2.0
        var maxY = rect.center.y + rect.size.height/2.0
        val hLimit = rect.size.height * 2.5; val vLimit = rect.size.height * 0.3
        val sMinX = minX; val sMaxX = maxX; val sMinY = minY; val sMaxY = maxY

        while (minY > 0 && (sMinY - minY) < vLimit) { if (checkLine(edgeMap, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true)) break; minY -= 1.0 }
        while (maxY < maxH - 1 && (maxY - sMaxY) < vLimit) { if (checkLine(edgeMap, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true)) break; maxY += 1.0 }
        while (minX > 0 && (sMinX - minX) < hLimit) { if (checkLine(edgeMap, minY.toInt(), maxY.toInt(), (minX - 1).toInt(), false)) break; minX -= 1.0 }
        while (maxX < maxW - 1 && (maxX - sMaxX) < hLimit) { if (checkLine(edgeMap, minY.toInt(), maxY.toInt(), (maxX + 1).toInt(), false)) break; maxX += 1.0 }
        return Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(maxW, maxX.toInt()), min(maxH, maxY.toInt()))
    }

    private fun expandPerimeter(rect: RotatedRect, mask: Mat, maxH: Int, maxW: Int): Rect {
        var minX = rect.center.x - rect.size.width/2.0
        var maxX = rect.center.x + rect.size.width/2.0
        var minY = rect.center.y - rect.size.height/2.0
        var maxY = rect.center.y + rect.size.height/2.0
        val hLimit = rect.size.height * 2.5; val vLimit = rect.size.height * 0.3
        val sX = minX; val sXX = maxX; val sY = minY; val sYY = maxY
        var changed = true
        while (changed) {
            changed = false
            if (minY > 0 && (sY - minY) < vLimit && checkLine(mask, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true)) { minY -= 1.0; changed = true }
            if (maxY < maxH - 1 && (maxY - sYY) < vLimit && checkLine(mask, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true)) { maxY += 1.0; changed = true }
            if (minX > 0 && (sX - minX) < hLimit && checkLine(mask, minY.toInt(), maxY.toInt(), (minX - 1).toInt(), false)) { minX -= 1.0; changed = true }
            if (maxX < maxW - 1 && (maxX - sXX) < hLimit && checkLine(mask, minY.toInt(), maxY.toInt(), (maxX + 1).toInt(), false)) { maxX += 1.0; changed = true }
        }
        return Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(maxW, maxX.toInt()), min(maxH, maxY.toInt()))
    }

    private fun checkLine(mask: Mat, start: Int, end: Int, fixed: Int, horizontal: Boolean): Boolean {
        val maxDim = if (horizontal) mask.cols() else mask.rows()
        val fixedDim = if (horizontal) mask.rows() else mask.cols()
        if (fixed < 0 || fixed >= fixedDim) return false
        for (i in start..end) {
            if (i < 0 || i >= maxDim) continue
            val pixel = if (horizontal) mask.get(fixed, i)[0] else mask.get(i, fixed)[0]
            if (pixel > 0) return true
        }
        return false
    }

    private fun unclipBox(points: Array<Point>, ratio: Float): List<Point> {
        val area = calculatePolygonArea(points); val perimeter = calculatePolygonPerimeter(points)
        if (perimeter <= 0) return points.toList()
        val distance = (area * ratio / perimeter)
        val center = Point(0.0, 0.0); for (p in points) { center.x += p.x; center.y += p.y }; center.x /= 4.0; center.y /= 4.0
        return points.map { p ->
            val dx = p.x - center.x; val dy = p.y - center.y; val mag = sqrt(dx * dx + dy * dy)
            if (mag == 0.0) p else Point(p.x + (dx / mag * distance), p.y + (dy / mag * distance))
        }
    }

    private fun calculatePolygonArea(points: Array<Point>): Double {
        var area = 0.0
        for (i in points.indices) { val next = (i + 1) % points.size; area += points[i].x * points[next].y - points[next].x * points[i].y }
        return Math.abs(area) / 2.0
    }

    private fun calculatePolygonPerimeter(points: Array<Point>): Double {
        var perimeter = 0.0
        for (i in points.indices) { val next = (i + 1) % points.size; val dx = points[i].x - points[next].x; val dy = points[i].y - points[next].y; perimeter += sqrt(dx * dx + dy * dy) }
        return perimeter
    }
}
