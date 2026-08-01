package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Which side of the plot an independent Y scale sits on (stable; no jumping). */
enum class LabAxisSide {
    Left,
    Right,
}

/**
 * One independent Y-scale family for the multi-axis time chart (A1–A6).
 * Multiple series on a family share that family's scale (e.g. all trip-% types).
 */
data class LabAxisFamily(
    val id: String,
    val unitLabel: String,
    val side: LabAxisSide,
    val axisColor: Color,
    val series: Map<String, List<LabTimeYPoint>>,
    /** Optional per-series stroke colors; defaults to [axisColor] with shade variation. */
    val seriesColors: Map<String, Color> = emptyMap(),
) {
    fun nonempty(): LabAxisFamily? {
        val clean = series.filter { it.value.isNotEmpty() }
        if (clean.isEmpty()) return null
        return copy(series = clean)
    }
}

/**
 * Single plot area, shared date X, **N independent Y scales** (Canvas).
 * Vico 3.2.3 only supports Start/End — this is the multi-family host for Time based reports.
 */
@Composable
fun LabMultiFamilyTimeSeriesChart(
    families: List<LabAxisFamily>,
    caption: String,
    emptyMessage: String = "Not enough points for a chart.",
    heightDp: Int = 280,
) {
    val active = families.mapNotNull { it.nonempty() }
    val allPts = active.flatMap { f -> f.series.values.flatten() }
    val totalPts = allPts.size
    if (active.isEmpty() || totalPts < 2) {
        ReportsLabEmpty(emptyMessage)
        return
    }
    if (caption.isNotBlank()) {
        Text(caption, style = MaterialTheme.typography.labelMedium, softWrap = true)
    }
    val left = active.filter { it.side == LabAxisSide.Left }
    val right = active.filter { it.side == LabAxisSide.Right }
    val axisLegend = (left + right).joinToString(" · ") { f ->
        val side = if (f.side == LabAxisSide.Left) "L" else "R"
        "$side:${f.unitLabel}"
    }
    Text(
        "Axes: $axisLegend",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        softWrap = true,
    )

    val minX = allPts.minOf { it.timestampMs }.toDouble()
    val maxX = allPts.maxOf { it.timestampMs }.toDouble().coerceAtLeast(minX + 1.0)
    val dateFmt = SimpleDateFormat("MM/dd", Locale.getDefault())

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp),
    ) {
        val w = size.width
        val h = size.height
        val axisSlot = 44f
        val padL = max(48f, left.size * axisSlot)
        val padR = max(16f, right.size * axisSlot)
        val padT = 12f
        val padB = 36f
        val plotW = (w - padL - padR).coerceAtLeast(1f)
        val plotH = (h - padT - padB).coerceAtLeast(1f)
        val plotLeft = padL
        val plotTop = padT
        val plotBottom = padT + plotH
        val plotRight = padL + plotW

        // Plot background + frame
        drawRect(
            color = Color(0x0A000000),
            topLeft = Offset(plotLeft, plotTop),
            size = androidx.compose.ui.geometry.Size(plotW, plotH),
        )
        drawLine(Color(0xFF888888), Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), 1.5f)
        drawLine(Color(0xFF888888), Offset(plotRight, plotTop), Offset(plotRight, plotBottom), 1.5f)
        drawLine(Color(0xFF888888), Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), 1.5f)

        fun xOf(ts: Long): Float =
            plotLeft + ((ts - minX) / (maxX - minX) * plotW).toFloat()

        fun rangeOf(f: LabAxisFamily): Pair<Double, Double> {
            val ys = f.series.values.flatten().map { it.y.toDouble() }
            if (ys.isEmpty()) return 0.0 to 1.0
            var lo = ys.min()
            var hi = ys.max()
            if (hi - lo < 1e-9) {
                lo -= 1.0
                hi += 1.0
            }
            // Small padding
            val pad = (hi - lo) * 0.05
            return (lo - pad) to (hi + pad)
        }

        fun yOf(y: Float, lo: Double, hi: Double): Float {
            val t = ((y - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
            return plotBottom - t * plotH
        }

        val native = drawContext.canvas.nativeCanvas
        val tickPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
        }
        val labelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Left axes (outermost first)
        left.forEachIndexed { idx, fam ->
            val (lo, hi) = rangeOf(fam)
            val ax = plotLeft - idx * axisSlot - 4f
            drawLine(fam.axisColor, Offset(ax, plotTop), Offset(ax, plotBottom), 2.5f)
            tickPaint.color = fam.axisColor.toArgb()
            labelPaint.color = fam.axisColor.toArgb()
            // 4 ticks
            for (t in 0..3) {
                val v = lo + (hi - lo) * (1.0 - t / 3.0)
                val yy = plotTop + plotH * (t / 3f)
                drawLine(fam.axisColor, Offset(ax - 4f, yy), Offset(ax + 4f, yy), 1.5f)
                val txt = formatTick(v)
                native.drawText(txt, ax - 8f - tickPaint.measureText(txt), yy + 8f, tickPaint)
            }
            native.save()
            native.rotate(-90f, ax - 28f, (plotTop + plotBottom) / 2f)
            native.drawText(fam.unitLabel, ax - 28f, (plotTop + plotBottom) / 2f, labelPaint)
            native.restore()
        }

        // Right axes
        right.forEachIndexed { idx, fam ->
            val (lo, hi) = rangeOf(fam)
            val ax = plotRight + idx * axisSlot + 4f
            drawLine(fam.axisColor, Offset(ax, plotTop), Offset(ax, plotBottom), 2.5f)
            tickPaint.color = fam.axisColor.toArgb()
            labelPaint.color = fam.axisColor.toArgb()
            for (t in 0..3) {
                val v = lo + (hi - lo) * (1.0 - t / 3.0)
                val yy = plotTop + plotH * (t / 3f)
                drawLine(fam.axisColor, Offset(ax - 4f, yy), Offset(ax + 4f, yy), 1.5f)
                native.drawText(formatTick(v), ax + 8f, yy + 8f, tickPaint)
            }
            native.save()
            native.rotate(90f, ax + 28f, (plotTop + plotBottom) / 2f)
            native.drawText(fam.unitLabel, ax + 28f, (plotTop + plotBottom) / 2f, labelPaint)
            native.restore()
        }

        // Bottom X ticks
        tickPaint.color = 0xFF444444.toInt()
        val xTicks = 4
        for (t in 0..xTicks) {
            val frac = t / xTicks.toFloat()
            val xx = plotLeft + plotW * frac
            val ts = (minX + (maxX - minX) * frac).toLong()
            drawLine(Color(0xFF888888), Offset(xx, plotBottom), Offset(xx, plotBottom + 6f), 1.5f)
            val label = dateFmt.format(Date(ts))
            native.drawText(label, xx - tickPaint.measureText(label) / 2f, plotBottom + 28f, tickPaint)
        }

        // Series polylines
        active.forEach { fam ->
            val (lo, hi) = rangeOf(fam)
            val keys = fam.series.keys.toList()
            keys.forEachIndexed { si, name ->
                val pts = fam.series[name]?.sortedBy { it.timestampMs }.orEmpty()
                if (pts.size < 2) return@forEachIndexed
                val color = fam.seriesColors[name]
                    ?: seriesStrokeColor(name, fam.axisColor, si, keys.size)
                val path = Path()
                pts.forEachIndexed { i, pt ->
                    val x = xOf(pt.timestampMs)
                    val y = yOf(pt.y, lo, hi)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = color, style = Stroke(width = 3.2f))
            }
        }
    }
}

