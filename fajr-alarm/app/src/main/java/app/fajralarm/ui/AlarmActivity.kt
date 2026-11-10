package app.fajralarm.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fajralarm.alarm.AlarmService
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.DismissMission
import app.fajralarm.core.MathChallenge
import app.fajralarm.data.SettingsStore
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Full-screen alarm shown over the lock screen while the alarm rings. */
class AlarmActivity : ComponentActivity() {

    private var screenLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Over the lock screen some phones (Samsung) apply the short lock-screen timeout
        // and ignore FLAG_KEEP_SCREEN_ON, so also hold a bright-screen wake lock while
        // the alarm screen is up. It's deprecated but still honoured, and the only API
        // that keeps the display on regardless of the keyguard.
        @Suppress("DEPRECATION")
        screenLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "FajrAlarm:screen")
            .apply { acquire(SCREEN_LOCK_MAX_MS) }

        val settings = SettingsStore(this).load()
        setContent {
            FajrTheme {
                val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
                LaunchedEffect(ringing) {
                    if (!ringing) {
                        // Give the service a moment in case we raced its start-up.
                        delay(1_500)
                        if (!AlarmService.ringing.value) finish()
                    }
                }
                BackHandler { /* the alarm must be snoozed or dismissed explicitly */ }
                AlarmScreen(
                    settings = settings,
                    canSnooze = AlarmService.canSnooze(this),
                    onSnooze = { send(AlarmService.ACTION_SNOOZE) },
                    onDismiss = { send(AlarmService.ACTION_DISMISS) },
                )
            }
        }
    }

    private fun send(action: String) {
        startService(AlarmService.commandIntent(this, action))
        finish()
    }

    override fun onDestroy() {
        screenLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        /** Just over the longest the alarm can ring (the sink mission's 60 minutes). */
        private const val SCREEN_LOCK_MAX_MS = 61 * 60 * 1000L

        fun intent(context: Context): Intent =
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

/** Which mission actually runs: finding a sink needs the camera. */
private fun effectiveMission(context: Context, s: AlarmSettings): Pair<DismissMission, String?> {
    if (s.dismissMission != DismissMission.SINK_PHOTO) return s.dismissMission to null
    val camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    return when {
        !camera -> DismissMission.MATH to "Camera permission is off, so solve a sum instead."
        else -> DismissMission.SINK_PHOTO to null
    }
}

@Composable
private fun AlarmScreen(
    settings: AlarmSettings,
    canSnooze: Boolean,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val (mission, fallbackNote) = remember { effectiveMission(context, settings) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize().background(DawnGradient),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (mission == DismissMission.SINK_PHOTO) Arrangement.Top else Arrangement.Center,
        ) {
            val compact = mission == DismissMission.SINK_PHOTO
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)),
                fontSize = if (compact) 48.sp else 72.sp,
                fontWeight = FontWeight.Light,
            )
            Text("Time for Fajr", style = MaterialTheme.typography.headlineMedium)
            // The camera needs the room, so the sink screen keeps only the clock and title.
            if (!compact) {
                Text(
                    settings.describeOffset(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Prayer is better than sleep",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(if (compact) 16.dp else 40.dp))

            fallbackNote?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }

            when (mission) {
                DismissMission.NONE -> DismissButton("I'm up, dismiss", onDismiss)
                DismissMission.MATH -> MathMission(onDismiss)
                DismissMission.SINK_PHOTO -> SinkMission(onDismiss)
            }

            if (canSnooze) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Snooze ${settings.snoozeMinutes} min") }
            }
        }
    }
}

@Composable
private fun DismissButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(label) }
}

@Composable
private fun MathMission(onDismiss: () -> Unit) {
    var problem by remember { mutableStateOf(MathChallenge.random()) }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Text("Solve to turn off the alarm", style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
    Text("${problem.question} = ?", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = answer,
        onValueChange = { v -> answer = v.filter { it.isDigit() }.take(4); wrong = false },
        singleLine = true,
        isError = wrong,
        supportingText = { if (wrong) Text("Not quite, try this one") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    DismissButton("Turn off") {
        if (answer.toIntOrNull() == problem.answer) {
            onDismiss()
        } else {
            wrong = true
            answer = ""
            problem = MathChallenge.random()
        }
    }
}

@Composable
private fun SinkMission(onDismiss: () -> Unit) {
    // If the detector can't run on this phone, a sum still stops the alarm.
    var unavailable by remember { mutableStateOf(false) }
    if (unavailable) {
        Text("The sink detector couldn't start, so solve a sum instead.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        MathMission(onDismiss)
        return
    }
    Text(
        "Go to a sink and point the camera at it",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    SinkFinder(onConfirmed = onDismiss, onUnavailable = { unavailable = true })
}
