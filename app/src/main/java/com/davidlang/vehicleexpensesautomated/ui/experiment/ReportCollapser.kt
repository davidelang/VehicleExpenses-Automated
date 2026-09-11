package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One tray cell. [sortA]/[sortB]/[sortC] are HTML file order (e.g. photo, row, column). */
data class ReportCellRef(
    val id: Int,
    val sortA: Int = 0,
    val sortB: Int = 0,
    val sortC: Int = 0,
)

/**
 * Stream-splice tray cells into a sparse HTML (and optional sparse JSON) report.
 * Tray stays on disk — never loads the report or a batch of bodies into RAM.
 */
class ReportCollapser(
    private val htmlFileForId: (Int) -> File,
    private val cellsDir: File,
    private val cursorFile: File,
    private val nCells: Int,
    private val cellOrder: List<ReportCellRef>,
    private val onLog: (String) -> Unit,
    private val sparseFile: File? = null,
    private val onStatus: ((phase: String, done: Int, total: Int, cursor: Int, detail: String) -> Unit)? = null,
    private val periodMs: Long = PERIOD_MS,
) {
    constructor(
        htmlFile: File,
        cellsDir: File,
        cursorFile: File,
        nCells: Int,
        cellOrder: List<ReportCellRef>,
        onLog: (String) -> Unit,
        sparseFile: File? = null,
        onStatus: ((phase: String, done: Int, total: Int, cursor: Int, detail: String) -> Unit)? = null,
        periodMs: Long = PERIOD_MS,
    ) : this(
        htmlFileForId = { htmlFile },
        cellsDir = cellsDir,
        cursorFile = cursorFile,
        nCells = nCells,
        cellOrder = cellOrder,
        onLog = onLog,
        sparseFile = sparseFile,
        onStatus = onStatus,
        periodMs = periodMs,
    )
    private val byId: Map<Int, ReportCellRef> = cellOrder.associateBy { it.id }
    @Volatile var cursor: Int = 0
        private set
    @Volatile private var finalRequested = false
    private var job: Job? = null

    fun start(scope: CoroutineScope): Job {
        cursor = cursorFile.readText().trim().toIntOrNull() ?: 0
        val j = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val n = collapseOnce(force = false)
                    if (finalRequested) {
                        if (!hasReadyBeyond(cursor) && n == 0) break
                        if (n == 0) delay(50) else continue
                    } else {
                        delay(periodMs)
                    }
                }
                drainAll()
            } catch (c: CancellationException) {
                drainAll()
                throw c
            }
        }
        job = j
        return j
    }

    /**
     * Signal end-of-run consolidation: cancel the schedule delay so the collapser
     * job wakes and drains, then [drainAll] from the run finally as a backstop.
     */
    fun requestFinal(cancelMessage: String = "report collapse final") {
        finalRequested = true
        job?.cancel(CancellationException(cancelMessage))
    }

    private fun hasReadyBeyond(c: Int): Boolean {
        var i = c + 1
        while (i <= nCells) {
            if (cellReady(i)) return true
            i++
        }
        return false
    }

    private fun cellReady(id: Int): Boolean {
        val tag = idTag(id)
        return File(cellsDir, "$tag.html").isFile && File(cellsDir, "$tag.json").isFile
    }

    /**
     * Merge all contiguous ready cells from [cursor] until a gap or [nCells].
     * Used at end of run so we do not wait for the next period tick.
     */
    fun drainAll() {
        var total = 0
        var rounds = 0
        while (rounds < nCells + 2) {
            val n = collapseOnce(force = true)
            total += n
            rounds++
            if (n == 0) break
        }
        onLog(
            "COLLAPSE drainAll merged=$total cursor=$cursor/$nCells " +
                "ready_beyond=${hasReadyBeyond(cursor)}",
        )
        deleteEmptyPumpImgDirs()
    }

    /**
     * Collapse contiguous ready ids (cursor+1…). Multi-splice in file (row-major) order.
     * @return number of cells merged
     */
    fun collapseOnce(force: Boolean): Int {
        val batch = ArrayList<Int>()
        var id = cursor + 1
        while (id <= nCells && cellReady(id)) {
            batch.add(id)
            id++
        }
        if (batch.isEmpty()) {
            if (force && finalRequested) {
                // Quiet when mid-run force is rare; end-of-run drain logs via drainAll.
            } else if (force) {
                onLog("COLLAPSE cursor=$cursor (no new cells)")
            }
            return 0
        }

        val fileOrder = batch.sortedWith(
            compareBy(
                { byId[it]?.sortA ?: 0 },
                { byId[it]?.sortB ?: 0 },
                { byId[it]?.sortC ?: 0 },
            ),
        )

        onLog("COLLAPSE ids ${batch.first()}..${batch.last()} (n=${batch.size}) fileOrder=${fileOrder.size} stream")

        val byFile = fileOrder.groupBy { htmlFileForId(it) }
        for ((file, ids) in byFile) {
            multiSpliceStream(
                src = file,
                orderedIds = ids,
                begin = { htmlBegin(it) },
                end = { htmlEnd(it) },
                bodyFile = { File(cellsDir, "${idTag(it)}.html") },
            )
        }
        val sparse = sparseFile
        if (sparse != null) {
            multiSpliceSparseLines(
                src = sparse,
                orderedIds = batch,
                bodyFile = { File(cellsDir, "${idTag(it)}.json") },
            )
        }

        for (cid in batch) {
            val tag = idTag(cid)
            File(cellsDir, "$tag.html").delete()
            File(cellsDir, "$tag.json").delete()
            File(cellsDir, "$tag.html.part").delete()
            File(cellsDir, "$tag.json.part").delete()
        }

        cursor = batch.last()
        cursorFile.writeText(cursor.toString())
        onStatus?.invoke("collapsing", cursor, nCells, cursor, "merged ${batch.size}")
        return batch.size
    }

    /**
     * results.sparse: each cell is one line `BEGIN{json}END`. Replace matching lines
     * by streaming the tray JSON (one cell at a time).
     */
    private fun multiSpliceSparseLines(
        src: File,
        orderedIds: List<Int>,
        bodyFile: (Int) -> File,
    ) {
        if (orderedIds.isEmpty()) return
        val begins = orderedIds.associateWith { jsonBegin(it) }
        val tmp = File(src.parentFile, src.name + ".tmp")
        tmp.delete()
        src.bufferedReader().use { reader ->
            tmp.bufferedWriter().use { writer ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val ln = line!!
                    var replaced = false
                    for (cid in orderedIds) {
                        val b = begins[cid]!!
                        if (ln.contains(b)) {
                            val body = bodyFile(cid)
                            if (body.isFile) {
                                writer.append(jsonBegin(cid))
                                body.bufferedReader().use { br ->
                                    var chunk: String?
                                    while (br.readLine().also { chunk = it } != null) {
                                        writer.append(chunk)
                                    }
                                }
                                writer.append(jsonEnd(cid))
                                writer.append('\n')
                            } else {
                                writer.append(ln)
                                writer.append('\n')
                            }
                            replaced = true
                            break
                        }
                    }
                    if (!replaced) {
                        writer.append(ln)
                        writer.append('\n')
                    }
                }
            }
        }
        val bak = File(src.parentFile, src.name + ".bak")
        bak.delete()
        if (src.exists()) src.renameTo(bak)
        if (!tmp.renameTo(src)) {
            tmp.copyTo(src, overwrite = true)
            tmp.delete()
        }
        bak.delete()
    }

    /**
     * Stream multi-splice: O(marker) memory.
     * 1) Scan src once with a rolling window → offsets of BEGIN/END for each id (file order).
     * 2) Copy gaps via FileChannel.transferTo; stream each tray body file into the gap.
     * 3) Atomic mv of .tmp → src.
     */
    private fun multiSpliceStream(
        src: File,
        orderedIds: List<Int>,
        begin: (Int) -> String,
        end: (Int) -> String,
        bodyFile: (Int) -> File,
    ) {
        if (orderedIds.isEmpty()) return
        val begins = orderedIds.map { begin(it).toByteArray(Charsets.UTF_8) }
        val ends = orderedIds.map { end(it).toByteArray(Charsets.UTF_8) }
        val spans = findMarkerSpans(src, begins, ends) ?: run {
            Log.e(TAG, "multiSpliceStream: marker scan failed for ${src.name}")
            return
        }

        val toDelete = ArrayList<File>()
        val tmp = File(src.parentFile, src.name + ".tmp")
        tmp.delete()
        java.io.RandomAccessFile(src, "r").use { rafIn ->
            val inCh = rafIn.channel
            FileOutputStream(tmp).use { fos ->
                val outCh = fos.channel
                var pos = 0L
                for (i in orderedIds.indices) {
                    val cid = orderedIds[i]
                    val bAt = spans[i * 2]
                    val eAt = spans[i * 2 + 1]
                    val bMark = begins[i]
                    val eMark = ends[i]
                    transferRange(inCh, outCh, pos, bAt - pos)
                    writeFully(outCh, bMark)
                    val body = bodyFile(cid)
                    if (body.isFile) {
                        val inlined = inlineCellHtml(body, src.parentFile)
                        writeFully(outCh, inlined.bytes)
                        toDelete.addAll(inlined.files)
                    }
                    writeFully(outCh, eMark)
                    pos = eAt + eMark.size
                }
                val len = inCh.size()
                if (pos < len) transferRange(inCh, outCh, pos, len - pos)
                outCh.force(true)
            }
        }

        val bak = File(src.parentFile, src.name + ".bak")
        bak.delete()
        if (src.exists() && !src.renameTo(bak)) {
            Log.w(TAG, "rename src→bak failed ${src.name}")
        }
        val replaced = if (!tmp.renameTo(src)) {
            tmp.inputStream().use { inp ->
                FileOutputStream(src).use { out -> inp.copyTo(out, 64 * 1024) }
            }
            tmp.delete()
            src.isFile
        } else {
            true
        }
        bak.delete()
        if (replaced) {
            for (f in toDelete) {
                if (f.isFile && !f.delete()) {
                    Log.w(TAG, "inline delete failed ${f.path}")
                }
            }
        }
    }

    private data class InlinedCell(val bytes: ByteArray, val files: List<File>)

    /** Relative img src → data URI. Cells are small; do not load the report. */
    private fun inlineCellHtml(body: File, reportDir: File?): InlinedCell {
        val text = body.readText(Charsets.UTF_8)
        if (reportDir == null) return InlinedCell(text.toByteArray(Charsets.UTF_8), emptyList())
        val inlined = ArrayList<File>()
        val out = StringBuilder(text.length + 64)
        var i = 0
        while (i < text.length) {
            val q1 = text.indexOf("src='", i)
            val q2 = text.indexOf("src=\"", i)
            val useSingle = when {
                q1 < 0 && q2 < 0 -> -1
                q1 < 0 -> 1
                q2 < 0 -> 0
                q1 < q2 -> 0
                else -> 1
            }
            if (useSingle < 0) {
                out.append(text, i, text.length)
                break
            }
            val start = if (useSingle == 0) q1 else q2
            val quote = if (useSingle == 0) '\'' else '"'
            val prefixLen = 5
            out.append(text, i, start)
            val valStart = start + prefixLen
            val valEnd = text.indexOf(quote, valStart)
            if (valEnd < 0) {
                out.append(text, start, text.length)
                break
            }
            val srcVal = text.substring(valStart, valEnd)
            out.append("src=").append(quote)
            if (srcVal.startsWith("data:") || srcVal.contains("..")) {
                out.append(srcVal)
            } else {
                val f = File(reportDir, srcVal)
                if (f.isFile) {
                    val mime = if (srcVal.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
                    val b64 = java.util.Base64.getEncoder().encodeToString(f.readBytes())
                    out.append("data:").append(mime).append(";base64,").append(b64)
                    inlined.add(f)
                } else {
                    Log.w(TAG, "missing sidecar $srcVal")
                    out.append(srcVal)
                }
            }
            out.append(quote)
            i = valEnd + 1
        }
        return InlinedCell(out.toString().toByteArray(Charsets.UTF_8), inlined)
    }

    private fun deleteEmptyPumpImgDirs() {
        val dir = cellsDir.parentFile ?: return
        val kids = dir.listFiles() ?: return
        for (d in kids) {
            if (!d.isDirectory || !d.name.startsWith("pump_imgs_")) continue
            val leftover = d.listFiles()
            if (leftover != null && leftover.isEmpty()) d.delete()
        }
    }

    private fun transferRange(
        from: java.nio.channels.FileChannel,
        to: java.nio.channels.FileChannel,
        position: Long,
        count: Long,
    ) {
        var pos = position
        var rem = count
        while (rem > 0) {
            val n = from.transferTo(pos, rem, to)
            if (n <= 0) break
            pos += n
            rem -= n
        }
    }

    private fun writeFully(ch: java.nio.channels.FileChannel, bytes: ByteArray) {
        val buf = java.nio.ByteBuffer.wrap(bytes)
        while (buf.hasRemaining()) ch.write(buf)
    }

    /**
     * Single forward scan; needles alternate BEGIN_i, END_i for ordered ids.
     * Returns flat [b0,e0,b1,e1,…] file offsets, or null if any marker missing.
     * Uses a fixed-size rolling window (no full-file buffer).
     */
    private fun findMarkerSpans(
        src: File,
        begins: List<ByteArray>,
        ends: List<ByteArray>,
    ): LongArray? {
        val n = begins.size
        if (n == 0) return LongArray(0)
        val needles = ArrayList<ByteArray>(n * 2)
        for (i in 0 until n) {
            needles.add(begins[i])
            needles.add(ends[i])
        }
        val maxNeedle = needles.maxOf { it.size }.coerceAtLeast(1)
        val chunk = 64 * 1024
        val window = ByteArray(chunk + maxNeedle)
        val positions = LongArray(needles.size)
        var needleIdx = 0
        var fileBase = 0L
        var winLen = 0

        FileInputStream(src).use { fis ->
            while (needleIdx < needles.size) {
                val needle = needles[needleIdx]
                if (winLen < needle.size) {
                    val nread = fis.read(window, winLen, window.size - winLen)
                    if (nread <= 0) {
                        Log.e(TAG, "EOF seeking marker #$needleIdx in ${src.name}")
                        return null
                    }
                    winLen += nread
                    continue
                }
                val at = indexOfBytes(window, 0, winLen, needle)
                if (at >= 0) {
                    positions[needleIdx] = fileBase + at
                    needleIdx++
                    val consume = at + needle.size
                    val remain = winLen - consume
                    if (remain > 0) {
                        System.arraycopy(window, consume, window, 0, remain)
                    }
                    fileBase += consume
                    winLen = remain
                } else {
                    val keep = maxNeedle - 1
                    if (winLen > keep) {
                        val drop = winLen - keep
                        System.arraycopy(window, drop, window, 0, keep)
                        fileBase += drop
                        winLen = keep
                    }
                    val nread = fis.read(window, winLen, window.size - winLen)
                    if (nread <= 0) {
                        Log.e(TAG, "EOF seeking marker #$needleIdx in ${src.name}")
                        return null
                    }
                    winLen += nread
                }
            }
        }
        return positions
    }

    private fun indexOfBytes(hay: ByteArray, from: Int, to: Int, needle: ByteArray): Int {
        if (needle.isEmpty()) return from
        val last = to - needle.size
        outer@ for (i in from..last) {
            for (j in needle.indices) {
                if (hay[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    companion object {
        private const val TAG = "ReportCollapser"
        const val PERIOD_MS = 60_000L

        fun idTag(id: Int): String = "%07d".format(Locale.US, id)
        fun htmlBegin(id: Int) = "<!--C:B:${idTag(id)}-->"
        fun htmlEnd(id: Int) = "<!--C:E:${idTag(id)}-->"
        fun jsonBegin(id: Int) = "<!--JC:B:${idTag(id)}-->"
        fun jsonEnd(id: Int) = "<!--JC:E:${idTag(id)}-->"

        /** Atomic tray publish: write .part (fsync), renameTo final. Caller must drop body refs after. */
        fun publishCell(
            cellsDir: File,
            id: Int,
            htmlBody: String,
            jsonBody: String,
        ) {
            val tag = idTag(id)
            val htmlPart = File(cellsDir, "$tag.html.part")
            val htmlFinal = File(cellsDir, "$tag.html")
            val jsonPart = File(cellsDir, "$tag.json.part")
            val jsonFinal = File(cellsDir, "$tag.json")
            writeTextSynced(htmlPart, htmlBody)
            writeTextSynced(jsonPart, jsonBody)
            if (!htmlPart.renameTo(htmlFinal)) {
                htmlFinal.delete()
                htmlPart.renameTo(htmlFinal)
            }
            if (!jsonPart.renameTo(jsonFinal)) {
                jsonFinal.delete()
                jsonPart.renameTo(jsonFinal)
            }
        }

        private fun writeTextSynced(file: File, text: String) {
            FileOutputStream(file).use { fos ->
                fos.write(text.toByteArray(Charsets.UTF_8))
                fos.fd.sync()
            }
        }
    }
}
