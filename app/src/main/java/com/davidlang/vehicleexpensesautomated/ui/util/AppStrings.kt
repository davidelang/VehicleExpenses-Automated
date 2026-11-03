package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * String lookup for non-composable call sites (catalog init, toasts outside composition).
 * [install] runs from [com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication.onCreate]
 * after [AppLanguage.applyFromPrefs], so the application context already has the pack locale.
 */
object AppStrings {
    private lateinit var app: Context

    fun install(context: Context) {
        app = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any?): String {
        val safe = Array<Any>(args.size) { args[it] ?: "" }
        return app.getString(id, *safe)
    }
}
