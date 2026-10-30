package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log

object RcloneLoader {
    private const val TAG = "RcloneLoader"
    private var isInitialized = false

    fun load(context: Context) {
        if (isInitialized) return
        Log.i(TAG, "Loading bundled rclone library (full backends)")
        System.loadLibrary("gojni")
        isInitialized = true
    }
}
