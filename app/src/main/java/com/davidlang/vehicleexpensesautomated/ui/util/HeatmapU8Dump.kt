package com.davidlang.vehicleexpensesautomated.ui.util

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Det-time raw u8 heatmap dump (not float copies).
 *
 * File format `*.u8z`:
 * ```
 * magic "HMU8" (4 bytes ASCII)
 * w:u32 LE, h:u32 LE
 * comp:u8  (0=raw, 1=zlib of raw row-major u8)
 * reserved:u8
 * raw_len:u32 LE  (uncompressed w*h)
 * payload: comp bytes
 * ```
 *
 * Sidecar `*.meta.json`: path_id, tier, crc32 of uncompressed u8, nz, sum, sizes.
 */
object HeatmapU8Dump {
    const val MAGIC = "HMU8"
    const val COMP_RAW: Int = 0
    const val COMP_ZLIB: Int = 1

    fun crc32(data: ByteArray, len: Int = data.size): Long {
        val c = CRC32()
        c.update(data, 0, len)
        return c.value
    }

    fun zlibCompress(raw: ByteArray): ByteArray {
        val def = Deflater(Deflater.BEST_COMPRESSION)
        def.setInput(raw)
        def.finish()
        val buf = ByteArray(8192)
        val out = ByteArrayOutputStream(raw.size / 8 + 64)
        while (!def.finished()) {
            val n = def.deflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        def.end()
        return out.toByteArray()
    }

    /**
     * Write compressed raw u8 heat + optional meta JSON next to it.
     * @return true if heat file written
     */
    fun writeU8z(
        file: File,
        u8: ByteArray,
        w: Int,
        h: Int,
        metaExtra: Map<String, Any?> = emptyMap(),
    ): Boolean {
        if (w <= 0 || h <= 0) return false
        val need = w * h
        if (u8.size < need) return false
        val raw = if (u8.size == need) u8 else u8.copyOf(need)
        val compressed = zlibCompress(raw)
        file.parentFile?.mkdirs()
        DataOutputStream(file.outputStream().buffered()).use { dos ->
            dos.writeBytes(MAGIC)
            // writeInt is big-endian; write little-endian explicitly
            writeIntLE(dos, w)
            writeIntLE(dos, h)
            dos.writeByte(COMP_ZLIB)
            dos.writeByte(0)
            writeIntLE(dos, need)
            dos.write(compressed)
        }
        val nz = raw.count { it != 0.toByte() }
        var sum = 0L
        for (b in raw) sum += (b.toInt() and 0xff)
        val meta = JSONObject()
            .put("w", w)
            .put("h", h)
            .put("raw_len", need)
            .put("comp", COMP_ZLIB)
            .put("payload_len", compressed.size)
            .put("crc32_u8", crc32(raw))
            .put("nz", nz)
            .put("sum", sum)
            .put("nz_frac", if (need > 0) nz.toDouble() / need else 0.0)
        metaExtra.forEach { (k, v) ->
            when (v) {
                null -> meta.put(k, JSONObject.NULL)
                is Number -> meta.put(k, v)
                is Boolean -> meta.put(k, v)
                else -> meta.put(k, v.toString())
            }
        }
        File(file.parentFile, file.nameWithoutExtension + ".meta.json").writeText(meta.toString(2))
        return true
    }

    private fun writeIntLE(dos: DataOutputStream, v: Int) {
        dos.writeByte(v and 0xff)
        dos.writeByte((v ushr 8) and 0xff)
        dos.writeByte((v ushr 16) and 0xff)
        dos.writeByte((v ushr 24) and 0xff)
    }
}
