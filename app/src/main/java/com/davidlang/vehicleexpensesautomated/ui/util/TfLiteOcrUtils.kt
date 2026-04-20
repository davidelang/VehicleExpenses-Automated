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
     * Phase 51: Relative Sub-Thresholding.
     */
    fun processDbNetOutput(
        heatmap: FloatArray, heatmapW: Int, heatmapH: Int,
        scale: Float = 1.0f,
        sourceBitmap: Bitmap? = null, algorithm: String = "C",
        recursive: Boolean = false
    ): DbNetResult {
        val tDiscoveryStart = System.currentTimeMillis()
        if (heatmapW <= 0 || heatmapH <= 0 || heatmap.size < heatmapW * heatmapH) return DbNetResult(emptyList(), emptyList())
        
        // Find Peak Energy for Relative Thresholding
        var maxHeat = 0f
        for (v in heatmap) { if (v > maxHeat) maxHeat = v }

        // Phase 51/54: Relative Sub-Thresholding
        // Standard pass uses 0.20f. Sub-pass uses 50% of the peak energy found in that crop.
        val maskThreshold = if (recursive) (maxHeat * 0.50f).coerceAtLeast(0.01f) else 0.20f

        if (recursive) {
            val colEnergies = FloatArray(heatmapW)
            for (x in 0 until heatmapW) {
                var sum = 0f; for (y in 0 until heatmapH) { sum += heatmap[y * heatmapW + x] }
                colEnergies[x] = sum / heatmapH
            }
            Log.i("HEAT_PROFILE", "Sub-Pass Peak=$maxHeat, Threshold=$maskThreshold")
        }

        val mask = Mat(heatmapH, heatmapW, CvType.CV_8UC1)
        val data = ByteArray(heatmapW * heatmapH)
        for (i in heatmap.indices) { data[i] = if (heatmap[i] > maskThreshold) 255.toByte() else 0.toByte() }
        mask.put(0, 0, data)

        val sourceMat = if (sourceBitmap != null) {
            try { val mat = Mat(); Utils.bitmapToMat(sourceBitmap, mat); mat } catch (e: Exception) { null }
        } else null

        val contours = mutableListOf<MatOfPoint>(); val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val rawBoxes = mutableListOf<DetectedBox>()
        val intermediateRefined = mutableListOf<DetectedBox>()
        val suspectCrops = mutableListOf<RectF>()
        val sourceW = sourceBitmap?.width?.toDouble() ?: heatmapW.toDouble()
        val sourceH = sourceBitmap?.height?.toDouble() ?: heatmapH.toDouble()
        val invScale = 1.0 / scale.toDouble()

        val threshold = if (!recursive) calculateAdaptiveThreshold(contours) else 1000f

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < 10) continue 
            val rotatedRect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
            val rectBounds = rotatedRect.boundingRect()
            val aspect = rectBounds.width.toDouble() / rectBounds.height.toDouble()
            
            Log.i("OcrTrigger", "Recursive=$recursive | Area=$area | Aspect=$aspect | Threshold=$threshold | Action=${if (!recursive && aspect > threshold) "RECURSE" else "PROCESS"}")

            if (!recursive && aspect > threshold && sourceMat != null) {
                val orangeBox = expandInRoi(
                    RotatedRect(Point(rotatedRect.center.x * invScale, rotatedRect.center.y * invScale), Size(rotatedRect.size.width * invScale, rotatedRect.size.height * invScale), rotatedRect.angle),
                    sourceMat, algorithm,
                    android.graphics.Rect((rectBounds.x * invScale).toInt(), (rectBounds.y * invScale).toInt(), ((rectBounds.x + rectBounds.width) * invScale).toInt(), ((rectBounds.y + rectBounds.height) * invScale).toInt())
                )
                val padW = (orangeBox.width() * 0.05).toInt()
                val padH = (orangeBox.height() * 0.05).toInt()
                suspectCrops.add(RectF(
                    ((orangeBox.left - padW).toFloat() / sourceW.toFloat()).coerceIn(0f, 1f),
                    ((orangeBox.top - padH).toFloat() / sourceH.toFloat()).coerceIn(0f, 1f),
                    ((orangeBox.right + padW).toFloat() / sourceW.toFloat()).coerceIn(0f, 1f),
                    ((orangeBox.bottom + padH).toFloat() / sourceH.toFloat()).coerceIn(0f, 1f)
                ))
                // Removed 'continue' so primary pass still outputs the bloated blob
            }
            processSubBlob(rotatedRect, invScale, sourceW, sourceH, sourceMat, algorithm, rawBoxes, intermediateRefined)
        }
        
        // Phase 53: RE-ENABLE CLEANUP
        val (finalRaw, finalRefined) = mergeOverlappingBoxesSync(rawBoxes, intermediateRefined)
        val (gluedRaw, gluedRefined) = mergeNearbyBoxesSync(finalRaw, finalRefined, sourceW, sourceH)
        
        mask.release(); hierarchy.release(); sourceMat?.release()
        return DbNetResult(gluedRaw, gluedRefined, discoveryTimeMs = System.currentTimeMillis() - tDiscoveryStart, suspectCrops = suspectCrops)
    }

    private fun calculateAdaptiveThreshold(contours: List<MatOfPoint>): Float {
        val aspects = contours.filter { Imgproc.contourArea(it) >= 10 }.map { 
            val r = Imgproc.minAreaRect(MatOfPoint2f(*it.toArray())).boundingRect()
            r.width.toDouble() / r.height.toDouble()
        }.sorted()
        if (aspects.isEmpty()) return 3.0f
        val bins = mutableMapOf<Int, Int>()
        for (a in aspects) { val bucket = (a / 0.5).toInt(); bins[bucket] = (bins[bucket] ?: 0) + 1 }
        val maxBucket = (aspects.last() / 0.5).toInt(); var gapBucketStart = maxBucket + 1
        for (b in 0..maxBucket) {
            if ((bins[b] ?: 0) == 0 && (bins[b+1] ?: 0) == 0) {
                if (aspects.count { it < b * 0.5 } >= (aspects.size * 0.5)) { gapBucketStart = b; break }
            }
        }
        val gapValue = gapBucketStart * 0.5f
        Log.i("OcrAdaptive", "GapBucket=$gapBucketStart, Threshold=$gapValue")
        return gapValue
    }

    private fun processSubBlob(rect: Any, invScale: Double, sourceW: Double, sourceH: Double, sourceMat: Mat?, algorithm: String, rawBoxes: MutableList<DetectedBox>, refinedBoxes: MutableList<DetectedBox>) {
        val rotatedRect = if (rect is RotatedRect) rect else {
            val r = rect as org.opencv.core.Rect
            RotatedRect(Point(r.x + r.width/2.0, r.y + r.height/2.0), Size(r.width.toDouble(), r.height.toDouble()), 0.0)
        }
        val rawPoints = arrayOf(Point(), Point(), Point(), Point()); rotatedRect.points(rawPoints)
        val normRawPoints = rawPoints.map { Point(it.x * invScale / sourceW, it.y * invScale / sourceH) }
        val rawBounds = RectF((normRawPoints.minOf { it.x }.toFloat()).coerceIn(0f, 1f), (normRawPoints.minOf { it.y }.toFloat()).coerceIn(0f, 1f), (normRawPoints.maxOf { it.x }.toFloat()).coerceIn(0f, 1f), (normRawPoints.maxOf { it.y }.toFloat()).coerceIn(0f, 1f))
        rawBoxes.add(DetectedBox(rawPoints.toList(), rawBounds, rotatedRect.angle.toFloat()))
        val redFloorPixels = android.graphics.Rect((rawBounds.left * sourceW).toInt(), (rawBounds.top * sourceH).toInt(), (rawBounds.right * sourceW).toInt(), (rawBounds.bottom * sourceH).toInt())
        val sourceRect = RotatedRect(Point(rotatedRect.center.x * invScale, rotatedRect.center.y * invScale), Size(rotatedRect.size.width * invScale, rotatedRect.size.height * invScale), rotatedRect.angle)
        val bounds = if (sourceMat != null) expandInRoi(sourceRect, sourceMat, algorithm, redFloorPixels) else redFloorPixels
        val normRefinedBounds = RectF((bounds.left.toFloat() / sourceW.toFloat()).coerceIn(0f, 1f), (bounds.top.toFloat() / sourceH.toFloat()).coerceIn(0f, 1f), (bounds.right.toFloat() / sourceW.toFloat()).coerceIn(0f, 1f), (bounds.bottom.toFloat() / sourceH.toFloat()).coerceIn(0f, 1f))
        refinedBoxes.add(DetectedBox(emptyList(), normRefinedBounds, rotatedRect.angle.toFloat()))
    }

    private fun expandInRoi(rect: RotatedRect, sourceMat: Mat, algorithm: String, redFloor: android.graphics.Rect): android.graphics.Rect {
        val margin = (rect.size.height * 3.0).toInt()
        val roiL = max(0, (rect.center.x - rect.size.width/2 - margin).toInt()); val roiT = max(0, (rect.center.y - rect.size.height/2 - margin).toInt())
        val roiR = min(sourceMat.cols(), (rect.center.x + rect.size.width/2 + margin).toInt()); val roiB = min(sourceMat.rows(), (rect.center.y + rect.size.height/2 + margin).toInt())
        if (roiR <= roiL || roiB <= roiT) return redFloor
        val roiMat = sourceMat.submat(roiT, roiB, roiL, roiR); val gray = Mat(); if (roiMat.channels() > 1) Imgproc.cvtColor(roiMat, gray, Imgproc.COLOR_RGBA2GRAY) else roiMat.copyTo(gray)
        val localRedFloor = android.graphics.Rect(redFloor.left - roiL, redFloor.top - roiT, redFloor.right - roiL, redFloor.bottom - roiT)
        val expandedRect = expandByValleyStop(localRedFloor, gray); gray.release(); roiMat.release()
        return android.graphics.Rect(expandedRect.left + roiL, expandedRect.top + roiT, expandedRect.right + roiL, expandedRect.bottom + roiT)
    }

    private fun expandByValleyStop(redFloor: android.graphics.Rect, gray: Mat): android.graphics.Rect {
        var minX = redFloor.left.toDouble(); var maxX = redFloor.right.toDouble(); var minY = redFloor.top.toDouble(); var maxY = redFloor.bottom.toDouble()
        if (redFloor.width() < 1 || redFloor.height() < 1) return redFloor
        val hillSub = gray.submat(redFloor.top, redFloor.bottom, redFloor.left, redFloor.right); val hillBrightness = Core.mean(hillSub).`val`[0]; hillSub.release()
        val valleyThreshold = hillBrightness * 0.40; val maxH = gray.rows(); val maxW = gray.cols()
        val hL = (maxX - minX) * 4.0; val vL = (maxY - minY) * 1.0; val sX = minX; val sXX = maxX; val sY = minY; val sYY = maxY
        val requiredBridgeHeight = (maxY - minY) * 0.15
        while (minY > 0 && (sY - minY) < vL) { if (getLineInkCount(gray, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true, valleyThreshold) < 8) break; minY -= 1.0 }
        while (maxY < maxH - 1 && (maxY - sYY) < vL) { if (getLineInkCount(gray, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true, valleyThreshold) < 8) break; maxY += 1.0 }
        while (minX > 0 && (sX - minX) < hL) { if (getLineInkCount(gray, minY.toInt(), maxY.toInt(), (minX - 1).toInt(), false, valleyThreshold) < requiredBridgeHeight) break; minX -= 1.0 }
        while (maxX < maxW - 1 && (maxX - sXX) < hL) { if (getLineInkCount(gray, minY.toInt(), maxY.toInt(), (maxX + 1).toInt(), false, valleyThreshold) < requiredBridgeHeight) break; maxX += 1.0 }
        return android.graphics.Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(maxW, maxX.toInt()), min(maxH, maxY.toInt()))
    }

    private fun getLineInkCount(mat: Mat, start: Int, end: Int, fixed: Int, horizontal: Boolean, threshold: Double): Int {
        var inkPixels = 0; val maxD = if (horizontal) mat.cols() else mat.rows()
        if (fixed < 0 || fixed >= (if (horizontal) mat.rows() else mat.cols())) return 0
        for (i in start until end) { if (i < 0 || i >= maxD) continue; if ((if (horizontal) mat.get(fixed, i)[0] else mat.get(i, fixed)[0]) > threshold) inkPixels++ }
        return inkPixels
    }

    private fun mergeOverlappingBoxesSync(rawBoxes: List<DetectedBox>, refinedBoxes: List<DetectedBox>): Pair<List<DetectedBox>, List<DetectedBox>> {
        if (refinedBoxes.isEmpty() || rawBoxes.size != refinedBoxes.size) return Pair(rawBoxes, refinedBoxes)
        val mutableRaw = rawBoxes.toMutableList(); val mutableRefined = refinedBoxes.toMutableList()
        var changed = true
        while (changed) {
            changed = false; var i = 0
            while (i < mutableRefined.size) {
                var j = i + 1
                while (j < mutableRefined.size) {
                    val boxA = mutableRefined[i].boundingBox; val boxB = mutableRefined[j].boundingBox
                    val interL = max(boxA.left, boxB.left); val interT = max(boxA.top, boxB.top); val interR = min(boxA.right, boxB.right); val interB = min(boxA.bottom, boxB.bottom)
                    if (interR > interL && interB > interT) {
                        val interArea = (interR - interL) * (interB - interT); val areaA = (boxA.right - boxA.left) * (boxA.bottom - boxA.top); val areaB = (boxB.right - boxB.left) * (boxB.bottom - boxB.top)
                        val minArea = min(areaA, areaB)
                        if (minArea > 0 && (interArea / minArea) > 0.40f) {
                            val unionRefined = RectF(min(boxA.left, boxB.left), min(boxA.top, boxB.top), max(boxA.right, boxB.right), max(boxA.bottom, boxB.bottom))
                            val rawA = mutableRaw[i].boundingBox; val rawB = mutableRaw[j].boundingBox
                            val unionRaw = RectF(min(rawA.left, rawB.left), min(rawA.top, rawB.top), max(rawA.right, rawB.right), max(rawA.bottom, rawB.bottom))
                            val angle = if (areaA > areaB) mutableRefined[i].angle else mutableRefined[j].angle
                            mutableRefined[i] = DetectedBox(emptyList(), unionRefined, angle); mutableRaw[i] = DetectedBox(emptyList(), unionRaw, angle)
                            mutableRefined.removeAt(j); mutableRaw.removeAt(j); changed = true; break
                        }
                    }
                    j++
                }
                if (changed) break; i++
            }
        }
        return Pair(mutableRaw, mutableRefined)
    }

    private fun mergeNearbyBoxesSync(rawBoxes: List<DetectedBox>, refinedBoxes: List<DetectedBox>, sourceW: Double, sourceH: Double): Pair<List<DetectedBox>, List<DetectedBox>> {
        if (refinedBoxes.isEmpty() || rawBoxes.size != refinedBoxes.size) return Pair(rawBoxes, refinedBoxes)
        val mutableRaw = rawBoxes.toMutableList(); val mutableRefined = refinedBoxes.toMutableList()
        var changed = true
        while (changed) {
            changed = false; var i = 0
            while (i < mutableRefined.size) {
                var j = i + 1
                while (j < mutableRefined.size) {
                    val boxA = mutableRefined[i].boundingBox; val boxB = mutableRefined[j].boundingBox
                    val pixelBoxA = RectF(boxA.left * sourceW.toFloat(), boxA.top * sourceH.toFloat(), boxA.right * sourceW.toFloat(), boxA.bottom * sourceH.toFloat())
                    val pixelBoxB = RectF(boxB.left * sourceW.toFloat(), boxB.top * sourceH.toFloat(), boxB.right * sourceW.toFloat(), boxB.bottom * sourceH.toFloat())
                    val vOverlap = min(pixelBoxA.bottom, pixelBoxB.bottom) - max(pixelBoxA.top, pixelBoxB.top)
                    val minH = min(pixelBoxA.bottom - pixelBoxA.top, pixelBoxB.bottom - pixelBoxB.top)
                    if (vOverlap > minH * 0.75f) {
                        val hGap = if (pixelBoxA.right < pixelBoxB.left) pixelBoxB.left - pixelBoxA.right else if (pixelBoxB.right < pixelBoxA.left) pixelBoxA.left - pixelBoxB.right else 0f
                        if (hGap <= minH * 0.25f) {
                            val unionRefined = RectF(min(boxA.left, boxB.left), min(boxA.top, boxB.top), max(boxA.right, boxB.right), max(boxA.bottom, boxB.bottom))
                            val rawA = mutableRaw[i].boundingBox; val rawB = mutableRaw[j].boundingBox
                            val unionRaw = RectF(min(rawA.left, rawB.left), min(rawA.top, rawB.top), max(rawA.right, rawB.right), max(rawA.bottom, rawB.bottom))
                            val areaA = (boxA.right - boxA.left) * (boxA.bottom - boxA.top); val areaB = (boxB.right - boxB.left) * (boxB.bottom - boxB.top)
                            val angle = if (areaA > areaB) mutableRefined[i].angle else mutableRefined[j].angle
                            mutableRefined[i] = DetectedBox(emptyList(), unionRefined, angle); mutableRaw[i] = DetectedBox(emptyList(), unionRaw, angle)
                            mutableRefined.removeAt(j); mutableRaw.removeAt(j); changed = true; break
                        }
                    }
                    j++
                }
                if (changed) break; i++
            }
        }
        return Pair(mutableRaw, mutableRefined)
    }
}
