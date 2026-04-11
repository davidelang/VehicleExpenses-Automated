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
import org.json.JSONArray
import org.json.JSONObject
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle

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

data class VetoResult(
    val isVetoed: Boolean,
    val reasonWord: String = "",
    val tierReached: Int = 0
)

object ImageAlignmentUtils {
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("ImageAlignment", "OpenCV initialization failed!")
        }
    }

    fun getLandmarksFromJson(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        val result = mutableSetOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(obj.getString("text").lowercase().trim())
            }
        } catch (e: Exception) { Log.e("ImageAlignment", "JSON parse failed", e) }
        return result
    }

    /**
     * Tier 1: Veto Elimination
     * For each vehicle, check if any word in the query belongs to OTHER vehicles
     * but NOT to this vehicle.
     */
    fun performTier1Veto(queryLandmarks: List<TextBlock>, allVehicles: List<Vehicle>): Map<Int, VetoResult> {
        val queryWords = queryLandmarks.map { it.text.lowercase().trim() }.toSet()
        val vehicleLandmarks = allVehicles.associate { it.id to getLandmarksFromJson(it.landmarkTextBlocksJson) }
        
        return allVehicles.associate { currentVehicle ->
            val myWords = vehicleLandmarks[currentVehicle.id] ?: emptySet()
            
            // Pool = all words from everyone else
            val otherWordsPool = vehicleLandmarks.filter { it.key != currentVehicle.id }
                .values.flatten().toSet()
            
            // Veto Pool = Words others have that I don't
            val vetoPool = otherWordsPool - myWords
            
            // DYNAMIC FIX: Identify ALL triggers, sort them, and remove duplicates
            val triggers = queryWords.intersect(vetoPool).sorted()
            
            currentVehicle.id to VetoResult(
                isVetoed = triggers.isNotEmpty(),
                reasonWord = if (triggers.isNotEmpty()) triggers.joinToString(", ") else "",
                tierReached = 0
            )
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
        val kp1 = MatOfKeyPoint(); val kp2 = MatOfKeyPoint(); val desc1 = Mat(); val desc2 = Mat()
        orb.detectAndCompute(refMat, Mat(), kp1, desc1)
        orb.detectAndCompute(queryMat, Mat(), kp2, desc2)
        if (desc1.empty() || desc2.empty()) { refMat.release(); queryMat.release(); return@withContext AlignmentResult(false, null, 0f, "Descriptors empty") }
        val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
        val matches = mutableListOf<MatOfDMatch>(); matcher.knnMatch(desc1, desc2, matches, 2)
        val goodMatches = mutableListOf<DMatch>()
        for (match in matches) { val m = match.toArray(); if (m.size >= 2 && m[0].distance < 0.75 * m[1].distance) goodMatches.add(m[0]) }
        if (goodMatches.size < minInliers) { refMat.release(); queryMat.release(); return@withContext AlignmentResult(false, null, 0f, "Too few matches (${goodMatches.size})") }
        val srcPoints = MatOfPoint2f(); val dstPoints = MatOfPoint2f(); val srcList = mutableListOf<org.opencv.core.Point>(); val dstList = mutableListOf<org.opencv.core.Point>()
        val kp1Array = kp1.toArray(); val kp2Array = kp2.toArray()
        for (match in goodMatches) { srcList.add(kp1Array[match.queryIdx].pt); dstList.add(kp2Array[match.trainIdx].pt) }
        srcPoints.fromList(srcList); dstPoints.fromList(dstList)
        val inliers = Mat(); val affineMatrix = Calib3d.estimateAffinePartial2D(dstPoints, srcPoints, inliers)
        if (affineMatrix.empty()) { refMat.release(); queryMat.release(); return@withContext AlignmentResult(false, null, 0f, "Affine matrix empty") }
        val a = affineMatrix.get(0, 0)?.get(0) ?: 0.0; val b = affineMatrix.get(0, 1)?.get(0) ?: 0.0; val det = a * a + b * b
        if (det < 0.0001 || det > 1000.0) { refMat.release(); queryMat.release(); return@withContext AlignmentResult(false, null, 0f, "Alignment abandoned: Scale determinant ($det) insane") }
        val alignedMat = Mat(); Imgproc.warpAffine(queryMat, alignedMat, affineMatrix, refMat.size())
        val alignedBitmap = Bitmap.createBitmap(alignedMat.cols(), alignedMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(alignedMat, alignedBitmap)
        refMat.release(); queryMat.release(); alignedMat.release()
        AlignmentResult(true, alignedBitmap, goodMatches.size / 100f, "ORB Affine Success", kp1Array.size, kp2Array.size, goodMatches.size)
    }

    suspend fun hubAlign(reference: Bitmap, query: Bitmap): AlignmentResult = withContext(Dispatchers.IO) {
        val refMat = Mat(); val queryMat = Mat()
        org.opencv.android.Utils.bitmapToMat(reference, refMat); org.opencv.android.Utils.bitmapToMat(query, queryMat)
        val grayRef = Mat(); val grayQuery = Mat()
        Imgproc.cvtColor(refMat, grayRef, Imgproc.COLOR_RGB2GRAY); Imgproc.cvtColor(queryMat, grayQuery, Imgproc.COLOR_RGB2GRAY)
        Imgproc.GaussianBlur(grayRef, grayRef, Size(9.0, 9.0), 2.0); Imgproc.GaussianBlur(grayQuery, grayQuery, Size(9.0, 9.0), 2.0)
        val circlesRef = Mat(); Imgproc.HoughCircles(grayRef, circlesRef, Imgproc.HOUGH_GRADIENT, 1.0, 100.0, 100.0, 30.0, 100, 1000)
        val circlesQuery = Mat(); Imgproc.HoughCircles(grayQuery, circlesQuery, Imgproc.HOUGH_GRADIENT, 1.0, 100.0, 100.0, 30.0, 100, 1000)
        if (circlesRef.cols() > 0 && circlesQuery.cols() > 0) {
            val cRef = circlesRef.get(0, 0); val cQue = circlesQuery.get(0, 0)
            val tx = cRef[0] - cQue[0]; val ty = cRef[1] - cQue[1]; val scale = cRef[2] / cQue[2]
            val matrix = Mat.zeros(2, 3, CvType.CV_64F); matrix.put(0, 0, scale, 0.0, tx); matrix.put(1, 0, 0.0, scale, ty)
            val alignedMat = Mat(); Imgproc.warpAffine(queryMat, alignedMat, matrix, refMat.size())
            val alignedBitmap = Bitmap.createBitmap(alignedMat.cols(), alignedMat.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(alignedMat, alignedBitmap)
            refMat.release(); queryMat.release(); grayRef.release(); grayQuery.release(); alignedMat.release()
            return@withContext AlignmentResult(true, alignedBitmap, 0.8f, "Hub aligned", method = "hub")
        }
        refMat.release(); queryMat.release(); grayRef.release(); grayQuery.release()
        AlignmentResult(false, null, 0f, "Hub Alignment Abandoned", method = "hub")
    }

    fun histogramMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>): Float {
        val refHist = getDensityProfile(refBlocks); val queryHist = getDensityProfile(queryBlocks)
        var dot = 0f; var normRef = 0f; var normQuery = 0f
        for (i in 0 until 100) { dot += refHist[i] * queryHist[i]; normRef += refHist[i] * refHist[i]; normQuery += queryHist[i] * queryHist[i] }
        val denom = sqrt(normRef * normQuery)
        return if (denom > 0) dot / denom else 0f
    }

    private fun getDensityProfile(blocks: List<TextBlock>): FloatArray {
        val profile = FloatArray(100)
        blocks.forEach { val idx = (it.boundingBox.centerY() / 3000f * 100).toInt().coerceIn(0, 99); profile[idx] += 1f }
        return profile
    }

    fun embeddingMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>): Float {
        val refWords = refBlocks.map { it.text.lowercase() }.toSet()
        val queryWords = queryBlocks.map { it.text.lowercase() }.toSet()
        val intersect = refWords.intersect(queryWords)
        if (refWords.isEmpty()) return 0f
        return intersect.size.toFloat() / refWords.size.toFloat()
    }

    fun argMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>, crop: android.graphics.RectF?, otherCrop: android.graphics.RectF?, w: Int, h: Int): Float {
        val refSet = refBlocks.filter { !isBlockInCrop(it, crop, w, h) && !isBlockInCrop(it, otherCrop, w, h) }.map { it.text.lowercase() }.toSet()
        val querySet = queryBlocks.map { it.text.lowercase() }.toSet()
        if (refSet.isEmpty()) return 0f
        return refSet.intersect(querySet).size.toFloat() / refSet.size.toFloat()
    }

    fun anchorMatch(refBlocks: List<TextBlock>, queryBlocks: List<TextBlock>, currentVehicleName: String, dynamicAnchors: Map<String, String>): Float {
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
        val bx = block.boundingBox.centerX().toFloat() / w; val by = block.boundingBox.centerY().toFloat() / h
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
        veto: VetoResult = VetoResult(false),
        onLog: (suspend (String) -> Unit)? = null
    ): Map<String, AlignmentResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, AlignmentResult>()
        onLog?.invoke("Aligning: ORB Feature pass...")
        var t0 = System.currentTimeMillis()
        val featureResult = if (skipExpensiveORB) AlignmentResult(false, null, 0f, "ORB Skipped", method = "feature") else alignImages(reference, query, 10, odometerCrop, otherTextCrop)
        results["feature"] = featureResult.copy(timeMs = System.currentTimeMillis() - t0)
        
        onLog?.invoke("Aligning: Hub pass...")
        t0 = System.currentTimeMillis()
        val hubResult = hubAlign(reference, query)
        results["hub"] = hubResult.copy(timeMs = System.currentTimeMillis() - t0)
        
        onLog?.invoke("Matching: ARG pass...")
        t0 = System.currentTimeMillis()
        val argRes = argMatch(refOcr.textBlocks, queryOcr.textBlocks, odometerCrop, otherTextCrop, refOcr.imageWidth, refOcr.imageHeight)
        results["arg"] = AlignmentResult(true, null, argRes, "ARG", method = "arg", timeMs = System.currentTimeMillis() - t0)
        
        onLog?.invoke("Matching: Histogram pass...")
        t0 = System.currentTimeMillis()
        val histRes = histogramMatch(refOcr.textBlocks, queryOcr.textBlocks)
        results["histogram"] = AlignmentResult(true, null, histRes, "Hist", method = "histogram", timeMs = System.currentTimeMillis() - t0)
        
        onLog?.invoke("Matching: Embedding pass...")
        t0 = System.currentTimeMillis()
        val embRes = embeddingMatch(refOcr.textBlocks, queryOcr.textBlocks)
        results["embedding"] = AlignmentResult(true, null, embRes, "Emb", method = "embedding", timeMs = System.currentTimeMillis() - t0)
        
        onLog?.invoke("Matching: Anchor pass...")
        t0 = System.currentTimeMillis()
        val ancRes = anchorMatch(refOcr.textBlocks, queryOcr.textBlocks, currentVehicleName, dynamicAnchors)
        results["anchor"] = AlignmentResult(true, null, ancRes, "Anchor", method = "anchor", timeMs = System.currentTimeMillis() - t0)
        
        val tCons0 = System.currentTimeMillis()
        val featScoreNorm = if (featureResult.success) (featureResult.goodMatchesCount / 40f).coerceIn(0f, 1f) else 0f
        val consensusScore = (featScoreNorm * 0.05f) + (results["embedding"]!!.confidence * 0.40f) + (results["histogram"]!!.confidence * 0.40f) + (results["arg"]!!.confidence * 0.10f) + (results["anchor"]!!.confidence * 0.05f)
        results["consensus"] = AlignmentResult(true, null, if (veto.isVetoed) -1f else consensusScore, if (veto.isVetoed) "VETO: ${veto.reasonWord}" else "OK", method = "consensus", wordVeto = veto.isVetoed, vetoReason = veto.reasonWord, timeMs = System.currentTimeMillis() - tCons0)
        
        val tTier0 = System.currentTimeMillis()
        val tieredResult = calculateTieredMatch(results, veto)
        results["tiered"] = tieredResult.copy(timeMs = System.currentTimeMillis() - tTier0)
        results
    }

    private fun calculateTieredMatch(results: Map<String, AlignmentResult>, veto: VetoResult): AlignmentResult {
        if (veto.isVetoed) return AlignmentResult(true, null, -1f, "TIER 0: VETO (Word: '${veto.reasonWord}')", method = "tiered", wordVeto = true, tierReached = 0, vetoReason = veto.reasonWord)
        val hist = results["histogram"]?.confidence ?: 0f; val emb = results["embedding"]?.confidence ?: 0f; val arg = results["arg"]?.confidence ?: 0f; val feat = results["feature"]?.confidence ?: 0f
        if (hist > 0.85f) return AlignmentResult(true, null, hist, "TIER 1: Histogram High-Conf", method = "tiered", tierReached = 1)
        if ((hist > 0.5f && emb > 0.5f) || (hist > 0.5f && arg > 0.5f)) return AlignmentResult(true, null, hist.coerceAtLeast(emb), "TIER 2: Text Agreement", method = "tiered", tierReached = 2)
        if (feat > 0.3f) return AlignmentResult(true, null, feat, "TIER 3: Spatial Feature Match", method = "tiered", tierReached = 3)
        return AlignmentResult(false, null, 0f, "TIER 4: Inconclusive", method = "tiered", tierReached = 4)
    }
}
