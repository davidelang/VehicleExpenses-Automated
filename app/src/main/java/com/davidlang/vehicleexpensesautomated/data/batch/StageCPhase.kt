package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context

/**
 * Stage C phased question queue (1…6).
 *
 * **Generation is phase-scoped:** pending store holds only the current phase’s
 * kinds after any normal Stage C op (not a full multi-phase backlog).
 *
 * @see batch-stage-c-ux-skip-photos-phase-scope-20260728-plan.md
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
 * Persisted current Stage C phase. Reset to phase 1 after fuel-changing sync.
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
        StageCSkipLedger.clear(context)
    }

    /** Advance to next phase (capped at 6). Clears skip ledger for the new phase. */
    fun advance(context: Context): Int {
        val next = (currentPhase(context) + 1).coerceAtMost(StageCPhase.MAX)
        setPhase(context, next)
        StageCSkipLedger.clear(context)
        return next
    }

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
            BatchPendingKind.ECONOMY_IGNORED -> StageCPhase.MPG.number
            BatchPendingKind.OTHER -> StageCPhase.MPG.number
        }
    }

    /** True if [item] belongs to [phase] (for phase-scoped store). */
    fun belongsToPhase(item: BatchPendingItem, phase: Int): Boolean =
        phaseFor(item) == phase

    fun countForPhase(items: List<BatchPendingItem>, phase: Int): Int =
        items.count { phaseFor(it) == phase }

    fun label(phase: Int): String {
        val p = StageCPhase.fromNumber(phase)
        return "Phase ${p.number} of ${StageCPhase.COUNT}: ${p.title}"
    }

    /**
     * Prefer dash-only photos for odo phases; pump-only for pump/assign phases.
     */
    fun photoRole(item: BatchPendingItem): PhotoRole {
        return when (item.kind) {
            BatchPendingKind.ODO_SUSPECT,
            BatchPendingKind.CONFLICT_ODO,
            -> PhotoRole.DASH
            BatchPendingKind.BAD_PUMP_RATIO,
            BatchPendingKind.UNREADABLE_PUMP,
            BatchPendingKind.AMBIGUOUS_MULTI_PUMP,
            BatchPendingKind.ASSIGN_UNKNOWN_VEHICLE,
            BatchPendingKind.ASSIGN_VEHICLE,
            BatchPendingKind.SKIP_OR_ASSIGN_VEHICLE,
            -> PhotoRole.PUMP
            BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE -> PhotoRole.DASH
            BatchPendingKind.MPG_OUTLIER -> PhotoRole.BOTH
            else -> PhotoRole.BOTH
        }
    }

    enum class PhotoRole { DASH, PUMP, BOTH }
}
