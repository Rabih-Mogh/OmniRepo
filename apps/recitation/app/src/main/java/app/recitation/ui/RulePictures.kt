package app.recitation.ui

import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recitation.R
import app.recitation.core.Drills
import app.recitation.core.Fault
import app.recitation.core.Listened
import app.recitation.core.Measure
import app.recitation.core.Topic
import app.recitation.core.Went
import app.recitation.core.WordRecited
import app.recitation.data.Script
import app.recitation.data.Verse
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * A word of the Quran with some of its letters in another colour: the letters a rule is
 * about ([ranges], stretches of [text]), in the rule's colour ([ink]). The colour is
 * painted over the letters where they stand (see [Repaint]), so the word keeps its shape.
 */
@Composable
internal fun RuledWord(text: String, ranges: List<IntRange>, ink: Color, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, fontSize: TextUnit = 30.sp, lineHeight: TextUnit = 62.sp) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { fontSize.toPx() }
    val repaint = remember(text, sizePx, ranges.isEmpty()) { if (ranges.isEmpty()) null else Typefaces.of(context, Script.AMIRI)?.let { Repaint(text, it, sizePx) } }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        modifier.then(
            if (repaint == null) Modifier else Modifier.drawWithContent {
                drawContent()
                val placed = layout ?: return@drawWithContent
                val room = 12.dp.toPx()
                val box = RectF(-room, -room, size.width + room, size.height + room)
                drawIntoCanvas { canvas ->
                    for (range in ranges) repaint.draw(canvas.nativeCanvas, range.first, range.last + 1, placed.getLineRight(0), placed.firstBaseline, box, ink)
                }
            },
        ),
        fontFamily = QuranFont, fontSize = fontSize, lineHeight = lineHeight, color = color, onTextLayout = { layout = it },
    )
}

/**
 * One sound of an example, as it is said: its letters ([text]) and how many counts it
 * lasts. [focus] is the sound the rule is about; [nose] says it is made through the nose;
 * [more] are counts it may be held for besides (a madd of four or five, of two, four or
 * six); [written] is how the mushaf writes it where that is not how it is said; [bounce]
 * gives it the echo of a qalqalah.
 */
class Beat(val text: String, val counts: Float, val nose: Boolean = false, val focus: Boolean = false, val more: Int = 0, val written: String? = null, val bounce: Boolean = false, val counted: Boolean = focus && !bounce)

/**
 * An example ([words], as the mushaf writes it) laid out in time, a sound after another;
 * [label] says which case of the rule it shows, where the rule has more than one. [nose]
 * keeps the row for the nose even where nothing goes through it, to show that nothing does.
 */
class Strip(val words: String, val beats: List<Beat>, val label: Int? = null, val nose: Boolean = beats.any { it.nose })

/**
 * The examples that show [topic] in time: what is said, for how many counts, and what of
 * it goes through the nose. None for a stop or for letters, which are not a matter of time.
 */
fun stripsOf(topic: Topic): List<Strip> = when (topic) {
    Topic.GHUNNAH -> listOf(Strip("إِنَّ", listOf(Beat("إِ", 1f), Beat("نَّ", 2f, nose = true, focus = true))))
    Topic.IKHFA -> listOf(Strip("مِن قَبْلِ", listOf(Beat("مِـ", 1f), Beat("ن", 2f, nose = true, focus = true), Beat("قَبْـ", 2f), Beat("ـلِ", 1f))))
    Topic.IQLAB -> listOf(Strip("مِنۢ بَعْدِ", listOf(Beat("مِـ", 1f), Beat("م", 2f, nose = true, focus = true, written = "نۢ"), Beat("بَعْـ", 2f), Beat("ـدِ", 1f))))
    Topic.IDGHAM -> listOf(
        Strip("مَن يَقُولُ", listOf(Beat("مَـ", 1f), Beat("يَّـ", 2f, nose = true, focus = true, written = "ن يَـ"), Beat("ـقُولُ", 3f)), R.string.strip_idgham_with),
        Strip("مِن رَّبِّهِمْ", listOf(Beat("مِـ", 1f), Beat("رَّ", 1.6f, focus = true, written = "ن رَّ", counted = false), Beat("بِّهِمْ", 3f)), R.string.strip_idgham_without, nose = true),
    )
    Topic.QALQALAH -> listOf(
        Strip("يَدْخُلُونَ", listOf(Beat("يَـ", 1f), Beat("ـدْ", 1.5f, focus = true, bounce = true), Beat("خُلُونَ", 4f)), R.string.strip_qalqalah_mid),
        Strip("ٱلْفَلَقِ", listOf(Beat("ٱلْفَلَـ", 3.5f), Beat("ـقْ", 2f, focus = true, bounce = true)), R.string.strip_qalqalah_stop),
    )
    Topic.MADD_NATURAL -> listOf(Strip("قَالَ", listOf(Beat("قَـ", 1f), Beat("ـا", 2f, focus = true), Beat("لَ", 1f))))
    Topic.MADD_MUNFASIL -> listOf(Strip("بِمَآ أُنزِلَ", listOf(Beat("بِـ", 1f), Beat("ـمَـ", 1f), Beat("ـآ", 4f, focus = true, more = 1), Beat("أُنزِلَ", 3f))))
    Topic.MADD_MUTTASIL -> listOf(Strip("جَآءَ", listOf(Beat("جَـ", 1f), Beat("ـآ", 4f, focus = true, more = 1), Beat("ءَ", 1f))))
    Topic.MADD_LAZIM -> listOf(Strip("ٱلضَّآلِّينَ", listOf(Beat("ٱلضَّـ", 2f), Beat("ـآ", 6f, focus = true), Beat("لِّينَ", 3f))))
    Topic.MADD_ARID -> listOf(Strip("نَسْتَعِينُ", listOf(Beat("نَسْتَعِـ", 3.5f), Beat("ـيـ", 2f, focus = true, more = 4), Beat("ـنْ", 1f))))
    Topic.STOP, Topic.LETTERS -> emptyList()
}

