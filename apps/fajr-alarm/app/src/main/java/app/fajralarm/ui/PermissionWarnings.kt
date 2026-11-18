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
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import app.fajralarm.R

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
    val overlayOff = !Settings.canDrawOverlays(context)
    val batteryOptimised = !context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)

    if (notificationsOff) {
        Warning(R.string.warning_notifications, R.string.allow, onRequestNotifications)
    }
    if (exactOff && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Warning(R.string.warning_exact_alarms, R.string.fix) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
            )
        }
    }
    if (fullScreenOff && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Warning(R.string.warning_full_screen, R.string.fix) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
            )
        }
    }
    if (overlayOff) {
        Warning(R.string.warning_overlay, R.string.allow) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                )
            } catch (_: ActivityNotFoundException) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            }
        }
    }
    if (batteryOptimised) {
        Warning(R.string.warning_battery, R.string.fix) {
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
                stringResource(R.string.samsung_tip),
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
                }) { Text(stringResource(R.string.app_settings)) }
                TextButton(onClick = {
                    prefs.edit().putBoolean(SAMSUNG_TIP, true).apply()
                    hidden = true
                }) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

@Composable
private fun Warning(@StringRes text: Int, @StringRes action: Int, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(text), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onClick) { Text(stringResource(action)) }
        }
    }
}

private const val TIPS_PREFS = "tips"
private const val SAMSUNG_TIP = "samsung_never_sleeping_done"
