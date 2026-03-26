package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel   // updated import
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import kotlinx.coroutines.launch

@Composable
fun FuelEntryScreen(vehicleId: Int = 0, navController: NavHostController? = null) {
    val viewModel: FuelViewModel = hiltViewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    var step by remember { mutableStateOf(1) } // 1 = dash, 2 = pump
    var isMissedFill by remember { mutableStateOf(false) }
    var isPartialFill by remember { mutableStateOf(false) }
    var odometer by remember { mutableStateOf(0) }
    var gallons by remember { mutableStateOf(0.0) }
    var cost by remember { mutableStateOf(0.0) }

    val advancedMode = true

    // Create PreviewView once (this fixes the factory type issue)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Start camera immediately
    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        cameraProvider = provider
        val preview = Preview.Builder().build()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        preview.setSurfaceProvider(previewView.surfaceProvider)
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Camera half (top in portrait, left in landscape)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { _ -> previewView },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Controls half
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            if (step == 1) {
                Text("Step 1: Point at dashboard", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = odometer.toString(),
                    onValueChange = { odometer = it.toIntOrNull() ?: 0 },
                    label = { Text("Odometer") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row {
                    Checkbox(checked = isMissedFill, onCheckedChange = { isMissedFill = it })
                    Text("Missed fill (unknown gas added)")
                }
                Row {
                    Checkbox(checked = isPartialFill, onCheckedChange = { isPartialFill = it })
                    Text("Partial fill")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    scope.launch {
                        val result = OdometerOcrUtils.extractFromPhoto("dummy_dash.jpg") // real capture will replace this
                        odometer = result.odometer?.toIntOrNull() ?: odometer
                        if (isMissedFill) {
                            val entry = FuelEntry(
                                vehicleId = vehicleId,
                                odometer = odometer,
                                gallons = -1.0,
                                cost = -1.0,
                                timestamp = System.currentTimeMillis(),
                                isPartialFill = false
                            )
                            viewModel.saveFuel(entry)
                            navController?.navigate("reports")
                        } else {
                            step = 2
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Take Dash Picture")
                }

                if (advancedMode) {
                    Button(onClick = { /* pick existing picture */ }, modifier = Modifier.fillMaxWidth()) {
                        Text("Advanced: Pick existing picture")
                    }
                }
            } else {
                Text("Step 2: Point at pump", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = gallons.toString(), onValueChange = { gallons = it.toDoubleOrNull() ?: 0.0 }, label = { Text("Gallons") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = cost.toString(), onValueChange = { cost = it.toDoubleOrNull() ?: 0.0 }, label = { Text("Cost") }, modifier = Modifier.fillMaxWidth())

                Row {
                    Checkbox(checked = isPartialFill, onCheckedChange = { isPartialFill = it })
                    Text("Partial fill")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    scope.launch {
                        val result = OdometerOcrUtils.extractFromPhoto("dummy_pump.jpg")
                        gallons = result.gallons?.toDoubleOrNull() ?: gallons
                        cost = result.cost?.toDoubleOrNull() ?: cost

                        val entry = FuelEntry(
                            vehicleId = vehicleId,
                            odometer = odometer,
                            gallons = gallons,
                            cost = cost,
                            timestamp = System.currentTimeMillis(),
                            isPartialFill = isPartialFill
                        )
                        viewModel.saveFuel(entry)
                        navController?.navigate("reports")
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Take Pump Picture")
                }

                if (advancedMode) {
                    Button(onClick = { /* pick existing picture */ }, modifier = Modifier.fillMaxWidth()) {
                        Text("Advanced: Pick existing picture")
                    }
                }
            }
        }
    }
}
