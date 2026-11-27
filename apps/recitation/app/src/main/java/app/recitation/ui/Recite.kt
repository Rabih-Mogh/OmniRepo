package app.recitation.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recitation.R
import app.recitation.core.Correction
import app.recitation.core.Fault
import app.recitation.core.Surah
import app.recitation.core.WordRecited
import app.recitation.data.Verse
import app.recitation.speech.EngineStore
import app.recitation.speech.TajweedStore

/** Where reciting a verse to the phone stands. */
sealed interface Reciting {
    /** Waiting for the reader to begin. */
    data object Idle : Reciting

    /** The microphone is open. */
    data object Listening : Reciting

    /** Something is being fetched first: [what] names it (a string that takes a percentage) and [fraction] says how far it is, [UNKNOWN] before that is known. */
    class Fetching(val what: Int, val fraction: Float) : Reciting {
        companion object {
            const val UNKNOWN = -1f
        }
    }

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
 * marked, red for a mistake in the wording and, for a point of tajweed, in the colour its
 * rule has in a surah. Tapping a marked word shows
 * what was heard and how to put it right. While the microphone is open the word being
 * recited is lit, as in a surah: [reached] is that word, counted from 0. [needsModel] is
 * true until the tajweed model has been fetched, which [onFetch] asks for again when it
 * isn't being fetched already.
 *
 * An exercise uses it too, a verse at a time: with its own [title] and [subtitle] in the
 * top bar, its own word on what to do ([how], or a picture of it: [guide]), its own
 * judgement over the general one ([verdict]), a mark on the word to stop on ([stopAfter],
 * counted from 0), and its own say over whether there is a verse before and after
 * ([canPrevious], [canNext]). Where a reciter's recording has the verse, [onHear] plays
 * it: the verse is heard before it is recited. An exercise on one rule has the letters
 * the rule falls on in the rule's colour ([focus], for each word the stretches of it, in
 * [focusInk]), stands how far it has got over the verse ([top]), and judges that rule
 * alone: it leaves out the count of sound words ([summary]) and, where it tells what was
 * heard in its own way, what there is to go over word by word ([card]). A word with
 * something to go over can be played back as the reader said it ([onPlayBack]).
 *
 * It is in the language chosen for teaching (see [Taught]), which its top bar changes.
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
    reached: Int = -1,
    title: String? = null,
    subtitle: String? = null,
    how: String? = null,
    verdict: (@Composable () -> Unit)? = null,
    stopAfter: Int = -1,
    canPrevious: Boolean = verseNumber > 1,
    canNext: Boolean = verseNumber < surah.verses,
    onHear: (() -> Unit)? = null,
    focus: List<List<IntRange>> = emptyList(),
    focusInk: Color? = null,
    top: (@Composable () -> Unit)? = null,
    guide: (@Composable () -> Unit)? = null,
    summary: Boolean = true,
    card: Boolean = true,
    onPlayBack: ((WordRecited) -> Unit)? = null,
) = Taught {
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // A word said twice (the reciter went back) is judged by the last time it was said.
    val heard = (state as? Reciting.Heard)?.words
    val words = verse?.words.orEmpty()
    val byWord: Map<Int, WordRecited> = remember(heard, words.size) { byWord(heard, words.size) }
    var chosen by remember(heard) { mutableIntStateOf(byWord.entries.firstOrNull { !it.value.sound }?.key ?: -1) }
    Column(Modifier.fillMaxSize()) {
        Banner {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(title ?: stringResource(R.string.recite_title), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(subtitle ?: stringResource(R.string.recite_verse, surahName(surah), verseNumber), style = MaterialTheme.typography.bodySmall, color = LocalBanner.current.quiet, maxLines = 1)
                }
                DoingChip()
                TeachSwitch(Modifier.padding(horizontal = 10.dp))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            top?.invoke()
            // The verse, from the right whatever the phone's language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    words.forEachIndexed { i, word ->
                        val result = byWord[i]
                        // Marked in red for a mistake in the wording, or in the colour of the rule of tajweed it is about.
                        val mark = result?.colour()
                        val marked = mark != null
                        // While it listens: the word being recited on the app's own block, the words still to come faint.
                        val reciting = state == Reciting.Listening
                        val ink = when {
                            mark != null -> mark
                            heard != null && result == null -> colors.outline
                            reciting && i > reached -> colors.outline
                            else -> colors.onSurface
                        }
                        // In an exercise on a rule, the letters the rule falls on are in its colour.
                        RuledWord(
                            word.arabic, focus.getOrNull(i).orEmpty(), focusInk ?: colors.primary,
                            (if (marked) {
                                Modifier.clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = if (i == chosen) 0.22f else 0.1f))
                                    .clickable(role = Role.Button) { chosen = i }
                            } else if (reciting && i == reached) {
                                Modifier.background(colors.primary.copy(alpha = 0.2f)).testTag("reciting-word")
                            } else Modifier).padding(horizontal = 6.dp),
                            color = ink,
                        )
                        // Where an exercise asks for a stop: the sign for pausing, after the word to stop on.
                        if (i == stopAfter) {
                            Icon(AppIcons.Pause, stringResource(R.string.drill_stop_here), Modifier.align(Alignment.CenterVertically).size(22.dp).testTag("stop-here"), tint = colors.primary)
                        }
                    }
                }
            }
            when (state) {
                Reciting.Idle -> {
                    if (guide != null) guide() else Text(how ?: stringResource(R.string.recite_how), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    // How it should go, in the reciter's own voice.
                    if (onHear != null) ThemeButton(stringResource(R.string.echo_play), onHear, Modifier.testTag("hear-reciter"), icon = AppIcons.Sound)
                }
                // It closes the microphone by itself when the reader falls silent: said, so they don't wait to be asked.
                Reciting.Listening -> Text(stringResource(R.string.recite_stops_itself), Modifier.testTag("listening-hint"), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                // What is being done is a picture: the ear in its turning ring, the fetch with its ring filling.
                Reciting.Checking -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WorkIcon(Picture(AppIcons.Ear), stringResource(R.string.recite_checking), size = 56.dp)
                }
                is Reciting.Fetching -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WorkIcon(Picture(AppIcons.Model, AppIcons.Download, state.fraction), stringResource(state.what, (state.fraction * 100).toInt()), size = 56.dp)
                }
                is Reciting.Failed -> Text(state.message, style = MaterialTheme.typography.bodyMedium, color = colors.error)
                is Reciting.Heard -> {
                    verdict?.invoke()
                    val sound = byWord.values.count { it.sound }
                    if (summary) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (sound == byWord.size) AppIcons.Check else AppIcons.Ear, null, Modifier.size(20.dp), tint = colors.primary)
                            Text(
                                if (sound == byWord.size) stringResource(R.string.recite_all_sound) else stringResource(R.string.recite_sound_words, sound, byWord.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    val picked = byWord[chosen]?.takeIf { !it.sound }
                    if (picked != null && card) CorrectionsCard(picked, onPlayBack = onPlayBack?.takeIf { picked.heardFrom >= 0f }?.let { play -> { play(picked) } }) else if (sound != byWord.size && card) {
                        Text(stringResource(R.string.recite_tap), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    guide?.invoke()
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
                // At the surah's first verse there is none before, and at its last none after: that button is plainly faint.
                val unavailable = band.content.faded(0.28f, band.background)
                // The verse before on the side reading starts from, the verse after on the other.
                IconButton(onClick = onPrevious, enabled = canPrevious) {
                    Icon(AppIcons.SkipPrevious, stringResource(R.string.previous_verse), Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f }, tint = if (canPrevious) band.content else unavailable)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val busy = state is Reciting.Checking || state is Reciting.Fetching
                    val label = stringResource(if (state == Reciting.Listening) R.string.recite_stop else if (state is Reciting.Heard) R.string.recite_again else R.string.recite_start)
                    Box(Modifier.graphicsLayer { alpha = if (busy || needsModel) 0.4f else 1f }.semantics { contentDescription = label }) {
                        MicButton(state == Reciting.Listening, onClick = { if (!busy && !needsModel) onMic() })
                    }
                }
                IconButton(onClick = onNext, enabled = canNext) {
                    Icon(AppIcons.SkipNext, stringResource(R.string.next_verse), Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f }, tint = if (canNext) band.content else unavailable)
                }
            }
        }
    }
}

