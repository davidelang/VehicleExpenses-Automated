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
import org.opencv.android.Utils
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

    /**
     * Unified Anchor Alignment Flow.
     * Geometrically equivalent to legacy anchorAlign but executes natively via OpenCV warpAffine.
     * Supports both BufferSet (Native Path) and Bitmap (Standard Path).
     */
    suspend fun anchorAlign(
        input: Any,
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
        val icrsTargetY = if (vehicle.odometerCropTop != null && vehicle.odometerCropBottom != null) {
            val center = (vehicle.odometerCropTop!! + vehicle.odometerCropBottom!!) / 2.0f
            if (vehicle.isIcrs) center else IcrsMath.legacyAnisotropicToIcrs(0.5f, center, refW, refH).y
        } else 0f
        
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
                    
                    val r1Icrs = IcrsMath.pixelToIcrs(r1.boundingBox.centerX().toFloat(), r1.boundingBox.centerY().toFloat(), refW, refH)
                    val r2Icrs = IcrsMath.pixelToIcrs(r2.boundingBox.centerX().toFloat(), r2.boundingBox.centerY().toFloat(), refW, refH)
                    val q1Icrs = IcrsMath.pixelToIcrs(q1.boundingBox.centerX().toFloat(), q1.boundingBox.centerY().toFloat(), queW, queH)
                    val q2Icrs = IcrsMath.pixelToIcrs(q2.boundingBox.centerX().toFloat(), q2.boundingBox.centerY().toFloat(), queW, queH)
                    
                    val refDist = Math.sqrt((r1Icrs.x - r2Icrs.x).toDouble().pow(2.0) + (r1Icrs.y - r2Icrs.y).toDouble().pow(2.0))
                    val queDist = Math.sqrt((q1Icrs.x - q2Icrs.x).toDouble().pow(2.0) + (q1Icrs.y - q2Icrs.y).toDouble().pow(2.0))
                    
                    if (queDist > 0) {
                        val s = (refDist / queDist).toFloat()
                        val rAngle = Math.atan2((r2Icrs.y - r1Icrs.y).toDouble(), (r2Icrs.x - r1Icrs.x).toDouble())
                        val qAngle = Math.atan2((q2Icrs.y - q1Icrs.y).toDouble(), (q2Icrs.x - q1Icrs.x).toDouble())
                        val rot = Math.toDegrees(rAngle - qAngle).toFloat()
                        
                        if (kotlin.math.abs(rot) > 4.0f) continue
                        val tx = r1Icrs.x - (s * q1Icrs.x)
                        val ty = r1Icrs.y - (s * q1Icrs.y)

                        val debugMsg = "S=%.3f, R=%.1f, tx_icrs=%.3f, ty_icrs=%.3f".format(s, rot, tx, ty)
                        allCandidates.add(AnchorCandidate("Deterministic", listOf(r1.text, r2.text), s, rot, tx, ty, refDist, debugMsg, (r1Icrs.y + r2Icrs.y)/2f, r1Icrs.y, r2Icrs.y))
                    }
                }
            }
        } 

        if (allCandidates.isEmpty()) return AnchorResult(false, message = "No valid anchors.", timeMs = System.currentTimeMillis() - t0)

        // Consensus math
        var bestGroup = mutableListOf<AnchorCandidate>()
        var maxSupport = -1
        val threshold = 0.05f
        for (c1 in allCandidates) {
            val supportGroup = mutableListOf<AnchorCandidate>()
            for (c2 in allCandidates) {
                val ds = kotlin.math.abs(c1.scale - c2.scale) / c1.scale
                val dtx = kotlin.math.abs(c1.tx - c2.tx)
                val dty = kotlin.math.abs(c1.ty - c2.ty)
                if (ds < threshold && dtx < threshold && dty < threshold) supportGroup.add(c2)
            }
            if (supportGroup.size > maxSupport) { maxSupport = supportGroup.size; bestGroup = supportGroup }
        }

        val finalScale: Float; val finalTx: Float; val finalTy: Float; var bracketedCount = 0
        if (bestGroup.isNotEmpty()) {
            var sumScale = 0.0; var sumTx = 0.0; var sumTy = 0.0; var totalW = 0.0
            for (c in bestGroup) {
                val isBracketed = (c.y1Ref - icrsTargetY) * (c.y2Ref - icrsTargetY) < 0
                val bracketBonus = if (isBracketed) { bracketedCount++; 5.0 } else 1.0
                val vDist = kotlin.math.abs(c.cyRef - icrsTargetY)
                val w = (c.distance * bracketBonus) / (vDist + 0.05)
                sumScale += c.scale * w; sumTx += c.tx * w; sumTy += c.ty * w; totalW += w
            }
            finalScale = if (totalW > 0) (sumScale / totalW).toFloat() else allCandidates.map { it.scale }.median()
            finalTx = if (totalW > 0) (sumTx / totalW).toFloat() else allCandidates.map { it.tx }.median()
            finalTy = if (totalW > 0) (sumTy / totalW).toFloat() else allCandidates.map { it.ty }.median()
        } else {
            finalScale = allCandidates.map { it.scale }.median(); finalTx = allCandidates.map { it.tx }.median(); finalTy = allCandidates.map { it.ty }.median()
        }

        val sqS = minOf(queW, queH).toFloat()
        val cxq = queW / 2f
        val cyq = queH / 2f
        
        val matrixTX = cxq * (1f - finalScale) + (finalTx * sqS)
        val matrixTY = cyq * (1f - finalScale) + (finalTy * sqS)

        val metadata = mapOf(
            "Consensus" to "S=%.3f, tx_icrs=%.3f, ty_icrs=%.3f (Support: %d/%d)".format(finalScale, finalTx, finalTy, bestGroup.size, allCandidates.size),
            "matrix_tx" to matrixTX.toString(),
            "matrix_ty" to matrixTY.toString(),
            "raw_scale" to finalScale.toString(),
            "raw_tx" to matrixTX.toString(),
            "raw_ty" to matrixTY.toString()
        )

        return try {
            val src: Mat
            val dst: Mat
            val useBufferSet = input is BufferSet

            if (useBufferSet) {
                src = (input as BufferSet).p.mat
                dst = input.s.mat
            } else {
                src = Mat()
                Utils.bitmapToMat(input as Bitmap, src)
                dst = Mat(src.size(), src.type(), Scalar(0.0, 0.0, 0.0, 255.0))
            }

            val matrixLocal = android.graphics.Matrix()
            val values = floatArrayOf(
                finalScale, 0f, matrixTX,
                0f, finalScale, matrixTY,
                0f, 0f, 1f
            )
            matrixLocal.setValues(values)
            val matrixValues = FloatArray(9)
            matrixLocal.getValues(matrixValues)

            val warpMat = Mat(2, 3, CvType.CV_64F)
            warpMat.put(0, 0, matrixValues[0].toDouble(), matrixValues[1].toDouble(), matrixValues[2].toDouble())
            warpMat.put(1, 0, matrixValues[3].toDouble(), matrixValues[4].toDouble(), matrixValues[5].toDouble())
            
            Imgproc.warpAffine(src, dst, warpMat, src.size(), Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT, Scalar(0.0, 0.0, 0.0, 255.0))
            warpMat.release()

            if (useBufferSet) {
                (input as BufferSet).flip()
                // Sync to scratchBmp for report visualization
                NativeImageUtils.syncMatToArgb(input.p.mat, scratchBmp)
            } else {
                Utils.matToBitmap(dst, input as Bitmap)
                src.release(); dst.release()
            }
            
            AnchorResult(true, if (useBufferSet) scratchBmp else input as Bitmap, 0.5f, System.currentTimeMillis() - t0, metadata, "Consensus (%d/%d)".format(bestGroup.size, allCandidates.size))
        } catch (e: Exception) {
            AnchorResult(false, message = "Warp failed: ${e.message}", timeMs = System.currentTimeMillis() - t0, metadata = metadata)
        }
    }

    private fun dist(a: TextBlock, b: TextBlock): Double {
        val dx = (a.boundingBox.centerX() - b.boundingBox.centerX()).toDouble()
        val dy = (a.boundingBox.centerY() - b.boundingBox.centerY()).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    private fun List<Float>.median(): Float {
        if (isEmpty()) return 0f
        val sorted = sorted()
        return if (size % 2 == 0) (sorted[size / 2 - 1] + sorted[size / 2]) / 2f else sorted[size / 2]
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

    fun performTier1Veto(queryLandmarks: List<TextBlock>, allVehicles: List<Vehicle>, engineName: String): Map<Int, VetoResult> {
        val queryWordsList = queryLandmarks.map { it.text.trim() }.sorted()
        val queryWordsSet = queryWordsList.toSet()
        val vehicleLandmarks = allVehicles.associate { it.id to getLandmarksFromJson(it.landmarkTextBlocksJson, engineName) }
        
        val initialResults = allVehicles.associate { currentVehicle ->
            val myWords = vehicleLandmarks[currentVehicle.id] ?: emptySet()
            val otherWordsPool = vehicleLandmarks.filter { it.key != currentVehicle.id }.values.flatten().toSet()
            val vetoPool = otherWordsPool - myWords
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

        if (initialResults.values.all { it.isVetoed }) {
            val triggerCounts = initialResults.mapValues { (_, res) -> if (res.reasonWord.isEmpty()) 0 else res.reasonWord.split(", ").size }
            val vehiclesWithOneTrigger = triggerCounts.filter { it.value == 1 }.keys.toList()
            val otherVehiclesHaveManyTriggers = triggerCounts.filter { it.key !in vehiclesWithOneTrigger }.all { it.value >= 3 }
            if (vehiclesWithOneTrigger.size == 1 && otherVehiclesHaveManyTriggers && allVehicles.size > 1) {
                val rescuedId = vehiclesWithOneTrigger[0]
                val rescuedResult = initialResults[rescuedId]!!
                val newResults = initialResults.toMutableMap()
                newResults[rescuedId] = rescuedResult.copy(isVetoed = false, reasonWord = "[RESCUED 1 vs 3+] Was: ${rescuedResult.reasonWord}")
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
}
