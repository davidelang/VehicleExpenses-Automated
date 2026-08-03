package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Multi-page text/table/chart PDF builder for Lab reports using platform [PdfDocument].
 */
object ReportsLabPdf {

    data class PdfSection(
        val heading: String? = null,
        val lines: List<String> = emptyList(),
        /** When non-null, drawn as a simple equal-width column table. */
        val tableRows: List<List<String>>? = null,
        /** Optional chart image (ARGB bitmap); scaled to content width. */
        val chartBitmap: Bitmap? = null,
    )

    /**
     * Build a PDF from structured sections. Paginate when near bottom; repeat title + meta on each page.
     */
    fun buildTextReportPdf(
        title: String,
        metaLines: List<String>,
        sections: List<PdfSection>,
        pageWidthPt: Int = 612,
        pageHeightPt: Int = 792,
    ): ByteArray {
        val margin = 40f
        val contentWidth = pageWidthPt - margin * 2
        val bottomLimit = pageHeightPt - margin

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF000000.toInt()
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = 0xFF333333.toInt()
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF000000.toInt()
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = 0xFF000000.toInt()
        }
        val lineGap = 3f

        val doc = PdfDocument()
        var pageNum = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun finishOpenPage() {
            page?.let { doc.finishPage(it) }
            page = null
            canvas = null
        }

        fun drawHeader(c: Canvas): Float {
            var yy = margin
            c.drawText(title, margin, yy + titlePaint.textSize, titlePaint)
            yy += titlePaint.textSize + 6f
            for (m in metaLines) {
                for (w in wrapText(m, metaPaint, contentWidth)) {
                    c.drawText(w, margin, yy + metaPaint.textSize, metaPaint)
                    yy += metaPaint.textSize + 2f
                }
            }
            yy += 6f
            c.drawLine(margin, yy, pageWidthPt - margin, yy, metaPaint)
            yy += 10f
            return yy
        }

