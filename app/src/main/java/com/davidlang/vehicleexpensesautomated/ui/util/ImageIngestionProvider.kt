package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File

data class IngestionMetadata(
    val originalWidth: Int,
    val originalHeight: Int,
    val decodedWidth: Int,
    val decodedHeight: Int,
    val format: String,
    val timeMs: Long,
    val isDegraded: Boolean = false,
    val diagnostic: String = ""
)

object ImageIngestionProvider {
    private const val TAG = "ImageIngestion"

    private class HeaderDecodedException(val width: Int, val height: Int, val mimeType: String) : Exception()

    /**
     * Probes the natural dimensions of an image file, bypassing thumbnails where possible.
     */
    fun probeDimensions(context: Context, path: String): Pair<Int, Int> {
        val ext = path.lowercase().substringAfterLast(".", "")
        if (ext == "dng") {
            try {
                val diag = NativeImageUtils.probeDngResolution(path)
                if (diag != "FAILED") {
                    val parts = diag.split("x")
                    if (parts.size == 2) {
                        return Pair(parts[0].toInt(), parts[1].toInt())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Native DNG probe failed for $path: ${e.message}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = if (path.startsWith("content://")) {
                ImageDecoder.createSource(context.contentResolver, Uri.parse(path))
            } else {
                ImageDecoder.createSource(File(path))
            }
            
            try {
                // Use ImageDecoder header listener to find TRUE raw sensor dimensions.
                ImageDecoder.decodeDrawable(source) { _, info, _ ->
                    throw HeaderDecodedException(info.size.width, info.size.height, info.mimeType)
                }
            } catch (e: HeaderDecodedException) {
                return Pair(e.width, e.height)
            } catch (e: Exception) {
                Log.w(TAG, "ImageDecoder probe failed for $path, falling back to BitmapFactory: ${e.message}")
            }
        }
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, options)
        return Pair(options.outWidth, options.outHeight)
    }

    /**
     * High-Fidelity Ingestion: Bypasses thumbnails and moves data into native YUV primary.
     * Phase 116: Supports dual A/B BufferSets with zero-allocation copying.
     */
    suspend fun ingestFromFile(
        context: Context,
        path: String,
        target: BufferSet.Slice
    ): IngestionMetadata {
        val t0 = System.currentTimeMillis()
        val file = java.io.File(path)
        val ext = file.extension.lowercase()
        
        // --- TYPE-AWARE DISPATCHER ---
        return when (ext) {
            "jpg", "jpeg" -> ingestJpeg(path, target, t0)
            "dng" -> ingestDng(context, path, target, t0)
            else -> ingestViaImageDecoder(context, path, target, t0) // Fallback for png etc
        }
    }

    private fun ingestDng(
        context: Context,
        path: String,
        target: BufferSet.Slice,
        startTime: Long
    ): IngestionMetadata {
        // We use LibRaw for dimensions as it's the source of truth for developed pixels
        val (probedW, probedH) = probeDimensions(context, path)

        // Step 1: Native Ingestion (Direct LibRaw -> YUV)
        NativeImageUtils.ingestDngToYuv(path, target)
        
        // Step 2: Stabilize state for monochrome-expecting logic
        target.clearChroma()
        
        return IngestionMetadata(
            probedW, probedH, 
            probedW, probedH, 
            "image/x-adobe-dng", 
            System.currentTimeMillis() - startTime,
            false,
            "LibRaw: ${probedW}x${probedH}"
        )
    }

    private fun ingestJpeg(
        path: String,
        target: BufferSet.Slice,
        startTime: Long
    ): IngestionMetadata {
        val diag = NativeImageUtils.testImread(path)
        if (diag == "FAILED_TO_LOAD") {
            throw Exception("Native imread failed for JPEG: $path")
        }
        
        // Parse "WxH channels:C"
        val parts = diag.split(" ")
        if (parts.isEmpty()) throw Exception("Invalid native diagnostic: $diag")
        
        val res = parts[0].split("x")
        if (res.size < 2) throw Exception("Invalid native resolution: ${parts[0]}")
        
        val w = res[0].toInt()
        val h = res[1].toInt()

        // Step 1: Native Ingestion (Direct imread -> YUV)
        NativeImageUtils.ingestJpegToYuv(path, target)
        
        // Step 2: Stabilize state
        target.clearChroma()
        
        return IngestionMetadata(
            w, h, 
            w, h, 
            "image/jpeg", 
            System.currentTimeMillis() - startTime,
            false,
            diag
        )
    }

    private fun ingestViaImageDecoder(
        context: Context,
        path: String,
        target: BufferSet.Slice,
        startTime: Long
    ): IngestionMetadata {
        val (probedW, probedH) = probeDimensions(context, path)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = if (path.startsWith("content://")) ImageDecoder.createSource(context.contentResolver, Uri.parse(path)) else ImageDecoder.createSource(File(path))
            var format = "unknown"
            val decodedBitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                format = info.mimeType
                if (probedW > info.size.width || probedH > info.size.height) decoder.setTargetSize(probedW, probedH)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }

            NativeImageUtils.ingestArgbToYuv(decodedBitmap, target)
            target.clearChroma()
            
            val meta = IngestionMetadata(probedW, probedH, decodedBitmap.width, decodedBitmap.height, format, System.currentTimeMillis() - startTime, decodedBitmap.width < probedW || decodedBitmap.height < probedH)
            decodedBitmap.recycle()
            return meta
        } else {
            val bmp = OdometerOcrUtils.decodeBitmapSafely(context, path) ?: throw Exception("Fallback decode failed")
            NativeImageUtils.ingestArgbToYuv(bmp, target)
            target.clearChroma()
            val meta = IngestionMetadata(bmp.width, bmp.height, bmp.width, bmp.height, "legacy", System.currentTimeMillis() - startTime, false)
            bmp.recycle()
            return meta
        }
    }
}
