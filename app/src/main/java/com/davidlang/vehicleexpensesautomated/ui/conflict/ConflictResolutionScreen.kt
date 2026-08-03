package com.davidlang.vehicleexpensesautomated.ui.conflict

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.ui.res.stringResource

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
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.conflict_conflict_detected)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Conflict Type: $conflictType", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))

            Text(stringResource(R.string.conflict_local_version), style = MaterialTheme.typography.titleMedium)
            Text(localData, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.conflict_sheet_version), style = MaterialTheme.typography.titleMedium)
            Text(sheetData, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(32.dp))

            Text(stringResource(R.string.conflict_how_would_you_like_to_resolve_this), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { onResolve("keep_local"); navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.conflict_keep_local_version))
            }
            Button(onClick = { onResolve("keep_sheet"); navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.conflict_keep_sheet_version))
            }
            Button(onClick = { onResolve("merge"); navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.conflict_merge_both_recommended))
            }
            Button(onClick = { onResolve("keep_both"); navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.conflict_keep_both_as_separate_entries))
            }
        }
    }
}
