package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.davidlang.vehicleexpensesautomated.data.storage.PickedImageReader
import com.davidlang.vehicleexpensesautomated.ui.components.rememberImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.davidlang.vehicleexpensesautomated.ui.util.S

@Composable
fun FillGalleryButton(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPicked: (Uri, Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberImagePicker(allowCamera = false) { uri ->
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    PickedImageReader.decodeFullSize(context, uri)
                }
                onPicked(uri, bitmap)
            } catch (e: Exception) {
                Toast.makeText(context, S("Could not use picture: %1\$s", e.message.orEmpty()), Toast.LENGTH_LONG).show()
            }
        }
    }
    IconButton(onClick = { picker.pickFromGallery() }, enabled = enabled, modifier = modifier) {
        Icon(Icons.Filled.Image, contentDescription = S("Pick an existing image"))
    }
}
