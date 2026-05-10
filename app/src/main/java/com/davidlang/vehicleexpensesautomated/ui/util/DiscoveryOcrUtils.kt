package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Performs detection and expansion visualization.
 */
object DiscoveryOcrUtils {

    suspend fun runDiscoveryMultiStepOcr(
        bitmap: Bitmap,
        context: Context,
        engineName: String,
        targetHeight: Int? = null,
        paddleEngine: NativePaddleEngine? = null,
        expansion: DiscoveryExpansion = DiscoveryExpansion.UNCLIP,
        argbScratch: Bitmap? = null,
        monoScratch: MemoryBridge? = null,
        detBridge: MemoryBridge? = null,
        recBridge: MemoryBridge? = null
    ): List<OcrStepResult> {
        val steps = mutableListOf<OcrStepResult>()
        if (paddleEngine == null) return emptyList()

        /**
         * Executes sequential detection and expansion visualization.
         */
        suspend fun exec(bmp: Bitmap, stageName: String): OcrStepResult {
            val sb = StringBuilder()
            val finalStepBlocks = mutableListOf<TextBlock>()
            
            // 1. Detection at 512x128 (Explicit scaling into provided bridge)
            if (detBridge == null) return OcrStepResult(stageName, "", null, "Bridge Null", emptyList(), emptyList(), Rect(0,0,1,1), Rect(0,0,1,1), emptyMap())
            
            val detBmp = detBridge.getBitmap()
            val detCanvas = Canvas(detBmp)
            detCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            val detScale = min(512f / bmp.width.toFloat(), 128f / bmp.height.toFloat())
            val detMatrix = android.graphics.Matrix()
            detMatrix.postScale(detScale, detScale)
            detCanvas.drawBitmap(bmp, detMatrix, NativePaddleEngine.srcPaint)

            val det = paddleEngine.detect(detBmp, 512, 128) ?: return OcrStepResult(stageName, "", null, "Det Failed", emptyList(), emptyList(), Rect(0,0,1,1), Rect(0,0,1,1), emptyMap())
            
            // Phase 115: Use existing heatmap processor to get blocks mapped directly to crop resolution
            val sortedBlocks = OdometerOcrUtils.processPaddleHeatmap(det.heatmap, det.width, det.height, detScale, bmp, "Paddle").sortedBy { it.boundingBox.left }
            Log.d("DISCOVERY_DEBUG", "[$stageName] Found ${sortedBlocks.size} fragments")
            
            var primaryRefinedBox: Rect? = null
            val orangeFragments = mutableListOf<Rect>()
            val rawFragments = mutableListOf<Rect>()
            for (block in sortedBlocks) {
                val rawBox = block.boundingBox
                rawFragments.add(rawBox)
                val unclipBox = unclipRect(rawBox, 1.5f)
                val refinedBox = if (expansion == DiscoveryExpansion.VALLEY) expandByValleyStop(rawBox, bmp) else unclipBox
                val orangeBox = Rect(max(2, refinedBox.left), max(2, refinedBox.top), min(bmp.width - 2, refinedBox.right), min(bmp.height - 2, refinedBox.bottom))
                orangeFragments.add(orangeBox)
            }

            // Phase 63: Union-Find style clustering
            val clusters = mutableListOf<MutableList<Rect>>()
            for (frag in orangeFragments) {
                val matchingClusters = mutableListOf<Int>()
                for ((idx, cluster) in clusters.withIndex()) {
                    if (cluster.any { c ->
                        val overlapTop = max(frag.top, c.top); val overlapBottom = min(frag.bottom, c.bottom)
                        val overlapHeight = overlapBottom - overlapTop
                        overlapHeight > 0 && overlapHeight >= min(frag.height(), c.height()) * 0.20
                    }) matchingClusters.add(idx)
                }
                if (matchingClusters.isEmpty()) {
                    clusters.add(mutableListOf(frag))
                } else {
                    val firstIdx = matchingClusters[0]; clusters[firstIdx].add(frag)
                    for (k in matchingClusters.size - 1 downTo 1) {
                        clusters[firstIdx].addAll(clusters[matchingClusters[k]]); clusters.removeAt(matchingClusters[k])
                    }
                }
            }

            val consolidatedBoxes = clusters.map { cluster ->
                Rect(cluster.minOf { it.left }, cluster.minOf { it.top }, cluster.maxOf { it.right }, cluster.maxOf { it.bottom })
            }

            // Final Recognition Pass on stable boxes (sorted by top for reading order)
            var lastOcrInputB64: String? = null
            for ((i, consolidatedBox) in consolidatedBoxes.sortedBy { it.top }.withIndex()) {
                if (i == 0) primaryRefinedBox = consolidatedBox
                if (recBridge == null) continue
                
                val targetBmp = recBridge.getBitmap()
                val targetSize = org.opencv.core.Size(320.0, 48.0)
                val argbMat = org.opencv.core.Mat(); org.opencv.android.Utils.bitmapToMat(bmp, argbMat)
                val roiRect = org.opencv.core.Rect(consolidatedBox.left, consolidatedBox.top, consolidatedBox.width(), consolidatedBox.height())
                val roiMat = org.opencv.core.Mat(argbMat, roiRect)
                
                if (paddleEngine.useMono) {
                    val redMat = org.opencv.core.Mat(); org.opencv.core.Core.extractChannel(roiMat, redMat, 0)
                    org.opencv.imgproc.Imgproc.resize(redMat, recBridge.getMat(), targetSize, 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                    recBridge.syncToBitmap(); redMat.release()
                } else {
                    val resizedMat = org.opencv.core.Mat(); org.opencv.imgproc.Imgproc.resize(roiMat, resizedMat, targetSize, 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA)
                    org.opencv.android.Utils.matToBitmap(resizedMat, targetBmp); resizedMat.release()
                }
                argbMat.release(); roiMat.release()

                val ocrResult = paddleEngine.runConstrainedStatic(targetBmp, 48, paddleEngine.getDictionary(), paddleEngine.isV3())
                if (ocrResult.text.isNotBlank()) sb.append("${ocrResult.text} ")
                lastOcrInputB64 = ocrResult.ocrInputB64
                finalStepBlocks.add(TextBlock(text = ocrResult.text, boundingBox = consolidatedBox))
            }

            val b64 = OcrUtils.takeSnapshot(bmp, rawFragments, consolidatedBoxes, argbScratch)
            val box = primaryRefinedBox ?: Rect(0,0,bmp.width,bmp.height)
            return OcrStepResult(stageName, b64, lastOcrInputB64, sb.toString().trim(), consolidatedBoxes, finalStepBlocks, box, box, emptyMap())
        }

        val scratch = if (bitmap.config == Bitmap.Config.ALPHA_8) NativePaddleEngine.sharedBmpOdoScratchMono else NativePaddleEngine.sharedBmpOdoScratch
        val scratchCanvas = Canvas(scratch)
        steps.add(exec(bitmap, "Raw"))
        
        // Refinement stages
        fun populateScratch() {
            scratchCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            scratchCanvas.drawBitmap(bitmap, 0f, 0f, null)
        }

        populateScratch()
        OdometerOcrUtils.applyContrastStretch(scratch, 80, monoScratch)
        steps.add(exec(scratch, "80% Stretch Only"))

        populateScratch()
        OdometerOcrUtils.applyBilateral(scratch, argbScratch, monoScratch)
        OdometerOcrUtils.applyContrastStretch(scratch, 80, monoScratch)
        steps.add(exec(scratch, "Bile -> 80% Stretch"))

        populateScratch()
        OdometerOcrUtils.applyContrastStretch(scratch, 80, monoScratch)
        OdometerOcrUtils.applyBilateral(scratch, argbScratch, monoScratch)
        steps.add(exec(scratch, "80% Stretch -> Bile"))

        return steps
    }

    private fun unclipRect(rect: Rect, ratio: Float): Rect {
        val area = rect.width().toDouble() * rect.height().toDouble()
        val perimeter = 2.0 * (rect.width() + rect.height())
        if (perimeter <= 0) return rect
        val delta = (area * ratio / perimeter).toInt()
        return Rect(rect.left - delta, rect.top - delta, rect.right + delta, rect.bottom + delta)
    }

    private fun expandByValleyStop(rect: Rect, bmp: Bitmap): Rect {
        val dx = (rect.width() * 0.15f).toInt(); val dy = (rect.height() * 0.15f).toInt()
        return Rect(rect.left - dx, rect.top - dy, rect.right + dx, rect.bottom + dy)
    }
}
