package app.fajralarm.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fajralarm.R
import app.fajralarm.core.PrayerMoment
import app.fajralarm.core.PrayerTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

/**
 * The full-screen alert for a prayer time that has arrived: its name and clock time
 * on a black screen, lock screen included, until it is answered. Its notification stays behind. It is not the Fajr
 * alarm: the notification makes whatever sound was chosen for it and there is no mission.
 */
class PrayerAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Black bars over a black screen, with pale icons in them.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
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
        show(intent)
    }

    // A later time arrived while this one was still up.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        show(intent)
    }

    private fun show(intent: Intent) {
        val prayer = PrayerTime.values().firstOrNull { it.name == intent.getStringExtra(EXTRA_PRAYER) }
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        if (prayer == null || at == 0L) {
            finish()
            return
        }
        val moment = PrayerMoment(prayer, Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
        setContent {
            FajrTheme {
                // OK closes this screen only: the notification stays in the shade for as long as the settings keep it.
                PrayerAlert(moment) { finish() }
            }
        }
    }

    companion object {
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_AT = "at"

        fun intent(context: Context, moment: PrayerMoment): Intent =
            Intent(context, PrayerAlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_PRAYER, moment.prayer.name)
                .putExtra(EXTRA_AT, moment.at.toInstant().toEpochMilli())
    }
}

/**
 * Black with a few pale marks, like a phone's always-on clock: the clock time large and
 * thin, the prayer over it, and a quiet button. Nothing is lit that doesn't need to be.
 */
@Composable
private fun PrayerAlert(moment: PrayerMoment, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pale = Color(0xFFE8E8E8)
    val dim = Color(0xFF8A8A8A)
    val dateFormat = remember {
        val locale = Locale.getDefault()
        DateTimeFormatter.ofPattern("EEE d MMM", locale).withDecimalStyle(DecimalStyle.of(locale))
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        moment.prayer.icon?.let {
            Icon(painterResource(it), contentDescription = null, tint = pale, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
        }
        Text(
            stringResource(moment.prayer.label),
            style = MaterialTheme.typography.headlineSmall,
            color = pale,
            textAlign = TextAlign.Center,
        )
        Text(
            moment.at.clockTime(context),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Thin,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(moment.at.format(dateFormat), style = MaterialTheme.typography.titleMedium, color = dim)
        Text(
            stringResource(moment.prayer.alertText),
            style = MaterialTheme.typography.bodyLarge,
            color = dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onDismiss,
            border = BorderStroke(1.dp, dim),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = pale),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.prayer_alert_dismiss), style = MaterialTheme.typography.titleMedium)
        }
    }
}
