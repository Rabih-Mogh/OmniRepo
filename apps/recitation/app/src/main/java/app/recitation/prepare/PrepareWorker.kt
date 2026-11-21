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
import app.recitation.core.Heard
import app.recitation.core.Hearing
import app.recitation.core.LiveAlignment
import app.recitation.core.Recital
import app.recitation.core.Timings
import app.recitation.data.Library
import app.recitation.data.QuranStore
import app.recitation.data.Settings
import app.recitation.data.Track
import app.recitation.speech.Listener
import app.recitation.speech.EngineStore
import app.recitation.speech.ModelStore
import app.recitation.yt.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** What is being done to a recording right now. */
enum class Step { MODEL_CHECK, MODEL_DOWNLOAD, ENGINE, DOWNLOAD, VERSES, LISTEN, ALIGN, RETRY }

/** A recording's word timings while, and just after, it is listened to. */
class Live(val trackId: String, val surah: Int, val timings: Timings)

/** Where a recording is being played, in ms. */
class Playing(val trackId: String, val ms: Int)

/** Why the speech model couldn't be copied from Accent Coach. */
data class ModelProblem(val reason: ModelStore.Lender, val detail: String?)

/** [trackId] is null while the speech model, which every recording needs, is being fetched. */
data class Progress(val trackId: String?, val step: Step, val fraction: Float, val speed: Float = 0f)

/**
 * Gets every recording the user asked for ready to read along with: downloads its sound,
 * fetches the surah's words, has the speech model listen to it, and lines the words up
 * with what was heard. It runs as a foreground task with its progress in a notification,
 * so it carries on when the app is closed or the screen is off, and each step picks up
 * where it stopped if it is paused, the phone restarts or the connection drops.
 */
class PrepareWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val library = Library.get(context)
    private val quran = QuranStore(context)
    private val model = ModelStore(context)
    private val engine = EngineStore(context)
    private var listener: Listener? = null
    private var shown = ""

    // A paused or cancelled run can still be finishing a step; the next run waits for it
    // rather than writing to the same files.
    override suspend fun doWork(): Result = oneAtATime.withLock { work() }

    private suspend fun work(): Result {
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
                var attempt = 0
                while (true) {
                    try {
                        withContext(Dispatchers.IO) { prepare(track) }
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        // The connection dropped, or a server hiccuped. Try again shortly, in
                        // plain sight; after a few goes, say what went wrong and move on.
                        if (isStopped) return Result.retry()
                        if (++attempt >= ATTEMPTS) {
                            fail(track, applicationContext.getString(R.string.error_network, detail(e)))
                            break
                        }
                        _progress.value = Progress(track.id, Step.RETRY, 0f)
                        show(track, Step.RETRY, 0f)
                        delay(attempt * 5_000L)
                    } catch (e: ModelStore.Unavailable) {
                        // Every recording needs the model, so there's nothing to go on with. The
                        // recordings stay in the queue and the screen says what to do about it.
                        _modelProblem.value = ModelProblem(e.reason, e.detail)
                        return Result.success()
                    } catch (e: Stopped) {
                        // Paused or cancelled by the user, or Android is stopping the work.
                        if (isStopped) return Result.retry()
                        break
                    } catch (e: TooShort) {
                        fail(track, applicationContext.getString(R.string.error_too_short))
                        break
                    } catch (e: EngineStore.WontStart) {
                        fail(track, applicationContext.getString(R.string.error_engine, e.detail))
                        break
                    } catch (e: Exception) {
                        fail(track, applicationContext.getString(R.string.error_other, detail(e)))
                        break
                    }
                }
            }
            return Result.success()
        } finally {
            _progress.value = null
            listener?.close()
            if (awake.isHeld) awake.release()
        }
    }

    /** The system's own words for what went wrong: the only clue there is from a phone. */
    private fun detail(e: Exception): String = e.message?.takeIf { it.isNotBlank() }?.let { "${e.javaClass.simpleName}: $it" } ?: e.javaClass.simpleName

    /** The recording to work on next: the one the reader has open, if it is waiting; otherwise the first in line. */
    private fun next(): Track? {
        val waiting = library.playlists.value.flatMap { it.tracks }.filter(::waits)
        return waiting.firstOrNull { it.id == preferred } ?: waiting.firstOrNull()
    }

    private fun waits(track: Track) = track.wanted && (!track.ready || track.againFrom >= 0) && track.error == null && track.surah > 0

    /** Whether the recording the reader has open is waiting behind [id], in which case [id] steps aside. */
    private fun outranked(id: String): Boolean {
        val open = preferred ?: return false
        return open != id && library.track(open)?.let(::waits) == true
    }

    private fun fail(track: Track, message: String) = library.update(track.id) { it.copy(wanted = false, error = message, againFrom = -1, againTo = -1) }

    private class Stopped : Exception()

    private class TooShort : Exception()

    private suspend fun prepare(track: Track) {
        val id = track.id
        fun wanted() = !isStopped && library.track(id)?.wanted == true && !outranked(id)
        var speed = 0f // how many times faster than it plays the recording is being listened to
        fun report(step: Step, fraction: Float, forModel: Boolean = false) {
            _progress.value = Progress(if (forModel) null else id, step, fraction, if (step == Step.LISTEN) speed else 0f)
            show(track, step, fraction)
        }

        // The speech model: Accent Coach's copy, read where it is, or this app's own.
        model.obtain(download = Settings.downloadModel(applicationContext)) { checking, p ->
            report(if (checking) Step.MODEL_CHECK else Step.MODEL_DOWNLOAD, p, forModel = true)
        }
        // The engine that runs it isn't shipped inside the app: it is put on the phone once.
        if (!engine.isReady) engine.obtain { report(Step.ENGINE, it, forModel = true) }

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
        val recital = Recital(track.surah, verses.map { verse -> verse.words.map { it.arabic } })
        val timingsFile = library.timings(id, track.surah)

        // Only some verses are to be heard again: that stretch of the sound is listened to, its
        // words placed afresh, and put into the timings the recording already has.
        if (track.ready && track.againFrom >= 0) {
            val old = Timings.decode(timingsFile.readText())
            val first = track.againFrom.coerceIn(0, verses.size - 1)
            val last = track.againTo.coerceIn(first, verses.size - 1)
            val span = old.spanOf(first, last) ?: throw TooShort()
            report(Step.LISTEN, 0f)
            engine.start()
            val listener = listener ?: Listener(model.open(), vocab()).also { listener = it }
            val notes = File(library.heard(id).path + ".$first-$last")
            val heard = listener.listen(
                audio, notes, ::wanted, onPiece = { _, _, _ -> }, onProgress = { report(Step.LISTEN, it) },
                fromMs = span.first, untilMs = span.last,
            ) ?: throw Stopped()
            report(Step.ALIGN, 0f)
            val words = verses.subList(first, last + 1).map { verse -> verse.words.map { it.arabic } }
            val part = Recital(track.surah, words, withOpening = false).place(heard.rows, heard.frames) ?: throw TooShort()
            val timings = old.with(first, part, span.first)
            timingsFile.writeText(timings.encode())
            notes.delete()
            _live.value = Live(id, track.surah, timings)
            library.update(id) { it.copy(wanted = false, againFrom = -1, againTo = -1, error = null) }
            return
        }

        // With the sound and the words on the phone it can already be played: words light up
        // as far as the listening has got, and the listening carries on meanwhile.
        if (library.track(id)?.ready != true) {
            if (!timingsFile.exists()) timingsFile.writeText(Timings(emptyList(), 1f, heardMs = 0).encode())
            library.update(id) { it.copy(playable = true) }
        }

        // What the model heard is kept, so choosing another surah for a recording doesn't mean listening again.
        val notes = library.heard(id)
        val done = File(notes.path + ".done")
        val heard = if (done.exists()) {
            Hearing.read(notes)
        } else {
            report(Step.LISTEN, 0f)
            engine.start()
            val listener = listener ?: Listener(model.open(), vocab()).also { listener = it }
            // The words are placed as the listening goes, a few seconds behind it, for the reader to follow meanwhile.
            val live = LiveAlignment(recital)
            // The recording is heard in ten-second pieces, in order from the start, except that
            // the listening goes to where the reader is playing it, if that is further on. What
            // has been heard in order is placed as it comes; a stretch heard out of turn is
            // found in the surah by its sounds (see Locator) and shown until the rest catches up.
            val pieces = java.util.TreeMap<Int, Heard>(listener.resume(notes))
            var inOrder = 0 // pieces before this one have been handed on in order
            fun handOn() {
                while (true) {
                    val piece = pieces[inOrder] ?: break
                    live.add(piece.frames, piece.rows)
                    inOrder++
                }
            }
            handOn()
            var outOfTurn = -1 // the first piece of the stretch heard out of turn, or -1
            var published = 0
            val length = maxOf(1L, track.seconds * 1000)
            fun publish() {
                val soFar = live.advance()
                var shown = soFar
                if (outOfTurn >= inOrder) {
                    val stretch = generateSequence(outOfTurn) { it + 1 }.takeWhile { it in pieces }.map { pieces.getValue(it) }.toList()
                    if (stretch.isNotEmpty()) {
                        val heardThere = Hearing.joined(stretch)
                        val from = outOfTurn * Hearing.PIECE_MS
                        recital.locate(heardThere.rows, heardThere.frames, from.toFloat() / length)?.let { words ->
                            shown = Timings(soFar.verses, soFar.clarity, soFar.heardMs, soFar.repeats + words, from until from + stretch.size * Hearing.PIECE_MS)
                        }
                    }
                }
                _live.value = Live(id, track.surah, shown)
                // Kept on the phone too, every half minute of recitation, in case the app is closed meanwhile.
                if (published++ % 3 == 0) timingsFile.writeText(soFar.encode())
            }
            publish()
            var last = System.nanoTime()
            val whole = listener.listen(
                audio, notes, ::wanted,
                onPiece = { piece, frames, rows ->
                    pieces[piece] = Heard(frames, rows)
                    handOn()
                    // Ten seconds of recitation in however long that took, evened out over the last few pieces.
                    val now = System.nanoTime()
                    val took = (now - last) / 1e9f
                    last = now
                    if (took > 0.05f) speed = if (speed == 0f) Hearing.PIECE_MS / 1000f / took else speed * 0.7f + 0.3f * Hearing.PIECE_MS / 1000f / took
                    publish()
                },
                onProgress = { report(Step.LISTEN, it) },
                next = { done ->
                    val at = playing?.takeIf { it.trackId == id }?.ms?.div(Hearing.PIECE_MS)
                    if (at == null || at < inOrder) {
                        // Nothing is being played, or what is has been heard already: on in order.
                        outOfTurn = -1
                        null
                    } else {
                        // A new stretch begins where the reader is, unless they are still in the one being heard.
                        if (outOfTurn < 0 || at < outOfTurn || !(outOfTurn until at).all { it in done }) outOfTurn = at
                        // On from there, keeping up to a minute ahead of the reader; with that in hand, back to the pieces in order.
                        (at..at + AHEAD_PIECES).firstOrNull { it !in done }
                    }
                },
            ) ?: throw Stopped()
            whole.also { done.createNewFile() }
        }

        // Now that all of it has been heard, every word is placed again against the whole recording.
        report(Step.ALIGN, 0f)
        val timings = recital.place(heard.rows, heard.frames) ?: throw TooShort()
        timingsFile.writeText(timings.encode())
        _live.value = Live(id, track.surah, timings)
        if (timings.matches) {
            notes.delete()
            done.delete()
        }
        library.update(id) { it.copy(ready = true, playable = true, wanted = false, clarity = timings.clarity, error = null) }
    }

    private fun vocab(): List<String> {
        val json = JSONObject(applicationContext.assets.open("vocab.json").bufferedReader().use { it.readText() })
        val vocab = arrayOfNulls<String>(json.length())
        for (key in json.keys()) vocab[json.getInt(key)] = key
        return vocab.map { it.orEmpty() }
    }

    private fun show(track: Track, step: Step, fraction: Float) {
        val text = applicationContext.getString(stepLabel(step), (fraction * 100).toInt())
        val line = if (step == Step.MODEL_CHECK || step == Step.MODEL_DOWNLOAD || step == Step.ENGINE) text else "${track.title}\n$text"
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
        const val WORK_NAME = "prepare"
        private const val CHANNEL = "prepare"
        private const val NOTIFICATION_ID = 1
        private const val ATTEMPTS = 3

        private val oneAtATime = Mutex()

        private val _progress = MutableStateFlow<Progress?>(null)

        /** What the worker is doing, for the screens; null when it isn't running. */
        val progress: StateFlow<Progress?> = _progress.asStateFlow()

        private val _live = MutableStateFlow<Live?>(null)

        /** The word timings of the recording being listened to, as far as they have got; then its final ones. */
        val live: StateFlow<Live?> = _live.asStateFlow()

        /**
         * The recording the reader has open. If it is waiting to be prepared it goes first,
         * ahead of whatever is being worked on.
         */
        @Volatile var preferred: String? = null

        /** Where a recording is being played: the listening goes there when it hasn't got that far. */
        @Volatile var playing: Playing? = null

        /** How many ten-second pieces ahead of where it is being played a recording is listened to before the rest gets its turn. */
        private const val AHEAD_PIECES = 6

        private val _modelProblem = MutableStateFlow<ModelProblem?>(null)

        /** Set when the work stopped because Accent Coach is installed but couldn't lend its speech model. */
        val modelProblem: StateFlow<ModelProblem?> = _modelProblem.asStateFlow()

        fun stepLabel(step: Step): Int = when (step) {
            Step.MODEL_CHECK -> R.string.step_model_check
            Step.MODEL_DOWNLOAD -> R.string.step_model_download
            Step.ENGINE -> R.string.step_engine
            Step.DOWNLOAD -> R.string.step_download
            Step.VERSES -> R.string.step_verses
            Step.LISTEN -> R.string.step_listen
            Step.ALIGN -> R.string.step_align
            Step.RETRY -> R.string.step_retry
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
