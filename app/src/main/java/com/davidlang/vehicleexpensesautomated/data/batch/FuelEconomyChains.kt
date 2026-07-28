package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry

/**
 * Shared MPG / $/mi chain predicates for reports and Stage C questions.
 * Keep in lockstep with [docs/reference/REPORTS_METRICS.md].
 *
 * Field presence: numeric field is present iff value **> 0**.
 */
object FuelEconomyChains {

    fun hasOdo(e: FuelEntry): Boolean = e.odometer > 0
    fun hasCost(e: FuelEntry): Boolean = e.cost > 0
    fun hasVol(e: FuelEntry): Boolean = e.gallons > 0

    /**
     * Full-fill anchor: not economyIgnored, not explicit partial, odo+cost+vol present.
     */
    fun isFullFill(e: FuelEntry): Boolean =
        !e.economyIgnored && !e.isPartialFill && hasOdo(e) && hasCost(e) && hasVol(e)

    /** Contributes cost/vol to economy windows (not economyIgnored). */
    fun contributesToEconomy(e: FuelEntry): Boolean = !e.economyIgnored

    /**
     * MPG chain breaker: blank (no odo/cost/vol) or cost without volume.
     * Odo-only rows are neither breakers nor volume contributors.
     */
    fun isMpgChainBreaker(e: FuelEntry): Boolean =
        (!hasOdo(e) && !hasCost(e) && !hasVol(e)) ||
            (hasCost(e) && !hasVol(e))

    /**
     * $/mi chain breaker: blank or volume without cost.
     */
    fun isDpmChainBreaker(e: FuelEntry): Boolean =
        (!hasOdo(e) && !hasCost(e) && !hasVol(e)) ||
            (hasVol(e) && !hasCost(e))

    /**
     * Rows in (prevTs, endTs] that may contribute (excludes economyIgnored).
     * Includes the end anchor when its timestamp equals endTs.
     */
    fun windowContributors(
        entries: List<FuelEntry>,
        prevTs: Long,
        endTs: Long,
    ): List<FuelEntry> =
        entries.filter {
            !it.deleted &&
                it.timestamp > prevTs &&
                it.timestamp <= endTs &&
                !it.economyIgnored
        }

    fun sumVol(window: List<FuelEntry>): Double =
        window.filter { hasVol(it) }.sumOf { it.gallons }

    /** True if any non-ignored window row is an MPG breaker (blank or cost-no-vol). */
    fun windowHasMpgBreaker(window: List<FuelEntry>): Boolean =
        window.any { isMpgChainBreaker(it) }

    /**
     * Compact shape label for inventory UI.
     * FULL / odo-only / pump-no-odo / BLANK / partial / mixed
     */
    fun rowShape(e: FuelEntry): String {
        if (e.economyIgnored) return "ignored"
        if (!hasOdo(e) && !hasCost(e) && !hasVol(e)) return "BLANK"
        if (isFullFill(e)) return "FULL"
        if (e.isPartialFill && hasOdo(e) && hasCost(e) && hasVol(e)) return "partial"
        if (hasOdo(e) && !hasCost(e) && !hasVol(e)) return "odo-only"
        if (!hasOdo(e) && (hasCost(e) || hasVol(e))) return "pump-no-odo"
        return "mixed"
    }

    /**
     * Time-ordered window inventory encoding for pending.extra["windowSummary"].
     * Each token: `id:odo:gal:cost:shape`, `|`-separated. Cap [maxRows] then `+N more`.
     */
    fun encodeWindowSummary(
        entries: List<FuelEntry>,
        prevTs: Long,
        endTs: Long,
        maxRows: Int = 12,
    ): String {
        val window = windowContributors(entries, prevTs, endTs)
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
        if (window.isEmpty()) return ""
        val take = window.take(maxRows)
        val parts = take.map { e ->
            val gal = "%.2f".format(e.gallons)
            val cost = "%.2f".format(e.cost)
            "${e.id}:${e.odometer}:$gal:$cost:${rowShape(e)}"
        }
        val more = window.size - take.size
        return if (more > 0) {
            parts.joinToString("|") + "|+$more more"
        } else {
            parts.joinToString("|")
        }
    }
}
