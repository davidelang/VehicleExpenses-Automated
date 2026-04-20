package com.davidlang.vehicleexpensesautomated.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.davidlang.vehicleexpensesautomated.ui.util.RectF
import com.davidlang.vehicleexpensesautomated.ui.util.TextBlock
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import kotlin.math.max
import kotlin.math.min
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LandmarkDebugDialog(
    sourceImage: Bitmap? = null,
    photoPath: String? = null,
    odometerCrop: androidx.compose.ui.geometry.Rect? = null,
    otherTextCrop: androidx.compose.ui.geometry.Rect? = null,
    landmarks: List<TextBlock>,
    rawDiscoveryBoxes: List<RectF> = emptyList(),
    onDismiss: () -> Unit,
    engineName: String = "Unknown",
    odometerText: String = "",
    sourceWidth: Int = 1,
    sourceHeight: Int = 1,
    executionTimeMs: Long = 0,
    discoveryTimeMs: Long = 0,
    totalTimeMs: Long = 0
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    var showDiscovery by remember { mutableStateOf(true) }

    // Resolve source image
    val bitmap = remember(sourceImage, photoPath) {
        sourceImage ?: try {
            photoPath?.let { path ->
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                val raw = if (path.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(path))?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                } else if (File(path).exists()) {
                    BitmapFactory.decodeFile(path, options)
                } else null
                raw?.let { OdometerOcrUtils.applyBilateral(OdometerOcrUtils.applyGrayscale(it)) }
            }
        } catch (e: Exception) { null }
    } ?: return

    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reference OCR Check", style = MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Discovery", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = showDiscovery, onCheckedChange = { showDiscovery = it })
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(onClick = onDismiss) { Text("✕", style = MaterialTheme.typography.titleLarge) }
                    }
                }

                // REMOVED OUTER SCROLL to let LazyVerticalGrid work properly
                Column(modifier = Modifier.weight(1f)) {
                    // IMAGE VIEW
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(imgW / imgH)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val dw = size.width; val dh = size.height

                            drawImage(
                                image = bitmap.asImageBitmap(),
                                dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt())
                            )

                            if (showDiscovery) {
                                // RED: SOLID FILL (30%)
                                rawDiscoveryBoxes.forEach { box ->
                                    drawRect(color = Color.Red.copy(alpha = 0.3f), topLeft = Offset(box.left * dw, box.top * dh), size = Size((box.right - box.left) * dw, (box.bottom - box.top) * dh), style = Fill)
                                }
                                landmarks.forEach { lm ->
                                    // ORANGE: 3PX STROKE
                                    lm.refinedDiscoveryBox?.let { box ->
                                        drawRect(color = Color(0xFFFF8C00), topLeft = Offset(box.left * dw, box.top * dh), size = Size((box.right - box.left) * dw, (box.bottom - box.top) * dh), style = Stroke(3f))
                                    }
                                    // YELLOW: 1PX STROKE
                                    if (lm.boundingBox.width() > 0) {
                                        val nx = lm.boundingBox.left.toFloat() / imgW; val ny = lm.boundingBox.top.toFloat() / imgH
                                        val nw = lm.boundingBox.width().toFloat() / imgW; val nh = lm.boundingBox.height().toFloat() / imgH
                                        drawRect(color = Color.Yellow, topLeft = Offset(nx * dw, ny * dh), size = Size(nw * dw, nh * dh), style = Stroke(1f))
                                    }
                                }
                                // Crops (Normalized Coords)
                                odometerCrop?.let { drawRect(color = Color.Blue, topLeft = Offset(it.left * dw, it.top * dh), size = Size(it.width * dw, it.height * dh), style = Stroke(2f)) }
                                otherTextCrop?.let { drawRect(color = Color.Green, topLeft = Offset(it.left * dw, it.top * dh), size = Size(it.width * dw, it.height * dh), style = Stroke(2f)) }
                            }
                        }
                    }

                    // Metadata
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Engine: $engineName", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Discovery: ${discoveryTimeMs}ms / Total: ${totalTimeMs.coerceAtLeast(executionTimeMs)}ms", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Discovery Pipeline Previews:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }

                    // RESTORE ADAPTIVE GRID (200dp min)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 200.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(landmarks) { lm ->
                            Surface(
                                modifier = Modifier.height(48.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.extraSmall,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(modifier = Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // PREVIEW CROP
                                    val zone = if (lm.boundingBox.width() > 0) lm.boundingBox else lm.refinedDiscoveryBox?.let { 
                                        android.graphics.Rect((it.left * imgW).toInt(), (it.top * imgH).toInt(), (it.right * imgW).toInt(), (it.bottom * imgH).toInt())
                                    } ?: lm.rawDiscoveryBox?.let { 
                                        android.graphics.Rect((it.left * imgW).toInt(), (it.top * imgH).toInt(), (it.right * imgW).toInt(), (it.bottom * imgH).toInt())
                                    }
                                    
                                    Box(modifier = Modifier.size(40.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                                        val crop = remember(zone, bitmap) {
                                            if (zone != null && zone.width() > 0 && zone.height() > 0) {
                                                try {
                                                    Bitmap.createBitmap(bitmap, max(0, zone.left), max(0, zone.top), min(bitmap.width - zone.left, zone.width()), min(bitmap.height - zone.top, zone.height()))
                                                } catch (e: Exception) { null }
                                            } else null
                                        }
                                        
                                        crop?.let { c ->
                                            Image(bitmap = c.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (lm.text.isNotBlank()) Text(lm.text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                        else Text("[Container]", style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp), fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.secondary)
                                        
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            lm.rawDiscoveryBox?.let { MetricChip(color = Color.Red, w = (it.right - it.left) * imgW, h = (it.bottom - it.top) * imgH) }
                                            lm.refinedDiscoveryBox?.let { MetricChip(color = Color(0xFFFF8C00), w = (it.right - it.left) * imgW, h = (it.bottom - it.top) * imgH) }
                                            if (lm.boundingBox.width() > 0) MetricChip(color = Color.Yellow, w = lm.boundingBox.width().toFloat(), h = lm.boundingBox.height().toFloat())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(color: Color, w: Float, h: Float) {
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall, border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.6f))) {
        Text(text = "${w.toInt()}x${h.toInt()}", modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.5.dp), style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Black, color = color.copy(alpha = 0.9f)))
    }
}
