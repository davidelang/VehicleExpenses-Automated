package com.davidlang.vehicleexpensesautomated.ui.components

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.view.OrientationEventListener
import android.view.Surface
data class CameraZoomControl(
    val currentRatio: Float,
    val minRatio: Float,
    val maxRatio: Float,
    val availableRatios: List<Float>,
    val setZoomRatio: (Float) -> Unit
)

private val COMMON_ZOOM_RATIOS = listOf(0.5f, 1f, 2f, 4f)

private fun computeAvailableRatios(minRatio: Float, maxRatio: Float): List<Float> {
    return COMMON_ZOOM_RATIOS.filter { it in minRatio..maxRatio }
        .ifEmpty { listOf(minRatio.coerceAtLeast(1f)) }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture,
    onImageCaptured: (ImageProxy) -> Unit = {},
    onZoomControlChanged: (CameraZoomControl?) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var imageAnalysisState by remember { mutableStateOf<ImageAnalysis?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(boundCamera, lifecycleOwner) {
        val camera = boundCamera
        if (camera == null) {
            onZoomControlChanged(null)
            onDispose { }
        } else {
            val zoomStateLiveData = camera.cameraInfo.zoomState
            val observer = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { zoomState ->
                val minRatio = zoomState.minZoomRatio
                val maxRatio = zoomState.maxZoomRatio
                onZoomControlChanged(
                    CameraZoomControl(
                        currentRatio = zoomState.zoomRatio,
                        minRatio = minRatio,
                        maxRatio = maxRatio,
                        availableRatios = computeAvailableRatios(minRatio, maxRatio),
                        setZoomRatio = { ratio ->
                            val clamped = ratio.coerceIn(minRatio, maxRatio)
                            camera.cameraControl.setZoomRatio(clamped)
                        }
                    )
                )
            }
            zoomStateLiveData.observe(lifecycleOwner, observer)
            onDispose {
                zoomStateLiveData.removeObserver(observer)
                onZoomControlChanged(null)
            }
        }
    }

    DisposableEffect(imageAnalysisState, imageCapture) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                try {
                    imageAnalysisState?.targetRotation = rotation
                    imageCapture.targetRotation = rotation
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Failed to set target rotation dynamically", e)
                }
            }
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
        }
        onDispose {
            listener.disable()
        }
    }

    var activeCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                activeCameraProvider?.unbindAll()
            } catch (e: Exception) {
                Log.e("CameraPreview", "Failed to unbind camera provider on dispose", e)
            }
            try {
                cameraExecutor.shutdown()
            } catch (e: Exception) {
                Log.e("CameraPreview", "Failed to shutdown camera executor on dispose", e)
            }
            boundCamera = null
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FIT_CENTER
            }
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                activeCameraProvider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 4:3 full sensor aspect (2048x1536 ~2000 wide fine for odo; long side wide in landscape; avoids 13:9)
                val resSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(
                        android.util.Size(2048, 1536),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                    ))
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    onImageCaptured(imageProxy)
                }

                imageAnalysisState = imageAnalysis

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis,
                        imageCapture
                    )
                    boundCamera = camera
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, executor)
            previewView
        },
        modifier = modifier.fillMaxSize()
    )
}