package com.davidlang.vehicleexpensesautomated.data.email

import android.content.Context
import android.util.Log
import com.davidelang.extractmail.Extractmail
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline ingest of packaged **extractmail golden JSON** (no Gmail, no HTML re-parse).
 * Numbers match extractmail fixtures/expected-*.json (Shell×2 + Sam's).
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
        val expectedJsonAsset: String,
        val messageKey: String,
        val typeKey: String,
    )

    /**
     * Load golden JSON + insert all packaged sample receipts. Writes nothing to Gmail.
     */
    suspend fun ingestSampleShellReceipts(): Aggregate = ingestAllSampleReceipts()

    /** Alias: Shell + Sam's offline samples via extractmail goldens. */
    suspend fun ingestAllSampleReceipts(): Aggregate {
        var inserted = 0
        var duplicates = 0
        var parseSkip = 0
        for (fx in FIXTURES) {
            val raw = readAsset(fx.expectedJsonAsset)
            if (raw == null) {
                parseSkip++
                Log.w(TAG, "missing asset ${fx.expectedJsonAsset}")
                continue
            }
            val parsed = parseGoldenJson(raw, fx)
            if (parsed == null) {
                parseSkip++
                Log.w(TAG, "golden parse failed for ${fx.messageKey}")
                continue
            }
            val result = ingest.ingest(
                parsed = parsed,
                gmailMessageId = fx.messageKey,
                messageId = fx.messageKey,
                messageProvider = MESSAGE_PROVIDER,
                originDeviceId = ORIGIN_OFFLINE_FIXTURE,
            )
            when {
                result.skippedDuplicate -> duplicates++
                result.inserted -> inserted++
            }
            Log.i(
                TAG,
                "fixture ${fx.messageKey} type=${fx.typeKey} brand=${parsed.brand} " +
                    "cost=${parsed.cost} gal=${parsed.gallons} " +
                    "inserted=${result.inserted} dup=${result.skippedDuplicate}",
            )
        }
        val summary =
            "offlineFixtures extractmail=v${Extractmail.VERSION} " +
                "inserted=$inserted dup=$duplicates parseSkip=$parseSkip"
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

    private fun parseGoldenJson(raw: String, fx: FixtureSpec): ParsedFuelReceipt? {
        return try {
            val o = JSONObject(raw)
            val cost = o.getDouble("cost")
            val gallons = o.getDouble("gallons")
            val timestampMs = o.optLong("timestampMs", 0L)
            if (timestampMs <= 0L) {
                Log.w(TAG, "${fx.messageKey}: missing timestampMs")
                return null
            }
            val location = when {
                o.has("locationText") && !o.isNull("locationText") ->
                    o.optString("locationText", "")
                o.has("locationTextContains") -> {
                    val arr = o.optJSONArray("locationTextContains")
                    if (arr != null) {
                        buildList {
                            for (i in 0 until arr.length()) add(arr.optString(i))
                        }.joinToString(" ")
                    } else {
                        ""
                    }
                }
                else -> ""
            }
            fun optStr(key: String): String? =
                if (o.has(key) && !o.isNull(key)) o.optString(key).takeIf { it.isNotBlank() } else null
            ParsedFuelReceipt(
                cost = cost,
                gallons = gallons,
                timestampMs = timestampMs,
                locationText = location,
                currency = o.optString("currency", "USD").ifBlank { "USD" },
                brand = o.optString("brand", "Shell").ifBlank { "Shell" },
                messageKey = fx.messageKey,
                timestampLocal = optStr("timestampLocal"),
                siteId = optStr("siteId"),
                pump = optStr("pump"),
                product = optStr("product"),
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseGoldenJson ${fx.expectedJsonAsset}", e)
            null
        }
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
        private const val MESSAGE_PROVIDER = "fixture"

        /**
         * Stable keys — second ingest → dups only.
         * Type keys from [Extractmail] (extractmail AAR).
         */
        val FIXTURES: List<FixtureSpec> = listOf(
            FixtureSpec(
                expectedJsonAsset = "expected-shell-receipt1.json",
                messageKey = "fixture|shell-receipt1",
                typeKey = Extractmail.TYPE_SHELL,
            ),
            FixtureSpec(
                expectedJsonAsset = "expected-shell-receipt2.json",
                messageKey = "fixture|shell-receipt2",
                typeKey = Extractmail.TYPE_SHELL,
            ),
            FixtureSpec(
                expectedJsonAsset = "expected-sams-club-receipt1.json",
                messageKey = "fixture|sams-club-receipt1",
                typeKey = Extractmail.TYPE_SAMS_CLUB,
            ),
        )
    }
}
