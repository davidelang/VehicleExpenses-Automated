package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

/** PocketBase via remotetable AAR. */
@Singleton
class PocketBaseTabularBackend @Inject constructor() :
    RemoteTableRowDbTabularBackend(SpreadsheetProvider.POCKETBASE, "pocketbase")
