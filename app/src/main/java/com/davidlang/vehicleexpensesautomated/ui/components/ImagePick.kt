package com.davidlang.vehicleexpensesautomated.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.storage.PickedImageReader
import com.davidlang.vehicleexpensesautomated.ui.util.CameraCaptureProfile
import com.davidlang.vehicleexpensesautomated.ui.util.CameraResolutionPicker
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

class ImagePickerHandle(
    val pickFromGallery: () -> Unit,
    val takePhoto: () -> Unit,
    val imageCapture: ImageCapture,
)

private val imageMimeTypes = arrayOf("image/*", PickedImageReader.DNG_MIME)

/**
 * Device gallery (ACTION_PICK on the images collection), not the document file picker.
 * The photo picker allow-list drops image/x-adobe-dng, so this launch keeps both types.
 * Do not put grant flags on this intent: they grant the collection URI (no id), and
 * MediaProvider rejects that before the gallery opens.
 */
private class PickDeviceGallery : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return Intent(Intent.ACTION_PICK).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            putExtra(Intent.EXTRA_MIME_TYPES, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}

private fun durablePickedUri(context: Context, uri: Uri, persisted: Boolean): Uri {
    if (persisted || mediaStoreImageStaysReadable(context, uri)) return uri
    return copyPickedBytes(context, uri)
}

private fun mediaStoreImageStaysReadable(context: Context, uri: Uri): Boolean {
    if (!isMediaStoreImageUri(uri)) return false
    val perm = if (Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
}

private fun isMediaStoreImageUri(uri: Uri): Boolean {
    if (uri.scheme != "content" || uri.authority != "media") return false
    val segments = uri.pathSegments
    return segments.size >= 4 && segments[1] == "images" && segments[2] == "media"
}

private fun copyPickedBytes(context: Context, uri: Uri): Uri {
    val dest = File(context.filesDir, pickedCopyName(context, uri))
    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("could not read picture")
        input.use { src ->
            FileOutputStream(dest).use { out -> src.copyTo(out) }
        }
        if (dest.length() <= 0L) throw IllegalStateException("could not read picture")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
    } catch (e: Exception) {
        dest.delete()
        throw e
    }
}

private fun pickedCopyName(context: Context, uri: Uri): String {
    val ext = when {
        PickedImageReader.isDng(context, uri) -> "dng"
        else -> when (context.contentResolver.getType(uri)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "img"
        }
    }
    return "picked_${System.nanoTime()}.$ext"
}

@Composable
fun rememberImagePicker(
    allowCamera: Boolean,
    photoStorageManager: PhotoStorageManager? = null,
    captureProfile: CameraCaptureProfile = CameraCaptureProfile.OCR_MEDIUM,
    imageCapture: ImageCapture? = null,
    displayName: () -> String = { "photo_${System.currentTimeMillis()}.jpg" },
    beforeSave: (File) -> Unit = {},
    onCaptured: ((Uri) -> Unit)? = null,
    onCaptureFailed: () -> Unit = {},
    onPicked: (Uri) -> Unit,
): ImagePickerHandle {
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }
    val ownedCapture = remember(captureProfile) {
        ImageCapture.Builder()
            .setResolutionSelector(CameraResolutionPicker.resolutionSelector(captureProfile))
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val capture = imageCapture ?: ownedCapture
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val gallery = rememberLauncherForActivityResult(PickDeviceGallery()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            // A gallery often refuses a persistable grant. That refusal is not a failed pick.
            val persisted = try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                true
            } catch (_: Exception) {
                false
            }
            val reason = PickedImageReader.galleryHeaderRejectReason(context, uri)
            if (reason != null) throw IllegalStateException(reason)
            onPicked(durablePickedUri(context, uri, persisted))
        } catch (e: Exception) {
            Toast.makeText(context, AppStrings.get(R.string.ui_could_not_use_picture_1_s, e.message.orEmpty()), Toast.LENGTH_LONG).show()
        }
    }
    fun shoot() {
        if (!allowCamera) return
        val mgr = photoStorageManager
        if (mgr == null) {
            Toast.makeText(context, AppStrings.get(R.string.ui_could_not_use_picture_camera_file), Toast.LENGTH_LONG).show()
            onCaptureFailed()
            return
        }
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        val main = ContextCompat.getMainExecutor(context)
        try {
            capture.takePicture(
                options,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        try {
                            beforeSave(file)
                            val name = displayName()
                            val uri = mgr.saveGalleryPhoto(name, "image/jpeg", file)
                            val deliver = onCaptured ?: onPicked
                            main.execute {
                                showCamera = false
                                deliver(uri)
                            }
                        } catch (e: Exception) {
                            main.execute {
                                Toast.makeText(
                                    context,
                                    AppStrings.get(R.string.ui_could_not_use_picture_1_s, e.message.orEmpty()),
                                    Toast.LENGTH_LONG,
                                ).show()
                                onCaptureFailed()
                            }
                        } finally {
                            file.delete()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        main.execute {
                            Toast.makeText(
                                context,
                                AppStrings.get(R.string.ui_could_not_use_picture_1_s, exception.message.orEmpty()),
                                Toast.LENGTH_LONG,
                            ).show()
                            onCaptureFailed()
                        }
                    }
                },
            )
        } catch (e: Exception) {
            file.delete()
            Toast.makeText(context, AppStrings.get(R.string.ui_could_not_use_picture_1_s, e.message.orEmpty()), Toast.LENGTH_LONG).show()
            onCaptureFailed()
        }
    }
    if (showCamera && imageCapture == null) {
        Dialog(
            onDismissRequest = { showCamera = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    imageCapture = capture,
                    captureProfile = captureProfile,
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TextButton(onClick = { shoot() }) { Text(AppStrings.get(R.string.ui_shutter)) }
                    TextButton(onClick = { showCamera = false }) { Text(AppStrings.get(R.string.settings_close)) }
                }
            }
        }
    }
    return ImagePickerHandle(
        pickFromGallery = { gallery.launch(imageMimeTypes) },
        takePhoto = {
            if (allowCamera) {
                if (imageCapture == null) {
                    showCamera = true
                } else {
                    shoot()
                }
            }
        },
        imageCapture = capture,
    )
}
