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
    val method: String = "feature",
    val wordVeto: Boolean = false
)

object ImageAlignmentUtils {
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("ImageAlignment", "OpenCV initialization failed!")
        } else {
            Log.i("ImageAlignment", "OpenCV initialized successfully")
        }
    }

    private fun isBlockInCrop(block: TextBlock, crop: android.graphics.RectF?, imgW: Int, imgH: Int): Boolean {
        if (crop == null || imgW == 0 || imgH == 0) return false
        val bx = block.boundingBox.centerX().toFloat() / imgW.toFloat()
        val by = block.boundingBox.centerY().toFloat() / imgH.toFloat()
        return bx >= crop.left && bx <= crop.right && by >= crop.top && by <= crop.bottom
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

    private fun argMatch(
        refBlocks: List<TextBlock>, 
        queryBlocks: List<TextBlock>, 
        globalWordCounts: Map<String, Int> = emptyMap(),
        refOdoCrop: android.graphics.RectF? = null,
        refOtherCrop: android.graphics.RectF? = null,
        refW: Int = 0,
        refH: Int = 0
    ): Float {
        if (refBlocks.isEmpty() || queryBlocks.isEmpty()) return 0f
        
        // Filter out blocks in crop zones for reference
        val filteredRef = refBlocks.filter { !isBlockInCrop(it, refOdoCrop, refW, refH) && !isBlockInCrop(it, refOtherCrop, refW, refH) }
        if (filteredRef.isEmpty()) return 0f

        var score = 0f
        var totalWeight = 0f
        
        val refWords = filteredRef.map { it.text.lowercase().trim() }.toSet()
        
        for (r in filteredRef) {
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
        
        // Soft penalty for words found in query that are NOT in reference
        // but ARE known to exist in other vehicles.
        var penalty = 0f
        for (q in queryBlocks) {
            val word = q.text.lowercase().trim()
            if (!refWords.contains(word) && globalWordCounts.containsKey(word)) {
                val weight = 1.0f / globalWordCounts[word]!!.toFloat()
                penalty += weight * 0.5f 
            }
        }
        
        return if (totalWeight > 0) (score - penalty) / totalWeight else 0f
    }

    fun anchorMatch(
        refBlocks: List<TextBlock>, 
        queryBlocks: List<TextBlock>, 
        allOtherRefs: List<OcrResult> = emptyList(),
        refOdoCrop: android.graphics.RectF? = null,
        refOtherCrop: android.graphics.RectF? = null,
        refW: Int = 0,
        refH: Int = 0
    ): Float {
        if (refBlocks.isEmpty() || queryBlocks.isEmpty()) return 0f
        
        // 1. Define standard anchors
        val anchors = listOf("MPH", "KM/H", "160", "140", "120", "100", "80", "60", "40", "20", "PRNDL", "TRIP", "ODO")
        
        // 2. Filter blocks
        val filteredRef = refBlocks.filter { !isBlockInCrop(it, refOdoCrop, refW, refH) && !isBlockInCrop(it, refOtherCrop, refW, refH) }
        val refWords = filteredRef.map { it.text.lowercase().trim() }.toSet()
        
        var matchCount = 0
        var totalPossible = 0
        
        for (anchor in anchors) {
            val anchorLower = anchor.lowercase()
            val inRef = refWords.any { it.contains(anchorLower) }
            val inQuery = queryBlocks.any { it.text.lowercase().contains(anchorLower) }
            
            if (inQuery && !inRef) {
                // Check if any other vehicle actually HAS this anchor in its non-crop zones
                val knownByOthers = allOtherRefs.any { other -> 
                    val otherFiltered = other.textBlocks.filter { !isBlockInCrop(it, null, other.imageWidth, other.imageHeight) } // we don't have other's crops easily here
                    otherFiltered.any { it.text.lowercase().contains(anchorLower) }
                }
                
                if (knownByOthers) {
                    return -1.0f // HARD VETO
                }
            }
            
            if (inRef) {
                totalPossible++
                if (inQuery) matchCount++
            }
        }
        
        // 3. New: Dynamic Word Veto (Not in standard anchor list)
        // If a word is NOT in current ref, but is in another ref, and is NOT in a query crop zone...
        // For now, standard anchors are safer for hard vetoes. 
        // We'll stick to the anchor list for -1.0 and use ARG for soft penalties.

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
            // USE AFFINE PARTIAL 2D (4-DOF: Translation, Rotation, Scale)
            // This prevents "pinwheel/wedge" distortion by strictly forbidding perspective/tilt changes.
            val affine = Calib3d.estimateAffinePartial2D(srcPoints, dstPoints, mask, Calib3d.RANSAC, 3.0)
            
            var inlierCount = 0
            if (!affine.empty()) {
                for (i in 0 until mask.rows()) {
                    if (mask.get(i, 0)[0].toInt() == 1) {
                        inlierCount++
                    }
                }
            }
            mask.release()

            if (affine.empty() || inlierCount < minInliers) {
                if (!affine.empty()) affine.release()
                return@withContext AlignmentResult(false, null, 0f, "Affine alignment failed or too few inliers ($inlierCount < $minInliers)", refKpCount, queryKpCount, goodMatchesCount, "feature")
            }
            
            // AFFINE SANITY CHECK
            // Matrix structure: [ cos(th)*s, -sin(th)*s, tx ]
            //                   [ sin(th)*s,  cos(th)*s, ty ]
            val a00 = affine.get(0, 0)[0]
            val a01 = affine.get(0, 1)[0]
            val a10 = affine.get(1, 0)[0]
            val a11 = affine.get(1, 1)[0]
            
            // Determinant of the 2x2 part is the squared scale
            val det = a00 * a11 - a01 * a10
            // Scale should be roughly 1.0 (between 0.5x and 2.0x)
            val isSane = det > 0.25 && det < 4.0
            
            if (!isSane) {
                affine.release()
                return@withContext AlignmentResult(false, null, 0f, "Affine failed sanity check (scaleSq=${"%.2f".format(det)})", refKpCount, queryKpCount, inlierCount, "feature")
            }

            val confidence = inlierCount.toFloat() / matchList.size.toFloat()
            val warped = Mat()
            Imgproc.warpAffine(queryMat, warped, affine, Size(refMat.cols().toDouble(), refMat.rows().toDouble()))
            val alignedBitmap = Bitmap.createBitmap(warped.cols(), warped.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(warped, alignedBitmap)
            warped.release()
            affine.release()
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
        globalWordCounts: Map<String, Int> = emptyMap(),
        allOtherRefs: List<OcrResult> = emptyList()
    ): Map<String, AlignmentResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, AlignmentResult>()
        
        var t0 = System.currentTimeMillis()
        val featureResult = if (skipExpensiveORB) AlignmentResult(false, null, 0f, "ORB Skipped") 
                           else alignImages(reference, query, 10, odometerCrop, otherTextCrop)
        results["feature"] = featureResult.copy(message = featureResult.message + " (${System.currentTimeMillis()-t0}ms)")
        
        t0 = System.currentTimeMillis()
        val argScore = argMatch(refOcr.textBlocks, queryOcr.textBlocks, globalWordCounts, odometerCrop, otherTextCrop, refOcr.imageWidth, refOcr.imageHeight)
        results["arg"] = AlignmentResult(true, null, argScore, "ARG (${System.currentTimeMillis()-t0}ms)", method = "arg")
        
        t0 = System.currentTimeMillis()
        val histScore = histogramMatch(refOcr.textBlocks, queryOcr.textBlocks)
        results["histogram"] = AlignmentResult(true, null, histScore, "Hist (${System.currentTimeMillis()-t0}ms)", method = "histogram")
        
        t0 = System.currentTimeMillis()
        val embScore = embeddingMatch(refOcr.textBlocks, queryOcr.textBlocks, globalWordCounts)
        results["embedding"] = AlignmentResult(true, null, embScore, "Emb (${System.currentTimeMillis()-t0}ms)", method = "embedding")
        
        t0 = System.currentTimeMillis()
        val ancScore = anchorMatch(refOcr.textBlocks, queryOcr.textBlocks, allOtherRefs, odometerCrop, otherTextCrop, refOcr.imageWidth, refOcr.imageHeight)
        results["anchor"] = AlignmentResult(true, null, ancScore, "Anchor (${System.currentTimeMillis()-t0}ms)", method = "anchor")
        
        // 3. CONSENSUS SCORING
        val featScoreNorm = if (featureResult.success) (featureResult.goodMatchesCount / 40f).coerceIn(0f, 1f) else 0f
        
        // Word Veto: any distinctive word found in query that belongs to another vehicle but NOT this one
        var hasWordVeto = false
        val refWords = refOcr.textBlocks.filter { !isBlockInCrop(it, odometerCrop, refOcr.imageWidth, refOcr.imageHeight) && !isBlockInCrop(it, otherTextCrop, refOcr.imageWidth, refOcr.imageHeight) }.map { it.text.lowercase().trim() }.toSet()
        
        for (q in queryOcr.textBlocks) {
            val word = q.text.lowercase().trim()
            if (word.length < 3) continue
            if (!refWords.contains(word)) {
                // If it's a distinctive word for another vehicle, hard veto
                val belongsToOther = allOtherRefs.any { other -> 
                    // check if distinctive for 'other'
                    val otherWords = other.textBlocks.map { it.text.lowercase().trim() }.toSet()
                    otherWords.contains(word)
                }
                if (belongsToOther) {
                    hasWordVeto = true
                    break
                }
            }
        }

        var consensusScore = (featScoreNorm * 0.35f) + 
                             (embScore * 0.35f) + 
                             (histScore * 0.10f) + 
                             (argScore * 0.10f) + 
                             (ancScore * 0.10f)

        var finalMessage = "Consensus score: ${"%.2f".format(consensusScore)}"
        if (ancScore < 0) {
            consensusScore = -1.0f
            finalMessage = "VETO (Anchor mismatch)"
        } else if (hasWordVeto) {
            consensusScore = -1.0f
            finalMessage = "VETO (Distinctive word mismatch)"
        }
                             
        results["consensus"] = AlignmentResult(true, null, consensusScore, finalMessage, method = "consensus", wordVeto = hasWordVeto)
        
        results
    }
}
