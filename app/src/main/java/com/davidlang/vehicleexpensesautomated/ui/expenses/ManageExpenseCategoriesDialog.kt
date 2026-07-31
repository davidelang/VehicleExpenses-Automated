package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.expense.ExpenseCategories
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField

/**
 * Per-vehicle expense category catalog: reorder, rename, delete, add.
 * Does not rewrite historical [com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry.category] strings.
 */
@Composable
fun ManageExpenseCategoriesDialog(
    vehicle: Vehicle,
    onDismiss: () -> Unit,
    onSave: (Vehicle) -> Unit,
) {
    var categories by remember(vehicle.id, vehicle.expenseCategoriesJson) {
        mutableStateOf(ExpenseCategories.parse(vehicle.expenseCategoriesJson).toMutableList())
    }
    var newName by remember { mutableStateOf("") }
    var renameIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Expense categories — ${vehicle.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "First item is the new-expense default. Renames/deletes do not rewrite past expense Category strings.",
                    style = MaterialTheme.typography.bodySmall,
                )
                categories.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${index + 1}. $name",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(
                            onClick = {
                                categories = ExpenseCategories.moveUp(categories, index).toMutableList()
                            },
                            enabled = index > 0,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                categories = ExpenseCategories.moveDown(categories, index).toMutableList()
                            },
                            enabled = index < categories.lastIndex,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        TextButton(
                            onClick = {
                                renameIndex = index
                                renameText = name
                            },
                        ) { Text("Rename") }
                        IconButton(
                            onClick = {
                                if (categories.size <= 1) return@IconButton
                                categories = ExpenseCategories.removeAt(categories, index).toMutableList()
                                if (renameIndex == index) {
                                    renameIndex = null
                                } else if (renameIndex != null && renameIndex!! > index) {
                                    renameIndex = renameIndex!! - 1
                                }
                            },
                            enabled = categories.size > 1,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete category",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                if (renameIndex != null) {
                    CaretEnabledOutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("New name") },
                        singleLine = true,
                        showCaretButtons = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                val i = renameIndex ?: return@TextButton
                                categories = ExpenseCategories.rename(categories, i, renameText).toMutableList()
                                renameIndex = null
                            },
                        ) { Text("Apply rename") }
                        TextButton(onClick = { renameIndex = null }) { Text("Cancel") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CaretEnabledOutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New category") },
                        singleLine = true,
                        showCaretButtons = false,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            categories = ExpenseCategories.add(categories, newName).toMutableList()
                            newName = ""
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Add") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val json = ExpenseCategories.format(categories)
                    onSave(vehicle.copy(expenseCategoriesJson = json))
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
