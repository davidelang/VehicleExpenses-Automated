package com.davidlang.vehicleexpensesautomated.ui.photo

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch

@Composable
fun PhotoAnalysisScreen(
    photoUri: Uri,
    onDataExtracted: (amount: Double?, date: Long?, description: String?) -> Unit,
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
                val date = extractDate(text)
                val description = extractDescription(text)

                status = "Extracted: Amount=$amount, Date=$date"
                onDataExtracted(amount, date, description)
            } catch (e: Exception) {
                status = "OCR failed — falling back to manual"
                onManualEntry()
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Photo Analysis") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(status, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onManualEntry) {
                Text("Enter Data Manually")
            }
        }
    }
}

// Simple regex-based extractors (can be improved with more ML later)
private fun extractAmount(text: String): Double? = Regex("""\$?(\d+\.\d{2})""").find(text)?.groupValues?.get(1)?.toDouble()
private fun extractDate(text: String): Long? = Regex("""(\d{1,2}/\d{1,2}/\d{2,4})""").find(text)?.let { /* parse to millis */ System.currentTimeMillis() }
private fun extractDescription(text: String): String? = text.lines().firstOrNull { it.length > 10 }
