package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Y-band role-pool classifier: port of Python `classify_band_gated` / `role_band` path from
 * pump_cost_vol_classifier_sim.py.
 */
object PumpRoleBandClassifier {

    const val PUMP_VOL_VALUE_MAX = 250f
    private const val RB_MAX_BAND_MERGE_PX = 140
    private const val RB_MAX_MERGE_HEIGHT_PX = 720
    private const val RB_STRONG_LOCK_MAX_PRIORITY = 3

    private val RB_COST_LABEL_RE = Regex(
        "(?i)(?:\\bthis\\s*sale\\b|\\bthissale\\b|\\bsale\\b|\\btotal\\b|\\bprice\\b|\\bamount\\b|\\bwholesale\\b|\\$)"
    )
    private val RB_VOL_LABEL_RE = Regex(
        "(?i)(?:\\bgallons?\\b|(?<=[0-9.])gallons?\\b|\\bgal\\b|/gal|\\bvolume\\b|\\blitre\\b|\\bliter\\b)"
    )
    private val RB_GOLDEN_BAND_RE = Regex("(\\$|/gal)", RegexOption.IGNORE_CASE)

    private val COST_KEYWORDS = listOf(
        "thissale", "this sale", "total", "sale", "price", "amount", "$", "usd",
    )
    private val VOL_KEYWORDS = listOf(
        "gallon", "gallons", "gal", "/gal", "volume", "litre", "liter", "l ",
    )

    private data class CandidateKey(val label: String, val field: String)

    private data class Enriched(
        val cand: RedBoxOcrCandidate,
        val field: String,
        val cleanDigits: String,
        val value: Float,
        val dp: Int,
        val probScore: Float,
        val costScore: Int,
        val volScore: Int,
    ) {
        fun key(): CandidateKey = CandidateKey(cand.label, field)
    }

    private data class YBand(
        var centerY: Float,
        val members: MutableList<RedBoxOcrCandidate> = mutableListOf(),
        var lockRole: String = "unknown",
        var lockPriority: Int = 99,
    ) {
        fun add(c: RedBoxOcrCandidate) {
            members.add(c)
        }
    }

    private data class PairScoreOptions(
        val useRatio: Boolean = true,
        val ratioBandLo: Float = 2f,
        val ratioBandHi: Float = 6f,
        val ratioTightLo: Float = 2.8f,
        val ratioTightHi: Float = 4.2f,
        val ratioTightMinVol: Float = 12f,
        val useImpliedVolPenalty: Boolean = true,
        val impliedVolDivisor: Float = 4.5f,
    ) {
        companion object {
            fun noRatio(): PairScoreOptions = PairScoreOptions(
                useRatio = false,
                useImpliedVolPenalty = false,
            )
        }
    }

    private var maxExplicitCostConsensus = 0

    fun classify(
        candidates: List<RedBoxOcrCandidate>,
        ratioBandLo: Float = PumpOcrSettings.DEFAULT_RATIO_BAND_LO,
        ratioBandHi: Float = PumpOcrSettings.DEFAULT_RATIO_BAND_HI,
        labelYBandExtraFraction: Float = PumpOcrSettings.DEFAULT_LABEL_Y_BAND_EXTRA_FRACTION,
    ): CostVolClassifyResult {
        val na = naResult()
        if (candidates.isEmpty()) return na

        val pairOpts = PairScoreOptions()
        val bands = rbClusterYBands(candidates, labelYBandExtraFraction)
        rbApplyLabelLocks(bands, candidates, labelYBandExtraFraction)
        val (costBands, volBands, gated) = rbDeriveRolePools(bands)

        if (!gated) {
            return classifyImprovedCoreResult(candidates, pairOpts)
        }

        val costLabels = rbPoolLabels(bands, costBands)
        val volLabels = rbPoolLabels(bands, volBands)
        if (costLabels.isEmpty() || volLabels.isEmpty()) {
            return classifyImprovedCoreResult(candidates, pairOpts)
        }

        val (costEnriched, volEnriched) = buildBandGatedEnrichedPools(
            candidates, costLabels, volLabels,
        )
        if (costEnriched.isEmpty() || volEnriched.isEmpty()) {
            return classifyImprovedCoreResult(candidates, pairOpts)
        }

        val consensus = consensusCounts(candidates)
        val allEnriched = costEnriched + volEnriched
        val bestPair = bestEnrichedPair(
            allEnriched,
            candidates,
            consensus,
            pairOpts,
            costPool = costEnriched,
            volPool = volEnriched,
            bandGated = true,
            ratioBandLo = ratioBandLo,
            ratioBandHi = ratioBandHi,
        ) ?: return classifyBaselineResult(candidates)

        val (cst, vlm) = finalizePair(
            bestPair.first,
            bestPair.second,
            allEnriched,
            candidates,
            consensus,
            pairOpts,
            volSearch = volEnriched,
        )
        val costE = matchOutputToEnriched(allEnriched, cst, "cost") ?: bestPair.first
        val volE = matchOutputToEnriched(volEnriched, vlm, "vol")
            ?: matchOutputToEnriched(allEnriched, vlm, "vol")
            ?: bestPair.second
        return toResult(cst, vlm, costE, volE, candidates)
    }

    // ── Public entry helpers ────────────────────────────────────────────────

    private fun naResult(): CostVolClassifyResult {
        val empty = RedBoxOcrCandidate("", "", "")
        return CostVolClassifyResult("N/A", "N/A", empty, empty)
    }

    private fun toResult(
        cost: String,
        vol: String,
        costEnriched: Enriched?,
        volEnriched: Enriched?,
        candidates: List<RedBoxOcrCandidate>,
    ): CostVolClassifyResult {
        val naCand = RedBoxOcrCandidate("", "", "")
        val costCand = if (cost == "N/A" || costEnriched == null) {
            naCand
        } else {
            resolveOriginalCandidate(costEnriched, candidates)
        }
        val volCand = if (vol == "N/A" || volEnriched == null) {
            naCand
        } else {
            resolveOriginalCandidate(volEnriched, candidates)
        }
        return CostVolClassifyResult(cost, vol, costCand, volCand)
    }

    private fun resolveOriginalCandidate(
        enriched: Enriched,
        candidates: List<RedBoxOcrCandidate>,
    ): RedBoxOcrCandidate {
        val baseLabel = enriched.cand.label.substringBefore("#")
        return candidates.find { it.label == baseLabel }
            ?: RedBoxOcrCandidate(
                baseLabel,
                enriched.cand.asis,
                enriched.cand.digits,
                enriched.cand.asisProbs,
                enriched.cand.digitsProbs,
                enriched.cand.rect,
            )
    }

    // ── Global-pair fallback (classify_improved_core) ───────────────────────

