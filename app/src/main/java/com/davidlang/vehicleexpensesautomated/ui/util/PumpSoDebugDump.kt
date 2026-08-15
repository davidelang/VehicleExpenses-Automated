package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.davidlang.vehicleexpensesautomated.BuildConfig
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/**
 * Single-photo SO / pipeline dump for First-10 L1 regression A/B.
 *
 * Runs the product Set G-- path on one pump photo and writes mono buffers,
 * heatmaps (raw f32 + u8 preview + hist), boxes, crops, and OCR probs to
 * `Android/data/.../files/pump_so_debug/<runId>/`.
 *
 * Default target: [DEFAULT_L1_NAME] (First 10 line 1).
 */
object PumpSoDebugDump {
    private const val TAG = "PumpSoDebugDump"

    /** First 10 L1 filename that fails cost/vol vs pin golden. */
    const val DEFAULT_L1_NAME = "PXL_20220701_020625793.dng"

    data class Result(
        val outDir: File,
        val photoName: String,
        val cost: String,
        val vol: String,
        val tilt: Float,
        val message: String,
    )

    /**
     * @param photoName exact filename under pump_photos, or null for [DEFAULT_L1_NAME]
     * @param label optional SO label (e.g. "pin" / "r20b") stored in manifest
     */
    suspend fun run(
        context: Context,
        photoName: String? = null,
        label: String = "",
        onLog: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val name = photoName?.ifBlank { null } ?: DEFAULT_L1_NAME
        val pumpDir = File(context.getExternalFilesDir(null), "pump_photos")
        val photo = File(pumpDir, name)
        if (!photo.isFile) {
            val msg = "Missing $name under ${pumpDir.absolutePath}"
            onLog(msg)
            throw IllegalStateException(msg)
        }

        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeVer = BuildConfig.VERSION_NAME.replace(Regex("[^A-Za-z0-9._+-]"), "_")
        val safeLabel = label.trim().ifEmpty { "nolabel" }.replace(Regex("[^A-Za-z0-9._+-]"), "_")
        val runId = "${safeVer}_${safeLabel}_$ts"
        val outDir = File(context.getExternalFilesDir(null), "pump_so_debug/$runId")
        outDir.mkdirs()
        onLog("Dump dir: ${outDir.absolutePath}")

        val soInfo = collectSoInfo(context)
        writeText(
            File(outDir, "00_manifest.json"),
            JSONObject()
                .put("run_id", runId)
                .put("timestamp", ts)
                .put("version", BuildConfig.VERSION_NAME)
                .put("paddle_so_stamp", BuildConfig.PADDLE_SO_STAMP)
                .put("label", label)
                .put("device", Build.MODEL)
                .put("device_manufacturer", Build.MANUFACTURER)
                .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                .put("primary_abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "")
                .put("product_path", NativePaddleEngine.activeProductPathId)
                .put("product_dir", NativePaddleEngine.activeProductDir)
                .put("product_arch_dir", NativePaddleEngine.productArchAndDir().let { "${it.first}/${it.second}" })
                .put("photo", name)
                .put("photo_bytes", photo.length())
                .put("photo_sha256", sha256File(photo))
                .put(
                    "paddle_so_buildconfig",
                    JSONObject()
                        .put("x86_64", BuildConfig.PADDLE_SO_X86_64)
                        .put("arm64-v8a", BuildConfig.PADDLE_SO_ARM64_V8A)
                        .put("armeabi-v7a", BuildConfig.PADDLE_SO_ARMEABI_V7A),
                )
                .put("paddle_so_runtime", soInfo)
                .put("threads_note", "NativePaddleEngine uses product threads=4 (pin-era)")
                .put("purpose", "A/B SO dumps for L1 First 10 regression; pull entire directory after each SO APK")
                .toString(2),
        )

        val (probedW, probedH) = ImageIngestionProvider.probeDimensions(context, photo.absolutePath)
        if (probedW <= 0 || probedH <= 0) {
            throw IllegalStateException("probe failed ${probedW}x$probedH for $name")
        }
        val imgW = probedW
        val imgH = probedH
        onLog("Ingest $name ${imgW}x$imgH")

        val workspace = BufferSet(imgW, imgH)
        val recBuffer = BufferSet(320, 48)
        val meta = ImageIngestionProvider.ingestFromFile(context, photo.absolutePath, workspace.p)
        writeText(
            File(outDir, "01_ingest.json"),
            JSONObject()
                .put("width", imgW)
                .put("height", imgH)
                .put("originalWidth", meta.originalWidth)
                .put("originalHeight", meta.originalHeight)
                .put("decodedWidth", meta.decodedWidth)
                .put("decodedHeight", meta.decodedHeight)
                .put("format", meta.format)
                .put("timeMs", meta.timeMs)
                .put("isDegraded", meta.isDegraded)
                .put("diagnostic", meta.diagnostic)
                .toString(2),
        )
        saveMono(workspace.p.mat, File(outDir, "01_original_mono.png"))
        saveMonoPgm(workspace.p.mat, File(outDir, "01_original_mono.pgm"))

        val paddleEngine = NativePaddleEngine(context)
        if (!paddleEngine.isAvailable) {
            throw IllegalStateException("Paddle engine unavailable: check assets/SO")
        }

        // --- deskew (same as Set G) ---
        NativePaddleEngine.heartbeat("so_debug_deskew")
        onLog("Deskew…")
        val tDeskew0 = System.currentTimeMillis()
        val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(workspace.p)
        val tilt = -deskewRes.paddleCppAngle
        OdometerOcrUtils.rotate(workspace, tilt)
        val tDeskew = System.currentTimeMillis() - tDeskew0
        writeText(
            File(outDir, "02_deskew.json"),
            JSONObject()
                .put("paddle_cpp_angle", deskewRes.paddleCppAngle.toDouble())
                .put("tilt_applied", tilt.toDouble())
                .put("angle_result", deskewRes.angle.toDouble())
                .put("t_deskew_ms", tDeskew)
                .put("engines", JSONObject().apply {
                    deskewRes.engines.forEach { (k, v) ->
                        put(k, JSONObject().put("angle", v.angle.toDouble()).put("times_ms", JSONArray(v.timesMs)))
                    }
                })
                .toString(2),
        )
        saveMono(workspace.p.mat, File(outDir, "02_deskewed_mono.png"))
        saveMonoPgm(workspace.p.mat, File(outDir, "02_deskewed_mono.pgm"))

        // --- multi-scale det (Set G red boxes) ---
        val scales = listOf(224, 608, 1024)
        val pdHunksRawTotal = mutableListOf<PumpHunk>()
        val scaleSummaries = JSONArray()

        scales.forEach { scale ->
            NativePaddleEngine.heartbeat("so_debug_det_$scale")
            onLog("Det scale $scale…")
            val srcW = workspace.p.width
            val srcH = workspace.p.height
            val currentLongEdge = max(srcW, srcH)
            val scaleFactor = if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
            val targetW = (srcW * scaleFactor).toInt()
            val targetH = (srcH * scaleFactor).toInt()
            val (outerId, innerId) = PumpCostVolUtils.prepareScale(workspace, scale)

            val outer = workspace.c[outerId]
            val prefix = "03_scale${scale}"
            saveMono(outer.mat, File(outDir, "${prefix}_det_input.png"))
            saveMonoPgm(outer.mat, File(outDir, "${prefix}_det_input.pgm"))

            // Also dump the exact square tier feed the engine builds (top-left pad).
            val tier = NativePaddleEngine.TIER_SCALES.filter { it >= max(outer.width, outer.height) }.minOrNull()
                ?: 2560
            val feed = ByteArray(tier * tier)
            NativeImageUtils.populateMonoUInt8(outer.mat, feed, tier, tier)
            File(outDir, "${prefix}_feed_u8_${tier}x${tier}.bin").writeBytes(feed)
            writeText(
                File(outDir, "${prefix}_feed.meta.json"),
                JSONObject()
                    .put("tier", tier)
                    .put("outer_w", outer.width)
                    .put("outer_h", outer.height)
                    .put("content_w", targetW)
                    .put("content_h", targetH)
                    .put("feed_sum", feed.fold(0L) { a, b -> a + (b.toInt() and 0xff) })
                    .put("feed_crc32", crc32(feed))
                    .toString(2),
            )

            val det = paddleEngine.detect(outer, copyHeatmap = true)
            val detJson = JSONObject()
                .put("scale", scale)
                .put("tier", tier)
                .put("target_w", targetW)
                .put("target_h", targetH)
                .put("outer_w", outer.width)
                .put("outer_h", outer.height)
            if (det == null) {
                detJson.put("error", "detect returned null")
                writeText(File(outDir, "${prefix}_det.json"), detJson.toString(2))
            } else {
                val hist = det.heatmapHist ?: IntArray(0)
                val mass = if (hist.isNotEmpty()) hist.drop(1).sum() else 0
                val metaJo = JSONObject()
                det.metadata.forEach { (k, v) -> metaJo.put(k, v) }
                detJson
                    .put("heat_w", det.width)
                    .put("heat_h", det.height)
                    .put("n_boxes", det.nativeBoxes.size)
                    .put("heatmap_hist", JSONArray(hist.toList()))
                    .put("heatmap_mass_bins1_99", mass)
                    .put("heatmap_hist0", if (hist.isNotEmpty()) hist[0] else -1)
                    .put("metadata", metaJo)
                    .put(
                        "boxes",
                        JSONArray().apply {
                            det.nativeBoxes.forEach { b ->
                                put(
                                    JSONObject()
                                        .put("points", JSONArray(b.points.toList()))
                                        .put("confidence", b.confidence.toDouble()),
                                )
                            }
                        },
                    )
                val heat = det.heatmap
                if (heat != null && heat.isNotEmpty()) {
                    val hw = det.width
                    val hh = det.height
                    val n = min(heat.size, hw * hh)
                    detJson
                        .put("heat_max", heat.take(n).maxOrNull()?.toDouble() ?: 0.0)
                        .put("heat_crc32_f32_bits", crc32FloatBits(heat, n))
                        .put("heat_sum", heat.take(n).sum().toDouble())
                    saveHeatmapF32(heat, hw, hh, File(outDir, "${prefix}_heatmap.f32"))
                    saveHeatmapU8Preview(heat, hw, hh, File(outDir, "${prefix}_heatmap_u8.png"))
                } else {
                    detJson.put("heatmap_copy", "null")
                }
                writeText(File(outDir, "${prefix}_det.json"), detJson.toString(2))
                scaleSummaries.put(detJson)

                // Product discovery path (boxes mapped to full image) for red-box filter
                val metaMap = mutableMapOf<String, String>()
                val paddleResults = PumpCostVolUtils.runDiscoveryPaddle(
                    workspace, outerId, paddleEngine, targetW, targetH, scale, metaMap,
                )
                pdHunksRawTotal.addAll(paddleResults[1])
                val discMeta = JSONObject()
                metaMap.forEach { (k, v) -> discMeta.put(k, v) }
                writeText(
                    File(outDir, "${prefix}_discovery.json"),
                    JSONObject()
                        .put("meta", discMeta)
                        .put("n_raw", paddleResults[1].size)
                        .put("n_exp", paddleResults[2].size)
                        .put("n_max", paddleResults[3].size)
                        .put("n_native", paddleResults[4].size)
                        .put("raw_boxes", hunksToJson(paddleResults[1]))
                        .toString(2),
                )
            }
            workspace.c[innerId].release()
            workspace.c[outerId].release()
        }

        // --- redbox filter + classify (Set G) ---
        NativePaddleEngine.heartbeat("so_debug_redbox")
        onLog("Redbox filter + OCR…")
        PumpCostVolUtils.doCrossScaleRedboxFilter(pdHunksRawTotal, imgW, imgH)
        val redPixelList = PumpCostVolUtils.hunksToRects(pdHunksRawTotal).toMutableList()
        PumpCostVolUtils.pruneRectsToTopN(redPixelList, PumpOcrSettings.DEFAULT_MAX_RED_BOXES, imgH)
        writeText(
            File(outDir, "04_red_boxes.json"),
            JSONObject()
                .put("count", redPixelList.size)
                .put("rects", rectsToJson(redPixelList))
                .toString(2),
        )
        saveOverlayBoxes(workspace.p.mat, redPixelList, File(outDir, "04_red_boxes_overlay.png"), 0, 0, 255)

        val detail = if (redPixelList.isEmpty()) {
            null
        } else {
            // Re-run full Set G detailed for cost/vol (same code path as product)
            // Workspace is already deskewed; runSetG deskews again — use a fresh copy.
            // To avoid double-deskew, call OCR pieces here with current workspace.
            // Match Set G-- / Quick Fill vert factors (not full Set G k=8 list).
            val (blueH, orangeH) = PumpCostVolUtils.createBlueAndOrangeHunksFromReds(
                PumpCostVolUtils.rectsToHunks(redPixelList),
                imgW,
                imgH,
                SET_G_MINUS_MINUS_VERT_FACTORS,
            )
            val blue = PumpCostVolUtils.hunksToRects(blueH)
            val orange = PumpCostVolUtils.hunksToRects(orangeH)
            writeText(
                File(outDir, "05_blue_orange_boxes.json"),
                JSONObject()
                    .put("blue", rectsToJson(blue))
                    .put("orange", rectsToJson(orange))
                    .toString(2),
            )
            saveOverlayBoxes(workspace.p.mat, blue, File(outDir, "05_blue_boxes_overlay.png"), 255, 0, 0)
            saveOverlayBoxes(workspace.p.mat, orange, File(outDir, "05_orange_boxes_overlay.png"), 255, 128, 0)

            // Save each blue/orange crop + OCR
            fun dumpCrops(rects: List<Rect>, tag: String) {
                rects.forEachIndexed { i, r ->
                    val l = r.left.coerceIn(0, imgW - 1)
                    val t = r.top.coerceIn(0, imgH - 1)
                    val rr = r.right.coerceIn(l + 1, imgW)
                    val bb = r.bottom.coerceIn(t + 1, imgH)
                    if (rr - l < 2 || bb - t < 2) return@forEachIndexed
                    val cropId = workspace.createCrop(l, t, rr - l, bb - t)
                    saveMono(workspace.c[cropId].mat, File(outDir, "06_${tag}_crop_${i}.png"))
                    workspace.c[cropId].release()
                }
            }
            dumpCrops(blue, "blue")
            dumpCrops(orange, "orange")

            val ocrBlue = PumpCostVolUtils.ocrPumpRectsAsisAndDigits(
                workspace, paddleEngine, recBuffer, blue, imgW, imgH,
            )
            val ocrOrange = if (orange.isNotEmpty()) {
                PumpCostVolUtils.ocrPumpRectsAsisAndDigits(
                    workspace, paddleEngine, recBuffer, orange, imgW, imgH,
                )
            } else {
                PumpRectOcrLists(emptyList(), emptyList(), emptyList(), emptyList())
            }
            val blueCands = PumpCostVolUtils.buildRedBoxCandidates(
                blue, ocrBlue.asis, ocrBlue.digits, ocrBlue.asisProbs, ocrBlue.digitsProbs,
                labelPrefix = "Blue",
            )
            val orangeCands = PumpCostVolUtils.buildRedBoxCandidates(
                orange, ocrOrange.asis, ocrOrange.digits, ocrOrange.asisProbs, ocrOrange.digitsProbs,
                labelPrefix = "Orange",
            )
            val classify = PumpCostVolUtils.classifyCostVolFromBoxOcr(blueCands)
            fun candJson(c: RedBoxOcrCandidate) = JSONObject()
                .put("label", c.label)
                .put("asis", c.asis)
                .put("digits", c.digits)
                .put("asis_probs", c.asisProbs)
                .put("digits_probs", c.digitsProbs)
                .put("rect", c.rect?.let { rectJson(it) } ?: JSONObject.NULL)
            writeText(
                File(outDir, "07_ocr_classify.json"),
                JSONObject()
                    .put("tilt", tilt.toDouble())
                    .put("t_deskew_ms", tDeskew)
                    .put("cost", classify.cost)
                    .put("vol", classify.vol)
                    .put("blue_candidates", JSONArray().apply { blueCands.forEach { put(candJson(it)) } })
                    .put("orange_candidates", JSONArray().apply { orangeCands.forEach { put(candJson(it)) } })
                    .put("cost_cand", candJson(classify.costCand))
                    .put("vol_cand", candJson(classify.volCand))
                    .toString(2),
            )
            Triple(classify.cost, classify.vol, blueCands.size)
        }

        val cost = detail?.first ?: "N/A"
        val vol = detail?.second ?: "N/A"
        writeText(
            File(outDir, "08_summary.json"),
            JSONObject()
                .put("photo", name)
                .put("version", BuildConfig.VERSION_NAME)
                .put("label", label)
                .put("tilt", tilt.toDouble())
                .put("cost", cost)
                .put("vol", vol)
                .put("n_red", redPixelList.size)
                .put("scale_summaries", scaleSummaries)
                .put(
                    "golden_l1_hint",
                    JSONObject()
                        .put("cost", "84.50")
                        .put("vol", "14.325")
                        .put("mass1024", 36825)
                        .put("tilt", 0.0),
                )
                .put("out_dir", outDir.absolutePath)
                .toString(2),
        )
        writeText(
            File(outDir, "README.txt"),
            """
            Pump SO debug dump
            ==================
            Pull this whole directory after each APK/SO under test:

              adb -s emulator-5554 pull \\
                /sdcard/Android/data/com.davidlang.vehicleexpensesautomated/files/pump_so_debug/$runId \\
                ./pump_so_debug_$runId

            Compare 03_scale*_heatmap.f32 / *.json and 07_ocr_classify.json across SO labels.
            Feed tensors: 03_scale*_feed_u8_*.bin (should match if preprocess identical).
            Manifest lists SO path/size/sha256 from nativeLibraryDir.

            Label this run: $label
            Version: ${BuildConfig.VERSION_NAME}
            Photo: $name
            Result: cost=$cost vol=$vol tilt=$tilt
            """.trimIndent() + "\n",
        )

        val msg = "L1 dump OK cost=$cost vol=$vol tilt=$tilt → ${outDir.name}"
        onLog(msg)
        Log.i(TAG, msg)
        Result(outDir, name, cost, vol, tilt, msg)
    }

    private fun collectSoInfo(context: Context): JSONObject {
        val dir = context.applicationInfo.nativeLibraryDir ?: ""
        val so = File(dir, "libpaddle_light_api_shared.so")
        val o = JSONObject()
            .put("nativeLibraryDir", dir)
            .put("path", so.absolutePath)
            .put("exists", so.isFile)
        if (so.isFile) {
            o.put("size", so.length())
            o.put("sha256", sha256File(so))
        } else {
            // Emulators sometimes leave nativeLibraryDir empty/unreadable; try lib/ subdirs.
            val ai = context.applicationInfo
            val candidates = listOfNotNull(
                ai.nativeLibraryDir?.let { File(it, "libpaddle_light_api_shared.so") },
                File(ai.sourceDir).parentFile?.let { File(it, "lib/${Build.SUPPORTED_ABIS.firstOrNull()}/libpaddle_light_api_shared.so") },
            )
            for (c in candidates) {
                if (c.isFile) {
                    o.put("path", c.absolutePath)
                    o.put("exists", true)
                    o.put("size", c.length())
                    o.put("sha256", sha256File(c))
                    break
                }
            }
        }
        return o
    }

    private fun writeText(f: File, s: String) {
        f.parentFile?.mkdirs()
        f.writeText(s)
    }

    private fun sha256File(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { inp ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = inp.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
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
        val tmp = ByteArray(4)
        for (i in 0 until n) {
            val bits = java.lang.Float.floatToIntBits(heat[i])
            tmp[0] = (bits and 0xff).toByte()
            tmp[1] = ((bits ushr 8) and 0xff).toByte()
            tmp[2] = ((bits ushr 16) and 0xff).toByte()
            tmp[3] = ((bits ushr 24) and 0xff).toByte()
            c.update(tmp)
        }
        return c.value
    }

    private fun saveMono(mat: Mat, out: File) {
        if (mat.empty()) return
        val gray = when (mat.type()) {
            CvType.CV_8UC1 -> mat
            else -> {
                val g = Mat()
                if (mat.channels() == 3) Imgproc.cvtColor(mat, g, Imgproc.COLOR_BGR2GRAY)
                else if (mat.channels() == 4) Imgproc.cvtColor(mat, g, Imgproc.COLOR_BGRA2GRAY)
                else mat.convertTo(g, CvType.CV_8UC1)
                g
            }
        }
        Imgcodecs.imwrite(out.absolutePath, gray)
        if (gray !== mat) gray.release()
    }

    private fun saveMonoPgm(mat: Mat, out: File) {
        if (mat.empty()) return
        val gray = Mat()
        when (mat.type()) {
            CvType.CV_8UC1 -> mat.copyTo(gray)
            else -> {
                if (mat.channels() == 3) Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
                else if (mat.channels() == 4) Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGRA2GRAY)
                else mat.convertTo(gray, CvType.CV_8UC1)
            }
        }
        val w = gray.cols()
        val h = gray.rows()
        val n = w * h
        val bytes = ByteArray(n)
        gray.get(0, 0, bytes)
        FileOutputStream(out).use { fos ->
            fos.write("P5\n$w $h\n255\n".toByteArray(Charsets.US_ASCII))
            fos.write(bytes)
        }
        gray.release()
    }

    private fun saveHeatmapF32(heat: FloatArray, w: Int, h: Int, out: File) {
        DataOutputStream(FileOutputStream(out)).use { dos ->
            // little-endian raw float32 row-major + 8-byte header: magic 'HM32', w, h
            dos.writeBytes("HM32")
            dos.writeInt(Integer.reverseBytes(w))
            dos.writeInt(Integer.reverseBytes(h))
            val n = min(heat.size, w * h)
            for (i in 0 until n) {
                val bits = java.lang.Float.floatToIntBits(heat[i])
                dos.writeInt(Integer.reverseBytes(bits))
            }
        }
    }

    private fun saveHeatmapU8Preview(heat: FloatArray, w: Int, h: Int, out: File) {
        val n = min(heat.size, w * h)
        val mat = Mat(h, w, CvType.CV_8UC1)
        val bytes = ByteArray(n)
        var mx = 1e-6f
        for (i in 0 until n) mx = max(mx, heat[i])
        for (i in 0 until n) {
            val v = (heat[i] / mx * 255f).toInt().coerceIn(0, 255)
            bytes[i] = v.toByte()
        }
        mat.put(0, 0, bytes)
        Imgcodecs.imwrite(out.absolutePath, mat)
        mat.release()
    }

    private fun saveOverlayBoxes(base: Mat, rects: List<Rect>, out: File, b: Int, g: Int, r: Int) {
        if (base.empty()) return
        val color = Mat()
        when (base.channels()) {
            1 -> Imgproc.cvtColor(base, color, Imgproc.COLOR_GRAY2BGR)
            3 -> base.copyTo(color)
            4 -> Imgproc.cvtColor(base, color, Imgproc.COLOR_BGRA2BGR)
            else -> return
        }
        val scalar = org.opencv.core.Scalar(b.toDouble(), g.toDouble(), r.toDouble())
        rects.forEach { rect ->
            Imgproc.rectangle(
                color,
                org.opencv.core.Point(rect.left.toDouble(), rect.top.toDouble()),
                org.opencv.core.Point(rect.right.toDouble(), rect.bottom.toDouble()),
                scalar,
                3,
            )
        }
        Imgcodecs.imwrite(out.absolutePath, color)
        color.release()
    }

    private fun rectJson(r: Rect) = JSONObject()
        .put("l", r.left).put("t", r.top).put("r", r.right).put("b", r.bottom)

    private fun rectsToJson(rects: List<Rect>) = JSONArray().apply {
        rects.forEach { put(rectJson(it)) }
    }

    private fun hunksToJson(hunks: List<PumpHunk>) = JSONArray().apply {
        hunks.forEach { h ->
            val rf = h.rect
            put(
                JSONObject()
                    .put("text", h.text)
                    .put("l", rf.left.toDouble())
                    .put("t", rf.top.toDouble())
                    .put("r", rf.right.toDouble())
                    .put("b", rf.bottom.toDouble()),
            )
        }
    }
}
