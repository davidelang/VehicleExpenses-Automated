package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import org.opencv.core.Mat
import java.nio.ByteBuffer

// Top-level JNI declarations (outside class)
// These result in Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_ naming
private external fun nativeSetup(width: Int, height: Int): Long
private external fun nativeRelease(handle: Long)
private external fun nativeGetMatPtr(handle: Long): Long
private external fun nativeGetMasterBuffer(handle: Long): ByteBuffer?

/**
 * MemoryBridge implements a zero-copy "Triple-View" onto a shared memory block.
 * Master buffer is now allocated in native code and wrapped with NewDirectByteBuffer
 * for reliable visibility to ML Kit on emulator.
 */
class MemoryBridge(val width: Int, val height: Int) {
    private val masterBuffer: ByteBuffer
    private val masterBitmap: Bitmap
    private var nativeHandle: Long = 0

    init {
        try {
            System.loadLibrary("memory_bridge")
        } catch (e: Exception) {
            android.util.Log.e("MemoryBridge", "Failed to load memory_bridge library", e)
        }

        // Native allocation + NewDirectByteBuffer (stable for ML Kit)
        nativeHandle = nativeSetup(width, height)
        if (nativeHandle == 0L) {
            throw IllegalStateException("Failed to setup MemoryBridge native handles")
        }

        masterBuffer = nativeGetMasterBuffer(nativeHandle)
            ?: throw IllegalStateException("Failed to get master DirectByteBuffer from native")

        // Allocate matching ALPHA_8 bitmap for Paddle/Thumbnails
        masterBitmap = Bitmap.createBitmap(width, (height * 1.5).toInt(), Bitmap.Config.ALPHA_8)
    }

    fun getBitmap(): Bitmap = masterBitmap

    fun getMat(): Mat {
        val ptr = nativeGetMatPtr(nativeHandle)
        return Mat(ptr)
    }

    fun getNv21(): ByteBuffer = masterBuffer

    fun syncToBitmap() {
        masterBuffer.rewind()
        masterBitmap.copyPixelsFromBuffer(masterBuffer)
        masterBuffer.rewind()
    }

    fun syncFromBitmap() {
        masterBuffer.rewind()
        masterBitmap.copyPixelsToBuffer(masterBuffer)
        masterBuffer.rewind()
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0
        }
    }
}
