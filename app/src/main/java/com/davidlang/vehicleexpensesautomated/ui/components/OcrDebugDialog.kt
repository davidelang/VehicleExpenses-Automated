package com.davidlang.vehicleexpensesautomated.ui.components

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.davidlang.vehicleexpensesautomated.ui.util.OcrResult
import java.io.File

@Composable
fun OcrDebugDialog(
    ocrResult: OcrResult,
    originalPhotoPath: String?,
    onDismiss: () -> Unit
) {
    val originalUri = originalPhotoPath?.let { path ->
        Log.d("OcrDebug", "Loading Original with URI: file://$path (exists = ${File(path).exists()})")
        Uri.fromFile(File(path))
    }

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
                        originalUri?.let {
                            Image(
                                painter = rememberAsyncImagePainter(it),
                                contentDescription = "Original image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentScale = ContentScale.Fit
                            )
                        } ?: Text("No original photo path")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cropped")
                        ocrResult.croppedBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Cropped image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentScale = ContentScale.Fit
                            )
                        } ?: Text("No cropped image")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OpenCV Processed")
                        ocrResult.openCvProcessedBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "OpenCV processed image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentScale = ContentScale.Fit
                            )
                        } ?: Text("No OpenCV processed image")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(ocrResult.debugText)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
