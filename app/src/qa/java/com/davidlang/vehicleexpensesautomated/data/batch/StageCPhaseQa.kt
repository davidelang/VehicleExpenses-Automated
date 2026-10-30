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


    enum class PhotoRole { DASH, PUMP, BOTH }


    /** Advance to next phase (capped at 6). Clears skip ledger for the new phase. */
    fun StageCPhaseStore.advance(context: Context): Int {
        val next = (currentPhase(context) + 1).coerceAtMost(StageCPhase.MAX)
        setPhase(context, next)
        StageCSkipLedger.clear(context)
        return next
    }


    /**
     * Prefer dash-only photos for odo phases; pump-only for pump/assign phases.
     */
    fun StageCPhaseStore.photoRole(item: BatchPendingItem): PhotoRole {
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

