package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Minimal production-path OCR exercise for library/smoke gates:
 * angle (Paddle heatmap) → deskew → det boxes → crop → rec (V3).
 *
 * Reuses the same helpers as Quick Fill / pump paths:
 * [OdometerOcrUtils.calculatePaddleAngleOptimized], [OdometerOcrUtils.rotate],
 * [NativePaddleEngine.detect], [NativePaddleEngine.recognize].
 *
 * Does **not** run vehicle landmark ID / ICRS odo crop (those need a vehicle DB).
 * Fixture images live under `third_party/paddle/tests/ocr_functional/` and
 * `androidTest/assets/ocr_functional/`.
 */
object OcrFunctionalPipeline {
    private const val TAG = "OcrFunctionalPipeline"

    data class StageTimings(
        val angleMs: Long,
        val deskewMs: Long,
        val detMs: Long,
        val recMs: Long,
        val totalMs: Long,
    )

    data class CropBox(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val confidence: Float,
    ) {
        fun toRect(): Rect = Rect(left, top, right, bottom)
        fun width(): Int = right - left
        fun height(): Int = bottom - top
    }

    data class Result(
        val ok: Boolean,
        val angleDeg: Float,
        val boxCount: Int,
        val primaryBox: CropBox?,
        val ocrText: String,
        val ocrTextNormalized: String,
        val timings: StageTimings,
        val error: String? = null,
        val debug: Map<String, String> = emptyMap(),
    )

    data class Expectations(
        val sourceAngleDeg: Float,
        val angleAbsToleranceDeg: Float,
        val expectTextNormalized: String,
        val expectTextContains: List<String>,
        val minDetectedBoxes: Int,
        /** Allow near-miss OCR (default 2). Exact match or all contains still pass. */
        val maxEditDistance: Int = 2,
    )

    data class Verdict(
        val pass: Boolean,
        val failures: List<String>,
        val result: Result,
    )

