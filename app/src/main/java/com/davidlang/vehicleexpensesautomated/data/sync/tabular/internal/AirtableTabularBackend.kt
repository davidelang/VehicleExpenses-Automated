package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Airtable via remotetable AAR. */
@Singleton
class AirtableTabularBackend @Inject constructor() :
    RemoteTableRowDbTabularBackend(SpreadsheetProvider.AIRTABLE, "airtable")
