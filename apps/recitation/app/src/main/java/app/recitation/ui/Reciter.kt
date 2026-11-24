package app.recitation.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import app.recitation.R
import app.recitation.core.Corrections
import app.recitation.core.Expected
import app.recitation.core.Follower
import app.recitation.core.Mel
import app.recitation.core.Silences
import app.recitation.core.TajweedReference
import app.recitation.core.WordRecited
import app.recitation.data.Mistakes
import app.recitation.speech.EngineStore
import app.recitation.speech.Mic
import app.recitation.speech.TajweedEar
import app.recitation.speech.TajweedStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Listens to the reader reciting to the phone and corrects them, all on the phone. Either
 * one verse it is told ([toggle]): the microphone is opened, the word being recited is
 * found as it goes, and when the reader is done the tajweed model listens and the
 * corrections are worked out. Or a surah, from wherever the reader begins ([follow]): the
 * verse and the word are found by what is heard, and each verse is gone over as the reader
 * goes on to the next (see [Follower]). The verses recited before keep what was heard of
 * them. It lives as long as the screen that made it, and lets go of the microphone with it.
 */
@Stable
class Reciter(private val context: Context, private val scope: CoroutineScope, private val says: Says) {
    /** What it says when it can't: the microphone not allowed, or not to be had; nothing heard; a verse it has no reference for; anything else (takes the reason). */
    class Says(val refused: String, val busy: String, val nothing: String, val unknown: String, val failed: String)

    /** The verse, counted from 1, that is being recited or was recited last; 0 before any. */
    var verse by mutableIntStateOf(0)
        private set

    /** Where reciting that verse stands. */
    var state by mutableStateOf<Reciting>(Reciting.Idle)
        private set

    /** The word of that verse being recited, counted from 0, while the microphone is open; -1 before the first sound. */
    var reached by mutableIntStateOf(-1)
        private set

    /** Every verse recited so far as it was heard the last time, by its number. */
    val heard = mutableStateMapOf<Int, List<WordRecited>>()

    val listening: Boolean get() = state == Reciting.Listening

    /** Asks the reader to allow the microphone; [allowed] is told the answer. Set by the screen. */
    var ask: () -> Unit = {}

    private var surah = 0
    private var done = false
    private var job: Job? = null
    private var waiting: Pair<Int, Int>? = null

    // The reciter's pace, kept from verse to verse: a short verse may have no natural madd to count by.
    private val tempo = ArrayList<Float>()

    /**
     * The microphone button of [verse] of [surah]: opens the microphone on it, or, with the
     * microphone open, says the reader is done. Asks for the microphone the first time.
     */
    fun toggle(surah: Int, verse: Int) {
        when {
            listening -> done = true
            state == Reciting.Checking -> {}
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> recite(surah, verse)
            else -> {
                waiting = surah to verse
                ask()
            }
        }
    }

    /** The reader's answer to being asked for the microphone. */
    fun allowed(granted: Boolean) {
        val (surah, verse) = waiting ?: return
        waiting = null
        if (granted) {
            if (verse == 0) followFrom(surah) else recite(surah, verse)
        } else {
            this.verse = verse
            state = Reciting.Failed(says.refused)
        }
    }

    /**
     * The page's microphone: opens it on [surah], to follow the reader from whichever
     * verse they begin at; with it open, says the reader is done; and after something
     * couldn't be done, puts that away. Asks for the microphone the first time.
     */
    fun follow(surah: Int) {
        when {
            listening -> done = true
            state == Reciting.Checking -> {}
            state is Reciting.Failed -> rest()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> followFrom(surah)
            else -> {
                waiting = surah to 0
                ask()
            }
        }
    }

    /** Forgets what was heard of [verse]. */
    fun forget(verse: Int) {
        heard.remove(verse)
        if (verse == this.verse && !listening && state != Reciting.Checking) state = Reciting.Idle
    }

    /** Lets go of whatever it is at: for the screen moving to another verse. */
    fun rest() {
        job?.cancel()
        state = Reciting.Idle
        reached = -1
    }

