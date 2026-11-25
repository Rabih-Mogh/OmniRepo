package app.recitation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.recitation.R
import app.recitation.core.Corrections
import app.recitation.core.Drill
import app.recitation.core.Drills
import app.recitation.core.Expected
import app.recitation.core.Family
import app.recitation.core.Holding
import app.recitation.core.Mel
import app.recitation.core.Rule
import app.recitation.core.SURAHS
import app.recitation.core.Silences
import app.recitation.core.TajweedReference
import app.recitation.core.Topic
import app.recitation.core.WordRecited
import app.recitation.data.Echo
import app.recitation.data.Hear
import app.recitation.data.Spot
import app.recitation.data.Tally
import app.recitation.data.Verse
import app.recitation.data.Voice
import app.recitation.data.VerseRef
import app.recitation.speech.AudioDecoder
import app.recitation.speech.EngineStore
import app.recitation.speech.Mic
import app.recitation.speech.TajweedStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

/** The rule of recitation a topic is, where it is one: it gives the topic its name and its colour. */
fun Topic.rule(): Rule? = when (this) {
    Topic.GHUNNAH -> Rule.GHUNNAH
    Topic.IKHFA -> Rule.IKHFA
    Topic.IQLAB -> Rule.IQLAB
    Topic.IDGHAM -> Rule.IDGHAM_GHUNNAH
    Topic.QALQALAH -> Rule.QALQALAH
    Topic.MADD_NATURAL -> Rule.MADD_TABII
    Topic.MADD_MUNFASIL -> Rule.MADD_MUNFASIL
    Topic.MADD_MUTTASIL -> Rule.MADD_MUTTASIL
    Topic.MADD_LAZIM -> Rule.MADD_LAZIM
    Topic.MADD_ARID -> Rule.MADD_ARID
    Topic.STOP, Topic.LETTERS -> null
}

/** A topic's name: the rule's own, in Arabic or in Latin letters as the app's language has it; for letters, the two letters. */
@Composable
fun Topic.named(letters: String = ""): String {
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    return when (this) {
        Topic.STOP -> stringResource(R.string.topic_stop)
        Topic.LETTERS -> letters.toList().joinToString(" · ")
        Topic.IDGHAM -> stringResource(R.string.topic_idgham)
        else -> rule()!!.named().let { if (arabic) it.arabic else it.latin }
    }
}

/** The topics a rule can be drilled on, in the order a learner meets them. */
val RULE_TOPICS = listOf(
    Topic.GHUNNAH, Topic.IKHFA, Topic.IQLAB, Topic.IDGHAM, Topic.QALQALAH,
    Topic.MADD_NATURAL, Topic.MADD_MUNFASIL, Topic.MADD_MUTTASIL, Topic.MADD_LAZIM, Topic.MADD_ARID,
)

