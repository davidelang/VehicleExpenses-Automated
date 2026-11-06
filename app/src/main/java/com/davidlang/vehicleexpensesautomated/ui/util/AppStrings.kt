package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import androidx.annotation.StringRes
import java.util.Locale

/**
 * String lookup. [install] stores the application context.
 * [S] resolves on [AppLanguage.wrap] of that context, so a pref change is picked up
 * on the next read. See docs/facts/translations.md.
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

fun S(sentence: String, vararg args: Any?): String {
    val id = I18nSentences.id(sentence)
    if (id == 0) return formatEnglish(sentence, args)
    return AppStrings.get(id, *args)
}

/** Plural helper next to [S]. No call site uses this. One quantity uses [one]; every other count uses [other]. */
fun P(count: Int, one: String, other: String, vararg args: Any?): String {
    val sentence = if (count == 1) one else other
    return S(sentence, *args)
}

private fun formatEnglish(sentence: String, args: Array<out Any?>): String {
    if (args.isEmpty()) return sentence
    val safe = Array<Any>(args.size) { args[it] ?: "" }
    return String.format(Locale.getDefault(), sentence, *safe)
}
