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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.davidlang.vehicleexpensesautomated.ui.util.RectF
import com.davidlang.vehicleexpensesautomated.ui.util.TextBlock
import kotlin.math.min

@Composable
fun LandmarkDebugDialog(
    photoPath: String?,
    odometerCrop: Rect?,
    otherTextCrop: Rect?,
    landmarks: List<TextBlock>,
    rawDiscoveryBoxes: List<RectF> = emptyList(),
    odometerText: String,
    engineName: String = "Unknown",
    sourceWidth: Int = 1,
    sourceHeight: Int = 1,
    onDismiss: () -> Unit
) {
    if (photoPath == null) return
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    
    var showDiscovery by remember { mutableStateOf(rawDiscoveryBoxes.isNotEmpty()) }

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
                    if (rawDiscoveryBoxes.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Discovery", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = showDiscovery, onCheckedChange = { showDiscovery = it })
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.titleLarge)
                    }
                }
                
                val bitmap = remember(photoPath) {
                    try {
                        val options = BitmapFactory.Options().apply { inSampleSize = 1 } // Use full res for debug
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
                        .wrapContentHeight()
                        .background(Color.Black)
                    ) {
                        Canvas(modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(imgW / imgH)
                        ) {
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

                            // 1. Draw Raw Discovery Boxes (RED) - Already Normalized 0.0-1.0
                            if (showDiscovery) {
                                rawDiscoveryBoxes.forEach { box ->
                                    drawRect(
                                        color = Color.Red,
                                        topLeft = Offset(offsetX + box.left * dw, offsetY + box.top * dh),
                                        size = Size((box.right - box.left) * dw, (box.bottom - box.top) * dh),
                                        style = Stroke(2f)
                                    )
                                }
                            }

                            // User Crops (Blue/Green)
                            odometerCrop?.let {
                                val rect = Offset(offsetX + it.left * dw, offsetY + it.top * dh)
                                val boxSize = Size(it.width * dw, it.height * dh)
                                drawRect(color = Color.Blue, topLeft = rect, size = boxSize, style = Stroke(4f))
                            }

                            otherTextCrop?.let {
                                val rect = Offset(offsetX + it.left * dw, offsetY + it.top * dh)
                                val boxSize = Size(it.width * dw, it.height * dh)
                                drawRect(color = Color.Green, topLeft = rect, size = boxSize, style = Stroke(4f))
                            }

                            // 2. Draw Final Landmarks (YELLOW) - Convert pixels to normalized then to display
                            landmarks.forEach { lm ->
                                val nx = lm.boundingBox.left.toFloat() / sourceWidth.toFloat()
                                val ny = lm.boundingBox.top.toFloat() / sourceHeight.toFloat()
                                val nw = (lm.boundingBox.right - lm.boundingBox.left).toFloat() / sourceWidth.toFloat()
                                val nh = (lm.boundingBox.bottom - lm.boundingBox.top).toFloat() / sourceHeight.toFloat()
                                
                                val rect = Offset(offsetX + nx * dw, offsetY + ny * dh)
                                drawRect(color = Color.Yellow, topLeft = rect, size = Size(nw * dw, nh * dh), style = Stroke(2f))

                                if (lm.text.isNotBlank()) {
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = lm.text,
                                        topLeft = rect,
                                        style = androidx.compose.ui.text.TextStyle(color = Color.Yellow, fontSize = 8.sp, background = Color.Black.copy(alpha = 0.7f))
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("Image error")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Engine: $engineName", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Source: ${sourceWidth}x${sourceHeight}", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Discovered Landmarks (${landmarks.size}):", style = MaterialTheme.typography.titleSmall)
                
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
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
                                val angle = if (lm.angle.isNaN() || lm.angle.isInfinite()) 0f else lm.angle
                                val angleText = "%.1f°".format(angle)
                                Text(angleText, style = androidx.compose.ui.text.TextStyle(fontSize = 8.sp, color = MaterialTheme.colorScheme.secondary))
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
