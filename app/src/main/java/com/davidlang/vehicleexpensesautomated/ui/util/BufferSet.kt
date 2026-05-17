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

    internal data class CropDefinition(
        val x: Float, val y: Float, val w: Float, val h: Float, 
        val isNormalized: Boolean
    )

    private fun toEvenInt(v: Float): Int = ((v + 1).toInt() / 2) * 2

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

    inner class ManagedCrop internal constructor(internal val definition: CropDefinition) {
        var yMat: Mat? = null
            private set
        var uvMat: Mat? = null
            private set
        var yuv: YuvHandle? = null
            private set

        fun refresh(parentY: Mat, parentUV: Mat, parentW: Int, parentH: Int, parentNv21: ByteBuffer) {
            // Disarm old proxies to prevent GC crash
            yMat?.let { nativeDisarmMat(it) }
            uvMat?.let { nativeDisarmMat(it) }
            
            val rect = if (definition.isNormalized) {
                val rx = toEvenInt(definition.x * parentW).coerceIn(0, parentW - 2)
                val ry = toEvenInt(definition.y * parentH).coerceIn(0, parentH - 2)
                val rw = toEvenInt(definition.w * parentW).coerceIn(2, parentW - rx)
                val rh = toEvenInt(definition.h * parentH).coerceIn(2, parentH - ry)
                Rect(rx, ry, rw, rh)
            } else {
                val rx = toEvenInt(definition.x).coerceIn(0, parentW - 2)
                val ry = toEvenInt(definition.y).coerceIn(0, parentH - 2)
                val rw = toEvenInt(definition.w).coerceIn(2, parentW - rx)
                val rh = toEvenInt(definition.h).coerceIn(2, parentH - ry)
                Rect(rx, ry, rw, rh)
            }
            
            if (rect.width <= 0 || rect.height <= 0) {
                yMat = null; uvMat = null; yuv = null
                return
            }

            yMat = parentY.submat(rect)
            val uvRect = Rect(rect.x / 2, rect.y / 2, rect.width / 2, rect.height / 2)
            uvMat = parentUV.submat(uvRect)

            // Multi-Plane ROI Calculation
            val stride = parentW
            val yOffset = rect.y * stride + rect.x
            val uvOffset = (parentW * parentH) + (rect.y / 2 * stride) + rect.x
            
            yuv = YuvHandle(
                width = rect.width,
                height = rect.height,
                planes = arrayOf(
                    YuvHandle.Plane(parentNv21.duplicate().position(yOffset).slice() as ByteBuffer, stride, 1),
                    YuvHandle.Plane(parentNv21.duplicate().position(uvOffset + 1).slice() as ByteBuffer, stride, 2),
                    YuvHandle.Plane(parentNv21.duplicate().position(uvOffset).slice() as ByteBuffer, stride, 2)
                )
            )
        }

        fun release() {
            yMat?.let { nativeDisarmMat(it) }
            uvMat?.let { nativeDisarmMat(it) }
            yMat = null; uvMat = null; yuv = null
        }
    }

    inner class Instance {
        private var nativeHandle: Long = 0
        internal val nativeHandleInternal: Long get() = nativeHandle
        private var proxyY: Mat? = null
        private var proxyUV: Mat? = null
        private var buffer: ByteBuffer? = null
        var yuv: YuvHandle? = null
            private set

        fun setup(w: Int, h: Int) {
            nativeHandle = nativeSetup(w, h)
            if (nativeHandle == 0L) throw IllegalStateException("Native setup failed for Instance")
            refreshViews()
        }

        fun release() {
            if (nativeHandle != 0L) {
                proxyY?.let { nativeDisarmMat(it) }
                proxyUV?.let { nativeDisarmMat(it) }
                nativeRelease(nativeHandle, null)
                nativeHandle = 0L
                proxyY = null; proxyUV = null
                buffer = null; yuv = null
            }
        }

        fun resize(w: Int, h: Int) {
            if (nativeHandle != 0L) {
                proxyY?.let { nativeDisarmMat(it) }
                proxyUV?.let { nativeDisarmMat(it) }
            }
            if (!nativeResize(nativeHandle, w, h)) {
                throw IllegalStateException("Native resize failed for Instance")
            }
            refreshViews()
        }

        private fun refreshViews() {
            proxyY = Mat(nativeGetMatPtr(nativeHandle))
            proxyUV = Mat(nativeGetUVMatPtr(nativeHandle))
            buffer = nativeGetBuffer(nativeHandle)
            
            val buf = buffer!!
            val w = width
            val h = height
            yuv = YuvHandle(
                width = w, height = h,
                planes = arrayOf(
                    YuvHandle.Plane(buf.duplicate().position(0).slice() as ByteBuffer, w, 1),
                    YuvHandle.Plane(buf.duplicate().position(w * h + 1).slice() as ByteBuffer, w, 2),
                    YuvHandle.Plane(buf.duplicate().position(w * h).slice() as ByteBuffer, w, 2)
                )
            )
        }

        fun clear() {
            if (nativeHandle != 0L) {
                nativeClear(nativeHandle)
            }
        }

        suspend fun annotate(annotations: List<SnapshotAnnotation>, targetW: Int, targetH: Int, sourceW: Int, sourceH: Int) {
            val flat = IntArray(annotations.size * 7)
            annotations.forEachIndexed { i, ann ->
                val scaleX = targetW.toFloat() / sourceW.toFloat()
                val scaleY = targetH.toFloat() / sourceH.toFloat()
                flat[i * 7] = toEvenInt(ann.x1 * scaleX)
                flat[i * 7 + 1] = toEvenInt(ann.y1 * scaleY)
                flat[i * 7 + 2] = toEvenInt(ann.x2 * scaleX)
                flat[i * 7 + 3] = toEvenInt(ann.y2 * scaleY)
                flat[i * 7 + 4] = if (ann.shape == Shape.RECTANGLE) 1 else 0
                flat[i * 7 + 5] = ann.color
                flat[i * 7 + 6] = toEvenInt(ann.strokeWidth.toFloat()).coerceAtLeast(2)
            }
            nativeAnnotate(nativeHandle, flat)
        }

        val yMat: Mat get() = proxyY ?: throw IllegalStateException("Instance not initialized")
        val uvMat: Mat get() = proxyUV ?: throw IllegalStateException("Instance not initialized")
        val nv21: ByteBuffer get() = buffer ?: throw IllegalStateException("Instance not initialized")
        
        fun getYuvMat(): Mat = Mat(height * 3 / 2, width, org.opencv.core.CvType.CV_8UC1, nv21)
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
        normalizedCrops.values.forEach { it.refresh(primary.yMat, primary.uvMat, width, height, primary.nv21) }
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
        crop.refresh(primary.yMat, primary.uvMat, width, height, primary.nv21)
        managedCrops[id] = crop
        return id
    }

    fun createCropNormalized(x: Float, y: Float, w: Float, h: Float): Int {
        val id = nextCropId++
        val crop = ManagedCrop(CropDefinition(x, y, w, h, true))
        crop.refresh(primary.yMat, primary.uvMat, width, height, primary.nv21)
        managedCrops[id] = crop
        return id
    }

    fun createCropNormalizedWithId(id: Int, x: Float, y: Float, w: Float, h: Float) {
        val crop = ManagedCrop(CropDefinition(x, y, w, h, true))
        crop.refresh(primary.yMat, primary.uvMat, width, height, primary.nv21)
        managedCrops[id] = crop
    }

    fun getCropMat(id: Int): Mat {
        return managedCrops[id]?.yMat ?: throw IllegalArgumentException("Invalid or released crop ID: $id")
    }

    fun getCrop(id: Int): ManagedCrop {
        return managedCrops[id] ?: throw IllegalArgumentException("Invalid or released crop ID: $id")
    }

    fun releaseCrop(id: Int) {
        managedCrops.remove(id)?.release()
    }

    private fun refreshCrops() {
        managedCrops.values.forEach { it.refresh(primary.yMat, primary.uvMat, width, height, primary.nv21) }
    }

    // JNI Bindings
    private external fun nativeSetup(w: Int, h: Int): Long
    private external fun nativeRelease(handle: Long, matObj: Mat?)
    private external fun nativeClear(handle: Long)
    private external fun nativeResize(handle: Long, w: Int, h: Int): Boolean
    private external fun nativeRotate(src: Long, dst: Long, angle: Float)
    private external fun nativeAnnotate(handle: Long, annotations: IntArray)

    suspend fun rotate(angle: Float) = mutex.withLock {
        if (kotlin.math.abs(angle) < 0.01f) return
        nativeRotate(primary.nativeHandleInternal, scratch.nativeHandleInternal, angle)
        primaryIdx = 1 - primaryIdx
        refreshCrops()
    }

    suspend fun compressYuvToBase64(handle: YuvHandle, quality: Int): String = mutex.withLock {
        nativeCompressYuvToBase64(handle.planes[0].buffer, handle.planes[1].buffer, handle.planes[2].buffer, handle.width, handle.height, handle.planes[0].rowStride, quality)
    }
    private external fun nativeGetMatPtr(handle: Long): Long
    private external fun nativeGetUVMatPtr(handle: Long): Long
    private external fun nativeGetBuffer(handle: Long): ByteBuffer?
    private external fun nativeCompressYuvToBase64(yBuf: ByteBuffer, uBuf: ByteBuffer, vBuf: ByteBuffer, w: Int, h: Int, stride: Int, quality: Int): String

    companion object {
        init {
            System.loadLibrary("memory_bridge")
        }

        @JvmStatic
        private external fun nativeDisarmMat(matObj: Mat)
    }
}
