package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import com.davidlang.vehicleexpensesautomated.ui.experiment.ExperimentReportHtml
import com.davidlang.vehicleexpensesautomated.ui.experiment.ReportCellRef
import com.davidlang.vehicleexpensesautomated.ui.experiment.ReportCollapser
import com.davidlang.vehicleexpensesautomated.ui.experiment.ocrPumpFeedOriented40
import com.davidlang.vehicleexpensesautomated.ui.experiment.recognizeRotGmmQuads
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Dash-only collection: product_det single-feed at each long-edge on raw and deskewed gray.
 * Raw gray uses [recognizeRotGmmQuads].
 * Deskew gray uses [ocrPumpFeedOriented40] and [VehicleExpensesApplication.anchoredEngineV3].
 * No seed merge and no interior-energy expand. Collapser + FGS job stay.
 *
 * The Lite feed is this runner's square letterbox at the requested long-edge.
 * [PumpCostVolUtils.prepareScale] and [NativePaddleEngine.deskewSetFor] are not used:
 * they clamp every long-edge above 256 onto the 608 square.
 */
object MultiScaleDetRunner {
    private const val TAG = "MultiScaleDet"

    /** Long-lived master and deskew capacity. Detection does not use 4096. */
    private const val MAX_SIDE = 4096

    /** Existing processHeatmap connected-component area floor. */
    private const val HEAT_MIN_AREA = 10f

    private const val PREVIEW_MAX_W = 360
    private const val ASSET_DIR = "paddle/exp_det_ab"
    private const val MODEL_NAME = "product_det"
    private const val COLLAPSE_PERIOD_MS = 60_000L
    private const val DESKEW_LONG_EDGE = 256

    /**
     * Single-feed long-edges, ascending. 224 through product maxLite 2048,
     * including pump operating points 224 and 608. No 64, no 96, no 4096.
     * No square / hspan / vspan rows.
     */
    val OUTER_SCALES: List<Int> = listOf(224, 256, 384, 512, 608, 768, 1024, 2048)

    private val ROT_G_VERT: Float = SET_G_ROT_VERT_FACTORS.first()

    val DET_MODELS: List<String> = listOf(MODEL_NAME)

    /** @deprecated use [OUTER_SCALES]. */
    val SCALES: List<Int> get() = OUTER_SCALES


    fun effectiveRowCount(): Int = OUTER_SCALES.size

    /** Alignment paddle columns. Not a sweep row and not a second scale. */
    private const val ALIGN_DET_LONG_EDGE = 2048

    private var alignDetPred: MonoDetPredictor? = null
    private var alignDetHeat: Mat? = null
    private var alignDetRec: BufferSet? = null

    /**
     * product_det at long-edge 2048 plus rot-G-- recognition on [gray].
     * Same letterbox and rot-G side fractions as the multi-scale runner (left 0.54, right 0.55).
     * [rotGVert] defaults to [SET_G_ROT_VERT_FACTORS.first()] (0.34); alignment passes 0.40f.
     * No second scale and no seed merge. Returned blocks are in [gray]'s pixels.
     */
    fun productDetLandmarks2048(
        context: Context,
        gray: Mat,
        rotGVert: Float = SET_G_ROT_VERT_FACTORS.first(),
    ): List<TextBlock> {
        if (gray.empty()) return emptyList()
        return try {
            productDetLandmarks2048Once(context, gray, rotGVert)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.e(TAG, "product_det 2048: ${t.message}", t)
            releaseAlignDet()
            productDetLandmarks2048Once(context, gray, rotGVert)
        }
    }

    private fun alignDetSession(context: Context): Pair<MonoDetPredictor, BufferSet> {
        val pred = alignDetPred
        val rec = alignDetRec
        if (pred != null && rec != null) return pred to rec
        val side = ALIGN_DET_LONG_EDGE
        val nb = copyModelAsset(context, modelArchForDevice())
        val heat = Mat(side, side, CvType.CV_8UC1)
        alignDetHeat = heat
        val created = MonoDetPredictor(nb, mapOf(side to ByteArray(side * side)), heat)
        alignDetPred = created
        val recH = NativeImageUtils.HEATMAP_MAX_BOXES_DEFAULT * RecBufferFeed.DEFAULT_REC_H
        val recBuf = BufferSet(NativePaddleEngine.REC_CANVAS_W, recH)
        alignDetRec = recBuf
        Log.i(TAG, "product_det 2048 loaded ${nb.name}")
        return created to recBuf
    }

    private fun releaseAlignDet() {
        try {
            alignDetPred?.close()
        } catch (_: Throwable) {
        }
        alignDetPred = null
        try {
            alignDetHeat?.release()
        } catch (_: Throwable) {
        }
        alignDetHeat = null
        try {
            alignDetRec?.release()
        } catch (_: Throwable) {
        }
        alignDetRec = null
    }

    private fun productDetLandmarks2048Once(context: Context, gray: Mat, rotGVert: Float): List<TextBlock> {
        val side = ALIGN_DET_LONG_EDGE
        val (pred, rec) = alignDetSession(context)
        val det = pred.detectQuads(gray, side) ?: return emptyList()
        val srcW = gray.cols()
        val srcH = gray.rows()
        val n = det.corners.size
        if (n == 0) return emptyList()
        val bluePts = FloatArray(n * 8)
        val aabbs = ArrayList<Rect>(n)
        val seeds = ArrayList<IntArray>(n)
        for (i in det.corners.indices) {
            val photoPts = mapHeatCorners(
                det.corners[i], det.heatW, det.heatH, det.feed,
                det.contentW, det.contentH, srcW, srcH,
            )
            val seedI = roundQuad(photoPts)
            val seedQ = ContentExpandUtils.orientedFromPoints8(intsToFloats(seedI))
            val blueQ = ContentExpandUtils.calculatedOrientedUv(
                seedQ, rotGVert, SET_G_UV_LEFT_FRAC, SET_G_UV_RIGHT_FRAC,
            )
            val blueI = roundQuad(blueQ.pts)
            for (k in 0 until 8) bluePts[i * 8 + k] = blueI[k].toFloat()
            aabbs.add(aabbOf(seedI))
            seeds.add(seedI)
        }
        try {
            rec.clearCrops()
            val raw = recognizeRotGmmQuads(
                gray.nativeObj,
                bluePts,
                rec,
                MAX_SIDE,
                MAX_SIDE,
                landmarkAlphabet = true,
            )
            val out = ArrayList<TextBlock>(n)
            for (i in aabbs.indices) {
                val text = OdometerOcrUtils.cleanLandmarkString(raw.asis.getOrNull(i) ?: "")
                val q = seeds[i]
                val pts = listOf(
                    Point(q[0].toDouble(), q[1].toDouble()),
                    Point(q[2].toDouble(), q[3].toDouble()),
                    Point(q[4].toDouble(), q[5].toDouble()),
                    Point(q[6].toDouble(), q[7].toDouble()),
                )
                out.add(TextBlock(text, aabbs[i], points = pts))
            }
            Log.i(TAG, "product_det 2048 seeds=$n kept=${out.size} ${srcW}x$srcH")
            return out
        } finally {
            try {
                rec.clearCrops()
            } catch (_: Throwable) {
            }
        }
    }