    private fun classifyImprovedCoreResult(
        candidates: List<RedBoxOcrCandidate>,
        pairOpts: PairScoreOptions,
    ): CostVolClassifyResult {
        val enriched = buildEnrichedPool(candidates)
        val consensus = consensusCounts(candidates)
        if (enriched.isEmpty()) return naResult()
        if (enriched.size == 1) {
            val e = enriched[0]
            return if (e.costScore >= e.volScore) {
                val cst = PumpCostVolUtils.repairDecimalForRole(e.cleanDigits, "cost")
                toResult(cst, "N/A", e, null, candidates)
            } else {
                val vlm = PumpCostVolUtils.repairDecimalForRole(e.cleanDigits, "vol")
                toResult("N/A", vlm, null, e, candidates)
            }
        }
        val bestPair = bestEnrichedPair(
            enriched, candidates, consensus, pairOpts, bandGated = false,
        ) ?: return classifyBaselineResult(candidates)
        val (cst, vlm) = finalizePair(
            bestPair.first, bestPair.second, enriched, candidates, consensus, pairOpts,
        )
        val costE = matchOutputToEnriched(enriched, cst, "cost") ?: bestPair.first
        val volE = matchOutputToEnriched(enriched, vlm, "vol") ?: bestPair.second
        return toResult(cst, vlm, costE, volE, candidates)
    }

    // ── Baseline fallback ───────────────────────────────────────────────────

    private fun classifyBaselineResult(candidates: List<RedBoxOcrCandidate>): CostVolClassifyResult {
        val enriched = enrichCandidatesKotlin(candidates)
        if (enriched.isEmpty()) return naResult()

        if (enriched.none { it.cand.rect != null }) {
            val costE = enriched.maxByOrNull { it.costScore }!!
            val volE = enriched.filter { it.cand.label != costE.cand.label }
                .maxByOrNull { it.volScore }
                ?: enriched.maxByOrNull { it.volScore }!!
            var cst = PumpCostVolUtils.repairDecimalForRole(costE.cleanDigits, "cost")
            var vlm = PumpCostVolUtils.repairDecimalForRole(volE.cleanDigits, "vol")
            if (cst == vlm && enriched.size >= 2) vlm = "N/A"
            if (digitCount(cst) < 2) cst = "N/A"
            if (digitCount(vlm) < 2) vlm = "N/A"
            return toResult(cst, vlm, costE, volE, candidates)
        }

        val seed1 = enriched.maxByOrNull { max(it.costScore, it.volScore) }!!
        val firstBest = pickClusterBest(enriched, seed1)
        val firstCluster = clusterOf(enriched, firstBest)
        val firstClusterLabels = firstCluster.map { it.cand.label }.toSet()
        val costLikelihood = firstCluster.sumOf { it.costScore.toDouble() }.toFloat() +
            firstBest.probScore * 30f
        val volLikelihood = firstCluster.sumOf { it.volScore.toDouble() }.toFloat() +
            firstBest.probScore * 30f
        val firstIsCost = costLikelihood >= volLikelihood
        val prefRect1 = firstBest.cand.rect
        val secondPool = enriched.filter { e ->
            e.cand.label !in firstClusterLabels &&
                (prefRect1 == null || e.cand.rect == null ||
                    !PumpCostVolUtils.significantYOverlap(prefRect1, e.cand.rect))
        }

        var costE = firstBest
        var volE = firstBest
        var cst: String
        var vlm: String

        if (firstIsCost) {
            cst = firstBest.cleanDigits
            if (secondPool.isNotEmpty()) {
                val seed2 = secondPool.maxByOrNull { max(it.costScore, it.volScore) }!!
                volE = pickClusterBest(secondPool, seed2)
                vlm = volE.cleanDigits
            } else {
                val alt = enriched.filter { it.cand.label != firstBest.cand.label }
                    .maxByOrNull { it.volScore }
                if (alt != null) {
                    volE = alt
                    vlm = alt.cleanDigits
                } else {
                    volE = firstBest
                    vlm = "N/A"
                }
            }
        } else {
            vlm = firstBest.cleanDigits
            if (secondPool.isNotEmpty()) {
                val seed2 = secondPool.maxByOrNull { max(it.costScore, it.volScore) }!!
                costE = pickClusterBest(secondPool, seed2)
                cst = costE.cleanDigits
            } else {
                val alt = enriched.filter { it.cand.label != firstBest.cand.label }
                    .maxByOrNull { it.costScore }
                if (alt != null) {
                    costE = alt
                    cst = alt.cleanDigits
                } else {
                    costE = firstBest
                    cst = "N/A"
                }
            }
        }

        cst = PumpCostVolUtils.repairDecimalForRole(cst, "cost")
        vlm = PumpCostVolUtils.repairDecimalForRole(vlm, "vol")
        if (cst == vlm && enriched.size >= 2) {
            if (firstIsCost) {
                val alt = secondPool.filter { it.cand.label != volE.cand.label }
                    .maxByOrNull { it.volScore }
                    ?: enriched.filter {
                        it.cand.label != costE.cand.label && it.cand.label != volE.cand.label
                    }.maxByOrNull { it.volScore }
                if (alt != null) {
                    volE = alt
                    vlm = PumpCostVolUtils.repairDecimalForRole(alt.cleanDigits, "vol")
                } else {
                    vlm = "N/A"
                }
            } else {
                val alt = secondPool.filter { it.cand.label != costE.cand.label }
                    .maxByOrNull { it.costScore }
                    ?: enriched.filter {
                        it.cand.label != costE.cand.label && it.cand.label != volE.cand.label
                    }.maxByOrNull { it.costScore }
                if (alt != null) {
                    costE = alt
                    cst = PumpCostVolUtils.repairDecimalForRole(alt.cleanDigits, "cost")
                } else {
                    cst = "N/A"
                }
            }
        }
        if (digitCount(cst) < 2) cst = "N/A"
        if (digitCount(vlm) < 2) vlm = "N/A"
        return toResult(cst, vlm, costE, volE, candidates)
    }

    // ── Y-band clustering & label locks ───────────────────────────────────────

    private fun rbClusterYBands(
        candidates: List<RedBoxOcrCandidate>,
        extraFraction: Float,
    ): List<YBand> {
        val withRect = candidates.filter { it.rect != null }
            .sortedBy { rbCenterY(it.rect!!) }
        val bands = mutableListOf<YBand>()

        for (c in withRect) {
            val cy = rbCenterY(c.rect!!)
            if (bands.isEmpty()) {
                bands.add(YBand(centerY = cy))
                bands.last().add(c)
                continue
            }
            val b = bands.last()
            if (abs(cy - b.centerY) <= rbMergeThreshold(b.members, extraFraction)) {
                val n = b.members.size + 1
                b.centerY = (b.centerY * b.members.size + cy) / n
                b.add(c)
            } else {
                bands.add(YBand(centerY = cy))
                bands.last().add(c)
            }
        }
        return bands
    }

