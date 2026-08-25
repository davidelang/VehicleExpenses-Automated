package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.ui.util.BufferSet
import com.davidlang.vehicleexpensesautomated.ui.util.ContentExpandUtils
import com.davidlang.vehicleexpensesautomated.ui.util.HEAT_THR_U8_GE1
import com.davidlang.vehicleexpensesautomated.ui.util.ImageIngestionProvider
import com.davidlang.vehicleexpensesautomated.ui.util.NativeImageUtils
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.util.PumpCostVolUtils
import com.davidlang.vehicleexpensesautomated.ui.util.PumpOcrSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * One-shot discover dump: prod × deskew/rot. No expand, no OCR, no pump_results.
 *
 * Writes `pump_reports/pump_det_boxes_<ts>/<safe_filename>.json` plus `manifest.json`.
 */
object PumpDetDiscoverDump {
    private const val TAG = "PumpDetDiscoverDump"
    private const val NATIVE_CAP = 200
    private val PROD_SCALES = listOf(224, 608)

    data class Result(
        val outDir: File,
        val nPhotos: Int,
        val message: String,
    )

    private data class CropBox(
        val ptsCrop: FloatArray,
        val conf: Float,
        val aabb: Rect,
    )

    private data class PhotoBox(
        val pts: FloatArray,
        val conf: Float,
        val rect: Rect,
    )

