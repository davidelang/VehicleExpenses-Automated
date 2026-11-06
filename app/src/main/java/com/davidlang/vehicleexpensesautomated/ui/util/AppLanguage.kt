package com.davidlang.vehicleexpensesautomated.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
 * Indonesian stores pref tag `id` and applies bcp47 `in` (legacy Android resource match
 * for `values-in` only). There is no `values-id` folder.
 *
 * A stored pref `pt-BR` selects `values-pt`. `ms-MY` is not a pack.
 *
 * All supported packs ship in the base APK (`values/` + `values-*`). Right-to-left packs
 * are not in [SUPPORTED]. See `docs/facts/translations.md`.
 *
 * [wrap] is the configuration context for the active pack. [applyFromPrefs] still calls
 * [AppCompatDelegate.setApplicationLocales], which is the API 33 platform record.
 */
object AppLanguage {
    const val PREFS_NAME = "vehicle_settings"
    const val PREF_KEY = "app_language"

    const val SYSTEM = "system"
    const val EN = "en"

    /**
     * @param prefTag value stored in prefs / Settings picker
     * @param bcp47 tag for [LocaleListCompat.forLanguageTags]
     * @param manualPathSegment path segment under `docs/i18n/<tag>/` (empty for English)
     * @param displayName Settings list label (endonym, same in every pack)
     */
    data class SupportedLocale(
        val prefTag: String,
        val bcp47: String,
        val manualPathSegment: String,
        val displayName: String,
    )

