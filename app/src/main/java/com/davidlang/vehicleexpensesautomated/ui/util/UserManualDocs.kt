package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context

/**
 * Public full user manual (illustrated).
 *
 * Uses [raw.githubusercontent.com] so opening the manual does **not** require a GitHub
 * account (public repo content). Prefer Custom Tabs via [SyncSetupDocs.open] so the
 * GitHub mobile app does not intercept and force a login wall.
 *
 * Image paths in [docs/user-manual.md] use the same raw host so screenshots load when
 * the markdown is viewed outside the GitHub web UI.
 */
object UserManualDocs {
    private const val REPO_RAW =
        "https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master"

    /** Full illustrated manual (Markdown, public, no GitHub login). */
    fun fullManualUrl(): String = "$REPO_RAW/docs/user-manual.md"

    fun openFullManual(context: Context) {
        SyncSetupDocs.open(context, fullManualUrl())
    }
}
