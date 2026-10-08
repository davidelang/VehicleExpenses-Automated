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
 * Sidecar `{filename}.meta.json` (full name, including `.u8z`): path_id, tier, crc32 of uncompressed u8, nz, sum, sizes.
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
        File(file.parentFile, file.name + ".meta.json").writeText(meta.toString(2))
        return true
    }

    /**
     * P4 of a returned u8 heat plane. Bit 1 iff `(b and 0xFF) > hmThresh * 255`.
     * Header `P4\n{w} {h}\n`. MSB-first; each row padded to a byte.
     * One-line `*.meta.json` beside the file (nz = count of 1-bits).
     * @return true if the P4 file was written
     */
    fun writeP4(
        file: File,
        u8: ByteArray,
        w: Int,
        h: Int,
        hmThresh: Float,
        metaExtra: Map<String, Any?> = emptyMap(),
    ): Boolean {
        if (w <= 0 || h <= 0) return false
        val need = w * h
        if (u8.size < need) return false
        val thrU = hmThresh * 255f
        val rowBytes = (w + 7) / 8
        val packed = ByteArray(rowBytes * h)
        var nz = 0
        for (y in 0 until h) {
            val row = y * w
            val out = y * rowBytes
            for (x in 0 until w) {
                if ((u8[row + x].toInt() and 0xFF) > thrU) {
                    val bi = out + (x ushr 3)
                    packed[bi] = (packed[bi].toInt() or (0x80 ushr (x and 7))).toByte()
                    nz++
                }
            }
        }
        file.parentFile?.mkdirs()
        file.outputStream().buffered().use { out ->
            out.write("P4\n$w $h\n".toByteArray(Charsets.US_ASCII))
            out.write(packed)
        }
        val meta = JSONObject()
        fun putExtra(key: String) {
            when (val v = metaExtra[key]) {
                null -> if (metaExtra.containsKey(key)) meta.put(key, JSONObject.NULL) else meta.put(key, "")
                is Number -> meta.put(key, v)
                is Boolean -> meta.put(key, v)
                else -> meta.put(key, v.toString())
            }
        }
        putExtra("geom")
        meta.put("w", w)
        meta.put("h", h)
        putExtra("tier")
        putExtra("thr")
        putExtra("product_dir")
        putExtra("device_model")
        putExtra("device_dir")
        meta.put("nz", nz)
        File(file.parentFile, file.nameWithoutExtension + ".meta.json").writeText(meta.toString())
        return true
    }

    private fun writeIntLE(dos: DataOutputStream, v: Int) {
        dos.writeByte(v and 0xff)
        dos.writeByte((v ushr 8) and 0xff)
        dos.writeByte((v ushr 16) and 0xff)
        dos.writeByte((v ushr 24) and 0xff)
    }
}
