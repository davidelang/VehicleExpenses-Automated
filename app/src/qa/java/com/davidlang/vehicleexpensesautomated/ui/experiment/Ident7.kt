package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.util.Log
import com.davidlang.vehicleexpensesautomated.ui.util.PathResult
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Port of ident7.py (md5 3decbb97), the run_ident.py polarity/band merge, and the
 * sim_fallback columns. I7_OFF defaults to "one" (narrow-1 cell off). I7_MEDCAP = 0.
 * Fallback T = 0.037 and the printed flag. Deskew and rot stay separate.
 */
object Ident7 {
    private const val TAG = "Ident7"
    private const val EXP = 0.15
    private const val HB = 64.0
    private const val T_FALLBACK = 0.037
    private const val IDENT_VERSION = "3decbb97"
    private const val INK_H = 1.0e9
    private const val SEG_NAMES = "abcdefg"

    private val SEG_ON = arrayOf(
        "abcdef", "bc", "abdeg", "abcdg", "bcfg",
        "acdfg", "acdefg", "abc", "abcdefg", "abcdfg",
    )
    private val SEG_VARS = Array(10) { d ->
        val v = ArrayList<String>(2)
        v.add(SEG_ON[d])
        when (d) {
            6 -> v.add("cdefg")
            7 -> v.add("abcf")
            9 -> v.add("abcfg")
        }
        v
    }

    private class LineHit(
        val pol: String,
        val asis: String,
        val quality: Double,
        val printed: Boolean,
        val not7: Boolean,
        val hFull: Double,
        val cy: Double,
    )

    private class Run(val a: Int, val b: Int)

    private class Bag {
        private val ms = ArrayList<Mat>()
        fun <T : Mat> T.k(): T {
            ms.add(this)
            return this
        }
        fun close() {
            for (i in ms.indices.reversed()) ms[i].release()
            ms.clear()
        }
    }

    fun attach(root: PumpBranch, ink: PumpBranch, src: Mat, frame: String): List<PumpBranch> {
        val cv = parseCv(ink)
        val rects = cv?.optJSONArray("ocrSourceRects") ?: JSONArray()
        val inkCands = cv?.optJSONArray("candidates") ?: JSONArray()
        val chosen = cv?.optJSONObject("chosen")
        val perBox = ArrayList<List<LineHit>>(rects.length())
        val gray = toGray(src)
        try {
            for (i in 0 until rects.length()) {
                val r = rects.optJSONObject(i)
                val lines = if (gray == null || r == null) {
                    emptyList()
                } else {
                    try {
                        identifyBox(gray, r.optDouble("l"), r.optDouble("t"), r.optDouble("r"), r.optDouble("b"))
                    } catch (e: Exception) {
                        Log.e(TAG, "box $i $frame", e)
                        emptyList()
                    }
                }
                perBox.add(lines)
            }
        } finally {
            gray?.release()
        }
        return emit(root, frame, rects, inkCands, chosen, perBox)
    }

    fun attachEmpty(root: PumpBranch, ink: PumpBranch, frame: String): List<PumpBranch> {
        val cv = parseCv(ink)
        val rects = cv?.optJSONArray("ocrSourceRects") ?: JSONArray()
        val inkCands = cv?.optJSONArray("candidates") ?: JSONArray()
        val chosen = cv?.optJSONObject("chosen")
        val perBox = ArrayList<List<LineHit>>(rects.length())
        repeat(rects.length()) { perBox.add(emptyList()) }
        return emit(root, frame, rects, inkCands, chosen, perBox)
    }

