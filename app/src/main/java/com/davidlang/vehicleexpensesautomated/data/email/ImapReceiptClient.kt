package com.davidlang.vehicleexpensesautomated.data.email

import android.util.Log
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Generic IMAPS folder fetch for email fuel receipts.
 * TLS required (default port 993). Never logs password.
 */
@Singleton
class ImapReceiptClient @Inject constructor() {

    data class FetchResult(
        val messages: List<EmailReceiptMessage>,
        val errorSummary: String? = null,
        val skippedCount: Int = 0,
        val skipReasons: List<String> = emptyList(),
    )

    /**
     * Open folder read-only (EXAMINE), fetch last [maxResults] messages.
     */
    fun listFolderMessages(
        host: String,
        port: Int,
        username: String,
        password: String,
        folderName: String,
        maxResults: Int = 25,
    ): FetchResult {
        val h = host.trim()
        val user = username.trim()
        val folder = folderName.trim().ifBlank { "INBOX" }
        if (h.isEmpty() || user.isEmpty()) {
            return FetchResult(emptyList(), "imap: host/username required")
        }
        if (password.isEmpty()) {
            return FetchResult(emptyList(), "imap: password required")
        }
        if (port <= 0) {
            return FetchResult(emptyList(), "imap: invalid port")
        }

        var store: Store? = null
        var mailFolder: Folder? = null
        return try {
            val props = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", h)
                put("mail.imaps.port", port.toString())
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.ssl.trust", "*")
                put("mail.imaps.connectiontimeout", "30000")
                put("mail.imaps.timeout", "60000")
            }
            val session = Session.getInstance(props)
            store = session.getStore("imaps")
            store.connect(h, port, user, password)
            mailFolder = store.getFolder(folder)
            if (mailFolder == null || !mailFolder.exists()) {
                return FetchResult(emptyList(), "imap: folder not found: $folder")
            }
            mailFolder.open(Folder.READ_ONLY)
            val total = mailFolder.messageCount
            if (total <= 0) {
                return FetchResult(emptyList(), null)
            }
            val start = maxOf(1, total - maxResults + 1)
            val msgs = mailFolder.getMessages(start, total)
            // Fetch envelope + content for range
            val out = mutableListOf<EmailReceiptMessage>()
            val skipReasons = mutableListOf<String>()
            for (msg in msgs.reversed()) {
                try {
                    out.add(toReceiptMessage(msg, folder))
                // kept: failure stays on this call so the screen or caller can continue
                } catch (e: Exception) {
                    skipReasons.add(shortSkipReason(e))
                    Log.w(TAG, "imap message skip: ${e.javaClass.simpleName}")
                }
            }
            FetchResult(
                out,
                null,
                skippedCount = skipReasons.size,
                skipReasons = skipReasons.toList(),
            )
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            val kind = e.javaClass.simpleName
            val brief = (e.message ?: kind).take(100).replace(Regex("(?i)password|passwd|secret"), "[redacted]")
            Log.w(TAG, "imap fetch failed: $kind")
            FetchResult(emptyList(), "imap: $kind: $brief")
        } finally {
            try {
                if (mailFolder != null && mailFolder.isOpen) mailFolder.close(false)
            // kept: failure stays on this call so the screen or caller can continue
            } catch (_: Exception) {
            }
            try {
                store?.close()
            // kept: failure stays on this call so the screen or caller can continue
            } catch (_: Exception) {
            }
        }
    }

    private fun toReceiptMessage(msg: Message, folderName: String): EmailReceiptMessage {
        val mime = msg as? MimeMessage
        val messageId = mime?.messageID?.trim()?.takeIf { it.isNotEmpty() }
        val uid = try {
            // UIDFolder when available
            val uf = msg.folder as? javax.mail.UIDFolder
            uf?.getUID(msg)?.takeIf { it > 0 }?.toString()
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            Log.e(TAG, "imap uid unreadable", e)
            null
        }
        val stableId = when {
            !messageId.isNullOrBlank() -> messageId
            !uid.isNullOrBlank() -> "$folderName|$uid"
            else -> "$folderName|seq|${msg.messageNumber}"
        }
        val from = msg.from?.firstOrNull()?.let { addr ->
            (addr as? InternetAddress)?.toUnicodeString() ?: addr.toString()
        }.orEmpty()
        val subject = msg.subject.orEmpty()
        val dateHeader = mime?.getHeader("Date")?.firstOrNull().orEmpty().ifBlank {
            msg.sentDate?.toString().orEmpty()
        }
        val body = extractHtmlOrText(msg)
        return EmailReceiptMessage(
            stableId = stableId,
            from = from,
            subject = subject,
            dateHeader = dateHeader,
            htmlOrTextBody = body,
            provider = "imap",
        )
    }

    private fun extractHtmlOrText(part: Part, topLevel: Boolean = true): String {
        val content = try {
            part.content
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            if (topLevel) throw e
            Log.w(TAG, "content read failed: ${e.javaClass.simpleName}")
            return ""
        }
        try {
            when {
                part.isMimeType("text/html") -> return content?.toString().orEmpty()
                part.isMimeType("text/plain") -> {
                    // Prefer HTML from multipart; keep plain as fallback
                    return content?.toString().orEmpty()
                }
                content is Multipart -> {
                    var html: String? = null
                    var plain: String? = null
                    val mp = content
                    for (i in 0 until mp.count) {
                        try {
                            val bp = mp.getBodyPart(i)
                            when {
                                bp.isMimeType("text/html") && html == null ->
                                    html = extractHtmlOrText(bp, topLevel = false)
                                bp.isMimeType("text/plain") && plain == null ->
                                    plain = extractHtmlOrText(bp, topLevel = false)
                                bp.isMimeType("multipart/*") -> {
                                    val nested = extractHtmlOrText(bp, topLevel = false)
                                    if (nested.contains("<html", ignoreCase = true) || nested.contains("<body", ignoreCase = true)) {
                                        if (html == null) html = nested
                                    } else if (plain == null && nested.isNotBlank()) {
                                        plain = nested
                                    }
                                }
                            }
                        // kept: failure stays on this call so the screen or caller can continue
                        } catch (e: Exception) {
                            Log.w(TAG, "nested part skip: ${e.javaClass.simpleName}")
                        }
                    }
                    return html?.takeIf { it.isNotBlank() } ?: plain.orEmpty()
                }
                else -> return content?.toString().orEmpty()
            }
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            if (topLevel) throw e
            Log.w(TAG, "content read failed: ${e.javaClass.simpleName}")
            return ""
        }
    }

    private fun shortSkipReason(e: Exception): String {
        val message = (e.message ?: "")
            .replace(Regex("(?i)password|passwd|secret"), "[redacted]")
            .take(100)
        val reason = if (message.isBlank()) {
            e.javaClass.simpleName
        } else {
            "${e.javaClass.simpleName}: $message"
        }
        return reason.replace(Regex("(?i)password|passwd|secret"), "[redacted]")
    }

    companion object {
        private const val TAG = "ImapReceiptClient"
    }
}
