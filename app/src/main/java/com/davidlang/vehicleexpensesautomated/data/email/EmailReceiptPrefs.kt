package com.davidlang.vehicleexpensesautomated.data.email

import android.content.Context

/**
 * Prefs for in-app Gmail email receipt poller (vehicle_settings).
 */
class EmailReceiptPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Gmail label name only (not full mailbox). */
    var labelName: String
        get() = prefs.getString(KEY_LABEL, DEFAULT_LABEL)?.trim().orEmpty()
        set(value) = prefs.edit().putString(KEY_LABEL, value.trim()).apply()

    var accountEmail: String?
        get() = prefs.getString(KEY_ACCOUNT, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_ACCOUNT, value?.trim()).apply()

    var lastRunSummary: String
        get() = prefs.getString(KEY_LAST_SUMMARY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SUMMARY, value).apply()

    var lastRunAtMs: Long
        get() = prefs.getLong(KEY_LAST_RUN_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RUN_AT, value).apply()

    companion object {
        const val PREFS_NAME = "vehicle_settings"
        const val KEY_ENABLED = "email_receipt_poll_enabled"
        const val KEY_LABEL = "email_receipt_gmail_label"
        const val KEY_ACCOUNT = "email_receipt_gmail_account"
        const val KEY_LAST_SUMMARY = "email_receipt_last_summary"
        const val KEY_LAST_RUN_AT = "email_receipt_last_run_at"
        const val DEFAULT_LABEL = "VehicleExpenses/ShellReceipts"
        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    }
}
