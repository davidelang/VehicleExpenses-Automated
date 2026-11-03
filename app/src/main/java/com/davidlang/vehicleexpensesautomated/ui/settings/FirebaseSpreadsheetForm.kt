package com.davidlang.vehicleexpensesautomated.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.ui.util.SyncSetupDocs
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

@Composable
fun FirebaseSpreadsheetForm(
    projectId: String,
    onProjectIdChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    vehiclesCollection: String,
    onVehiclesCollectionChange: (String) -> Unit,
    expensesCollection: String,
    onExpensesCollectionChange: (String) -> Unit,
    fuelCollections: String,
    onFuelCollectionsChange: (String) -> Unit,
) {
    val context = LocalContext.current
    TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.tabular("firebase")) }) {
        Text(stringResource(R.string.settings_setup_help_firebase))
    }
    Text(
        stringResource(R.string.settings_create_firestore_collections_with_string_f) +
            stringResource(R.string.settings_use_a_short_lived_id_token_or_power_user_access_),
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = projectId,
        onValueChange = onProjectIdChange,
        label = { Text(stringResource(R.string.settings_firebase_project_id)) },
        supportingText = { Text(AppStrings.get(R.string.settings_e_g_my_vehicle_expenses)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        label = { Text(stringResource(R.string.settings_id_token_access_token)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = vehiclesCollection,
        onValueChange = onVehiclesCollectionChange,
        label = { Text(stringResource(R.string.settings_collection_fmt, TabularSchema.TAB_VEHICLES)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = expensesCollection,
        onValueChange = onExpensesCollectionChange,
        label = { Text(stringResource(R.string.settings_collection_fmt, TabularSchema.TAB_EXPENSES)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = fuelCollections,
        onValueChange = onFuelCollectionsChange,
        label = { Text(stringResource(R.string.settings_fuel_tab_collections_optional)) },
        supportingText = {
            Text(AppStrings.get(R.string.settings_one_per_line_fuel_vehiclename_collectionid))
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        minLines = 2,
    )
}