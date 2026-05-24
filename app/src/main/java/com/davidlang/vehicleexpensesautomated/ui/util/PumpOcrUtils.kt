package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Implementation of the Gas Pump Field Extraction Algorithm (v40).
 * Ported from: dev-ai-interaction/research/PUMP_EXTRACTION_ALGORITHM.md
 *
 * This utility is designed for high-resolution discovery and extraction of 
 * Total Cost and Volume fields from gas pump displays using the Rigid Buffer Architecture.
 */
object PumpOcrUtils {
    private const val TAG = "PumpOcrUtils"

    // Multi-scale resolutions defined in v40 spec, clamped to 2048 mono-tensor limits
    private val SCALES = listOf(200, 600, 1000, 2048)

    /**
     * Main entry point for pump field discovery.
     * Operates strictly on global rigid buffers (fullBufferSet, deskewBufferSet2048).
     */
    suspend fun discoverPumpFields(
        context: Context,
        paddleEngine: NativePaddleEngine,
        report: (String) -> Unit = {}
    ): OcrResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val metaLog = mutableMapOf<String, String>()
        report("Starting multi-scale discovery...")

        // 1. Multi-Scale Detection Pass
        val allBoxes = performMultiScalePass(paddleEngine, report, metaLog)

        // 2. Hybrid Hunk Construction (IoU Merging)
        val mergedBoxes = mergeBoxes(allBoxes)

        // 3. Horizontal Stitching
        val stitchedLanes = stitchHorizontal(mergedBoxes)

        // 4. Lane Grouping & Selection
        val bestPair = findBestCostVolumePair(stitchedLanes)

        // 5. Final Recognition Pass (on best pair)
        val result = if (bestPair != null) {
            recognizeFields(bestPair, paddleEngine, metaLog)
        } else {
            OcrResult(debugText = "No pump lanes detected")
        }

