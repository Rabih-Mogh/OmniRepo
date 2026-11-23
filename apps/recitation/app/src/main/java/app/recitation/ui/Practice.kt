package app.recitation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.recitation.R
import app.recitation.core.Pitch
import app.recitation.core.VoiceTake
import app.recitation.data.Settings
import app.recitation.speech.Mic
import java.util.Locale
import kotlin.math.ln
import kotlinx.coroutines.CancellationException

/**
 * The exercises for the voice. The first is one recitation teachers give; the other two
 * are general exercises for any voice. None of them teaches a melody: what they train is
 * the breath, a steady note, and knowing one's own range.
 */
enum class Exercise(val title: Int, val line: Int, val how: Int, val icon: ImageVector) {
    BREATH(R.string.voice_breath, R.string.voice_breath_line, R.string.voice_breath_how, AppIcons.Breath),
    STEADY(R.string.voice_steady, R.string.voice_steady_line, R.string.voice_steady_how, AppIcons.Steady),
    RANGE(R.string.voice_range, R.string.voice_range_line, R.string.voice_range_how, AppIcons.Span);

    /** What a go at it comes to as one number, to keep the best of: seconds, cents, or semitones. */
    fun measure(take: VoiceTake): Float = when (this) {
        BREATH -> take.longestBreath
        STEADY -> take.wavering
        RANGE -> take.span
    }

    /** Whether [a] is a better result than [b]: a longer breath, a steadier note, a wider range. */
    fun better(a: Float, b: Float): Boolean = if (this == STEADY) b == 0f || a < b else a > b
}

/** A figure with one place after the point, in the phone's own numerals. */
private fun oneDecimal(value: Float): String = String.format(Locale.getDefault(), "%.1f", value)

/** An exercise's result as it is shown. */
@Composable
private fun shown(exercise: Exercise, value: Float): String = when (exercise) {
    Exercise.BREATH -> stringResource(R.string.voice_seconds, oneDecimal(value))
    Exercise.STEADY -> stringResource(R.string.voice_cents, value.toInt())
    Exercise.RANGE -> stringResource(R.string.voice_semitones, oneDecimal(value))
}

/**
 * The first screen's Practise tab: reciting a verse to the phone to be corrected, and the
 * voice exercises, each with the best result so far ([bests]; 0 before the first go).
 */
