package com.davidlang.vehicleexpensesautomated.data.email

/**
 * Shell e-Receipt HTML parser (Kotlin port of sandbox lib/shell-receipt-parser.js).
 *
 * Fuel-only: require fuel volume + amount paid after discounts.
 * Timestamp rule (v1): MM-DD-YYYY + HH:MM:SS treated as UTC wall clock.
 */
object ShellReceiptParser {

    fun parse(html: String?, messageKey: String? = null, gmailMessageId: String? = null): ParsedFuelReceipt? {
        if (html.isNullOrBlank()) return null
        val lower = html.lowercase()
        if (lower.contains("samsclub.com") || lower.contains("sam's club fuel")) return null
        val looksShell =
            lower.contains("ereceiptshell") ||
                lower.contains("shell e-receipt") ||
                lower.contains("welcome to shell") ||
                lower.contains("shell oil")
        if (!looksShell) return null

        val parts = htmlToText(html)
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val address = extractAddress(parts)
        val (dateStr, timeStr) = extractDateTime(parts)
        val fuel = extractFuelLine(parts) ?: return null
        val amountPaid = extractAmountPaid(parts, htmlToText(html)) ?: return null
        if (fuel.gallons <= 0.0 || amountPaid <= 0.0) return null
        if (dateStr == null || timeStr == null) return null

        val timestampMs = wallTimeToEpochMs(dateStr, timeStr) ?: return null
        val timestampLocal = toIsoLocal(dateStr, timeStr)
        val siteId = extractSiteId(parts)
        val pump = extractPump(parts)
        val locationText = if (address != null) {
            "Shell — ${address.joinToString(", ")}"
        } else {
            "Shell"
        }
        val key = messageKey?.takeIf { it.isNotBlank() }
            ?: gmailMessageId?.takeIf { it.isNotBlank() }
            ?: listOf("shell", siteId ?: "nosite", timestampLocal ?: timestampMs.toString()).joinToString("|")

        return ParsedFuelReceipt(
            cost = roundMoney(amountPaid),
            gallons = roundGallons(fuel.gallons),
            timestampMs = timestampMs,
            locationText = locationText,
            currency = "USD",
            brand = "Shell",
            messageKey = key,
            timestampLocal = timestampLocal,
            siteId = siteId,
            pump = pump,
            product = fuel.product,
        )
    }

