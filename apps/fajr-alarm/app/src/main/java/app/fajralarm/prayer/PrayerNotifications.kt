package app.fajralarm.prayer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.fajralarm.FajrAlarmApp
import app.fajralarm.R
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.AlertSound
import app.fajralarm.core.PrayerMoment
import app.fajralarm.core.PrayerNow
import app.fajralarm.ui.MainActivity
import app.fajralarm.ui.PrayerAlertActivity
import app.fajralarm.ui.alertText
import app.fajralarm.ui.clockTime
import app.fajralarm.ui.label
import java.time.ZonedDateTime

/** The two notifications about the day's prayers: a time arriving, and the one that stays on the lock screen. */
object PrayerNotifications {

    private const val ALERT_ID = 31
    private const val CURRENT_ID = 32
    private const val REQ_OPEN = 22
    private const val REQ_ALERT_SCREEN = 25

    /** A notification posted late still stays up at least this long. */
    private const val ALERT_SHORTEST_MS = 60_000L

    /**
     * A notification that [moment] has arrived, heard the way [sound] says: the phone's
     * notification sound, a vibration, or neither. With [fullScreen] it also opens
     * [PrayerAlertActivity] over a locked or sleeping phone; on a phone in use Android
     * shows it as a banner instead. Either way the notification stays in the shade until
     * it is cleared, or until [keepMinutes] after the time itself.
     */
    fun alert(
        context: Context,
        moment: PrayerMoment,
        fullScreen: Boolean = false,
        sound: AlertSound = AlertSound.SOUND,
        keepMinutes: Int = AlarmSettings.DEFAULT_ALERT_KEEP_MINUTES,
    ) {
        val leaves = moment.at.toInstant().toEpochMilli() + keepMinutes * 60_000L
        val screen = PendingIntent.getActivity(
            context, REQ_ALERT_SCREEN,
            PrayerAlertActivity.intent(context, moment),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = when (sound) {
            AlertSound.SOUND -> FajrAlarmApp.CHANNEL_PRAYER
            AlertSound.VIBRATE -> FajrAlarmApp.CHANNEL_PRAYER_VIBRATE
            AlertSound.SILENT -> FajrAlarmApp.CHANNEL_PRAYER_SILENT
        }
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(R.string.prayer_alert_title, context.getString(moment.label), moment.at.clockTime(context)),
            )
            .setContentText(context.getString(moment.prayer.alertText))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(moment.at.toInstant().toEpochMilli())
            .setAutoCancel(true)
            .setTimeoutAfter((leaves - System.currentTimeMillis()).coerceAtLeast(ALERT_SHORTEST_MS))
            .setContentIntent(if (fullScreen) screen else openApp(context))
            .apply { if (fullScreen) setFullScreenIntent(screen, true) }
            .build()
        post(context, ALERT_ID, n)
    }

    /**
     * Shows the current prayer and counts down the time it has left, silently and for as
     * long as it lasts; with no prayer current it counts down to the next one. The
     * countdown ticks by itself. Null takes the notification down.
     */
    fun showCurrent(context: Context, state: PrayerNow?) {
        if (state == null) {
            NotificationManagerCompat.from(context).cancel(CURRENT_ID)
            return
        }
        val text = CurrentPrayerText.of(context, state)
        val views = RemoteViews(context.packageName, R.layout.notification_current_prayer).apply {
            setTextViewText(R.id.current_headline, text.headline)
            setTextViewText(R.id.current_next, text.next)
            setCountdown(R.id.current_countdown, text.countsDownTo)
        }
        val n = NotificationCompat.Builder(context, FajrAlarmApp.CHANNEL_NOW)
            .setSmallIcon(R.drawable.ic_notification)
            // Shown where the phone doesn't draw the custom layout, so it has to make sense without a countdown.
            .setContentTitle(text.plain)
            .setContentText(text.next)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setWhen(text.countsDownTo.toInstant().toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .build()
        post(context, CURRENT_ID, n)
    }

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications permission denied; the main screen's warning says so.
        }
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, REQ_OPEN,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** The words for "which prayer is it and how long is left", shared by the notification and the widgets. */
class CurrentPrayerText(
    /** "Asr ends in" or "Dhuhr starts in": a countdown to [countsDownTo] goes after it. */
    val headline: String,
    /** The same without a countdown: "Asr until 4:31 PM" or "Dhuhr at 11:25 AM". */
    val plain: String,
    /** "Next: Maghrib 4:31 PM". */
    val next: String,
    val countsDownTo: ZonedDateTime,
) {
    companion object {
        fun of(context: Context, state: PrayerNow): CurrentPrayerText {
            val next = context.getString(
                R.string.next_prayer, context.getString(state.next.label), state.next.at.clockTime(context),
            )
            val current = state.current
            val ends = state.currentEnds
            return if (current != null && ends != null) {
                val name = context.getString(current.label)
                CurrentPrayerText(
                    headline = context.getString(R.string.now_ends_in, name),
                    plain = context.getString(R.string.now_until, name, ends.clockTime(context)),
                    next = next,
                    countsDownTo = ends,
                )
            } else {
                val name = context.getString(state.next.label)
                CurrentPrayerText(
                    headline = context.getString(R.string.now_starts_in, name),
                    plain = context.getString(R.string.now_at, name, state.next.at.clockTime(context)),
                    next = next,
                    countsDownTo = state.next.at,
                )
            }
        }
    }
}

/** Starts the Chronometer [viewId] counting down to [until]; it then ticks by itself. */
fun RemoteViews.setCountdown(viewId: Int, until: ZonedDateTime) {
    val base = SystemClock.elapsedRealtime() + (until.toInstant().toEpochMilli() - System.currentTimeMillis())
    setChronometerCountDown(viewId, true)
    setChronometer(viewId, base, null, true)
}
