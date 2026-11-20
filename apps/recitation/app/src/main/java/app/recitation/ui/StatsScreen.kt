package app.recitation.ui

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.recitation.R
import app.recitation.core.SURAHS
import app.recitation.data.Space
import app.recitation.data.Summary
import java.time.LocalDate
import java.time.format.TextStyle

/** What the playlists hold, for the Stats tab: recordings ready out of all, and how long the ready ones run. */
class RecordingStats(val ready: Int, val all: Int, val readyMs: Long)

/** What has been saved, for the Stats tab. */
class SavedStats(val pins: Int, val collections: Int, val verses: Int)

/** "12 min" under an hour, "2 h 5 min" under ten, and whole hours ("12 h") from there on, where the minutes no longer matter. */
@Composable
fun timeSpent(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return when {
        minutes >= 600 -> stringResource(R.string.hours, (minutes + 30) / 60)
        minutes >= 60 -> stringResource(R.string.hours_minutes, minutes / 60, minutes % 60)
        else -> stringResource(R.string.minutes, minutes)
    }
}

/**
 * The fourth tab of the first screen: how much has been listened to and when, how much of
 * the Quran has been heard, what the playlists and the saved verses hold, and the room the
 * app takes on the phone ([space]; null while it is being measured).
 */
@Composable
fun StatsTab(summary: Summary, recordings: RecordingStats, saved: SavedStats, space: Space?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val allVerses = SURAHS.sumOf { it.verses }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Section(stringResource(R.string.stats_listening)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Figure(timeSpent(summary.today), stringResource(R.string.stats_today), Modifier.weight(1f))
                    Figure(timeSpent(summary.thisWeek), stringResource(R.string.stats_week), Modifier.weight(1f))
                    Figure(timeSpent(summary.allTime), stringResource(R.string.stats_all_time), Modifier.weight(1f))
                }
                WeekBars(summary.week, Modifier.fillMaxWidth().padding(top = 12.dp))
                Text(
                    pluralStringResource(R.plurals.stats_streak, summary.streak, summary.streak),
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                )
            }
        }
        item {
            Section(stringResource(R.string.stats_heard)) {
                Text(stringResource(R.string.stats_verses_heard, summary.versesHeard, allVerses), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { summary.versesHeard.toFloat() / allVerses },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = colors.primary, trackColor = colors.surfaceContainerHighest, drawStopIndicator = {},
                )
                Text(stringResource(R.string.stats_surahs_heard, summary.surahsHeard, SURAHS.size), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        item {
            Section(stringResource(R.string.stats_recordings)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Figure(stringResource(R.string.stats_of, recordings.ready, recordings.all), stringResource(R.string.stats_ready), Modifier.weight(1f))
                    Figure(timeSpent(recordings.readyMs), stringResource(R.string.stats_ready_length), Modifier.weight(1f))
                }
            }
        }
        item {
            Section(stringResource(R.string.tab_saved)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Figure(stringResource(R.string.number, saved.pins), stringResource(R.string.pins_title), Modifier.weight(1f))
                    Figure(stringResource(R.string.number, saved.collections), stringResource(R.string.collections_title), Modifier.weight(1f))
                    Figure(stringResource(R.string.number, saved.verses), stringResource(R.string.stats_kept), Modifier.weight(1f))
                }
            }
        }
        item {
            Section(stringResource(R.string.stats_space)) {
                if (space == null) {
                    Text(stringResource(R.string.stats_measuring), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                } else {
                    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
                    Line(stringResource(R.string.stats_space_recordings), size(space.recordings))
                    Line(stringResource(R.string.stats_space_engine), size(space.engine))
                    Line(stringResource(R.string.stats_space_model), if (space.model > 0) size(space.model) else stringResource(R.string.stats_space_model_borrowed))
                    Line(stringResource(R.string.stats_space_other), size(space.other))
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** One figure with what it counts under it. */
@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The last seven days' listening as bars, oldest first, today's in full colour and the
 * days before it paler. One measure in one hue: the bars stand on a faint line, with
 * the day's letter under each, and only today's is labelled with its time.
 */
@Composable
private fun WeekBars(week: List<Pair<LocalDate, Long>>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val most = week.maxOf { it.second }.coerceAtLeast(1L)
    // Said in words for a screen reader, since the bars themselves can't be read.
    val spoken = week.map { (day, ms) -> "${day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}: ${timeSpent(ms)}" }.joinToString(", ")
    val today = colors.primary
    val earlier = colors.primary.faded(0.4f, colors.surfaceContainerLow)
    Column(modifier.semantics { contentDescription = spoken }) {
        Canvas(Modifier.fillMaxWidth().height(88.dp)) {
            val slot = size.width / week.size
            val bar = minOf(slot - 2.dp.toPx(), 28.dp.toPx())
            val base = size.height - 1.dp.toPx()
            for ((i, entry) in week.withIndex()) {
                val height = (base - 4.dp.toPx()) * entry.second / most
                // A day with a little listening still shows: never thinner than a sliver.
                val shown = if (entry.second > 0) maxOf(height, 3.dp.toPx()) else 0f
                val column = if (rtl) week.lastIndex - i else i
                val left = slot * column + (slot - bar) / 2
                if (shown > 0f) {
                    // Rounded at the top only: the foot stands square on the line.
                    val corner = CornerRadius(minOf(4.dp.toPx(), shown / 2))
                    val shape = Path().apply { addRoundRect(RoundRect(Rect(Offset(left, base - shown), Size(bar, shown)), topLeft = corner, topRight = corner)) }
                    drawPath(shape, if (i == week.lastIndex) today else earlier)
                }
            }
            drawLine(colors.outlineVariant, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for ((i, entry) in week.withIndex()) {
                Text(
                    entry.first.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                    style = MaterialTheme.typography.labelSmall, color = if (i == week.lastIndex) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
    }
}
