package app.recitation.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.recitation.R
import app.recitation.core.Correction
import app.recitation.core.Corrections
import app.recitation.core.Expected
import app.recitation.core.Fault
import app.recitation.core.Mel
import app.recitation.core.Surah
import app.recitation.core.TajweedReference
import app.recitation.core.WordRecited
import app.recitation.data.Verse
import app.recitation.speech.EngineStore
import app.recitation.speech.Mic
import app.recitation.speech.TajweedEar
import app.recitation.speech.TajweedStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens a verse (counted from 1) of the surah on show to be recited to the phone; null where that isn't offered. */
val LocalReciteVerse = staticCompositionLocalOf<((Int) -> Unit)?> { null }

/** Where reciting a verse to the phone stands. */
sealed interface Reciting {
    /** Waiting for the reader to begin. */
    data object Idle : Reciting

    /** The microphone is open. */
    data object Listening : Reciting

    /** Something is being fetched first: [what] names it (a string that takes a percentage) and [fraction] says how far it is. */
    class Fetching(val what: Int, val fraction: Float) : Reciting

    /** The model is listening to what was recited. */
    data object Checking : Reciting

    /** The verse as it was recited, a word at a time. */
    class Heard(val words: List<WordRecited>) : Reciting

    /** It couldn't be done: [message] says why. */
    class Failed(val message: String) : Reciting
}