    suspend fun run(
        context: Context,
        photoDir: File,
        reportDir: File,
        onLog: (String) -> Unit = {},
        onProgress: (done: Int, total: Int, name: String) -> Unit = { _, _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val photos = photoDir.listFiles { f ->
            f.isFile && f.extension.lowercase() in listOf("jpg", "jpeg", "png", "dng")
        }?.sortedBy { it.name } ?: emptyList()
        if (photos.isEmpty()) {
            val msg = "Det dump: 0 photos in ${photoDir.absolutePath}"
            onLog(msg)
            throw IllegalStateException(msg)
        }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val outDir = File(reportDir, "pump_det_boxes_$timestamp")
        outDir.mkdirs()
        onLog("Det dump dir: ${outDir.absolutePath} n=${photos.size}")

        val prodDir = experimentPumpProductDir()
        onLog("loadProductionModels forceProdDir=$prodDir")
        NativePaddleEngine.loadProductionModels(context, forceProdDir = prodDir)
        val engine = NativePaddleEngine(context)
        val master = BufferSet(1, 1)
        val deskewWs = BufferSet(1, 1)
        val maxN = PumpOcrSettings.maxRedBoxes(context)
        var nOk = 0
        try {
            photos.forEachIndexed { index, file ->
                onProgress(index + 1, photos.size, file.name)
                onLog("Det dump ${index + 1}/${photos.size}: ${file.name}")
                try {
                    val (probedW, probedH) = ImageIngestionProvider.probeDimensions(
                        context, file.absolutePath,
                    )
                    if (probedW <= 0 || probedH <= 0) {
                        onLog("skip invalid probe ${file.name}")
                        return@forEachIndexed
                    }
                    master.resize(probedW, probedH)
                    ImageIngestionProvider.ingestFromFile(context, file.absolutePath, master.p)
                    copyPrimary(master, deskewWs)
                    val deskewRes = OdometerOcrUtils.calculateDeskewAnglePaddleOnly(
                        deskewWs.p, longEdgeTarget = 256,
                    )
                    val tilt = -deskewRes.paddleCppAngle
                    OdometerOcrUtils.rotate(deskewWs, tilt)

                    val prodDeskew = discoverRecipe(
                        engine, deskewWs, PROD_SCALES, rotPath = false, maxN,
                    )
                    val prodRot = discoverRecipe(
                        engine, master, PROD_SCALES, rotPath = true, maxN,
                    )

                    val photo = JSONObject()
                        .put("file", file.name)
                        .put("img_w", master.width)
                        .put("img_h", master.height)
                        .put(
                            "recipes",
                            JSONObject()
                                .put("prod-deskew", recipeJson("product_det", PROD_SCALES, tilt, prodDeskew))
                                .put("prod-rot", recipeJson("product_det", PROD_SCALES, 0f, prodRot)),
                        )
                    val safe = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    File(outDir, "$safe.json").writeText(photo.toString())
                    nOk++
                } catch (t: Throwable) {
                    Log.e(TAG, "det dump failed for ${file.name}", t)
                    onLog("fail ${file.name}: ${t.message}")
                }
            }
        } finally {
            master.release()
            deskewWs.release()
        }

        val manifest = JSONObject()
            .put("timestamp", timestamp)
            .put("device", Build.MODEL)
            .put("n_photos", nOk)
            .put("n_listed", photos.size)
            .put(
                "recipes",
                JSONArray(listOf("prod-deskew", "prod-rot")),
            )
        manifest.put("version", BuildConfig.VERSION_NAME)
        manifest.put("product_path", NativePaddleEngine.activeProductPathId)
        manifest.put("product_dir", NativePaddleEngine.activeProductDir)
        File(outDir, "manifest.json").writeText(manifest.toString(2))
        val msg = "Det dump wrote $nOk/${photos.size} → ${outDir.name}"
        onLog(msg)
        Result(outDir, nOk, msg)
    }

    private data class RecipeBoxes(
        val byScale: Map<Int, Pair<List<PhotoBox>, List<PhotoBox>>>,
        val union: List<PhotoBox>,
        val pruned: List<PhotoBox>,
    )

    private fun discoverRecipe(
        engine: NativePaddleEngine,
        workspace: BufferSet,
        scales: List<Int>,
        rotPath: Boolean,
        maxN: Int,
    ): RecipeBoxes {
        val imgW = workspace.p.width
        val imgH = workspace.p.height
        val byScale = LinkedHashMap<Int, Pair<List<PhotoBox>, List<PhotoBox>>>()
        val unionRot = ArrayList<PhotoBox>()
        val unionDeskew = ArrayList<PhotoBox>()
        scales.forEach { scale ->
            val (native, denest) = detectOneScale(engine, workspace, scale, rotPath)
            byScale[scale] = native to denest
            if (rotPath) unionRot.addAll(native) else unionDeskew.addAll(denest)
        }
        if (rotPath) {
            val quads = unionRot.map { ContentExpandUtils.orientedFromPoints8(it.pts) }
            val kept = ContentExpandUtils.pruneOrientedQuads(quads, maxN, imgH)
            val pruned = kept.map { q ->
                val r = q.toAabb()
                PhotoBox(q.copyPts(), 0f, r)
            }
            return RecipeBoxes(byScale, unionRot, pruned)
        }
        val unionRects = unionDeskew.map {
            Rect(it.rect.left, it.rect.top, it.rect.right, it.rect.bottom)
        }.toMutableList()
        PumpCostVolUtils.doCrossScaleRedboxFilterPixel(unionRects)
        val union = unionRects.map { rectToBox(it) }
        val prunedRects = unionRects.map {
            Rect(it.left, it.top, it.right, it.bottom)
        }.toMutableList()
        PumpCostVolUtils.pruneRectsToTopN(prunedRects, maxN, imgH)
        val pruned = prunedRects.map { rectToBox(it) }
        return RecipeBoxes(byScale, union, pruned)
    }

    private fun detectOneScale(
        engine: NativePaddleEngine,
        workspace: BufferSet,
        scale: Int,
        rotPath: Boolean,
    ): Pair<List<PhotoBox>, List<PhotoBox>> {
        val srcW = workspace.p.width
        val srcH = workspace.p.height
        val currentLongEdge = max(srcW, srcH)
        val scaleFactor =
            if (currentLongEdge <= scale) 1.0f else scale.toFloat() / currentLongEdge
        val targetW = (srcW * scaleFactor).toInt().coerceAtLeast(2)
        val targetH = (srcH * scaleFactor).toInt().coerceAtLeast(2)
        val (outerId, innerId) = PumpCostVolUtils.prepareScale(workspace, scale)
        try {
            val outer = workspace.c[outerId]
            val masterW = outer.width.coerceAtLeast(1)
            val masterH = outer.height.coerceAtLeast(1)
            val fullW = workspace.p.width
            val fullH = workspace.p.height
            val det = engine.detect(
                outer,
                copyHeatmap = false,
                boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                hmThresh = HEAT_THR_U8_GE1,
                maskDilatePasses = 0,
            )
            val cropBoxes = ArrayList<CropBox>()
            det?.nativeBoxes.orEmpty().take(NATIVE_CAP).forEach { box ->
                val p = box.points
                if (p.size < 8) return@forEach
                val minX = minOf(p[0], p[2], p[4], p[6]).toInt()
                val minY = minOf(p[1], p[3], p[5], p[7]).toInt()
                val maxX = maxOf(p[0], p[2], p[4], p[6]).toInt()
                val maxY = maxOf(p[1], p[3], p[5], p[7]).toInt()
                cropBoxes.add(
                    CropBox(
                        ptsCrop = p.copyOf(8),
                        conf = box.confidence,
                        aabb = Rect(minX, minY, maxX, maxY),
                    ),
                )
            }
            val sx: Float
            val sy: Float
            if (rotPath) {
                sx = fullW.toFloat() / masterW
                sy = fullH.toFloat() / masterH
            } else {
                sx = fullW.toFloat() / targetW
                sy = fullH.toFloat() / targetH
            }
            fun toPhoto(c: CropBox): PhotoBox {
                val pts = FloatArray(8)
                for (i in 0 until 4) {
                    pts[i * 2] = c.ptsCrop[i * 2] * sx
                    pts[i * 2 + 1] = c.ptsCrop[i * 2 + 1] * sy
                }
                val r = Rect(
                    (c.aabb.left * sx).toInt(),
                    (c.aabb.top * sy).toInt(),
                    (c.aabb.right * sx).toInt().coerceAtLeast((c.aabb.left * sx).toInt() + 1),
                    (c.aabb.bottom * sy).toInt().coerceAtLeast((c.aabb.top * sy).toInt() + 1),
                )
                return PhotoBox(pts, c.conf, r)
            }
            val native = cropBoxes.map { toPhoto(it) }
            val denest = if (rotPath) {
                native
            } else {
                val rects = cropBoxes.map { it.aabb }
                cropBoxes.filter { r1 ->
                    rects.none { r2 ->
                        r1.aabb !== r2 &&
                            r2.contains(
                                r1.aabb.left + 5, r1.aabb.top + 5,
                                r1.aabb.right - 5, r1.aabb.bottom - 5,
                            )
                    }
                }.map { toPhoto(it) }
            }
            return native to denest
        } finally {
            workspace.c[innerId].release()
            workspace.c[outerId].release()
        }
    }

    private fun recipeJson(
        detModel: String,
        scales: List<Int>,
        tiltDeg: Float,
        boxes: RecipeBoxes,
    ): JSONObject {
        val scaleBoxes = JSONObject()
        scales.forEach { s ->
            val pair = boxes.byScale[s]
            val native = pair?.first.orEmpty()
            val denest = pair?.second.orEmpty()
            scaleBoxes.put(
                s.toString(),
                JSONObject()
                    .put("native", boxArr(native))
                    .put("denest", boxArr(denest)),
            )
        }
        return JSONObject()
            .put("det_model", detModel)
            .put("scales", JSONArray(scales))
            .put("tilt_deg", tiltDeg.toDouble())
            .put("box_mode", "minAreaRect")
            .put("scale_boxes", scaleBoxes)
            .put("union", boxArr(boxes.union))
            .put("pruned", boxArr(boxes.pruned))
    }

    private fun boxArr(list: List<PhotoBox>): JSONArray {
        val a = JSONArray()
        list.forEach { a.put(boxJson(it)) }
        return a
    }

    private fun boxJson(b: PhotoBox): JSONObject {
        val pts = JSONArray()
        b.pts.forEach { pts.put(it.toDouble()) }
        return JSONObject()
            .put("pts", pts)
            .put("conf", b.conf.toDouble())
            .put("l", b.rect.left)
            .put("t", b.rect.top)
            .put("r", b.rect.right)
            .put("b", b.rect.bottom)
    }

    private fun rectToBox(r: Rect): PhotoBox {
        val pts = floatArrayOf(
            r.left.toFloat(), r.top.toFloat(),
            r.right.toFloat(), r.top.toFloat(),
            r.right.toFloat(), r.bottom.toFloat(),
            r.left.toFloat(), r.bottom.toFloat(),
        )
        return PhotoBox(pts, 0f, r)
    }

    private fun copyPrimary(src: BufferSet, dst: BufferSet) {
        dst.resize(src.width, src.height)
        src.p.mat.copyTo(dst.p.mat)
        if (!src.p.uvMat.empty() && !dst.p.uvMat.empty() &&
            src.p.uvMat.size() == dst.p.uvMat.size()
        ) {
            src.p.uvMat.copyTo(dst.p.uvMat)
        }
    }

    private fun experimentPumpProductDir(): String {
        val isEmu =
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
                Build.FINGERPRINT.contains("sdk_", ignoreCase = true) ||
                Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
                Build.PRODUCT.contains("sdk", ignoreCase = true) ||
                Build.MODEL.contains("sdk", ignoreCase = true) ||
                Build.SUPPORTED_ABIS.any { it.startsWith("x86") }
        return if (isEmu) "prod_u8fp32_u8" else "prod_u8fp16"
    }
}
