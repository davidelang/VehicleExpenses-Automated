package com.davidlang.vehicleexpensesautomated.data.email

import com.davidelang.extractmail.Extractmail

/**
 * Multi-vendor autodetect for email fuel receipts (live HTML path).
 *
 * **Type detect SoT:** [Extractmail.detectType] (extractmail AAR).
 * HTML field extraction remains in-app Kotlin ([ShellReceiptParser] /
 * [SamsClubReceiptParser]) until a pure-Kotlin port of host goldens —
 * no Node on device. Offline samples use extractmail golden JSON
 * ([EmailReceiptFixtureIngest]).
 */
object ReceiptParsers {

    data class Meta(
        val messageKey: String? = null,
        val gmailMessageId: String? = null,
        val fromHeader: String? = null,
        val subject: String? = null,
        val emailDateHeader: String? = null,
    )

    /**
     * Detect extractmail type key for logging / future dispatch.
     * @return [Extractmail.TYPE_SHELL], [Extractmail.TYPE_SAMS_CLUB], or null
     */
    fun detectType(html: String?, meta: Meta = Meta()): String? =
        Extractmail.detectType(html, meta.fromHeader, meta.subject)

    fun tryParse(html: String?, meta: Meta = Meta()): ParsedFuelReceipt? {
        if (html.isNullOrBlank()) return null
        val type = Extractmail.detectType(html, meta.fromHeader, meta.subject)
        return when (type) {
            Extractmail.TYPE_SHELL -> ShellReceiptParser.parse(
                html = html,
                messageKey = meta.messageKey,
                gmailMessageId = meta.gmailMessageId,
            )
            Extractmail.TYPE_SAMS_CLUB -> SamsClubReceiptParser.parse(
                html = html,
                messageKey = meta.messageKey,
                gmailMessageId = meta.gmailMessageId,
                fromHeader = meta.fromHeader,
                subject = meta.subject,
                emailDateHeader = meta.emailDateHeader,
            )
            else -> {
                // Fallback self-rejecting tries (unknown brand markers)
                ShellReceiptParser.parse(html, meta.messageKey, meta.gmailMessageId)
                    ?: SamsClubReceiptParser.parse(
                        html = html,
                        messageKey = meta.messageKey,
                        gmailMessageId = meta.gmailMessageId,
                        fromHeader = meta.fromHeader,
                        subject = meta.subject,
                        emailDateHeader = meta.emailDateHeader,
                    )
            }
        }
    }

    /** @deprecated Prefer [Extractmail.looksShell] / [detectType]. */
    fun looksShell(blob: String): Boolean = Extractmail.looksShell(blob)

    /** @deprecated Prefer [Extractmail.looksSamsClub] / [detectType]. */
    fun looksSams(blob: String): Boolean = Extractmail.looksSamsClub(blob)
}
