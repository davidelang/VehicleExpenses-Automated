package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.davidlang.vehicleexpensesautomated.BuildConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
 * Fast heatmap-only stage (no deskew rotate, no rec/OCR, no pump tree).
 *
 * Per photo x product pack:
 *  1) decode to mono full-res (source_sha256 / CRC)
 *  2) multi-scale prep 224/608/1024/2048 to u8 feed CRC/sha
 *  3) det only to heatmap hist / mass / f32 + u8 CRC
 *  4) record NativeImageUtils.heatmapToAngle (C path) for later — not a gate yet
 *
 * Scale 2048 uses full-square or tiled path per NativePaddleEngine.useTiledLargeDet;
 * results include det_mode (single vs tiled_3x3_1024).
 *
 * Output under Android/data/.../files/heatmap_stage/runId/
 * Logs: tag HeatmapStage STAGE_START / PHOTO / STAGE_DONE / STAGE_FAIL
 */
object HeatmapStageDump {
    private const val TAG = "HeatmapStage"

    data class Result(
        val outDir: File,
        val nPhotos: Int,
        val productPath: String,
        val message: String,
    )

    suspend fun run(
        context: Context,
        forceProdDir: String,
        photoDir: File? = null,
        writeBins: Boolean = false,
        maxPhotos: Int? = null,
        /** If non-null, only these basenames (e.g. feed-matched triage). */
        allowNames: List<String>? = null,
        onLog: (String) -> Unit = {},
        onProgress: (done: Int, total: Int, name: String) -> Unit = { _, _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val arch = NativePaddleEngine.modelArchForPrimaryAbi()
        val pathId = NativePaddleEngine.productPathIdForDir(forceProdDir)
        Log.i(TAG, "STAGE_START path=$pathId dir=$forceProdDir arch=$arch writeBins=$writeBins")
        onLog("STAGE_START path=$pathId dir=$forceProdDir arch=$arch writeBins=$writeBins")

        NativePaddleEngine.loadProductionModels(
            context,
            forceArch = arch,
            forceProdDir = forceProdDir,
        )

        val srcDir = photoDir
            ?: File(context.getExternalFilesDir(null), "pump_photos").also { it.mkdirs() }
        val allow = allowNames?.toSet()
        var photos = srcDir.listFiles { f ->
            f.isFile &&
                f.extension.lowercase() in setOf("jpg", "jpeg", "png", "dng") &&
                (allow == null || f.name in allow)
        }?.sortedBy { it.name } ?: emptyList()
        if (maxPhotos != null) photos = photos.take(maxPhotos)

        if (photos.isEmpty()) {
            val msg = "No photos in ${srcDir.absolutePath}"
            Log.e(TAG, "STAGE_FAIL $msg")
            throw IllegalStateException(msg)
        }

        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safePath = pathId.replace(Regex("[^A-Za-z0-9._+-]"), "_")
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val runId = "${BuildConfig.PADDLE_SO_STAMP}_${safePath}_${abi}_$ts"
        val outDir = File(context.getExternalFilesDir(null), "heatmap_stage/$runId")
        outDir.mkdirs()

        // 2048 = deskew-class long edge; large path may be tiled (see det_mode in per-scale JSON).
        val scales = listOf(224, 608, 1024, 2048)
        val paddleEngine = NativePaddleEngine(context, "V3")
        val master = BufferSet(1, 1)

        File(outDir, "00_manifest.json").writeText(
            JSONObject()
                .put("run_id", runId)
                .put("version", BuildConfig.VERSION_NAME)
                .put("paddle_so_stamp", BuildConfig.PADDLE_SO_STAMP)
                .put("paddle_so_arm64", BuildConfig.PADDLE_SO_ARM64_V8A)
                .put("paddle_so_x86_64", BuildConfig.PADDLE_SO_X86_64)
                .put("product_path", pathId)
                .put("product_dir", forceProdDir)
                .put("arch", arch)
                .put("primary_abi", abi)
                .put("device", Build.MODEL)
                .put("photo_dir", srcDir.absolutePath)
                .put("n_photos", photos.size)
                .put("scales", JSONArray(scales))
                .put("use_tiled_large_det", NativePaddleEngine.useTiledLargeDet)
                .put("det_large_outer", NativePaddleEngine.DET_LARGE_OUTER)
                .put("det_tile", NativePaddleEngine.DET_TILE)
                .put(
                    "notes",
                    "No deskew rotate; no rec/OCR. Scales include 2048 (large det; " +
                        "tiled 3x3x1024 when use_tiled_large_det=true). " +
                        "paddle_cpp_angle = NativeImageUtils.heatmapToAngle (C) — informational only.",
                )
                .toString(2),
        )

        val jsonl = File(outDir, "results.jsonl").bufferedWriter()
        var done = 0
        for (photo in photos) {
            done++
            onProgress(done, photos.size, photo.name)
            Log.i(TAG, "PHOTO $done/${photos.size} ${photo.name} path=$pathId")
            onLog("PHOTO $done/${photos.size} ${photo.name}")

            val row = JSONObject()
                .put("file", photo.name)
                .put("product_path", pathId)
                .put("index", done)

            try {
                val (imgW, imgH) = ImageIngestionProvider.probeDimensions(context, photo.absolutePath)
                if (imgW <= 0 || imgH <= 0) throw IllegalStateException("probe ${imgW}x$imgH")
                master.resize(imgW, imgH)
                ImageIngestionProvider.ingestFromFile(context, photo.absolutePath, master.p)

                // Source mono identity (Y plane length = w*h for mono path)
                val srcBytes = ByteArray(master.p.width * master.p.height)
                master.p.mat.get(0, 0, srcBytes)
                row.put("source_w", master.p.width)
                row.put("source_h", master.p.height)
                row.put("source_sha256", sha256(srcBytes))
                row.put("source_crc32", crc32(srcBytes))
                row.put("source_sum", srcBytes.fold(0L) { a, b -> a + (b.toInt() and 0xff) })

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

                    // Work on a copy so prepareScale does not destroy master
                    val workspace = BufferSet(srcW, srcH)
                    master.p.mat.copyTo(workspace.p.mat)
                    try {
                        val (outerId, _) = PumpCostVolUtils.prepareScale(workspace, scale)
                        val outer = workspace.c[outerId]
                        val maxEdge = max(outer.width, outer.height)
                        // Single-tier if available; else letterbox side used by tiled large det (2048).
                        val tier = NativePaddleEngine.TIER_SCALES.filter { it >= maxEdge }.minOrNull()
                            ?: NativePaddleEngine.DET_LARGE_OUTER
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

                        val photoSub = File(
                            outDir,
                            "%03d_%s".format(
                                done,
                                photo.name.replace(Regex("[^A-Za-z0-9._-]"), "_"),
                            ),
                        )
                        if (writeBins) {
                            photoSub.mkdirs()
                            File(photoSub, "scale${scale}_feed_u8_${tier}x${tier}.bin").writeBytes(feed)
                        }

                        val t0 = System.currentTimeMillis()
                        val det = paddleEngine.detect(outer, copyHeatmap = true)
                        scaleJo.put("t_det_ms", System.currentTimeMillis() - t0)

                        if (det == null) {
                            scaleJo.put("error", "detect_null")
                        } else {
                            val hist = det.heatmapHist ?: IntArray(0)
                            val mass = if (hist.isNotEmpty()) hist.drop(1).sum() else 0
                            val detMode = det.metadata["det_mode"]
                                ?: if (tier > (NativePaddleEngine.TIER_SCALES.maxOrNull() ?: 0) &&
                                    NativePaddleEngine.useTiledLargeDet
                                ) {
                                    "tiled"
                                } else {
                                    "single"
                                }
                            scaleJo
                                .put("det_mode", detMode)
                                .put("heat_w", det.width)
                                .put("heat_h", det.height)
                                .put("n_boxes", det.nativeBoxes.size)
                                .put("heatmap_hist0", if (hist.isNotEmpty()) hist[0] else -1)
                                .put("heatmap_mass_bins1_99", mass)
                                .put("heatmap_hist_sha256", sha256IntArray(hist))
                            if (hist.isNotEmpty()) {
                                scaleJo.put("heatmap_hist", JSONArray(hist.toList()))
                            }
                            // Product path: prefer native u8 plane (tiled dumps this; single via tensor copy).
                            val heatU8 = det.heatU8
                                ?: if (det.outputTensor != null) {
                                    NativeImageUtils.heatmapToUInt8Array(det.outputTensor)
                                } else {
                                    null
                                }
                            if (heatU8 != null && heatU8.isNotEmpty()) {
                                val nU = minOf(heatU8.size, det.width * det.height)
                                val plane = if (heatU8.size == nU) heatU8 else heatU8.copyOf(nU)
                                scaleJo
                                    .put("heat_crc32_u8", crc32(plane))
                                    .put("heat_sha256_u8", sha256(plane))
                                    .put("heat_sum_u8", plane.fold(0L) { a, b -> a + (b.toInt() and 0xff) })
                                if (writeBins) {
                                    photoSub.mkdirs()
                                    HeatmapU8Dump.writeU8z(
                                        File(photoSub, "scale${scale}_heatmap.u8z"),
                                        plane,
                                        det.width,
                                        det.height,
                                        mapOf(
                                            "path" to pathId,
                                            "product_dir" to forceProdDir,
                                            "scale" to scale,
                                            "tier" to tier,
                                            "det_mode" to detMode,
                                            "file" to photo.name,
                                        ),
                                    )
                                }
                            }
                            val heat = det.heatmap
                            if (heat != null && heat.isNotEmpty()) {
                                val n = minOf(heat.size, det.width * det.height)
                                scaleJo
                                    .put("heat_crc32_f32_bits", crc32FloatBits(heat, n))
                                    .put("heat_sum", heat.take(n).sum().toDouble())
                                    .put("heat_max", heat.take(n).maxOrNull()?.toDouble() ?: 0.0)
                                if (writeBins) {
                                    photoSub.mkdirs()
                                    saveHeatmapF32(
                                        heat,
                                        det.width,
                                        det.height,
                                        File(photoSub, "scale${scale}_heatmap.f32"),
                                    )
                                }
                            }
                            // C-path angle (informational until heatmaps match)
                            val angle = if (det.outputTensor != null || det.heatU8 != null) {
                                det.deskewAngleCpp(0.20f)
                            } else {
                                0f
                            }
                            scaleJo.put("paddle_cpp_angle", angle.toDouble())
                        }
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
        master.release()

        try {
            NativePaddleEngine.loadProductionModels(context)
        } catch (t: Throwable) {
            Log.w(TAG, "restore models: ${t.message}")
        }

        val msg = "STAGE_DONE path=$pathId n=$done out=${outDir.absolutePath}"
        Log.i(TAG, msg)
        onLog(msg)
        Result(outDir, done, pathId, msg)
    }

    private fun sha256(data: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(data)
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun sha256IntArray(a: IntArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (v in a) {
            md.update((v shr 24 and 0xff).toByte())
            md.update((v shr 16 and 0xff).toByte())
            md.update((v shr 8 and 0xff).toByte())
            md.update((v and 0xff).toByte())
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun crc32(data: ByteArray): Long {
        val c = CRC32()
        c.update(data)
        return c.value
    }

    private fun crc32FloatBits(heat: FloatArray, n: Int): Long {
        val c = CRC32()
        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            bb.clear()
            bb.putFloat(heat[i])
            c.update(bb.array())
        }
        return c.value
    }

    private fun saveHeatmapF32(heat: FloatArray, w: Int, h: Int, file: File) {
        val n = minOf(heat.size, w * h)
        val bb = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) bb.putFloat(heat[i])
        file.writeBytes(bb.array())
    }
}
