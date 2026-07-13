package com.davidlang.vehicleexpensesautomated.data.sync

import org.json.JSONArray
import org.json.JSONObject

private val OAUTH_BACKEND_TYPES = setOf(
    "drive", "dropbox", "onedrive", "box", "pcloud", "yandex",
    "fichier", "koofr", "mailru", "putio", "seafile",
)

data class RcloneProviderInfo(
    val name: String,
    val description: String,
    val options: List<RcloneConfigOption>,
) {
    val requiresOAuth: Boolean
        get() = name in OAUTH_BACKEND_TYPES
}

data class RcloneConfigOption(
    val name: String,
    val fieldName: String,
    val help: String,
    val required: Boolean,
    val isPassword: Boolean,
    val advanced: Boolean,
    val exclusive: Boolean,
    val examples: List<Pair<String, String>>,
    val defaultValue: String?,
    val type: String,
)

data class RcloneProviderKind(
    val id: String,
    val label: String,
)

/** Dynamic provider list from librclone with curated fallback for sparse schemas. */
object RcloneProviderCatalog {

    val KIND_GROUPS: List<RcloneProviderKind> = listOf(
        RcloneProviderKind("cloud", "Cloud storage"),
        RcloneProviderKind("selfhost", "Self-hosted / protocols"),
        RcloneProviderKind("decentralized", "Decentralized"),
        RcloneProviderKind("more", "More services"),
    )

    /** Hidden from Other create UI (still usable via import conf). */
    val UI_HIDDEN_TYPES: Set<String> = setOf(
        "alias", "union", "combine", "crypt", "chunker", "compress", "hasher", "cache", "archive",
        "drive", "onedrive", "s3",
    )

    /** Compiled out of photo AAR (denylist hides even if present in a full binary). */
    val COMPILED_OUT_TYPES: Set<String> = setOf(
        "local", "memory", "http", "doi", "imagekit", "cloudinary",
        "sharefile", "linkbox", "googlephotos", "hdfs",
        "netstorage", "qingstor", "swift", "filefabric", "quatrix", "internetarchive",
    )

    private val KIND_TYPE_MAP: Map<String, Set<String>> = mapOf(
        "cloud" to setOf(
            "azureblob", "azurefiles", "b2", "box", "dropbox", "googlecloudstorage",
            "oracleobjectstorage", "jottacloud", "yandex", "zoho", "hidrive", "koofr",
            "mailru", "fichier",
        ),
        "selfhost" to setOf("webdav", "sftp", "ftp", "smb", "seafile"),
        "decentralized" to setOf("storj", "sia"),
        "more" to setOf(
            "mega", "pcloud", "protondrive", "pikpak", "putio", "premiumizeme",
            "opendrive", "iclouddrive",
        ),
    )

    fun isVisibleInOtherCreateUI(type: String): Boolean {
        val lower = type.lowercase()
        if (lower in UI_HIDDEN_TYPES) return false
        if (lower in COMPILED_OUT_TYPES) return false
        return true
    }

    fun filterForOtherCreateUI(providers: List<RcloneProviderInfo>): List<RcloneProviderInfo> =
        providers.filter { isVisibleInOtherCreateUI(it.name) }

    fun providersForKind(providers: List<RcloneProviderInfo>, kindId: String): List<RcloneProviderInfo> {
        val filtered = filterForOtherCreateUI(providers)
        val knownForKind = KIND_TYPE_MAP[kindId].orEmpty()
        val inKind = filtered.filter { it.name.lowercase() in knownForKind }
        if (inKind.isNotEmpty()) return inKind.sortedBy { it.name }
        if (kindId == "more") {
            val allKnown = KIND_TYPE_MAP.values.flatten().map { it.lowercase() }.toSet()
            return filtered.filter { it.name.lowercase() !in allKnown }.sortedBy { it.name }
        }
        return emptyList()
    }

    fun listProviders(runtime: RcloneRuntime): List<RcloneProviderInfo> {
        return try {
            val output = runtime.rpc("config/providers", "{}")
            val parsed = parseProviders(output).ifEmpty { curatedFallback() }
            filterForOtherCreateUI(parsed)
        } catch (_: Exception) {
            filterForOtherCreateUI(curatedFallback())
        }
    }

