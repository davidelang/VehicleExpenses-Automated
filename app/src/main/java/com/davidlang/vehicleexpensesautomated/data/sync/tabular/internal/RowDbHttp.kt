package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal object RowDbHttp {

    data class Response(val code: Int, val body: String)

    fun request(
        url: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 60_000,
    ): Response {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { reader ->
                BufferedReader(InputStreamReader(reader, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            return Response(code, text)
        } finally {
            conn.disconnect()
        }
    }

    fun jsonObject(body: String): JSONObject? = try {
        if (body.isBlank()) null else JSONObject(body)
    } catch (_: Exception) {
        null
    }

    fun jsonArray(body: String): JSONArray? = try {
        if (body.isBlank()) null else JSONArray(body)
    } catch (_: Exception) {
        null
    }

    fun errorMessage(response: Response, fallback: String): String {
        val fromJson = jsonObject(response.body)?.optString("detail")
            ?: jsonObject(response.body)?.optString("message")
        return fromJson?.takeIf { it.isNotBlank() } ?: "$fallback (HTTP ${response.code})"
    }
}