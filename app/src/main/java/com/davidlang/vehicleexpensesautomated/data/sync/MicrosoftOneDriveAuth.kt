package com.davidlang.vehicleexpensesautomated.data.sync

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import com.davidlang.vehicleexpensesautomated.R
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class MicrosoftAuthResult(
    val email: String,
    val accessToken: String,
    val tokenJsonForRclone: String,
    val driveId: String,
    val driveType: String,
)

@Singleton
class MicrosoftOneDriveAuth @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val initLock = Any()
    private var clientApp: IMultipleAccountPublicClientApplication? = null

    fun persistAccountEmail(email: String) {
        prefs().edit().putString(PREFS_ONEDRIVE_ACCOUNT, email.trim()).apply()
    }

    fun getPersistedAccountEmail(): String? =
        prefs().getString(PREFS_ONEDRIVE_ACCOUNT, null)?.takeIf { it.isNotBlank() }

    /** Hints for MSAL account picker — never reads passwords. */
    fun deviceMicrosoftAccountHints(): List<String> {
        return try {
            val am = AccountManager.get(context)
            val types = listOf(
                "com.microsoft.workaccount",
                "com.microsoft.skype.raima",
                "com.microsoft.msa.auth",
            )
            buildList {
                for (type in types) {
                    am.getAccountsByType(type).forEach { acct ->
                        val name = acct.name?.trim().orEmpty()
                        if (name.isNotBlank() && name !in this) add(name)
                    }
                }
            }.sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun ensureClient(): IMultipleAccountPublicClientApplication = withContext(Dispatchers.Main) {
        synchronized(initLock) {
            clientApp?.let { return@withContext it }
        }
        suspendCancellableCoroutine { cont ->
            PublicClientApplication.createMultipleAccountPublicClientApplication(
                context,
                R.raw.msal_auth_config,
                object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                    override fun onCreated(application: IMultipleAccountPublicClientApplication) {
                        synchronized(initLock) { clientApp = application }
                        cont.resume(application)
                    }

                    override fun onError(exception: MsalException) {
                        cont.resumeWithException(
                            IllegalStateException("Microsoft sign-in unavailable: ${exception.message}"),
                        )
                    }
                },
            )
        }
    }

    suspend fun signInInteractive(activity: Activity, loginHint: String? = null): MicrosoftAuthResult =
        withContext(Dispatchers.Main) {
            val app = ensureClient()
            val result = suspendCancellableCoroutine<IAuthenticationResult> { cont ->
                val params = AcquireTokenParameters.Builder()
                    .startAuthorizationFromActivity(activity)
                    .withScopes(SCOPES)
                    .withCallback(object : com.microsoft.identity.client.AuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            cont.resume(authenticationResult)
                        }

                        override fun onError(exception: MsalException) {
                            cont.resumeWithException(
                                IllegalStateException("Microsoft sign-in failed: ${exception.message}"),
                            )
                        }

                        override fun onCancel() {
                            cont.resumeWithException(IllegalStateException("Microsoft sign-in cancelled"))
                        }
                    })
                loginHint?.trim()?.takeIf { it.isNotBlank() }?.let { params.withLoginHint(it) }
                app.acquireToken(params.build())
            }
            buildAuthResult(result)
        }

    suspend fun refreshSilent(email: String? = null): MicrosoftAuthResult? = withContext(Dispatchers.IO) {
        val app = ensureClient()
        val account = resolveAccount(app, email) ?: return@withContext null
        val result = suspendCancellableCoroutine<IAuthenticationResult> { cont ->
            val params = AcquireTokenSilentParameters.Builder()
                .forAccount(account)
                .fromAuthority(account.authority)
                .withScopes(SCOPES)
                .withCallback(object : SilentAuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        cont.resume(authenticationResult)
                    }

                    override fun onError(exception: MsalException) {
                        cont.resumeWithException(exception)
                    }
                })
                .build()
            app.acquireTokenSilentAsync(params)
        }
        buildAuthResult(result)
    }

    private suspend fun buildAuthResult(result: IAuthenticationResult): MicrosoftAuthResult {
        val email = result.account.username?.trim().orEmpty()
        if (email.isBlank()) throw IllegalStateException("Microsoft account has no email")
        persistAccountEmail(email)
        val drive = fetchDefaultDrive(result.accessToken)
        val tokenJson = rcloneTokenJson(result)
        return MicrosoftAuthResult(
            email = email,
            accessToken = result.accessToken,
            tokenJsonForRclone = tokenJson,
            driveId = drive.first,
            driveType = drive.second,
        )
    }

    private fun resolveAccount(app: IMultipleAccountPublicClientApplication, email: String?): IAccount? {
        val target = email?.trim()?.takeIf { it.isNotBlank() } ?: getPersistedAccountEmail()
        val accounts = try {
            app.accounts
        } catch (_: Exception) {
            emptyList()
        }
        if (target.isNullOrBlank()) return accounts.firstOrNull()
        return accounts.firstOrNull { acct -> acct.username.equals(target, ignoreCase = true) }
    }

    private fun rcloneTokenJson(result: IAuthenticationResult): String {
        val expiry = result.expiresOn?.let { formatRfc3339(it) }.orEmpty()
        return JSONObject().apply {
            put("access_token", result.accessToken)
            put("token_type", "Bearer")
            if (expiry.isNotBlank()) put("expiry", expiry)
        }.toString()
    }

    private fun formatRfc3339(date: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(date)
    }

    private suspend fun fetchDefaultDrive(accessToken: String): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val conn = (URL(GRAPH_DRIVE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            try {
                val code = conn.responseCode
                val body = if (code in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText().orEmpty()
                }
                if (code !in 200..299) {
                    throw IllegalStateException("Could not read OneDrive drive info (HTTP $code)")
                }
                val obj = JSONObject(body)
                val driveId = obj.optString("id", "").trim()
                if (driveId.isBlank()) throw IllegalStateException("OneDrive drive id missing")
                val driveType = obj.optString("driveType", "personal").ifBlank { "personal" }
                driveId to driveType
            } finally {
                conn.disconnect()
            }
        }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "vehicle_settings"
        const val PREFS_ONEDRIVE_ACCOUNT = "onedrive_account_name"
        private const val GRAPH_DRIVE_URL = "https://graph.microsoft.com/v1.0/me/drive"
        val SCOPES = listOf(
            "https://graph.microsoft.com/Files.ReadWrite",
            "https://graph.microsoft.com/User.Read",
        )
    }
}