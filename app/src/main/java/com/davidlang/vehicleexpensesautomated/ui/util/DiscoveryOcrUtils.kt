package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 63 Increment 8: Phase 3 - Discovery Visualization & Logging.
 * Performs detection (Red Box) and expansion (Orange Box) with detailed logging.
 * Recognition is disabled to prevent SIGSEGV while debugging.
 */
object DiscoveryOcrUtils {

    suspend fun runDiscoveryMultiStepOcr(
        bitmap: Bitmap,
        context: Context,
        engineName: String,
        targetHeight: Int? = null,
        paddleEngine: NativePaddleEngine? = null,
        expansion: DiscoveryExpansion = DiscoveryExpansion.UNCLIP
    ): List<OcrStepResult> {
        val steps = mutableListOf<OcrStepResult>()
        if (paddleEngine == null) return emptyList()

        Log.d("DISCOVERY_DEBUG", "--- Starting Discovery for $engineName ($expansion) ---")
        Log.d("DISCOVERY_DEBUG", "Crop dimensions: ${bitmap.width}x${bitmap.height}")

        /**
         * Executes sequential detection and expansion visualization.
         */
        suspend fun exec(bmp: Bitmap, stageName: String): OcrStepResult {
            val sb = StringBuilder()
            val finalBlocks = mutableListOf<TextBlock>()
            
            // 1. Detection at 320x128 (High-speed asymmetrical discovery path)
            val det = paddleEngine.runDetectionOnly(bmp, 320, 128)
            val invScale = 1.0 / det.scaleFactor.toDouble()
            val sortedBlocks = det.textBlocks.sortedBy { it.boundingBox.left }
            Log.d("DISCOVERY_DEBUG", "[$stageName] Found ${sortedBlocks.size} fragments")
            
            var primaryRawBox: Rect? = null
            var primaryRefinedBox: Rect? = null

            // Phase 63: Row-Aware Consolidation (Calculations only, no drawing)
            val orangeFragments = mutableListOf<Rect>()
            val rawFragments = mutableListOf<Rect>()
            for ((idx, block) in sortedBlocks.withIndex()) {
                val rawBox = Rect(
                    block.boundingBox.left.toInt(),
                    block.boundingBox.top.toInt(),
                    block.boundingBox.right.toInt(),
                    block.boundingBox.bottom.toInt()
                )
                Log.d("DISCOVERY_DEBUG", "  Fragment $idx: [L=${rawBox.left}, T=${rawBox.top}, R=${rawBox.right}, B=${rawBox.bottom}]")
                rawFragments.add(rawBox)

                val unclipBox = unclipRect(rawBox, 1.5f)
                val refinedBox = if (expansion == DiscoveryExpansion.VALLEY) expandByValleyStop(rawBox, bmp) else unclipBox
                val orangeBox = Rect(max(2, refinedBox.left), max(2, refinedBox.top), min(bmp.width - 2, refinedBox.right), min(bmp.height - 2, refinedBox.bottom))
                orangeFragments.add(orangeBox)
                if (primaryRawBox == null) primaryRawBox = rawBox
            }

            // Phase 63: Iterative Row-Aware Consolidation (20% overlap, no vertical gap)
            val consolidatedBoxes = orangeFragments.toMutableList()
            do {
                var merged = false
                var i = 0
                while (i < consolidatedBoxes.size && !merged) {
                    var j = i + 1
                    while (j < consolidatedBoxes.size && !merged) {
                        val a = consolidatedBoxes[i]
                        val b = consolidatedBoxes[j]

                        val overlapTop = max(a.top, b.top)
                        val overlapBottom = min(a.bottom, b.bottom)
                        val overlapHeight = overlapBottom - overlapTop
                        
                        if (overlapHeight > 0) { // No vertical gap
                            val minH = min(a.height(), b.height())
                            if (overlapHeight >= minH * 0.20) { // At least 20% overlap of shorter box
                                // Union A and B into a single bounding box
                                val union = Rect(
                                    min(a.left, b.left),
                                    min(a.top, b.top),
                                    max(a.right, b.right),
                                    max(a.bottom, b.bottom)
                                )
                                consolidatedBoxes.removeAt(j)
                                consolidatedBoxes.removeAt(i)
                                consolidatedBoxes.add(union)
                                merged = true
                            }
                        }
                        j++
                    }
                    i++
                }
            } while (merged)

            // Final Recognition Pass on stable boxes (sorted by top for reading order)
            val finalStepBlocks = mutableListOf<TextBlock>()
            for ((i, consolidatedBox) in consolidatedBoxes.sortedBy { it.top }.withIndex()) {
                if (i == 0) primaryRefinedBox = consolidatedBox

                Log.d("DISCOVERY_DEBUG", "  Consolidated Row $i: [L=${consolidatedBox.left}, T=${consolidatedBox.top}, R=${consolidatedBox.right}, B=${consolidatedBox.bottom}]")

                val recognitionCrop = Bitmap.createBitmap(bmp, consolidatedBox.left, consolidatedBox.top, consolidatedBox.width(), consolidatedBox.height())
                val recognizedText = paddleEngine.runConstrainedStatic(recognitionCrop, targetHeight ?: 48, paddleEngine.getDictionary(), paddleEngine.isV3())
                
                if (recognizedText.isNotBlank()) {
                    sb.append("$recognizedText ")
                }
                // Store BOTH the fragments and the consolidated box for late-stage annotation
                finalStepBlocks.add(TextBlock(
                    text = recognizedText, 
                    boundingBox = consolidatedBox, 
                    metadata = mapOf("frags" to rawFragments.joinToString("|") { "${it.left},${it.top},${it.right},${it.bottom}" })
                ))
            }

            return OcrStepResult(
                stageName = stageName,
                bitmap = bmp, // CLEAN OCR: No annotations on source
                text = sb.toString().trim(),
                normalizedBoxes = finalStepBlocks,
                rawBox = primaryRawBox,
                refinedBox = primaryRefinedBox
            )

        }

        // Sequential pipeline
        steps.add(exec(bitmap, "Raw"))
        val gray = OdometerOcrUtils.applyGrayscale(bitmap); steps.add(exec(gray, "Grayscale")); gray.recycle()
        val baseBile = OdometerOcrUtils.applyBilateral(bitmap); steps.add(exec(baseBile, "Bilateral"))
        val s75 = OdometerOcrUtils.applyContrastStretch(baseBile, 75); steps.add(exec(s75, "Enhanced (75% Stretch)")); s75.recycle()
        val s80 = OdometerOcrUtils.applyContrastStretch(baseBile, 80); steps.add(exec(s80, "Enhanced (80% Stretch)")); s80.recycle()

        baseBile.recycle()
        return steps
    }

