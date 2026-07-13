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
        Text("Setup help — Firebase")
    }
    Text(
        "Create Firestore collections with string fields matching app headers (Sync ID first). " +
            "Use a short-lived ID token or power-user access token — never ship unrestricted service accounts in the APK.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = projectId,
        onValueChange = onProjectIdChange,
        label = { Text("Firebase project id") },
        supportingText = { Text("e.g. my-vehicle-expenses") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        label = { Text("ID token / access token") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = vehiclesCollection,
        onValueChange = onVehiclesCollectionChange,
        label = { Text("${TabularSchema.TAB_VEHICLES} collection") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = expensesCollection,
        onValueChange = onExpensesCollectionChange,
        label = { Text("${TabularSchema.TAB_EXPENSES} collection") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = fuelCollections,
        onValueChange = onFuelCollectionsChange,
        label = { Text("Fuel tab collections (optional)") },
        supportingText = {
            Text("One per line: Fuel - VehicleName=collectionId")
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        minLines = 2,
    )
}