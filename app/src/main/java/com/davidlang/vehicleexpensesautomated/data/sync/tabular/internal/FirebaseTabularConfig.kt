package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import org.json.JSONObject

/** Firestore-specific config (project id + bearer token + collection map). */
object FirebaseTabularConfig {

    private const val FIRESTORE_BASE =
        "https://firestore.googleapis.com/v1/projects/%s/databases/(default)/documents"

    fun parse(json: String, targetUrl: String = ""): RowDbTabularConfig? {
        if (json.isBlank() && targetUrl.isBlank()) return null
        return try {
            val obj = if (json.isNotBlank()) JSONObject(json) else JSONObject()
            val projectId = obj.optString("projectId", "").trim()
                .ifBlank { extractProjectId(targetUrl) }
            if (projectId.isBlank()) return null
            val token = obj.optString("token", "").trim()
            val tablesObj = obj.optJSONObject("tables")
            val tables = buildMap {
                if (tablesObj != null) {
                    tablesObj.keys().forEach { key ->
                        val id = tablesObj.optString(key, "").trim()
                        if (id.isNotBlank()) put(key, id)
                    }
                }
            }
            RowDbTabularConfig(
                backendType = "firebase",
                baseUrl = FIRESTORE_BASE.format(projectId),
                token = token,
                projectId = projectId,
                tables = tables,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isConfigured(config: RowDbTabularConfig?): Boolean {
        if (config == null) return false
        if (config.projectId.isBlank() || config.token.isBlank()) return false
        return config.tables.containsKey(TabularSchema.TAB_VEHICLES) || config.tables.isNotEmpty()
    }

    fun toJson(
        projectId: String,
        token: String,
        vehiclesCollection: String,
        expensesCollection: String,
        fuelCollections: String,
    ): String {
        var config = RowDbTabularConfig(
            backendType = "firebase",
            baseUrl = FIRESTORE_BASE.format(projectId.trim()),
            token = token.trim(),
            projectId = projectId.trim(),
        )
        if (vehiclesCollection.isNotBlank()) {
            config = config.withTable(TabularSchema.TAB_VEHICLES, vehiclesCollection.trim())
        }
        if (expensesCollection.isNotBlank()) {
            config = config.withTable(TabularSchema.TAB_EXPENSES, expensesCollection.trim())
        }
        fuelCollections.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@forEach
            val parts = trimmed.split('=', limit = 2)
            if (parts.size == 2) {
                val tab = parts[0].trim()
                val collection = parts[1].trim()
                if (tab.isNotBlank() && collection.isNotBlank()) {
                    config = config.withTable(tab, collection)
                }
            }
        }
        return config.toJson()
    }

    fun hydrateFormState(configJson: String, targetUrl: String): FirebaseFormState {
        val parsed = parse(configJson, targetUrl)
        val fuelLines = parsed?.tables?.filterKeys { it.startsWith(TabularSchema.FUEL_TAB_PREFIX) }
            ?.entries?.joinToString("\n") { (tab, id) -> "$tab=$id" }
            .orEmpty()
        return FirebaseFormState(
            projectId = parsed?.projectId.orEmpty(),
            token = parsed?.token.orEmpty(),
            vehiclesCollection = parsed?.tableIdForTab(TabularSchema.TAB_VEHICLES).orEmpty(),
            expensesCollection = parsed?.tableIdForTab(TabularSchema.TAB_EXPENSES).orEmpty(),
            fuelCollections = fuelLines,
        )
    }

    private fun extractProjectId(targetUrl: String): String {
        val trimmed = targetUrl.trim()
        if (trimmed.isBlank()) return ""
        val regex = Regex("""projects/([^/]+)""")
        return regex.find(trimmed)?.groupValues?.get(1).orEmpty()
    }
}

data class FirebaseFormState(
    val projectId: String = "",
    val token: String = "",
    val vehiclesCollection: String = "",
    val expensesCollection: String = "",
    val fuelCollections: String = "",
)