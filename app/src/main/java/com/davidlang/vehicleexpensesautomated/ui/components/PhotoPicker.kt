package com.davidlang.vehicleexpensesautomated.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import kotlinx.coroutines.launch

@Composable
fun PhotoPicker(
    photoStorageManager: PhotoStorageManager,
    photoType: PhotoType,
    currentPhotoUrl: String?,
    onPhotoUrlChanged: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var previewUrl by remember { mutableStateOf(currentPhotoUrl) }
    var loadingPath by remember { mutableStateOf<String?>(null) }

    // Sync previewUrl with currentPhotoUrl when it changes externally
    LaunchedEffect(currentPhotoUrl) {
        previewUrl = currentPhotoUrl
        if (currentPhotoUrl != null) loadingPath = null
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            scope.launch {
                previewUrl = null // Immediate clear for loading state
                loadingPath = "Processing camera photo..."
                val savedUrl = photoStorageManager.savePhoto(photoUri!!, "photo_${System.currentTimeMillis()}.jpg", photoType)
                if (savedUrl != null) {
                    onPhotoUrlChanged(savedUrl)
                } else {
                    loadingPath = null
                    Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                previewUrl = null // Immediate clear for loading state
                loadingPath = it.path ?: "Loading from gallery..."
                val savedUrl = photoStorageManager.savePhoto(it, "photo_${System.currentTimeMillis()}.jpg", photoType)
                if (savedUrl != null) {
                    onPhotoUrlChanged(savedUrl)
                } else {
                    loadingPath = null
                    Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = modifier) {
        // LOADING / PATH PLACEHOLDER - ONLY SHOWS WHEN NO PREVIEW
        if (previewUrl == null && loadingPath != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📂 Loading:\n$loadingPath",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val uri = photoStorageManager.createMediaStoreUri("photo_${System.currentTimeMillis()}.jpg", photoType)
                if (uri != null) {
                    photoUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    Toast.makeText(context, "Failed to create photo storage location", Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.weight(1f)) {
                Text("📸 Take Photo")
            }

            Button(onClick = { galleryLauncher.launch(arrayOf("image/jpeg", "image/png", "image/x-adobe-dng")) }, modifier = Modifier.weight(1f)) {
                Text("🖼️ Gallery")
            }
        }
    }
}
