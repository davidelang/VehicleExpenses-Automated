package com.davidlang.vehicleexpensesautomated.ui.photo

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun PhotoAnalysisScreen(
    photoUri: Uri,
    isVehicleDetection: Boolean = false,
    onVehicleDetected: ((vehicleId: Int, vehicleName: String) -> Unit)? = null,
    onDataExtracted: (amount: Double?, odometer: Int?, dateMillis: Long?, description: String?) -> Unit,
    onManualEntry: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Analyzing photo...") }
    var isComplete by remember { mutableStateOf(false) }

    LaunchedEffect(photoUri) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                status = "Running OCR..."
                val image = InputImage.fromFilePath(context, photoUri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val result = recognizer.process(image).await()

                val text = result.text

                if (isVehicleDetection) {
                    // TODO: real DB lookup for license plate / VIN / make-model match
                    // For now: simple stub — replace with real vehicle repository call later
                    val plateMatch = Regex("""[A-Z0-9]{3,8}""").find(text)?.value
                    if (plateMatch != null) {
                        status = "Vehicle matched!"
                        onVehicleDetected?.invoke(1, "Toyota Camry (stub match)")
                        isComplete = true
                        return@launch
                    } else {
                        status = "No vehicle match — tap to create new?"
                        onManualEntry()
                        return@launch
                    }
                } else {
                    // existing fuel/expense OCR
                    val amount = extractAmount(text)
                    val odometer = extractOdometer(text)
                    val dateMillis = extractDateMillis(text)
                    val description = extractDescription(text)

                    if (amount != null || odometer != null) {
                        status = "Auto-detected successfully"
                        onDataExtracted(amount, odometer, dateMillis, description)
                        isComplete = true
                    } else {
                        status = "OCR uncertain — review needed"
                        onManualEntry()
                    }
                }
            } catch (e: Exception) {
                status = "Analysis failed"
                onManualEntry()
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (isVehicleDetection) "Identifying Vehicle" else "Analyzing Photo") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(status, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// (existing extract functions unchanged)
private fun extractAmount(text: String): Double? = Regex("""\$?(\d+\.\d{2})""").find(text)?.groupValues?.get(1)?.toDouble()
private fun extractOdometer(text: String): Int? = Regex("""(\d{4,6})""").findAll(text).map { it.value.toInt() }.maxOrNull()
private fun extractDateMillis(text: String): Long? = System.currentTimeMillis()
private fun extractDescription(text: String): String? = text.lines().firstOrNull { it.length > 10 }

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { exception -> continuation.resumeWithException(exception) }
}
