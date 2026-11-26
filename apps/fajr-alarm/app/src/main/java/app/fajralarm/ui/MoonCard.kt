package app.fajralarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fajralarm.R
import app.fajralarm.core.Moon
import app.fajralarm.core.MoonPhase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tonight's moon: its phase drawn as it looks, how much of it is lit, the day of its
 * cycle, and when the next four phases come. Worked out on the phone. [latitude]
 * decides which way round it is drawn: south of the equator the moon looks mirrored.
 */
@Composable
internal fun MoonCard(latitude: Double?, tick: Int) {
    val now = remember(tick) { Instant.now() }
    val phase = remember(now) { Moon.phaseAt(now) }
    val coming = remember(now) { Moon.nextPrincipal(now) }
    val southern = (latitude ?: 0.0) < 0
    val dateFormat = remember {
        val locale = Locale.getDefault()
        DateTimeFormatter.ofPattern("d MMM", locale).withDecimalStyle(DecimalStyle.of(locale)).withZone(ZoneId.systemDefault())
    }

    Section(stringResource(R.string.section_moon)) {
        val name = stringResource(phase.name.label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MoonDisc(phase.elongation, southern, 56.dp, stringResource(R.string.moon_picture, name))
            Column {
                Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.moon_lit, Math.round(phase.illumination * 100).toInt()) + " · " +
                        stringResource(R.string.moon_day, phase.ageDays.toInt() + 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            coming.forEach { (phaseName, at) ->
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MoonDisc(phaseName.ordinal * 45.0, southern, 28.dp, null)
                    Text(
                        stringResource(phaseName.label),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        dateFormat.format(at),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The moon with [elongation] degrees between it and the sun: 0 new, 90 first quarter,
 * 180 full. Seen from north of the equator it fills from the right and empties to the left.
 */
@Composable
private fun MoonDisc(elongation: Double, southern: Boolean, size: Dp, description: String?) {
    val dark = MaterialTheme.colorScheme.surfaceContainerHighest
    val lit = Color(0xFFF4EFD8)
    val rim = MaterialTheme.colorScheme.outline
    Canvas(
        Modifier
            .size(size)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        val radius = this.size.minDimension / 2
        val waxing = elongation < 180.0
        // How far the edge of the shadow sits from the middle: at the lit limb when new,
        // through the middle at the quarters, at the dark limb when full.
        val edge = cos(elongation * PI / 180).toFloat()
        drawCircle(dark, radius)
        // Drawn for a waxing moon in the north, lit from the right, and mirrored for the other cases.
        scale(scaleX = if (waxing != southern) 1f else -1f, scaleY = 1f) {
            val path = Path().apply {
                moveTo(center.x, center.y - radius)
                // Down the right-hand limb.
                for (step in 0..36) {
                    val angle = -PI / 2 + PI * step / 36
                    lineTo(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                }
                // Back up along the edge of the shadow.
                for (step in 0..36) {
                    val angle = PI / 2 - PI * step / 36
                    lineTo(center.x + radius * edge * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                }
                close()
            }
            drawPath(path, lit)
        }
        drawCircle(rim, radius, center = Offset(center.x, center.y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
}
