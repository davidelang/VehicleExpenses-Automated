package com.davidlang.vehicleexpensesautomated.data.email

/**
 * Normalized mail message for Gmail or IMAP → parse/ingest.
 * [stableId] is used for Sync ID derivation (Gmail API id, RFC Message-ID, or folder+UID).
 */
data class EmailReceiptMessage(
    val stableId: String,
    val from: String,
    val subject: String,
    val dateHeader: String,
    val htmlOrTextBody: String,
    val provider: String,
)
