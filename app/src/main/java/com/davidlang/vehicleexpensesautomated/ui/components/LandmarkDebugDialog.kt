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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    
    var showDiscovery by remember { mutableStateOf(true) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Discovery", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = showDiscovery, onCheckedChange = { showDiscovery = it })
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.titleLarge)
                    }
                }
                
                val bitmap = remember(photoPath) {
                    try {
                        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                        if (photoPath.startsWith("content://")) {
                            context.contentResolver.openInputStream(Uri.parse(photoPath))?.use {
                                BitmapFactory.decodeStream(it, null, options)
                            }
                        } else {
                            BitmapFactory.decodeFile(photoPath, options)
                        }
                    } catch (e: Exception) { null }
                }

                if (bitmap != null) {
                    val imgW = bitmap.width.toFloat()
                    val imgH = bitmap.height.toFloat()
                    
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp) // Fixed height for image area to ensure grid visibility
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

                            if (showDiscovery) {
                                // 1. RED TIER (Model Suspicion) - SOLID TINTED
                                landmarks.forEach { lm ->
                                    lm.rawDiscoveryBox?.let { box ->
                                        drawRect(
                                            color = Color.Red.copy(alpha = 0.15f),
                                            topLeft = Offset(offsetX + box.left * dw, offsetY + box.top * dh),
                                            size = Size((box.right - box.left) * dw, (box.bottom - box.top) * dh)
                                        )
                                    }
                                }

                                // 2. ORANGE TIER (ROI Expansion) - THICK STROKE
                                landmarks.forEach { lm ->
                                    lm.refinedDiscoveryBox?.let { box ->
                                        drawRect(
                                            color = Color(0xFFFF8C00), // Orange
                                            topLeft = Offset(offsetX + box.left * dw, offsetY + box.top * dh),
                                            size = Size((box.right - box.left) * dw, (box.bottom - box.top) * dh),
                                            style = Stroke(6f) // 3x thickness
                                        )
                                    }
                                }
                            }

                            // User Crops (Blue/Green)
                            odometerCrop?.let {
                                drawRect(color = Color.Blue, topLeft = Offset(offsetX + it.left * dw, offsetY + it.top * dh), size = Size(it.width * dw, it.height * dh), style = Stroke(4f))
                            }

                            // 3. YELLOW TIER (Final Landmark) - THIN STROKE
                            landmarks.forEach { lm ->
                                val nx = lm.boundingBox.left.toFloat() / sourceWidth.toFloat()
                                val ny = lm.boundingBox.top.toFloat() / sourceHeight.toFloat()
                                val nw = (lm.boundingBox.right - lm.boundingBox.left).toFloat() / sourceWidth.toFloat()
                                val nh = (lm.boundingBox.bottom - lm.boundingBox.top).toFloat() / sourceHeight.toFloat()
                                
                                val rect = Offset(offsetX + nx * dw, offsetY + ny * dh)
                                drawRect(color = Color.Yellow, topLeft = rect, size = Size(nw * dw, nh * dh), style = Stroke(2f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Engine: $engineName", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Source: ${sourceWidth}x${sourceHeight}", style = MaterialTheme.typography.labelMedium)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Discovery Pipeline Metrics:", style = MaterialTheme.typography.titleSmall)
                
                // Grouped Metrics Grid
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(landmarks) { lm ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(text = lm.text.ifBlank { "[No Text]" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // RED METRIC
                                    lm.rawDiscoveryBox?.let { box ->
                                        MetricChip(label = "RED", color = Color.Red, w = (box.right - box.left) * sourceWidth, h = (box.bottom - box.top) * sourceHeight)
                                    }
                                    // ORANGE METRIC
                                    lm.refinedDiscoveryBox?.let { box ->
                                        MetricChip(label = "ORNGE", color = Color(0xFFFF8C00), w = (box.right - box.left) * sourceWidth, h = (box.bottom - box.top) * sourceHeight)
                                    }
                                    // YELLOW METRIC
                                    MetricChip(label = "YELW", color = Color.Yellow, w = lm.boundingBox.width().toFloat(), h = lm.boundingBox.height().toFloat())
                                }
                                HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }

                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, color: Color, w: Float, h: Float) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.extraSmall,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = androidx.compose.ui.text.TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Black, color = color))
            Text("${w.toInt()}x${h.toInt()}", style = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium))
        }
    }
}
