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
     * OLD METHOD — kept exactly as requested (only original + Variants 1-3)
     */
    suspend fun createDiagnosticVariants(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val variants = mutableListOf<Bitmap>()
        Log.i("ImageAlignment", "Starting createDiagnosticVariants on ${original.width}x${original.height} image")

        // 0 = original (downscaled)
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)
        variants.add(Bitmap.createScaledBitmap(original, thumbW, thumbH, true))
        Log.i("ImageAlignment", "Variant 0 (original) created")

        val src = Mat()
        org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat()
        Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)

        val paramSets = listOf(
            Triple(14.0, 72.0, 14),   // Variant 1
            Triple(12.0, 68.0, 16),   // Variant 2
            Triple(11.0, 66.0, 17)    // Variant 3
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
                        Imgproc.line(mask, Point(x1.toDouble(), y1.toDouble()), Point(x2.toDouble(), y2.toDouble()), Scalar(255.0), thickness)
                    }
                }

                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
                Imgproc.dilate(mask, mask, kernel)

                val cleaned = Mat()
                Photo.inpaint(srcBGR, mask, cleaned, 14.0, Photo.INPAINT_TELEA)

                val debug = Mat()
                Imgproc.cvtColor(cleaned, debug, Imgproc.COLOR_BGR2RGBA)
                val greenTint = Mat.zeros(debug.size(), CvType.CV_8UC4)
                greenTint.setTo(Scalar(0.0, 55.0, 25.0, 0.0))
                Core.addWeighted(debug, 1.0, greenTint, 0.4, 45.0, debug)

                val result = Bitmap.createBitmap(debug.cols(), debug.rows(), Bitmap.Config.ARGB_8888)
                org.opencv.android.Utils.matToBitmap(debug, result)

                val gridBmp = if (result.width > 512) {
                    val h = (512f / result.width * result.height).toInt()
                    Bitmap.createScaledBitmap(result, 512, h, true)
                } else result

                variants.add(gridBmp)
                Log.i("ImageAlignment", "Variant ${index + 1} created successfully")

                gray.release()
                edges.release()
                lines.release()
                mask.release()
                cleaned.release()
                debug.release()
                if (result !== gridBmp) result.recycle()
            } catch (e: Exception) {
                Log.e("ImageAlignment", "Failed to create variant ${index + 1}", e)
            }
        }

        src.release()
        srcBGR.release()
        Log.i("ImageAlignment", "✅ Finished createDiagnosticVariants — ${variants.size} variants created")
        variants
    }

    /**
     * OLD RADIAL LINE SUBTRACTION METHOD — restored exactly as it was (full thickness)
     */
    suspend fun createRadialLineRemovalSteps(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<Bitmap>()
        Log.i("ImageAlignment", "Starting createRadialLineRemovalSteps on ${original.width}x${original.height} image")

        // Step 0: Original (downscaled)
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)
        steps.add(Bitmap.createScaledBitmap(original, thumbW, thumbH, true))

        val src = Mat()
        org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat()
        Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)

        val gray = Mat()
        Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)
        Core.bitwise_not(gray, gray)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(blurred, edges, 10.0, 60.0)

        val lines = Mat()
        Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 15, 30.0, 5.0)

        val centerX = src.cols() / 2.0
        val centerY = src.rows() / 2.0

        val linesMask = Mat.zeros(gray.size(), CvType.CV_8UC1)
        for (i in 0 until lines.rows()) {
            val line = lines.get(i, 0)
            val x1 = line[0]
            val y1 = line[1]
            val x2 = line[2]
            val y2 = line[3]
            val length = Math.hypot(x2 - x1, y2 - y1)

            if (length < 30) continue

            val distToCenter = Math.abs((y2 - y1) * (x1 - centerX) - (x2 - x1) * (y1 - centerY)) / length
            if (distToCenter < 40) {
                Imgproc.line(linesMask, Point(x1, y1), Point(x2, y2), Scalar(255.0), 14)
            }
        }

        val linesBmp = Bitmap.createBitmap(linesMask.cols(), linesMask.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(linesMask, linesBmp)
        steps.add(linesBmp)

        Core.bitwise_not(linesMask, linesMask)
        val invertedBmp = Bitmap.createBitmap(linesMask.cols(), linesMask.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(linesMask, invertedBmp)
        steps.add(invertedBmp)

        val cleaned = Mat()
        Photo.inpaint(srcBGR, linesMask, cleaned, 14.0, Photo.INPAINT_TELEA)

        val finalBmp = Bitmap.createBitmap(cleaned.cols(), cleaned.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(cleaned, finalBmp)
        steps.add(finalBmp)

        src.release()
        srcBGR.release()
        gray.release()
        blurred.release()
        edges.release()
        lines.release()
        linesMask.release()
        cleaned.release()

        Log.i("ImageAlignment", "✅ Finished createRadialLineRemovalSteps — 4 step images created")
        steps
    }

    /**
     * NEW ARC-MASK REMOVAL METHOD — robust fallback for large tic ring
     * Uses lenient HoughCircles + strong fallback to image center + wide radius range
     * Blacks out the entire tic arc (inner/outer radius) exactly as you requested.
     */
    suspend fun createArcMaskRemovalSteps(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<Bitmap>()
        Log.i("ImageAlignment", "Starting createArcMaskRemovalSteps on ${original.width}x${original.height} image")

        // Step 0: Original (downscaled)
        val thumbW = if (original.width > 512) 512 else original.width
        val thumbH = (thumbW.toFloat() / original.width * original.height).toInt().coerceAtMost(512)
        steps.add(Bitmap.createScaledBitmap(original, thumbW, thumbH, true))

        val src = Mat()
        org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat()
        Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)

        val gray = Mat()
        Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)

        // Detect gauge circle with very lenient parameters for large circles
        val circles = Mat()
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.2, 150.0, 70.0, 25.0, 100, 400)

        var centerX = src.cols() / 2.0
        var centerY = src.rows() / 2.0
        var gaugeRadius = Math.min(src.cols(), src.rows()) * 0.42   // strong fallback

        if (circles.cols() > 0) {
            // Take the largest detected circle
            val largest = circles.get(0, 0)
            centerX = largest[0]
            centerY = largest[1]
            gaugeRadius = largest[2]
            Log.i("ImageAlignment", "Detected gauge circle at ($centerX, $centerY) radius $gaugeRadius")
        } else {
            Log.i("ImageAlignment", "No circle detected — using strong fallback center + radius $gaugeRadius")
        }

        // Wide tic ring that covers all tics on both dashboards you showed
        val innerRadius = gaugeRadius * 0.55
        val outerRadius = gaugeRadius * 0.92

        val arcMask = Mat.zeros(gray.size(), CvType.CV_8UC1)
        Imgproc.circle(arcMask, Point(centerX, centerY), outerRadius.toInt(), Scalar(255.0), -1)
        Imgproc.circle(arcMask, Point(centerX, centerY), innerRadius.toInt(), Scalar(0.0), -1)

        // Step 1: Extracted Tic Arc Mask (full ring)
        val maskBmp = Bitmap.createBitmap(arcMask.cols(), arcMask.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(arcMask, maskBmp)
        steps.add(maskBmp)

        // Step 2: Inverted Tic Arc Mask
        Core.bitwise_not(arcMask, arcMask)
        val invertedBmp = Bitmap.createBitmap(arcMask.cols(), arcMask.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(arcMask, invertedBmp)
        steps.add(invertedBmp)

        // Step 3: Final Merged (tic-free)
        val cleaned = Mat()
        Photo.inpaint(srcBGR, arcMask, cleaned, 14.0, Photo.INPAINT_TELEA)

        val finalBmp = Bitmap.createBitmap(cleaned.cols(), cleaned.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(cleaned, finalBmp)
        steps.add(finalBmp)

        // Cleanup
        src.release()
        srcBGR.release()
        gray.release()
        circles.release()
        arcMask.release()
        cleaned.release()

        Log.i("ImageAlignment", "✅ Finished createArcMaskRemovalSteps — 4 step images created")
        steps
    }

    /**
     * Production cleaning — unchanged (still uses the best sweet-spot from earlier tests)
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