@Composable
fun PracticeTab(bests: Map<Exercise, Float>, onRecite: () -> Unit, onExercise: (Exercise) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Mic, null, Modifier.size(24.dp), tint = colors.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.practice_recite_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.practice_recite_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                ThemeButton(stringResource(R.string.practice_recite_go), onRecite, Modifier.fillMaxWidth(), icon = AppIcons.Mic, filled = true)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.voice_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.voice_hadith), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.voice_condition), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                for (exercise in Exercise.entries) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onExercise(exercise) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(exercise.icon, null, Modifier.size(24.dp), tint = colors.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(exercise.title), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(exercise.line), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        val best = bests[exercise] ?: 0f
                        if (best > 0f) Text(shown(exercise, best), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }
            }
        }
        Text(stringResource(R.string.voice_sources), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(stringResource(R.string.voice_health), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/**
 * The line a voice draws as it is heard: its pitch at every look, the latest at the end
 * reading finishes on, high notes high. An octave is the same height wherever it is.
 * Where there was no voice the line breaks.
 */
@Composable
fun PitchLine(pitches: List<Float>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        val low = ln(LOWEST_SHOWN)
        val high = ln(HIGHEST_SHOWN)
        fun y(hz: Float) = size.height * (1f - ((ln(hz) - low) / (high - low)).coerceIn(0f, 1f))
        // Faint lines an octave apart, to judge height by.
        var octave = 110f
        while (octave < HIGHEST_SHOWN) {
            drawLine(colors.outlineVariant, Offset(0f, y(octave)), Offset(size.width, y(octave)), 1.dp.toPx())
            octave *= 2
        }
        val shown = pitches.takeLast(LOOKS_SHOWN)
        val step = size.width / LOOKS_SHOWN
        val path = Path()
        var drawing = false
        for ((i, hz) in shown.withIndex()) {
            val along = (LOOKS_SHOWN - shown.size + i) * step
            val x = if (rtl) size.width - along else along
            if (hz > 0f) {
                if (drawing) path.lineTo(x, y(hz)) else path.moveTo(x, y(hz))
                drawing = true
            } else drawing = false
        }
        drawPath(path, colors.primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        shown.lastOrNull()?.takeIf { it > 0f }?.let { drawCircle(colors.primary, 5.dp.toPx(), Offset(if (rtl) size.width - (LOOKS_SHOWN - 1) * step else (LOOKS_SHOWN - 1) * step, y(it))) }
    }
}

private const val LOWEST_SHOWN = 70f
private const val HIGHEST_SHOWN = 620f

/** About ten seconds of looks across the line. */
private const val LOOKS_SHOWN = 10 * Pitch.SAMPLE_RATE / Pitch.HOP

/**
 * One exercise: what to do, the line the voice draws while it is heard, the button that
 * starts and stops the listening, and afterwards what the go came to. [take] is the last
 * go, [best] the best so far; [problem] is said in place of a result when the microphone
 * couldn't be used.
 */
@Composable
fun ExerciseContent(
    exercise: Exercise,
    listening: Boolean,
    pitches: List<Float>,
    take: VoiceTake?,
    best: Float,
    problem: String?,
    onToggle: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Banner {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(exercise.title), Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                DoingChip()
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(exercise.how), style = MaterialTheme.typography.bodyLarge)
            PitchLine(pitches, Modifier.fillMaxWidth().height(170.dp).clip(MaterialTheme.shapes.medium).background(colors.surfaceContainerLow))
            when {
                problem != null -> Text(problem, style = MaterialTheme.typography.bodyMedium, color = colors.error)
                take != null && take.seconds < 0.5f -> Text(stringResource(R.string.voice_nothing_heard), style = MaterialTheme.typography.bodyMedium, color = colors.error)
                take != null -> Result(exercise, take)
            }
            if (best > 0f) Text(stringResource(R.string.voice_best, shown(exercise, best)), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Text(stringResource(R.string.voice_health), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        BottomBand {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) { MicButton(listening, onToggle) }
        }
    }
}

/** What a go at an exercise came to: its figure large, and a line saying how it stands. */
@Composable
private fun Result(exercise: Exercise, take: VoiceTake) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(shown(exercise, exercise.measure(take)), style = MaterialTheme.typography.displaySmall, color = colors.primary)
        when (exercise) {
            Exercise.BREATH -> {
                Text(stringResource(R.string.voice_longest_breath), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Text(stringResource(if (take.unevenness < EVEN_DB) R.string.voice_even else R.string.voice_uneven), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Exercise.STEADY -> {
                Text(
                    stringResource(if (take.wavering < VERY_STEADY_CENTS) R.string.voice_very_steady else if (take.wavering < STEADY_CENTS) R.string.voice_steady_ok else R.string.voice_wavers),
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                )
                Text(stringResource(R.string.voice_cents_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Exercise.RANGE -> RangeBar(take)
        }
    }
}

/** The reach of the voice as a bar from its lowest note to its highest, the low end named qarar and the high end jawab. */
@Composable
private fun RangeBar(take: VoiceTake) {
    val colors = MaterialTheme.colorScheme
    val (low, high) = take.range
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val a = ln(LOWEST_SHOWN)
            val b = ln(HIGHEST_SHOWN)
            // Low at the side reading starts from, as the names under it are.
            fun x(hz: Float) = (size.width * ((ln(hz) - a) / (b - a)).coerceIn(0f, 1f)).let { if (rtl) size.width - it else it }
            drawLine(colors.outlineVariant, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 4.dp.toPx(), StrokeCap.Round)
            if (low > 0f) drawLine(colors.primary, Offset(x(low), size.height / 2), Offset(x(high), size.height / 2), 8.dp.toPx(), StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.voice_low), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(stringResource(R.string.voice_high), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

private const val EVEN_DB = 3f
private const val VERY_STEADY_CENTS = 15f
private const val STEADY_CENTS = 30f

/** The round button that starts the listening and stops it: a microphone, or a square while it listens. */
@Composable
fun MicButton(listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val band = LocalBanner.current
    val label = stringResource(if (listening) R.string.voice_stop else R.string.voice_start)
    Box(
        modifier.size(68.dp).clip(CircleShape).background(band.accent).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (listening) AppIcons.Stop else AppIcons.Mic, null, Modifier.size(32.dp), tint = band.onAccent)
    }
}

/** How long an exercise listens for at the most, in seconds. */
private const val LONGEST_GO = 60

/**
 * An exercise at work: asks for the microphone the first time, reads the voice's pitch as
 * it comes for the line on the screen, and when the listening stops reckons the result
 * and keeps the best.
 */
@Composable
fun ExerciseScreen(exercise: Exercise, onBack: () -> Unit) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val pitches = remember { mutableStateListOf<Float>() }
    var take by remember { mutableStateOf<VoiceTake?>(null) }
    var best by remember { mutableFloatStateOf(Settings.voiceBest(context, exercise.name)) }
    val refused = stringResource(R.string.voice_no_mic)
    val busy = stringResource(R.string.voice_mic_busy)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listening = true else problem = refused
    }
    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        pitches.clear()
        take = null
        problem = null
        val loudness = ArrayList<Float>()
        // What has come from the microphone and not yet been looked at.
        var pending = FloatArray(0)
        try {
            Mic.sound().collect { block ->
                pending += block
                var at = 0
                while (at + Pitch.FRAME <= pending.size) {
                    pitches += Pitch.at(pending, at)
                    loudness += Pitch.loudness(pending, at, Pitch.FRAME)
                    at += Pitch.HOP
                }
                pending = pending.copyOfRange(at, pending.size)
                if (pitches.size * Pitch.HOP >= LONGEST_GO * Pitch.SAMPLE_RATE) listening = false
            }
        } catch (e: CancellationException) {
            // Stopped by the button: reckoned below.
        } catch (e: Mic.Unavailable) {
            problem = busy
        } finally {
            listening = false
            if (problem == null && pitches.isNotEmpty()) {
                val done = VoiceTake(VoiceTake.smoothed(pitches.toFloatArray()), loudness.toFloatArray())
                take = done
                val value = exercise.measure(done)
                if (done.seconds >= MIN_SECONDS && value > 0f && exercise.better(value, best)) {
                    best = value
                    Settings.setVoiceBest(context, exercise.name, value)
                }
            }
        }
    }
    ExerciseContent(
        exercise, listening, pitches, take, best, problem,
        onToggle = {
            if (listening) listening = false
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listening = true
            else ask.launch(Manifest.permission.RECORD_AUDIO)
        },
        onBack = onBack,
    )
}

/** A go shorter than this isn't kept as a best: too short to judge. */
private const val MIN_SECONDS = 2f
