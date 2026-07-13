package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Firestore REST client — one collection per logical tab. */
@Singleton
class FirebaseTabularClient @Inject constructor() : RowDbTableClient {

    private fun authHeaders(token: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
    )

    private fun collectionUrl(config: RowDbTabularConfig, collectionId: String): String =
        "${config.baseUrl}/${encodePathSegment(collectionId)}"

    private fun documentUrl(config: RowDbTabularConfig, collectionId: String, documentId: String): String =
        "${collectionUrl(config, collectionId)}/${encodePathSegment(documentId)}"

    private fun encodePathSegment(segment: String): String =
        segment.split('/').joinToString("/") { part ->
            URLEncoder.encode(part, Charsets.UTF_8.name()).replace("+", "%20")
        }

    private fun parseFieldValue(fieldObj: JSONObject?): String {
        if (fieldObj == null) return ""
        return when {
            fieldObj.has("stringValue") -> fieldObj.optString("stringValue", "")
            fieldObj.has("integerValue") -> fieldObj.optString("integerValue", "")
            fieldObj.has("doubleValue") -> fieldObj.optString("doubleValue", "")
            fieldObj.has("booleanValue") -> fieldObj.optBoolean("booleanValue").toString()
            else -> fieldObj.optString("nullValue", "")
        }
    }

    private fun fieldsJson(headers: List<String>, row: List<String>): JSONObject {
        val fields = JSONObject()
        headers.forEachIndexed { index, header ->
            val value = row.getOrElse(index) { "" }
            fields.put(header, JSONObject().put("stringValue", value))
        }
        return JSONObject().put("fields", fields)
    }

    private fun documentIdFromName(name: String): String =
        name.substringAfterLast('/')

    override suspend fun listFieldMaps(
        config: RowDbTabularConfig,
        tableId: String,
    ): List<Pair<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        val response = RowDbHttp.request(
            collectionUrl(config, tableId),
            "GET",
            authHeaders(config.token),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Firestore list failed"))
        }
        val json = RowDbHttp.jsonObject(response.body) ?: JSONObject()
        val documents = json.optJSONArray("documents") ?: JSONArray()
        buildList {
            for (i in 0 until documents.length()) {
                val doc = documents.optJSONObject(i) ?: continue
                val name = doc.optString("name", "")
                val docId = documentIdFromName(name)
                if (docId.isBlank()) continue
                val fieldsObj = doc.optJSONObject("fields") ?: JSONObject()
                val fields = mutableMapOf<String, String>()
                fieldsObj.keys().forEach { key ->
                    fields[key] = parseFieldValue(fieldsObj.optJSONObject(key))
                }
                add(docId to fields)
            }
        }
    }

    override suspend fun createRow(
        config: RowDbTabularConfig,
        tableId: String,
        headers: List<String>,
        row: List<String>,
    ): String = withContext(Dispatchers.IO) {
        val syncIndex = headers.indexOf(RowDbTabularConfig.FIELD_SYNC_ID)
        val syncId = row.getOrElse(syncIndex) { "" }.trim()
        val url = if (syncId.isNotBlank()) {
            "${collectionUrl(config, tableId)}?documentId=${encodePathSegment(syncId)}"
        } else {
            collectionUrl(config, tableId)
        }
        val response = RowDbHttp.request(
            url,
            "POST",
            authHeaders(config.token),
            fieldsJson(headers, row).toString(),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Firestore create failed"))
        }
        val json = RowDbHttp.jsonObject(response.body)
        val name = json?.optString("name").orEmpty()
        documentIdFromName(name).ifBlank { syncId }
    }

    override suspend fun updateRow(
        config: RowDbTabularConfig,
        tableId: String,
        rowId: String,
        headers: List<String>,
        row: List<String>,
    ) = withContext(Dispatchers.IO) {
        val mask = headers.joinToString("&") { header ->
            "updateMask.fieldPaths=${URLEncoder.encode(header, Charsets.UTF_8.name())}"
        }
        val url = "${documentUrl(config, tableId, rowId)}?$mask"
        val response = RowDbHttp.request(
            url,
            "PATCH",
            authHeaders(config.token),
            fieldsJson(headers, row).toString(),
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(RowDbHttp.errorMessage(response, "Firestore update failed"))
        }
    }

    override suspend fun deleteRow(config: RowDbTabularConfig, tableId: String, rowId: String) =
        withContext(Dispatchers.IO) {
            val response = RowDbHttp.request(
                documentUrl(config, tableId, rowId),
                "DELETE",
                authHeaders(config.token),
            )
            if (response.code !in 200..299 && response.code != 404) {
                throw IllegalStateException(RowDbHttp.errorMessage(response, "Firestore delete failed"))
            }
        }
}