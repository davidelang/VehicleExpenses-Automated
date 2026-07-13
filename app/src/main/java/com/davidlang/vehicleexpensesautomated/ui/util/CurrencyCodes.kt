package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.content.SharedPreferences
import java.util.Currency
import java.util.Locale

/**
 * Normalize UI symbols / ISO codes to a stable stored form (ISO 4217 preferred).
 * Blank stored currency = legacy row → use settings default at display time.
 */
object CurrencyCodes {

    private val SYMBOL_TO_CODE = mapOf(
        "$" to "USD",
        "€" to "EUR",
        "£" to "GBP",
        "CA$" to "CAD",
        "A$" to "AUD",
        "¥" to "JPY",
    )

    fun fromSymbolOrCode(input: String, locale: Locale = Locale.getDefault()): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.equals("system", ignoreCase = true)) {
            return fromSymbolOrCode(systemCurrencySymbol(locale), locale)
        }
        val upper = trimmed.uppercase(locale)
        if (upper.length == 3 && upper.all { it.isLetter() }) {
            return upper
        }
        SYMBOL_TO_CODE[trimmed]?.let { return it }
        try {
            for (c in Currency.getAvailableCurrencies()) {
                if (c.getSymbol(locale) == trimmed) return c.currencyCode
                if (c.currencyCode.equals(trimmed, ignoreCase = true)) return c.currencyCode
            }
        } catch (_: Exception) {
        }
        return trimmed
    }

    fun systemCurrencySymbol(locale: Locale = Locale.getDefault()): String {
        return try {
            Currency.getInstance(locale).getSymbol(locale)
        } catch (_: Exception) {
            "$"
        }
    }

    fun settingsDefaultSymbol(
        prefs: SharedPreferences,
        locale: Locale = Locale.getDefault(),
    ): String {
        val pref = prefs.getString("currency_symbol", null)
        return when {
            pref.isNullOrBlank() || pref == "system" -> systemCurrencySymbol(locale)
            else -> pref
        }
    }

    fun settingsDefaultStored(
        prefs: SharedPreferences,
        locale: Locale = Locale.getDefault(),
    ): String = fromSymbolOrCode(settingsDefaultSymbol(prefs, locale), locale)

    fun settingsDefaultSymbol(context: Context, locale: Locale = Locale.getDefault()): String {
        val prefs = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
        return settingsDefaultSymbol(prefs, locale)
    }

    fun settingsDefaultStored(context: Context, locale: Locale = Locale.getDefault()): String {
        val prefs = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
        return settingsDefaultStored(prefs, locale)
    }

    /** Display symbol for a row: prefer stored code → symbol; blank uses settings default symbol. */
    fun displaySymbol(
        stored: String,
        defaultSymbol: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        if (stored.isBlank()) return defaultSymbol
        symbolForCode(stored, locale)?.let { return it }
        return stored
    }

    fun formatAmount(
        amount: Double,
        rowCurrency: String,
        defaultSymbol: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val sym = displaySymbol(rowCurrency, defaultSymbol, locale)
        return sym + "%.2f".format(amount)
    }

    /** Effective currency key for aggregation (blank rows → settings default stored code). */
    fun effectiveKey(rowCurrency: String, defaultStored: String): String =
        rowCurrency.ifBlank { defaultStored }

    fun <T> sumByCurrency(
        items: List<T>,
        defaultStored: String,
        currencyOf: (T) -> String,
        amountOf: (T) -> Double,
    ): Map<String, Double> {
        return items.groupBy { effectiveKey(currencyOf(it), defaultStored) }
            .mapValues { (_, list) -> list.sumOf { amountOf(it) } }
    }

    /**
     * Format a monetary aggregate. Single currency → one total; mixed → per-currency subtotals
     * joined with " + " (no silent cross-currency sum).
     */
    fun formatAggregateSum(
        sums: Map<String, Double>,
        defaultSymbol: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        if (sums.isEmpty()) return formatAmount(0.0, "", defaultSymbol, locale)
        if (sums.size == 1) {
            val (code, sum) = sums.entries.first()
            return formatAmount(sum, code, defaultSymbol, locale)
        }
        return sums.entries
            .sortedByDescending { it.value }
            .joinToString(" + ") { (code, sum) ->
                formatAmount(sum, code, defaultSymbol, locale)
            }
    }

    private fun symbolForCode(code: String, locale: Locale): String? {
        if (code.length == 3 && code.all { it.isLetter() }) {
            return try {
                Currency.getInstance(code).getSymbol(locale)
            } catch (_: Exception) {
                code
            }
        }
        return null
    }
}