    private fun rbApplyLabelLocks(
        bands: List<YBand>,
        candidates: List<RedBoxOcrCandidate>,
        extraFraction: Float,
    ) {
        val heights = rbCompactHeights(bands.flatMap { it.members })
        val gapMax = if (heights.isNotEmpty()) {
            (heights.sum().toFloat() / heights.size * 0.5f).toInt()
        } else {
            30
        }

        // Priority 1a: same-candidate golden prefix
        for (c in candidates) {
            val rect = c.rect ?: continue
            val asis = c.asis
            for (band in bands) {
                if (c !in band.members) continue
                if ("$" in asis && rbPlausibleCostNumeric(c)) {
                    rbSetBandLock(band, "cost", 1)
                }
                if (RB_VOL_LABEL_RE.containsMatchIn(asis) && rbPlausibleVolNumeric(c)) {
                    rbSetBandLock(band, "vol", 1)
                }
            }
        }

        // Priority 1: horizontal label left of numerics
        for (lc in candidates) {
            if (!rbIsLabelOnlyCandidate(lc) || lc.rect == null) continue
            val bi = rbClosestBandForHorizontalLabel(lc, bands, gapMax, extraFraction) ?: continue
            val band = bands[bi]
            val roles = rbLabelRoles(lc.asis)
            if ("cost" in roles && rbBandHasPlausibleCostNumeric(band)) {
                rbSetBandLock(band, "cost", 1)
            }
            if ("vol" in roles && rbBandHasPlausibleVolNumeric(band)) {
                var skipHorizVol = false
                if (bi > 0) {
                    val prev = bands[bi - 1]
                    if (rbBandHasRoleLabel(band, "vol") &&
                        rbBandHasPlausibleVolNumeric(prev) &&
                        rbBandsShareColumn(prev, band)
                    ) {
                        skipHorizVol = true
                    }
                }
                if (!skipHorizVol) {
                    rbSetBandLock(band, "vol", 1)
                }
            }
        }

        // Priority 3: $ or /gal on same row
        for (lc in candidates) {
            if (!rbIsLabelOnlyCandidate(lc) || lc.rect == null) continue
            if (!RB_GOLDEN_BAND_RE.containsMatchIn(lc.asis)) continue
            val bi = rbClosestBandForHorizontalLabel(lc, bands, gapMax, extraFraction) ?: continue
            val band = bands[bi]
            val asis = lc.asis
            if ("$" in asis && rbBandHasPlausibleCostNumeric(band)) {
                rbSetBandLock(band, "cost", 3)
            }
            if ("/gal" in asis.lowercase() && rbBandHasPlausibleVolNumeric(band)) {
                rbSetBandLock(band, "vol", 3)
            }
        }

        // Priority 2 down: label-only cost band above → numeric below
        for (i in bands.indices) {
            if (i + 1 >= bands.size) break
            val band = bands[i]
            val nb = bands[i + 1]
            if (nb.centerY <= band.centerY) continue
            if (!rbBandIsLabelOnlyForVertical(band, "cost")) continue
            if (rbNumericAnchorRect(nb) == null) continue
            if (!rbBandsShareColumn(band, nb)) continue
            rbSetBandLock(nb, "cost", 2)
        }

        // Priority 2 up: Gallons below → vol band above
        for (i in bands.indices) {
            if (i < 1) continue
            val band = bands[i]
            val prev = bands[i - 1]
            if (band.centerY <= prev.centerY) continue
            if (!rbBandHasRoleLabel(band, "vol")) continue
            if (rbNumericAnchorRect(prev) == null) continue
            if (!rbBandsShareColumn(prev, band)) continue
            rbSetBandLock(prev, "vol", 2)
        }

        // Row complement: strong cost row → next row is volume
        for (i in bands.indices) {
            if (i + 1 >= bands.size) break
            val band = bands[i]
            if (band.lockRole != "cost" || band.lockPriority > 1) continue
            val nb = bands[i + 1]
            if (rbNumericAnchorRect(nb) == null) continue
            if (nb.lockRole == "cost" && nb.lockPriority <= 1) continue
            if (!rbBandHasPlausibleVolNumeric(nb)) continue
            rbForceBandLock(nb, "vol", 2)
        }
    }

    private fun rbDeriveRolePools(bands: List<YBand>): Triple<Set<Int>, Set<Int>, Boolean> {
        var volBands = rbLockedBandIndices(bands, "vol")
        var costBands = rbLockedBandIndices(bands, "cost")
        val numericBands = rbNumericBandIndices(bands)

        if (volBands.isNotEmpty()) {
            costBands = numericBands - volBands
        } else if (costBands.isNotEmpty()) {
            volBands = numericBands - costBands
        }

        val refined = rbRefineEliminationPools(bands, costBands, volBands)
        costBands = refined.first
        volBands = refined.second
        val gated = costBands.isNotEmpty() && volBands.isNotEmpty()
        return Triple(costBands, volBands, gated)
    }

    private fun rbRefineEliminationPools(
        bands: List<YBand>,
        costBands: Set<Int>,
        volBands: Set<Int>,
    ): Pair<Set<Int>, Set<Int>> {
        val volLocked = volBands.filter { i ->
            bands[i].lockRole == "vol" && bands[i].lockPriority <= RB_STRONG_LOCK_MAX_PRIORITY
        }.toSet()
        if (volLocked.isEmpty()) return costBands to volBands
        if (volLocked.any { rbBandHasPlausibleVolNumeric(bands[it]) }) {
            return costBands to volBands
        }

        val moveToVol = mutableSetOf<Int>()
        for (i in costBands) {
            if (i in volBands) continue
            val volLike = bands[i].members.any { rbPlausibleVolNumeric(it) }
            val costLike = bands[i].members.any { rbPlausibleCostNumeric(it) }
            if (volLike && !costLike) moveToVol.add(i)
        }
        if (moveToVol.isNotEmpty()) {
            return (costBands - moveToVol) to (volBands + moveToVol)
        }
        return costBands to volBands
    }

    private fun rbPoolLabels(bands: List<YBand>, bandIndices: Set<Int>): Set<String> {
        val labels = mutableSetOf<String>()
        for (i in bandIndices) {
            for (c in bands[i].members) {
                if (rbEligibleNumeric(c)) labels.add(c.label)
            }
        }
        return labels
    }

    // ── Band-gated enriched pools ───────────────────────────────────────────

    private fun buildBandGatedEnrichedPools(
        candidates: List<RedBoxOcrCandidate>,
        costLabels: Set<String>,
        volLabels: Set<String>,
    ): Pair<List<Enriched>, List<Enriched>> {
        val overlap = costLabels.intersect(volLabels)
        val volLabelsAdj = if (overlap.isNotEmpty()) volLabels - overlap else volLabels
        val costCands = candidates.filter { it.label in costLabels }
        val volCands = candidates.filter { it.label in volLabelsAdj }
        val costEnriched = collapseRolePoolReadings(
            pruneSyntheticRepairVariants(buildRoleEnriched(costCands, "cost")),
            "cost",
        )
        val volEnriched = collapseRolePoolReadings(
            pruneSyntheticRepairVariants(buildRoleEnriched(volCands, "vol")),
            "vol",
        )
        return costEnriched to volEnriched
    }

