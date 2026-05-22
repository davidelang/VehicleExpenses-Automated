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
     */
    fun probeDimensions(context: Context, path: String): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = if (path.startsWith("content://")) {
                ImageDecoder.createSource(context.contentResolver, Uri.parse(path))
            } else {
                ImageDecoder.createSource(File(path))
            }
            // ImageDecoder doesn't have a cheap 'probe' without listener.
            // We use a dummy decode that we abort or just use it to get info.
            // Actually, we can use the 'info' from the first part of decode.
            var w = 0; var h = 0
            try {
                // This is slightly expensive but accurate for DNG RAW dimensions.
                // We don't actually decode the pixels here if we throw an exception in the listener? 
                // No, that's hacky. 
                // Let's use BitmapFactory as a first pass, it's usually correct for metadata 
                // even if it fails for pixel development.
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, options)
                w = options.outWidth
                h = options.outHeight
            } catch (e: Exception) {}
            return Pair(w, h)
        }
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, options)
        return Pair(options.outWidth, options.outHeight)
    }

    /**
     * High-Fidelity Ingestion: Bypasses thumbnails and moves data into native YUV primary.
     * Temporary Bridge: Uses ARGB intermediates to maintain visual feedback.
     */
    suspend fun ingestFromFile(
        context: Context,
        path: String,
        target: BufferSet,
        scratchBmp: Bitmap?, // Placeholder for future reuse logic
        masterBmp: Bitmap
    ): IngestionMetadata {
        val t0 = System.currentTimeMillis()
        val file = File(path)
        
        var originalW = 0
        var originalH = 0
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
                originalW = info.size.width
                originalH = info.size.height
                format = info.mimeType
                
                // Allow hardware limit check if needed, but default to Natural resolution
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
