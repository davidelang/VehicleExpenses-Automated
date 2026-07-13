package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

import javax.inject.Inject
import javax.inject.Singleton

/** Microsoft Graph Excel workbook I/O — isolated from photo/OneDrive rclone paths. */
@Singleton
class GraphExcelClient @Inject constructor() {

    suspend fun listWorksheets(accessToken: String, workbookItemId: String): List<String> =
        withContext(Dispatchers.IO) {
            val url = "$GRAPH_BASE/me/drive/items/$workbookItemId/workbook/worksheets"
            val body = graphGet(accessToken, url)
            val arr = body.optJSONArray("value") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val name = arr.optJSONObject(i)?.optString("name", "")?.trim().orEmpty()
                    if (name.isNotBlank()) add(name)
                }
            }
        }

    suspend fun ensureWorksheet(accessToken: String, workbookItemId: String, name: String) =
        withContext(Dispatchers.IO) {
            val existing = listWorksheets(accessToken, workbookItemId)
            if (name in existing) return@withContext
            val url = "$GRAPH_BASE/me/drive/items/$workbookItemId/workbook/worksheets/add"
            graphPost(accessToken, url, JSONObject().put("name", name))
        }

    suspend fun renameWorksheet(
        accessToken: String,
        workbookItemId: String,
        oldName: String,
        newName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        if (oldName == newName) return@withContext true
        val existing = listWorksheets(accessToken, workbookItemId)
        if (oldName !in existing) return@withContext newName in existing
        if (newName in existing) return@withContext false
        val encoded = encodeSheetName(oldName)
        val url = "$GRAPH_BASE/me/drive/items/$workbookItemId/workbook/worksheets('$encoded')"
        graphPatch(accessToken, url, JSONObject().put("name", newName))
        true
    }

    suspend fun deleteWorksheet(accessToken: String, workbookItemId: String, name: String) =
        withContext(Dispatchers.IO) {
            val encoded = encodeSheetName(name)
            val url = "$GRAPH_BASE/me/drive/items/$workbookItemId/workbook/worksheets('$encoded')"
            graphDelete(accessToken, url)
        }

    suspend fun readRange(
        accessToken: String,
        workbookItemId: String,
        sheetName: String,
    ): List<List<String>> = withContext(Dispatchers.IO) {
        val encoded = encodeSheetName(sheetName)
        val url =
            "$GRAPH_BASE/me/drive/items/$workbookItemId/workbook/worksheets('$encoded')/range(address='A:ZZ')"
        val body = graphGet(accessToken, url)
        val values = body.optJSONArray("values") ?: return@withContext emptyList()
        parseValues(values)
    }

    suspend fun writeRange(
        accessToken: String,
        workbookItemId: String,
        sheetName: String,
        startCell: String,
        rows: List<List<String>>,
    ) = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext
        val encoded = encodeSheetName(sheetName)
        val startRowNum = startCell.filter { it.isDigit() }.toIntOrNull() ?: 1
        val endRow = startRowNum + rows.size - 1
        val endCol = rows.maxOfOrNull { it.size } ?: 1
        val endColLetter = columnLetter(endCol)
        val startCol = startCell.filter { it.isLetter() }.ifBlank { "A" }
        val address = "$startCol$startRowNum:$endColLetter$endRow"
        val url =
            "$GRAPH_BASE/me/drive/items/$workbookItemId/workbook/worksheets('$encoded')/range(address='$address')"
        val arr = JSONArray()
        rows.forEach { row ->
            val ja = JSONArray()
            row.forEach { cell -> ja.put(cell) }
            arr.put(ja)
        }
        graphPatch(accessToken, url, JSONObject().put("values", arr))
    }

    suspend fun clearFromRow(
        accessToken: String,
        workbookItemId: String,
        sheetName: String,
        startRow: Int,
    ) = withContext(Dispatchers.IO) {
        if (startRow < 1) return@withContext
        val encoded = encodeSheetName(sheetName)
        val address = "A$startRow:ZZ1048576"
        val url =
            "$GRAPH_BASE/me/drive/items/$workbookItemId/workbook/worksheets('$encoded')/range(address='$address')/clear"
        graphPost(accessToken, url, JSONObject().put("applyTo", "contents"))
    }

    suspend fun testWorkbook(accessToken: String, workbookItemId: String): Boolean =
        withContext(Dispatchers.IO) {
            val marker = "sync-test-${System.currentTimeMillis()}"
            try {
                ensureWorksheet(accessToken, workbookItemId, TAB_SYNC_TEST)
                writeRange(accessToken, workbookItemId, TAB_SYNC_TEST, "A1", listOf(listOf("marker"), listOf(marker)))
                val rows = readRange(accessToken, workbookItemId, TAB_SYNC_TEST)
                val readBack = rows.drop(1).firstOrNull()?.firstOrNull()
                deleteWorksheet(accessToken, workbookItemId, TAB_SYNC_TEST)
                readBack == marker
            } catch (e: Exception) {
                Log.e(TAG, "Excel test failed", e)
                try {
                    deleteWorksheet(accessToken, workbookItemId, TAB_SYNC_TEST)
                } catch (_: Exception) {
                }
                false
            }
        }

    private fun parseValues(values: JSONArray): List<List<String>> {
        val result = mutableListOf<List<String>>()
        for (i in 0 until values.length()) {
            val row = values.optJSONArray(i) ?: JSONArray()
            val cells = mutableListOf<String>()
            for (j in 0 until row.length()) {
                cells.add(row.opt(j)?.toString() ?: "")
            }
            result.add(cells)
        }
        return result
    }

    private fun encodeSheetName(name: String): String =
        URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")

    private fun columnLetter(col: Int): String {
        var n = col
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.toString().ifBlank { "A" }
    }

    private fun graphGet(accessToken: String, url: String): JSONObject =
        graphRequest(accessToken, url, "GET", null)

    private fun graphPost(accessToken: String, url: String, body: JSONObject?): JSONObject =
        graphRequest(accessToken, url, "POST", body)

    private fun graphPatch(accessToken: String, url: String, body: JSONObject): JSONObject =
        graphRequest(accessToken, url, "PATCH", body)

    private fun graphDelete(accessToken: String, url: String) {
        graphRequest(accessToken, url, "DELETE", null)
    }

    private fun graphRequest(accessToken: String, url: String, method: String, body: JSONObject?): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 30_000
            readTimeout = 60_000
            if (body != null) {
                doOutput = true
                outputStream.bufferedWriter().use { it.write(body.toString()) }
            }
        }
        try {
            val code = conn.responseCode
            val text = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            if (code !in 200..299) {
                throw IllegalStateException("Graph Excel HTTP $code: ${text.take(300)}")
            }
            return if (text.isBlank() || method == "DELETE") JSONObject() else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "GraphExcelClient"
        private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
        const val TAB_SYNC_TEST = "__sync_test"
    }
}