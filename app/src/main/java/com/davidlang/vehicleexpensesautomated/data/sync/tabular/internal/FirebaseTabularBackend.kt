package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Firebase/Firestore via remotetable AAR. */
@Singleton
class FirebaseTabularBackend @Inject constructor() :
    RemoteTableRowDbTabularBackend(SpreadsheetProvider.FIREBASE, "firebase") {

    override fun parseConfig(dest: SpreadsheetDestination): RowDbTabularConfig? =
        FirebaseTabularConfig.parse(dest.configJson, dest.targetUrl)

    override fun isConfigured(dest: SpreadsheetDestination): Boolean =
        FirebaseTabularConfig.isConfigured(parseConfig(dest))
}
