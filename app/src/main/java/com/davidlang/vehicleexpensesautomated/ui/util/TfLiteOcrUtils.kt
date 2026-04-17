package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Rect
import android.util.Log
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
 */
object TfLiteOcrUtils {

    /**
     * Decodes raw 3D float array (logits) from a TFLite OCR model using CTC Greedy Search.
     * Returns Pair(Decoded String, Average Confidence)
     */
    fun decodeCtcGreedy(logits: Array<Array<FloatArray>>, dictionary: List<String>, blankIndex: Int = 0): Pair<String, Float> {
        val result = StringBuilder()
        val sequence = logits[0]
        val timeSteps = sequence.size
        val numClasses = sequence[0].size
        
        var lastIndex = -1
        var totalConf = 0f
        var count = 0

        for (t in 0 until timeSteps) {
            var maxIndex = 0
            var maxVal = sequence[t][0]
            
            for (c in 1 until numClasses) {
                if (sequence[t][c] > maxVal) {
                    maxVal = sequence[t][c]
                    maxIndex = c
                }
            }

            if (maxIndex != blankIndex && maxIndex != lastIndex) {
                val dictIndex = if (blankIndex == 0) maxIndex - 1 else maxIndex
                if (dictIndex >= 0 && dictIndex < dictionary.size) {
                    result.append(dictionary[dictIndex])
                    totalConf += maxVal
                    count++
                }
            }
            lastIndex = maxIndex
        }
        val avgConf = if (count > 0) totalConf / count else 0f
        return Pair(result.toString(), avgConf)
    }

    /**
     * Processes DBNet detection heatmap into rotated text polygons.
     * Implements AGGRESSIVE DILATION and MORPHOLOGICAL CLOSING for sparse signals.
     */
    fun processDbNetOutput(
        heatmap: FloatArray,
        width: Int,
        height: Int,
        thresh: Float = 0.3f,
        unclipRatio: Float = 2.5f
    ): List<DetectedBox> {
        // 1. MEMORY-SAFE STATS: Avoid large array copy/sort
        var maxProb = 0f
        var sumProb = 0.0
        
        // Use a simpler approach for P99 estimate to save memory
        var p99Estimate = 0f
        val sampleSize = min(heatmap.size, 1000)
        val step = heatmap.size / sampleSize
        val samples = FloatArray(sampleSize)
        for (i in 0 until sampleSize) {
            val v = heatmap[i * step]
            samples[i] = v
            if (v > maxProb) maxProb = v
            sumProb += v
        }
        samples.sort()
        p99Estimate = samples[(sampleSize * 0.99).toInt()]
        val meanProb = sumProb / sampleSize
        Log.i("TfLiteOcrUtils", "FORENSIC (Sample): Max=%.3f, Mean=%.4f, P99=%.3f".format(maxProb, meanProb, p99Estimate))
        
        // 2. ADAPTIVE THRESHOLD
        val effectiveThresh = if (p99Estimate > 0.8f) 0.05f else max(0.05f, min(thresh, p99Estimate * 0.5f))
        
        val mask = Mat(height, width, CvType.CV_8UC1)
        val data = ByteArray(width * height)
        for (i in heatmap.indices) {
            data[i] = if (heatmap[i] > effectiveThresh) 255.toByte() else 0.toByte()
        }
        mask.put(0, 0, data)

        // 3. ASYMMETRIC DILATION: 9x5 kernel to bridge characters horizontally without merging lines vertically
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 5.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.dilate(mask, mask, kernel)
        kernel.release()

        // 4. SYNCHRONIZE HEATMAP: Copy dilated mask back to heatmap array for UI rendering
        val dilatedData = ByteArray(width * height)
        mask.get(0, 0, dilatedData)
        for (i in heatmap.indices) {
            // Set heatmap value to 1.0f if the pixel is part of a dilated contour, else 0.0f
            heatmap[i] = if (dilatedData[i] != 0.toByte()) 1.0f else 0.0f
        }

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

            val expandedPoints = unclipBox(points, unclipRatio)
            
            var minX = width.toDouble(); var minY = height.toDouble()
            var maxX = 0.0; var maxY = 0.0
            for (p in expandedPoints) {
                minX = min(minX, p.x); minY = min(minY, p.y)
                maxX = max(maxX, p.x); maxY = max(maxY, p.y)
            }
            
            val bounds = Rect(
                max(0, minX.toInt()),
                max(0, minY.toInt()),
                min(width, maxX.toInt()),
                min(height, maxY.toInt())
            )
            
            results.add(DetectedBox(expandedPoints, bounds, rotatedRect.angle.toFloat()))
        }
        
        mask.release(); hierarchy.release()
        return results
    }

    /**
     * Expands a text box to prevent digit clipping using the Area/Perimeter formula.
     */
    private fun unclipBox(points: Array<Point>, ratio: Float): List<Point> {
        val area = calculatePolygonArea(points)
        val perimeter = calculatePolygonPerimeter(points)
        if (perimeter <= 0) return points.toList()
        
        val distance = (area * ratio / perimeter)
        
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