private fun formatTick(v: Double): String = when {
    kotlin.math.abs(v) >= 100 -> "%.0f".format(v)
    kotlin.math.abs(v) >= 10 -> "%.1f".format(v)
    kotlin.math.abs(v) >= 1 -> "%.2f".format(v)
    else -> "%.3f".format(v)
}

/**
 * Multi-family chart as Android [android.graphics.Bitmap] for PDF (same assignment as on-screen).
 */
fun renderMultiFamilyChartBitmap(
    families: List<LabAxisFamily>,
    widthPx: Int = 1100,
    heightPx: Int = 560,
    title: String = "",
): android.graphics.Bitmap {
    val active = families.mapNotNull { it.nonempty() }
    val bmp = android.graphics.Bitmap.createBitmap(widthPx, heightPx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(0xFFFFFFFF.toInt())
    val allPts = active.flatMap { f -> f.series.values.flatten() }
    if (active.isEmpty() || allPts.size < 2) {
        val p = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF666666.toInt()
            textSize = 28f
        }
        canvas.drawText("No chart data", 40f, heightPx / 2f, p)
        return bmp
    }
    val left = active.filter { it.side == LabAxisSide.Left }
    val right = active.filter { it.side == LabAxisSide.Right }
    val axisSlot = 48f
    val padL = max(52f, left.size * axisSlot)
    val padR = max(20f, right.size * axisSlot)
    val padT = if (title.isNotBlank()) 40f else 16f
    val padB = 44f
    val plotW = (widthPx - padL - padR).coerceAtLeast(1f)
    val plotH = (heightPx - padT - padB).coerceAtLeast(1f)
    val plotLeft = padL
    val plotTop = padT
    val plotBottom = padT + plotH
    val plotRight = padL + plotW
    val minX = allPts.minOf { it.timestampMs }.toDouble()
    val maxX = allPts.maxOf { it.timestampMs }.toDouble().coerceAtLeast(minX + 1.0)
    val dateFmt = SimpleDateFormat("MM/dd", Locale.getDefault())

    fun rangeOf(f: LabAxisFamily): Pair<Double, Double> {
        val ys = f.series.values.flatten().map { it.y.toDouble() }
        var lo = ys.min()
        var hi = ys.max()
        if (hi - lo < 1e-9) {
            lo -= 1.0
            hi += 1.0
        }
        val pad = (hi - lo) * 0.05
        return (lo - pad) to (hi + pad)
    }

    fun xOf(ts: Long) = plotLeft + ((ts - minX) / (maxX - minX) * plotW).toFloat()
    fun yOf(y: Float, lo: Double, hi: Double): Float {
        val t = ((y - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
        return plotBottom - t * plotH
    }

    val frame = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt()
        strokeWidth = 2f
        style = AndroidPaint.Style.STROKE
    }
    canvas.drawRect(plotLeft, plotTop, plotRight, plotBottom, frame)
    if (title.isNotBlank()) {
        val tp = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, plotLeft, 28f, tp)
    }

    val tickPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { textSize = 18f }
    left.forEachIndexed { idx, fam ->
        val (lo, hi) = rangeOf(fam)
        val ax = plotLeft - idx * axisSlot - 4f
        val ap = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = fam.axisColor.toArgb()
            strokeWidth = 3f
        }
        canvas.drawLine(ax, plotTop, ax, plotBottom, ap)
        tickPaint.color = fam.axisColor.toArgb()
        for (t in 0..3) {
            val v = lo + (hi - lo) * (1.0 - t / 3.0)
            val yy = plotTop + plotH * (t / 3f)
            canvas.drawLine(ax - 4f, yy, ax + 4f, yy, ap)
            val txt = formatTick(v)
            canvas.drawText(txt, ax - 8f - tickPaint.measureText(txt), yy + 6f, tickPaint)
        }
    }
    right.forEachIndexed { idx, fam ->
        val (lo, hi) = rangeOf(fam)
        val ax = plotRight + idx * axisSlot + 4f
        val ap = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = fam.axisColor.toArgb()
            strokeWidth = 3f
        }
        canvas.drawLine(ax, plotTop, ax, plotBottom, ap)
        tickPaint.color = fam.axisColor.toArgb()
        for (t in 0..3) {
            val v = lo + (hi - lo) * (1.0 - t / 3.0)
            val yy = plotTop + plotH * (t / 3f)
            canvas.drawLine(ax - 4f, yy, ax + 4f, yy, ap)
            canvas.drawText(formatTick(v), ax + 8f, yy + 6f, tickPaint)
        }
    }
    tickPaint.color = 0xFF444444.toInt()
    for (t in 0..4) {
        val frac = t / 4f
        val xx = plotLeft + plotW * frac
        val ts = (minX + (maxX - minX) * frac).toLong()
        canvas.drawLine(xx, plotBottom, xx, plotBottom + 6f, frame)
        val label = dateFmt.format(Date(ts))
        canvas.drawText(label, xx - tickPaint.measureText(label) / 2f, plotBottom + 28f, tickPaint)
    }

    active.forEach { fam ->
        val (lo, hi) = rangeOf(fam)
        val keys = fam.series.keys.toList()
        keys.forEachIndexed { si, name ->
            val pts = fam.series[name]?.sortedBy { it.timestampMs }.orEmpty()
            if (pts.size < 2) return@forEachIndexed
            val color = fam.seriesColors[name]
                ?: seriesStrokeColor(name, fam.axisColor, si, keys.size)
            val lp = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                strokeWidth = 3.5f
                style = AndroidPaint.Style.STROKE
            }
            val path = AndroidPath()
            pts.forEachIndexed { i, pt ->
                val x = xOf(pt.timestampMs)
                val y = yOf(pt.y, lo, hi)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, lp)
        }
    }
    return bmp
}
