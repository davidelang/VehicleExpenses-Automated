package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import org.opencv.core.Mat
import java.nio.ByteBuffer

// Top-level JNI declarations (outside class) for absolute naming stability.
// These result in Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_ naming
private external fun nativeSetup(width: Int, height: Int): Long
private external fun nativeGetMasterBuffer(handle: Long): ByteBuffer?
private external fun nativeGetMatPtr(handle: Long): Long
private external fun nativeRelease(handle: Long)

/**
 * MemoryBridge implements a zero-copy "Triple-View" onto a shared memory block.
 * It uses a NATIVELY allocated buffer wrapped in a DirectByteBuffer for maximum 
 * stability across hardware-accelerated engines (ML Kit, OpenCV, Paddle).
 */
class MemoryBridge(val width: Int, val height: Int) {
    private var nativeHandle: Long = 0
    private val masterBuffer: ByteBuffer
    private val masterBitmap: Bitmap

    init {
        // Load library once
        try {
            System.loadLibrary("memory_bridge")
        } catch (e: Exception) {
            android.util.Log.e("MemoryBridge", "Failed to load memory_bridge library", e)
        }

        // Step 1: Allocate native memory and get handle
        nativeHandle = nativeSetup(width, height)
        if (nativeHandle == 0L) {
            throw IllegalStateException("Failed to allocate native memory for MemoryBridge")
        }

        // Step 2: Get the DirectByteBuffer view (wraps the native pointer)
        masterBuffer = nativeGetMasterBuffer(nativeHandle) ?: 
            throw IllegalStateException("Failed to wrap native memory in DirectBuffer")

        // Step 3: Allocate matching ALPHA_8 bitmap for Paddle/Thumbnails
        // Note: Paddle needs a real Bitmap object. We use sync methods to bridge them.
        masterBitmap = Bitmap.createBitmap(width, (height * 1.5).toInt(), Bitmap.Config.ALPHA_8)
    }

    /**
     * Returns an ALPHA_8 bitmap for Paddle/UI. 
     * IMPORTANT: Call [syncToBitmap] if the buffer was modified by OpenCV or ML Kit.
     */
    fun getBitmap(): Bitmap = masterBitmap

    /**
     * Returns an OpenCV Mat header (CV_8UC1) pointing to the native memory.
     */
    fun getMat(): Mat {
        val ptr = nativeGetMatPtr(nativeHandle)
        return Mat(ptr)
    }

    /**
     * Returns the master Direct ByteBuffer for ML Kit (IMAGE_FORMAT_NV21).
     */
    fun getNv21(): ByteBuffer = masterBuffer

    /**
     * Syncs the native buffer data into the Bitmap for Paddle/UI.
     */
    fun syncToBitmap() {
        masterBuffer.rewind()
        masterBitmap.copyPixelsFromBuffer(masterBuffer)
        masterBuffer.rewind()
    }

    /**
     * Syncs the Bitmap pixels back to the native buffer.
     */
    fun syncFromBitmap() {
        masterBuffer.rewind()
        masterBitmap.copyPixelsToBuffer(masterBuffer)
        masterBuffer.rewind()
    }

    /**
     * Call this when the bridge is no longer needed to free native memory.
     */
    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0
        }
    }
}
