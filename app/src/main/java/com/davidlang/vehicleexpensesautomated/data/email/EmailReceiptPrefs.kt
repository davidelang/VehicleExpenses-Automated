package com.davidlang.vehicleexpensesautomated.data.email

import android.content.Context

/**
 * Prefs for in-app email receipt pollers (Gmail OAuth + generic IMAP).
 * IMAP password is stored only via [ImapSecretStore], not here.
 */
class EmailReceiptPrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secrets = ImapSecretStore(appContext)

    /** Master enable for scheduled / poll-now work. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /**
     * Which backends to poll: [SOURCE_GMAIL], [SOURCE_IMAP], or [SOURCE_BOTH].
     */
    var source: String
        get() = prefs.getString(KEY_SOURCE, SOURCE_GMAIL)?.trim().orEmpty().ifBlank { SOURCE_GMAIL }
        set(value) = prefs.edit().putString(KEY_SOURCE, value.trim()).apply()

    fun useGmail(): Boolean =
        source == SOURCE_GMAIL || source == SOURCE_BOTH ||
            (source != SOURCE_IMAP && enabled && !imapEnabled)

    fun useImap(): Boolean =
        source == SOURCE_IMAP || source == SOURCE_BOTH || imapEnabled

    /** Gmail label name only (not full mailbox). */
    var labelName: String
        get() = prefs.getString(KEY_LABEL, DEFAULT_LABEL)?.trim().orEmpty()
        set(value) = prefs.edit().putString(KEY_LABEL, value.trim()).apply()

    var accountEmail: String?
        get() = prefs.getString(KEY_ACCOUNT, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_ACCOUNT, value?.trim()).apply()

    var imapEnabled: Boolean
        get() = prefs.getBoolean(KEY_IMAP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_IMAP_ENABLED, value).apply()

    var imapHost: String
        get() = prefs.getString(KEY_IMAP_HOST, "")?.trim().orEmpty()
        set(value) = prefs.edit().putString(KEY_IMAP_HOST, value.trim()).apply()

    var imapPort: Int
        get() = prefs.getInt(KEY_IMAP_PORT, DEFAULT_IMAP_PORT).let { if (it <= 0) DEFAULT_IMAP_PORT else it }
        set(value) = prefs.edit().putInt(KEY_IMAP_PORT, value.coerceIn(1, 65535)).apply()

    var imapUsername: String
        get() = prefs.getString(KEY_IMAP_USER, "")?.trim().orEmpty()
        set(value) = prefs.edit().putString(KEY_IMAP_USER, value.trim()).apply()

    var imapPassword: String
        get() = secrets.password
        set(value) {
            secrets.password = value
        }

    /** IMAP folder / mailbox name, e.g. INBOX or Receipts. */
    var imapFolder: String
        get() = prefs.getString(KEY_IMAP_FOLDER, DEFAULT_IMAP_FOLDER)?.trim().orEmpty()
            .ifBlank { DEFAULT_IMAP_FOLDER }
        set(value) = prefs.edit().putString(KEY_IMAP_FOLDER, value.trim()).apply()

    var lastRunSummary: String
        get() = prefs.getString(KEY_LAST_SUMMARY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SUMMARY, value).apply()

    var lastRunAtMs: Long
        get() = prefs.getLong(KEY_LAST_RUN_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RUN_AT, value).apply()

    companion object {
        const val PREFS_NAME = "vehicle_settings"
        const val KEY_ENABLED = "email_receipt_poll_enabled"
        const val KEY_SOURCE = "email_receipt_source"
        const val KEY_LABEL = "email_receipt_gmail_label"
        const val KEY_ACCOUNT = "email_receipt_gmail_account"
        const val KEY_IMAP_ENABLED = "email_receipt_imap_enabled"
        const val KEY_IMAP_HOST = "email_receipt_imap_host"
        const val KEY_IMAP_PORT = "email_receipt_imap_port"
        const val KEY_IMAP_USER = "email_receipt_imap_user"
        const val KEY_IMAP_FOLDER = "email_receipt_imap_folder"
        const val KEY_LAST_SUMMARY = "email_receipt_last_summary"
        const val KEY_LAST_RUN_AT = "email_receipt_last_run_at"
        const val DEFAULT_LABEL = "VehicleExpenses/ShellReceipts"
        const val DEFAULT_IMAP_FOLDER = "INBOX"
        const val DEFAULT_IMAP_PORT = 993
        const val SOURCE_GMAIL = "gmail"
        const val SOURCE_IMAP = "imap"
        const val SOURCE_BOTH = "both"
        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    }
}
