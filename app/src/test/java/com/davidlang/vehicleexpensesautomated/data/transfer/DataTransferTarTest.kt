package com.davidlang.vehicleexpensesautomated.data.transfer

import com.davidlang.vehicleexpensesautomated.data.transfer.DataTransferTar.TarException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DataTransferTarTest {

    @Test
    fun roundTrip_normalFile_andLongPath_byteForByte() {
        val normal = "files/hello.txt"
        val normalBytes = "hello tar".toByteArray()
        val longPath = "files/" + "d".repeat(90) + "/" + "f".repeat(20) + ".bin"
        assertTrue(longPath.toByteArray(Charsets.UTF_8).size > 100)
        assertTrue(longPath.toByteArray(Charsets.UTF_8).size <= DataTransferTar.MAX_PATH_BYTES)
        val longBytes = ByteArray(600) { i -> (i * 17 + 3).toByte() }

        val archive = ByteArrayOutputStream()
        DataTransferTar.Writer(archive).useWriter {
            it.writeFile(normal, normalBytes.size.toLong(), ByteArrayInputStream(normalBytes))
            it.writeFile(longPath, longBytes.size.toLong(), ByteArrayInputStream(longBytes))
        }

        val reader = DataTransferTar.Reader(ByteArrayInputStream(archive.toByteArray()))
        val first = reader.next()!!
        assertEquals(normal, first.path)
        assertArrayEquals(normalBytes, reader.readBodyBytes(first))
        val second = reader.next()!!
        assertEquals(longPath, second.path)
        assertArrayEquals(longBytes, reader.readBodyBytes(second))
        assertEquals(null, reader.next())
    }

    @Test
    fun reader_rejectsDotDot_absolute_symlink_andHardlink() {
        assertRejects(craft("files/../../x", '0'), "path contains ..")
        assertRejects(craft("/etc/passwd", '0'), "absolute path not allowed")
        assertRejects(craft("files/link", '2', "files/target"), "symlink not allowed")
        assertRejects(craft("files/hard", '1', "files/target"), "hard link not allowed")
    }

    private fun assertRejects(archive: ByteArray, messagePart: String) {
        val reader = DataTransferTar.Reader(ByteArrayInputStream(archive))
        try {
            reader.next()
            throw AssertionError("expected TarException containing $messagePart")
        } catch (e: TarException) {
            assertTrue(e.message.orEmpty(), messagePart in e.message.orEmpty())
        }
    }

    private fun craft(path: String, type: Char, linkName: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        val header = DataTransferTar.headerBytes(path, 0L, type, linkName, enforcePath = false)
        out.write(header)
        out.write(ByteArray(512))
        out.write(ByteArray(512))
        return out.toByteArray()
    }

    private fun DataTransferTar.Writer.useWriter(block: (DataTransferTar.Writer) -> Unit) {
        block(this)
        finish()
    }
}
