package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import org.opencv.core.Mat
import java.nio.ByteBuffer

private external fun nativeSetup(width: Int, height: Int): Long
private external fun nativeRelease(handle: Long)
private external fun nativeGetMatPtr(handle: Long): Long
private external fun nativeGetMasterBuffer(handle: Long): ByteBuffer?

/**
 * MemoryBridge for refinement stage (320x128 / 320x48).
 * - Mat + NV21: zero-copy shared (native allocation)
 * - ALPHA_8: explicit fast copy via syncToBitmap / syncFromBitmap
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

        nativeHandle = nativeSetup(width, height)
        if (nativeHandle == 0L) {
            throw IllegalStateException("Failed to setup MemoryBridge native handles")
        }

        masterBuffer = nativeGetMasterBuffer(nativeHandle)
            ?: throw IllegalStateException("Failed to get master DirectByteBuffer")

        // Normal size ALPHA_8 for refinement (copy path)
        masterBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
    }

    fun getBitmap(): Bitmap = masterBitmap
    fun getMat(): Mat = Mat(nativeGetMatPtr(nativeHandle))
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
