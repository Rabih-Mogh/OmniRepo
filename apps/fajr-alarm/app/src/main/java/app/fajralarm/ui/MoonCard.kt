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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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

/** A cycle of the moon, to the nearest day: the ring is full after this many. */
private const val MOON_CYCLE_DAYS = 30

/**
 * Tonight's moon: its phase drawn as it looks inside a ring for the day of its cycle,
 * how much of it is lit, and when the next four phases come. Worked out on the phone. [latitude]
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
            // The ring round the moon is the day of its cycle; the words under the name say which.
            val day = stringResource(R.string.moon_day, phase.ageDays.toInt() + 1, MOON_CYCLE_DAYS)
            MoonDial(phase, southern, 96.dp, stringResource(R.string.moon_picture, name) + ", " + day)
            Column {
                Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.moon_lit, Math.round(phase.illumination * 100).toInt()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(day, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            coming.forEach { (phaseName, at) ->
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MoonDisc(phaseName.ordinal * 45.0, southern, 28.dp)
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
 * The moon inside a ring that fills through its cycle: empty at the new moon, half
 * round at the full moon, closed again at the next new one. Four notches mark the new
 * moon, the quarters and the full moon. The moon has its seas where it is big enough to show them.
 */
@Composable
private fun MoonDial(phase: MoonPhase, southern: Boolean, size: Dp, description: String) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val progress = MaterialTheme.colorScheme.primary
    val notch = MaterialTheme.colorScheme.surfaceContainer
    val colors = moonColors()
    Canvas(Modifier.size(size).semantics { contentDescription = description }) {
        val width = 5.dp.toPx()
        val ringRadius = (this.size.minDimension - width) / 2
        val box = Size(ringRadius * 2, ringRadius * 2)
        val corner = Offset(center.x - ringRadius, center.y - ringRadius)
        drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = corner, size = box, style = Stroke(width))
        // From the top, clockwise: the part of the cycle that has gone by.
        drawArc(
            progress,
            startAngle = -90f,
            sweepAngle = phase.elongation.toFloat(),
            useCenter = false,
            topLeft = corner,
            size = box,
            style = Stroke(width, cap = StrokeCap.Round),
        )
        for (quarter in 0..3) {
            val angle = -PI / 2 + quarter * PI / 2
            drawCircle(
                notch,
                radius = width / 4,
                center = Offset(center.x + ringRadius * cos(angle).toFloat(), center.y + ringRadius * sin(angle).toFloat()),
            )
        }
        drawMoon(ringRadius - width / 2 - 5.dp.toPx(), phase.elongation, southern, colors, seas = true)
    }
}

/**
 * The moon with [elongation] degrees between it and the sun: 0 new, 90 first quarter,
 * 180 full. Seen from north of the equator it fills from the right and empties to the left.
 */
@Composable
private fun MoonDisc(elongation: Double, southern: Boolean, size: Dp) {
    val colors = moonColors()
    Canvas(Modifier.size(size)) {
        drawMoon(this.size.minDimension / 2, elongation, southern, colors, seas = false)
    }
}

private class MoonColors(val dark: Color, val lit: Color, val sea: Color, val rim: Color)

@Composable
private fun moonColors() = MoonColors(
    dark = MaterialTheme.colorScheme.surfaceContainerHighest,
    lit = Color(0xFFF4EFD8),
    sea = Color(0xFFD9D2B4),
    rim = MaterialTheme.colorScheme.outline,
)

/** Draws the moon in the middle of the canvas. */
private fun DrawScope.drawMoon(radius: Float, elongation: Double, southern: Boolean, colors: MoonColors, seas: Boolean) {
    val waxing = elongation < 180.0
    // How far the edge of the shadow sits from the middle: at the lit limb when new,
    // through the middle at the quarters, at the dark limb when full.
    val edge = cos(elongation * PI / 180).toFloat()
    drawCircle(colors.dark, radius)
    // Drawn for a waxing moon in the north, lit from the right, and mirrored for the other cases.
    scale(scaleX = if (waxing != southern) 1f else -1f, scaleY = 1f) {
        val lit = Path().apply {
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
        drawPath(lit, colors.lit)
        if (seas) {
            // A few of the dark seas, only where the moon is lit.
            clipPath(lit) {
                for ((x, y, r) in SEAS) {
                    drawCircle(colors.sea, radius * r, Offset(center.x + radius * x, center.y + radius * y))
                }
            }
        }
    }
    drawCircle(colors.rim, radius, style = Stroke(1.dp.toPx()))
}

/** Where the seas are, as parts of the moon's radius: across, down, and how big. */
private val SEAS = listOf(
    Triple(-0.35f, -0.38f, 0.26f),
    Triple(0.18f, -0.42f, 0.20f),
    Triple(0.42f, -0.05f, 0.24f),
    Triple(-0.12f, 0.12f, 0.17f),
    Triple(-0.48f, 0.32f, 0.15f),
    Triple(0.22f, 0.45f, 0.12f),
)
