package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import kotlin.math.abs

/**
 * Per-device stable offsets so periodic sync workers on multiple devices rarely collide.
 */
object SyncScheduleJitter {

    private const val MAX_INITIAL_DELAY_MINUTES = 30L
    private const val PERIOD_FLEX_PERCENT = 15

    fun initialDelayMinutes(context: Context, basePeriodMinutes: Long): Long {
        val deviceHash = stableDeviceHash(context)
        val spread = minOf(MAX_INITIAL_DELAY_MINUTES, maxOf(2L, basePeriodMinutes / 2))
        return 2L + (abs(deviceHash) % spread)
    }

    /** WorkManager minimum flex for periodic work (API 23+). */
    fun flexMinutes(basePeriodMinutes: Long): Long {
        if (basePeriodMinutes < 15) return 5L
        return maxOf(5L, basePeriodMinutes * PERIOD_FLEX_PERCENT / 100)
    }

    private fun stableDeviceHash(context: Context): Int =
        SyncIdentity.getOrCreateDeviceId(context).hashCode()
}