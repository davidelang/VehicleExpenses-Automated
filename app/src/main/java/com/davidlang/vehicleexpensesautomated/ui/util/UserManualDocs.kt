package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.content.Intent
import com.davidlang.vehicleexpensesautomated.ui.help.UserManualActivity

/**
 * Full illustrated user manual.
 *
 * Opens an **in-app** HTML copy (assets) with screenshots. No network and no GitHub
 * login are required. Source of truth for content remains `docs/user-manual.md`
 * (and generated `docs/user-manual.html`); assets are packaged for offline reading.
 *
 * Optional online HTML (after publish to master), for sharing outside the app:
 * [ONLINE_HTML_URL].
 */
object UserManualDocs {
    private const val REPO = "davidelang/VehicleExpenses-Automated"

    /**
     * Public HTML build served via jsDelivr (renders images; no GitHub login).
     * Available only after `docs/user-manual.html` is on the public master branch.
     */
    const val ONLINE_HTML_URL =
        "https://cdn.jsdelivr.net/gh/$REPO@master/docs/user-manual.html"

    fun openFullManual(context: Context) {
        context.startActivity(Intent(context, UserManualActivity::class.java))
    }

    /** Open the published web HTML (Custom Tabs). Prefer [openFullManual] in-app. */
    fun openOnlineManual(context: Context) {
        SyncSetupDocs.open(context, ONLINE_HTML_URL)
    }
}
