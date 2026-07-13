package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import go.Seq
import gomobile.Gomobile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

class RcloneException(message: String) : Exception(message)

/** Result of a non-interactive config/create or config/update step. */
data class RcloneConfigStepResult(
    val complete: Boolean,
    val state: String = "",
    val question: RcloneConfigQuestion? = null,
)

data class RcloneConfigQuestion(
    val state: String,
    val name: String,
    val help: String,
    val defaultValue: String,
    val required: Boolean,
    val isPassword: Boolean,
    val exclusive: Boolean,
    val examples: List<Pair<String, String>>,
    val type: String,
    val error: String,
)

@Singleton
class RcloneRuntime @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val initLock = Any()
    private var initialized = false
    private var activeConfigPath: String? = null

    fun ensureInitialized() {
        synchronized(initLock) {
            if (initialized) return
            RcloneLoader.load(context)
            Seq.setContext(context)
            Gomobile.rcloneInitialize()
            initialized = true
            Log.i(TAG, "rclone initialized")
        }
    }

    fun smokeVersion(): String {
        ensureInitialized()
        val output = rpc("core/version", "{}")
        val version = output.optString("version", output.toString())
        Log.i(TAG, "rclone core/version: $version")
        return version
    }

    fun setConfigPath(confPath: String) {
        ensureInitialized()
        if (activeConfigPath == confPath) return
        val file = File(confPath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText("")
        }
        rpc("config/setpath", JSONObject().put("path", confPath).toString())
        activeConfigPath = confPath
        Log.i(TAG, "rclone config path set for dest conf (not logging contents)")
    }

    fun ensureConfigForDest(destId: String, config: RcloneDestConfig) {
        RcloneConfStorage.ensureEmptyConf(context, destId, config.confFileName)
        val confPath = RcloneConfStorage.confFile(context, destId, config.confFileName).absolutePath
        setConfigPath(confPath)
    }

    fun setConfigForDest(destId: String, config: RcloneDestConfig) {
        ensureConfigForDest(destId, config)
    }

    fun listRemotes(destId: String, config: RcloneDestConfig): List<String> {
        ensureConfigForDest(destId, config)
        val output = rpc("config/listremotes", "{}")
        val remotes = output.optJSONArray("remotes") ?: JSONArray()
        return buildList {
            for (i in 0 until remotes.length()) {
                val name = remotes.optString(i, "").trim()
                if (name.isNotBlank()) add(name)
            }
        }.sorted()
    }

    fun listProviders(): List<RcloneProviderInfo> = RcloneProviderCatalog.listProviders(this)

    fun createRemote(
        destId: String,
        config: RcloneDestConfig,
        name: String,
        type: String,
        parameters: Map<String, String>,
        continueState: String? = null,
        continueResult: String? = null,
    ): RcloneConfigStepResult {
        validateRemoteName(name)
        RcloneConfStorage.backupConfBeforeWrite(context, destId, config.confFileName)
        ensureConfigForDest(destId, config)
        val existing = listRemotes(destId, config)
        if (existing.any { it.equals(name, ignoreCase = true) }) {
            throw RcloneException("Remote name already exists: $name")
        }
        val opt = buildConfigOpt(continueState, continueResult)
        val input = JSONObject().apply {
            put("name", name.trim())
            put("type", type.trim())
            put("parameters", parameters.toJsonObject())
            put("opt", opt)
        }
        return parseConfigStep(rpc("config/create", input.toString()))
    }

    fun updateRemote(
        destId: String,
        config: RcloneDestConfig,
        name: String,
        parameters: Map<String, String>,
        continueState: String? = null,
        continueResult: String? = null,
    ): RcloneConfigStepResult {
        validateRemoteName(name)
        RcloneConfStorage.backupConfBeforeWrite(context, destId, config.confFileName)
        ensureConfigForDest(destId, config)
        val opt = buildConfigOpt(continueState, continueResult)
        val input = JSONObject().apply {
            put("name", name.trim())
            put("parameters", parameters.toJsonObject())
            put("opt", opt)
        }
        return parseConfigStep(rpc("config/update", input.toString()))
    }

    fun getRemoteType(destId: String, config: RcloneDestConfig, name: String): String? {
        ensureConfigForDest(destId, config)
        return try {
            val output = rpc("config/get", JSONObject().put("name", name.trim()).toString())
            output.optString("type", "").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun deleteRemote(destId: String, config: RcloneDestConfig, name: String) {
        validateRemoteName(name)
        RcloneConfStorage.backupConfBeforeWrite(context, destId, config.confFileName)
        ensureConfigForDest(destId, config)
        rpc("config/delete", JSONObject().put("name", name.trim()).toString())
        Log.i(TAG, "rclone remote deleted name=$name (secrets not logged)")
    }

    fun rpc(method: String, inputJson: String): JSONObject {
        ensureInitialized()
        val result = Gomobile.rcloneRPC(method, inputJson)
        val status = result.status
        val output = result.output ?: ""
        if (status != 200L) {
            val safeOutput = redactSecrets(output)
            throw RcloneException("RPC $method failed status=$status: $safeOutput")
        }
        return if (output.isBlank()) JSONObject() else JSONObject(output)
    }

    fun ensureRemoteDir(config: RcloneDestConfig) {
        val fs = config.remoteFs()
        try {
            rpc(
                "operations/mkdir",
                JSONObject().apply {
                    put("fs", fs)
                    put("remote", "")
                }.toString(),
            )
        } catch (e: Exception) {
            Log.d(TAG, "operations/mkdir best-effort for $fs: ${e.message}")
        }
    }

    private fun buildConfigOpt(continueState: String?, continueResult: String?): JSONObject =
        JSONObject().apply {
            put("obscure", true)
            put("noOutput", true)
            put("nonInteractive", true)
            if (!continueState.isNullOrBlank()) put("continue", true)
            if (!continueState.isNullOrBlank()) put("state", continueState)
            if (!continueResult.isNullOrBlank()) put("result", continueResult)
        }

    private fun parseConfigStep(output: JSONObject): RcloneConfigStepResult {
        val state = output.optString("State", output.optString("state", ""))
        val optionObj = output.optJSONObject("Option") ?: output.optJSONObject("option")
        if (optionObj != null && state.isNotBlank()) {
            val question = parseQuestion(state, optionObj, output.optString("Error", ""))
            return RcloneConfigStepResult(complete = false, state = state, question = question)
        }
        return RcloneConfigStepResult(complete = true)
    }

    private fun parseQuestion(state: String, optionObj: JSONObject, error: String): RcloneConfigQuestion {
        val examples = optionObj.optJSONArray("Examples")?.let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val ex = arr.optJSONObject(i) ?: continue
                    add(ex.optString("Value", "") to ex.optString("Help", ""))
                }
            }
        } ?: emptyList()
        return RcloneConfigQuestion(
            state = state,
            name = optionObj.optString("Name", ""),
            help = optionObj.optString("Help", ""),
            defaultValue = optionObj.optString("Default", optionObj.opt("Default")?.toString() ?: ""),
            required = optionObj.optBoolean("Required", false),
            isPassword = optionObj.optBoolean("IsPassword", false),
            exclusive = optionObj.optBoolean("Exclusive", false),
            examples = examples,
            type = optionObj.optString("Type", "string"),
            error = error,
        )
    }

    private fun validateRemoteName(name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Remote name is required" }
        require(!trimmed.contains(':')) { "Remote name cannot contain ':'" }
    }

    private fun Map<String, String>.toJsonObject(): JSONObject {
        val obj = JSONObject()
        forEach { (k, v) -> obj.put(k, v) }
        return obj
    }

    private fun redactSecrets(output: String): String =
        output.replace(Regex("""(?i)(password|secret|token|key)\s*[:=]\s*[^,\s"]+"""), "$1=***")

    companion object {
        private const val TAG = "RcloneRuntime"
    }
}