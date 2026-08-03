package com.davidlang.vehicleexpensesautomated.data.libs

/**
 * First-party library surface (M2): remotetable live backends + extractmail constants.
 * Tabular Sheets/excel-graph/EtherCalc route through remotetable AAR at runtime.
 */
object FirstPartyLibsProbe {
    fun remotetableBackendIds(): List<String> = listOf(
        com.davidelang.remotetable.BackendIds.GOOGLE_SHEETS,
        com.davidelang.remotetable.BackendIds.EXCEL_GRAPH,
        com.davidelang.remotetable.BackendIds.ETHERCALC,
        com.davidelang.remotetable.BackendIds.MOCK,
    )

    fun extractmailVersion(): Int = com.davidelang.extractmail.Extractmail.VERSION

    fun extractmailTypes(): List<String> = com.davidelang.extractmail.Extractmail.KNOWN_TYPES

    fun extractmailHostCliHint(): String = com.davidelang.extractmail.Extractmail.hostCliHint()
}

