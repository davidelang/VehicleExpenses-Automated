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
 * Shared utilities for processing TFLite OCR and Detection model outputs.
 */
object TfLiteOcrUtils {

    enum class Polarity { LIGHT_ON_DARK, DARK_ON_LIGHT }

    fun decodeCtcGreedy(logits: Array<Array<FloatArray>>, dictionary: List<String>, blankIndex: Int = 0): Pair<String, Float> {
        val result = StringBuilder(); val sequence = logits[0]
        val timeSteps = sequence.size; val numClasses = sequence[0].size
        var lastIndex = -1; var totalConf = 0f; var count = 0
        for (t in 0 until timeSteps) {
            var maxIndex = 0; var maxVal = sequence[t][0]
            for (c in 1 until numClasses) { if (sequence[t][c] > maxVal) { maxVal = sequence[t][c]; maxIndex = c } }
            if (maxIndex != blankIndex && maxIndex != lastIndex) {
                val dictIndex = if (blankIndex == 0) maxIndex - 1 else maxIndex
                if (dictIndex >= 0 && dictIndex < dictionary.size) { result.append(dictionary[dictIndex]); totalConf += maxVal; count++ }
            }
            lastIndex = maxIndex
        }
        return Pair(result.toString(), if (count > 0) totalConf / count else 0f)
    }

    fun detectPolarity(mat: Mat): Polarity {
        val samples = mutableListOf<Double>(); val w = mat.cols(); val h = mat.rows()
        if (w < 1 || h < 1) return Polarity.DARK_ON_LIGHT
        samples.add(mat.get(0, 0)[0]); samples.add(mat.get(0, (w-1).coerceAtLeast(0))[0])
        samples.add(mat.get((h-1).coerceAtLeast(0), 0)[0]); samples.add(mat.get((h-1).coerceAtLeast(0), (w-1).coerceAtLeast(0))[0])
        return if (samples.average() > 127) Polarity.DARK_ON_LIGHT else Polarity.LIGHT_ON_DARK
    }

    /**
     * Processes DBNet heatmap into vector discovery boxes with Zero-Anchor math.
     * @param scale Ratio of Resized/Original
     */
    fun processDbNetOutput(
        heatmap: FloatArray, heatmapW: Int, heatmapH: Int,
        scale: Float = 1.0f,
        sourceBitmap: Bitmap? = null, algorithm: String = "C" 
    ): DbNetResult {
        if (heatmapW <= 0 || heatmapH <= 0 || heatmap.size < heatmapW * heatmapH) return DbNetResult(emptyList(), emptyList())
        val mask = Mat(heatmapH, heatmapW, CvType.CV_8UC1)
        val data = ByteArray(heatmapW * heatmapH)
        for (i in heatmap.indices) { data[i] = if (heatmap[i] > 0.2f) 255.toByte() else 0.toByte() }
        mask.put(0, 0, data)

        val sourceMat = if (sourceBitmap != null) {
            try { val mat = Mat(); Utils.bitmapToMat(sourceBitmap, mat); mat } catch (e: Exception) { null }
        } else null

        val contours = mutableListOf<MatOfPoint>(); val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val rawBoxes = mutableListOf<DetectedBox>()
        val refinedBoxes = mutableListOf<DetectedBox>()
        val sourceW = sourceBitmap?.width?.toDouble() ?: heatmapW.toDouble()
        val sourceH = sourceBitmap?.height?.toDouble() ?: heatmapH.toDouble()
        
        // ZERO-ANCHOR SCALE: Map buffer pixels directly back to source pixels
        val invScale = 1.0 / scale.toDouble()

        for (contour in contours) {
            if (Imgproc.contourArea(contour) < 10) continue 
            val rotatedRect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
            
            // 1. Raw Discovery Box (Model Suspicion) - Normalize to Source
            val rawPoints = arrayOf(Point(), Point(), Point(), Point()); rotatedRect.points(rawPoints)
            val normRawPoints = rawPoints.map { Point(it.x * invScale / sourceW, it.y * invScale / sourceH) }
            val rawBounds = RectF(
                (normRawPoints.minOf { it.x }.toFloat()).coerceIn(0f, 1f),
                (normRawPoints.minOf { it.y }.toFloat()).coerceIn(0f, 1f),
                (normRawPoints.maxOf { it.x }.toFloat()).coerceIn(0f, 1f),
                (normRawPoints.maxOf { it.y }.toFloat()).coerceIn(0f, 1f)
            )
            rawBoxes.add(DetectedBox(rawPoints.toList(), rawBounds, rotatedRect.angle.toFloat()))

            // 2. High Precision Box (ROI Expansion)
            val redFloorPixels = Rect(
                (rawBounds.left * sourceW).toInt(), (rawBounds.top * sourceH).toInt(),
                (rawBounds.right * sourceW).toInt(), (rawBounds.bottom * sourceH).toInt()
            )

            val sourceRect = RotatedRect(Point(rotatedRect.center.x * invScale, rotatedRect.center.y * invScale), Size(rotatedRect.size.width * invScale, rotatedRect.size.height * invScale), rotatedRect.angle)
            
            var bounds: Rect
            if (sourceMat != null) {
                bounds = expandInRoi(sourceRect, sourceMat, algorithm, redFloorPixels)
            } else {
                bounds = redFloorPixels
            }
            
            // FINAL ENFORCEMENT: Result MUST encompass Red Floor
            bounds.union(redFloorPixels)
            
            val normRefinedBounds = RectF(
                (bounds.left.toFloat() / sourceW.toFloat()).coerceIn(0f, 1f), (bounds.top.toFloat() / sourceH.toFloat()).coerceIn(0f, 1f),
                (bounds.right.toFloat() / sourceW.toFloat()).coerceIn(0f, 1f), (bounds.bottom.toFloat() / sourceH.toFloat()).coerceIn(0f, 1f)
            )
            refinedBoxes.add(DetectedBox(emptyList(), normRefinedBounds, rotatedRect.angle.toFloat()))
        }
        
        mask.release(); hierarchy.release(); sourceMat?.release()
        return DbNetResult(rawBoxes, refinedBoxes)
    }