    private fun buildRoleEnriched(
        candidates: List<RedBoxOcrCandidate>,
        role: String,
    ): List<Enriched> {
        if (candidates.isEmpty()) return emptyList()
        val enriched = mergeEnrichedBranches(
            enrichCandidates(candidates, useAsis = false),
            enrichCandidates(candidates, useAsis = true),
        )
        return if (role == "cost") addCostRepairVariants(enriched) else addVolRepairVariants(enriched)
    }

    private fun buildEnrichedPool(candidates: List<RedBoxOcrCandidate>): List<Enriched> {
        var enriched = mergeEnrichedBranches(
            enrichCandidates(candidates, useAsis = false),
            enrichCandidates(candidates, useAsis = true),
        )
        enriched = addCostRepairVariants(enriched)
        enriched = addVolRepairVariants(enriched)
        return enriched
    }

    private fun pruneSyntheticRepairVariants(enriched: List<Enriched>): List<Enriched> {
        val dottedBases = mutableSetOf<String>()
        for (e in enriched) {
            if ("#" in e.cand.label) continue
            if ("." in e.cleanDigits || "." in e.cand.asis) {
                dottedBases.add(e.cand.label)
            }
        }
        if (dottedBases.isEmpty()) return enriched
        return enriched.filter { e ->
            "#" !in e.cand.label || e.cand.label.substringBefore("#") !in dottedBases
        }
    }

    private fun collapseRolePoolReadings(enriched: List<Enriched>, role: String): List<Enriched> {
        val byKey = mutableMapOf<Pair<String, String>, Enriched>()
        for (e in enriched) {
            val base = e.cand.label.substringBefore("#")
            val key = base to e.cleanDigits
            val prev = byKey[key]
            if (prev == null || enrichedOcrQuality(e, role) > enrichedOcrQuality(prev, role)) {
                byKey[key] = e
            }
        }

        val nativeDottedLabels = mutableSetOf<String>()
        val repairDottedLabels = mutableSetOf<String>()
        for ((base, digs) in byKey.keys) {
            if ("." !in digs) continue
            val e = byKey[base to digs]!!
            if ("#" in e.cand.label) repairDottedLabels.add(base) else nativeDottedLabels.add(base)
        }
        val repairOnlyDotted = repairDottedLabels - nativeDottedLabels

        val out = mutableListOf<Enriched>()
        val seenDigits = mutableSetOf<String>()
        val sorted = byKey.entries.sortedByDescending { enrichedOcrQuality(it.value, role) }
        for ((key, e) in sorted) {
            val (base, digs) = key
            if (base in nativeDottedLabels && "." !in digs) continue
            if (base in repairOnlyDotted && "." !in digs && "#" !in e.cand.label) continue
            if ("#" in e.cand.label && base in nativeDottedLabels) continue
            if (digs in seenDigits) continue
            seenDigits.add(digs)
            out.add(e)
        }
        return out
    }

    // ── Pair scoring & selection ──────────────────────────────────────────────

    private fun bestEnrichedPair(
        enriched: List<Enriched>,
        candidates: List<RedBoxOcrCandidate>,
        consensus: Map<String, Int>,
        pairOpts: PairScoreOptions,
        costPool: List<Enriched>? = null,
        volPool: List<Enriched>? = null,
        bandGated: Boolean = false,
        ratioBandLo: Float = PumpOcrSettings.DEFAULT_RATIO_BAND_LO,
        ratioBandHi: Float = PumpOcrSettings.DEFAULT_RATIO_BAND_HI,
    ): Pair<Enriched, Enriched>? {
        val poolForConsensus = if (costPool != null && volPool != null) {
            if (costPool.isEmpty() || volPool.isEmpty()) return null
            costPool + volPool
        } else {
            if (enriched.size < 2) return null
            enriched
        }

        val pw = if (candidates.size > 40) 22f else 28f
        maxExplicitCostConsensus = poolForConsensus
            .filter { it.dp == 2 && "." in it.cleanDigits }
            .map { cleanVal(it.cleanDigits)?.let { v -> consensus[v] ?: 0 } ?: 0 }
            .maxOrNull() ?: 0

        val scoredPairs = mutableListOf<Triple<Float, Enriched, Enriched>>()
        if (costPool != null && volPool != null) {
            for (ce in costPool) {
                for (ve in volPool) {
                    if (ce.key() == ve.key()) continue
                    val sc = if (bandGated) {
                        bandGatedPairScore(ce, ve, consensus, pw, ratioBandLo, ratioBandHi)
                    } else {
                        pairScore(ce, ve, consensus, pw, pairOpts)
                    }
                    scoredPairs.add(Triple(sc, ce, ve))
                }
            }
        } else {
            for (i in enriched.indices) {
                for (j in i + 1 until enriched.size) {
                    val a = enriched[i]
                    val b = enriched[j]
                    val s1 = pairScore(a, b, consensus, pw, pairOpts)
                    val s2 = pairScore(b, a, consensus, pw, pairOpts)
                    if (s1 >= s2) {
                        scoredPairs.add(Triple(s1, a, b))
                    } else {
                        scoredPairs.add(Triple(s2, b, a))
                    }
                }
            }
        }

        if (scoredPairs.isEmpty()) return null
        scoredPairs.sortByDescending { it.first }
        val bestScore = scoredPairs[0].first
        val nearMargin = if (bandGated) 12f else 3f
        val near = scoredPairs.filter { it.first >= bestScore - nearMargin }

        fun tieCompare(a: Triple<Float, Enriched, Enriched>, b: Triple<Float, Enriched, Enriched>): Int {
            fun key(t: Triple<Float, Enriched, Enriched>): FloatArray {
                val (_, ce, ve) = t
                var bonus = 0f
                if (ce.dp == 2) bonus += 5f
                if (ve.dp == 3) bonus += 5f
                if ("." in ce.cleanDigits) bonus += 2f
                if ("." in ve.cleanDigits) bonus += 2f
                return if (bandGated) {
                    val extremePen = bandExtremeRatioPenalty(ce, ve, ratioBandLo, ratioBandHi)
                    val ocrQ = enrichedOcrQuality(ce, "cost") + enrichedOcrQuality(ve, "vol")
                    floatArrayOf(
                        extremePen, ocrQ, t.first + bonus,
                        ce.probScore + ve.probScore, ce.value,
                    )
                } else {
                    floatArrayOf(t.first + bonus, ce.probScore + ve.probScore)
                }
            }
            val ka = key(a)
            val kb = key(b)
            for (i in 0 until min(ka.size, kb.size)) {
                val cmp = ka[i].compareTo(kb[i])
                if (cmp != 0) return cmp
            }
            return ka.size.compareTo(kb.size)
        }

        val best = near.maxWith(::tieCompare)
        return best.second to best.third
    }

