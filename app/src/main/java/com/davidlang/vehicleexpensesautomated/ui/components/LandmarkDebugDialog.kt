package com.davidlang.vehicleexpensesautomated.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Reference OCR Check", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onDismiss) {
                        Text("X", style = MaterialTheme.typography.titleLarge)
                    }
                }
                
                // Use a fixed height or aspect ratio Box to avoid "huge wasted space"
                Box(modifier = Modifier.fillMaxWidth().height(350.dp).background(Color.Black)) {
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
                                drawImage(
                                    image = bitmap.asImageBitmap(),
                                    dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                                    dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt())
                                )

                                odometerCrop?.let {
                                    val rect = Offset(offsetX + it.left * dw, offsetY + it.top * dh)
                                    val size = androidx.compose.ui.geometry.Size(it.width * dw, it.height * dh)
                                    drawRect(color = Color.Blue, topLeft = rect, size = size, style = Stroke(4f))
                                    
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = "ODO: $odometerText",
                                        topLeft = rect.copy(y = (rect.y - 20.dp.toPx()).coerceAtLeast(0f)),
                                        style = androidx.compose.ui.text.TextStyle(color = Color.Cyan, fontSize = 12.sp, background = Color.Black.copy(alpha = 0.7f))
                                    )
                                }

                                otherTextCrop?.let {
                                    drawRect(
                                        color = Color.Green,
                                        topLeft = Offset(offsetX + it.left * dw, offsetY + it.top * dh),
                                        size = androidx.compose.ui.geometry.Size(it.width * dw, it.height * dh),
                                        style = Stroke(4f)
                                    )
                                }

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
                                        style = androidx.compose.ui.text.TextStyle(color = Color.Yellow, fontSize = 9.sp, background = Color.Black.copy(alpha = 0.6f))
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Preview error", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Odometer Reading: $odometerText", style = MaterialTheme.typography.titleMedium, color = Color.Blue)
                
                Text("Discovered Landmarks (Red):", style = MaterialTheme.typography.titleSmall)
                // Expand the LazyColumn to take remaining space
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
                    items(landmarks) { lm ->
                        ListItem(
                            headlineContent = { Text(lm.text, style = MaterialTheme.typography.bodyMedium) },
                            supportingContent = { Text("Box: ${lm.boundingBox.toShortString()} | Angle: ${"%.1f".format(lm.angle)}°", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(0.dp)
                        )
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
