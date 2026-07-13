package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTabularBackend @Inject constructor(
    client: FirebaseTabularClient,
) : RowDbTabularBackend(client, SpreadsheetProvider.FIREBASE, "firebase") {

    override fun parseConfig(dest: SpreadsheetDestination): RowDbTabularConfig? =
        FirebaseTabularConfig.parse(dest.configJson, dest.targetUrl)

    override fun isConfigured(dest: SpreadsheetDestination): Boolean =
        FirebaseTabularConfig.isConfigured(parseConfig(dest))
}