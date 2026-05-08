package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import org.opencv.core.Mat
import java.nio.ByteBuffer

/**
 * MemoryBridge implements a zero-copy "Triple-View" onto a shared memory block.
 * It uses an ALPHA_8 Bitmap allocated at 1.5x height as the master memory backing.
 */
class MemoryBridge(val width: Int, val height: Int) {
    private val masterBitmap: Bitmap
    private var nativeHandle: Long = 0
    private val nv21Buffer: ByteBuffer

    init {
        // Allocate 1.5x height to accommodate NV21 chroma tail
        val masterHeight = (height * 1.5).toInt()
        masterBitmap = Bitmap.createBitmap(width, masterHeight, Bitmap.Config.ALPHA_8)
        
        // Step 1: Lock the bitmap and get primitive data
        val res = Companion.nativeLock(masterBitmap, width, height)
        if (res == null || res.size < 1 || res[0] == 0L) {
            throw IllegalStateException("Failed to lock bitmap in MemoryBridge")
        }
        
        nativeHandle = res[0]
        
        // Step 2: Get the DirectByteBuffer view using the handle
        nv21Buffer = Companion.nativeGetDirectBuffer(nativeHandle) ?: 
            throw IllegalStateException("Failed to create DirectBuffer in MemoryBridge")
    }

    /**
     * Returns the master ALPHA_8 bitmap. 
     * Paddle/UI should only operate on the top [height] rows.
     */
    fun getBitmap(): Bitmap = masterBitmap

    /**
     * Returns an OpenCV Mat header (CV_8UC1) pointing to the same memory.
     */
    fun getMat(): Mat {
        val ptr = Companion.nativeGetMatPtr(nativeHandle)
        return Mat(ptr)
    }

    /**
     * Returns a Direct ByteBuffer for ML Kit (IMAGE_FORMAT_NV21).
     */
    fun getNv21(): ByteBuffer = nv21Buffer

    /**
     * Call this when the bridge is no longer needed to unlock native pixels.
     */
    fun release() {
        if (nativeHandle != 0L) {
            Companion.nativeUnlock(masterBitmap, nativeHandle)
            nativeHandle = 0
        }
    }

    companion object {
        init {
            try {
                System.loadLibrary("memory_bridge")
            } catch (e: Exception) {
                android.util.Log.e("MemoryBridge", "Failed to load memory_bridge library", e)
            }
        }

        @JvmStatic private external fun nativeLock(bitmap: Bitmap, w: Int, h: Int): LongArray?
        @JvmStatic private external fun nativeUnlock(bitmap: Bitmap, handle: Long)
        @JvmStatic private external fun nativeGetMatPtr(handle: Long): Long
        @JvmStatic private external fun nativeGetDirectBuffer(handle: Long): ByteBuffer?
    }
}
