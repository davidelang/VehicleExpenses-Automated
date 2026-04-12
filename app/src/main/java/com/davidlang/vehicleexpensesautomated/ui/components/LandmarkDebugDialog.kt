package com.davidlang.vehicleexpensesautomated.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Reference OCR Check", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.titleLarge)
                    }
                }
                
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
                    val imgW = bitmap.width.toFloat()
                    val imgH = bitmap.height.toFloat()
                    
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(imgW / imgH)
                        .background(Color.Black)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val containerW = size.width
                            val containerH = size.height
                            
                            val scale = minOf(containerW / imgW, containerH / imgH)
                            val dw = imgW * scale
                            val dh = imgH * scale
                            val offsetX = (containerW - dw) / 2
                            val offsetY = (containerH - dh) / 2

                            drawImage(
                                image = bitmap.asImageBitmap(),
                                dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt())
                            )

                            // Odometer (Blue)
                            odometerCrop?.let {
                                val rect = Offset(offsetX + it.left * dw, offsetY + it.top * dh)
                                val boxSize = androidx.compose.ui.geometry.Size(it.width * dw, it.height * dh)
                                drawRect(color = Color.Blue, topLeft = rect, size = boxSize, style = Stroke(4f))
                                
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "ODO: $odometerText",
                                    topLeft = rect.copy(y = (rect.y - 18.dp.toPx()).coerceAtLeast(0f)),
                                    style = androidx.compose.ui.text.TextStyle(color = Color.Cyan, fontSize = 11.sp, background = Color.Black.copy(alpha = 0.8f))
                                )
                            }

                            // Other Text (Green)
                            otherTextCrop?.let {
                                val rect = Offset(offsetX + it.left * dw, offsetY + it.top * dh)
                                val boxSize = androidx.compose.ui.geometry.Size(it.width * dw, it.height * dh)
                                drawRect(color = Color.Green, topLeft = rect, size = boxSize, style = Stroke(4f))
                            }

                            // Landmarks (Red)
                            landmarks.forEach { lm ->
                                val nx = lm.boundingBox.left / 1500f
                                val ny = lm.boundingBox.top / (imgH * (1500f / imgW))
                                val nw = (lm.boundingBox.right - lm.boundingBox.left) / 1500f
                                val nh = (lm.boundingBox.bottom - lm.boundingBox.top) / (imgH * (1500f / imgW))
                                
                                val rect = Offset(offsetX + nx * dw, offsetY + ny * dh)
                                drawRect(color = Color.Red, topLeft = rect, size = androidx.compose.ui.geometry.Size(nw * dw, nh * dh), style = Stroke(2f))

                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = lm.text,
                                    topLeft = rect,
                                    style = androidx.compose.ui.text.TextStyle(color = Color.Yellow, fontSize = 8.sp, background = Color.Black.copy(alpha = 0.7f))
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("Image loading error")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Detected Odometer: $odometerText", style = MaterialTheme.typography.titleMedium, color = Color.Blue)
                
                Text("Discovered Landmarks (${landmarks.size}):", style = MaterialTheme.typography.titleSmall)
                
                // MULTI-COLUMN GRID: Adapts based on screen width
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(landmarks) { lm ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                Text(lm.text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text("${"%.1f".format(lm.angle)}°", style = androidx.compose.ui.text.TextStyle(fontSize = 8.sp, color = MaterialTheme.colorScheme.secondary))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}
