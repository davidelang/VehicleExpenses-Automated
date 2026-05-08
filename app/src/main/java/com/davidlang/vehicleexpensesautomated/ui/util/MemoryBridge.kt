package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import org.opencv.core.Mat
import java.nio.ByteBuffer

// Top-level JNI declarations (outside class)
// These result in Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_ naming
private external fun nativeSetup(buffer: ByteBuffer, width: Int, height: Int): Long
private external fun nativeRelease(handle: Long)
private external fun nativeGetMatPtr(handle: Long): Long

/**
 * MemoryBridge implements a zero-copy "Triple-View" onto a shared memory block.
 * It uses a Direct ByteBuffer as the master memory backing for stability with ML Kit.
 */
class MemoryBridge(val width: Int, val height: Int) {
    private val masterBuffer: ByteBuffer
    private val masterBitmap: Bitmap
    private var nativeHandle: Long = 0

    init {
        // Load library once
        try {
            System.loadLibrary("memory_bridge")
        } catch (e: Exception) {
            android.util.Log.e("MemoryBridge", "Failed to load memory_bridge library", e)
        }

        // Allocate master memory in JVM as DirectByteBuffer
        val totalSize = (width * height * 1.5).toInt()
        masterBuffer = ByteBuffer.allocateDirect(totalSize)
        
        // Setup native handles and headers
        nativeHandle = nativeSetup(masterBuffer, width, height)
        if (nativeHandle == 0L) {
            throw IllegalStateException("Failed to setup MemoryBridge native handles")
        }

        // Allocate matching ALPHA_8 bitmap for Paddle/Thumbnails
        masterBitmap = Bitmap.createBitmap(width, (height * 1.5).toInt(), Bitmap.Config.ALPHA_8)
    }

    /**
     * Returns the master ALPHA_8 bitmap. 
     * IMPORTANT: Call [syncToBitmap] if the buffer was modified by OpenCV or ML Kit.
     */
    fun getBitmap(): Bitmap = masterBitmap

    /**
     * Returns an OpenCV Mat header (CV_8UC1) pointing to the same memory.
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
     * Syncs the master buffer data into the master bitmap for Paddle/UI.
     */
    fun syncToBitmap() {
        masterBuffer.rewind()
        masterBitmap.copyPixelsFromBuffer(masterBuffer)
        masterBuffer.rewind()
    }

    /**
     * Syncs the bitmap pixels back to the master buffer.
     */
    fun syncFromBitmap() {
        masterBuffer.rewind()
        masterBitmap.copyPixelsToBuffer(masterBuffer)
        masterBuffer.rewind()
    }

    /**
     * Call this when the bridge is no longer needed to free native headers.
     */
    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0
        }
    }
}
