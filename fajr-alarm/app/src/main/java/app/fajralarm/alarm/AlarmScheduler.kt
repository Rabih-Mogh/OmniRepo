package app.fajralarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.fajralarm.core.AlarmPlanner
import app.fajralarm.core.PlannedAlarm
import app.fajralarm.data.SettingsStore
import app.fajralarm.ui.MainActivity
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Keeps exactly one "next Fajr alarm" queued with AlarmManager. Every time the alarm
 * fires (or settings, time, timezone change, or the phone reboots) the next one is
 * recalculated, so the alarm tracks Fajr as it shifts day by day.
 */
object AlarmScheduler {

    private const val REQ_MAIN = 1
    private const val REQ_SNOOZE = 2
    private const val REQ_SHOW = 3

    /**
     * Recalculate and (re)queue the next alarm. Returns what was scheduled.
     *
     * [afterFire] is true when called from the alarm that just went off; a small lead
     * then guarantees we move on to the following day. Otherwise no lead is used, so
     * an alarm due in the next few seconds is never skipped.
     */
    fun reschedule(context: Context, afterFire: Boolean = false): PlannedAlarm? {
        val store = SettingsStore(context)
        val settings = store.load()
        val am = context.getSystemService(AlarmManager::class.java)
        val fire = mainPendingIntent(context)

        val planned = AlarmPlanner.nextAlarm(
            settings,
            ZonedDateTime.now(ZoneId.systemDefault()),
            minLeadSeconds = if (afterFire) 30 else 0,
        )
        if (planned == null) {
            am.cancel(fire)
            store.nextAlarmAt = 0L
            return null
        }
        val at = planned.triggerAt.toInstant().toEpochMilli()
        setExact(context, am, at, fire)
        store.nextAlarmAt = at
        return planned
    }

    fun scheduleSnooze(context: Context, minutes: Int) {
        val store = SettingsStore(context)
        val at = System.currentTimeMillis() + minutes * 60_000L
        val am = context.getSystemService(AlarmManager::class.java)
        setExact(context, am, at, snoozePendingIntent(context))
        store.snoozeUntil = at
        store.snoozeCount = store.snoozeCount + 1
    }

    fun cancelSnooze(context: Context) {
        val store = SettingsStore(context)
        context.getSystemService(AlarmManager::class.java).cancel(snoozePendingIntent(context))
        store.snoozeUntil = 0L
        store.snoozeCount = 0
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun setExact(context: Context, am: AlarmManager, atMillis: Long, operation: PendingIntent) {
        if (canScheduleExact(context)) {
            // setAlarmClock is the most reliable option: it is exempt from Doze and
            // shows the alarm icon in the status bar like a regular clock alarm.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, showPendingIntent(context)), operation)
        } else {
            // Best effort if the user revoked exact alarms (Android 12/12L only).
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    private fun mainPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQ_MAIN,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun snoozePendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQ_SNOOZE,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_SNOOZE_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun showPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, REQ_SHOW,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
