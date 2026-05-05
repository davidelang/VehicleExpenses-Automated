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
        
        /**
         * Phase 109 Architecture: Manual Landmarks (instanceId == -2)
         * 1. Uniqueness: Presence of a manual landmark makes other visible instances of the same word non-unique.
         * 2. Veto system: Manual landmarks like "FORD" are used by performTier1Veto to disqualify incorrect vehicles.
         * 3. Alignment: Manual landmarks are filtered out here because they lack coordinates for geometric math.
         */
        val refValid = refLandmarks.filter { it.boundingBox.width() > 0 && it.instanceId != -2 }
        if (dashValid.isEmpty() || refValid.isEmpty()) return dashLandmarks

        val refTexts = refValid.map { it.text }.toSet()
        val refUniqueMap = refValid.filter { it.instanceId == 0 }.associateBy { it.text }
        
        Log.d("DISAMB_TRACE", "Ref Landmarks with instanceId=0: ${refUniqueMap.keys}")
        
        // Step 1: Initialization & Unique Match
        val results = dashValid.map { dashMark ->
            val isPotential = dashMark.text in refTexts
            val uniqueRef = refUniqueMap[dashMark.text]
            Log.d("DISAMB_TRACE", "  Init DashMark: '${dashMark.text}' | isPotential=$isPotential, uniqueRefInstance=${uniqueRef?.instanceId ?: "null"}")
            
            if (!isPotential) {
                dashMark.copy(instanceId = -2)
            } else if (uniqueRef != null) {
                dashMark.copy(instanceId = 0)
            } else {
                dashMark.copy(instanceId = -1)
            }
        }.toMutableList()

        // Step 2: Unique Sanity Check (Only invalidate if multiple dash marks claim to be unique instance 0)
        val uniqueCounts = results.filter { it.instanceId == 0 }.groupBy { it.text }.mapValues { it.value.size }
        for (i in results.indices) {
            val text = results[i].text
            if (results[i].instanceId == 0 && (uniqueCounts[text] ?: 0) > 1) {
                results[i] = results[i].copy(instanceId = -2)
            }
        }

        val potentialCount = results.count { it.instanceId == -1 }
        val commonUniqueCount = results.count { it.instanceId == 0 }
        Log.d("DISAMB_TRACE", "START: Dash=${dashValid.size}, Ref=${refValid.size}, RefUnique= ${refUniqueMap.size} | CommonUnique=$commonUniqueCount, DashPotential=$potentialCount")

        // Pass 2: If < 2 anchors, find seed triangle
        if (results.count { it.instanceId >= 0 } < 2) {
            Log.d("DISAMB_TRACE", "  Pass 2: Insufficient anchors (${results.count { it.instanceId >= 0 }}). Searching for seed triangle...")
            val dashCounts = dashValid.groupBy { it.text }.mapValues { it.value.size }
            val refCounts = refValid.groupBy { it.text }.mapValues { it.value.size }
            val commonTexts = dashValid.map { it.text }.toSet().intersect(refTexts)

            val seedPool = commonTexts.map { text ->
                val dCount = dashCounts[text] ?: 0
                val rCount = refCounts[text] ?: 0
                val tier = if (dCount == 1 && rCount == 1) 1 else if (dCount == 1) 2 else 3
                Triple(text, tier, dCount)
            }.sortedWith(compareBy({ it.second }, { it.third }))

            var triCount = 0
            outer@for (i in seedPool.indices) {
                for (j in i + 1 until seedPool.size) {
                    for (k in j + 1 until seedPool.size) {
                        val d1s = results.filter { it.text == seedPool[i].first }; val d2s = results.filter { it.text == seedPool[j].first }; val d3s = results.filter { it.text == seedPool[k].first }
                        val r1s = refValid.filter { it.text == seedPool[i].first }; val r2s = refValid.filter { it.text == seedPool[j].first }; val r3s = refValid.filter { it.text == seedPool[k].first }
                        
                        for (d1 in d1s) for (d2 in d2s) for (d3 in d3s) {
                            val d12 = dist(d1, d2); val d23 = dist(d2, d3); val d31 = dist(d3, d1)
                            val dPerim = d12 + d23 + d31
                            if (dPerim == 0.0) continue
                            
                            if (triCount < 5) {
                                for (r1 in r1s) for (r2 in r2s) for (r3 in r3s) {
                                    val r12 = dist(r1, r2); val r23 = dist(r2, r3); val r31 = dist(r3, r1)
                                    val rPerim = r12 + r23 + r31
                                    if (rPerim == 0.0) continue
                                    Log.d("DISAMB_TRI", "    Trying Dash Triangle: matching ['${d1.text}'-${r1.instanceId}, '${d2.text}'-${r2.instanceId}, '${d3.text}'-${r3.instanceId}] | Prop: %.2f, %.2f, %.2f".format(d12/dPerim, d23/dPerim, d31/dPerim))
                                    triCount++
                                    if (triCount >= 5) break
                                }
                            }
                            
                            for (r1 in r1s) for (r2 in r2s) for (r3 in r3s) {
                                val r12 = dist(r1, r2); val r23 = dist(r2, r3); val r31 = dist(r3, r1)
                                val rPerim = r12 + r23 + r31
                                if (rPerim == 0.0) continue
                                
                                val dev1 = abs((d12/dPerim) / (r12/rPerim) - 1.0)
                                val dev2 = abs((d23/dPerim) / (r23/rPerim) - 1.0)
                                val dev3 = abs((d31/dPerim) / (r31/rPerim) - 1.0)
                                
                                if (dev1 < 0.05 && dev2 < 0.05 && dev3 < 0.05) {
                                    results[results.indexOf(d1)] = d1.copy(instanceId = r1.instanceId)
                                    results[results.indexOf(d2)] = d2.copy(instanceId = r2.instanceId)
                                    results[results.indexOf(d3)] = d3.copy(instanceId = r3.instanceId)
                                    Log.d("DISAMB_TRACE", "  Pass 2 [Seed]: Triangle found ('${d1.text}'-${r1.instanceId}, '${d2.text}'-${r2.instanceId}, '${d3.text}'-${r3.instanceId}) | Devs: %.3f, %.3f, %.3f".format(dev1, dev2, dev3))
                                    break@outer
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pass 3: Bootstrapping with Optimal Baseline
        val confirmed = results.filter { it.instanceId >= 0 }
        if (confirmed.size >= 2) {
            // Find most distant baseline pair
            var bestP1: TextBlock? = null
            var bestP2: TextBlock? = null
            var maxDist = -1.0
            
            for (i in confirmed.indices) {
                for (j in i + 1 until confirmed.size) {
                    val d = dist(confirmed[i], confirmed[j])
                    if (d > maxDist) {
                        maxDist = d
                        bestP1 = confirmed[i]
                        bestP2 = confirmed[j]
                    }
                }
            }
            
            if (bestP1 != null && bestP2 != null) {
                val p1 = bestP1!!; val p2 = bestP2!!
                val rP1 = refValid.find { it.text == p1.text && it.instanceId == p1.instanceId }!!
                val rP2 = refValid.find { it.text == p2.text && it.instanceId == p2.instanceId }!!
                val d12 = dist(p1, p2)
                val r12 = dist(rP1, rP2)

                Log.d("DISAMB_TRACE", "  Pass 3: Bootstrapping from baseline ['${p1.text}'-${p1.instanceId}, '${p2.text}'-${p2.instanceId}]")

                for (idx in results.indices) {
                    if (results[idx].instanceId != -1) continue
                    val dashMark = results[idx]
                    val d1c = dist(p1, dashMark); val d2c = dist(p2, dashMark)
                    val dPerim = d12 + d1c + d2c
                    if (dPerim == 0.0) continue
                    
                    val refCandidates = refValid.filter { it.text == dashMark.text }
                    for (cand in refCandidates) {
                        val r1c = dist(rP1, cand); val r2c = dist(rP2, cand)
                        val rPerim = r12 + r1c + r2c
                        if (rPerim == 0.0) continue
                        
                        val dev1 = abs((d12/dPerim) / (r12/rPerim) - 1.0)
                        val dev2 = abs((d1c/dPerim) / (r1c/rPerim) - 1.0)
                        val dev3 = abs((d2c/dPerim) / (r2c/rPerim) - 1.0)
                        
                        if (dev1 < 0.05 && dev2 < 0.05 && dev3 < 0.05) {
                            results[idx] = dashMark.copy(instanceId = cand.instanceId)
                            Log.d("DISAMB_TRACE", "    -> Landmark #$idx Matched: '${dashMark.text}'-${cand.instanceId} | Devs: %.3f, %.3f, %.3f".format(dev1, dev2, dev3))
                            break
                        } else {
                            Log.d("DISAMB_TRI", "    Trying Dash Landmark #$idx: matching '${dashMark.text}'-${cand.instanceId} | Prop: %.2f, %.2f".format(d1c/dPerim, d2c/dPerim))
                        }
                    }
                }
            }
        }
        
        Log.d("DISAMB_TRACE", "FINISH: Tagged ${results.count { it.instanceId >= 0 }}/${dashValid.size} landmarks")
        return results
    }

    fun anchorAlign(
        bmp: Bitmap,
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle
    ): AnchorResult {
        val t0 = System.currentTimeMillis()
        val allCandidates = mutableListOf<AnchorCandidate>()
        val targetY = if (vehicle.odometerCropTop != null && vehicle.odometerCropBottom != null) {
            (vehicle.odometerCropTop!! + vehicle.odometerCropBottom!!) / 2.0f
        } else 0.5f
        
        // 1. Filter and Match Confirmed Landmarks
        val confirmedPairs = queryLandmarks.filter { it.instanceId >= 0 && it.boundingBox.width() > 0 }
            .mapNotNull { queMark ->
                val refMark = refLandmarks.find { it.text == queMark.text && it.instanceId == queMark.instanceId && it.boundingBox.width() > 0 }
                if (refMark != null) refMark to queMark else null
            }

        // 2. Generate Alignment Candidates from Pairs of Confirmed Anchors
        if (confirmedPairs.size >= 2) {
            for (i in confirmedPairs.indices) {
                for (j in i + 1 until confirmedPairs.size) {
                    val r1 = confirmedPairs[i].first
                    val r2 = confirmedPairs[j].first
                    val q1 = confirmedPairs[i].second
                    val q2 = confirmedPairs[j].second
                    
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
                        
                        // Calculate Rotation (Phase 44)
                        val rAngle = Math.atan2((r2cy - r1cy).toDouble(), (r2cx - r1cx).toDouble())
                        val qAngle = Math.atan2((q2cy - q1cy).toDouble(), (q2cx - q1cx).toDouble())
                        val rot = Math.toDegrees(rAngle - qAngle).toFloat()
                        
                        // Phase 53: Rotational Tolerance Filter and Zero-Rotation Warp
                        if (kotlin.math.abs(rot) > 4.0f) continue
                        val tx = r1cx - (s * q1cx)
                        val ty = r1cy - (s * q1cy)
                        val cyRef = (r1cy + r2cy) / 2.0f

                        allCandidates.add(AnchorCandidate("Deterministic", listOf(r1.text, r2.text), s, rot, tx, ty, refDist, "S=%.3f, R=%.1f (Filter), tx=%.1f, ty=%.1f".format(s, rot, tx, ty), cyRef, r1cy, r2cy))
                    }
                }
            }
        } 

        if (allCandidates.isEmpty()) return AnchorResult(false, message = "No valid anchor sets after disambiguation.", timeMs = System.currentTimeMillis() - t0)

        // RANSAC-Lite Consensus (Phase 64)
        // Find the candidate group with the most mutual agreement
        var bestGroup = mutableListOf<AnchorCandidate>()
        var maxSupport = -1

        for (c1 in allCandidates) {
            val supportGroup = mutableListOf<AnchorCandidate>()
            for (c2 in allCandidates) {
                val ds = kotlin.math.abs(c1.scale - c2.scale) / c1.scale
                val dtx = kotlin.math.abs(c1.tx - c2.tx)
                val dtxNorm = dtx / bmp.width
                val dty = kotlin.math.abs(c1.ty - c2.ty)
                val dtyNorm = dty / bmp.height
                
                // Agreement threshold: 5% scale, 0.05 normalized translation
                if (ds < 0.05f && dtxNorm < 0.05f && dtyNorm < 0.05f) {
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
            // Phase 92: In-Place Morphing. We use a temporary scratch bitmap to warp, then draw it back.
            val scratch = Bitmap.createBitmap(bmp.width, bmp.height, bmp.config ?: Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(scratch)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.drawBitmap(bmp, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            
            // Draw scratch back to the original passed buffer
            val originalCanvas = android.graphics.Canvas(bmp)
            originalCanvas.drawBitmap(scratch, 0f, 0f, null)
            scratch.recycle()
            
            AnchorResult(true, bmp, 0.5f, System.currentTimeMillis() - t0, metadata, "Consensus (%d/%d) [B:%d]: S=%.3f, tx=%.1f, ty=%.1f".format(bestGroup.size, allCandidates.size, bracketedCount, finalScale, finalTx, finalTy))
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