    fun loadExpectationsFromJson(json: String): Expectations {
        // Tiny hand parser to avoid pulling org.json edge cases; expected.json is simple.
        fun str(key: String): String {
            val re = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            return re.find(json)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("missing string $key")
        }
        fun num(key: String): Float {
            val re = Regex("\"$key\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)")
            return re.find(json)?.groupValues?.get(1)?.toFloatOrNull()
                ?: throw IllegalArgumentException("missing number $key")
        }
        fun int(key: String): Int {
            val re = Regex("\"$key\"\\s*:\\s*([0-9]+)")
            return re.find(json)?.groupValues?.get(1)?.toIntOrNull()
                ?: throw IllegalArgumentException("missing int $key")
        }
        val contains = Regex("\"expect_text_contains\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
            .find(json)?.groupValues?.get(1)
            ?.let { body ->
                Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
            } ?: emptyList()
        val maxEdit = try {
            int("max_edit_distance")
        } catch (_: Exception) {
            2
        }
        return Expectations(
            sourceAngleDeg = num("source_angle_deg"),
            angleAbsToleranceDeg = num("angle_abs_tolerance_deg"),
            expectTextNormalized = str("expect_text_normalized"),
            expectTextContains = contains.ifEmpty { listOf(str("expect_text_normalized")) },
            minDetectedBoxes = int("min_detected_boxes"),
            maxEditDistance = maxEdit,
        )
    }

    fun normalizeOcr(text: String): String =
        text.uppercase()
            .replace(Regex("[^A-Z0-9]"), "")

    /** Classic Levenshtein distance (small strings only). */
    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val m = a.length
        val n = b.length
        val dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..n) {
                val tmp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(prev, dp[j], dp[j - 1])
                prev = tmp
            }
        }
        return dp[n]
    }

    /**
     * Run the pipeline on a PNG/JPEG file path already on device storage.
     */
    suspend fun runOnFile(context: Context, imagePath: String): Result = withContext(Dispatchers.IO) {
        val tTotal = System.currentTimeMillis()
        try {
            ensureRuntime(context)
            val bmp = BitmapFactory.decodeFile(imagePath)
                ?: return@withContext fail("decode_failed", tTotal, "cannot decode $imagePath")
            try {
                runOnBitmap(context, bmp)
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "runOnFile failed", t)
            fail("exception", tTotal, t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Core path: Bitmap → BufferSet A → angle → deskew → det → crop → rec V3.
     */
    suspend fun runOnBitmap(context: Context, input: Bitmap): Result = withContext(Dispatchers.IO) {
        val tTotal = System.currentTimeMillis()
        try {
            ensureRuntime(context)
            val master = NativePaddleEngine.bufferSetA
            val recBuffer = NativePaddleEngine.recBufferSet
            val engine = NativePaddleEngine(context.applicationContext, "V3")
            if (!engine.isAvailable) {
                return@withContext fail("engine_unavailable", tTotal, "NativePaddleEngine V3 not available")
            }

            val imgW = input.width
            val imgH = input.height
            if (imgW < 8 || imgH < 8) {
                return@withContext fail("tiny_image", tTotal, "${imgW}x$imgH")
            }

            // 1) Resize primary to fixture size and ingest (same dim contract as production ingest)
            //    Keep even dims for UV planes.
            val evenW = if (imgW % 2 == 0) imgW else imgW + 1
            val evenH = if (imgH % 2 == 0) imgH else imgH + 1
            master.resize(evenW, evenH)
            master.p.clear()
            try {
                NativeImageUtils.ingestArgbToYuv(input, master.p)
            } catch (t: Throwable) {
                Log.w(TAG, "ingestArgbToYuv failed, fallback bitmapToMat+cvtColor: ${t.message}")
                val argb = org.opencv.core.Mat()
                org.opencv.android.Utils.bitmapToMat(input, argb)
                val gray = org.opencv.core.Mat()
                Imgproc.cvtColor(argb, gray, Imgproc.COLOR_RGBA2GRAY)
                if (gray.cols() != evenW || gray.rows() != evenH) {
                    val padded = org.opencv.core.Mat(evenH, evenW, gray.type(), org.opencv.core.Scalar(255.0))
                    gray.copyTo(padded.submat(0, imgH, 0, imgW))
                    padded.copyTo(master.p.mat)
                    padded.release()
                } else {
                    gray.copyTo(master.p.mat)
                }
                argb.release()
                gray.release()
            }
            master.p.clearChroma()

            // 2) Angle from Paddle heatmap (production optimized deskew)
            val tAngle0 = System.currentTimeMillis()
            val (angleDeg, angleMsReported) = OdometerOcrUtils.calculatePaddleAngleOptimized(master.p)
            val angleMs = System.currentTimeMillis() - tAngle0
            Log.i(TAG, "angle=$angleDeg deg (reported ${angleMsReported}ms)")

            // 3) Deskew — same sign as Quick Fill (cameraRotation=0): totalAngle = -optAngle
            val tDeskew0 = System.currentTimeMillis()
            val deskewAngle = -angleDeg
            OdometerOcrUtils.rotate(master, deskewAngle, evenW, evenH)
            val deskewMs = System.currentTimeMillis() - tDeskew0
            val frameW = master.width
            val frameH = master.height

            // 4) Detect text boxes on deskewed full frame
            val tDet0 = System.currentTimeMillis()
            val det = engine.detect(master.p, copyHeatmap = false)
            val detMs = System.currentTimeMillis() - tDet0
            val boxes = det?.nativeBoxes.orEmpty()
            Log.i(TAG, "det boxes=${boxes.size} meta=${det?.metadata}")

            if (boxes.isEmpty()) {
                restoreMasterBuffers()
                return@withContext Result(
                    ok = false,
                    angleDeg = angleDeg,
                    boxCount = 0,
                    primaryBox = null,
                    ocrText = "",
                    ocrTextNormalized = "",
                    timings = StageTimings(angleMs, deskewMs, detMs, 0, System.currentTimeMillis() - tTotal),
                    error = "no_det_boxes",
                    debug = mapOf("angle" to angleDeg.toString()),
                )
            }

            // 5) Primary crop = largest box by area (with 8px expand, odo-style)
            val primary = boxes.maxBy { box ->
                val ext = boxExtents(box.points)
                max(1f, ext[2] - ext[0]) * max(1f, ext[3] - ext[1])
            }
            val ext = boxExtents(primary.points)
            val pad = 8
            val crop = CropBox(
                left = floor(ext[0] - pad).toInt().coerceIn(0, frameW - 1),
                top = floor(ext[1] - pad).toInt().coerceIn(0, frameH - 1),
                right = ceil(ext[2] + pad).toInt().coerceIn(1, frameW),
                bottom = ceil(ext[3] + pad).toInt().coerceIn(1, frameH),
                confidence = primary.confidence,
            )
            if (crop.width() < 2 || crop.height() < 2) {
                restoreMasterBuffers()
                return@withContext Result(
                    ok = false,
                    angleDeg = angleDeg,
                    boxCount = boxes.size,
                    primaryBox = crop,
                    ocrText = "",
                    ocrTextNormalized = "",
                    timings = StageTimings(angleMs, deskewMs, detMs, 0, System.currentTimeMillis() - tTotal),
                    error = "degenerate_crop",
                )
            }

            // 6) Height-strip into rec buffer with source-border (no black 4px inset)
            val tRec0 = System.currentTimeMillis()
            val fed = RecBufferFeed.feedSourceBorderHeightStrip(
                master.p.mat,
                crop.left, crop.top, crop.right, crop.bottom,
                recBuffer,
                targetH = 48, maxW = 320,
            )
            val ocr = engine.recognize(recBuffer.c[fed.recCropId])
            recBuffer.c[fed.recCropId].release()
            val recMs = System.currentTimeMillis() - tRec0

            val text = ocr.debugText.trim()
            val norm = normalizeOcr(text)
            Log.i(TAG, "ocr='$text' norm='$norm' conf=${ocr.textBlocks.firstOrNull()?.confidence}")

            restoreMasterBuffers()

            Result(
                ok = text.isNotBlank(),
                angleDeg = angleDeg,
                boxCount = boxes.size,
                primaryBox = crop,
                ocrText = text,
                ocrTextNormalized = norm,
                timings = StageTimings(angleMs, deskewMs, detMs, recMs, System.currentTimeMillis() - tTotal),
                error = if (text.isBlank()) "empty_ocr" else null,
                debug = mapOf(
                    "deskew_applied_deg" to deskewAngle.toString(),
                    "target_rec" to "${fed.targetW}x${fed.targetH}",
                    "rec_src_pad_px" to fed.sourcePadPx.toString(),
                    "path" to NativePaddleEngine.productArchAndDir().let { "${it.second}/${it.first}" },
                ),
            )
        } catch (t: Throwable) {
            Log.e(TAG, "runOnBitmap failed", t)
            fail("exception", tTotal, t.message ?: t.javaClass.simpleName)
        }
    }

    fun evaluate(result: Result, exp: Expectations): Verdict {
        val failures = mutableListOf<String>()
        if (!result.ok) {
            failures += "pipeline_not_ok: ${result.error ?: "unknown"}"
        }
        if (result.boxCount < exp.minDetectedBoxes) {
            failures += "boxCount ${result.boxCount} < min ${exp.minDetectedBoxes}"
        }
        // Angle: heatmap returns deskew angle whose magnitude should match source tilt
        val angErr = abs(abs(result.angleDeg) - abs(exp.sourceAngleDeg))
        if (angErr > exp.angleAbsToleranceDeg) {
            failures += "angle |${result.angleDeg}| vs |${exp.sourceAngleDeg}| err=$angErr > tol ${exp.angleAbsToleranceDeg}"
        }
        val norm = result.ocrTextNormalized
        val want = exp.expectTextNormalized
        val exact = want.isNotEmpty() && norm == want
        val containsOk = exp.expectTextContains.isNotEmpty() &&
            exp.expectTextContains.all { norm.contains(normalizeOcr(it)) }
        val dist = if (want.isNotEmpty()) editDistance(norm, want) else Int.MAX_VALUE
        val editOk = want.isNotEmpty() && dist <= exp.maxEditDistance
        if (!exact && !containsOk && !editOk) {
            failures += "ocr norm='$norm' want='$want' contains=${exp.expectTextContains} edit=$dist (max ${exp.maxEditDistance})"
        }
        return Verdict(pass = failures.isEmpty(), failures = failures, result = result)
    }

    /**
     * Copy an asset to a cache file and return it.
     * @param assetContext context whose [Context.getAssets] holds the fixture (androidTest package)
     * @param writeContext context for cache dir (prefer app [Context] so cacheDir is writable)
     */
    fun materializeAsset(
        assetContext: Context,
        assetPath: String,
        outName: String = File(assetPath).name,
        writeContext: Context = assetContext,
    ): File {
        val dir = writeContext.cacheDir ?: writeContext.filesDir
        dir.mkdirs()
        val out = File(dir, "ocr_functional_$outName")
        assetContext.assets.open(assetPath).use { inp ->
            FileOutputStream(out).use { outp -> inp.copyTo(outp) }
        }
        return out
    }

    private fun ensureRuntime(context: Context) {
        if (!org.opencv.android.OpenCVLoader.initLocal()) {
            Log.w(TAG, "OpenCV initLocal returned false (may already be loaded)")
        }
        if (!NativePaddleEngine.isAvailableGlobally) {
            NativePaddleEngine.initializeGlobalBuffers(context.applicationContext)
        }
        if (!NativePaddleEngine.isAvailableGlobally) {
            throw IllegalStateException("NativePaddleEngine not available after init")
        }
        System.loadLibrary("native_ocr")
        System.loadLibrary("buffer_set")
    }

    /** Restore production buffer sizes after fixture resize of bufferSetA. */
    private fun restoreMasterBuffers() {
        try {
            NativePaddleEngine.bufferSetA.resize(
                NativePaddleEngine.DEFAULT_REF_DASH_W,
                NativePaddleEngine.DEFAULT_REF_DASH_H,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "restoreMasterBuffers: ${t.message}")
        }
    }

    private fun boxExtents(points: FloatArray): FloatArray {
        val minX = min(min(points[0], points[2]), min(points[4], points[6]))
        val minY = min(min(points[1], points[3]), min(points[5], points[7]))
        val maxX = max(max(points[0], points[2]), max(points[4], points[6]))
        val maxY = max(max(points[1], points[3]), max(points[5], points[7]))
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    private fun fail(code: String, tTotal: Long, msg: String): Result =
        Result(
            ok = false,
            angleDeg = 0f,
            boxCount = 0,
            primaryBox = null,
            ocrText = "",
            ocrTextNormalized = "",
            timings = StageTimings(0, 0, 0, 0, System.currentTimeMillis() - tTotal),
            error = "$code: $msg",
        )
}

