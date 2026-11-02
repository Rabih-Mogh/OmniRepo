package app.fajralarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.fajralarm.data.SettingsStore

/** Receives the AlarmManager broadcast and starts the ringing service. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                // A fresh alarm: reset snooze state and queue tomorrow's alarm right away,
                // so even if the user never touches the phone the next one is set.
                val store = SettingsStore(context)
                store.snoozeCount = 0
                store.snoozeUntil = 0L
                AlarmScheduler.reschedule(context, afterFire = true)
            }
            ACTION_SNOOZE_FIRE -> SettingsStore(context).snoozeUntil = 0L
            else -> return
        }
        try {
            ContextCompat.startForegroundService(context, AlarmService.startIntent(context))
        } catch (_: IllegalStateException) {
            // Only possible if exact alarms were revoked and Android refuses a background
            // foreground-service start; fall back to a plain full-screen notification.
            AlarmService.postFallbackNotification(context)
        }
    }

    companion object {
        const val ACTION_FIRE = "app.fajralarm.action.FIRE"
        const val ACTION_SNOOZE_FIRE = "app.fajralarm.action.SNOOZE_FIRE"
    }
}

/** Re-queues the alarm after reboot, app update, clock or timezone changes. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.reschedule(context)
        // A snooze that was pending across a reboot is lost; that's acceptable because
        // the original alarm time has long passed by then.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SettingsStore(context).snoozeUntil = 0L
        }
    }
}
