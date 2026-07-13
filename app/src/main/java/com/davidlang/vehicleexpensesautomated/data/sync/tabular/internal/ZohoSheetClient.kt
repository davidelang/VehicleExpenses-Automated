package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZohoSheetClient @Inject constructor() {

    private fun authHeaders(accessToken: String): Map<String, String> = mapOf(
        "Authorization" to "Zoho-oauthtoken $accessToken",
    )

    private fun encodeSheetName(name: String): String =
        URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")

    private fun apiBase(config: ZohoSheetConfig): String =
        "${config.apiDomain.trimEnd('/')}/api/v2"

    suspend fun listWorksheets(config: ZohoSheetConfig): List<String> = withContext(Dispatchers.IO) {
        val url = "${apiBase(config)}/${config.workbookId}/worksheets"
        val response = RowDbHttp.request(url, "GET", authHeaders(config.accessToken))
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Zoho list worksheets failed"))
        }
        val json = RowDbHttp.jsonObject(response.body) ?: JSONObject()
        val worksheets = json.optJSONArray("worksheets")
            ?: json.optJSONObject("worksheet_details")?.let { JSONArray().put(it) }
            ?: JSONArray()
        buildList {
            for (i in 0 until worksheets.length()) {
                val item = worksheets.optJSONObject(i) ?: continue
                val name = item.optString("worksheet_name", item.optString("name", "")).trim()
                if (name.isNotBlank()) add(name)
            }
        }
    }

    suspend fun readAllRows(config: ZohoSheetConfig, worksheetName: String): List<List<String>> =
        withContext(Dispatchers.IO) {
            val encoded = encodeSheetName(worksheetName)
            val url = "${apiBase(config)}/${config.workbookId}/worksheets/$encoded/cells"
            val response = RowDbHttp.request(url, "GET", authHeaders(config.accessToken))
            if (response.code !in 200..299) {
                throw IllegalStateException(RowDbHttp.errorMessage(response, "Zoho read cells failed"))
            }
            parseCellsResponse(response.body)
        }

    suspend fun writeAllRows(
        config: ZohoSheetConfig,
        worksheetName: String,
        rows: List<List<String>>,
    ) = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext
        val encoded = encodeSheetName(worksheetName)
        val url = "${apiBase(config)}/${config.workbookId}/worksheets/$encoded/cells"
        val body = JSONObject()
        val data = JSONArray()
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { colIndex, value ->
                data.put(
                    JSONObject()
                        .put("row", rowIndex + 1)
                        .put("column", colIndex + 1)
                        .put("value", value),
                )
            }
        }
        body.put("cells", data)
        val response = RowDbHttp.request(url, "POST", authHeaders(config.accessToken), body.toString())
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Zoho write cells failed"))
        }
    }

    suspend fun ensureWorksheet(config: ZohoSheetConfig, worksheetName: String) = withContext(Dispatchers.IO) {
        val existing = listWorksheets(config)
        if (worksheetName in existing) return@withContext
        val url = "${apiBase(config)}/${config.workbookId}/worksheets"
        val body = JSONObject().put("worksheet_name", worksheetName)
        val response = RowDbHttp.request(url, "POST", authHeaders(config.accessToken), body.toString())
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Zoho create worksheet failed"))
        }
    }

    suspend fun renameWorksheet(
        config: ZohoSheetConfig,
        oldName: String,
        newName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        if (oldName == newName) return@withContext true
        val existing = listWorksheets(config)
        if (oldName !in existing) return@withContext newName in existing
        if (newName in existing) return@withContext false
        val encoded = encodeSheetName(oldName)
        val url = "${apiBase(config)}/${config.workbookId}/worksheets/$encoded"
        val body = JSONObject().put("worksheet_name", newName)
        val response = RowDbHttp.request(url, "PUT", authHeaders(config.accessToken), body.toString())
        response.code in 200..299
    }

    suspend fun deleteWorksheet(config: ZohoSheetConfig, worksheetName: String) = withContext(Dispatchers.IO) {
        val encoded = encodeSheetName(worksheetName)
        val url = "${apiBase(config)}/${config.workbookId}/worksheets/$encoded"
        val response = RowDbHttp.request(url, "DELETE", authHeaders(config.accessToken))
        if (response.code !in 200..299 && response.code != 404) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Zoho delete worksheet failed"))
        }
    }

    private fun parseCellsResponse(body: String): List<List<String>> {
        val json = RowDbHttp.jsonObject(body) ?: return emptyList()
        val cells = json.optJSONArray("cells") ?: json.optJSONArray("range_details") ?: return emptyList()
        val sparse = mutableMapOf<Pair<Int, Int>, String>()
        var maxRow = 0
        var maxCol = 0
        for (i in 0 until cells.length()) {
            val cell = cells.optJSONObject(i) ?: continue
            val row = cell.optInt("row", cell.optInt("row_index", 0))
            val col = cell.optInt("column", cell.optInt("column_index", 0))
            if (row <= 0 || col <= 0) continue
            val value = cell.optString("value", cell.optString("display_value", ""))
            sparse[row to col] = value
            maxRow = maxOf(maxRow, row)
            maxCol = maxOf(maxCol, col)
        }
        if (maxRow == 0 || maxCol == 0) return emptyList()
        return (1..maxRow).map { row ->
            (1..maxCol).map { col -> sparse[row to col].orEmpty() }
        }
    }
}