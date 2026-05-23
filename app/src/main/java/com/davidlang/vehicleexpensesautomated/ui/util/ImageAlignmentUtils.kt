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
         * 3-Pass Architecture for Landmark Disambiguation:
         * Instance ID Documentation:
         * -2: Manual/Invalid (Do not use for geometric math)
         * -1: Unmapped/Potential (Needs triangulation resolution)
         *  0: Globally Unique Anchor (Appears exactly once in vehicle manifest)
         * 1+: Duplicate Instance (One of several detected occurrences)
         */
        val refValid = refLandmarks.filter { it.boundingBox.width() > 0 && it.instanceId != -2 }
        if (dashValid.isEmpty() || refValid.isEmpty()) return dashLandmarks

        val refTexts = refValid.map { it.text }.toSet()
        val refUniqueMap = refValid.filter { it.instanceId == 0 }.associateBy { it.text }
        
        // Pass 1: Classification
        val results = dashValid.map { dashMark ->
            val isPotential = dashMark.text in refTexts
            when {
                !isPotential -> dashMark.copy(instanceId = -2)
                refUniqueMap.containsKey(dashMark.text) -> dashMark.copy(instanceId = 0)
                else -> dashMark.copy(instanceId = -1)
            }
        }.toMutableList()

        // Pass 2: Veto/Collision Check (Unique landmarks that collide are vetoed)
        val uniqueCounts = results.filter { it.instanceId == 0 }.groupBy { it.text }.mapValues { it.value.size }
        for (i in results.indices) {
            val text = results[i].text
            if (results[i].instanceId == 0 && (uniqueCounts[text] ?: 0) > 1) {
                results[i] = results[i].copy(instanceId = -2)
            }
        }
        
        Log.d("DISAMB_TRACE", "START (3-Pass): Dash=${dashValid.size}, Ref=${refValid.size} | Unique=${results.count { it.instanceId == 0 }}, Potential=${results.count { it.instanceId == -1 }}")

        // Pass 3: Triangulation Resolution
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
        vehicle: Vehicle,
        refW: Int = 4000,
        refH: Int = 3072,
        queW: Int = 4000,
        queH: Int = 3072,
        scratchBmp: Bitmap
    ): AnchorResult {
        val t0 = System.currentTimeMillis()

        if (bmp.width != scratchBmp.width || bmp.height != scratchBmp.height) {
            throw IllegalArgumentException("Dimension mismatch: bmp=${bmp.width}x${bmp.height}, scratch=${scratchBmp.width}x${scratchBmp.height}")
        }
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
                    
                    // Use raw pixel coordinates (absolute) for RANSAC consensus math
                    val r1px = r1.boundingBox.centerX().toFloat()
                    val r1py = r1.boundingBox.centerY().toFloat()
                    val r2px = r2.boundingBox.centerX().toFloat()
                    val r2py = r2.boundingBox.centerY().toFloat()
                    
                    val q1px = q1.boundingBox.centerX().toFloat()
                    val q1py = q1.boundingBox.centerY().toFloat()
                    val q2px = q2.boundingBox.centerX().toFloat()
                    val q2py = q2.boundingBox.centerY().toFloat()
                    
                    val refDist = sqrt((r1px - r2px).toDouble().pow(2.0) + (r1py - r2py).toDouble().pow(2.0))
                    val queDist = sqrt((q1px - q2px).toDouble().pow(2.0) + (q1py - q2py).toDouble().pow(2.0))
                    
                    if (queDist > 0) {
                        val s = (refDist / queDist).toFloat()
                        
                        // Calculate Rotation
                        val rAngle = Math.atan2((r2py - r1py).toDouble(), (r2px - r1px).toDouble())
                        val qAngle = Math.atan2((q2py - q1py).toDouble(), (q2px - q1px).toDouble())
                        val rot = Math.toDegrees(rAngle - qAngle).toFloat()
                        
                        if (kotlin.math.abs(rot) > 4.0f) continue
                        val tx = r1px - (s * q1px)
                        val ty = r1py - (s * q1py)
                        
                        // Forensic data: include absolute pixel coordinates and candidates
                        val debugMsg = "S=%.3f, R=%.1f, tx=%.1f, ty=%.1f | P1(%.1f,%.1f) P2(%.1f,%.1f)".format(s, rot, tx, ty, r1px, r1py, r2px, r2py)
                        allCandidates.add(AnchorCandidate("Deterministic", listOf(r1.text, r2.text), s, rot, tx, ty, refDist.toFloat(), debugMsg, 0f, r1py, r2py))
                    }
                }
            }
        } 

        if (allCandidates.isEmpty()) return AnchorResult(false, message = "No valid anchor sets after disambiguation.", timeMs = System.currentTimeMillis() - t0)

        // RANSAC-Lite Consensus (Phase 64)
        // Find the candidate group with the most mutual agreement
        var bestGroup = mutableListOf<AnchorCandidate>()
        var maxSupport = -1
        
        // Agreement threshold: 5% scale, 5% of short edge in translation
        val pixelThreshold = (minOf(bmp.width, bmp.height) * 0.05f)

        for (c1 in allCandidates) {
            val supportGroup = mutableListOf<AnchorCandidate>()
            for (c2 in allCandidates) {
                val ds = kotlin.math.abs(c1.scale - c2.scale) / c1.scale
                val dtx = kotlin.math.abs(c1.tx - c2.tx)
                val dty = kotlin.math.abs(c1.ty - c2.ty)
                
                if (ds < 0.05f && dtx < pixelThreshold && dty < pixelThreshold) {
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
        
        // Corrected Origin: Convert ICRS translation to Pixel Space (Top-Left origin)
        val pixelTranslation = IcrsMath.icrsToPixel(finalTx, finalTy, bmp.width, bmp.height)
        val originOffset = IcrsMath.icrsToPixel(0f, 0f, bmp.width, bmp.height)
        val pixTx = pixelTranslation.x - originOffset.x
        val pixTy = pixelTranslation.y - originOffset.y
        
        matrix.postTranslate(pixTx, pixTy)

        val metadata = mapOf(
            "Candidates" to allCandidates.sortedByDescending { it.distance }.take(5).mapIndexed { i, c ->
                "#${i+1}: ${c.strategy} [${c.anchorsUsed.joinToString(", ")}] -> ${c.message}"
            }.joinToString("\n"),
            "Consensus" to "S=%.3f, tx=%.1f, ty=%.1f (Support: %d/%d, Bracketing: %d)".format(finalScale, finalTx * bmp.width, finalTy * bmp.height, bestGroup.size, allCandidates.size, bracketedCount),
            "raw_scale" to finalScale.toString(),
            "raw_tx" to (finalTx * bmp.width).toString(),
            "raw_ty" to (finalTy * bmp.height).toString()
        )

        return try {
            // Phase 115: In-Place Morphing using passed scratch buffer.
            val canvas = android.graphics.Canvas(scratchBmp)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.drawBitmap(bmp, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            
            // Draw scratch back to the original passed buffer (bmp)
            val originalCanvas = android.graphics.Canvas(bmp)
            originalCanvas.drawBitmap(scratchBmp, 0f, 0f, null)
            
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
                Log.e("ImageAlignment", "Manifest missing data for engine: $engineName")
                return emptySet()
            }

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

    /**
     * Phase 115: Native Alignment Flow for BufferSet.
     * Geometrically equivalent to anchorAlign but executes natively via OpenCV warpAffine.
     */
    suspend fun anchorAlignNative(
        bufferSet: BufferSet,
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle,
        refW: Int = 4000,
        refH: Int = 3072,
        queW: Int = 4000,
        queH: Int = 3072,
        scratchBmp: Bitmap
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
                    
                    val r1nx = if (r1.boundingBox.width() > 1) r1.boundingBox.centerX().toFloat() / refW else r1.boundingBox.centerX().toFloat()
                    val r1ny = if (r1.boundingBox.width() > 1) r1.boundingBox.centerY().toFloat() / refH else r1.boundingBox.centerY().toFloat()
                    val r2nx = if (r2.boundingBox.width() > 1) r2.boundingBox.centerX().toFloat() / refW else r2.boundingBox.centerX().toFloat()
                    val r2ny = if (r2.boundingBox.width() > 1) r2.boundingBox.centerY().toFloat() / refH else r2.boundingBox.centerY().toFloat()
                    
                    val q1nx = if (q1.boundingBox.width() > 1) q1.boundingBox.centerX().toFloat() / queW else q1.boundingBox.centerX().toFloat()
                    val q1ny = if (q1.boundingBox.width() > 1) q1.boundingBox.centerY().toFloat() / queH else q1.boundingBox.centerY().toFloat()
                    val q2nx = if (q2.boundingBox.width() > 1) q2.boundingBox.centerX().toFloat() / queW else q2.boundingBox.centerX().toFloat()
                    val q2ny = if (q2.boundingBox.width() > 1) q2.boundingBox.centerY().toFloat() / queH else q2.boundingBox.centerY().toFloat()
                    
                    val refDist = Math.sqrt((r1nx - r2nx).toDouble().pow(2.0) + (r1ny - r2ny).toDouble().pow(2.0))
                    val queDist = Math.sqrt((q1nx - q2nx).toDouble().pow(2.0) + (q1ny - q2ny).toDouble().pow(2.0))
                    
                    if (queDist > 0) {
                        val s = (refDist / queDist).toFloat()
                        val rAngle = Math.atan2((r2ny - r1ny).toDouble(), (r2nx - r1nx).toDouble())
                        val qAngle = Math.atan2((q2ny - q1ny).toDouble(), (q2nx - q1nx).toDouble())
                        val rot = Math.toDegrees(rAngle - qAngle).toFloat()
                        
                        if (kotlin.math.abs(rot) > 4.0f) continue
                        val tx = r1nx - (s * q1nx)
                        val ty = r1ny - (s * q1ny)
                        val cyRef = (r1ny + r2ny) / 2.0f

                        allCandidates.add(AnchorCandidate("Deterministic", listOf(r1.text, r2.text), s, rot, tx, ty, refDist, "S=%.3f, R=%.1f (Filter), tx=%.3f, ty=%.3f".format(s, rot, tx, ty), cyRef, r1ny, r2ny))
                    }
                }
            }
        } 

        if (allCandidates.isEmpty()) return AnchorResult(false, message = "No valid anchors.", timeMs = System.currentTimeMillis() - t0)

        // Consensus math (Identical to ARGB flow)
        var bestGroup = mutableListOf<AnchorCandidate>()
        var maxSupport = -1
        for (c1 in allCandidates) {
            val supportGroup = mutableListOf<AnchorCandidate>()
            for (c2 in allCandidates) {
                val ds = kotlin.math.abs(c1.scale - c2.scale) / c1.scale
                val dtx = kotlin.math.abs(c1.tx - c2.tx)
                val dty = kotlin.math.abs(c1.ty - c2.ty)
                if (ds < 0.05f && dtx < 0.05f && dty < 0.05f) supportGroup.add(c2)
            }
            if (supportGroup.size > maxSupport) { maxSupport = supportGroup.size; bestGroup = supportGroup }
        }

        val finalScale: Float; val finalTx: Float; val finalTy: Float; var bracketedCount = 0
        if (bestGroup.isNotEmpty()) {
            var sumScale = 0.0; var sumTx = 0.0; var sumTy = 0.0; var totalW = 0.0
            for (c in bestGroup) {
                val isBracketed = (c.y1Ref - targetY) * (c.y2Ref - targetY) < 0
                val bracketBonus = if (isBracketed) { bracketedCount++; 5.0 } else 1.0
                val vDist = kotlin.math.abs(c.cyRef - targetY)
                val w = (c.distance * bracketBonus) / (vDist + 0.05)
                sumScale += c.scale * w; sumTx += c.tx * w; sumTy += c.ty * w; totalW += w
            }
            finalScale = if (totalW > 0) (sumScale / totalW).toFloat() else allCandidates.map { it.scale }.median()
            finalTx = if (totalW > 0) (sumTx / totalW).toFloat() else allCandidates.map { it.tx }.median()
            finalTy = if (totalW > 0) (sumTy / totalW).toFloat() else allCandidates.map { it.ty }.median()
        } else {
            finalScale = allCandidates.map { it.scale }.median(); finalTx = allCandidates.map { it.tx }.median(); finalTy = allCandidates.map { it.ty }.median()
        }

        val metadata = mapOf(
            "Consensus" to "S=%.3f, tx=%.1f, ty=%.1f (Support: %d/%d)".format(finalScale, finalTx * queW, finalTy * queH, bestGroup.size, allCandidates.size),
            "raw_scale" to finalScale.toString(), "raw_tx" to (finalTx * queW).toString(), "raw_ty" to (finalTy * queH).toString()
        )

        return try {
            // Phase 115: Native Morphing using Imgproc.warpAffine
            val src = bufferSet.p.mat
            val dst = bufferSet.s.mat

            // Use Android Matrix as mathematical calculator for 100% parity
            val m = android.graphics.Matrix()
            m.postScale(finalScale, finalScale)
            m.postTranslate(finalTx * queW, finalTy * queH)
            val values = FloatArray(9)
            m.getValues(values)

            val warpMat = Mat(2, 3, CvType.CV_64F)
            warpMat.put(0, 0, values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
            warpMat.put(1, 0, values[3].toDouble(), values[4].toDouble(), values[5].toDouble())
            
            Imgproc.warpAffine(src, dst, warpMat, src.size(), Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT, Scalar(0.0))
            bufferSet.flip()
            warpMat.release()
            
            // Sync to scratchBmp for report visualization
            NativeImageUtils.syncMatToArgb(bufferSet.p.mat, scratchBmp)
            
            AnchorResult(true, scratchBmp, 0.5f, System.currentTimeMillis() - t0, metadata, "Native Consensus (%d/%d)".format(bestGroup.size, allCandidates.size))
        } catch (e: Exception) {
            AnchorResult(false, message = "Native warp failed: ${e.message}", timeMs = System.currentTimeMillis() - t0, metadata = metadata)
        }
    }
}
