package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * String lookup for non-composable call sites (catalog init, toasts outside composition).
 * [install] stores the application context. [get] resolves on [AppLanguage.wrap] of that
 * context, so a later pref change is not stuck on the locale from startup.
 */
object AppStrings {
    private lateinit var app: Context

    fun install(context: Context) {
        app = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any?): String {
        val safe = Array<Any>(args.size) { args[it] ?: "" }
        return AppLanguage.wrap(app).getString(id, *safe)
    }
}
