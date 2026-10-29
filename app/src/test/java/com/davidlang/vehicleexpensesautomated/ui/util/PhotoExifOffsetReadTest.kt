package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * OffsetTimeOriginal is applied to DateTimeOriginal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PhotoExifOffsetReadTest {

    @Test
    fun readsOffsetTimeAndOffsetTimeOriginal() {
        val file = File.createTempFile("exif-offset", ".jpg")
        try {
            val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2024:06:01 12:34:56")
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "-07:00")
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, "+05:30")
            exif.saveAttributes()

            val meta = PhotoExifMetaReader.read(file.absolutePath)
            assertEquals("-07:00", meta.offsetTimeOriginal)
            assertEquals("+05:30", meta.offsetTime)
            val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("GMT-07:00")
            assertEquals(fmt.parse("2024:06:01 12:34:56")!!.time, meta.timestampMs)
        } finally {
            file.delete()
        }
    }
}
