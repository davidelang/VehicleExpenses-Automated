package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.davidlang.vehicleexpensesautomated.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Preprocess-only triage dumps (no paddle / no det).
 *
 * Per selected photo:
 *  - DNG: LibRaw unpack CRC + RGB after dcraw + Y after RGB2YUV (nativeDumpDngDevelopStages)
 *  - all: source mono Y via normal ingest path (same as HeatmapStageDump)
 *  - all: feed u8 at 224/608/1024 after prepareScale + populateMonoUInt8
 *
 * Output: Android/data/.../files/preprocess_stage/<runId>/
 * logcat: PreprocessStage
 */
object PreprocessStageDump {
    private const val TAG = "PreprocessStage"

    /** Known phone↔emu divergences from heatmap-stage-20260807 analysis. */
    val DEFAULT_TRIAGE: List<String> = listOf(
        // DNG source match controls
        "PXL_20221230_182006230.dng",
        "PXL_20230705_105304742.dng",
        // DNG source miss (worst / mid / mild)
        "PXL_20221128_172956178.dng",
        "PXL_20230113_231616307.dng",
        "PXL_20221228_165217774.dng",
        // JPG source match, feed diverge at large scale
        "PXL_20250822_062416579.jpg",
        "PXL_20250303_172259346.jpg",
        "PXL_20221121_195449335.jpg",
        // JPG full feed match control
        "PXL_20250703_032207597.jpg",
        "fuel_1784243183762.jpg",
    )

    data class Result(val outDir: File, val nPhotos: Int, val message: String)

    suspend fun run(
        context: Context,
        photoDir: File? = null,
        allowNames: List<String> = DEFAULT_TRIAGE,
        onLog: (String) -> Unit = {},
        onProgress: (done: Int, total: Int, name: String) -> Unit = { _, _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val srcDir = photoDir
            ?: File(context.getExternalFilesDir(null), "pump_photos").also { it.mkdirs() }
        val want = allowNames.toSet()
        val photos = srcDir.listFiles { f ->
            f.isFile && f.name in want
        }?.sortedBy { it.name } ?: emptyList()

        if (photos.isEmpty()) {
            val msg = "No triage photos in ${srcDir.absolutePath} (want ${want.size})"
            Log.e(TAG, msg)
            throw IllegalStateException(msg)
        }

        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val runId = "preprocess_${BuildConfig.PADDLE_SO_STAMP}_${abi}_$ts"
        val outDir = File(context.getExternalFilesDir(null), "preprocess_stage/$runId")
        outDir.mkdirs()

        File(outDir, "00_manifest.json").writeText(
            JSONObject()
                .put("run_id", runId)
                .put("version", BuildConfig.VERSION_NAME)
                .put("device", Build.MODEL)
                .put("primary_abi", abi)
                .put("photo_dir", srcDir.absolutePath)
                .put("n_photos", photos.size)
                .put("allowlist", JSONArray(allowNames))
                .put(
                    "notes",
                    "No paddle. DNG: dumpDngDevelopStages (raw_crc, rgb, y). " +
                        "All: ImageIngestion + prepareScale feeds.",
                )
                .toString(2),
        )

        val scales = listOf(224, 608, 1024)
        val master = BufferSet(1, 1)
        val jsonl = File(outDir, "results.jsonl").bufferedWriter()
        var done = 0

        Log.i(TAG, "STAGE_START n=${photos.size} out=${outDir.absolutePath}")
        onLog("STAGE_START n=${photos.size}")

        for (photo in photos) {
            done++
            onProgress(done, photos.size, photo.name)
            Log.i(TAG, "PHOTO $done/${photos.size} ${photo.name}")
            onLog("PHOTO $done/${photos.size} ${photo.name}")

            val sub = File(
                outDir,
                "%03d_%s".format(done, photo.name.replace(Regex("[^A-Za-z0-9._-]"), "_")),
            )
            sub.mkdirs()

            val row = JSONObject()
                .put("file", photo.name)
                .put("index", done)

            try {
                val ext = photo.extension.lowercase()
                if (ext == "dng") {
                    val rgbPath = File(sub, "libraw_rgb_u8.bin").absolutePath
                    val yPath = File(sub, "libraw_y_u8.bin").absolutePath
                    val dumpJson = NativeImageUtils.dumpDngDevelopStages(
                        photo.absolutePath,
                        rgbPath,
                        yPath,
                    )
                    row.put("dng_develop", JSONObject(dumpJson))
                }

                val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, photo.absolutePath)
                if (imgW <= 0 || imgH <= 0) throw IllegalStateException("probe ${imgW}x$imgH")
                master.resize(imgW, imgH)
                ImageIngestionProvider.ingestFromFile(context, photo.absolutePath, master.p)

                val srcBytes = ByteArray(master.p.width * master.p.height)
                master.p.mat.get(0, 0, srcBytes)
                row.put("source_w", master.p.width)
                row.put("source_h", master.p.height)
                row.put("source_sha256", sha256(srcBytes))
                row.put("source_crc32", crc32(srcBytes))
                row.put("source_sum", srcBytes.fold(0L) { a, b -> a + (b.toInt() and 0xff) })
                File(sub, "source_y_u8.bin").writeBytes(srcBytes)

                val scalesJa = JSONArray()
                for (scale in scales) {
                    val scaleJo = JSONObject().put("scale", scale)
                    val srcW = master.p.width
                    val srcH = master.p.height
                    val currentLongEdge = max(srcW, srcH)
                    val scaleFactor =
                        if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
                    val targetW = (srcW * scaleFactor).toInt()
                    val targetH = (srcH * scaleFactor).toInt()

                    val workspace = BufferSet(srcW, srcH)
                    master.p.mat.copyTo(workspace.p.mat)
                    try {
                        val (outerId, _) = PumpCostVolUtils.prepareScale(workspace, scale)
                        val outer = workspace.c[outerId]
                        val tier = NativePaddleEngine.TIER_SCALES.filter {
                            it >= max(outer.width, outer.height)
                        }.minOrNull() ?: 2560
                        val feed = ByteArray(tier * tier)
                        NativeImageUtils.populateMonoUInt8(outer.mat, feed, tier, tier)
                        scaleJo
                            .put("tier", tier)
                            .put("outer_w", outer.width)
                            .put("outer_h", outer.height)
                            .put("content_w", targetW)
                            .put("content_h", targetH)
                            .put("feed_sha256", sha256(feed))
                            .put("feed_crc32", crc32(feed))
                            .put("feed_sum", feed.fold(0L) { a, b -> a + (b.toInt() and 0xff) })
                        File(sub, "scale${scale}_feed_u8_${tier}x${tier}.bin").writeBytes(feed)
                        try {
                            workspace.c[outerId].release()
                        } catch (_: Throwable) {
                        }
                    } finally {
                        workspace.release()
                    }
                    scalesJa.put(scaleJo)
                }
                row.put("scales", scalesJa)
            } catch (t: Throwable) {
                Log.e(TAG, "photo failed ${photo.name}", t)
                row.put("error", t.message ?: t.javaClass.simpleName)
            }
            jsonl.write(row.toString())
            jsonl.write("\n")
            jsonl.flush()
        }
        jsonl.close()
        try {
            master.release()
        } catch (_: Throwable) {
        }

        val msg = "preprocess triage n=${photos.size} out=${outDir.absolutePath}"
        Log.i(TAG, "STAGE_DONE $msg")
        onLog("STAGE_DONE $msg")
        Result(outDir, photos.size, msg)
    }

    private fun sha256(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun crc32(bytes: ByteArray): Long {
        val c = CRC32()
        c.update(bytes)
        return c.value
    }
}