    /** Product-supported forceable languages (not including [SYSTEM]). English is `values/` only. */
    val SUPPORTED: List<SupportedLocale> = listOf(
        SupportedLocale(EN, "en", "", "English"),
        SupportedLocale("en-001", "en-001", "en-001", "English"),
        SupportedLocale("en-AU", "en-AU", "en-AU", "English (Australia)"),
        SupportedLocale("en-CA", "en-CA", "en-CA", "English (Canada)"),
        SupportedLocale("en-GB", "en-GB", "en-GB", "English (United Kingdom)"),
        SupportedLocale("en-IN", "en-IN", "en-IN", "English (India)"),
        SupportedLocale("af", "af", "af", "Afrikaans"),
        SupportedLocale("sq", "sq", "sq", "Shqip"),
        SupportedLocale("am", "am", "am", "አማርኛ"),
        SupportedLocale("hy", "hy", "hy", "Հայերեն"),
        SupportedLocale("as", "as", "as", "অসমীয়া"),
        SupportedLocale("az", "az", "az", "Azərbaycan"),
        SupportedLocale("eu", "eu", "eu", "Euskara"),
        SupportedLocale("be", "be", "be", "Беларуская"),
        SupportedLocale("bn", "bn", "bn", "বাংলা"),
        SupportedLocale("bs", "bs", "bs", "Bosanski"),
        SupportedLocale("bg", "bg", "bg", "Български"),
        SupportedLocale("my", "my", "my", "မြန်မာ"),
        SupportedLocale("ca", "ca", "ca", "Català"),
        SupportedLocale("ceb", "ceb", "ceb", "Cebuano"),
        SupportedLocale("zh-HK", "zh-HK", "zh-HK", "中文（香港）"),
        SupportedLocale("zh-CN", "zh-CN", "zh-CN", "中文（简体）"),
        SupportedLocale("zh-TW", "zh-TW", "zh-TW", "中文（繁體）"),
        SupportedLocale("hr", "hr", "hr", "Hrvatski"),
        SupportedLocale("cs", "cs", "cs", "Čeština"),
        SupportedLocale("da", "da", "da", "Dansk"),
        SupportedLocale("nl", "nl", "nl", "Nederlands"),
        SupportedLocale("et", "et", "et", "Eesti"),
        SupportedLocale("fo", "fo", "fo", "Føroyskt"),
        SupportedLocale("tl", "tl", "tl", "Filipino"),
        SupportedLocale("fi", "fi", "fi", "Suomi"),
        SupportedLocale("fr-CA", "fr-CA", "fr-CA", "Français (Canada)"),
        SupportedLocale("fr", "fr", "fr", "Français"),
        SupportedLocale("fy", "fy", "fy", "Frysk"),
        SupportedLocale("gl", "gl", "gl", "Galego"),
        SupportedLocale("gd", "gd", "gd", "Gàidhlig"),
        SupportedLocale("ka", "ka", "ka", "ქართული"),
        SupportedLocale("de", "de", "de", "Deutsch"),
        SupportedLocale("el", "el", "el", "Ελληνικά"),
        SupportedLocale("gu", "gu", "gu", "ગુજરાતી"),
        SupportedLocale("ha", "ha", "ha", "Hausa"),
        SupportedLocale("hi", "hi", "hi", "हिन्दी"),
        SupportedLocale("hu", "hu", "hu", "Magyar"),
        SupportedLocale("is", "is", "is", "Íslenska"),
        SupportedLocale("ig", "ig", "ig", "Igbo"),
        // bcp47 "in" is the legacy code Android resource matching still prefers for Indonesian.
        SupportedLocale("id", "in", "id", "Bahasa Indonesia"),
        SupportedLocale("ga", "ga", "ga", "Gaeilge"),
        SupportedLocale("it", "it", "it", "Italiano"),
        SupportedLocale("ja", "ja", "ja", "日本語"),
        SupportedLocale("jv", "jv", "jv", "Basa Jawa"),
        SupportedLocale("kn", "kn", "kn", "ಕನ್ನಡ"),
        SupportedLocale("kk", "kk", "kk", "Қазақ тілі"),
        SupportedLocale("km", "km", "km", "ខ្មែរ"),
        SupportedLocale("rw", "rw", "rw", "Kinyarwanda"),
        SupportedLocale("ko", "ko", "ko", "한국어"),
        SupportedLocale("ku", "ku", "ku", "Kurdî"),
        SupportedLocale("ky", "ky", "ky", "Кыргызча"),
        SupportedLocale("lo", "lo", "lo", "ລາວ"),
        SupportedLocale("lv", "lv", "lv", "Latviešu"),
        SupportedLocale("lt", "lt", "lt", "Lietuvių"),
        SupportedLocale("lb", "lb", "lb", "Lëtzebuergesch"),
        SupportedLocale("mk", "mk", "mk", "Македонски"),
        SupportedLocale("mg", "mg", "mg", "Malagasy"),
        SupportedLocale("ms", "ms", "ms", "Bahasa Melayu"),
        SupportedLocale("ml", "ml", "ml", "മലയാളം"),
        SupportedLocale("mt", "mt", "mt", "Malti"),
        SupportedLocale("mi", "mi", "mi", "Māori"),
        SupportedLocale("mr", "mr", "mr", "मराठी"),
        SupportedLocale("mn", "mn", "mn", "Монгол"),
        SupportedLocale("ne", "ne", "ne", "नेपाली"),
        SupportedLocale("nb", "nb", "nb", "Norsk bokmål"),
        SupportedLocale("nn", "nn", "nn", "Norsk nynorsk"),
        SupportedLocale("or", "or", "or", "ଓଡ଼ିଆ"),
        SupportedLocale("pl", "pl", "pl", "Polski"),
        SupportedLocale("pt-BR", "pt-BR", "pt-BR", "Português (Brasil)"),
        SupportedLocale("pt-PT", "pt-PT", "pt-PT", "Português (Portugal)"),
        SupportedLocale("pa", "pa", "pa", "ਪੰਜਾਬੀ"),
        SupportedLocale("qu", "qu", "qu", "Runasimi"),
        SupportedLocale("ro", "ro", "ro", "Română"),
        SupportedLocale("rm", "rm", "rm", "Rumantsch"),
        SupportedLocale("ru", "ru", "ru", "Русский"),
        SupportedLocale("sr", "sr", "sr", "Српски"),
        SupportedLocale("sr-Latn", "sr-Latn", "sr-Latn", "srpski (latinica)"),
        SupportedLocale("si", "si", "si", "සිංහල"),
        SupportedLocale("sk", "sk", "sk", "Slovenčina"),
        SupportedLocale("sl", "sl", "sl", "Slovenščina"),
        SupportedLocale("so", "so", "so", "Soomaali"),
        SupportedLocale("es-419", "es-419", "es-419", "Español (Latinoamérica)"),
        SupportedLocale("es", "es", "es", "Español"),
        SupportedLocale("es-US", "es-US", "es-US", "Español (Estados Unidos)"),
        SupportedLocale("su", "su", "su", "Basa Sunda"),
        SupportedLocale("sw", "sw", "sw", "Kiswahili"),
        SupportedLocale("sv", "sv", "sv", "Svenska"),
        SupportedLocale("tg", "tg", "tg", "Тоҷикӣ"),
        SupportedLocale("ta", "ta", "ta", "தமிழ்"),
        SupportedLocale("tt", "tt", "tt", "Татар"),
        SupportedLocale("te", "te", "te", "తెలుగు"),
        SupportedLocale("th", "th", "th", "ไทย"),
        SupportedLocale("tr", "tr", "tr", "Türkçe"),
        SupportedLocale("tk", "tk", "tk", "Türkmençe"),
        SupportedLocale("uk", "uk", "uk", "Українська"),
        SupportedLocale("uz", "uz", "uz", "Oʻzbek"),
        SupportedLocale("vi", "vi", "vi", "Tiếng Việt"),
        SupportedLocale("cy", "cy", "cy", "Cymraeg"),
        SupportedLocale("xh", "xh", "xh", "IsiXhosa"),
        SupportedLocale("yo", "yo", "yo", "Yorùbá"),
        SupportedLocale("zu", "zu", "zu", "IsiZulu"),
    )

