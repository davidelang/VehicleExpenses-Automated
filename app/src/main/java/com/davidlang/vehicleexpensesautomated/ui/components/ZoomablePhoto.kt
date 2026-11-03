package com.davidlang.vehicleexpensesautomated.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.davidlang.vehicleexpensesautomated.data.storage.PickedImageReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

/**
 * Fullscreen photo viewer: pinch zoom + pan, on-screen +/−, Close always on top.
 * [uris] may be content URIs or absolute file paths.
 */
@Composable
fun ZoomablePhotoDialog(
    uris: List<String>,
    initialIndex: Int = 0,
    title: String? = null,
    onDismiss: () -> Unit,
) {
    if (uris.isEmpty()) {
        onDismiss()
        return
    }
    var index by remember(uris) { mutableIntStateOf(initialIndex.coerceIn(0, uris.lastIndex)) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val path = uris[index]
    val context = LocalContext.current
    var fileBitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var photoError by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        scale = 1f
        offset = Offset.Zero
        photoError = null
        fileBitmap = try {
            withContext(Dispatchers.IO) {
                val uri = when {
                    path.startsWith("content:") || path.startsWith("file:") || path.startsWith("http") ->
                        Uri.parse(path)
                    path.startsWith("/") -> Uri.fromFile(File(path))
                    else -> Uri.parse(path)
                }
                PickedImageReader.decodeFullSize(context, uri)
            }
        // kept: failure stays on this call so the screen or caller can continue
        } catch (e: Exception) {
            photoError = e.message ?: AppStrings.get(R.string.ui_could_not_display_photo)
            null
        }
    }
    DisposableEffect(path) {
        onDispose {
            fileBitmap?.recycle()
            fileBitmap = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title ?: path.substringAfterLast('/'),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_close), color = Color.White)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RectangleShape)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 10f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val layerMod = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                    when {
                        fileBitmap != null -> {
                            Image(
                                bitmap = fileBitmap!!.asImageBitmap(),
                                contentDescription = AppStrings.get(R.string.ui_photo),
                                modifier = layerMod,
                                contentScale = ContentScale.Fit,
                            )
                        }
                        photoError != null -> Text(photoError!!, color = Color.White)
                        else -> Text(stringResource(R.string.ui_photo_unavailable), color = Color.White)
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SmallFloatingActionButton(
                            onClick = { scale = (scale * 1.2f).coerceIn(1f, 10f) },
                            containerColor = Color.White.copy(alpha = 0.75f),
                        ) { Text("+") }
                        SmallFloatingActionButton(
                            onClick = {
                                scale = (scale / 1.2f).coerceIn(1f, 10f)
                                if (scale == 1f) offset = Offset.Zero
                            },
                            containerColor = Color.White.copy(alpha = 0.75f),
                        ) { Text("−") }
                    }
                }
                if (uris.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = {
                                index = (index - 1).coerceAtLeast(0)
                            },
                            enabled = index > 0,
                        ) { Text(stringResource(R.string.ui_prev), color = Color.White) }
                        Text(
                            "${index + 1} / ${uris.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        TextButton(
                            onClick = {
                                index = (index + 1).coerceAtMost(uris.lastIndex)
                            },
                            enabled = index < uris.lastIndex,
                        ) { Text(stringResource(R.string.ui_next), color = Color.White) }
                    }
                }
            }
        }
    }
}

/**
 * Clickable photo preview with larger default on wide layouts; opens [ZoomablePhotoDialog].
 */
@Composable
fun ZoomablePhotoThumb(
    uris: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String = AppStrings.get(R.string.ui_photo),
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (uris.isEmpty()) return
    var show by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tall = maxWidth >= 600.dp
        val h = if (tall) 320.dp else 200.dp
        val uri = uris.first()
        val context = LocalContext.current
        var thumb by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
        var thumbError by remember(uri) { mutableStateOf<String?>(null) }
        LaunchedEffect(uri) {
            thumb = try {
                withContext(Dispatchers.IO) {
                    val parsed = when {
                        uri.startsWith("/") -> Uri.fromFile(File(uri))
                        uri.startsWith("content:") || uri.startsWith("file:") || uri.startsWith("http") ->
                            Uri.parse(uri)
                        else -> Uri.parse(uri)
                    }
                    PickedImageReader.decodeFullSize(context, parsed)
                }
            } catch (e: Exception) {
                thumbError = e.message ?: AppStrings.get(R.string.ui_could_not_display_photo)
                null
            }
        }
        DisposableEffect(uri) {
            onDispose {
                thumb?.recycle()
                thumb = null
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(h)
                .heightIn(min = if (tall) 280.dp else 200.dp)
                .clickable { show = true },
            contentAlignment = Alignment.Center,
        ) {
            when {
                thumb != null -> Image(
                    bitmap = thumb!!.asImageBitmap(),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
                thumbError != null -> Text(thumbError!!)
            }
            if (uris.size > 1) {
                Text(
                    stringResource(R.string.ui_s_1_s_photos, uris.size),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (show) {
            ZoomablePhotoDialog(
                uris = uris,
                onDismiss = { show = false },
            )
        }
    }
}

/** All photo URIs from a fuel/expense photoUrl JSON or plain path. */
fun photoUrisFromJsonOrPath(photoUrl: String?): List<String> {
    if (photoUrl.isNullOrBlank()) return emptyList()
    return when (val parsed = com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson.read(photoUrl)) {
        is com.davidlang.vehicleexpensesautomated.data.model.StoredParse.Corrupt -> emptyList()
        is com.davidlang.vehicleexpensesautomated.data.model.StoredParse.Blank -> emptyList()
        is com.davidlang.vehicleexpensesautomated.data.model.StoredParse.Valid ->
            parsed.value.map { it.uri }.filter { it.isNotBlank() }.distinct()
    }
}
