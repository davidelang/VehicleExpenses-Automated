package com.davidlang.vehicleexpensesautomated.data.libs

/**
 * Compile-time link to first-party AARs (M1 pin). Full cutover of tabular/mail
 * stacks is phased; this probe ensures Gradle resolves the artifacts.
 */
object FirstPartyLibsProbe {
    fun remotetableBackendIds(): List<String> = listOf(
        com.davidelang.remotetable.BackendIds.GOOGLE_SHEETS,
        com.davidelang.remotetable.BackendIds.EXCEL_GRAPH,
        com.davidelang.remotetable.BackendIds.ETHERCALC,
    )

    fun extractmailVersion(): Int = com.davidelang.extractmail.Extractmail.VERSION
}