    private fun bandGatedPairScore(
        costE: Enriched,
        volE: Enriched,
        consensus: Map<String, Int>,
        pw: Float,
        ratioBandLo: Float,
        ratioBandHi: Float,
    ): Float {
        val base = pairScore(costE, volE, consensus, pw, PairScoreOptions.noRatio())
        return base +
            bandExtremeRatioPenalty(costE, volE, ratioBandLo, ratioBandHi) +
            enrichedOcrQuality(costE, "cost") * 0.15f +
            enrichedOcrQuality(volE, "vol") * 0.15f +
            volLeadingDigitRepairBonus(volE)
    }

    private fun bandExtremeRatioPenalty(
        costE: Enriched,
        volE: Enriched,
        ratioBandLo: Float,
        ratioBandHi: Float,
    ): Float {
        if (volE.value <= 0.5f) return 0f
        val ratio = costE.value / volE.value
        if (ratio < ratioBandLo) return -18f
        if (ratio > 50f || ratio < 0.15f) return -25f
        if (ratio > ratioBandHi) return -10f
        return 0f
    }

    private fun enrichedOcrQuality(e: Enriched, role: String): Float {
        var q = e.probScore * 35f
        if ("." in e.cleanDigits) q += 12f
        if ("#" !in e.cand.label) q += 4f
        if (role == "cost" && e.dp == 2) q += 8f
        if (role == "vol" && e.dp == 3) q += 10f
        else if (role == "vol" && e.dp == 2 && RB_VOL_LABEL_RE.containsMatchIn(e.cand.asis)) q += 6f
        if (role == "vol" && digitCount(e.cleanDigits) >= 5) q += 6f
        val asis = e.cand.asis
        if (role == "vol" && "#" in e.cand.label &&
            asis.trimStart().uppercase().startsWith("T") &&
            digitCount(e.cleanDigits) >= 5
        ) {
            q += 14f
        }
        return q
    }

    private fun volLeadingDigitRepairBonus(volE: Enriched): Float {
        val asis = volE.cand.asis
        if ("#" !in volE.cand.label) return 0f
        if (asis.trimStart().uppercase().startsWith("T") && digitCount(volE.cleanDigits) >= 5) {
            return 22f
        }
        if (digitCount(volE.cleanDigits) >= 5) return 8f
        return 0f
    }

    private fun pairScore(
        costE: Enriched,
        volE: Enriched,
        consensus: Map<String, Int>,
        probWeight: Float,
        pairOpts: PairScoreOptions,
    ): Float {
        if (costE.key() == volE.key()) return -1e9f
        var score = (costE.costScore + volE.volScore).toFloat()
        score += costE.probScore * probWeight + volE.probScore * probWeight
        if (costE.dp == 2) score += 25f
        else if (costE.dp == 3) score -= 8f
        if (volE.dp == 3) score += 25f
        else if (volE.dp == 2) score -= 8f
        if (costE.value > volE.value) score += 12f
        if (costE.value > 20f) score += 10f
        if (digitCount(costE.cleanDigits) <= digitCount(volE.cleanDigits)) score += 3f
        val costRect = costE.cand.rect
        val volRect = volE.cand.rect
        if (costRect != null && volRect != null) {
            if (costRect.top < volRect.top) score += 18f
            else if (costRect.top > volRect.top) score -= 10f
            if (PumpCostVolUtils.significantYOverlap(costRect, volRect)) score -= 35f
        }
        score += keywordBonus(costE.cand.asis, "cost").toFloat()
        score += keywordBonus(volE.cand.asis, "vol").toFloat()
        if (costE.field == "digits") score += 6f
        if (volE.field == "digits") score += 6f
        val cRep = PumpCostVolUtils.repairDecimalForRole(costE.cleanDigits, "cost")
        val vRep = PumpCostVolUtils.repairDecimalForRole(volE.cleanDigits, "vol")
        if (cRep == vRep) score -= 50f
        if (costE.value < 1f) score -= 20f
        if (volE.value < 1f) score -= 20f
        if (costE.value > 250f) score -= 90f
        else if (costE.value > 150f) score -= 30f
        if (costE.value in 8f..250f) score += 12f
        else if (costE.value in 5f..300f) score += 6f
        if (costE.dp in 1..2 && costE.value in 5f..200f) score += 10f
        if ("." !in costE.cleanDigits && costE.value in 10f..99f) score -= 18f
        if (costE.value < 20f && volE.value < 20f) score -= 25f
        if (costE.dp == 3 && volE.dp == 3) score -= 20f
        if (pairOpts.useRatio && volE.value > 0.5f) {
            val ratio = costE.value / volE.value
            if (ratio in pairOpts.ratioBandLo..pairOpts.ratioBandHi) score += 15f
            else if (ratio < 1.2f) score -= 30f
            if (ratio in pairOpts.ratioTightLo..pairOpts.ratioTightHi &&
                volE.value >= pairOpts.ratioTightMinVol
            ) {
                score += 22f
            }
        }
        if (volE.value in 1f..4f && costE.value in 6f..9f) score += 22f
        if (costE.value in 9f..10.5f && volE.value in 1.5f..2.5f) score -= 22f
        if (costE.value in 6f..8.8f && volE.value in 1.5f..2.5f) score += 18f
        if (pairOpts.useImpliedVolPenalty && pairOpts.impliedVolDivisor > 0f) {
            val impliedVol = costE.value / pairOpts.impliedVolDivisor
            if (impliedVol in 2f..40f) {
                score -= abs(volE.value - impliedVol) * 5.5f
            }
        }
        val cc = cleanVal(costE.cleanDigits)
        val vc = cleanVal(volE.cleanDigits)
        if (cc != null) score += min(consensus[cc] ?: 0, 10) * 5f
        if (vc != null) score += min(consensus[vc] ?: 0, 10) * 9f
        if ("#c" in costE.cand.label && cc != null) {
            if (maxExplicitCostConsensus > (consensus[cc] ?: 0) * 2) score -= 45f
        }
        return score
    }

