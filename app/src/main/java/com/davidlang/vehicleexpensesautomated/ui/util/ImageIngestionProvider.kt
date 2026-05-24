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
        targetA: BufferSet,
        targetB: BufferSet,
        scratchBmp: Bitmap?, 
        masterBmp: Bitmap
    ): IngestionMetadata {
        val t0 = System.currentTimeMillis()
        val file = java.io.File(path)
        val ext = file.extension.lowercase()
        
        // --- TYPE-AWARE DISPATCHER ---
        val meta = when (ext) {
            "jpg", "jpeg" -> ingestViaNativeJpeg(path, targetA, masterBmp, t0)
            "dng" -> ingestViaNativeDng(context, path, targetA, masterBmp, t0)
            else -> ingestViaImageDecoder(context, path, targetA, masterBmp, t0) // Fallback for png etc
        }

        // Phase 116: Dual Buffer Sync
        // Duplicate the primary data from A to B (Zero-allocation copy)
        targetA.p.mat.copyTo(targetB.p.mat)
        targetA.p.uvMat.copyTo(targetB.p.uvMat)
        
        return meta
    }

    private fun ingestViaNativeDng(
        context: Context,
        path: String,
        target: BufferSet,
        masterBmp: Bitmap,
        startTime: Long
    ): IngestionMetadata {
        // We use LibRaw for dimensions as it's the source of truth for developed pixels
        val (probedW, probedH) = probeDimensions(context, path)

        // Step 1: Native Ingestion (Direct LibRaw -> YUV)
        NativeImageUtils.ingestDngToYuv(path, target.p)
        
        // Step 2: Stabilize state for monochrome-expecting logic
        target.p.clearChroma()
        
        // Step 3: UI Sync (YUV -> ARGB)
        if (masterBmp.width == probedW && masterBmp.height == probedH) {
            NativeImageUtils.syncMatToArgb(target.p.mat, masterBmp)
        } else {
            Log.w(TAG, "DNG MasterBmp mismatch: expected ${probedW}x${probedH}, got ${masterBmp.width}x${masterBmp.height}")
        }

        return IngestionMetadata(
            probedW, probedH, 
            probedW, probedH, 
            "image/x-adobe-dng", 
            System.currentTimeMillis() - startTime,
            false,
            "LibRaw: ${probedW}x${probedH}"
        )
    }

    private fun ingestViaNativeJpeg(
        path: String,
        target: BufferSet,
        masterBmp: Bitmap,
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
        NativeImageUtils.ingestJpegToYuv(path, target.p)
        
        // Step 2: Stabilize state
        target.p.clearChroma()
        
        // Step 3: UI Sync (YUV -> ARGB)
        if (masterBmp.width == w && masterBmp.height == h) {
            NativeImageUtils.syncMatToArgb(target.p.mat, masterBmp)
        }

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
        target: BufferSet,
        masterBmp: Bitmap,
        startTime: Long
    ): IngestionMetadata {
        val (probedW, probedH) = probeDimensions(context, path)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = if (path.startsWith("content://")) {
                ImageDecoder.createSource(context.contentResolver, Uri.parse(path))
            } else {
                ImageDecoder.createSource(File(path))
            }

            var originalW = 0
            var originalH = 0
            var format = "unknown"

            val decodedBitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                originalW = info.size.width
                originalH = info.size.height
                format = info.mimeType
                
                // Force high-res development if the probed size is larger than the default (preview)
                if (probedW > info.size.width || probedH > info.size.height) {
                    decoder.setTargetSize(probedW, probedH)
                }
                
                // Software allocator is required for JNI access
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }

            val decodedW = decodedBitmap.width
            val decodedH = decodedBitmap.height
            val isDegraded = decodedW < probedW || decodedH < probedH

            // Native Ingestion (ARGB -> YUV)
            NativeImageUtils.ingestArgbToYuv(decodedBitmap, target.p)
            target.p.clearChroma()
            
            // UI Sync (YUV -> ARGB)
            if (masterBmp.width == decodedW && masterBmp.height == decodedH) {
                NativeImageUtils.syncMatToArgb(target.p.mat, masterBmp)
            }
            
            decodedBitmap.recycle()

            return IngestionMetadata(
                probedW, probedH, 
                decodedW, decodedH, 
                format, 
                System.currentTimeMillis() - startTime,
                isDegraded
            )
        } else {
            // Fallback for older devices
            val bmp = OdometerOcrUtils.decodeBitmapSafely(context, path) ?: throw Exception("Fallback decode failed")
            NativeImageUtils.ingestArgbToYuv(bmp, target.p)
            target.p.clearChroma()
            if (masterBmp.width == bmp.width && masterBmp.height == bmp.height) {
                NativeImageUtils.syncMatToArgb(target.p.mat, masterBmp)
            }
            val meta = IngestionMetadata(bmp.width, bmp.height, bmp.width, bmp.height, "legacy", System.currentTimeMillis() - startTime, false)
            bmp.recycle()
            return meta
        }
    }
}