/**
 * Tajweed on the Practise tab, a card each. First the lessons: every rule is taught before
 * it is practised, and the ones whose exercise has gone well are ticked. Then the reader's
 * weak spots, letters made close to each other, stopping on a word (each of those two
 * taught first as well), counting a madd, spotting the rule in a word, and echoing a
 * reciter. [tally] is what the reader has been corrected on and has learned; [canEcho]
 * whether there is a recording to echo. [onLesson] opens the lesson on a topic.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TajweedExercises(
    tally: Tally,
    canEcho: Boolean,
    onLesson: (Topic, String) -> Unit,
    onWeak: () -> Unit,
    onMadd: () -> Unit,
    onSpot: () -> Unit,
    onEcho: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tajweed = LocalTajweed.current
    ExerciseCard(AppIcons.Book, stringResource(R.string.practice_learn_title), stringResource(R.string.practice_learn_body)) {
        Text(
            stringResource(R.string.practice_learned, RULE_TOPICS.count { tally.learned(it) }, RULE_TOPICS.size), Modifier.testTag("learned-count"),
            style = MaterialTheme.typography.labelLarge, color = colors.primary,
        )
        // The rules in the order a learner meets them, each by its own name with the dot of its colour.
        Column {
            for (topic in RULE_TOPICS) {
                val learned = tally.learned(topic)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onLesson(topic, "") }.padding(horizontal = 4.dp, vertical = 11.dp).testTag("lesson-${topic.name}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(12.dp).background(tajweed.ink(topic.rule()!!.family), CircleShape))
                    Text(topic.named(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (learned) Icon(AppIcons.Check, stringResource(R.string.lesson_learned), Modifier.size(20.dp), tint = colors.primary)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = colors.outline)
                }
            }
        }
    }
    ExerciseCard(AppIcons.Retry, stringResource(R.string.practice_weak_title), stringResource(if (tally.open.isEmpty()) R.string.practice_weak_none else R.string.practice_weak_body)) {
        if (tally.open.isNotEmpty()) {
            // Where the reader is corrected most, with how often.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((topic, count) in tally.weakest.take(4)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(10.dp).background(topic.rule()?.let { tajweed.ink(it.family) } ?: colors.error, CircleShape))
                        Text(if (topic == Topic.LETTERS) stringResource(R.string.practice_pairs_title) else topic.named(), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.number, count), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    }
                }
            }
            ThemeButton(stringResource(R.string.practice_weak_go), onWeak, Modifier.fillMaxWidth().testTag("weak-go"), icon = AppIcons.Mic, filled = true)
        }
    }
    ExerciseCard(AppIcons.Meanings, stringResource(R.string.practice_pairs_title), stringResource(R.string.practice_pairs_body)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (pair in Drills.PAIRS) Choice(Topic.LETTERS.named(pair), null, Modifier.testTag("pair-$pair"), arabic = true, ticked = tally.learned(Topic.LETTERS, pair)) { onLesson(Topic.LETTERS, pair) }
        }
    }
    ExerciseCard(AppIcons.Pause, stringResource(R.string.practice_stops_title), stringResource(R.string.practice_stops_body)) {
        ThemeButton(stringResource(R.string.practice_start), { onLesson(Topic.STOP, "") }, Modifier.fillMaxWidth().testTag("stops-go"), icon = AppIcons.Book)
    }
    ExerciseCard(AppIcons.Steady, stringResource(R.string.practice_madd_title), stringResource(R.string.practice_madd_body)) {
        ThemeButton(stringResource(R.string.practice_start), onMadd, Modifier.fillMaxWidth().testTag("madd-go"), icon = AppIcons.Mic)
    }
    ExerciseCard(AppIcons.Search, stringResource(R.string.practice_spot_title), stringResource(R.string.practice_spot_body)) {
        ThemeButton(stringResource(R.string.practice_start), onSpot, Modifier.fillMaxWidth().testTag("spot-go"), icon = AppIcons.Play)
    }
    ExerciseCard(AppIcons.Ear, stringResource(R.string.practice_echo_title), stringResource(if (canEcho) R.string.practice_echo_body else R.string.practice_echo_none)) {
        if (canEcho) ThemeButton(stringResource(R.string.practice_start), onEcho, Modifier.fillMaxWidth().testTag("echo-go"), icon = AppIcons.Play)
    }
}

/** A card of the Practise tab: its picture and name, a line on what it is, and what starts it. */
@Composable
private fun ExerciseCard(icon: ImageVector, title: String, body: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(24.dp), tint = colors.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            content()
        }
    }
}

/** One of a card's choices: a rule with the dot of its colour, or a pair of letters. */
@Composable
private fun Choice(label: String, dot: Color?, modifier: Modifier = Modifier, arabic: Boolean = false, ticked: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.clip(RoundedCornerShape(50)).border(1.dp, colors.outlineVariant, RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (dot != null) Box(Modifier.size(10.dp).background(dot, CircleShape))
        if (arabic) Text(label, fontFamily = QuranFont, fontSize = 20.sp, color = colors.onSurface) else Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
        if (ticked) Icon(AppIcons.Check, stringResource(R.string.lesson_learned), Modifier.size(16.dp), tint = colors.primary)
    }
}

/**
 * An exercise made of verses to recite, one after another, each judged for one thing
 * alone: a rule, a pair of letters, a stop, or whatever the reader was corrected on in
 * that verse. [title] names it. Each go counts as it was last recited; at the end the
 * goes that were sound are counted. The verses are recited and checked as on the page for
 * reciting a verse (see [ReciteScreen]). A go that wasn't sound is taught again there and
 * then: how the thing is said stands under the verdict. [onDone] is told how it went at
 * the end: how many goes were sound, of how many.
 *
 * It is taught in a reciter's voice wherever a recording on the phone has the verse
 * ([voiceOf]): the verse can be heard before it is recited, and after a go that wasn't
 * sound, the words the go was about can be heard as the reciter says them.
 */
