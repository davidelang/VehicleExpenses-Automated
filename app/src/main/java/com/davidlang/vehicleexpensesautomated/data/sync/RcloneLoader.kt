package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import java.io.File

object RcloneLoader {
    private const val TAG = "RcloneLoader"
    private var isInitialized = false

    fun load(context: Context) {
        if (isInitialized) return
        val expandedSo = File(context.filesDir, "expanded_rclone/libgojni.so")
        try {
            if (expandedSo.exists()) {
                Log.i(TAG, "Loading expanded rclone library from: ${expandedSo.absolutePath}")
                System.load(expandedSo.absolutePath)
            } else {
                Log.i(TAG, "Loading bundled rclone library (full backends)")
                System.loadLibrary("gojni")
            }
            isInitialized = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load rclone library", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading rclone library", e)
        }
    }
    fun isReady() = isInitialized
}
