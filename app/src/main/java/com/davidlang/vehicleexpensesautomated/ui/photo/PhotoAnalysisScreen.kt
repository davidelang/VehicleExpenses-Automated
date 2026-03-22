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
import kotlinx.coroutines.launch

@Composable
fun PhotoAnalysisScreen(
    photoUri: Uri,
    onDataExtracted: (amount: Double?, odometer: Int?, dateMillis: Long?, description: String?) -> Unit,
    onManualEntry: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Analyzing photo...") }

    LaunchedEffect(photoUri) {
        coroutineScope.launch {
            try {
                val image = InputImage.fromFilePath(context, photoUri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val result = recognizer.process(image).await()

                val text = result.text
                val amount = extractAmount(text)
                val odometer = extractOdometer(text)
                val dateMillis = extractDateMillis(text)
                val description = extractDescription(text)

                if (amount != null && (odometer != null || dateMillis != null)) {
                    status = "Auto-detected successfully"
                    onDataExtracted(amount, odometer, dateMillis, description)
                } else {
                    status = "OCR uncertain — showing confirmation"
                    // Only show screen if ambiguous (this is the rare case)
                    onManualEntry()
                }
            } catch (e: Exception) {
                status = "Analysis failed"
                onManualEntry()
            }
        }
    }

    // This screen is almost never shown (invisible background process)
    Scaffold(topBar = { TopAppBar(title = { Text("Analyzing Photo") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(status)
        }
    }
}

// Simple regex-based extractors (can be improved later)
private fun extractAmount(text: String): Double? = Regex("""\$?(\d+\.\d{2})""").find(text)?.groupValues?.get(1)?.toDouble()
private fun extractOdometer(text: String): Int? = Regex("""(\d{4,6})""").findAll(text).map { it.value.toInt() }.maxOrNull()
private fun extractDateMillis(text: String): Long? = System.currentTimeMillis() // placeholder — improve with regex
private fun extractDescription(text: String): String? = text.lines().firstOrNull { it.length > 10 }
