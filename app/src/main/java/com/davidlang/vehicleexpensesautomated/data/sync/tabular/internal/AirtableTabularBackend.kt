package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AirtableTabularBackend @Inject constructor(
    client: AirtableClient,
) : RowDbTabularBackend(client, SpreadsheetProvider.AIRTABLE, "airtable") {

    override fun parseConfig(dest: SpreadsheetDestination): RowDbTabularConfig? {
        val parsed = super.parseConfig(dest)
        return parsed?.copy(baseUrl = parsed.baseUrl.ifBlank { "https://api.airtable.com" })
    }
}