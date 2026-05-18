package com.davidlang.vehicleexpensesautomated.ui.util

import org.opencv.core.Mat
import org.opencv.core.Rect
import java.nio.ByteBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * BufferSet: Modern handle-centric memory manager for native image buffers.
 * Implements the Slice hierarchy for Primary (p) and Scratch (s) roles.
 */
class BufferSet(private var width: Int, private var height: Int) {
    private val mutex = Mutex()
    private var primaryIdx = 0
    private val instances = arrayOf(Instance(), Instance())
    private val managedCrops = mutableMapOf<Int, ManagedCrop>()
    private var nextCropId = 1000

    /**
     * Slice represents a consistent view of a logical image hunk.
     */
    interface Slice {
        val mat: Mat      // Luma (8UC1)
        val uvMat: Mat    // Chroma (8UC2 interleaved)
        val nv21: Mat     // Full 1.5x Mat (Instance only)
        val raw: ByteBuffer // Luma-only raw buffer
        val yuv: YuvHandle  // Standard multi-plane descriptor
        val width: Int
        val height: Int
        fun clear()
        fun clearChroma()
    }

    data class YuvHandle(
        val width: Int,
        val height: Int,
        val format: Int = 35, // ImageFormat.YUV_420_888
        val planes: Array<Plane>
    ) {
        data class Plane(
            val buffer: ByteBuffer,
            val rowStride: Int,
            val pixelStride: Int
        )
    }

    /**
     * Computed logical handles for atomic role-based access.
     */
    val p: Slice get() = instances[primaryIdx]
    val s: Slice get() = instances[1 - primaryIdx]

    inner class Instance : Slice {
        private var nativeHandle: Long = 0
        private var _mat: Mat? = null
        private var _uvMat: Mat? = null
        private var _nv21: Mat? = null
        private var _buffer: ByteBuffer? = null
        
        // Internal access to full 1.5x buffer for crop slicing
        internal val fullBuffer: ByteBuffer get() = _buffer ?: throw IllegalStateException("Not initialized")

        override val mat: Mat get() = _mat ?: throw IllegalStateException("Not initialized")
        override val uvMat: Mat get() = _uvMat ?: throw IllegalStateException("Not initialized")
        override val nv21: Mat get() = _nv21 ?: throw IllegalStateException("Not initialized")
        override val raw: ByteBuffer get() = (fullBuffer.duplicate().position(0).limit(width * height) as ByteBuffer).slice()
        override val width: Int get() = this@BufferSet.width
        override val height: Int get() = this@BufferSet.height

        override val yuv: YuvHandle get() {
            val buf = fullBuffer
            val w = width
            val h = height
            return YuvHandle(
                width = w, height = h,
                planes = arrayOf(
                    YuvHandle.Plane((buf.duplicate().position(0) as ByteBuffer).slice(), w, 1),
                    YuvHandle.Plane((buf.duplicate().position(w * h + 1) as ByteBuffer).slice(), w, 2),
                    YuvHandle.Plane((buf.duplicate().position(w * h) as ByteBuffer).slice(), w, 2)
                )
            )
        }

        fun setup(w: Int, h: Int) {
            nativeHandle = nativeSetup(w, h)
            refreshViews()
        }

        fun resize(w: Int, h: Int) {
            disarm()
            nativeResize(nativeHandle, w, h)
            refreshViews()
        }

        private fun refreshViews() {
            _mat = Mat(nativeGetMatPtr(nativeHandle))
            _uvMat = Mat(nativeGetUVMatPtr(nativeHandle))
            _nv21 = Mat(nativeGetNv21MatPtr(nativeHandle))
            _buffer = nativeGetBuffer(nativeHandle)
        }

        private fun disarm() {
            _mat?.let { nativeDisarmMat(it) }
            _uvMat?.let { nativeDisarmMat(it) }
            _nv21?.let { nativeDisarmMat(it) }
            _mat = null; _uvMat = null; _nv21 = null; _buffer = null
        }

        fun release() {
            disarm()
            nativeRelease(nativeHandle)
            nativeHandle = 0
        }

        override fun clear() = nativeClear(nativeHandle)
        override fun clearChroma() = nativeClearChroma(nativeHandle)
    }