    data class PhotoRef(
        val file: File,
        val domain: String,
        val displayName: String,
        val nativeW: Int = 0,
        val nativeH: Int = 0,
    )

    data class Result(
        val reportDir: File,
        val jsonFile: File,
        val nPhotos: Int,
        val models: List<String>,
        val arch: String,
        val message: String,
    )

    private data class QuadDet(
        val corners: List<FloatArray>,
        val heatW: Int,
        val heatH: Int,
        val feed: Int,
        val contentW: Int,
        val contentH: Int,
        val capped: Boolean,
    )

    private data class SeedRec(
        val text: String,
        val probs: String,
        val uSpan: Float,
        val vSpan: Float,
        val aabb: Rect,
        val seed: IntArray,
        val blue: IntArray,
    )

    private data class SideSeeds(
        val seeds: List<SeedRec>,
        val capped: Boolean,
        val feed: Int,
        val contentW: Int,
        val contentH: Int,
        val heatW: Int,
        val heatH: Int,
        val imageW: Int,
        val imageH: Int,
        val overlayB64: String = "",
        val error: String? = null,
    )

    fun modelArchForDevice(): String = NativePaddleEngine.modelArchForPrimaryAbi()

    fun dashPhotoDir(context: Context): File =
        File(context.getExternalFilesDir(null), "dash_photos").also { it.mkdirs() }

    fun reportDir(context: Context): File =
        File(context.getExternalFilesDir(null), "multi_scale_det_reports").also { it.mkdirs() }

    /** Dash photos only. Does not seed an expense receipt and does not list pump photos. */
    fun collectPhotos(context: Context): List<PhotoRef> {
        val exts = setOf("jpg", "jpeg", "png", "dng")
        val dir = dashPhotoDir(context)
        val files = dir.listFiles { f ->
            f.isFile && f.extension.lowercase() in exts
        }?.sortedBy { it.name } ?: emptyList()
        return files.map { f ->
            val (nw, nh) = try {
                ImageIngestionProvider.probeDimensions(context, f.absolutePath)
            } catch (_: Throwable) {
                0 to 0
            }
            PhotoRef(f, "dash", f.name, nw, nh)
        }
    }

    fun countByDomain(photos: List<PhotoRef>): Map<String, Int> =
        photos.groupingBy { it.domain }.eachCount()