    private fun expandInRoi(rect: RotatedRect, sourceMat: Mat, algorithm: String, redFloor: Rect): Rect {
        val margin = (rect.size.height * 3.0).toInt()
        val roiL = max(0, (rect.center.x - rect.size.width/2 - margin).toInt())
        val roiT = max(0, (rect.center.y - rect.size.height/2 - margin).toInt())
        val roiR = min(sourceMat.cols(), (rect.center.x + rect.size.width/2 + margin).toInt())
        val roiB = min(sourceMat.rows(), (rect.center.y + rect.size.height/2 + margin).toInt())
        if (roiR <= roiL || roiB <= roiT) return redFloor
        
        val roiMat = sourceMat.submat(roiT, roiB, roiL, roiR)
        val gray = Mat(); if (roiMat.channels() > 1) Imgproc.cvtColor(roiMat, gray, Imgproc.COLOR_RGBA2GRAY) else roiMat.copyTo(gray)
        
        // Translate Red Floor to local ROI coordinates
        val localRedFloor = Rect(redFloor.left - roiL, redFloor.top - roiT, redFloor.right - roiL, redFloor.bottom - roiT)
        
        val expandedRect = when (algorithm) {
            "A" -> {
                // ALGORITHM A: High-Res Density Projections (Aggressive)
                val mask = Mat(); Imgproc.adaptiveThreshold(gray, mask, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 15, 2.0)
                val res = expandByProjection(localRedFloor, mask)
                mask.release(); res
            }
            "B" -> {
                // ALGORITHM B: Binary Perimeter Growth (Aggressive)
                val mask = Mat(); val pol = detectPolarity(gray)
                if (pol == Polarity.LIGHT_ON_DARK) Imgproc.threshold(gray, mask, 80.0, 255.0, Imgproc.THRESH_BINARY)
                else Imgproc.threshold(gray, mask, 170.0, 255.0, Imgproc.THRESH_BINARY_INV)
                val res = expandPerimeter(localRedFloor, mask, mask.rows(), mask.cols())
                mask.release(); res
            }
            else -> {
                // ALGORITHM C: Valley-Stop (Expanded Search)
                val edges = Mat(); Imgproc.Canny(gray, edges, 10.0, 40.0)
                val res = expandToEdges(localRedFloor, edges, edges.rows(), edges.cols())
                edges.release(); res
            }
        }
        
        gray.release(); roiMat.release()
        return Rect(expandedRect.left + roiL, expandedRect.top + roiT, expandedRect.right + roiL, expandedRect.bottom + roiT)
    }

