package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExpenseListScreen(vehicleId: Int, vehicleName: String) {
    val viewModel: ExpenseViewModel = hiltViewModel(key = "expense_$vehicleId")

    val expenses = viewModel.expenses.collectAsState(initial = emptyList()).value

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("$vehicleName Expenses") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            items(expenses) { expense ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("$${expense.amount}", style = MaterialTheme.typography.titleMedium)
                        Text(expense.category, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Instant.ofEpochMilli(expense.dateMillis)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!expense.description.isNullOrBlank()) {
                            Text(expense.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (expenses.isEmpty()) {
                item {
                    Text("No expenses yet for this vehicle.", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            onSave = { amount, category, description, dateMillis ->
                viewModel.addExpense(amount, category, description, dateMillis)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun AddExpenseDialog(
    onSave: (Double, String, String?, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g. Maintenance)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                // Date is current time for demo
                Text("Date: ${Instant.ofEpochMilli(dateMillis).atZone(java.time.ZoneId.systemDefault())}")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                amount.toDoubleOrNull()?.let { amt ->
                    onSave(amt, category, description.ifBlank { null }, dateMillis)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
