package com.davidlang.vehicleexpensesautomated.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * OffsetTimeOriginal is applied to DateTimeOriginal.
 */
class PhotoExifOffsetReadTest {

    @Test
    fun wallClockWithMinus7OffsetIsThatInstant() {
        val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("GMT-07:00")
        val expected = fmt.parse("2024:06:01 12:34:56")!!.time
        assertEquals(
            expected,
            PhotoExifMetaReader.parseExifWall("2024:06:01 12:34:56", "-07:00"),
        )
    }
}
