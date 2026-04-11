package com.davidlang.vehicleexpensesautomated.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.davidlang.vehicleexpensesautomated.ui.util.TextBlock
import java.io.File

@Composable
fun LandmarkDebugDialog(
    photoPath: String?,
    odometerCrop: Rect?,
    otherTextCrop: Rect?,
    landmarks: List<TextBlock>,
    odometerText: String,
    onDismiss: () -> Unit
) {
    if (photoPath == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reference OCR Check", style = MaterialTheme.typography.headlineSmall)
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val bitmap = remember(photoPath) {
                        val options = BitmapFactory.Options().apply { inSampleSize = 4 } // downscale for display
                        BitmapFactory.decodeFile(photoPath, options)
                    }

                    if (bitmap != null) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val imgW = bitmap.width.toFloat()
                            val imgH = bitmap.height.toFloat()
                            val containerW = maxWidth.value
                            val containerH = maxHeight.value
                            
                            // Simple fit center calculation
                            val scale = minOf(containerW / imgW, containerH / imgH)
                            val dw = imgW * scale
                            val dh = imgH * scale
                            val offsetX = (containerW - dw) / 2
                            val offsetY = (containerH - dh) / 2

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Draw Odometer (Blue)
                                odometerCrop?.let {
                                    drawRect(
                                        color = Color.Blue,
                                        topLeft = Offset(offsetX + it.left * dw, offsetY + it.top * dh),
                                        size = androidx.compose.ui.geometry.Size(it.width * dw, it.height * dh),
                                        style = Stroke(4f)
                                    )
                                }
                                // Draw Other Text (Green)
                                otherTextCrop?.let {
                                    drawRect(
                                        color = Color.Green,
                                        topLeft = Offset(offsetX + it.left * dw, offsetY + it.top * dh),
                                        size = androidx.compose.ui.geometry.Size(it.width * dw, it.height * dh),
                                        style = Stroke(4f)
                                    )
                                }
                                // Draw Landmarks (Red)
                                landmarks.forEach { lm ->
                                    // Landmarks are currently in pixels relative to 1500px scale
                                    // Need to convert to normalized 0-1 for this display
                                    val nx = lm.boundingBox.left / 1500f
                                    val ny = lm.boundingBox.top / (imgH * (1500f / imgW))
                                    val nw = (lm.boundingBox.right - lm.boundingBox.left) / 1500f
                                    val nh = (lm.boundingBox.bottom - lm.boundingBox.top) / (imgH * (1500f / imgW))
                                    
                                    drawRect(
                                        color = Color.Red,
                                        topLeft = Offset(offsetX + nx * dw, offsetY + ny * dh),
                                        size = androidx.compose.ui.geometry.Size(nw * dw, nh * dh),
                                        style = Stroke(2f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Odometer Reading: $odometerText", style = MaterialTheme.typography.titleMedium, color = Color.Blue)
                
                Text("Discovered Landmarks (Red):", style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.height(150.dp).fillMaxWidth()) {
                    items(landmarks) { lm ->
                        ListItem(
                            headlineContent = { Text(lm.text) },
                            supportingContent = { Text("Box: ${lm.boundingBox.toShortString()}") }
                        )
                    }
                }

                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}
