package com.davidlang.vehicleexpensesautomated.ui.util

import org.opencv.core.Mat
import java.nio.ByteBuffer

class RawMemoryBridge(val width: Int, val height: Int) {
    private val masterBuffer: ByteBuffer
    private val masterMat: Mat
    private var nativeHandle: Long = 0

    init {
        // Reuse the existing native setup logic
        nativeHandle = nativeSetup(width, height)
        if (nativeHandle == 0L) {
            throw IllegalStateException("Failed to setup RawMemoryBridge native handles")
        }

        masterBuffer = nativeGetMasterBuffer(nativeHandle)
            ?: throw IllegalStateException("Failed to get master DirectByteBuffer")

        // Pre-allocate permanent Mat view
        masterMat = Mat(nativeGetMatPtr(nativeHandle))
    }

    fun getNv21(): ByteBuffer = masterBuffer
    fun getMat(): Mat = masterMat

    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0
        }
    }

    // External JNI methods (assuming they exist and are accessible from MemoryBridge or similar)
    private external fun nativeSetup(w: Int, h: Int): Long
    private external fun nativeGetMasterBuffer(handle: Long): ByteBuffer?
    private external fun nativeGetMatPtr(handle: Long): Long
    private external fun nativeRelease(handle: Long)
}
