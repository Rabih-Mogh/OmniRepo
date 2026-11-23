package app.recitation.prepare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
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
import app.recitation.core.Repeat
import app.recitation.core.Sketch
import app.recitation.core.Stretch
import app.recitation.speech.Arriving
import app.recitation.speech.AudioDecoder
import app.recitation.core.Timings
import app.recitation.data.Journal
import app.recitation.data.Library
import app.recitation.data.NoRoom
import app.recitation.data.QuranStore
import app.recitation.data.Settings
import app.recitation.data.Track
import app.recitation.data.isNoRoom
import app.recitation.data.writeWhole
import app.recitation.speech.Listener
import app.recitation.speech.EngineStore
import app.recitation.speech.ModelStore
import app.recitation.speech.Tuning
import app.recitation.yt.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sin

/** What is being done to a recording right now. */
enum class Step { MODEL_CHECK, MODEL_DOWNLOAD, ENGINE, TUNE, DOWNLOAD, VERSES, LISTEN, ALIGN, RETRY }

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
 *
 * Only the fetching needs a connection. Without one, the recordings that have everything
 * they need on the phone are still listened to; the rest wait for the connection to come back.
 * The same goes when YouTube won't answer for a while: what is on the phone is worked on,
 * and the downloads are tried again later.
 */
class PrepareWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val library = Library.get(context)
    private val quran = QuranStore(context)
    private val model = ModelStore(context)
    private val engine = EngineStore(context)
    private val journal = Journal.get(context)
    private var listener: Listener? = null
    private var shown = ""

    /** YouTube has refused this phone during this run: no more is asked of it until a later one. */
    private var refused = false

    // A paused or cancelled run can still be finishing a step; the next run waits for it
    // rather than writing to the same files.
    override suspend fun doWork(): Result = oneAtATime.withLock { work() }

    private suspend fun work(): Result {
        createChannel(applicationContext)
        // Android can refuse this when the work restarts while the app is closed; the work still runs.
        val foreground = runCatching { setForeground(foregroundInfo(applicationContext.getString(R.string.preparing))) }
        journal.note("work started" + (foreground.exceptionOrNull()?.let { ", not in the foreground: ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
        // Listening is an hour of work for a long surah; don't let the phone doze off in the middle.
        val awake = applicationContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "recitation:prepare")
        awake.acquire(6 * 60 * 60 * 1000L)
        try {
            _modelProblem.value = null
            while (true) {
                val track = next() ?: break
                val forgotten = library.timesForgotten(track.id)
                var attempt = 0
                journal.note("surah ${track.surah}: taken up")
                try {
                    while (true) {
                        try {
                            withContext(Dispatchers.IO) { prepare(track, forgotten) }
                            break
                        } catch (e: CancellationException) {
                            journal.note("work stopped" + if (Build.VERSION.SDK_INT >= 31) ", reason $stopReason" else "")
                            throw e
                        } catch (e: YouTube.Refused) {
                            // Asking again only makes YouTube refuse for longer. The recording
                            // stays in the queue with the others that need their sound fetched,
                            // and those that have theirs get their turn meanwhile.
                            journal.note("surah ${track.surah}: YouTube refused (${e.cause?.javaClass?.simpleName ?: "429"})")
                            refused = true
                            break
                        } catch (e: IOException) {
                            if (isStopped) return Result.retry()
                            // Nothing more can be written to the phone: trying again won't help.
                            if (isNoRoom(e) || applicationContext.filesDir.usableSpace < FULL_BELOW_BYTES) {
                                fail(track, applicationContext.getString(R.string.error_storage))
                                break
                            }
                            // No connection at all: nothing is wrong with the recording. It waits,
                            // and one that needs nothing fetched gets its turn meanwhile.
                            if (!online() && needsConnection(track)) {
                                journal.note("surah ${track.surah}: no connection, left waiting")
                                delay(2_000)
                                break
                            }
                            journal.note("surah ${track.surah}: ${detail(e)}; try ${attempt + 1}")
                            // The connection dropped, or a server hiccuped. Try again shortly, in
                            // plain sight; after a few goes, say what went wrong and move on.
                            if (++attempt >= ATTEMPTS) {
                                fail(track, applicationContext.getString(R.string.error_network, detail(e)))
                                break
                            }
                            _progress.value = Progress(track.id, Step.RETRY, 0f)
                            show(track, Step.RETRY, 0f)
                            delay(attempt * 5_000L)
                        } catch (e: NoRoom) {
                            fail(track, applicationContext.getString(R.string.error_storage))
                            break
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
                } finally {
                    // Its files were deleted while it was being worked on: what was written since goes too.
                    if (library.timesForgotten(track.id) != forgotten) library.clear(track.id)
                }
            }
            // What is left needs something fetched: it is taken up when there is a connection
            // again, and, if YouTube was refusing, not before it has had a while to relent.
            val left = library.playlists.value.sumOf { p -> p.tracks.count(::waits) }
            journal.note("work done" + if (left > 0) ", $left left waiting" + (if (refused) " on YouTube" else " for a connection") else "")
            if (left > 0) whenOnline(applicationContext, if (refused) REFUSED_WAIT_MINUTES else 0)
            return Result.success()
        } finally {
            _progress.value = null
            listener?.close()
            if (awake.isHeld) awake.release()
            // Where Android didn't let the work run in the foreground, the notification is the
            // app's own to take down.
            notifications.cancel(NOTIFICATION_ID)
        }
    }

    /** The system's own words for what went wrong: the only clue there is from a phone. */
    private fun detail(e: Exception): String = e.message?.takeIf { it.isNotBlank() }?.let { "${e.javaClass.simpleName}: $it" } ?: e.javaClass.simpleName

    /**
     * The recording to work on next: the one the reader has open, if it is waiting; otherwise
     * the first in line. With no connection, only those that need nothing fetched.
     */
    private fun next(): Track? {
        val online = online()
        val waiting = library.playlists.value.flatMap { it.tracks }.filter { waits(it) && canBeWorkedOn(it, online) }
        return waiting.firstOrNull { it.id == preferred } ?: waiting.firstOrNull()
    }

    /** Whether what [track] still needs can be had right now: there is a connection for what is to be fetched, and YouTube isn't refusing its sound. */
    private fun canBeWorkedOn(track: Track, online: Boolean = online()) =
        (online || !needsConnection(track)) && !(refused && library.audio(track.id) == null)

    private fun waits(track: Track) = track.wanted && (!track.ready || track.againFrom >= 0) && track.error == null && track.surah > 0

    /** Whether the phone has a connection to fetch over. */
    private fun online(): Boolean {
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** Whether something [track] needs is still to be fetched: its sound, its surah's words, the speech model or the engine. */
    private fun needsConnection(track: Track) =
        library.audio(track.id) == null || !quran.onPhone(track.surah) || !engine.isReady || model.needsDownload

    /** Whether the recording the reader has open is waiting behind [id] and can be worked on, in which case [id] steps aside. */
    private fun outranked(id: String): Boolean {
        val open = preferred ?: return false
        if (open == id) return false
        val track = library.track(open)?.takeIf(::waits) ?: return false
        // Asked between every block of sound, so the answer is kept for a moment.
        val now = SystemClock.elapsedRealtime()
        if (open != askedFor || now - askedAt > 2_000) {
            canGoFirst = canBeWorkedOn(track)
            askedFor = open
            askedAt = now
        }
        return canGoFirst
    }

    private var askedFor: String? = null
    private var askedAt = 0L
    private var canGoFirst = false

    /** Unless it has been given another surah since: then what went wrong was to do with the old one. */
    private fun fail(track: Track, message: String) {
        journal.note("surah ${track.surah}: failed: $message")
        library.update(track.id) { if (it.surah != track.surah) it else it.copy(wanted = false, error = message, againFrom = -1, againTo = -1) }
    }

    private class Stopped : Exception()

    private class TooShort : Exception()

    /** [forgotten]: how many times the recording's files had been deleted when it was taken up (see [Library.timesForgotten]). */
    private suspend fun prepare(track: Track, forgotten: Int): Unit = coroutineScope {
        val id = track.id
        // Not if it was taken out of the queue, forgotten, or given another surah: what is being done is for this one.
        fun wanted() = !isStopped && library.timesForgotten(id) == forgotten &&
            library.track(id)?.let { it.wanted && it.surah == track.surah } == true && !outranked(id)
        fun mark(change: (Track) -> Track) = library.update(id) { if (it.surah == track.surah) change(it) else it }
        // Nothing of what follows can be kept on a phone that is full.
        if (applicationContext.filesDir.usableSpace < SPARE_BYTES) throw NoRoom()
        var speed = 0f // how many times faster than it plays the recording is being listened to
        fun report(step: Step, fraction: Float, forModel: Boolean = false) {
            // In half-percent steps: a download says how far it is with every few bytes, and
            // every change here redraws the screens that show it.
            val shown = (fraction * 200).toInt() / 200f
            _progress.value = Progress(if (forModel) null else id, step, shown, if (step == Step.LISTEN) (speed * 10).toInt() / 10f else 0f)
            show(track, step, shown)
        }
        // The engine with the model in it, run the way found fastest on this phone: found
        // by trying, the first time, with a few seconds of sound each way.
        fun listener(): Listener = listener ?: run {
            val vocab = vocab()
            val tuning = Tuning.kept(applicationContext) ?: run {
                val sound = FloatArray(TRIAL_SAMPLES) { sin(it * 0.05f) * 0.1f }
                var tried = 0
                Tuning.find(
                    Runtime.getRuntime().availableProcessors(),
                    time = { way ->
                        if (!wanted()) throw Stopped()
                        report(Step.TUNE, tried++ / 4f, forModel = true)
                        runCatching {
                            Listener(model.open(), vocab, way).use {
                                it.run(sound) // the first run sets the engine up; the second is the one timed
                                val from = System.nanoTime()
                                it.run(sound)
                                (System.nanoTime() - from) / 1e9f
                            }
                        }.getOrNull()
                    },
                    note = journal::note,
                ).also { Tuning.keep(applicationContext, it) }
            }
            Listener(model.open(), vocab, tuning).also { listener = it }
        }

        val timingsFile = library.timings(id, track.surah)
        // Roughly where each verse begins (see lookOver below) is added to the timings that are
        // shown and kept, whenever it turns up.
        val starts = object {
            private var sketch: IntArray? = null
            private var shown: Timings? = null
            private var kept: Timings? = null
            private var settled = false

            /** Shows [timings] to the reader, and keeps [keep] on the phone. */
            @Synchronized fun show(timings: Timings, keep: Timings? = null) {
                shown = timings
                _live.value = Live(id, track.surah, timings.sketched(sketch))
                if (keep != null) {
                    kept = keep
                    timingsFile.writeWhole(keep.sketched(sketch).encode())
                }
            }

            @Synchronized fun found(sketch: IntArray) {
                if (settled) return
                this.sketch = sketch
                shown?.let { _live.value = Live(id, track.surah, it.sketched(sketch)) }
                kept?.let { timingsFile.writeWhole(it.sketched(sketch).encode()) }
            }

            /** Every word has its place now: nothing more is added. */
            @Synchronized fun settle() {
                settled = true
            }
        }

        // A quick look over the sound for where the reciter pauses says roughly where each verse
        // begins, before anything has been listened to and without the speech model: enough to
        // go to a verse, and to show which one is playing. It goes on by the side, waiting for
        // nothing and holding nothing up: over the sound as it arrives, while that is still
        // being downloaded. The pauses are kept; which surah the recording is doesn't change them.
        val arrived = CompletableDeferred<File>()
        val text = CompletableDeferred<Recital>()
        fun lookOver(arriving: Arriving?): Job = launch(Dispatchers.Default) {
            fun still() = isActive && wanted()
            try {
                val kept = library.pauses(id)
                val (pauses, total) = readPauses(kept) ?: run {
                    var levels = arriving?.use { source -> runCatching { AudioDecoder(source).use { Sketch.levels(it, ::still) } }.getOrNull() }
                    val whole = arrived.await()
                    // What was read as it arrived counts only if it turned out to be all of it.
                    if (levels != null && !isAllOf(whole, levels.size * 20, track)) levels = null
                    if (levels == null) levels = AudioDecoder(whole).use { Sketch.levels(it, ::still) } ?: return@launch
                    (Sketch.pauses(levels) to levels.size * 20).also { writePauses(kept, it.first, it.second) }
                }
                starts.found(text.await().sketch(pauses, total))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No rough verse starts for this recording, then. Nothing else depends on them.
            }
        }

        var looking: Job? = null
        val audio = library.audio(id)?.also { if (!track.ready) looking = lookOver(null) } ?: run {
            report(Step.DOWNLOAD, 0f)
            val source = YouTube.audio(id)
            val part = library.audioPart(id, source.extension)
            // The start of a download in another form, from an earlier try, is of no use now.
            library.dropPartial(id, keep = part)
            // Found out now rather than most of the way through it.
            if (applicationContext.filesDir.usableSpace < (track.seconds * BYTES_A_SECOND - part.length()).coerceAtLeast(0) + SPARE_BYTES) throw NoRoom()
            val whole = File(part.path.removeSuffix(".part"))
            val arriving = Arriving(part, whole) { wanted() && looking?.isActive != false }
            if (!track.ready) looking = lookOver(arriving)
            try {
                YouTube.download(source, part, library.sizeNote(part), ::wanted) { report(Step.DOWNLOAD, it) }
            } catch (_: InterruptedException) {
                throw Stopped()
            } finally {
                arriving.over = true
            }
            whole.also {
                if (!part.renameTo(it)) throw IOException("couldn't keep the download")
                library.sizeNote(part).delete()
            }
        }
        arrived.complete(audio)

        report(Step.VERSES, 0f)
        val verses = quran.surah(track.surah)
        val recital = Recital(track.surah, verses.map { verse -> verse.words.map { it.arabic } })
        text.complete(recital)

        // With the sound and the words on the phone it can already be played: words light up
        // as far as the listening has got, and the listening carries on meanwhile.
        if (library.track(id)?.ready != true) {
            val soFar = timingsIn(timingsFile) ?: Timings(emptyList(), 1f, heardMs = 0)
            if (!soFar.complete) starts.show(soFar, keep = soFar)
            mark { it.copy(playable = true) }
        }

        // The speech model: Accent Coach's copy, read where it is, or this app's own.
        model.obtain(download = Settings.downloadModel(applicationContext)) { checking, p ->
            report(if (checking) Step.MODEL_CHECK else Step.MODEL_DOWNLOAD, p, forModel = true)
        }
        // The engine that runs it isn't shipped inside the app: it is put on the phone once.
        if (!engine.isReady) engine.obtain { report(Step.ENGINE, it, forModel = true) }

        // Only some verses are to be heard again: that stretch of the sound is listened to, its
        // words placed afresh, and put into the timings the recording already has.
        val old = if (track.ready && track.againFrom >= 0) timingsIn(timingsFile) else null
        if (old != null) {
            val first = track.againFrom.coerceIn(0, verses.size - 1)
            val last = track.againTo.coerceIn(first, verses.size - 1)
            val span = old.spanOf(first, last) ?: throw TooShort()
            engine.start()
            val listener = listener()
            report(Step.LISTEN, 0f)
            val notes = File(library.heard(id).path + ".$first-$last")
            val heard = listener.listen(
                audio, notes, ::wanted, onPiece = { _, _, _ -> }, onProgress = { report(Step.LISTEN, it) },
                fromMs = span.first, untilMs = span.last,
            ) ?: throw Stopped()
            report(Step.ALIGN, 0f)
            val words = verses.subList(first, last + 1).map { verse -> verse.words.map { it.arabic } }
            val part = Recital(track.surah, words, withOpening = false).place(heard.rows, heard.frames) ?: throw TooShort()
            val timings = old.with(first, part, span.first)
            timingsFile.writeWhole(timings.encode())
            notes.delete()
            _live.value = Live(id, track.surah, timings)
            mark { it.copy(wanted = false, againFrom = -1, againTo = -1, error = null) }
            return@coroutineScope
        }
        // Timings that can't be read have nothing to put some verses back into: all of it is placed again.
        if (track.againFrom >= 0) mark { it.copy(ready = false, againFrom = -1, againTo = -1) }

        // What the model heard is kept, so choosing another surah for a recording doesn't mean listening again.
        val notes = library.heard(id)
        val done = File(notes.path + ".done")
        val heard = if (done.exists() && notes.exists()) {
            Hearing.read(notes)
        } else {
            done.delete()
            engine.start()
            val listener = listener()
            report(Step.LISTEN, 0f)
            // The words are placed as the listening goes, a few seconds behind it, for the reader to follow meanwhile.
            val live = LiveAlignment(recital)
            // The recording is heard in ten-second pieces, in order from the start, except that
            // the listening goes to where the reader is playing it, if that is further on. What
            // has been heard in order is placed as it comes; every stretch heard out of turn is
            // found in the surah by its sounds (see Stretch) and shown until the rest catches up.
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
            var published = 0
            val stretches = HashMap<Int, Stretch>() // by the piece a stretch heard out of turn begins at
            fun publish() {
                val soFar = live.advance()
                // Every run of pieces past those heard in order: wherever the reader has been.
                val words = ArrayList<Repeat>()
                val ahead = ArrayList<IntRange>()
                var first = pieces.ceilingKey(inOrder)
                while (first != null) {
                    val found = stretches.getOrPut(first) { Stretch(recital, track.seconds * 1000) }.words(pieces, first)
                    words += found.words
                    for (run in found.pieces) ahead += run.first * Hearing.PIECE_MS until (run.last + 1) * Hearing.PIECE_MS
                    var end: Int = first
                    while (end + 1 in pieces) end++
                    first = pieces.higherKey(end)
                }
                stretches.keys.retainAll { it >= inOrder && it in pieces && it - 1 !in pieces }
                val shown = if (ahead.isEmpty()) soFar else Timings(soFar.verses, soFar.clarity, soFar.heardMs, soFar.repeats + words, ahead)
                // Kept on the phone too, every half minute of recitation, in case the app is closed meanwhile.
                starts.show(shown, keep = shown.takeIf { published++ % 3 == 0 })
            }
            publish()
            var last = System.nanoTime()
            // For the journal: how fast each ten minutes of recitation was listened to, which is
            // where a phone slowing itself down as it warms up shows.
            var lapPieces = 0
            var lapFrom = last
            fun lap() {
                if (lapPieces > 0) journal.note("surah ${track.surah}: $lapPieces pieces at ${String.format(java.util.Locale.US, "%.2f", lapPieces * Hearing.PIECE_MS / 1000f / ((System.nanoTime() - lapFrom) / 1e9f))}x")
                lapPieces = 0
                lapFrom = System.nanoTime()
            }
            val whole = try {
                listener.listen(
                    audio, notes, ::wanted,
                    onPiece = { piece, frames, rows ->
                        pieces[piece] = Heard(frames, rows)
                        handOn()
                        // Ten seconds of recitation in however long that took, evened out over the last few pieces.
                        val now = System.nanoTime()
                        val took = (now - last) / 1e9f
                        last = now
                        if (took > 0.05f) speed = if (speed == 0f) Hearing.PIECE_MS / 1000f / took else speed * 0.7f + 0.3f * Hearing.PIECE_MS / 1000f / took
                        if (++lapPieces >= LAP_PIECES) lap()
                        publish()
                    },
                    onProgress = { report(Step.LISTEN, it) },
                    next = { done ->
                        val at = playing?.takeIf { it.trackId == id }?.ms?.div(Hearing.PIECE_MS)
                        // Nothing is being played, or what is has been heard in order already: on in
                        // order. Otherwise on from where the reader is, for as long as they stay:
                        // only with five minutes in hand is there time for the pieces in order.
                        if (at == null || at < inOrder) null else (at..at + AHEAD_PIECES).firstOrNull { it !in done }
                    },
                )
            } finally {
                lap()
            } ?: throw Stopped()
            whole.also { done.createNewFile() }
        }

        // Now that all of it has been heard, every word is placed again against the whole recording.
        starts.settle()
        looking?.cancel()
        report(Step.ALIGN, 0f)
        val timings = recital.place(heard.rows, heard.frames) ?: throw TooShort()
        timingsFile.writeWhole(timings.encode())
        // Given another surah while its words were being placed: what was heard is kept, for that one's words.
        if (library.track(id)?.surah != track.surah) throw Stopped()
        _live.value = Live(id, track.surah, timings)
        if (timings.matches) {
            notes.delete()
            done.delete()
        }
        mark { it.copy(ready = true, playable = true, wanted = false, clarity = timings.clarity, error = null) }
    }

    /** The timings kept in [file]; null if there are none, or none that can be read. */
    private fun timingsIn(file: File): Timings? = if (!file.exists()) null else runCatching { Timings.decode(file.readText()) }.getOrNull()

    /** Whether [ms] of sound, read from a recording as it arrived, is all of [whole]. */
    private fun isAllOf(whole: File, ms: Int, track: Track): Boolean {
        val length = runCatching { AudioDecoder(whole).use { it.total } }.getOrDefault(0L) * 1000 / Hearing.SAMPLE_RATE
        val expected = if (length > 0) length else track.seconds * 1000
        return expected > 0 && abs(ms - expected) <= maxOf(2_000L, expected / 500)
    }

    /** The pauses kept in [file], and how long the recording is in ms; null if there are none kept. */
    private fun readPauses(file: File): Pair<List<IntRange>, Int>? {
        if (!file.exists()) return null
        val lines = file.readLines()
        val total = lines.firstOrNull()?.toIntOrNull() ?: return null
        return lines.drop(1).mapNotNull { line ->
            val from = line.substringBefore('-').toIntOrNull() ?: return@mapNotNull null
            val until = line.substringAfter('-').toIntOrNull() ?: return@mapNotNull null
            from until until
        } to total
    }

    private fun writePauses(file: File, pauses: List<IntRange>, total: Int) {
        file.writeWhole(buildString {
            append(total).append('\n')
            for (pause in pauses) append(pause.first).append('-').append(pause.last + 1).append('\n')
        })
    }

    private fun vocab(): List<String> {
        val json = JSONObject(applicationContext.assets.open("vocab.json").bufferedReader().use { it.readText() })
        val vocab = arrayOfNulls<String>(json.length())
        for (key in json.keys()) vocab[json.getInt(key)] = key
        return vocab.map { it.orEmpty() }
    }

    private fun show(track: Track, step: Step, fraction: Float) {
        val text = applicationContext.getString(stepLabel(step), (fraction * 100).toInt())
        val line = if (step == Step.MODEL_CHECK || step == Step.MODEL_DOWNLOAD || step == Step.ENGINE || step == Step.TUNE) text else "${track.title}\n$text"
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
        const val WORK_NAME = "prepare-now"

        /** The run that waits for a connection. It keeps the name the work had when all of it waited for one. */
        const val WAITING_NAME = "prepare"
        private const val CHANNEL = "prepare"
        private const val NOTIFICATION_ID = 1
        private const val ATTEMPTS = 3

        /** Marks the run that waits for YouTube to stop refusing. */
        const val REFUSED_TAG = "refused"
        private const val REFUSED_WAIT_MINUTES = 15L

        /** Room a recording's sound is reckoned to take for each second of it: a little over what YouTube's best sound does. */
        private const val BYTES_A_SECOND = 20_000L

        /** Room left over for what is kept beside the sound: what was heard, and the timings. */
        private const val SPARE_BYTES = 16L shl 20

        /** With less room than this, a failure to write is the phone being full. */
        private const val FULL_BELOW_BYTES = 1L shl 20

        /** How much sound the engine is tried on, each way of running it: four seconds. */
        private const val TRIAL_SAMPLES = 4 * Hearing.SAMPLE_RATE

        /** How many pieces go into each line of the journal about the listening's speed: ten minutes of recitation. */
        private const val LAP_PIECES = 60

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

        /** How many ten-second pieces ahead of where it is being played a recording is listened to before the rest gets its turn: the reader comes first. */
        private const val AHEAD_PIECES = 30

        private val _modelProblem = MutableStateFlow<ModelProblem?>(null)

        /** Set when the work stopped because Accent Coach is installed but couldn't lend its speech model. */
        val modelProblem: StateFlow<ModelProblem?> = _modelProblem.asStateFlow()

        fun stepLabel(step: Step): Int = when (step) {
            Step.MODEL_CHECK -> R.string.step_model_check
            Step.MODEL_DOWNLOAD -> R.string.step_model_download
            Step.ENGINE -> R.string.step_engine
            Step.TUNE -> R.string.step_tune
            Step.DOWNLOAD -> R.string.step_download
            Step.VERSES -> R.string.step_verses
            Step.LISTEN -> R.string.step_listen
            Step.ALIGN -> R.string.step_align
            Step.RETRY -> R.string.step_retry
        }

        /**
         * Starts preparing what's wanted, or leaves a run that's already under way to pick the
         * new work up. It runs with or without a connection, and does what it can.
         */
        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<PrepareWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /**
         * Has the work start again once the phone has a connection, for what couldn't be
         * fetched without one; and not before [waitMinutes] have passed, when it was YouTube
         * that wouldn't be fetched from.
         */
        private fun whenOnline(context: Context, waitMinutes: Long = 0) {
            val request = OneTimeWorkRequestBuilder<PrepareWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .apply { if (waitMinutes > 0) setInitialDelay(waitMinutes, TimeUnit.MINUTES).addTag(REFUSED_TAG) }
                .build()
            // In place of a run already waiting, rather than left to it: that run may be the very one asking, about to end.
            WorkManager.getInstance(context).enqueueUniqueWork(WAITING_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(WAITING_NAME)
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
