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

    // Returns Pair<cleanedBitmap, textBlocksString> so we only do OCR once
    suspend fun createCleanedReference(original: Bitmap): Pair<Bitmap?, String?> = withContext(Dispatchers.IO) {
        Log.i("VehicleReferenceCleaning", "Starting text-only mask cleaning + text extraction")
        val tempFile = File.createTempFile("ocr_temp", ".jpg")
        val out = java.io.FileOutputStream(tempFile)
        original.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.close()

        val ocrResult = com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils.extractFromPhoto(tempFile.absolutePath)
        tempFile.delete()

        val mask = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val bgPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, original.width.toFloat(), original.height.toFloat(), bgPaint)

        val textPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; textSize = 48f }
        ocrResult.textBlocks.forEach { block ->
            val r = block.boundingBox
            canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), textPaint)
        }

        val matMask = Mat()
        org.opencv.android.Utils.bitmapToMat(mask, matMask)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(matMask, matMask, kernel)

        val origMat = Mat()
        org.opencv.android.Utils.bitmapToMat(original, origMat)
        val maskedMat = Mat()
        Core.bitwise_and(origMat, matMask, maskedMat)

        val result = Bitmap.createBitmap(maskedMat.cols(), maskedMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(maskedMat, result)

        // Convert result to grayscale (as expected for cleaning/alignment)
        val grayMat = Mat()
        Imgproc.cvtColor(maskedMat, grayMat, Imgproc.COLOR_RGB2GRAY)
        val grayBitmap = Bitmap.createBitmap(grayMat.cols(), grayMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(grayMat, grayBitmap)

        origMat.release()
        matMask.release()
        maskedMat.release()
        grayMat.release()
        kernel.release()
        mask.recycle()

        val textBlocksString = ocrResult.textBlocks.joinToString("|") { block ->
            "${block.text}:${block.boundingBox.left},${block.boundingBox.top},${block.boundingBox.right},${block.boundingBox.bottom}"
        }

        Log.i("VehicleReferenceCleaning", "✅ Reference cleaned + text extracted in one pass (grayscale)")
        Pair(grayBitmap, textBlocksString)
    }

    // alignImages remains unchanged
    suspend fun alignImages(
        reference: Bitmap,
        query: Bitmap,
        minInliers: Int = 15,
        odometerCrop: android.graphics.RectF? = null,
        otherTextCrop: android.graphics.RectF? = null
    ): AlignmentResult = withContext(Dispatchers.IO) {
        val refMat = Mat()
        val queryMat = Mat()
        try {
            org.opencv.android.Utils.bitmapToMat(reference, refMat)
            org.opencv.android.Utils.bitmapToMat(query, queryMat)

            val refMasked = refMat.clone()
            if (odometerCrop != null) {
                val left = (odometerCrop.left * refMat.cols()).toInt().coerceAtLeast(0)
                val top = (odometerCrop.top * refMat.rows()).toInt().coerceAtLeast(0)
                val right = (odometerCrop.right * refMat.cols()).toInt().coerceAtMost(refMat.cols())
                val bottom = (odometerCrop.bottom * refMat.rows()).toInt().coerceAtMost(refMat.rows())
                if (right > left && bottom > top) {
                    val roi = Rect(left, top, right - left, bottom - top)
                    refMasked.submat(roi).setTo(Scalar(0.0, 0.0, 0.0))
                }
            }
            if (otherTextCrop != null) {
                val left = (otherTextCrop.left * refMat.cols()).toInt().coerceAtLeast(0)
                val top = (otherTextCrop.top * refMat.rows()).toInt().coerceAtLeast(0)
                val right = (otherTextCrop.right * refMat.cols()).toInt().coerceAtMost(refMat.cols())
                val bottom = (otherTextCrop.bottom * refMat.rows()).toInt().coerceAtMost(refMat.rows())
                if (right > left && bottom > top) {
                    val roi = Rect(left, top, right - left, bottom - top)
                    refMasked.submat(roi).setTo(Scalar(0.0, 0.0, 0.0))
                }
            }

            val refGray = Mat()
            val queryGray = Mat()
            Imgproc.cvtColor(refMasked, refGray, Imgproc.COLOR_RGB2GRAY)
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
            val matchList = matches.toList()
            val minDist = matchList.minOfOrNull { it.distance } ?: 0f

            for (i in 0 until matchList.size - 1) {
                val m = matchList[i]
                val n = matchList[i + 1]
                if (m.distance < 0.75 * n.distance && m.distance < 2.5 * minDist) {
                    goodMatches.add(m)
                }
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
            val confidence = goodMatches.size.toFloat() / matchList.size.toFloat()

            val warped = Mat()
            Imgproc.warpPerspective(queryMat, warped, homography, Size(refMat.cols().toDouble(), refMat.rows().toDouble()))

            val alignedBitmap = Bitmap.createBitmap(warped.cols(), warped.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(warped, alignedBitmap)

            warped.release()
            homography.release()
            refMasked.release()

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
