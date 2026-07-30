package com.davidlang.vehicleexpensesautomated.data.email

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline ingest of packaged Shell fixture HTML (no Gmail).
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

    /**
     * Parse + insert both packaged fixtures. Writes nothing to Gmail.
     * @return human-readable summary for [EmailReceiptPrefs.lastRunSummary]
     */
    suspend fun ingestSampleShellReceipts(): Aggregate {
        var inserted = 0
        var duplicates = 0
        var parseSkip = 0
        for ((assetName, messageKey) in FIXTURES) {
            val html = readAsset(assetName)
            if (html == null) {
                parseSkip++
                Log.w(TAG, "missing asset $assetName")
                continue
            }
            val parsed = ShellReceiptParser.parse(
                html = html,
                messageKey = messageKey,
                gmailMessageId = messageKey,
            )
            if (parsed == null) {
                parseSkip++
                Log.w(TAG, "parse failed for $messageKey")
                continue
            }
            val result = ingest.ingest(
                parsed = parsed,
                gmailMessageId = messageKey,
                originDeviceId = ORIGIN_OFFLINE_FIXTURE,
            )
            when {
                result.skippedDuplicate -> duplicates++
                result.inserted -> inserted++
            }
            Log.i(
                TAG,
                "fixture $messageKey cost=${parsed.cost} gal=${parsed.gallons} " +
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
        /** Stable keys (not random Gmail ids) — second ingest → dups only. */
        val FIXTURES: List<Pair<String, String>> = listOf(
            "shell-receipt1.html" to "fixture|shell-receipt1",
            "shell-receipt2.html" to "fixture|shell-receipt2",
        )
    }
}
