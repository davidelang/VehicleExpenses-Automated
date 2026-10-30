package com.davidlang.vehicleexpensesautomated.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.davidlang.vehicleexpensesautomated.ui.util.NativeImageUtils
import java.io.File
import java.io.FileOutputStream

object PickedImageReader {
    const val DNG_MIME = "image/x-adobe-dng"
    private const val CANVAS_LIMIT = 100 * 1024 * 1024

    private val GPS_TAGS = arrayOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        "GPSHPositioningError",
    )

    /**
     * Null when the image is accepted. A TIFF header stays on the LibRaw path.
     * Any other image the platform decoder can read is accepted. No format list.
     */
    fun galleryHeaderRejectReason(context: Context, uri: Uri): String? {
        val head = ByteArray(8)
        val n = context.contentResolver.openInputStream(uri)?.use { it.read(head) } ?: 0
        if (isTiffHeader(head, n)) return null
        return try {
            if (platformCanDecode(context, uri)) null else "could not decode"
        } catch (e: Exception) {
            e.message ?: "could not decode"
        }
    }

    private fun platformCanDecode(context: Context, uri: Uri): Boolean {
        return if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width.coerceAtLeast(1)
                val height = info.size.height.coerceAtLeast(1)
                val longEdge = maxOf(width, height)
                val divisor = (longEdge / 32).coerceAtLeast(1)
                decoder.setTargetSize(
                    (width / divisor).coerceAtLeast(1),
                    (height / divisor).coerceAtLeast(1),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            bitmap.recycle()
            true
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            options.outWidth > 0 && options.outHeight > 0
        }
    }

    fun isDng(context: Context, uri: Uri): Boolean {
        val head = ByteArray(8)
        val n = context.contentResolver.openInputStream(uri)?.use { it.read(head) } ?: return false
        return isTiffHeader(head, n)
    }

    fun developToJpeg(context: Context, uri: Uri, outFile: File): File {
        if (isDng(context, uri)) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                NativeImageUtils.nativeDevelopDngToJpeg(pfd.fd, outFile.absolutePath, 95)
            } ?: throw IllegalStateException("developToJpeg: could not open $uri")
        } else {
            val bitmap = decodeBitmap(context, uri)
            try {
                FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            } finally {
                bitmap.recycle()
            }
        }
        copyCaptureExif(context, uri, outFile)
        return outFile
    }

    fun decodeFullSize(context: Context, uri: Uri): Bitmap {
        val bitmap = if (isDng(context, uri)) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                NativeImageUtils.nativeDevelopDngToBitmap(pfd.fd)
            } ?: throw IllegalStateException("decodeFullSize: could not open $uri")
        } else {
            decodeBitmap(context, uri)
        }
        if (bitmap.byteCount > CANVAS_LIMIT) {
            val w = bitmap.width
            val h = bitmap.height
            bitmap.recycle()
            throw IllegalStateException("Photo too large to display ($w x $h)")
        }
        return bitmap
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalStateException("decodeBitmap: $uri")
        }
    }

    private fun isTiffHeader(head: ByteArray, n: Int): Boolean {
        if (n < 2) return false
        val le = head[0] == 0x49.toByte() && head[1] == 0x49.toByte()
        val be = head[0] == 0x4D.toByte() && head[1] == 0x4D.toByte()
        return le || be
    }

    private fun copyCaptureExif(context: Context, uri: Uri, outFile: File) {
        val src = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            ExifInterface(pfd.fileDescriptor)
        } ?: throw IllegalStateException("developToJpeg: could not read exif $uri")
        val dst = ExifInterface(outFile.absolutePath)
        copyAttr(src, dst, ExifInterface.TAG_DATETIME_ORIGINAL)
        copyAttr(src, dst, ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
        for (tag in GPS_TAGS) copyAttr(src, dst, tag)
        dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        dst.saveAttributes()
    }

    private fun copyAttr(src: ExifInterface, dst: ExifInterface, tag: String) {
        val value = src.getAttribute(tag) ?: return
        dst.setAttribute(tag, value)
    }
}
