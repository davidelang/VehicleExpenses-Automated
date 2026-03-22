package com.davidlang.vehicleexpensesautomated.ui.conflict

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ConflictResolutionScreen(
    conflictType: String,
    localData: String,
    sheetData: String,
    onResolve: (String) -> Unit,   // "keep_local" | "keep_sheet" | "merge" | "keep_both"
    navController: NavController
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Conflict Detected") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Conflict Type: $conflictType", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))

            Text("Local version:", style = MaterialTheme.typography.titleMedium)
            Text(localData, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Sheet version:", style = MaterialTheme.typography.titleMedium)
            Text(sheetData, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(32.dp))

            Text("How would you like to resolve this?", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { onResolve("keep_local"); navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Keep Local Version")
            }
            Button(onClick = { onResolve("keep_sheet"); navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Keep Sheet Version")
            }
            Button(onClick = { onResolve("merge"); navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Merge Both (Recommended)")
            }
            Button(onClick = { onResolve("keep_both"); navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Keep Both as Separate Entries")
            }
        }
    }
}
