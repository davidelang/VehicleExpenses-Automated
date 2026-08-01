package com.davidlang.vehicleexpensesautomated.data.email

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

/**
 * Sam's Club fuel-station e-receipt HTML parser.
 * Cost = Total paid; volume = N.NNN gal. Year/zone from email Date header (required).
 * Timezone rule (v1): fill wall clock in the offset from email Date.
 */
object SamsClubReceiptParser {

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    fun parse(
        html: String?,
        messageKey: String? = null,
        gmailMessageId: String? = null,
        fromHeader: String? = null,
        subject: String? = null,
        emailDateHeader: String? = null,
    ): ParsedFuelReceipt? {
        if (html.isNullOrBlank()) return null
        if (!looksSamsClubFuel(html, fromHeader, subject)) return null

        val parts = htmlToText(html)
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val address = extractAddress(parts)
        val fillStr = extractFillDateTimeString(parts) ?: return null
        val fuel = extractFuel(parts) ?: return null
        val amountPaid = extractTotalPaid(parts) ?: return null
        if (fuel.gallons <= 0.0 || amountPaid <= 0.0) return null

        val ts = resolveTimestamp(fillStr, emailDateHeader) ?: return null
        val pump = extractPump(parts)
        val txnId = extractTxnId(parts)

        val locationBits = mutableListOf<String>()
        address?.club?.let { locationBits.add(it) }
        address?.street?.let { locationBits.add(it) }
        address?.cityStateZip?.let { locationBits.add(it) }
        var locationText = if (locationBits.isNotEmpty()) {
            "Sam's Club — ${locationBits.joinToString(", ")}"
        } else {
            "Sam's Club"
        }
        if (pump != null) locationText += " (Pump $pump)"

        val key = messageKey?.takeIf { it.isNotBlank() }
            ?: gmailMessageId?.takeIf { it.isNotBlank() }
            ?: listOf("sams", txnId ?: "notxn", ts.timestampLocal).joinToString("|")

        return ParsedFuelReceipt(
            cost = roundMoney(amountPaid),
            gallons = roundGallons(fuel.gallons),
            timestampMs = ts.timestampMs,
            locationText = locationText,
            currency = "USD",
            brand = "SamsClub",
            messageKey = key,
            timestampLocal = ts.timestampLocal,
            siteId = txnId,
            pump = pump,
            product = fuel.product,
        )
    }

    fun looksSamsClubFuel(html: String, fromHeader: String?, subject: String?): Boolean {
        val blob = listOf(html, fromHeader.orEmpty(), subject.orEmpty()).joinToString("\n").lowercase(Locale.US)
        if (blob.contains("ereceiptshell") || blob.contains("mail.ereceiptshell.com")) return false
        val fromSams =
            blob.contains("samsclub.com") ||
                blob.contains("sam's club") ||
                blob.contains("sams club")
        val fuelMarkers =
            blob.contains("fuel station receipt") ||
                blob.contains("sam's club fuel station") ||
                blob.contains("sams club fuel station") ||
                (blob.contains("total paid") && blob.contains("fuel -"))
        return fromSams && fuelMarkers
    }

