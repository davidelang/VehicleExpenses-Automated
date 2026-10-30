package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.davidlang.vehicleexpensesautomated.ui.ocr.*
import java.io.File
import java.util.Arrays
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * production det landmark detect at long-edge 2048, plus rot-G-- recognition on the gray.
 * The Lite feed is this runner's square letterbox at 2048.
 */
object MultiScaleDetRunner {
    private const val TAG = "MultiScaleDet"

    /** Existing processHeatmap connected-component area floor. */
    private const val HEAT_MIN_AREA = 10f

    private const val MODEL_NAME = "det"

    /** Alignment paddle columns. Not a sweep row and not a second scale. */
    private const val ALIGN_DET_LONG_EDGE = 2048

    private var alignDetPred: MonoDetPredictor? = null
    private var alignDetHeat: Mat? = null
    private var alignDetRec: BufferSet? = null

    /**
     * production det at long-edge 2048 plus rot-G-- recognition on [gray].
     * Same letterbox and rot-G side fractions as the multi-scale runner (left 0.54, right 0.55).
     * [rotGVert] defaults to [SET_G_ROT_VERT_FACTORS.first()] (0.34); alignment passes 0.40f.
     * No second scale and no seed merge. Returned blocks are in [gray]'s pixels.
     */
    fun productDetLandmarks2048(
        context: Context,
        gray: Mat,
        rotGVert: Float = SET_G_ROT_VERT_FACTORS.first(),
        debug: ((detJson: String, recJson: String) -> Unit)? = null,
    ): List<TextBlock> {
        if (gray.empty()) return emptyList()
        return try {
            productDetLandmarks2048Once(context, gray, rotGVert, debug)
        // kept: failure stays on this call so the screen or caller can continue
        } catch (t: CancellationException) {
            throw t
        }
    }

    private fun alignDetSession(context: Context): Pair<MonoDetPredictor, BufferSet> {
        val pred = alignDetPred
        val rec = alignDetRec
        if (pred != null && rec != null) return pred to rec
        val side = ALIGN_DET_LONG_EDGE
        val nb = NativePaddleEngine.productionDetFile(context)
        val heat = Mat(side, side, CvType.CV_8UC1)
        alignDetHeat = heat
        val created = MonoDetPredictor(nb, mapOf(side to ByteArray(side * side)), heat)
        alignDetPred = created
        val recH = RecBufferFeed.DEFAULT_REC_H
        val recBuf = BufferSet(2, recH)
        alignDetRec = recBuf
        Log.i(TAG, "production det 2048 loaded ${nb.name}")
        return created to recBuf
    }


