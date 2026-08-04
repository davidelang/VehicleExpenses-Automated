package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

/** NocoDB via remotetable AAR. */
@Singleton
class NocoDbTabularBackend @Inject constructor() :
    RemoteTableRowDbTabularBackend(SpreadsheetProvider.NOCODB, "nocodb")
