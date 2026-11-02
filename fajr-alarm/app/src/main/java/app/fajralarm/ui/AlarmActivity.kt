package app.fajralarm.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fajralarm.alarm.AlarmService
import app.fajralarm.core.MathChallenge
import app.fajralarm.data.SettingsStore
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Full-screen alarm shown over the lock screen while the alarm rings. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    subtitle = settings.describeOffset(),
                    canSnooze = AlarmService.canSnooze(this),
                    snoozeMinutes = settings.snoozeMinutes,
                    challenge = settings.wakeUpChallenge,
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

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
private fun AlarmScreen(
    subtitle: String,
    canSnooze: Boolean,
    snoozeMinutes: Int,
    challenge: Boolean,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    var problem by remember { mutableStateOf(MathChallenge.random()) }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)),
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(8.dp))
            Text("Time for Fajr", style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                "Prayer is better than sleep",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))

            if (challenge) {
                Text("Solve to turn off the alarm", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text("${problem.question} = ?", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { v -> answer = v.filter { it.isDigit() }.take(4); wrong = false },
                    singleLine = true,
                    isError = wrong,
                    supportingText = { if (wrong) Text("Not quite — try this one") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    if (!challenge || answer.toIntOrNull() == problem.answer) {
                        onDismiss()
                    } else {
                        wrong = true
                        answer = ""
                        problem = MathChallenge.random()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("I'm up — dismiss") }

            if (canSnooze) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Snooze $snoozeMinutes min") }
            }
        }
    }
}
