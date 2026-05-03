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
        // Step 0: Filter to common candidates with physical positions
        val dashValid = dashLandmarks.filter { it.boundingBox.width() > 0 }
        val refValid = refLandmarks.filter { it.boundingBox.width() > 0 }
        if (dashValid.isEmpty() || refValid.isEmpty()) return dashLandmarks

        val dashCounts = dashValid.groupBy { it.text }.mapValues { it.value.size }
        val refCounts = refValid.groupBy { it.text }.mapValues { it.value.size }
        val commonTexts = dashCounts.keys.intersect(refCounts.keys).toList()

        if (commonTexts.size < 3) return dashLandmarks

        // Step 1: Prioritize Seed Pool (Tier 1: Globally Unique, Tier 2: Unique on Dash, Tier 3: Fewest on Dash)
        val seedPool = commonTexts.map { text ->
            val dCount = dashCounts[text] ?: 0
            val rCount = refCounts[text] ?: 0
            val tier = when {
                dCount == 1 && rCount == 1 -> 1
                dCount == 1 -> 2
                else -> 3
            }
            Triple(text, tier, dCount)
        }.sortedWith(compareBy({ it.second }, { it.third }))

        // Step 2: Find the First Matching Triangle with geometric distance weighting
        var certifiedPairs = mutableListOf<Pair<TextBlock, TextBlock>>()
        
        outer@for (i in seedPool.indices) {
            for (j in i + 1 until seedPool.size) {
                for (k in j + 1 until seedPool.size) {
                    val t1 = seedPool[i].first; val t2 = seedPool[j].first; val t3 = seedPool[k].first
                    
                    val d1s = dashValid.filter { it.text == t1 }; val d2s = dashValid.filter { it.text == t2 }; val d3s = dashValid.filter { it.text == t3 }
                    val r1s = refValid.filter { it.text == t1 }; val r2s = refValid.filter { it.text == t2 }; val r3s = refValid.filter { it.text == t3 }
                    
                    for (d1 in d1s) for (d2 in d2s) for (d3 in d3s) {
                        val qD12 = dist(d1, d2); val qD23 = dist(d2, d3); val qD31 = dist(d3, d1)
                        // Geometric Stability Mandate: Prioritize triangles whose points are furthest apart
                        if (qD12 < 20.0 || qD23 < 20.0 || qD31 < 20.0) continue

                        for (r1 in r1s) for (r2 in r2s) for (r3 in r3s) {
                            val rD12 = dist(r1, r2); val rD23 = dist(r2, r3); val rD31 = dist(r3, r1)
                            if (rD12 == 0.0 || rD23 == 0.0 || rD31 == 0.0) continue

                            val ratio1 = (qD12 / rD12) / (qD23 / rD23)
                            val ratio2 = (qD23 / rD23) / (qD31 / rD31)

                            if (abs(ratio1 - 1.0) < 0.05 && abs(ratio2 - 1.0) < 0.05) {
                                certifiedPairs.add(d1 to r1); certifiedPairs.add(d2 to r2); certifiedPairs.add(d3 to r3)
                                break@outer
                            }
                        }
                    }
                }
            }
        }

        if (certifiedPairs.isEmpty()) return dashLandmarks

        // Step 3: Bootstrap - Use the 3 certified unique points to identify the rest
        val p1 = certifiedPairs[0]; val p2 = certifiedPairs[1]
        val results = dashLandmarks.map { dashMark ->
            // If already certified, just copy the ID
            val exactMatch = certifiedPairs.find { it.first === dashMark }
            if (exactMatch != null) return@map dashMark.copy(instanceId = exactMatch.second.instanceId)

            val refCandidates = refValid.filter { it.text == dashMark.text }
            if (refCandidates.isEmpty()) return@map dashMark

            var matchedId = 0
            val qD1C = dist(p1.first, dashMark); val qD2C = dist(p2.first, dashMark); val qD12 = dist(p1.first, p2.first)

            for (cand in refCandidates) {
                val rD1C = dist(p1.second, cand); val rD2C = dist(p2.second, cand); val rD12 = dist(p1.second, p2.second)
                val ratio1 = (qD1C / rD1C) / (qD12 / rD12)
                val ratio2 = (qD2C / rD2C) / (qD12 / rD12)
                if (abs(ratio1 - 1.0) < 0.05 && abs(ratio2 - 1.0) < 0.05) {
                    matchedId = cand.instanceId
                    break
                }
            }
            if (matchedId > 0) dashMark.copy(instanceId = matchedId) else dashMark
        }

        return results
    }

    fun anchorAlign(
        refBmp: Bitmap,
        queryBmp: Bitmap,
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle,
        useMono: Boolean = false
    ): AnchorResult {
        val t0 = System.currentTimeMillis()
        val targetY = if (vehicle.odometerCropTop != null && vehicle.odometerCropBottom != null) {
            (vehicle.odometerCropTop!! + vehicle.odometerCropBottom!!) / 2.0f
        } else 0.5f

        // Phase 71: Disambiguate dash landmarks before matching
        val disambiguatedDash = disambiguateLandmarks(queryLandmarks, refLandmarks)
        
        val allCandidates = mutableListOf<AnchorCandidate>()

        // Simplified 1:1 Matching: Since landmarks are now unique-id'd, 
        // we can just pair them up directly without O(N^3) search.
        // Mandate: Only use landmarks with width > 0 (excludes manual positionless anchors)
        val pairs = mutableListOf<Pair<TextBlock, TextBlock>>()
        refLandmarks.filter { it.boundingBox.width() > 0 }.forEach { refMark ->
            val dashMark = disambiguatedDash.find { 
                it.text == refMark.text && 
                it.instanceId == refMark.instanceId && 
                it.boundingBox.width() > 0 
            }
            if (dashMark != null) {
                pairs.add(refMark to dashMark)
            }
        }

        // Generate Candidates from every pair of unique points
        if (pairs.size >= 2) {
            for (i in pairs.indices) {
                for (j in i + 1 until pairs.size) {
                    val r1 = pairs[i].first
                    val r2 = pairs[j].first
                    val q1 = pairs[i].second
                    val q2 = pairs[j].second

                    val r1cx = r1.boundingBox.centerX().toFloat()
                    val r1cy = r1.boundingBox.centerY().toFloat()
                    val r2cx = r2.boundingBox.centerX().toFloat()
                    val r2cy = r2.boundingBox.centerY().toFloat()
                    val q1cx = q1.boundingBox.centerX().toFloat()
                    val q1cy = q1.boundingBox.centerY().toFloat()
                    val q2cx = q2.boundingBox.centerX().toFloat()
                    val q2cy = q2.boundingBox.centerY().toFloat()

                    val refDist = sqrt((r1cx - r2cx).toDouble().pow(2.0) + (r1cy - r2cy).toDouble().pow(2.0))
                    val queDist = sqrt((q1cx - q2cx).toDouble().pow(2.0) + (q1cy - q2cy).toDouble().pow(2.0))

                    if (queDist > 0) {
                        val s = (refDist / queDist).toFloat()
                        val rAngle = Math.atan2((r2cy - r1cy).toDouble(), (r2cx - r1cx).toDouble())
                        val qAngle = Math.atan2((q2cy - q1cy).toDouble(), (q2cx - q1cx).toDouble())
                        val rot = Math.toDegrees(rAngle - qAngle).toFloat()

                        if (kotlin.math.abs(rot) > 4.0f) continue
                        val tx = r1cx - (s * q1cx)
                        val ty = r1cy - (s * q1cy)
                        val cyRef = (r1cy + r2cy) / 2.0f

                        val label1 = if (r1.instanceId > 0) "${r1.text}-${r1.instanceId}" else r1.text
                        val label2 = if (r2.instanceId > 0) "${r2.text}-${r2.instanceId}" else r2.text
                        allCandidates.add(AnchorCandidate("Phase 71 Unique", listOf(label1, label2), s, rot, tx, ty, refDist, "S=%.3f, tx=%.1f, ty=%.1f".format(s, tx, ty), cyRef, r1cy, r2cy))
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
            val outBmp = Bitmap.createBitmap(refBmp.width, refBmp.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(outBmp)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.drawBitmap(queryBmp, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            AnchorResult(true, outBmp, 0.5f, System.currentTimeMillis() - t0, metadata, "Consensus (%d/%d) [B:%d]: S=%.3f, tx=%.1f, ty=%.1f".format(bestGroup.size, allCandidates.size, bracketedCount, finalScale, finalTx, finalTy))
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