/**
 * What was heard of a verse, by the verse's words as the app counts them ([words] of
 * them). A word said twice (the reciter went back) is judged by the last time it was said.
 */
internal fun byWord(heard: List<WordRecited>?, words: Int): Map<Int, WordRecited> {
    val last = heard?.maxOfOrNull { it.index } ?: -1
    // The reference counts a verse's words as the mushaf does; where the app's count differs, each takes its share.
    val counted = maxOf(last + 1, 1)
    return heard.orEmpty().associateBy { if (last < words) it.index else it.index * words / counted }
}

/**
 * The colour a word with something to go over is marked in: red for a mistake in the
 * wording itself; for a point of tajweed, the colour its rule has on the page and in the
 * legend of the colours, so the mark and the rule read as one.
 */
@Composable
internal fun WordRecited.colour(): Color? {
    val worst = corrections.firstOrNull { it.family == null } ?: corrections.firstOrNull() ?: return null
    return worst.family?.let { LocalTajweed.current.ink(it) } ?: if (worst.clear) MaterialTheme.colorScheme.error else LocalHighlight.current
}

/**
 * What to go over in one word: each correction with its kind, what was heard, and how to
 * put it right; then its madds as they were held. A point of tajweed is headed by its
 * family of rules, with the dot of its colour as in the legend: tapping that opens the
 * page that sets the rules out. It is in the language chosen for teaching (see [Taught]);
 * where it stands by itself on a page, [switch] gives it the way to change that. At its
 * foot, where the reader's own voice is at hand, the way to hear the word as they said it
 * ([onPlayBack]).
 */
