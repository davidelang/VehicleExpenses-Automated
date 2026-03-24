package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Lightweight Average Hash (aHash) + Hamming distance
 * Perfect for comparing dashboard/odometer photos on-device.
 * No extra libraries, fast, works great for this exact use case.
 */
object ImageHashUtils {

    private const val HASH_SIZE = 8

    /**
     * Computes 64-bit Average Hash from a Bitmap.
     */
    fun computeAverageHash(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, HASH_SIZE, HASH_SIZE, false)
        val pixels = IntArray(HASH_SIZE * HASH_SIZE)
        small.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE)

        var totalGray = 0L
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            totalGray += (r + g + b) / 3
        }
        val avgGray = totalGray / pixels.size

        var hash = 0L
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (r + g + b) / 3
            hash = hash shl 1
            if (gray > avgGray) hash = hash or 1L
        }
        small.recycle()
        return hash
    }

    /**
     * Hamming distance between two 64-bit hashes.
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        var diff = hash1 xor hash2
        var count = 0
        while (diff != 0L) {
            count += (diff and 1L).toInt()
            diff = diff shr 1
        }
        return count
    }

    /**
     * Similarity score 0.0–1.0 (1.0 = identical).
     */
    fun similarity(hash1: Long, hash2: Long): Double =
        1.0 - (hammingDistance(hash1, hash2).toDouble() / 64.0)

    /**
     * Convenience: compute hash directly from the absolute file path returned by PhotoPicker.
     */
    fun computeHashFromFilePath(photoPath: String): Long? {
        val file = File(photoPath)
        if (!file.exists()) return null

        val bitmap = BitmapFactory.decodeFile(photoPath) ?: return null
        val hash = computeAverageHash(bitmap)
        bitmap.recycle()
        return hash
    }
}
