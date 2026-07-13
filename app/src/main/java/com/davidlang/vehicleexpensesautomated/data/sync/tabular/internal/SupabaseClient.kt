package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClient @Inject constructor() : RowDbTableClient {

    private fun authHeaders(token: String): Map<String, String> = mapOf(
        "apikey" to token,
        "Authorization" to "Bearer $token",
        "Prefer" to "return=representation",
    )

    private fun tableUrl(config: RowDbTabularConfig, tableId: String, query: String = ""): String {
        val base = "${config.baseUrl}/rest/v1/$tableId"
        return if (query.isBlank()) base else "$base?$query"
    }

    override suspend fun listFieldMaps(
        config: RowDbTabularConfig,
        tableId: String,
    ): List<Pair<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        val response = RowDbHttp.request(
            tableUrl(config, tableId, "select=*"),
            "GET",
            authHeaders(config.token),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Supabase list failed"))
        }
        val array = RowDbHttp.jsonArray(response.body)
            ?: throw IllegalStateException("Supabase list returned invalid JSON")
        buildList {
            for (i in 0 until array.length()) {
                val rowObj = array.optJSONObject(i) ?: continue
                val rowId = rowObj.opt("id")?.toString().orEmpty()
                if (rowId.isBlank()) continue
                val fields = mutableMapOf<String, String>()
                rowObj.keys().forEach { key ->
                    if (key == "id") return@forEach
                    fields[key] = rowObj.opt(key)?.toString().orEmpty()
                }
                add(rowId to fields)
            }
        }
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
            tableUrl(config, tableId),
            "POST",
            authHeaders(config.token),
            body.toString(),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Supabase create failed"))
        }
        val array = RowDbHttp.jsonArray(response.body)
        val obj = array?.optJSONObject(0) ?: RowDbHttp.jsonObject(response.body)
        obj?.opt("id")?.toString().orEmpty()
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
            tableUrl(config, tableId, "id=eq.$rowId"),
            "PATCH",
            authHeaders(config.token),
            body.toString(),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Supabase update failed"))
        }
    }

    override suspend fun deleteRow(config: RowDbTabularConfig, tableId: String, rowId: String) =
        withContext(Dispatchers.IO) {
            val response = RowDbHttp.request(
                tableUrl(config, tableId, "id=eq.$rowId"),
                "DELETE",
                authHeaders(config.token),
            )
            if (response.code !in 200..299 && response.code != 204) {
                throw IllegalStateException(RowDbHttp.errorMessage(response, "Supabase delete failed"))
            }
        }
}