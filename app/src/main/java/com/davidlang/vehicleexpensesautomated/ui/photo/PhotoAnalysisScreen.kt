package com.davidlang.vehicleexpensesautomated.ui.photo

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PhotoAnalysisScreen(
    photoUri: Uri,
    isVehicleDetection: Boolean = false,
    onVehicleDetected: (vehicleId: Int, vehicleName: String) -> Unit = { _, _ -> },
    onDataExtracted: (amount: Double?, odometer: Int?, gallons: Double?, cost: Double?, dateMillis: Long?, description: String?) -> Unit,
    onManualEntry: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Analyzing photo...") }

    LaunchedEffect(photoUri) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Copy uri to temp file for Tesseract processing
                val tempFile = File.createTempFile("analysis_", ".jpg", context.cacheDir)
                context.contentResolver.openInputStream(photoUri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                val result = OdometerOcrUtils.extractFromPhoto(tempFile.absolutePath)
                val fullText = result.textBlocks.joinToString(" ") { it.text }
                tempFile.delete()

                if (isVehicleDetection) {
                    val plate = Regex("[A-Z0-9]{3,8}").find(fullText)?.value
                    val odo = extractOdometer(fullText)
                    if (plate != null) {
                        onVehicleDetected(1, "Auto-matched Vehicle")
                        onDataExtracted(null, odo, null, null, null, null)
                        status = "Vehicle + odometer detected!"
                    } else {
                        onManualEntry()
                    }
                } else {
                    val gallons = extractGallons(fullText)
                    val cost = extractCost(fullText)
                    val odo = extractOdometer(fullText)
                    val desc = extractDescription(fullText)
                    onDataExtracted(null, odo, gallons, cost, null, desc)
                    status = if (gallons != null && cost != null) "Data extracted!" else "Review values"
                }
            } catch (e: Exception) {
                Log.e("PhotoAnalysis", "Analysis failed", e)
                onManualEntry()
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (isVehicleDetection) "Vehicle Detection" else "Pump Analysis") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            @Suppress("DEPRECATION")
            Text(status, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun extractGallons(text: String): Double? = Regex("""(\d+\.\d+)\s*GAL""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toDouble()
private fun extractCost(text: String): Double? = Regex("""\$?(\d+\.\d{2})""").find(text)?.groupValues?.get(1)?.toDouble()
private fun extractOdometer(text: String): Int? = Regex("""\b(\d{4,7})\b""").findAll(text).map { it.value.toInt() }.maxOrNull()
private fun extractDescription(text: String): String? = text.lines().firstOrNull { it.length > 10 }
