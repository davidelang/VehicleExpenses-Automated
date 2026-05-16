package com.davidlang.vehicleexpensesautomated.ui.util

import org.opencv.core.Mat
import java.nio.ByteBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * BufferSet: A unified container for high-performance native image buffers.
 * Holds two Hunks (Primary and Scratch) that can be atomically flipped.
 * All views (Mat, ByteBuffer) share the same underlying memory.
 */
class BufferSet(private var width: Int, private var height: Int) {
    private val mutex = Mutex()
    private var primaryIdx = 0
    private val hunks = arrayOf(Hunk(), Hunk())

    inner class Hunk {
        private var nativeHandle: Long = 0
        private var mat: Mat? = null
        private var buffer: ByteBuffer? = null

        fun setup(w: Int, h: Int) {
            nativeHandle = nativeSetup(w, h)
            if (nativeHandle == 0L) throw IllegalStateException("Native setup failed for Hunk")
            refreshViews()
        }

        fun release() {
            if (nativeHandle != 0L) {
                nativeRelease(nativeHandle)
                nativeHandle = 0L
                mat = null
                buffer = null
            }
        }

        fun resize(w: Int, h: Int) {
            if (!nativeResize(nativeHandle, w, h)) {
                throw IllegalStateException("Native resize failed for Hunk")
            }
            refreshViews()
        }

        private fun refreshViews() {
            mat = Mat(nativeGetMatPtr(nativeHandle))
            buffer = nativeGetBuffer(nativeHandle)
        }

        val yMat: Mat get() = mat ?: throw IllegalStateException("Hunk not initialized")
        val nv21: ByteBuffer get() = buffer ?: throw IllegalStateException("Hunk not initialized")
    }

    init {
        hunks[0].setup(width, height)
        hunks[1].setup(width, height)
    }

    val primary: Hunk get() = hunks[primaryIdx]
    val scratch: Hunk get() = hunks[1 - primaryIdx]

    suspend fun flip() = mutex.withLock {
        primaryIdx = 1 - primaryIdx
    }

    suspend fun resize(w: Int, h: Int) = mutex.withLock {
        if (w == width && h == height) return
        hunks[0].resize(w, h)
        hunks[1].resize(w, h)
        width = w
        height = h
    }

    fun release() {
        hunks[0].release()
        hunks[1].release()
    }

    // JNI Bindings
    private external fun nativeSetup(w: Int, h: Int): Long
    private external fun nativeRelease(handle: Long)
    private external fun nativeResize(handle: Long, w: Int, h: Int): Boolean
    private external fun nativeGetMatPtr(handle: Long): Long
    private external fun nativeGetBuffer(handle: Long): ByteBuffer?

    companion object {
        init {
            System.loadLibrary("memory_bridge")
        }
    }
}
