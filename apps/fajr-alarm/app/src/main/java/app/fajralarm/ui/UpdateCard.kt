package app.fajralarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fajralarm.update.AppUpdate
import kotlinx.coroutines.launch

/** Appears when the repo has a newer build; checks once each time the screen opens. */
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val state by AppUpdate.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AppUpdate.check(context) }

    when (val s = state) {
        is AppUpdate.State.Available -> UpdateBanner(
            "Version ${s.release.versionName} is available (${s.release.size / 1_000_000} MB).", "Update",
        ) { AppUpdate.install(context, s.release) }
        is AppUpdate.State.Downloading -> UpdateBanner("Downloading the update… ${(s.progress * 100).toInt()}%", progress = s.progress)
        AppUpdate.State.Installing -> UpdateBanner("Installing… The app closes when it's done. Open it again afterwards.")
        is AppUpdate.State.Failed -> s.release?.let { UpdateBanner(s.message, "Try again") { AppUpdate.install(context, it) } }
        else -> Unit
    }
}

/** The installed version, with a button to look for a newer one. */
@Composable
fun VersionFooter() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AppUpdate.state.collectAsStateWithLifecycle()
    val version = remember { AppUpdate.installedVersionName(context) }
    val failed = state as? AppUpdate.State.Failed

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Version $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(
                enabled = state != AppUpdate.State.Checking && state !is AppUpdate.State.Downloading && state != AppUpdate.State.Installing,
                onClick = { scope.launch { AppUpdate.check(context, quiet = false) } },
            ) { Text("Check for updates") }
        }
        val note = when {
            state == AppUpdate.State.Checking -> "Checking…"
            state == AppUpdate.State.UpToDate -> "You have the latest version."
            failed != null && failed.release == null -> failed.message
            else -> null
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun UpdateBanner(text: String, action: String? = null, progress: Float? = null, onClick: () -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, Modifier.weight(1f).padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                action?.let { TextButton(onClick = onClick) { Text(it) } }
            }
            progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) }
        }
    }
}