@Composable
internal fun CorrectionsCard(word: WordRecited, modifier: Modifier = Modifier, switch: Boolean = false, onPlayBack: (() -> Unit)? = null) = Taught {
    val colors = MaterialTheme.colorScheme
    val amber = LocalHighlight.current
    val tajweed = LocalTajweed.current
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val rules = LocalTajweedRules.current
    val madds = stringArrayResource(R.array.madd_names)
    Card(modifier, colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (switch) CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) { TeachSwitch(Modifier.align(Alignment.End)) }
            for (correction in word.corrections) {
                val (title, how) = said(correction)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(if (correction.clear) R.string.recite_clear else R.string.recite_fine),
                            style = MaterialTheme.typography.labelMedium, color = if (correction.clear) colors.error else amber,
                        )
                        correction.family?.let { family ->
                            val name = family.named()
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.tajweed_rules), onClick = rules)
                                    .background(tajweed.ink(family).copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp).testTag("rule-family"),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(Modifier.size(10.dp).background(tajweed.ink(family), CircleShape))
                                Text(if (arabic) name.arabic else name.latin, style = MaterialTheme.typography.labelMedium, color = colors.onSurface, maxLines = 1)
                            }
                        }
                    }
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
            // The word as the phone heard it: what was marked can be heard for what it was.
            if (onPlayBack != null) ThemeButton(stringResource(R.string.recite_play_back), onPlayBack, Modifier.testTag("play-back"), icon = AppIcons.Sound)
        }
    }
}

/** The letters the guidance is written for, in the order of the letter_tips array. */
internal const val TIP_LETTERS = "ءهعحغخقكجشيضلنرطدتصزسظذثفبمو"

/** A correction in words: what is wrong, and how to put it right. */
@Composable
internal fun said(c: Correction): Pair<String, String> {
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

/**
 * Reciting verse [startVerse] of [surah] to the phone, and going on to the verses beside
 * it: opens the microphone, lights the word being recited as it goes, and when the reader
 * is done has the tajweed model listen and the corrections worked out, all on the phone
 * (see [Reciter]). [verseOf] fetches a verse's words. The model is fetched by the
 * background work: [fetching] is how far that has got (null when it isn't being fetched
 * now), and [onWant] asks for it: by itself when the screen opens, or because its button
 * was pressed.
 */
@Composable
fun ReciteScreen(surah: Surah, startVerse: Int, verseOf: suspend (Int) -> Verse?, fetching: Float?, onWant: (pressed: Boolean) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var number by remember(surah.number) { mutableIntStateOf(startVerse.coerceIn(1, surah.verses)) }
    var verse by remember { mutableStateOf<Verse?>(null) }
    val reciter = rememberReciter()
    val store = remember { TajweedStore(context) }
    val engine = remember { EngineStore(context) }
    // Looked at again whenever the fetching moves: it ends with the model on the phone.
    val ready = remember(fetching) { store.isReady && engine.isReady }
    LaunchedEffect(Unit) { if (!ready) onWant(false) }
    LaunchedEffect(surah.number, number) {
        verse = null
        reciter.rest()
        verse = verseOf(number)
    }
    val state = reciter.state
    ReciteContent(
        surah, number, verse,
        // Until the model is here, the fetching of it is what there is to show.
        state = if (!ready && fetching != null && state == Reciting.Idle) Reciting.Fetching(R.string.recite_getting_model, fetching) else state,
        needsModel = !ready,
        onMic = { reciter.toggle(surah.number, number) },
        onFetch = { onWant(true) },
        onPrevious = { if (number > 1 && !reciter.listening) number-- },
        onNext = { if (number < surah.verses && !reciter.listening) number++ },
        onBack = onBack,
        reached = reciter.reached,
        onPlayBack = { reciter.play(it) },
    )
}
