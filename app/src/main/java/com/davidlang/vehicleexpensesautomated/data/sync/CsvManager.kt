package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.net.Uri
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.CsvZipSource
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.CsvZipTarget
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Thin UI wrapper over [TabularShareApi] CSV zip export/import (hidden adapter, not a sync destination). */
@Singleton
class CsvManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tabularApi: TabularShareApi,
) {
    private val downloadsDir = context.getExternalFilesDir("Downloads")!!

    suspend fun exportToZip(): Uri = withContext(Dispatchers.IO) {
        tabularApi.exportCsvZip(CsvZipTarget(downloadsDir)).uri
    }

    suspend fun importFromZip(uri: Uri) = withContext(Dispatchers.IO) {
        tabularApi.importCsvZip(CsvZipSource(uri))
    }
}