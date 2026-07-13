package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

import javax.inject.Inject
import javax.inject.Singleton

/** EtherCalc HTTP client — one room per logical tab. */
@Singleton
class EtherCalcClient @Inject constructor() {

    data class Config(val baseUrl: String, val roomPrefix: String = "ve")

    fun roomForTab(config: Config, tabName: String): String {
        val safe = tabName.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .take(40)
            .ifBlank { "sheet" }
        val prefix = config.roomPrefix.trim().ifBlank { "ve" }
        return "$prefix-$safe"
    }

    suspend fun readAllRows(config: Config, tabName: String): List<List<String>> =
        withContext(Dispatchers.IO) {
            val room = roomForTab(config, tabName)
            val csv = fetchCsv(config.baseUrl, room)
            if (csv.isBlank()) return@withContext emptyList()
            csv.lines().map { line -> parseCsvLine(line) }
        }

    suspend fun writeAllRows(config: Config, tabName: String, headers: List<String>, rows: List<List<String>>) =
        withContext(Dispatchers.IO) {
            val room = roomForTab(config, tabName)
            ensureRoom(config.baseUrl, room)
            val allRows = listOf(headers) + rows
            val csv = allRows.joinToString("\n") { row ->
                row.joinToString(",") { cell -> csvEscape(cell) }
            }
            postCsv(config.baseUrl, room, csv)
        }

    suspend fun ensureRoom(baseUrl: String, room: String) {
        val url = "${baseUrl.trimEnd('/')}/_/$room"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    suspend fun renameRoom(config: Config, oldTab: String, newTab: String): Boolean =
        withContext(Dispatchers.IO) {
            // EtherCalc has no rename; copy data to new room if old exists and new empty.
            val oldRoom = roomForTab(config, oldTab)
            val newRoom = roomForTab(config, newTab)
            if (oldRoom == newRoom) return@withContext true
            val oldCsv = fetchCsv(config.baseUrl, oldRoom)
            if (oldCsv.isBlank()) return@withContext true
            val newCsv = fetchCsv(config.baseUrl, newRoom)
            if (newCsv.isNotBlank()) return@withContext false
            postCsv(config.baseUrl, newRoom, oldCsv)
            true
        }

    suspend fun deleteRoom(config: Config, tabName: String) =
        withContext(Dispatchers.IO) {
            val room = roomForTab(config, tabName)
            val url = "${config.baseUrl.trimEnd('/')}/_/$room"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }

    suspend fun testConnection(config: Config): Boolean = withContext(Dispatchers.IO) {
        val marker = "sync-test-${System.currentTimeMillis()}"
        val room = "${config.roomPrefix}-sync-test"
        try {
            ensureRoom(config.baseUrl, room)
            postCsv(config.baseUrl, room, "marker\n$marker")
            val csv = fetchCsv(config.baseUrl, room)
            val readBack = csv.lines().drop(1).firstOrNull()?.trim()
            deleteRoom(config, "__sync_test")
            readBack == marker
        } catch (e: Exception) {
            Log.e(TAG, "EtherCalc test failed", e)
            false
        }
    }

    private fun fetchCsv(baseUrl: String, room: String): String {
        val url = "${baseUrl.trimEnd('/')}/_/$room/csv"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        try {
            val code = conn.responseCode
            return if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                ""
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun postCsv(baseUrl: String, room: String, csv: String) {
        val url = "${baseUrl.trimEnd('/')}/_/$room"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "text/csv")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            outputStream.write(csv.toByteArray())
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IllegalStateException("EtherCalc POST HTTP $code: ${err.take(200)}")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun parseConfig(configJson: String, targetUrl: String, targetId: String): Config? {
        if (configJson.isNotBlank()) {
            return try {
                val obj = JSONObject(configJson)
                val base = obj.optString("baseUrl", "").trim()
                if (base.isBlank()) return null
                Config(base, obj.optString("roomPrefix", "ve"))
            } catch (_: Exception) {
                null
            }
        }
        val base = targetUrl.trim()
        if (base.isBlank()) return null
        return Config(base, targetId.trim().ifBlank { "ve" })
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        var inQuotes = false
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                        i++
                        continue
                    }
                }
                !inQuotes && c == '"' -> {
                    inQuotes = true
                    i++
                    continue
                }
                !inQuotes && c == ',' -> {
                    result.add(cur.toString())
                    cur.clear()
                    i++
                    continue
                }
                else -> {
                    cur.append(c)
                    i++
                }
            }
        }
        result.add(cur.toString())
        return result
    }

    companion object {
        private const val TAG = "EtherCalcClient"
    }
}