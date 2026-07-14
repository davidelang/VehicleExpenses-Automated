package com.davidlang.vehicleexpensesautomated.ui.expenses

/** Typed navigation mode — edit always carries a concrete row id. */
sealed interface ExpenseEntryMode {
    data object Create : ExpenseEntryMode
    data class Edit(val id: Long) : ExpenseEntryMode

    companion object {
        fun fromRoute(expenseId: Long?): ExpenseEntryMode =
            if (expenseId != null && expenseId > 0) Edit(expenseId) else Create
    }
}