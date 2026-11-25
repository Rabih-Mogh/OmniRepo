package app.fajralarm.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.fajralarm.alarm.AlarmScheduler
import app.fajralarm.core.PrayerClock
import app.fajralarm.core.PrayerMoment
import app.fajralarm.data.SettingsStore
import app.fajralarm.widget.PrayerWidgets
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Keeps the widgets and the lock screen notification on the right prayer, and posts a
 * notification for the times that asked for one. Nothing runs in between: an alarm is
 * queued for the next time that matters, and the app sleeps until then.
 *
 * The phone is only woken for a time whose bell is on, or for every time while the
 * lock screen notification is up. Widgets alone never wake it: their alarm waits until
 * the phone is awake anyway, which is before anyone can look at a home screen.
 */
object PrayerTicker {

    private const val REQ_WAKE = 21
    private const val REQ_REDRAW = 24

    /** A time that came this long ago still gets its notification; later than that it is stale. */
    private const val ALERT_GRACE_MS = 10 * 60_000L

    /**
     * Brings everything up to date and queues the next wake-up. Safe to call at any
     * moment: when a time arrives, and whenever the settings, the clock or the widgets change.
     */
    fun refresh(context: Context) {
        val store = SettingsStore(context)
        val settings = store.load()
        val zone = ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()

        // The time that was being waited for has come: say so, once.
        store.awaitedPrayer?.let { (prayer, at) ->
            if (at <= nowMillis) {
                store.awaitedPrayer = null
                if (prayer in settings.alerts && nowMillis - at < ALERT_GRACE_MS) {
                    PrayerNotifications.alert(context, PrayerMoment(prayer, Instant.ofEpochMilli(at).atZone(zone)))
                }
            }
        }

        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val state = settings.calcParams()?.let { PrayerClock.at(now, it) }

        // Queue what comes next before drawing anything, so a failure to draw can't stop the chain.
        val nextAlert = state?.upcoming?.firstOrNull { it.prayer in settings.alerts }
        store.awaitedPrayer = nextAlert?.let { it.prayer to it.at.toInstant().toEpochMilli() }
        val wakeAt = listOfNotNull(nextAlert, state?.nextEvent.takeIf { settings.lockScreenCountdown })
            .minOfOrNull { it.at.toInstant().toEpochMilli() }
        val redrawAt = state?.nextEvent?.at?.toInstant()?.toEpochMilli()
            ?.takeIf { it != wakeAt && PrayerWidgets.anyPlaced(context) }
        queue(context, REQ_WAKE, AlarmManager.RTC_WAKEUP, wakeAt)
        queue(context, REQ_REDRAW, AlarmManager.RTC, redrawAt)

        PrayerWidgets.update(context, state)
        PrayerNotifications.showCurrent(context, state.takeIf { settings.lockScreenCountdown })
    }

    /** Sets the alarm [request] for [at], or clears it when [at] is null. */
    private fun queue(context: Context, request: Int, type: Int, at: Long?) {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(
            context, request,
            Intent(context, PrayerTickReceiver::class.java).setAction(PrayerTickReceiver.ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        when {
            at == null -> am.cancel(operation)
            // Not setAlarmClock: that would put these times in the status bar as alarms.
            !AlarmScheduler.canScheduleExact(context) -> am.setAndAllowWhileIdle(type, at, operation)
            // Only an alarm that wakes the phone needs to get through its deep sleep.
            type == AlarmManager.RTC_WAKEUP -> am.setExactAndAllowWhileIdle(type, at, operation)
            else -> am.setExact(type, at, operation)
        }
    }
}

/** Receives the wake-up at each time of the day. */
class PrayerTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TICK) PrayerTicker.refresh(context)
    }

    companion object {
        const val ACTION_TICK = "app.fajralarm.action.PRAYER_TICK"
    }
}
