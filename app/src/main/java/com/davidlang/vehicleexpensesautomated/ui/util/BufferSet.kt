package com.davidlang.vehicleexpensesautomated.ui.util

import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import java.nio.ByteBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * BufferSet: Modern handle-centric memory manager for native image buffers.
 * Implements the Slice hierarchy for Primary (p) and Scratch (s) roles.
 * ROI: Region of Interest. A specific sub-section of an image buffer, also known as a Crop.
 */
class BufferSet(internal var _width: Int, internal var _height: Int) {
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
        val yMat: Mat get() = mat
        val uvMat: Mat    // Chroma (8UC2 interleaved)
        val nv21: ByteBuffer // Contiguous 1.5x Byte hunk (Instances only)
        val raw: ByteBuffer  // Luma-only 1.0x contiguous Byte hunk
        val nv21Mat: Mat     // Single Mat view of 1.5x RAM (Instances only)
        val yuv: YuvHandle   // Standard multi-plane descriptor
        val width: Int
        val height: Int
        
        // Creation & Lifecycle
        fun createCrop(x: Int, y: Int, w: Int, h: Int, id: Int? = null): Int
        fun createCrop(x: Float, y: Float, w: Float, h: Float, id: Int? = null): Int
        fun resize(x: Int, y: Int, w: Int, h: Int)
        fun resize(x: Float, y: Float, w: Float, h: Float)
        fun release()
        
