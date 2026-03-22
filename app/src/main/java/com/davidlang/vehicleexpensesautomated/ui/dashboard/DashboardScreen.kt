package com.davidlang.vehicleexpensesautomated.ui.dashboard

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val totalVehicles = viewModel.totalVehicles.collectAsState(initial = 0).value
    val totalExpenses = viewModel.totalExpenses.collectAsState(initial = 0.0).value
    val totalFuelCost = viewModel.totalFuelCost.collectAsState(initial = 0.0).value
    val totalGallons = viewModel.totalGallons.collectAsState(initial = 0.0).value
    val avgPricePerGallon = viewModel.avgPricePerGallon.collectAsState(initial = 0.0).value
    val roughAvgMPG = viewModel.roughAvgMPG.collectAsState(initial = 0.0).value
    val perVehicleSummary = viewModel.perVehicleSummary.collectAsState(initial = emptyList()).value

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { saveCsvToUri(context, it, viewModel) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importCsvFromUri(context, it, viewModel) }
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    TextButton(onClick = { exportLauncher.launch("vehicle-expenses-${System.currentTimeMillis()}.csv") }) {
                        Text("Export CSV")
                    }
                    TextButton(onClick = { importLauncher.launch("text/csv") }) {
                        Text("Import CSV")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("Overall Summary", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Vehicles: $totalVehicles")
                        Text("Total Expenses: $${"%.2f".format(totalExpenses)}")
                        Text("Total Fuel Cost: $${"%.2f".format(totalFuelCost)}")
                        Text("Total Gallons: ${"%.1f".format(totalGallons)}")
                        Text("Avg Price/Gal: $${"%.2f".format(avgPricePerGallon)}")
                        Text("Rough Avg MPG: ${"%.1f".format(roughAvgMPG)}")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            item {
                Text("Per-Vehicle Breakdown", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
            }

            items(perVehicleSummary) { summary ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${summary.vehicle.make} ${summary.vehicle.model} (${summary.vehicle.year})", style = MaterialTheme.typography.titleMedium)
                        Text("Expenses: $${"%.2f".format(summary.totalExpense)}")
                        Text("Fuel Cost: $${"%.2f".format(summary.totalFuelCost)}")
                        Text("Gallons: ${"%.1f".format(summary.totalGallons)}")
                    }
                }
            }
        }
    }
}

private fun saveCsvToUri(context: Context, uri: Uri, viewModel: DashboardViewModel) {
    val expenses = viewModel.allExpenses.value
    val fills = viewModel.allFuelFills.value

    val csv = buildString {
        appendLine("Type,Date,VehicleID,Amount,Description,Gallons,PricePerGallon,Odometer,TotalCost")
        expenses.forEach { e ->
            appendLine("Expense,${Instant.ofEpochMilli(e.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()},${e.vehicleId},${e.amount},${e.description ?: ""},,,,")
        }
        fills.forEach { f ->
            appendLine("Fuel,${Instant.ofEpochMilli(f.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()},${f.vehicleId},,,${f.gallons},${f.pricePerGallon},${f.odometer},${f.totalCost}")
        }
    }

    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
}

private fun importCsvFromUri(context: Context, uri: Uri, viewModel: DashboardViewModel) {
    // TODO: implement import later (after you confirm the format)
    // For now, this is a stub so the button compiles
}
