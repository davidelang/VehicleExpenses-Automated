package com.davidlang.vehicleexpensesautomated.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.davidlang.vehicleexpensesautomated.data.storage.PickedImageReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.davidlang.vehicleexpensesautomated.ui.util.S

private sealed class Decoded {
    data object Loading : Decoded()
    data class Ready(val bitmap: Bitmap) : Decoded()
    data class Failed(val message: String) : Decoded()
}

@Composable
fun DecodedPhotoImage(
    uri: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val decoded = produceState<Decoded>(Decoded.Loading, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val photoUri = if (uri.startsWith("/")) Uri.fromFile(File(uri)) else Uri.parse(uri)
                Decoded.Ready(PickedImageReader.decodeFullSize(context, photoUri))
            } catch (e: Exception) {
                Log.e("DecodedPhotoImage", uri, e)
                Decoded.Failed(e.message ?: S("Cannot display"))
            }
        }
    }
    when (val state = decoded.value) {
        is Decoded.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
            Text("…", color = Color.White)
        }
        is Decoded.Failed -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(S("Cannot display"), color = Color.White)
        }
        is Decoded.Ready -> Image(
            bitmap = state.bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    }
}
