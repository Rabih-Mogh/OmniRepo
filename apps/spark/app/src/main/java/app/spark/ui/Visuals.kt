package app.spark.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.spark.core.Visual
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/** A number as a person would write it: no ".0", at most two decimals, and commas only in large ones. */
fun plain(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    return when {
        rounded != Math.rint(rounded) -> rounded.toBigDecimal().stripTrailingZeros().toPlainString()
        abs(rounded) < 10_000 -> rounded.toLong().toString()
        else -> String.format(Locale.US, "%,d", rounded.toLong())
    }
}

/**
 * Draws [visual] as far along as [progress] (0 to 1) has it: a number partly counted, bars
 * partly grown, a curve partly drawn. [ink] is the colour of text here and [glow] what stands out.
 * A [compact] one is drawn smaller, for a reel with a lot to say.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualView(visual: Visual, progress: Float, ink: Color, glow: Color, modifier: Modifier = Modifier, compact: Boolean = false) {
    val p = FastOutSlowInEasing.transform(progress.coerceIn(0f, 1f))
    when (visual) {
        is Visual.Number -> {
            val shown = String.format(Locale.US, "%,.${visual.decimals}f", visual.value * p)
            // A unit made of letters stands apart from the number; a sign like % sits against it.
            val gap = if (visual.suffix.firstOrNull()?.isLetter() == true) " " else ""
            Text(
                visual.prefix + shown + gap + visual.suffix,
                modifier.fillMaxWidth(),
                color = glow,
                style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        is Visual.Bars -> {
            val most = visual.values.max()
            Row(modifier.fillMaxWidth().height(if (compact) 130.dp else 170.dp), Arrangement.spacedBy(10.dp), Alignment.Bottom) {
                visual.values.forEachIndexed { i, value ->
                    // Each bar starts a little after the one before it.
                    val grown = FastOutSlowInEasing.transform((progress * (visual.values.size + 1.5f) / 1.5f - i / 1.5f).coerceIn(0f, 1f))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(plain(value), color = ink.copy(alpha = grown), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                .height((if (compact) 78.dp else 118.dp) * (value / most).toFloat() * grown)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (visual.highlight == null || visual.highlight == i) glow else ink.copy(alpha = 0.32f)),
                        )
                        Text(
                            visual.labels[i],
                            Modifier.padding(top = 4.dp),
                            color = ink.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        is Visual.Dots -> {
            val columns = minOf(visual.total, maxOf(5, ceil(sqrt(visual.total * 2.2)).toInt()))
            val rows = ceil(visual.total / columns.toDouble()).toInt()
            Canvas(modifier.fillMaxWidth().height(minOf(if (compact) 110 else 150, rows * 40).dp)) {
                val cell = minOf(size.width / columns, size.height / rows)
                val left = (size.width - cell * columns) / 2
                val top = (size.height - cell * rows) / 2
                for (i in 0 until visual.total) {
                    val centre = Offset(left + cell * (i % columns + 0.5f), top + cell * (i / columns + 0.5f))
                    // The lit dots come on one after another.
                    val lit = if (i < visual.lit) (progress * (visual.lit + 1) - i).coerceIn(0f, 1f) else 0f
                    drawCircle(ink.copy(alpha = 0.25f), cell * 0.32f, centre)
                    if (lit > 0f) drawCircle(glow.copy(alpha = lit), cell * 0.36f, centre)
                }
            }
        }

        is Visual.Curve -> {
            val ys = remember(visual) { (0..POINTS).map { visual.plot.at(visual.from + (visual.to - visual.from) * it / POINTS) } }
            Canvas(modifier.fillMaxWidth().height(if (compact) 120.dp else 160.dp)) {
                val low = ys.min()
                val high = ys.max()
                val span = (high - low).takeIf { it > 1e-12 } ?: 1.0
                val edge = 6.dp.toPx()
                fun at(i: Int) = Offset(
                    edge + (size.width - 2 * edge) * i / POINTS,
                    (size.height - edge - (size.height - 2 * edge) * ((ys[i] - low) / span)).toFloat(),
                )
                // The line where the curve is zero, when that is in the picture; the floor otherwise.
                val floor = if (low < 0 && high > 0) (size.height - edge - (size.height - 2 * edge) * (-low / span)).toFloat() else size.height - edge / 2
                drawLine(ink.copy(alpha = 0.3f), Offset(edge, floor), Offset(size.width - edge, floor), 1.dp.toPx())
                val upTo = (p * POINTS).toInt()
                if (upTo > 0) {
                    val path = Path().apply {
                        moveTo(at(0).x, at(0).y)
                        for (i in 1..upTo) lineTo(at(i).x, at(i).y)
                    }
                    drawPath(path, glow, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(glow, 5.dp.toPx(), at(upTo))
                }
            }
        }

        // Side by side, and on to a new line when the row is full.
        is Visual.Chain -> FlowRow(modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), Arrangement.spacedBy(8.dp)) {
            visual.items.forEachIndexed { i, item ->
                val shown = (progress * (visual.items.size + 0.5f) - i).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (i > 0) Text("→", color = glow.copy(alpha = shown), style = MaterialTheme.typography.titleMedium)
                    Text(
                        item,
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(glow.copy(alpha = 0.18f * shown))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        color = ink.copy(alpha = shown),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private const val POINTS = 80
