package app.recitation.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recitation.R
import app.recitation.core.SURAHS
import app.recitation.prepare.PrepareWorker
import app.recitation.prepare.Step
import java.util.Locale

/*
 * What the app is at is shown in pictures, not told in words: each kind of work has an
 * icon, a ring round it fills as the work goes, and the surah it is for stands beside it.
 * The words are still there for a screen reader, and for anyone who taps the picture to
 * ask what it means.
 */

/** What the app is at, handed down to every screen, so each can show it in its top bar. Null when nothing is being done or waited for. */
class Doing(val status: Status, val actions: StatusActions)

val LocalDoing = compositionLocalOf<Doing?> { null }

/**
 * A kind of work as a picture: its [icon], a smaller one at its corner where two kinds
 * share an icon ([badge]), and how far along it is. A [fraction] of null is work that
 * can't say how far it is, whose ring turns; [still] is a state of waiting, whose ring
 * neither fills nor turns.
 */
class Picture(val icon: ImageVector, val badge: ImageVector? = null, val fraction: Float? = null, val still: Boolean = false)

/** The picture for each step of getting a recording ready. */
fun Step.picture(fraction: Float? = null): Picture = when (this) {
    Step.DOWNLOAD -> Picture(AppIcons.Download, fraction = fraction)
    Step.VERSES -> Picture(AppIcons.Book)
    Step.MODEL_DOWNLOAD -> Picture(AppIcons.Model, AppIcons.Download, fraction)
    Step.MODEL_CHECK -> Picture(AppIcons.Model, AppIcons.Check, fraction)
    Step.ENGINE -> Picture(AppIcons.Engine, AppIcons.Download, fraction)
    Step.TUNE -> Picture(AppIcons.Tune, fraction = fraction)
    Step.LISTEN -> Picture(AppIcons.Ear, fraction = fraction)
    Step.ALIGN -> Picture(AppIcons.Match)
    Step.RETRY -> Picture(AppIcons.CloudOff, AppIcons.Retry)
    Step.TAJWEED -> Picture(AppIcons.Mic, AppIcons.Download, fraction)
}

fun Status.picture(): Picture = when (this) {
    is Status.Working -> progress.step.picture(progress.fraction)
    is Status.ModelBlocked -> Picture(AppIcons.Model, AppIcons.Warning, still = true)
    Status.Paused -> Picture(AppIcons.Pause, still = true)
    Status.WaitingForNetwork -> Picture(AppIcons.CloudOff, still = true)
    Status.Refused -> Picture(AppIcons.Blocked, AppIcons.Hourglass, still = true)
    Status.Starting -> Picture(AppIcons.Hourglass)
}

/** What a status's picture means, in words: for a screen reader, and for whoever taps the picture to ask. */
@Composable
fun Status.says(): String = when (this) {
    is Status.Working -> stringResource(PrepareWorker.stepLabel(progress.step), (progress.fraction * 100).toInt())
    is Status.ModelBlocked -> stringResource(R.string.model_problem_title)
    Status.Paused -> stringResource(R.string.status_paused)
    Status.WaitingForNetwork -> stringResource(R.string.status_waiting)
    Status.Refused -> stringResource(R.string.status_refused)
    Status.Starting -> stringResource(R.string.status_starting)
}

/** How many times as fast as it plays a recording is being listened to, as a figure. */
fun times(speed: Float): String = String.format(Locale.getDefault(), "%.1f×", speed)

/**
 * A [picture] inside its ring, [size] across, in [color] on [ground]. The ring fills
 * clockwise from the top as the work goes; for work that can't say how far it is, a
 * quarter of it turns instead. [says] is what a screen reader is told.
 */
