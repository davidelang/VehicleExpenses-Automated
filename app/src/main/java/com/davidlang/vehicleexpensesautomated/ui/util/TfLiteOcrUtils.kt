package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Rect
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Shared utilities for processing TFLite OCR and Detection model outputs.
 */
object TfLiteOcrUtils {

    /**
     * Decodes raw 3D float array (logits) from a TFLite OCR model using CTC Greedy Search.
     * @param logits Shape: [1][timeSteps][numClasses]
     * @param dictionary List of characters (excluding blank)
     * @param blankIndex The index of the CTC blank/spacer token (usually 0 or numClasses-1)
     */
    fun decodeCtcGreedy(logits: Array<Array<FloatArray>>, dictionary: List<String>, blankIndex: Int = 0): String {
        val result = StringBuilder()
        val sequence = logits[0]
        val timeSteps = sequence.size
        val numClasses = sequence[0].size
        
        var lastIndex = -1
        val rawIndices = mutableListOf<Int>()

        for (t in 0 until timeSteps) {
            var maxIndex = 0
            var maxVal = sequence[t][0]
            
            for (c in 1 until numClasses) {
                if (sequence[t][c] > maxVal) {
                    maxVal = sequence[t][c]
                    maxIndex = c
                }
            }
            
            rawIndices.add(maxIndex)

            // CTC Rules: Ignore blanks and collapse consecutive duplicates
            if (maxIndex != blankIndex && maxIndex != lastIndex) {
                val dictIndex = if (blankIndex == 0) maxIndex - 1 else maxIndex
                if (dictIndex >= 0 && dictIndex < dictionary.size) {
                    result.append(dictionary[dictIndex])
                }
            }
            lastIndex = maxIndex
        }
        
        if (result.isEmpty()) {
            android.util.Log.d("TfLiteOcrUtils", "Decode empty. Raw indices: ${rawIndices.take(20)}... BlankIndex: $blankIndex")
        }
        
        return result.toString()
    }

    /**
     * Processes DBNet detection heatmap into text polygons.
     * @param heatmap Shape: [1][1][H][W] or flat array
     * @param width Width of the heatmap
     * @param height Height of the heatmap
     * @param thresh Binary threshold (default 0.3)
     * @param unclipRatio Expansion ratio (default 1.5)
     */
    fun processDbNetOutput(
        heatmap: FloatArray,
        width: Int,
        height: Int,
        thresh: Float = 0.3f,
        unclipRatio: Float = 1.5f
    ): List<Rect> {
        val mask = Mat(height, width, CvType.CV_8UC1)
        val data = ByteArray(width * height)
        for (i in heatmap.indices) {
            data[i] = if (heatmap[i] > thresh) 255.toByte() else 0.toByte()
        }
        mask.put(0, 0, data)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val results = mutableListOf<Rect>()
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < 16) continue

            // 1. Fit Rotated Rect
            val rotatedRect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
            val points = arrayOf(Point(), Point(), Point(), Point())
            rotatedRect.points(points)

            // 2. Unclip (Expansion)
            val expandedRect = unclipBox(points, unclipRatio)
            
            // 3. Convert back to axis-aligned Rect for standard Android use
            var minX = width.toDouble(); var minY = height.toDouble()
            var maxX = 0.0; var maxY = 0.0
            for (p in expandedRect) {
                minX = min(minX, p.x); minY = min(minY, p.y)
                maxX = max(maxX, p.x); maxY = max(maxY, p.y)
            }
            
            results.add(Rect(
                max(0, minX.toInt()),
                max(0, minY.toInt()),
                min(width, maxX.toInt()),
                min(height, maxY.toInt())
            ))
        }
        
        mask.release(); hierarchy.release()
        return results
    }

    /**
     * Expands a text box to prevent digit clipping.
     */
    private fun unclipBox(points: Array<Point>, ratio: Float): List<Point> {
        val area = calculatePolygonArea(points)
        val perimeter = calculatePolygonPerimeter(points)
        if (perimeter <= 0) return points.toList()
        
        val distance = (area * ratio / perimeter).toDouble()
        
        // Simple expansion logic: scale outward from center
        val center = Point(0.0, 0.0)
        for (p in points) { center.x += p.x; center.y += p.y }
        center.x /= 4.0; center.y /= 4.0
        
        return points.map { p ->
            val dx = p.x - center.x
            val dy = p.y - center.y
            val mag = Math.sqrt(dx * dx + dy * dy)
            if (mag == 0.0) p
            else Point(p.x + (dx / mag * distance), p.y + (dy / mag * distance))
        }
    }

    private fun calculatePolygonArea(points: Array<Point>): Double {
        var area = 0.0
        for (i in points.indices) {
            val next = (i + 1) % points.size
            area += points[i].x * points[next].y - points[next].x * points[i].y
        }
        return Math.abs(area) / 2.0
    }

    private fun calculatePolygonPerimeter(points: Array<Point>): Double {
        var perimeter = 0.0
        for (i in points.indices) {
            val next = (i + 1) % points.size
            val dx = points[i].x - points[next].x
            val dy = points[i].y - points[next].y
            perimeter += Math.sqrt(dx * dx + dy * dy)
        }
        return perimeter
    }
}