        fun ensurePage(): Canvas {
            if (canvas != null && y < bottomLimit - bodyPaint.textSize) {
                return canvas!!
            }
            finishOpenPage()
            pageNum++
            val info = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, pageNum).create()
            val p = doc.startPage(info)
            page = p
            val c = p.canvas
            canvas = c
            y = drawHeader(c)
            return c
        }

        fun needSpace(h: Float) {
            if (y + h > bottomLimit) {
                // force new page on next ensure
                y = bottomLimit
            }
        }

        fun drawWrapped(text: String, paint: Paint) {
            val wrapped = wrapText(text, paint, contentWidth)
            for (line in wrapped) {
                needSpace(paint.textSize + lineGap)
                val c = ensurePage()
                c.drawText(line, margin, y + paint.textSize, paint)
                y += paint.textSize + lineGap
            }
        }

        // At least one page with header even if empty body
        ensurePage()

        for (section in sections) {
            if (!section.heading.isNullOrBlank()) {
                needSpace(headingPaint.textSize + 8f)
                val c = ensurePage()
                y += 4f
                c.drawText(section.heading!!, margin, y + headingPaint.textSize, headingPaint)
                y += headingPaint.textSize + 6f
            }
            val bmp = section.chartBitmap
            if (bmp != null && !bmp.isRecycled) {
                val scale = contentWidth / bmp.width.toFloat()
                val drawH = bmp.height * scale
                needSpace(drawH + 10f)
                val c = ensurePage()
                val dst = android.graphics.RectF(margin, y, margin + contentWidth, y + drawH)
                c.drawBitmap(bmp, null, dst, null)
                y += drawH + 8f
            }
            val table = section.tableRows
            if (table != null && table.isNotEmpty()) {
                val cols = table.maxOf { it.size }.coerceAtLeast(1)
                val colW = contentWidth / cols
                for (row in table) {
                    // Measure row height (max wrap lines among cells)
                    val cellLines = (0 until cols).map { ci ->
                        val cell = row.getOrElse(ci) { "" }
                        wrapText(cell, bodyPaint, colW - 4f)
                    }
                    val rowLines = cellLines.maxOf { it.size }.coerceAtLeast(1)
                    val rowH = rowLines * (bodyPaint.textSize + 2f) + 4f
                    needSpace(rowH)
                    val c = ensurePage()
                    for (li in 0 until rowLines) {
                        for (ci in 0 until cols) {
                            val lines = cellLines[ci]
                            val t = lines.getOrElse(li) { "" }
                            if (t.isNotEmpty()) {
                                c.drawText(
                                    t,
                                    margin + ci * colW,
                                    y + bodyPaint.textSize,
                                    bodyPaint,
                                )
                            }
                        }
                        y += bodyPaint.textSize + 2f
                    }
                    y += 4f
                }
            } else {
                for (line in section.lines) {
                    drawWrapped(line, bodyPaint)
                }
            }
        }

        finishOpenPage()
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /**
     * Build PDF from the same plain-text body used for TEXT share (period/vehicle already in body).
     * Adds generated timestamp + app name in the page header.
     */
    fun fromPlainText(
        title: String,
        plainText: String,
        generatedMs: Long = System.currentTimeMillis(),
        generatedLabel: String? = null,
    ): ByteArray {
        val allLines = plainText.lines()
        val genLine = generatedLabel ?: "Generated: ${formatGenerated(generatedMs)}"
        val meta = mutableListOf(
            // Export meta stays English product name (share/PDF header; not Compose scope)
            "Vehicle Expenses",
            genLine,
        )
        // Promote Period: / Vehicle: lines into header when present
        val body = mutableListOf<String>()
        for (line in allLines) {
            val t = line.trim()
            when {
                t.startsWith("Period:", ignoreCase = true) -> meta.add(t)
                t.startsWith("Vehicle:", ignoreCase = true) -> meta.add(t)
                t.startsWith("Vehicle Expenses", ignoreCase = true) -> { /* skip redundant title line */ }
                else -> body.add(line)
            }
        }
        if (body.isEmpty() && allLines.isNotEmpty()) {
            body.addAll(allLines)
        }
        return buildTextReportPdf(
            title = title,
            metaLines = meta.distinct(),
            sections = listOf(PdfSection(lines = body)),
        )
    }

    private fun formatGenerated(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

    /**
     * Software line chart from series points for PDF embed (P2).
     * Colors keyed by series name; unknown series use a palette.
     */
    fun renderLineChartBitmap(
        series: Map<String, List<LabTimeYPoint>>,
        seriesColors: Map<String, Color> = emptyMap(),
        widthPx: Int = 1000,
        heightPx: Int = 480,
        title: String = "",
        emptyChartLabel: String = "No chart data",
    ): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val padL = 56f
        val padR = 16f
        val padT = if (title.isNotBlank()) 36f else 16f
        val padB = 48f
        val plotW = widthPx - padL - padR
        val plotH = heightPx - padT - padB
        val allPts = series.values.flatten()
        if (allPts.size < 2 || plotW <= 0 || plotH <= 0) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF666666.toInt()
                textSize = 28f
            }
            canvas.drawText(emptyChartLabel, padL, heightPx / 2f, p)
            return bmp
        }
        val minX = allPts.minOf { it.timestampMs }.toDouble()
        val maxX = allPts.maxOf { it.timestampMs }.toDouble().coerceAtLeast(minX + 1.0)
        val minY = allPts.minOf { it.y }.toDouble()
        val maxY = allPts.maxOf { it.y }.toDouble()
        val ySpan = (maxY - minY).let { if (it < 1e-9) 1.0 else it }
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF333333.toInt()
            strokeWidth = 2f
            textSize = 18f
        }
        canvas.drawLine(padL, padT, padL, padT + plotH, axisPaint)
        canvas.drawLine(padL, padT + plotH, padL + plotW, padT + plotH, axisPaint)
        if (title.isNotBlank()) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF000000.toInt()
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(title, padL, 26f, tp)
        }
        val palette = listOf(
            0xFF1565C0.toInt(), 0xFF00897B.toInt(), 0xFF2E7D32.toInt(), 0xFF6A1B9A.toInt(),
            0xFFE65100.toInt(), 0xFFC62828.toInt(), 0xFF4527A0.toInt(), 0xFF00695C.toInt(),
        )
        var pi = 0
        series.entries.forEach { (name, pts) ->
            val sorted = pts.sortedBy { it.timestampMs }
            if (sorted.size < 2) return@forEach
            val colorInt = seriesColors[name]?.toArgb() ?: palette[pi % palette.size]
            pi++
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorInt
                strokeWidth = 3.5f
                style = Paint.Style.STROKE
            }
            val path = Path()
            sorted.forEachIndexed { i, pt ->
                val x = padL + ((pt.timestampMs - minX) / (maxX - minX) * plotW).toFloat()
                val y = (padT + plotH - ((pt.y - minY) / ySpan * plotH)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, linePaint)
        }
        // Legend strip
        val leg = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f }
        var lx = padL
        val ly = heightPx - 14f
        pi = 0
        for ((name, _) in series) {
            val colorInt = seriesColors[name]?.toArgb() ?: palette[pi % palette.size]
            pi++
            leg.color = colorInt
            canvas.drawCircle(lx + 6f, ly - 5f, 5f, leg)
            leg.color = 0xFF222222.toInt()
            canvas.drawText(name.take(28), lx + 16f, ly, leg)
            lx += leg.measureText(name.take(28)) + 36f
            if (lx > widthPx - 40) break
        }
        return bmp
    }

    /** Word-wrap (and hard-break long tokens) to [maxWidth] using [paint]. */
    fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        if (maxWidth <= 0f) return listOf(text)
        val result = mutableListOf<String>()
        val paragraphs = text.split('\n')
        for (para in paragraphs) {
            if (para.isEmpty()) {
                result.add("")
                continue
            }
            val words = para.split(Regex("\\s+"))
            var current = StringBuilder()
            for (word in words) {
                val pieces = breakLongToken(word, paint, maxWidth)
                for (piece in pieces) {
                    val candidate = if (current.isEmpty()) piece else "$current $piece"
                    if (paint.measureText(candidate) <= maxWidth) {
                        current = StringBuilder(candidate)
                    } else {
                        if (current.isNotEmpty()) {
                            result.add(current.toString())
                        }
                        current = StringBuilder(piece)
                    }
                }
            }
            if (current.isNotEmpty()) result.add(current.toString())
        }
        return result.ifEmpty { listOf("") }
    }

    private fun breakLongToken(token: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(token) <= maxWidth) return listOf(token)
        val out = mutableListOf<String>()
        var i = 0
        while (i < token.length) {
            var j = i + 1
            var lastOk = i + 1
            while (j <= token.length && paint.measureText(token.substring(i, j)) <= maxWidth) {
                lastOk = j
                j++
            }
            if (lastOk == i) lastOk = (i + 1).coerceAtMost(token.length)
            out.add(token.substring(i, lastOk))
            i = lastOk
        }
        return out
    }
}