@Composable
fun DrillScreen(
    title: String,
    drills: List<Drill>,
    verseOf: suspend (VerseRef) -> Verse?,
    fetching: Float?,
    onWant: (pressed: Boolean) -> Unit,
    onBack: () -> Unit,
    onDone: (sound: Int, of: Int) -> Unit = { _, _ -> },
    voiceOf: suspend (Drill) -> Voice? = { null },
) {
    val context = LocalContext.current.applicationContext
    var at by remember(drills) { mutableIntStateOf(0) }
    val results = remember(drills) { mutableStateMapOf<Int, Boolean>() }
    var over by remember(drills) { mutableStateOf(drills.isEmpty()) }
    var verse by remember { mutableStateOf<Verse?>(null) }
    val reciter = rememberReciter()
    val store = remember { TajweedStore(context) }
    val engine = remember { EngineStore(context) }
    val ready = remember(fetching) { store.isReady && engine.isReady }
    LaunchedEffect(Unit) { if (!ready) onWant(false) }
    val drill = drills.getOrNull(at)
    var voice by remember { mutableStateOf<Voice?>(null) }
    val clip = rememberClip()
    LaunchedEffect(drills, at) {
        verse = null
        voice = null
        clip.stop()
        reciter.rest()
        verse = drill?.let { verseOf(VerseRef(it.surah, it.verse)) }
        voice = drill?.let { voiceOf(it) }
    }
    val state = reciter.state
    LaunchedEffect(state) { if (state is Reciting.Heard && drill != null) results[at] = Drills.sound(drill, state.words) }
    LaunchedEffect(over) { if (over && drills.isNotEmpty()) onDone(results.values.count { it }, drills.size) }
    if (over || drill == null) {
        DrillOver(title, drills, results, onAgain = { results.clear(); at = 0; over = drills.isEmpty() }, onBack = onBack)
        return
    }
    val surah = SURAHS[drill.surah - 1]
    val name = drill.topic.named(drill.letters)
    ReciteContent(
        surah, drill.verse, verse,
        state = if (!ready && fetching != null && state == Reciting.Idle) Reciting.Fetching(R.string.recite_getting_model, fetching) else state,
        needsModel = !ready,
        onMic = {
            // The reader's voice is what is listened to: the reciter's falls silent.
            clip.stop()
            reciter.toggle(drill.surah, drill.verse)
        },
        onFetch = { onWant(true) },
        onPrevious = { if (at > 0 && !reciter.listening) at-- },
        onNext = { if (!reciter.listening) { if (at == drills.lastIndex) over = true else at++ } },
        onBack = onBack,
        reached = reciter.reached,
        title = title,
        subtitle = stringResource(R.string.drill_progress, at + 1, drills.size) + " · " + stringResource(R.string.recite_verse, surahName(surah), drill.verse),
        how = when (drill.topic) {
            Topic.STOP -> stringResource(R.string.drill_how_stop)
            Topic.LETTERS -> stringResource(R.string.drill_how_letters, name)
            else -> stringResource(R.string.drill_how, name)
        },
        verdict = {
            results[at]?.let { sound ->
                DrillVerdict(
                    drill, sound,
                    // The words the go was about as the reciter says them, or failing that the whole verse.
                    onHear = voice?.let { v -> { if (v.placeFromMs >= 0) clip.play(v.sound, v.placeFromMs, v.placeUntilMs) else clip.play(v.sound, v.fromMs, v.untilMs) } },
                )
            }
        },
        stopAfter = drill.stopAfter,
        canPrevious = at > 0, canNext = true,
        onHear = voice?.let { v -> { clip.play(v.sound, v.fromMs, v.untilMs) } },
    )
}

/**
 * How a go went for the thing it was about: sound, or to go over; and if to go over, how
 * it is said, as the lesson has it, with the way to hear a reciter say it ([onHear];
 * null where no recording has the verse).
 */
