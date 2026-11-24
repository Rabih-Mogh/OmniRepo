package app.recitation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.recitation.R
import app.recitation.data.Summary
import java.time.format.TextStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * The days listened on in a row, as a plant: a day of listening is a day of growth. It is
 * a seed in the soil before the first day, a sprout on the first, and grows a pair of
 * leaves at a time (at three days, at a week), flowers at a fortnight and bears fruit at
 * a month. On a day not yet listened on it is grey and its leaves hang, until that day's
 * minute is in; a day missed, and it is a seed again.
 */
@Composable
fun Plant(
    days: Int,
    watered: Boolean,
    modifier: Modifier = Modifier,
    leaf: Color = MaterialTheme.colorScheme.primary,
    bloom: Color = LocalHighlight.current,
    soil: Color = MaterialTheme.colorScheme.outline,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ground = h * 0.9f
        drawArc(soil, 180f, 180f, useCenter = true, topLeft = Offset(w * 0.2f, ground - h * 0.08f), size = Size(w * 0.6f, h * 0.16f))
        val stage = stageOf(days)
        if (stage == 0) {
            drawCircle(soil, w * 0.05f, Offset(w / 2, ground - h * 0.12f))
            return@Canvas
        }
        val ink = if (watered) leaf else soil
        // The stem: from the soil up, leaning a little, taller with every stage.
        val foot = Offset(w / 2, ground - h * 0.06f)
        val top = Offset(w / 2 + w * 0.04f, foot.y - h * floatArrayOf(0f, 0.22f, 0.38f, 0.52f, 0.6f, 0.66f)[stage])
        val bend = Offset(w / 2 - w * 0.06f, (foot.y + top.y) / 2)
        fun along(t: Float) = Offset(
            (1 - t) * (1 - t) * foot.x + 2 * (1 - t) * t * bend.x + t * t * top.x,
            (1 - t) * (1 - t) * foot.y + 2 * (1 - t) * t * bend.y + t * t * top.y,
        )
        drawPath(Path().apply { moveTo(foot.x, foot.y); quadraticTo(bend.x, bend.y, top.x, top.y) }, ink, style = Stroke(w * 0.05f, cap = StrokeCap.Round))
        // Leaves in pairs up the stem, the lower the larger; they hang on a day not yet listened on.
        val pairs = intArrayOf(0, 1, 2, 3, 3, 4)[stage]
        val spread = if (watered) 58f else 112f
        for (i in 0 until pairs) {
            val at = along((i + 0.7f) / (pairs + 0.4f))
            val length = w * (0.3f - 0.03f * i) * if (stage == 1) 0.8f else 1f
            leaf(ink, at, -spread, length)
            leaf(ink, at, spread, length)
        }
        if (stage < 4) {
            // Still growing: a young leaf at the tip.
            leaf(ink, top, if (watered) 8f else 40f, w * 0.2f)
        } else {
            // In flower: five petals round a heart.
            for (k in 0 until 5) {
                val a = Math.toRadians(k * 72.0 - 90).toFloat()
                drawCircle(if (watered) bloom else soil, w * 0.075f, Offset(top.x + cos(a) * w * 0.085f, top.y + sin(a) * w * 0.085f))
            }
            drawCircle(ink, w * 0.045f, top)
        }
        // In fruit: one under each of the upper leaves.
        if (stage == 5) {
            for ((t, side) in listOf(0.5f to -1f, 0.68f to 1f, 0.82f to -1f)) {
                val at = along(t)
                drawCircle(if (watered) bloom else soil, w * 0.05f, Offset(at.x + side * w * 0.2f, at.y + h * 0.05f))
            }
        }
    }
}

/** How far grown the plant is after [days] in a row, 0 (a seed) to 5 (in fruit). */
internal fun stageOf(days: Int): Int = when {
    days <= 0 -> 0
    days < 3 -> 1
    days < 7 -> 2
    days < 14 -> 3
    days < 30 -> 4
    else -> 5
}

/** A leaf from [base], [turned] degrees from straight up (to the right if positive), [length] long. */
private fun DrawScope.leaf(color: Color, base: Offset, turned: Float, length: Float) {
    val a = Math.toRadians(turned.toDouble()).toFloat()
    val tip = Offset(base.x + sin(a) * length, base.y - cos(a) * length)
    val middle = Offset((base.x + tip.x) / 2, (base.y + tip.y) / 2)
    val across = Offset(cos(a) * length * 0.3f, sin(a) * length * 0.3f)
    drawPath(
        Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(middle.x + across.x, middle.y + across.y, tip.x, tip.y)
            quadraticTo(middle.x - across.x, middle.y - across.y, base.x, base.y)
            close()
        },
        color,
    )
}

/**
 * The run of days in the first screen's top bar: the plant, small, and the number of days.
 * A tap opens the whole of it.
 */
@Composable
fun StreakChip(summary: Summary, modifier: Modifier = Modifier) {
    val band = LocalBanner.current
    val says = pluralStringResource(R.plurals.stats_streak, summary.streak, summary.streak)
    var open by remember { mutableStateOf(false) }
    Row(
        modifier.clip(CircleShape).clickable(role = Role.Button, onClickLabel = says) { open = true }.padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("streak").clearAndSetSemantics { contentDescription = says },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Plant(summary.streak, summary.todayDone, Modifier.size(28.dp), leaf = band.content, bloom = band.accent, soil = band.quiet)
        Text(stringResource(R.string.number, summary.streak), style = MaterialTheme.typography.labelLarge, color = if (summary.todayDone) band.content else band.quiet)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            text = { Streak(summary) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

/** The run of days as a card, for the Stats tab. */
@Composable
fun StreakCard(summary: Summary, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Box(Modifier.padding(16.dp)) { Streak(summary) }
    }
}

/**
 * The whole of it: the plant as it stands, how many days in a row, what today still asks
 * for, the longest run so far, and the last seven days, each a dot that is filled if it
 * was listened on.
 */
@Composable
private fun Streak(summary: Summary) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxWidth().testTag("streak-card"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Plant(summary.streak, summary.todayDone, Modifier.size(104.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pluralStringResource(R.plurals.stats_streak, summary.streak, summary.streak), style = MaterialTheme.typography.titleLarge, color = colors.primary)
                Text(
                    stringResource(
                        when {
                            summary.todayDone -> R.string.streak_today_done
                            summary.streak > 0 -> R.string.streak_today_left
                            else -> R.string.streak_none
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
                )
                if (summary.best > summary.streak) {
                    Text(pluralStringResource(R.plurals.streak_best, summary.best, summary.best), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for ((i, day) in summary.week.withIndex()) {
                val done = day.second >= Summary.A_DAYS_LISTENING
                val today = i == summary.week.lastIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.size(DAY).clip(CircleShape)
                            .then(if (done) Modifier.background(colors.primary) else Modifier.border(if (today) 2.dp else 1.dp, if (today) colors.primary else colors.outlineVariant, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Canvas(Modifier.size(DAY * 0.6f)) { leaf(colors.onPrimary, Offset(size.width / 2, size.height * 0.95f), 0f, size.height * 0.9f) }
                    }
                    Text(
                        day.first.dayOfWeek.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelSmall,
                        color = if (today) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val DAY: Dp = 30.dp
