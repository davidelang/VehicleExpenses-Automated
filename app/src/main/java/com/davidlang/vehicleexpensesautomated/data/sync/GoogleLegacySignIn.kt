@file:Suppress("DEPRECATION")

package com.davidlang.vehicleexpensesautomated.data.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single suppression boundary for deprecated Google Sign-In SDK APIs
 * (`@file:Suppress("DEPRECATION")`). Credential Manager migration will replace this adapter.
 */
@Singleton
class GoogleLegacySignIn @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @Suppress("DEPRECATION")
    fun signInClient(oauthScope: String): GoogleSignInClient =
        signInClient(listOf(oauthScope))

    @Suppress("DEPRECATION")
    fun signInClient(oauthScopes: List<String>): GoogleSignInClient {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
        oauthScopes.distinct().forEach { builder.requestScopes(Scope(it)) }
        return GoogleSignIn.getClient(context, builder.build())
    }

    @Suppress("DEPRECATION")
    fun hasScope(scope: String): Boolean {
        val account = lastAccount() ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(scope))
    }

    @Suppress("DEPRECATION")
    fun lastAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    @Suppress("DEPRECATION")
    fun parseSignInResult(data: Intent?): GoogleSignInAccount {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return task.getResult(ApiException::class.java)
    }

    fun persistEmail(prefsKey: String, email: String) {
        prefs().edit().putString(prefsKey, email).apply()
    }

    fun persistedEmail(prefsKey: String): String? = prefs().getString(prefsKey, null)

    /** Never pass null account name to [GoogleAccountCredential]. */
    @Suppress("DEPRECATION")
    fun resolveAccount(signInAccount: GoogleSignInAccount?): Account? {
        if (signInAccount == null) return null
        signInAccount.account?.let { acct ->
            if (!acct.name.isNullOrBlank()) return acct
        }
        return accountFromEmail(signInAccount.email)
    }

    @Suppress("DEPRECATION")
    fun resolveAccountFromHint(hint: String?, prefsKey: String): Account? {
        if (!hint.isNullOrBlank()) return accountFromEmail(hint)
        resolveAccount(lastAccount())?.let { return it }
        return accountFromEmail(persistedEmail(prefsKey))
    }

    fun accountFromEmail(email: String?): Account? {
        val trimmed = email?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return Account(trimmed, GOOGLE_ACCOUNT_TYPE)
    }

    fun oauthCredential(oauthScope: String, account: Account): GoogleAccountCredential {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(oauthScope))
        credential.selectedAccount = account
        return credential
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "vehicle_settings"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}