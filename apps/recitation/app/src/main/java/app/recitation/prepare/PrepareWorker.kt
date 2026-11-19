package app.recitation.prepare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.recitation.MainActivity
import app.recitation.R
import app.recitation.core.Aligner
import app.recitation.core.Hearing
import app.recitation.core.Recital
import app.recitation.data.Library
import app.recitation.data.QuranStore
import app.recitation.data.Track
import app.recitation.speech.Listener
import app.recitation.speech.ModelStore
import app.recitation.yt.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** What is being done to a recording right now. */
enum class Step { MODEL_COPY, MODEL_DOWNLOAD, DOWNLOAD, VERSES, LISTEN, ALIGN }

/** Why the speech model couldn't be copied from Accent Coach. */
data class ModelProblem(val reason: ModelStore.Lender, val detail: String?)

/** The user's choices that outlast the app being closed. */
object Settings {
    private fun store(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Whether the model may be downloaded even though Accent Coach is installed and could, once put right, lend it. */
    fun downloadModel(context: Context): Boolean = store(context).getBoolean("download_model", false)

    fun setDownloadModel(context: Context, allowed: Boolean) = store(context).edit().putBoolean("download_model", allowed).apply()
}

/** [trackId] is null while the speech model, which every recording needs, is being fetched. */
data class Progress(val trackId: String?, val step: Step, val fraction: Float)

/**
 * Gets every recording the user asked for ready to read along with: downloads its sound,
 * fetches the surah's words, has the speech model listen to it, and lines the words up
 * with what was heard. It runs as a foreground task with its progress in a notification,
 * so it carries on when the app is closed or the screen is off, and each step picks up
 * where it stopped if the phone restarts or the connection drops.
 */
class PrepareWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val library = Library.get(context)
    private val quran = QuranStore(context)
    private val model = ModelStore(context)
    private var listener: Listener? = null
    private var shown = ""

    override suspend fun doWork(): Result {
        createChannel(applicationContext)
        // Android can refuse this when the work restarts while the app is closed; the work still runs.
        runCatching { setForeground(foregroundInfo(applicationContext.getString(R.string.preparing))) }
        // Listening is an hour of work for a long surah; don't let the phone doze off in the middle.
        val awake = applicationContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "recitation:prepare")
        awake.acquire(6 * 60 * 60 * 1000L)
        try {
            _modelProblem.value = null
            while (true) {
                val track = next() ?: break
                try {
                    withContext(Dispatchers.IO) { prepare(track) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    // No connection, or YouTube hiccuped: WorkManager tries again once there's a network.
                    if (runAttemptCount < MAX_ATTEMPTS) return Result.retry()
                    fail(track, applicationContext.getString(R.string.error_network))
                } catch (e: ModelStore.Unavailable) {
                    // Every recording needs the model, so there's nothing to go on with. The
                    // recordings stay in the queue and the screen says what to do about it.
                    _modelProblem.value = ModelProblem(e.reason, e.detail)
                    return Result.success()
                } catch (e: Stopped) {
                    // The user cancelled it, or Android is stopping the work.
                    if (isStopped) return Result.retry()
                } catch (e: Exception) {
                    fail(track, e.message ?: applicationContext.getString(R.string.error_unknown))
                }
            }
            return Result.success()
        } finally {
            _progress.value = null
            listener?.close()
            if (awake.isHeld) awake.release()
        }
    }

    private fun next(): Track? =
        library.playlists.value.asSequence().flatMap { it.tracks }.firstOrNull { it.wanted && !it.ready && it.error == null && it.surah > 0 }

    private fun fail(track: Track, message: String) = library.update(track.id) { it.copy(wanted = false, error = message) }

    private class Stopped : Exception()

