package com.davidlang.vehicleexpensesautomated.ui.settings

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.ui.util.SyncSetupDocs

@Composable
fun ZohoSheetSpreadsheetForm(
    workbookId: String,
    onWorkbookIdChange: (String) -> Unit,
    clientId: String,
    onClientIdChange: (String) -> Unit,
    clientSecret: String,
    onClientSecretChange: (String) -> Unit,
    accessToken: String,
    onAccessTokenChange: (String) -> Unit,
    refreshToken: String,
    onRefreshTokenChange: (String) -> Unit,
    vehiclesSheet: String,
    onVehiclesSheetChange: (String) -> Unit,
    expensesSheet: String,
    onExpensesSheetChange: (String) -> Unit,
    fuelSheets: String,
    onFuelSheetsChange: (String) -> Unit,
    signedIn: Boolean,
    onSignIn: () -> Unit,
) {
    val context = LocalContext.current
    TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.tabular("zoho-sheet")) }) {
        Text(stringResource(R.string.settings_setup_help_zoho_sheet))
    }
    Text(
        "Register a Zoho API client (client-based or server-based). Sign in stores an OAuth access token app-privately. " +
            stringResource(R.string.settings_optional_refresh_token_client_secret_enable_sile),
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
        Text(if (signedIn) "Signed in with Zoho (re-sign in)" else "Sign in with Zoho")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = workbookId,
        onValueChange = onWorkbookIdChange,
        label = { Text(stringResource(R.string.settings_workbook_resource_id)) },
        supportingText = { Text(stringResource(R.string.settings_from_the_zoho_sheet_url_open_rid)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = clientId,
        onValueChange = onClientIdChange,
        label = { Text(stringResource(R.string.settings_oauth_client_id)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = clientSecret,
        onValueChange = onClientSecretChange,
        label = { Text(stringResource(R.string.settings_client_secret_optional_for_refresh)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = accessToken,
        onValueChange = onAccessTokenChange,
        label = { Text(stringResource(R.string.settings_access_token)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = refreshToken,
        onValueChange = onRefreshTokenChange,
        label = { Text(stringResource(R.string.settings_refresh_token_optional)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = vehiclesSheet,
        onValueChange = onVehiclesSheetChange,
        label = {
            Text(
                stringResource(R.string.settings_worksheet_name_fmt, TabularSchema.TAB_VEHICLES),
            )
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = expensesSheet,
        onValueChange = onExpensesSheetChange,
        label = {
            Text(
                stringResource(R.string.settings_worksheet_name_fmt, TabularSchema.TAB_EXPENSES),
            )
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = fuelSheets,
        onValueChange = onFuelSheetsChange,
        label = { Text(stringResource(R.string.settings_fuel_tab_worksheet_names_optional)) },
        supportingText = { Text(stringResource(R.string.settings_one_per_line_fuel_vehiclename_sheetname)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        minLines = 2,
    )
}