        // Operations
        fun clear()
        fun clearChroma()
    }

    inner class CropRegistry {
        operator fun get(id: Int): Slice = managedCrops[id] ?: throw IllegalArgumentException("Invalid crop ID: $id")
    }

    // Manager Properties
    val p: Slice get() = instances[primaryIdx]
    val primary: Slice get() = p
    val s: Slice get() = instances[1 - primaryIdx]
    val scratch: Slice get() = s
    val secondary: Slice get() = s
    
    val crop = CropRegistry()
    val c = crop
    
    val width: Int get() = _width
    val height: Int get() = _height

    // Manager Functions
    suspend fun flip() = mutex.withLock {
        primaryIdx = 1 - primaryIdx
        val newOwner = instances[primaryIdx] as Instance
        managedCrops.values.forEach { 
            it.owner = newOwner
            it.rebindToOwner()
        }
    }

    suspend fun resize(w: Int, h: Int) = mutex.withLock {
        if (w == _width && h == _height) return
        instances[0].physicalResize(w, h)
        instances[1].physicalResize(w, h)
        _width = w
        _height = h
        
        // Lifecycle Rule: Preserved Normalized, drop Pixel
        val toRemove = mutableListOf<Int>()
        managedCrops.forEach { (id, c) ->
            if (c.isNormalized) {
                c.refresh()
            } else {
                toRemove.add(id)
            }
        }
        toRemove.forEach { managedCrops.remove(it)?.disarm() }
    }

    suspend fun normalizeYUV() = mutex.withLock {
        val src = p.yuv
        val dstHandle = (s as Instance).nativePtr
        
        nativeNormalizeYUV(
            src.planes[0].buffer, src.planes[1].buffer, src.planes[2].buffer,
            src.planes[0].rowStride, src.planes[1].rowStride, src.planes[2].rowStride,
            src.planes[0].pixelStride, src.planes[1].pixelStride, src.planes[2].pixelStride,
            _width, _height, dstHandle
        )
        flip()
    }

    fun createCrop(x: Int, y: Int, w: Int, h: Int, id: Int? = null): Int = p.createCrop(x, y, w, h, id)
    fun createCrop(x: Float, y: Float, w: Float, h: Float, id: Int? = null): Int = p.createCrop(x, y, w, h, id)

    fun release() {
        managedCrops.values.forEach { it.disarm() }
        managedCrops.clear()
        instances[0].physicalRelease()
        instances[1].physicalRelease()
    }

    abstract class YuvHandle {
        abstract val width: Int
        abstract val height: Int
        val format: Int = 35
        abstract val planes: Array<Plane>
        data class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)
    }

    private fun registerCrop(crop: ManagedCrop, id: Int?): Int {
        val cid = id ?: nextCropId++
        managedCrops[cid]?.disarm()
        managedCrops[cid] = crop
        return cid
    }

    inner class Instance : Slice {
        private var nativeHandle: Long = 0
        val nativePtr: Long get() = nativeHandle
        private var _mat: Mat? = null
        private var _uvMat: Mat? = null
        private var _nv21Mat: Mat? = null
        private var _buffer: ByteBuffer? = null

        override val mat: Mat get() = _mat ?: throw IllegalStateException("Not initialized")
        override val uvMat: Mat get() = _uvMat ?: throw IllegalStateException("Not initialized")
        override val nv21: ByteBuffer get() = _buffer?.duplicate()?.position(0) as ByteBuffer
        override val raw: ByteBuffer get() = (_buffer?.duplicate()?.position(0)?.limit(_width * _height) as ByteBuffer).slice()
        override val nv21Mat: Mat get() = _nv21Mat ?: throw IllegalStateException("Not initialized")
        override val width: Int get() = _width
        override val height: Int get() = _height

        private val _yuv = object : YuvHandle() {
            override val width: Int get() = _width
            override val height: Int get() = _height
            override val planes: Array<Plane> get() {
                val buf = _buffer ?: throw IllegalStateException("Not initialized")
                return arrayOf(
                    Plane((buf.duplicate().position(0) as ByteBuffer).slice(), _width, 1),
                    Plane((buf.duplicate().position(_width * _height + 1) as ByteBuffer).slice(), _width, 2),
                    Plane((buf.duplicate().position(_width * _height) as ByteBuffer).slice(), _width, 2)
                )
            }
        }
        override val yuv: YuvHandle get() = _yuv

        fun setup(w: Int, h: Int) { nativeHandle = nativeSetup(w, h); refreshViews() }
        fun physicalResize(w: Int, h: Int) { nativeResize(nativeHandle, w, h); _buffer = nativeGetBuffer(nativeHandle) }
        private fun refreshViews() {
            _mat = Mat(nativeGetMatPtr(nativeHandle))
            _uvMat = Mat(nativeGetUVMatPtr(nativeHandle))
            _nv21Mat = Mat(nativeGetNv21MatPtr(nativeHandle))
            _buffer = nativeGetBuffer(nativeHandle)
        }
        private fun disarm() {
            _mat?.let { nativeDisarmMat(it) }; _uvMat?.let { nativeDisarmMat(it) }; _nv21Mat?.let { nativeDisarmMat(it) }
            _mat = null; _uvMat = null; _nv21Mat = null; _buffer = null
        }
        fun physicalRelease() { disarm(); if (nativeHandle != 0L) { nativeRelease(nativeHandle); nativeHandle = 0 } }

        override fun createCrop(x: Int, y: Int, w: Int, h: Int, id: Int?): Int {
            val crop = ManagedCrop(this, false, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
            crop.refresh(); return registerCrop(crop, id)
        }
        override fun createCrop(x: Float, y: Float, w: Float, h: Float, id: Int?): Int {
            val crop = ManagedCrop(this, true, x, y, w, h)
            crop.refresh(); return registerCrop(crop, id)
        }
        override fun resize(x: Int, y: Int, w: Int, h: Int) = throw UnsupportedOperationException("Base Primary/Scratch buffers cannot be resized via Slice interface. Use BufferSet.resize().")
        override fun resize(x: Float, y: Float, w: Float, h: Float) = throw UnsupportedOperationException("Base Primary/Scratch buffers cannot be resized via Slice interface. Use BufferSet.resize().")
        override fun release() = throw UnsupportedOperationException("Base Primary/Scratch buffers cannot be released via Slice interface. Use BufferSet.release().")
        override fun clear() = nativeClear(nativeHandle)
        override fun clearChroma() = nativeClearChroma(nativeHandle)
    }

    inner class ManagedCrop(
        internal var owner: Instance,
        internal var isNormalized: Boolean,
        private var rawX: Float, private var rawY: Float, private var rawW: Float, private var rawH: Float
    ) : Slice {
        private var _mat: Mat? = null
        private var _uvMat: Mat? = null
        private var absX = 0; private var absY = 0; private var absW = 0; private var absH = 0

        override val mat: Mat get() = _mat ?: throw IllegalStateException("Disarmed")
        override val uvMat: Mat get() = _uvMat ?: throw IllegalStateException("Disarmed")
        override val nv21: ByteBuffer get() = throw UnsupportedOperationException("nv21 contiguous access is not supported on Crops.")
        override val raw: ByteBuffer get() {
            val parentBuf = owner.raw
            return (parentBuf.duplicate().position(absY * _width + absX) as ByteBuffer).slice()
        }
        override val nv21Mat: Mat get() = throw UnsupportedOperationException("nv21Mat access is not supported on Crops.")
        override val width: Int get() = absW
        override val height: Int get() = absH

        private val _yuv = object : YuvHandle() {
            override val width: Int get() = absW
            override val height: Int get() = absH
            override val planes: Array<Plane> get() {
                val fullBuf = owner.nv21
                val uvOffset = (_width * _height) + (absY / 2 * _width) + absX
                return arrayOf(
                    Plane((fullBuf.duplicate().position(absY * _width + absX) as ByteBuffer).slice(), _width, 1),
                    Plane((fullBuf.duplicate().position(uvOffset + 1) as ByteBuffer).slice(), _width, 2),
                    Plane((fullBuf.duplicate().position(uvOffset) as ByteBuffer).slice(), _width, 2)
                )
            }
        }
        override val yuv: YuvHandle get() = _yuv

        fun rebindToOwner() {
            val matPtr = _mat?.nativeObj ?: return
            val uvMatPtr = _uvMat?.nativeObj ?: return
            val lumaOffset = absY * _width + absX
            val chromaOffset = (_width * _height) + (absY / 2 * _width) + absX
            nativeUpdateMatData(matPtr, owner.mat.nativeObj, lumaOffset)
            nativeUpdateMatData(uvMatPtr, owner.uvMat.nativeObj, chromaOffset)
        }

        fun refresh() {
            val (px, py, pw, ph) = if (isNormalized) {
                listOf((rawX * _width).toInt(), (rawY * _height).toInt(), (rawW * _width).toInt(), (rawH * _height).toInt())
            } else {
                listOf(rawX.toInt(), rawY.toInt(), rawW.toInt(), rawH.toInt())
            }
            absX = (px / 2) * 2; absY = (py / 2) * 2
            val x2 = ((px + pw + 1) / 2) * 2; val y2 = ((py + ph + 1) / 2) * 2
            absW = (x2 - absX).coerceIn(2, _width - absX); absH = (y2 - absY).coerceIn(2, _height - absY)
            
            val curMat = _mat
            val curUvMat = _uvMat
            if (curMat == null || curUvMat == null) {
                _mat = owner.mat.submat(Rect(absX, absY, absW, absH))
                _uvMat = owner.uvMat.submat(Rect(absX / 2, absY / 2, absW / 2, absH / 2))
            } else {
                nativeUpdateCropMat(curMat.nativeObj, owner.mat.nativeObj, absX, absY, absW, absH)
                nativeUpdateCropMat(curUvMat.nativeObj, owner.uvMat.nativeObj, absX / 2, absY / 2, absW / 2, absH / 2)
            }
        }

        override fun createCrop(x: Int, y: Int, w: Int, h: Int, id: Int?): Int {
            Log.w("BufferSet", "Nested crop creation is discouraged (flattening used). Use at your own risk.")
            return registerCrop(ManagedCrop(owner, false, (absX + x).toFloat(), (absY + y).toFloat(), w.toFloat(), h.toFloat()), id)
        }
        override fun createCrop(x: Float, y: Float, w: Float, h: Float, id: Int?): Int {
            Log.w("BufferSet", "Nested crop creation is discouraged (flattening used). Use at your own risk.")
            return registerCrop(ManagedCrop(owner, false, absX + (x * absW), absY + (y * absH), w * absW, h * absH), id)
        }
        override fun resize(x: Int, y: Int, w: Int, h: Int) { isNormalized = false; rawX = x.toFloat(); rawY = y.toFloat(); rawW = w.toFloat(); rawH = h.toFloat(); refresh() }
        override fun resize(x: Float, y: Float, w: Float, h: Float) { isNormalized = true; rawX = x; rawY = y; rawW = w; rawH = h; refresh() }
        override fun release() { 
            managedCrops.values.remove(this)
            _mat?.release()
            _uvMat?.release()
            _mat = null
            _uvMat = null
        }
        internal fun disarm() { _mat?.let { nativeDisarmMat(it) }; _uvMat?.let { nativeDisarmMat(it) }; _mat = null; _uvMat = null }
        override fun clear() { mat.setTo(Scalar(0.0)); clearChroma() }
        override fun clearChroma() { uvMat.setTo(Scalar(128.0, 128.0)) }
    }

    init { instances[0].setup(_width, _height); instances[1].setup(_width, _height) }
    private external fun nativeSetup(w: Int, h: Int): Long
    private external fun nativeRelease(handle: Long)
    private external fun nativeResize(handle: Long, w: Int, h: Int)
    private external fun nativeClear(handle: Long)
    private external fun nativeClearChroma(handle: Long)
    private external fun nativeGetMatPtr(handle: Long): Long
    private external fun nativeGetUVMatPtr(handle: Long): Long
    private external fun nativeGetNv21MatPtr(handle: Long): Long
    private external fun nativeGetBuffer(handle: Long): ByteBuffer?
    private external fun nativeUpdateMatData(matPtr: Long, parentMatPtr: Long, byteOffset: Int)
    private external fun nativeUpdateCropMat(cropMatPtr: Long, parentMatPtr: Long, x: Int, y: Int, w: Int, h: Int)
    private external fun nativeNormalizeYUV(yBuf: ByteBuffer, uBuf: ByteBuffer, vBuf: ByteBuffer, yRStride: Int, uRStride: Int, vRStride: Int, yPStride: Int, uPStride: Int, vPStride: Int, w: Int, h: Int, dstHandle: Long)
    companion object {
        init { System.loadLibrary("memory_bridge") }
        @JvmStatic private external fun nativeDisarmMat(matObj: Mat)
    }
}
