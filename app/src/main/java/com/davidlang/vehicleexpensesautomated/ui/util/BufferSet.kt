package com.davidlang.vehicleexpensesautomated.ui.util

import org.opencv.core.Mat
import java.nio.ByteBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * BufferSet: A unified container for high-performance native image buffers.
 * Holds two Instances (Primary and Scratch) that can be atomically flipped.
 * All views (Mat, ByteBuffer) share the same underlying memory.
 */
class BufferSet(private var width: Int, private var height: Int) {
    private val mutex = Mutex()
    private var primaryIdx = 0
    private val hunks = arrayOf(Instance(), Instance())

    inner class Instance {
        private var nativeHandle: Long = 0
        private var proxyMat: Mat? = null
        private var buffer: ByteBuffer? = null

        fun setup(w: Int, h: Int) {
            nativeHandle = nativeSetup(w, h)
            if (nativeHandle == 0L) throw IllegalStateException("Native setup failed for Instance")
            refreshViews()
        }

        fun release() {
            if (nativeHandle != 0L) {
                proxyMat?.let { nativeDisarmMat(it) }
                nativeRelease(nativeHandle, null)
                nativeHandle = 0L
                proxyMat = null
                buffer = null
            }
        }

        fun resize(w: Int, h: Int) {
            if (nativeHandle != 0L) {
                proxyMat?.let { nativeDisarmMat(it) }
            }
            if (!nativeResize(nativeHandle, w, h)) {
                throw IllegalStateException("Native resize failed for Instance")
            }
            refreshViews()
        }

        private fun refreshViews() {
            proxyMat = Mat(nativeGetMatPtr(nativeHandle))
            buffer = nativeGetBuffer(nativeHandle)
        }

        val yMat: Mat get() = proxyMat ?: throw IllegalStateException("Instance not initialized")
        val nv21: ByteBuffer get() = buffer ?: throw IllegalStateException("Instance not initialized")
    private val instances = arrayOf(Instance(), Instance())

    init {
        instances[0].setup(width, height)
        instances[1].setup(width, height)
    }

    val primary: Instance get() = instances[primaryIdx]
    val scratch: Instance get() = instances[1 - primaryIdx]

    suspend fun flip() = mutex.withLock {
        primaryIdx = 1 - primaryIdx
    }

    suspend fun resize(w: Int, h: Int) = mutex.withLock {
        if (w == width && h == height) return
        instances[0].resize(w, h)
        instances[1].resize(w, h)
        width = w
        height = h
    }

    fun release() {
        instances[0].release()
        instances[1].release()
    }


    // JNI Bindings
    private external fun nativeSetup(w: Int, h: Int): Long
    private external fun nativeRelease(handle: Long, matObj: Mat?)
    private external fun nativeResize(handle: Long, w: Int, h: Int): Boolean
    private external fun nativeGetMatPtr(handle: Long): Long
    private external fun nativeGetBuffer(handle: Long): ByteBuffer?

    companion object {
        init {
            System.loadLibrary("memory_bridge")
        }

        @JvmStatic
        private external fun nativeDisarmMat(matObj: Mat)
    }
}