@Composable
fun WorkIcon(
    picture: Picture,
    says: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    ground: Color = MaterialTheme.colorScheme.background,
) {
    val turning = picture.fraction == null && !picture.still
    val turn = if (turning) {
        val forever = rememberInfiniteTransition(label = "work")
        forever.animateFloat(0f, 360f, infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    } else null
    Box(
        modifier.size(size).then(if (says != null) Modifier.clearAndSetSemantics { contentDescription = says } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val line = maxOf(size.toPx() / 13f, 1.5.dp.toPx())
            val inset = line / 2
            val round = Size(this.size.width - line, this.size.height - line)
            drawArc(color.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), round, style = Stroke(line))
            val stroke = Stroke(line, cap = StrokeCap.Round)
            when {
                turn != null -> drawArc(color, turn.value - 90f, 90f, false, Offset(inset, inset), round, style = stroke)
                picture.fraction != null && picture.fraction > 0f ->
                    drawArc(color, -90f, 360f * picture.fraction.coerceIn(0f, 1f), false, Offset(inset, inset), round, style = stroke)
            }
        }
        Icon(picture.icon, null, Modifier.size(size * 0.5f), tint = color)
        if (picture.badge != null) {
            // On a disc of the ground's colour, so it reads apart from the ring it sits on.
            Box(Modifier.align(Alignment.BottomEnd).size(size * 0.42f).background(ground, CircleShape), contentAlignment = Alignment.Center) {
                Icon(picture.badge, null, Modifier.size(size * 0.32f), tint = color)
            }
        }
    }
}

// The steps a recording goes through, in order, as the row of small pictures shows them.
private val STAGES = listOf(Step.DOWNLOAD, Step.VERSES, Step.LISTEN, Step.ALIGN)

// What is got once, for every recording, when the app is first set up: the speech model,
// the engine that runs it, and the trial of the fastest way to run it on this phone.
private val SETUP = listOf(Step.MODEL_DOWNLOAD, Step.ENGINE, Step.TUNE)

private fun Step.setsUp() = this == Step.MODEL_CHECK || this == Step.MODEL_DOWNLOAD || this == Step.ENGINE || this == Step.TUNE

private fun Step.stage(): Int = when (this) {
    Step.DOWNLOAD, Step.RETRY -> 0
    Step.VERSES -> 1
    Step.LISTEN -> 2
    Step.ALIGN -> 3
    Step.MODEL_CHECK, Step.MODEL_DOWNLOAD -> 0
    Step.ENGINE -> 1
    Step.TUNE -> 2
    // Not a step of any recording: the row of steps isn't shown for it.
    Step.TAJWEED -> -1
}

/**
 * The whole way a recording goes, as a row of small pictures: its sound, its verses, the
 * listening, and the matching of words. The speech model and the engine aren't among
 * them: they are got once, when the app is first set up, and only while that is being
 * done is the row theirs instead. Those behind [step] are in [color], the one being done
 * stands on a disc of it, and those to come are in [faint].
 */
@Composable
fun Stages(step: Step, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, onColor: Color = MaterialTheme.colorScheme.onPrimary, faint: Color = MaterialTheme.colorScheme.outline) {
    val at = step.stage()
    Row(modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (if (step.setsUp()) SETUP else STAGES).forEachIndexed { i, stage ->
            val icon = stage.picture().icon
            if (i == at) {
                Box(Modifier.size(22.dp).background(color, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(14.dp), tint = onColor) }
            } else {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(14.dp), tint = if (i < at) color else faint) }
            }
        }
    }
}

/**
 * What the background work is doing right now, in pictures: its icon in a ring, the surah
 * it is for, the row of steps, and the buttons to pause it or call it off. Tapping the
 * icon says in words what it means ([told] starts it off said).
 */
