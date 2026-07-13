package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NocoDbClient @Inject constructor() : RowDbTableClient {

    private fun authHeaders(token: String): Map<String, String> = mapOf(
        "xc-token" to token,
    )

    private fun recordsUrl(config: RowDbTabularConfig, tableId: String): String =
        "${config.baseUrl}/api/v2/tables/$tableId/records"

    override suspend fun listFieldMaps(
        config: RowDbTabularConfig,
        tableId: String,
    ): List<Pair<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, Map<String, String>>>()
        var offset = 0
        val limit = 200
        while (true) {
            val url = "${recordsUrl(config, tableId)}?offset=$offset&limit=$limit"
            val response = RowDbHttp.request(url, "GET", authHeaders(config.token))
            if (response.code !in 200..299) {
                throw IllegalStateException(RowDbHttp.errorMessage(response, "NocoDB list failed"))
            }
            val json = RowDbHttp.jsonObject(response.body)
                ?: throw IllegalStateException("NocoDB list returned invalid JSON")
            val pageResults = json.optJSONArray("list") ?: break
            if (pageResults.length() == 0) break
            for (i in 0 until pageResults.length()) {
                val rowObj = pageResults.optJSONObject(i) ?: continue
                val rowId = rowObj.opt("Id")?.toString()
                    ?: rowObj.opt("id")?.toString()
                    ?: continue
                val fields = mutableMapOf<String, String>()
                rowObj.keys().forEach { key ->
                    if (key.equals("Id", ignoreCase = true) || key == "id") return@forEach
                    fields[key] = rowObj.opt(key)?.toString().orEmpty()
                }
                results.add(rowId to fields)
            }
            val pageInfo = json.optJSONObject("pageInfo")
            val isLast = pageInfo?.optBoolean("isLastPage", true) ?: true
            if (isLast) break
            offset += limit
        }
        results
    }

    override suspend fun createRow(
        config: RowDbTabularConfig,
        tableId: String,
        headers: List<String>,
        row: List<String>,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
        headers.forEachIndexed { index, header ->
            body.put(header, row.getOrElse(index) { "" })
        }
        val response = RowDbHttp.request(
            recordsUrl(config, tableId),
            "POST",
            authHeaders(config.token),
            body.toString(),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "NocoDB create failed"))
        }
        val json = RowDbHttp.jsonObject(response.body)
        json?.opt("Id")?.toString()
            ?: json?.opt("id")?.toString()
            ?: ""
    }

    override suspend fun updateRow(
        config: RowDbTabularConfig,
        tableId: String,
        rowId: String,
        headers: List<String>,
        row: List<String>,
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("Id", rowId.toLongOrNull() ?: rowId)
        headers.forEachIndexed { index, header ->
            body.put(header, row.getOrElse(index) { "" })
        }
        val response = RowDbHttp.request(
            recordsUrl(config, tableId),
            "PATCH",
            authHeaders(config.token),
            body.toString(),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "NocoDB update failed"))
        }
    }

    override suspend fun deleteRow(config: RowDbTabularConfig, tableId: String, rowId: String) =
        withContext(Dispatchers.IO) {
            val idValue = rowId.toLongOrNull() ?: rowId
            val body = JSONArray().put(JSONObject().put("Id", idValue)).toString()
            val response = RowDbHttp.request(
                recordsUrl(config, tableId),
                "DELETE",
                authHeaders(config.token),
                body,
            )
            if (response.code !in 200..299 && response.code != 204) {
                throw IllegalStateException(RowDbHttp.errorMessage(response, "NocoDB delete failed"))
            }
        }
}