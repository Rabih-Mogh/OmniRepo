package app.accentcoach.update

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/**
 * Keeps an update downloading while the app isn't on screen. Left alone, Android puts an
 * app to sleep soon after the screen turns off, and the download stops with it. A service
 * that shows a notification is how an app says the work is something the user asked for.
 * The download itself is [AppUpdate]'s: this holds the phone awake while it runs and
 * shows how far it is.
 */
class UpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private var awake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // First thing: Android gives a service started this way only a few seconds to show its notification.
        ServiceCompat.startForeground(
            this,
            UpdateNotice.PROGRESS,
            UpdateNotice.progress(this, UpdateNotice.percent(AppUpdate.state.value)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (watching?.isActive != true) {
            // Without this the processor sleeps with the screen, and the download waits for it to wake.
            val lock = awake ?: getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:update")
                .apply { setReferenceCounted(false) }
                .also { awake = it }
            lock.acquire(AWAKE_MS)
            watching = scope.launch {
                combine(AppUpdate.working, AppUpdate.state) { working, state -> if (working) UpdateNotice.percent(state) else null }
                    .takeWhile { it != null }
                    .distinctUntilChanged()
                    .collect { UpdateNotice.show(this@UpdateService, UpdateNotice.PROGRESS, UpdateNotice.progress(this@UpdateService, it!!)) }
                // Android has the file, or the attempt failed: either way there is nothing left to keep alive.
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // Android 15 gives a download service six hours a day. No update takes that long, but it must stop when told.
    override fun onTimeout(startId: Int, fgsType: Int) = stopSelf()

    override fun onDestroy() {
        scope.cancel()
        awake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val AWAKE_MS = 30 * 60 * 1000L

        /** Best effort: if Android won't start it, the download still runs for as long as the app stays open. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, UpdateService::class.java))
            } catch (_: Exception) {
                // Only allowed while the app is on screen, which it is whenever the user asks for an update.
            }
        }
    }
}

/** The update's notifications: how far the download is, and a line when it needs the user. */
internal object UpdateNotice {
    const val PROGRESS = 7301
    const val RESULT = 7302
    private const val CHANNEL = "app_updates"

    /** 0 to 100 while downloading, -1 once the file is being handed to Android. */
    fun percent(state: AppUpdate.State): Int =
        (state as? AppUpdate.State.Downloading)?.let { (it.progress * 100).toInt() } ?: -1

    fun progress(context: Context, percent: Int): Notification =
        builder(context)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (percent >= 0) "Downloading the update… $percent%" else "Installing the update…")
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)
            // Android otherwise holds a new service's notification back for ten seconds.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** Says, outside the app, that the update is waiting for the user or has failed. Tapping it opens the app. */
    fun result(context: Context, text: String) = show(
        context,
        RESULT,
        builder(context)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("App update")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build(),
    )

    fun clear(context: Context) = manager(context).cancel(RESULT)

    fun show(context: Context, id: Int, notification: Notification) = manager(context).notify(id, notification)

    /** Whether the user is looking at the app right now. Android only lets an app open a screen when they are. */
    fun appOnScreen(): Boolean {
        val me = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(me)
        return me.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun builder(context: Context): NotificationCompat.Builder {
        // Quiet: progress shouldn't buzz. Creating a channel that exists changes nothing.
        manager(context).createNotificationChannel(
            NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_LOW),
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
    }
}