    private fun expandByProjection(redFloor: Rect, mask: Mat): Rect {
        val hProj = Mat(); Core.reduce(mask, hProj, 1, Core.REDUCE_SUM, CvType.CV_32F)
        val vProj = Mat(); Core.reduce(mask, vProj, 0, Core.REDUCE_SUM, CvType.CV_32F)
        
        var minX = redFloor.left.toDouble(); var maxX = redFloor.right.toDouble()
        var minY = redFloor.top.toDouble(); var maxY = redFloor.bottom.toDouble()
        
        // AGGRESSIVE: noise floor = 5% of max possible ink
        val hThreshold = mask.cols() * 255.0 * 0.05; val vThreshold = mask.rows() * 255.0 * 0.05
        
        // Expand horizontally first (Text flow)
        while (minX > 0 && vProj.get(0, minX.toInt() - 1)[0] > vThreshold) minX -= 1.0
        while (maxX < mask.cols() - 1 && vProj.get(0, maxX.toInt() + 1)[0] > vThreshold) maxX += 1.0
        while (minY > 0 && hProj.get(minY.toInt() - 1, 0)[0] > hThreshold) minY -= 1.0
        while (maxY < mask.rows() - 1 && hProj.get(maxY.toInt() + 1, 0)[0] > hThreshold) maxY += 1.0
        
        hProj.release(); vProj.release()
        return Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(mask.cols(), maxX.toInt()), min(mask.rows(), maxY.toInt()))
    }

    private fun expandToEdges(redFloor: Rect, edgeMap: Mat, maxH: Int, maxW: Int): Rect {
        var minX = redFloor.left.toDouble(); var maxX = redFloor.right.toDouble()
        var minY = redFloor.top.toDouble(); var maxY = redFloor.bottom.toDouble()
        
        // AGGRESSIVE: 400% horizontal expansion limit
        val hL = (maxX - minX) * 4.0; val vL = (maxY - minY) * 1.0
        val sX = minX; val sXX = maxX; val sY = minY; val sYY = maxY
        
        while (minY > 0 && (sY - minY) < vL) { if (checkLine(edgeMap, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true)) break; minY -= 1.0 }
        while (maxY < maxH - 1 && (maxY - sYY) < vL) { if (checkLine(edgeMap, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true)) break; maxY += 1.0 }
        while (minX > 0 && (sX - minX) < hL) { if (checkLine(edgeMap, minY.toInt(), maxY.toInt(), (minX - 1).toInt(), false)) break; minX -= 1.0 }
        while (maxX < maxW - 1 && (maxX - sXX) < hL) { if (checkLine(edgeMap, minY.toInt(), maxY.toInt(), (maxX + 1).toInt(), false)) break; maxX += 1.0 }
        
        return Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(maxW, maxX.toInt()), min(maxH, maxY.toInt()))
    }

    private fun expandPerimeter(redFloor: Rect, mask: Mat, maxH: Int, maxW: Int): Rect {
        var minX = redFloor.left.toDouble(); var maxX = redFloor.right.toDouble()
        var minY = redFloor.top.toDouble(); var maxY = redFloor.bottom.toDouble()
        
        val hL = (maxX - minX) * 4.0; val vL = (maxY - minY) * 1.0; val sX = minX; val sXX = maxX; val sY = minY; val sYY = maxY
        var changed = true
        while (changed) {
            changed = false
            if (minY > 0 && (sY - minY) < vL && checkLine(mask, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true)) { minY -= 1.0; changed = true }
            if (maxY < maxH - 1 && (maxY - sYY) < vL && checkLine(mask, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true)) { maxY += 1.0; changed = true }
            if (minX > 0 && (sX - minX) < hL && checkLine(mask, minY.toInt(), maxY.toInt(), (minX - 1).toInt(), false)) { minX -= 1.0; changed = true }
            if (maxX < maxW - 1 && (maxX - sXX) < hL && checkLine(mask, minY.toInt(), maxY.toInt(), (maxX + 1).toInt(), false)) { maxX += 1.0; changed = true }
        }
        return Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(maxW, maxX.toInt()), min(maxH, maxY.toInt()))
    }

    private fun checkLine(mask: Mat, start: Int, end: Int, fixed: Int, horizontal: Boolean): Boolean {
        val maxDim = if (horizontal) mask.cols() else mask.rows(); val fixedDim = if (horizontal) mask.rows() else mask.cols()
        if (fixed < 0 || fixed >= fixedDim) return false
        for (i in start..end) { if (i < 0 || i >= maxDim) continue; val pixel = if (horizontal) mask.get(fixed, i)[0] else mask.get(i, fixed)[0]; if (pixel > 0) return true }
        return false
    }

    private fun calculatePolygonArea(points: Array<Point>): Double {
        var area = 0.0; for (i in points.indices) { val next = (i + 1) % points.size; area += points[i].x * points[next].y - points[next].x * points[i].y }
        return Math.abs(area) / 2.0
    }

    private fun calculatePolygonPerimeter(points: Array<Point>): Double {
        var perimeter = 0.0; for (i in points.indices) { val next = (i + 1) % points.size; val dx = points[i].x - points[next].x; val dy = points[i].y - points[next].y; perimeter += sqrt(dx * dx + dy * dy) }
        return perimeter
    }
}
