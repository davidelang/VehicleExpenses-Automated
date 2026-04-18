package com.davidlang.vehicleexpensesautomated.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
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
import androidx.compose.ui.graphics.*
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
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun LandmarkDebugDialog(
    photoPath: String?,
    odometerCrop: Rect?,
    otherTextCrop: Rect?,
    landmarks: List<TextBlock>,
    odometerText: String,
    engineName: String = "Unknown",
    sourceWidth: Int = 1500,
    sourceHeight: Int = 1125,
    rawHeatmap: FloatArray? = null,
    discoveryHeatmap: FloatArray? = null,
    onDismiss: () -> Unit
) {
    if (photoPath == null) return
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    
    var showHeatmap by remember { mutableStateOf(rawHeatmap != null || discoveryHeatmap != null) }

    // Optimization: Create DOWNSCALED Heatmap Bitmaps (512x512) for stability
    val (rawBmp, discBmp) = remember(rawHeatmap, discoveryHeatmap) {
        val visualSize = 512
        // Use Actual Heatmap Dimensions instead of hardcoded 1280
        val hSize = if (rawHeatmap != null) sqrt(rawHeatmap.size.toDouble()).toInt() else 1024
        val scaleRatio = hSize.toFloat() / visualSize
        
        val rBmp = rawHeatmap?.let { data ->
            try {
                val bmp = Bitmap.createBitmap(visualSize, visualSize, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(visualSize * visualSize)
                for (y in 0 until visualSize) {
                    for (x in 0 until visualSize) {
                        val rawX = (x * scaleRatio).toInt().coerceIn(0, hSize - 1)
                        val rawY = (y * scaleRatio).toInt().coerceIn(0, hSize - 1)
                        val idx = rawY * hSize + rawX
                        if (idx < data.size) {
                            val prob = data[idx]
                            if (prob > 0.05f) {
                                pixels[y * visualSize + x] = android.graphics.Color.argb((prob.coerceIn(0f, 0.6f) * 255).toInt(), 255, 0, 0)
                            }
                        }
                    }
                }
                bmp.setPixels(pixels, 0, visualSize, 0, 0, visualSize, visualSize)
                bmp.asImageBitmap()
            } catch (e: Exception) { null }
        }

        val dBmp = discoveryHeatmap?.let { data ->
            try {
                val hSizeDisc = sqrt(data.size.toDouble()).toInt()
                val scaleRatioDisc = hSizeDisc.toFloat() / visualSize
                val bmp = Bitmap.createBitmap(visualSize, visualSize, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(visualSize * visualSize)
                for (y in 0 until visualSize) {
                    for (x in 0 until visualSize) {
                        val rawX = (x * scaleRatioDisc).toInt().coerceIn(0, hSizeDisc - 1)
                        val rawY = (y * scaleRatioDisc).toInt().coerceIn(0, hSizeDisc - 1)
                        val idx = rawY * hSizeDisc + rawX
                        if (idx < data.size) {
                            val prob = data[idx]
                            if (prob > 0.5f) {
                                pixels[y * visualSize + x] = android.graphics.Color.argb(100, 255, 165, 0) // Orange semi-transparent
                            }
                        }
                    }
                }
                bmp.setPixels(pixels, 0, visualSize, 0, 0, visualSize, visualSize)
                bmp.asImageBitmap()
            } catch (e: Exception) { null }
        }
        rBmp to dBmp
    }

    val sW = if (sourceWidth <= 0) 1500f else sourceWidth.toFloat()
    val sH = if (sourceHeight <= 0) 1125f else sourceHeight.toFloat()

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
                    if (rawHeatmap != null || discoveryHeatmap != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Heatmap", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = showHeatmap, onCheckedChange = { showHeatmap = it })
                        }
                    }
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

                            // DRAW CENTERED HEATMAP OVERLAYS (Optimized Bitmaps)
                            if (showHeatmap) {
                                // Match the display directly to the original photo's content
                                rawBmp?.let {
                                    drawImage(image = it, dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()), dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()))
                                }
                                discBmp?.let {
                                    drawImage(image = it, dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()), dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()))
                                }
                            }

                            odometerCrop?.let {
                                val rect = Offset(offsetX + it.left * dw, offsetY + it.top * dh)
                                val boxSize = androidx.compose.ui.geometry.Size(it.width * dw, it.height * dh)
                                drawRect(color = Color.Blue, topLeft = rect, size = boxSize, style = Stroke(4f))
                            }

                            otherTextCrop?.let {
                                val rect = Offset(offsetX + it.left * dw, offsetY + it.top * dh)
                                val boxSize = androidx.compose.ui.geometry.Size(it.width * dw, it.height * dh)
                                drawRect(color = Color.Green, topLeft = rect, size = boxSize, style = Stroke(4f))
                            }

                            landmarks.forEach { lm ->
                                // COORDINATE CONVERSION: Landmarks are now NORMALIZED (0.0 to 1.0)
                                val nx = lm.boundingBox.left.toFloat() / sW
                                val ny = lm.boundingBox.top.toFloat() / sH
                                val nw = (lm.boundingBox.right - lm.boundingBox.left).toFloat() / sW
                                val nh = (lm.boundingBox.bottom - lm.boundingBox.top).toFloat() / sH
                                
                                val rect = Offset(offsetX + nx * dw, offsetY + ny * dh)
                                // YELLOW OUTLINES for final text boxes
                                drawRect(color = Color.Yellow, topLeft = rect, size = androidx.compose.ui.geometry.Size(nw * dw, nh * dh), style = Stroke(2f))

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
                    Text("Source: ${sourceWidth.toInt()}x${sourceHeight.toInt()}", style = MaterialTheme.typography.labelMedium)
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
