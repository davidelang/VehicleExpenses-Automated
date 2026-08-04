package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EtherCalc **config + room naming** helpers only.
 * HTTP I/O is via remotetable [com.davidelang.remotetable.EtherCalcBackend].
 */
@Singleton
class EtherCalcClient @Inject constructor() {

    data class Config(val baseUrl: String, val roomPrefix: String = "ve")

    fun roomForTab(config: Config, tabName: String): String {
        val safe = tabName.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .take(40)
            .ifBlank { "sheet" }
        val prefix = config.roomPrefix.trim().ifBlank { "ve" }
        return "$prefix-$safe"
    }

    fun parseConfig(configJson: String, targetUrl: String, targetId: String): Config? {
        if (configJson.isNotBlank()) {
            return try {
                val obj = JSONObject(configJson)
                val base = obj.optString("baseUrl", "").trim()
                if (base.isBlank()) return null
                Config(base, obj.optString("roomPrefix", "ve"))
            } catch (_: Exception) {
                null
            }
        }
        val base = targetUrl.trim()
        if (base.isBlank()) return null
        return Config(base, targetId.trim().ifBlank { "ve" })
    }
}
