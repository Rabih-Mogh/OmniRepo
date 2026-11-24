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
import app.recitation.core.Mel
import app.recitation.core.Silences
import app.recitation.core.TajweedReference
import app.recitation.core.WordRecited
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
 * Listens to a verse being recited to the phone and corrects it: opens the microphone,
 * finds the word being recited as it goes, and when the reader is done has the tajweed
 * model listen and the corrections worked out, all on the phone. One verse at a time; the
 * verses recited before keep what was heard of them. It lives as long as the screen that
 * made it, and lets go of the microphone with it.
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
        if (granted) recite(surah, verse) else {
            this.verse = verse
            state = Reciting.Failed(says.refused)
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
            (state as? Reciting.Heard)?.let { heard[verse] = it.words }
        }
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
 * Reciting to the phone on the page of a surah: what the page shows of it, and what the
 * microphone beside each verse does. [verse] (counted from 1; 0 for none) is the verse
 * being recited or recited last, [state] where that stands, [reached] the word of it being
 * recited while the microphone is open, and [heard] every verse recited so far as it was
 * heard, by its number. [onForget] takes a verse's corrections off the page.
 */
class PageRecital(
    val verse: Int,
    val state: Reciting,
    val reached: Int,
    val heard: Map<Int, List<WordRecited>>,
    val onMic: (verse: Int) -> Unit,
    val onForget: (verse: Int) -> Unit,
)

/** Reciting to the phone on the page of the surah on show; null where that isn't offered. */
val LocalPageRecital = compositionLocalOf<PageRecital?> { null }

/**
 * Reciting to the phone on the page of [surah]. The model it checks with is fetched by the
 * background work: [fetching] is how far that has got (null when it isn't being fetched
 * now), and [onWant] asks for it: by itself when the page opens, or because a microphone
 * was tapped before it was here.
 */
@Composable
fun rememberPageRecital(surah: Int, fetching: Float?, onWant: (pressed: Boolean) -> Unit): PageRecital {
    val context = LocalContext.current.applicationContext
    val reciter = rememberReciter()
    val store = remember { TajweedStore(context) }
    val engine = remember { EngineStore(context) }
    // Looked at again whenever the fetching moves: it ends with the model on the phone.
    val ready = remember(fetching) { store.isReady && engine.isReady }
    // The verse whose microphone was tapped before the model was here: the fetching shows there.
    var wanted by remember(surah) { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { if (!ready) onWant(false) }
    return PageRecital(
        verse = if (ready) reciter.verse else wanted,
        state = when {
            ready -> reciter.state
            wanted != 0 -> Reciting.Fetching(R.string.recite_getting_model, fetching ?: Reciting.Fetching.UNKNOWN)
            else -> Reciting.Idle
        },
        reached = reciter.reached,
        heard = reciter.heard,
        onMic = { verse ->
            if (ready) reciter.toggle(surah, verse) else {
                wanted = verse
                onWant(true)
            }
        },
        onForget = reciter::forget,
    )
}

/** The tajweed model, opened once and kept for as long as the app runs: opening it takes a moment. */
private object Ears {
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

/** The reference file in the app's package: what a correct recitation of every word sounds like. Gzipped, but not named so: Android's build would unpack it. */
private const val REFERENCE = "tajweed_reference.bin"
