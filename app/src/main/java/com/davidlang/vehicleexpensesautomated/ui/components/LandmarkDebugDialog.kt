package com.davidlang.vehicleexpensesautomated.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.davidlang.vehicleexpensesautomated.ui.util.RectF
import com.davidlang.vehicleexpensesautomated.ui.util.TextBlock
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    onLandmarksChanged: (List<TextBlock>) -> Unit = {},
    engineName: String = "Unknown",
    odometerText: String = "",
    sourceWidth: Int = 1,
    sourceHeight: Int = 1,
    executionTimeMs: Long = 0,
    discoveryTimeMs: Long = 0,
    totalTimeMs: Long = 0
) {
    val context = LocalContext.current
    var showDiscovery by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    
    // Local state for editing
    var editableLandmarks by remember { mutableStateOf(landmarks) }

    // Optimization Phase 37: Async image loading to prevent Main-thread stall
    var processedBitmap by remember { mutableStateOf<Bitmap?>(sourceImage) }
    var isImageLoading by remember { mutableStateOf(sourceImage == null && photoPath != null) }

    LaunchedEffect(photoPath) {
        if (processedBitmap == null && photoPath != null) {
            isImageLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                    val raw = if (photoPath.startsWith("content://")) {
                        context.contentResolver.openInputStream(Uri.parse(photoPath))?.use {
                            BitmapFactory.decodeStream(it, null, options)
                        }
                    } else if (File(photoPath).exists()) {
                        BitmapFactory.decodeFile(photoPath, options)
                    } else null
                    
                    if (raw != null) {
                        // Apply heavy filters on background thread
                        val filtered = OdometerOcrUtils.applyBilateral(OdometerOcrUtils.applyGrayscale(raw))
                        processedBitmap = filtered
                        raw.recycle()
                    }
                } catch (e: Exception) {
                    Log.e("LandmarkDialog", "Failed to load/process image", e)
                } finally {
                    isImageLoading = false
                }
            }
        }
    }

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
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Reference OCR Check", style = MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isEditing) {
                            Button(onClick = { isEditing = true }, modifier = Modifier.padding(end = 8.dp)) { Text("Edit OCR") }
                        } else {
                            Button(onClick = { onLandmarksChanged(editableLandmarks); onDismiss() }, modifier = Modifier.padding(end = 8.dp)) { Text("Save Overrides") }
                            Button(onClick = { isEditing = false; editableLandmarks = landmarks }, modifier = Modifier.padding(end = 8.dp)) { Text("Cancel") }
                        }
                        
                        Text("Discovery", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = showDiscovery, onCheckedChange = { showDiscovery = it })
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(onClick = onDismiss) { Text("✕", style = MaterialTheme.typography.titleLarge) }
                    }
                }

                if (isImageLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val bitmap = processedBitmap
                    if (bitmap != null) {
                        val imgW = bitmap.width.toFloat()
                        val imgH = bitmap.height.toFloat()

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
                                        rawDiscoveryBoxes.forEach { box ->
                                            drawRect(color = Color.Red.copy(alpha = 0.3f), topLeft = Offset(box.left * dw, box.top * dh), size = Size((box.right - box.left) * dw, (box.bottom - box.top) * dh), style = Fill)
                                        }
                                        editableLandmarks.forEach { lm ->
                                            lm.refinedDiscoveryBox?.let { box ->
                                                drawRect(color = Color(0xFFFF8C00), topLeft = Offset(box.left * dw, box.top * dh), size = Size((box.right - box.left) * dw, (box.bottom - box.top) * dh), style = Stroke(3f))
                                            }
                                            if (lm.boundingBox.width() > 0) {
                                                val nx = lm.boundingBox.left.toFloat() / imgW; val ny = lm.boundingBox.top.toFloat() / imgH
                                                val nw = lm.boundingBox.width().toFloat() / imgW; val nh = lm.boundingBox.height().toFloat() / imgH
                                                drawRect(color = Color.Yellow, topLeft = Offset(nx * dw, ny * dh), size = Size(nw * dw, nh * dh), style = Stroke(1f))
                                            }
                                        }
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

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 150.dp),
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(editableLandmarks.size) { index ->
                                    val lm = editableLandmarks[index]
                                    Surface(
                                        modifier = Modifier.height(if (isEditing) 80.dp else 64.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(modifier = Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            val zone = if (lm.boundingBox.width() > 0) lm.boundingBox else lm.refinedDiscoveryBox?.let { 
                                                android.graphics.Rect((it.left * imgW).toInt(), (it.top * imgH).toInt(), (it.right * imgW).toInt(), (it.bottom * imgH).toInt())
                                            } ?: lm.rawDiscoveryBox?.let { 
                                                android.graphics.Rect((it.left * imgW).toInt(), (it.top * imgH).toInt(), (it.right * imgW).toInt(), (it.bottom * imgH).toInt())
                                            }
                                            
                                            Box(modifier = Modifier.size(48.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                                                val crop = remember(zone, bitmap) {
                                                    if (zone != null && zone.width() > 0 && zone.height() > 0) {
                                                        try {
                                                            Bitmap.createBitmap(bitmap, max(0, zone.left), max(0, zone.top), min(bitmap.width - zone.left, zone.width()), min(bitmap.height - zone.top, zone.height()))
                                                        } catch (e: Exception) { null }
                                                    } else null
                                                }
                                                crop?.let { c -> Image(bitmap = c.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit) }
                                            }
                                            
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                if (isEditing) {
                                                    TextField(
                                                        value = lm.text,
                                                        onValueChange = { newText: String ->
                                                            val newList = editableLandmarks.toMutableList()
                                                            newList[index] = lm.copy(text = newText)
                                                            editableLandmarks = newList
                                                        },
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                                        colors = TextFieldDefaults.colors(
                                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                            unfocusedIndicatorColor = Color.Transparent,
                                                            focusedIndicatorColor = MaterialTheme.colorScheme.primary
                                                        ),
                                                        singleLine = true
                                                    )
                                                } else {
                                                    if (lm.text.isNotBlank()) Text(lm.text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                                    else Text("[Container]", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.secondary)
                                                }
                                                
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
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No image data available", color = MaterialTheme.colorScheme.error)
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
