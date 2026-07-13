package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaserowTabularBackend @Inject constructor(
    client: BaserowClient,
) : RowDbTabularBackend(client, SpreadsheetProvider.BASEROW, "baserow")