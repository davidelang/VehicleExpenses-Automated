package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * MemoryBridgeNative provides the unambiguous, top-level JNI declarations
 * for shared memory buffer management across the entire application.
 */
object MemoryBridgeNative {
    init {
        try {
            System.loadLibrary("memory_bridge")
        } catch (e: Exception) {
            android.util.Log.e("MemoryBridge", "Failed to load memory_bridge library", e)
        }
    }

    @JvmStatic external fun nativeLock(bitmap: Bitmap, w: Int, h: Int): LongArray?
    @JvmStatic external fun nativeUnlock(bitmap: Bitmap, handle: Long)
    @JvmStatic external fun nativeGetMatPtr(handle: Long): Long
    @JvmStatic external fun nativeGetDirectBuffer(handle: Long): ByteBuffer?
}
