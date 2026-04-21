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
import kotlin.math.pow
import org.json.JSONArray
import org.json.JSONObject
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle

data class AlignmentResult(
    val success: Boolean,
    val alignedImage: Bitmap?,
    val confidence: Float,
    val message: String,
    val method: String = "feature",
    val wordVeto: Boolean = false,
    val vetoReason: String = "",
    val timeMs: Long = 0,
    val tierReached: Int = 0,
    val metadata: Map<String, String> = emptyMap()
)

data class VetoResult(
    val isVetoed: Boolean,
    val reasonWord: String = "",
    val tierReached: Int = 0,
    val queryWords: List<String> = emptyList(),
    val myManifest: List<String> = emptyList(),
    val vetoPool: List<String> = emptyList()
)

data class AnchorCandidate(
    val strategy: String,
    val anchorsUsed: List<String>,
    val scale: Float,
    val tx: Float,
    val ty: Float,
    val distance: Double,
    val message: String
)

data class AnchorResult(
    val success: Boolean,
    val alignedImage: Bitmap? = null,
    val confidence: Float = 0f,
    val timeMs: Long = 0,
    val metadata: Map<String, String> = emptyMap(),
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
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle
    ): AnchorResult {
        val t0 = System.currentTimeMillis()
        val allCandidates = mutableListOf<AnchorCandidate>()
        
        val refScale = refBmp.width / 1500f
        val queScale = queryBmp.width / 1500f

        // 1. STRATEGY A: Uniqueness
        val refCounts = refLandmarks.groupBy { it.text }.mapValues { it.value.size }
        val queCounts = queryLandmarks.groupBy { it.text }.mapValues { it.value.size }
        
        val uniqueMatches = refLandmarks.filter { refCounts[it.text] == 1 }
            .mapNotNull { refMark ->
                val queMark = queryLandmarks.find { it.text == refMark.text && queCounts[it.text] == 1 }
                if (queMark != null) refMark to queMark else null
            }

        if (uniqueMatches.size >= 2) {
            for (i in uniqueMatches.indices) {
                for (j in i + 1 until uniqueMatches.size) {
                    val r1 = uniqueMatches[i].first; val r2 = uniqueMatches[j].first
                    val q1 = uniqueMatches[i].second; val q2 = uniqueMatches[j].second
                    
                    val r1cx = r1.boundingBox.centerX() * refScale; val r1cy = r1.boundingBox.centerY() * refScale
                    val r2cx = r2.boundingBox.centerX() * refScale; val r2cy = r2.boundingBox.centerY() * refScale
                    val q1cx = q1.boundingBox.centerX() * queScale; val q1cy = q1.boundingBox.centerY() * queScale
                    val q2cx = q2.boundingBox.centerX() * queScale; val q2cy = q2.boundingBox.centerY() * queScale
                    
                    val refDist = sqrt((r1cx - r2cx).toDouble().pow(2.0) + (r1cy - r2cy).toDouble().pow(2.0))
                    val queDist = sqrt((q1cx - q2cx).toDouble().pow(2.0) + (q1cy - q2cy).toDouble().pow(2.0))
                    
                    if (queDist > 0) {
                        val s = (refDist / queDist).toFloat()
                        val tx = r1cx - (s * q1cx); val ty = r1cy - (s * q1cy)
                        allCandidates.add(AnchorCandidate("A (Unique)", listOf(r1.text, r2.text), s, tx, ty, refDist, "S=%.3f, tx=%.1f, ty=%.1f".format(s, tx, ty)))
                    }
                }
            }
        } 
        
        // 2. STRATEGY B: Triangle Similarity
        val commonWords = refCounts.keys.intersect(queCounts.keys).toList()
        if (commonWords.size >= 3) {
            for (i in commonWords.indices) {
                for (j in i + 1 until commonWords.size) {
                    for (k in j + 1 until commonWords.size) {
                        val w1 = commonWords[i]; val w2 = commonWords[j]; val w3 = commonWords[k]
                        val r1s = refLandmarks.filter { it.text == w1 }; val r2s = refLandmarks.filter { it.text == w2 }; val r3s = refLandmarks.filter { it.text == w3 }
                        val q1s = queryLandmarks.filter { it.text == w1 }; val q2s = queryLandmarks.filter { it.text == w2 }; val q3s = queryLandmarks.filter { it.text == w3 }
                        
                        for (r1 in r1s) for (r2 in r2s) for (r3 in r3s) {
                            val rD12 = dist(r1, r2); val rD23 = dist(r2, r3); val rD31 = dist(r3, r1)
                            if (rD12 == 0.0 || rD23 == 0.0 || rD31 == 0.0) continue
                            for (q1 in q1s) for (q2 in q2s) for (q3 in q3s) {
                                val qD12 = dist(q1, q2); val qD23 = dist(q2, q3); val qD31 = dist(q3, q1)
                                if (qD12 == 0.0 || qD23 == 0.0 || qD31 == 0.0) continue
                                val ratio1 = (qD12 / rD12) / (qD23 / rD23); val ratio2 = (qD23 / rD23) / (qD31 / rD31)
                                if (abs(ratio1 - 1.0) < 0.05 && abs(ratio2 - 1.0) < 0.05) {
                                    val rPairs = listOf(r1 to r2, r2 to r3, r3 to r1); val qPairs = listOf(q1 to q2, q2 to q3, q3 to q1)
                                    val longestIdx = listOf(rD12, rD23, rD31).indices.maxBy { listOf(rD12, rD23, rD31)[it] }
                                    val rA = rPairs[longestIdx].first; val rB = rPairs[longestIdx].second
                                    val qA = qPairs[longestIdx].first; val qB = qPairs[longestIdx].second
                                    
                                    val rAcx = rA.boundingBox.centerX() * refScale; val rAcy = rA.boundingBox.centerY() * refScale
                                    val rBcx = rB.boundingBox.centerX() * refScale; val rBcy = rB.boundingBox.centerY() * refScale
                                    val qAcx = qA.boundingBox.centerX() * queScale; val qAcy = qA.boundingBox.centerY() * queScale
                                    val qBcx = qB.boundingBox.centerX() * queScale; val qBcy = qB.boundingBox.centerY() * queScale
                                    
                                    val dR = sqrt((rAcx - rBcx).toDouble().pow(2.0) + (rAcy - rBcy).toDouble().pow(2.0))
                                    val dQ = sqrt((qAcx - qBcx).toDouble().pow(2.0) + (qAcy - rBcy).toDouble().pow(2.0))
                                    if (dQ > 0) {
                                        val s = (dR / dQ).toFloat()
                                        val tx = rAcx - (s * qAcx); val ty = rAcy - (s * qAcy)
                                        allCandidates.add(AnchorCandidate("B (Tri)", listOf(w1, w2, w3), s, tx, ty, dR, "S=%.3f, tx=%.1f, ty=%.1f".format(s, tx, ty)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val top3 = allCandidates.sortedByDescending { it.distance }.take(3)
        if (top3.isEmpty()) return AnchorResult(false, message = "No valid anchor sets.", timeMs = System.currentTimeMillis() - t0)

        // Average the top 3 candidates
        val avgScale = top3.map { it.scale }.average().toFloat()
        val avgTx = top3.map { it.tx }.average().toFloat()
        val avgTy = top3.map { it.ty }.average().toFloat()

        val matrix = android.graphics.Matrix()
        matrix.postScale(avgScale, avgScale)
        matrix.postTranslate(avgTx, avgTy)

        val metadata = mapOf(
            "Candidates" to top3.mapIndexed { i, c ->
                "#${i+1}: ${c.strategy} [${c.anchorsUsed.joinToString(", ")}] -> S=%.3f, tx=%.1f, ty=%.1f".format(c.scale, c.tx, c.ty)
            }.joinToString("\n"),
            "Average" to "S=%.3f, tx=%.1f, ty=%.1f".format(avgScale, avgTx, avgTy)
        )

        return try {
            val outBmp = Bitmap.createBitmap(refBmp.width, refBmp.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(outBmp)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.drawBitmap(queryBmp, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            AnchorResult(true, outBmp, 0.5f, System.currentTimeMillis() - t0, metadata, "Avg: S=%.3f, tx=%.1f, ty=%.1f".format(avgScale, avgTx, avgTy))
        } catch (e: Exception) {
            AnchorResult(false, message = "Warp failed: ${e.message}", timeMs = System.currentTimeMillis() - t0, metadata = metadata)
        }
    }

    private fun dist(a: TextBlock, b: TextBlock): Double {
        val dx = (a.boundingBox.centerX() - b.boundingBox.centerX()).toDouble()
        val dy = (a.boundingBox.centerY() - b.boundingBox.centerY()).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    fun getLandmarksFromJson(json: String?, engineName: String): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        val result = mutableSetOf<String>()
        try {
            val root = JSONObject(json)
            val array = if (root.has(engineName)) {
                root.getJSONArray(engineName)
            } else if (json.startsWith("[")) {
                JSONArray(json) // Legacy support
            } else {
                // Fallback to first available if specific engine not found
                val keys = root.keys()
                if (keys.hasNext()) root.getJSONArray(keys.next()) else null
            } ?: return emptySet()

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
    fun performTier1Veto(queryLandmarks: List<TextBlock>, allVehicles: List<Vehicle>, engineName: String): Map<Int, VetoResult> {
        val queryWordsList = queryLandmarks.map { it.text.trim() }.sorted()
        val queryWordsSet = queryWordsList.toSet()
        val vehicleLandmarks = allVehicles.associate { it.id to getLandmarksFromJson(it.landmarkTextBlocksJson, engineName) }
        
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
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle
    ): AlignmentResult = withContext(Dispatchers.IO) {
        val minInliers = 10
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
        val kp1Array = kp1.toArray(); val kp2Array = kp2.toArray()
        if (goodMatches.size < minInliers) { refMat.release(); queryMat.release(); return@withContext AlignmentResult(false, null, 0f, "Too few matches (${goodMatches.size})", metadata = mapOf("Ref Keypoints" to kp1Array.size.toString(), "Query Keypoints" to kp2Array.size.toString(), "Good Matches" to goodMatches.size.toString())) }
        val srcPoints = MatOfPoint2f(); val dstPoints = MatOfPoint2f(); val srcList = mutableListOf<org.opencv.core.Point>(); val dstList = mutableListOf<org.opencv.core.Point>()
        for (match in goodMatches) { srcList.add(kp1Array[match.queryIdx].pt); dstList.add(kp2Array[match.trainIdx].pt) }
        srcPoints.fromList(srcList); dstPoints.fromList(dstList)
        val inliers = Mat(); val affineMatrix = Calib3d.estimateAffinePartial2D(dstPoints, srcPoints, inliers)
        if (affineMatrix.empty()) { refMat.release(); queryMat.release(); return@withContext AlignmentResult(false, null, 0f, "Affine matrix empty", metadata = mapOf("Ref Keypoints" to kp1Array.size.toString(), "Query Keypoints" to kp2Array.size.toString(), "Good Matches" to goodMatches.size.toString())) }
        val a = affineMatrix.get(0, 0)?.get(0) ?: 0.0; val b = affineMatrix.get(0, 1)?.get(0) ?: 0.0; val det = a * a + b * b
        if (det < 0.0001 || det > 1000.0) { refMat.release(); queryMat.release(); return@withContext AlignmentResult(false, null, 0f, "Alignment abandoned: Scale determinant ($det) insane", metadata = mapOf("Ref Keypoints" to kp1Array.size.toString(), "Query Keypoints" to kp2Array.size.toString(), "Good Matches" to goodMatches.size.toString())) }
        
        // Deskew handles rotation now. Strip rotation from affine matrix, keep only translation and scale.
        val scale = kotlin.math.sqrt(det)
        val tx = affineMatrix.get(0, 2)?.get(0) ?: 0.0
        val ty = affineMatrix.get(1, 2)?.get(0) ?: 0.0
        val noRotMatrix = Mat.zeros(2, 3, CvType.CV_64F); noRotMatrix.put(0, 0, scale, 0.0, tx); noRotMatrix.put(1, 0, 0.0, scale, ty)
        
        val alignedMat = Mat(); Imgproc.warpAffine(queryMat, alignedMat, noRotMatrix, refMat.size())
        val alignedBitmap = Bitmap.createBitmap(alignedMat.cols(), alignedMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(alignedMat, alignedBitmap)
        refMat.release(); queryMat.release(); alignedMat.release()
        AlignmentResult(true, alignedBitmap, (goodMatches.size / 40f).coerceIn(0f, 1f), "ORB Affine Success", metadata = mapOf("Ref Keypoints" to kp1Array.size.toString(), "Query Keypoints" to kp2Array.size.toString(), "Good Matches" to goodMatches.size.toString()))
    }

    suspend fun hubAlign(
        reference: Bitmap,
        query: Bitmap,
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle
    ): AlignmentResult = withContext(Dispatchers.IO) {
        val refMat = Mat(); val queryMat = Mat()
        org.opencv.android.Utils.bitmapToMat(reference, refMat); org.opencv.android.Utils.bitmapToMat(query, queryMat)
        val grayRef = Mat(); val grayQuery = Mat()
        Imgproc.cvtColor(refMat, grayRef, Imgproc.COLOR_RGB2GRAY); Imgproc.cvtColor(queryMat, grayQuery, Imgproc.COLOR_RGB2GRAY)
        Imgproc.GaussianBlur(grayRef, grayRef, Size(9.0, 9.0), 2.0); Imgproc.GaussianBlur(grayQuery, grayQuery, Size(9.0, 9.0), 2.0)
        val circlesRef = Mat(); val circlesQuery = Mat()
        Imgproc.HoughCircles(grayRef, circlesRef, Imgproc.HOUGH_GRADIENT, 1.0, grayRef.rows() / 8.0, 100.0, 30.0, 50, 300)
        Imgproc.HoughCircles(grayQuery, circlesQuery, Imgproc.HOUGH_GRADIENT, 1.0, grayQuery.rows() / 8.0, 100.0, 30.0, 50, 300)
        if (circlesRef.cols() > 0 && circlesQuery.cols() > 0) {
            val cR = circlesRef.get(0, 0); val cQ = circlesQuery.get(0, 0)
            val scale = (cR[2] / cQ[2]).toFloat(); val tx = (cR[0] - scale * cQ[0]).toFloat(); val ty = (cR[1] - scale * cQ[1]).toFloat()
            val matrix = android.graphics.Matrix(); matrix.postScale(scale, scale); matrix.postTranslate(tx, ty)
            val outBmp = Bitmap.createBitmap(reference.width, reference.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(outBmp); canvas.drawBitmap(query, matrix, null)
            AlignmentResult(true, outBmp, 0.5f, "Hub Success", method = "hub")
        } else { AlignmentResult(false, null, 0f, "Hub failed", method = "hub") }
    }

    fun calculateTieredMatch(results: Map<String, AlignmentResult>, veto: VetoResult): AlignmentResult {
        if (veto.isVetoed) return AlignmentResult(true, null, -1f, "TIER 0: VETO (Word: '${veto.reasonWord}')", method = "tiered", wordVeto = true, tierReached = 0, vetoReason = veto.reasonWord)
        val emb = results["embedding"]?.confidence ?: 0f; val arg = results["arg"]?.confidence ?: 0f; val feat = results["feature"]?.confidence ?: 0f
        if (emb > 0.85f || arg > 0.85f) return AlignmentResult(true, null, maxOf(emb, arg), "TIER 1: Text High-Conf", method = "tiered", tierReached = 1)
        if (emb > 0.5f && arg > 0.5f) return AlignmentResult(true, null, (emb + arg) / 2f, "TIER 2: Text Agreement", method = "tiered", tierReached = 2)
        if (feat > 0.3f) return AlignmentResult(true, null, feat, "TIER 3: Spatial Feature Match", method = "tiered", tierReached = 3)
        return AlignmentResult(false, null, maxOf(emb, arg, feat) * 0.5f, "TIER 4: Inconclusive", method = "tiered", tierReached = 4)
    }

    fun argMatch(ref: List<TextBlock>, query: List<TextBlock>, odo: android.graphics.RectF?, other: android.graphics.RectF?, w: Int, h: Int): Float {
        val r = ref.filter { !isBlockInCrop(it, odo, w, h) && !isBlockInCrop(it, other, w, h) }.map { it.text }.toSet()
        val q = query.map { it.text }.toSet()
        if (r.isEmpty()) return 0f
        return r.intersect(q).size.toFloat() / r.size.toFloat()
    }

    fun embeddingMatch(ref: List<TextBlock>, query: List<TextBlock>): Float {
        val r = ref.map { it.text }.toSet(); val q = query.map { it.text }.toSet()
        if (r.isEmpty()) return 0f
        return r.intersect(q).size.toFloat() / r.size.toFloat()
    }

    fun isBlockInCrop(block: TextBlock, crop: android.graphics.RectF?, imgW: Int, imgH: Int): Boolean {
        if (crop == null) return false
        val b = block.boundingBox
        val bL = b.left.toFloat() / imgW; val bT = b.top.toFloat() / imgH; val bR = b.right.toFloat() / imgW; val bB = b.bottom.toFloat() / imgH
        return bL >= crop.left && bR <= crop.right && bT >= crop.top && bB <= crop.bottom
    }
}
