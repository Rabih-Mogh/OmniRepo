package app.accentcoach.ui

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.accentcoach.ModelState
import app.accentcoach.PracticeViewModel

/** First run: fetch the speech model, then hand over to practice. */
@Composable
fun SetupScreen(vm: PracticeViewModel) {
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
                Button(onClick = vm::downloadModel, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Download (318 MB)") }
            }
            is ModelState.Downloading -> {
                LinearProgressIndicator(progress = { m.progress }, modifier = Modifier.fillMaxWidth())
                Text("Downloading… ${(m.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Keep the app open. If it's interrupted, it carries on from where it stopped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            is ModelState.Failed -> {
                Text(m.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Button(onClick = vm::downloadModel, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Try again") }
            }
            ModelState.Loading -> {
                CircularProgressIndicator()
                Text("Starting the speech model…", style = MaterialTheme.typography.bodyMedium)
            }
            ModelState.Ready -> Unit
        }
    }
}