    private val byPrefTag: Map<String, SupportedLocale> = SUPPORTED.associateBy { it.prefTag }

    fun isSupportedPrefTag(tag: String): Boolean = tag == SYSTEM || byPrefTag.containsKey(tag)

    /** Raw pref value, or null if never set (first-run / unset → English). */
    fun readPrefOrNull(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_KEY)) return null
        return prefs.getString(PREF_KEY, EN)?.takeIf { it.isNotBlank() }
    }

    /** Value for Settings UI: unset displays as English selected (`en`). */
    fun readPrefForUi(context: Context): String = readPrefOrNull(context) ?: EN

    /** Resolved pack pref tag always in [SUPPORTED] (never `system`). */
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
     * Bare `pt` stays Brazilian (`values-pt`). `fil` maps to `tl`, `no` to `nb`, `nn` to Nynorsk.
     * `in` maps to `id`. Serbian in Latin script maps to `sr-Latn`.
     */
    fun matchSystemToSupported(): String {
        val systemList = LocaleListCompat.getAdjustedDefault()
        if (systemList.isEmpty) return EN
        for (i in 0 until systemList.size()) {
            val loc = systemList[i] ?: continue
            var language = loc.language.lowercase(Locale.ROOT)
            when (language) {
                "fil" -> language = "tl"
                "in" -> language = "id"
                "no" -> language = "nb"
            }
            val country = loc.country.orEmpty().uppercase(Locale.ROOT)
            val script = loc.script.orEmpty()
            val tag = loc.toLanguageTag().lowercase(Locale.ROOT)
            val full = when {
                language == "zh" && country == "HK" -> "zh-HK"
                language == "zh" && (country == "TW" || script.equals("Hant", ignoreCase = true)) -> "zh-TW"
                language == "zh" -> "zh-CN"
                language == "pt" && country == "PT" -> "pt-PT"
                language == "pt" -> "pt-BR"
                language == "fr" && country == "CA" -> "fr-CA"
                language == "es" && country == "US" -> "es-US"
                language == "es" && (country == "419" || tag.contains("419")) -> "es-419"
                language == "sr" && script.equals("Latn", ignoreCase = true) -> "sr-Latn"
                country.isEmpty() -> language
                else -> "$language-$country"
            }
            byPrefTag[full]?.let { return it.prefTag }
            byPrefTag[language]?.let { return it.prefTag }
            SUPPORTED.firstOrNull { pack ->
                pack.prefTag.substringBefore('-').equals(language, ignoreCase = true)
            }?.let { return it.prefTag }
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
        // stringResource keeps the activity configuration until that activity is recreated.
        findActivity(context)?.recreate()
    }

    /**
     * Configuration context for [resolveActiveLocale]'s bcp47 tag, the same tag
     * [applyFromPrefs] passes to [LocaleListCompat]. Unset is English. `system` is
     * [matchSystemToSupported]. A [androidx.activity.ComponentActivity] does not apply
     * [AppCompatDelegate.setApplicationLocales] before API 33, so this is what puts the
     * pack on screen.
     */
    fun wrap(context: Context): Context {
        val tag = resolveActiveLocale(context).bcp47
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(config)
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val next = current.baseContext
            if (next === current) return null
            current = next
        }
        return null
    }

    /** Apply locales from current pref. Call early in [android.app.Application.onCreate]. */
    fun applyFromPrefs(context: Context) {
        val active = resolveActiveLocale(context)
        val locales = LocaleListCompat.forLanguageTags(active.bcp47)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private const val REPO = "davidelang/VehicleExpenses-Automated"

    /** jsDelivr HTML URL for the active language. English keeps the current manual path. */
    fun onlineManualHtmlUrl(context: Context): String = onlineManualHtmlUrlFor(resolveActiveLocale(context))

    fun onlineManualHtmlUrlFor(loc: SupportedLocale): String {
        return if (loc.prefTag == EN || loc.manualPathSegment.isEmpty()) {
            "https://cdn.jsdelivr.net/gh/$REPO@master/docs/user-manual/index.html"
        } else {
            "https://cdn.jsdelivr.net/gh/$REPO@master/docs/i18n/${loc.manualPathSegment}/user-manual.html"
        }
    }
}
