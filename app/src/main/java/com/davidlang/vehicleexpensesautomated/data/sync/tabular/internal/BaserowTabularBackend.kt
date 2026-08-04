package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Baserow via remotetable AAR. */
@Singleton
class BaserowTabularBackend @Inject constructor() :
    RemoteTableRowDbTabularBackend(SpreadsheetProvider.BASEROW, "baserow")
