package com.davidlang.vehicleexpensesautomated.ui.components

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult

/**
 * Unified OCR Debug dialog used by both ManageVehiclesScreen and QuickFillupScreen.
 * Future image enhancement features should be added here (one place only).
 */
@Composable
fun OcrDebugDialog(
    ocrResult: OcrResult,
    originalPhotoPath: String?,
    onDismiss: () -> Unit
) {
    Log.d("OcrDebug", "Dialog rendered with originalPhotoPath = $originalPhotoPath (file exists = ${originalPhotoPath?.let { java.io.File(it).exists() } ?: false})")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OCR Debug") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Original")
                        originalPhotoPath?.let {
                            Image(
                                painter = rememberAsyncImagePainter(it),
                                contentDescription = "Original image",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cropped")
                        ocrResult.croppedBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Cropped image",
                                modifier = Modifier.fillMaxWidth()
                            )
                        } ?: Text("Cropped image (see logs for details)")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(ocrResult.debugText)

                // TODO: Image enhancement (add here so both screens get it automatically)
                // Example future button:
                // Button(onClick = { /* enhance logic */ }) { Text("Enhance Cropped Image") }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
