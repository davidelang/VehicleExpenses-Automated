package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetDestination
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.AirtableTabularBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.BaserowTabularBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.DeferredTabularBackendStub
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.EtherCalcTabularBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.ExcelGraphTabularBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.FirebaseTabularBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.GoogleSheetsTabularBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.NocoDbTabularBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.OtherTabularBackendStub
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.PocketBaseTabularBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.SupabaseTabularBackend
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.ZohoSheetTabularBackend
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabularBackendRegistry @Inject constructor(
    private val googleSheetsBackend: GoogleSheetsTabularBackend,
    private val excelBackend: ExcelGraphTabularBackend,
    private val etherCalcBackend: EtherCalcTabularBackend,
    private val baserowBackend: BaserowTabularBackend,
    private val nocoDbBackend: NocoDbTabularBackend,
    private val pocketBaseBackend: PocketBaseTabularBackend,
    private val supabaseBackend: SupabaseTabularBackend,
    private val airtableBackend: AirtableTabularBackend,
    private val firebaseBackend: FirebaseTabularBackend,
    private val zohoSheetBackend: ZohoSheetTabularBackend,
    private val deferredStub: DeferredTabularBackendStub,
    private val otherStub: OtherTabularBackendStub,
) {
    fun forProvider(provider: SpreadsheetProvider): TabularShareBackend? = when (provider) {
        SpreadsheetProvider.GOOGLE_SHEETS -> googleSheetsBackend
        SpreadsheetProvider.EXCEL_GRAPH -> excelBackend
        SpreadsheetProvider.ETHERCALC -> etherCalcBackend
        SpreadsheetProvider.BASEROW -> baserowBackend
        SpreadsheetProvider.NOCODB -> nocoDbBackend
        SpreadsheetProvider.POCKETBASE -> pocketBaseBackend
        SpreadsheetProvider.SUPABASE -> supabaseBackend
        SpreadsheetProvider.AIRTABLE -> airtableBackend
        SpreadsheetProvider.FIREBASE -> firebaseBackend
        SpreadsheetProvider.ZOHO_SHEET -> zohoSheetBackend
        SpreadsheetProvider.ONLYOFFICE,
        SpreadsheetProvider.COLLABORA,
        -> deferredStub
        SpreadsheetProvider.OTHER -> otherStub
    }

    fun forDestination(dest: SpreadsheetDestination): TabularShareBackend? = forProvider(dest.provider)
}