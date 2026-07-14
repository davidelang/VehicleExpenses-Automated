package com.davidlang.vehicleexpensesautomated.data.sync

import android.accounts.Account
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsAuth @Inject constructor(
    private val legacy: GoogleLegacySignIn,
) {
    fun getSignInClient(): GoogleSignInClient = legacy.signInClient(SHEETS_SCOPE)

    fun getLastAccount(): GoogleSignInAccount? = legacy.lastAccount()

    fun signInIntent(): Intent = getSignInClient().signInIntent

    fun parseSignInResult(data: Intent?): GoogleSignInAccount = legacy.parseSignInResult(data)

    fun persistAccountEmail(email: String) = legacy.persistEmail(PREFS_SHEETS_ACCOUNT, email)

    fun getPersistedAccountEmail(): String? = legacy.persistedEmail(PREFS_SHEETS_ACCOUNT)

    fun buildSheetsService(account: GoogleSignInAccount): Sheets {
        val resolved = legacy.resolveAccount(account)
            ?: throw IllegalStateException("No Google account signed in for Sheets")
        return buildSheetsServiceForAccount(resolved)
    }

    fun buildSheetsServiceForAccountName(accountName: String): Sheets {
        val resolved = legacy.accountFromEmail(accountName)
            ?: throw IllegalStateException("No Google account signed in for Sheets")
        return buildSheetsServiceForAccount(resolved)
    }

    fun resolveAccount(signInAccount: GoogleSignInAccount?): Account? = legacy.resolveAccount(signInAccount)

    fun resolveAccountFromHint(hint: String?): Account? =
        legacy.resolveAccountFromHint(hint, PREFS_SHEETS_ACCOUNT)

    private fun buildSheetsServiceForAccount(account: Account): Sheets {
        val credential = legacy.oauthCredential(SHEETS_SCOPE, account)
        return Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("VehicleExpenses-Automated")
            .build()
    }

    companion object {
        const val PREFS_SHEETS_ACCOUNT = "sheets_account_name"
        const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
        /** Origin device id assigned to human-created sheet rows without Origin Device ID. */
        const val SHEET_IMPORT_ORIGIN = "sheet-import"
    }
}