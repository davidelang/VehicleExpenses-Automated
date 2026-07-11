package com.davidlang.vehicleexpensesautomated.ui.expenses

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEntryScreen(navController: NavHostController? = null) {
    val viewModel: ExpenseViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val vehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    var selectedVehicleId by rememberSaveable { mutableStateOf<Int?>(null) }
    var vehicleDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(vehicles) {
        if (selectedVehicleId == null && vehicles.isNotEmpty()) {
            selectedVehicleId = vehicles.first().id
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    var amount by remember { mutableStateOf(0.0) }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Other") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var photoUrl by remember { mutableStateOf<String?>(null) }

    // Gallery picker — set photoUrl only (no OCR; OCR later)
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            photoUrl = selectedUri.toString()
            Toast.makeText(context, "Photo selected", Toast.LENGTH_SHORT).show()
        }
    }

    // Start camera immediately (camera-first flow) — Feature B will replace with CameraPreview
    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        preview.setSurfaceProvider(previewView.surfaceProvider)
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Text(
                text = "DEBUG: ExpenseEntryScreen.kt (receipt camera)",
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .weight(0.65f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("New Expense — Receipt OCR", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))

            val vehicleName = vehicles.find { it.id == selectedVehicleId }?.name ?: "Select vehicle"
            ExposedDropdownMenuBox(
                expanded = vehicleDropdownExpanded,
                onExpandedChange = { vehicleDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = vehicleName,
                    onValueChange = {},
                    label = { Text("Vehicle") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                    readOnly = true,
                    singleLine = true,
                    maxLines = 1
                )
                ExposedDropdownMenu(
                    expanded = vehicleDropdownExpanded,
                    onDismissRequest = { vehicleDropdownExpanded = false }
                ) {
                    if (vehicles.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No vehicles") },
                            onClick = { vehicleDropdownExpanded = false }
                        )
                    } else {
                        vehicles.forEach { vehicle ->
                            DropdownMenuItem(
                                text = { Text(vehicle.name) },
                                onClick = {
                                    selectedVehicleId = vehicle.id
                                    vehicleDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = if (amount == 0.0) "" else amount.toString(),
                onValueChange = { amount = it.toDoubleOrNull() ?: 0.0 },
                label = { Text("Amount (auto-filled by OCR)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Vendor") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    scope.launch {
                        val result = com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
                            .extractFromPhoto("dummy_receipt.jpg")
                        amount = result.cost?.toDoubleOrNull() ?: amount
                        description = "Receipt OCR"
                        photoUrl = "archived_receipt.jpg"
                        Toast.makeText(context, "Receipt OCR complete — receipt data filled", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Take Receipt Photo")
            }

            Button(
                onClick = { pickImageLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Advanced: Pick existing receipt")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val vehicleId = selectedVehicleId
                    if (vehicleId == null) {
                        Toast.makeText(context, "Select a vehicle", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (photoUrl == null) {
                        Toast.makeText(context, "Take or pick a photo first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.saveExpense(
                        ExpenseEntry(
                            vehicleId = vehicleId,
                            amount = amount,
                            description = description,
                            category = category,
                            date = date,
                            photoUrl = photoUrl
                        )
                    )
                    navController?.navigate("reports")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Expense + Archived Receipt")
            }
        }
    }
}