    private suspend fun prepare(track: Track) {
        val id = track.id
        fun wanted() = !isStopped && library.track(id)?.wanted == true
        fun report(step: Step, fraction: Float, forModel: Boolean = false) {
            _progress.value = Progress(if (forModel) null else id, step, fraction)
            show(track, step, fraction)
        }

        if (!model.isReady) {
            model.obtain(download = Settings.downloadModel(applicationContext)) { copied, p ->
                report(if (copied) Step.MODEL_COPY else Step.MODEL_DOWNLOAD, p, forModel = true)
            }
        }

        val audio = library.audio(id) ?: run {
            report(Step.DOWNLOAD, 0f)
            val source = YouTube.audio(id)
            val part = library.audioPart(id, source.extension)
            try {
                YouTube.download(source, part, ::wanted) { report(Step.DOWNLOAD, it) }
            } catch (_: InterruptedException) {
                throw Stopped()
            }
            File(part.path.removeSuffix(".part")).also { if (!part.renameTo(it)) throw IOException("couldn't keep the download") }
        }

        report(Step.VERSES, 0f)
        val verses = quran.surah(track.surah)

        // What the model heard is kept, so choosing another surah for a recording doesn't mean listening again.
        val notes = library.heard(id)
        val done = File(notes.path + ".done")
        val heard = if (done.exists()) {
            Hearing.read(notes)
        } else {
            report(Step.LISTEN, 0f)
            val listener = listener ?: Listener(model.modelFile, vocab()).also { listener = it }
            (listener.listen(audio, notes, ::wanted) { report(Step.LISTEN, it) } ?: throw Stopped()).also { done.createNewFile() }
        }

        report(Step.ALIGN, 0f)
        val recital = Recital(track.surah, verses.map { verse -> verse.words.map { it.arabic } })
        val aligner = Aligner(heard.rows, recital.phones)
        val starts = aligner.align() ?: throw IllegalStateException(applicationContext.getString(R.string.error_too_short))
        val timings = recital.timings(starts, heard.frames, aligner)
        library.timings(id, track.surah).writeText(timings.encode())
        if (timings.matches) {
            notes.delete()
            done.delete()
        }
        library.update(id) { it.copy(ready = true, wanted = false, clarity = timings.clarity, error = null) }
    }

    private fun vocab(): List<String> {
        val json = JSONObject(applicationContext.assets.open("vocab.json").bufferedReader().use { it.readText() })
        val vocab = arrayOfNulls<String>(json.length())
        for (key in json.keys()) vocab[json.getInt(key)] = key
        return vocab.map { it.orEmpty() }
    }

    private fun show(track: Track, step: Step, fraction: Float) {
        val text = applicationContext.getString(stepLabel(step), (fraction * 100).toInt())
        val line = if (step == Step.MODEL_COPY || step == Step.MODEL_DOWNLOAD) text else "${track.title}\n$text"
        if (line == shown) return
        shown = line
        notifications.notify(NOTIFICATION_ID, notification(line))
    }

    private fun foregroundInfo(text: String): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification(text))
        }

    private fun notification(text: String) =
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.preparing))
            .setContentText(text.substringAfterLast('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(applicationContext))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    companion object {
        private const val WORK_NAME = "prepare"
        private const val CHANNEL = "prepare"
        private const val NOTIFICATION_ID = 1
        private const val MAX_ATTEMPTS = 6

        private val _progress = MutableStateFlow<Progress?>(null)

        /** What the worker is doing, for the screens; null when it isn't running. */
        val progress: StateFlow<Progress?> = _progress.asStateFlow()

        private val _modelProblem = MutableStateFlow<ModelProblem?>(null)

        /** Set when the work stopped because Accent Coach is installed but couldn't lend its speech model. */
        val modelProblem: StateFlow<ModelProblem?> = _modelProblem.asStateFlow()

        fun stepLabel(step: Step): Int = when (step) {
            Step.MODEL_COPY -> R.string.step_model_copy
            Step.MODEL_DOWNLOAD -> R.string.step_model_download
            Step.DOWNLOAD -> R.string.step_download
            Step.VERSES -> R.string.step_verses
            Step.LISTEN -> R.string.step_listen
            Step.ALIGN -> R.string.step_align
        }

        /** Starts preparing what's wanted, or leaves a run that's already under way to pick the new work up. */
        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<PrepareWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.preparing), NotificationManager.IMPORTANCE_LOW),
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
