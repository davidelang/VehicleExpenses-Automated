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
            
            // 1. Detection at 512x128 (High-speed asymmetrical discovery path)
            val det = paddleEngine.runDetectionOnly(bmp, 512, 128)
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

            // Phase 63: Robust Clustering (Union-Find style)
            val clusters = mutableListOf<MutableList<Rect>>()
            for (frag in orangeFragments) {
                val matchingClusters = mutableListOf<Int>()
                for ((idx, cluster) in clusters.withIndex()) {
                    if (cluster.any { c ->
                        val overlapTop = max(frag.top, c.top)
                        val overlapBottom = min(frag.bottom, c.bottom)
                        val overlapHeight = overlapBottom - overlapTop
                        overlapHeight > 0 && overlapHeight >= min(frag.height(), c.height()) * 0.20
                    }) {
                        matchingClusters.add(idx)
                    }
                }

                if (matchingClusters.isEmpty()) {
                    clusters.add(mutableListOf(frag))
                } else {
                    val firstIdx = matchingClusters[0]
                    clusters[firstIdx].add(frag)
                    // Merge multiple matching clusters if needed
                    for (k in matchingClusters.size - 1 downTo 1) {
                        clusters[firstIdx].addAll(clusters[matchingClusters[k]])
                        clusters.removeAt(matchingClusters[k])
                    }
                }
            }

            // Convert clusters to single bounding boxes
            val consolidatedBoxes = clusters.map { cluster ->
                Rect(
                    cluster.minOf { it.left },
                    cluster.minOf { it.top },
                    cluster.maxOf { it.right },
                    cluster.maxOf { it.bottom }
                )
            }

            // Final Recognition Pass on stable boxes (sorted by top for reading order)
            val finalStepBlocks = mutableListOf<TextBlock>()
            var lastOcrInputB64: String? = null
            for ((i, consolidatedBox) in consolidatedBoxes.sortedBy { it.top }.withIndex()) {
                if (i == 0) primaryRefinedBox = consolidatedBox

                Log.d("DISCOVERY_DEBUG", "  Consolidated Row $i: [L=${consolidatedBox.left}, T=${consolidatedBox.top}, R=${consolidatedBox.right}, B=${consolidatedBox.bottom}]")

                val targetBmp = if (paddleEngine.useMono) MemoryBridge.pool320x48!!.getBitmap() else NativePaddleEngine.sharedBmpRec
                val targetSize = org.opencv.core.Size(320.0, 48.0)
                
                val argbMat = org.opencv.core.Mat()
                org.opencv.android.Utils.bitmapToMat(bmp, argbMat)
                val roiRect = org.opencv.core.Rect(consolidatedBox.left, consolidatedBox.top, consolidatedBox.width(), consolidatedBox.height())
                val roiMat = org.opencv.core.Mat(argbMat, roiRect)
                
                if (paddleEngine.useMono) {
                    val redMat = org.opencv.core.Mat()
                    org.opencv.core.Core.extractChannel(roiMat, redMat, 0)
                    org.opencv.imgproc.Imgproc.resize(redMat, MemoryBridge.pool320x48!!.getMat(), targetSize, 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                    MemoryBridge.pool320x48!!.syncToBitmap()
                    redMat.release()
                } else {
                    val resizedMat = org.opencv.core.Mat()
                    org.opencv.imgproc.Imgproc.resize(roiMat, resizedMat, targetSize, 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                    org.opencv.android.Utils.matToBitmap(resizedMat, targetBmp)
                    resizedMat.release()
                }
                argbMat.release(); roiMat.release()

                val ocrResult = paddleEngine.runConstrainedStatic(targetBmp, targetHeight ?: 48, paddleEngine.getDictionary(), paddleEngine.isV3())
                val recognizedText = ocrResult.text
                lastOcrInputB64 = ocrResult.ocrInputB64
                
                if (recognizedText.isNotBlank()) {
                    sb.append("$recognizedText ")
                }
                // Store BOTH the fragments and the consolidated box for late-stage annotation
                finalStepBlocks.add(TextBlock(text = recognizedText, boundingBox = consolidatedBox))
            }

            // Capture snapshot using ALL row boxes (Orange) and ALL fragments (Red)
            val b64 = OcrUtils.takeSnapshot(bmp, rawFragments, consolidatedBoxes)

            return OcrStepResult(
                stageName = stageName,
                thumbB64 = b64, 
                ocrInputB64 = lastOcrInputB64,
                text = sb.toString().trim(),
                normalizedBoxes = finalStepBlocks,
                rawBox = primaryRawBox,
                refinedBox = primaryRefinedBox,
                metadata = emptyMap()
            )
        }

        // Preprocessing Overhaul: Test filter combinations on Monochrome Baseline

        // 1. Raw (Monochrome Baseline)
        steps.add(exec(bitmap, "Raw"))

        // 2. 80% Stretch Only
        val s80Only = OdometerOcrUtils.applyContrastStretch(bitmap, 80)
        steps.add(exec(s80Only, "80% Stretch Only"))

        // 3. Bile -> 80% Stretch
        val bileBase = OdometerOcrUtils.applyBilateral(bitmap)
        val bileThen80 = OdometerOcrUtils.applyContrastStretch(bileBase, 80)
        steps.add(exec(bileThen80, "Bile -> 80% Stretch"))

        // 4. 80% Stretch -> Bile
        // We reuse s80Only logic here, but need a new bitmap since we recycled it
        val stretchBase = OdometerOcrUtils.applyContrastStretch(bitmap, 80)
        val stretchThenBile = OdometerOcrUtils.applyBilateral(stretchBase)
        steps.add(exec(stretchThenBile, "80% Stretch -> Bile"))

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
        val gray = if (sourceBitmap.config == Bitmap.Config.ALPHA_8) {
            OdometerOcrUtils.bitmapToMatMono(sourceBitmap)
        } else {
            val mat = Mat(); org.opencv.android.Utils.bitmapToMat(sourceBitmap, mat)
            val g = Mat(); if (mat.channels() > 1) Imgproc.cvtColor(mat, g, Imgproc.COLOR_RGBA2GRAY) else mat.copyTo(g)
            mat.release(); g
        }

        var minX = redFloor.left.toDouble(); var maxX = redFloor.right.toDouble(); var minY = redFloor.top.toDouble(); var maxY = redFloor.bottom.toDouble()
        val safeFloor = Rect(max(0, redFloor.left), max(0, redFloor.top), min(gray.cols(), redFloor.right), min(gray.rows(), redFloor.bottom))
        if (safeFloor.width() < 1 || safeFloor.height() < 1) { gray.release(); return redFloor }
        
        val hillSub = gray.submat(safeFloor.top, safeFloor.bottom, safeFloor.left, safeFloor.right)
        val hillBrightness = Core.mean(hillSub).`val`[0]; hillSub.release()
        val valleyThreshold = hillBrightness * 0.40; val maxH = gray.rows(); val maxW = gray.cols()
        val hL = (maxX - minX) * 4.0; val vL = (maxY - minY) * 1.0; val sX = minX; val sXX = maxX; val sY = minY; val sYY = maxY
        val requiredBridgeHeight = (maxY - minY) * 0.15
        
        // Phase 63: Differentiated Look-Ahead (Horizontal needs more reach)
        val lookAheadLimitV = redFloor.height().toDouble()
        val lookAheadLimitH = redFloor.height().toDouble() * 2.0

        // 1. Expand Vertically (Top)
        var lastGoodY = minY
        var lookY = minY
        while (lookY > 0 && (sY - lookY) < vL) {
            val ink = getLineInkCount(gray, minX.toInt(), maxX.toInt(), (lookY - 1).toInt(), true, valleyThreshold)
            if (ink >= 8) {
                lastGoodY = lookY - 1
                lookY = lastGoodY
            } else {
                if (lastGoodY - lookY > lookAheadLimitV) break
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
                if (lookY - lastGoodY > lookAheadLimitV) break
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
                if (lastGoodX - lookX > lookAheadLimitH) break
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
                if (lookX - lastGoodX > lookAheadLimitH) break
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
