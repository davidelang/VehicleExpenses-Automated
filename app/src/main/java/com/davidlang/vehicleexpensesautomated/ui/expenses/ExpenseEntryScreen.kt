package com.davidlang.vehicleexpensesautomated.ui.expenses

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import kotlinx.coroutines.launch

@Composable
fun ExpenseEntryScreen(navController: NavController? = null) {
    val viewModel: ExpenseViewModel = hiltViewModel()
    val context = LocalContext.current
    val photoStorageManager = remember { PhotoStorageManager(context) }
    val scope = rememberCoroutineScope()

    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("New Expense", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isUploading = true
                scope.launch {
                    val fakeUri = Uri.parse("content://com.davidlang.vehicleexpensesautomated.test/expense-receipt.jpg")
                    val uploadedUrl = photoStorageManager.savePhoto(fakeUri, "expense_${System.currentTimeMillis()}.jpg", PhotoType.EXPENSE)
                    photoUrl = uploadedUrl
                    isUploading = false
                }
            },
            enabled = !isUploading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isUploading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("📸 Take / Choose Receipt Photo")
        }

        if (photoUrl != null) Text("✅ Photo uploaded: $photoUrl", color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    val entry = ExpenseEntry(
                        vehicleId = 0,
                        amount = amount.toDoubleOrNull() ?: 0.0,
                        description = description,
                        date = System.currentTimeMillis(),
                        photoUrl = photoUrl
                    )
                    viewModel.saveExpense(entry)
                    navController?.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Expense")
        }
    }
}
