package com.davidlang.vehicleexpensesautomated.data.transfer

import java.io.InputStream
import java.io.OutputStream

/**
 * Hand-written POSIX ustar. No extra dependency, so the Fuel Smoothing plugin can copy this file.
 * Paths longer than 100 bytes use the ustar prefix field. A path longer than 255 bytes fails.
 */
object DataTransferTar {
    const val MAX_PATH_BYTES = 255
    private const val BLOCK = 512

    class TarException(message: String) : Exception(message)

    class Writer(private val out: OutputStream) {
        fun writeDirectory(path: String) {
            out.write(headerBytes(path, 0L, '5', "", enforcePath = true))
        }

        fun writeFile(path: String, size: Long, body: InputStream) {
            if (size < 0L) throw TarException("negative size for $path")
            out.write(headerBytes(path, size, '0', "", enforcePath = true))
            var left = size
            val buf = ByteArray(8192)
            while (left > 0L) {
                val n = body.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) throw TarException("short file body: $path")
                out.write(buf, 0, n)
                left -= n
            }
            writePad(size)
        }

        fun finish() {
            out.write(ByteArray(BLOCK))
            out.write(ByteArray(BLOCK))
            out.flush()
        }

        private fun writePad(size: Long) {
            val pad = ((BLOCK - (size % BLOCK)) % BLOCK).toInt()
            if (pad > 0) out.write(ByteArray(pad))
        }
    }

    class Entry(
        val path: String,
        val directory: Boolean,
        val size: Long,
    )

    class Reader(private val input: InputStream) {
        private var bodyRemaining: Long = 0L
        private var padRemaining: Int = 0
        private var open = false

        fun next(): Entry? {
            skipRestOfBody()
            val header = readExact(BLOCK, eofAtStartIsError = false) ?: throw TarException("truncated tar")
            if (isZero(header)) {
                val second = readExact(BLOCK, eofAtStartIsError = false)
                if (second == null || isZero(second)) return null
                throw TarException("truncated tar")
            }
            val stored = parseOctal(header, 148, 8)
            for (i in 148 until 156) header[i] = 0x20
            var sum = 0
            for (b in header) sum += b.toInt() and 0xff
            if (sum.toLong() != stored) throw TarException("bad tar checksum")

            val type = header[156].toInt().toChar()
            val name = readField(header, 0, 100)
            val prefix = readField(header, 345, 155)
            val path = when {
                prefix.isEmpty() -> name
                else -> "$prefix/$name"
            }
            when (type) {
                '1' -> throw TarException("hard link not allowed: $path")
                '2' -> throw TarException("symlink not allowed: $path")
                '3', '4', '6' -> throw TarException("special file not allowed: $path")
                '0', '\u0000', '5' -> Unit
                else -> throw TarException("unsupported tar entry type '$type': $path")
            }
            rejectUnsafePath(path)
            val size = parseOctal(header, 124, 12)
            val directory = type == '5'
            if (directory && size != 0L) throw TarException("directory entry has a body: $path")
            bodyRemaining = if (directory) 0L else size
            padRemaining = if (directory) 0 else ((BLOCK - (size % BLOCK)) % BLOCK).toInt()
            open = true
            return Entry(path, directory, size)
        }

        fun readBodyTo(entry: Entry, dest: OutputStream) {
            requireOpen(entry)
            var left = bodyRemaining
            val buf = ByteArray(8192)
            while (left > 0L) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) throw TarException("truncated tar")
                dest.write(buf, 0, n)
                left -= n
            }
            bodyRemaining = 0L
            skipPad()
        }

        fun readBodyBytes(entry: Entry): ByteArray {
            val out = java.io.ByteArrayOutputStream(entry.size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            readBodyTo(entry, out)
            return out.toByteArray()
        }

        private fun requireOpen(entry: Entry) {
            if (!open) throw TarException("no open entry")
            if (entry.directory) return
        }

        private fun skipRestOfBody() {
            if (!open) return
            var left = bodyRemaining
            val buf = ByteArray(8192)
            while (left > 0L) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) throw TarException("truncated tar")
                left -= n
            }
            bodyRemaining = 0L
            skipPad()
        }

        private fun skipPad() {
            var left = padRemaining
            val buf = ByteArray(512)
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size, left))
                if (n < 0) throw TarException("truncated tar")
                left -= n
            }
            padRemaining = 0
            open = false
        }

        private fun readExact(n: Int, eofAtStartIsError: Boolean): ByteArray? {
            val buf = ByteArray(n)
            var off = 0
            while (off < n) {
                val r = input.read(buf, off, n - off)
                if (r < 0) {
                    if (off == 0 && !eofAtStartIsError) return null
                    throw TarException("truncated tar")
                }
                off += r
            }
            return buf
        }
    }

    fun rejectUnsafePath(path: String) {
        if (path.isEmpty()) throw TarException("empty tar path")
        if (path.startsWith("/") || path.startsWith("\\")) {
            throw TarException("absolute path not allowed: $path")
        }
        val segments = path.split('/', '\\')
        if (segments.any { it == ".." }) throw TarException("path contains ..: $path")
    }

    internal fun headerBytes(
        path: String,
        size: Long,
        typeFlag: Char,
        linkName: String,
        enforcePath: Boolean,
    ): ByteArray {
        if (enforcePath) rejectUnsafePath(path)
        val (prefix, name) = splitPath(path)
        val header = ByteArray(BLOCK)
        putBytes(header, 0, 100, name)
        putOctal(header, 100, 8, if (typeFlag == '5') 0x755L else 0x644L)
        putOctal(header, 108, 8, 0L)
        putOctal(header, 116, 8, 0L)
        putOctal(header, 124, 12, size)
        putOctal(header, 136, 12, System.currentTimeMillis() / 1000L)
        for (i in 148 until 156) header[i] = 0x20
        header[156] = typeFlag.code.toByte()
        if (linkName.isNotEmpty()) putBytes(header, 157, 100, linkName.toByteArray(Charsets.UTF_8))
        putBytes(header, 257, 6, "ustar\u0000".toByteArray(Charsets.US_ASCII))
        putBytes(header, 263, 2, "00".toByteArray(Charsets.US_ASCII))
        if (prefix.isNotEmpty()) putBytes(header, 345, 155, prefix)
        var sum = 0
        for (b in header) sum += b.toInt() and 0xff
        val chk = sum.toString(8).padStart(6, '0')
        if (chk.length > 6) throw TarException("checksum overflow")
        for (i in chk.indices) header[148 + i] = chk[i].code.toByte()
        header[154] = 0
        header[155] = 0x20
        return header
    }

    private fun splitPath(path: String): Pair<ByteArray, ByteArray> {
        val full = path.toByteArray(Charsets.UTF_8)
        if (full.size > MAX_PATH_BYTES) {
            throw TarException("path longer than $MAX_PATH_BYTES bytes: $path")
        }
        if (full.size <= 100) return ByteArray(0) to full
        var idx = path.length
        while (true) {
            val slash = path.lastIndexOf('/', idx - 1)
            if (slash <= 0) throw TarException("path cannot fit ustar name and prefix: $path")
            val prefix = path.substring(0, slash).toByteArray(Charsets.UTF_8)
            val name = path.substring(slash + 1).toByteArray(Charsets.UTF_8)
            if (prefix.size <= 155 && name.isNotEmpty() && name.size <= 100) return prefix to name
            idx = slash
        }
    }

    private fun putBytes(dest: ByteArray, offset: Int, length: Int, value: ByteArray) {
        if (value.size > length) throw TarException("ustar field overflow")
        value.copyInto(dest, offset, 0, value.size)
    }

    private fun putOctal(dest: ByteArray, offset: Int, length: Int, value: Long) {
        val digits = value.toString(8).padStart(length - 1, '0')
        if (digits.length != length - 1) throw TarException("octal field overflow")
        for (i in digits.indices) dest[offset + i] = digits[i].code.toByte()
        dest[offset + length - 1] = 0
    }

    private fun parseOctal(header: ByteArray, offset: Int, length: Int): Long {
        var i = offset
        val end = offset + length
        while (i < end && (header[i] == 0x20.toByte() || header[i] == 0.toByte())) i++
        var n = 0L
        while (i < end && header[i] != 0.toByte() && header[i] != 0x20.toByte()) {
            val c = header[i].toInt().toChar()
            if (c !in '0'..'7') throw TarException("bad octal field")
            n = n * 8L + (c.code - '0'.code)
            i++
        }
        return n
    }

    private fun readField(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = offset + length
        while (end < limit && header[end] != 0.toByte()) end++
        return header.copyOfRange(offset, end).toString(Charsets.UTF_8)
    }

    private fun isZero(block: ByteArray): Boolean = block.all { it == 0.toByte() }
}