    private fun finalizePair(
        costE: Enriched,
        volE: Enriched,
        enriched: List<Enriched>,
        candidates: List<RedBoxOcrCandidate>,
        consensus: Map<String, Int>,
        pairOpts: PairScoreOptions,
        volSearch: List<Enriched>? = null,
    ): Pair<String, String> {
        var cst = PumpCostVolUtils.repairDecimalForRole(costE.cleanDigits, "cost")
        var vlm = PumpCostVolUtils.repairDecimalForRole(volE.cleanDigits, "vol")
        if (cst == vlm) {
            val pw = if (candidates.size > 40) 22f else 28f
            val volPool = volSearch ?: enriched
            val altVol = volPool
                .filter { it.key() != costE.key() }
                .maxByOrNull { pairScore(costE, it, consensus, pw, pairOpts) }
            vlm = if (altVol != null) {
                PumpCostVolUtils.repairDecimalForRole(altVol.cleanDigits, "vol")
            } else {
                "N/A"
            }
        }
        if (digitCount(cst) < 2) cst = "N/A"
        if (digitCount(vlm) < 2) vlm = "N/A"
        return cst to vlm
    }

    private fun matchOutputToEnriched(
        enriched: List<Enriched>,
        text: String,
        role: String,
    ): Enriched? {
        if (text.isEmpty() || text == "N/A") return null
        val target = cleanVal(text) ?: return null
        var best: Enriched? = null
        var bestS = -1e18f
        for (e in enriched) {
            for (cand in listOf(
                e.cleanDigits,
                PumpCostVolUtils.repairDecimalForRole(e.cleanDigits, role),
            )) {
                if (cleanVal(cand) == target) {
                    val s = correctnessScore(e) +
                        (if (role == "cost") e.costScore else e.volScore).toFloat()
                    if (s > bestS) {
                        bestS = s
                        best = e
                    }
                }
            }
        }
        return best
    }

    // ── Enrichment ──────────────────────────────────────────────────────────

    private fun enrichCandidatesKotlin(candidates: List<RedBoxOcrCandidate>): List<Enriched> {
        val goldenYs = candidates.filter { c ->
            val a = c.asis.lowercase()
            a.contains("$") || a.contains("/gal") || a.contains("gal")
        }.mapNotNull { it.rect?.top }

        return candidates.mapNotNull { c ->
            if (PumpCostVolUtils.hasBadInternalDecimals(c.digits)) return@mapNotNull null
            val cleanDigits = PumpCostVolUtils.cleanDecimal(c.digits)
            if (digitCount(cleanDigits) < 2) return@mapNotNull null
            val (v, dp) = parseValueDpKotlin(cleanDigits)
            var cs = 0
            var vs = 0
            if (dp == 2) cs += 12
            if (dp == 3) vs += 12
            if (v > 20f) cs += 8
            if (dp > 0) {
                cs += 2
                vs += 2
            }
            if ("." in cleanDigits) {
                cs += 5
                vs += 5
            }
            if (goldenYs.isNotEmpty() && c.rect != null) {
                val minDist = goldenYs.minOf { abs(it - c.rect.top) }
                cs += 20 - min(minDist / 10, 20)
            }
            val prob = PumpCostVolUtils.probCorrectness(c.digitsProbs)
            cs += (prob * 20).toInt()
            vs += (prob * 20).toInt()
            Enriched(c, "digits", cleanDigits, v, dp, prob, cs, vs)
        }
    }

    private fun enrichCandidates(
        candidates: List<RedBoxOcrCandidate>,
        useAsis: Boolean,
    ): List<Enriched> {
        val goldenYs = candidates.filter { c ->
            val a = c.asis.lowercase()
            a.contains("$") || a.contains("/gal") || a.contains("gal")
        }.mapNotNull { it.rect?.top }

        val enriched = mutableListOf<Enriched>()
        for (c in candidates) {
            val fields = if (useAsis) {
                listOf("digits" to (c.digits to c.digitsProbs), "asis" to (c.asis to c.asisProbs))
            } else {
                listOf("digits" to (c.digits to c.digitsProbs))
            }
            for ((field, pair) in fields) {
                val (text, probs) = pair
                if (PumpCostVolUtils.hasBadInternalDecimals(text)) continue
                val cleanDigits = sanitizeNumericText(text)
                if (digitCount(cleanDigits) < 2) continue
                val (v, dp) = parseValueDp(cleanDigits)
                if (v <= 0.2f) continue
                var cs = 0
                var vs = 0
                if (dp == 2) cs += 12
                if (dp == 3) vs += 12
                if (v > 20f) cs += 8
                if (dp > 0) {
                    cs += 2
                    vs += 2
                }
                if ("." in cleanDigits) {
                    cs += 5
                    vs += 5
                }
                if (goldenYs.isNotEmpty() && c.rect != null) {
                    val minDist = goldenYs.minOf { abs(it - c.rect.top) }
                    cs += 20 - min(minDist / 10, 20)
                }
                val prob = PumpCostVolUtils.probCorrectness(probs)
                cs += (prob * 20).toInt()
                vs += (prob * 20).toInt()
                enriched.add(Enriched(c, field, cleanDigits, v, dp, prob, cs, vs))
            }
        }
        return enriched
    }

    private fun addCostRepairVariants(enriched: List<Enriched>): List<Enriched> {
        val extra = mutableListOf<Enriched>()
        val seen = enriched.map { it.cand.label to it.cleanDigits }.toMutableSet()
        for (e in enriched) {
            if ("." in e.cleanDigits) continue
            val dc = digitCount(e.cleanDigits)
            if (dc < 4 || dc > 5) continue
            val rep = PumpCostVolUtils.repairDecimalForRole(e.cleanDigits, "cost")
            if ("." !in rep) continue
            val (v, dp) = parseValueDp(rep)
            if (v !in 8f..250f || dp != 2) continue
            val newLabel = e.cand.label + "#c"
            if (newLabel to rep in seen) continue
            val nc = RedBoxOcrCandidate(
                newLabel, e.cand.asis, rep,
                e.cand.asisProbs, e.cand.digitsProbs, e.cand.rect,
            )
            extra.add(Enriched(nc, "digits", rep, v, dp, e.probScore, e.costScore + 28, e.volScore))
            seen.add(newLabel to rep)
        }
        return enriched + extra
    }

    private fun addVolRepairVariants(enriched: List<Enriched>): List<Enriched> {
        val extra = mutableListOf<Enriched>()
        val seen = enriched.map { it.cand.label to it.cleanDigits }.toMutableSet()
        for (e in enriched) {
            if ("." in e.cleanDigits) continue
            val dc = digitCount(e.cleanDigits)
            if (dc < 4 || dc > 6) continue
            val rep = PumpCostVolUtils.repairDecimalForRole(e.cleanDigits, "vol")
            if ("." !in rep) continue
            val (v, dp) = parseValueDp(rep)
            if (v <= 0f || v > PUMP_VOL_VALUE_MAX || dp != 3) continue
            val newLabel = e.cand.label + "#v"
            if (newLabel to rep in seen) continue
            val nc = RedBoxOcrCandidate(
                newLabel, e.cand.asis, rep,
                e.cand.asisProbs, e.cand.digitsProbs, e.cand.rect,
            )
            extra.add(Enriched(nc, "digits", rep, v, dp, e.probScore, e.costScore, e.volScore + 28))
            seen.add(newLabel to rep)
        }
        return enriched + extra
    }

