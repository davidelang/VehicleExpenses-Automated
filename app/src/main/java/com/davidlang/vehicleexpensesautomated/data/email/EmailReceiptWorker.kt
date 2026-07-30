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
    private val ingest: FuelReceiptIngest,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = EmailReceiptPrefs(appContext)
        if (!prefs.enabled) {
            Log.i(TAG, "disabled; skip")
            return@withContext Result.success()
        }
        val label = prefs.labelName
        if (label.isBlank()) {
            Log.w(TAG, "empty label; skip")
            prefs.lastRunSummary = "skipped: empty label"
            prefs.lastRunAtMs = System.currentTimeMillis()
            return@withContext Result.success()
        }

        var scanned = 0
        var parsed = 0
        var inserted = 0
        var skippedDup = 0
        var skippedParse = 0

        try {
            val account = gmailClient.resolveAccount(prefs.accountEmail)
            if (account == null) {
                val msg = "skipped: no Google account (sign in under Email receipts)"
                Log.w(TAG, msg)
                prefs.lastRunSummary = msg
                prefs.lastRunAtMs = System.currentTimeMillis()
                return@withContext Result.success()
            }
            val messages = try {
                gmailClient.listShellReceipts(account, label)
            } catch (e: GmailReceiptClient.NeedsConsentException) {
                val msg = "needs Gmail consent — open Settings → Email receipts"
                Log.w(TAG, msg, e)
                prefs.lastRunSummary = msg
                prefs.lastRunAtMs = System.currentTimeMillis()
                return@withContext Result.failure()
            }

            for (msg in messages) {
                scanned++
                val receipt = ShellReceiptParser.parse(
                    html = msg.htmlBody,
                    gmailMessageId = msg.id,
                    messageKey = msg.id,
                )
                if (receipt == null) {
                    skippedParse++
                    Log.i(TAG, "parse skip id=${msg.id} subject=${msg.subject}")
                    continue
                }
                parsed++
                val result = ingest.ingest(receipt, gmailMessageId = msg.id)
                if (result.skippedDuplicate) skippedDup++
                else if (result.inserted) inserted++
            }

            val summary =
                "scanned=$scanned parsed=$parsed inserted=$inserted dup=$skippedDup parseSkip=$skippedParse"
            Log.i(TAG, summary)
            prefs.lastRunSummary = summary
            prefs.lastRunAtMs = System.currentTimeMillis()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "email receipt poll failed", e)
            prefs.lastRunSummary = "error: ${e.message?.take(120)}"
            prefs.lastRunAtMs = System.currentTimeMillis()
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "EmailReceiptWorker"
    }
}
