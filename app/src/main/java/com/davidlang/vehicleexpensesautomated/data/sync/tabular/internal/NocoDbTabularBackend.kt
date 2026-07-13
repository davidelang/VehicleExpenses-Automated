package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NocoDbTabularBackend @Inject constructor(
    client: NocoDbClient,
) : RowDbTabularBackend(client, SpreadsheetProvider.NOCODB, "nocodb")