@Composable
fun StatusCard(status: Status, actions: StatusActions, told: Boolean = false, plain: Boolean = false) {
    if (status is Status.ModelBlocked) {
        ModelProblemCard(status.problem, actions.retryModel)
        return
    }
    val colors = MaterialTheme.colorScheme
    val says = status.says()
    var saying by remember { mutableStateOf(told) }
    Card(colors = CardDefaults.cardColors(containerColor = if (plain) Color.Transparent else colors.secondaryContainer)) {
        Column(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WorkIcon(
                    status.picture(), says, Modifier.clip(CircleShape).clickable(onClickLabel = says) { saying = !saying },
                    size = 48.dp, ground = if (plain) colors.surfaceContainer else colors.secondaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val working = status as? Status.Working
                    val surah = working?.surah?.let { SURAHS.getOrNull(it - 1) }
                    // Where: the surah the work is for. Work for every recording (the speech model, the engine) has none.
                    if (surah != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.number, surah.number), style = MaterialTheme.typography.titleMedium, color = colors.primary)
                            SurahTitle(surah, 24.sp, colors.onSurface)
                        }
                    } else if (working?.title != null) {
                        Text(working.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (working != null && working.progress.step != Step.TAJWEED) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stages(working.progress.step)
                            // How the listening compares with playing the recording: under 1 and it can't keep up with a reader.
                            if (working.progress.speed > 0f) Text(times(working.progress.speed), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
                when (status) {
                    Status.Paused -> IconButton(onClick = actions.resume) { Icon(AppIcons.Play, stringResource(R.string.resume), tint = colors.primary) }
                    else -> IconButton(onClick = actions.pause) { Icon(AppIcons.Pause, stringResource(R.string.pause_work), tint = colors.primary) }
                }
                IconButton(onClick = actions.cancel) { Icon(AppIcons.Close, stringResource(R.string.cancel), tint = colors.onSurfaceVariant) }
            }
            // The sound being downloaded beside that: its own ring, smaller, and the surah it is for.
            (status as? Status.Working)?.fetching?.let { beside ->
                val surah = SURAHS.getOrNull(beside.surah - 1)
                Row(Modifier.padding(start = 10.dp, top = 6.dp, end = 8.dp).testTag("fetching-beside"), verticalAlignment = Alignment.CenterVertically) {
                    WorkIcon(beside.picture(), beside.says(), size = 28.dp, ground = if (plain) colors.surfaceContainer else colors.secondaryContainer)
                    Spacer(Modifier.width(22.dp))
                    if (surah != null) {
                        Text(stringResource(R.string.number, surah.number), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        Spacer(Modifier.width(8.dp))
                        SurahTitle(surah, 18.sp, colors.onSurface)
                    } else if (beside.title != null) {
                        Text(beside.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (saying) Text(says, Modifier.padding(top = 8.dp, end = 8.dp), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * What the app is at, small enough for a top bar: the work's icon in its ring and the
 * number of the surah it is for. It is there on every screen for as long as anything is
 * being done or waited for. Tapping it opens the whole of it, with its buttons and its words.
 * In a surah ([apartFrom] is its number) it shows only what is being done elsewhere: the
 * work on that surah's own recording shows with its player.
 */
@Composable
fun DoingChip(modifier: Modifier = Modifier, size: Dp = 30.dp, apartFrom: Int = 0) {
    val doing = LocalDoing.current ?: return
    val band = LocalBanner.current
    val status = when (val all = doing.status) {
        is Status.Working -> if (apartFrom > 0 && all.surah == apartFrom) all.fetching?.takeIf { it.surah != apartFrom } ?: return else all
        else -> all
    }
    val says = status.says()
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(CircleShape).clickable(onClickLabel = says) { open = true }.padding(4.dp).semantics(mergeDescendants = true) { contentDescription = says },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WorkIcon(status.picture(), null, size = size, color = band.content, ground = band.background)
            val surah = (status as? Status.Working)?.surah ?: 0
            if (surah > 0) Text(stringResource(R.string.number, surah), style = MaterialTheme.typography.labelLarge, color = band.content)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Box(Modifier.width(320.dp)) { StatusCard(doing.status, doing.actions, told = true, plain = true) }
        }
    }
}