        result.copy(
            executionTimeMs = System.currentTimeMillis() - t0,
            imageWidth = NativePaddleEngine.fullBufferSet.width,
            imageHeight = NativePaddleEngine.fullBufferSet.height,
            metadata = result.metadata + metaLog
        )
    }

    /**
     * Executes the detection model at four scales (200, 600, 1000, 2048 px).
     */
    private suspend fun performMultiScalePass(
        paddleEngine: NativePaddleEngine,
        report: (String) -> Unit,
        metaLog: MutableMap<String, String>
    ): List<RectF> {
        val resultBoxes = mutableListOf<RectF>()
        
        val sourceW = NativePaddleEngine.fullBufferSet.width
        val sourceH = NativePaddleEngine.fullBufferSet.height
        val aspect = sourceW.toFloat() / sourceH.toFloat()

        for (scale in SCALES) {
            report("Detection at ${scale}px...")
            
            // 1. Prepare target scale (Preserve aspect ratio)
            val targetW: Int
            val targetH: Int
            if (sourceW > sourceH) {
                targetW = scale; targetH = (scale / aspect).toInt()
            } else {
                targetH = scale; targetW = (scale * aspect).toInt()
            }
            
            metaLog["Scale_${scale}_Dims"] = "${targetW}x${targetH}"

            // 2. Create Logical Crop on Scratchpad
            val cropId = NativePaddleEngine.deskewBufferSet2048.s.createCrop(0, 0, targetW, targetH)
            
            try {
                // 3. Resize Source into Scratchpad Crop
                val targetMat = NativePaddleEngine.deskewBufferSet2048.c[cropId].mat
                Imgproc.resize(
                    NativePaddleEngine.fullBufferSet.p.mat,
                    targetMat,
                    Size(targetW.toDouble(), targetH.toDouble()),
                    0.0, 0.0, Imgproc.INTER_AREA
                )

                // 4. Run Detection (Paddle Mono)
                val det = paddleEngine.detectMono(NativePaddleEngine.deskewBufferSet2048.c[cropId])
                if (det != null) {
                    // 5. Process Heatmap to get blocks
                    val blocks = OdometerOcrUtils.processPaddleHeatmap(
                        det.heatmap, det.width, det.height, 1.0f, "None", "PaddlePump"
                    )
                    
                    // 6. Collect Normalized Boxes
                    blocks.forEach { block ->
                        block.rawDiscoveryBox?.let { resultBoxes.add(it) }
                    }
                }
            } finally {
                NativePaddleEngine.deskewBufferSet2048.c[cropId].release()
            }
        }
        
        return resultBoxes
    }

    /**
     * Merges overlapping boxes from different scales using IoU thresholds.
     */
    private fun mergeBoxes(boxes: List<RectF>): List<RectF> {
        if (boxes.isEmpty()) return emptyList()
        
        val sorted = boxes.sortedByDescending { (it.right - it.left) * (it.bottom - it.top) }
        val merged = mutableListOf<RectF>()
        
        for (box in sorted) {
            var isMerged = false
            for (i in merged.indices) {
                if (calculateIoU(box, merged[i]) > 0.5f) {
                    // Merge by taking the union (encompassing both)
                    merged[i] = RectF(
                        minOf(box.left, merged[i].left),
                        minOf(box.top, merged[i].top),
                        maxOf(box.right, merged[i].right),
                        maxOf(box.bottom, merged[i].bottom)
                    )
                    isMerged = true
                    break
                }
            }
            if (!isMerged) {
                merged.add(box)
            }
        }
        return merged
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)
        
        if (interLeft >= interRight || interTop >= interBottom) return 0f
        
        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        
        return interArea / (areaA + areaB - interArea)
    }

    /**
     * Connects horizontally adjacent boxes into unified text lanes.
     */
    private fun stitchHorizontal(boxes: List<RectF>): List<RectF> {
        if (boxes.isEmpty()) return emptyList()

        // Sort by top coordinate then left
        val sorted = boxes.sortedWith(compareBy({ it.top }, { it.left }))
        val lanes = mutableListOf<RectF>()

        for (box in sorted) {
            var stitched = false
            for (i in lanes.indices) {
                val lane = lanes[i]
                
                // Vertical Overlap Check (Must share at least 50% vertical space)
                val vOverlap = minOf(box.bottom, lane.bottom) - maxOf(box.top, lane.top)
                val minH = minOf(box.bottom - box.top, lane.bottom - lane.top)
                
                // Horizontal Proximity Check (Gap < 2x height)
                val hGap = box.left - lane.right
                val hThreshold = (box.bottom - box.top) * 2.0f

                if (vOverlap > minH * 0.5f && hGap < hThreshold && hGap > -0.05f) {
                    lanes[i] = RectF(
                        minOf(lane.left, box.left),
                        minOf(lane.top, box.top),
                        maxOf(lane.right, box.right),
                        maxOf(lane.bottom, box.bottom)
                    )
                    stitched = true
                    break
                }
            }
            if (!stitched) {
                lanes.add(box)
            }
        }
        return lanes
    }

    /**
     * Identifies the primary Cost/Volume pair based on vertical alignment and golden words.
     */
    private fun findBestCostVolumePair(lanes: List<RectF>): Pair<RectF, RectF>? {
        if (lanes.size < 2) return null

        // Sort by vertical position
        val sorted = lanes.sortedBy { it.top }
        
        var bestPair: Pair<RectF, RectF>? = null
        var bestScore = -1f

        for (i in 0 until sorted.size - 1) {
            val top = sorted[i]
            for (j in i + 1 until sorted.size) {
                val bottom = sorted[j]
                
                // Criteria 1: Vertical proximity (bottom must be below top, gap < 3x height)
                val vGap = bottom.top - top.bottom
                val h = top.bottom - top.top
                if (vGap < 0 || vGap > h * 3.0f) continue
                
                // Criteria 2: Horizontal alignment (centers must be within 30% of width)
                val centerTop = (top.left + top.right) / 2f
                val centerBottom = (bottom.left + bottom.right) / 2f
                val width = maxOf(top.right - top.left, bottom.right - bottom.left)
                if (Math.abs(centerTop - centerBottom) > width * 0.3f) continue
                
                // Criteria 3: Width similarity (should be within 60%)
                val wTop = top.right - top.left
                val wBottom = bottom.right - bottom.left
                if (Math.abs(wTop - wBottom) > width * 0.6f) continue

                val score = 1.0f / (1.0f + vGap + Math.abs(centerTop - centerBottom))
                if (score > bestScore) {
                    bestScore = score
                    bestPair = Pair(top, bottom)
                }
            }
        }

        return bestPair
    }

    /**
     * Performs final high-resolution recognition on the selected lanes.
     */
    private suspend fun recognizeFields(
        pair: Pair<RectF, RectF>,
        paddleEngine: NativePaddleEngine,
        metaLog: MutableMap<String, String>
    ): OcrResult {
        val top = pair.first
        val bottom = pair.second

        val costStr = recognizeLane(top, paddleEngine, "Cost", metaLog)
        val gallonsStr = recognizeLane(bottom, paddleEngine, "Volume", metaLog)

        return OcrResult(
            cost = cleanNumericString(costStr, 2),
            gallons = cleanNumericString(gallonsStr, 3),
            debugText = "Cost: $costStr, Gallons: $gallonsStr",
            metadata = mapOf("label_match" to hasGoldenWords(costStr, gallonsStr).toString())
        )
    }

    private fun hasGoldenWords(cost: String, volume: String): Boolean {
        val golden = listOf("SALE", "TOTAL", "GALLON", "GAL", "PRICE", "AMT")
        val combined = (cost + volume).uppercase()
        return golden.any { combined.contains(it) }
    }

    private suspend fun recognizeLane(
        roi: RectF, 
        paddleEngine: NativePaddleEngine, 
        label: String,
        metaLog: MutableMap<String, String>
    ): String {
        val w = NativePaddleEngine.fullBufferSet.width
        val h = NativePaddleEngine.fullBufferSet.height

        val roiW = (roi.right - roi.left) * w
        val roiH = (roi.bottom - roi.top) * h

        // v40 Context Expansion:
        // Vertical: Grow height by 50% (25% top, 25% bottom)
        // Horizontal: Expand both sides by value of NEW height
        val newH = roiH * 1.5f
        val expTop = (roi.top * h) - (roiH * 0.25f)
        val expBottom = (roi.bottom * h) + (roiH * 0.25f)
        val expLeft = (roi.left * w) - newH
        val expRight = (roi.right * w) + newH

        // Create logical crop on primary buffer using Float layout to trigger Anisotropic extraction
        val cropX = expLeft / w.toFloat()
        val cropY = expTop / h.toFloat()
        val cropW = (expRight - expLeft) / w.toFloat()
        val cropH = (expBottom - expTop) / h.toFloat()
        
        // Log telemetry for dynamic sizing analysis
        val pixelWidth = expRight - expLeft
        val pixelHeight = expBottom - expTop
        val requiredRatio = pixelWidth / pixelHeight
        val recTargetHeight = 48
        val requiredTargetWidth = (recTargetHeight * requiredRatio).toInt()
        metaLog["Rec_${label}_SourceRatio"] = "%.2f".format(requiredRatio)
        metaLog["Rec_${label}_ReqWidthForH48"] = "$requiredTargetWidth"
        
        // Clamp to tensor max bounds
        val tensorW = minOf(requiredTargetWidth, 320)
        
        val sourceCropId = NativePaddleEngine.fullBufferSet.p.createCropLegacy(cropX, cropY, cropW, cropH)
        val targetCropId = NativePaddleEngine.deskewBufferSet2048.s.createCrop(0, 0, tensorW, recTargetHeight)

        try {
            val srcMat = NativePaddleEngine.fullBufferSet.c[sourceCropId].mat
            if (srcMat.cols() <= 0 || srcMat.rows() <= 0) return ""

            val dstMat = NativePaddleEngine.deskewBufferSet2048.c[targetCropId].mat
            Imgproc.resize(
                srcMat, 
                dstMat, 
                Size(tensorW.toDouble(), recTargetHeight.toDouble()), 
                0.0, 0.0, Imgproc.INTER_AREA
            )

            val res = paddleEngine.runConstrainedStaticMono(
                NativePaddleEngine.deskewBufferSet2048.c[targetCropId], 
                paddleEngine.getDictionary()
            )
            return res.text
        } finally {
            NativePaddleEngine.fullBufferSet.c[sourceCropId].release()
            NativePaddleEngine.deskewBufferSet2048.c[targetCropId].release()
        }
    }

    private fun cleanNumericString(input: String, decimalPlaces: Int): String {
        // Remove non-numeric/period chars
        var cleaned = input.replace(Regex("[^0-9.]"), "")

        // Remove leading decimal
        if (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1)
        }

        // Handle implicit decimal (if no decimal found, assume fixed point)
        if (!cleaned.contains(".") && cleaned.isNotEmpty()) {
            val value = cleaned.toDoubleOrNull() ?: 0.0
            val divisor = Math.pow(10.0, decimalPlaces.toDouble())
            return String.format("%.${decimalPlaces}f", value / divisor)
        }

        return cleaned
    }

}