package com.davidlang.vehicleexpensesautomated.data.email

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Stable Sync ID helpers for email intake.
 * Matches sandbox `lib/parsed-fuel-receipt.js` (SHA-1 name UUID, v5 layout).
 */
object EmailReceiptIds {
    const val UNASSIGNED_VEHICLE_ID = 0
    const val UNASSIGNED_VEHICLE_SYNC_ID = "a0000000-0000-4000-8000-000000000001"
    const val ORIGIN_ANDROID_EMAIL_POLLER = "android-email-poller"
    private const val NS = "email|receipt|v1|"

    fun syncIdForGmailMessage(messageId: String): String =
        uuidFromName("email|gmail|${messageId.trim()}")

    fun syncIdForShellFallback(siteId: String?, timestampLocal: String?, timestampMs: Long): String {
        val site = siteId?.takeIf { it.isNotBlank() } ?: "unknown"
        val local = timestampLocal?.takeIf { it.isNotBlank() } ?: timestampMs.toString()
        return uuidFromName("email|shell|$site|$local")
    }

    fun syncIdFor(parsed: ParsedFuelReceipt, gmailMessageId: String?): String {
        val mid = gmailMessageId?.trim().orEmpty()
        if (mid.isNotEmpty()) return syncIdForGmailMessage(mid)
        return syncIdForShellFallback(parsed.siteId, parsed.timestampLocal, parsed.timestampMs)
    }

    fun uuidFromName(name: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest((NS + name).toByteArray(StandardCharsets.UTF_8))
        val bytes = digest.copyOf(16)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte() // version 5
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte() // variant
        return buildString(36) {
            for (i in 0 until 16) {
                if (i == 4 || i == 6 || i == 8 || i == 10) append('-')
                append(String.format("%02x", bytes[i]))
            }
        }
    }
}
