package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaserowClient @Inject constructor() : RowDbTableClient {

    private fun authHeaders(token: String): Map<String, String> = mapOf(
        "Authorization" to "Token $token",
    )

    private fun rowsUrl(config: RowDbTabularConfig, tableId: String, rowId: String? = null): String {
        val base = "${config.baseUrl}/api/database/rows/table/$tableId/"
        return if (rowId != null) "${base.trimEnd('/')}/$rowId/?user_field_names=true" else "${base}?user_field_names=true"
    }

    override suspend fun listFieldMaps(
        config: RowDbTabularConfig,
        tableId: String,
    ): List<Pair<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, Map<String, String>>>()
        var page = 1
        while (true) {
            val url = "${rowsUrl(config, tableId)}&page=$page&size=200"
            val response = RowDbHttp.request(url, "GET", authHeaders(config.token))
            if (response.code !in 200..299) {
                throw IllegalStateException(RowDbHttp.errorMessage(response, "Baserow list failed"))
            }
            val json = RowDbHttp.jsonObject(response.body)
                ?: throw IllegalStateException("Baserow list returned invalid JSON")
            val pageResults = json.optJSONArray("results") ?: break
            if (pageResults.length() == 0) break
            for (i in 0 until pageResults.length()) {
                val rowObj = pageResults.optJSONObject(i) ?: continue
                val rowId = rowObj.opt("id")?.toString().orEmpty()
                if (rowId.isBlank()) continue
                val fields = mutableMapOf<String, String>()
                rowObj.keys().forEach { key ->
                    if (key == "id" || key == "order") return@forEach
                    fields[key] = rowObj.opt(key)?.toString().orEmpty()
                }
                results.add(rowId to fields)
            }
            if (json.optString("next").isBlank()) break
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
            rowsUrl(config, tableId),
            "POST",
            authHeaders(config.token),
            body.toString(),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Baserow create failed"))
        }
        val json = RowDbHttp.jsonObject(response.body)
        json?.opt("id")?.toString().orEmpty()
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
            rowsUrl(config, tableId, rowId),
            "PATCH",
            authHeaders(config.token),
            body.toString(),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Baserow update failed"))
        }
    }

    override suspend fun deleteRow(config: RowDbTabularConfig, tableId: String, rowId: String) =
        withContext(Dispatchers.IO) {
            val response = RowDbHttp.request(
                rowsUrl(config, tableId, rowId),
                "DELETE",
                authHeaders(config.token),
            )
            if (response.code !in 200..299 && response.code != 204) {
                throw IllegalStateException(RowDbHttp.errorMessage(response, "Baserow delete failed"))
            }
        }
}