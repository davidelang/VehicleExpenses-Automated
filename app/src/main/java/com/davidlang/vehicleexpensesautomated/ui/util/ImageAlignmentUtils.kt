package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.features2d.*
import org.opencv.imgproc.Imgproc
import org.opencv.calib3d.Calib3d
import org.opencv.photo.Photo
import java.io.File

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
            Log.i("ImageAlignment", "OpenCV initialized successfully")
        }
    }

    private fun detectSpeedometerCenter(original: Bitmap): Point? = try {
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(original, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        val circles = Mat()
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.0, 50.0, 100.0, 30.0, 80, 200)
        mat.release()
        gray.release()
        if (circles.rows() > 0) {
            val c = circles.get(0, 0)
            Point(c[0], c[1])
        } else null
    } catch (e: Exception) {
        null
    }

    private fun drawRedCenter(original: Bitmap, center: Point?): Bitmap {
        val bmp = original.copy(Bitmap.Config.ARGB_8888, true)
        center?.let {
            val canvas = Canvas(bmp)
            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }
            canvas.drawCircle(it.x.toFloat(), it.y.toFloat(), 120f, paint)
        }
        return bmp
    }

    // Production cleaning — final text-only mask (Experiment 4)
    suspend fun createCleanedReference(original: Bitmap): Bitmap? = withContext(Dispatchers.IO) {
        Log.i("VehicleReferenceCleaning", "Starting text-only mask cleaning (Exp 4)")

        val tempFile = File.createTempFile("ocr_temp", ".jpg")
        val out = java.io.FileOutputStream(tempFile)
        original.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.close()

        val ocrResult = com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils.extractFromPhoto(tempFile.absolutePath)

        tempFile.delete()

        val textMask = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(textMask)
        val bgPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        val textPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; textSize = 48f }
        canvas.drawRect(0f, 0f, original.width.toFloat(), original.height.toFloat(), bgPaint)

        ocrResult.textBlocks.forEach { block ->
            val r = block.boundingBox
            canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), textPaint)
            canvas.drawText(block.text, r.left.toFloat(), r.bottom.toFloat(), textPaint)
        }

        val matMask = Mat()
        org.opencv.android.Utils.bitmapToMat(textMask, matMask)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(matMask, matMask, kernel)
        val dilatedMask = Bitmap.createBitmap(matMask.cols(), matMask.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(matMask, dilatedMask)
        matMask.release()
        kernel.release()

        val origMat = Mat()
        org.opencv.android.Utils.bitmapToMat(original, origMat)
        val maskMat = Mat()
        org.opencv.android.Utils.bitmapToMat(dilatedMask, maskMat)

        val maskedMat = Mat()
        Core.bitwise_and(origMat, maskMat, maskedMat)

        val result = Bitmap.createBitmap(maskedMat.cols(), maskedMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(maskedMat, result)

        origMat.release()
        maskMat.release()
        maskedMat.release()
        textMask.recycle()
        dilatedMask.recycle()

        Log.i("VehicleReferenceCleaning", "✅ Reference cleaned with text-only mask (Exp 4)")
        result
    }

    suspend fun alignImages(reference: Bitmap, query: Bitmap, minInliers: Int = 15): AlignmentResult = withContext(Dispatchers.IO) {
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
