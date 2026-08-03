package com.davidlang.vehicleexpensesautomated.ui.settings

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.SpreadsheetProvider
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.RowDbTabularConfig
import com.davidlang.vehicleexpensesautomated.ui.util.SyncSetupDocs

@Composable
fun RowDbSpreadsheetForm(
    provider: SpreadsheetProvider,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    databaseId: String,
    onDatabaseIdChange: (String) -> Unit,
    projectId: String,
    onProjectIdChange: (String) -> Unit,
    baseId: String,
    onBaseIdChange: (String) -> Unit,
    vehiclesTableId: String,
    onVehiclesTableIdChange: (String) -> Unit,
    expensesTableId: String,
    onExpensesTableIdChange: (String) -> Unit,
    fuelTableIds: String,
    onFuelTableIdsChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val docsStem = when (provider) {
        SpreadsheetProvider.BASEROW -> "baserow"
        SpreadsheetProvider.NOCODB -> "nocodb"
        SpreadsheetProvider.POCKETBASE -> "pocketbase"
        SpreadsheetProvider.SUPABASE -> "supabase-selfhost"
        SpreadsheetProvider.AIRTABLE -> "airtable"
        else -> "tabular/README"
    }
    TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.tabular(docsStem)) }) {
        Text(
            stringResource(
                R.string.settings_setup_help_provider_fmt,
                provider.displayLabel(),
            ),
        )
    }
    Text(stringResource(R.string.settings_create_tables_collections_with_fields_matching_a),
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        label = {
            Text(
                when (provider) {
                    SpreadsheetProvider.AIRTABLE -> "API URL (optional)"
                    else -> "Base URL"
                },
            )
        },
        supportingText = {
            Text(
                when (provider) {
                    SpreadsheetProvider.AIRTABLE -> "Leave blank to use api.airtable.com"
                    SpreadsheetProvider.SUPABASE -> "e.g. https://supabase.example.com"
                    else -> "e.g. https://your-server.example.com"
                },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        label = {
            Text(
                when (provider) {
                    SpreadsheetProvider.AIRTABLE -> "Personal access token"
                    SpreadsheetProvider.SUPABASE -> "API key (anon or service)"
                    SpreadsheetProvider.POCKETBASE -> "Admin auth token"
                    else -> "API token"
                },
            )
        },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    if (provider == SpreadsheetProvider.BASEROW) {
        OutlinedTextField(
            value = databaseId,
            onValueChange = onDatabaseIdChange,
            label = { Text(stringResource(R.string.settings_database_id_optional)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    if (provider == SpreadsheetProvider.NOCODB) {
        OutlinedTextField(
            value = projectId,
            onValueChange = onProjectIdChange,
            label = { Text(stringResource(R.string.settings_project_base_id_optional)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    if (provider == SpreadsheetProvider.AIRTABLE) {
        OutlinedTextField(
            value = baseId,
            onValueChange = onBaseIdChange,
            label = { Text(stringResource(R.string.settings_airtable_base_id)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    OutlinedTextField(
        value = vehiclesTableId,
        onValueChange = onVehiclesTableIdChange,
        label = {
            Text(stringResource(R.string.settings_table_id_fmt, TabularSchema.TAB_VEHICLES))
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = expensesTableId,
        onValueChange = onExpensesTableIdChange,
        label = {
            Text(stringResource(R.string.settings_table_id_fmt, TabularSchema.TAB_EXPENSES))
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = fuelTableIds,
        onValueChange = onFuelTableIdsChange,
        label = { Text(stringResource(R.string.settings_fuel_tab_table_ids_optional)) },
        supportingText = {
            Text(
                stringResource(
                    R.string.settings_one_per_line_fuel_table_id,
                    TabularSchema.FUEL_TAB_PREFIX,
                ),
            )
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        minLines = 2,
    )
}

fun buildRowDbConfigJson(
    provider: SpreadsheetProvider,
    baseUrl: String,
    token: String,
    databaseId: String,
    projectId: String,
    baseId: String,
    vehiclesTableId: String,
    expensesTableId: String,
    fuelTableIds: String,
): String {
    var config = RowDbTabularConfig(
        backendType = RowDbTabularConfig.backendTypeFor(provider),
        baseUrl = baseUrl.trim().trimEnd('/'),
        token = token.trim(),
        databaseId = databaseId.trim().toLongOrNull(),
        projectId = projectId.trim(),
        baseId = baseId.trim(),
    )
    if (vehiclesTableId.isNotBlank()) {
        config = config.withTable(TabularSchema.TAB_VEHICLES, vehiclesTableId.trim())
    }
    if (expensesTableId.isNotBlank()) {
        config = config.withTable(TabularSchema.TAB_EXPENSES, expensesTableId.trim())
    }
    fuelTableIds.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@forEach
        val parts = trimmed.split('=', limit = 2)
        if (parts.size == 2) {
            val tab = parts[0].trim()
            val tableId = parts[1].trim()
            if (tab.isNotBlank() && tableId.isNotBlank()) {
                config = config.withTable(tab, tableId)
            }
        }
    }
    return config.toJson()
}

fun hydrateRowDbFormFields(configJson: String, targetUrl: String, provider: SpreadsheetProvider): RowDbFormState {
    val parsed = RowDbTabularConfig.parse(configJson, targetUrl, provider.jsonValue)
    val fuelLines = parsed?.tables?.filterKeys { it.startsWith(TabularSchema.FUEL_TAB_PREFIX) }
        ?.entries?.joinToString("\n") { (tab, id) -> "$tab=$id" }
        .orEmpty()
    return RowDbFormState(
        baseUrl = parsed?.baseUrl.orEmpty(),
        token = parsed?.token.orEmpty(),
        databaseId = parsed?.databaseId?.toString().orEmpty(),
        projectId = parsed?.projectId.orEmpty(),
        baseId = parsed?.baseId.orEmpty(),
        vehiclesTableId = parsed?.tableIdForTab(TabularSchema.TAB_VEHICLES).orEmpty(),
        expensesTableId = parsed?.tableIdForTab(TabularSchema.TAB_EXPENSES).orEmpty(),
        fuelTableIds = fuelLines,
    )
}

data class RowDbFormState(
    val baseUrl: String = "",
    val token: String = "",
    val databaseId: String = "",
    val projectId: String = "",
    val baseId: String = "",
    val vehiclesTableId: String = "",
    val expensesTableId: String = "",
    val fuelTableIds: String = "",
)