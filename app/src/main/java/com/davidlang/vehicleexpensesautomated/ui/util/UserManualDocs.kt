package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.content.Intent
import com.davidlang.vehicleexpensesautomated.ui.help.UserManualActivity

/**
 * Full illustrated user manual — **HTML**, not raw Markdown.
 *
 * Browsers do not render GitHub raw `.md` as a document with images. Browser-facing
 * and in-app readers always open **HTML**:
 * - In-app: packaged assets (`UserManualActivity` WebView) — offline, no login
 * - Web: [ONLINE_HTML_URL] (`docs/user-manual.html` on master via jsDelivr)
 *
 * Edit source remains `docs/user-manual.md`. After editing, run
 * `./scripts/render-user-manual.sh` to refresh HTML + assets.
 */
object UserManualDocs {
    private const val REPO = "davidelang/VehicleExpenses-Automated"

    /**
     * Public **HTML** manual (screenshots render). No GitHub login.
     * Available after `docs/user-manual.html` is on the public master branch.
     */
    const val ONLINE_HTML_URL =
        "https://cdn.jsdelivr.net/gh/$REPO@master/docs/user-manual.html"

    fun openFullManual(context: Context) {
        context.startActivity(Intent(context, UserManualActivity::class.java))
    }

    /** Open published web HTML in Custom Tabs (not raw .md). */
    fun openOnlineManual(context: Context) {
        SyncSetupDocs.open(context, ONLINE_HTML_URL)
    }
}
