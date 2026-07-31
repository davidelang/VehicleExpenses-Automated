package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Multi-page text/table PDF builder for Lab reports using platform [PdfDocument].
 * Content is tabular/text equivalent of on-screen / TEXT share (no chart bitmaps).
 */
object ReportsLabPdf {

    data class PdfSection(
        val heading: String? = null,
        val lines: List<String> = emptyList(),
        /** When non-null, drawn as a simple equal-width column table. */
        val tableRows: List<List<String>>? = null,
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
    ): ByteArray {
        val allLines = plainText.lines()
        val meta = mutableListOf(
            "Vehicle Expenses",
            "Generated: ${formatGenerated(generatedMs)}",
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
