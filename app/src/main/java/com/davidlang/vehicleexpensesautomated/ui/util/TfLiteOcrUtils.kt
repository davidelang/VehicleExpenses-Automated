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
     * Phase 36: Split Retraction (Shrinking Red Boxes to Ink).
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
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val rawBoxes = mutableListOf<DetectedBox>()
        val intermediateRefined = mutableListOf<DetectedBox>()
        val sourceW = sourceBitmap?.width?.toDouble() ?: heatmapW.toDouble()
        val sourceH = sourceBitmap?.height?.toDouble() ?: heatmapH.toDouble()
        val invScale = 1.0 / scale.toDouble()

        for (contour in contours) {
            if (Imgproc.contourArea(contour) < 10) continue 
            val rotatedRect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
            val rectBounds = rotatedRect.boundingRect()
            val aspect = rectBounds.width.toDouble() / rectBounds.height.toDouble()
            
            if (aspect > 2.2 && sourceMat != null) {
                val roiL = (rectBounds.x * invScale).toInt(); val roiT = (rectBounds.y * invScale).toInt()
                val roiW = (rectBounds.width * invScale).toInt(); val roiH = (rectBounds.height * invScale).toInt()
                
                if (roiL >= 0 && (roiL + roiW) <= sourceMat.cols() && roiT >= 0 && (roiT + roiH) <= sourceMat.rows()) {
                    val highResRoi = sourceMat.submat(roiT, roiT + roiH, roiL, roiL + roiW)
                    val splitResult = findProportionalGapAndRetract(highResRoi)
                    highResRoi.release()
                    
                    if (splitResult != null) {
                        val (gapCenter, leftRetract, rightRetract) = splitResult
                        
                        // Map the High-Res retractions back to the Heatmap coordinate space
                        val splitHeatmapX = (gapCenter.toDouble() * scale).toInt()
                        val leftRetractHeatmapX = (leftRetract.toDouble() * scale).toInt()
                        val rightRetractHeatmapX = (rightRetract.toDouble() * scale).toInt()
                        
                        // Ensure minimum fragment width (40% of height) to prevent shattering '0's
                        val minFragmentWidth = rectBounds.height * 0.40 
                        if (leftRetractHeatmapX > minFragmentWidth && (rectBounds.width - rightRetractHeatmapX) > minFragmentWidth) {
                            
                            // Left Box: Starts at 0, ends at leftRetract (shrunken from gapCenter)
                            val leftRect = org.opencv.core.Rect(rectBounds.x, rectBounds.y, leftRetractHeatmapX, rectBounds.height)
                            // Right Box: Starts at rightRetract, ends at width
                            val rightRect = org.opencv.core.Rect(rectBounds.x + rightRetractHeatmapX, rectBounds.y, rectBounds.width - rightRetractHeatmapX, rectBounds.height)
                            
                            processSubBlob(leftRect, invScale, sourceW, sourceH, sourceMat, algorithm, rawBoxes, intermediateRefined)
                            processSubBlob(rightRect, invScale, sourceW, sourceH, sourceMat, algorithm, rawBoxes, intermediateRefined)
                            Log.i("OcrSplit", "RETRACT SPLIT: Center=$splitHeatmapX, LeftEdge=$leftRetractHeatmapX, RightEdge=$rightRetractHeatmapX")
                            continue
                        }
                    }
                }
            }
            processSubBlob(rotatedRect, invScale, sourceW, sourceH, sourceMat, algorithm, rawBoxes, intermediateRefined)
        }
        
        // Phase 35: ONLY OVERLAP UNION (No Proximity Glue)
        val (finalRaw, finalRefined) = mergeOverlappingBoxesSync(rawBoxes, intermediateRefined)
        
        mask.release(); hierarchy.release(); sourceMat?.release()
        return DbNetResult(finalRaw, finalRefined, discoveryTimeMs = System.currentTimeMillis() - tDiscoveryStart)
    }

    private data class SplitRetraction(val center: Int, val leftEdge: Int, val rightEdge: Int)

    private fun findProportionalGapAndRetract(roi: Mat): SplitRetraction? {
        val gray = Mat(); if (roi.channels() > 1) Imgproc.cvtColor(roi, gray, Imgproc.COLOR_RGBA2GRAY) else roi.copyTo(gray)
        
        val h = gray.rows()
        val w = gray.cols()
        val hillBrightness = Core.mean(gray).`val`[0]
        val inkThreshold = hillBrightness * 0.40 
        
        val maxInkHeightAllowedInGap = h * 0.15 
        val requiredGapWidth = (h * 0.20).toInt() 
        
        val margin = (w * 0.10).toInt()
        var bestValleyX = -1; var maxGap = 0; var currGap = 0; var currStart = -1
        
        val inkCounts = IntArray(w)
        for (x in 0 until w) {
            var inkCount = 0
            for (y in 0 until h) {
                if (gray.get(y, x)[0] > inkThreshold) inkCount++
            }
            inkCounts[x] = inkCount
        }

        for (x in margin until (w - margin)) {
            if (inkCounts[x] < maxInkHeightAllowedInGap) {
                if (currStart == -1) currStart = x
                currGap++
            } else {
                if (currGap >= requiredGapWidth && currGap > maxGap) {
                    maxGap = currGap
                    bestValleyX = currStart + (currGap / 2)
                }
                currStart = -1; currGap = 0
            }
        }
        
        if (currGap >= requiredGapWidth && currGap > maxGap) {
            bestValleyX = currStart + (currGap / 2)
        }
        
        if (bestValleyX == -1) {
            gray.release()
            return null
        }

        // RETRACTION PHASE:
        // We found a gap center. Now walk LEFT until we hit solid ink (the left word).
        var leftEdge = bestValleyX
        while (leftEdge > 0 && inkCounts[leftEdge] < (h * 0.25)) { // 25% ink defines the start of a character
            leftEdge--
        }
        // Add a 3px padding so the Red Box doesn't clip the ink perfectly
        leftEdge = min(bestValleyX, leftEdge + 3)

        // Walk RIGHT until we hit solid ink (the right word).
        var rightEdge = bestValleyX
        while (rightEdge < w - 1 && inkCounts[rightEdge] < (h * 0.25)) {
            rightEdge++
        }
        // Add a 3px padding
        rightEdge = max(bestValleyX, rightEdge - 3)

        gray.release()
        return SplitRetraction(bestValleyX, leftEdge, rightEdge)
    }

    private fun mergeOverlappingBoxesSync(rawBoxes: List<DetectedBox>, refinedBoxes: List<DetectedBox>): Pair<List<DetectedBox>, List<DetectedBox>> {
        if (refinedBoxes.isEmpty() || rawBoxes.size != refinedBoxes.size) return Pair(rawBoxes, refinedBoxes)
        
        val mutableRaw = rawBoxes.toMutableList()
        val mutableRefined = refinedBoxes.toMutableList()
        
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < mutableRefined.size) {
                var j = i + 1
                while (j < mutableRefined.size) {
                    val boxA = mutableRefined[i].boundingBox
                    val boxB = mutableRefined[j].boundingBox
                    
                    val interL = max(boxA.left, boxB.left); val interT = max(boxA.top, boxB.top)
                    val interR = min(boxA.right, boxB.right); val interB = min(boxA.bottom, boxB.bottom)
                    
                    if (interR > interL && interB > interT) {
                        val interArea = (interR - interL) * (interB - interT)
                        val areaA = (boxA.right - boxA.left) * (boxA.bottom - boxA.top)
                        val areaB = (boxB.right - boxB.left) * (boxB.bottom - boxB.top)
                        
                        val minArea = min(areaA, areaB)
                        
                        if (minArea > 0 && (interArea / minArea) > 0.40f) {
                            val unionRefined = RectF(
                                min(boxA.left, boxB.left), min(boxA.top, boxB.top),
                                max(boxA.right, boxB.right), max(boxA.bottom, boxB.bottom)
                            )
                            val rawA = mutableRaw[i].boundingBox
                            val rawB = mutableRaw[j].boundingBox
                            val unionRaw = RectF(
                                min(rawA.left, rawB.left), min(rawA.top, rawB.top),
                                max(rawA.right, rawB.right), max(rawA.bottom, rawB.bottom)
                            )
                            
                            val angle = if (areaA > areaB) mutableRefined[i].angle else mutableRefined[j].angle
                            mutableRefined[i] = DetectedBox(emptyList(), unionRefined, angle)
                            mutableRaw[i] = DetectedBox(emptyList(), unionRaw, angle)
                            
                            mutableRefined.removeAt(j)
                            mutableRaw.removeAt(j)
                            changed = true
                            break
                        }
                    }
                    j++
                }
                if (changed) break
                i++
            }
        }
        return Pair(mutableRaw, mutableRefined)
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
        
        val finalL = bounds.left; val finalT = bounds.top; val finalR = bounds.right; val finalB = bounds.bottom
        val finalW = finalR - finalL; val finalH = finalB - finalT
        Log.i("OCR_TRACE", "RED: [${rawBounds.left}, ${rawBounds.top}, ${rawBounds.right}, ${rawBounds.bottom}] YELLOW: [L=$finalL, T=$finalT, R=$finalR, B=$finalB, W=$finalW, H=$finalH]")

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
        val hillSub = gray.submat(redFloor.top, redFloor.bottom, redFloor.left, redFloor.right)
        val hillBrightness = Core.mean(hillSub).`val`[0]
        hillSub.release()
        val valleyThreshold = hillBrightness * 0.40 
        val maxH = gray.rows(); val maxW = gray.cols()
        val hL = (maxX - minX) * 4.0; val vL = (maxY - minY) * 1.0; val sX = minX; val sXX = maxX; val sY = minY; val sYY = maxY
        
        val requiredBridgeHeight = (maxY - minY) * 0.15

        while (minY > 0 && (sY - minY) < vL) { if (isDarkGap(gray, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true) || getLineAverage(gray, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true) < valleyThreshold) break; minY -= 1.0 }
        while (maxY < maxH - 1 && (maxY - sYY) < vL) { if (isDarkGap(gray, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true) || getLineAverage(gray, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true) < valleyThreshold) break; maxY += 1.0 }
        while (minX > 0 && (sX - minX) < hL) { if (getLineInkCount(gray, minY.toInt(), maxY.toInt(), (minX - 1).toInt(), false, valleyThreshold) < requiredBridgeHeight) break; minX -= 1.0 }
        while (maxX < maxW - 1 && (maxX - sXX) < hL) { if (getLineInkCount(gray, minY.toInt(), maxY.toInt(), (maxX + 1).toInt(), false, valleyThreshold) < requiredBridgeHeight) break; maxX += 1.0 }
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
    
    private fun getLineInkCount(mat: Mat, start: Int, end: Int, fixed: Int, horizontal: Boolean, threshold: Double): Int {
        var inkPixels = 0; val maxD = if (horizontal) mat.cols() else mat.rows()
        if (fixed < 0 || fixed >= (if (horizontal) mat.rows() else mat.cols())) return 0
        for (i in start until end) { 
            if (i < 0 || i >= maxD) continue
            val v = if (horizontal) mat.get(fixed, i)[0] else mat.get(i, fixed)[0]
            if (v > threshold) inkPixels++ 
        }
        return inkPixels
    }

    private fun expandPerimeter(redFloor: android.graphics.Rect, mask: Mat, maxH: Int, maxW: Int): android.graphics.Rect {
        var minX = redFloor.left.toDouble(); var maxX = redFloor.right.toDouble()
        var minY = redFloor.top.toDouble(); var maxY = redFloor.bottom.toDouble()
        val hL = (maxX - minX) * 4.0; val vL = (maxY - minY) * 1.0; val sX = minX; val sXX = maxX; val sY = minY; val sYY = maxY
        
        val requiredBridgeHeight = (maxY - minY) * 0.15
        
        var changed = true
        while (changed) {
            changed = false
            if (minY > 0 && (sY - minY) < vL && checkLine(mask, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true)) { minY -= 1.0; changed = true }
            if (maxY < maxH - 1 && (maxY - sYY) < vL && checkLine(mask, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true)) { maxY += 1.0; changed = true }
            if (minX > 0 && (sX - minX) < hL && getLineInkCount(mask, minY.toInt(), maxY.toInt(), (minX - 1).toInt(), false, 0.0) >= requiredBridgeHeight) { minX -= 1.0; changed = true }
            if (maxX < maxW - 1 && (maxX - sXX) < hL && getLineInkCount(mask, minY.toInt(), maxY.toInt(), (maxX + 1).toInt(), false, 0.0) >= requiredBridgeHeight) { maxX += 1.0; changed = true }
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
