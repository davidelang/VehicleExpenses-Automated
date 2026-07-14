@file:Suppress("DEPRECATION") // Google Sign-In SDK deprecated; Credential Manager migration is future work.

package com.davidlang.vehicleexpensesautomated.data.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveAuth @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun getSignInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun getLastAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun signInIntent(): Intent = getSignInClient().signInIntent

    fun persistAccountEmail(email: String) {
        prefs().edit().putString(PREFS_DRIVE_ACCOUNT, email).apply()
    }

    fun getPersistedAccountEmail(): String? = prefs().getString(PREFS_DRIVE_ACCOUNT, null)

    fun buildDriveService(account: GoogleSignInAccount): Drive {
        val resolved = resolveAccount(account)
            ?: throw IllegalStateException("No Google account signed in for Drive")
        return buildDriveServiceForAccount(resolved)
    }

    fun buildDriveServiceForAccountName(accountName: String): Drive {
        val resolved = accountFromEmail(accountName)
            ?: throw IllegalStateException("No Google account signed in for Drive")
        return buildDriveServiceForAccount(resolved)
    }

    /** Never pass null account name to GoogleAccountCredential. */
    fun resolveAccount(signInAccount: GoogleSignInAccount?): Account? {
        if (signInAccount == null) return null
        signInAccount.account?.let { acct ->
            if (!acct.name.isNullOrBlank()) return acct
        }
        return accountFromEmail(signInAccount.email)
    }

    fun resolveAccountFromHint(hint: String?): Account? {
        if (!hint.isNullOrBlank()) return accountFromEmail(hint)
        resolveAccount(getLastAccount())?.let { return it }
        return accountFromEmail(getPersistedAccountEmail())
    }

    private fun accountFromEmail(email: String?): Account? {
        val trimmed = email?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return Account(trimmed, GOOGLE_ACCOUNT_TYPE)
    }

    private fun buildDriveServiceForAccount(account: Account): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DRIVE_SCOPE))
        credential.selectedAccount = account
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("VehicleExpenses-Automated")
            .build()
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "vehicle_settings"
        const val PREFS_DRIVE_ACCOUNT = "drive_account_name"
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}