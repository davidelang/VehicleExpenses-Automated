package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.content.Intent
import com.davidlang.vehicleexpensesautomated.ui.help.UserManualActivity

/**
 * Full illustrated user manual — **HTML**, not raw Markdown.
 *
 * Primary path: **GitHub-hosted** HTML via jsDelivr for the **active app language**
 * (`AppLanguage.onlineManualHtmlUrl`). English uses root `docs/user-manual.html`;
 * other locales use `docs/i18n/<tag>/user-manual.html`.
 *
 * Optional offline English WebView assets remain under `assets/user-manual/` for
 * [openOfflineEnglishManual] (fallback / no network).
 *
 * Edit sources: `docs/user-manual.md` (en) and `docs/i18n/<tag>/user-manual.md`.
 * Regenerate: `./scripts/render-user-manual.sh`. Never send users raw `.md` blob URLs.
 * See `docs/reference/I18N.md` and `USER_MANUAL_BUILD.md`.
 */
object UserManualDocs {
    private const val REPO = "davidelang/VehicleExpenses-Automated"

    /**
     * Public **HTML** English manual (screenshots render). No GitHub login.
     * Available after `docs/user-manual.html` is on the public master branch.
     */
    const val ONLINE_HTML_URL =
        "https://cdn.jsdelivr.net/gh/$REPO@master/docs/user-manual.html"

    /**
     * Open the full manual for the **active app language** in Custom Tabs / browser
     * (hosted HTML). Prefer this for Help / About.
     */
    fun openFullManual(context: Context) {
        val url = AppLanguage.onlineManualHtmlUrl(context)
        SyncSetupDocs.open(context, url)
    }

    /** Packaged English offline WebView (optional; no network). */
    fun openOfflineEnglishManual(context: Context) {
        context.startActivity(Intent(context, UserManualActivity::class.java))
    }

    /** Open published English web HTML in Custom Tabs (not raw .md). */
    fun openOnlineManual(context: Context) {
        SyncSetupDocs.open(context, ONLINE_HTML_URL)
    }

    /** jsDelivr URL for a specific supported locale tag (e.g. `es`, `en`). */
    fun onlineManualHtmlUrlForTag(prefTag: String): String {
        val loc = AppLanguage.SUPPORTED.firstOrNull { it.prefTag == prefTag }
            ?: AppLanguage.SUPPORTED.first { it.prefTag == AppLanguage.EN }
        return AppLanguage.onlineManualHtmlUrlFor(loc)
    }
}
