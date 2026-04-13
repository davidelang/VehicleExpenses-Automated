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
    val tierReached: Int = 0,
    val queryWords: List<String> = emptyList(),
    val myManifest: List<String> = emptyList(),
    val vetoPool: List<String> = emptyList()
)

data class AnchorResult(
    val success: Boolean,
    val alignedImage: Bitmap? = null,
    val timeMs: Long = 0,
    val strategy: String = "",
    val anchorsUsed: List<String> = emptyList(),
    val message: String = ""
)

object ImageAlignmentUtils {
    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("ImageAlignment", "OpenCV initialization failed!")
        }
    }

    fun anchorAlign(
        refBmp: Bitmap,
        queryBmp: Bitmap,
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>
    ): AnchorResult {
        val t0 = System.currentTimeMillis()
        
        // 1. STRATEGY A: Uniqueness
        // Find strings that appear exactly once in both lists
        val refCounts = refLandmarks.groupBy { it.text }.mapValues { it.value.size }
        val queCounts = queryLandmarks.groupBy { it.text }.mapValues { it.value.size }
        
        val uniqueMatches = refLandmarks.filter { refCounts[it.text] == 1 }
            .mapNotNull { refMark ->
                val queMark = queryLandmarks.find { it.text == refMark.text && queCounts[it.text] == 1 }
                if (queMark != null) refMark to queMark else null
            }

        var strategyUsed = ""
        var bestPair: Pair<Pair<TextBlock, TextBlock>, Pair<TextBlock, TextBlock>>? = null
        var anchorWords = emptyList<String>()

        if (uniqueMatches.size >= 2) {
            strategyUsed = "A (Uniqueness)"
            // Find pair with max distance in reference
            var maxDist = -1.0
            for (i in uniqueMatches.indices) {
                for (j in i + 1 until uniqueMatches.size) {
                    val d = dist(uniqueMatches[i].first, uniqueMatches[j].first)
                    if (d > maxDist) {
                        maxDist = d
                        bestPair = uniqueMatches[i] to uniqueMatches[j]
                    }
                }
            }
        } 
        
        // 2. STRATEGY B: Triangle Similarity (Fallback)
        if (bestPair == null) {
            val commonWords = refCounts.keys.intersect(queCounts.keys).toList()
            if (commonWords.size >= 3) {
                var maxTriDist = -1.0
                // This is O(N^3), but usually small sets
                for (i in commonWords.indices) {
                    for (j in i + 1 until commonWords.size) {
                        for (k in j + 1 until commonWords.size) {
                            val w1 = commonWords[i]; val w2 = commonWords[j]; val w3 = commonWords[k]
                            
                            // Get ALL instances (might be multiples)
                            val r1s = refLandmarks.filter { it.text == w1 }; val r2s = refLandmarks.filter { it.text == w2 }; val r3s = refLandmarks.filter { it.text == w3 }
                            val q1s = queryLandmarks.filter { it.text == w1 }; val q2s = queryLandmarks.filter { it.text == w2 }; val q3s = queryLandmarks.filter { it.text == w3 }
                            
                            for (r1 in r1s) for (r2 in r2s) for (r3 in r3s) {
                                val rD12 = dist(r1, r2); val rD23 = dist(r2, r3); val rD31 = dist(r3, r1)
                                if (rD12 == 0.0 || rD23 == 0.0 || rD31 == 0.0) continue
                                
                                for (q1 in q1s) for (q2 in q2s) for (q3 in q3s) {
                                    val qD12 = dist(q1, q2); val qD23 = dist(q2, q3); val qD31 = dist(q3, q1)
                                    if (qD12 == 0.0 || qD23 == 0.0 || qD31 == 0.0) continue
                                    
                                    // Check ratios (Similar Triangles) within 5% tolerance
                                    val ratio1 = (qD12 / rD12) / (qD23 / rD23)
                                    val ratio2 = (qD23 / rD23) / (qD31 / rD31)
                                    
                                    if (abs(ratio1 - 1.0) < 0.05 && abs(ratio2 - 1.0) < 0.05) {
                                        strategyUsed = "B (Triangle)"
                                        // Pick the longest side of this triangle as our anchors
                                        val sides = listOf((r1 to q1) to (r2 to q2), (r2 to q2) to (r3 to q3), (r3 to q3) to (r1 to q1))
                                        val longest = sides.maxBy { dist(it.first.first, it.second.first) }
                                        if (dist(longest.first.first, longest.second.first) > maxTriDist) {
                                            maxTriDist = dist(longest.first.first, longest.second.first)
                                            bestPair = longest
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (bestPair == null) {
            return AnchorResult(false, message = "No valid anchor sets found.", timeMs = System.currentTimeMillis() - t0)
        }

        anchorWords = listOf(bestPair.first.first.text, bestPair.second.first.text)

        // 3. TRANSFORMATION (Scale & Pan)
        val r1 = bestPair.first.first.boundingBox; val r2 = bestPair.second.first.boundingBox
        val q1 = bestPair.first.second.boundingBox; val q2 = bestPair.second.second.boundingBox
        
        // Landmarks were extracted from a 1500px scaled image. We must map coordinates back to the original full-res bitmaps.
        val refScale = refBmp.width / 1500f
        val queScale = queryBmp.width / 1500f

        val r1cx = r1.centerX() * refScale; val r1cy = r1.centerY() * refScale
        val r2cx = r2.centerX() * refScale; val r2cy = r2.centerY() * refScale
        
        val q1cx = q1.centerX() * queScale; val q1cy = q1.centerY() * queScale
        val q2cx = q2.centerX() * queScale; val q2cy = q2.centerY() * queScale
        
        val refDist = sqrt(((r1cx - r2cx).toDouble().let { it * it }) + ((r1cy - r2cy).toDouble().let { it * it }))
        val queDist = sqrt(((q1cx - q2cx).toDouble().let { it * it }) + ((q1cy - q2cy).toDouble().let { it * it }))
        
        if (queDist == 0.0) return AnchorResult(false, message = "Query anchors overlap.", timeMs = System.currentTimeMillis() - t0)
        
        val scale = (refDist / queDist).toFloat()
        
        // Panning: Exact match for the first anchor
        // target_cx = scale * query_cx + tx => tx = target_cx - (scale * query_cx)
        val tx = r1cx - (scale * q1cx)
        val ty = r1cy - (scale * q1cy)

        val matrix = android.graphics.Matrix()
        matrix.postScale(scale, scale)
        matrix.postTranslate(tx, ty)

        val msg = "S=%.3f, tx=%.1f, ty=%.1f".format(scale, tx, ty)

        return try {
            val outBmp = Bitmap.createBitmap(refBmp.width, refBmp.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(outBmp)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.drawBitmap(queryBmp, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            
            AnchorResult(true, outBmp, System.currentTimeMillis() - t0, strategyUsed, anchorWords, msg)
        } catch (e: Exception) {
            AnchorResult(false, message = "Warp failed: ${e.message}", timeMs = System.currentTimeMillis() - t0)
        }
    }

    private fun dist(a: TextBlock, b: TextBlock): Double {
        val dx = (a.boundingBox.centerX() - b.boundingBox.centerX()).toDouble()
        val dy = (a.boundingBox.centerY() - b.boundingBox.centerY()).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    fun getLandmarksFromJson(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        val result = mutableSetOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(obj.getString("text").trim())
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
        val queryWordsList = queryLandmarks.map { it.text.trim() }.sorted()
        val queryWordsSet = queryWordsList.toSet()
        val vehicleLandmarks = allVehicles.associate { it.id to getLandmarksFromJson(it.landmarkTextBlocksJson) }
        
        return allVehicles.associate { currentVehicle ->
            val myWords = vehicleLandmarks[currentVehicle.id] ?: emptySet()
            
            // Pool = all words from everyone else
            val otherWordsPool = vehicleLandmarks.filter { it.key != currentVehicle.id }
                .values.flatten().toSet()
            
            // Veto Pool = Words others have that I don't
            val vetoPool = otherWordsPool - myWords
            
            // DYNAMIC FIX: Identify ALL triggers, sort them, and remove duplicates
            val triggers = queryWordsSet.intersect(vetoPool).sorted()
            
            currentVehicle.id to VetoResult(
                isVetoed = triggers.isNotEmpty(),
                reasonWord = if (triggers.isNotEmpty()) triggers.joinToString(", ") else "",
                tierReached = 0,
                queryWords = queryWordsList,
                myManifest = myWords.toList().sorted(),
                vetoPool = vetoPool.toList().sorted()
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
        
        // Deskew handles rotation now. Strip rotation from affine matrix, keep only translation and scale.
        val scale = kotlin.math.sqrt(det)
        val tx = affineMatrix.get(0, 2)?.get(0) ?: 0.0
        val ty = affineMatrix.get(1, 2)?.get(0) ?: 0.0
        val noRotMatrix = Mat.zeros(2, 3, CvType.CV_64F); noRotMatrix.put(0, 0, scale, 0.0, tx); noRotMatrix.put(1, 0, 0.0, scale, ty)
        
        val alignedMat = Mat(); Imgproc.warpAffine(queryMat, alignedMat, noRotMatrix, refMat.size())
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
        
        // OPTIMIZATION: Shrink search area to the middle third of the image to vastly speed up HoughCircles
        val refW = grayRef.cols(); val refH = grayRef.rows()
        val queW = grayQuery.cols(); val queH = grayQuery.rows()
        val croppedRef = Mat(grayRef, org.opencv.core.Rect(refW / 3, refH / 3, refW / 3, refH / 3))
        val croppedQuery = Mat(grayQuery, org.opencv.core.Rect(queW / 3, queH / 3, queW / 3, queH / 3))

        val circlesRef = Mat(); Imgproc.HoughCircles(croppedRef, circlesRef, Imgproc.HOUGH_GRADIENT, 1.0, 100.0, 100.0, 30.0, 100, 1000)
        val circlesQuery = Mat(); Imgproc.HoughCircles(croppedQuery, circlesQuery, Imgproc.HOUGH_GRADIENT, 1.0, 100.0, 100.0, 30.0, 100, 1000)
        
        if (circlesRef.cols() > 0 && circlesQuery.cols() > 0) {
            val cRef = circlesRef.get(0, 0); val cQue = circlesQuery.get(0, 0)
            // Adjust circle centers back to global coordinates
            val tx = (cRef[0] + refW / 3) - (cQue[0] + queW / 3)
            val ty = (cRef[1] + refH / 3) - (cQue[1] + queH / 3)
            val scale = cRef[2] / cQue[2]
            
            val matrix = Mat.zeros(2, 3, CvType.CV_64F); matrix.put(0, 0, scale, 0.0, tx); matrix.put(1, 0, 0.0, scale, ty)
            val alignedMat = Mat(); Imgproc.warpAffine(queryMat, alignedMat, matrix, refMat.size())
            val alignedBitmap = Bitmap.createBitmap(alignedMat.cols(), alignedMat.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(alignedMat, alignedBitmap)
            
            refMat.release(); queryMat.release(); grayRef.release(); grayQuery.release(); croppedRef.release(); croppedQuery.release(); alignedMat.release()
            return@withContext AlignmentResult(true, alignedBitmap, 0.8f, "Hub aligned", method = "hub")
        }
        refMat.release(); queryMat.release(); grayRef.release(); grayQuery.release(); croppedRef.release(); croppedQuery.release()
        AlignmentResult(false, null, 0f, "Hub Alignment Abandoned", method = "hub")
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
        veto: VetoResult = VetoResult(false),
        onLog: (suspend (String) -> Unit)? = null
    ): Map<String, AlignmentResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, AlignmentResult>()
        
        onLog?.invoke("Aligning: ORB Feature pass...")
        var t0 = System.currentTimeMillis()
        val featureResult = if (skipExpensiveORB) AlignmentResult(false, null, 0f, "ORB Skipped", method = "feature") else alignImages(reference, query, 10, odometerCrop, otherTextCrop)
        results["feature"] = featureResult.copy(timeMs = System.currentTimeMillis() - t0)
        
        onLog?.invoke("Matching: ARG pass...")
        t0 = System.currentTimeMillis()
        val argRes = argMatch(refOcr.textBlocks, queryOcr.textBlocks, odometerCrop, otherTextCrop, refOcr.imageWidth, refOcr.imageHeight)
        results["arg"] = AlignmentResult(true, null, argRes, "ARG", method = "arg", timeMs = System.currentTimeMillis() - t0)
        
        onLog?.invoke("Matching: Embedding pass...")
        t0 = System.currentTimeMillis()
        val embRes = embeddingMatch(refOcr.textBlocks, queryOcr.textBlocks)
        results["embedding"] = AlignmentResult(true, null, embRes, "Emb", method = "embedding", timeMs = System.currentTimeMillis() - t0)
        
        val tCons0 = System.currentTimeMillis()
        val featScoreNorm = if (featureResult.success) (featureResult.goodMatchesCount / 40f).coerceIn(0f, 1f) else 0f
        val consensusScore = (featScoreNorm * 0.10f) + (results["embedding"]!!.confidence * 0.45f) + (results["arg"]!!.confidence * 0.45f)
        results["consensus"] = AlignmentResult(true, null, if (veto.isVetoed) -1f else consensusScore, if (veto.isVetoed) "VETO: ${veto.reasonWord}" else "OK", method = "consensus", wordVeto = veto.isVetoed, vetoReason = veto.reasonWord, timeMs = System.currentTimeMillis() - tCons0)
        
        val tTier0 = System.currentTimeMillis()
        val tieredResult = calculateTieredMatch(results, veto)
        results["tiered"] = tieredResult.copy(timeMs = System.currentTimeMillis() - tTier0)
        results
    }

    private fun calculateTieredMatch(results: Map<String, AlignmentResult>, veto: VetoResult): AlignmentResult {
        if (veto.isVetoed) return AlignmentResult(true, null, -1f, "TIER 0: VETO (Word: '${veto.reasonWord}')", method = "tiered", wordVeto = true, tierReached = 0, vetoReason = veto.reasonWord)
        val emb = results["embedding"]?.confidence ?: 0f; val arg = results["arg"]?.confidence ?: 0f; val feat = results["feature"]?.confidence ?: 0f
        if (emb > 0.85f || arg > 0.85f) return AlignmentResult(true, null, maxOf(emb, arg), "TIER 1: Text High-Conf", method = "tiered", tierReached = 1)
        if (emb > 0.5f && arg > 0.5f) return AlignmentResult(true, null, (emb + arg) / 2f, "TIER 2: Text Agreement", method = "tiered", tierReached = 2)
        if (feat > 0.3f) return AlignmentResult(true, null, feat, "TIER 3: Spatial Feature Match", method = "tiered", tierReached = 3)
        return AlignmentResult(false, null, maxOf(emb, arg, feat) * 0.5f, "TIER 4: Inconclusive", method = "tiered", tierReached = 4)
    }
}
