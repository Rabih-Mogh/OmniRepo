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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
            val xs = remember(visual) { (0..POINTS).map { visual.from + (visual.to - visual.from) * it / POINTS } }
            val ys = remember(visual) { xs.map { visual.plot.at(it) } }
            val others = remember(visual) { visual.versus?.let { other -> xs.map { other.at(it) } } }
            val measurer = rememberTextMeasurer()
            val words = MaterialTheme.typography.labelLarge
            Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(if (compact) 120.dp else 160.dp)) {
                    // Two curves share one scale, so that one can be seen to pull away from the other.
                    val low = minOf(ys.min(), others?.min() ?: ys.min())
                    val high = maxOf(ys.max(), others?.max() ?: ys.max())
                    val span = (high - low).takeIf { it > 1e-12 } ?: 1.0
                    val edge = 6.dp.toPx()
                    fun place(x: Double, y: Double) = Offset(
                        edge + (size.width - 2 * edge) * ((x - visual.from) / (visual.to - visual.from)).toFloat(),
                        (size.height - edge - (size.height - 2 * edge) * ((y - low) / span)).toFloat(),
                    )
                    // The line where the curve is zero, when that is in the picture; the floor otherwise.
                    val floor = if (low < 0 && high > 0) (size.height - edge - (size.height - 2 * edge) * (-low / span)).toFloat() else size.height - edge / 2
                    drawLine(ink.copy(alpha = 0.3f), Offset(edge, floor), Offset(size.width - edge, floor), 1.dp.toPx())
                    val upTo = (p * POINTS).toInt()
                    fun drawn(values: List<Double>) = Path().apply {
                        moveTo(place(xs[0], values[0]).x, place(xs[0], values[0]).y)
                        for (i in 1..upTo) place(xs[i], values[i]).let { lineTo(it.x, it.y) }
                    }
                    if (upTo > 0) {
                        others?.let { drawPath(drawn(it), ink.copy(alpha = 0.45f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                        drawPath(drawn(ys), glow, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawCircle(glow, 5.dp.toPx(), place(xs[upTo], ys[upTo]))
                    }
                    visual.mark?.let { mark ->
                        // The mark comes up as the pen passes it.
                        val passed = (mark.at - visual.from) / (visual.to - visual.from) * POINTS
                        val shown = ((p * (POINTS + 8) - passed) / 8).toFloat().coerceIn(0f, 1f)
                        if (shown > 0f) {
                            val spot = place(mark.at, visual.plot.at(mark.at))
                            drawCircle(glow.copy(alpha = 0.3f * shown), 11.dp.toPx(), spot)
                            drawCircle(glow.copy(alpha = shown), 6.dp.toPx(), spot)
                            val label = measurer.measure(mark.label, words)
                            val above = spot.y - 12.dp.toPx() - label.size.height
                            // The words keep to the side the curve leaves clear: above a climbing curve that is the left, below it the right.
                            val nudge = (visual.to - visual.from) / POINTS
                            val climbs = place(mark.at + nudge, visual.plot.at(mark.at + nudge)).y < place(mark.at - nudge, visual.plot.at(mark.at - nudge)).y
                            val left = if (climbs == (above >= 0f)) spot.x + 8.dp.toPx() - label.size.width else spot.x - 8.dp.toPx()
                            drawText(
                                label,
                                ink,
                                Offset(left.coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)), if (above >= 0f) above else spot.y + 12.dp.toPx()),
                                shown,
                            )
                        }
                    }
                }
                if (visual.names.size == 2) {
                    Row(Modifier.padding(top = 6.dp), Arrangement.spacedBy(16.dp), Alignment.CenterVertically) {
                        for ((name, colour) in visual.names.zip(listOf(glow, ink.copy(alpha = 0.45f)))) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(14.dp, 3.dp).clip(RoundedCornerShape(50)).background(colour))
                                Text(name, color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        is Visual.Line -> {
            val measurer = rememberTextMeasurer()
            val ends = MaterialTheme.typography.labelMedium
            val words = MaterialTheme.typography.labelLarge
            // Left to right along the line, so that neighbours take turns above and below it.
            val order = remember(visual) { visual.points.indices.sortedBy { visual.points[it].at } }
            Canvas(modifier.fillMaxWidth().height(if (compact) 84.dp else 96.dp)) {
                val first = measurer.measure(plain(visual.from), ends)
                val last = measurer.measure(plain(visual.to), ends)
                val y = size.height / 2
                val left = first.size.width + 8.dp.toPx()
                val right = size.width - last.size.width - 8.dp.toPx()
                fun x(value: Double) = left + (right - left) * ((value - visual.from) / (visual.to - visual.from)).toFloat()
                drawText(first, ink.copy(alpha = 0.6f), Offset(0f, y - first.size.height / 2f))
                drawText(last, ink.copy(alpha = 0.6f), Offset(size.width - last.size.width, y - last.size.height / 2f))
                drawLine(ink.copy(alpha = 0.3f), Offset(left, y), Offset(right, y), 4.dp.toPx(), StrokeCap.Round)
                order.forEachIndexed { turn, i ->
                    // The points land one after another.
                    val shown = (progress * (order.size + 0.5f) - turn).coerceIn(0f, 1f)
                    if (shown > 0f) {
                        val point = visual.points[i]
                        val colour = if (visual.highlight == null || visual.highlight == i) glow else ink.copy(alpha = 0.7f)
                        val centre = Offset(x(point.at), y)
                        drawCircle(colour, 7.dp.toPx() * shown, centre)
                        val label = measurer.measure(point.label, words)
                        val start = (centre.x - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
                        drawText(label, colour, Offset(start, if (turn % 2 == 0) y - 14.dp.toPx() - label.size.height else y + 14.dp.toPx()), shown)
                    }
                }
            }
        }

        is Visual.Share -> {
            val whole = visual.values.sum()
            // The part picked out is bright; the others are in fainter and fainter ink.
            val colours = remember(visual, ink, glow) {
                var faint = 0
                visual.values.indices.map { if (it == visual.highlight) glow else ink.copy(alpha = SHADES[faint++ % SHADES.size]) }
            }
            Row(modifier.fillMaxWidth(), Arrangement.spacedBy(18.dp), Alignment.CenterVertically) {
                Box(Modifier.size(if (compact) 104.dp else 128.dp), Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val width = (if (compact) 15.dp else 18.dp).toPx()
                        // The ring is swept round once from the top, part after part.
                        var from = 0f
                        visual.values.forEachIndexed { i, value ->
                            val sweep = (value / whole * 360).toFloat()
                            val drawn = (360f * p - from).coerceIn(0f, sweep)
                            if (drawn > 0f) {
                                drawArc(colours[i], from - 90f, drawn, false, Offset(width / 2, width / 2), Size(size.width - width, size.height - width), style = Stroke(width))
                            }
                            from += sweep
                        }
                    }
                    Text(
                        visual.percent(visual.highlight),
                        color = glow.copy(alpha = p),
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(Modifier.weight(1f), Arrangement.spacedBy(6.dp)) {
                    visual.labels.forEachIndexed { i, label ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(colours[i]))
                            Text(label, Modifier.weight(1f), color = ink.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(visual.percent(i), color = ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        is Visual.Grid -> Canvas(modifier.fillMaxWidth().height(minOf(if (compact) 110 else 150, visual.rows * 44).dp)) {
            val cell = minOf(size.width / visual.columns, size.height / visual.rows)
            val left = (size.width - cell * visual.columns) / 2
            val top = (size.height - cell * visual.rows) / 2
            val gap = 2.dp.toPx()
            val corner = CornerRadius(4.dp.toPx())
            for (i in 0 until visual.rows * visual.columns) {
                val at = Offset(left + cell * (i % visual.columns) + gap, top + cell * (i / visual.columns) + gap)
                val square = Size(cell - 2 * gap, cell - 2 * gap)
                // The lit squares come on one after another.
                val lit = if (i < visual.lit) (progress * (visual.lit + 1) - i).coerceIn(0f, 1f) else 0f
                drawRoundRect(ink.copy(alpha = 0.2f), at, square, corner)
                if (lit > 0f) drawRoundRect(glow.copy(alpha = lit), at, square, corner)
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

// How strong the ink is in each part of a share that isn't the one picked out.
private val SHADES = listOf(0.5f, 0.3f, 0.18f, 0.1f)
