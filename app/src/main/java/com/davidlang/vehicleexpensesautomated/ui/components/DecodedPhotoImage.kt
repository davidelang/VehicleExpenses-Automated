package com.davidlang.vehicleexpensesautomated.ui.components

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

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
                Decoded.Ready(PickedImageReader.decodeFullSize(context, Uri.parse(uri)))
            } catch (e: Exception) {
                Decoded.Failed(e.message ?: AppStrings.get(R.string.ui_cannot_display))
            }
        }
    }
    when (val state = decoded.value) {
        is Decoded.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
            Text("…", color = Color.White)
        }
        is Decoded.Failed -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.ui_cannot_display), color = Color.White)
        }
        is Decoded.Ready -> Image(
            bitmap = state.bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    }
}