    internal fun htmlToText(html: String): String {
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

    private fun extractAddress(parts: List<String>): List<String>? {
        for (i in parts.indices) {
            val p = parts[i]
            if (p.contains("here is your", ignoreCase = true) && p.contains("receipt", ignoreCase = true)) {
                val street = parts.getOrNull(i + 1)
                val city = parts.getOrNull(i + 2)
                val stZip = parts.getOrNull(i + 3)
                if (street != null && city != null && stZip != null &&
                    looksStreet(street) && looksStateZip(stZip)
                ) {
                    return listOf(street, city, stZip)
                }
            }
        }
        for (i in 2 until parts.size) {
            if (looksStateZip(parts[i]) && looksStreet(parts[i - 2])) {
                return listOf(parts[i - 2], parts[i - 1], parts[i])
            }
        }
        return null
    }

    private fun looksStreet(s: String): Boolean =
        s.any { it.isDigit() } && s.any { it.isLetter() } && s.length in 5..79

    private fun looksStateZip(s: String): Boolean =
        Regex("^[A-Z]{2}\\s+\\d{5}(-\\d{4})?$").matches(s.trim())

    private fun extractDateTime(parts: List<String>): Pair<String?, String?> {
        var dateStr: String? = null
        var timeStr: String? = null
        for (p in parts) {
            if (Regex("^\\d{1,2}-\\d{1,2}-\\d{4}$").matches(p)) dateStr = p
            if (Regex("^\\d{1,2}:\\d{2}:\\d{2}$").matches(p)) timeStr = p
        }
        return dateStr to timeStr
    }

    private data class FuelLine(
        val product: String?,
        val gallons: Double,
        val pricePerGal: Double?,
        val fuelTotal: Double?,
    )

    private fun extractFuelLine(parts: List<String>): FuelLine? {
        var headerIdx = -1
        for (i in 0 until parts.size - 3) {
            if (parts[i].equals("Fuel Type", ignoreCase = true) &&
                parts[i + 1].equals("Gallons", ignoreCase = true) &&
                parts[i + 2].equals("Price/Gal", ignoreCase = true) &&
                parts[i + 3].equals("Fuel Total", ignoreCase = true)
            ) {
                headerIdx = i
                break
            }
        }
        if (headerIdx < 0) {
            for (i in 0 until parts.size - 4) {
                if (parts[i].equals("Gallons", ignoreCase = true) &&
                    parts[i + 1].equals("Price/Gal", ignoreCase = true)
                ) {
                    headerIdx = i - 1
                    break
                }
            }
        }
        if (headerIdx < 0) return null
        val product = parts.getOrNull(headerIdx + 4)
        val gallons = parseNumber(parts.getOrNull(headerIdx + 5)) ?: return null
        if (gallons <= 0.0) return null
        return FuelLine(
            product = product,
            gallons = gallons,
            pricePerGal = parseMoney(parts.getOrNull(headerIdx + 6)),
            fuelTotal = parseMoney(parts.getOrNull(headerIdx + 7)),
        )
    }

    private fun extractAmountPaid(parts: List<String>, fullText: String): Double? {
        val re = Regex("Amount\\s*Paid\\s*:\\s*\\$?\\s*([\\d,]+\\.\\d{2})", RegexOption.IGNORE_CASE)
        for (p in parts) {
            re.find(p)?.groupValues?.getOrNull(1)?.let { return parseMoney(it) }
        }
        re.find(fullText)?.groupValues?.getOrNull(1)?.let { return parseMoney(it) }

        var totalIdx = -1
        for (i in parts.indices) {
            if (parts[i].equals("Total", ignoreCase = true)) totalIdx = i
        }
        if (totalIdx >= 0) {
            val monies = mutableListOf<Double>()
            for (j in (totalIdx + 1) until minOf(parts.size, totalIdx + 8)) {
                if (parts[j].startsWith("Payment", ignoreCase = true)) break
                parseMoney(parts[j])?.let { monies.add(it) }
            }
            if (monies.isNotEmpty()) return monies.last()
        }
        return null
    }

    private fun extractSiteId(parts: List<String>): String? {
        for (i in parts.indices) {
            if (Regex("^\\d{10,14}$").matches(parts[i])) {
                if (i + 1 < parts.size && Regex("^\\d{1,2}-\\d{1,2}-\\d{4}$").matches(parts[i + 1])) {
                    return parts[i]
                }
            }
        }
        return parts.firstOrNull { Regex("^\\d{10,14}$").matches(it) }
    }

    private fun extractPump(parts: List<String>): String? {
        val re = Regex("PUMP\\s*#\\s*(\\d+)", RegexOption.IGNORE_CASE)
        for (p in parts) {
            re.find(p)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    private fun parseMoney(s: String?): Double? {
        if (s == null) return null
        var t = s.replace(Regex("[$,\\s]"), "")
        val paren = Regex("^\\((.*)\\)$").find(t)
        if (paren != null) t = "-" + paren.groupValues[1]
        val m = Regex("^(-?\\d+(?:\\.\\d+)?)$").find(t) ?: return null
        return m.groupValues[1].toDoubleOrNull()
    }

    private fun parseNumber(s: String?): Double? =
        s?.replace(",", "")?.trim()?.toDoubleOrNull()

    private fun roundMoney(n: Double): Double = Math.round(n * 100.0) / 100.0

    private fun roundGallons(n: Double): Double = Math.round(n * 1000.0) / 1000.0

    /** MM-DD-YYYY + HH:MM:SS → epoch ms (wall clock as UTC). */
    fun wallTimeToEpochMs(dateStr: String, timeStr: String): Long? {
        val dm = Regex("^(\\d{1,2})-(\\d{1,2})-(\\d{4})$").find(dateStr) ?: return null
        val tm = Regex("^(\\d{1,2}):(\\d{2}):(\\d{2})$").find(timeStr) ?: return null
        val month = dm.groupValues[1].toInt()
        val day = dm.groupValues[2].toInt()
        val year = dm.groupValues[3].toInt()
        val hh = tm.groupValues[1].toInt()
        val mm = tm.groupValues[2].toInt()
        val ss = tm.groupValues[3].toInt()
        // Calendar UTC
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, month - 1, day, hh, mm, ss)
        return cal.timeInMillis
    }

    private fun toIsoLocal(dateStr: String, timeStr: String): String? {
        val dm = Regex("^(\\d{1,2})-(\\d{1,2})-(\\d{4})$").find(dateStr) ?: return null
        val tm = Regex("^(\\d{1,2}):(\\d{2}):(\\d{2})$").find(timeStr) ?: return null
        fun pad(n: String) = n.padStart(2, '0')
        return "${dm.groupValues[3]}-${pad(dm.groupValues[1])}-${pad(dm.groupValues[2])}T" +
            "${pad(tm.groupValues[1])}:${pad(tm.groupValues[2])}:${pad(tm.groupValues[3])}"
    }
}
