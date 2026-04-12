package com.davidlang.vehicleexpensesautomated.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()

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
                        try {
                            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                            if (photoPath.startsWith("content://")) {
                                context.contentResolver.openInputStream(Uri.parse(photoPath))?.use {
                                    BitmapFactory.decodeStream(it, null, options)
                                }
                            } else {
                                BitmapFactory.decodeFile(photoPath, options)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (bitmap != null) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val imgW = bitmap.width.toFloat()
                            val imgH = bitmap.height.toFloat()
                            val containerW = maxWidth.value
                            val containerH = maxHeight.value
                            
                            val scale = minOf(containerW / imgW, containerH / imgH)
                            val dw = imgW * scale
                            val dh = imgH * scale
                            val offsetX = (containerW - dw) / 2
                            val offsetY = (containerH - dh) / 2

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // 1. Draw the actual background image!
                                drawImage(
                                    image = bitmap.asImageBitmap(),
                                    dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                                    dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt())
                                )

                                // 2. Draw Odometer (Blue)
                                odometerCrop?.let {
                                    val rect = Offset(offsetX + it.left * dw, offsetY + it.top * dh)
                                    val size = androidx.compose.ui.geometry.Size(it.width * dw, it.height * dh)
                                    drawRect(color = Color.Blue, topLeft = rect, size = size, style = Stroke(4f))
                                    
                                    // Draw text label for odometer
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = "ODO: $odometerText",
                                        topLeft = rect.copy(y = rect.y - 20.dp.toPx()),
                                        style = androidx.compose.ui.text.TextStyle(color = Color.Blue, fontSize = 14.sp)
                                    )
                                }

                                // 3. Draw Other Text (Green)
                                otherTextCrop?.let {
                                    drawRect(
                                        color = Color.Green,
                                        topLeft = Offset(offsetX + it.left * dw, offsetY + it.top * dh),
                                        size = androidx.compose.ui.geometry.Size(it.width * dw, it.height * dh),
                                        style = Stroke(4f)
                                    )
                                }

                                // 4. Draw Landmarks (Red)
                                landmarks.forEach { lm ->
                                    // Landmarks are relative to 1500px scale pass
                                    val nx = lm.boundingBox.left / 1500f
                                    val ny = lm.boundingBox.top / (imgH * (1500f / imgW))
                                    val nw = (lm.boundingBox.right - lm.boundingBox.left) / 1500f
                                    val nh = (lm.boundingBox.bottom - lm.boundingBox.top) / (imgH * (1500f / imgW))
                                    
                                    val rect = Offset(offsetX + nx * dw, offsetY + ny * dh)
                                    drawRect(
                                        color = Color.Red,
                                        topLeft = rect,
                                        size = androidx.compose.ui.geometry.Size(nw * dw, nh * dh),
                                        style = Stroke(2f)
                                    )

                                    // Draw the actual text found
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = lm.text,
                                        topLeft = rect,
                                        style = androidx.compose.ui.text.TextStyle(color = Color.Yellow, fontSize = 10.sp, background = Color.Black.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Failed to load preview image", modifier = Modifier.align(Alignment.Center))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Odometer Reading: $odometerText", style = MaterialTheme.typography.titleMedium, color = Color.Blue)
                
                Text("Discovered Landmarks (Red):", style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.height(150.dp).fillMaxWidth()) {
                    items(landmarks) { lm ->
                        ListItem(
                            headlineContent = { Text(lm.text) },
                            supportingContent = { Text("Box: ${lm.boundingBox.toShortString()} | Angle: ${"%.1f".format(lm.angle)}°") }
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
