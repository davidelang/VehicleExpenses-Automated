package com.davidlang.vehicleexpensesautomated.data.email

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline ingest of packaged Shell + Sam's Club fixture HTML (no Gmail).
 * Message keys are stable so re-run is idempotent (dups only).
 */
@Singleton
class EmailReceiptFixtureIngest @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ingest: FuelReceiptIngest,
) {
    data class Aggregate(
        val summary: String,
        val inserted: Int,
        val duplicates: Int,
        val parseSkip: Int,
    )

    data class FixtureSpec(
        val assetName: String,
        val messageKey: String,
        val fromHeader: String? = null,
        val subject: String? = null,
        val emailDateHeader: String? = null,
    )

    /**
     * Parse + insert all packaged sample receipts. Writes nothing to Gmail.
     */
    suspend fun ingestSampleShellReceipts(): Aggregate = ingestAllSampleReceipts()

    /** Alias: Shell + Sam's offline samples. */
    suspend fun ingestAllSampleReceipts(): Aggregate {
        var inserted = 0
        var duplicates = 0
        var parseSkip = 0
        for (fx in FIXTURES) {
            val html = readAsset(fx.assetName)
            if (html == null) {
                parseSkip++
                Log.w(TAG, "missing asset ${fx.assetName}")
                continue
            }
            val parsed = ReceiptParsers.tryParse(
                html = html,
                meta = ReceiptParsers.Meta(
                    messageKey = fx.messageKey,
                    gmailMessageId = fx.messageKey,
                    fromHeader = fx.fromHeader,
                    subject = fx.subject,
                    emailDateHeader = fx.emailDateHeader,
                ),
            )
            if (parsed == null) {
                parseSkip++
                Log.w(TAG, "parse failed for ${fx.messageKey}")
                continue
            }
            val result = ingest.ingest(
                parsed = parsed,
                gmailMessageId = fx.messageKey,
                originDeviceId = ORIGIN_OFFLINE_FIXTURE,
            )
            when {
                result.skippedDuplicate -> duplicates++
                result.inserted -> inserted++
            }
            Log.i(
                TAG,
                "fixture ${fx.messageKey} brand=${parsed.brand} cost=${parsed.cost} gal=${parsed.gallons} " +
                    "inserted=${result.inserted} dup=${result.skippedDuplicate}",
            )
        }
        val summary =
            "offlineFixtures inserted=$inserted dup=$duplicates parseSkip=$parseSkip"
        val prefs = EmailReceiptPrefs(context)
        prefs.lastRunSummary = summary
        prefs.lastRunAtMs = System.currentTimeMillis()
        Log.i(TAG, summary)
        return Aggregate(
            summary = summary,
            inserted = inserted,
            duplicates = duplicates,
            parseSkip = parseSkip,
        )
    }

    private fun readAsset(name: String): String? {
        return try {
            context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "readAsset $name failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "EmailReceiptFixtureIngest"
        private const val ASSET_DIR = "email-receipt"
        private const val ORIGIN_OFFLINE_FIXTURE = "android-email-fixture"
        /** Stable keys — second ingest → dups only. */
        val FIXTURES: List<FixtureSpec> = listOf(
            FixtureSpec("shell-receipt1.html", "fixture|shell-receipt1"),
            FixtureSpec("shell-receipt2.html", "fixture|shell-receipt2"),
            FixtureSpec(
                assetName = "sams-club-receipt1.html",
                messageKey = "fixture|sams-club-receipt1",
                fromHeader = "Sam's Club <transaction@info.samsclub.com>",
                subject = "Here's your fuel station receipt",
                emailDateHeader = "Fri, 31 Jul 2026 21:48:47 -0600",
            ),
        )
    }
}
