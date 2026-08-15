@file:Suppress("DEPRECATION")

package com.davidlang.vehicleexpensesautomated.data.sync

import android.accounts.Account
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveAuth @Inject constructor(
    private val legacy: GoogleLegacySignIn,
) {
    fun getSignInClient(): GoogleSignInClient = legacy.signInClient(DRIVE_SCOPE)

    /** Sign-in that adds [DRIVE_READONLY_SCOPE] for hybrid browse (all + shared files). */
    fun getReadonlyBrowseSignInClient(): GoogleSignInClient =
        legacy.signInClient(listOf(DRIVE_SCOPE, DRIVE_READONLY_SCOPE))

    fun readonlyBrowseSignInIntent(): Intent = getReadonlyBrowseSignInClient().signInIntent

    fun hasReadonlyBrowseScope(): Boolean = legacy.hasScope(DRIVE_READONLY_SCOPE)

    fun getLastAccount(): GoogleSignInAccount? = legacy.lastAccount()

    fun signInIntent(): Intent = getSignInClient().signInIntent

    fun parseSignInResult(data: Intent?): GoogleSignInAccount = legacy.parseSignInResult(data)

    fun persistAccountEmail(email: String) = legacy.persistEmail(PREFS_DRIVE_ACCOUNT, email)

    fun getPersistedAccountEmail(): String? = legacy.persistedEmail(PREFS_DRIVE_ACCOUNT)

    fun buildDriveService(account: GoogleSignInAccount): Drive {
        val resolved = legacy.resolveAccount(account)
            ?: throw IllegalStateException("No Google account signed in for Drive")
        return buildDriveServiceForAccount(resolved)
    }

    fun buildDriveServiceForAccountName(accountName: String): Drive {
        val resolved = legacy.accountFromEmail(accountName)
            ?: throw IllegalStateException("No Google account signed in for Drive")
        return buildDriveServiceForAccount(resolved)
    }

    fun resolveAccount(signInAccount: GoogleSignInAccount?): Account? = legacy.resolveAccount(signInAccount)

    fun resolveAccountFromHint(hint: String?): Account? =
        legacy.resolveAccountFromHint(hint, PREFS_DRIVE_ACCOUNT)

    fun buildDriveServiceReadOnlyForAccountName(accountName: String): Drive {
        val resolved = legacy.accountFromEmail(accountName)
            ?: throw IllegalStateException("No Google account signed in for Drive")
        return buildDriveServiceReadOnlyForAccount(resolved)
    }

    private fun buildDriveServiceForAccount(account: Account): Drive {
        val credential = legacy.oauthCredential(DRIVE_SCOPE, account)
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("VehicleExpenses-Automated")
            .build()
    }

    private fun buildDriveServiceReadOnlyForAccount(account: Account): Drive {
        val credential = legacy.oauthCredential(DRIVE_READONLY_SCOPE, account)
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("VehicleExpenses-Automated")
            .build()
    }

    companion object {
        const val PREFS_DRIVE_ACCOUNT = "drive_account_name"
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
    }
}