    private fun unclipRect(rect: Rect, ratio: Float): Rect {
        val area = rect.width().toDouble() * rect.height().toDouble()
        val perimeter = 2.0 * (rect.width() + rect.height())
        if (perimeter <= 0) return rect
        val distance = (area * ratio / perimeter).toInt()
        return Rect(rect.left - distance, rect.top - distance, rect.right + distance, rect.bottom + distance)
    }

    private fun expandByValleyStop(redFloor: Rect, sourceBitmap: Bitmap): Rect {
        if (redFloor.width() < 1 || redFloor.height() < 1) return redFloor
        val mat = Mat(); Utils.bitmapToMat(sourceBitmap, mat)
        val gray = Mat(); if (mat.channels() > 1) Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY) else mat.copyTo(gray)
        mat.release()

        var minX = redFloor.left.toDouble(); var maxX = redFloor.right.toDouble(); var minY = redFloor.top.toDouble(); var maxY = redFloor.bottom.toDouble()
        val safeFloor = Rect(max(0, redFloor.left), max(0, redFloor.top), min(gray.cols(), redFloor.right), min(gray.rows(), redFloor.bottom))
        if (safeFloor.width() < 1 || safeFloor.height() < 1) { gray.release(); return redFloor }
        
        val hillSub = gray.submat(safeFloor.top, safeFloor.bottom, safeFloor.left, safeFloor.right)
        val hillBrightness = Core.mean(hillSub).`val`[0]; hillSub.release()
        val valleyThreshold = hillBrightness * 0.40; val maxH = gray.rows(); val maxW = gray.cols()
        val hL = (maxX - minX) * 4.0; val vL = (maxY - minY) * 1.0; val sX = minX; val sXX = maxX; val sY = minY; val sYY = maxY
        val requiredBridgeHeight = (maxY - minY) * 0.15
        val lookAheadLimit = redFloor.height().toDouble()

