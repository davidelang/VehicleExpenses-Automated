package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 63 Increment 8: Phase 3 - Paddle Discovery Implementation.
 * Implements Red Box detection and Orange Box refinement (Unclip/Valley Expansion).
 */
object DiscoveryOcrUtils {

    suspend fun runDiscoveryMultiStepOcr(
        bitmap: Bitmap,
        context: Context,
        engineName: String,
        targetHeight: Int? = null,
        paddleEngine: NativePaddleEngine? = null
    ): List<OcrStepResult> {
        val steps = mutableListOf<OcrStepResult>()
        if (paddleEngine == null) return emptyList()

        val isValley = engineName.contains("Valley") || engineName.contains("Padded")
        Log.i("DiscoveryOcr", "Starting full discovery refinement for $engineName")

        /**
         * Executes sequential detection and recognition.
         */
        suspend fun exec(bmp: Bitmap, stageName: String): OcrStepResult {
            val sb = StringBuilder()
            val finalBlocks = mutableListOf<TextBlock>()
            
            // 1. Detection at standard 1280px (Using the dynamic model at sub-resolution)
            // We use 320 for discovery to focus on the odometer digits within the crop
            val det = paddleEngine.runDetectionOnly(bmp, 320)
            val invScale = 1.0 / det.scaleFactor.toDouble()
            val sortedBlocks = det.textBlocks.sortedBy { it.boundingBox.left }
            
            val annotatedBmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(annotatedBmp)
            val redPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 2f }
            val orangePaint = Paint().apply { color = Color.rgb(255, 165, 0); style = Paint.Style.STROKE; strokeWidth = 2f }

            for (block in sortedBlocks) {
                val rawBox = Rect(
                    (block.boundingBox.left * invScale).toInt(),
                    (block.boundingBox.top * invScale).toInt(),
                    (block.boundingBox.right * invScale).toInt(),
                    (block.boundingBox.bottom * invScale).toInt()
                )
                
                // Draw Red Box (Detection)
                canvas.drawRect(rawBox, redPaint)

                val unclipBox = unclipRect(rawBox, 1.5f)
                val valleyBox = if (isValley) expandByValleyStop(rawBox, bmp) else unclipBox
                val safeValley = Rect(max(0, valleyBox.left), max(0, valleyBox.top), min(bmp.width, valleyBox.right), min(bmp.height, valleyBox.bottom))
                
                // Draw Orange Box (Refinement)
                canvas.drawRect(safeValley, orangePaint)

                if (safeValley.width() > 0 && safeValley.height() > 0) {
                    val subCrop = OdometerOcrUtils.cropBitmap(bmp, safeValley)
                    try {
                        val text = NativePaddleEngine.runConstrainedStatic(subCrop, targetHeight ?: 48, paddleEngine.getDictionary(), paddleEngine.isV3())
                        if (text.isNotBlank()) {
                            if (sb.isNotEmpty()) sb.append(" ")
                            sb.append(text)
                        }
                        finalBlocks.add(TextBlock(
                            text = text,
                            boundingBox = safeValley
                        ))
                    } finally {
                        subCrop.recycle()
                    }
                }
            }

            return OcrStepResult(
                stageName = stageName,
                bitmap = annotatedBmp,
                text = sb.toString().trim(),
                normalizedBoxes = finalBlocks
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
