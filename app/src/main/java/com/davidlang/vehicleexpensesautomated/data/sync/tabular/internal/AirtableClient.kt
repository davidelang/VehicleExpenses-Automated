package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AirtableClient @Inject constructor() : RowDbTableClient {

    private fun authHeaders(token: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
    )

    private fun recordsUrl(config: RowDbTabularConfig, tableId: String, recordId: String? = null): String {
        val base = "https://api.airtable.com/v0/${config.baseId}/$tableId"
        return if (recordId != null) "$base/$recordId" else base
    }

    override suspend fun listFieldMaps(
        config: RowDbTabularConfig,
        tableId: String,
    ): List<Pair<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, Map<String, String>>>()
        var offset: String? = null
        while (true) {
            val query = if (offset.isNullOrBlank()) "" else "?offset=$offset"
            val response = RowDbHttp.request(
                recordsUrl(config, tableId) + query,
                "GET",
                authHeaders(config.token),
            )
            if (response.code !in 200..299) {
                throw IllegalStateException(RowDbHttp.errorMessage(response, "Airtable list failed"))
            }
            val json = RowDbHttp.jsonObject(response.body)
                ?: throw IllegalStateException("Airtable list returned invalid JSON")
            val pageResults = json.optJSONArray("records") ?: break
            for (i in 0 until pageResults.length()) {
                val rowObj = pageResults.optJSONObject(i) ?: continue
                val rowId = rowObj.optString("id", "")
                val fieldsObj = rowObj.optJSONObject("fields") ?: continue
                val fields = mutableMapOf<String, String>()
                fieldsObj.keys().forEach { key ->
                    fields[key] = fieldsObj.opt(key)?.toString().orEmpty()
                }
                results.add(rowId to fields)
            }
            offset = json.optString("offset", "").takeIf { it.isNotBlank() }
            if (offset == null) break
        }
        results
    }

    override suspend fun createRow(
        config: RowDbTabularConfig,
        tableId: String,
        headers: List<String>,
        row: List<String>,
    ): String = withContext(Dispatchers.IO) {
        val fields = JSONObject()
        headers.forEachIndexed { index, header ->
            fields.put(header, row.getOrElse(index) { "" })
        }
        val body = JSONObject().put("fields", fields).toString()
        val response = RowDbHttp.request(
            recordsUrl(config, tableId),
            "POST",
            authHeaders(config.token),
            body,
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Airtable create failed"))
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
        val fields = JSONObject()
        headers.forEachIndexed { index, header ->
            fields.put(header, row.getOrElse(index) { "" })
        }
        val body = JSONObject().put("fields", fields).toString()
        val response = RowDbHttp.request(
            recordsUrl(config, tableId, rowId),
            "PATCH",
            authHeaders(config.token),
            body,
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Airtable update failed"))
        }
    }

    override suspend fun deleteRow(config: RowDbTabularConfig, tableId: String, rowId: String) =
        withContext(Dispatchers.IO) {
            val response = RowDbHttp.request(
                recordsUrl(config, tableId, rowId),
                "DELETE",
                authHeaders(config.token),
            )
            if (response.code !in 200..299 && response.code != 204) {
                throw IllegalStateException(RowDbHttp.errorMessage(response, "Airtable delete failed"))
            }
        }
}