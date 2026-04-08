package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.features2d.*
import org.opencv.imgproc.Imgproc
import org.opencv.calib3d.Calib3d
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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

    private fun boxIoU(r1: android.graphics.Rect, r2: android.graphics.Rect): Float {
        val interLeft = max(r1.left, r2.left)
        val interTop = max(r1.top, r2.top)
        val interRight = min(r1.right, r2.right)
        val interBottom = min(r1.bottom, r2.bottom)
        if (interRight <= interLeft || interBottom <= interTop) return 0f
        val interArea = (interRight - interLeft) * (interBottom - interTop).toFloat()
        val area1 = (r1.right - r1.left) * (r1.bottom - r1.top).toFloat()
        val area2 = (r2.right - r2.left) * (r2.bottom - r2.top).toFloat()
        return interArea / (area1 + area2 - interArea)
    }

    private fun argMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>, globalWordCounts: Map<String, Int> = emptyMap()): Float {
        if (refBlocks.isEmpty() || queryBlocks.isEmpty()) return 0f
        var score = 0f
        var totalWeight = 0f
        
        val refWords = refBlocks.map { it.text.lowercase().trim() }.toSet()
        
        for (r in refBlocks) {
            val word = r.text.lowercase().trim()
            val weight = 1.0f / (globalWordCounts[word] ?: 1).toFloat()
            totalWeight += weight
            
            for (q in queryBlocks) {
                if (word == q.text.lowercase().trim()) {
                    score += weight
                    break // Only match once per reference block
                }
            }
        }
        
        // Negative voting: penalize words found in query that are NOT in reference
        // but ARE known to exist in other vehicles (i.e. they are in globalWordCounts).
        var penalty = 0f
        for (q in queryBlocks) {
            val word = q.text.lowercase().trim()
            if (!refWords.contains(word) && globalWordCounts.containsKey(word)) {
                // It's a word known from other vehicles, but not this one.
                // Rare words (low global count) have a higher penalty weight.
                val weight = 1.0f / globalWordCounts[word]!!.toFloat()
                penalty += weight * 0.5f // Weight the penalty
            }
        }
        
        return if (totalWeight > 0) (score - penalty) / totalWeight else 0f
    }

    fun anchorMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>): Float {
        if (refBlocks.isEmpty() || queryBlocks.isEmpty()) return 0f
        val anchors = listOf("MPH", "KM/H", "160", "140", "120", "100", "80", "60", "40", "20", "PRNDL", "TRIP", "ODO")
        var matchCount = 0
        var totalPossible = 0
        
        for (anchor in anchors) {
            val inRef = refBlocks.any { it.text.contains(anchor, ignoreCase = true) }
            val inQuery = queryBlocks.any { it.text.contains(anchor, ignoreCase = true) }
            
            if (inQuery && !inRef) {
                // HARD VETO: This anchor exists on the dash we are looking at, 
                // but NOT on the reference for this vehicle. 
                // Therefore it cannot be this vehicle.
                return -1.0f
            }
            
            if (inRef) {
                totalPossible++
                if (inQuery) matchCount++
            }
        }
        
        if (totalPossible == 0) return 0f
        return matchCount.toFloat() / totalPossible.toFloat()
    }

    fun projectCropViaAnchor(
        refBlocks: List<TextBlock>,
        queryBlocks: List<TextBlock>,
        refCrop: android.graphics.RectF,
        refW: Int,
        refH: Int,
        queryW: Int,
        queryH: Int
    ): android.graphics.RectF? {
        val anchors = listOf("MPH", "KM/H", "160", "140", "120", "100", "80", "60", "40", "20", "PRNDL", "ODO", "TRIP")
        for (anchorText in anchors) {
            val refAnchor = refBlocks.find { it.text.contains(anchorText, ignoreCase = true) }
            val queryAnchor = queryBlocks.find { it.text.contains(anchorText, ignoreCase = true) }
            if (refAnchor != null && queryAnchor != null) {
                val rA = refAnchor.boundingBox
                val qA = queryAnchor.boundingBox
                val scaleX = qA.width().toFloat() / rA.width().toFloat()
                val scaleY = qA.height().toFloat() / rA.height().toFloat()
                val avgScale = (scaleX + scaleY) / 2f
                val refCropPx = android.graphics.RectF(refCrop.left * refW, refCrop.top * refH, refCrop.right * refW, refCrop.bottom * refH)
                val dx = refCropPx.centerX() - rA.centerX()
                val dy = refCropPx.centerY() - rA.centerY()
                val qCenterX = qA.centerX() + (dx * avgScale)
                val qCenterY = qA.centerY() + (dy * avgScale)
                val qWidth = refCropPx.width() * avgScale
                val qHeight = refCropPx.height() * avgScale
                val qLeft = (qCenterX - qWidth / 2f) / queryW
                val qTop = (qCenterY - qHeight / 2f) / queryH
                val qRight = (qCenterX + qWidth / 2f) / queryW
                val qBottom = (qCenterY + qHeight / 2f) / queryH
                return android.graphics.RectF(qLeft.coerceIn(0f, 1f), qTop.coerceIn(0f, 1f), qRight.coerceIn(0f, 1f), qBottom.coerceIn(0f, 1f))
            }
        }
        return null
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

    private fun embeddingMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>, globalWordCounts: Map<String, Int> = emptyMap()): Float {
        val allWords = (refBlocks + queryBlocks).map { it.text.lowercase().trim() }.toSet()
        val refVec = FloatArray(allWords.size)
        val queryVec = FloatArray(allWords.size)
        val wordMap = allWords.withIndex().associate { it.value to it.index }
        
        for (b in refBlocks) {
            val word = b.text.lowercase().trim()
            val idx = wordMap[word] ?: continue
            val weight = 1.0f / (globalWordCounts[word] ?: 1).toFloat()
            refVec[idx] += weight
        }
        for (b in queryBlocks) {
            val word = b.text.lowercase().trim()
            val idx = wordMap[word] ?: continue
            val weight = 1.0f / (globalWordCounts[word] ?: 1).toFloat()
            queryVec[idx] += weight
        }
        var dot = 0f
        var normRef = 0f
        var normQuery = 0f
        for (i in refVec.indices) {
            dot += refVec[i] * queryVec[i]
            normRef += refVec[i] * refVec[i]
            normQuery += queryVec[i] * queryVec[i]
        }
        val textSim = if (normRef > 0 && normQuery > 0) dot / (sqrt(normRef.toDouble()).toFloat() * sqrt(normQuery.toDouble()).toFloat()) else 0f
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
                    val roi = org.opencv.core.Rect(left, top, right - left, bottom - top)
                    val sub = refMasked.submat(roi)
                    sub.setTo(Scalar(0.0, 0.0, 0.0))
                    sub.release()
                }
            }
            if (otherTextCrop != null) {
                val left = (otherTextCrop.left * refMat.cols()).toInt().coerceAtLeast(0)
                val top = (otherTextCrop.top * refMat.rows()).toInt().coerceAtLeast(0)
                val right = (otherTextCrop.right * refMat.cols()).toInt().coerceAtMost(refMat.cols())
                val bottom = (otherTextCrop.bottom * refMat.rows()).toInt().coerceAtMost(refMat.rows())
                if (right > left && bottom > top) {
                    val roi = org.opencv.core.Rect(left, top, right - left, bottom - top)
                    val sub = refMasked.submat(roi)
                    sub.setTo(Scalar(0.0, 0.0, 0.0))
                    sub.release()
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
            val srcList = mutableListOf<org.opencv.core.Point>()
            val dstList = mutableListOf<org.opencv.core.Point>()
            goodMatches.forEach { match ->
                val queryPt = queryKeypoints.toArray()[match.queryIdx].pt
                val refPt = refKeypoints.toArray()[match.trainIdx].pt
                srcList.add(queryPt)
                dstList.add(refPt)
            }
            srcPoints.fromList(srcList)
            dstPoints.fromList(dstList)
            val mask = Mat()
            val homography = Calib3d.findHomography(srcPoints, dstPoints, Calib3d.RANSAC, 5.0, mask)
            
            var inlierCount = 0
            if (!homography.empty()) {
                for (i in 0 until mask.rows()) {
                    if (mask.get(i, 0)[0].toInt() == 1) {
                        inlierCount++
                    }
                }
            }
            mask.release()

            if (homography.empty() || inlierCount < minInliers) {
                if (!homography.empty()) homography.release()
                return@withContext AlignmentResult(false, null, 0f, "Homography failed or too few inliers ($inlierCount < $minInliers)", refKpCount, queryKpCount, goodMatchesCount, "feature")
            }
            
            // GEOMETRIC SANITY CHECK
            val h00 = homography.get(0, 0)[0]
            val h01 = homography.get(0, 1)[0]
            val h10 = homography.get(1, 0)[0]
            val h11 = homography.get(1, 1)[0]
            val det = h00 * h11 - h01 * h10
            
            // Stricter sanity checks to prevent "wedges of color" (severe perspective skew/scale)
            val isSane = det > 0.1 && det < 10.0 && Math.abs(h01) < 0.5 && Math.abs(h10) < 0.5
            
            if (!isSane) {
                homography.release()
                return@withContext AlignmentResult(false, null, 0f, "Homography failed sanity check (det=${"%.2f".format(det)}, skewX=${"%.2f".format(h01)}, skewY=${"%.2f".format(h10)})", refKpCount, queryKpCount, inlierCount, "feature")
            }

            val confidence = inlierCount.toFloat() / matchList.size.toFloat()
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
        otherTextCrop: android.graphics.RectF? = null,
        skipExpensiveORB: Boolean = false,
        globalWordCounts: Map<String, Int> = emptyMap()
    ): Map<String, AlignmentResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, AlignmentResult>()
        
        var t0 = System.currentTimeMillis()
        val featureResult = if (skipExpensiveORB) AlignmentResult(false, null, 0f, "ORB Skipped") 
                           else alignImages(reference, query, 10, odometerCrop, otherTextCrop)
        results["feature"] = featureResult.copy(message = featureResult.message + " (${System.currentTimeMillis()-t0}ms)")
        
        t0 = System.currentTimeMillis()
        val argScore = argMatch(refOcr.textBlocks, queryOcr.textBlocks, globalWordCounts)
        results["arg"] = AlignmentResult(true, null, argScore, "ARG (${System.currentTimeMillis()-t0}ms)", method = "arg")
        
        t0 = System.currentTimeMillis()
        val histScore = histogramMatch(refOcr.textBlocks, queryOcr.textBlocks)
        results["histogram"] = AlignmentResult(true, null, histScore, "Hist (${System.currentTimeMillis()-t0}ms)", method = "histogram")
        
        t0 = System.currentTimeMillis()
        val embScore = embeddingMatch(refOcr.textBlocks, queryOcr.textBlocks, globalWordCounts)
        results["embedding"] = AlignmentResult(true, null, embScore, "Emb (${System.currentTimeMillis()-t0}ms)", method = "embedding")
        
        t0 = System.currentTimeMillis()
        val ancScore = anchorMatch(refOcr.textBlocks, queryOcr.textBlocks)
        results["anchor"] = AlignmentResult(true, null, ancScore, "Anchor (${System.currentTimeMillis()-t0}ms)", method = "anchor")
        
        // 3. CONSENSUS SCORING
        // ORB features and Embeddings are our most discriminative signals.
        // Anchors are useful but prone to accidental matches on speedo numbers.
        val featScoreNorm = if (featureResult.success) (featureResult.goodMatchesCount / 40f).coerceIn(0f, 1f) else 0f
        
        var consensusScore = (featScoreNorm * 0.35f) + 
                             (embScore * 0.35f) + 
                             (histScore * 0.10f) + 
                             (argScore * 0.10f) + 
                             (ancScore * 0.10f)

        if (ancScore < 0) {
            consensusScore = -1.0f
        }
                             
        results["consensus"] = AlignmentResult(true, null, consensusScore, "Consensus score: ${"%.2f".format(consensusScore)}", method = "consensus")
        
        results
    }
}
