package app.accentcoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.accentcoach.core.Streak
import java.time.format.TextStyle
import java.util.Locale

/**
 * The streak: how many days in a row the day's recordings were done, with today's count
 * as a ring that closes at [Streak.GOAL], and the last seven days as dots.
 */
@Composable
fun StreakCard(streak: Streak) {
    val title = when (streak.days) {
        0 -> "No streak yet"
        1 -> "1 day in a row"
        else -> "${streak.days} days in a row"
    }
    val left = Streak.GOAL - streak.today
    val note = when {
        streak.todayDone && streak.best > streak.days -> "Today's ${Streak.GOAL} recordings are done. Longest: ${streak.best} days."
        streak.todayDone -> "Today's ${Streak.GOAL} recordings are done."
        streak.days > 0 -> "$left more today to keep it going."
        else -> "$left more today to start one."
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val track = MaterialTheme.colorScheme.outlineVariant
            val done = MaterialTheme.colorScheme.secondary
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp).clearAndSetSemantics {
                    contentDescription = "${streak.today.coerceAtMost(Streak.GOAL)} of ${Streak.GOAL} recordings today"
                },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val width = 5.dp.toPx()
                    val topLeft = Offset(width / 2, width / 2)
                    val ring = Size(size.width - width, size.height - width)
                    drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = ring, style = Stroke(width))
                    val part = (streak.today.toFloat() / Streak.GOAL).coerceIn(0f, 1f)
                    if (part > 0f) drawArc(done, -90f, 360f * part, useCenter = false, topLeft = topLeft, size = ring, style = Stroke(width, cap = StrokeCap.Round))
                }
                Text("${streak.days}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Week(streak)
            }
        }
    }
}

/** The last seven days, oldest first: filled where the day's recordings were done, tinted where some were. */
@Composable
private fun Week(streak: Streak) {
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((day, count) in streak.week) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape).background(
                        when {
                            count >= Streak.GOAL -> MaterialTheme.colorScheme.secondary
                            count > 0 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                )
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
