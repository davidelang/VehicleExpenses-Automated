package com.davidlang.vehicleexpensesautomated.ui.util

import kotlin.math.abs

/**
 * Human-readable signed duration for neighbor / unknown-vehicle context.
 * Never shows multi-thousand "m" labels (e.g. 53424m → weeks/months).
 *
 * | |Δ| | Display |
 * |------|---------|
 * | < 60 min | `Nm` |
 * | < 48 h | `Nh` or `Nh Nm` |
 * | < 14 d | `N.Nd` |
 * | < 10 w | `N.Nw` |
 * | else | `N.N mo` or `N.Ny` |
 */
fun formatTimeDelta(ms: Long): String {
    val sign = if (ms < 0) -1 else 1
    val absMs = abs(ms)
    val suffix = if (sign < 0) " earlier" else if (ms > 0) " later" else ""
    if (absMs < 60_000L) {
        val s = (absMs / 1000.0).coerceAtLeast(0.0)
        return if (s < 1) "now" else "${s.toInt()}s$suffix"
    }
    val minutes = absMs / 60_000.0
    if (minutes < 60) {
        return "${minutes.toInt()}m$suffix"
    }
    val hours = minutes / 60.0
    if (hours < 48) {
        val h = hours.toInt()
        val m = ((minutes - h * 60).toInt()).coerceAtLeast(0)
        return if (m == 0 || h >= 10) "${h}h$suffix" else "${h}h ${m}m$suffix"
    }
    val days = hours / 24.0
    if (days < 14) {
        return "${"%.1f".format(days)}d$suffix"
    }
    val weeks = days / 7.0
    if (weeks < 10) {
        return "${"%.1f".format(weeks)}w$suffix"
    }
    val months = days / 30.4375
    if (months < 18) {
        return "${"%.1f".format(months)} mo$suffix"
    }
    val years = days / 365.25
    return "${"%.1f".format(years)}y$suffix"
}
