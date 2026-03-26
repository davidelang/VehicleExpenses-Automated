package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun QuickFillupScreen(navController: NavHostController) {
    val viewModel: FuelViewModel = hiltViewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var step by remember { mutableStateOf(1) }
    var isMissedFill by remember { mutableStateOf(false) }
    var isPartialFill by remember { mutableStateOf(false) }
    var odometer by remember { mutableStateOf(0) }
    var gallons by remember { mutableStateOf(0.0) }
    var cost by remember { mutableStateOf(0.0) }

    // Vehicle selector (simple dropdown - matches original "Select vehicle" UI)
    var selectedVehicle by remember { mutableStateOf("Select vehicle") }
    val vehicles = listOf("My Truck", "Family Car", "Work Van") // will be replaced with real list from ViewModel in future iteration

    // Start camera immediately
    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        preview.setSurfaceProvider(previewView.surfaceProvider)
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Camera - already good for landscape
                Box(modifier = Modifier.weight(0.60f)) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                }
                // Controls - scrollable
                Column(
                    modifier = Modifier
                        .weight(0.40f)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ControlsContent(
                        step = step,
                        isMissedFill = isMissedFill,
                        isPartialFill = isPartialFill,
                        odometer = odometer,
                        gallons = gallons,
                        cost = cost,
                        selectedVehicle = selectedVehicle,
                        vehicles = vehicles,
                        onVehicleChange = { selectedVehicle = it },
                        onMissedChange = { isMissedFill = it },
                        onPartialChange = { isPartialFill = it },
                        onOdometerChange = { odometer = it },
                        onGallonsChange = { gallons = it },
                        onCostChange = { cost = it },
                        onStepChange = { step = it },
                        scope = scope,
                        viewModel = viewModel,
                        navController = navController
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Camera - FIXED small height in portrait (260.dp) so it CANNOT stay big
                Box(modifier = Modifier.height(260.dp)) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    Text(
                        text = "DEBUG: QuickFillupScreen.kt (portrait)",
                        color = Color.Red,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp)
                    )
                }
                // Controls - now gets the rest of the screen
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ControlsContent(
                        step = step,
                        isMissedFill = isMissedFill,
                        isPartialFill = isPartialFill,
                        odometer = odometer,
                        gallons = gallons,
                        cost = cost,
                        selectedVehicle = selectedVehicle,
                        vehicles = vehicles,
                        onVehicleChange = { selectedVehicle = it },
                        onMissedChange = { isMissedFill = it },
                        onPartialChange = { isPartialFill = it },
                        onOdometerChange = { odometer = it },
                        onGallonsChange = { gallons = it },
                        onCostChange = { cost = it },
                        onStepChange = { step = it },
                        scope = scope,
                        viewModel = viewModel,
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsContent(
    step: Int,
    isMissedFill: Boolean,
    isPartialFill: Boolean,
    odometer: Int,
    gallons: Double,
    cost: Double,
    selectedVehicle: String,
    vehicles: List<String>,
    onVehicleChange: (String) -> Unit,
    onMissedChange: (Boolean) -> Unit,
    onPartialChange: (Boolean) -> Unit,
    onOdometerChange: (Int) -> Unit,
    onGallonsChange: (Double) -> Unit,
    onCostChange: (Double) -> Unit,
    onStepChange: (Int) -> Unit,
    scope: CoroutineScope,
    viewModel: FuelViewModel,
    navController: NavHostController
) {
    // Vehicle pulldown at VERY TOP of controls
    ExposedDropdownMenuBox(
        expanded = false,
        onExpandedChange = {}
    ) {
        OutlinedTextField(
            value = selectedVehicle,
            onValueChange = {},
            label = { Text("Vehicle") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (step == 1) {
        Text("Step 1: Point at dashboard", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = odometer.toString(),
            onValueChange = { onOdometerChange(it.toIntOrNull() ?: 0) },
            label = { Text("Odometer") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Missed checkbox
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isMissedFill, onCheckedChange = onMissedChange)
            Text("Missed fill (unknown gas added)")
        }

        // Take Picture button - SMALLER + placed BETWEEN the two checkboxes
        Button(
            onClick = {
                scope.launch {
                    val result = OdometerOcrUtils.extractFromPhoto("dummy_dash.jpg")
                    onOdometerChange(result.odometer?.toIntOrNull() ?: odometer)
                    if (isMissedFill) {
                        val entry = FuelEntry(
                            vehicleId = 1,
                            odometer = odometer,
                            gallons = -1.0,
                            cost = -1.0,
                            timestamp = System.currentTimeMillis(),
                            isPartialFill = false
                        )
                        viewModel.saveFuel(entry)
                        navController.navigate("reports")
                    } else {
                        onStepChange(2)
                    }
                }
            },
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text("Take Dash Picture")
        }

        // Partial checkbox (directly after button)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isPartialFill, onCheckedChange = onPartialChange)
            Text("Partial fill")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { /* gallery only for import old pictures */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Advanced: Pick existing picture")
        }
    } else {
        Text("Step 2: Point at pump", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = gallons.toString(),
            onValueChange = { onGallonsChange(it.toDoubleOrNull() ?: 0.0) },
            label = { Text("Gallons") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = cost.toString(),
            onValueChange = { onCostChange(it.toDoubleOrNull() ?: 0.0) },
            label = { Text("Total Cost") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isPartialFill, onCheckedChange = onPartialChange)
            Text("Partial fill")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    val result = OdometerOcrUtils.extractFromPhoto("dummy_pump.jpg")
                    onGallonsChange(result.gallons?.toDoubleOrNull() ?: gallons)
                    onCostChange(result.cost?.toDoubleOrNull() ?: cost)
                    val entry = FuelEntry(
                        vehicleId = 1,
                        odometer = odometer,
                        gallons = gallons,
                        cost = cost,
                        timestamp = System.currentTimeMillis(),
                        isPartialFill = isPartialFill
                    )
                    viewModel.saveFuel(entry)
                    navController.navigate("reports")
                }
            },
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text("Take Pump Picture")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { /* gallery only */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Advanced: Pick existing picture")
        }
    }
}
