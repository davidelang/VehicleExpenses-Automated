package com.davidlang.vehicleexpensesautomated.data.sync

import kotlinx.coroutines.CancellationException

/** Optional live status updates during manual sync (UI status line). */
fun interface SyncProgressListener {
    fun onStatus(message: String)
}

/**
 * True when the throwable means "UI/job cancelled" rather than a real Drive/Sheets error.
 * Compose [ForgottenCoroutineScopeException] is a [CancellationException] subclass.
 */
fun Throwable.isNonFailureCancel(): Boolean {
    if (this is CancellationException) return true
    var t: Throwable? = this
    while (t != null) {
        val name = t.javaClass.name
        if (name.contains("ForgottenCoroutineScope")) return true
        val msg = t.message.orEmpty()
        if (msg.contains("rememberCoroutineScope left the composition", ignoreCase = true)) {
            return true
        }
        t = t.cause
    }
    return false
}
