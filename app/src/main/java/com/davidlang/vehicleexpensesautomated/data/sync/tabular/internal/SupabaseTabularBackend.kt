package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Supabase via remotetable AAR. */
@Singleton
class SupabaseTabularBackend @Inject constructor() :
    RemoteTableRowDbTabularBackend(SpreadsheetProvider.SUPABASE, "supabase")
