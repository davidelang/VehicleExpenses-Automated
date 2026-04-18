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
                        .height(300.dp)
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
                                // 1. RED TIER (Model Suspicion) - HIGH VISIBILITY 45% SOLID TINT
                                // Rendering ALL raw boxes to explain "activity"
                                rawDiscoveryBoxes.forEach { box ->
                                    drawRect(
                                        color = Color.Red.copy(alpha = 0.45f),
                                        topLeft = Offset(offsetX + box.left * dw, offsetY + box.top * dh),
                                        size = Size((box.right - box.left) * dw, (box.bottom - box.top) * dh)
                                    )
                                }

                                // 2. ORANGE TIER (ROI Expansion) - THICK STROKE
                                landmarks.forEach { lm ->
                                    lm.refinedDiscoveryBox?.let { box ->
                                        drawRect(
                                            color = Color(0xFFFF8C00), // Orange
                                            topLeft = Offset(offsetX + box.left * dw, offsetY + box.top * dh),
                                            size = Size((box.right - box.left) * dw, (box.bottom - box.top) * dh),
                                            style = Stroke(6f)
                                        )
                                    }
                                }
                            }

                            // 3. YELLOW TIER (Final Landmark) - THIN STROKE
                            landmarks.forEach { lm ->
                                val nx = lm.boundingBox.left.toFloat() / sourceWidth.toFloat()
                                val ny = lm.boundingBox.top.toFloat() / sourceHeight.toFloat()
                                val nw = (lm.boundingBox.right - lm.boundingBox.left).toFloat() / sourceWidth.toFloat()
                                val nh = (lm.boundingBox.bottom - lm.boundingBox.top).toFloat() / sourceHeight.toFloat()
                                
                                val rect = Offset(offsetX + nx * dw, offsetY + ny * dh)
                                
                                // Only draw Yellow box if it's not degenerate
                                if (nw > 0 && nh > 0) {
                                    drawRect(color = Color.Yellow, topLeft = rect, size = Size(nw * dw, nh * dh), style = Stroke(2f))
                                }

                                if (lm.text.isNotBlank()) {
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = lm.text,
                                        topLeft = rect,
                                        style = androidx.compose.ui.text.TextStyle(color = Color.Yellow, fontSize = 8.sp, background = Color.Black.copy(alpha = 0.7f))
                                    )
                                }
                            }

                            // MANDATE: Blue (Odo) and Green (Veto) drawn LAST (on top)
                            odometerCrop?.let {
                                drawRect(color = Color.Blue, topLeft = Offset(offsetX + it.left * dw, offsetY + it.top * dh), size = Size(it.width * dw, it.height * dh), style = Stroke(4f))
                            }
                            otherTextCrop?.let {
                                drawRect(color = Color.Green, topLeft = Offset(offsetX + it.left * dw, offsetY + it.top * dh), size = Size(it.width * dw, it.height * dh), style = Stroke(4f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Engine: $engineName", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Source: ${sourceWidth}x${sourceHeight}", style = MaterialTheme.typography.labelMedium)
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text("Discovery Pipeline Metrics:", style = MaterialTheme.typography.titleSmall)
                
                // MULTI-COLUMN METRICS GRID
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(landmarks) { lm ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                if (lm.text.isNotBlank()) {
                                    Text(text = lm.text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                } else {
                                    // Empty text area for Ghost Landmarks
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    lm.rawDiscoveryBox?.let { box ->
                                        MetricChip(color = Color.Red, w = (box.right - box.left) * sourceWidth, h = (box.bottom - box.top) * sourceHeight)
                                    }
                                    lm.refinedDiscoveryBox?.let { box ->
                                        MetricChip(color = Color(0xFFFF8C00), w = (box.right - box.left) * sourceWidth, h = (box.bottom - box.top) * sourceHeight)
                                    }
                                    // Only show yellow chip if there's a valid crop result
                                    if (lm.boundingBox.width() > 0) {
                                        MetricChip(color = Color.Yellow, w = lm.boundingBox.width().toFloat(), h = lm.boundingBox.height().toFloat())
                                    }
                                }
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
private fun MetricChip(color: Color, w: Float, h: Float) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Text(
            text = "${w.toInt()}x${h.toInt()}",
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
            style = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black, color = color.copy(alpha = 0.9f))
        )
    }
}
