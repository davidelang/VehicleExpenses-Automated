package com.davidlang.vehicleexpensesautomated.ui.util

import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import kotlin.math.pow

/**
 * Vehicle odometer face length + rollover encoding for values stored on [com.davidlang.vehicleexpensesautomated.data.model.FuelEntry].
 *
 * **Fill table stores tracking miles**, not only the face reading:
 * `tracking = rolloverCount * 10^digitCount + displayReading`.
 * When the face wraps (e.g. 999xxx → 000xxx), [Vehicle.odometerRolloverCount] increments and
 * the next fill’s odometer is still a single increasing Int.
 */
object OdometerTracking {
    const val DEFAULT_DIGIT_COUNT = 6
    const val MIN_DIGIT_COUNT = 3
    const val MAX_DIGIT_COUNT = 9

    fun digitCount(vehicle: Vehicle?): Int =
        (vehicle?.odometerDigitCount ?: DEFAULT_DIGIT_COUNT).coerceIn(MIN_DIGIT_COUNT, MAX_DIGIT_COUNT)

    fun rolloverCount(vehicle: Vehicle?): Int =
        (vehicle?.odometerRolloverCount ?: 0).coerceAtLeast(0)

    fun modulus(digitCount: Int): Int {
        val dc = digitCount.coerceIn(MIN_DIGIT_COUNT, MAX_DIGIT_COUNT)
        return 10.0.pow(dc.toDouble()).toInt()
    }

    fun displayReading(tracking: Int, digitCount: Int): Int {
        val mod = modulus(digitCount)
        if (mod <= 0) return tracking.coerceAtLeast(0)
        val t = tracking.coerceAtLeast(0)
        return t % mod
    }

    /** Zero-padded face string for “starts with 9” checks. */
    fun displayString(tracking: Int, digitCount: Int): String {
        val dc = digitCount.coerceIn(MIN_DIGIT_COUNT, MAX_DIGIT_COUNT)
        val d = displayReading(tracking, dc)
        return d.toString().padStart(dc, '0')
    }

    fun lastFillStartsWithNine(lastTracking: Int?, digitCount: Int): Boolean {
        if (lastTracking == null || lastTracking <= 0) return false
        return displayString(lastTracking, digitCount).startsWith('9')
    }

    /**
     * Lengths allowed for OCR stage candidates (Raw/Bin pick and bin PreferLen).
     * Always includes [preferred]; if [allowExtraDigit] (last fill started with 9), also preferred+1.
     * Loose band preferred±1 for fallback when preferred is empty.
     */
    fun allowedLengths(preferred: Int, allowExtraDigit: Boolean): IntRange {
        val p = preferred.coerceIn(MIN_DIGIT_COUNT, MAX_DIGIT_COUNT)
        val hi = if (allowExtraDigit) (p + 1).coerceAtMost(MAX_DIGIT_COUNT + 1) else p + 1
        val lo = (p - 1).coerceAtLeast(3)
        return lo..hi.coerceAtMost(9)
    }

    fun preferredLength(preferred: Int): Int =
        preferred.coerceIn(MIN_DIGIT_COUNT, MAX_DIGIT_COUNT)

    data class ResolvedOdo(
        /** Value to store on FuelEntry.odometer */
        val trackingOdometer: Int,
        /** Vehicle.odometerRolloverCount after this reading */
        val newRolloverCount: Int,
        /** Face digits (no rollover prefix) */
        val displayDigits: String,
    )

    /**
     * Map OCR digit string + vehicle rollover state → tracking odometer for the fill table.
     *
     * @param ocrDigits pure digits from OCR / UI
     * @param digitCount face width
     * @param rolloverCount current vehicle rollover flag
     * @param lastTrackingOdometer last fill’s stored odometer (tracking), if any
     */
    fun resolveFromOcr(
        ocrDigits: String,
        digitCount: Int,
        rolloverCount: Int,
        lastTrackingOdometer: Int?,
    ): ResolvedOdo? {
        val dc = digitCount.coerceIn(MIN_DIGIT_COUNT, MAX_DIGIT_COUNT)
        val mod = modulus(dc)
        val digits = ocrDigits.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val vLong = digits.toLongOrNull() ?: return null
        if (vLong <= 0L) return null
        // Cap to Int range for FuelEntry.odometer
        if (vLong > Int.MAX_VALUE) return null
        val v = vLong.toInt()
        val rollover = rolloverCount.coerceAtLeast(0)
        val allowExtra = lastFillStartsWithNine(lastTrackingOdometer, dc)
        val len = digits.length

        // Full tracking / crossed modulus: e.g. 7 digits ≥ 1_000_000 on a 6-digit face
        if (len >= dc + 1 && v >= mod) {
            val newRollover = v / mod
            val display = v % mod
            return ResolvedOdo(
                trackingOdometer = v,
                newRolloverCount = newRollover.coerceAtLeast(rollover),
                displayDigits = display.toString().padStart(dc, '0'),
            )
        }

        // Face reading (possibly with junk leading digit of length dc+1 but value < mod)
        val display = when {
            len <= dc -> v
            allowExtra && len == dc + 1 -> v % mod // strip high digit if still below mod
            else -> v % mod
        }.coerceIn(0, mod - 1)

        var newRollover = rollover
        var tracking = rollover * mod + display

        val last = lastTrackingOdometer
        if (last != null && last > 0) {
            val lastDisp = displayReading(last, dc)
            val lastNine = lastFillStartsWithNine(last, dc)
            // Face went backward while previous was in the 9xxxxx band → wrap
            if (lastNine && display < lastDisp) {
                newRollover = rollover + 1
                tracking = newRollover * mod + display
            } else if (tracking < last && lastNine) {
                newRollover = rollover + 1
                tracking = newRollover * mod + display
            } else if (tracking < last && !lastNine) {
                // Prefer not to go backward without wrap signal; keep candidate (user can edit)
                tracking = rollover * mod + display
            }
        }

        return ResolvedOdo(
            trackingOdometer = tracking,
            newRolloverCount = newRollover,
            displayDigits = display.toString().padStart(dc, '0'),
        )
    }

    fun resolveFromOcr(
        ocrDigits: String,
        vehicle: Vehicle?,
        lastTrackingOdometer: Int?,
    ): ResolvedOdo? = resolveFromOcr(
        ocrDigits = ocrDigits,
        digitCount = digitCount(vehicle),
        rolloverCount = rolloverCount(vehicle),
        lastTrackingOdometer = lastTrackingOdometer,
    )
}
