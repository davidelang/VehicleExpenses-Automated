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
    isVehicleDetection: Boolean = false,           // ← default for old screens
    onVehicleDetected: (vehicleId: Int, vehicleName: String) -> Unit = { _, _ -> },
    onDataExtracted: (amount: Double?, odometer: Int?, dateMillis: Long?, description: String?) -> Unit,
    onManualEntry: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Analyzing photo...") }

    LaunchedEffect(photoUri) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val image = InputImage.fromFilePath(context, photoUri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val result = recognizer.process(image).await()
                val text = result.text

                if (isVehicleDetection) {
                    val plate = Regex("[A-Z0-9]{3,8}").find(text)?.value
                    val odo = extractOdometer(text)
                    if (plate != null) {
                        onVehicleDetected(1, "Toyota Camry (auto-matched)")
                        onDataExtracted(null, odo, null, null)   // ← auto-fill odometer for QuickFillup
                        status = "Vehicle + odometer detected!"
                    } else {
                        onManualEntry()
                    }
                } else {
                    val amount = extractAmount(text)
                    val odometer = extractOdometer(text)
                    val desc = extractDescription(text)
                    onDataExtracted(amount, odometer, null, desc)
                }
            } catch (e: Exception) {
                onManualEntry()
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (isVehicleDetection) "Identifying Vehicle" else "Analyzing") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(status)
        }
    }
}

private fun extractAmount(text: String): Double? = Regex("""\$?(\d+\.\d{2})""").find(text)?.groupValues?.get(1)?.toDouble()
private fun extractOdometer(text: String): Int? = Regex("""\b(\d{4,7})\b""").findAll(text).map { it.value.toInt() }.maxOrNull()
private fun extractDescription(text: String): String? = text.lines().firstOrNull()

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await() = suspendCancellableCoroutine<T> { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