        // 1. Expand Vertically (Top)
        var lastGoodY = minY
        var lookY = minY
        while (lookY > 0 && (sY - lookY) < vL) {
            val ink = getLineInkCount(gray, minX.toInt(), maxX.toInt(), (lookY - 1).toInt(), true, valleyThreshold)
            if (ink >= 8) {
                lastGoodY = lookY - 1
                lookY = lastGoodY
            } else {
                if (lastGoodY - lookY > lookAheadLimit) break
                lookY -= 1.0
            }
        }
        minY = lastGoodY

        // 2. Expand Vertically (Bottom)
        lastGoodY = maxY
        lookY = maxY
        while (lookY < maxH - 1 && (lookY - sYY) < vL) {
            val ink = getLineInkCount(gray, minX.toInt(), maxX.toInt(), (lookY + 1).toInt(), true, valleyThreshold)
            if (ink >= 8) {
                lastGoodY = lookY + 1
                lookY = lastGoodY
            } else {
                if (lookY - lastGoodY > lookAheadLimit) break
                lookY += 1.0
            }
        }
        maxY = lastGoodY

        // 3. Expand Horizontally (Left)
        var lastGoodX = minX
        var lookX = minX
        while (lookX > 0 && (sX - lookX) < hL) {
            val ink = getLineInkCount(gray, minY.toInt(), maxY.toInt(), (lookX - 1).toInt(), false, valleyThreshold)
            if (ink >= requiredBridgeHeight) {
                lastGoodX = lookX - 1
                lookX = lastGoodX
            } else {
                if (lastGoodX - lookX > lookAheadLimit) break
                lookX -= 1.0
            }
        }
        minX = lastGoodX

        // 4. Expand Horizontally (Right)
        lastGoodX = maxX
        lookX = maxX
        while (lookX < maxW - 1 && (lookX - sXX) < hL) {
            val ink = getLineInkCount(gray, minY.toInt(), maxY.toInt(), (lookX + 1).toInt(), false, valleyThreshold)
            if (ink >= requiredBridgeHeight) {
                lastGoodX = lookX + 1
                lookX = lastGoodX
            } else {
                if (lookX - lastGoodX > lookAheadLimit) break
                lookX += 1.0
            }
        }
        maxX = lastGoodX

        gray.release()
        return Rect(max(0, minX.toInt()), max(0, minY.toInt()), min(maxW, maxX.toInt()), min(maxH, maxY.toInt()))
    }

    private fun getLineInkCount(mat: Mat, start: Int, end: Int, fixed: Int, horizontal: Boolean, threshold: Double): Int {
        var inkPixels = 0; val maxD = if (horizontal) mat.cols() else mat.rows()
        if (fixed < 0 || fixed >= (if (horizontal) mat.rows() else mat.cols())) return 0
        for (i in start until end) { if (i < 0 || i >= maxD) continue; if ((if (horizontal) mat.get(fixed, i)[0] else mat.get(i, fixed)[0]) > threshold) inkPixels++ }
        return inkPixels
    }
}