@Composable
fun DrillVerdict(drill: Drill, sound: Boolean, onHear: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val name = drill.topic.named(drill.letters)
    val says = when {
        drill.topic == Topic.STOP -> stringResource(if (sound) R.string.drill_stop_sound else R.string.drill_stop_missed)
        sound -> stringResource(R.string.drill_sound, name)
        else -> stringResource(R.string.drill_missed, name)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background((if (sound) colors.primary else colors.error).copy(alpha = 0.1f)).padding(12.dp).testTag("drill-verdict"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (sound) AppIcons.Check else AppIcons.Warning, null, Modifier.size(22.dp), tint = if (sound) colors.primary else colors.error)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(says, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            if (!sound) Text(stringResource(lessonOf(drill.topic, drill.letters).how), Modifier.testTag("drill-teach"), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            if (!sound && onHear != null) ThemeButton(stringResource(R.string.drill_hear_it), onHear, Modifier.testTag("hear-it"), icon = AppIcons.Sound)
        }
    }
}

/** The end of an exercise: how many of its goes were sound, each go with how it went, and whether to do it once more. */
@Composable
fun DrillOver(title: String, drills: List<Drill>, results: Map<Int, Boolean>, onAgain: () -> Unit, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Banner {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (drills.isEmpty()) {
                Text(stringResource(R.string.drill_none), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(stringResource(R.string.drill_over, results.values.count { it }, drills.size), Modifier.testTag("drill-score"), style = MaterialTheme.typography.headlineSmall, color = colors.primary)
                for ((i, drill) in drills.withIndex()) {
                    val result = results[i]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            when (result) { true -> AppIcons.Check; false -> AppIcons.Warning; null -> AppIcons.Hourglass }, null, Modifier.size(20.dp),
                            tint = when (result) { true -> colors.primary; false -> colors.error; null -> colors.outline },
                        )
                        Text(stringResource(R.string.recite_verse, surahName(SURAHS[drill.surah - 1]), drill.verse), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(drill.topic.named(drill.letters), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (drills.isNotEmpty()) ThemeButton(stringResource(R.string.drill_again), onAgain, Modifier.weight(1f), icon = AppIcons.Retry)
            ThemeButton(stringResource(R.string.drill_done), onBack, Modifier.weight(1f), icon = AppIcons.Check, filled = true)
        }
    }
}

/** Each madd with how long it is held, for the table over the counting. */
private val MADD_LENGTHS = listOf(
    Topic.MADD_NATURAL to R.string.madd_len_2, Topic.MADD_MUNFASIL to R.string.madd_len_4_5, Topic.MADD_MUTTASIL to R.string.madd_len_4_5,
    Topic.MADD_LAZIM to R.string.madd_len_6, Topic.MADD_ARID to R.string.madd_len_2_4_6,
)

/** The counts a madd can be asked to be held for. */
val MADD_COUNTS = listOf(2, 4, 5, 6)

/** How near the counts aimed for a hold has to come to be on the mark. */
private const val ON_THE_MARK = 0.5f

/**
 * Counting a madd: the reader holds a long vowel and its counts fill as they hold it.
 * [target] is the counts aimed for and [pace] how long one count is, in seconds;
 * [seconds] is the hold under way and [holds] the ones before it, oldest first.
 */
@Composable
fun MaddContent(
    target: Int,
    pace: Float,
    listening: Boolean,
    seconds: Float,
    holds: List<Float>,
    problem: String?,
    onTarget: (Int) -> Unit,
    onPace: (Float) -> Unit,
    onToggle: () -> Unit,
    onBack: () -> Unit,
    onHearMadd: ((Topic) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shown = if (seconds > 0f) seconds else holds.lastOrNull() ?: 0f
    val counts = Holding.counts(shown, pace)
    Column(Modifier.fillMaxSize()) {
        Banner {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(R.string.practice_madd_title), Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.madd_how), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            // What to aim for: how long each madd is.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerLow).padding(12.dp).testTag("madd-table"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.madd_table), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                for ((topic, length) in MADD_LENGTHS) {
                    // Each can be heard as a reciter holds it, where a recording on the phone has one.
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .then(if (onHearMadd != null) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.madd_hear)) { onHearMadd(topic) } else Modifier)
                            .padding(vertical = 4.dp).testTag("madd-row-${topic.name}"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        topic.ink()?.let { Box(Modifier.size(10.dp).background(it, CircleShape)) }
                        Text(topic.named(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(length), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                        if (onHearMadd != null) Icon(AppIcons.Sound, null, Modifier.size(18.dp), tint = colors.primary)
                    }
                }
            }
            // How many counts to aim for.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.madd_target), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                for (n in MADD_COUNTS) {
                    val chosen = n == target
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).then(if (chosen) Modifier.background(colors.primary) else Modifier.border(1.dp, colors.outlineVariant, CircleShape))
                            .clickable(role = Role.RadioButton) { onTarget(n) }.testTag("madd-$n"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.number, n), style = MaterialTheme.typography.titleMedium, color = if (chosen) colors.onPrimary else colors.onSurface)
                    }
                }
            }
            // How long one count is: longer for a slow recitation, shorter for a quick one.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { onPace((pace - 0.05f).coerceAtLeast(0.3f)) }) { Icon(AppIcons.Remove, stringResource(R.string.madd_shorter), tint = colors.primary) }
                Text(stringResource(R.string.madd_pace, String.format(Locale.getDefault(), "%.2f", pace)), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onPace((pace + 0.05f).coerceAtMost(0.9f)) }) { Icon(AppIcons.Add, stringResource(R.string.madd_longer), tint = colors.primary) }
            }
            // The counts as beads on a line: each fills as the hold reaches it, and the hold runs on past them if it is too long.
            Canvas(Modifier.fillMaxWidth().height(64.dp).testTag("madd-beads")) {
                val beads = maxOf(target, 6)
                val gap = size.width / (beads + 1)
                val y = size.height / 2
                drawLine(colors.outlineVariant, Offset(gap, y), Offset(size.width - gap, y), 4.dp.toPx(), StrokeCap.Round)
                if (counts > 0f) drawLine(colors.primary, Offset(gap, y), Offset(gap + ((counts - 1f).coerceAtLeast(0f) * gap).coerceAtMost(size.width - 2 * gap), y), 4.dp.toPx(), StrokeCap.Round)
                for (n in 1..beads) {
                    val x = n * gap
                    val full = counts >= n
                    val part = (counts - (n - 1)).coerceIn(0f, 1f)
                    drawCircle(if (n <= target) colors.surfaceContainerHighest else colors.surfaceContainerLow, 16.dp.toPx(), Offset(x, y))
                    if (part > 0f) drawCircle(if (n <= target) colors.primary else colors.error, 16.dp.toPx() * (if (full) 1f else 0.35f + 0.65f * part), Offset(x, y))
                    if (n == target) drawCircle(colors.primary, 20.dp.toPx(), Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
            }
            if (shown > 0f) {
                Text(stringResource(R.string.madd_held, String.format(Locale.getDefault(), "%.1f", counts)), Modifier.testTag("madd-held"), style = MaterialTheme.typography.headlineSmall, color = colors.primary)
                if (seconds == 0f) {
                    val off = counts - target
                    Text(
                        when {
                            abs(off) <= ON_THE_MARK -> stringResource(R.string.madd_on)
                            off < 0 -> stringResource(R.string.madd_short, target)
                            else -> stringResource(R.string.madd_long, target)
                        },
                        style = MaterialTheme.typography.titleMedium, color = if (abs(off) <= ON_THE_MARK) colors.primary else colors.error,
                    )
                }
            }
            // The holds so far, each a bar against the line of the counts aimed for: how even they are shows at a glance.
            if (holds.size > 1) {
                Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                    val most = maxOf(target + 2f, holds.takeLast(8).maxOf { Holding.counts(it, pace) })
                    val wide = size.width / 8
                    for ((i, hold) in holds.takeLast(8).withIndex()) {
                        val c = Holding.counts(hold, pace)
                        val tall = c / most * size.height
                        drawRect(if (abs(c - target) <= ON_THE_MARK) colors.primary else colors.outline, Offset(i * wide + wide * 0.2f, size.height - tall), Size(wide * 0.6f, tall))
                    }
                    val line = size.height - target / most * size.height
                    drawLine(colors.primary, Offset(0f, line), Offset(size.width, line), 1.5.dp.toPx())
                }
            }
            if (problem != null) Text(problem, style = MaterialTheme.typography.bodyMedium, color = colors.error)
        }
        BottomBand {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) { MicButton(listening, onToggle) }
        }
    }
}

