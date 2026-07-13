package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PocketBaseClient @Inject constructor() : RowDbTableClient {

    private fun authHeaders(token: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
    )

    private fun collectionUrl(config: RowDbTabularConfig, collectionId: String, recordId: String? = null): String {
        val base = "${config.baseUrl}/api/collections/$collectionId/records"
        return if (recordId != null) "$base/$recordId" else base
    }

    override suspend fun listFieldMaps(
        config: RowDbTabularConfig,
        tableId: String,
    ): List<Pair<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, Map<String, String>>>()
        var page = 1
        while (true) {
            val url = "${collectionUrl(config, tableId)}?page=$page&perPage=200"
            val response = RowDbHttp.request(url, "GET", authHeaders(config.token))
            if (response.code !in 200..299) {
                throw IllegalStateException(RowDbHttp.errorMessage(response, "PocketBase list failed"))
            }
            val json = RowDbHttp.jsonObject(response.body)
                ?: throw IllegalStateException("PocketBase list returned invalid JSON")
            val pageResults = json.optJSONArray("items") ?: break
            if (pageResults.length() == 0) break
            for (i in 0 until pageResults.length()) {
                val rowObj = pageResults.optJSONObject(i) ?: continue
                val rowId = rowObj.optString("id", "")
                if (rowId.isBlank()) continue
                val fields = mutableMapOf<String, String>()
                rowObj.keys().forEach { key ->
                    if (key == "id" || key == "collectionId" || key == "collectionName" ||
                        key == "created" || key == "updated"
                    ) {
                        return@forEach
                    }
                    fields[key] = rowObj.opt(key)?.toString().orEmpty()
                }
                results.add(rowId to fields)
            }
            val totalPages = json.optInt("totalPages", 1)
            if (page >= totalPages) break
            page++
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
            collectionUrl(config, tableId),
            "POST",
            authHeaders(config.token),
            body.toString(),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "PocketBase create failed"))
        }
        RowDbHttp.jsonObject(response.body)?.optString("id", "").orEmpty()
    }

    override suspend fun updateRow(
        config: RowDbTabularConfig,
        tableId: String,
        rowId: String,
        headers: List<String>,
        row: List<String>,
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
        headers.forEachIndexed { index, header ->
            body.put(header, row.getOrElse(index) { "" })
        }
        val response = RowDbHttp.request(
            collectionUrl(config, tableId, rowId),
            "PATCH",
            authHeaders(config.token),
            body.toString(),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "PocketBase update failed"))
        }
    }

    override suspend fun deleteRow(config: RowDbTabularConfig, tableId: String, rowId: String) =
        withContext(Dispatchers.IO) {
            val response = RowDbHttp.request(
                collectionUrl(config, tableId, rowId),
                "DELETE",
                authHeaders(config.token),
            )
            if (response.code !in 200..299 && response.code != 204) {
                throw IllegalStateException(RowDbHttp.errorMessage(response, "PocketBase delete failed"))
            }
        }
}