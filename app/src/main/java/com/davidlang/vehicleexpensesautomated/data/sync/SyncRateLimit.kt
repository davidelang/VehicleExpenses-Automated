package com.davidlang.vehicleexpensesautomated.data.sync

import android.util.Log
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Shared rate-limit detection, **API-call-level** retry, and pacing for Google Sheets
 * (and photo callers that reuse [backoffMs]).
 *
 * ## Chosen numbers (plan sheets-rate-limit-write-level-retry-and-pacing residual)
 * Google enforces **separate** ~60/min caps for **read** and **write** requests.
 * - [MIN_API_GAP_MS] = 1300 ms between **any** Sheets `.execute()` (read or write)
 *   → ~≤45 requests/min of either kind under one process.
 * - On 429/quota: wait **60–120 s** first, then **90–180 s**, cap **180 s**;
 *   up to [MAX_API_ATTEMPTS] = 8 retries of the **same** call.
 * - Multi-dest: [interDestPaceMs] **10–20 s** after each dest + [postDestReadCooldownMs]
 *   **15–30 s** extra cooldown before dest 2+ (fuel tab GETs are heavy).
 * - Cross-device: detect → random wait → retry only (no distributed lock).
 *
 * Install [installProgress] during a sync so UI can show long waits.
 */
object SyncRateLimit {

    private const val TAG = "SyncRateLimit"

    /** Retries of a single Sheets API call (read or write). */
    const val MAX_API_ATTEMPTS = 8

    /** @deprecated Prefer [MAX_API_ATTEMPTS]; kept for photo / older call sites. */
    const val MAX_WRITE_ATTEMPTS = MAX_API_ATTEMPTS
    const val MAX_ATTEMPTS = MAX_API_ATTEMPTS

    const val MAX_DETAIL_CHARS = 6_000

    /**
     * Min gap between **any** Sheets API calls (GET meta/values or writes).
     * Stays under both 60/min read and 60/min write when one device is busy.
     */
    const val MIN_API_GAP_MS = 1_300L

    /** @deprecated Prefer [MIN_API_GAP_MS]. */
    const val MIN_WRITE_GAP_MS = MIN_API_GAP_MS

    const val CROSS_DEVICE_HINT =
        "Another device may be syncing the same Google account — wait a few minutes and try again."

    private val lastApiAtMs = AtomicLong(0L)
    private val progressRef = AtomicReference<SyncProgressListener?>(null)

    /** Multi-dest: 10–20 s between destinations. */
    val interDestPaceMs: Long
        get() = 10_000L + Random.nextLong(0, 10_001L)

    /**
     * After a full dest (many fuel tab reads), cool down before the next dest’s GETs.
     * 15–30 s on top of [interDestPaceMs].
     */
    val postDestReadCooldownMs: Long
        get() = 15_000L + Random.nextLong(0, 15_001L)

    fun installProgress(listener: SyncProgressListener?) {
        progressRef.set(listener)
    }

    private fun notifyStatus(message: String) {
        try {
            progressRef.get()?.onStatus(message)
        } catch (_: Exception) {
            // UI listener must never break API path
        }
    }

    /**
     * Forward status from remotetable (or other L0) rate-limit waits to the
     * listener installed via [installProgress] during spreadsheet sync.
     */
    fun notifyProgress(message: String) = notifyStatus(message)

    fun isRateLimitError(throwable: Throwable): Boolean {
        var t: Throwable? = throwable
        while (t != null) {
            if (isRateLimitError(t.message)) return true
            if (isRateLimitError(t.toString())) return true
            t = t.cause
        }
        return false
    }

    fun isRateLimitError(message: String?): Boolean {
        val m = message?.trim().orEmpty()
        if (m.isEmpty()) return false
        val lower = m.lowercase()
        return lower.contains("ratelimitexceeded") ||
            lower.contains("resource_exhausted") ||
            lower.contains("quota exceeded") ||
            lower.contains("write requests per minute") ||
            lower.contains("read requests per minute") ||
            lower.contains("read_requests") ||
            lower.contains("write_requests") ||
            lower.contains("user-rate limit exceeded") ||
            lower.contains("http 429") ||
            lower.contains("status code 429") ||
            lower.contains("statuscode=429") ||
            Regex("""\b429\b""").containsMatchIn(m)
    }

