package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
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
     * Phase 23: Intelligent Split Filtering (Prevent shattering).
     */
    fun processDbNetOutput(
        heatmap: FloatArray, heatmapW: Int, heatmapH: Int,
        scale: Float = 1.0f,
        sourceBitmap: Bitmap? = null, algorithm: String = "C" 
    ): DbNetResult {
        val tDiscoveryStart = System.currentTimeMillis()
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
        val invScale = 1.0 / scale.toDouble()

        for (contour in contours) {
            if (Imgproc.contourArea(contour) < 10) continue 
            val rotatedRect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
            val rectBounds = rotatedRect.boundingRect()
            val aspect = rectBounds.width.toDouble() / rectBounds.height.toDouble()
            
            // Phase 23: Balanced Split Trigger (Word gaps vs Character gaps)
            if (aspect > 2.2 && sourceMat != null) {
                val roiL = (rectBounds.x * invScale).toInt(); val roiT = (rectBounds.y * invScale).toInt()
                val roiW = (rectBounds.width * invScale).toInt(); val roiH = (rectBounds.height * invScale).toInt()
                
                if (roiL >= 0 && (roiL + roiW) <= sourceMat.cols() && roiT >= 0 && (roiT + roiH) <= sourceMat.rows()) {
                    val highResRoi = sourceMat.submat(roiT, roiT + roiH, roiL, roiL + roiW)
                    val splitIndex = findStableHighResValley(highResRoi)
                    highResRoi.release()
                    
                    if (splitIndex != -1) {
                        // SANITY CHECK: Ensure resulting sub-blobs are not too narrow (character fragments)
                        if (splitIndex > 10 && (roiW - splitIndex) > 10) {
                            val splitHeatmapX = (splitIndex.toDouble() * scale).toInt()
                            val leftRect = org.opencv.core.Rect(rectBounds.x, rectBounds.y, splitHeatmapX, rectBounds.height)
                            val rightRect = org.opencv.core.Rect(rectBounds.x + splitHeatmapX, rectBounds.y, rectBounds.width - splitHeatmapX, rectBounds.height)
                            processSubBlob(leftRect, invScale, sourceW, sourceH, sourceMat, algorithm, rawBoxes, refinedBoxes)
                            processSubBlob(rightRect, invScale, sourceW, sourceH, sourceMat, algorithm, rawBoxes, refinedBoxes)
                            Log.i("OcrSplit", "RESULT: BALANCED SPLIT Wide Blob [${rectBounds.width}x${rectBounds.height}] at $splitIndex")
                            continue
                        }
                    }
                }
            }
            
            processSubBlob(rotatedRect, invScale, sourceW, sourceH, sourceMat, algorithm, rawBoxes, refinedBoxes)
        }
        
        mask.release(); hierarchy.release(); sourceMat?.release()
        return DbNetResult(rawBoxes, refinedBoxes, discoveryTimeMs = System.currentTimeMillis() - tDiscoveryStart)
    }

    private fun findStableHighResValley(roi: Mat): Int {
        val gray = Mat(); if (roi.channels() > 1) Imgproc.cvtColor(roi, gray, Imgproc.COLOR_RGBA2GRAY) else roi.copyTo(gray)
        val hProj = FloatArray(gray.cols())
        for (x in 0 until gray.cols()) {
            var sum = 0.0
            for (y in 0 until gray.rows()) { sum += gray.get(y, x)[0] }
            hProj[x] = (sum / gray.rows()).toFloat()
        }
        
        val maxBrightness = hProj.maxOrNull() ?: 1.0f
        val valleyThreshold = maxBrightness * 0.45 
        val margin = (gray.cols() * 0.15).toInt()
        
        var bestValleyX = -1
        var maxGapWidth = 0; var currentGapWidth = 0; var currentGapStart = -1

        for (i in margin until (gray.cols() - margin)) {
            if (hProj[i] < valleyThreshold) {
                if (currentGapStart == -1) currentGapStart = i
                currentGapWidth++
            } else {
                if (currentGapWidth >= 15 && currentGapWidth > maxGapWidth) { // INCREASED FROM 5PX TO 15PX
                    maxGapWidth = currentGapWidth
                    bestValleyX = currentGapStart + (currentGapWidth / 2)
                }
                currentGapStart = -1; currentGapWidth = 0
            }
        }
        if (currentGapWidth >= 15 && currentGapWidth > maxGapWidth) {
            bestValleyX = currentGapStart + (currentGapWidth / 2)
        }
        
        gray.release()
        return if (bestValleyX != -1) bestValleyX else -1
    }

    private fun processSubBlob(rect: Any, invScale: Double, sourceW: Double, sourceH: Double, sourceMat: Mat?, algorithm: String, rawBoxes: MutableList<DetectedBox>, refinedBoxes: MutableList<DetectedBox>) {
        val rotatedRect = if (rect is RotatedRect) rect else {
            val r = rect as org.opencv.core.Rect
            RotatedRect(Point(r.x + r.width/2.0, r.y + r.height/2.0), Size(r.width.toDouble(), r.height.toDouble()), 0.0)
        }
        
        val rawPoints = arrayOf(Point(), Point(), Point(), Point()); rotatedRect.points(rawPoints)
        val normRawPoints = rawPoints.map { Point(it.x * invScale / sourceW, it.y * invScale / sourceH) }
        val rawBounds = RectF(
            (normRawPoints.minOf { it.x }.toFloat()).coerceIn(0f, 1f), (normRawPoints.minOf { it.y }.toFloat()).coerceIn(0f, 1f),
            (normRawPoints.maxOf { it.x }.toFloat()).coerceIn(0f, 1f), (normRawPoints.maxOf { it.y }.toFloat()).coerceIn(0f, 1f)
        )
        rawBoxes.add(DetectedBox(rawPoints.toList(), rawBounds, rotatedRect.angle.toFloat()))

        val redFloorPixels = android.graphics.Rect((rawBounds.left * sourceW).toInt(), (rawBounds.top * sourceH).toInt(), (rawBounds.right * sourceW).toInt(), (rawBounds.bottom * sourceH).toInt())
        val sourceRect = RotatedRect(Point(rotatedRect.center.x * invScale, rotatedRect.center.y * invScale), Size(rotatedRect.size.width * invScale, rotatedRect.size.height * invScale), rotatedRect.angle)
        
        val bounds = if (sourceMat != null) expandInRoi(sourceRect, sourceMat, algorithm, redFloorPixels) else redFloorPixels
        bounds.union(redFloorPixels)
        
        val normRefinedBounds = RectF(
            (bounds.left.toFloat() / sourceW.toFloat()).coerceIn(0f, 1f), (bounds.top.toFloat() / sourceH.toFloat()).coerceIn(0f, 1f),
            (bounds.right.toFloat() / sourceW.toFloat()).coerceIn(0f, 1f), (bounds.bottom.toFloat() / sourceH.toFloat()).coerceIn(0f, 1f)
        )
        refinedBoxes.add(DetectedBox(emptyList(), normRefinedBounds, rotatedRect.angle.toFloat()))
    }

    private fun expandInRoi(rect: RotatedRect, sourceMat: Mat, algorithm: String, redFloor: android.graphics.Rect): android.graphics.Rect {
        val margin = (rect.size.height * 3.0).toInt()
        val roiL = max(0, (rect.center.x - rect.size.width/2 - margin).toInt())
        val roiT = max(0, (rect.center.y - rect.size.height/2 - margin).toInt())
        val roiR = min(sourceMat.cols(), (rect.center.x + rect.size.width/2 + margin).toInt())
        val roiB = min(sourceMat.rows(), (rect.center.y + rect.size.height/2 + margin).toInt())
        if (roiR <= roiL || roiB <= roiT) return redFloor
        
        val roiMat = sourceMat.submat(roiT, roiB, roiL, roiR)
        val gray = Mat(); if (roiMat.channels() > 1) Imgproc.cvtColor(roiMat, gray, Imgproc.COLOR_RGBA2GRAY) else roiMat.copyTo(gray)
        val localRedFloor = android.graphics.Rect(redFloor.left - roiL, redFloor.top - roiT, redFloor.right - roiL, redFloor.bottom - roiT)
        
        val expandedRect = when (algorithm) {
            "B" -> {
                val mask = Mat(); val pol = detectPolarity(gray)
                if (pol == Polarity.LIGHT_ON_DARK) Imgproc.threshold(gray, mask, 80.0, 255.0, Imgproc.THRESH_BINARY)
                else Imgproc.threshold(gray, mask, 170.0, 255.0, Imgproc.THRESH_BINARY_INV)
                val res = expandPerimeter(localRedFloor, mask, mask.rows(), mask.cols())
                mask.release(); res
            }
            else -> expandByValleyStop(localRedFloor, gray)
        }
        
        gray.release(); roiMat.release()
        return android.graphics.Rect(expandedRect.left + roiL, expandedRect.top + roiT, expandedRect.right + roiL, expandedRect.bottom + roiT)
    }

    private fun expandByValleyStop(redFloor: android.graphics.Rect, gray: Mat): android.graphics.Rect {
        var minX = redFloor.left.toDouble(); var maxX = redFloor.right.toDouble()
        var minY = redFloor.top.toDouble(); var maxY = redFloor.bottom.toDouble()
        val hillBrightness = Core.mean(gray.submat(redFloor.top, redFloor.bottom, redFloor.left, redFloor.right)).`val`[0]
        val valleyThreshold = hillBrightness * 0.40 
        val maxH = gray.rows(); val maxW = gray.cols()
        val hL = (maxX - minX) * 4.0; val vL = (maxY - minY) * 1.0; val sX = minX; val sXX = maxX; val sY = minY; val sYY = maxY

        while (minY > 0 && (sY - minY) < vL) { if (isDarkGap(gray, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true) || getLineAverage(gray, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true) < valleyThreshold) break; minY -= 1.0 }
        while (maxY < maxH - 1 && (maxY - sYY) < vL) { if (isDarkGap(gray, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true) || getLineAverage(gray, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true) < valleyThreshold) break; maxY += 1.0 }
        while (minX > 0 && (sX - minX) < hL) { if (isDarkGap(gray, minY.toInt(), maxY.toInt(), (minX - 1).toInt(), false) || getLineAverage(gray, minY.toInt(), maxY.toInt(), (minX - 1).toInt(), false) < valleyThreshold) break; minX -= 1.0 }
        while (maxX < maxW - 1 && (maxX - sXX) < hL) { if (isDarkGap(gray, minY.toInt(), maxY.toInt(), (maxX + 1).toInt(), false) || getLineAverage(gray, minY.toInt(), maxY.toInt(), (maxX + 1).toInt(), false) < valleyThreshold) break; maxX += 1.0 }
        
        return android.graphics.Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(maxW, maxX.toInt()), min(maxH, maxY.toInt()))
    }

    private fun isDarkGap(mat: Mat, start: Int, end: Int, fixed: Int, horizontal: Boolean): Boolean {
        return getLineAverage(mat, start, end, fixed, horizontal) < 10.0
    }

    private fun getLineAverage(mat: Mat, start: Int, end: Int, fixed: Int, horizontal: Boolean): Double {
        var sum = 0.0; var count = 0; val maxD = if (horizontal) mat.cols() else mat.rows()
        if (fixed < 0 || fixed >= (if (horizontal) mat.rows() else mat.cols())) return 0.0
        for (i in start until end) { if (i < 0 || i >= maxD) continue; sum += if (horizontal) mat.get(fixed, i)[0] else mat.get(i, fixed)[0]; count++ }
        return if (count > 0) sum / count else 0.0
    }

    private fun expandPerimeter(redFloor: android.graphics.Rect, mask: Mat, maxH: Int, maxW: Int): android.graphics.Rect {
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
        return android.graphics.Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(maxW, maxX.toInt()), min(maxH, maxY.toInt()))
    }

    private fun checkLine(mask: Mat, start: Int, end: Int, fixed: Int, horizontal: Boolean): Boolean {
        val maxDim = if (horizontal) mask.cols() else mask.rows(); val fixedDim = if (horizontal) mask.rows() else mask.cols()
        if (fixed < 0 || fixed >= fixedDim) return false
        for (i in start until end) { if (i < 0 || i >= maxDim) continue; if (if (horizontal) mask.get(fixed, i)[0] > 0 else mask.get(i, fixed)[0] > 0) return true }
        return false
    }
}
