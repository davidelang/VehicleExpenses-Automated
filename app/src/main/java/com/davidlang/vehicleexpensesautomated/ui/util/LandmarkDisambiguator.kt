package com.davidlang.vehicleexpensesautomated.ui.util

import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

object LandmarkDisambiguator {
    private fun dist(a: TextBlock, b: TextBlock): Double {
        val dx = (a.boundingBox.centerX() - b.boundingBox.centerX()).toDouble()
        val dy = (a.boundingBox.centerY() - b.boundingBox.centerY()).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    fun disambiguate(
        dashLandmarks: List<TextBlock>,
        refLandmarks: List<TextBlock>
    ): List<TextBlock> {
        val dashValid = dashLandmarks.filter { it.boundingBox.width() > 0 }
        val refValid = refLandmarks.filter { it.boundingBox.width() > 0 }
        
        val dashTexts = dashValid.map { it.text }.toSet()
        val refTexts = refValid.map { it.text }.toSet()
        val commonTexts = dashTexts.intersect(refTexts)
        val potentialCount = dashValid.count { it.text in refTexts }

        Log.d("DISAMB_TRACE", "START: Dash=${dashValid.size}, Ref=${refValid.size} | CommonUnique=${commonTexts.size}, DashPotential=$potentialCount")
        if (dashValid.isEmpty() || refValid.isEmpty()) return dashLandmarks

        val dashCounts = dashValid.groupBy { it.text }.mapValues { it.value.size }
        val refCounts = refValid.groupBy { it.text }.mapValues { it.value.size }

        val results = dashValid.map { it.copy(instanceId = -1) }.toMutableList()

        // Pass 1: Match unique strings (Phase 109: Prioritize Instance 0)
        for (i in results.indices) {
            val dashMark = results[i]
            if (dashCounts[dashMark.text] == 1) {
                val refMatch = refValid.find { it.text == dashMark.text && it.instanceId == 0 }
                if (refMatch != null) {
                    results[i] = dashMark.copy(instanceId = 0)
                    Log.d("DISAMB_TRACE", "  Pass 1 [Unique]: '${dashMark.text}' matched Instance 0")
                }
            }
        }

        // Pass 2: If < 2 anchors, find seed triangle
        if (results.count { it.instanceId != -1 } < 2) {
            Log.d("DISAMB_TRACE", "  Pass 2: Insufficient anchors (${results.count { it.instanceId != -1 }}). Searching for seed triangle...")
            val seedPool = commonTexts.map { text ->
                val dCount = dashCounts[text] ?: 0
                val rCount = refCounts[text] ?: 0
                // Tier 1: Unique on both sides. Tier 2: Unique on Dash. Tier 3: Duplicates.
                val tier = if (dCount == 1 && (refValid.any { it.text == text && it.instanceId == 0 })) 1 else if (dCount == 1) 2 else 3
                Triple(text, tier, dCount)
            }.sortedWith(compareBy({ it.second }, { it.third }))

            outer@for (i in seedPool.indices) {
                for (j in i + 1 until seedPool.size) {
                    for (k in j + 1 until seedPool.size) {
                        val d1s = results.filter { it.text == seedPool[i].first }; val d2s = results.filter { it.text == seedPool[j].first }; val d3s = results.filter { it.text == seedPool[k].first }
                        val r1s = refValid.filter { it.text == seedPool[i].first }; val r2s = refValid.filter { it.text == seedPool[j].first }; val r3s = refValid.filter { it.text == seedPool[k].first }
                        
                        for (d1 in d1s) for (d2 in d2s) for (d3 in d3s) {
                            val dist12 = dist(d1, d2); val dist23 = dist(d2, d3); val dist31 = dist(d3, d1)
                            if (dist12 < 20.0 || dist23 < 20.0 || dist31 < 20.0) continue
                            
                            Log.d("DISAMB_TRI", "    Trying Dash Triangle: [${d1.text}, ${d2.text}, ${d3.text}] | Dists: %.1f, %.1f, %.1f".format(dist12, dist23, dist31))
                            
                            for (r1 in r1s) for (r2 in r2s) for (r3 in r3s) {
                                val rd12 = dist(r1, r2); val rd23 = dist(r2, r3); val rd31 = dist(r3, r1)
                                if (rd12 == 0.0 || rd23 == 0.0 || rd31 == 0.0) continue
                                
                                val ratio1 = (dist12 / rd12) / (dist23 / rd23)
                                val ratio2 = (dist23 / rd23) / (dist31 / rd31)
                                val dev1 = abs(ratio1 - 1.0); val dev2 = abs(ratio2 - 1.0)
                                
                                if (dev1 < 0.05 && dev2 < 0.05) {
                                    results[results.indexOf(d1)] = d1.copy(instanceId = r1.instanceId)
                                    results[results.indexOf(d2)] = d2.copy(instanceId = r2.instanceId)
                                    results[results.indexOf(d3)] = d3.copy(instanceId = r3.instanceId)
                                    Log.d("DISAMB_TRACE", "  Pass 2 [Seed]: Triangle found ('${d1.text}'-${r1.instanceId}, '${d2.text}'-${r2.instanceId}, '${d3.text}'-${r3.instanceId}) | Devs: %.3f, %.3f".format(dev1, dev2))
                                    break@outer
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pass 3: Bootstrap ambiguous landmarks (instanceId == -1) using confirmed anchors
        val confirmed = results.filter { it.instanceId != -1 }
        if (confirmed.size >= 2) {
            Log.d("DISAMB_TRACE", "  Pass 3: Bootstrapping from ${confirmed.size} anchors...")
            val p1 = confirmed[0]; val p2 = confirmed[1]
            val rP1 = refValid.find { it.text == p1.text && it.instanceId == p1.instanceId }!!
            val rP2 = refValid.find { it.text == p2.text && it.instanceId == p2.instanceId }!!
            val rDist12 = dist(rP1, rP2)

            for (idx in results.indices) {
                if (results[idx].instanceId != -1) continue
                val dashMark = results[idx]
                val refCandidates = refValid.filter { it.text == dashMark.text }
                
                Log.d("DISAMB_BOOT", "    Bootstrapping '${dashMark.text}' | Candidates: ${refCandidates.size}")
                
                for (cand in refCandidates) {
                    val rD1C = dist(rP1, cand); val rD2C = dist(rP2, cand)
                    if (rD1C == 0.0 || rD2C == 0.0 || rDist12 == 0.0) continue
                    
                    val ratio1 = (dist(p1, dashMark) / rD1C) / (dist(p1, p2) / rDist12)
                    val ratio2 = (dist(p2, dashMark) / rD2C) / (dist(p1, p2) / rDist12)
                    val dev1 = abs(ratio1 - 1.0); val dev2 = abs(ratio2 - 1.0)
                    
                    if (dev1 < 0.05 && dev2 < 0.05) {
                        results[idx] = dashMark.copy(instanceId = cand.instanceId)
                        Log.d("DISAMB_TRACE", "    -> Bootstrap Match: '${dashMark.text}' assigned Instance ${cand.instanceId} | Devs: %.3f, %.3f".format(dev1, dev2))
                        break
                    }
                }
            }
        }
        
        Log.d("DISAMB_TRACE", "FINISH: Tagged ${results.count { it.instanceId != -1 }}/${dashValid.size} landmarks")
        return results
    }
}