    fun shortTitle(message: String?, forSheets: Boolean = true): String? {
        if (!isRateLimitError(message)) return null
        if (!forSheets) return "Rate limited"
        val lower = message.orEmpty().lowercase()
        return when {
            lower.contains("read request") || lower.contains("read_requests") ->
                "Rate limited (Sheets reads)"
            else -> "Rate limited (Sheets writes)"
        }
    }

    fun capDetail(message: String?): String {
        val trimmed = message?.trim().orEmpty().ifBlank { "Sync failed" }
        return if (trimmed.length <= MAX_DETAIL_CHARS) {
            trimmed
        } else {
            trimmed.take(MAX_DETAIL_CHARS) + "\n…(truncated)"
        }
    }

    fun appendCrossDeviceHint(message: String): String {
        val base = message.trim().ifBlank { "Sync failed" }
        return if (base.contains(CROSS_DEVICE_HINT)) {
            base
        } else {
            "$base\n\n$CROSS_DEVICE_HINT"
        }
    }

    /**
     * API-call backoff after rate limit.
     * [attemptAfterFailure] 1 → **60–120 s**; later → **90–180 s**; hard cap **180 s**.
     */
    fun apiBackoffMs(attemptAfterFailure: Int): Long {
        val n = attemptAfterFailure.coerceAtLeast(1)
        val ms = when (n) {
            1 -> 60_000L + Random.nextLong(0, 60_001L) // 60–120 s
            else -> 90_000L + Random.nextLong(0, 90_001L) // 90–180 s
        }
        return ms.coerceIn(60_000L, 180_000L)
    }

    /** @deprecated Prefer [apiBackoffMs]. */
    fun writeBackoffMs(attemptAfterFailure: Int): Long = apiBackoffMs(attemptAfterFailure)

    fun backoffMs(attemptAfterFailure: Int): Long = apiBackoffMs(attemptAfterFailure)

    /** Space any Sheets API call so sustained rate stays under ~60/min read and write. */
    suspend fun paceBeforeApiIfNeeded() {
        val last = lastApiAtMs.get()
        if (last <= 0L) return
        val elapsed = System.currentTimeMillis() - last
        val wait = MIN_API_GAP_MS - elapsed
        if (wait > 0L) {
            delay(wait)
        }
    }

    /** @deprecated Prefer [paceBeforeApiIfNeeded]. */
    suspend fun paceBeforeWriteIfNeeded() = paceBeforeApiIfNeeded()

    fun noteApiSucceeded() {
        lastApiAtMs.set(System.currentTimeMillis())
    }

    /** @deprecated Prefer [noteApiSucceeded]. */
    fun noteWriteSucceeded() = noteApiSucceeded()

    /**
     * Run a **single** Sheets API call (read or write) with pace + rate-limit wait/retry.
     * On success resumes the same call (does not re-run earlier tabs).
     *
     * [block] may be blocking (Google client `.execute()`); call from IO dispatcher.
     */
    suspend fun <T> withSheetsApiLimit(block: () -> T): T {
        var attempt = 0
        while (true) {
            paceBeforeApiIfNeeded()
            try {
                val result = block()
                noteApiSucceeded()
                return result
            } catch (e: Exception) {
                if (!isRateLimitError(e)) throw e
                attempt++
                if (attempt >= MAX_API_ATTEMPTS) {
                    Log.e(TAG, "Sheets API rate limit exhausted after $attempt attempts", e)
                    throw e
                }
                val waitMs = apiBackoffMs(attempt)
                val sec = (waitMs / 1000L).toInt()
                val status =
                    "Rate limited — waiting ${sec}s (try $attempt/$MAX_API_ATTEMPTS)…"
                Log.w(TAG, status)
                notifyStatus(status)
                delay(waitMs)
            }
        }
    }

    /** @deprecated Prefer [withSheetsApiLimit] (reads and writes). */
    suspend fun <T> withSheetsWriteLimit(block: () -> T): T = withSheetsApiLimit(block)
}
