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
    val timeMs: Long
)

object ImageIngestionProvider {
    private const val TAG = "ImageIngestion"

    /**
     * Probes the natural dimensions of an image file, bypassing thumbnails where possible.
     * For DNG files, uses ExifInterface to find the true sensor resolution.
     */
    fun probeDimensions(context: Context, path: String): Pair<Int, Int> {
        return try {
            val exif = android.media.ExifInterface(path)
            val w = exif.getAttributeInt(android.media.ExifInterface.TAG_IMAGE_WIDTH, 0)
            val h = exif.getAttributeInt(android.media.ExifInterface.TAG_IMAGE_LENGTH, 0)
            
            if (w > 0 && h > 0) {
                Pair(w, h)
            } else {
                // Fallback to BitmapFactory for metadata if tags are missing
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, options)
                Pair(options.outWidth, options.outHeight)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exif probe failed for $path, falling back to BitmapFactory: ${e.message}")
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, options)
            Pair(options.outWidth, options.outHeight)
        }
    }

    /**
     * High-Fidelity Ingestion: Bypasses thumbnails and moves data into native YUV primary.
     * Temporary Bridge: Uses ARGB intermediates to maintain visual feedback.
     */
    suspend fun ingestFromFile(
        context: Context,
        path: String,
        target: BufferSet,
        probedW: Int,
        probedH: Int,
        scratchBmp: Bitmap?, // Placeholder for future reuse logic
        masterBmp: Bitmap
    ): IngestionMetadata {
        val t0 = System.currentTimeMillis()
        val file = File(path)
        
        var originalW = probedW
        var originalH = probedH
        var decodedW = 0
        var decodedH = 0
        var format = "unknown"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = if (path.startsWith("content://")) {
                ImageDecoder.createSource(context.contentResolver, Uri.parse(path))
            } else {
                ImageDecoder.createSource(file)
            }

            val decodedBitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                format = info.mimeType
                
                // If it's a DNG or we have a larger probed resolution, force high-res development
                if (probedW > info.size.width || probedH > info.size.height) {
                    decoder.setTargetSize(probedW, probedH)
                }
                
                // Note: ALLOCATOR_SOFTWARE is critical for JNI access to RAW data developed by ImageDecoder
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }

            decodedW = decodedBitmap.width
            decodedH = decodedBitmap.height

            // Step 2: Native Ingestion (ARGB -> YUV)
            // We use the fullBufferSet.p as the target
            NativeImageUtils.ingestArgbToYuv(decodedBitmap, target.p)
            
            // Step 3: Stabilize Luma
            target.p.clearChroma()
            
            // Step 4: UI Sync (YUV -> ARGB)
            // Re-populate the provided masterBmp for visual verification
            if (masterBmp.width == decodedW && masterBmp.height == decodedH) {
                NativeImageUtils.syncMatToArgb(target.p.mat, masterBmp)
            } else {
                Log.e(TAG, "masterBmp size mismatch: ${masterBmp.width}x${masterBmp.height} vs decoded ${decodedW}x${decodedH}")
            }
            
            decodedBitmap.recycle()
        } else {
            // Fallback for older devices using existing (potentially low-res) logic
            val bmp = OdometerOcrUtils.decodeBitmapSafely(context, path) ?: throw Exception("Fallback decode failed")
            originalW = bmp.width; originalH = bmp.height
            decodedW = bmp.width; decodedH = bmp.height
            NativeImageUtils.ingestArgbToYuv(bmp, target.p)
            target.p.clearChroma()
            if (masterBmp.width == bmp.width && masterBmp.height == bmp.height) {
                NativeImageUtils.syncMatToArgb(target.p.mat, masterBmp)
            }
            bmp.recycle()
        }

        return IngestionMetadata(
            originalW, originalH, 
            decodedW, decodedH, 
            format, 
            System.currentTimeMillis() - t0
        )
    }
}
