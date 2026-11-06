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
import com.davidlang.vehicleexpensesautomated.ui.util.S

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
        Text(S("Setup help — Firebase"))
    }
    Text(
        S("Create Firestore collections with string fields matching app headers (Sync ID first). ") +
            S("Use a short-lived ID token or power-user access token — never ship unrestricted service accounts in the APK."),
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = projectId,
        onValueChange = onProjectIdChange,
        label = { Text(S("Firebase project id")) },
        supportingText = { Text(S("e.g. my-vehicle-expenses")) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        label = { Text(S("ID token / access token")) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = vehiclesCollection,
        onValueChange = onVehiclesCollectionChange,
        label = { Text(S("%1\$s collection", TabularSchema.TAB_VEHICLES)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = expensesCollection,
        onValueChange = onExpensesCollectionChange,
        label = { Text(S("%1\$s collection", TabularSchema.TAB_EXPENSES)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = fuelCollections,
        onValueChange = onFuelCollectionsChange,
        label = { Text(S("Fuel tab collections (optional)")) },
        supportingText = {
            Text(S("One per line: Fuel - VehicleName=collectionId"))
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        minLines = 2,
    )
}