package com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal

/** Low-level row CRUD for a single remote table/collection. */
interface RowDbTableClient {
    suspend fun listFieldMaps(config: RowDbTabularConfig, tableId: String): List<Pair<String, Map<String, String>>>

    suspend fun createRow(
        config: RowDbTabularConfig,
        tableId: String,
        headers: List<String>,
        row: List<String>,
    ): String

    suspend fun updateRow(
        config: RowDbTabularConfig,
        tableId: String,
        rowId: String,
        headers: List<String>,
        row: List<String>,
    )

    suspend fun deleteRow(config: RowDbTabularConfig, tableId: String, rowId: String)
}