package app.fajralarm.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.fajralarm.core.SinkStatus
import app.fajralarm.core.SinkVerifier
import app.fajralarm.detect.SinkDetector

/**
 * Live camera that looks for a real sink and calls [onConfirmed] once, when one has
 * been held in view long enough. Calls [onUnavailable] if the detector can't start.
 */
@Composable
fun SinkFinder(onConfirmed: () -> Unit, onUnavailable: () -> Unit = {}) {
    val context = LocalContext.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val detector = remember { runCatching { SinkDetector.create(context) }.getOrNull() }
    DisposableEffect(detector) { onDispose { detector?.close() } }
    if (detector == null) {
        LaunchedEffect(Unit) { onUnavailable() }
        Text("The sink detector couldn't start on this phone.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        return
    }

    // The verifier is only touched on the camera's analysis thread; UI state only on the main thread.
    val verifier = remember { SinkVerifier() }
    var status by remember { mutableStateOf<SinkStatus>(SinkStatus.Searching) }
    var done by remember { mutableStateOf(false) }
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)

    CameraCapture(onFrame = { frame ->
        val result = detector.detect(frame) ?: return@CameraCapture
        val next = verifier.offer(result)
        mainExecutor.execute {
            if (done) return@execute
            status = next
            if (next == SinkStatus.Confirmed) {
                done = true
                currentOnConfirmed()
            }
        }
    })
    Spacer(Modifier.height(8.dp))
    Text(
        when (val s = status) {
            SinkStatus.Searching -> "Looking for a sink…"
            SinkStatus.Dark -> "Too dark. Turn the light on."
            SinkStatus.OnScreen -> "That sink is on a screen. Find a real one."
            is SinkStatus.Holding -> if (s.progress > 0f) "Sink found, keep it in view…" else "Looking for a sink…"
            SinkStatus.Confirmed -> "Sink found"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    (status as? SinkStatus.Holding)?.let { h ->
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { h.progress }, modifier = Modifier.fillMaxWidth())
    }
}
