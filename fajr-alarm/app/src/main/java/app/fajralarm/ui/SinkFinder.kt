package app.fajralarm.ui

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import app.fajralarm.core.PickupDetector
import app.fajralarm.core.SinkStatus
import app.fajralarm.core.SinkVerifier
import app.fajralarm.detect.SinkDetector
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live camera that looks for a real sink and calls [onConfirmed] once, with the frame
 * that settled it, when one has been held in view long enough. Nothing counts until
 * the phone has been picked up, so a phone left ringing on the nightstand can't
 * mistake something for a sink. Calls [onUnavailable] if the detector can't start.
 */
@Composable
fun SinkFinder(onConfirmed: (Bitmap) -> Unit, onUnavailable: () -> Unit = {}) {
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
    val pickedUp = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) { onDispose(watchForPickup(context) { pickedUp.set(true) }) }

    CameraCapture(onFrame = { frame ->
        val next = if (!pickedUp.get()) {
            verifier.reset()
            SinkStatus.Resting
        } else {
            val result = detector.detect(frame) ?: return@CameraCapture
            verifier.offer(result)
        }
        mainExecutor.execute {
            if (done) return@execute
            status = next
            if (next == SinkStatus.Confirmed) {
                done = true
                currentOnConfirmed(frame)
            }
        }
    })
    Spacer(Modifier.height(8.dp))
    Text(
        when (val s = status) {
            SinkStatus.Resting -> "Pick up the phone and walk to a sink"
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

/**
 * Calls [onPickedUp] (on the main thread) once the phone has been tilted from where it
 * lay. Returns the function that stops listening. A phone without an accelerometer
 * counts as picked up straight away rather than locking the user out.
 */
private fun watchForPickup(context: Context, onPickedUp: () -> Unit): () -> Unit {
    val sensors = context.getSystemService(SensorManager::class.java)
    val accel = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    if (sensors == null || accel == null) {
        onPickedUp()
        return {}
    }
    val pickup = PickupDetector()
    val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (pickup.offer(e.values[0], e.values[1], e.values[2])) {
                onPickedUp()
                sensors.unregisterListener(this)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    sensors.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
    return { sensors.unregisterListener(listener) }
}
