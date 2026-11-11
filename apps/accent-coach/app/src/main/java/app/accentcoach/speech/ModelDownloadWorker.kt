package app.accentcoach.speech

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.accentcoach.MainActivity
import app.accentcoach.R
import kotlinx.coroutines.CancellationException

/**
 * Downloads the speech model in the background, with its progress in a notification.
 * It runs as a foreground task, so it carries on when the app is closed or the screen
 * is off. If the connection drops or Android stops it, WorkManager starts it again once
 * there's a network, and [ModelStore] resumes from where it stopped.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notifications = context.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result {
        val store = ModelStore(applicationContext)
        if (store.isReady) return Result.success()
        createChannel(applicationContext)
        setForeground(foregroundInfo(store.partialProgress))
        return try {
            var shownPct = -1
            store.download { p ->
                setProgressAsync(workDataOf(PROGRESS to p))
                val pct = (p * 100).toInt()
                if (pct != shownPct) {
                    shownPct = pct
                    notifications.notify(NOTIFICATION_ID, progressNotification(p))
                }
            }
            notifications.notify(DONE_NOTIFICATION_ID, doneNotification())
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network drops and server errors are usually brief: try again with back-off.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else Result.failure(workDataOf(ERROR to (e.message ?: "Download failed")))
        }
    }

    private fun foregroundInfo(progress: Float): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, progressNotification(progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, progressNotification(progress))
        }

    private fun progressNotification(progress: Float) =
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Downloading the speech model")
            .setContentText("${(progress * 100).toInt()}% of 318 MB")
            .setProgress(1000, (progress * 1000).toInt(), false)
            .setContentIntent(openApp(applicationContext))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun doneNotification() =
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Speech model ready")
            .setContentText("Tap to start practising")
            .setContentIntent(openApp(applicationContext))
            .setAutoCancel(true)
            .build()

    companion object {
        const val PROGRESS = "progress"
        const val ERROR = "error"
        const val WORK_NAME = "model-download"
        private const val CHANNEL = "download"
        private const val NOTIFICATION_ID = 1
        private const val DONE_NOTIFICATION_ID = 2
        private const val MAX_ATTEMPTS = 8

        /** Starts the download, or leaves one that's already under way alone. */
        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Model download", NotificationManager.IMPORTANCE_LOW),
            )
        }

        private fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
