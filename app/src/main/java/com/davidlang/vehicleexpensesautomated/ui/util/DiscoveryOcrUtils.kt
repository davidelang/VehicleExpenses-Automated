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
            
            val annotatedBmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(annotatedBmp)
            // Brighter, Thicker Red Boxes for fragments
            val redPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 4f }
            val orangePaint = Paint().apply { color = Color.rgb(255, 165, 0); style = Paint.Style.STROKE; strokeWidth = 6f }

            var primaryRawBox: Rect? = null
            var primaryRefinedBox: Rect? = null

            // Phase 63: Row-Aware Consolidation
            val orangeFragments = mutableListOf<Rect>()
            for ((idx, block) in sortedBlocks.withIndex()) {
                val rawBox = Rect(
                    block.boundingBox.left.toInt(),
                    block.boundingBox.top.toInt(),
                    block.boundingBox.right.toInt(),
                    block.boundingBox.bottom.toInt()
                )
                Log.d("DISCOVERY_DEBUG", "  Fragment $idx: [L=${rawBox.left}, T=${rawBox.top}, R=${rawBox.right}, B=${rawBox.bottom}]")
                canvas.drawRect(rawBox, redPaint)

                val unclipBox = unclipRect(rawBox, 1.5f)
                val refinedBox = if (expansion == DiscoveryExpansion.VALLEY) expandByValleyStop(rawBox, bmp) else unclipBox
                val orangeBox = Rect(max(2, refinedBox.left), max(2, refinedBox.top), min(bmp.width - 2, refinedBox.right), min(bmp.height - 2, refinedBox.bottom))
                orangeFragments.add(orangeBox)
                if (primaryRawBox == null) primaryRawBox = rawBox
            }

            // Group fragments into rows with AGGRESSIVE merging (any overlap or within 10% height)
            val rows = mutableListOf<MutableList<Rect>>()
            val sortedFragments = orangeFragments.sortedBy { it.top }
            
            for (frag in sortedFragments) {
                var placed = false
                val fragHeight = frag.height()
                for (row in rows) {
                    val anchor = row[0]
                    val overlapTop = max(frag.top, anchor.top)
                    val overlapBottom = min(frag.bottom, anchor.bottom)
                    val overlapHeight = overlapBottom - overlapTop
                    
                    // Merge if they overlap at all, or are within a tiny vertical distance
                    if (overlapHeight > -(fragHeight * 0.1)) {
                        row.add(frag)
                        placed = true
                        break
                    }
                }
                if (!placed) rows.add(mutableListOf(frag))
            }

            // Consolidate rows horizontally and run OCR
            val rowRects = rows.map { row ->
                val consolidated = Rect(
                    row.minOf { it.left },
                    row.minOf { it.top },
                    row.maxOf { it.right },
                    row.maxOf { it.bottom }
                )
                Log.d("DISCOVERY_DEBUG", "  Merged Row (from ${row.size} frags): [L=${consolidated.left}, T=${consolidated.top}, R=${consolidated.right}, B=${consolidated.bottom}]")
                consolidated
            }.sortedBy { it.top }

            for ((i, consolidatedRow) in rowRects.withIndex()) {
                canvas.drawRect(consolidatedRow, orangePaint)
                if (i == 0) primaryRefinedBox = consolidatedRow

                Log.d("DISCOVERY_DEBUG", "[$stageName] Consolidated Row $i: [L=${consolidatedRow.left}, T=${consolidatedRow.top}, R=${consolidatedRow.right}, B=${consolidatedRow.bottom}]")

                val recognitionCrop = Bitmap.createBitmap(bmp, consolidatedRow.left, consolidatedRow.top, consolidatedRow.width(), consolidatedRow.height())
                val recognizedText = paddleEngine.runConstrainedStatic(recognitionCrop, targetHeight ?: 48, paddleEngine.getDictionary(), paddleEngine.isV3())
                
                if (recognizedText.isNotBlank()) {
                    sb.append("$recognizedText ")
                }
                finalBlocks.add(TextBlock(text = recognizedText, boundingBox = consolidatedRow))
            }

            return OcrStepResult(
                stageName = stageName,
                bitmap = annotatedBmp,
                text = sb.toString().trim(),
                normalizedBoxes = finalBlocks,
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

        while (minY > 0 && (sY - minY) < vL) { if (getLineInkCount(gray, minX.toInt(), maxX.toInt(), (minY - 1).toInt(), true, valleyThreshold) < 8) break; minY -= 1.0 }
        while (maxY < maxH - 1 && (maxY - sYY) < vL) { if (getLineInkCount(gray, minX.toInt(), maxX.toInt(), (maxY + 1).toInt(), true, valleyThreshold) < 8) break; maxY += 1.0 }
        while (minX > 0 && (sX - minX) < hL) { if (getLineInkCount(gray, minY.toInt(), maxY.toInt(), (minX - 1).toInt(), false, valleyThreshold) < requiredBridgeHeight) break; minX -= 1.0 }
        while (maxX < maxW - 1 && (maxX - sXX) < hL) { if (getLineInkCount(gray, minY.toInt(), maxY.toInt(), (maxX + 1).toInt(), false, valleyThreshold) < requiredBridgeHeight) break; maxX += 1.0 }

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