    fun providerForType(runtime: RcloneRuntime, type: String): RcloneProviderInfo? {
        val all = try {
            val output = runtime.rpc("config/providers", "{}")
            parseProviders(output).ifEmpty { curatedFallback() }
        } catch (_: Exception) {
            curatedFallback()
        }
        return all.firstOrNull { it.name.equals(type, ignoreCase = true) }
            ?: curatedFallback().firstOrNull { it.name.equals(type, ignoreCase = true) }
    }

    fun formOptions(provider: RcloneProviderInfo, includeAdvanced: Boolean = false): List<RcloneConfigOption> =
        provider.options.filter { opt ->
            !opt.advanced || includeAdvanced
        }.filter { opt ->
            opt.name !in HIDDEN_OPTION_NAMES
        }

    private val HIDDEN_OPTION_NAMES = setOf(
        "config_is_local", "config_token", "config_refresh_token",
    )

    private fun parseProviders(output: JSONObject): List<RcloneProviderInfo> {
        val providers = output.optJSONArray("providers") ?: return emptyList()
        return buildList {
            for (i in 0 until providers.length()) {
                val p = providers.optJSONObject(i) ?: continue
                val name = p.optString("Name", p.optString("name", "")).trim()
                if (name.isBlank()) continue
                val desc = p.optString("Description", p.optString("description", name))
                val optionsArr = p.optJSONArray("Options") ?: p.optJSONArray("options") ?: JSONArray()
                add(
                    RcloneProviderInfo(
                        name = name,
                        description = desc,
                        options = parseOptions(optionsArr),
                    ),
                )
            }
        }.sortedBy { it.name }
    }

    private fun parseOptions(arr: JSONArray): List<RcloneConfigOption> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val hide = o.optInt("Hide", 0)
            if (hide != 0) continue
            val examples = o.optJSONArray("Examples")?.let { exArr ->
                buildList {
                    for (j in 0 until exArr.length()) {
                        val ex = exArr.optJSONObject(j) ?: continue
                        add(ex.optString("Value", "") to ex.optString("Help", ""))
                    }
                }
            } ?: emptyList()
            add(
                RcloneConfigOption(
                    name = o.optString("Name", ""),
                    fieldName = o.optString("FieldName", o.optString("Name", "")),
                    help = o.optString("Help", ""),
                    required = o.optBoolean("Required", false),
                    isPassword = o.optBoolean("IsPassword", false),
                    advanced = o.optBoolean("Advanced", false),
                    exclusive = o.optBoolean("Exclusive", false),
                    examples = examples,
                    defaultValue = o.opt("Default")?.toString(),
                    type = o.optString("Type", "string"),
                ),
            )
        }
    }

    private fun curatedFallback(): List<RcloneProviderInfo> = listOf(
        provider(
            "sftp",
            "SFTP",
            listOf(
                opt("host", "SFTP host", required = true),
                opt("user", "User", required = true),
                opt("port", "Port", defaultValue = "22"),
                opt("pass", "Password", isPassword = true),
                opt("key_file", "SSH key file path", advanced = true),
            ),
        ),
        provider(
            "webdav",
            "WebDAV",
            listOf(
                opt("url", "URL", required = true),
                opt("vendor", "Vendor", examples = listOf("other" to "Other")),
                opt("user", "User"),
                opt("pass", "Password", isPassword = true),
            ),
        ),
        provider(
            "b2",
            "Backblaze B2",
            listOf(
                opt("account", "Account ID or Application Key ID", required = true),
                opt("key", "Application Key", required = true, isPassword = true),
            ),
        ),
        provider(
            "azureblob",
            "Azure Blob Storage",
            listOf(
                opt("account", "Storage account name", required = true),
                opt("key", "Storage account key", required = true, isPassword = true),
            ),
        ),
        provider("dropbox", "Dropbox (requires browser sign-in)", listOf()),
        provider("mega", "Mega", listOf()),
        provider("storj", "Storj", listOf()),
    )

    private fun provider(name: String, description: String, options: List<RcloneConfigOption>) =
        RcloneProviderInfo(name, description, options)

    private fun opt(
        name: String,
        help: String,
        required: Boolean = false,
        isPassword: Boolean = false,
        advanced: Boolean = false,
        defaultValue: String? = null,
        examples: List<Pair<String, String>> = emptyList(),
    ) = RcloneConfigOption(
        name = name,
        fieldName = name,
        help = help,
        required = required,
        isPassword = isPassword,
        advanced = advanced,
        exclusive = examples.isNotEmpty(),
        examples = examples,
        defaultValue = defaultValue,
        type = "string",
    )
}