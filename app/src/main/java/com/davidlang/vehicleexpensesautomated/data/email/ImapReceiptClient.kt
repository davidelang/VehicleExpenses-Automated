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
            for (msg in msgs.reversed()) {
                try {
                    out.add(toReceiptMessage(msg, folder))
                } catch (e: Exception) {
                    Log.w(TAG, "imap message skip: ${e.javaClass.simpleName}")
                }
            }
            FetchResult(out, null)
        } catch (e: Exception) {
            val kind = e.javaClass.simpleName
            val brief = (e.message ?: kind).take(100).replace(Regex("(?i)password|passwd|secret"), "[redacted]")
            Log.w(TAG, "imap fetch failed: $kind")
            FetchResult(emptyList(), "imap: $kind: $brief")
        } finally {
            try {
                if (mailFolder != null && mailFolder.isOpen) mailFolder.close(false)
            } catch (_: Exception) {
            }
            try {
                store?.close()
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
        } catch (_: Exception) {
            null
        }
        val stableId = when {
            !messageId.isNullOrBlank() -> messageId
            !uid.isNullOrBlank() -> "$folderName|$uid"
            else -> "$folderName|seq|${msg.messageNumber}"
        }
        val from = try {
            msg.from?.firstOrNull()?.let { addr ->
                (addr as? InternetAddress)?.toUnicodeString() ?: addr.toString()
            }.orEmpty()
        } catch (_: Exception) {
            ""
        }
        val subject = try {
            msg.subject.orEmpty()
        } catch (_: Exception) {
            ""
        }
        val dateHeader = try {
            mime?.getHeader("Date")?.firstOrNull().orEmpty().ifBlank {
                msg.sentDate?.toString().orEmpty()
            }
        } catch (_: Exception) {
            ""
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

    private fun extractHtmlOrText(part: Part): String {
        val content = try {
            part.content
        } catch (e: Exception) {
            Log.w(TAG, "content read failed: ${e.javaClass.simpleName}")
            return ""
        }
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
                    val bp = mp.getBodyPart(i)
                    when {
                        bp.isMimeType("text/html") && html == null ->
                            html = extractHtmlOrText(bp)
                        bp.isMimeType("text/plain") && plain == null ->
                            plain = extractHtmlOrText(bp)
                        bp.isMimeType("multipart/*") -> {
                            val nested = extractHtmlOrText(bp)
                            if (nested.contains("<html", ignoreCase = true) || nested.contains("<body", ignoreCase = true)) {
                                if (html == null) html = nested
                            } else if (plain == null && nested.isNotBlank()) {
                                plain = nested
                            }
                        }
                    }
                }
                return html?.takeIf { it.isNotBlank() } ?: plain.orEmpty()
            }
            else -> return content?.toString().orEmpty()
        }
    }

    companion object {
        private const val TAG = "ImapReceiptClient"
    }
}
