package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.davidlang.vehicleexpensesautomated.R
import java.util.Locale

/**
 * In-app language preference and locale apply.
 *
 * Pref key [PREF_KEY] in [PREFS_NAME]:
 * - **unset / first run** → English (not System)
 * - `en` → force English
 * - `system` → first system locale matching a supported tag; else English
 * - other supported tag → force that pack
 *
 * All supported packs ship in the base APK (`values/` + `values-*`).
 * See `docs/reference/I18N.md`.
 */
object AppLanguage {
    const val PREFS_NAME = "vehicle_settings"
    const val PREF_KEY = "app_language"

    const val SYSTEM = "system"
    const val EN = "en"

    /**
     * @param prefTag value stored in prefs / Settings picker (`en`, `es`, `pt-BR`, …)
     * @param bcp47 tag for [LocaleListCompat.forLanguageTags] and resource resolution
     * @param manualPathSegment path segment under `docs/i18n/<tag>/` (empty for English root manual)
     * @param displayNameRes Settings list label string id
     */
    data class SupportedLocale(
        val prefTag: String,
        val bcp47: String,
        val manualPathSegment: String,
        val displayNameRes: Int,
    )

    /** Product-supported forceable languages (not including [SYSTEM]). */
    val SUPPORTED: List<SupportedLocale> = listOf(
        SupportedLocale(EN, "en", "", R.string.lang_name_en),
        SupportedLocale("es", "es", "es", R.string.lang_name_es),
        SupportedLocale("fr", "fr", "fr", R.string.lang_name_fr),
        SupportedLocale("pt-BR", "pt-BR", "pt-BR", R.string.lang_name_pt_br),
        SupportedLocale("de", "de", "de", R.string.lang_name_de),
        SupportedLocale("it", "it", "it", R.string.lang_name_it),
        SupportedLocale("nl", "nl", "nl", R.string.lang_name_nl),
        SupportedLocale("pl", "pl", "pl", R.string.lang_name_pl),
        SupportedLocale("ru", "ru", "ru", R.string.lang_name_ru),
        SupportedLocale("id", "id", "id", R.string.lang_name_id),
        SupportedLocale("vi", "vi", "vi", R.string.lang_name_vi),
        SupportedLocale("tr", "tr", "tr", R.string.lang_name_tr),
    )

    private val byPrefTag: Map<String, SupportedLocale> = SUPPORTED.associateBy { it.prefTag }

    fun isSupportedPrefTag(tag: String): Boolean = tag == SYSTEM || byPrefTag.containsKey(tag)

    /**
     * Raw pref value, or `null` if never set (first-run / unset → English).
     */
    fun readPrefOrNull(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_KEY)) return null
        return prefs.getString(PREF_KEY, EN)?.takeIf { it.isNotBlank() }
    }

    /**
     * Value for Settings UI: unset displays as English selected (`en`).
     */
    fun readPrefForUi(context: Context): String = readPrefOrNull(context) ?: EN

    /**
     * Resolved pack pref tag always in [SUPPORTED] (never `system`).
     */
    fun resolveActivePrefTag(context: Context): String {
        val raw = readPrefOrNull(context)
        return when {
            raw == null -> EN
            raw == SYSTEM -> matchSystemToSupported()
            byPrefTag.containsKey(raw) -> raw
            else -> EN
        }
    }

    fun resolveActiveLocale(context: Context): SupportedLocale {
        val tag = resolveActivePrefTag(context)
        return byPrefTag[tag] ?: SUPPORTED.first { it.prefTag == EN }
    }

    /**
     * First system locale (language, then language-region) that matches a supported pack; else English.
     */
    fun matchSystemToSupported(): String {
        val systemList = LocaleListCompat.getAdjustedDefault()
        if (systemList.isEmpty) return EN
        for (i in 0 until systemList.size()) {
            val loc = systemList[i] ?: continue
            val language = loc.language.lowercase(Locale.ROOT)
            val country = loc.country
            val full = if (country.isNullOrEmpty()) language else "$language-${country.uppercase(Locale.ROOT)}"
            // Prefer exact region match (pt-BR)
            byPrefTag[full]?.let { return it.prefTag }
            // Map pt → pt-BR (only Brazilian pack)
            if (language == "pt") return "pt-BR"
            // Bare language tags
            byPrefTag[language]?.let { return it.prefTag }
            // es-MX etc. → es
            SUPPORTED.firstOrNull { it.prefTag == language || it.bcp47.equals(language, ignoreCase = true) }
                ?.let { return it.prefTag }
        }
        return EN
    }

    fun setPref(context: Context, prefValue: String) {
        val normalized = when {
            prefValue == SYSTEM -> SYSTEM
            byPrefTag.containsKey(prefValue) -> prefValue
            else -> EN
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY, normalized)
            .apply()
        applyFromPrefs(context)
    }

    /** Apply locales from current pref. Call early in [android.app.Application.onCreate]. */
    fun applyFromPrefs(context: Context) {
        val active = resolveActiveLocale(context)
        val locales = LocaleListCompat.forLanguageTags(active.bcp47)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private const val REPO = "davidelang/VehicleExpenses-Automated"

    /** jsDelivr HTML URL for the active language; English uses root path for back-compat. */
    fun onlineManualHtmlUrl(context: Context): String {
        val loc = resolveActiveLocale(context)
        return onlineManualHtmlUrlFor(loc)
    }

    fun onlineManualHtmlUrlFor(loc: SupportedLocale): String {
        return if (loc.prefTag == EN || loc.manualPathSegment.isEmpty()) {
            "https://cdn.jsdelivr.net/gh/$REPO@master/docs/user-manual.html"
        } else {
            "https://cdn.jsdelivr.net/gh/$REPO@master/docs/i18n/${loc.manualPathSegment}/user-manual.html"
        }
    }
}
