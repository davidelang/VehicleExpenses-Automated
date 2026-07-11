package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.davidlang.vehicleexpensesautomated.ui.util.ImageIngestionProvider
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.PrecisionPath
import com.davidlang.vehicleexpensesautomated.ui.util.PumpCostVolUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Headless Set G precision campaign.
 *
 * adb shell am start -n com.davidlang.vehicleexpensesautomated/.ui.experiment.PrecCampaignBatchActivity \
 *   --es paths baseline,int8_fp32,int8_fp16_u8 \
 *   --ei limit 10 \
 *   --ei timeout_sec 120
 *
 * logcat -s PrecCampaign
 * progress: getExternalFilesDir()/prec_campaign/progress.txt
 * limit: first N photos only (smoke); omit or 0 = all
 * timeout_sec: per-image wall budget (default 120); on TIMEOUT log last STAGE, skip, reload models
 */
class PrecCampaignBatchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val single = intent.getStringExtra("path")
        val multi = intent.getStringExtra("paths")
        val pathIds = when {
            !multi.isNullOrBlank() -> multi.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            !single.isNullOrBlank() -> listOf(single.trim())
            else -> listOf(
                PrecisionPath.BASELINE.id,
                PrecisionPath.INT8_FP32.id,
                PrecisionPath.INT8_FP32_U8.id,
                PrecisionPath.INT8_FP16_F32.id,
                PrecisionPath.INT8_FP16_U8.id,
                PrecisionPath.FLOAT_FP16.id,
                PrecisionPath.UINT8_FP32.id,
                PrecisionPath.UINT8_FP16_F32.id,
                PrecisionPath.UINT8_FP16_U8.id,
            )
        }
        val limit = intent.getIntExtra("limit", 0)
        val timeoutSec = intent.getIntExtra("timeout_sec", DEFAULT_TIMEOUT_SEC).coerceAtLeast(30)
        // Comma-separated exact basenames; when set, only these photos run (order preserved).
        val photosExtra = intent.getStringExtra("photos")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        thread(name = "PrecCampaign") {
            try {
                runCampaign(pathIds, limit, timeoutSec, photosExtra)
            } catch (t: Throwable) {
                Log.e(TAG, "Campaign fatal", t)
                writeProgress("FATAL ${t.javaClass.simpleName}: ${t.message}")
            } finally {
                runOnUiThread { finish() }
            }
        }
    }

    private fun runCampaign(
        pathIds: List<String>,
        limit: Int = 0,
        timeoutSec: Int = DEFAULT_TIMEOUT_SEC,
        photoNames: List<String> = emptyList(),
    ) {
        val photoDir = File(getExternalFilesDir(null), "pump_photos")
        val allFiles = photoDir.listFiles()
            ?.filter { it.isFile && it.name.matches(PHOTO_RE) }
            ?.sortedBy { it.name }
            ?: emptyList()
        val files = when {
            photoNames.isNotEmpty() -> {
                val byName = allFiles.associateBy { it.name }
                photoNames.mapNotNull { byName[it] }.also { resolved ->
                    val missing = photoNames.filter { it !in byName }
                    if (missing.isNotEmpty()) {
                        Log.w(TAG, "photos extra missing ${missing.size}: ${missing.take(5)}")
                        writeProgress("PHOTOS_MISSING ${missing.size} e.g. ${missing.take(3)}")
                    }
                }
            }
            limit > 0 -> allFiles.take(limit)
            else -> allFiles
        }
        if (files.isEmpty()) {
            writeProgress("NO_PHOTOS in ${photoDir.absolutePath}")
            Log.e(TAG, "No photos in $photoDir")
            return
        }

        // Prefer internal filesDir (reliable for :prec_campaign process); also mirror progress externally.
        val outRoot = File(filesDir, "prec_campaign").apply { mkdirs() }
        try {
            getExternalFilesDir(null)?.let { File(it, "prec_campaign").mkdirs() }
        } catch (_: Throwable) {
        }
        val deviceTag = if (Build.SUPPORTED_ABIS[0].contains("arm")) "phone" else "emu"
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val timeoutMs = timeoutSec * 1000L

        writeProgress(
            "START device=$deviceTag photos=${files.size}/${allFiles.size} " +
                "limit=${if (limit > 0) limit else "all"} timeout_sec=$timeoutSec paths=$pathIds"
        )
        Log.i(
            TAG,
            "START device=$deviceTag n=${files.size}/${allFiles.size} " +
                "limit=${if (limit > 0) limit else "all"} timeout_sec=$timeoutSec paths=$pathIds"
        )
        NativePaddleEngine.initializeGlobalBuffers(applicationContext)
        if (!NativePaddleEngine.isAvailableGlobally) {
            writeProgress("PADDLE_INIT_FAILED")
            return
        }

        val engine = NativePaddleEngine(applicationContext, "V3")
        val totalJobs = pathIds.size * files.size
        var doneJobs = 0

        // One worker at a time; replaced after TIMEOUT so a hung native thread does not block the campaign.
        var imageExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "PrecCampaign-Image").apply { isDaemon = true }
        }

        val isArm = Build.SUPPORTED_ABIS[0].contains("arm")
        for (pathId in pathIds) {
            val path = try {
                PrecisionPath.fromId(pathId)
            } catch (t: Throwable) {
                writeProgress("PATH_FAIL $pathId unknown_id ${t.message}")
                Log.e(TAG, "Unknown path id $pathId", t)
                doneJobs += files.size
                continue
            }
            // Belt-and-suspenders: never run a different path than the campaign label.
            if (path.id != pathId) {
                writeProgress("PATH_FAIL $pathId id_mismatch active_would_be=${path.id}")
                Log.e(TAG, "PATH_FAIL $pathId fromId returned ${path.id}")
                doneJobs += files.size
                continue
            }
            writeProgress("PATH_BEGIN $pathId ($doneJobs/$totalJobs)")
            Log.i(TAG, "PATH_BEGIN $pathId")
            if (!path.isSupportedOnDevice(isArm)) {
                writeProgress(
                    "PATH_SKIP $pathId unsupported on ${if (isArm) "arm" else "x86"} " +
                        "(no silent remap; real models only on supported ABI)"
                )
                Log.w(TAG, "PATH_SKIP $pathId unsupported on this ABI")
                doneJobs += files.size
                continue
            }
            try {
                NativePaddleEngine.switchPrecisionPath(applicationContext, path)
                val active = NativePaddleEngine.activePrecisionPath.id
                if (active != pathId) {
                    writeProgress("PATH_FAIL $pathId active_mismatch active=$active")
                    Log.e(TAG, "PATH_FAIL $pathId after switch active=$active")
                    doneJobs += files.size
                    continue
                }
                writeProgress("PATH_ACTIVE $pathId ok models_loaded")
                Log.i(TAG, "PATH_ACTIVE $pathId confirmed")
            } catch (t: Throwable) {
                Log.e(TAG, "switch path failed $pathId", t)
                writeProgress("PATH_FAIL $pathId ${t.message}")
                doneJobs += files.size
                continue
            }

            val outFile = File(outRoot, "${deviceTag}_${pathId}_$ts.jsonl")
            FileWriter(outFile, true).use { writer ->
                files.forEachIndexed { idx, file ->
                    val index1 = idx + 1
                    val t0 = System.currentTimeMillis()
                    NativePaddleEngine.heartbeat("image_begin $pathId $index1/${files.size} ${file.name}")
                    writeProgress(
                        "IMAGE_BEGIN $deviceTag $pathId $index1/${files.size} ${file.name}"
                    )

                    val stageAtTimeout = AtomicReference("?")
                    val future = imageExecutor.submit<ImageResult> {
                        processOneImage(engine, file, pathId, deviceTag, index1)
                    }
                    try {
                        val result = future.get(timeoutMs, TimeUnit.MILLISECONDS)
                        writer.append(result.jsonLine).append('\n')
                        writer.flush()
                        if (result.ok) {
                            if (result.qualityFailEmptyOcr) {
                                Log.e(
                                    TAG,
                                    "QUALITY_FAIL empty_ocr $pathId $index1 ${file.name} " +
                                        "red=${result.nRed} active=${NativePaddleEngine.activePrecisionPath.id}"
                                )
                            }
                            Log.i(
                                TAG,
                                "OK $pathId $index1/${files.size} ${file.name} " +
                                    "n_ocr=${result.nOcr} n_nonempty=${result.nNonEmpty} " +
                                    "strings=${result.stringSample} " +
                                    "total_ms=${result.totalMs}"
                            )
                        } else {
                            Log.e(TAG, "FAIL $pathId $index1 ${file.name}: ${result.error}")
                        }
                    } catch (te: TimeoutException) {
                        future.cancel(true)
                        stageAtTimeout.set(NativePaddleEngine.lastStage)
                        val stage = stageAtTimeout.get()
                        val wall = System.currentTimeMillis() - t0
                        Log.e(
                            TAG,
                            "TIMEOUT $pathId $index1/${files.size} ${file.name} " +
                                "after ${timeoutSec}s last_stage=$stage wall_ms=$wall"
                        )
                        writeProgress(
                            "TIMEOUT $deviceTag $pathId $index1/${files.size} ${file.name} " +
                                "last_stage=$stage"
                        )
                        val row = JSONObject()
                            .put("index1", index1)
                            .put("file", file.name)
                            .put("path", pathId)
                            .put("device", deviceTag)
                            .put("ok", false)
                            .put("timeout", true)
                            .put("error", "TimeoutException: exceeded ${timeoutSec}s at stage=$stage")
                            .put("last_stage", stage)
                            .put("wall_ms", wall)
                        writer.append(row.toString()).append('\n')
                        writer.flush()

                        // Abandon hung worker (native cancel is not reliable) and reload models.
                        try {
                            imageExecutor.shutdownNow()
                        } catch (_: Throwable) {
                        }
                        imageExecutor = Executors.newSingleThreadExecutor { r ->
                            Thread(r, "PrecCampaign-Image").apply { isDaemon = true }
                        }
                        try {
                            NativePaddleEngine.releaseAllPredictors("after_timeout $pathId ${file.name}")
                            NativePaddleEngine.switchPrecisionPath(applicationContext, path)
                            Log.i(TAG, "Reloaded path=$pathId after TIMEOUT")
                        } catch (t: Throwable) {
                            Log.e(TAG, "reload after TIMEOUT failed path=$pathId", t)
                            writeProgress("PATH_FAIL_RELOAD $pathId ${t.message}")
                        }
                    } catch (ee: ExecutionException) {
                        val cause = ee.cause ?: ee
                        Log.e(TAG, "FAIL $pathId $index1 ${file.name}", cause)
                        val row = JSONObject()
                            .put("index1", index1)
                            .put("file", file.name)
                            .put("path", pathId)
                            .put("device", deviceTag)
                            .put("ok", false)
                            .put(
                                "error",
                                cause.javaClass.simpleName + ": " + (cause.message ?: "")
                            )
                            .put("last_stage", NativePaddleEngine.lastStage)
                            .put("wall_ms", System.currentTimeMillis() - t0)
                        writer.append(row.toString()).append('\n')
                        writer.flush()
                    } catch (t: Throwable) {
                        Log.e(TAG, "FAIL $pathId $index1 ${file.name}", t)
                        val row = JSONObject()
                            .put("index1", index1)
                            .put("file", file.name)
                            .put("path", pathId)
                            .put("device", deviceTag)
                            .put("ok", false)
                            .put("error", t.javaClass.simpleName + ": " + (t.message ?: ""))
                            .put("last_stage", NativePaddleEngine.lastStage)
                            .put("wall_ms", System.currentTimeMillis() - t0)
                        writer.append(row.toString()).append('\n')
                        writer.flush()
                    }
                    doneJobs++
                    writeProgress(
                        "PROGRESS $deviceTag $pathId $index1/${files.size} overall=$doneJobs/$totalJobs"
                    )
                }
            }
            writeProgress("PATH_END $pathId file=${outFile.name}")
            Log.i(TAG, "PATH_END $pathId -> ${outFile.absolutePath}")
        }
        try {
            imageExecutor.shutdownNow()
        } catch (_: Throwable) {
        }
        writeProgress("DONE overall=$doneJobs/$totalJobs")
        Log.i(TAG, "DONE")
    }

    private data class ImageResult(
        val ok: Boolean,
        val jsonLine: String,
        val nOcr: Int = 0,
        val nNonEmpty: Int = 0,
        val nRed: Int = 0,
        val qualityFailEmptyOcr: Boolean = false,
        val stringSample: String = "",
        val totalMs: Long? = null,
        val error: String = "",
    )

    private fun processOneImage(
        engine: NativePaddleEngine,
        file: File,
        pathId: String,
        deviceTag: String,
        index1: Int,
    ): ImageResult {
        val t0 = System.currentTimeMillis()
        try {
            NativePaddleEngine.heartbeat("probe ${file.name}")
            val (probedW, probedH) = ImageIngestionProvider.probeDimensions(
                applicationContext, file.absolutePath
            )
            if (probedW <= 0 || probedH <= 0) {
                throw IllegalStateException("bad probe ${probedW}x$probedH")
            }
            val ws = NativePaddleEngine.bufferSetA
            ws.resize(probedW, probedH)
            NativePaddleEngine.heartbeat("ingest_begin ${file.name} ${probedW}x$probedH")
            runBlocking {
                ImageIngestionProvider.ingestFromFile(
                    applicationContext, file.absolutePath, ws.p
                )
            }
            NativePaddleEngine.heartbeat("ingest_done ${file.name}")
            val imgW = ws.p.width
            val imgH = ws.p.height
            NativePaddleEngine.heartbeat("extract_begin ${imgW}x$imgH")
            val detail = runBlocking {
                PumpCostVolUtils.runSetGCostVolExtractionDetailed(
                    ws, engine, NativePaddleEngine.recBufferSet, imgW, imgH
                )
            }
            NativePaddleEngine.heartbeat("extract_done ${file.name}")
            val timings = JSONObject()
            detail.timingsMs.forEach { (k, v) -> timings.put(k, v) }
            val ocrBlue = JSONArray()
            val ocrOrange = JSONArray()
            val ocrAll = JSONArray()
            val ocrStrings = JSONArray()
            // Blue candidates (classification source)
            for (c in detail.ocrCandidates) {
                val j = PumpCostVolUtils.redBoxOcrCandidateToJson(c)
                ocrBlue.put(j)
                ocrAll.put(j)
                if (c.asis.isNotBlank()) ocrStrings.put(c.asis)
                if (c.digits.isNotBlank() && c.digits != c.asis) ocrStrings.put(c.digits)
            }
            // Orange candidates (extra crops; not used for cost/vol pick)
            for (c in detail.ocrOrangeCandidates) {
                val j = PumpCostVolUtils.redBoxOcrCandidateToJson(c)
                ocrOrange.put(j)
                ocrAll.put(j)
                if (c.asis.isNotBlank()) ocrStrings.put("orange:${c.asis}")
                if (c.digits.isNotBlank() && c.digits != c.asis) ocrStrings.put("orange:${c.digits}")
            }
            val nNonEmpty = (detail.ocrCandidates + detail.ocrOrangeCandidates).count {
                it.asis.isNotBlank() || it.digits.isNotBlank()
            }
            val row = JSONObject()
                .put("index1", index1)
                .put("file", file.name)
                .put("path", pathId)
                .put("device", deviceTag)
                .put("active_path", NativePaddleEngine.activePrecisionPath.id)
                .put("ocr_strings", ocrStrings)
                // Back-compat: ocr_candidates = blue only (classification set)
                .put("ocr_candidates", ocrBlue)
                .put("ocr_blue", ocrBlue)
                .put("ocr_orange", ocrOrange)
                .put("ocr_all", ocrAll)
                .put("cost", detail.result.cost)
                .put("vol", detail.result.vol)
                .put("angle_deg", detail.angleDeg.toDouble())
                .put("timings_ms", timings)
                .put("red_boxes", boxesJson(detail.redBoxes))
                .put("blue_boxes", boxesJson(detail.blueBoxes))
                .put("orange_boxes", boxesJson(detail.orangeBoxes))
                .put("wall_ms", System.currentTimeMillis() - t0)
                .put("last_stage", NativePaddleEngine.lastStage)
                .put("ok", true)
            val sample = (detail.ocrCandidates + detail.ocrOrangeCandidates)
                .map { it.digits.ifBlank { it.asis } }
                .filter { it.isNotBlank() }
                .take(8)
                .toString()
            return ImageResult(
                ok = true,
                jsonLine = row.toString(),
                nOcr = detail.ocrCandidates.size + detail.ocrOrangeCandidates.size,
                nNonEmpty = nNonEmpty,
                nRed = detail.redBoxes.size,
                qualityFailEmptyOcr = detail.redBoxes.isNotEmpty() && nNonEmpty == 0,
                stringSample = sample,
                totalMs = detail.timingsMs["t_total_ms"],
            )
        } catch (t: Throwable) {
            val row = JSONObject()
                .put("index1", index1)
                .put("file", file.name)
                .put("path", pathId)
                .put("device", deviceTag)
                .put("ok", false)
                .put("error", t.javaClass.simpleName + ": " + (t.message ?: ""))
                .put("last_stage", NativePaddleEngine.lastStage)
                .put("wall_ms", System.currentTimeMillis() - t0)
            return ImageResult(
                ok = false,
                jsonLine = row.toString(),
                error = t.javaClass.simpleName + ": " + (t.message ?: ""),
            )
        }
    }

    private fun boxesJson(boxes: List<android.graphics.Rect>): JSONArray {
        val arr = JSONArray()
        for (r in boxes) {
            arr.put(
                JSONObject()
                    .put("l", r.left).put("t", r.top)
                    .put("r", r.right).put("b", r.bottom)
            )
        }
        return arr
    }

    private fun writeProgress(line: String) {
        val body = line + "\n" + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        try {
            val f = File(filesDir, "prec_campaign/progress.txt")
            f.parentFile?.mkdirs()
            f.writeText(body)
        } catch (_: Throwable) {
        }
        try {
            getExternalFilesDir(null)?.let {
                val f2 = File(it, "prec_campaign/progress.txt")
                f2.parentFile?.mkdirs()
                f2.writeText(body)
            }
        } catch (_: Throwable) {
        }
        // World-readable for adb without run-as
        try {
            File("/data/local/tmp/prec_campaign_progress_${if (Build.SUPPORTED_ABIS[0].contains("arm")) "phone" else "emu"}.txt")
                .writeText(body)
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "PrecCampaign"
        private const val DEFAULT_TIMEOUT_SEC = 120
        private val PHOTO_RE = Regex("(?i).*\\.(jpg|jpeg|png|dng)$")
    }
}
