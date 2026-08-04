package com.davidlang.vehicleexpensesautomated.data.email

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleLegacySignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal Gmail REST client: list messages under a user label (mixed vendors), fetch HTML body.
 * Vendor filter is autodetect at parse time — not a Gmail From: restriction.
 * Uses OAuth via [GoogleLegacySignIn] with gmail.readonly only (no send/delete).
 */
@Singleton
class GmailReceiptClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val legacy: GoogleLegacySignIn,
) {
    data class GmailMessage(
        val id: String,
        val from: String,
        val subject: String,
        val dateHeader: String,
        val htmlBody: String,
    )

    class NeedsConsentException(val recoverIntent: android.content.Intent?) : Exception("Gmail consent required")

    fun resolveAccount(emailHint: String?): Account? =
        legacy.resolveAccountFromHint(emailHint, EmailReceiptPrefs.KEY_ACCOUNT)
            ?: legacy.resolveAccount(legacy.lastAccount())

    /** @deprecated Use [listLabeledReceipts]; kept as alias for call sites. */
    fun listShellReceipts(account: Account, labelName: String, maxResults: Int = 25): List<GmailMessage> =
        listLabeledReceipts(account, labelName, maxResults)

    /**
     * Label-only query (no Shell-only from:). Autodetect vendor after fetch.
     * @return messages (may be empty). Throws [NeedsConsentException] if user must re-auth.
     */
    fun listLabeledReceipts(account: Account, labelName: String, maxResults: Int = 25): List<GmailMessage> {
        val credential = legacy.oauthCredential(EmailReceiptPrefs.GMAIL_READONLY_SCOPE, account)
        val token = try {
            credential.token
        } catch (e: UserRecoverableAuthIOException) {
            throw NeedsConsentException(e.intent)
        } catch (e: Exception) {
            Log.w(TAG, "token failed", e)
            throw e
        }

        val label = labelName.trim()
        if (label.isEmpty()) return emptyList()

        // User filter → label is source of truth; mixed Shell + Sam's (etc.).
        val q = "label:${quoteLabel(label)}"
        val listUrl =
            "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=${enc(q)}&maxResults=$maxResults"
        val listJson = httpGet(listUrl, token)
        val root = JSONObject(listJson)
        if (!root.has("messages")) return emptyList()
        val arr = root.getJSONArray("messages")
        val out = mutableListOf<GmailMessage>()
        for (i in 0 until arr.length()) {
            val id = arr.getJSONObject(i).getString("id")
            try {
                val full = fetchMessage(token, id) ?: continue
                out.add(full)
            } catch (e: UserRecoverableAuthIOException) {
                throw NeedsConsentException(e.intent)
            } catch (e: Exception) {
                Log.w(TAG, "fetch message $id failed: ${e.message}")
            }
        }
        return out
    }

    private fun fetchMessage(token: String, id: String): GmailMessage? {
        val url =
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=full"
        val json = JSONObject(httpGet(url, token))
        val payload = json.optJSONObject("payload") ?: return null
        val headers = payload.optJSONArray("headers")
        var from = ""
        var subject = ""
        var dateHeader = ""
        if (headers != null) {
            for (i in 0 until headers.length()) {
                val h = headers.getJSONObject(i)
                when (h.optString("name").lowercase()) {
                    "from" -> from = h.optString("value")
                    "subject" -> subject = h.optString("value")
                    "date" -> dateHeader = h.optString("value")
                }
            }
        }
        val html = extractHtml(payload) ?: return null
        return GmailMessage(
            id = id,
            from = from,
            subject = subject,
            dateHeader = dateHeader,
            htmlBody = html,
        )
    }

    private fun extractHtml(payload: JSONObject): String? {
        val mime = payload.optString("mimeType")
        if (mime.equals("text/html", ignoreCase = true)) {
            val data = payload.optJSONObject("body")?.optString("data")
            if (!data.isNullOrBlank()) return decodeB64Url(data)
        }
        val parts = payload.optJSONArray("parts") ?: return null
        // Prefer text/html part; recurse multipart
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            val pm = p.optString("mimeType")
            if (pm.equals("text/html", ignoreCase = true)) {
                val data = p.optJSONObject("body")?.optString("data")
                if (!data.isNullOrBlank()) return decodeB64Url(data)
            }
        }
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (p.optString("mimeType").startsWith("multipart/")) {
                extractHtml(p)?.let { return it }
            }
        }
        // Fallback: text/plain
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (p.optString("mimeType").equals("text/plain", ignoreCase = true)) {
                val data = p.optJSONObject("body")?.optString("data")
                if (!data.isNullOrBlank()) return decodeB64Url(data)
            }
        }
        return null
    }

    private fun decodeB64Url(data: String): String {
        val bytes = android.util.Base64.decode(data, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        return String(bytes, Charsets.UTF_8)
    }

    private fun httpGet(urlStr: String, token: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 30_000
            readTimeout = 60_000
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.use { BufferedReader(InputStreamReader(it)).readText() } ?: ""
        if (code !in 200..299) {
            throw IllegalStateException("Gmail HTTP $code: ${body.take(200)}")
        }
        return body
    }

    private fun quoteLabel(name: String): String =
        if (name.any { it.isWhitespace() }) "\"$name\"" else name

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val TAG = "GmailReceiptClient"
    }
}
