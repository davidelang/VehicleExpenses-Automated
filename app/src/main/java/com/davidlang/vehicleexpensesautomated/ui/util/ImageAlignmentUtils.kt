package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.features2d.*
import org.opencv.imgproc.Imgproc
import org.opencv.calib3d.Calib3d
import org.opencv.photo.Photo

data class AlignmentResult(
    val success: Boolean,
    val alignedImage: Bitmap?,
    val confidence: Float,
    val message: String
)

object ImageAlignmentUtils {
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("ImageAlignment", "OpenCV initialization failed!")
        } else {
            Log.i("ImageAlignment", "OpenCV initialized successfully for alignment")
        }
    }

    /**
     * Experiment 1: Full cleaned dash image (6 images) — more aggressive
     */
    suspend fun createExperiment1Cleaned(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val variants = mutableListOf<Bitmap>()
        Log.i("Exp1", "Creating full cleaned images (6 aggressive variants)")
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)
        variants.add(Bitmap.createScaledBitmap(original, thumbW, thumbH, true))

        val src = Mat()
        org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat()
        Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)

        val paramSets = listOf(
            Triple(4.0, 35.0, 25),   // extremely aggressive
            Triple(6.0, 40.0, 22),
            Triple(8.0, 45.0, 20),
            Triple(10.0, 50.0, 18),
            Triple(12.0, 55.0, 16)
        )

        for ((index, params) in paramSets.withIndex()) {
            val (cannyLow, cannyHigh, thickness) = params
            try {
                val gray = Mat()
                Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)
                Core.bitwise_not(gray, gray)
                val edges = Mat()
                Imgproc.Canny(gray, edges, cannyLow, cannyHigh)
                val lines = Mat()
                Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 10, 12.0, 2.0)
                val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
                for (i in 0 until lines.rows()) {
                    val line = lines.get(i, 0)
                    val x1 = line[0].toInt()
                    val y1 = line[1].toInt()
                    val x2 = line[2].toInt()
                    val y2 = line[3].toInt()
                    val length = Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())
                    if (length < 260) {
                        Imgproc.line(mask, Point(x1.toDouble(), y1.toDouble()), Point(x2.toDouble(), y2.toDouble()), Scalar(255.0), thickness)
                    }
                }
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                Imgproc.dilate(mask, mask, kernel)
                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
                val cleaned = Mat()
                Photo.inpaint(srcBGR, mask, cleaned, 14.0, Photo.INPAINT_TELEA)
                val resultBmp = Bitmap.createBitmap(cleaned.cols(), cleaned.rows(), Bitmap.Config.ARGB_8888)
                org.opencv.android.Utils.matToBitmap(cleaned, resultBmp)
                val display = if (resultBmp.width > 512) {
                    val h = (512f / resultBmp.width * resultBmp.height).toInt()
                    Bitmap.createScaledBitmap(resultBmp, 512, h, true)
                } else resultBmp
                variants.add(display)
                gray.release()
                edges.release()
                lines.release()
                mask.release()
                cleaned.release()
                if (resultBmp !== display) resultBmp.recycle()
            } catch (e: Exception) {
                Log.e("Exp1", "Failed variant ${index + 1}", e)
            }
        }
        src.release()
        srcBGR.release()
        Log.i("Exp1", "✅ 6 aggressive cleaned images created")
        variants
    }

    /**
     * Experiment 2: Radial line masks (6 images) — lower thresholds + thicker lines
     */
    suspend fun createExperiment2RadialVariants(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val variants = mutableListOf<Bitmap>()
        Log.i("Exp2", "Starting radial line sweep (6 images — lower thresholds + thick lines)")
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)
        variants.add(Bitmap.createScaledBitmap(original, thumbW, thumbH, true))

        val src = Mat()
        org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat()
        Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)

        val paramSets = listOf(
            Triple(3.0, 30.0, 8),
            Triple(5.0, 35.0, 10),
            Triple(7.0, 40.0, 12),
            Triple(9.0, 45.0, 14),
            Triple(11.0, 50.0, 16)
        )

        for ((index, params) in paramSets.withIndex()) {
            val (cannyLow, cannyHigh, houghThresh) = params
            try {
                val gray = Mat()
                Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)
                val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                val equalized = Mat()
                clahe.apply(gray, equalized)
                Core.bitwise_not(equalized, equalized)
                val blurred = Mat()
                Imgproc.GaussianBlur(equalized, blurred, Size(3.0, 3.0), 0.0)
                val edges = Mat()
                Imgproc.Canny(blurred, edges, cannyLow, cannyHigh)
                val lines = Mat()
                Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, houghThresh, 12.0, 2.0)

                val centerX = src.cols() / 2.0
                val centerY = src.rows() / 2.0
                val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
                for (i in 0 until lines.rows()) {
                    val line = lines.get(i, 0)
                    val x1 = line[0]
                    val y1 = line[1]
                    val x2 = line[2]
                    val y2 = line[3]
                    val length = Math.hypot(x2 - x1, y2 - y1)
                    if (length < 12) continue
                    val distToCenter = Math.abs((y2 - y1) * (x1 - centerX) - (x2 - x1) * (y1 - centerY)) / length
                    if (distToCenter < 40) {
                        Imgproc.line(mask, Point(x1, y1), Point(x2, y2), Scalar(255.0), 20)  // thick solid lines
                    }
                }
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
                Imgproc.dilate(mask, mask, kernel)
                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)

                val resultBmp = Bitmap.createBitmap(mask.cols(), mask.rows(), Bitmap.Config.ARGB_8888)
                org.opencv.android.Utils.matToBitmap(mask, resultBmp)
                val display = if (resultBmp.width > 512) {
                    val h = (512f / resultBmp.width * resultBmp.height).toInt()
                    Bitmap.createScaledBitmap(resultBmp, 512, h, true)
                } else resultBmp
                variants.add(display)
                gray.release()
                equalized.release()
                blurred.release()
                edges.release()
                lines.release()
                mask.release()
                if (resultBmp !== display) resultBmp.recycle()
            } catch (e: Exception) {
                Log.e("Exp2", "Failed variant ${index + 1}", e)
            }
        }
        src.release()
        srcBGR.release()
        Log.i("Exp2", "✅ 6 radial masks created (thick solid lines)")
        variants
    }

    /**
     * Experiment 3: Polar Tic Removal — pure mask only (6 images — wider + lower threshold)
     */
    suspend fun createExperiment3PolarVariants(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val variants = mutableListOf<Bitmap>()
        Log.i("Exp3", "Starting polar tic mask sweep (6 images — wider blur + lower threshold)")
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)
        variants.add(Bitmap.createScaledBitmap(original, thumbW, thumbH, true))

        val src = Mat()
        org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat()
        Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)
        val gray = Mat()
        Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)
        val center = Point(src.cols() / 2.0, src.rows() / 2.0)
        val maxRadius = Math.max(src.cols(), src.rows()) / 2.0

        val blurSizes = listOf(8f, 15f, 25f, 35f, 45f, 55f)

        for ((index, blurSize) in blurSizes.withIndex()) {
            try {
                val polar = Mat()
                Imgproc.linearPolar(gray, polar, center, maxRadius, Imgproc.INTER_LINEAR + Imgproc.WARP_FILL_OUTLIERS)
                val blurredPolar = Mat()
                Imgproc.GaussianBlur(polar, blurredPolar, Size(blurSize.toDouble(), 1.0), 0.0)
                val ticMask = Mat()
                Core.absdiff(polar, blurredPolar, ticMask)
                Imgproc.threshold(ticMask, ticMask, 15.0, 255.0, Imgproc.THRESH_BINARY)  // lower threshold

                val resultBmp = Bitmap.createBitmap(ticMask.cols(), ticMask.rows(), Bitmap.Config.ARGB_8888)
                org.opencv.android.Utils.matToBitmap(ticMask, resultBmp)
                val display = if (resultBmp.width > 512) {
                    val h = (512f / resultBmp.width * resultBmp.height).toInt()
                    Bitmap.createScaledBitmap(resultBmp, 512, h, true)
                } else resultBmp
                variants.add(display)
                polar.release()
                blurredPolar.release()
                ticMask.release()
                if (resultBmp !== display) resultBmp.recycle()
            } catch (e: Exception) {
                Log.e("Exp3", "Failed polar variant ${index + 1}", e)
            }
        }
        src.release()
        srcBGR.release()
        gray.release()
        Log.i("Exp3", "✅ 6 pure polar tic masks created")
        variants
    }

    /**
     * Experiment 4: Text-Only Mask + Masked-Original (logical AND)
     */
    suspend fun createExperiment4TextOnly(original: Bitmap, textBlocks: List<TextBlock>): List<Bitmap> = withContext(Dispatchers.IO) {
        val variants = mutableListOf<Bitmap>()
        Log.i("Exp4", "Creating text-only + masked-original (logical AND)")
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)
        variants.add(Bitmap.createScaledBitmap(original, thumbW, thumbH, true))

        val textMask = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(textMask)
        val bgPaint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; style = android.graphics.Paint.Style.FILL }
        val textPaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.FILL; textSize = 48f }
        canvas.drawRect(0f, 0f, original.width.toFloat(), original.height.toFloat(), bgPaint)
        textBlocks.forEach { block ->
            val r = block.boundingBox
            canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), textPaint)
            canvas.drawText(block.text, r.left.toFloat(), r.bottom.toFloat(), textPaint)
        }
        val displayTextMask = if (textMask.width > 512) {
            val h = (512f / textMask.width * textMask.height).toInt()
            Bitmap.createScaledBitmap(textMask, 512, h, true)
        } else textMask
        variants.add(displayTextMask)

        // Logical AND masked-original (only content inside white text boxes)
        val maskedOriginal = original.copy(Bitmap.Config.ARGB_8888, true)
        val maskedCanvas = android.graphics.Canvas(maskedOriginal)
        maskedCanvas.drawBitmap(original, 0f, 0f, null)
        val maskPaint = android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
        maskedCanvas.drawBitmap(textMask, 0f, 0f, maskPaint)
        val displayMasked = if (maskedOriginal.width > 512) {
            val h = (512f / maskedOriginal.width * maskedOriginal.height).toInt()
            Bitmap.createScaledBitmap(maskedOriginal, 512, h, true)
        } else maskedOriginal
        variants.add(displayMasked)

        textMask.recycle()
        maskedOriginal.recycle()
        Log.i("Exp4", "✅ Text-only + masked-original (logical AND) created")
        variants
    }

    /**
     * Experiment 5: Straight line segment masks (6 images) — lower minLength + thicker
     */
    suspend fun createExperiment5LineSegments(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val variants = mutableListOf<Bitmap>()
        Log.i("Exp5", "Starting line segment sweep (6 images — shorter tics + thick lines)")
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)
        variants.add(Bitmap.createScaledBitmap(original, thumbW, thumbH, true))

        val src = Mat()
        org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat()
        Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)
        val gray = Mat()
        Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)
        Core.bitwise_not(gray, gray)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 5.0, 40.0)

        val minLengths = listOf(5, 8, 12, 15, 20)

        for ((index, minLen) in minLengths.withIndex()) {
            try {
                val lines = Mat()
                Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 10, minLen.toDouble(), 2.0)
                val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
                for (i in 0 until lines.rows()) {
                    val line = lines.get(i, 0)
                    val x1 = line[0]
                    val y1 = line[1]
                    val x2 = line[2]
                    val y2 = line[3]
                    Imgproc.line(mask, Point(x1, y1), Point(x2, y2), Scalar(255.0), 18)  // thick solid lines
                }
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
                Imgproc.dilate(mask, mask, kernel)
                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)

                val resultBmp = Bitmap.createBitmap(mask.cols(), mask.rows(), Bitmap.Config.ARGB_8888)
                org.opencv.android.Utils.matToBitmap(mask, resultBmp)
                val display = if (resultBmp.width > 512) {
                    val h = (512f / resultBmp.width * resultBmp.height).toInt()
                    Bitmap.createScaledBitmap(resultBmp, 512, h, true)
                } else resultBmp
                variants.add(display)
                lines.release()
                mask.release()
                if (resultBmp !== display) resultBmp.recycle()
            } catch (e: Exception) {
                Log.e("Exp5", "Failed variant ${index + 1}", e)
            }
        }
        src.release()
        srcBGR.release()
        gray.release()
        edges.release()
        Log.i("Exp5", "✅ 6 line segment masks created (thick solid lines)")
        variants
    }

    /**
     * Production cleaning — unchanged
     */
    suspend fun createCleanedReference(original: Bitmap): Bitmap? = withContext(Dispatchers.IO) {
        Log.i("VehicleReferenceCleaning", "Starting fast single-pass cleaning on full-size image")
        val src = Mat()
        org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat()
        Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)
        val gray = Mat()
        Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)
        Core.bitwise_not(gray, gray)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 12.0, 68.0)
        val lines = Mat()
        Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 20, 15.0, 4.0)
        val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
        for (i in 0 until lines.rows()) {
            val line = lines.get(i, 0)
            val x1 = line[0].toInt()
            val y1 = line[1].toInt()
            val x2 = line[2].toInt()
            val y2 = line[3].toInt()
            val length = Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())
            if (length < 260) {
                Imgproc.line(mask, Point(x1.toDouble(), y1.toDouble()), Point(x2.toDouble(), y2.toDouble()), Scalar(255.0), 16)
            }
        }
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(mask, mask, kernel)
        val cleaned = Mat()
        Photo.inpaint(srcBGR, mask, cleaned, 14.0, Photo.INPAINT_TELEA)
        val result = Bitmap.createBitmap(cleaned.cols(), cleaned.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(cleaned, result)
        src.release()
        srcBGR.release()
        gray.release()
        edges.release()
        lines.release()
        mask.release()
        cleaned.release()
        Log.i("VehicleReferenceCleaning", "✅ Fast cleaning succeeded")
        result
    }

    suspend fun alignImages(
        reference: Bitmap,
        query: Bitmap,
        minInliers: Int = 15
    ): AlignmentResult = withContext(Dispatchers.IO) {
        val refMat = Mat()
        val queryMat = Mat()
        try {
            org.opencv.android.Utils.bitmapToMat(reference, refMat)
            org.opencv.android.Utils.bitmapToMat(query, queryMat)
            val refGray = Mat()
            val queryGray = Mat()
            Imgproc.cvtColor(refMat, refGray, Imgproc.COLOR_RGB2GRAY)
            Imgproc.cvtColor(queryMat, queryGray, Imgproc.COLOR_RGB2GRAY)
            val orb = ORB.create(500)
            val refKeypoints = MatOfKeyPoint()
            val queryKeypoints = MatOfKeyPoint()
            val refDescriptors = Mat()
            val queryDescriptors = Mat()
            orb.detectAndCompute(refGray, Mat(), refKeypoints, refDescriptors)
            orb.detectAndCompute(queryGray, Mat(), queryKeypoints, queryDescriptors)
            if (refDescriptors.empty() || queryDescriptors.empty()) {
                return@withContext AlignmentResult(false, null, 0f, "Not enough features detected")
            }
            val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
            val matches = MatOfDMatch()
            matcher.match(queryDescriptors, refDescriptors, matches)
            val goodMatches = mutableListOf<DMatch>()
            val minDist = matches.toList().minOfOrNull { it.distance } ?: 0f
            matches.toList().forEach { match ->
                if (match.distance < 2.5 * minDist) goodMatches.add(match)
            }
            if (goodMatches.size < minInliers) {
                return@withContext AlignmentResult(false, null, 0f, "Only ${goodMatches.size} good matches (need $minInliers)")
            }
            val srcPoints = MatOfPoint2f()
            val dstPoints = MatOfPoint2f()
            val srcList = mutableListOf<Point>()
            val dstList = mutableListOf<Point>()
            goodMatches.forEach { match ->
                val queryPt = queryKeypoints.toArray()[match.queryIdx].pt
                val refPt = refKeypoints.toArray()[match.trainIdx].pt
                srcList.add(queryPt)
                dstList.add(refPt)
            }
            srcPoints.fromList(srcList)
            dstPoints.fromList(dstList)
            val homography = Calib3d.findHomography(srcPoints, dstPoints, Calib3d.RANSAC, 5.0)
            val confidence = goodMatches.size.toFloat() / matches.toList().size.toFloat()
            val warped = Mat()
            Imgproc.warpPerspective(queryMat, warped, homography, Size(refMat.cols().toDouble(), refMat.rows().toDouble()))
            val alignedBitmap = Bitmap.createBitmap(warped.cols(), warped.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(warped, alignedBitmap)
            warped.release()
            homography.release()
            AlignmentResult(
                success = true,
                alignedImage = alignedBitmap,
                confidence = confidence,
                message = "Aligned with ${goodMatches.size} inliers (${"%.1f".format(confidence * 100)}%)"
            )
        } catch (e: Exception) {
            Log.e("ImageAlignment", "Alignment failed", e)
            AlignmentResult(false, null, 0f, "Exception: ${e.message}")
        } finally {
            refMat.release()
            queryMat.release()
        }
    }
}