    private fun htmlToText(html: String): String {
        var s = html
        s = s.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("<!--[\\s\\S]*?-->"), " ")
        s = s.replace(Regex("<\\s*br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("</\\s*(p|div|tr|h[1-6]|li|table)\\s*>", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("<\\s*td[^>]*>", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("<[^>]+>"), " ")
        s = s
            .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")
            .replace(Regex("&lt;", RegexOption.IGNORE_CASE), "<")
            .replace(Regex("&gt;", RegexOption.IGNORE_CASE), ">")
            .replace(Regex("&quot;", RegexOption.IGNORE_CASE), "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { mr ->
                mr.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: " "
            }
            .replace(Regex("&[a-z]+;", RegexOption.IGNORE_CASE), " ")
        s = s.replace('\r', '\n')
        s = s.replace(Regex("[ \\t]+"), " ")
        s = s.replace(Regex("\\n[ \\t]+"), "\n")
        s = s.replace(Regex("[ \\t]+\\n"), "\n")
        s = s.replace(Regex("\\n{2,}"), "\n")
        return s.trim()
    }

    private data class Address(val club: String?, val street: String?, val cityStateZip: String?)
    private data class FuelLine(val product: String?, val gallons: Double)
    private data class Ts(val timestampMs: Long, val timestampLocal: String)

    private fun extractAddress(parts: List<String>): Address? {
        var start = -1
        for (i in parts.indices) {
            if (parts[i].contains("Transaction Details", ignoreCase = true)) {
                start = i + 1
                break
            }
        }
        if (start < 0) {
            for (i in parts.indices) {
                if (parts[i].contains("Sam", ignoreCase = true) &&
                    parts[i].contains("Club", ignoreCase = true) &&
                    (parts.getOrNull(i + 1)?.any { it.isDigit() } == true)
                ) {
                    start = i
                    break
                }
            }
        }
        if (start < 0) return null
        val club = parts.getOrNull(start)
        val street = parts.getOrNull(start + 1)
        val city = parts.getOrNull(start + 2)
        if (club == null || street == null || city == null) return null
        if (!street.any { it.isDigit() }) return null
        return Address(club, street, city)
    }

    private fun extractFillDateTimeString(parts: List<String>): String? {
        val re = Regex("^[A-Za-z]{3},\\s+[A-Za-z]{3}\\s+\\d{1,2}\\s+at\\s+\\d{1,2}:\\d{2}\\s*[ap]m$", RegexOption.IGNORE_CASE)
        for (p in parts) {
            if (re.matches(p.trim())) return p.trim()
        }
        for (p in parts) {
            if (Regex("\\bat\\s+\\d{1,2}:\\d{2}\\s*[ap]m\\b", RegexOption.IGNORE_CASE).containsMatchIn(p) &&
                Regex("[A-Za-z]{3}").containsMatchIn(p)
            ) {
                return p.trim()
            }
        }
        return null
    }

    private fun extractFuel(parts: List<String>): FuelLine? {
        for (i in parts.indices) {
            val prod = parts[i]
            if (!prod.startsWith("Fuel", ignoreCase = true) || !prod.contains("-")) continue
            if (!Regex("^Fuel\\s*-", RegexOption.IGNORE_CASE).containsMatchIn(prod)) continue
            for (j in (i + 1) until minOf(parts.size, i + 6)) {
                val m = Regex("^([\\d,]+\\.\\d+)\\s*gal\\b", RegexOption.IGNORE_CASE).find(parts[j])
                if (m != null) {
                    val gallons = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
                    return FuelLine(prod, gallons)
                }
            }
        }
        return null
    }

    private fun extractTotalPaid(parts: List<String>): Double? {
        for (i in parts.indices) {
            if (parts[i].trim().equals("Total paid", ignoreCase = true)) {
                for (j in (i + 1) until minOf(parts.size, i + 4)) {
                    parseMoney(parts[j])?.takeIf { it > 0 }?.let { return it }
                }
            }
        }
        for (p in parts) {
            val m = Regex("Total\\s+paid\\s*:?\\s*\\$?\\s*([\\d,]+\\.\\d{2})", RegexOption.IGNORE_CASE).find(p)
            if (m != null) return parseMoney(m.groupValues[1])
        }
        return null
    }

    private fun extractPump(parts: List<String>): String? {
        for (p in parts) {
            val m = Regex("Pump\\s+(\\d+)", RegexOption.IGNORE_CASE).find(p)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private fun extractTxnId(parts: List<String>): String? {
        for (p in parts) {
            val m = Regex("TC\\s+(\\d+)", RegexOption.IGNORE_CASE).find(p)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private fun parseMoney(s: String?): Double? {
        if (s == null) return null
        val t = s.replace(Regex("[$,\\s]"), "")
        return t.toDoubleOrNull()
    }

    private fun roundMoney(n: Double): Double = Math.round(n * 100.0) / 100.0
    private fun roundGallons(n: Double): Double = Math.round(n * 1000.0) / 1000.0

    private fun resolveTimestamp(fillStr: String, emailDateHeader: String?): Ts? {
        val fm = Regex(
            "([A-Za-z]{3}),\\s*([A-Za-z]{3})\\s+(\\d{1,2})\\s+at\\s+(\\d{1,2}):(\\d{2})\\s*([ap]m)",
            RegexOption.IGNORE_CASE,
        ).find(fillStr) ?: return null
        val mon = MONTHS[fm.groupValues[2].take(3).lowercase(Locale.US)] ?: return null
        val day = fm.groupValues[3].toInt()
        var hour = fm.groupValues[4].toInt()
        val minute = fm.groupValues[5].toInt()
        val ap = fm.groupValues[6].lowercase(Locale.US)
        if (ap == "pm" && hour < 12) hour += 12
        if (ap == "am" && hour == 12) hour = 0

        val hdr = parseRfc822Date(emailDateHeader) ?: return null
        val year = hdr.year
        val offsetMin = hdr.offsetMin

        fun pad(n: Int) = n.toString().padStart(2, '0')
        val timestampLocal = "$year-${pad(mon)}-${pad(day)}T${pad(hour)}:${pad(minute)}:00"

        // Wall clock in email Date offset → epoch
        val utcLike = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, mon - 1, day, hour, minute, 0)
        }.timeInMillis
        val ms = utcLike - offsetMin * 60_000L
        return Ts(ms, timestampLocal)
    }

    private data class Rfc822(val year: Int, val offsetMin: Int)

    private fun parseRfc822Date(s: String?): Rfc822? {
        if (s.isNullOrBlank()) return null
        val m = Regex(
            "\\d{1,2}\\s+[A-Za-z]{3}\\s+(\\d{4})\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*([+-]\\d{4}|[A-Z]{2,4})",
        ).find(s)
        if (m != null) {
            val year = m.groupValues[1].toInt()
            var offsetMin = 0
            val off = m.groupValues[2]
            if (Pattern.compile("^[+-]\\d{4}$").matcher(off).matches()) {
                val sign = if (off[0] == '-') -1 else 1
                val hh = off.substring(1, 3).toInt()
                val mm = off.substring(3, 5).toInt()
                offsetMin = sign * (hh * 60 + mm)
            }
            return Rfc822(year, offsetMin)
        }
        return null
    }
}
