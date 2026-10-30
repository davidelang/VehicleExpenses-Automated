package com.davidlang.vehicleexpensesautomated.ui.util

import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32

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
    init {
        System.loadLibrary("native_ocr_qa")
    }

    const val MAGIC = "HMU8"
    const val COMP_RAW: Int = 0
    const val COMP_ZLIB: Int = 1

    fun crc32(data: ByteArray, len: Int = data.size): Long {
        val c = CRC32()
        c.update(data, 0, len)
        return c.value
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
        if (need <= 0 || u8.size < need) return false
        val stats = LongArray(3)
        val compressed = nativePackU8z(u8, need, stats) ?: return false
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
        val nz = stats[0]
        val sum = stats[1]
        val crc = stats[2]
        val meta = JSONObject()
            .put("w", w)
            .put("h", h)
            .put("raw_len", need)
            .put("comp", COMP_ZLIB)
            .put("payload_len", compressed.size)
            .put("crc32_u8", crc)
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

    private external fun nativePackU8z(raw: ByteArray, len: Int, stats: LongArray): ByteArray?

    private fun writeIntLE(dos: DataOutputStream, v: Int) {
        dos.writeByte(v and 0xff)
        dos.writeByte((v ushr 8) and 0xff)
        dos.writeByte((v ushr 16) and 0xff)
        dos.writeByte((v ushr 24) and 0xff)
    }
}
