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
import app.fajralarm.core.DismissMission
import app.fajralarm.data.SettingsStore
import app.fajralarm.data.SinkProof
import app.fajralarm.data.StopReason
import app.fajralarm.ui.AlarmActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
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
            ACTION_DISMISS -> dismiss(reason ?: StopReason.NOTIFICATION)
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
        // The sink-photo mission is meant to be impossible to sleep through, so it keeps
        // ringing much longer; the cap only stops a phone left at home ringing all day.
        val ringLimit = if (settings.dismissMission == DismissMission.SINK_PHOTO) PHOTO_AUTO_STOP_MS else AUTO_STOP_MS

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FajrAlarm:ringing")
            .apply { acquire(ringLimit + 5_000) }

        playSound(settings.soundUri, settings.gradualVolume)
        if (settings.vibrate) startVibration()

        // Also launch the screen directly; on locked/idle devices the full-screen intent does this.
        try {
            startActivity(AlarmActivity.intent(this))
        } catch (_: Exception) {
            // Background activity starts may be blocked; the full-screen notification covers it.
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
        val store = SettingsStore(this)
        val settings = store.load()
        if (!canSnooze(this)) {
            dismiss(if (reason == StopReason.TIMED_OUT_SNOOZED) StopReason.TIMED_OUT else reason)
            return
        }
        if (_ringing.value) store.recordStop(reason, System.currentTimeMillis())
        AlarmScheduler.scheduleSnooze(this, settings.snoozeMinutes)
        stopRinging()
        val until = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(store.snoozeUntil))
        val n = NotificationCompat.Builder(this, FajrAlarmApp.CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Snoozed until $until")
            .setContentText("Tap to dismiss the snoozed alarm")
            .setContentIntent(servicePendingIntent(this, ACTION_DISMISS, 11))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(SNOOZE_NOTIFICATION_ID, n)
    }

    private fun dismiss(reason: StopReason) {
        // A dismiss can also arrive from the "Snoozed until" notice, when nothing is ringing.
        if (_ringing.value) SettingsStore(this).recordStop(reason, System.currentTimeMillis())
        AlarmScheduler.cancelSnooze(this)
        getSystemService(NotificationManager::class.java).cancel(SNOOZE_NOTIFICATION_ID)
        stopRinging()
    }

    private fun timeout() {
        if (!_ringing.value) return
        // Nobody responded: snooze once if allowed, otherwise give up quietly.
        if (canSnooze(this)) snooze(StopReason.TIMED_OUT_SNOOZED) else dismiss(StopReason.TIMED_OUT)
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
            .setContentTitle("Time for Fajr")
            .setContentText(settings.describeOffset())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
        if (canSnooze(this)) {
            b.addAction(0, "Snooze", servicePendingIntent(this, ACTION_SNOOZE, 12))
        }
        // With a mission (sum or sink photo) the alarm can only be turned off from the
        // alarm screen, so there's no notification shortcut to dismiss it.
        if (settings.dismissMission == DismissMission.NONE) {
            b.addAction(0, "Dismiss", servicePendingIntent(this, ACTION_DISMISS, 13))
        }
        return b.build()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val SNOOZE_NOTIFICATION_ID = 43
        private const val AUTO_STOP_MS = 10 * 60 * 1000L
        private const val PHOTO_AUTO_STOP_MS = 60 * 60 * 1000L

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
            return s.snoozeMinutes > 0 && store.snoozeCount < s.maxSnoozes
        }

        fun postFallbackNotification(context: Context) {
            val open = PendingIntent.getActivity(
                context, 14, Intent(context, app.fajralarm.ui.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, FajrAlarmApp.CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Time for Fajr")
                .setContentText("Allow exact alarms in settings so the full alarm can ring.")
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
