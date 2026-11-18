package app.fajralarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fajralarm.R
import app.fajralarm.update.AppUpdate
import kotlinx.coroutines.launch

/**
 * Pops up when the repo has a newer build, and stays up while it downloads and installs.
 * Checks once each time the screen opens. Closing it doesn't stop a download.
 */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val state by AppUpdate.state.collectAsStateWithLifecycle()
    val open by AppUpdate.prompt.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AppUpdate.check(context) }

    val failed = state as? AppUpdate.State.Failed
    val release = (state as? AppUpdate.State.Available)?.release ?: failed?.release
    val downloading = state as? AppUpdate.State.Downloading
    if (!open || (release == null && downloading == null && state != AppUpdate.State.Installing)) return

    AlertDialog(
        onDismissRequest = AppUpdate::dismiss,
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    when {
                        failed != null -> failed.message
                        downloading != null -> stringResource(R.string.update_downloading, (downloading.progress * 100).toInt())
                        release != null -> stringResource(R.string.update_available, release.versionName, release.size / 1_000_000)
                        else -> stringResource(R.string.update_installing)
                    },
                )
                downloading?.let { LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            if (release != null) {
                TextButton(onClick = { AppUpdate.install(context, release) }) {
                    Text(stringResource(if (failed != null) R.string.try_again else R.string.update))
                }
            } else {
                TextButton(onClick = AppUpdate::dismiss) { Text(stringResource(R.string.update_hide)) }
            }
        },
        dismissButton = if (release == null) {
            null
        } else {
            { TextButton(onClick = AppUpdate::dismiss) { Text(stringResource(if (failed != null) R.string.close else R.string.update_later)) } }
        },
    )
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
            Text(
                stringResource(R.string.version, version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                enabled = state != AppUpdate.State.Checking && state !is AppUpdate.State.Downloading && state != AppUpdate.State.Installing,
                onClick = { scope.launch { AppUpdate.check(context, quiet = false) } },
            ) { Text(stringResource(R.string.check_for_updates)) }
        }
        val note = when {
            state == AppUpdate.State.Checking -> stringResource(R.string.update_checking)
            state == AppUpdate.State.UpToDate -> stringResource(R.string.update_latest)
            failed != null && failed.release == null -> failed.message
            else -> null
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
