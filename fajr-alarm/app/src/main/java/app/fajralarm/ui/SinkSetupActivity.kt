package app.fajralarm.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.fajralarm.core.ImageMatcher
import app.fajralarm.data.SettingsStore
import app.fajralarm.data.SinkPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Take and manage the reference photos of the sink, and try a wake-up photo against them. */
class SinkSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FajrTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SinkSetupScreen(onDone = { finish() })
                }
            }
        }
    }
}

@Composable
private fun SinkSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) } // bump to reload photos
    val files = remember(version) { SinkPhotos.list(context) }
    val thumbs = remember(version) { files.map { it to SinkPhotos.load(it) } }
    var testMode by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<File?>(null) }
    var resumeTick by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera) askCamera.launch(Manifest.permission.CAMERA) }

    fun onPhoto(photo: Bitmap) {
        working = true
        scope.launch {
            if (testMode) {
                val strictness = SettingsStore(context).load().photoStrictness
                val result = withContext(Dispatchers.Default) {
                    ImageMatcher.bestMatch(SinkPhotos.referenceFeatures(context), SinkPhotos.features(photo), strictness)
                }
                message = when {
                    result.unusable -> "Too dark or blurry to judge. Turn the light on and try again."
                    result.matched -> "✓ Match (${result.percent}%). This photo would turn the alarm off."
                    else -> "✗ No match (${result.percent}%, needs ${(strictness.structural * 100).toInt()}%). " +
                        "Stand where you took the reference, or add another reference from here."
                }
            } else {
                val usable = withContext(Dispatchers.Default) { !SinkPhotos.features(photo).unusable }
                if (usable) {
                    withContext(Dispatchers.IO) { SinkPhotos.add(context, photo) }
                    version++
                    message = "Saved. ${SinkPhotos.count(context)} of ${SinkPhotos.MAX} reference photos."
                } else {
                    message = "That photo is too dark or plain. Turn the light on and include the tap and edges."
                }
            }
            working = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your sink", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "When the alarm rings it only turns off once the camera recognises this sink. " +
                "Take the photos the way you will half-asleep: standing at the sink with the light on, " +
                "phone upright. Get the tap, basin edge and tiles or mirror frame in shot, " +
                "and leave out towels and things that move. Taking 2–3 photos from slightly different spots helps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The mission is useless if the alarm screen can't open by itself, so repeat the checks here.
        PermissionWarnings(context, resumeTick, onRequestNotifications = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        })

        if (thumbs.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                thumbs.forEach { (file, bmp) ->
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Reference photo (tap to delete)",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { toDelete = file },
                        )
                    }
                }
            }
            Text("Tap a photo to delete it.", style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !testMode, onClick = { testMode = false; message = null }, label = { Text("Add reference") })
            FilterChip(
                selected = testMode,
                enabled = files.isNotEmpty(),
                onClick = { testMode = true; message = null },
                label = { Text("Test a wake-up photo") },
            )
        }

        if (!hasCamera) {
            Text("Camera permission is needed for this mission.", color = MaterialTheme.colorScheme.error)
            Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        } else {
            val full = !testMode && files.size >= SinkPhotos.MAX
            CameraCapture(
                ghost = if (testMode) thumbs.firstOrNull()?.second else null,
                buttonLabel = when {
                    testMode -> "Take test photo"
                    full -> "Maximum ${SinkPhotos.MAX} photos, delete one first"
                    else -> "Save as reference photo"
                },
                enabled = !working && !full,
                onCaptured = ::onPhoto,
            )
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }

        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }

    toDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this photo?") },
            confirmButton = {
                TextButton(onClick = { SinkPhotos.delete(file); toDelete = null; version++ }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}