/**
 * Counting a madd, at work: the microphone is listened to and each stretch of voice timed
 * (see [Holding]). [hearOf] finds words of a recording on the phone with a madd of a kind
 * in them, to be heard as its reciter holds it.
 */
@Composable
fun MaddScreen(onBack: () -> Unit, hearOf: suspend (Topic) -> Hear? = { null }) {
    val context = LocalContext.current
    val clip = rememberClip()
    val scope = rememberCoroutineScope()
    var target by remember { mutableIntStateOf(4) }
    var pace by remember { mutableFloatStateOf(0.5f) }
    var listening by remember { mutableStateOf(false) }
    var seconds by remember { mutableFloatStateOf(0f) }
    val holds = remember { mutableStateListOf<Float>() }
    var problem by remember { mutableStateOf<String?>(null) }
    val refused = stringResource(R.string.voice_no_mic)
    val busy = stringResource(R.string.voice_mic_busy)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) listening = true else problem = refused }
    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        problem = null
        val holding = Holding()
        try {
            Mic.sound().collect { block ->
                holding.add(block)
                seconds = holding.seconds
                while (holds.size < holding.holds.size) holds += holding.holds[holds.size]
            }
        } catch (e: CancellationException) {
            // Stopped by the button.
        } catch (e: Mic.Unavailable) {
            problem = busy
        } finally {
            listening = false
            seconds = 0f
        }
    }
    MaddContent(
        target, pace, listening, seconds, holds, problem,
        onTarget = { target = it }, onPace = { pace = it },
        onToggle = {
            if (listening) listening = false
            else {
                holds.clear()
                clip.stop()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listening = true
                else ask.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onBack = onBack,
        // Not while the microphone is open: it would time the reciter's madd as the reader's.
        onHearMadd = { topic -> if (!listening) scope.launch { hearOf(topic)?.let { clip.play(it.sound, it.fromMs, it.untilMs) } } },
    )
}

/**
 * Spotting the rule: a word is shown without its colours, and played where it can be; the
 * reader says which family of rules is at work in it, and is shown whether that is so and
 * what the family is. At the end, how many were right.
 */
@Composable
fun SpotContent(spots: List<Spot>, onPlay: (Spot) -> Unit, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tajweed = LocalTajweed.current
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    var at by remember(spots) { mutableIntStateOf(0) }
    var picked by remember(spots, at) { mutableStateOf<Family?>(null) }
    var right by remember(spots) { mutableIntStateOf(0) }
    val spot = spots.getOrNull(at)
    Column(Modifier.fillMaxSize()) {
        Banner {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.practice_spot_title), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    if (spot != null) Text(stringResource(R.string.drill_progress, at + 1, spots.size), style = MaterialTheme.typography.bodySmall, color = LocalBanner.current.quiet)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                spots.isEmpty() -> Text(stringResource(R.string.spot_none), style = MaterialTheme.typography.bodyLarge)
                spot == null -> Text(stringResource(R.string.drill_over, right, spots.size), Modifier.testTag("spot-score"), style = MaterialTheme.typography.headlineSmall, color = colors.primary)
                else -> {
                    Text(stringResource(R.string.spot_which), style = MaterialTheme.typography.titleMedium)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(spot.word, Modifier.testTag("spot-word"), fontFamily = QuranFont, fontSize = 52.sp, lineHeight = 96.sp, color = colors.onSurface, textAlign = TextAlign.Center)
                    }
                    Text(stringResource(R.string.recite_verse, surahName(SURAHS[spot.surah - 1]), spot.verse), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    if (spot.sound != null) ThemeButton(stringResource(R.string.spot_play), { onPlay(spot) }, icon = AppIcons.Sound)
                    for (family in spot.options) {
                        val name = family.named().let { if (arabic) it.arabic else it.latin }
                        val answered = picked != null
                        val isRight = family == spot.family
                        val edge = when {
                            answered && isRight -> colors.primary
                            answered && family == picked -> colors.error
                            else -> colors.outlineVariant
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(if (edge == colors.outlineVariant) 1.dp else 2.dp, edge, RoundedCornerShape(14.dp))
                                .clickable(enabled = !answered, role = Role.RadioButton) {
                                    picked = family
                                    if (isRight) right++
                                }.padding(14.dp).testTag("spot-${family.name}"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(14.dp).background(tajweed.ink(family), CircleShape))
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (answered && isRight) Icon(AppIcons.Check, null, Modifier.size(20.dp), tint = colors.primary)
                            if (answered && family == picked && !isRight) Icon(AppIcons.Warning, null, Modifier.size(20.dp), tint = colors.error)
                        }
                    }
                    // Once answered: what that family of rules is.
                    // Once answered, it is taught: the rule at work in the word by its name, and what the rule is.
                    if (picked != null) {
                        val family = spot.family.named()
                        val rule = spot.rule?.named()
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerLow).padding(14.dp).testTag("spot-note"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (rule != null) (if (arabic) rule.arabic else rule.latin) else (if (arabic) family.arabic else family.latin), style = MaterialTheme.typography.titleSmall, color = tajweed.ink(spot.family))
                            Text(stringResource(rule?.meaning ?: family.note), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (spot == null) {
                if (spots.isNotEmpty()) ThemeButton(stringResource(R.string.drill_again), { at = 0; right = 0 }, Modifier.weight(1f), icon = AppIcons.Retry)
                ThemeButton(stringResource(R.string.drill_done), onBack, Modifier.weight(1f), icon = AppIcons.Check, filled = true)
            } else {
                ThemeButton(stringResource(R.string.spot_next), { if (picked != null) at++ }, Modifier.weight(1f).testTag("spot-next"), icon = AppIcons.SkipNext, filled = picked != null)
            }
        }
    }
}

/** Spotting the rule, with the words played from the reader's recordings. */
@Composable
fun SpotScreen(spots: List<Spot>, onBack: () -> Unit) {
    val clip = rememberClip()
    // Each word is played as it comes up, where it can be.
    SpotContent(spots, onPlay = { spot -> spot.sound?.let { clip.play(it, spot.fromMs, spot.untilMs) } }, onBack = onBack)
}

/**
 * Echoing a reciter: a verse from one of the reader's recordings is played, the reader
 * recites it, and is corrected as for any verse; and for every madd longer than the
 * natural one, the counts the reciter held it for stand beside the reader's own. The
 * reciter's counts are had by listening to that stretch of the recording with the same
 * model that listens to the reader.
 */
@Composable
fun EchoScreen(echoes: List<Echo>, verseOf: suspend (VerseRef) -> Verse?, fetching: Float?, onWant: (pressed: Boolean) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var at by remember(echoes) { mutableIntStateOf(0) }
    var verse by remember { mutableStateOf<Verse?>(null) }
    var theirs by remember { mutableStateOf<List<WordRecited>?>(null) }
    val reciter = rememberReciter()
    val clip = rememberClip()
    val store = remember { TajweedStore(context) }
    val engine = remember { EngineStore(context) }
    val ready = remember(fetching) { store.isReady && engine.isReady }
    LaunchedEffect(Unit) { if (!ready) onWant(false) }
    val echo = echoes.getOrNull(at)
    if (echo == null) {
        DrillOver(stringResource(R.string.practice_echo_title), emptyList(), emptyMap(), onAgain = {}, onBack = onBack)
        return
    }
    LaunchedEffect(echoes, at, ready) {
        verse = null
        theirs = null
        reciter.rest()
        verse = verseOf(VerseRef(echo.surah, echo.verse))
        // The reciter is heard out first, so the reader hears how it should go.
        clip.play(echo.sound, echo.fromMs, echo.untilMs)
        if (ready) theirs = withContext(Dispatchers.Default) { runCatching { heardIn(context, echo) }.getOrNull() }
    }
    val surah = SURAHS[echo.surah - 1]
    val state = reciter.state
    ReciteContent(
        surah, echo.verse, verse,
        state = if (!ready && fetching != null && state == Reciting.Idle) Reciting.Fetching(R.string.recite_getting_model, fetching) else state,
        needsModel = !ready,
        onMic = {
            clip.stop()
            reciter.toggle(echo.surah, echo.verse)
        },
        onFetch = { onWant(true) },
        onPrevious = { if (at > 0 && !reciter.listening) at-- },
        onNext = { if (at < echoes.lastIndex && !reciter.listening) at++ },
        onBack = onBack,
        reached = reciter.reached,
        title = stringResource(R.string.practice_echo_title),
        subtitle = stringResource(R.string.drill_progress, at + 1, echoes.size) + " · " + stringResource(R.string.recite_verse, surahName(surah), echo.verse),
        how = stringResource(R.string.echo_how),
        verdict = {
            ThemeButton(stringResource(R.string.echo_play), { clip.play(echo.sound, echo.fromMs, echo.untilMs) }, icon = AppIcons.Sound)
            (state as? Reciting.Heard)?.let { MaddsBeside(verse, theirs, it.words) }
        },
        canPrevious = at > 0, canNext = at < echoes.lastIndex,
    )
}

/** The madds longer than the natural one, word by word: the counts the reciter held each for, and the reader's. */
@Composable
fun MaddsBeside(verse: Verse?, theirs: List<WordRecited>?, mine: List<WordRecited>) {
    val colors = MaterialTheme.colorScheme
    val words = verse?.words.orEmpty()
    val reciter = byWord(theirs, words.size)
    val reader = byWord(mine, words.size)
    val held = words.indices.filter { reciter[it]?.madds?.isNotEmpty() == true || reader[it]?.madds?.isNotEmpty() == true }
    Column(Modifier.fillMaxWidth().testTag("madds-beside"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(if (held.isEmpty()) R.string.echo_none_heard else R.string.echo_madds), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        for (w in held) {
            val them = reciter[w]?.madds?.maxOfOrNull { it.counts }
            val me = reader[w]?.madds?.maxOfOrNull { it.counts }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(words[w].arabic, Modifier.width(110.dp), fontFamily = QuranFont, fontSize = 22.sp, color = colors.onSurface)
                if (them != null) Text(stringResource(R.string.echo_reciter, them), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (me != null) Text(stringResource(R.string.echo_you, me), style = MaterialTheme.typography.bodyMedium, color = if (them != null && me != them) colors.error else colors.primary)
            }
        }
    }
}

/** A verse of a recording as the tajweed model hears it, a word at a time: for the lengths its reciter holds the madds. */
private fun heardIn(context: android.content.Context, echo: Echo): List<WordRecited>? {
    val forms = context.assets.open(REFERENCE).use { TajweedReference.surah(it, echo.surah) }.getOrNull(echo.verse - 1) ?: return null
    val from = echo.fromMs.toLong() * Mel.SAMPLE_RATE / 1000
    val until = minOf(echo.untilMs.toLong() * Mel.SAMPLE_RATE / 1000, from + LONGEST_ECHO * Mel.SAMPLE_RATE)
    val sound = FloatArray((until - from).toInt())
    AudioDecoder(echo.sound).use { decoder ->
        decoder.seekTo(from)
        while (true) {
            val block = decoder.read() ?: break
            // The block just read ends where the next one starts.
            val start = decoder.position - block.size
            for (i in block.indices) {
                val at = start + i - from
                if (at in 0 until sound.size) sound[at.toInt()] = block[i]
            }
            if (decoder.position >= until) break
        }
    }
    val script = Ears.get(context).hear(sound)
    if (script.symbols.length < 2) return null
    return Corrections.of(Expected.recited(forms, script.symbols, silences = Silences.among(script, sound)), script)
}

/** The longest stretch of a recording listened to for an echo, in seconds: what the model takes at once. */
private const val LONGEST_ECHO = 28

/** Plays a stretch of a recording on the phone, apart from the player the recitations go through: a word, or a verse. */
class Clip {
    private var player: MediaPlayer? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val end = Runnable { stop() }

    /** Plays [file] from [fromMs] until [untilMs]; whatever was playing stops. */
    fun play(file: File, fromMs: Int, untilMs: Int) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(file.path)
                setOnPreparedListener {
                    it.seekTo(fromMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                    it.start()
                    handler.postDelayed(end, (untilMs - fromMs).coerceAtLeast(200).toLong())
                }
                prepareAsync()
            }
        }
    }

    fun stop() {
        handler.removeCallbacks(end)
        player?.let { runCatching { it.release() } }
        player = null
    }
}

/** A [Clip] that falls silent when the screen that made it goes. */
@Composable
fun rememberClip(): Clip {
    val clip = remember { Clip() }
    DisposableEffect(Unit) { onDispose { clip.stop() } }
    return clip
}
