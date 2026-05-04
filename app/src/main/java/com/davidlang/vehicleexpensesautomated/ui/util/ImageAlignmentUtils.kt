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
    val rotation: Float, // Degrees
    val tx: Float,
    val ty: Float,
    val distance: Double,
    val message: String,
    val cyRef: Float = 0.5f,
    val y1Ref: Float = 0.5f,
    val y2Ref: Float = 0.5f
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

    fun disambiguateLandmarks(
        dashLandmarks: List<TextBlock>,
        refLandmarks: List<TextBlock>
    ): List<TextBlock> {
        val dashValid = dashLandmarks.filter { it.boundingBox.width() > 0 }
        val refValid = refLandmarks.filter { it.boundingBox.width() > 0 }
        if (dashValid.isEmpty() || refValid.isEmpty()) return dashLandmarks

        val dashCounts = dashValid.groupBy { it.text }.mapValues { it.value.size }
        val refCounts = refValid.groupBy { it.text }.mapValues { it.value.size }
        val commonTexts = dashCounts.keys.intersect(refCounts.keys).toList()

        val results = dashValid.map { it.copy(instanceId = -1) }.toMutableList()

        for (i in results.indices) {
            val dashMark = results[i]
            if (dashCounts[dashMark.text] == 1 && refCounts[dashMark.text] == 1) {
                val refMatch = refValid.find { it.text == dashMark.text }!!
                results[i] = dashMark.copy(instanceId = refMatch.instanceId)
            }
        }

        if (results.count { it.instanceId != -1 } < 2) {
            val seedPool = commonTexts.map { text ->
                val dCount = dashCounts[text] ?: 0
                val rCount = refCounts[text] ?: 0
                val tier = if (dCount == 1 && rCount == 1) 1 else if (dCount == 1) 2 else 3
                Triple(text, tier, dCount)
            }.sortedWith(compareBy({ it.second }, { it.third }))

            outer@for (i in seedPool.indices) {
                for (j in i + 1 until seedPool.size) {
                    for (k in j + 1 until seedPool.size) {
                        val d1s = results.filter { it.text == seedPool[i].first }; val d2s = results.filter { it.text == seedPool[j].first }; val d3s = results.filter { it.text == seedPool[k].first }
                        val r1s = refValid.filter { it.text == seedPool[i].first }; val r2s = refValid.filter { it.text == seedPool[j].first }; val r3s = refValid.filter { it.text == seedPool[k].first }
                        
                        for (d1 in d1s) for (d2 in d2s) for (d3 in d3s) {
                            if (dist(d1, d2) < 20.0 || dist(d2, d3) < 20.0 || dist(d3, d1) < 20.0) continue
                            for (r1 in r1s) for (r2 in r2s) for (r3 in r3s) {
                                if (dist(r1, r2) == 0.0 || dist(r2, r3) == 0.0 || dist(r3, r1) == 0.0) continue
                                if (abs((dist(d1, d2) / dist(r1, r2)) / (dist(d2, d3) / dist(r2, r3)) - 1.0) < 0.05 && 
                                    abs((dist(d2, d3) / dist(r2, r3)) / (dist(d3, d1) / dist(r3, r1)) - 1.0) < 0.05) {
                                    results[results.indexOf(d1)] = d1.copy(instanceId = r1.instanceId)
                                    results[results.indexOf(d2)] = d2.copy(instanceId = r2.instanceId)
                                    results[results.indexOf(d3)] = d3.copy(instanceId = r3.instanceId)
                                    break@outer
                                }
                            }
                        }
                    }
                }
            }
        }

        val confirmed = results.filter { it.instanceId != -1 }
        if (confirmed.size >= 2) {
            val p1 = confirmed[0]; val p2 = confirmed[1]
            for (idx in results.indices) {
                if (results[idx].instanceId != -1) continue
                val refCandidates = refValid.filter { it.text == results[idx].text }
                for (cand in refCandidates) {
                    val rD1C = dist(confirmed.find { it.text == p1.text && it.instanceId == p1.instanceId }!!, cand)
                    val rD2C = dist(confirmed.find { it.text == p2.text && it.instanceId == p2.instanceId }!!, cand)
                    val rD12 = dist(confirmed.find { it.text == p1.text && it.instanceId == p1.instanceId }!!, confirmed.find { it.text == p2.text && it.instanceId == p2.instanceId }!!)
                    if (abs((dist(p1, results[idx]) / rD1C) / (dist(p1, p2) / rD12) - 1.0) < 0.05 && 
                        abs((dist(p2, results[idx]) / rD2C) / (dist(p1, p2) / rD12) - 1.0) < 0.05) {
                        results[idx] = results[idx].copy(instanceId = cand.instanceId)
                        break
                    }
                }
            }
        }
        return results
    }

    fun anchorAlign(
        refBmp: Bitmap,
        queryBmp: Bitmap,
        targetBmp: Bitmap,
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle
    ): AnchorResult {
        val t0 = System.currentTimeMillis()
        val allCandidates = mutableListOf<AnchorCandidate>()
        val targetY = if (vehicle.odometerCropTop != null && vehicle.odometerCropBottom != null) {
            (vehicle.odometerCropTop!! + vehicle.odometerCropBottom!!) / 2.0f
        } else 0.5f
        
        // Phase 48: Landmarks are now extracted from full-res images, no scaling needed.
        val refScale = 1.0f
        val queScale = 1.0f

        // 1. STRATEGY A: Uniqueness
        val refCounts = refLandmarks.groupBy { it.text }.mapValues { it.value.size }
        val queCounts = queryLandmarks.groupBy { it.text }.mapValues { it.value.size }

        val uniqueMatches = refLandmarks.filter { refCounts[it.text] == 1 && it.boundingBox.width() > 0 }
            .mapNotNull { refMark ->
                val queMark = queryLandmarks.find { it.text == refMark.text && queCounts[it.text] == 1 && it.boundingBox.width() > 0 }
                if (queMark != null) refMark to queMark else null
            }
        if (uniqueMatches.size >= 2) {
            for (i in uniqueMatches.indices) {
                for (j in i + 1 until uniqueMatches.size) {
                    val r1 = uniqueMatches[i].first
                    val r2 = uniqueMatches[j].first
                    val q1 = uniqueMatches[i].second
                    val q2 = uniqueMatches[j].second
                    
                    val r1cx = r1.boundingBox.centerX() * refScale
                    val r1cy = r1.boundingBox.centerY() * refScale
                    val r2cx = r2.boundingBox.centerX() * refScale
                    val r2cy = r2.boundingBox.centerY() * refScale
                    val q1cx = q1.boundingBox.centerX() * queScale
                    val q1cy = q1.boundingBox.centerY() * queScale
                    val q2cx = q2.boundingBox.centerX() * queScale
                    val q2cy = q2.boundingBox.centerY() * queScale
                    
                    val refDist = sqrt((r1cx - r2cx).toDouble().pow(2.0) + (r1cy - r2cy).toDouble().pow(2.0))
                    val queDist = sqrt((q1cx - q2cx).toDouble().pow(2.0) + (q1cy - q2cy).toDouble().pow(2.0))
                    
                    if (queDist > 0) {
                        val s = (refDist / queDist).toFloat()
                        
                        // Calculate Rotation (Phase 44)
                        val rAngle = Math.atan2((r2cy - r1cy).toDouble(), (r2cx - r1cx).toDouble())
                        val qAngle = Math.atan2((q2cy - q1cy).toDouble(), (q2cx - q1cx).toDouble())
                        val rot = Math.toDegrees(rAngle - qAngle).toFloat()
                        
                        // Phase 53: Rotational Tolerance Filter and Zero-Rotation Warp
                        if (kotlin.math.abs(rot) > 4.0f) continue
                        val tx = r1cx - (s * q1cx)
                        val ty = r1cy - (s * q1cy)
                        val cyRef = (r1cy + r2cy) / 2.0f

                        allCandidates.add(AnchorCandidate("A (Unique)", listOf(r1.text, r2.text), s, rot, tx, ty, refDist, "S=%.3f, R=%.1f (Filter), tx=%.1f, ty=%.1f".format(s, rot, tx, ty), cyRef, r1cy, r2cy))
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
                        val w1 = commonWords[i]
                        val w2 = commonWords[j]
                        val w3 = commonWords[k]
                        val r1s = refLandmarks.filter { it.text == w1 && it.boundingBox.width() > 0 }
                        val r2s = refLandmarks.filter { it.text == w2 && it.boundingBox.width() > 0 }
                        val r3s = refLandmarks.filter { it.text == w3 && it.boundingBox.width() > 0 }
                        val q1s = queryLandmarks.filter { it.text == w1 && it.boundingBox.width() > 0 }
                        val q2s = queryLandmarks.filter { it.text == w2 && it.boundingBox.width() > 0 }
                        val q3s = queryLandmarks.filter { it.text == w3 && it.boundingBox.width() > 0 }
                        
                        for (r1 in r1s) for (r2 in r2s) for (r3 in r3s) {
                            val rD12 = dist(r1, r2)
                            val rD23 = dist(r2, r3)
                            val rD31 = dist(r3, r1)
                            if (rD12 == 0.0 || rD23 == 0.0 || rD31 == 0.0) continue
                            for (q1 in q1s) for (q2 in q2s) for (q3 in q3s) {
                                val qD12 = dist(q1, q2)
                                val qD23 = dist(q2, q3)
                                val qD31 = dist(q3, q1)
                                if (qD12 == 0.0 || qD23 == 0.0 || qD31 == 0.0) continue
                                val ratio1 = (qD12 / rD12) / (qD23 / rD23)
                                val ratio2 = (qD23 / rD23) / (qD31 / rD31)
                                if (abs(ratio1 - 1.0) < 0.05 && abs(ratio2 - 1.0) < 0.05) {
                                    val rPairs = listOf(r1 to r2, r2 to r3, r3 to r1)
                                    val qPairs = listOf(q1 to q2, q2 to q3, q3 to q1)
                                    val longestIdx = listOf(rD12, rD23, rD31).indices.maxBy { listOf(rD12, rD23, rD31)[it] }
                                    val rA = rPairs[longestIdx].first
                                    val rB = rPairs[longestIdx].second
                                    val qA = qPairs[longestIdx].first
                                    val qB = qPairs[longestIdx].second
                                    
                                    val rAcx = rA.boundingBox.centerX() * refScale
                                    val rAcy = rA.boundingBox.centerY() * refScale
                                    val rBcx = rB.boundingBox.centerX() * refScale
                                    val rBcy = rB.boundingBox.centerY() * refScale
                                    val qAcx = qA.boundingBox.centerX() * queScale
                                    val qAcy = qA.boundingBox.centerY() * queScale
                                    val qBcx = qB.boundingBox.centerX() * queScale
                                    val qBcy = qB.boundingBox.centerY() * queScale
                                    
                                    val dR = sqrt((rAcx - rBcx).toDouble().pow(2.0) + (rAcy - rBcy).toDouble().pow(2.0))
                                    val dQ = sqrt((qAcx - qBcx).toDouble().pow(2.0) + (qAcy - qBcy).toDouble().pow(2.0))
                                    if (dQ > 0) {
                                        val s = (dR / dQ).toFloat()
                                        
                                        // Calculate Rotation (Phase 44)
                                        val rAngle = Math.atan2((rBcy - rAcy).toDouble(), (rBcx - rAcx).toDouble())
                                        val qAngle = Math.atan2((qBcy - qAcy).toDouble(), (qBcx - qAcx).toDouble())
                                        val rot = Math.toDegrees(rAngle - qAngle).toFloat()

                                        // Phase 53: Rotational Tolerance Filter and Zero-Rotation Warp
                                        if (kotlin.math.abs(rot) > 4.0f) continue
                                        val tx = rAcx - (s * qAcx)
                                        val ty = rAcy - (s * qAcy)
                                        val cyRef = (rAcy + rBcy) / 2.0f

                                        allCandidates.add(AnchorCandidate("B (Tri)", listOf(w1, w2, w3), s, rot, tx, ty, dR, "S=%.3f, R=%.1f (Filter), tx=%.1f, ty=%.1f".format(s, rot, tx, ty), cyRef, rAcy, rBcy))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        if (allCandidates.isEmpty()) return AnchorResult(false, message = "No valid anchor sets.", timeMs = System.currentTimeMillis() - t0)

        // RANSAC-Lite Consensus (Phase 64)
        // Find the candidate group with the most mutual agreement
        var bestGroup = mutableListOf<AnchorCandidate>()
        var maxSupport = -1

        for (c1 in allCandidates) {
            val supportGroup = mutableListOf<AnchorCandidate>()
            for (c2 in allCandidates) {
                val ds = kotlin.math.abs(c1.scale - c2.scale) / c1.scale
                val dtx = kotlin.math.abs(c1.tx - c2.tx)
                val dty = kotlin.math.abs(c1.ty - c2.ty)
                
                // Agreement threshold: 5% scale, 0.05 normalized translation
                if (ds < 0.05f && dtx < 0.05f && dty < 0.05f) {
                    supportGroup.add(c2)
                }
            }
            if (supportGroup.size > maxSupport) {
                maxSupport = supportGroup.size
                bestGroup = supportGroup
            }
        }

        // Distance-Weighted and Proximity-Weighted Average of the winning consensus group
        val finalScale: Float
        val finalTx: Float
        val finalTy: Float
        var bracketedCount = 0

        if (bestGroup.isNotEmpty()) {
            var sumScale = 0.0
            var sumTx = 0.0
            var sumTy = 0.0
            var totalW = 0.0
            for (c in bestGroup) {
                // Phase 66: Midline Bracketing Bonus
                // A pair "brackets" the odometer if one point is above and one is below the midline.
                val isBracketed = (c.y1Ref - targetY) * (c.y2Ref - targetY) < 0
                val bracketBonus = if (isBracketed) { bracketedCount++; 5.0 } else 1.0

                // Vertical Proximity Weighting (Phase 65): Favor landmarks near the odometer midline
                val vDist = kotlin.math.abs(c.cyRef - targetY)
                val w = (c.distance * bracketBonus) / (vDist + 0.05)
                sumScale += c.scale * w
                sumTx += c.tx * w
                sumTy += c.ty * w
                totalW += w
            }
            finalScale = if (totalW > 0) (sumScale / totalW).toFloat() else allCandidates.map { it.scale }.median()
            finalTx = if (totalW > 0) (sumTx / totalW).toFloat() else allCandidates.map { it.tx }.median()
            finalTy = if (totalW > 0) (sumTy / totalW).toFloat() else allCandidates.map { it.ty }.median()
        } else {
            finalScale = allCandidates.map { it.scale }.median()
            finalTx = allCandidates.map { it.tx }.median()
            finalTy = allCandidates.map { it.ty }.median()
        }

        val matrix = android.graphics.Matrix()
        matrix.postScale(finalScale, finalScale)
        // Phase 53: Zero-Rotation Warp. Global deskew already handled rotation.
        matrix.postTranslate(finalTx, finalTy)

        val metadata = mapOf(
            "Candidates" to allCandidates.sortedByDescending { it.distance }.take(5).mapIndexed { i, c ->
                "#${i+1}: ${c.strategy} [${c.anchorsUsed.joinToString(", ")}] -> ${c.message}"
            }.joinToString("\n"),
            "Consensus" to "S=%.3f, tx=%.1f, ty=%.1f (Support: %d/%d, Bracketing: %d)".format(finalScale, finalTx, finalTy, bestGroup.size, allCandidates.size, bracketedCount),
            "raw_scale" to finalScale.toString(),
            "raw_tx" to finalTx.toString(),
            "raw_ty" to finalTy.toString()
        )

        return try {
            val canvas = android.graphics.Canvas(targetBmp)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.drawBitmap(queryBmp, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            AnchorResult(true, targetBmp, 0.5f, System.currentTimeMillis() - t0, metadata, "Consensus (%d/%d) [B:%d]: S=%.3f, tx=%.1f, ty=%.1f".format(bestGroup.size, allCandidates.size, bracketedCount, finalScale, finalTx, finalTy))
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
        
        val initialResults = allVehicles.associate { currentVehicle ->
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

        // Phase 47: Least-Vetoed Rescue Algorithm
        // Check if ALL vehicles were vetoed (Mutual Veto)
        if (initialResults.values.all { it.isVetoed }) {
            // Count triggers for each vehicle
            val triggerCounts = initialResults.mapValues { (_, res) -> 
                if (res.reasonWord.isEmpty()) 0 else res.reasonWord.split(", ").size 
            }
            
            // Identify vehicles with exactly 1 trigger
            val vehiclesWithOneTrigger = triggerCounts.filter { it.value == 1 }.keys.toList()
            
            // Identify if all OTHER vehicles have 3 or more triggers
            val otherVehiclesHaveManyTriggers = triggerCounts.filter { it.key !in vehiclesWithOneTrigger }
                .all { it.value >= 3 }
                
            // Rescue Condition: Exactly ONE vehicle has 1 trigger, AND all others have >= 3
            if (vehiclesWithOneTrigger.size == 1 && otherVehiclesHaveManyTriggers && allVehicles.size > 1) {
                val rescuedId = vehiclesWithOneTrigger[0]
                val rescuedResult = initialResults[rescuedId]!!
                val newResults = initialResults.toMutableMap()
                newResults[rescuedId] = rescuedResult.copy(
                    isVetoed = false,
                    reasonWord = "[RESCUED 1 vs 3+] Was: ${rescuedResult.reasonWord}"
                )
                return newResults
            }
        }
        
        return initialResults
    }

    fun isBlockInCrop(block: TextBlock, crop: android.graphics.RectF?, imgW: Int, imgH: Int): Boolean {
        if (crop == null) return false
        val b = block.boundingBox
        val bL = b.left.toFloat() / imgW
        val bT = b.top.toFloat() / imgH
        val bR = b.right.toFloat() / imgW
        val bB = b.bottom.toFloat() / imgH
        return bL >= crop.left && bR <= crop.right && bT >= crop.top && bB <= crop.bottom
    }

    private fun List<Float>.median(): Float {
        if (isEmpty()) return 0f
        val sorted = sorted()
        return if (size % 2 == 0) (sorted[size / 2 - 1] + sorted[size / 2]) / 2f else sorted[size / 2]
    }
}