    fun availableModels(context: Context, arch: String = modelArchForDevice()): List<String> {
        return DET_MODELS.filter { name ->
            try {
                context.assets.open("$ASSET_DIR/${name}_$arch.nb").close()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun cellsPerPhoto(): Int = OUTER_SCALES.size

    private fun paddleId(photoIndex: Int, edgeIndex: Int): Int =
        photoIndex * cellsPerPhoto() + 1 + edgeIndex

    private fun photoIndexForId(id: Int): Int = (id - 1) / cellsPerPhoto()

    private fun idTag(id: Int): String = ReportCollapser.idTag(id)
    private fun htmlBegin(id: Int) = ReportCollapser.htmlBegin(id)
    private fun htmlEnd(id: Int) = ReportCollapser.htmlEnd(id)
    private fun jsonBegin(id: Int) = ReportCollapser.jsonBegin(id)
    private fun jsonEnd(id: Int) = ReportCollapser.jsonEnd(id)

    private fun copyModelAsset(context: Context, arch: String): File {
        val asset = "$ASSET_DIR/${MODEL_NAME}_$arch.nb"
        val dest = File(context.filesDir, "exp_det_${MODEL_NAME}_$arch.nb")
        context.assets.open(asset).use { inp ->
            FileOutputStream(dest).use { out -> inp.copyTo(out) }
        }
        return dest
    }

    /**
     * One product_det graph. Reloads when the square feed side changes.
     * [feedBySide] values are exact side×side host planes.
     */
    private class MonoDetPredictor(
        private val nbFile: File,
        private val feedBySide: Map<Int, ByteArray>,
        private val heatScratch: Mat,
    ) {
        private var pred: PaddlePredictor? = null
        private var lastSide: Int = -1

        private fun openPredictor(side: Int) {
            val config = MobileConfig()
            config.setThreads(4)
            config.setPowerMode(PowerMode.LITE_POWER_NO_BIND)
            config.setModelFromFile(nbFile.absolutePath)
            val p = PaddlePredictor.createPaddlePredictor(config)
                ?: throw IllegalStateException("createPaddlePredictor null for ${nbFile.name}")
            val s = side.coerceAtLeast(1)
            p.getInput(0).resize(longArrayOf(1, 1, s.toLong(), s.toLong()))
            pred = p
            lastSide = s
        }

        private fun ensureLiteShape(side: Int): PaddlePredictor {
            val cur = pred
            if (cur != null && lastSide == side) return cur
            if (cur != null) {
                Log.i(TAG, "reload $MODEL_NAME Lite input $lastSide → $side")
                pred = null
                lastSide = -1
                NativePaddleEngine.releasePredictor(cur, "exp_det_${MODEL_NAME}_reshape")
                System.gc()
            }
            openPredictor(side)
            return pred!!
        }

        fun close() {
            val p = pred ?: return
            pred = null
            lastSide = -1
            NativePaddleEngine.releasePredictor(p, "exp_det_$MODEL_NAME")
        }

        fun detectQuads(gray: Mat, side: Int): QuadDet? {
            if (gray.empty()) return null
            val buf = feedBySide[side]
                ?: throw IllegalStateException("no feed for side=$side")
            require(buf.size == side * side) { "feed ${buf.size} != $side²" }
            val srcW = gray.cols()
            val srcH = gray.rows()
            val (contentW, contentH) = contentSize(srcW, srcH, side)
            var scaled: Mat = gray
            var ownedScale = false
            try {
                if (contentW != srcW || contentH != srcH) {
                    val m = Mat()
                    ownedScale = true
                    scaled = m
                    Imgproc.resize(
                        gray, m,
                        Size(contentW.toDouble(), contentH.toDouble()),
                        0.0, 0.0, Imgproc.INTER_AREA,
                    )
                }
                Arrays.fill(buf, 0.toByte())
                NativeImageUtils.populateMonoUInt8(scaled, buf, side, side)
            } finally {
                if (ownedScale) scaled.release()
            }
            val p = ensureLiteShape(side)
            val input = p.getInput(0)
            input.resize(longArrayOf(1, 1, side.toLong(), side.toLong()))
            if (!input.setData(buf)) return null
            p.run()
            val output = p.getOutput(0)
            val dims = output.shape()
            val heatH = dims[2].toInt()
            val heatW = dims[3].toInt()
            if (heatW.toLong() * heatH.toLong() > heatScratch.total()) {
                Log.e(TAG, "heat ${heatW}x$heatH exceeds scratch ${heatScratch.total()}")
                return null
            }
            val nativeRes = NativeImageUtils.processHeatmap(
                tensor = output,
                threshold = HEAT_THR_U8_GE1,
                minArea = HEAT_MIN_AREA,
                boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                maskDilatePasses = 0,
                maxBoxes = NativeImageUtils.HEATMAP_MAX_BOXES_DEFAULT,
                growCells = 0,
                scratchY = heatScratch,
            ) ?: return null
            val boxFloats = nativeRes.size - 100
            if (boxFloats < 0 || boxFloats % 9 != 0) return null
            val n = boxFloats / 9
            val corners = ArrayList<FloatArray>(n)
            for (i in 0 until n) {
                val o = i * 9
                corners.add(
                    floatArrayOf(
                        nativeRes[o], nativeRes[o + 1], nativeRes[o + 2], nativeRes[o + 3],
                        nativeRes[o + 4], nativeRes[o + 5], nativeRes[o + 6], nativeRes[o + 7],
                    ),
                )
            }
            return QuadDet(
                corners = corners,
                heatW = heatW,
                heatH = heatH,
                feed = side,
                contentW = contentW,
                contentH = contentH,
                capped = n == NativeImageUtils.HEATMAP_MAX_BOXES_DEFAULT,
            )
        }
    }

    suspend fun run(
        context: Context,
        maxPhotos: Int? = null,
        /** Exact basenames. Selected sample passes [com.davidlang.vehicleexpensesautomated.ui.experiment.SelectedSamplePhotos.DASH]. */
        allowNames: Collection<String>? = null,
        /** If non-null, only these domains. Collection is dash, so other domains match nothing. */
        allowDomains: Set<String>? = null,
        onLog: (String) -> Unit = {},
        onProgress: (done: Int, total: Int, name: String) -> Unit = { _, _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val arch = modelArchForDevice()
        val models = availableModels(context, arch)
        if (models.isEmpty()) {
            throw IllegalStateException("No $MODEL_NAME for arch=$arch under assets/$ASSET_DIR")
        }
        val allow = allowNames?.toSet()
        val photosAll = collectPhotos(context).filter { ref ->
            (allowDomains == null || ref.domain in allowDomains) &&
                (allow == null || ref.displayName in allow || ref.file.name in allow)
        }
        val photos = if (maxPhotos != null) photosAll.take(maxPhotos) else photosAll
        if (photos.isEmpty()) {
            throw IllegalStateException(
                "No dash photos" +
                    (if (allow != null) " matching allowlist (${allow.size})" else ""),
            )
        }
        val nPhotos = photos.size
        val per = cellsPerPhoto()
        val nCells = nPhotos * per
        val counts = countByDomain(photos)
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val outDir = reportDir(context)
        val cellsDir = File(outDir, "cells").also { it.mkdirs() }
        cellsDir.listFiles()?.forEach { it.delete() }
        val rowsPerFile = ExperimentReportHtml.htmlRowsPerFile(context)
        val nParts = ExperimentReportHtml.nParts(nPhotos, rowsPerFile)
        val chunks = ExperimentReportHtml.photoChunks(nPhotos, rowsPerFile)
        val reportStem = "multi_scale_det_report_$ts"
        val sparseFile = File(outDir, "multi_scale_det_results_$ts.sparse")
        val statusFile = File(outDir, "multi_scale_det_status_$ts.json")
        val manifestFile = File(outDir, "multi_scale_det_manifest_$ts.json")
        val cursorFile = File(outDir, "multi_scale_det_cursor_$ts.txt")
        val cellOrder = ArrayList<ReportCellRef>(nCells)
        for (pi in photos.indices) {
            for (ei in OUTER_SCALES.indices) {
                cellOrder.add(ReportCellRef(paddleId(pi, ei), pi, ei, 0))
            }
        }

        onLog(
            "START arch=$arch model=$MODEL_NAME nPhotos=$nPhotos nCells=$nCells " +
                "dash=${counts["dash"] ?: 0} longEdges=$OUTER_SCALES " +
                "feed=single rotG vert=$ROT_G_VERT " +
                "left=$SET_G_UV_LEFT_FRAC right=$SET_G_UV_RIGHT_FRAC " +
                "grow=0 dilate=0 maxBoxes=${NativeImageUtils.HEATMAP_MAX_BOXES_DEFAULT} " +
                "out=${outDir.name} ts=$ts",
        )
        ProcessMemProbe.log("before_releaseAll", onLog)
        NativePaddleEngine.releaseAllPredictors("multi_scale_det_begin")
        ProcessMemProbe.log("after_releaseAll_predictors", onLog)

        writeManifest(manifestFile, ts, arch, photos, counts, nCells)
        chunks.forEachIndexed { part0, photoRange ->
            val partIndex1 = part0 + 1
            val partFile = ExperimentReportHtml.partFile(outDir, reportStem, partIndex1, nParts)
            writeSparseHtml(
                partFile, ts, arch, photos, counts, photoRange,
                partIndex1, nParts, reportStem,
            )
        }
        writeSparseResults(sparseFile, ts, arch, photos, counts, nCells)
        cursorFile.writeText("0")
        writeStatus(statusFile, ts, "stage1_done", 0, nCells, 0, "skeleton")
        onLog("STAGE1 html_parts=$nParts stem=$reportStem sparse=${sparseFile.name} cells=$nCells")

        val collapser = ReportCollapser(
            htmlFileForId = { id ->
                val pi = photoIndexForId(id)
                val partIndex1 = ExperimentReportHtml.partIndex1ForPhoto(pi, rowsPerFile)
                ExperimentReportHtml.partFile(outDir, reportStem, partIndex1, nParts)
            },
            cellsDir = cellsDir,
            cursorFile = cursorFile,
            nCells = nCells,
            cellOrder = cellOrder,
            onLog = onLog,
            sparseFile = sparseFile,
            onStatus = { phase, done, total, cursor, detail ->
                writeStatus(statusFile, ts, phase, done, total, cursor, detail)
            },
            periodMs = COLLAPSE_PERIOD_MS,
        )
        val collapseJob = collapser.start(this)

        var ownBuffers = false
        var master: BufferSet? = null
        var deskew: BufferSet? = null
        var pred: MonoDetPredictor? = null
        var heatScratch: Mat? = null
        var recHold: BufferSet? = null
        var warpScratch: BufferSet? = null
        var cellsDone = 0
        val published = HashSet<Int>(nCells)
        var runError: Throwable? = null

        fun publish(id: Int, htmlBody: String, jsonBody: String, label: String) {
            if (!published.add(id)) return
            ReportCollapser.publishCell(cellsDir, id, htmlBody, jsonBody)
            cellsDone++
            onProgress(cellsDone, nCells, label)
        }

        fun publishError(id: Int, photo: PhotoRef, kind: String, err: String, edge: Int? = null) {
            val span = "<span class='stat' style='color:#f66'>${esc(err)}</span>"
            val html = span + htmlColumnSeam(1, 3) + span
            val o = JSONObject()
                .put("id", id)
                .put("id_tag", idTag(id))
                .put("kind", kind)
                .put("photo", photo.displayName)
                .put("domain", photo.domain)
                .put("status", "error")
                .put("error", err)
            if (edge != null) o.put("long_edge", edge)
            val label = if (edge != null) "${photo.displayName} $edge" else "${photo.displayName} $kind"
            publish(id, html, o.toString(), label)
        }

        try {
            val m: BufferSet
            val d: BufferSet
            if (NativePaddleEngine.isAvailableGlobally) {
                m = NativePaddleEngine.bufferSetA
                d = NativePaddleEngine.bufferSetB
                ownBuffers = false
                m.resize(MAX_SIDE, MAX_SIDE)
                d.resize(MAX_SIDE, MAX_SIDE)
                onLog("BUF reuse global A/B capacity ${m.width}x${m.height}")
            } else {
                m = BufferSet(MAX_SIDE, MAX_SIDE)
                d = BufferSet(MAX_SIDE, MAX_SIDE)
                ownBuffers = true
                onLog("BUF allocate private master+deskew ${MAX_SIDE}x$MAX_SIDE")
            }
            master = m
            deskew = d
            val scratchSide = OUTER_SCALES.max()
            val scratch = Mat(scratchSide, scratchSide, CvType.CV_8UC1)
            heatScratch = scratch
            val feeds = LinkedHashMap<Int, ByteArray>(OUTER_SCALES.size)
            for (le in OUTER_SCALES) feeds[le] = ByteArray(le * le)
            val nb = copyModelAsset(context, arch)
            onLog("load $MODEL_NAME ← ${nb.name} (${nb.length() / 1024} KB)")
            pred = MonoDetPredictor(nb, feeds, scratch)
            val recH = NativeImageUtils.HEATMAP_MAX_BOXES_DEFAULT * RecBufferFeed.DEFAULT_REC_H
            val rec = BufferSet(NativePaddleEngine.REC_CANVAS_W, recH)
            recHold = rec
            // Deskew gray is bufferSetB.p. The pump helper writes the warp into scratch.s.
            val warp = BufferSet(MAX_SIDE, MAX_SIDE)
            warpScratch = warp
            ProcessMemProbe.log("after_model_and_buffers", onLog)

            for (pi in photos.indices) {
                val ref = photos[pi]
                onProgress(cellsDone, nCells, ref.displayName)
                onLog("PHOTO ${pi + 1}/$nPhotos ${ref.displayName}")
                try {
                    if (!ingestPhotoToMaster(context, ref, m, MAX_SIDE, onLog)) {
                        for (ei in OUTER_SCALES.indices) {
                            publishError(paddleId(pi, ei), ref, "paddle", "ingest_fail", OUTER_SCALES[ei])
                        }
                        continue
                    }
                    var deskewAngle = 0f
                    try {
                        deskewAngle = OdometerOcrUtils.calculateAverageTextAngle(
                            m.p, DESKEW_LONG_EDGE,
                        ).angle
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        onLog("ANGLE_FAIL ${ref.displayName}: ${t.message ?: "angle_fail"}")
                    }
                    copyPrimary(m, d)
                    OdometerOcrUtils.rotate(d, -deskewAngle)
                    onLog(
                        "DESKEW ${ref.displayName} angle=$deskewAngle rot=${-deskewAngle} " +
                            "image=${m.width}x${m.height}",
                    )
                    for (ei in OUTER_SCALES.indices) {
                        val le = OUTER_SCALES[ei]
                        val id = paddleId(pi, ei)
                        val rawSide = detectAndRecognize(
                            m.p.mat, le, pred!!, rec, warp, onLog, ref.displayName, "raw",
                        )
                        val deskewSide = detectAndRecognize(
                            d.p.mat, le, pred!!, rec, warp, onLog, ref.displayName, "deskew",
                        )
                        publish(
                            id,
                            paddleHtml(rawSide, deskewSide),
                            paddleJson(id, ref, le, rawSide, deskewSide),
                            "${ref.displayName} $le",
                        )
                    }
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    onLog("PHOTO_FAIL ${ref.displayName}: ${t.javaClass.simpleName}: ${t.message}")
                    Log.e(TAG, "photo ${ref.displayName}", t)
                    val err = t.message ?: t.javaClass.simpleName
                    for (ei in OUTER_SCALES.indices) {
                        publishError(paddleId(pi, ei), ref, "paddle", err, OUTER_SCALES[ei])
                    }
                } finally {
                    try {
                        m.clearCrops()
                    } catch (_: Throwable) {
                    }
                    try {
                        d.clearCrops()
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (t: Throwable) {
            runError = t
            onLog("RUN_ERROR ${t.javaClass.simpleName}: ${t.message}")
            Log.e(TAG, "run failed", t)
        } finally {
            try {
                pred?.close()
            } catch (_: Throwable) {
            }
            pred = null
            NativePaddleEngine.releaseAllPredictors("multi_scale_det_end")
            try {
                heatScratch?.release()
            } catch (_: Throwable) {
            }
            try {
                recHold?.release()
            } catch (_: Throwable) {
            }
            try {
                warpScratch?.release()
            } catch (_: Throwable) {
            }
            collapser.requestFinal("multi_scale_det final collapse")
            try {
                collapseJob.join()
            } catch (_: CancellationException) {
            }
            collapser.drainAll()
            if (ownBuffers) {
                try {
                    master?.release()
                } catch (_: Throwable) {
                }
                try {
                    deskew?.release()
                } catch (_: Throwable) {
                }
            } else {
                try {
                    master?.clearCrops()
                } catch (_: Throwable) {
                }
                try {
                    deskew?.clearCrops()
                } catch (_: Throwable) {
                }
            }
            val phase = if (runError == null) "done" else "failed"
            writeStatus(
                statusFile, ts, phase, collapser.cursor, nCells, collapser.cursor,
                runError?.message ?: "complete",
            )
            ProcessMemProbe.log("run_finally", onLog)
        }
        if (runError != null) throw runError

        val msg = "DONE arch=$arch nPhotos=$nPhotos nCells=$nCells cursor=${collapser.cursor} ts=$ts"
        onLog(msg)
        Result(outDir, sparseFile, nPhotos, models, arch, msg)
    }

    private fun contentSize(srcW: Int, srcH: Int, side: Int): Pair<Int, Int> {
        val long = max(srcW, srcH).coerceAtLeast(1)
        if (long <= side) return srcW.coerceAtLeast(1) to srcH.coerceAtLeast(1)
        val scale = side.toFloat() / long.toFloat()
        var cw = (srcW * scale).roundToInt().coerceIn(1, side)
        var ch = (srcH * scale).roundToInt().coerceIn(1, side)
        if (srcW >= srcH) cw = side else ch = side
        return cw to ch
    }

    private fun mapHeatCorners(
        heat: FloatArray,
        heatW: Int,
        heatH: Int,
        feed: Int,
        contentW: Int,
        contentH: Int,
        srcW: Int,
        srcH: Int,
    ): FloatArray {
        val out = FloatArray(8)
        val hw = heatW.coerceAtLeast(1).toFloat()
        val hh = heatH.coerceAtLeast(1).toFloat()
        val cw = contentW.coerceAtLeast(1).toFloat()
        val ch = contentH.coerceAtLeast(1).toFloat()
        for (i in 0 until 4) {
            val fx = heat[i * 2] * feed.toFloat() / hw
            val fy = heat[i * 2 + 1] * feed.toFloat() / hh
            out[i * 2] = fx * srcW.toFloat() / cw
            out[i * 2 + 1] = fy * srcH.toFloat() / ch
        }
        return out
    }

    private fun roundQuad(pts: FloatArray): IntArray {
        val out = IntArray(8)
        for (i in 0 until 8) out[i] = pts[i].roundToInt()
        return out
    }

    private fun intsToFloats(q: IntArray): FloatArray = FloatArray(q.size) { q[it].toFloat() }

    private fun aabbOf(q: IntArray): Rect {
        val l = minOf(q[0], q[2], q[4], q[6])
        val t = minOf(q[1], q[3], q[5], q[7])
        val r = maxOf(q[0], q[2], q[4], q[6])
        val b = maxOf(q[1], q[3], q[5], q[7])
        return Rect(l, t, if (r > l) r else l + 1, if (b > t) b else t + 1)
    }

    /** Raw gray: pump rot branch. Stores the as-is string. */
    private fun recognizeRawRot(
        gray: Mat,
        bluePts: FloatArray,
        seeds: MutableList<SeedRec>,
        recHold: BufferSet,
    ) {
        val raw = recognizeRotGmmQuads(
            gray.nativeObj,
            bluePts,
            recHold,
            MAX_SIDE,
            MAX_SIDE,
        )
        for (i in seeds.indices) {
            val s = seeds[i]
            seeds[i] = s.copy(
                text = raw.asis.getOrElse(i) { "" },
                probs = raw.asisProbs.getOrElse(i) { "" },
            )
        }
    }

    /**
     * Deskew gray: pump deskew branch. Scratch primary must not be [gray]
     * because [ocrPumpFeedOriented40] writes the warp into scratch.s.
     */
    private suspend fun recognizeDeskewPump(
        gray: Mat,
        blueQuads: List<ContentExpandUtils.OrientedQuad>,
        seeds: MutableList<SeedRec>,
        warpScratch: BufferSet,
        recHold: BufferSet,
    ) {
        if (gray.nativeObj == warpScratch.p.mat.nativeObj) {
            throw IllegalStateException("warp scratch primary holds the deskew gray")
        }
        val engine = VehicleExpensesApplication.anchoredEngineV3
            ?: throw IllegalStateException("anchoredEngineV3 missing")
        for (i in seeds.indices) {
            val fed = ocrPumpFeedOriented40(gray, blueQuads[i], warpScratch, recHold) ?: continue
            try {
                val res = engine.recognize(recHold.c[fed.recCropId])
                val s = seeds[i]
                seeds[i] = s.copy(text = res.debugText)
            } finally {
                recHold.c[fed.recCropId].release()
            }
        }
    }

    private suspend fun detectAndRecognize(
        gray: Mat,
        side: Int,
        pred: MonoDetPredictor,
        recHold: BufferSet,
        warpScratch: BufferSet,
        onLog: (String) -> Unit,
        photo: String,
        which: String,
    ): SideSeeds {
        val srcW = gray.cols()
        val srcH = gray.rows()
        val det = try {
            pred.detectQuads(gray, side)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.e(TAG, "detect $which $side: ${t.message}", t)
            onLog("DETECT_FAIL $photo $which le=$side ${t.javaClass.simpleName}: ${t.message}")
            return SideSeeds(
                emptyList(), false, side, 0, 0, 0, 0, srcW, srcH,
                error = t.message ?: "detect_fail",
            )
        }
        if (det == null) {
            onLog("DETECT_NULL $photo $which le=$side")
            return SideSeeds(
                emptyList(), false, side, 0, 0, 0, 0, srcW, srcH,
                error = "detect_null",
            )
        }
        onLog(
            "DETECT_OK $photo $which le=$side feed=${det.feed}x${det.feed} " +
                "heat=${det.heatW}x${det.heatH} content=${det.contentW}x${det.contentH} " +
                "seeds=${det.corners.size} capped=${det.capped}",
        )
        val seeds = ArrayList<SeedRec>(det.corners.size)
        val blueQuads = ArrayList<ContentExpandUtils.OrientedQuad>(det.corners.size)
        val bluePts = FloatArray(det.corners.size * 8)
        for (i in det.corners.indices) {
            val photoPts = mapHeatCorners(
                det.corners[i], det.heatW, det.heatH, det.feed,
                det.contentW, det.contentH, srcW, srcH,
            )
            val seedI = roundQuad(photoPts)
            val seedQ = ContentExpandUtils.orientedFromPoints8(intsToFloats(seedI))
            val blueQ = ContentExpandUtils.calculatedOrientedUv(
                seedQ, ROT_G_VERT, SET_G_UV_LEFT_FRAC, SET_G_UV_RIGHT_FRAC,
            )
            blueQuads.add(blueQ)
            val blueI = roundQuad(blueQ.pts)
            for (k in 0 until 8) bluePts[i * 8 + k] = blueI[k].toFloat()
            seeds.add(
                SeedRec(
                    text = "",
                    probs = "",
                    uSpan = seedQ.longAxisBw(),
                    vSpan = seedQ.shortAxisBh(),
                    aabb = aabbOf(seedI),
                    seed = seedI,
                    blue = blueI,
                ),
            )
        }
        if (seeds.isNotEmpty()) {
            try {
                recHold.clearCrops()
                when (which) {
                    "raw" -> recognizeRawRot(gray, bluePts, seeds, recHold)
                    "deskew" -> recognizeDeskewPump(gray, blueQuads, seeds, warpScratch, recHold)
                    else -> throw IllegalStateException("unknown side $which")
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.e(TAG, "rec $which $side: ${t.message}", t)
                onLog("REC_FAIL $photo $which le=$side ${t.message}")
                val err = t.message ?: "rec_fail"
                for (i in seeds.indices) {
                    val s = seeds[i]
                    if (s.text.isEmpty()) seeds[i] = s.copy(text = err)
                }
            } finally {
                try {
                    recHold.clearCrops()
                } catch (_: Throwable) {
                }
                try {
                    warpScratch.clearCrops()
                } catch (_: Throwable) {
                }
            }
        }
        val overlay = renderQuadOverlay(gray, seeds)
        return SideSeeds(
            seeds, det.capped, det.feed, det.contentW, det.contentH,
            det.heatW, det.heatH, srcW, srcH, overlay, null,
        )
    }

    private fun copyPrimary(src: BufferSet, dst: BufferSet) {
        dst.resize(src.width, src.height)
        dst.clearCrops()
        src.p.mat.copyTo(dst.p.mat)
        val su = src.p.uvMat
        val du = dst.p.uvMat
        if (!su.empty() && !du.empty() &&
            su.cols() == du.cols() && su.rows() == du.rows() && su.type() == du.type()
        ) {
            su.copyTo(du)
        } else {
            dst.p.clearChroma()
        }
    }

    private suspend fun ingestPhotoToMaster(
        context: Context,
        ref: PhotoRef,
        master: BufferSet,
        ingestCap: Int,
        onLog: (String) -> Unit,
    ): Boolean {
        val (imgW, imgH) = try {
            ImageIngestionProvider.probeDimensions(context, ref.file.absolutePath)
        } catch (t: Throwable) {
            onLog("probe fail ${ref.displayName}: ${t.message}")
            return false
        }
        if (imgW <= 0 || imgH <= 0) return false
        val decode = BufferSet(imgW, imgH)
        try {
            try {
                ImageIngestionProvider.ingestFromFile(context, ref.file.absolutePath, decode.p)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                onLog("ingest fail ${ref.displayName}: ${t.message}")
                return false
            }
            val srcW = decode.p.width
            val srcH = decode.p.height
            val srcLong = max(srcW, srcH)
            val cap = ingestCap.coerceAtMost(MAX_SIDE)
            val scaleFactor = if (srcLong <= cap) 1.0f else cap.toFloat() / srcLong
            var nw = (srcW * scaleFactor).roundToInt().coerceAtLeast(2)
            var nh = (srcH * scaleFactor).roundToInt().coerceAtLeast(2)
            if (nw % 2 != 0) nw -= 1
            if (nh % 2 != 0) nh -= 1
            master.resize(nw, nh)
            master.clearCrops()
            Imgproc.resize(
                decode.p.mat,
                master.p.mat,
                Size(nw.toDouble(), nh.toDouble()),
                0.0, 0.0, Imgproc.INTER_AREA,
            )
            val su = decode.p.uvMat
            val du = master.p.uvMat
            if (!su.empty() && !du.empty() && du.cols() > 0 && du.rows() > 0) {
                Imgproc.resize(
                    su, du,
                    Size(du.cols().toDouble(), du.rows().toDouble()),
                    0.0, 0.0, Imgproc.INTER_LINEAR,
                )
            } else {
                master.p.clearChroma()
            }
            onLog("INGEST ${ref.displayName} ${srcW}x$srcH → ${nw}x$nh")
            return true
        } finally {
            decode.release()
        }
    }

    private fun quadJson(q: IntArray): JSONArray {
        val a = JSONArray()
        for (v in q) a.put(v)
        return a
    }

    private fun seedJson(s: SeedRec): JSONObject = JSONObject()
        .put("text", s.text)
        .put("probs", s.probs)
        .put("u_span", s.uSpan.toDouble())
        .put("v_span", s.vSpan.toDouble())
        .put("aabb", JSONArray(listOf(s.aabb.left, s.aabb.top, s.aabb.right, s.aabb.bottom)))
        .put("seed_corners", quadJson(s.seed))
        .put("blue_corners", quadJson(s.blue))

    private fun sideJson(side: SideSeeds): JSONObject {
        val seeds = JSONArray()
        for (s in side.seeds) seeds.put(seedJson(s))
        return JSONObject()
            .put("seed_count", side.seeds.size)
            .put("capped", side.capped)
            .put("feed_side", side.feed)
            .put("feed_w", side.feed)
            .put("feed_h", side.feed)
            .put("content_w", side.contentW)
            .put("content_h", side.contentH)
            .put("heat_w", side.heatW)
            .put("heat_h", side.heatH)
            .put("image_w", side.imageW)
            .put("image_h", side.imageH)
            .put("error", side.error ?: JSONObject.NULL)
            .put("seeds", seeds)
    }

    private fun paddleJson(id: Int, photo: PhotoRef, longEdge: Int, raw: SideSeeds, deskew: SideSeeds): String {
        val status = if (raw.error == null && deskew.error == null) "ok" else "error"
        return JSONObject()
            .put("id", id)
            .put("id_tag", idTag(id))
            .put("kind", "paddle")
            .put("photo", photo.displayName)
            .put("domain", photo.domain)
            .put("model", MODEL_NAME)
            .put("long_edge", longEdge)
            .put("status", status)
            .put("box_mode", "minAreaRect")
            .put("grow", 0)
            .put("dilate", 0)
            .put("max_boxes", NativeImageUtils.HEATMAP_MAX_BOXES_DEFAULT)
            .put("rot_g_vert", ROT_G_VERT.toDouble())
            .put("rot_g_left", SET_G_UV_LEFT_FRAC.toDouble())
            .put("rot_g_right", SET_G_UV_RIGHT_FRAC.toDouble())
            .put("raw", sideJson(raw))
            .put("deskew", sideJson(deskew))
            .toString()
    }

    /** Opens the next report column inside one collapser cell body. */
    private fun htmlColumnSeam(rowspan: Int, dataCol: Int): String {
        val span = if (rowspan > 1) " rowspan='$rowspan'" else ""
        return "</td><td$span data-col='$dataCol'>"
    }

    private fun sideHtml(side: SideSeeds): String {
        val sb = StringBuilder()
        if (side.error != null) {
            sb.append("<span class='stat' style='color:#f66'>").append(esc(side.error)).append("</span><br>")
        }
        if (side.overlayB64.isNotEmpty()) {
            sb.append("<img src='data:image/jpeg;base64,").append(side.overlayB64).append("' loading='lazy'/>")
        }
        sb.append("<div class='stat'>")
        for (s in side.seeds) {
            sb.append(esc(s.text)).append("<br>")
        }
        sb.append("</div>")
        return sb.toString()
    }

    private fun paddleHtml(raw: SideSeeds, deskew: SideSeeds): String =
        sideHtml(raw) + htmlColumnSeam(1, 3) + sideHtml(deskew)

    /** Red seed quad and blue rec quad on a preview of [gray]. */
    private fun renderQuadOverlay(gray: Mat, seeds: List<SeedRec>): String {
        if (gray.empty()) return ""
        val fullW = gray.cols()
        val fullH = gray.rows()
        if (fullW < 1 || fullH < 1) return ""
        val scale = if (fullW > PREVIEW_MAX_W) PREVIEW_MAX_W.toFloat() / fullW else 1f
        val pw = max(1, (fullW * scale).roundToInt())
        val ph = max(1, (fullH * scale).roundToInt())
        val small = Mat()
        val rgba = Mat()
        try {
            Imgproc.resize(gray, small, Size(pw.toDouble(), ph.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.cvtColor(small, rgba, Imgproc.COLOR_GRAY2RGBA)
            fun draw(pts: IntArray, r: Double, g: Double, b: Double) {
                if (pts.size < 8) return
                for (i in 0 until 4) {
                    val j = (i + 1) % 4
                    val x1 = pts[i * 2] * scale
                    val y1 = pts[i * 2 + 1] * scale
                    val x2 = pts[j * 2] * scale
                    val y2 = pts[j * 2 + 1] * scale
                    Imgproc.line(
                        rgba,
                        Point(x1.toDouble(), y1.toDouble()),
                        Point(x2.toDouble(), y2.toDouble()),
                        Scalar(r, g, b, 255.0),
                        1,
                    )
                }
            }
            for (s in seeds) {
                draw(s.seed, 255.0, 0.0, 0.0)
                draw(s.blue, 0.0, 0.0, 255.0)
            }
            val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, bmp)
            val b64 = OcrUtils.bitmapToBase64(bmp, 72)
            bmp.recycle()
            return b64
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.w(TAG, "overlay: ${t.message}")
            return ""
        } finally {
            small.release()
            rgba.release()
        }
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun countsToJson(counts: Map<String, Int>): JSONObject {
        val o = JSONObject()
        for ((k, v) in counts) o.put(k, v)
        return o
    }

    private fun writeManifest(
        f: File,
        ts: String,
        arch: String,
        photos: List<PhotoRef>,
        counts: Map<String, Int>,
        nCells: Int,
    ) {
        val photosJa = JSONArray()
        photos.forEachIndexed { i, p ->
            photosJa.put(
                JSONObject()
                    .put("idx", i)
                    .put("domain", p.domain)
                    .put("file", p.displayName)
                    .put("native_w", p.nativeW)
                    .put("native_h", p.nativeH),
            )
        }
        f.writeText(
            JSONObject()
                .put("timestamp", ts)
                .put("arch", arch)
                .put("model", MODEL_NAME)
                .put("domain", "dash")
                .put("long_edges", JSONArray(OUTER_SCALES))
                .put("feed", "single letterbox at the long-edge; not prepareScale/deskewSetFor")
                .put("max_side", MAX_SIDE)
                .put("box_mode", "HEATMAP_BOX_MIN_AREA_RECT")
                .put("heat_u8_ge", 1)
                .put("grow", 0)
                .put("dilate", 0)
                .put("max_boxes", NativeImageUtils.HEATMAP_MAX_BOXES_DEFAULT)
                .put("rot_g_vert", ROT_G_VERT.toDouble())
                .put("rot_g_left", SET_G_UV_LEFT_FRAC.toDouble())
                .put("rot_g_right", SET_G_UV_RIGHT_FRAC.toDouble())
                .put("expand", "none")
                .put("seed_merge", "none")
                .put("n_photos", photos.size)
                .put("n_cells", nCells)
                .put("cells_per_photo", cellsPerPhoto())
                .put("counts", countsToJson(counts))
                .put("photos", photosJa)
                .put("collapse_period_ms", COLLAPSE_PERIOD_MS)
                .toString(2),
        )
    }

    private fun writeSparseHtml(
        f: File,
        ts: String,
        arch: String,
        photos: List<PhotoRef>,
        counts: Map<String, Int>,
        photoRange: IntRange,
        partIndex1: Int,
        nParts: Int,
        reportStem: String,
    ) {
        val nRows = OUTER_SCALES.size
        val colLabels = listOf(
            "photo", "long-edge", "paddle raw", "paddle deskew",
        )
        val metaHtml =
            "<b>Run</b> $ts · <b>Device</b> ${Build.MODEL} · " +
                "<b>Version</b> ${BuildConfig.VERSION_NAME} · <b>arch</b> $arch · " +
                "<b>dash photos</b> ${photos.size} (dash=${counts["dash"] ?: 0})"
        f.bufferedWriter().use { w ->
            w.append(
                ExperimentReportHtml.documentHead(
                    "Dash paddle long-edge det/rec $ts",
                    ExperimentReportHtml.Kind.MULTISCALE,
                ),
            )
            w.appendLine("<h1>Dash paddle long-edge det/rec</h1>")
            w.appendLine("<p class='meta'>$metaHtml</p>")
            w.appendLine(
                "<p class='meta dump-details'>Dash photos only. Long-edges $OUTER_SCALES, " +
                    "one product_det square feed each (not the 608 clamp). " +
                    "Columns: photo, long-edge, paddle raw, paddle deskew. " +
                    "Deskew angle from paddle at long-edge $DESKEW_LONG_EDGE (rotate −angle). " +
                    "Each paddle cell is that side’s red-seed / blue-quad overlay and blue-box strings. " +
                    "rot-G-- vert $ROT_G_VERT left $SET_G_UV_LEFT_FRAC " +
                    "right $SET_G_UV_RIGHT_FRAC. No expand, no seed merge. " +
                    "Widths, corners, and probabilities stay in the JSON.</p>",
            )
            w.append(
                ExperimentReportHtml.toolbar(
                    ExperimentReportHtml.Kind.MULTISCALE, colLabels, metaHtml,
                    partIndex1 = partIndex1, nParts = nParts, reportStem = reportStem,
                ),
            )
            w.append(ExperimentReportHtml.tableOpen(colLabels))
            try {
                for (pi in photoRange) {
                    val ref = photos[pi]
                    val photoNum = pi + 1
                    for (ei in OUTER_SCALES.indices) {
                        val le = OUTER_SCALES[ei]
                        val id = paddleId(pi, ei)
                        w.append("<tr data-photo='$photoNum'>")
                        if (ei == 0) {
                            w.append(
                                "<td rowspan='$nRows' data-col='0'><b>#$photoNum</b><br>" +
                                    "<small>${esc(ref.displayName)}</small></td>",
                            )
                        }
                        w.append("<th class='scale' data-col='1'>$le</th>")
                        w.append("<td data-col='2' id='c${idTag(id)}'>")
                        w.append(htmlBegin(id))
                        w.append("<span class='stat pending'>… ${idTag(id)}</span>")
                        w.append(htmlColumnSeam(1, 3))
                        w.append("<span class='stat pending'>… ${idTag(id)}</span>")
                        w.append(htmlEnd(id))
                        w.append("</td>")
                        w.appendLine("</tr>")
                    }
                }
            } finally {
                w.append(
                    ExperimentReportHtml.footer(
                        ExperimentReportHtml.Kind.MULTISCALE, colLabels, metaHtml,
                    ),
                )
            }
        }
    }

    private fun writeSparseResults(
        f: File,
        ts: String,
        arch: String,
        photos: List<PhotoRef>,
        counts: Map<String, Int>,
        nCells: Int,
    ) {
        f.bufferedWriter().use { w ->
            w.appendLine("# dash multi-scale sparse results — paddle per long-edge")
            w.appendLine(
                JSONObject()
                    .put("_meta", true)
                    .put("timestamp", ts)
                    .put("arch", arch)
                    .put("model", MODEL_NAME)
                    .put("long_edges", JSONArray(OUTER_SCALES))
                    .put("n_cells", nCells)
                    .put("n_photos", photos.size)
                    .put("counts", countsToJson(counts))
                    .toString(),
            )
            for (pi in photos.indices) {
                val ref = photos[pi]
                for (ei in OUTER_SCALES.indices) {
                    val id = paddleId(pi, ei)
                    val le = OUTER_SCALES[ei]
                    w.append(jsonBegin(id))
                    w.append(
                        JSONObject()
                            .put("id", id)
                            .put("kind", "paddle")
                            .put("status", "pending")
                            .put("photo", ref.displayName)
                            .put("domain", ref.domain)
                            .put("long_edge", le)
                            .put("model", MODEL_NAME)
                            .toString(),
                    )
                    w.append(jsonEnd(id))
                    w.append('\n')
                }
            }
        }
    }

    private fun writeStatus(
        f: File,
        ts: String,
        phase: String,
        done: Int,
        total: Int,
        cursor: Int,
        detail: String,
    ) {
        f.writeText(
            JSONObject()
                .put("timestamp", ts)
                .put("phase", phase)
                .put("cells_done", done)
                .put("cells_total", total)
                .put("collapse_cursor", cursor)
                .put("detail", detail)
                .put("updated_ms", System.currentTimeMillis())
                .toString(2),
        )
    }
}
