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
 * Indonesian stores pref tag `id` and applies bcp47 `in` (legacy Android resource match
 * for `values-in` only). There is no `values-id` folder.
 *
 * A stored pref `pt-BR` selects `values-pt`. `ms-MY` is not a pack.
 *
 * All supported packs ship in the base APK (`values/` + `values-*`). Right-to-left packs
 * are not in [SUPPORTED]. See `docs/reference/I18N.md`.
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
     * @param displayNameRes Settings list label (endonym, same in every pack)
     */
    data class SupportedLocale(
        val prefTag: String,
        val bcp47: String,
        val manualPathSegment: String,
        val displayNameRes: Int,
    )

    /** Product-supported forceable languages (not including [SYSTEM]). English is `values/` only. */
    val SUPPORTED: List<SupportedLocale> = listOf(
        SupportedLocale(EN, "en", "", R.string.lang_name_en),
        SupportedLocale("en-001", "en-001", "en-001", R.string.lang_name_en_001),
        SupportedLocale("en-AU", "en-AU", "en-AU", R.string.lang_name_en_au),
        SupportedLocale("en-CA", "en-CA", "en-CA", R.string.lang_name_en_ca),
        SupportedLocale("en-GB", "en-GB", "en-GB", R.string.lang_name_en_gb),
        SupportedLocale("en-IN", "en-IN", "en-IN", R.string.lang_name_en_in),
        SupportedLocale("af", "af", "af", R.string.lang_name_af),
        SupportedLocale("sq", "sq", "sq", R.string.lang_name_sq),
        SupportedLocale("am", "am", "am", R.string.lang_name_am),
        SupportedLocale("hy", "hy", "hy", R.string.lang_name_hy),
        SupportedLocale("as", "as", "as", R.string.lang_name_as),
        SupportedLocale("az", "az", "az", R.string.lang_name_az),
        SupportedLocale("eu", "eu", "eu", R.string.lang_name_eu),
        SupportedLocale("be", "be", "be", R.string.lang_name_be),
        SupportedLocale("bn", "bn", "bn", R.string.lang_name_bn),
        SupportedLocale("bs", "bs", "bs", R.string.lang_name_bs),
        SupportedLocale("bg", "bg", "bg", R.string.lang_name_bg),
        SupportedLocale("my", "my", "my", R.string.lang_name_my),
        SupportedLocale("ca", "ca", "ca", R.string.lang_name_ca),
        SupportedLocale("ceb", "ceb", "ceb", R.string.lang_name_ceb),
        SupportedLocale("zh-HK", "zh-HK", "zh-HK", R.string.lang_name_zh_hk),
        SupportedLocale("zh-CN", "zh-CN", "zh-CN", R.string.lang_name_zh_cn),
        SupportedLocale("zh-TW", "zh-TW", "zh-TW", R.string.lang_name_zh_tw),
        SupportedLocale("hr", "hr", "hr", R.string.lang_name_hr),
        SupportedLocale("cs", "cs", "cs", R.string.lang_name_cs),
        SupportedLocale("da", "da", "da", R.string.lang_name_da),
        SupportedLocale("nl", "nl", "nl", R.string.lang_name_nl),
        SupportedLocale("et", "et", "et", R.string.lang_name_et),
        SupportedLocale("fo", "fo", "fo", R.string.lang_name_fo),
        SupportedLocale("tl", "tl", "tl", R.string.lang_name_tl),
        SupportedLocale("fi", "fi", "fi", R.string.lang_name_fi),
        SupportedLocale("fr-CA", "fr-CA", "fr-CA", R.string.lang_name_fr_ca),
        SupportedLocale("fr", "fr", "fr", R.string.lang_name_fr),
        SupportedLocale("fy", "fy", "fy", R.string.lang_name_fy),
        SupportedLocale("gl", "gl", "gl", R.string.lang_name_gl),
        SupportedLocale("gd", "gd", "gd", R.string.lang_name_gd),
        SupportedLocale("ka", "ka", "ka", R.string.lang_name_ka),
        SupportedLocale("de", "de", "de", R.string.lang_name_de),
        SupportedLocale("el", "el", "el", R.string.lang_name_el),
        SupportedLocale("gu", "gu", "gu", R.string.lang_name_gu),
        SupportedLocale("ha", "ha", "ha", R.string.lang_name_ha),
        SupportedLocale("hi", "hi", "hi", R.string.lang_name_hi),
        SupportedLocale("hu", "hu", "hu", R.string.lang_name_hu),
        SupportedLocale("is", "is", "is", R.string.lang_name_is),
        SupportedLocale("ig", "ig", "ig", R.string.lang_name_ig),
        // bcp47 "in" is the legacy code Android resource matching still prefers for Indonesian.
        SupportedLocale("id", "in", "id", R.string.lang_name_id),
        SupportedLocale("ga", "ga", "ga", R.string.lang_name_ga),
        SupportedLocale("it", "it", "it", R.string.lang_name_it),
        SupportedLocale("ja", "ja", "ja", R.string.lang_name_ja),
        SupportedLocale("jv", "jv", "jv", R.string.lang_name_jv),
        SupportedLocale("kn", "kn", "kn", R.string.lang_name_kn),
        SupportedLocale("kk", "kk", "kk", R.string.lang_name_kk),
        SupportedLocale("km", "km", "km", R.string.lang_name_km),
        SupportedLocale("rw", "rw", "rw", R.string.lang_name_rw),
        SupportedLocale("ko", "ko", "ko", R.string.lang_name_ko),
        SupportedLocale("ku", "ku", "ku", R.string.lang_name_ku),
        SupportedLocale("ky", "ky", "ky", R.string.lang_name_ky),
        SupportedLocale("lo", "lo", "lo", R.string.lang_name_lo),
        SupportedLocale("lv", "lv", "lv", R.string.lang_name_lv),
        SupportedLocale("lt", "lt", "lt", R.string.lang_name_lt),
        SupportedLocale("lb", "lb", "lb", R.string.lang_name_lb),
        SupportedLocale("mk", "mk", "mk", R.string.lang_name_mk),
        SupportedLocale("mg", "mg", "mg", R.string.lang_name_mg),
        SupportedLocale("ms", "ms", "ms", R.string.lang_name_ms),
        SupportedLocale("ml", "ml", "ml", R.string.lang_name_ml),
        SupportedLocale("mt", "mt", "mt", R.string.lang_name_mt),
        SupportedLocale("mi", "mi", "mi", R.string.lang_name_mi),
        SupportedLocale("mr", "mr", "mr", R.string.lang_name_mr),
        SupportedLocale("mn", "mn", "mn", R.string.lang_name_mn),
        SupportedLocale("ne", "ne", "ne", R.string.lang_name_ne),
        SupportedLocale("nb", "nb", "nb", R.string.lang_name_nb),
        SupportedLocale("nn", "nn", "nn", R.string.lang_name_nn),
        SupportedLocale("or", "or", "or", R.string.lang_name_or),
        SupportedLocale("pl", "pl", "pl", R.string.lang_name_pl),
        SupportedLocale("pt-BR", "pt-BR", "pt-BR", R.string.lang_name_pt_br),
        SupportedLocale("pt-PT", "pt-PT", "pt-PT", R.string.lang_name_pt_pt),
        SupportedLocale("pa", "pa", "pa", R.string.lang_name_pa),
        SupportedLocale("qu", "qu", "qu", R.string.lang_name_qu),
        SupportedLocale("ro", "ro", "ro", R.string.lang_name_ro),
        SupportedLocale("rm", "rm", "rm", R.string.lang_name_rm),
        SupportedLocale("ru", "ru", "ru", R.string.lang_name_ru),
        SupportedLocale("sr", "sr", "sr", R.string.lang_name_sr),
        SupportedLocale("sr-Latn", "sr-Latn", "sr-Latn", R.string.lang_name_sr_latn),
        SupportedLocale("si", "si", "si", R.string.lang_name_si),
        SupportedLocale("sk", "sk", "sk", R.string.lang_name_sk),
        SupportedLocale("sl", "sl", "sl", R.string.lang_name_sl),
        SupportedLocale("so", "so", "so", R.string.lang_name_so),
        SupportedLocale("es-419", "es-419", "es-419", R.string.lang_name_es_419),
        SupportedLocale("es", "es", "es", R.string.lang_name_es),
        SupportedLocale("es-US", "es-US", "es-US", R.string.lang_name_es_us),
        SupportedLocale("su", "su", "su", R.string.lang_name_su),
        SupportedLocale("sw", "sw", "sw", R.string.lang_name_sw),
        SupportedLocale("sv", "sv", "sv", R.string.lang_name_sv),
        SupportedLocale("tg", "tg", "tg", R.string.lang_name_tg),
        SupportedLocale("ta", "ta", "ta", R.string.lang_name_ta),
        SupportedLocale("tt", "tt", "tt", R.string.lang_name_tt),
        SupportedLocale("te", "te", "te", R.string.lang_name_te),
        SupportedLocale("th", "th", "th", R.string.lang_name_th),
        SupportedLocale("tr", "tr", "tr", R.string.lang_name_tr),
        SupportedLocale("tk", "tk", "tk", R.string.lang_name_tk),
        SupportedLocale("uk", "uk", "uk", R.string.lang_name_uk),
        SupportedLocale("uz", "uz", "uz", R.string.lang_name_uz),
        SupportedLocale("vi", "vi", "vi", R.string.lang_name_vi),
        SupportedLocale("cy", "cy", "cy", R.string.lang_name_cy),
        SupportedLocale("xh", "xh", "xh", R.string.lang_name_xh),
        SupportedLocale("yo", "yo", "yo", R.string.lang_name_yo),
        SupportedLocale("zu", "zu", "zu", R.string.lang_name_zu),
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
