package com.davidlang.vehicleexpensesautomated.data.email

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class EmailReceiptWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val gmailClient: GmailReceiptClient,
    private val imapClient: ImapReceiptClient,
    private val ingest: FuelReceiptIngest,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = EmailReceiptPrefs(appContext)
        if (!prefs.enabled) {
            Log.i(TAG, "disabled; skip")
            return@withContext Result.success()
        }

        val parts = mutableListOf<String>()
        var anyRetry = false
        var anyFailure = false

        val src = prefs.source
        val runGmailPath = src == EmailReceiptPrefs.SOURCE_GMAIL || src == EmailReceiptPrefs.SOURCE_BOTH
        val runImapPath = src == EmailReceiptPrefs.SOURCE_IMAP || src == EmailReceiptPrefs.SOURCE_BOTH

        if (runGmailPath) {
            val g = runGmail(prefs)
            parts.add(g.summary)
            if (g.retry) anyRetry = true
            if (g.hardFail) anyFailure = true
        }
        if (runImapPath) {
            val i = runImap(prefs)
            parts.add(i.summary)
            if (i.retry) anyRetry = true
            if (i.hardFail) anyFailure = true
        }

        if (parts.isEmpty()) {
            prefs.lastRunSummary = "skipped: no source (enable Gmail and/or IMAP)"
            prefs.lastRunAtMs = System.currentTimeMillis()
            return@withContext Result.success()
        }

        val summary = parts.joinToString(" | ")
        Log.i(TAG, summary)
        prefs.lastRunSummary = summary
        prefs.lastRunAtMs = System.currentTimeMillis()
        return@withContext when {
            anyFailure && !anyRetry -> Result.failure()
            anyRetry -> Result.retry()
            else -> Result.success()
        }
    }

    private data class PathResult(
        val summary: String,
        val retry: Boolean = false,
        val hardFail: Boolean = false,
    )

    private suspend fun runGmail(prefs: EmailReceiptPrefs): PathResult {
        val label = prefs.labelName
        if (label.isBlank()) {
            return PathResult("gmail skipped: empty label")
        }
        var scanned = 0
        var parsed = 0
        var inserted = 0
        var skippedDup = 0
        var skippedParse = 0
        return try {
            val account = gmailClient.resolveAccount(prefs.accountEmail)
            if (account == null) {
                return PathResult("gmail skipped: no Google account")
            }
            val messages = try {
                gmailClient.listLabeledReceipts(account, label)
            } catch (e: GmailReceiptClient.NeedsConsentException) {
                Log.w(TAG, "gmail consent", e)
                return PathResult("gmail needs consent", hardFail = true)
            }
            for (msg in messages) {
                scanned++
                val meta = ReceiptParsers.Meta(
                    messageKey = msg.id,
                    gmailMessageId = msg.id,
                    fromHeader = msg.from,
                    subject = msg.subject,
                    emailDateHeader = msg.dateHeader,
                )
                val typeKey = ReceiptParsers.detectType(msg.htmlBody, meta)
                val receipt = ReceiptParsers.tryParse(html = msg.htmlBody, meta = meta)
                if (receipt == null) {
                    skippedParse++
                    Log.i(TAG, "gmail parse skip id=${msg.id} type=${typeKey ?: "none"}")
                    continue
                }
                parsed++
                val result = ingest.ingest(
                    parsed = receipt,
                    messageId = msg.id,
                    messageProvider = "gmail",
                )
                if (result.skippedDuplicate) skippedDup++
                else if (result.inserted) inserted++
            }
            PathResult(
                "gmail scanned=$scanned parsed=$parsed inserted=$inserted dup=$skippedDup parseSkip=$skippedParse",
            )
        } catch (e: Exception) {
            Log.e(TAG, "gmail path failed", e)
            PathResult("gmail error: ${e.message?.take(80)}", retry = true)
        }
    }

    private suspend fun runImap(prefs: EmailReceiptPrefs): PathResult {
        if (prefs.imapHost.isBlank() || prefs.imapUsername.isBlank()) {
            return PathResult("imap skipped: host/user not set")
        }
        if (prefs.imapPassword.isEmpty()) {
            return PathResult("imap skipped: password not set")
        }
        var scanned = 0
        var parsed = 0
        var inserted = 0
        var skippedDup = 0
        var skippedParse = 0
        return try {
            val fetch = imapClient.listFolderMessages(
                host = prefs.imapHost,
                port = prefs.imapPort,
                username = prefs.imapUsername,
                password = prefs.imapPassword,
                folderName = prefs.imapFolder,
            )
            if (fetch.errorSummary != null && fetch.messages.isEmpty()) {
                return PathResult(fetch.errorSummary, hardFail = true)
            }
            for (msg in fetch.messages) {
                scanned++
                val meta = ReceiptParsers.Meta(
                    messageKey = msg.stableId,
                    gmailMessageId = msg.stableId,
                    fromHeader = msg.from,
                    subject = msg.subject,
                    emailDateHeader = msg.dateHeader,
                )
                val typeKey = ReceiptParsers.detectType(msg.htmlOrTextBody, meta)
                val receipt = ReceiptParsers.tryParse(html = msg.htmlOrTextBody, meta = meta)
                if (receipt == null) {
                    skippedParse++
                    Log.i(
                        TAG,
                        "imap parse skip id=${msg.stableId} type=${typeKey ?: "none"} " +
                            "subject=${msg.subject.take(40)}",
                    )
                    continue
                }
                parsed++
                val result = ingest.ingest(
                    parsed = receipt,
                    messageId = msg.stableId,
                    messageProvider = "imap",
                    originDeviceId = "android-email-imap",
                )
                if (result.skippedDuplicate) skippedDup++
                else if (result.inserted) inserted++
            }
            val base =
                "imap scanned=$scanned parsed=$parsed inserted=$inserted dup=$skippedDup parseSkip=$skippedParse"
            if (fetch.errorSummary != null) PathResult("$base (${fetch.errorSummary})")
            else PathResult(base)
        } catch (e: Exception) {
            Log.e(TAG, "imap path failed", e)
            val brief = (e.message ?: e.javaClass.simpleName).take(80)
                .replace(Regex("(?i)password|passwd|secret"), "[redacted]")
            PathResult("imap error: $brief", retry = true)
        }
    }

    companion object {
        private const val TAG = "EmailReceiptWorker"
    }
}
