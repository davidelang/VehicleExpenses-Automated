package com.davidlang.vehicleexpensesautomated.data.sync

import android.util.Log

/** Logcat filter: `adb logcat -s GoogleRestApi` */
object GoogleRestApiFailureLog {
    private const val TAG = "GoogleRestApi"

    fun log(operation: String, detail: String? = null, error: Throwable) {
        val suffix = detail?.let { " $it" }.orEmpty()
        Log.e(
            TAG,
            "operation=$operation$suffix: ${error.javaClass.simpleName}: ${error.message}",
            error,
        )
    }
}