    private fun mergeEnrichedBranches(
        enrichedDigits: List<Enriched>,
        enrichedAsis: List<Enriched>,
    ): List<Enriched> {
        val seen = enrichedDigits.map { it.cand.label to it.cleanDigits }.toMutableSet()
        val merged = enrichedDigits.toMutableList()
        for (e in enrichedAsis) {
            if (e.cand.label to e.cleanDigits !in seen) {
                merged.add(e)
            }
        }
        return dedupeEnriched(merged)
    }

    private fun dedupeEnriched(enriched: List<Enriched>): List<Enriched> {
        val best = mutableMapOf<Pair<String, Int>, Enriched>()
        for (e in enriched) {
            val cv = cleanVal(e.cleanDigits) ?: e.cleanDigits
            val dec = if ("." in e.cleanDigits) 1 else 0
            val key = cv to dec
            val scoreE = correctnessScore(e) + max(e.costScore, e.volScore)
            val cur = best[key]
            val curScore = cur?.let { correctnessScore(it) + max(it.costScore, it.volScore) } ?: Int.MIN_VALUE
            if (cur == null || scoreE > curScore) best[key] = e
        }
        return best.values.toList()
    }

    private fun consensusCounts(candidates: List<RedBoxOcrCandidate>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (c in candidates) {
            for (fld in listOf("digits", "asis")) {
                val raw = if (fld == "digits") c.digits else c.asis
                val ac = cleanVal(sanitizeNumericText(raw))
                if (ac != null) counts[ac] = (counts[ac] ?: 0) + 1
            }
        }
        return counts
    }

    private fun correctnessScore(e: Enriched): Int {
        var s = (e.probScore * 100).toInt()
        if ("." in e.cleanDigits) s += 50
        return s
    }

    private fun clusterOf(pool: List<Enriched>, seed: Enriched): List<Enriched> {
        val pref = seed.cand.rect ?: return listOf(seed)
        return pool.filter { e ->
            e.cand.rect?.let { PumpCostVolUtils.significantYOverlap(pref, it) } == true
        }.ifEmpty { listOf(seed) }
    }

    private fun pickClusterBest(pool: List<Enriched>, seed: Enriched): Enriched =
        clusterOf(pool, seed).maxByOrNull {
            correctnessScore(it) + max(it.costScore, it.volScore)
        } ?: seed

    private fun keywordBonus(text: String, role: String): Int {
        val t = text.lowercase()
        var bonus = 0
        if (role == "cost") {
            for (kw in COST_KEYWORDS) {
                if (kw in t) bonus += 15
            }
            if (Regex("\\$\\s*\\d").containsMatchIn(t)) bonus += 20
        } else {
            for (kw in VOL_KEYWORDS) {
                if (kw in t) bonus += 15
            }
        }
        return bonus
    }

    // ── RB geometry & eligibility helpers ───────────────────────────────────

    private fun rbCenterY(rect: Rect): Float = (rect.top + rect.bottom) / 2f

    private fun rbBandHeight(rect: Rect): Int = max(1, rect.bottom - rect.top)

    private fun rbRectArea(rect: Rect): Int =
        max(1, rect.right - rect.left) * max(1, rect.bottom - rect.top)

    private fun rbSmallestMember(members: List<RedBoxOcrCandidate>): RedBoxOcrCandidate? {
        val withR = members.filter { it.rect != null }
        if (withR.isEmpty()) return null
        return withR.minByOrNull { rbRectArea(it.rect!!) }
    }

    private fun rbIsLabelAsis(asis: String): Boolean {
        if (asis.trim().isEmpty()) return false
        if (RB_COST_LABEL_RE.containsMatchIn(asis) || RB_VOL_LABEL_RE.containsMatchIn(asis)) {
            return true
        }
        return digitCount(sanitizeNumericText(asis)) < 2 && asis.trim().length >= 2
    }

    private fun rbLabelRoles(asis: String): Set<String> {
        val roles = mutableSetOf<String>()
        if (RB_COST_LABEL_RE.containsMatchIn(asis)) roles.add("cost")
        if (RB_VOL_LABEL_RE.containsMatchIn(asis)) roles.add("vol")
        return roles
    }

    private fun rbIsLabelOnlyCandidate(c: RedBoxOcrCandidate): Boolean {
        if (digitCount(sanitizeNumericText(c.digits)) >= 2) return false
        return rbIsLabelAsis(c.asis) || c.asis.trim().length >= 2
    }

    private fun rbSameBoxLabelJunk(c: RedBoxOcrCandidate): Boolean {
        val asis = c.asis
        val digs = sanitizeNumericText(c.digits)
        if (digitCount(digs) < 2) return true
        if (!rbIsLabelAsis(asis)) return false
        val asisDigs = digitCount(sanitizeNumericText(asis))
        if (asisDigs >= 4) {
            val ac = cleanVal(sanitizeNumericText(asis))
            val dc = cleanVal(digs)
            if (ac != null && dc != null && (ac == dc || relaxedMatch(ac, dc))) return false
        }
        if (asisDigs < 4) return true
        val (v, dp) = parseValueDpKotlin(digs)
        if (v < 100f && dp <= 1 && digs.length <= 3) return true
        return false
    }

    private fun rbCompactHeights(members: List<RedBoxOcrCandidate>): List<Int> {
        val hs = members.mapNotNull { c ->
            c.rect?.let { r ->
                val h = rbBandHeight(r)
                if (h <= RB_MAX_MERGE_HEIGHT_PX) h else null
            }
        }
        return hs.ifEmpty {
            members.mapNotNull { c -> c.rect?.let { rbBandHeight(it) } }
        }
    }

    private fun rbMergeThreshold(members: List<RedBoxOcrCandidate>, extraFraction: Float): Float {
        val hs = rbCompactHeights(members).sorted()
        if (hs.isEmpty()) return 80f
        val med = hs[hs.size / 2]
        return min(RB_MAX_BAND_MERGE_PX.toFloat(), med * (1f + extraFraction))
    }

    private fun rbEligibleNumeric(c: RedBoxOcrCandidate): Boolean {
        if (PumpCostVolUtils.hasBadInternalDecimals(c.digits)) return false
        val digs = PumpCostVolUtils.cleanDecimal(c.digits)
        if (digitCount(digs) < 2) return false
        if (rbSameBoxLabelJunk(c)) return false
        return true
    }

    private fun rbPlausibleCostNumeric(c: RedBoxOcrCandidate): Boolean {
        if (!rbEligibleNumeric(c)) return false
        val (v, dp) = parseValueDp(PumpCostVolUtils.cleanDecimal(c.digits))
        if (v !in 5f..250f) return false
        return dp <= 2
    }

