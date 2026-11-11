package app.fajralarm.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

/**
 * Cards for every setting that can stop the alarm screen from appearing on its own.
 * Shown on the main screen and on sink setup; pass a value that changes on resume as
 * [tick] so the state is re-read after the user comes back from system settings.
 */
@Composable
fun PermissionWarnings(
    context: Context,
    @Suppress("UNUSED_PARAMETER") tick: Int,
    onRequestNotifications: () -> Unit,
) {
    val notificationsOff = !NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactOff = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val fullScreenOff = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val batteryOptimised = !context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)

    if (notificationsOff) {
        Warning("Notifications are off, so the alarm can't show.", "Allow", onRequestNotifications)
    }
    if (exactOff && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Warning("Exact alarms are not allowed; the alarm may ring late.", "Fix") {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
            )
        }
    }
    if (fullScreenOff && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Warning("Full-screen alarms are blocked; you'll only get a notification.", "Fix") {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
            )
        }
    }
    if (batteryOptimised) {
        Warning("Battery optimisation is on, so the phone may put the alarm to sleep.", "Fix") {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                )
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
    SamsungTip(context)
}

/**
 * Samsung's "Sleeping apps" lists can freeze the app even with battery optimisation
 * off, and apps can't read those lists, so this is a one-off reminder the user hides.
 */
@Composable
private fun SamsungTip(context: Context) {
    if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return
    val prefs = remember { context.getSharedPreferences(TIPS_PREFS, Context.MODE_PRIVATE) }
    var hidden by remember { mutableStateOf(prefs.getBoolean(SAMSUNG_TIP, false)) }
    if (hidden) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                "Samsung phones can put the alarm to sleep. In Settings → Battery and device care → " +
                    "Battery → Background usage limits, add Fajr Alarm to Never sleeping apps.",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("App settings") }
                TextButton(onClick = {
                    prefs.edit().putBoolean(SAMSUNG_TIP, true).apply()
                    hidden = true
                }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun Warning(text: String, action: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

private const val TIPS_PREFS = "tips"
private const val SAMSUNG_TIP = "samsung_never_sleeping_done"