    inner class ManagedCrop(val x: Int, val y: Int, override val width: Int, override val height: Int) : Slice {
        private var _mat: Mat? = null
        private var _uvMat: Mat? = null

        override val mat: Mat get() = _mat ?: throw IllegalStateException("Disarmed")
        override val uvMat: Mat get() = _uvMat ?: throw IllegalStateException("Disarmed")
        override val nv21: Mat get() = throw UnsupportedOperationException("NV21 Mat view not supported for non-contiguous crops")
        
        override val raw: ByteBuffer get() {
            val parentBuf = (instances[primaryIdx] as Instance).fullBuffer
            val stride = this@BufferSet.width
            val offset = y * stride + x
            // Note: This ByteBuffer is non-contiguous if width < stride, but provides correct start
            return (parentBuf.duplicate().position(offset) as ByteBuffer).slice()
        }

        override val yuv: YuvHandle get() {
            val fullBuf = (instances[primaryIdx] as Instance).fullBuffer
            val stride = this@BufferSet.width
            val yOffset = y * stride + x
            val uvOffset = (this@BufferSet.width * this@BufferSet.height) + (y / 2 * stride) + x
            
            return YuvHandle(
                width = width, height = height,
                planes = arrayOf(
                    YuvHandle.Plane((fullBuf.duplicate().position(yOffset) as ByteBuffer).slice(), stride, 1),
                    YuvHandle.Plane((fullBuf.duplicate().position(uvOffset + 1) as ByteBuffer).slice(), stride, 2),
                    YuvHandle.Plane((fullBuf.duplicate().position(uvOffset) as ByteBuffer).slice(), stride, 2)
                )
            )
        }

        fun refresh() {
            _mat?.let { nativeDisarmMat(it) }
            _uvMat?.let { nativeDisarmMat(it) }
            _mat = instances[primaryIdx].mat.submat(Rect(x, y, width, height))
            _uvMat = instances[primaryIdx].uvMat.submat(Rect(x / 2, y / 2, width / 2, height / 2))
        }

        fun release() {
            _mat?.let { nativeDisarmMat(it) }
            _uvMat?.let { nativeDisarmMat(it) }
            _mat = null; _uvMat = null
        }

        override fun clear() {
            mat.setTo(org.opencv.core.Scalar(0.0))
        }

        override fun clearChroma() {
            uvMat.setTo(org.opencv.core.Scalar(128.0, 128.0))
        }
    }

    init {
        instances[0].setup(width, height)
        instances[1].setup(width, height)
    }

    suspend fun flip() = mutex.withLock {
        primaryIdx = 1 - primaryIdx
        managedCrops.values.forEach { it.refresh() }
    }

    suspend fun resize(w: Int, h: Int) = mutex.withLock {
        if (w == width && h == height) return
        instances[0].resize(w, h)
        instances[1].resize(w, h)
        width = w
        height = h
        managedCrops.values.forEach { it.refresh() }
    }

    fun release() {
        managedCrops.values.forEach { it.release() }
        instances[0].release()
        instances[1].release()
    }

    fun createCrop(x: Int, y: Int, w: Int, h: Int): Int {
        val id = nextCropId++
        val evenX = (x / 2) * 2
        val evenY = (y / 2) * 2
        val evenW = (w / 2) * 2
        val evenH = (h / 2) * 2
        val crop = ManagedCrop(evenX, evenY, evenW, evenH)
        crop.refresh()
        managedCrops[id] = crop
        return id
    }

    fun createCropNormalized(x: Float, y: Float, w: Float, h: Float): Int {
        return createCrop((x * width).toInt(), (y * height).toInt(), (w * width).toInt(), (h * height).toInt())
    }

    operator fun get(id: Int): Slice = managedCrops[id] ?: throw IllegalArgumentException("Invalid crop ID")

    fun releaseCrop(id: Int) {
        managedCrops.remove(id)?.release()
    }

    // Native JNI
    private external fun nativeSetup(w: Int, h: Int): Long
    private external fun nativeRelease(handle: Long)
    private external fun nativeResize(handle: Long, w: Int, h: Int)
    private external fun nativeClear(handle: Long)
    private external fun nativeClearChroma(handle: Long)
    private external fun nativeGetMatPtr(handle: Long): Long
    private external fun nativeGetUVMatPtr(handle: Long): Long
    private external fun nativeGetNv21MatPtr(handle: Long): Long
    private external fun nativeGetBuffer(handle: Long): ByteBuffer?

    companion object {
        init {
            System.loadLibrary("memory_bridge")
        }
        @JvmStatic
        private external fun nativeDisarmMat(matObj: Mat)
    }
}
