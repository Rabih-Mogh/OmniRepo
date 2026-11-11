package app.accentcoach.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.accentcoach.ModelState
import app.accentcoach.PracticeViewModel

/** First run: fetch the speech model, then hand over to practice. */
@Composable
fun SetupScreen(vm: PracticeViewModel) {
    val context = LocalContext.current
    // The download's progress lives in a notification, so ask to show one first (Android 13+).
    // It downloads either way.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.downloadModel() }
    val startDownload = {
        val needsAsking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsking) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.downloadModel()
    }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Accent Coach", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Say a sentence and see the sounds you made, next to how a native American speaker says them. " +
                "Sounds that give away an accent are highlighted.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        when (val m = vm.model) {
            ModelState.Missing -> {
                Text(
                    "First, the app needs its speech model: a one-time 318 MB download. " +
                        "After that everything runs on your phone, offline. Use Wi-Fi if you can.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = startDownload, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Download (318 MB)") }
            }
            is ModelState.Downloading -> {
                LinearProgressIndicator(progress = { m.progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    if (m.waiting) "Waiting for a connection… ${(m.progress * 100).toInt()}%"
                    else "Downloading… ${(m.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "You can leave the app: the download carries on in the background, with its progress in a " +
                        "notification. If the connection drops, it picks up from where it stopped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            is ModelState.Failed -> {
                Text(m.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Button(onClick = startDownload, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Try again") }
            }
            ModelState.Loading -> {
                CircularProgressIndicator()
                Text("Starting the speech model…", style = MaterialTheme.typography.bodyMedium)
            }
            ModelState.Ready -> Unit
        }
    }
}
