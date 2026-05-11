package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import org.opencv.android.Utils
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
            // Phase 115: Mandatory Sized-Input Validation
            requireNotNull(detBridge) { "Discovery requires detBridge (512x128)" }
            requireNotNull(recBridge) { "Discovery requires recBridge (320x48)" }
            require(detBridge.width >= 512 && detBridge.height >= 128) { "detBridge undersized: ${detBridge.width}x${detBridge.height}" }
            require(recBridge.width >= 320 && recBridge.height >= 48) { "recBridge undersized: ${recBridge.width}x${recBridge.height}" }
            
            val sb = StringBuilder()
            val finalStepBlocks = mutableListOf<TextBlock>()
            val detScale = min(512f / bmp.width.toFloat(), 128f / bmp.height.toFloat())
            
            // Phase 115: Dual-Path Detection Populating
            if (bmp.config == Bitmap.Config.ALPHA_8 && monoScratch != null) {
                // Mono Path: Use OpenCV resize for Mat-to-Mat transfer (Native Resolution)
                val targetSize = org.opencv.core.Size(512.0, 128.0)
                Imgproc.resize(monoScratch.getMat(), detBridge.getMat(), targetSize, 0.0, 0.0, Imgproc.INTER_AREA)
                detBridge.syncToBitmap() // Sync for engine tensor population
            } else {
                // Standard Path: Use Canvas draw into ARGB detection bridge
                val detCanvas = Canvas(detBmp)
                detCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                val detMatrix = android.graphics.Matrix()
                detMatrix.postScale(detScale, detScale)
                detCanvas.drawBitmap(bmp, detMatrix, null)
            }

            val det = paddleEngine.detect(detBmp, 512, 128) ?: return OcrStepResult(stageName, "", null, "Det Failed", emptyList(), emptyList(), Rect(0,0,1,1), Rect(0,0,1,1), emptyMap())
            
            // Process heatmap blocks mapped directly to crop resolution
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
                val roiRect = org.opencv.core.Rect(consolidatedBox.left, consolidatedBox.top, consolidatedBox.width(), consolidatedBox.height())
                
                // Phase 115: Dual-Path Recognition Dispatch
                if (bmp.config == Bitmap.Config.ALPHA_8 && monoScratch != null) {
                    // Native Path: Source from MemoryBridge Mat version
                    val roiMat = Mat(monoScratch.getMat(), roiRect)
                    Imgproc.resize(roiMat, recBridge.getMat(), targetSize, 0.0, 0.0, Imgproc.INTER_AREA)
                    recBridge.syncToBitmap() // Sync for engine tensor population
                    roiMat.release()
                    
                    val ocrResult = paddleEngine.runConstrainedStatic(recBridge, 48, paddleEngine.getDictionary(), paddleEngine.isV3())
                    if (ocrResult.text.isNotBlank()) sb.append("${ocrResult.text} ")
                    lastOcrInputB64 = ocrResult.ocrInputB64
                } else {
                    // Standard Path: Source from ARGB Bitmap
                    val argbMat = Mat(); Utils.bitmapToMat(bmp, argbMat)
                    val roiMat = Mat(argbMat, roiRect)
                    val resizedMat = Mat()
                    Imgproc.resize(roiMat, resizedMat, targetSize, 0.0, 0.0, Imgproc.INTER_AREA)
                    Utils.matToBitmap(resizedMat, targetBmp)
                    resizedMat.release(); argbMat.release(); roiMat.release()
                    
                    val ocrResult = paddleEngine.runConstrainedStatic(targetBmp, 48, paddleEngine.getDictionary(), paddleEngine.isV3())
                    if (ocrResult.text.isNotBlank()) sb.append("${ocrResult.text} ")
                    lastOcrInputB64 = ocrResult.ocrInputB64
                }
                finalStepBlocks.add(TextBlock(text = sb.toString().trim().split(" ").lastOrNull() ?: "", boundingBox = consolidatedBox))
            }

            val b64 = OcrUtils.takeSnapshot(bmp, rawFragments, consolidatedBoxes, argbScratch)
            val box = primaryRefinedBox ?: Rect(0,0,bmp.width,bmp.height)
            return OcrStepResult(stageName, b64, lastOcrInputB64, sb.toString().trim(), consolidatedBoxes, finalStepBlocks, box, box, emptyMap())
        }

        // Phase 115: Safe Workspace Selection. Use provided per-vehicle scratch buffers.
        val scratch = (if (bitmap.config == Bitmap.Config.ALPHA_8) monoScratch?.getBitmap() else argbScratch) ?: bitmap
        val scratchCanvas = Canvas(scratch)
        steps.add(exec(bitmap, "Raw"))
        
        // Refinement stages
        suspend fun process(name: String, block: () -> Unit) {
            scratchCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            
            // Phase 115: Safe population of scratch buffer
            if (bitmap.config == Bitmap.Config.ALPHA_8) {
                // If source is Mono, we use alphaToGrayPaint to draw into the ARGB report-safe scratch
                scratchCanvas.drawBitmap(bitmap, 0f, 0f, NativePaddleEngine.alphaToGrayPaint)
                monoScratch?.syncFromBitmap() // Sync Mat for in-place filters
            } else {
                scratchCanvas.drawBitmap(bitmap, 0f, 0f, null)
            }
            
            block()
            
            if (bitmap.config == Bitmap.Config.ALPHA_8) monoScratch?.syncToBitmap() // Sync Bitmap back for snapshot/diagnostics
            
            steps.add(exec(scratch, name))
        }

        process("80% Stretch Only") { OdometerOcrUtils.applyContrastStretch(scratch, 80, monoScratch) }
        process("Bile -> 80% Stretch") { 
            OdometerOcrUtils.applyBilateral(scratch, argbScratch, monoScratch)
            OdometerOcrUtils.applyContrastStretch(scratch, 80, monoScratch) 
        }
        process("80% Stretch -> Bile") { 
            OdometerOcrUtils.applyContrastStretch(scratch, 80, monoScratch)
            OdometerOcrUtils.applyBilateral(scratch, argbScratch, monoScratch) 
        }

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
