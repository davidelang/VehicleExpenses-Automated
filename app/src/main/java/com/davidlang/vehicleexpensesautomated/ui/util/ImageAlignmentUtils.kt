package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.features2d.*
import org.opencv.imgproc.Imgproc
import org.opencv.calib3d.Calib3d
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// ML Kit imports (only for createCleanedReference)
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text

import com.davidlang.vehicleexpensesautomated.ui.util.TextBlock
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils

data class AlignmentResult(
    val success: Boolean,
    val alignedImage: Bitmap?,
    val confidence: Float,
    val message: String,
    val refKeypoints: Int = 0,
    val queryKeypoints: Int = 0,
    val goodMatchesCount: Int = 0,
    val method: String = "feature"
)

object ImageAlignmentUtils {
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("ImageAlignment", "OpenCV initialization failed!")
        } else {
            Log.i("ImageAlignment", "OpenCV initialized successfully")
        }
    }

    suspend fun createCleanedReference(
        original: Bitmap,
        odometerCrop: RectF? = null,
        otherTextCrop: RectF? = null
    ): Pair<Bitmap?, String?> = withContext(Dispatchers.IO) {
        Log.i("VehicleReferenceCleaning", "ML Kit text-box detection (last working version)")

        val inputImage = InputImage.fromBitmap(original, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val result = suspendCoroutine<Text?> { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        } ?: return@withContext null to null

        // Create mask: black background + white rectangles in text areas
        val mask = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val bgPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, original.width.toFloat(), original.height.toFloat(), bgPaint)

        val textPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }

        val textBlocksString = buildString {
            result.textBlocks.forEach { block ->
                val r = block.boundingBox ?: return@forEach
                val blockRect = RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())

                val insideOdometer = odometerCrop != null && odometerCrop.contains(blockRect)
                val insideOtherText = otherTextCrop != null && otherTextCrop.contains(blockRect)

                if (!insideOdometer && !insideOtherText) {
                    canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), textPaint)
                    append("${block.text}:${r.left},${r.top},${r.right},${r.bottom}|")
                }
            }
        }

        // Convert mask and original to OpenCV mats
        val maskMat = Mat()
        org.opencv.android.Utils.bitmapToMat(mask, maskMat)
        val origMat = Mat()
        org.opencv.android.Utils.bitmapToMat(original, origMat)

        // Bitwise AND → keep original pixels in text areas, black everywhere else
        val cleanedMat = Mat()
        Core.bitwise_and(origMat, maskMat, cleanedMat)

        val resultBitmap = Bitmap.createBitmap(cleanedMat.cols(), cleanedMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(cleanedMat, resultBitmap)

        mask.recycle()
        maskMat.release()
        origMat.release()
        cleanedMat.release()

        Log.i("VehicleReferenceCleaning", "ML Kit text-box cleaning complete — text areas from original, background blacked out")
        Pair(resultBitmap, textBlocksString)
    }

    private fun argMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>): Float {
        if (refBlocks.isEmpty() || queryBlocks.isEmpty()) return 0f
        var score = 0f
        for (r in refBlocks) {
            for (q in queryBlocks) {
                val textSim = if (r.text == q.text) 1f else 0.5f * (1f - levenshtein(r.text, q.text).toFloat() / max(r.text.length, q.text.length))
                val boxSim = boxIoU(r.boundingBox, q.boundingBox)
                score += textSim * boxSim
            }
        }
        return score / (refBlocks.size * queryBlocks.size)
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i-1] == s2[j-1]) 0 else 1
                dp[i][j] = minOf(dp[i-1][j] + 1, dp[i][j-1] + 1, dp[i-1][j-1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun boxIoU(r1: android.graphics.Rect, r2: android.graphics.Rect): Float {
        val interLeft = max(r1.left, r2.left)
        val interTop = max(r1.top, r2.top)
        val interRight = min(r1.right, r2.right)
        val interBottom = min(r1.bottom, r2.bottom)
        if (interLeft >= interRight || interTop >= interBottom) return 0f
        val interArea = (interRight - interLeft) * (interBottom - interTop).toFloat()
        val area1 = (r1.right - r1.left) * (r1.bottom - r1.top).toFloat()
        val area2 = (r2.right - r2.left) * (r2.bottom - r2.top).toFloat()
        return interArea / (area1 + area2 - interArea)
    }

    private fun histogramMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>): Float {
        val gridSize = 5
        val refHist = IntArray(gridSize * gridSize)
        val queryHist = IntArray(gridSize * gridSize)
        val refWords = mutableSetOf<String>()
        val queryWords = mutableSetOf<String>()
        for (b in refBlocks) {
            val gx = (b.boundingBox.centerX() / 1000f * gridSize).toInt().coerceIn(0, gridSize-1)
            val gy = (b.boundingBox.centerY() / 1000f * gridSize).toInt().coerceIn(0, gridSize-1)
            refHist[gx + gy * gridSize]++
            refWords.add(b.text.lowercase())
        }
        for (b in queryBlocks) {
            val gx = (b.boundingBox.centerX() / 1000f * gridSize).toInt().coerceIn(0, gridSize-1)
            val gy = (b.boundingBox.centerY() / 1000f * gridSize).toInt().coerceIn(0, gridSize-1)
            queryHist[gx + gy * gridSize]++
            queryWords.add(b.text.lowercase())
        }
        var histScore = 0f
        for (i in refHist.indices) {
            histScore += min(refHist[i], queryHist[i]).toFloat()
        }
        val textScore = refWords.intersect(queryWords).size.toFloat() / max(refWords.size, queryWords.size)
        return (histScore / (refBlocks.size + queryBlocks.size)) * 0.6f + textScore * 0.4f
    }

    private fun embeddingMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>): Float {
        val allWords = (refBlocks + queryBlocks).map { it.text.lowercase() }.toSet()
        val refVec = FloatArray(allWords.size)
        val queryVec = FloatArray(allWords.size)
        val wordMap = allWords.withIndex().associate { it.value to it.index }
        for (b in refBlocks) {
            val idx = wordMap[b.text.lowercase()] ?: continue
            refVec[idx] += 1f
        }
        for (b in queryBlocks) {
            val idx = wordMap[b.text.lowercase()] ?: continue
            queryVec[idx] += 1f
        }
        var dot = 0f
        var normRef = 0f
        var normQuery = 0f
        for (i in refVec.indices) {
            dot += refVec[i] * queryVec[i]
            normRef += refVec[i] * refVec[i]
            normQuery += queryVec[i] * queryVec[i]
        }
        val textSim = if (normRef > 0 && normQuery > 0) dot / (kotlin.math.sqrt(normRef) * kotlin.math.sqrt(normQuery)) else 0f
        var layoutSim = 0f
        for (r in refBlocks) {
            for (q in queryBlocks) {
                layoutSim += boxIoU(r.boundingBox, q.boundingBox)
            }
        }
        layoutSim /= (refBlocks.size * queryBlocks.size).toFloat()
        return textSim * 0.7f + layoutSim * 0.3f
    }

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
            val refKpCount = refKeypoints.rows()
            val queryKpCount = queryKeypoints.rows()
            if (refDescriptors.empty() || queryDescriptors.empty()) {
                return@withContext AlignmentResult(false, null, 0f, "Not enough features detected", refKpCount, queryKpCount, 0, "feature")
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
            val goodMatchesCount = goodMatches.size
            if (goodMatchesCount < minInliers) {
                return@withContext AlignmentResult(false, null, 0f, "Only $goodMatchesCount good matches (need $minInliers)", refKpCount, queryKpCount, goodMatchesCount, "feature")
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
            
            // GEOMETRIC SANITY CHECK - Relaxed for wide/tight zoom variations
            val h00 = homography.get(0, 0)[0]
            val h01 = homography.get(0, 1)[0]
            val h10 = homography.get(1, 0)[0]
            val h11 = homography.get(1, 1)[0]
            val det = h00 * h11 - h01 * h10
            
            // Lenient check for scale (approx 0.2x to 5.0x) and no extreme flipping
            val isSane = det > 0.04 && det < 25.0 && Math.abs(h01) < 1.2 && Math.abs(h10) < 1.2
            
            if (!isSane) {
                homography.release()
                return@withContext AlignmentResult(false, null, 0f, "Homography failed sanity check (det=${"%.2f".format(det)})", refKpCount, queryKpCount, goodMatchesCount, "feature")
            }

            val confidence = goodMatchesCount.toFloat() / matchList.size.toFloat()
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
                message = "Aligned with $goodMatchesCount inliers (${"%.1f".format(confidence * 100)}%)",
                refKeypoints = refKpCount,
                queryKeypoints = queryKpCount,
                goodMatchesCount = goodMatchesCount,
                method = "feature"
            )
        } catch (e: Exception) {
            Log.e("ImageAlignment", "Alignment failed", e)
            AlignmentResult(false, null, 0f, "Exception: ${e.message}", 0, 0, 0, "feature")
        } finally {
            refMat.release()
            queryMat.release()
        }
    }

    suspend fun matchWithAllMethods(
        reference: Bitmap,
        query: Bitmap,
        refOcr: OcrResult,
        queryOcr: OcrResult,
        odometerCrop: android.graphics.RectF? = null,
        otherTextCrop: android.graphics.RectF? = null
    ): Map<String, AlignmentResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, AlignmentResult>()
        
        val t0 = System.currentTimeMillis()
        val featureResult = alignImages(reference, query, 10, odometerCrop, otherTextCrop)
        val tFeature = System.currentTimeMillis() - t0
        results["feature"] = featureResult.copy(message = featureResult.message + " (${tFeature}ms)")
        
        val t1 = System.currentTimeMillis()
        val argScore = argMatch(refOcr.textBlocks, queryOcr.textBlocks)
        val tArg = System.currentTimeMillis() - t1
        results["arg"] = AlignmentResult(true, null, argScore, "ARG score: ${"%.2f".format(argScore)} (${tArg}ms)", method = "arg")
        
        val t2 = System.currentTimeMillis()
        val histScore = histogramMatch(refOcr.textBlocks, queryOcr.textBlocks)
        val tHist = System.currentTimeMillis() - t2
        results["histogram"] = AlignmentResult(true, null, histScore, "Histogram+text score: ${"%.2f".format(histScore)} (${tHist}ms)", method = "histogram")
        
        val t3 = System.currentTimeMillis()
        val embScore = embeddingMatch(refOcr.textBlocks, queryOcr.textBlocks)
        val tEmb = System.currentTimeMillis() - t3
        results["embedding"] = AlignmentResult(true, null, embScore, "Embedding proxy score: ${"%.2f".format(embScore)} (${tEmb}ms)", method = "embedding")
        
        // Consensus: Weighted average of multiple metrics
        val featScoreNorm = if (featureResult.success) (featureResult.goodMatchesCount / 50f).coerceIn(0f, 1f) else 0f
        val consensusScore = (featScoreNorm * 0.4f) + (argScore * 0.2f) + (histScore * 0.2f) + (embScore * 0.2f)
        results["consensus"] = AlignmentResult(true, null, consensusScore, "Consensus score: ${"%.2f".format(consensusScore)}", method = "consensus")
        
        results
    }
}
