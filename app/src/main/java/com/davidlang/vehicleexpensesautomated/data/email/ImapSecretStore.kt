package com.davidlang.vehicleexpensesautomated.data.email

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores IMAP password / app-password only. Never log values.
 * Falls back to plain private prefs if encrypted prefs cannot be created
 * (still MODE_PRIVATE; not ideal but fail-soft for sideload).
 */
class ImapSecretStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createPrefs(appContext)

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    fun clear() {
        prefs.edit().remove(KEY_PASSWORD).apply()
    }

    companion object {
        private const val TAG = "ImapSecretStore"
        private const val FILE = "email_receipt_imap_secrets"
        private const val KEY_PASSWORD = "imap_password"

        private fun createPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                Log.w(TAG, "EncryptedSharedPreferences unavailable; using private prefs")
                context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            }
        }
    }
}
