package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import org.opencv.android.OpenCVLoader
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class AnchorCandidate(
    val strategy: String,
    val anchorsUsed: List<String>,
    val scale: Float,
    val rotation: Float, 
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

data class VetoResult(
    val isVetoed: Boolean,
    val reasonWord: String = "",
    val tierReached: Int = 0,
    val queryWords: List<String> = emptyList(),
    val myManifest: List<String> = emptyList(),
    val vetoPool: List<String> = emptyList()
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
        refLandmarks: List<TextBlock>,
        queryLandmarks: List<TextBlock>,
        vehicle: Vehicle,
        useMono: Boolean = false
    ): AnchorResult {
        val t0 = System.currentTimeMillis()
        val disambiguatedDash = disambiguateLandmarks(queryLandmarks, refLandmarks)
        val allCandidates = mutableListOf<AnchorCandidate>()
        val pairs = mutableListOf<Pair<TextBlock, TextBlock>>()
        refLandmarks.filter { it.boundingBox.width() > 0 }.forEach { refMark ->
            val dashMark = disambiguatedDash.find { 
                it.text == refMark.text && 
                it.instanceId == refMark.instanceId && 
                it.boundingBox.width() > 0 &&
                it.instanceId != -1
            }
            if (dashMark != null) pairs.add(refMark to dashMark)
        }


        if (pairs.size >= 2) {
            for (i in pairs.indices) {
                for (j in i + 1 until pairs.size) {
                    val r1 = pairs[i].first; val r2 = pairs[j].first; val q1 = pairs[i].second; val q2 = pairs[j].second
                    val s = (dist(r1, r2) / dist(q1, q2)).toFloat()
                    val rot = Math.toDegrees(Math.atan2((r2.boundingBox.centerY() - r1.boundingBox.centerY()).toDouble(), (r2.boundingBox.centerX() - r1.boundingBox.centerX()).toDouble()) - 
                                            Math.atan2((q2.boundingBox.centerY() - q1.boundingBox.centerY()).toDouble(), (q2.boundingBox.centerX() - q1.boundingBox.centerX()).toDouble())).toFloat()

                    if (abs(rot) < 4.0f) {
                        val tx = r1.boundingBox.centerX().toFloat() - (s * q1.boundingBox.centerX().toFloat())
                        val ty = r1.boundingBox.centerY().toFloat() - (s * q1.boundingBox.centerY().toFloat())
                        allCandidates.add(AnchorCandidate("Phase 73 Unique", listOf(r1.text, r2.text), s, rot, tx, ty, dist(r1, r2), "S=%.3f, tx=%.1f, ty=%.1f".format(s, tx, ty)))
                    }
                }
            }
        }

        if (allCandidates.isEmpty()) return AnchorResult(false, message = "No valid anchor sets.", timeMs = System.currentTimeMillis() - t0)

        var bestGroup = mutableListOf<AnchorCandidate>()
        var maxSupport = -1
        for (c1 in allCandidates) {
            val supportGroup = allCandidates.filter { c2 ->
                abs(c1.scale - c2.scale) / c1.scale < 0.05f && abs(c1.tx - c2.tx) < 0.05f && abs(c1.ty - c2.ty) < 0.05f
            }.toMutableList()
            if (supportGroup.size > maxSupport) { maxSupport = supportGroup.size; bestGroup = supportGroup }
        }
        
        val finalScale = bestGroup.map { it.scale }.average().toFloat()
        val finalTx = bestGroup.map { it.tx }.average().toFloat()
        val finalTy = bestGroup.map { it.ty }.average().toFloat()

        val matrix = android.graphics.Matrix()
        matrix.postScale(finalScale, finalScale)
        matrix.postTranslate(finalTx, finalTy)

        return try {
            val outBmp = Bitmap.createBitmap(refBmp.width, refBmp.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(outBmp)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.drawBitmap(queryBmp, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            AnchorResult(true, outBmp, 0.5f, System.currentTimeMillis() - t0, mapOf("Consensus" to "S=%.3f, tx=%.1f, ty=%.1f".format(finalScale, finalTx, finalTy)))
        } catch (e: Exception) { AnchorResult(false, message = "Warp failed: ${e.message}") }
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
            val array = if (root.has(engineName)) root.getJSONArray(engineName) else if (json.startsWith("[")) JSONArray(json) else null ?: return emptySet()
            for (i in 0 until array.length()) result.add(array.getJSONObject(i).getString("text").trim())
        } catch (e: Exception) { Log.e("ImageAlignment", "JSON parse failed", e) }
        return result
    }

    fun performTier1Veto(queryLandmarks: List<TextBlock>, allVehicles: List<Vehicle>, engineName: String): Map<Int, VetoResult> {
        val queryWordsList = queryLandmarks.map { it.text.trim() }.sorted()
        val queryWordsSet = queryWordsList.toSet()
        val vehicleLandmarks = allVehicles.associate { it.id to getLandmarksFromJson(it.landmarkTextBlocksJson, engineName) }
        
        val initialResults = allVehicles.associate { currentVehicle ->
            val myWords = vehicleLandmarks[currentVehicle.id] ?: emptySet()
            val otherWordsPool = vehicleLandmarks.filter { it.key != currentVehicle.id }
                .values.flatten().toSet()
            
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
        return initialResults
    }
}
