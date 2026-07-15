package com.davidlang.vehicleexpensesautomated.data.sync

/** Optional live status updates during manual sync (UI status line). */
fun interface SyncProgressListener {
    fun onStatus(message: String)
}