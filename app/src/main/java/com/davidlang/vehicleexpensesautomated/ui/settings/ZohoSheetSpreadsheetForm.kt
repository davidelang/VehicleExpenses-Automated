package com.davidlang.vehicleexpensesautomated.ui.settings

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
        Text("Setup help — Zoho Sheet")
    }
    Text(
        "Register a Zoho API client (client-based or server-based). Sign in stores an OAuth access token app-privately. " +
            "Optional refresh token + client secret enable silent refresh before sync.",
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
        label = { Text("Workbook resource id") },
        supportingText = { Text("From the Zoho Sheet URL (open/<rid>)") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = clientId,
        onValueChange = onClientIdChange,
        label = { Text("OAuth client id") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = clientSecret,
        onValueChange = onClientSecretChange,
        label = { Text("Client secret (optional, for refresh)") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = accessToken,
        onValueChange = onAccessTokenChange,
        label = { Text("Access token") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = refreshToken,
        onValueChange = onRefreshTokenChange,
        label = { Text("Refresh token (optional)") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = vehiclesSheet,
        onValueChange = onVehiclesSheetChange,
        label = { Text("${TabularSchema.TAB_VEHICLES} worksheet name") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = expensesSheet,
        onValueChange = onExpensesSheetChange,
        label = { Text("${TabularSchema.TAB_EXPENSES} worksheet name") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = fuelSheets,
        onValueChange = onFuelSheetsChange,
        label = { Text("Fuel tab worksheet names (optional)") },
        supportingText = { Text("One per line: Fuel - VehicleName=SheetName") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        minLines = 2,
    )
}