/**
 * Reciting one verse and being corrected: the verse in large letters; the microphone
 * button; and, once the phone has listened, every word that has something to go over
 * marked, red for a clear mistake and amber for a fine point. Tapping a marked word shows
 * what was heard and how to put it right. [needsModel] is true until the tajweed model
 * has been fetched, which [onFetch] does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReciteContent(
    surah: Surah,
    verseNumber: Int,
    verse: Verse?,
    state: Reciting,
    needsModel: Boolean,
    onMic: () -> Unit,
    onFetch: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val amber = LocalHighlight.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // A word said twice (the reciter went back) is judged by the last time it was said.
    val heard = (state as? Reciting.Heard)?.words
    val words = verse?.words.orEmpty()
    val byWord: Map<Int, WordRecited> = remember(heard, words.size) {
        val last = heard?.maxOfOrNull { it.index } ?: -1
        // The reference counts a verse's words as the mushaf does; where the app's count differs, each takes its share.
        val counted = maxOf(last + 1, 1)
        heard.orEmpty().associateBy { if (last < words.size) it.index else it.index * words.size / counted }
    }
    var chosen by remember(heard) { mutableIntStateOf(byWord.entries.firstOrNull { !it.value.sound }?.key ?: -1) }
    Column(Modifier.fillMaxSize()) {
        Banner {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.recite_title), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(stringResource(R.string.recite_verse, surahName(surah), verseNumber), style = MaterialTheme.typography.bodySmall, color = LocalBanner.current.quiet, maxLines = 1)
                }
                DoingChip()
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // The verse, from the right whatever the phone's language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    words.forEachIndexed { i, word ->
                        val result = byWord[i]
                        val clear = result?.corrections?.any { it.clear } == true
                        val marked = result != null && !result.sound
                        val ink = when {
                            clear -> colors.error
                            marked -> amber
                            heard != null && result == null -> colors.outline
                            else -> colors.onSurface
                        }
                        Text(
                            word.arabic,
                            (if (marked) {
                                Modifier.clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = if (i == chosen) 0.22f else 0.1f))
                                    .clickable(role = Role.Button) { chosen = i }
                            } else Modifier).padding(horizontal = 6.dp),
                            fontFamily = QuranFont, fontSize = 30.sp, lineHeight = 62.sp, color = ink,
                        )
                    }
                }
            }
            when (state) {
                Reciting.Idle -> Text(stringResource(R.string.recite_how), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Reciting.Listening -> {}
                // What is being done is a picture: the ear in its turning ring, the fetch with its ring filling.
                Reciting.Checking -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WorkIcon(Picture(AppIcons.Ear), stringResource(R.string.recite_checking), size = 56.dp)
                }
                is Reciting.Fetching -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WorkIcon(Picture(AppIcons.Model, AppIcons.Download, state.fraction), stringResource(state.what, (state.fraction * 100).toInt()), size = 56.dp)
                }
                is Reciting.Failed -> Text(state.message, style = MaterialTheme.typography.bodyMedium, color = colors.error)
                is Reciting.Heard -> {
                    val sound = byWord.values.count { it.sound }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (sound == byWord.size) AppIcons.Check else AppIcons.Ear, null, Modifier.size(20.dp), tint = colors.primary)
                        Text(
                            if (sound == byWord.size) stringResource(R.string.recite_all_sound) else stringResource(R.string.recite_sound_words, sound, byWord.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    val picked = byWord[chosen]?.takeIf { !it.sound }
                    if (picked != null) CorrectionsCard(picked) else if (sound != byWord.size) {
                        Text(stringResource(R.string.recite_tap), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
            if (needsModel && state !is Reciting.Fetching) {
                Card(colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.recite_needs_model), style = MaterialTheme.typography.bodyMedium)
                        ThemeButton(stringResource(R.string.recite_get_model), onFetch, icon = AppIcons.Download, filled = true)
                    }
                }
            }
            Text(stringResource(R.string.recite_caution), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        BottomBand {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val band = LocalBanner.current
                // The verse before on the side reading starts from, the verse after on the other.
                IconButton(onClick = onPrevious, enabled = verseNumber > 1) {
                    Icon(AppIcons.SkipPrevious, stringResource(R.string.previous_verse), Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f }, tint = if (verseNumber > 1) band.content else band.quiet)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val busy = state is Reciting.Checking || state is Reciting.Fetching
                    val label = stringResource(if (state == Reciting.Listening) R.string.recite_stop else if (state is Reciting.Heard) R.string.recite_again else R.string.recite_start)
                    Box(Modifier.graphicsLayer { alpha = if (busy || needsModel) 0.4f else 1f }.semantics { contentDescription = label }) {
                        MicButton(state == Reciting.Listening, onClick = { if (!busy && !needsModel) onMic() })
                    }
                }
                IconButton(onClick = onNext, enabled = verseNumber < surah.verses) {
                    Icon(AppIcons.SkipNext, stringResource(R.string.next_verse), Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f }, tint = if (verseNumber < surah.verses) band.content else band.quiet)
                }
            }
        }
    }
}

/** What to go over in one word: each correction with its kind, what was heard, and how to put it right; then its madds as they were held. */
@Composable
private fun CorrectionsCard(word: WordRecited) {
    val colors = MaterialTheme.colorScheme
    val amber = LocalHighlight.current
    val madds = stringArrayResource(R.array.madd_names)
    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (correction in word.corrections) {
                val (title, how) = said(correction)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(if (correction.clear) R.string.recite_clear else R.string.recite_fine),
                        style = MaterialTheme.typography.labelMedium, color = if (correction.clear) colors.error else amber,
                    )
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(how, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            for (madd in word.madds) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(madds[madd.rule.ordinal], Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(stringResource(R.string.recite_counts, madd.counts), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                }
            }
            if (word.stopped) Text(stringResource(R.string.recite_stopped_here), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/** The letters the guidance is written for, in the order of the letter_tips array. */
private const val TIP_LETTERS = "ءهعحغخقكجشيضلنرطدتصزسظذثفبمو"

/** A correction in words: what is wrong, and how to put it right. */
@Composable
private fun said(c: Correction): Pair<String, String> {
    val madds = stringArrayResource(R.array.madd_names)
    val tips = stringArrayResource(R.array.letter_tips)
    val name = c.madd?.let { madds[it.ordinal] }.orEmpty()
    @Composable fun vowel(sign: String) = stringResource(
        when (sign.firstOrNull()) {
            'ُ' -> R.string.vowel_damma
            'ِ' -> R.string.vowel_kasra
            else -> R.string.vowel_fatha
        },
    )
    return when (c.fault) {
        Fault.MADD_SHORT -> stringResource(R.string.fault_madd_short, name) to stringResource(R.string.fault_madd_short_how, c.heardCounts, c.wantedCounts)
        Fault.MADD_LONG -> stringResource(R.string.fault_madd_long, name) to stringResource(R.string.fault_madd_long_how, c.heardCounts, c.wantedCounts)
        Fault.MADD_MISSING -> stringResource(R.string.fault_madd_missing, name) to stringResource(R.string.fault_madd_missing_how, c.wantedCounts)
        Fault.MADD_ADDED -> stringResource(R.string.fault_madd_added) to stringResource(R.string.fault_madd_added_how)
        Fault.MADD_UNEVEN -> stringResource(R.string.fault_madd_uneven, name) to stringResource(R.string.fault_madd_uneven_how, c.lengths.map { stringResource(R.string.number, it) }.joinToString(" / "))
        Fault.GHUNNAH_SHORT -> stringResource(R.string.fault_ghunnah_short) to stringResource(R.string.fault_ghunnah_short_how)
        Fault.IKHFA_CLEAR -> stringResource(R.string.fault_ikhfa_clear) to stringResource(R.string.fault_ikhfa_clear_how)
        Fault.IKHFA_SHORT -> stringResource(R.string.fault_ikhfa_short) to stringResource(R.string.fault_ikhfa_short_how)
        Fault.IQLAB_MISSED -> stringResource(R.string.fault_iqlab_missed) to stringResource(R.string.fault_iqlab_missed_how)
        Fault.IDGHAM_MISSED -> stringResource(R.string.fault_idgham_missed) to stringResource(R.string.fault_idgham_missed_how, c.wanted)
        Fault.IDGHAM_GHUNNAH_SHORT -> stringResource(R.string.fault_idgham_ghunnah_short) to stringResource(R.string.fault_idgham_ghunnah_short_how, c.wanted)
        Fault.SHADDA_MISSING -> stringResource(R.string.fault_shadda_missing, c.wanted) to stringResource(R.string.fault_shadda_missing_how)
        Fault.QALQALAH_MISSING -> stringResource(R.string.fault_qalqalah_missing, c.wanted) to stringResource(R.string.fault_qalqalah_missing_how)
        // How the letter that was wanted is said, from where in the mouth: what a teacher would show.
        Fault.LETTER -> stringResource(R.string.fault_letter, c.heard, c.wanted) to
            (c.wanted.firstOrNull { it in TIP_LETTERS }?.let { tips[TIP_LETTERS.indexOf(it)] } ?: stringResource(R.string.fault_dropped_how))
        Fault.VOWEL -> stringResource(R.string.fault_vowel, vowel(c.heard), vowel(c.wanted)) to stringResource(R.string.fault_vowel_how)
        Fault.DROPPED -> stringResource(R.string.fault_dropped, c.wanted) to
            (c.wanted.firstOrNull { it in TIP_LETTERS }?.let { tips[TIP_LETTERS.indexOf(it)] } ?: stringResource(R.string.fault_dropped_how))
        Fault.ADDED -> stringResource(R.string.fault_added, c.heard) to stringResource(R.string.fault_added_how)
        Fault.WORD_MISSING -> stringResource(R.string.fault_word_missing) to stringResource(R.string.fault_word_missing_how)
    }
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

/**
 * Reciting verse [startVerse] of [surah] to the phone, and going on to the verses beside
 * it: opens the microphone, and when the reader is done has the tajweed model listen and
 * the corrections worked out, all on the phone. [verseOf] fetches a verse's words.
 */
@Composable
fun ReciteScreen(surah: Surah, startVerse: Int, verseOf: suspend (Int) -> Verse?, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var number by remember(surah.number) { mutableIntStateOf(startVerse.coerceIn(1, surah.verses)) }
    var verse by remember { mutableStateOf<Verse?>(null) }
    var state by remember { mutableStateOf<Reciting>(Reciting.Idle) }
    var listening by remember { mutableStateOf(false) }
    val store = remember { TajweedStore(context) }
    val engine = remember { EngineStore(context) }
    var ready by remember { mutableStateOf(store.isReady && engine.isReady) }
    // The reciter's pace, kept from verse to verse: a short verse may have no natural madd to count by.
    val tempo = remember(surah.number) { ArrayList<Float>() }
    val refused = stringResource(R.string.voice_no_mic)
    val busy = stringResource(R.string.voice_mic_busy)
    val nothing = stringResource(R.string.recite_nothing_heard)
    val unknown = stringResource(R.string.recite_no_reference)
    val failed = stringResource(R.string.recite_failed, "%s")
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listening = true else state = Reciting.Failed(refused)
    }
    LaunchedEffect(surah.number, number) {
        verse = null
        state = Reciting.Idle
        verse = verseOf(number)
    }
    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        state = Reciting.Listening
        val blocks = ArrayList<FloatArray>()
        var samples = 0
        var problem: String? = null
        try {
            Mic.sound().collect { block ->
                blocks += block
                samples += block.size
                if (samples >= LONGEST) listening = false
            }
        } catch (e: CancellationException) {
            // Stopped by the button: listened to below.
        } catch (e: Mic.Unavailable) {
            problem = busy
        } finally {
            listening = false
            if (problem != null) state = Reciting.Failed(problem) else {
                val sound = FloatArray(samples)
                var at = 0
                for (block in blocks) { System.arraycopy(block, 0, sound, at, block.size); at += block.size }
                val surahNumber = surah.number
                val verseNumber = number
                state = Reciting.Checking
                scope.launch {
                    state = try {
                        withContext(Dispatchers.Default) {
                            val forms = context.assets.open(REFERENCE).use { TajweedReference.surah(it, surahNumber) }.getOrNull(verseNumber - 1)
                            if (forms == null) Reciting.Failed(unknown) else {
                                val heard = Ears.get(context).hear(sound)
                                if (heard.symbols.length < 2) Reciting.Failed(nothing)
                                else Reciting.Heard(Corrections.of(Expected.recited(forms, heard.symbols), heard, tempo = tempo))
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Reciting.Failed(failed.format(e.message ?: e.javaClass.simpleName))
                    }
                }
            }
        }
    }
    ReciteContent(
        surah, number, verse, state, needsModel = !ready,
        onMic = {
            if (listening) listening = false
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listening = true
            else ask.launch(Manifest.permission.RECORD_AUDIO)
        },
        onFetch = {
            scope.launch {
                state = try {
                    // The engine first, if a recording has never been prepared on this phone; then the model.
                    engine.obtain { state = Reciting.Fetching(R.string.recite_getting_engine, it) }
                    store.obtain { state = Reciting.Fetching(R.string.recite_getting_model, it) }
                    ready = true
                    Reciting.Idle
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Reciting.Failed(failed.format(e.message ?: e.javaClass.simpleName))
                }
            }
        },
        onPrevious = { if (number > 1 && !listening) number-- },
        onNext = { if (number < surah.verses && !listening) number++ },
        onBack = onBack,
    )
}

/** The reference file in the app's package: what a correct recitation of every word sounds like. Gzipped, but not named so: Android's build would unpack it. */
private const val REFERENCE = "tajweed_reference.bin"
