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
     * Creates 8 versions of the reference image for diagnostics:
     * 0 = original (no cleaning)
     * 1–7 = increasingly aggressive tic removal
     */
    suspend fun createDiagnosticVariants(original: Bitmap): List<Bitmap> = withContext(Dispatchers.IO) {
        val variants = mutableListOf<Bitmap>()
        variants.add(original.copy(original.config, true)) // index 0 = original

        val params = listOf(
            // 1: mild
            Triple(30.0, 100.0, 5),
            // 2: medium
            Triple(25.0, 90.0, 6),
            // 3: strong
            Triple(20.0, 80.0, 7),
            // 4: aggressive
            Triple(15.0, 70.0, 8),
            // 5: very aggressive
            Triple(10.0, 60.0, 9),
            // 6: nuclear
            Triple(5.0, 50.0, 10),
            // 7: apocalypse
            Triple(3.0, 40.0, 12)
        )

        val src = Mat()
        org.opencv.android.Utils.bitmapToMat(original, src)
        val srcBGR = Mat()
        Imgproc.cvtColor(src, srcBGR, Imgproc.COLOR_RGBA2BGR)

        for ((cannyLow, cannyHigh, thickness) in params) {
            val gray = Mat()
            Imgproc.cvtColor(srcBGR, gray, Imgproc.COLOR_BGR2GRAY)

            val edges = Mat()
            Imgproc.Canny(gray, edges, cannyLow, cannyHigh)

            val lines = Mat()
            Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 30, 15.0, 5.0)

            val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
            for (i in 0 until lines.rows()) {
                val line = lines.get(i, 0)
                val x1 = line[0].toInt()
                val y1 = line[1].toInt()
                val x2 = line[2].toInt()
                val y2 = line[3].toInt()
                val length = Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())
                if (length < 220) {
                    Imgproc.line(mask, Point(x1.toDouble(), y1.toDouble()), Point(x2.toDouble(), y2.toDouble()), Scalar(255.0), thickness)
                }
            }

            val cleaned = Mat()
            Photo.inpaint(srcBGR, mask, cleaned, 8.0, Photo.INPAINT_TELEA)

            // Second pass
            val edges2 = Mat()
            Imgproc.Canny(cleaned, edges2, cannyLow * 0.8, cannyHigh * 0.8)
            val lines2 = Mat()
            Imgproc.HoughLinesP(edges2, lines2, 1.0, Math.PI / 180, 25, 12.0, 4.0)
            val mask2 = Mat.zeros(gray.size(), CvType.CV_8UC1)
            for (i in 0 until lines2.rows()) {
                val line = lines2.get(i, 0)
                val x1 = line[0].toInt()
                val y1 = line[1].toInt()
                val x2 = line[2].toInt()
                val y2 = line[3].toInt()
                if (Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()) < 200) {
                    Imgproc.line(mask2, Point(x1.toDouble(), y1.toDouble()), Point(x2.toDouble(), y2.toDouble()), Scalar(255.0), thickness - 1)
                }
            }
            Photo.inpaint(cleaned, mask2, cleaned, 6.0, Photo.INPAINT_TELEA)

            // Green debug tint so we can instantly tell it's cleaned
            val debug = Mat()
            Imgproc.cvtColor(cleaned, debug, Imgproc.COLOR_BGR2RGBA)
            val greenTint = Mat.zeros(debug.size(), CvType.CV_8UC4)
            greenTint.setTo(Scalar(0.0, 55.0, 25.0, 0.0))
            Core.addWeighted(debug, 1.0, greenTint, 0.4, 45.0, debug)

            val result = Bitmap.createBitmap(debug.cols(), debug.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(debug, result)
            variants.add(result)

            // cleanup
            gray.release()
            edges.release()
            lines.release()
            mask.release()
            cleaned.release()
            edges2.release()
            lines2.release()
            mask2.release()
            debug.release()
        }

        src.release()
        srcBGR.release()
        Log.i("VehicleReferenceCleaning", "✅ Created 8 diagnostic variants (0=original, 1-7=aggressive)")
        variants
    }

    // ... (rest of the file unchanged - alignImages remains exactly as it was)
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
