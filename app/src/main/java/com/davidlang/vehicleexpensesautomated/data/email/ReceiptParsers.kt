package com.davidlang.vehicleexpensesautomated.data.email

import java.util.Locale

/**
 * Multi-vendor autodetect for email fuel receipts.
 * Order: Shell exclusive markers → Sam's Club → null.
 */
object ReceiptParsers {

    data class Meta(
        val messageKey: String? = null,
        val gmailMessageId: String? = null,
        val fromHeader: String? = null,
        val subject: String? = null,
        val emailDateHeader: String? = null,
    )

    fun tryParse(html: String?, meta: Meta = Meta()): ParsedFuelReceipt? {
        if (html.isNullOrBlank()) return null
        val blob = listOf(html, meta.fromHeader.orEmpty(), meta.subject.orEmpty())
            .joinToString("\n")
            .lowercase(Locale.US)

        if (looksShell(blob)) {
            return ShellReceiptParser.parse(
                html = html,
                messageKey = meta.messageKey,
                gmailMessageId = meta.gmailMessageId,
            )
        }
        if (looksSams(blob)) {
            return SamsClubReceiptParser.parse(
                html = html,
                messageKey = meta.messageKey,
                gmailMessageId = meta.gmailMessageId,
                fromHeader = meta.fromHeader,
                subject = meta.subject,
                emailDateHeader = meta.emailDateHeader,
            )
        }
        // Fallback self-rejecting tries
        ShellReceiptParser.parse(html, meta.messageKey, meta.gmailMessageId)?.let { return it }
        return SamsClubReceiptParser.parse(
            html = html,
            messageKey = meta.messageKey,
            gmailMessageId = meta.gmailMessageId,
            fromHeader = meta.fromHeader,
            subject = meta.subject,
            emailDateHeader = meta.emailDateHeader,
        )
    }

    fun looksShell(blob: String): Boolean =
        blob.contains("ereceiptshell") ||
            blob.contains("mail.ereceiptshell.com") ||
            blob.contains("shell e-receipt") ||
            (blob.contains("welcome to shell") && blob.contains("amount paid"))

    fun looksSams(blob: String): Boolean {
        if (blob.contains("ereceiptshell")) return false
        return blob.contains("samsclub.com") ||
            blob.contains("sam's club fuel") ||
            blob.contains("fuel station receipt") ||
            (blob.contains("sam's club") && blob.contains("total paid"))
    }
}
