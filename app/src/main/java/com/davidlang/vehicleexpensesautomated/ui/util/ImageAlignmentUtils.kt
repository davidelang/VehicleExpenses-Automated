package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import org.opencv.video.Video
import org.opencv.calib3d.Calib3d
import kotlin.math.abs
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
    val wordVeto: Boolean = false,
    val vetoReason: String = "",
    val timeMs: Long = 0,
    val tierReached: Int = 0
)

object ImageAlignmentUtils {
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("ImageAlignment", "OpenCV initialization failed!")
        }
    }

    suspend fun alignImages(
        reference: Bitmap,
        query: Bitmap,
        minInliers: Int = 10,
        odometerCrop: android.graphics.RectF? = null,
        otherTextCrop: android.graphics.RectF? = null
    ): AlignmentResult = withContext(Dispatchers.IO) {
        val refMat = Mat()
        val queryMat = Mat()
        
        org.opencv.android.Utils.bitmapToMat(reference, refMat)
        org.opencv.android.Utils.bitmapToMat(query, queryMat)
        
        val orb = ORB.create(1000) 
        val kp1 = MatOfKeyPoint()
        val kp2 = MatOfKeyPoint()
        val desc1 = Mat()
        val desc2 = Mat()

        orb.detectAndCompute(refMat, Mat(), kp1, desc1)
        orb.detectAndCompute(queryMat, Mat(), kp2, desc2)

        if (desc1.empty() || desc2.empty()) {
            refMat.release(); queryMat.release()
            return@withContext AlignmentResult(false, null, 0f, "Descriptors empty")
        }

        val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
        val matches = mutableListOf<MatOfDMatch>()
        matcher.knnMatch(desc1, desc2, matches, 2)

        val goodMatches = mutableListOf<DMatch>()
        for (match in matches) {
            val m = match.toArray()
            if (m.size >= 2 && m[0].distance < 0.75 * m[1].distance) {
                goodMatches.add(m[0])
            }
        }

        if (goodMatches.size < minInliers) {
            refMat.release(); queryMat.release()
            return@withContext AlignmentResult(false, null, 0f, "Too few matches (${goodMatches.size})")
        }

        val srcPoints = MatOfPoint2f()
        val dstPoints = MatOfPoint2f()
        val srcList = mutableListOf<org.opencv.core.Point>()
        val dstList = mutableListOf<org.opencv.core.Point>()

        val kp1Array = kp1.toArray()
        val kp2Array = kp2.toArray()

        for (match in goodMatches) {
            srcList.add(kp1Array[match.queryIdx].pt)
            dstList.add(kp2Array[match.trainIdx].pt)
        }
        srcPoints.fromList(srcList)
        dstPoints.fromList(dstList)

        val inliers = Mat()
        val affineMatrix = Calib3d.estimateAffinePartial2D(dstPoints, srcPoints, inliers)

        if (affineMatrix.empty()) {
            refMat.release(); queryMat.release()
            return@withContext AlignmentResult(false, null, 0f, "Affine matrix empty")
        }

        val a = affineMatrix.get(0, 0)?.get(0) ?: 0.0
        val b = affineMatrix.get(0, 1)?.get(0) ?: 0.0
        val det = a * a + b * b
        val isSane = det > 0.0001 && det < 1000.0

        if (!isSane) {
            refMat.release(); queryMat.release()
            return@withContext AlignmentResult(false, null, 0f, "Alignment abandoned: Scale determinant ($det) insane")
        }

        val alignedMat = Mat()
        Imgproc.warpAffine(queryMat, alignedMat, affineMatrix, refMat.size())

        val alignedBitmap = Bitmap.createBitmap(alignedMat.cols(), alignedMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(alignedMat, alignedBitmap)

        refMat.release(); queryMat.release(); alignedMat.release()

        val confidence = goodMatches.size / 100f
        AlignmentResult(true, alignedBitmap, confidence, "ORB Affine Success", kp1Array.size, kp2Array.size, goodMatches.size)
    }

    suspend fun hubAlign(reference: Bitmap, query: Bitmap): AlignmentResult = withContext(Dispatchers.IO) {
        val refMat = Mat()
        val queryMat = Mat()
        org.opencv.android.Utils.bitmapToMat(reference, refMat)
        org.opencv.android.Utils.bitmapToMat(query, queryMat)

        val grayRef = Mat()
        val grayQuery = Mat()
        Imgproc.cvtColor(refMat, grayRef, Imgproc.COLOR_RGB2GRAY)
        Imgproc.cvtColor(queryMat, grayQuery, Imgproc.COLOR_RGB2GRAY)

        Imgproc.GaussianBlur(grayRef, grayRef, Size(9.0, 9.0), 2.0)
        Imgproc.GaussianBlur(grayQuery, grayQuery, Size(9.0, 9.0), 2.0)

        val circlesRef = Mat()
        Imgproc.HoughCircles(grayRef, circlesRef, Imgproc.HOUGH_GRADIENT, 1.0, 100.0, 100.0, 30.0, 100, 1000)

        val circlesQuery = Mat()
        Imgproc.HoughCircles(grayQuery, circlesQuery, Imgproc.HOUGH_GRADIENT, 1.0, 100.0, 100.0, 30.0, 100, 1000)

        if (circlesRef.cols() > 0 && circlesQuery.cols() > 0) {
            val cRef = circlesRef.get(0, 0)
            val cQue = circlesQuery.get(0, 0)

            val tx = cRef[0] - cQue[0]
            val ty = cRef[1] - cQue[1]
            val scale = cRef[2] / cQue[2]

            val matrix = Mat.zeros(2, 3, CvType.CV_64F)
            matrix.put(0, 0, scale, 0.0, tx)
            matrix.put(1, 0, 0.0, scale, ty)

            val alignedMat = Mat()
            Imgproc.warpAffine(queryMat, alignedMat, matrix, refMat.size())

            val alignedBitmap = Bitmap.createBitmap(alignedMat.cols(), alignedMat.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(alignedMat, alignedBitmap)

            refMat.release(); queryMat.release(); grayRef.release(); grayQuery.release(); alignedMat.release()
            return@withContext AlignmentResult(true, alignedBitmap, 0.8f, "Hub aligned (${cRef[0].toInt()},${cRef[1].toInt()})", method = "hub")
        }

        refMat.release(); queryMat.release(); grayRef.release(); grayQuery.release()
        AlignmentResult(false, null, 0f, "Hub Alignment Abandoned", method = "hub")
    }

    fun histogramMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>): Float {
        val refHist = getDensityProfile(refBlocks)
        val queryHist = getDensityProfile(queryBlocks)
        var dot = 0f
        var normRef = 0f
        var normQuery = 0f
        for (i in 0 until 100) {
            dot += refHist[i] * queryHist[i]
            normRef += refHist[i] * refHist[i]
            normQuery += queryHist[i] * queryHist[i]
        }
        val denom = sqrt(normRef * normQuery)
        return if (denom > 0) dot / denom else 0f
    }

    private fun getDensityProfile(blocks: List<TextBlock>): FloatArray {
        val profile = FloatArray(100)
        blocks.forEach { 
            val idx = (it.boundingBox.centerY() / 3000f * 100).toInt().coerceIn(0, 99)
            profile[idx] += 1f
        }
        return profile
    }

    fun embeddingMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>, globalWordCounts: Map<String, Int>): Float {
        val refWords = refBlocks.map { it.text.lowercase() }.toSet()
        val queryWords = queryBlocks.map { it.text.lowercase() }.toSet()
        val intersect = refWords.intersect(queryWords)
        if (refWords.isEmpty()) return 0f
        return intersect.size.toFloat() / refWords.size.toFloat()
    }

    fun argMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>, globalWordCounts: Map<String, Int>, crop: android.graphics.RectF?, otherCrop: android.graphics.RectF?, w: Int, h: Int): Float {
        val refSet = refBlocks.filter { !isBlockInCrop(it, crop, w, h) && !isBlockInCrop(it, otherCrop, w, h) }.map { it.text.lowercase() }.toSet()
        val querySet = queryBlocks.map { it.text.lowercase() }.toSet()
        if (refSet.isEmpty()) return 0f
        val matches = refSet.intersect(querySet)
        return matches.size.toFloat() / refSet.size.toFloat()
    }

    fun anchorMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>, allOtherRefs: List<OcrResult>, crop: android.graphics.RectF?, otherCrop: android.graphics.RectF?, w: Int, h: Int, dynamicAnchors: Map<String, String>, currentVehicleName: String): Float {
        for (q in queryBlocks) {
            val word = q.text.lowercase().trim()
            if (word.length < 3) continue
            val belongsTo = dynamicAnchors[word]
            if (belongsTo != null && belongsTo != currentVehicleName) return -1.0f 
        }
        val myAnchors = dynamicAnchors.filter { it.value == currentVehicleName }.keys
        if (myAnchors.isEmpty()) return 0.5f 
        val found = queryBlocks.map { it.text.lowercase().trim() }.intersect(myAnchors)
        return found.size.toFloat() / myAnchors.size.toFloat()
    }

    fun projectCropViaAnchor(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>, crop: android.graphics.RectF, refW: Int, refH: Int, queryW: Int, queryH: Int): android.graphics.RectF? {
        val refAnchors = refBlocks.filter { it.text.length >= 3 }
        val queryAnchors = queryBlocks.filter { it.text.length >= 3 }
        for (ra in refAnchors) {
            val match = queryAnchors.find { it.text == ra.text }
            if (match != null) {
                val dx = (match.boundingBox.centerX().toFloat() / queryW) - (ra.boundingBox.centerX().toFloat() / refW)
                val dy = (match.boundingBox.centerY().toFloat() / queryH) - (ra.boundingBox.centerY().toFloat() / refH)
                return android.graphics.RectF(crop.left + dx, crop.top + dy, crop.right + dx, crop.bottom + dy)
            }
        }
        return null
    }

    private fun isBlockInCrop(block: TextBlock, crop: android.graphics.RectF?, w: Int, h: Int): Boolean {
        if (crop == null) return false
        val bx = block.boundingBox.centerX().toFloat() / w
        val by = block.boundingBox.centerY().toFloat() / h
        return crop.contains(bx, by)
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
        allOtherRefs: List<OcrResult> = emptyList(),
        dynamicAnchors: Map<String, String> = emptyMap(),
        currentVehicleName: String = "",
        onLog: (suspend (String) -> Unit)? = null
    ): Map<String, AlignmentResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, AlignmentResult>()
        
        onLog?.invoke("Aligning: ORB Feature...")
        var t0 = System.currentTimeMillis()
        val featureResult = if (skipExpensiveORB) AlignmentResult(false, null, 0f, "ORB Skipped", method = "feature", timeMs = 0) 
                           else alignImages(reference, query, 10, odometerCrop, otherTextCrop)
        val tOrb = System.currentTimeMillis() - t0
        results["feature"] = featureResult.copy(timeMs = tOrb)
        
        onLog?.invoke("Aligning: Hub Mechanical...")
        t0 = System.currentTimeMillis()
        val hubResult = hubAlign(reference, query)
        val tHub = System.currentTimeMillis() - t0
        results["hub"] = hubResult.copy(timeMs = tHub)
        
        onLog?.invoke("Matching: ARG Engine...")
        t0 = System.currentTimeMillis()
        val argScore = argMatch(refOcr.textBlocks, queryOcr.textBlocks, globalWordCounts, odometerCrop, otherTextCrop, refOcr.imageWidth, refOcr.imageHeight)
        val tArg = System.currentTimeMillis() - t0
        results["arg"] = AlignmentResult(true, null, argScore, "ARG", method = "arg", timeMs = tArg)
        
        onLog?.invoke("Matching: Histogram Engine...")
        t0 = System.currentTimeMillis()
        val histScore = histogramMatch(refOcr.textBlocks, queryOcr.textBlocks)
        val tHist = System.currentTimeMillis() - t0
        results["histogram"] = AlignmentResult(true, null, histScore, "Hist", method = "histogram", timeMs = tHist)
        
        onLog?.invoke("Matching: Embedding Engine...")
        t0 = System.currentTimeMillis()
        val embScore = embeddingMatch(refOcr.textBlocks, queryOcr.textBlocks, globalWordCounts)
        val tEmb = System.currentTimeMillis() - t0
        results["embedding"] = AlignmentResult(true, null, embScore, "Emb", method = "embedding", timeMs = tEmb)
        
        onLog?.invoke("Matching: Dynamic Anchors...")
        t0 = System.currentTimeMillis()
        val ancScore = anchorMatch(refOcr.textBlocks, queryOcr.textBlocks, allOtherRefs, odometerCrop, otherTextCrop, refOcr.imageWidth, refOcr.imageHeight, dynamicAnchors, currentVehicleName)
        val tAnc = System.currentTimeMillis() - t0
        results["anchor"] = AlignmentResult(true, null, ancScore, "Anchor", method = "anchor", timeMs = tAnc)
        
        val tCons0 = System.currentTimeMillis()
        val featScoreNorm = if (featureResult.success) (featureResult.goodMatchesCount / 40f).coerceIn(0f, 1f) else 0f
        
        var hasWordVeto = false
        var vetoWord = ""
        val refWords = refOcr.textBlocks.filter { !isBlockInCrop(it, odometerCrop, refOcr.imageWidth, refOcr.imageHeight) && !isBlockInCrop(it, otherTextCrop, refOcr.imageWidth, refOcr.imageHeight) }.map { it.text.lowercase().trim() }.toSet()
        
        for (q in queryOcr.textBlocks) {
            val word = q.text.lowercase().trim()
            if (word.length < 3) continue
            if (!refWords.contains(word)) {
                val belongsToOther = allOtherRefs.any { other -> 
                    other.textBlocks.any { it.text.lowercase().trim() == word }
                }
                if (belongsToOther) {
                    hasWordVeto = true
                    vetoWord = word
                    break
                }
            }
        }

        var consensusScore = (featScoreNorm * 0.05f) + (embScore * 0.40f) + (histScore * 0.40f) + (argScore * 0.10f) + (ancScore * 0.05f)
        var finalMessage = "Consensus score: ${"%.2f".format(consensusScore)} | Landmarks: [${dynamicAnchors.filter { it.value == currentVehicleName }.keys.take(5).joinToString(",")}]"

        if (ancScore < 0) {
            consensusScore = -1.0f
            finalMessage = "VETO (Anchor mismatch)"
        } else if (hasWordVeto) {
            consensusScore = -1.0f
            finalMessage = "VETO (Word: '$vetoWord')"
        }
        
        val tCons = System.currentTimeMillis() - tCons0
        results["consensus"] = AlignmentResult(true, null, consensusScore, finalMessage, method = "consensus", wordVeto = hasWordVeto, vetoReason = vetoWord, timeMs = tCons)
        
        val tTier0 = System.currentTimeMillis()
        val tieredResult = calculateTieredMatch(results, hasWordVeto, vetoWord)
        results["tiered"] = tieredResult.copy(timeMs = System.currentTimeMillis() - tTier0)
        
        results
    }

    private fun calculateTieredMatch(results: Map<String, AlignmentResult>, hasVeto: Boolean, vetoWord: String): AlignmentResult {
        if (hasVeto) return AlignmentResult(true, null, -1f, "TIER 0: VETO (Word: '$vetoWord')", method = "tiered", wordVeto = true, tierReached = 0)
        val hist = results["histogram"]?.confidence ?: 0f
        val emb = results["embedding"]?.confidence ?: 0f
        val arg = results["arg"]?.confidence ?: 0f
        val feat = results["feature"]?.confidence ?: 0f

        if (hist > 0.85f) return AlignmentResult(true, null, hist, "TIER 1: Histogram High-Conf", method = "tiered", tierReached = 1)
        if ((hist > 0.5f && emb > 0.5f) || (hist > 0.5f && arg > 0.5f)) return AlignmentResult(true, null, hist.coerceAtLeast(emb), "TIER 2: Text Agreement", method = "tiered", tierReached = 2)
        if (feat > 0.3f) return AlignmentResult(true, null, feat, "TIER 3: Spatial Feature Match", method = "tiered", tierReached = 3)

        return AlignmentResult(false, null, 0f, "TIER 4: Inconclusive - Ask User", method = "tiered", tierReached = 4)
    }
}
