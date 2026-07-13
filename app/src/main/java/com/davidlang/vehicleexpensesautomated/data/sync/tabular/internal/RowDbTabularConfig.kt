package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import org.json.JSONObject

/** Shared config for row-database tabular backends (Baserow, NocoDB, PocketBase, Supabase, Airtable). */
data class RowDbTabularConfig(
    val backendType: String,
    val baseUrl: String,
    val token: String,
    val databaseId: Long? = null,
    val projectId: String = "",
    val baseId: String = "",
    val tables: Map<String, String> = emptyMap(),
) {
    fun tableIdForTab(tabName: String): String? = tables[tabName]?.trim()?.takeIf { it.isNotBlank() }

    fun withTable(tabName: String, tableId: String): RowDbTabularConfig =
        copy(tables = tables + (tabName to tableId.trim()))

    fun toJson(): String = JSONObject().apply {
        put("backendType", backendType)
        put("baseUrl", baseUrl)
        put("token", token)
        databaseId?.let { put("databaseId", it) }
        if (projectId.isNotBlank()) put("projectId", projectId)
        if (baseId.isNotBlank()) put("baseId", baseId)
        put("tables", JSONObject().apply {
            tables.forEach { (name, id) -> put(name, id) }
        })
    }.toString()

    companion object {
        const val FIELD_SYNC_ID = "Sync ID"

        fun backendTypeFor(provider: SpreadsheetProvider): String = when (provider) {
            SpreadsheetProvider.BASEROW -> "baserow"
            SpreadsheetProvider.NOCODB -> "nocodb"
            SpreadsheetProvider.POCKETBASE -> "pocketbase"
            SpreadsheetProvider.SUPABASE -> "supabase"
            SpreadsheetProvider.AIRTABLE -> "airtable"
            SpreadsheetProvider.FIREBASE -> "firebase"
            SpreadsheetProvider.ZOHO_SHEET -> "zoho_sheet"
            SpreadsheetProvider.ONLYOFFICE -> "onlyoffice"
            SpreadsheetProvider.COLLABORA -> "collabora"
            SpreadsheetProvider.OTHER -> "other"
            else -> provider.jsonValue
        }

        fun providerFromBackendType(type: String): SpreadsheetProvider? = when (type.lowercase()) {
            "baserow" -> SpreadsheetProvider.BASEROW
            "nocodb" -> SpreadsheetProvider.NOCODB
            "pocketbase" -> SpreadsheetProvider.POCKETBASE
            "supabase" -> SpreadsheetProvider.SUPABASE
            "airtable" -> SpreadsheetProvider.AIRTABLE
            "firebase" -> SpreadsheetProvider.FIREBASE
            "zoho_sheet", "zoho" -> SpreadsheetProvider.ZOHO_SHEET
            "onlyoffice" -> SpreadsheetProvider.ONLYOFFICE
            "collabora" -> SpreadsheetProvider.COLLABORA
            else -> null
        }

        fun parse(json: String, targetUrl: String = "", fallbackBackendType: String = ""): RowDbTabularConfig? {
            if (json.isBlank() && targetUrl.isBlank()) return null
            return try {
                val obj = if (json.isNotBlank()) JSONObject(json) else JSONObject()
                val backendType = obj.optString("backendType", fallbackBackendType).ifBlank { fallbackBackendType }
                val baseUrl = obj.optString("baseUrl", targetUrl).trim().trimEnd('/')
                val token = obj.optString("token", "")
                val databaseId = obj.optLong("databaseId", 0L).takeIf { it > 0L }
                    ?: obj.optString("databaseId", "").toLongOrNull()
                val projectId = obj.optString("projectId", "")
                val baseId = obj.optString("baseId", "")
                val tablesObj = obj.optJSONObject("tables")
                val tables = buildMap {
                    if (tablesObj != null) {
                        tablesObj.keys().forEach { key ->
                            val value = tablesObj.opt(key)
                            val id = when (value) {
                                is Number -> value.toString()
                                else -> value?.toString().orEmpty()
                            }
                            if (id.isNotBlank()) put(key, id)
                        }
                    }
                }
                val resolvedBaseUrl = baseUrl.ifBlank {
                    if (backendType.equals("airtable", ignoreCase = true)) {
                        "https://api.airtable.com"
                    } else {
                        ""
                    }
                }
                if (resolvedBaseUrl.isBlank()) return null
                RowDbTabularConfig(
                    backendType = backendType,
                    baseUrl = resolvedBaseUrl,
                    token = token,
                    databaseId = databaseId,
                    projectId = projectId,
                    baseId = baseId,
                    tables = tables,
                )
            } catch (_: Exception) {
                null
            }
        }

        fun isConfigured(config: RowDbTabularConfig?): Boolean {
            if (config == null) return false
            if (config.token.isBlank()) return false
            if (config.baseUrl.isBlank() && config.backendType.lowercase() != "airtable") return false
            return when (config.backendType.lowercase()) {
                "airtable" -> config.baseId.isNotBlank() &&
                    (config.tables.containsKey(TabularSchema.TAB_VEHICLES) || config.tables.isNotEmpty())
                else -> config.tables.containsKey(TabularSchema.TAB_VEHICLES) || config.tables.isNotEmpty()
            }
        }

        fun httpWarning(baseUrl: String): String? =
            if (baseUrl.startsWith("http://", ignoreCase = true)) {
                "Warning: HTTP is insecure; prefer HTTPS in production."
            } else {
                null
            }
    }
}