package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import android.content.Context
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import java.util.Calendar

enum class LabPeriod {
    ALL_TIME,
    YTD,
    LAST_12_MONTHS,
    LAST_90_DAYS,
    CUSTOM,
}

/**
 * Vehicle picker mode for Lab children (not hub).
 * - [ALL]: aggregate combined series / totals (no vehicle row filter).
 * - [EACH]: no row filter; charts/lists split by vehicleId.
 * - [SINGLE]: filter to [ReportsLabFilterState.vehicleId].
 */
enum class LabVehicleMode {
    ALL,
    EACH,
    SINGLE,
}

data class ReportsLabFilterState(
    val vehicleMode: LabVehicleMode = LabVehicleMode.ALL,
    /** Used when [vehicleMode] is [LabVehicleMode.SINGLE]. */
    val vehicleId: Int? = null,
    val period: LabPeriod = LabPeriod.ALL_TIME,
    /** Inclusive custom bounds (ms); used when [period] is CUSTOM. */
    val customStartMs: Long = 0L,
    val customEndMs: Long = System.currentTimeMillis(),
)

object ReportsLabPrefs {
    const val PREFS = "vehicle_settings"
    const val KEY_VEHICLE = "reports_lab_vehicle_id"
    const val KEY_VEHICLE_MODE = "reports_lab_vehicle_mode"
    const val KEY_PERIOD = "reports_lab_period"
    const val KEY_CUSTOM_START = "reports_lab_custom_start"
    const val KEY_CUSTOM_END = "reports_lab_custom_end"

    fun load(context: Context): ReportsLabFilterState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val vidRaw = p.getInt(KEY_VEHICLE, -1)
        val period = try {
            LabPeriod.valueOf(p.getString(KEY_PERIOD, LabPeriod.ALL_TIME.name) ?: LabPeriod.ALL_TIME.name)
        } catch (_: Exception) {
            LabPeriod.ALL_TIME
        }
        val modeStr = p.getString(KEY_VEHICLE_MODE, null)
        val mode = try {
            if (modeStr != null) LabVehicleMode.valueOf(modeStr)
            else {
                // Migration: previous prefs used vehicleId null = all, non-null = single.
                if (vidRaw < 0) LabVehicleMode.ALL else LabVehicleMode.SINGLE
            }
        } catch (_: Exception) {
            if (vidRaw < 0) LabVehicleMode.ALL else LabVehicleMode.SINGLE
        }
        val now = System.currentTimeMillis()
        val vehicleId = if (vidRaw < 0) null else vidRaw
        val normalizedMode = when {
            mode == LabVehicleMode.SINGLE && vehicleId == null -> LabVehicleMode.ALL
            else -> mode
        }
        return ReportsLabFilterState(
            vehicleMode = normalizedMode,
            vehicleId = if (normalizedMode == LabVehicleMode.SINGLE) vehicleId else null,
            period = period,
            customStartMs = p.getLong(KEY_CUSTOM_START, 0L),
            customEndMs = p.getLong(KEY_CUSTOM_END, now),
        )
    }

    fun save(context: Context, state: ReportsLabFilterState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_VEHICLE_MODE, state.vehicleMode.name)
            .putInt(
                KEY_VEHICLE,
                if (state.vehicleMode == LabVehicleMode.SINGLE) {
                    state.vehicleId ?: -1
                } else {
                    -1
                },
            )
            .putString(KEY_PERIOD, state.period.name)
            .putLong(KEY_CUSTOM_START, state.customStartMs)
            .putLong(KEY_CUSTOM_END, state.customEndMs)
            .apply()
    }
}

/** Inclusive [startMs, endMs] for period presets; null bounds = no time filter. */
fun periodBounds(state: ReportsLabFilterState, nowMs: Long = System.currentTimeMillis()): Pair<Long?, Long?> {
    return when (state.period) {
        LabPeriod.ALL_TIME -> null to null
        LabPeriod.YTD -> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis to nowMs
        }
        LabPeriod.LAST_12_MONTHS -> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(Calendar.MONTH, -12)
            }
            cal.timeInMillis to nowMs
        }
        LabPeriod.LAST_90_DAYS -> (nowMs - 90L * 24 * 60 * 60 * 1000) to nowMs
        LabPeriod.CUSTOM -> {
            val start = minOf(state.customStartMs, state.customEndMs)
            val end = maxOf(state.customStartMs, state.customEndMs)
            start to end
        }
    }
}

/** True when rows are not filtered to a single vehicle (ALL or EACH). */
fun ReportsLabFilterState.isMultiVehicleScope(): Boolean =
    vehicleMode == LabVehicleMode.ALL || vehicleMode == LabVehicleMode.EACH

fun filterFuel(
    entries: List<FuelEntry>,
    state: ReportsLabFilterState,
    nowMs: Long = System.currentTimeMillis(),
): List<FuelEntry> {
    val (start, end) = periodBounds(state, nowMs)
    return entries.filter { e ->
        if (state.vehicleMode == LabVehicleMode.SINGLE &&
            state.vehicleId != null &&
            e.vehicleId != state.vehicleId
        ) {
            return@filter false
        }
        if (start != null && e.timestamp < start) return@filter false
        if (end != null && e.timestamp > end) return@filter false
        true
    }
}

fun filterExpenses(
    entries: List<ExpenseEntry>,
    state: ReportsLabFilterState,
    nowMs: Long = System.currentTimeMillis(),
): List<ExpenseEntry> {
    val (start, end) = periodBounds(state, nowMs)
    return entries.filter { e ->
        if (state.vehicleMode == LabVehicleMode.SINGLE &&
            state.vehicleId != null &&
            e.vehicleId != state.vehicleId
        ) {
            return@filter false
        }
        if (start != null && e.date < start) return@filter false
        if (end != null && e.date > end) return@filter false
        true
    }
}

fun periodLabel(state: ReportsLabFilterState, nowMs: Long = System.currentTimeMillis()): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return when (state.period) {
        LabPeriod.ALL_TIME -> "All time"
        LabPeriod.YTD -> {
            val (s, e) = periodBounds(state, nowMs)
            "YTD (${fmt.format(java.util.Date(s!!))} – ${fmt.format(java.util.Date(e!!))})"
        }
        LabPeriod.LAST_12_MONTHS -> "Last 12 months"
        LabPeriod.LAST_90_DAYS -> "Last 90 days"
        LabPeriod.CUSTOM -> {
            val (s, e) = periodBounds(state, nowMs)
            "Custom (${fmt.format(java.util.Date(s ?: 0))} – ${fmt.format(java.util.Date(e ?: nowMs))})"
        }
    }
}
