package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PocketBaseTabularBackend @Inject constructor(
    client: PocketBaseClient,
) : RowDbTabularBackend(client, SpreadsheetProvider.POCKETBASE, "pocketbase")