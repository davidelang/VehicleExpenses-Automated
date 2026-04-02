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
     * New test range — tighter than the previous over-destructive set,
     * stronger than the one before that still left some tics.
     * Starts at moderate-high aggression and ramps gradually.
     * This should give us the sweet spot we need.
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

        // Balanced ramp for this test round
        val paramSets = listOf(
            Triple(12.0, 65.0, 14),   // Variant 1 — start reasonably aggressive
            Triple(10.0, 60.0, 16),
            Triple(8.0,  55.0, 18),
            Triple(7.0,  50.0, 20),
            Triple(6.0,  48.0, 22),
            Triple(5.0,  45.0, 24),
            Triple(4.0,  42.0, 26)    // Variant 7 — quite strong but controlled
        )

        for ((index, params) in paramSets.withIndex()) {
            val (cannyLow, cannyHigh, thickness) = params
            try {
                val gray = Mat()
                Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)
                val edges = Mat()
                Imgproc.Canny(gray, edges, cannyLow, cannyHigh)

                // Slightly less sensitive line detection than last round
                val lines = Mat()
                Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 18, 12.0, 3.0)

                val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
                for (i in 0 until lines.rows()) {
                    val line = lines.get(i, 0)
                    val x1 = line[0].toInt()
                    val y1 = line[1].toInt()
                    val x2 = line[2].toInt()
                    val y2 = line[3].toInt()
                    val length = Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())
                    if (length < 270) {   // protect digits better
                        Imgproc.line(mask, Point(x1.toDouble(), y1.toDouble()), Point(x2.toDouble(), y2.toDouble()), Scalar(255.0), thickness)
                    }
                }

                val cleaned = Mat()
                Photo.inpaint(srcBGR, mask, cleaned, 13.0, Photo.INPAINT_TELEA)

                // Second pass — controlled
                val edges2 = Mat()
                Imgproc.Canny(cleaned, edges2, cannyLow * 0.7, cannyHigh * 0.7)
                val lines2 = Mat()
                Imgproc.HoughLinesP(edges2, lines2, 1.0, Math.PI / 180, 15, 9.0, 2.0)
                val mask2 = Mat.zeros(gray.size(), CvType.CV_8UC1)
                for (i in 0 until lines2.rows()) {
                    val line = lines2.get(i, 0)
                    val x1 = line[0].toInt()
                    val y1 = line[1].toInt()
                    val x2 = line[2].toInt()
                    val y2 = line[3].toInt()
                    if (Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()) < 240) {
                        Imgproc.line(mask2, Point(x1.toDouble(), y1.toDouble()), Point(x2.toDouble(), y2.toDouble()), Scalar(255.0), thickness + 3)
                    }
                }
                Photo.inpaint(cleaned, mask2, cleaned, 11.0, Photo.INPAINT_TELEA)

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
                edges2.release()
                lines2.release()
                mask2.release()
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
     * Production cleaned reference — uses a strong but safe setting (around Variant 3 level)
     */
    suspend fun createCleanedReference(original: Bitmap): Bitmap? = withContext(Dispatchers.IO) {
        Log.i("VehicleReferenceCleaning", "Starting fast single-pass cleaning on full-size image")
        val src = Mat()
        org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat()
        Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)

        val gray = Mat()
        Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 8.0, 50.0)
        val lines = Mat()
        Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 18, 12.0, 3.0)

        val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
        for (i in 0 until lines.rows()) {
            val line = lines.get(i, 0)
            val x1 = line[0].toInt()
            val y1 = line[1].toInt()
            val x2 = line[2].toInt()
            val y2 = line[3].toInt()
            val length = Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())
            if (length < 270) {
                Imgproc.line(mask, Point(x1.toDouble(), y1.toDouble()), Point(x2.toDouble(), y2.toDouble()), Scalar(255.0), 30)
            }
        }

        val cleaned = Mat()
        Photo.inpaint(srcBGR, mask, cleaned, 13.0, Photo.INPAINT_TELEA)

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
