package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTabularBackend @Inject constructor(
    client: SupabaseClient,
) : RowDbTabularBackend(client, SpreadsheetProvider.SUPABASE, "supabase")