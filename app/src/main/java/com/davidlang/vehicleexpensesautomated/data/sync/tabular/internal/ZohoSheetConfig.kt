package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import org.json.JSONObject

data class ZohoSheetConfig(
    val workbookId: String,
    val clientId: String = "",
    val clientSecret: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val apiDomain: String = DEFAULT_API_DOMAIN,
    val accountsServer: String = DEFAULT_ACCOUNTS_SERVER,
    val sheets: Map<String, String> = emptyMap(),
) {
    fun sheetForTab(tabName: String): String? = sheets[tabName]?.trim()?.takeIf { it.isNotBlank() }

    fun withSheet(tabName: String, worksheetName: String): ZohoSheetConfig =
        copy(sheets = sheets + (tabName to worksheetName.trim()))

    fun withTokens(accessToken: String, refreshToken: String = this.refreshToken, apiDomain: String = this.apiDomain): ZohoSheetConfig =
        copy(
            accessToken = accessToken.trim(),
            refreshToken = refreshToken.trim().ifBlank { this.refreshToken },
            apiDomain = apiDomain.trim().ifBlank { DEFAULT_API_DOMAIN }.trimEnd('/'),
        )

    fun toJson(): String = JSONObject().apply {
        put("backendType", "zoho_sheet")
        put("workbookId", workbookId)
        if (clientId.isNotBlank()) put("clientId", clientId)
        if (clientSecret.isNotBlank()) put("clientSecret", clientSecret)
        if (accessToken.isNotBlank()) put("accessToken", accessToken)
        if (refreshToken.isNotBlank()) put("refreshToken", refreshToken)
        if (apiDomain.isNotBlank()) put("apiDomain", apiDomain)
        if (accountsServer.isNotBlank()) put("accountsServer", accountsServer)
        put("sheets", JSONObject().apply {
            sheets.forEach { (name, worksheet) -> put(name, worksheet) }
        })
    }.toString()

    companion object {
        const val DEFAULT_API_DOMAIN = "https://sheet.zoho.com"
        const val DEFAULT_ACCOUNTS_SERVER = "https://accounts.zoho.com"
        val OAUTH_SCOPES = "ZohoSheet.dataAPI.READ,ZohoSheet.dataAPI.UPDATE"
        val REDIRECT_URI = "vehicleexpenses://zoho/oauth"

        fun parse(json: String, targetId: String = ""): ZohoSheetConfig? {
            if (json.isBlank() && targetId.isBlank()) return null
            return try {
                val obj = if (json.isNotBlank()) JSONObject(json) else JSONObject()
                val workbookId = obj.optString("workbookId", targetId).trim()
                if (workbookId.isBlank()) return null
                val sheetsObj = obj.optJSONObject("sheets")
                val sheets = buildMap {
                    if (sheetsObj != null) {
                        sheetsObj.keys().forEach { key ->
                            val value = sheetsObj.optString(key, "").trim()
                            if (value.isNotBlank()) put(key, value)
                        }
                    }
                }
                ZohoSheetConfig(
                    workbookId = workbookId,
                    clientId = obj.optString("clientId", ""),
                    clientSecret = obj.optString("clientSecret", ""),
                    accessToken = obj.optString("accessToken", ""),
                    refreshToken = obj.optString("refreshToken", ""),
                    apiDomain = obj.optString("apiDomain", DEFAULT_API_DOMAIN).ifBlank { DEFAULT_API_DOMAIN },
                    accountsServer = obj.optString("accountsServer", DEFAULT_ACCOUNTS_SERVER)
                        .ifBlank { DEFAULT_ACCOUNTS_SERVER },
                    sheets = sheets,
                )
            } catch (_: Exception) {
                null
            }
        }

        fun isConfigured(config: ZohoSheetConfig?): Boolean {
            if (config == null) return false
            if (config.workbookId.isBlank() || config.accessToken.isBlank()) return false
            return config.sheets.containsKey(TabularSchema.TAB_VEHICLES) || config.sheets.isNotEmpty()
        }

        fun hydrateFormState(configJson: String, targetId: String): ZohoSheetFormState {
            val parsed = parse(configJson, targetId)
            val fuelLines = parsed?.sheets?.filterKeys { it.startsWith(TabularSchema.FUEL_TAB_PREFIX) }
                ?.entries?.joinToString("\n") { (tab, sheet) -> "$tab=$sheet" }
                .orEmpty()
            return ZohoSheetFormState(
                workbookId = parsed?.workbookId.orEmpty(),
                clientId = parsed?.clientId.orEmpty(),
                clientSecret = parsed?.clientSecret.orEmpty(),
                accessToken = parsed?.accessToken.orEmpty(),
                refreshToken = parsed?.refreshToken.orEmpty(),
                apiDomain = parsed?.apiDomain.orEmpty(),
                accountsServer = parsed?.accountsServer.orEmpty(),
                vehiclesSheet = parsed?.sheetForTab(TabularSchema.TAB_VEHICLES).orEmpty(),
                expensesSheet = parsed?.sheetForTab(TabularSchema.TAB_EXPENSES).orEmpty(),
                fuelSheets = fuelLines,
            )
        }

        fun buildJson(
            workbookId: String,
            clientId: String,
            clientSecret: String,
            accessToken: String,
            refreshToken: String,
            apiDomain: String,
            accountsServer: String,
            vehiclesSheet: String,
            expensesSheet: String,
            fuelSheets: String,
        ): String {
            var config = ZohoSheetConfig(
                workbookId = workbookId.trim(),
                clientId = clientId.trim(),
                clientSecret = clientSecret.trim(),
                accessToken = accessToken.trim(),
                refreshToken = refreshToken.trim(),
                apiDomain = apiDomain.trim().ifBlank { DEFAULT_API_DOMAIN },
                accountsServer = accountsServer.trim().ifBlank { DEFAULT_ACCOUNTS_SERVER },
            )
            if (vehiclesSheet.isNotBlank()) {
                config = config.withSheet(TabularSchema.TAB_VEHICLES, vehiclesSheet)
            }
            if (expensesSheet.isNotBlank()) {
                config = config.withSheet(TabularSchema.TAB_EXPENSES, expensesSheet)
            }
            fuelSheets.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) return@forEach
                val parts = trimmed.split('=', limit = 2)
                if (parts.size == 2) {
                    val tab = parts[0].trim()
                    val sheet = parts[1].trim()
                    if (tab.isNotBlank() && sheet.isNotBlank()) {
                        config = config.withSheet(tab, sheet)
                    }
                }
            }
            return config.toJson()
        }
    }
}

data class ZohoSheetFormState(
    val workbookId: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val apiDomain: String = "",
    val accountsServer: String = "",
    val vehiclesSheet: String = "",
    val expensesSheet: String = "",
    val fuelSheets: String = "",
)