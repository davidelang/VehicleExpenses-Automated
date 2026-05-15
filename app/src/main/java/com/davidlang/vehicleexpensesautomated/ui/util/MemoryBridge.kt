package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import org.opencv.core.Mat
import java.nio.ByteBuffer

private external fun nativeSetup(width: Int, height: Int): Long
private external fun nativeRelease(handle: Long)
private external fun nativeGetMatPtr(handle: Long): Long
private external fun nativeGetMasterBuffer(handle: Long): ByteBuffer?
private external fun nativeSyncMatFromArgb(bitmap: Bitmap, matPtr: Long)
private external fun nativeSyncMatToArgb(matPtr: Long, bitmap: Bitmap)

/**
 * MemoryBridge for refinement stage (320x128 / 320x48).
 * - Mat + NV21: zero-copy shared (native allocation)
 * - ALPHA_8: explicit fast copy via syncToBitmap / syncFromBitmap
 * 
 * Surgical stabilization: All pools are initialized eagerly on the main thread
 * to avoid the background JNI synchronization lock crashes (0x4).
 */
class MemoryBridge(val width: Int, val height: Int) {
    private val masterBuffer: ByteBuffer
    private val masterBitmap: Bitmap
    private val masterMat: Mat
    private var nativeHandle: Long = 0

    init {
        nativeHandle = nativeSetup(width, height)
        if (nativeHandle == 0L) {
            throw IllegalStateException("Failed to setup MemoryBridge native handles")
        }

        masterBuffer = nativeGetMasterBuffer(nativeHandle)
            ?: throw IllegalStateException("Failed to get master DirectByteBuffer")

        // Normal size ALPHA_8 for refinement (copy path)
        masterBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)

        // Pre-allocate permanent Mat view (Zero-Allocation)
        masterMat = Mat(nativeGetMatPtr(nativeHandle))
    }

    fun getBitmap(): Bitmap = masterBitmap
    fun getMat(): Mat = masterMat
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

    /**
     * Fast JNI linear copy from ARGB Bitmap to this Mono bridge.
     */
    fun syncFromArgb(bitmap: Bitmap) {
        syncMatFromArgb(bitmap, masterMat)
    }

    /**
     * Fast JNI linear copy from this Mono bridge to ARGB Bitmap.
     */
    fun syncToArgb(bitmap: Bitmap) {
        syncMatToArgb(masterMat, bitmap)
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0
        }
    }

    companion object {
        /**
         * Fast JNI linear copy from ARGB Bitmap to any 1-channel Mat of matching size.
         */
        fun syncMatFromArgb(src: Bitmap, dstMat: Mat) {
            if (src.width != dstMat.cols() || src.height != dstMat.rows()) {
                throw IllegalArgumentException("Dimension mismatch: Bitmap=${src.width}x${src.height}, Mat=${dstMat.cols()}x${dstMat.rows()}")
            }
            nativeSyncMatFromArgb(src, dstMat.nativeObj)
        }

        /**
         * Fast JNI linear copy from any 1-channel Mat to ARGB Bitmap of matching size.
         */
        fun syncMatToArgb(srcMat: Mat, dst: Bitmap) {
            if (dst.width != srcMat.cols() || dst.height != srcMat.rows()) {
                throw IllegalArgumentException("Dimension mismatch: Mat=${srcMat.cols()}x${srcMat.rows()}, Bitmap=${dst.width}x${dst.height}")
            }
            nativeSyncMatToArgb(srcMat.nativeObj, dst)
        }

        var pool512x128: MemoryBridge? = null
            private set
        var pool320x48: MemoryBridge? = null
            private set

        /**
         * Global shared pools for refinement. 
         * Initialized EAGERLY on the main thread to avoid circular dependencies.
         */
        fun initializeGlobalPools() {
            if (pool512x128 != null) return
            
            android.util.Log.i("MemoryBridge", "Initializing global pools on thread: ${Thread.currentThread().name}")
            try {
                System.loadLibrary("memory_bridge")
                pool512x128 = MemoryBridge(512, 128)
                pool320x48 = MemoryBridge(320, 48)
                android.util.Log.i("MemoryBridge", "Global pools initialized successfully.")
            } catch (e: Exception) {
                android.util.Log.e("MemoryBridge", "Failed to initialize global pools", e)
            }
        }
    }
}