    private fun parseCv(ink: PumpBranch): JSONObject? {
        val raw = ink.metadata["costVolDecisionData_Paddle"] ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun emit(
        root: PumpBranch,
        frame: String,
        rects: JSONArray,
        inkCands: JSONArray,
        chosen: JSONObject?,
        perBox: List<List<LineHit>>,
    ): List<PumpBranch> {
        // ident-only column dropped from the experiment report; keep ident+inkFB only.
        // Each candidate carries src=ident|ink; chosen + assembly echo which path fired.
        val fb = fill(
            root.getBranch("Set ident+inkFB $frame"),
            JSONArray(rects.toString()),
            inkCands,
            chosen,
            perBox,
            fallback = true,
        )
        return listOf(fb)
    }

    private fun fill(
        br: PumpBranch,
        rects: JSONArray,
        inkCands: JSONArray,
        inkChosen: JSONObject?,
        perBox: List<List<LineHit>>,
        fallback: Boolean,
    ): PumpBranch {
        val cands = JSONArray()
        val owned = ArrayList<JSONObject>()
        val boxOf = ArrayList<Int>()
        val heightOf = ArrayList<Double>()
        var nFb = 0
        for (i in 0 until rects.length()) {
            val rect = rects.optJSONObject(i) ?: JSONObject()
            val lines = perBox.getOrElse(i) { emptyList() }
            val use = if (!fallback) lines else lines.filter { it.quality >= T_FALLBACK && !it.printed }
            if (fallback && use.isEmpty()) {
                nFb++
                val ic = inkCands.optJSONObject(i)
                val o = JSONObject()
                    .put("label", "box${i + 1}.ink")
                    .put("asis", ic?.optString("asis") ?: "")
                    .put("digits", ic?.optString("digits") ?: "")
                    .put("rect", JSONObject(rect.toString()))
                    .put("src", "ink")
                    .put("printed", false)
                    .put("not7seg", false)
                    .put("H", INK_H)
                cands.put(o)
                owned.add(o)
                boxOf.add(i)
                heightOf.add(INK_H)
                continue
            }
            use.forEachIndexed { k, line ->
                val q = if (line.quality.isFinite()) line.quality else 0.0
                val h = if (line.hFull.isFinite()) line.hFull else 0.0
                val o = JSONObject()
                    .put("label", "box${i + 1}.line${k + 1}")
                    .put("asis", line.asis)
                    .put("digits", line.asis.replace(".", ""))
                    .put("rect", JSONObject(rect.toString()))
                    .put("src", "ident")
                    .put("quality", q)
                    .put("printed", line.printed)
                    .put("not7seg", line.not7)
                    .put("pol", line.pol)
                    .put("H", h)
                cands.put(o)
                owned.add(o)
                boxOf.add(i)
                heightOf.add(h)
            }
        }
        fun pick(field: String): Pair<String, JSONObject?> {
            val ch = inkChosen?.optJSONObject(field) ?: return "N/A" to null
            val rect = ch.optJSONObject("rect") ?: return "N/A" to null
            val b = indexOfRect(rects, rect)
            if (b < 0) return "N/A" to null
            var best = -1
            var bestH = Double.NEGATIVE_INFINITY
            for (j in owned.indices) {
                if (boxOf[j] != b) continue
                if (heightOf[j] > bestH) {
                    bestH = heightOf[j]
                    best = j
                }
            }
            if (best < 0) return "N/A" to null
            val asis = owned[best].optString("asis")
            val text = if (asis.isBlank()) "N/A" else asis
            return text to JSONObject(owned[best].toString())
        }
        val (cost, costO) = pick("cost")
        val (vol, volO) = pick("vol")
        val chosenOut = JSONObject()
        chosenOut.put("cost", costO ?: JSONObject.NULL)
        chosenOut.put("vol", volO ?: JSONObject.NULL)
        // Top-level path flags so scorers need not dig into chosen.*.src
        chosenOut.put("costSrc", costO?.optString("src") ?: JSONObject.NULL)
        chosenOut.put("volSrc", volO?.optString("src") ?: JSONObject.NULL)
        val assembly = JSONObject()
            .put("method", "ident7 B")
            .put("rule", if (fallback) "q>=0.037 && !printed else ink" else "ident-only")
            .put("nFallbackBoxes", if (fallback) nFb else 0)
            .put("identVersion", IDENT_VERSION)
            .put("costPath", costO?.optString("src") ?: "none")
            .put("volPath", volO?.optString("src") ?: "none")
            .put("usedInkFallback", nFb > 0 ||
                costO?.optString("src") == "ink" || volO?.optString("src") == "ink")
        val doc = JSONObject()
            .put("candidates", cands)
            .put("chosen", chosenOut)
            .put("final", JSONObject().put("cost", cost).put("vol", vol))
            .put("ocrSourceRects", rects)
            .put("scaleVariants", JSONArray())
            .put("assembly", assembly)
        br.metadata["costVolDecisionData_Paddle"] = doc.toString()
        br.metadata["ident_cost_src"] = costO?.optString("src") ?: "none"
        br.metadata["ident_vol_src"] = volO?.optString("src") ?: "none"
        br.metadata["ident_n_ink_fallback_boxes"] = nFb.toString()
        br.pathResults["Paddle"] = PathResult(cost, vol, "", "")
        br.discoveryDetails = JSONObject()
        return br
    }

    private fun indexOfRect(rects: JSONArray, rect: JSONObject): Int {
        val l = rect.optInt("l")
        val t = rect.optInt("t")
        val r = rect.optInt("r")
        val b = rect.optInt("b")
        for (i in 0 until rects.length()) {
            val o = rects.optJSONObject(i) ?: continue
            if (o.optInt("l") == l && o.optInt("t") == t && o.optInt("r") == r && o.optInt("b") == b) return i
        }
        return -1
    }

    private fun toGray(src: Mat): Mat? {
        if (src.empty()) return null
        return when (src.type()) {
            CvType.CV_8UC1 -> {
                val g = Mat()
                src.convertTo(g, CvType.CV_32FC1)
                g
            }
            CvType.CV_32FC1 -> src.clone()
            CvType.CV_8UC3, CvType.CV_8UC4 -> {
                val ch = ArrayList<Mat>(4)
                Core.split(src, ch)
                val b = Mat()
                val gch = Mat()
                val r = Mat()
                val g = Mat()
                try {
                    ch[0].convertTo(b, CvType.CV_32FC1)
                    ch[1].convertTo(gch, CvType.CV_32FC1)
                    ch[2].convertTo(r, CvType.CV_32FC1)
                    Core.addWeighted(b, 0.114, gch, 0.587, 0.0, g)
                    Core.addWeighted(g, 1.0, r, 0.299, 0.0, g)
                    g
                } finally {
                    b.release()
                    gch.release()
                    r.release()
                    ch.forEach { it.release() }
                }
            }
            else -> {
                Log.w(TAG, "unsupported mat type ${src.type()}")
                null
            }
        }
    }

    private fun identifyBox(g: Mat, x0: Double, y0: Double, x1: Double, y1: Double): List<LineHit> {
        val raw = ArrayList<LineHit>()
        for (pol in arrayOf("dark", "light")) {
            val s1 = stage1(g, x0, y0, x1, y1, pol == "dark") ?: continue
            for (band in s1.bands) {
                stage2(g, s1, band, pol)?.let { raw.add(it) }
            }
        }
        val groups = ArrayList<MutableList<LineHit>>()
        for (hit in raw) {
            val grp = groups.firstOrNull { g0 ->
                abs(g0[0].cy - hit.cy) < 0.5 * min(g0[0].hFull, hit.hFull)
            }
            if (grp != null) grp.add(hit) else groups.add(mutableListOf(hit))
        }
        return groups.map { grp ->
            var best = grp[0]
            for (i in 1 until grp.size) if (grp[i].quality > best.quality) best = grp[i]
            best
        }
    }

    private class Stage1(
        val a: DoubleArray,
        val bands: List<Run>,
        val sw: Int,
        val sh: Int,
        val f: Double,
    )

    private fun stage1(g: Mat, x0: Double, y0: Double, x1: Double, y1: Double, dark: Boolean): Stage1? {
        val w = x1 - x0
        val h = y1 - y0
        if (w < 1.0 || h < 1.0) return null
        var ex0 = max(0.0, x0 - EXP * w)
        var ey0 = max(0.0, y0 - EXP * h)
        var ex1 = min(g.cols().toDouble(), x1 + EXP * w)
        var ey1 = min(g.rows().toDouble(), y1 + EXP * h)
        val ww = ex1 - ex0
        val hh = ey1 - ey0
        if (ww < 8.0 || hh < 8.0) return null
        val f = min(300.0 / hh, 1500.0 / ww)
        val sw = (ww * f).toInt()
        val sh = (hh * f).toInt()
        if (sw < 4 || sh < 4) return null
        val a1 = doubleArrayOf(f, 0.0, -f * ex0, 0.0, f, -f * ey0)
        val bag = Bag()
        try {
            val crop = warp(g, a1, sw, sh).kBag(bag)
            val ink = inkMap(crop, 0.2 * sh, dark).kBag(bag)
            val bin = Mat().kBag(bag)
            Imgproc.threshold(ink, bin, 0.5, 1.0, Imgproc.THRESH_BINARY)
            val bin8 = Mat().kBag(bag)
            bin.convertTo(bin8, CvType.CV_8UC1)
            val mask = keepComponents(bin8, 12.0, 0.95 * sh, 0.6 * sw).kBag(bag)
            if (Core.countNonZero(mask) < 30) return Stage1(a1, emptyList(), sw, sh, f)
            val mf = Mat().kBag(bag)
            mask.convertTo(mf, CvType.CV_32FC1)
            val half = Mat().kBag(bag)
            Imgproc.resize(mf, half, Size(), 0.5, 0.5, Imgproc.INTER_AREA)
            var bestV = Double.NEGATIVE_INFINITY
            var ang = 0.0
            for (a in arange(-12.0, 12.01, 0.5)) {
                val v = rotVar(half, sw / 4.0, sh / 4.0, a, half.cols(), half.rows())
                if (v > bestV) {
                    bestV = v
                    ang = a
                }
            }
            bestV = Double.NEGATIVE_INFINITY
            var ang2 = ang
            for (a in arange(ang - 0.4, ang + 0.41, 0.2)) {
                val v = rotVar(mf, sw / 2.0, sh / 2.0, a, sw, sh)
                if (v > bestV) {
                    bestV = v
                    ang2 = a
                }
            }
            ang = ang2
            val rot = Imgproc.getRotationMatrix2D(Point(sw / 2.0, sh / 2.0), ang, 1.0).kBag(bag)
            val r23 = read23(rot)
            val rotated = Mat().kBag(bag)
            Imgproc.warpAffine(mf, rotated, rot, Size(sw.toDouble(), sh.toDouble()))
            val prof = gauss5(rowMeans(rotated))
            val bands = ArrayList<Run>()
            val mx = prof.maxOrNull() ?: 0.0
            if (mx > 0.0) {
                val minLen = max(8, (0.06 * sh).toInt())
                for (run in runsOf(BooleanArray(prof.size) { prof[it] > 0.12 * mx })) {
                    if (run.b - run.a >= minLen) bands.add(run)
                }
            }
            val composed = mul33(to33(r23), to33(a1))
            return Stage1(composed.copyOf(6), bands, sw, sh, f)
        } finally {
            bag.close()
        }
    }

    private fun stage2(g: Mat, s1: Stage1, band: Run, pol: String): LineHit? {
        val r0 = band.a
        val r1 = band.b
        val hb1 = r1 - r0
        if (hb1 < 1) return null
        val s = HB / hb1
        val mg = 0.45
        val ext = 1.4 * HB
        val bw = (s1.sw * s + 2.0 * ext).toInt()
        val bh = (HB * (1.0 + 2.0 * mg)).toInt()
        if (bw < 20 || bw > 7000 || bh < 4) return null
        val left = doubleArrayOf(s, 0.0, ext, 0.0, s, -s * (r0 - mg * hb1))
        val a2 = mul33(to33(left), to33(s1.a)).copyOf(6)
        val hx0 = ext
        val hx1 = ext + s1.sw * s
        val bag = Bag()
        try {
            val crop = warp(g, a2, bw, bh).kBag(bag)
            val ink = inkMap(crop, 0.5 * HB, pol == "dark").kBag(bag)
            val bin = Mat().kBag(bag)
            Imgproc.threshold(ink, bin, 0.5, 1.0, Imgproc.THRESH_BINARY)
            val bin8 = Mat().kBag(bag)
            bin.convertTo(bin8, CvType.CV_8UC1)
            val cleaned = deJunk(bin8).kBag(bag)
            val kept = keepComponents(cleaned, 0.004 * HB * HB, 1.4 * HB, Double.POSITIVE_INFINITY).kBag(bag)
            val mf = Mat().kBag(bag)
            kept.convertTo(mf, CvType.CV_32FC1)
            val prof = gauss5(rowMeans(mf))
            val mx = prof.maxOrNull() ?: return null
            if (mx <= 0.0) return null
            val rr = runsOf(BooleanArray(prof.size) { prof[it] > 0.2 * mx })
            if (rr.isEmpty()) return null
            var bestRun = rr[0]
            var bestSum = Double.NEGATIVE_INFINITY
            for (run in rr) {
                var sm = 0.0
                for (i in run.a until run.b) sm += prof[i]
                if (sm > bestSum) {
                    bestSum = sm
                    bestRun = run
                }
            }
            var y0 = bestRun.a
            var y1 = bestRun.b
            while (y0 > 0 && prof[y0 - 1] > 0.12 * mx) y0--
            while (y1 < bh && y1 < prof.size && prof[y1] > 0.12 * mx) y1++
            val hs = y1 - y0
            val hsD = hs.toDouble()
            if (hsD < 0.4 * HB) return null
            val ym = (y0 + y1) / 2.0
            var bestSlV = Double.NEGATIVE_INFINITY
            var sl = -0.12
            for (slTry in arange(-0.12, 0.36, 0.01)) {
                val shM = mat23(doubleArrayOf(1.0, slTry, -slTry * ym, 0.0, 1.0, 0.0)).kBag(bag)
                val warped = Mat().kBag(bag)
                Imgproc.warpAffine(mf, warped, shM, Size(bw.toDouble(), bh.toDouble()))
                val v = colMeanVar(warped, y0, y1)
                if (v > bestSlV) {
                    bestSlV = v
                    sl = slTry
                }
            }
            val shear = mat23(doubleArrayOf(1.0, sl, -sl * ym, 0.0, 1.0, 0.0)).kBag(bag)
            val dMat = Mat().kBag(bag)
            val iMat = Mat().kBag(bag)
            Imgproc.warpAffine(mf, dMat, shear, Size(bw.toDouble(), bh.toDouble()))
            Imgproc.warpAffine(ink, iMat, shear, Size(bw.toDouble(), bh.toDouble()))
            val d = cropRows(dMat, y0, y1)
            val di = cropRows(iMat, y0, y1)
            if (d.size != hs * bw || di.size != hs * bw) return null
            return decodeBand(d, di, bw, hs, hsD, s, s1.f, a2, y0, ym, sl, hx0, hx1, pol)
        } finally {
            bag.close()
        }
    }

    private fun decodeBand(
        d: FloatArray,
        di: FloatArray,
        bw: Int,
        hs: Int,
        hsD: Double,
        s: Double,
        fScale: Double,
        a2: DoubleArray,
        y0: Int,
        ym: Double,
        sl: Double,
        hx0: Double,
        hx1: Double,
        pol: String,
    ): LineHit? {
        val bin = Mat(hs, bw, CvType.CV_8UC1)
        val pix = ByteArray(d.size) { i -> if (d[i] > 0.5f) 1 else 0 }
        bin.put(0, 0, pix)
        val labels = Mat()
        val stats = Mat()
        val cents = Mat()
        val nD = Imgproc.connectedComponentsWithStats(bin, labels, stats, cents, 8)
        val lab = IntArray(d.size)
        if (d.isNotEmpty()) labels.get(0, 0, lab)
        val dpc = ArrayList<Double>()
        val dc = d.copyOf()
        for (k in 1 until nD) {
            val x = stats.get(k, Imgproc.CC_STAT_LEFT)[0]
            val y = stats.get(k, Imgproc.CC_STAT_TOP)[0]
            val ww = stats.get(k, Imgproc.CC_STAT_WIDTH)[0]
            val hh = stats.get(k, Imgproc.CC_STAT_HEIGHT)[0]
            val area = stats.get(k, Imgproc.CC_STAT_AREA)[0]
            if (0.04 * hsD < ww && ww < 0.26 * hsD && 0.04 * hsD < hh && hh < 0.26 * hsD &&
                max(ww, hh) < 1.8 * min(ww, hh) && area > 0.45 * ww * hh &&
                y + hh > 0.8 * hsD && y > 0.55 * hsD
            ) {
                dpc.add(x + ww / 2.0)
                for (i in lab.indices) if (lab[i] == k) dc[i] = 0f
            }
        }
        labels.release()
        stats.release()
        cents.release()
        bin.release()
        val cprof = DoubleArray(bw)
        for (x in 0 until bw) {
            var sm = 0.0
            for (y in 0 until hs) sm += dc[y * bw + x]
            cprof[x] = if (hs > 0) sm / hs else 0.0
        }
        val rawRuns = runsOf(BooleanArray(bw) { cprof[it] > 0.02 }).filter { it.b - it.a >= 2 }
        val merged = ArrayList<Run>()
        for (run in rawRuns) {
            if (merged.isNotEmpty() && run.a - merged.last().b < 0.06 * hsD) {
                val prev = merged.removeAt(merged.lastIndex)
                merged.add(Run(prev.a, run.b))
            } else {
                merged.add(run)
            }
        }
        fun spans(run: Run): Boolean {
            var ymin = Int.MAX_VALUE
            var ymax = Int.MIN_VALUE
            var any = false
            for (y in 0 until hs) {
                var sm = 0.0
                val row = y * bw
                for (x in run.a until run.b) sm += dc[row + x]
                if (sm > 0.0) {
                    any = true
                    if (y < ymin) ymin = y
                    if (y > ymax) ymax = y
                }
            }
            return any && ymax - ymin > 0.45 * hsD
        }
        val infoAll = merged.filter { spans(it) }
        var info = infoAll.filter { it.b > hx0 - 0.3 * hsD && it.a < hx1 + 0.3 * hsD }
        if (info.isEmpty()) info = infoAll
        if (info.isEmpty()) return null
        val wide = info.map { (it.b - it.a).toDouble() }.filter { it > 0.3 * hsD }
        var wd = if (wide.isEmpty()) 0.5 * hsD else median(wide.toDoubleArray())
        wd = min(max(wd, 0.3 * hsD), 0.8 * hsD)
        val narrow = info.filter { it.b - it.a < 1.6 * wd }
        val nar = if (narrow.isEmpty()) info else narrow
        val edges = DoubleArray(nar.size) { nar[it].b.toDouble() }
        val lt = lattice2(edges, hsD, wd, dpc)
        if (!lt.p.isFinite() || lt.p < 1e-3) return null
        val ext = HashSet<Double>()
        fun slotOk(x1: Double, strict: Boolean): Boolean {
            val a = max(0.0, x1 - wd).toInt()
            val b = min(bw.toDouble(), x1 + 0.03 * hsD).toInt()
            if (b - a <= 2) return false
            var sum = 0.0
            var maxCol = 0.0
            for (x in a until b) {
                if (x < 0 || x >= bw) continue
                var col = 0.0
                for (y in 0 until hs) col += dc[y * bw + x]
                sum += col
                if (col > maxCol) maxCol = col
            }
            if (sum <= 0.045 * hsD * hsD) return false
            return !strict || maxCol > 0.6 * hsD
        }
        fun side(ph: Double, runs: List<Run>): ArrayList<Double> {
            val xs = ArrayList<Double>()
            if (runs.isEmpty() || lt.p == 0.0) return xs
            var lo = runs[0].a.toDouble()
            var hi = runs[0].b.toDouble()
            for (run in runs) {
                if (run.a < lo) lo = run.a.toDouble()
                if (run.b > hi) hi = run.b.toDouble()
            }
            val m0 = floor((lo + 0.5 * wd - ph) / lt.p).toInt()
            val m1 = ceil((hi - ph) / lt.p).toInt()
            for (m in (m0 - 1)..(m1 + 1)) {
                val x1 = ph + m * lt.p
                val inside = m in m0..m1 && hx0 - 0.2 * wd < x1 && x1 - wd < hx1 + 0.2 * wd
                if (slotOk(x1, !inside)) {
                    xs.add(x1)
                    if (!inside) ext.add(round3(x1))
                }
            }
            return xs
        }
        val cells = ArrayList<Double>()
        if (lt.brk && lt.xb != null) {
            val xb = lt.xb
            val left = nar.filter { it.b < xb }
            val right = nar.filter { it.b > xb }
            for (x in side(lt.phL, left)) if (x < xb + 0.25 * lt.p) cells.add(x)
            for (x in side(lt.phR, right)) if (x - wd > xb - 0.25 * lt.p) cells.add(x)
        } else {
            val lat = ArrayList<Run>()
            for (i in nar.indices) if (i < lt.inl.size && lt.inl[i]) lat.add(nar[i])
            cells.addAll(side(lt.phL, if (lat.isEmpty()) nar else lat))
        }
        cells.sort()
        val ded = ArrayList<Double>()
        for (x in cells) if (ded.isEmpty() || x - ded.last() > 0.6 * lt.p) ded.add(x)
        if (ded.isEmpty() || ded.size > 9) return null
        val ws = ArrayList<Double>()
        val rowIdx = ArrayList<Int>()
        for (yy in (0.22 * hsD).toInt() until (0.36 * hsD).toInt()) if (yy in 0 until hs) rowIdx.add(yy)
        for (yy in (0.64 * hsD).toInt() until (0.78 * hsD).toInt()) if (yy in 0 until hs) rowIdx.add(yy)
        for (yy in rowIdx) {
            val mask = BooleanArray(bw) { d[yy * bw + it] > 0.5f }
            for (run in runsOf(mask)) {
                val len = run.b - run.a
                if (len > 1 && len < 0.35 * hsD) ws.add(len.toDouble())
            }
        }
        var tStroke = if (ws.isEmpty()) 0.13 * hsD else median(ws.toDoubleArray())
        tStroke = min(max(tStroke, 0.07 * hsD), 0.25 * hsD)
        val t = tStroke
        fun segVals(x0s: Double, x1s: Double): DoubleArray {
            val width = x1s - x0s
            val h2 = hsD / 2.0
            val lpad = 0.17
            val hxA = x0s + t + lpad * (width - 2 * t)
            val hxB = x1s - t - lpad * (width - 2 * t)
            fun vb(xa: Double, xb: Double, ya: Double, yb: Double): Double {
                return boxMean(di, bw, hs, xa, xb, ya + lpad * (yb - ya), yb - lpad * (yb - ya))
            }
            val v = DoubleArray(7)
            v[0] = boxMean(di, bw, hs, hxA, hxB, 0.0, t)
            v[1] = vb(x1s - t, x1s, t, h2 - t / 2.0)
            v[2] = vb(x1s - t, x1s, h2 + t / 2.0, hsD - t)
            v[3] = boxMean(di, bw, hs, hxA, hxB, hsD - t, hsD)
            v[4] = vb(x0s, x0s + t, h2 + t / 2.0, hsD - t)
            v[5] = vb(x0s, x0s + t, t, h2 - t / 2.0)
            v[6] = boxMean(di, bw, hs, hxA, hxB, h2 - t / 2.0, h2 + t / 2.0)
            return v
        }
        val sc = ArrayList<DoubleArray>(ded.size)
        val xs = ArrayList<Double>(ded.size)
        val step = max(1.0, 0.02 * wd)
        for (x1 in ded) {
            var bestJ = Double.NEGATIVE_INFINITY
            var bestV = DoubleArray(7)
            var bestX = x1
            for (dx in arange(-0.1 * wd, 0.1 * wd + 0.01, step)) {
                val v = segVals(x1 + dx - wd, x1 + dx)
                var j = 0.0
                for (u in v) j += abs(2.0 * u - 1.0)
                if (j > bestJ) {
                    bestJ = j
                    bestV = v
                    bestX = x1 + dx
                }
            }
            sc.add(bestV)
            xs.add(bestX)
        }
        val allv = ArrayList<Double>()
        for (v in sc) for (u in v) allv.add(u)
        val thr = otsu1(allv.toDoubleArray())
        val on = ArrayList<Double>()
        val off = ArrayList<Double>()
        for (u in allv) if (u > thr) on.add(u) else off.add(u)
        val spread = if (on.isNotEmpty() && off.isNotEmpty()) {
            median(on.toDoubleArray()) - median(off.toDoubleArray())
        } else {
            0.0
        }
        val tau = max(0.06, 0.22 * spread)
        val lls = ArrayList<DoubleArray>(sc.size)
        val bestCh = ArrayList<Int>(sc.size)
        val bestLl = ArrayList<Double>(sc.size)
        val secondLl = ArrayList<Double>(sc.size)
        for (v in sc) {
            val pr = DoubleArray(7) { k ->
                val z = (v[k] - thr) / tau
                val e = exp(-z)
                if (e.isInfinite()) 0.0 else 1.0 / (1.0 + e)
            }
            val ll = digitLls(pr)
            lls.add(ll)
            val order = (0..9).sortedWith(compareByDescending<Int> { ll[it] }.thenBy { it })
            bestCh.add(order[0])
            bestLl.add(ll[order[0]])
            secondLl.add(ll[order[1]])
        }
        val keep = BooleanArray(xs.size) { true }
        if (xs.size > 1) {
            val inner = ArrayList<Double>()
            for (i in xs.indices) if (round3(xs[i]) !in ext) inner.add(bestLl[i])
            val base = if (inner.isEmpty()) bestLl else inner
            var meanInner = 0.0
            for (v in base) meanInner += v
            meanInner /= base.size.coerceAtLeast(1)
            for (i in xs.indices) {
                if (round3(xs[i]) in ext && bestLl[i] < meanInner - 1.5) keep[i] = false
            }
        }
        val xsK = ArrayList<Double>()
        val chK = ArrayList<Int>()
        val llK = ArrayList<Double>()
        val ll2K = ArrayList<Double>()
        val scK = ArrayList<DoubleArray>()
        for (i in xs.indices) if (keep[i]) {
            xsK.add(xs[i])
            chK.add(bestCh[i])
            llK.add(bestLl[i])
            ll2K.add(secondLl[i])
            scK.add(sc[i])
        }
        if (xsK.isEmpty()) return null
        var dp: Int? = null
        for (xc in dpc) {
            for (j in 0 until xsK.size - 1) {
                if (xsK[j] - 0.1 * wd < xc && xc < xsK[j + 1] - 0.85 * wd) dp = j
            }
        }
        val dips = ArrayList<Double>()
        for (i in xsK.indices) {
            val x = xsK[i]
            val seg = scK[i]
            val pairs = arrayOf(
                intArrayOf(5, 4) to doubleArrayOf(x - wd, x - wd + t),
                intArrayOf(1, 2) to doubleArrayOf(x - t, x),
            )
            for ((ix, span) in pairs) {
                if (seg[ix[0]] > thr && seg[ix[1]] > thr) {
                    val a = max(0.0, span[0]).toInt()
                    val b = min(bw.toDouble(), span[1] + 1.0).toInt()
                    if (b - a < 1) continue
                    val profC = DoubleArray(hs)
                    for (y in 0 until hs) {
                        var sm = 0.0
                        var n = 0
                        for (xx in a until b) if (xx in 0 until bw) {
                            sm += di[y * bw + xx]
                            n++
                        }
                        profC[y] = if (n > 0) sm / n else 0.0
                    }
                    val ya = (0.3 * hsD).toInt()
                    val yb = (0.7 * hsD).toInt()
                    val midA = (0.42 * hsD).toInt()
                    val midB = (0.58 * hsD).toInt() + 1
                    if (midA >= midB || ya >= yb) continue
                    val mid = DoubleArray((midB - midA).coerceAtLeast(0)) { k ->
                        val yy = midA + k
                        if (yy in profC.indices) profC[yy] else 0.0
                    }
                    val bandP = DoubleArray((yb - ya).coerceAtLeast(0)) { k ->
                        val yy = ya + k
                        if (yy in profC.indices) profC[yy] else 0.0
                    }
                    if (mid.isNotEmpty() && bandP.isNotEmpty() && median(bandP) > 0.0) {
                        dips.add(mid.min() / max(1e-3, percentile75(bandP)))
                    }
                }
            }
        }
        val printed = dips.size >= 2 && median(dips.toDoubleArray()) > 0.85
        val holeEach = DoubleArray(xsK.size)
        for (i in xsK.indices) {
            val x = xsK[i]
            val hbxs = holeBoxes(x - wd, x, hsD, t)
            holeEach[i] = (boxMean(di, bw, hs, hbxs[0][0], hbxs[0][1], hbxs[0][2], hbxs[0][3]) +
                boxMean(di, bw, hs, hbxs[1][0], hbxs[1][1], hbxs[1][2], hbxs[1][3])) / 2.0
        }
        val hole = median(holeEach)
        var meanBest = 0.0
        var margin = 0.0
        for (i in llK.indices) {
            meanBest += llK[i]
            margin += llK[i] - ll2K[i]
        }
        meanBest /= llK.size
        margin /= llK.size
        val meanll = meanBest / 7.0
        val latFrac = lt.inlFrac
        val reasons = ArrayList<String>()
        if (hole > 0.3) reasons.add("hole")
        if (nar.size >= 3 && latFrac < 0.6) reasons.add("pitch")
        if (spread < 0.25) reasons.add("spread")
        if (meanll < -0.45) reasons.add("fit")
        if (xsK.size < 2) reasons.add("single")
        val chars = CharArray(chK.size) { ('0'.code + chK[it]).toChar() }
        val asis = fmt(String(chars), dp)
        val ti = inv33(to33(a2))
        var cySum = 0.0
        for (x in xsK) {
            val corners = arrayOf(
                doubleArrayOf(x - wd, 0.0),
                doubleArrayOf(x, 0.0),
                doubleArrayOf(x, hsD),
                doubleArrayOf(x - wd, hsD),
            )
            var ys = 0.0
            for (c in corners) {
                val yy = c[1] + y0
                val xx = c[0] - sl * (yy - ym)
                ys += apply33(ti, xx, yy)[1]
            }
            cySum += ys / 4.0
        }
        val cy = cySum / xsK.size
        val hFull = hsD / s / fScale
        val quality = meanll + 0.3 * spread - 0.5 * hole + 0.02 * min(margin, 10.0)
        return LineHit(pol, asis, quality, printed, reasons.isNotEmpty(), hFull, cy)
    }

    private fun holeBoxes(x0: Double, x1: Double, hs: Double, t: Double): Array<DoubleArray> {
        val width = x1 - x0
        val h2 = hs / 2.0
        val m = 0.25
        val xA = x0 + t + m * (width - 2 * t)
        val xB = x1 - t - m * (width - 2 * t)
        val topH = h2 - 1.5 * t
        return arrayOf(
            doubleArrayOf(xA, xB, t + m * topH, h2 - t / 2.0 - m * topH),
            doubleArrayOf(xA, xB, h2 + t / 2.0 + m * topH, hs - t - m * topH),
        )
    }

    private fun digitLls(pr: DoubleArray): DoubleArray {
        val ll = DoubleArray(10)
        for (d in 0..9) {
            var best = Double.NEGATIVE_INFINITY
            for (segs in SEG_VARS[d]) {
                var s = 0.0
                for (k in 0 until 7) {
                    val on = segs.indexOf(SEG_NAMES[k]) >= 0
                    val p = if (on) pr[k] else 1.0 - pr[k]
                    s += ln(max(1e-4, p))
                }
                if (s > best) best = s
            }
            ll[d] = best
        }
        return ll
    }

    private fun fmt(s: String, dp: Int?): String {
        if (dp == null || dp >= s.length - 1) return s
        return s.substring(0, dp + 1) + "." + s.substring(dp + 1)
    }

    private class Lat(val p: Double, val ph: Double, val inl: BooleanArray)
    private class Lat2(
        val p: Double,
        val phL: Double,
        val phR: Double,
        val xb: Double?,
        val inlFrac: Double,
        val inl: BooleanArray,
        val brk: Boolean,
    )

    private fun lattice(edgesIn: DoubleArray, hs: Double, wd: Double): Lat {
        val edges = edgesIn.copyOf()
        Arrays.sort(edges)
        val n = edges.size
        if (n < 2) {
            val ph = if (n == 1) edges[0] else 0.0
            return Lat(0.7 * hs, ph, BooleanArray(n) { true })
        }
        var bestN = -1
        var bestNeg = 0.0
        var bestP = 0.7 * hs
        var bestPh = edges[0]
        var bestInl = BooleanArray(n)
        var p = max(0.45 * hs, 1.1 * wd)
        val pStop = 1.3 * hs
        val step = 0.01 * hs
        while (p < pStop) {
            for (ph in edges) {
                var nIn = 0
                var resSum = 0.0
                val inl = BooleanArray(n)
                for (i in 0 until n) {
                    val m = Math.rint((edges[i] - ph) / p)
                    val res = abs(edges[i] - ph - m * p)
                    if (res < 0.1 * p) {
                        inl[i] = true
                        nIn++
                        resSum += res
                    }
                }
                val neg = if (nIn > 0) -(resSum / nIn) / p else Double.NEGATIVE_INFINITY
                if (nIn > bestN || (nIn == bestN && neg > bestNeg)) {
                    bestN = nIn
                    bestNeg = neg
                    bestP = p
                    bestPh = ph
                    bestInl = inl
                }
            }
            p += step
        }
        var pOut = bestP
        var phOut = bestPh
        val ms = ArrayList<Double>()
        val rs = ArrayList<Double>()
        val seen = HashSet<Long>()
        for (i in edges.indices) {
            if (!bestInl[i]) continue
            val m = Math.rint((edges[i] - bestPh) / bestP)
            ms.add(m)
            rs.add(edges[i])
            seen.add(m.toLong())
        }
        if (ms.size >= 2 && seen.size >= 2) {
            var sumM = 0.0
            var sumM2 = 0.0
            var sumR = 0.0
            var sumMR = 0.0
            for (i in ms.indices) {
                val m = ms[i]
                val r = rs[i]
                sumM += m
                sumM2 += m * m
                sumR += r
                sumMR += m * r
            }
            val det = sumM2 * ms.size - sumM * sumM
            if (abs(det) > 1e-8) {
                pOut = (sumMR * ms.size - sumM * sumR) / det
                phOut = (sumM2 * sumR - sumM * sumMR) / det
            }
        }
        return Lat(pOut, phOut, bestInl)
    }

    private fun lattice2(edgesIn: DoubleArray, hs: Double, wd: Double, dpx: List<Double>): Lat2 {
        val edges = edgesIn.copyOf()
        Arrays.sort(edges)
        val base = lattice(edges, hs, wd)
        var bestSc = -1
        var bestDp = false
        var bestP = base.p
        var bestPhL = base.ph
        var bestPhR = base.ph
        var bestK = 1
        if (edges.size >= 3) {
            val pStop = 1.3 * hs
            val step = 0.01 * hs
            for (k in 1 until edges.size) {
                val gl = edges[k - 1]
                val gr = edges[k]
                var dpIn = false
                for (x in dpx) if (gl - 0.2 * wd < x && x < gr) dpIn = true
                var p = max(0.45 * hs, 1.1 * wd)
                while (p < pStop) {
                    var sc = 0
                    val phs = DoubleArray(2)
                    val parts = arrayOf(edges.copyOfRange(0, k), edges.copyOfRange(k, edges.size))
                    for (pi in 0..1) {
                        val part = parts[pi]
                        var bpN = -1
                        var bpNeg = 0.0
                        var bpPh = part[0]
                        for (ph in part) {
                            var nIn = 0
                            var resSum = 0.0
                            for (v in part) {
                                val m = Math.rint((v - ph) / p)
                                val res = abs(v - ph - m * p)
                                resSum += res
                                if (res < 0.1 * p) nIn++
                            }
                            val neg = -resSum / part.size
                            if (nIn > bpN || (nIn == bpN && neg > bpNeg)) {
                                bpN = nIn
                                bpNeg = neg
                                bpPh = ph
                            }
                        }
                        sc += bpN
                        phs[pi] = bpPh
                    }
                    var dph = ((phs[1] - phs[0]) / p) % 1.0
                    if (dph < 0) dph += 1.0
                    if (dph > 0.08 && dph < 0.75) {
                        if (sc > bestSc || (sc == bestSc && dpIn && !bestDp)) {
                            bestSc = sc
                            bestDp = dpIn
                            bestP = p
                            bestPhL = phs[0]
                            bestPhR = phs[1]
                            bestK = k
                        }
                    }
                    p += step
                }
            }
        }
        val baseN = base.inl.count { it }
        if (bestDp && baseN < edges.size && bestSc > baseN) {
            val xb = (edges[bestK - 1] + edges[bestK]) / 2.0
            return Lat2(bestP, bestPhL, bestPhR, xb, bestSc.toDouble() / edges.size, base.inl, true)
        }
        val frac = if (edges.isEmpty()) 0.0 else baseN.toDouble() / edges.size
        return Lat2(base.p, base.ph, base.ph, null, frac, base.inl, false)
    }

    private fun Mat.kBag(bag: Bag): Mat = with(bag) { this@kBag.k() }

    private fun deJunk(m: Mat): Mat {
        val k1 = (0.3 * HB).toInt() or 1
        val e1 = structuring(k1, k1)
        val e2 = structuring((1.3 * HB).toInt(), 1)
        val e3 = structuring(1, (1.35 * HB).toInt())
        val e5 = structuring(5, 5)
        val j1 = Mat()
        val j2 = Mat()
        val j3 = Mat()
        Imgproc.morphologyEx(m, j1, Imgproc.MORPH_OPEN, e1)
        Imgproc.morphologyEx(m, j2, Imgproc.MORPH_OPEN, e2)
        Imgproc.morphologyEx(m, j3, Imgproc.MORPH_OPEN, e3)
        Core.bitwise_or(j1, j2, j1)
        Core.bitwise_or(j1, j3, j1)
        val dil = Mat()
        Imgproc.dilate(j1, dil, e5)
        val ones = Mat(dil.rows(), dil.cols(), CvType.CV_8UC1, Scalar(1.0))
        val inv = Mat()
        Core.subtract(ones, dil, inv)
        val out = Mat()
        Core.bitwise_and(m, inv, out)
        e1.release(); e2.release(); e3.release(); e5.release()
        j1.release(); j2.release(); j3.release()
        dil.release(); ones.release(); inv.release()
        return out
    }

    private fun structuring(a: Int, b: Int): Mat {
        val aa = max(1, a)
        val bb = max(1, b)
        val shape = if (aa == bb) Imgproc.MORPH_ELLIPSE else Imgproc.MORPH_RECT
        return Imgproc.getStructuringElement(shape, Size(aa.toDouble(), bb.toDouble()))
    }

    private fun keepComponents(bin8: Mat, minArea: Double, maxH: Double, maxW: Double): Mat {
        val h = bin8.rows()
        val w = bin8.cols()
        val out = Mat.zeros(h, w, CvType.CV_8UC1)
        if (w < 1 || h < 1) return out
        val labels = Mat()
        val stats = Mat()
        val cents = Mat()
        val n = Imgproc.connectedComponentsWithStats(bin8, labels, stats, cents, 8)
        val keep = BooleanArray(max(n, 0))
        for (k in 1 until n) {
            val area = stats.get(k, Imgproc.CC_STAT_AREA)[0]
            val hh = stats.get(k, Imgproc.CC_STAT_HEIGHT)[0]
            val ww = stats.get(k, Imgproc.CC_STAT_WIDTH)[0]
            keep[k] = area >= minArea && hh < maxH && ww < maxW
        }
        val lab = IntArray(w * h)
        labels.get(0, 0, lab)
        labels.release(); stats.release(); cents.release()
        val pix = ByteArray(w * h)
        for (i in pix.indices) {
            val id = lab[i]
            if (id in 1 until n && keep[id]) pix[i] = 1
        }
        out.put(0, 0, pix)
        return out
    }

    private fun inkMap(g: Mat, kIn: Double, dark: Boolean): Mat {
        val k = max(3, kIn.toInt()) or 1
        val ker = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(k.toDouble(), k.toDouble()))
        val bg = Mat()
        Imgproc.morphologyEx(g, bg, if (dark) Imgproc.MORPH_CLOSE else Imgproc.MORPH_OPEN, ker)
        ker.release()
        val d = Mat()
        if (dark) Core.subtract(bg, g, d) else Core.subtract(g, bg, d)
        bg.release()
        val kk = (3 * k) or 1
        val ker2 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(kk.toDouble(), kk.toDouble()))
        val loc = Mat()
        Imgproc.dilate(d, loc, ker2)
        ker2.release()
        val locC = Mat()
        Core.max(loc, Scalar(18.0), locC)
        loc.release()
        val ratio = Mat()
        Core.divide(d, locC, ratio)
        d.release()
        locC.release()
        val nonNeg = Mat()
        Core.max(ratio, Scalar(0.0), nonNeg)
        ratio.release()
        val clipped = Mat()
        Core.min(nonNeg, Scalar(1.0), clipped)
        nonNeg.release()
        return clipped
    }

    private fun warp(g: Mat, a: DoubleArray, w: Int, h: Int): Mat {
        val mean = if (g.empty()) 0.0 else Core.mean(g).`val`[0]
        fun blank() = Mat(max(h, 1), max(w, 1), CvType.CV_32FC1, Scalar(mean))
        if (w < 1 || h < 1) return blank()
        val big = mat23(a)
        val aiM = Mat()
        Imgproc.invertAffineTransform(big, aiM)
        val ai = read23(aiM)
        aiM.release()
        fun mapPt(x: Double, y: Double) = doubleArrayOf(
            ai[0] * x + ai[1] * y + ai[2],
            ai[3] * x + ai[4] * y + ai[5],
        )
        val pts = arrayOf(
            mapPt(0.0, 0.0),
            mapPt(w.toDouble(), 0.0),
            mapPt(0.0, h.toDouble()),
            mapPt(w.toDouble(), h.toDouble()),
        )
        var minx = pts[0][0]
        var maxx = minx
        var miny = pts[0][1]
        var maxy = miny
        for (p in pts) {
            if (p[0] < minx) minx = p[0]
            if (p[0] > maxx) maxx = p[0]
            if (p[1] < miny) miny = p[1]
            if (p[1] > maxy) maxy = p[1]
        }
        var x0 = floor(minx).toInt() - 2
        var y0 = floor(miny).toInt() - 2
        var x1 = ceil(maxx).toInt() + 2
        var y1 = ceil(maxy).toInt() + 2
        if (x0 < 0) x0 = 0
        if (y0 < 0) y0 = 0
        if (x1 > g.cols()) x1 = g.cols()
        if (y1 > g.rows()) y1 = g.rows()
        val det = a[0] * a[4] - a[1] * a[3]
        val sc = sqrt(abs(det))
        if (x1 - x0 < 4 || y1 - y0 < 4) {
            big.release()
            return blank()
        }
        val view = g.submat(y0, y1, x0, x1)
        val src: Mat
        val r: Double
        if (sc < 0.5) {
            r = min(1.0, 2.0 * sc)
            val resized = Mat()
            Imgproc.resize(view, resized, Size(), r, r, Imgproc.INTER_AREA)
            view.release()
            src = resized
        } else {
            r = 1.0
            src = view
        }
        val t = doubleArrayOf(
            1.0 / r, 0.0, x0.toDouble(),
            0.0, 1.0 / r, y0.toDouble(),
            0.0, 0.0, 1.0,
        )
        val b = mat23(mul23by33(a, t))
        val dst = Mat()
        Imgproc.warpAffine(
            src, dst, b, Size(w.toDouble(), h.toDouble()),
            Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE,
        )
        src.release()
        big.release()
        b.release()
        return dst
    }

    private fun rotVar(src: Mat, cx: Double, cy: Double, ang: Double, dw: Int, dh: Int): Double {
        if (dw < 1 || dh < 1) return 0.0
        val r = Imgproc.getRotationMatrix2D(Point(cx, cy), ang, 1.0)
        val d = Mat()
        Imgproc.warpAffine(src, d, r, Size(dw.toDouble(), dh.toDouble()))
        r.release()
        val v = rowMeanVar(d)
        d.release()
        return v
    }

    private fun floatData(m: Mat): FloatArray {
        val w = m.cols()
        val h = m.rows()
        val n = w * h
        if (n <= 0) return FloatArray(0)
        val src = if (m.isContinuous) m else m.clone()
        val buf = FloatArray(n)
        src.get(0, 0, buf)
        if (src !== m) src.release()
        return buf
    }

    private fun rowMeans(m: Mat): DoubleArray {
        val w = m.cols()
        val h = m.rows()
        if (w <= 0 || h <= 0) return DoubleArray(0)
        val buf = floatData(m)
        val out = DoubleArray(h)
        for (y in 0 until h) {
            var s = 0.0
            val row = y * w
            for (x in 0 until w) s += buf[row + x]
            out[y] = s / w
        }
        return out
    }

    private fun rowMeanVar(m: Mat): Double {
        val means = rowMeans(m)
        if (means.isEmpty()) return 0.0
        var s = 0.0
        var s2 = 0.0
        for (v in means) {
            s += v
            s2 += v * v
        }
        val mu = s / means.size
        return s2 / means.size - mu * mu
    }

    private fun colMeanVar(m: Mat, y0: Int, y1: Int): Double {
        val w = m.cols()
        val h = m.rows()
        if (w <= 0 || h <= 0) return 0.0
        val buf = floatData(m)
        val ya = y0.coerceIn(0, h)
        val yb = y1.coerceIn(ya, h)
        val n = (yb - ya).coerceAtLeast(1)
        var s = 0.0
        var s2 = 0.0
        for (x in 0 until w) {
            var acc = 0.0
            for (y in ya until yb) acc += buf[y * w + x]
            val mu = acc / n
            s += mu
            s2 += mu * mu
        }
        val mu = s / w
        return s2 / w - mu * mu
    }

    private fun cropRows(m: Mat, y0: Int, y1: Int): FloatArray {
        val w = m.cols()
        val h = m.rows()
        if (w <= 0 || h <= 0) return FloatArray(0)
        val ya = y0.coerceIn(0, h)
        val yb = y1.coerceIn(ya, h)
        val buf = floatData(m)
        val out = FloatArray((yb - ya) * w)
        if (out.isNotEmpty()) System.arraycopy(buf, ya * w, out, 0, out.size)
        return out
    }

    private fun gauss5(v: DoubleArray): DoubleArray {
        if (v.isEmpty()) return v
        val src = Mat(v.size, 1, CvType.CV_32FC1)
        src.put(0, 0, FloatArray(v.size) { v[it].toFloat() })
        val dst = Mat()
        Imgproc.GaussianBlur(src, dst, Size(1.0, 5.0), 0.0)
        val o = FloatArray(v.size)
        dst.get(0, 0, o)
        src.release()
        dst.release()
        return DoubleArray(v.size) { o[it].toDouble() }
    }

    private fun boxMean(img: FloatArray, w: Int, h: Int, xa0: Double, xb0: Double, ya0: Double, yb0: Double): Double {
        var xa = Math.rint(xa0).toInt()
        var xb = Math.rint(xb0).toInt()
        var ya = Math.rint(ya0).toInt()
        var yb = Math.rint(yb0).toInt()
        if (xa < 0) xa = 0
        if (ya < 0) ya = 0
        if (xb < xa + 1) xb = xa + 1
        if (yb < ya + 1) yb = ya + 1
        if (xb > w) xb = w
        if (yb > h) yb = h
        if (xb <= xa || yb <= ya) return 0.0
        var s = 0.0
        var n = 0
        for (y in ya until yb) {
            val row = y * w
            for (x in xa until xb) {
                s += img[row + x]
                n++
            }
        }
        return if (n == 0) 0.0 else s / n
    }

    private fun otsu1(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.5
        val v = values.copyOf()
        Arrays.sort(v)
        if (v.size == 1) return v[0]
        var bestC = 1e18
        var bestT = v.average()
        for (i in 1 until v.size) {
            val c = popVar(v, 0, i) * i + popVar(v, i, v.size) * (v.size - i)
            if (c < bestC) {
                bestC = c
                bestT = (v[i - 1] + v[i]) / 2.0
            }
        }
        return bestT
    }

    private fun popVar(v: DoubleArray, from: Int, to: Int): Double {
        val n = to - from
        if (n <= 0) return 0.0
        var s = 0.0
        for (i in from until to) s += v[i]
        val mu = s / n
        var s2 = 0.0
        for (i in from until to) {
            val d = v[i] - mu
            s2 += d * d
        }
        return s2 / n
    }

    private fun median(v: DoubleArray): Double {
        if (v.isEmpty()) return 0.0
        val s = v.copyOf()
        Arrays.sort(s)
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else 0.5 * (s[n / 2 - 1] + s[n / 2])
    }

    private fun percentile75(v: DoubleArray): Double {
        if (v.isEmpty()) return 0.0
        val s = v.copyOf()
        Arrays.sort(s)
        if (s.size == 1) return s[0]
        val idx = 0.75 * (s.size - 1)
        val lo = floor(idx).toInt()
        val hi = ceil(idx).toInt().coerceAtMost(s.size - 1)
        if (lo >= hi) return s[lo.coerceIn(0, s.size - 1)]
        val frac = idx - lo
        return s[lo] * (1.0 - frac) + s[hi] * frac
    }

    private fun round3(x: Double): Double = Math.rint(x * 1000.0) / 1000.0

    private fun arange(start: Double, stop: Double, step: Double): DoubleArray {
        if (step <= 0.0) return DoubleArray(0)
        val out = ArrayList<Double>()
        var i = 0
        while (i < 100000) {
            val v = start + i * step
            if (v >= stop) break
            out.add(v)
            i++
        }
        return out.toDoubleArray()
    }

    private fun runsOf(mask: BooleanArray): List<Run> {
        val r = ArrayList<Run>()
        var i = 0
        while (i < mask.size) {
            if (mask[i]) {
                var j = i
                while (j + 1 < mask.size && mask[j + 1]) j++
                r.add(Run(i, j + 1))
                i = j + 1
            } else {
                i++
            }
        }
        return r
    }

    private fun mat23(a: DoubleArray): Mat {
        val m = Mat(2, 3, CvType.CV_64FC1)
        m.put(0, 0, a[0], a[1], a[2])
        m.put(1, 0, a[3], a[4], a[5])
        return m
    }

    private fun read23(m: Mat): DoubleArray {
        val v = DoubleArray(6)
        m.get(0, 0, v)
        return v
    }

    private fun to33(a23: DoubleArray): DoubleArray = doubleArrayOf(
        a23[0], a23[1], a23[2],
        a23[3], a23[4], a23[5],
        0.0, 0.0, 1.0,
    )

    private fun mul33(a: DoubleArray, b: DoubleArray): DoubleArray {
        val o = DoubleArray(9)
        for (r in 0..2) {
            for (c in 0..2) {
                o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
            }
        }
        return o
    }

    private fun mul23by33(a: DoubleArray, t: DoubleArray): DoubleArray {
        val o = DoubleArray(6)
        for (r in 0..1) {
            for (c in 0..2) {
                o[r * 3 + c] = a[r * 3] * t[c] + a[r * 3 + 1] * t[3 + c] + a[r * 3 + 2] * t[6 + c]
            }
        }
        return o
    }

    private fun inv33(m: DoubleArray): DoubleArray {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        val s = if (abs(det) < 1e-12) 0.0 else 1.0 / det
        return doubleArrayOf(
            (e * i - f * h) * s, (c * h - b * i) * s, (b * f - c * e) * s,
            (f * g - d * i) * s, (a * i - c * g) * s, (c * d - a * f) * s,
            (d * h - e * g) * s, (b * g - a * h) * s, (a * e - b * d) * s,
        )
    }

    private fun apply33(m: DoubleArray, x: Double, y: Double): DoubleArray {
        val xx = m[0] * x + m[1] * y + m[2]
        val yy = m[3] * x + m[4] * y + m[5]
        val w = m[6] * x + m[7] * y + m[8]
        return if (abs(w) < 1e-12) doubleArrayOf(xx, yy) else doubleArrayOf(xx / w, yy / w)
    }
}
