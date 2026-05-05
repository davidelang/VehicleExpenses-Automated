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
        dashW: Int, dashH: Int,
        refLandmarks: List<TextBlock>,
        refW: Int, refH: Int
    ): List<TextBlock> {
        val dashValid = dashLandmarks.filter { it.boundingBox.width() > 0 }
        val refValid = refLandmarks.filter { it.boundingBox.width() > 0 }
        
        val refTexts = refValid.map { it.text }.toSet()
        val dashPotential = dashValid.filter { it.text in refTexts }
        val commonTexts = dashValid.map { it.text }.toSet().intersect(refTexts)

        Log.d("DISAMB_TRACE", "START: Dash=${dashValid.size}, Ref=${refValid.size} | CommonUnique=${commonTexts.size}, DashPotential=${dashPotential.size}")
        if (dashValid.isEmpty() || refValid.isEmpty()) return dashLandmarks

        val dashCounts = dashValid.groupBy { it.text }.mapValues { it.value.size }
        val refCounts = refValid.groupBy { it.text }.mapValues { it.value.size }

        val results = dashValid.map { it.copy(instanceId = -1) }.toMutableList()

        // Pass 1: Global Uniqueness Match
        for (i in results.indices) {
            val dashMark = results[i]
            if (dashCounts[dashMark.text] == 1 && refCounts[dashMark.text] == 1) {
                val refMatch = refValid.find { it.text == dashMark.text }!!
                results[i] = dashMark.copy(instanceId = refMatch.instanceId)
                Log.d("DISAMB_TRACE", "  Pass 1 [Global Unique]: '${dashMark.text}' matched Instance ${refMatch.instanceId}")
            }
        }

        // Pass 2: If < 2 anchors, find seed triangle
        if (results.count { it.instanceId != -1 } < 2) {
            Log.d("DISAMB_TRACE", "  Pass 2: Insufficient anchors (${results.count { it.instanceId != -1 }}). Searching for seed triangle...")
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
                                Log.d("DISAMB_TRI", "    Trying Dash Triangle: [${d1.text}, ${d2.text}, ${d3.text}] | Prop: %.2f, %.2f, %.2f".format(d12/dPerim, d23/dPerim, d31/dPerim))
                                triCount++
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

        // Pass 3: Bootstrapping
        val confirmed = results.filter { it.instanceId != -1 }
        if (confirmed.size >= 2) {
            Log.d("DISAMB_TRACE", "  Pass 3: Bootstrapping from ${confirmed.size} anchors...")
            val p1 = confirmed[0]; val p2 = confirmed[1]
            val rP1 = refValid.find { it.text == p1.text && it.instanceId == p1.instanceId }!!
            val rP2 = refValid.find { it.text == p2.text && it.instanceId == p2.instanceId }!!
            
            // Reference baseline for bootstrapping
            val r12 = dist(rP1, rP2)
            val d12 = dist(p1, p2)

            for (idx in results.indices) {
                if (results[idx].instanceId != -1) continue
                val dashMark = results[idx]
                if (dashMark.text !in refTexts) continue

                val refCandidates = refValid.filter { it.text == dashMark.text }
                for (cand in refCandidates) {
                    val r1c = dist(rP1, cand); val r2c = dist(rP2, cand)
                    val d1c = dist(p1, dashMark); val d2c = dist(p2, dashMark)
                    
                    // Ratio of candidate distances to known anchor distance
                    if (r1c == 0.0 || r2c == 0.0 || r12 == 0.0 || d12 == 0.0) continue
                    
                    val dev1 = abs((d1c/d12) / (r1c/r12) - 1.0)
                    val dev2 = abs((d2c/d12) / (r2c/r12) - 1.0)
                    
                    if (dev1 < 0.05 && dev2 < 0.05) {
                        results[idx] = dashMark.copy(instanceId = cand.instanceId)
                        Log.d("DISAMB_TRACE", "    -> Bootstrap Match: '${dashMark.text}' assigned Instance ${cand.instanceId}")
                        break
                    } else {
                        Log.d("DISAMB_BOOT_FAIL", "    '${dashMark.text}' cand ${cand.instanceId} rejected: devs=%.3f, %.3f".format(dev1, dev2))
                    }
                }
            }
        }
        
        Log.d("DISAMB_TRACE", "FINISH: Tagged ${results.count { it.instanceId != -1 }}/${dashValid.size} landmarks")
        return results
    }
}
        
        Log.d("DISAMB_TRACE", "FINISH: Tagged ${results.count { it.instanceId != -1 }}/${dashValid.size} landmarks")
        return results
    }
}
