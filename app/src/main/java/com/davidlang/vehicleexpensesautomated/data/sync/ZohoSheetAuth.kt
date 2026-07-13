package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.net.Uri
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.ZohoSheetConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ZohoAuthResult(
    val accessToken: String,
    val refreshToken: String = "",
    val apiDomain: String = ZohoSheetConfig.DEFAULT_API_DOMAIN,
    val accountsServer: String = ZohoSheetConfig.DEFAULT_ACCOUNTS_SERVER,
)

@Singleton
class ZohoSheetAuth @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val pendingLock = Any()
    private var pendingContinuation: ((Result<ZohoAuthResult>) -> Unit)? = null

    fun buildAuthorizationUrl(clientId: String, accountsServer: String = ZohoSheetConfig.DEFAULT_ACCOUNTS_SERVER): String {
        val state = "ve_${System.currentTimeMillis()}"
        val base = accountsServer.trimEnd('/')
        return buildString {
            append("$base/oauth/v2/auth?")
            append("response_type=token")
            append("&client_id=${URLEncoder.encode(clientId.trim(), Charsets.UTF_8.name())}")
            append("&scope=${URLEncoder.encode(ZohoSheetConfig.OAUTH_SCOPES, Charsets.UTF_8.name())}")
            append("&redirect_uri=${URLEncoder.encode(ZohoSheetConfig.REDIRECT_URI, Charsets.UTF_8.name())}")
            append("&state=${URLEncoder.encode(state, Charsets.UTF_8.name())}")
        }
    }

    suspend fun awaitRedirectResult(timeoutMs: Long = 120_000): ZohoAuthResult =
        suspendCancellableCoroutine { cont ->
            synchronized(pendingLock) {
                pendingContinuation = { result ->
                    if (cont.isActive) {
                        result.fold(
                            onSuccess = { cont.resume(it) },
                            onFailure = { cont.resumeWithException(it) },
                        )
                    }
                }
            }
            cont.invokeOnCancellation {
                synchronized(pendingLock) {
                    if (pendingContinuation != null) pendingContinuation = null
                }
            }
        }

    fun deliverRedirectUri(uri: Uri?) {
        val callback = synchronized(pendingLock) {
            val current = pendingContinuation
            pendingContinuation = null
            current
        } ?: return
        if (uri == null) {
            callback(Result.failure(IllegalStateException("Zoho sign-in cancelled")))
            return
        }
        val fragment = uri.fragment.orEmpty()
        val params = parseFragment(fragment)
        val error = params["error"]
        if (!error.isNullOrBlank()) {
            callback(Result.failure(IllegalStateException("Zoho sign-in failed: $error")))
            return
        }
        val accessToken = params["access_token"].orEmpty()
        if (accessToken.isBlank()) {
            callback(Result.failure(IllegalStateException("Zoho sign-in did not return an access token")))
            return
        }
        val apiDomain = params["api_domain"]?.let { Uri.decode(it) }
            ?: ZohoSheetConfig.DEFAULT_API_DOMAIN
        val location = params["location"].orEmpty()
        val accountsServer = when (location.lowercase()) {
            "eu" -> "https://accounts.zoho.eu"
            "in" -> "https://accounts.zoho.in"
            "au" -> "https://accounts.zoho.com.au"
            "jp" -> "https://accounts.zoho.jp"
            "ca" -> "https://accounts.zohocloud.ca"
            else -> ZohoSheetConfig.DEFAULT_ACCOUNTS_SERVER
        }
        callback(
            Result.success(
                ZohoAuthResult(
                    accessToken = accessToken,
                    apiDomain = apiDomain,
                    accountsServer = accountsServer,
                ),
            ),
        )
    }

    suspend fun refreshAccessToken(config: ZohoSheetConfig): ZohoAuthResult? = withContext(Dispatchers.IO) {
        if (config.refreshToken.isNotBlank() && config.clientId.isNotBlank() && config.clientSecret.isNotBlank()) {
            refreshWithRefreshToken(config)?.let { return@withContext it }
        }
        refreshWithSessionGrant(config)
    }

    private fun refreshWithRefreshToken(config: ZohoSheetConfig): ZohoAuthResult? {
        val base = config.accountsServer.trimEnd('/')
        val query = buildString {
            append("refresh_token=${URLEncoder.encode(config.refreshToken, Charsets.UTF_8.name())}")
            append("&grant_type=refresh_token")
            append("&client_id=${URLEncoder.encode(config.clientId, Charsets.UTF_8.name())}")
            append("&client_secret=${URLEncoder.encode(config.clientSecret, Charsets.UTF_8.name())}")
        }
        val response = postForm("$base/oauth/v2/token?$query")
        val accessToken = response.optString("access_token", "")
        if (accessToken.isBlank()) return null
        return ZohoAuthResult(
            accessToken = accessToken,
            refreshToken = config.refreshToken,
            apiDomain = response.optString("api_domain", config.apiDomain).ifBlank { config.apiDomain },
            accountsServer = config.accountsServer,
        )
    }

    private fun refreshWithSessionGrant(config: ZohoSheetConfig): ZohoAuthResult? {
        if (config.clientId.isBlank()) return null
        val base = config.accountsServer.trimEnd('/')
        val state = "ve_refresh_${System.currentTimeMillis()}"
        val query = buildString {
            append("client_id=${URLEncoder.encode(config.clientId, Charsets.UTF_8.name())}")
            append("&response_type=token")
            append("&scope=${URLEncoder.encode(ZohoSheetConfig.OAUTH_SCOPES, Charsets.UTF_8.name())}")
            append("&redirect_uri=${URLEncoder.encode(ZohoSheetConfig.REDIRECT_URI, Charsets.UTF_8.name())}")
            append("&state=${URLEncoder.encode(state, Charsets.UTF_8.name())}")
        }
        // Session refresh is browser-only; return null so caller can prompt interactive sign-in.
        return null
    }

    private fun postForm(url: String): JSONObject = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            JSONObject(body.ifBlank { "{}" })
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        JSONObject()
    }

    private fun parseFragment(fragment: String): Map<String, String> {
        if (fragment.isBlank()) return emptyMap()
        return fragment.split('&').mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size == 2) {
                Uri.decode(pieces[0]) to Uri.decode(pieces[1])
            } else {
                null
            }
        }.toMap()
    }

}