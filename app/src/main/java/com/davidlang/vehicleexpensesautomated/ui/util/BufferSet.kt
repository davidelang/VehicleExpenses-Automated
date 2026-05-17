package com.davidlang.vehicleexpensesautomated.ui.util

import org.opencv.core.Mat
import org.opencv.core.Rect
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
    private val instances = arrayOf(Instance(), Instance())
    
    // Managed Crops
    private val managedCrops = mutableMapOf<Int, ManagedCrop>()
    private var nextCropId = 1000

    private data class CropDefinition(
        val x: Float, val y: Float, val w: Float, val h: Float, 
        val isNormalized: Boolean
    )

    private inner class ManagedCrop(val definition: CropDefinition) {
        var proxyMat: Mat? = null

        fun refresh(parentMat: Mat, parentW: Int, parentH: Int) {
            // Disarm old proxy to prevent GC crash
            proxyMat?.let { nativeDisarmMat(it) }
            
            val rect = if (definition.isNormalized) {
                Rect(
                    (definition.x * parentW).toInt().coerceIn(0, parentW - 1),
                    (definition.y * parentH).toInt().coerceIn(0, parentH - 1),
                    (definition.w * parentW).toInt().coerceAtMost(parentW),
                    (definition.h * parentH).toInt().coerceAtMost(parentH)
                )
            } else {
                Rect(
                    definition.x.toInt().coerceIn(0, parentW - 1),
                    definition.y.toInt().coerceIn(0, parentH - 1),
                    definition.w.toInt().coerceAtMost(parentW),
                    definition.h.toInt().coerceAtMost(parentH)
                )
            }
            
            // Boundary safety check for OpenCV submat
            val safeW = rect.width.coerceAtMost(parentW - rect.x)
            val safeH = rect.height.coerceAtMost(parentH - rect.y)
            if (safeW <= 0 || safeH <= 0) {
                proxyMat = null
                return
            }

            proxyMat = parentMat.submat(Rect(rect.x, rect.y, safeW, safeH))
        }

        fun release() {
            proxyMat?.let { nativeDisarmMat(it) }
            proxyMat = null
        }
    }

    inner class Instance {
        private var nativeHandle: Long = 0
        internal val nativeHandleInternal: Long get() = nativeHandle
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

        fun clear() {
            if (nativeHandle != 0L) {
                nativeClear(nativeHandle)
            }
        }

        val yMat: Mat get() = proxyMat ?: throw IllegalStateException("Instance not initialized")
        val nv21: ByteBuffer get() = buffer ?: throw IllegalStateException("Instance not initialized")
    }

    init {
        instances[0].setup(width, height)
        instances[1].setup(width, height)
    }

    val primary: Instance get() = instances[primaryIdx]
    val scratch: Instance get() = instances[1 - primaryIdx]

    suspend fun flip() = mutex.withLock {
        primaryIdx = 1 - primaryIdx
        refreshCrops()
    }

    suspend fun resize(w: Int, h: Int) = mutex.withLock {
        if (w == width && h == height) return
        
        // Preserve normalized crops, kill absolute ones
        val normalizedCrops = managedCrops.filter { it.value.definition.isNormalized }
        val absoluteCrops = managedCrops.filter { !it.value.definition.isNormalized }
        
        absoluteCrops.values.forEach { it.release() }
        managedCrops.keys.removeAll(absoluteCrops.keys)
        
        // Disarm normalized proxies before memory reallocation
        normalizedCrops.values.forEach { it.release() }

        instances[0].resize(w, h)
        instances[1].resize(w, h)
        width = w
        height = h

        // Re-project normalized crops onto the new dimensions
        normalizedCrops.values.forEach { it.refresh(primary.yMat, width, height) }
    }

    fun release() {
        managedCrops.values.forEach { it.release() }
        managedCrops.clear()
        instances[0].release()
        instances[1].release()
    }

    // Crop API
    fun createCrop(x: Int, y: Int, w: Int, h: Int): Int {
        val id = nextCropId++
        val crop = ManagedCrop(CropDefinition(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), false))
        crop.refresh(primary.yMat, width, height)
        managedCrops[id] = crop
        return id
    }

    fun createCropNormalized(x: Float, y: Float, w: Float, h: Float): Int {
        val id = nextCropId++
        val crop = ManagedCrop(CropDefinition(x, y, w, h, true))
        crop.refresh(primary.yMat, width, height)
        managedCrops[id] = crop
        return id
    }

    fun createCropNormalizedWithId(id: Int, x: Float, y: Float, w: Float, h: Float) {
        val crop = ManagedCrop(CropDefinition(x, y, w, h, true))
        crop.refresh(primary.yMat, width, height)
        managedCrops[id] = crop
    }

    fun getCropMat(id: Int): Mat {
        return managedCrops[id]?.proxyMat ?: throw IllegalArgumentException("Invalid or released crop ID: $id")
    }

    fun releaseCrop(id: Int) {
        managedCrops.remove(id)?.release()
    }

    private fun refreshCrops() {
        managedCrops.values.forEach { it.refresh(primary.yMat, width, height) }
    }

    // JNI Bindings
    private external fun nativeSetup(w: Int, h: Int): Long
    private external fun nativeRelease(handle: Long, matObj: Mat?)
    private external fun nativeClear(handle: Long)
    private external fun nativeResize(handle: Long, w: Int, h: Int): Boolean
    private external fun nativeRotate(src: Long, dst: Long, angle: Float)

    suspend fun rotate(angle: Float) = mutex.withLock {
        if (kotlin.math.abs(angle) < 0.01f) return
        nativeRotate(primary.nativeHandleInternal, scratch.nativeHandleInternal, angle)
        primaryIdx = 1 - primaryIdx
        refreshCrops()
    }
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
