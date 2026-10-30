package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import java.nio.ByteBuffer

/** Copy a full-size picked bitmap into a buffer set as planar YUV. Rotation stays 0. */
object FillFrameIngest {
    fun ingestFullBitmap(bufferSet: BufferSet, bitmap: Bitmap) {
        val srcW = bitmap.width
        val srcH = bitmap.height
        val w = srcW and 1.inv()
        val h = srcH and 1.inv()
        if (w <= 0 || h <= 0) throw IllegalArgumentException("picked image has no pixels")
        val pixels = IntArray(srcW * h)
        bitmap.getPixels(pixels, 0, srcW, 0, 0, srcW, h)
        val y = ByteArray(w * h)
        val uvW = w / 2
        val u = ByteArray(uvW * (h / 2))
        val v = ByteArray(u.size)
        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = pixels[j * srcW + i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                y[j * w + i] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val uv = (j / 2) * uvW + (i / 2)
                    u[uv] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    v[uv] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                }
            }
        }
        val yBuf = ByteBuffer.allocateDirect(y.size).put(y).apply { rewind() }
        val uBuf = ByteBuffer.allocateDirect(u.size).put(u).apply { rewind() }
        val vBuf = ByteBuffer.allocateDirect(v.size).put(v).apply { rewind() }
        bufferSet.ingestCameraFrame(yBuf, uBuf, vBuf, w, uvW, 1, 1, w, h)
    }
}