    private fun rbPlausibleVolNumeric(c: RedBoxOcrCandidate): Boolean {
        if (!rbEligibleNumeric(c)) return false
        val (v, dp) = parseValueDp(PumpCostVolUtils.cleanDecimal(c.digits))
        if (v <= 0f || v > PUMP_VOL_VALUE_MAX) return false
        if (dp == 3) return true
        if (dp == 2 && RB_VOL_LABEL_RE.containsMatchIn(c.asis)) return true
        return false
    }

    private fun rbBandHasEligibleNumeric(band: YBand): Boolean =
        band.members.any { rbEligibleNumeric(it) }

    private fun rbBandHasPlausibleCostNumeric(band: YBand): Boolean =
        band.members.any { rbPlausibleCostNumeric(it) }

    private fun rbBandHasPlausibleVolNumeric(band: YBand): Boolean =
        band.members.any { rbPlausibleVolNumeric(it) }

    private fun rbIsVerticalStackLabel(c: RedBoxOcrCandidate): Boolean {
        if (!rbIsLabelOnlyCandidate(c)) return false
        if (c.asis.trim().length > 32) return false
        return true
    }

    private fun rbBandHasRoleLabel(band: YBand, role: String): Boolean =
        band.members.any { rbIsVerticalStackLabel(it) && role in rbLabelRoles(it.asis) }

    private fun rbBandIsLabelOnlyForVertical(band: YBand, role: String): Boolean =
        rbBandHasRoleLabel(band, role) && !rbBandHasEligibleNumeric(band)

    private fun rbLabelSharesRowWithRect(
        lc: RedBoxOcrCandidate,
        anchor: Rect,
        gapMax: Int,
        extraFraction: Float,
    ): Boolean {
        val lcRect = lc.rect ?: return false
        val lcy = rbCenterY(lcRect)
        val h = rbBandHeight(anchor)
        val pad = extraFraction * h
        if (lcy !in (anchor.top - pad)..(anchor.bottom + pad)) return false
        return lcRect.right <= anchor.left + gapMax
    }

    private fun horizontalOverlapFraction(a: Rect, b: Rect): Float {
        val inter = max(0, min(a.right, b.right) - max(a.left, b.left))
        val narrower = min(a.right - a.left, b.right - b.left)
        return inter.toFloat() / max(1, narrower)
    }

    private fun rbBandsShareColumn(upper: YBand, lower: YBand, minXFrac: Float = 0.12f): Boolean {
        val upperRects = upper.members.mapNotNull { it.rect }
        val lowerRects = lower.members.mapNotNull { it.rect }
        if (upperRects.isEmpty() || lowerRects.isEmpty()) return false
        val ua = rbSmallestMember(upper.members) ?: return false
        val la = rbSmallestMember(
            lower.members.filter { rbEligibleNumeric(it) }.ifEmpty { lower.members },
        ) ?: return false
        val uaRect = ua.rect ?: return false
        val laRect = la.rect ?: return false
        return horizontalOverlapFraction(uaRect, laRect) >= minXFrac
    }

    private fun rbSetBandLock(band: YBand, role: String, priority: Int) {
        if (priority < band.lockPriority) {
            band.lockRole = role
            band.lockPriority = priority
        }
    }

    private fun rbForceBandLock(band: YBand, role: String, priority: Int) {
        band.lockRole = role
        band.lockPriority = priority
    }

    private fun rbNumericAnchorRect(band: YBand): Rect? {
        val nums = band.members.filter { rbEligibleNumeric(it) }
        val anchor = rbSmallestMember(nums.ifEmpty { band.members })
        return anchor?.rect
    }

    private fun rbClosestBandForHorizontalLabel(
        lc: RedBoxOcrCandidate,
        bands: List<YBand>,
        gapMax: Int,
        extraFraction: Float,
    ): Int? {
        val lcRect = lc.rect ?: return null
        var bestI: Int? = null
        var bestDy = 1e18f
        for ((bi, band) in bands.withIndex()) {
            val anchor = rbNumericAnchorRect(band) ?: continue
            if (!rbLabelSharesRowWithRect(lc, anchor, gapMax, extraFraction)) continue
            val dy = abs(rbCenterY(lcRect) - rbCenterY(anchor))
            if (dy < bestDy) {
                bestDy = dy
                bestI = bi
            }
        }
        return bestI
    }

    private fun rbLockedBandIndices(bands: List<YBand>, role: String): Set<Int> =
        bands.indices.filter { i ->
            bands[i].lockRole == role && bands[i].lockPriority <= RB_STRONG_LOCK_MAX_PRIORITY
        }.toSet()

    private fun rbNumericBandIndices(bands: List<YBand>): Set<Int> =
        bands.indices.filter { rbBandHasEligibleNumeric(bands[it]) }.toSet()

    // ── Text / numeric utilities ──────────────────────────────────────────────

    private fun sanitizeNumericText(s: String): String {
        var t = s.trim()
        t = t.replace(Regex("[^0-9.,+\\-]"), "")
        return PumpCostVolUtils.cleanDecimal(t)
    }

    private fun digitCount(s: String): Int = s.count { it.isDigit() }

    private fun cleanVal(s: String?): String? {
        if (s.isNullOrEmpty() || s == "N/A" || s == "FAILED") return null
        return s.trimEnd('?')
            .replace(".", "")
            .replace(",", "")
            .replace("$", "")
            .trim()
            .ifEmpty { null }
    }

    private fun relaxedMatch(gtClean: String, actClean: String): Boolean {
        if (gtClean == actClean) return true
        if (gtClean.length == actClean.length && gtClean.length > 1 &&
            gtClean.dropLast(1) == actClean.dropLast(1)
        ) {
            return true
        }
        if (abs(gtClean.length - actClean.length) == 1) {
            val shorter: String
            val longer: String
            if (gtClean.length < actClean.length) {
                shorter = gtClean
                longer = actClean
            } else {
                shorter = actClean
                longer = gtClean
            }
            return longer.startsWith(shorter)
        }
        return false
    }

    private fun significantDecimalPlaces(s: String): Int {
        if ("." !in s) return 0
        val frac = s.substringAfter(".")
        if (frac.length <= 2) return frac.length
        val sig = frac.trimEnd('0')
        return if (sig.isEmpty()) frac.length else sig.length
    }

    private fun parseValueDp(s: String): Pair<Float, Int> {
        val d = s.filter { it.isDigit() || it == '.' }
        val f = d.toFloatOrNull() ?: 0f
        val dp = significantDecimalPlaces(d)
        return f to dp
    }

    private fun parseValueDpKotlin(s: String): Pair<Float, Int> {
        val d = PumpCostVolUtils.cleanDecimal(s).filter { it.isDigit() || it == '.' }
        val f = d.toFloatOrNull() ?: 0f
        val dp = if ("." in d) d.substringAfter(".").length else 0
        return f to dp
    }
}