/** How long a count lasts when a strip is played through: a measured pace. */
private const val COUNT_MS = 450

/**
 * An example in time, read from the right as it is recited. A row of what is said, each
 * sound as wide as it is long; under it, what of that goes through the nose; and under the
 * sound the rule is about, its counts. Over them, where the mushaf writes something else
 * than is said, what it writes. Its button plays the time through: a line crosses the row
 * at a measured pace, so that two counts and six are seen for how long they are. [ink] is
 * the rule's colour.
 */
@Composable
fun SoundStrip(strip: Strip, ink: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // How far the line has crossed, 0 to 1; below 0 it isn't showing.
    val crossed = remember { Animatable(-1f) }
    val whole = strip.beats.sumOf { (it.counts + it.more).toDouble() }.toFloat()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pace = stringResource(R.string.strip_pace)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerLow).padding(12.dp).testTag("strip"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // The example as the mushaf writes it, and which case of the rule it is.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strip.words, fontFamily = QuranFont, fontSize = 24.sp, lineHeight = 40.sp, color = colors.onSurface, maxLines = 1)
            Text(strip.label?.let { stringResource(it) }.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(ink.copy(alpha = 0.14f)).clickable(role = Role.Button) {
                    scope.launch {
                        crossed.snapTo(0f)
                        crossed.animateTo(1f, tween((whole * COUNT_MS).toInt(), easing = LinearEasing))
                        crossed.snapTo(-1f)
                    }
                }.semantics { contentDescription = pace }.testTag("strip-play"),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Play, null, Modifier.size(18.dp), tint = ink) }
        }
        Column(
            Modifier.drawWithContent {
                drawContent()
                val at = crossed.value
                if (at >= 0f) {
                    // The sounds lie from the right, clear of the names of the rows.
                    val names = LANE_NAMES.toPx()
                    val from = if (rtl) size.width - names else size.width
                    val x = from - at * (size.width - names)
                    drawLine(ink, Offset(x, 0f), Offset(x, size.height), 2.5.dp.toPx(), StrokeCap.Round)
                }
            },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (strip.beats.any { it.written != null }) {
                Lane(stringResource(R.string.strip_written), strip.beats, 38.dp) { beat ->
                    beat.written?.let { Text(it, fontFamily = QuranFont, fontSize = 20.sp, color = colors.onSurface, maxLines = 1) }
                }
            }
            Lane(stringResource(R.string.strip_mouth), strip.beats, 52.dp) { beat ->
                val shape = RoundedCornerShape(10.dp)
                Row(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.weight(beat.counts).fillMaxHeight().clip(shape)
                            .then(if (beat.focus) Modifier.background(ink.copy(alpha = 0.16f)).border(1.5.dp, ink, shape) else Modifier.background(colors.surfaceContainerHighest)),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(beat.text, fontFamily = QuranFont, fontSize = 22.sp, color = if (beat.focus) ink else colors.onSurface, maxLines = 1)
                        // The echo of a qalqalah: the letter is shut, then springs open.
                        if (beat.bounce) {
                            Canvas(Modifier.padding(start = 4.dp).size(18.dp, 22.dp)) {
                                val path = Path().apply {
                                    moveTo(size.width, size.height * 0.8f)
                                    quadraticTo(size.width * 0.7f, -size.height * 0.2f, size.width * 0.45f, size.height * 0.8f)
                                    quadraticTo(size.width * 0.25f, size.height * 0.3f, 0f, size.height * 0.8f)
                                }
                                drawPath(path, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }
                    // The counts it may be held for besides: room for them, left open.
                    if (beat.more > 0) {
                        Box(
                            Modifier.weight(beat.more.toFloat()).fillMaxHeight().padding(start = 3.dp).drawBehind {
                                drawRoundRect(
                                    ink.copy(alpha = 0.7f), cornerRadius = CornerRadius(10.dp.toPx()),
                                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                                )
                            },
                        )
                    }
                }
            }
            if (strip.nose) Lane(stringResource(R.string.strip_nose), strip.beats, 16.dp) { beat ->
                if (beat.nose) {
                    Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.12f))) {
                        // The hum, as a wave running the length of the sound.
                        val wave = Path()
                        val waves = (size.width / 14.dp.toPx()).toInt().coerceAtLeast(2)
                        val steps = waves * 12
                        for (k in 0..steps) {
                            val x = size.width * k / steps
                            val y = size.height / 2 + size.height * 0.26f * sin(2 * PI * waves * k / steps).toFloat()
                            if (k == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
                        }
                        drawPath(wave, ink, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
            if (strip.beats.any { it.counted }) Lane(stringResource(R.string.strip_counts), strip.beats, 20.dp) { beat ->
                if (beat.counted) {
                    val sure = beat.counts.toInt()
                    Row(Modifier.fillMaxSize()) {
                        for (n in 1..sure + beat.more) {
                            Text(
                                stringResource(R.string.number, n), Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium, color = if (n <= sure) ink else colors.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How much room the names of a strip's rows take. */
private val LANE_NAMES = 64.dp

/** A row of a strip: its name, then a cell for each sound, as wide as the sound is long, from the right. */
@Composable
private fun Lane(name: String, beats: List<Beat>, height: Dp, cell: @Composable BoxScope.(Beat) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.width(LANE_NAMES), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(Modifier.weight(1f).height(height), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (beat in beats) Box(Modifier.weight(beat.counts + beat.more).fillMaxHeight(), contentAlignment = Alignment.Center) { cell(beat) }
            }
        }
    }
}

/** Where the tip of the tongue is. */
enum class Tip { DOWN, GUM, NEAR, TEETH }

/** How the body of the tongue lies: flat, its back raised towards the palate, its middle raised (as for ي), or its back shut against the palate (further back for ق than for ك). */
enum class Back { FLAT, RAISED, MIDDLE, SHUT, SHUT_DEEP }

/** A place in the mouth or the throat where a letter is made, to put a mark on. */
enum class Place(val x: Float, val y: Float, val nameX: Float = x, val nameY: Float = y) {
    LIPS(27.5f, 62.5f, 15.5f, 62.5f), TEETH(33.5f, 61.5f), LOW_TEETH(36.5f, 65f), GUM(36.5f, 56f, 45f, 37f), PALATE(47f, 56.5f, 54.5f, 33f), SIDE(47f, 60f),
    FORWARD(55f, 56.5f), BACK(61.5f, 56.5f, 65f, 37f), THROAT_MID(70f, 80f), THROAT_DEEP(70f, 92f),
}

/**
 * The mouth as a letter is made, seen from the side, facing left: where the [tip] of the
 * tongue is and how its [back] lies; whether the [lips] are shut; and whether the sound
 * goes out through the [nose] (the soft palate hangs down and lets it up) or through the
 * mouth. [round] pushes the lips forward, as for و, and [wide] drops the jaw, as for an
 * alif. [air] draws the way the sound takes. [marks] are the places to point at, each
 * with the letters made there.
 */
class Mouth(
    val tip: Tip = Tip.DOWN, val back: Back = Back.FLAT, val lips: Boolean = false, val nose: Boolean = false, val air: Boolean = true,
    val marks: List<Pair<Place, String>> = emptyList(), val round: Boolean = false, val wide: Boolean = false,
)

/** A [Mouth], drawn. [ink] is the colour of the way the sound takes and of the marks. */
@Composable
fun MouthPicture(mouth: Mouth, ink: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val night = colors.background.luminance() < 0.5f
    val flesh = if (night) Color(0xFFB9776E) else Color(0xFFE8A79C)
    val line = colors.onSurfaceVariant
    val label = TextStyle(fontFamily = QuranFont, fontSize = 17.sp, color = colors.onSurface)
    Canvas(modifier.aspectRatio(VIEW_WIDTH / VIEW_HEIGHT).clipToBounds()) {
        // Drawn on a grid of a hundred each way, of which the part with the mouth in it is shown.
        val u = size.width / VIEW_WIDTH
        fun at(x: Float, y: Float) = Offset((x - VIEW_LEFT) * u, (y - VIEW_TOP) * u)
        fun Path.to(x: Float, y: Float) = at(x, y).let { lineTo(it.x, it.y) }
        fun Path.from(x: Float, y: Float) = at(x, y).let { moveTo(it.x, it.y) }
        fun Path.curve(a: Float, b: Float, c: Float, d: Float, x: Float, y: Float) = cubicTo(at(a, b).x, at(a, b).y, at(c, d).x, at(c, d).y, at(x, y).x, at(x, y).y)
        val thin = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val shut = mouth.lips
        // The jaw drops a little when the lips part: more for an open vowel, less behind rounded lips.
        val drop = if (shut) 0f else if (mouth.wide) 6.5f else if (mouth.round) 1.6f else 2.5f
        // Rounded lips stand forward of where they rest.
        val out = if (mouth.round) 3.4f else 0f

        // The face, from the brow down to the neck, with the lips shut or parted.
        val face = Path().apply {
            from(42f, 14f)
            curve(39.5f, 20f, 38f, 25f, 38f, 30f)
            curve(36f, 36f, 27f, 41f, 22f, 47f)
            curve(20f, 50f, 24f, 53f, 31f, 53f)
            curve(28f - out / 2, 55f, 25f - out, 59f, 28.5f - out, if (shut) 62.5f else 61f)
        }
        val jaw = Path().apply {
            from(28.5f - out, 62.5f + drop)
            curve(24.5f - out, 64f + drop, 25f - out / 2, 69f + drop, 31f, 70.5f + drop)
            curve(30f, 76f + drop, 30f, 83f, 36f, 88f)
            curve(44f, 93f, 52f, 92f, 58f, 94f)
            to(60f, 100f)
        }
        drawPath(face, line, style = thin)
        drawPath(jaw, line, style = thin)

        // The back of the throat.
        drawPath(Path().apply { from(72.5f, 20f); curve(74f, 50f, 72f, 80f, 74f, 100f) }, line, style = thin)
        // The roof of the nose.
        drawPath(Path().apply { from(31f, 47.5f); curve(42f, 40f, 62f, 39f, 72.5f, 45f) }, line.copy(alpha = 0.55f), style = thin)
        // The hard palate, from the gum behind the upper teeth back to where the soft palate begins.
        drawLine(line, at(33f, 54f), at(58f, 54f), 3.2f * u, StrokeCap.Round)
        // The soft palate: raised against the back of the throat, or hanging down and letting the sound up into the nose.
        val velum = Path().apply {
            from(58f, 54f)
            if (mouth.nose) curve(62f, 55f, 65f, 58f, 65.5f, 63f) else curve(63f, 54f, 68f, 52f, 72f, 49.5f)
        }
        drawPath(velum, line, style = Stroke(3.2f * u, cap = StrokeCap.Round))
        // The front teeth, upper and lower.
        drawRoundRect(line, at(32f, 54f), Size(2.4f * u, 6f * u), CornerRadius(1.2f * u))
        drawRoundRect(line, at(33f, 62.5f + drop * 0.6f), Size(2.4f * u, 6f * u), CornerRadius(1.2f * u))

        // The tongue.
        val (tx, ty) = when (mouth.tip) {
            Tip.DOWN -> 37.5f to 66.5f + drop * 0.6f
            Tip.GUM -> 36.5f to 56.3f
            Tip.NEAR -> 38.5f to 60.8f
            Tip.TEETH -> 32.5f to 61.6f
        }
        val (hx, hy) = when (mouth.back) {
            Back.FLAT -> 56f to 65f
            Back.RAISED -> 57f to 59.5f
            Back.MIDDLE -> 50f to 58.6f
            Back.SHUT -> 54f to 56.4f
            Back.SHUT_DEEP -> 61f to 56.6f
        }
        val tongue = Path().apply {
            from(67f, 94f)
            curve(65f, 84f, 66f, 74f, hx + 6f, hy + 4f)
            curve(hx + 3.5f, hy - 0.2f, hx - 3f, hy - 0.6f, hx - 8f, hy + 1.2f)
            curve(hx - 13f, hy + 3f, tx + 5f, ty - 2.2f, tx, ty)
            curve(tx - 1.2f, ty + 2.6f, tx + 2f, ty + 5.5f, 43f, 73f + drop * 0.6f)
            curve(49f, 78f, 54f, 86f, 57f, 94f)
            close()
        }
        drawPath(tongue, flesh)
        drawPath(tongue, flesh.copy(alpha = 1f).let { Color(it.red * 0.8f, it.green * 0.8f, it.blue * 0.8f) }, style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))

        // A heavy letter: the back of the tongue is shown rising.
        if (mouth.back == Back.RAISED) {
            val dark = Color(flesh.red * 0.45f, flesh.green * 0.45f, flesh.blue * 0.45f)
            drawLine(dark, at(58.5f, 71.5f), at(58.5f, 64f), 1.6.dp.toPx(), StrokeCap.Round)
            drawLine(dark, at(58.5f, 64f), at(56.3f, 66.6f), 1.6.dp.toPx(), StrokeCap.Round)
            drawLine(dark, at(58.5f, 64f), at(60.7f, 66.6f), 1.6.dp.toPx(), StrokeCap.Round)
        }

        // The way the sound takes: up behind the soft palate and out of the nose, or over the tongue and out of the mouth.
        if (mouth.air) {
            val way = Path()
            val tip: Offset
            val before: Offset
            if (mouth.nose) {
                way.from(69.5f, 90f)
                way.curve(69.5f, 72f, 70f, 58f, 66f, 49f)
                way.curve(62f, 44f, 44f, 44f, 33f, 48.5f)
                way.to(17f, 51.5f)
                tip = at(17f, 51.5f)
                before = at(33f, 48.5f)
            } else {
                way.from(69.5f, 90f)
                way.curve(69.5f, 74f, 69f, 64f, 62f, 60.5f)
                way.curve(54f, 58f, 44f, 58f, 34f, 61.8f + drop / 2)
                way.to(15f - out, 62.5f + drop / 2)
                tip = at(15f - out, 62.5f + drop / 2)
                before = at(34f, 61.8f + drop / 2)
            }
            drawPath(way, ink, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
            // Its arrowhead.
            val along = (tip - before).let { it / it.getDistance() }
            val across = Offset(-along.y, along.x)
            val head = 4.2f * u
            drawPath(
                Path().apply {
                    moveTo(tip.x + along.x * head * 0.6f, tip.y + along.y * head * 0.6f)
                    lineTo(tip.x - along.x * head + across.x * head * 0.7f, tip.y - along.y * head + across.y * head * 0.7f)
                    lineTo(tip.x - along.x * head - across.x * head * 0.7f, tip.y - along.y * head - across.y * head * 0.7f)
                    close()
                },
                ink,
            )
        }

        // The places pointed at, each with its letters beside it.
        for ((place, letters) in mouth.marks) {
            val centre = at(place.x, place.y)
            drawCircle(colors.background, 4.2f * u, centre)
            drawCircle(ink, 3f * u, centre)
            if (letters.isNotEmpty()) {
                // The letters stand clear of the mouth, a thin line leading from them to their place.
                val text = measurer.measure(letters, label)
                val name = at(place.nameX, place.nameY)
                if (place.nameY < place.y - 4f) drawLine(ink.copy(alpha = 0.7f), Offset(name.x, name.y + text.size.height / 2f), Offset(centre.x, centre.y - 3f * u), 1.dp.toPx())
                drawText(text, topLeft = Offset(name.x - text.size.width / 2f, name.y - text.size.height / 2f))
            }
        }
    }
}

// The part of the grid a mouth is drawn on that is shown: from the nose to under the chin.
private const val VIEW_LEFT = 9f
private const val VIEW_TOP = 26f
private const val VIEW_WIDTH = 72f
private const val VIEW_HEIGHT = 72f

/** One part of the mouth and what it does: both are strings of the app. */
private class Part(val name: Int, val does: Int)

/**
 * A mouth with what it shows: the letter it is the mouth of, where it is one's; a line of
 * words; and, for a rule, what each part of the mouth does while it is said ([parts]).
 */
private class Figure(val mouth: Mouth, val caption: Int?, val letter: String = "", val parts: List<Part> = emptyList())

/** The three long vowels' mouths, which every madd is held in: an alif, a و and a ي. */
private val LONG_VOWELS = listOf(
    Figure(
        Mouth(wide = true), null, "ـَا",
        listOf(Part(R.string.part_jaw, R.string.mouth_madd_alif_jaw), Part(R.string.part_tongue, R.string.mouth_madd_alif_tongue), Part(R.string.part_lips, R.string.mouth_madd_alif_lips)),
    ),
    Figure(Mouth(back = Back.RAISED, round = true), null, "ـُو", listOf(Part(R.string.part_lips, R.string.mouth_madd_waw_lips), Part(R.string.part_tongue, R.string.mouth_madd_waw_tongue))),
    Figure(Mouth(back = Back.MIDDLE), null, "ـِي", listOf(Part(R.string.part_tongue, R.string.mouth_madd_ya_tongue), Part(R.string.part_jaw_lips, R.string.mouth_madd_ya_lips))),
)

/**
 * The mouths that show how [topic] is made (for letters, the two of [letters]), with a line
 * on what they show; none where a mouth has nothing to show. A rule's mouths each say
 * what the tongue, the lips, the jaw and the nose do, and for how long.
 */
private fun figuresOf(topic: Topic, letters: String): Pair<List<Figure>, Int?> = when (topic) {
    Topic.GHUNNAH -> listOf(
        Figure(
            Mouth(tip = Tip.GUM, nose = true), null, "نّ",
            listOf(
                Part(R.string.part_tip, R.string.mouth_ghunnah_nun_tip), Part(R.string.part_lips, R.string.mouth_ghunnah_nun_lips),
                Part(R.string.part_nose, R.string.mouth_ghunnah_nose), Part(R.string.part_hold, R.string.mouth_ghunnah_nun_hold),
            ),
        ),
        Figure(
            Mouth(lips = true, nose = true), null, "مّ",
            listOf(
                Part(R.string.part_lips, R.string.mouth_ghunnah_mim_lips), Part(R.string.part_tongue, R.string.mouth_ghunnah_mim_tongue),
                Part(R.string.part_nose, R.string.mouth_ghunnah_nose), Part(R.string.part_hold, R.string.mouth_ghunnah_mim_hold),
            ),
        ),
    ) to R.string.mouth_nose
    Topic.IKHFA -> listOf(
        Figure(
            Mouth(tip = Tip.NEAR, nose = true), R.string.mouth_ikhfa, "",
            listOf(
                Part(R.string.part_tip, R.string.mouth_ikhfa_tip), Part(R.string.part_back, R.string.mouth_ikhfa_back), Part(R.string.part_lips, R.string.mouth_ikhfa_lips),
                Part(R.string.part_nose, R.string.mouth_ikhfa_nose), Part(R.string.part_hold, R.string.mouth_ikhfa_hold),
            ),
        ),
        Figure(Mouth(tip = Tip.GUM, nose = true), R.string.mouth_izhar, "", listOf(Part(R.string.part_tip, R.string.mouth_izhar_tip), Part(R.string.part_nose, R.string.mouth_izhar_nose))),
    ) to R.string.mouth_ikhfa_note
    Topic.IQLAB -> listOf(
        Figure(
            Mouth(lips = true, nose = true), null, "نۢ ب",
            listOf(
                Part(R.string.part_lips, R.string.mouth_iqlab_lips), Part(R.string.part_tongue, R.string.mouth_iqlab_tongue),
                Part(R.string.part_nose, R.string.mouth_iqlab_nose), Part(R.string.part_hold, R.string.mouth_iqlab_hold),
            ),
        ),
    ) to null
    Topic.IDGHAM -> listOf(
        // Shown merging into a ي: the middle of the tongue raised, the sound in the nose.
        Figure(
            Mouth(back = Back.MIDDLE, nose = true), R.string.mouth_idgham_with, "",
            listOf(
                Part(R.string.part_tip, R.string.mouth_idgham_tip), Part(R.string.part_next, R.string.mouth_idgham_next),
                Part(R.string.part_nose, R.string.mouth_idgham_nose), Part(R.string.part_hold, R.string.mouth_idgham_hold),
            ),
        ),
        // And into a ل: the tip on the gum, the sound out of the mouth.
        Figure(
            Mouth(tip = Tip.GUM), R.string.mouth_idgham_without, "",
            listOf(Part(R.string.part_tip, R.string.mouth_idgham_without_tip), Part(R.string.part_nose, R.string.mouth_idgham_without_nose), Part(R.string.part_hold, R.string.mouth_idgham_without_hold)),
        ),
    ) to null
    Topic.QALQALAH -> listOf(
        Figure(
            Mouth(air = false, marks = listOf(Place.LIPS to "ب", Place.GUM to "د ط", Place.PALATE to "ج", Place.BACK to "ق")), null, "",
            listOf(
                Part(R.string.part_place, R.string.mouth_qalqalah_place), Part(R.string.part_then, R.string.mouth_qalqalah_then),
                Part(R.string.part_jaw_lips, R.string.mouth_qalqalah_jaw), Part(R.string.part_nose, R.string.mouth_qalqalah_nose),
            ),
        ),
    ) to R.string.mouth_qalqalah
    Topic.MADD_NATURAL, Topic.MADD_MUNFASIL, Topic.MADD_MUTTASIL, Topic.MADD_LAZIM, Topic.MADD_ARID -> LONG_VOWELS to R.string.mouth_madd_note
    Topic.LETTERS -> when (letters) {
        "صس" -> pair(letters, Mouth(back = Back.RAISED, air = false, marks = listOf(Place.LOW_TEETH to "")), R.string.mouth_heavy, Mouth(air = false, marks = listOf(Place.LOW_TEETH to "")), R.string.mouth_light)
        "ضد" -> pair(letters, Mouth(tip = Tip.GUM, back = Back.RAISED, air = false, marks = listOf(Place.SIDE to "")), R.string.mouth_side, Mouth(tip = Tip.GUM, air = false, marks = listOf(Place.GUM to "")), R.string.mouth_tip)
        "طت" -> pair(letters, Mouth(tip = Tip.GUM, back = Back.RAISED, air = false, marks = listOf(Place.GUM to "")), R.string.mouth_heavy, Mouth(tip = Tip.GUM, air = false, marks = listOf(Place.GUM to "")), R.string.mouth_light)
        "ظذ" -> pair(letters, Mouth(tip = Tip.TEETH, back = Back.RAISED, air = false, marks = listOf(Place.TEETH to "")), R.string.mouth_heavy, Mouth(tip = Tip.TEETH, air = false, marks = listOf(Place.TEETH to "")), R.string.mouth_light)
        "قك" -> pair(letters, Mouth(back = Back.SHUT_DEEP, air = false, marks = listOf(Place.BACK to "")), R.string.mouth_back, Mouth(back = Back.SHUT, air = false, marks = listOf(Place.FORWARD to "")), R.string.mouth_forward)
        "حه" -> pair(letters, Mouth(air = false, marks = listOf(Place.THROAT_MID to "")), R.string.mouth_throat_mid, Mouth(air = false, marks = listOf(Place.THROAT_DEEP to "")), R.string.mouth_throat_deep)
        "عء" -> pair(letters, Mouth(air = false, marks = listOf(Place.THROAT_MID to "")), R.string.mouth_throat_mid, Mouth(air = false, marks = listOf(Place.THROAT_DEEP to "")), R.string.mouth_throat_deep)
        else -> emptyList<Figure>() to null
    }
    Topic.STOP -> emptyList<Figure>() to null
}

private fun pair(letters: String, first: Mouth, firstSays: Int, second: Mouth, secondSays: Int): Pair<List<Figure>, Int?> =
    listOf(Figure(first, firstSays, letters.take(1)), Figure(second, secondSays, letters.drop(1))) to null

/** Whether there is a picture to teach [topic] by. */
fun hasPicture(topic: Topic, letters: String = ""): Boolean = stripsOf(topic).isNotEmpty() || figuresOf(topic, letters).first.isNotEmpty()

/**
 * A rule in pictures. How it is made: the mouth from the side, with where the tongue and
 * the lips are and which way the sound goes out, and beside each mouth what every part of
 * it does while the rule is said: the tongue's tip and its back, the lips, the jaw, the
 * nose, and for how long. And how long it lasts: an example laid out in time (see
 * [SoundStrip]). Two letters to tell apart have their two mouths side by side instead.
 * [short] leaves it at the first example, for the foot of an exercise. [ink] is the
 * rule's colour.
 */
@Composable
fun RulePicture(topic: Topic, letters: String, ink: Color, modifier: Modifier = Modifier, short: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val (figures, note) = figuresOf(topic, letters)
    val strips = stripsOf(topic).let { if (short) it.take(1) else it }
    val reading = LocalLayoutDirection.current
    Column(modifier.testTag("rule-picture"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (figures.isNotEmpty() && !short) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerLow).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (figures.any { it.parts.isNotEmpty() }) {
                    // A rule: each mouth with what every part of it does, said beside it.
                    Text(stringResource(R.string.lesson_parts), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    for ((i, figure) in figures.withIndex()) {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MouthPicture(figure.mouth, ink, Modifier.width(118.dp).testTag("mouth"))
                            Column(Modifier.weight(1f).testTag("mouth-parts"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                if (figure.letter.isNotEmpty()) Text(figure.letter, fontFamily = QuranFont, fontSize = 26.sp, lineHeight = 40.sp, color = ink)
                                figure.caption?.let { Text(stringResource(it), style = MaterialTheme.typography.titleSmall, color = colors.onSurface) }
                                for (part in figure.parts) {
                                    Column {
                                        Text(stringResource(part.name), style = MaterialTheme.typography.labelMedium, color = ink)
                                        Text(stringResource(part.does), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Two letters: read from the right, as the letters under them are.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                            for (figure in figures) {
                                Column(Modifier.weight(1f, fill = false).widthIn(max = 190.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    MouthPicture(figure.mouth, ink, Modifier.fillMaxWidth().testTag("mouth"))
                                    if (figure.letter.isNotEmpty()) Text(figure.letter, fontFamily = QuranFont, fontSize = 28.sp, lineHeight = 44.sp, color = ink)
                                    figure.caption?.let {
                                        CompositionLocalProvider(LocalLayoutDirection provides reading) {
                                            Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = colors.onSurface, textAlign = TextAlign.Center)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
        }
        for (strip in strips) SoundStrip(strip, ink)
    }
}

/** What to do in an exercise on [topic], in a line: where the coloured letters are, this is done to them. */
fun cueOf(topic: Topic): Int = when (topic) {
    Topic.GHUNNAH -> R.string.cue_ghunnah
    Topic.IKHFA -> R.string.cue_ikhfa
    Topic.IQLAB -> R.string.cue_iqlab
    Topic.IDGHAM -> R.string.cue_idgham
    Topic.QALQALAH -> R.string.cue_qalqalah
    Topic.MADD_NATURAL -> R.string.cue_madd_natural
    Topic.MADD_MUNFASIL, Topic.MADD_MUTTASIL -> R.string.cue_madd_4_5
    Topic.MADD_LAZIM -> R.string.cue_madd_lazim
    Topic.MADD_ARID -> R.string.cue_madd_arid
    Topic.STOP -> R.string.drill_how_stop
    Topic.LETTERS -> R.string.cue_letters
}

/**
 * What an exercise asks for, under its verse: the one thing to do, in a line, in the
 * rule's colour; that nothing else is judged; and the rule's example in time, to keep the
 * length of it in the eye while reciting.
 */
@Composable
fun DrillGuide(topic: Topic, letters: String, ink: Color) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.testTag("drill-guide"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.padding(top = 7.dp).size(12.dp).background(ink, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(cueOf(topic)), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                Text(stringResource(R.string.drill_only), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        RulePicture(topic, letters, ink, short = true)
    }
}

/**
 * How an exercise stands: a dot for each of its verses (filled where the go was sound,
 * in the colour of a mistake where it wasn't, empty where it wasn't heard or hasn't been
 * recited; the one being recited ringed), and, in an exercise on one rule ([learning]),
 * how many are sound of the number that makes the rule count as learned.
 */
@Composable
fun DrillPips(results: Map<Int, Went>, count: Int, at: Int, learning: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().testTag("drill-pips"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until count) {
            val fill = when (results[i]) {
                Went.SOUND -> colors.primary
                Went.MISSED -> colors.error
                else -> Color.Transparent
            }
            Box(
                Modifier.size(if (i == at) 16.dp else 12.dp).background(fill, CircleShape)
                    .border(if (i == at) 2.dp else 1.dp, if (i == at) colors.primary else if (fill == Color.Transparent) colors.outline else fill, CircleShape),
            )
        }
        if (learning) Text(
            stringResource(R.string.drill_need, results.values.count { it == Went.SOUND }, Drills.needed(count)), Modifier.weight(1f).padding(start = 6.dp),
            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1,
        )
    }
}

/**
 * How the rule of an exercise was heard, place by place: the word it is in, and beside it
 * what was measured there. A madd as beads, one a count, filled as far as it was held; a
 * nasal sound as a bar, filled as far as it was kept; an echo, a nun turned or merged, as
 * heard or not. [words] are the verse as the exercise sees it (see [Drills.only]) and
 * [verse] its words as written. [ink] is the rule's colour.
 */
@Composable
fun RuleResults(verse: Verse?, words: List<WordRecited>, ink: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val written = byWord(words, verse?.words?.size ?: 0).entries.associate { it.value to verse?.words?.getOrNull(it.key)?.arabic }
    Column(modifier.testTag("rule-results"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (word in words) for (measure in word.measures) {
            val sound = measure.fault == null
            val tone = if (sound) ink else colors.error
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(written[word].orEmpty(), Modifier.width(92.dp), fontFamily = QuranFont, fontSize = 22.sp, lineHeight = 40.sp, color = colors.onSurface, maxLines = 1)
                }
                Meter(measure, tone, Modifier.weight(1f))
                Text(said(measure), style = MaterialTheme.typography.labelLarge, color = if (sound) colors.onSurface else colors.error, maxLines = 2)
            }
        }
    }
}

/** What was measured at one place, drawn: beads for a madd's counts, a bar for a nasal sound, a tick or a warning for what is there or isn't. */
@Composable
private fun Meter(measure: Measure, tone: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    when (measure.awaited.kind) {
        Listened.MADD -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            // One bead a count: those held filled, those still due empty, those held too long in the colour of a mistake.
            for (n in 1..maxOf(measure.wanted, measure.heard)) {
                val held = n <= measure.heard
                Box(
                    Modifier.size(14.dp).background(if (!held) Color.Transparent else if (n <= measure.wanted) tone else colors.error, CircleShape)
                        .border(1.5.dp, if (held && n > measure.wanted) colors.error else tone, CircleShape),
                )
            }
        }
        Listened.GHUNNAH, Listened.IKHFA, Listened.IDGHAM_GHUNNAH -> {
            val kept = if (measure.fault == null) 1f else if (measure.heard == 0) 0f else (measure.heard.toFloat() / measure.wanted).coerceIn(0.15f, 0.8f)
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Canvas(modifier.height(14.dp)) {
                val round = CornerRadius(size.height / 2)
                drawRoundRect(tone.copy(alpha = 0.16f), cornerRadius = round)
                if (kept > 0f) drawRoundRect(tone, Offset(if (rtl) size.width * (1 - kept) else 0f, 0f), Size(size.width * kept, size.height), round)
                drawRoundRect(tone, cornerRadius = round, style = Stroke(1.5.dp.toPx()))
            }
        }
        else -> Box(modifier) { Icon(if (measure.fault == null) AppIcons.Check else AppIcons.Warning, null, Modifier.size(20.dp), tint = tone) }
    }
}

/** What was measured at one place, in a word or two. */
@Composable
private fun said(measure: Measure): String {
    val sound = measure.fault == null
    return when (measure.awaited.kind) {
        Listened.MADD -> if (sound) stringResource(R.string.result_counts_sound, measure.heard) else stringResource(R.string.result_counts, measure.heard, measure.wanted)
        Listened.QALQALAH -> stringResource(if (sound) R.string.result_echo else R.string.result_no_echo)
        Listened.IQLAB -> stringResource(if (sound) R.string.result_turned else R.string.result_not_turned)
        Listened.IDGHAM, Listened.SHADDA -> stringResource(if (sound) R.string.result_merged else R.string.result_clear_nun)
        Listened.GHUNNAH, Listened.IKHFA, Listened.IDGHAM_GHUNNAH -> stringResource(
            when (measure.fault) {
                null -> R.string.result_held
                Fault.IKHFA_CLEAR, Fault.IDGHAM_MISSED -> R.string.result_clear_nun
                else -> R.string.result_cut
            },
        )
    }
}