    private fun recite(surah: Int, verse: Int) {
        if (surah != this.surah) {
            tempo.clear()
            heard.clear()
            this.surah = surah
        }
        job?.cancel()
        this.verse = verse
        heard.remove(verse)
        state = Reciting.Listening
        reached = -1
        done = false
        job = scope.launch {
            val forms = withContext(Dispatchers.Default) {
                runCatching { context.assets.open(REFERENCE).use { TajweedReference.surah(it, surah) }.getOrNull(verse - 1) }.getOrNull()
            }
            val blocks = ArrayList<FloatArray>()
            var samples = 0
            fun soFar(): FloatArray {
                val sound = FloatArray(samples)
                var at = 0
                for (block in blocks) { System.arraycopy(block, 0, sound, at, block.size); at += block.size }
                return sound
            }
            val whole = forms?.let { Expected.breath(it, 0, it.size) }
            try {
                coroutineScope {
                    // About once a second the model listens to what has been recited so far, only to
                    // find which word has been reached, and that word is lit. It judges nothing yet.
                    val following = launch {
                        while (whole != null) {
                            delay(FOLLOW_EVERY_MS)
                            if (samples < Mel.SAMPLE_RATE / 2) continue
                            val sound = soFar()
                            val at = withContext(Dispatchers.Default) { runCatching { whole.reached(Ears.get(context).hear(sound).symbols) }.getOrNull() }
                            if (at != null) reached = at
                        }
                    }
                    // Until the reader says they are done, or the model has as much as it takes at once.
                    Mic.sound().takeWhile { !done }.collect { block ->
                        blocks += block
                        samples += block.size
                        if (samples >= LONGEST) done = true
                    }
                    following.cancel()
                }
            } catch (e: Mic.Unavailable) {
                state = Reciting.Failed(says.busy)
                return@launch
            }
            val sound = soFar()
            state = Reciting.Checking
            state = try {
                withContext(Dispatchers.Default) {
                    if (forms == null) Reciting.Failed(says.unknown) else {
                        val script = Ears.get(context).hear(sound)
                        if (script.symbols.length < 2) Reciting.Failed(says.nothing)
                        // Where the reciter fell silent, a word may have been stopped on: not a mistake.
                        else Reciting.Heard(Corrections.of(Expected.recited(forms, script.symbols, silences = Silences.among(script, sound)), script, tempo = tempo))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Reciting.Failed(says.failed.format(e.message ?: e.javaClass.simpleName))
            }
            (state as? Reciting.Heard)?.let {
                heard[verse] = it.words
                // Kept for the exercises: what was corrected, and what has been put right.
                Mistakes.get(context).note(surah, verse, it.words)
            }
        }
    }

    private fun followFrom(surah: Int) {
        if (surah != this.surah) {
            tempo.clear()
            heard.clear()
            this.surah = surah
        }
        job?.cancel()
        verse = 0
        state = Reciting.Listening
        reached = -1
        done = false
        job = scope.launch {
            val follower = withContext(Dispatchers.Default) {
                runCatching {
                    val verses = context.assets.open(REFERENCE).use { TajweedReference.surah(it, surah) }
                    // The basmala a reader begins with is a verse of Al-Fatihah alone, and At-Tawbah has none.
                    val opening = if (surah == 1 || surah == 9) null else context.assets.open(REFERENCE).use { TajweedReference.surah(it, 1) }.firstOrNull()
                    Follower(verses, opening, tempo).takeIf { verses.isNotEmpty() }
                }.getOrNull()
            }
            if (follower == null) {
                state = Reciting.Failed(says.unknown)
                return@launch
            }
            // The sound not yet heard for good, and how much has been: a stretch is handed on
            // where the reader falls silent, and what is left is heard again at every look.
            val blocks = ArrayList<FloatArray>()
            var samples = 0
            var taken = 0L
            var failed: String? = null
            suspend fun look(last: Boolean) {
                val sound = FloatArray(samples)
                var at = 0
                for (block in blocks) { System.arraycopy(block, 0, sound, at, block.size); at += block.size }
                val (cut, finished) = withContext(Dispatchers.Default) {
                    val ear = Ears.get(context)
                    val cut = (if (last) sound.size else Breaths.end(sound)).takeIf { it >= LEAST } ?: 0
                    if (cut > 0) {
                        val stretch = sound.copyOf(cut)
                        follower.add(ear.hear(stretch), taken.toFloat() / Mel.SAMPLE_RATE, Silences.of(stretch))
                    }
                    val pending = if (!last && sound.size - cut >= LEAST) ear.hear(sound.copyOfRange(cut, sound.size)) else null
                    cut to follower.look(pending, last)
                }
                for (verse in finished) {
                    heard[verse.verse + 1] = verse.words
                    Mistakes.get(context).note(surah, verse.verse + 1, verse.words)
                }
                // Dropped from the front of what is kept; more has come in at the back meanwhile.
                var drop = cut
                while (drop > 0 && blocks.isNotEmpty()) {
                    val first = blocks[0]
                    if (first.size <= drop) { drop -= first.size; blocks.removeAt(0) } else { blocks[0] = first.copyOfRange(drop, first.size); drop = 0 }
                }
                samples -= cut
                taken += cut
                verse = follower.verse + 1
                reached = follower.word
            }
            try {
                coroutineScope {
                    // About once a second: where the reader is, and any verse they have finished.
                    val looking = launch {
                        var waited = 0L
                        while (!done) {
                            delay(LOOK_STEP_MS)
                            waited += LOOK_STEP_MS
                            if (waited < FOLLOW_EVERY_MS) continue
                            waited = 0
                            try {
                                look(last = false)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                failed = says.failed.format(e.message ?: e.javaClass.simpleName)
                                done = true
                            }
                        }
                    }
                    // Until the reader says they are done.
                    Mic.sound().takeWhile { !done }.collect { block ->
                        blocks += block
                        samples += block.size
                    }
                    // A look under way is let finish: what it took in is taken in once.
                    looking.join()
                }
            } catch (e: Mic.Unavailable) {
                state = Reciting.Failed(says.busy)
                return@launch
            }
            failed?.let {
                state = Reciting.Failed(it)
                return@launch
            }
            state = Reciting.Checking
            state = try {
                look(last = true)
                if (taken == 0L || (heard.isEmpty() && follower.verse < 0)) Reciting.Failed(says.nothing) else Reciting.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Reciting.Failed(says.failed.format(e.message ?: e.javaClass.simpleName))
            }
            reached = -1
        }
    }
}

/**
 * Where a stretch of sound still being recited can be handed on to be heard for good:
 * in the middle of the last silence the reader has made in it, so no word is cut in two.
 */
internal object Breaths {
    /** The shortest stretch handed on, and the longest one kept waiting for a silence, in seconds: the model takes under half a minute at once. */
    private const val SHORTEST = 0.8f
    private const val LONGEST = 24

    /** How long a silence the reader is still in has to have lasted to end a stretch, in seconds. */
    private const val STILL_SILENT = 0.7f

    /** How many samples from the start of [sound] the stretch ends; 0 if it isn't to end yet. */
    fun end(sound: FloatArray): Int {
        val seconds = sound.size.toFloat() / Mel.SAMPLE_RATE
        val silences = Silences.of(sound)
        for (s in silences.size / 2 - 1 downTo 0) {
            val from = silences[2 * s]
            val until = silences[2 * s + 1]
            if (from < SHORTEST) break
            // Over, the reader reciting again; or still going on, and long enough to be a breath.
            if (until < seconds - 0.1f || until - from >= STILL_SILENT) return ((from + until) / 2 * Mel.SAMPLE_RATE).toInt()
        }
        return if (seconds > LONGEST) LONGEST * Mel.SAMPLE_RATE else 0
    }
}

/** A [Reciter] for the screen on show. */
@Composable
fun rememberReciter(): Reciter {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val says = Reciter.Says(
        refused = stringResource(R.string.voice_no_mic), busy = stringResource(R.string.voice_mic_busy),
        nothing = stringResource(R.string.recite_nothing_heard), unknown = stringResource(R.string.recite_no_reference),
        failed = stringResource(R.string.recite_failed, "%s"),
    )
    val reciter = remember { Reciter(context, scope, says) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reciter.allowed(it) }
    reciter.ask = { ask.launch(Manifest.permission.RECORD_AUDIO) }
    return reciter
}

/**
 * Reciting to the phone on the page of a surah: what the page shows of it. [verse]
 * (counted from 1; 0 until it is known) is the verse the reader is heard to be at, [state]
 * where the reciting stands, [reached] the word of that verse being recited while the
 * microphone is open, and [heard] every verse gone over so far as it was heard, by its
 * number. [onToggle] opens the microphone, or with it open says the reader is done;
 * [onForget] takes a verse's corrections off the page.
 */
class PageRecital(
    val verse: Int,
    val state: Reciting,
    val reached: Int,
    val heard: Map<Int, List<WordRecited>>,
    val onToggle: () -> Unit,
    val onForget: (verse: Int) -> Unit,
) {
    /** Whether the reader is reciting to the phone, or that is being got ready, gone over, or couldn't be done: the bar for it shows. */
    val active: Boolean get() = state != Reciting.Idle && state !is Reciting.Heard
}

/** Reciting to the phone on the page of the surah on show; null where that isn't offered. */
val LocalPageRecital = compositionLocalOf<PageRecital?> { null }

/**
 * Reciting to the phone on the page of [surah]. The model it checks with is fetched by the
 * background work: [fetching] is how far that has got (null when it isn't being fetched
 * now), and [onWant] asks for it: by itself when the page opens, or because reciting was
 * asked for before it was here, in which case the microphone opens as soon as it is.
 */
@Composable
fun rememberPageRecital(surah: Int, fetching: Float?, onWant: (pressed: Boolean) -> Unit): PageRecital {
    val context = LocalContext.current.applicationContext
    val reciter = rememberReciter()
    val store = remember { TajweedStore(context) }
    val engine = remember { EngineStore(context) }
    // Looked at again whenever the fetching moves: it ends with the model on the phone.
    val ready = remember(fetching) { store.isReady && engine.isReady }
    // Reciting was asked for before the model was here: the fetching shows, and the microphone opens when it is.
    var wanted by remember(surah) { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!ready) onWant(false) }
    LaunchedEffect(ready, wanted) {
        if (ready && wanted) {
            wanted = false
            reciter.follow(surah)
        }
    }
    return PageRecital(
        verse = if (ready) reciter.verse else 0,
        state = when {
            ready -> reciter.state
            wanted -> Reciting.Fetching(R.string.recite_getting_model, fetching ?: Reciting.Fetching.UNKNOWN)
            else -> Reciting.Idle
        },
        reached = reciter.reached,
        heard = reciter.heard,
        onToggle = {
            when {
                ready -> reciter.follow(surah)
                // Asked for again while it is being fetched: not wanted after all.
                wanted -> wanted = false
                else -> {
                    wanted = true
                    onWant(true)
                }
            }
        },
        onForget = reciter::forget,
    )
}

/** The tajweed model, opened once and kept for as long as the app runs: opening it takes a moment. */
internal object Ears {
    private var ear: TajweedEar? = null

    @Synchronized
    fun get(context: Context): TajweedEar = ear ?: run {
        EngineStore(context).start()
        TajweedEar(TajweedStore(context).file).also { ear = it }
    }
}

/** The longest a recitation is listened to in one go, in samples: what the model takes at once. */
private const val LONGEST = TajweedEar.LONGEST_SECONDS * Mel.SAMPLE_RATE

/** How often, while the reader recites, the word they have reached is looked for. */
private const val FOLLOW_EVERY_MS = 900L

/** How often, between two looks, it is asked whether the reader is done. */
private const val LOOK_STEP_MS = 100L

/** The least sound worth hearing, in samples: half a second. */
private const val LEAST = Mel.SAMPLE_RATE / 2

/** The reference file in the app's package: what a correct recitation of every word sounds like. Gzipped, but not named so: Android's build would unpack it. */
internal const val REFERENCE = "tajweed_reference.bin"