    private fun productDetLandmarks2048Once(
        context: Context,
        gray: Mat,
        rotGVert: Float,
        debug: ((detJson: String, recJson: String) -> Unit)?,
    ): List<TextBlock> {
        val side = ALIGN_DET_LONG_EDGE
        val (pred, rec) = alignDetSession(context)
        NativePaddleEngine.heartbeat("odo_det")
        val srcW = gray.cols()
        val srcH = gray.rows()
        val det = pred.detectQuads(gray, side)
        if (det == null || det.corners.isEmpty()) {
            emitLandmarkDebug(
                debug, det, side, srcW, srcH, emptyList(), emptyList(), null, emptyList(),
            )
            return emptyList()
        }
        val n = det.corners.size
        val bluePts = FloatArray(n * 8)
        val aabbs = ArrayList<Rect>(n)
        val seeds = ArrayList<IntArray>(n)
        val blues = if (debug != null) ArrayList<IntArray>(n) else null
        NativePaddleEngine.heartbeat("odo_expand")
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
            blues?.add(blueI)
        }
        try {
            rec.clearCrops()
            val raw = recognizeRotGmmQuads(
                gray.nativeObj,
                bluePts,
                rec,
                srcW,
                srcH,
                landmarkAlphabet = true,
            )
            val out = ArrayList<TextBlock>(n)
            val texts = if (debug != null) ArrayList<String>(n) else null
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
                texts?.add(text)
            }
            Log.i(TAG, "production det 2048 seeds=$n kept=${out.size} ${srcW}x$srcH")
            emitLandmarkDebug(
                debug, det, side, srcW, srcH, seeds, blues ?: emptyList(), raw, texts ?: emptyList(),
            )
            return out
        } finally {
            rec.clearCrops()
        }
    }

    /** det.json and rec.json for one production det 2048 call. No-op when [debug] is null. */
    private fun emitLandmarkDebug(
        debug: ((detJson: String, recJson: String) -> Unit)?,
        det: QuadDet?,
        feedFallback: Int,
        srcW: Int,
        srcH: Int,
        seeds: List<IntArray>,
        blues: List<IntArray>,
        raw: OcrRotGmmOut?,
        texts: List<String>,
    ) {
        if (debug == null) return
        fun ints(q: IntArray): JSONArray {
            val a = JSONArray()
            for (v in q) a.put(v)
            return a
        }
        val detJson = JSONObject()
        detJson.put("feed", det?.feed ?: feedFallback)
        detJson.put("heatW", det?.heatW ?: 0)
        detJson.put("heatH", det?.heatH ?: 0)
        detJson.put("contentW", det?.contentW ?: 0)
        detJson.put("contentH", det?.contentH ?: 0)
        detJson.put("capped", det?.capped ?: false)
        detJson.put("srcW", srcW)
        detJson.put("srcH", srcH)
        val hist = JSONArray()
        det?.hist?.forEach { hist.put(it) }
        detJson.put("hist", hist)
        val corners = det?.corners ?: emptyList()
        val scores = det?.scores ?: emptyList()
        val boxes = JSONArray()
        for (i in corners.indices) {
            val box = JSONObject()
            val c = JSONArray()
            for (v in corners[i]) c.put(v.toDouble())
            box.put("corners", c)
            box.put("score", if (i < scores.size) scores[i].toDouble() else 0.0)
            boxes.put(box)
        }
        detJson.put("boxes", boxes)
        val seedArr = JSONArray()
        for (q in seeds) seedArr.put(ints(q))
        val blueArr = JSONArray()
        for (q in blues) blueArr.put(ints(q))
        detJson.put("seeds", seedArr)
        detJson.put("blues", blueArr)
        val recArr = JSONArray()
        if (raw != null) {
            for (i in seeds.indices) {
                val row = JSONObject()
                row.put("seed", ints(seeds[i]))
                row.put("blue", ints(if (i < blues.size) blues[i] else IntArray(0)))
                row.put("recW", raw.recW.getOrElse(i) { 0 })
                row.put("recH", raw.recH.getOrElse(i) { 0 })
                row.put("asis", raw.asis.getOrElse(i) { "" })
                row.put("asisProbs", raw.asisProbs.getOrElse(i) { "" })
                row.put("digits", raw.digits.getOrElse(i) { "" })
                row.put("digitsProbs", raw.digitsProbs.getOrElse(i) { "" })
                row.put("text", texts.getOrElse(i) { "" })
                recArr.put(row)
            }
        }
        debug.invoke(detJson.toString(), recArr.toString())
    }

    private data class QuadDet(
        val corners: List<FloatArray>,
        val heatW: Int,
        val heatH: Int,
        val feed: Int,
        val contentW: Int,
        val contentH: Int,
        val capped: Boolean,
        val scores: List<Float>,
        val hist: List<Int>,
    )


    /**
     * One production det graph. Reloads when the square feed side changes.
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
            if (gray.empty()) throw IllegalStateException("detectQuads: empty gray")
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
            if (!input.setData(buf)) throw IllegalStateException("detectQuads: setData")
            p.run()
            val output = p.getOutput(0)
            val dims = output.shape()
            val heatH = dims[2].toInt()
            val heatW = dims[3].toInt()
            if (heatW.toLong() * heatH.toLong() > heatScratch.total()) {
                throw IllegalStateException("heat ${heatW}x$heatH exceeds scratch ${heatScratch.total()}")
            }
            val nativeRes = NativeImageUtils.processHeatmap(
                tensor = output,
                threshold = HEAT_THR_U8_GE1,
                minArea = HEAT_MIN_AREA,
                boxMode = NativeImageUtils.HEATMAP_BOX_MIN_AREA_RECT,
                maskDilatePasses = 0,
                maxBoxes = 0,
                growCells = 0,
                scratchY = heatScratch,
            )
            val boxFloats = nativeRes.size - 100
            if (boxFloats < 0 || boxFloats % 9 != 0) {
                throw IllegalStateException("detectQuads: box layout $boxFloats")
            }
            val n = boxFloats / 9
            val corners = ArrayList<FloatArray>(n)
            val scores = ArrayList<Float>(n)
            for (i in 0 until n) {
                val o = i * 9
                corners.add(
                    floatArrayOf(
                        nativeRes[o], nativeRes[o + 1], nativeRes[o + 2], nativeRes[o + 3],
                        nativeRes[o + 4], nativeRes[o + 5], nativeRes[o + 6], nativeRes[o + 7],
                    ),
                )
                scores.add(nativeRes[o + 8])
            }
            val hist = List(100) { i -> nativeRes[boxFloats + i].toInt() }
            return QuadDet(
                corners = corners,
                heatW = heatW,
                heatH = heatH,
                feed = side,
                contentW = contentW,
                contentH = contentH,
                capped = false,
                scores = scores,
                hist = hist,
            )
        }
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
}
