package com.davidlang.vehicleexpensesautomated.ui.experiment

import com.davidlang.vehicleexpensesautomated.ui.util.ProcessMemProbe
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Append-only JSONL under pump_reports. One UTF-8 line + fd.sync per event.
 * Survives mid-photo LMK (unlike pump_results JSON, which flushes after all columns).
 */
class PumpProgressJournal(reportDir: File, timestamp: String) {
    val file: File = File(reportDir, "pump_progress_$timestamp.jsonl")
    private val fos = FileOutputStream(file, true)

    fun append(ev: String, extra: JSONObject.() -> Unit = {}) {
        val mem = ProcessMemProbe.snapshot()
        val obj = JSONObject()
            .put("ev", ev)
            .put("rss_kb", mem.vmRssKb)
            .put("native_pss_kb", mem.nativePssKb)
        obj.extra()
        fos.write((obj.toString() + "\n").toByteArray(Charsets.UTF_8))
        fos.fd.sync()
    }

    fun close() {
        try {
            fos.fd.sync()
        } catch (_: Exception) {
        }
        try {
            fos.close()
        } catch (_: Exception) {
        }
    }
}
