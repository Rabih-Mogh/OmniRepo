package app.fajralarm.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * Live back-camera preview, with a shutter button when [onCaptured] is given. Photos
 * come only from the live camera (no gallery), so an old picture can't be reused. If [ghost] is given it
 * is drawn faintly over the preview to help line the shot up with the reference.
 *
 * If [onFrame] is given, live preview frames are also handed to it on a background
 * thread, at most one every [frameIntervalMs]. Frames that arrive while it is still
 * running are dropped, so a slow check never queues up.
 */
@Composable
fun CameraCapture(
    ghost: Bitmap?,
    modifier: Modifier = Modifier,
    buttonLabel: String = "",
    enabled: Boolean = true,
    onCaptured: ((Bitmap) -> Unit)? = null,
    onFrame: ((Bitmap) -> Unit)? = null,
    frameIntervalMs: Long = 333,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val analyze = onFrame != null
    val capture = onCaptured != null

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
            .build()
    }

    DisposableEffect(lifecycleOwner, analyze, capture) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val analysisExecutor = if (analyze) Executors.newSingleThreadExecutor() else null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = analysisExecutor?.let { executor ->
                    var lastFrameAt = 0L
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                                )
                                .build(),
                        )
                        .build()
                        .also {
                            it.setAnalyzer(executor) { image ->
                                image.use { frame ->
                                    val now = SystemClock.elapsedRealtime()
                                    if (now - lastFrameAt < frameIntervalMs) return@use
                                    lastFrameAt = now
                                    val bitmap = frame.toBitmap().rotated(frame.imageInfo.rotationDegrees)
                                    currentOnFrame?.invoke(bitmap)
                                }
                            }
                        }
                }
                p.unbindAll()
                val useCases = listOfNotNull(preview, imageCapture.takeIf { capture }, analysis).toTypedArray()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases)
                error = null
            } catch (e: Exception) {
                error = "Camera unavailable: ${e.message ?: e.javaClass.simpleName}"
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            analysisExecutor?.shutdown()
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (ghost != null) {
                Image(
                    bitmap = ghost.asImageBitmap(),
                    contentDescription = "Reference photo overlay",
                    contentScale = ContentScale.Crop,
                    alpha = 0.33f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        if (onCaptured != null) Button(
            enabled = enabled && !busy && error == null,
            onClick = {
                busy = true
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = try {
                                image.toBitmap().rotated(image.imageInfo.rotationDegrees)
                            } finally {
                                image.close()
                            }
                            busy = false
                            onCaptured(bitmap)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            busy = false
                            error = "Couldn't take the photo, try again (${exception.imageCaptureError})"
                        }
                    },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(56.dp),
        ) { Text(if (busy) "Taking photo…" else buttonLabel) }
    }
}

private fun Bitmap.rotated(degrees: Int): Bitmap =
    if (degrees == 0) this
    else Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
