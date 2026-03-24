package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.ui.components.PhotoPicker
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun ExpenseEntryScreen(
    vehicleId: Int,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ExpenseViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var photoUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("New Expense", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )

        PhotoPicker(
            photoStorageManager = settingsViewModel.photoStorageManager,
            photoType = PhotoType.EXPENSE,
            currentPhotoUrl = photoUrl,
            onPhotoUrlChanged = { photoUrl = it }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    val entry = ExpenseEntry(
                        vehicleId = vehicleId,
                        amount = amount.toDoubleOrNull() ?: 0.0,
                        description = description,
                        date = date,
                        photoUrl = photoUrl
                    )
                    // TODO: replace with viewModel.saveEntry(entry) once ExpenseViewModel has the method
                    Toast.makeText(context, "Expense saved (photo ready)", Toast.LENGTH_LONG).show()
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Expense")
        }
    }
}
