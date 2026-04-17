package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a detected text region with both rotated points and axis-aligned bounds.
 */
data class DetectedBox(
    val points: List<Point>,
    val boundingBox: Rect,
    val angle: Float
)

/**
 * Shared utilities for processing TFLite OCR and Detection model outputs.
 * Implementation of Phase 7: Distributed Adaptive Comparison.
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
        val corners = listOf(Point(0.0,0.0), Point(mat.cols()-1.0, 0.0), Point(0.0, mat.rows()-1.0), Point(mat.cols()-1.0, mat.rows()-1.0))
        var sum = 0.0
        for (p in corners) { sum += mat.get(p.y.toInt(), p.x.toInt())[0] }
        val avgCorner = sum / 4.0
        return if (avgCorner > 127) Polarity.DARK_ON_LIGHT else Polarity.LIGHT_ON_DARK
    }

    /**
     * Processes DBNet detection heatmap into rotated text polygons.
     * Uses one of two adaptive expansion algorithms for side-by-side comparison.
     */
    fun processDbNetOutput(
        heatmap: FloatArray,
        width: Int,
        height: Int,
        sourceBitmap: Bitmap? = null,
        algorithm: String = "A" // "A" = Stroke Follower, "B" = Perimeter Expander
    ): List<DetectedBox> {
        val mask = Mat(height, width, CvType.CV_8UC1)
        val data = ByteArray(width * height)
        for (i in heatmap.indices) {
            data[i] = if (heatmap[i] > 0.2f) 255.toByte() else 0.toByte()
        }
        mask.put(0, 0, data)

        // Pre-process source image if available
        var textMask: Mat? = null
        if (sourceBitmap != null) {
            val sourceMat = Mat()
            Utils.bitmapToMat(sourceBitmap, sourceMat)
            val gray = Mat()
            Imgproc.cvtColor(sourceMat, gray, Imgproc.COLOR_RGBA2GRAY)
            val resizedGray = Mat()
            Imgproc.resize(gray, resizedGray, Size(width.toDouble(), height.toDouble()))
            
            val polarity = detectPolarity(resizedGray)
            textMask = Mat()
            if (polarity == Polarity.LIGHT_ON_DARK) {
                Imgproc.threshold(resizedGray, textMask, 100.0, 255.0, Imgproc.THRESH_BINARY)
            } else {
                Imgproc.threshold(resizedGray, textMask, 150.0, 255.0, Imgproc.THRESH_BINARY_INV)
            }
            
            if (algorithm == "A") {
                // Algorithm A: Morphological Reconstruction (Follow the Stroke)
                // Seed = mask, Constraint = textMask. 
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
                for (i in 0 until 6) { // 6 cycles of constrained growth
                    Imgproc.dilate(mask, mask, kernel)
                    Core.bitwise_and(mask, textMask, mask)
                }
                kernel.release()
            }
            sourceMat.release(); gray.release(); resizedGray.release()
        }

        // Synchronize heatmap for UI
        val dilatedData = ByteArray(width * height)
        mask.get(0, 0, dilatedData)
        for (i in heatmap.indices) { heatmap[i] = if (dilatedData[i] != 0.toByte()) 1.0f else 0.0f }

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val results = mutableListOf<DetectedBox>()
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < 10) continue 

            val rotatedRect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
            val points = arrayOf(Point(), Point(), Point(), Point())
            rotatedRect.points(points)

            var bounds: Rect
            if (algorithm == "B" && textMask != null) {
                // Algorithm B: Perimeter Pixel Check
                bounds = expandPerimeter(rotatedRect, textMask, height, width)
            } else {
                // Standard DBNet unclip as fallback
                val expandedPoints = unclipBox(points, 2.5f)
                var minX = width.toDouble(); var minY = height.toDouble(); var maxX = 0.0; var maxY = 0.0
                for (p in expandedPoints) {
                    minX = min(minX, p.x); minY = min(minY, p.y); maxX = max(maxX, p.x); maxY = max(maxY, p.y)
                }
                bounds = Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(width, maxX.toInt()), min(height, maxY.toInt()))
            }
            
            results.add(DetectedBox(points.toList(), bounds, rotatedRect.angle.toFloat()))
        }
        
        mask.release(); hierarchy.release(); textMask?.release()
        return results
    }

    /**
     * Algorithm B: Expands the bounding box pixel-by-pixel if the perimeter contains text.
     */
    private fun expandPerimeter(rect: RotatedRect, textMask: Mat, maxH: Int, maxW: Int): Rect {
        var minX = rect.center.x - rect.size.width/2.0
        var maxX = rect.center.x + rect.size.width/2.0
        var minY = rect.center.y - rect.size.height/2.0
        var maxY = rect.center.y + rect.size.height/2.0
        
        val startH = maxY - minY
        val limitH = startH * 4.0
        
        // Expand loop
        var changed = true
        while (changed && (maxY - minY) < limitH) {
            changed = false
            // Check top edge
            if (minY > 0 && checkLine(textMask, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true)) { minY -= 1.0; changed = true }
            // Check bottom edge
            if (maxY < maxH - 1 && checkLine(textMask, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true)) { maxY += 1.0; changed = true }
            // Check left edge
            if (minX > 0 && checkLine(textMask, minY.toInt(), maxY.toInt(), (minX - 1).toInt(), false)) { minX -= 1.0; changed = true }
            // Check right edge
            if (maxX < maxW - 1 && checkLine(textMask, minY.toInt(), maxY.toInt(), (maxX + 1).toInt(), false)) { maxX += 1.0; changed = true }
        }

        return Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(maxW, maxX.toInt()), min(maxH, maxY.toInt()))
    }

    private fun checkLine(mask: Mat, start: Int, end: Int, fixed: Int, horizontal: Boolean): Boolean {
        for (i in start..end) {
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
            val dx = p.x - center.x; val dy = p.y - center.y; val mag = Math.sqrt(dx * dx + dy * dy)
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
        for (i in points.indices) { val next = (i + 1) % points.size; val dx = points[i].x - points[next].x; val dy = points[i].y - points[next].y; perimeter += Math.sqrt(dx * dx + dy * dy) }
        return perimeter
    }
}
