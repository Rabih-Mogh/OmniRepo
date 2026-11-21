package app.fajralarm.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.fajralarm.FajrAlarmApp
import app.fajralarm.R
import app.fajralarm.core.AlarmPlanner
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.DismissMission
import app.fajralarm.core.SinkRinging
import app.fajralarm.data.SettingsStore
import app.fajralarm.data.SinkProof
import app.fajralarm.data.StopReason
import app.fajralarm.ui.AlarmActivity
import app.fajralarm.ui.describeOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Date

/**
 * Foreground service that rings the alarm: plays the sound on the alarm stream,
 * vibrates, and shows a full-screen notification that opens [AlarmActivity]
 * over the lock screen.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volume = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra(EXTRA_REASON)?.let { n -> StopReason.values().firstOrNull { it.name == n } }
        when (intent?.action) {
            ACTION_SNOOZE -> snooze(reason ?: StopReason.SNOOZED)
            ACTION_DISMISS -> {
                val r = reason ?: StopReason.NOTIFICATION
                // With a mission, only doing it stops the alarm, ringing or snoozed. This also
                // covers taps on notifications posted by older versions of the app.
                if (missionRequired() && r !in MISSION_REASONS) ignoreCommand() else dismiss(r)
            }
            else -> startRinging()
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        // Must call startForeground promptly on every start request.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildRingingNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        if (_ringing.value) return // already ringing (e.g. test pressed twice)
        _ringing.value = true

        val store = SettingsStore(this)
        store.recordRinging(System.currentTimeMillis())
        // A sink photo from an earlier alarm would be misleading next to this one.
        SinkProof.clear(this)
        val settings = store.load()
        // The sink mission is meant to be impossible to sleep through, so it keeps ringing
        // much longer, though not past sunrise.
        val ringLimit = if (settings.dismissMission == DismissMission.SINK_PHOTO) {
            SinkRinging.round(untilSunrise(settings)).toMillis()
        } else {
            AUTO_STOP_MS
        }

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FajrAlarm:ringing")
            .apply { acquire(ringLimit + 5_000) }

        playSound(settings.soundUri, settings.gradualVolume)
        if (settings.vibrate) startVibration()

        // Also launch the screen directly. The full-screen intent only opens it when the
        // screen is off or locked (and some phones ignore it); Android blocks this start
        // from the background unless "Display over other apps" is allowed.
        try {
            startActivity(AlarmActivity.intent(this))
        } catch (_: Exception) {
            // Blocked: the full-screen notification is all that's left.
        }

        handler.postDelayed({ timeout() }, ringLimit)
    }

    private fun playSound(soundUri: String?, gradual: Boolean) {
        val candidates = buildList<Uri> {
            soundUri?.let { add(Uri.parse(it)) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let { add(it) }
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attrs)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                volume = if (gradual) 0.08f else 1f
                mp.setVolume(volume, volume)
                mp.start()
                player = mp
                if (gradual) rampVolume()
                return
            } catch (_: Exception) {
                mp.release()
            }
        }
    }

    /** Fade in from quiet to full volume over ~45 seconds. */
    private fun rampVolume() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                val mp = player ?: return
                volume = (volume + 0.04f).coerceAtMost(1f)
                try {
                    mp.setVolume(volume, volume)
                } catch (_: IllegalStateException) {
                    return
                }
                if (volume < 1f) handler.postDelayed(this, 2_000)
            }
        }, 2_000)
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        v.vibrate(effect, attrs)
    }

    private fun snooze(reason: StopReason) {
        if (!canSnooze(this)) {
            // No snooze to be had: a snooze tap mustn't become a way round the mission.
            if (missionRequired()) ignoreCommand() else dismiss(reason)
            return
        }
        pause(reason, SettingsStore(this).load().snoozeMinutes)
    }

    /** Goes quiet for [minutes] and rings again after them. */
    private fun pause(reason: StopReason, minutes: Int) {
        val store = SettingsStore(this)
        val settings = store.load()
        if (_ringing.value) store.recordStop(reason, System.currentTimeMillis())
        AlarmScheduler.scheduleSnooze(this, minutes)
        stopRinging()
        val until = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(store.snoozeUntil))
        val b = NotificationCompat.Builder(this, FajrAlarmApp.CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.snoozed_until, until))
            .setAutoCancel(true)
        if (settings.dismissMission == DismissMission.NONE) {
            b.setContentText(getString(R.string.notification_snoozed_dismiss))
                .setContentIntent(servicePendingIntent(this, ACTION_DISMISS, 11))
        } else {
            // The mission is what gets you out of bed, so a snoozed alarm can't be called off here.
            b.setContentText(getString(R.string.notification_snoozed_mission))
                .setContentIntent(openAppPendingIntent(this))
        }
        val n = b.build()
        getSystemService(NotificationManager::class.java).notify(SNOOZE_NOTIFICATION_ID, n)
    }

    private fun dismiss(reason: StopReason) {
        // A dismiss can also arrive from the "Snoozed until" notice, when nothing is ringing.
        if (_ringing.value) SettingsStore(this).recordStop(reason, System.currentTimeMillis())
        AlarmScheduler.cancelSnooze(this)
        getSystemService(NotificationManager::class.java).cancel(SNOOZE_NOTIFICATION_ID)
        stopRinging()
    }

    private fun missionRequired(): Boolean =
        SettingsStore(this).load().dismissMission != DismissMission.NONE

    /** Drops a command that isn't allowed; the service only stays up while it rings. */
    private fun ignoreCommand() {
        if (!_ringing.value) stopSelf()
    }

    private fun timeout() {
        if (!_ringing.value) return
        val settings = SettingsStore(this).load()
        when {
            // The sink mission rings on towards sunrise, whatever snoozes are left.
            settings.dismissMission == DismissMission.SINK_PHOTO ->
                if (SinkRinging.ringsAgain(untilSunrise(settings))) {
                    pause(StopReason.TIMED_OUT_SNOOZED, SinkRinging.REST_MINUTES)
                } else {
                    dismiss(StopReason.TIMED_OUT)
                }
            // Nobody responded: snooze once if allowed, otherwise give up quietly.
            canSnooze(this) -> pause(StopReason.TIMED_OUT_SNOOZED, settings.snoozeMinutes)
            else -> dismiss(StopReason.TIMED_OUT)
        }
    }

    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        player?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        _ringing.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (_ringing.value) {
            SettingsStore(this).recordStop(StopReason.STOPPED_BY_ANDROID, System.currentTimeMillis())
            handler.removeCallbacksAndMessages(null)
            player?.release()
            vibrator?.cancel()
            wakeLock?.let { if (it.isHeld) it.release() }
            _ringing.value = false
        }
        super.onDestroy()
    }

    private fun buildRingingNotification(): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 10, AlarmActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val settings = SettingsStore(this).load()
        val b = NotificationCompat.Builder(this, FajrAlarmApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.alarm_title))
            .setContentText(settings.describeOffset(this))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
        if (canSnooze(this)) {
            b.addAction(0, getString(R.string.snooze), servicePendingIntent(this, ACTION_SNOOZE, 12))
        }
        // With a mission (sum or sink photo) the alarm can only be turned off from the
        // alarm screen, so there's no notification shortcut to dismiss it.
        if (settings.dismissMission == DismissMission.NONE) {
            b.addAction(0, getString(R.string.dismiss), servicePendingIntent(this, ACTION_DISMISS, 13))
        }
        return b.build()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val SNOOZE_NOTIFICATION_ID = 43
        private const val AUTO_STOP_MS = 10 * 60 * 1000L

        const val ACTION_START = "app.fajralarm.action.START"
        const val ACTION_SNOOZE = "app.fajralarm.action.SNOOZE"
        const val ACTION_DISMISS = "app.fajralarm.action.DISMISS"
        private const val EXTRA_REASON = "reason"

        private val _ringing = MutableStateFlow(false)

        /** True while the alarm is sounding; the alarm screen closes when this turns false. */
        val ringing: StateFlow<Boolean> = _ringing.asStateFlow()

        fun startIntent(context: Context): Intent =
            Intent(context, AlarmService::class.java).setAction(ACTION_START)

        fun commandIntent(context: Context, action: String, reason: StopReason? = null): Intent =
            Intent(context, AlarmService::class.java).setAction(action).apply {
                reason?.let { putExtra(EXTRA_REASON, it.name) }
            }

        fun canSnooze(context: Context): Boolean {
            val store = SettingsStore(context)
            val s = store.load()
            return snoozesLeft(store, s) && s.snoozeAllowed(untilSunrise(s))
        }

        /** True when there are snoozes left and only the coming sunrise rules one out. */
        fun sunriseStopsSnooze(context: Context): Boolean {
            val store = SettingsStore(context)
            val s = store.load()
            return snoozesLeft(store, s) && !s.snoozeAllowed(untilSunrise(s))
        }

        private fun snoozesLeft(store: SettingsStore, s: AlarmSettings): Boolean =
            s.snoozeMinutes > 0 && store.snoozeCount < s.maxSnoozes

        /** Time left until the next sunrise, or null where it isn't known. */
        private fun untilSunrise(s: AlarmSettings): Duration? {
            val now = ZonedDateTime.now()
            return AlarmPlanner.nextSunrise(s, now)?.let { Duration.between(now, it) }
        }

        /** The ways the alarm screen turns the alarm off once a mission is done. */
        private val MISSION_REASONS = setOf(StopReason.SINK, StopReason.SUM)

        private fun openAppPendingIntent(context: Context, requestCode: Int = 14): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode, Intent(context, app.fajralarm.ui.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun postFallbackNotification(context: Context) {
            val open = openAppPendingIntent(context)
            val n = NotificationCompat.Builder(context, FajrAlarmApp.CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.alarm_title))
                .setContentText(context.getString(R.string.notification_allow_exact))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setAutoCancel(true)
                .build()
            try {
                context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
            } catch (_: SecurityException) {
                // Notifications permission denied; nothing more we can do.
            }
        }

        private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context, requestCode, commandIntent(context, action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
