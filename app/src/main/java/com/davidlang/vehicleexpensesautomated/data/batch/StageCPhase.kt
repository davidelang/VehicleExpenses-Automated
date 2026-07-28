package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context

/**
 * Stage C phased question queue (1…6).
 * Only the **current** phase is shown in the default Import UI; detectors still
 * rebuild the full pending list so a rescan after phase advance picks up later kinds.
 *
 * @see batch-stage-c-phased-questions-20260728-plan.md
 */
enum class StageCPhase(val number: Int, val title: String) {
    SIMPLE_ODO(1, "Simple odometer fixes"),
    COMPLEX_ODO(2, "Complex odometer / conflicts"),
    BAD_PUMP(3, "Bad pump economics"),
    UNASSIGNED(4, "Unassigned pumps / vehicles"),
    UNREADABLE(5, "Unreadable / ambiguous"),
    MPG(6, "MPG range + mid-leg gap"),
    ;

    companion object {
        const val MIN = 1
        const val MAX = 6
        const val COUNT = 6

        fun fromNumber(n: Int): StageCPhase =
            entries.firstOrNull { it.number == n.coerceIn(MIN, MAX) } ?: SIMPLE_ODO
    }
}

/**
 * Persisted current Stage C phase. Reset to phase 1 after fuel-changing sync
 * so skipped/deferred items are not sticky across pull.
 */
object StageCPhaseStore {
    private const val PREFS = "stage_c_phase"
    private const val KEY_PHASE = "current_phase"

    fun currentPhase(context: Context): Int {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PHASE, StageCPhase.MIN)
        return p.coerceIn(StageCPhase.MIN, StageCPhase.MAX)
    }

    fun current(context: Context): StageCPhase =
        StageCPhase.fromNumber(currentPhase(context))

    fun setPhase(context: Context, phase: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PHASE, phase.coerceIn(StageCPhase.MIN, StageCPhase.MAX))
            .apply()
    }

    fun resetToPhase1(context: Context) {
        setPhase(context, StageCPhase.MIN)
    }

    /** Advance to next phase (capped at 6). Returns new phase number. */
    fun advance(context: Context): Int {
        val next = (currentPhase(context) + 1).coerceAtMost(StageCPhase.MAX)
        setPhase(context, next)
        return next
    }

    /**
     * Which phase a pending item belongs to (for UI filter).
     * ECONOMY_IGNORED → 0 (side panel / not blocking phase advance).
     */
    fun phaseFor(item: BatchPendingItem): Int {
        return when (item.kind) {
            BatchPendingKind.ODO_SUSPECT -> {
                if (item.extra["mode"] == "simple") StageCPhase.SIMPLE_ODO.number
                else StageCPhase.COMPLEX_ODO.number
            }
            BatchPendingKind.CONFLICT_ODO -> StageCPhase.COMPLEX_ODO.number
            BatchPendingKind.BAD_PUMP_RATIO -> StageCPhase.BAD_PUMP.number
            BatchPendingKind.ASSIGN_UNKNOWN_VEHICLE,
            BatchPendingKind.ASSIGN_VEHICLE,
            BatchPendingKind.SKIP_OR_ASSIGN_VEHICLE,
            -> StageCPhase.UNASSIGNED.number
            BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE,
            BatchPendingKind.UNREADABLE_PUMP,
            BatchPendingKind.AMBIGUOUS_MULTI_PUMP,
            -> StageCPhase.UNREADABLE.number
            BatchPendingKind.MPG_OUTLIER -> StageCPhase.MPG.number
            BatchPendingKind.ECONOMY_IGNORED -> 0
            BatchPendingKind.OTHER -> StageCPhase.MPG.number
        }
    }

    fun filterForPhase(
        items: List<BatchPendingItem>,
        phase: Int,
        showAll: Boolean,
    ): List<BatchPendingItem> {
        if (showAll) return items
        return items.filter { phaseFor(it) == phase || phaseFor(it) == 0 && phase == StageCPhase.MAX }
            .let { list ->
                // ECONOMY_IGNORED only as optional side items when no blocking work in phase —
                // still hide from early phases by default (phase 0).
                if (phase < StageCPhase.MAX) {
                    list.filter { phaseFor(it) == phase }
                } else {
                    list
                }
            }
    }

    fun countForPhase(items: List<BatchPendingItem>, phase: Int): Int =
        items.count { phaseFor(it) == phase }

    fun label(phase: Int): String {
        val p = StageCPhase.fromNumber(phase)
        return "Phase ${p.number} of ${StageCPhase.COUNT}: ${p.title}"
    }
}
