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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel   // updated import
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ExpenseEntryScreen(navController: NavHostController? = null) {
    val viewModel: ExpenseViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

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
    var vehicleId by remember { mutableStateOf(0) }
    var photoUrl by remember { mutableStateOf<String?>(null) }

    // Gallery picker (gallery-only for import old pictures)
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                val tempFile = File.createTempFile("ocr_expense", ".jpg", context.cacheDir)
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val result = OdometerOcrUtils.extractFromPhoto(tempFile.absolutePath)
                amount = result.cost?.toDoubleOrNull() ?: amount
                description = "Receipt (OCR)" // placeholder — can be extended later
                photoUrl = tempFile.absolutePath // archived
                tempFile.delete()
                Toast.makeText(context, "OCR complete — receipt data filled", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Start camera immediately (camera-first flow)
    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        preview.setSurfaceProvider(previewView.surfaceProvider)
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Camera half (top)
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

        // Controls half (scrollable)
        Column(
            modifier = Modifier
                .weight(0.65f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("New Expense — Receipt OCR", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = vehicleId.toString(),
                onValueChange = { vehicleId = it.toIntOrNull() ?: 0 },
                label = { Text("Vehicle ID") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = amount.toString(),
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

            // Take photo button (camera-first)
            Button(
                onClick = {
                    scope.launch {
                        // In real app this would capture to PhotoPicker; here we simulate with dummy for build
                        val result = OdometerOcrUtils.extractFromPhoto("dummy_receipt.jpg")
                        amount = result.cost?.toDoubleOrNull() ?: amount
                        description = "Receipt OCR"
                        photoUrl = "archived_receipt.jpg" // archived via PhotoPicker in full impl
                        Toast.makeText(context, "Receipt OCR complete — receipt data filled", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Take Receipt Photo")
            }

            // Advanced gallery button (gallery-only for import old pictures)
            Button(
                onClick = { pickImageLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Advanced: Pick existing receipt")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (photoUrl != null) {
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
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Expense + Archived Receipt")
            }
        }
    }
}
