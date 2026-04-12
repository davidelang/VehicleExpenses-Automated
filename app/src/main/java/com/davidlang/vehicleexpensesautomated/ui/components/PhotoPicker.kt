package com.davidlang.vehicleexpensesautomated.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            scope.launch {
                val savedUrl = photoStorageManager.savePhoto(photoUri!!, "photo_${System.currentTimeMillis()}.jpg", photoType)
                if (savedUrl != null) {
                    previewUrl = savedUrl
                    onPhotoUrlChanged(savedUrl)
                    Toast.makeText(context, "Photo saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val savedUrl = photoStorageManager.savePhoto(it, "photo_${System.currentTimeMillis()}.jpg", photoType)
                if (savedUrl != null) {
                    previewUrl = savedUrl
                    onPhotoUrlChanged(savedUrl)
                    Toast.makeText(context, "Photo imported", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = modifier) {
        if (previewUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📸 Photo saved:\n$previewUrl",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                photoUri = Uri.parse("content://com.davidlang.vehicleexpensesautomated.camera/photo.jpg")
                cameraLauncher.launch(photoUri!!)
            }) {
                Text("📸 Take Photo")
            }

            Button(onClick = { galleryLauncher.launch(arrayOf("image/jpeg", "image/png", "image/x-adobe-dng")) }) {
                Text("🖼️ Choose from Gallery")
            }
        }
    }
}
