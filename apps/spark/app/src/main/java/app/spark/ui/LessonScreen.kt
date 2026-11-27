package app.spark.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.spark.R
import app.spark.core.Chime
import app.spark.core.Lesson
import app.spark.core.Picture
import app.spark.core.Step
import app.spark.data.Store
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * A lesson, a step at a time. Each step that asks for something is marked once, on the
 * first answer, and then explains itself. [onDone] is given the score at the end, or null
 * when the lesson is closed before it.
 */
@Composable
fun LessonScreen(lesson: Lesson, onDone: (Store.Score?) -> Unit) {
    var at by remember(lesson) { mutableIntStateOf(0) }
    val marks = remember(lesson) { mutableStateListOf<Boolean>() }
    val steps = lesson.steps

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDone(null) }) { Icon(AppIcons.Close, stringResource(R.string.lesson_close)) }
            val label = stringResource(R.string.lesson_step, minOf(at + 1, steps.size), steps.size)
            LinearProgressIndicator(
                progress = { at / steps.size.toFloat() },
                Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = label },
            )
        }
        if (at >= steps.size) {
            Finished(lesson, marks) { onDone(Store.Score(marks.count { it }, marks.size)) }
        } else {
            // A step keeps nothing from the one before it.
            key(at) {
                StepPage(lesson, at, last = at == steps.lastIndex, run = marks.takeLastWhile { it }.size) { mark ->
                    mark?.let(marks::add)
                    at++
                }
            }
        }
    }
}

/**
 * One step: what it asks, then what was right and why, then on to the next. [run] is how
 * many answers in a row were right before it. [next] is given the mark, or null for a step
 * that has none.
 */
@Composable
private fun StepPage(lesson: Lesson, index: Int, last: Boolean, run: Int, next: (Boolean?) -> Unit) {
    val step = lesson.steps[index]
    // The same shuffle every time the lesson is opened, and a different one for each step.
    val random = remember { Random(lesson.id.hashCode() * 31 + index) }
    var mark by remember { mutableStateOf<Boolean?>(null) }
    var ready by remember { mutableStateOf(step is Step.Read || step is Step.Estimate || (step is Step.Explore && step.target == null)) }
    var check by remember { mutableStateOf<() -> Boolean>({ true }) }
    var answer by remember { mutableStateOf<String?>(null) }
    val marked = step !is Step.Read && !(step is Step.Explore && step.target == null)
    val feel = rememberFeel()
    val scroll = rememberScrollState()
    LaunchedEffect(mark) {
        // A right answer after others sounds a note higher than the last.
        mark?.let { if (it) feel(Chime.RIGHT, Chime.climb(run)) else feel(Chime.WRONG) }
    }

    Column(Modifier.fillMaxSize()) {
        // Lessons are in English whatever the phone's language, so they read from the left.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(vertical = 12.dp),
                Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    is Step.Read -> ReadStep(step)
                    is Step.Choice -> ChoiceStep(step, random, mark != null, {
                        ready = true
                        feel(Chime.TAP)
                    }, { answer = it }) { check = it }

                    is Step.Order -> OrderStep(step, random, mark != null, {
                        ready = it
                        feel(Chime.TAP)
                    }, { answer = it }) { check = it }

                    is Step.Estimate -> EstimateStep(step, mark != null, { answer = it }) { check = it }
                    is Step.Explore -> ExploreStep(step, mark != null) { hit ->
                        // Reaching the target is the answer; there is nothing to press.
                        if (hit && mark == null && step.target != null) mark = true
                    }
                }
                val why = when (step) {
                    is Step.Read -> null
                    is Step.Choice -> step.why
                    is Step.Order -> step.why
                    is Step.Estimate -> step.why
                    is Step.Explore -> step.why
                }
                mark?.let { right ->
                    val up = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        launch { up.animateTo(1f, tween(220)) }
                        // The explanation may have landed below the screen's edge: once it has its place, bring it up.
                        withFrameNanos { }
                        scroll.animateScrollTo(scroll.maxValue)
                    }
                    Surface(
                        Modifier.fillMaxWidth().graphicsLayer {
                            alpha = up.value
                            translationY = (1f - up.value) * 16.dp.toPx()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (right) Right.box else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Column(Modifier.padding(14.dp), Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(if (right) (if (step is Step.Explore) R.string.lesson_hit else R.string.lesson_right) else R.string.lesson_wrong),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (right) Right.ink else MaterialTheme.colorScheme.onErrorContainer,
                            )
                            if (!right) answer?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            why?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            step.reveal?.let { PictureView(it, compact = true, Modifier.padding(top = 4.dp), MaterialTheme.colorScheme.surface) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            // A curve that won't go through the dot shouldn't hold the whole lesson up.
            if (step is Step.Explore && step.target != null && mark == null) {
                TextButton(onClick = { mark = false }) { Text(stringResource(R.string.lesson_skip)) }
            }
            Spacer(Modifier.weight(1f))
            when {
                marked && mark == null && step !is Step.Explore ->
                    Button(onClick = { mark = check() }, enabled = ready) { Text(stringResource(R.string.lesson_check)) }

                // An explore step is answered on the picture: the way on opens when the curve is through the dot.
                else -> Button(onClick = { next(mark) }, enabled = !marked || mark != null) {
                    Text(stringResource(if (last) R.string.lesson_finish else R.string.lesson_continue))
                }
            }
        }
    }
}

/** What a step asks, and the picture that goes with the question. */
@Composable
private fun Question(step: Step) {
    Text(step.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    step.picture?.let { PictureView(it, compact = true) }
}

/** A step's picture, drawn as it comes on to the page, over the line that says what it shows. */
@Composable
private fun PictureView(picture: Picture, compact: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainer) {
    val drawn = remember { Animatable(0f) }
    LaunchedEffect(Unit) { drawn.animateTo(1f, tween(1400, easing = LinearEasing)) }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = color) {
        Column(Modifier.padding(if (compact) 14.dp else 18.dp), Arrangement.spacedBy(8.dp)) {
            VisualView(picture.visual, drawn.value, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, compact = compact)
            picture.caption?.let {
                Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ReadStep(step: Step.Read) {
    step.title?.let { Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
    step.picture?.let { PictureView(it, compact = false) }
    Text(step.text, style = MaterialTheme.typography.bodyLarge)
}

/**
 * Which notch of a slider [raw] rests in; crossing into another is felt as a tick. A slider
 * of many stops has a notch for every few of them.
 */
private fun notch(raw: Float, min: Double, step: Double, stops: Int): Long {
    val stop = Math.round((raw - min) / step)
    return if (stops <= NOTCHES) stop else stop * NOTCHES / stops
}

private const val NOTCHES = 40

/**
 * An answer to pick: outlined, filled once picked, and green or red once marked. Unless it
 * is [still], it answers the mark itself: a right one swells for a moment, a wrong one shakes.
 */
@Composable
private fun Option(
    text: String,
    picked: Boolean,
    right: Boolean?,
    badge: String? = null,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    still: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val swell = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(right) {
        if (still) return@LaunchedEffect
        if (right == true) {
            swell.animateTo(1.04f, tween(110))
            swell.animateTo(1f, spring(dampingRatio = 0.45f))
        }
        if (right == false) for (to in listOf(-9f, 9f, -5f, 5f, 0f)) shake.animateTo(to, tween(55))
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().graphicsLayer {
            scaleX = swell.value
            scaleY = swell.value
            translationX = shake.value.dp.toPx()
        },
        shape = RoundedCornerShape(16.dp),
        color = when {
            right == true -> Right.box
            right == false -> scheme.errorContainer
            picked -> scheme.primaryContainer
            else -> scheme.surface
        },
        border = BorderStroke(1.dp, if (picked || right != null) Color.Transparent else scheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            badge?.let {
                Box(Modifier.size(26.dp).clip(CircleShape).background(scheme.primary), Alignment.Center) {
                    Text(it, color = scheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            when (right) {
                true -> Icon(AppIcons.Check, null, Modifier.size(20.dp), tint = Right.ink)
                false -> Icon(AppIcons.Close, null, Modifier.size(20.dp), tint = scheme.onErrorContainer)
                null -> Unit
            }
        }
    }
}

@Composable
private fun ChoiceStep(
    step: Step.Choice,
    random: Random,
    marked: Boolean,
    onPicked: () -> Unit,
    answer: (String?) -> Unit,
    judge: (() -> Boolean) -> Unit,
) {
    val options = remember { (step.wrong + step.right).shuffled(random) }
    var picked by remember { mutableStateOf<String?>(null) }
    Question(step)
    for (option in options) {
        Option(
            option,
            picked = option == picked,
            right = if (!marked) null else if (option == step.right) true else if (option == picked) false else null,
            enabled = !marked,
        ) {
            picked = option
            onPicked()
            // What is said after a wrong pick is about that pick.
            answer(step.mistake(option))
            judge { option == step.right }
        }
    }
}

@Composable
private fun OrderStep(
    step: Step.Order,
    random: Random,
    marked: Boolean,
    onReady: (Boolean) -> Unit,
    answer: (String) -> Unit,
    judge: (() -> Boolean) -> Unit,
) {
    val shown = remember {
        // Shuffled until it isn't the right order by chance.
        generateSequence { step.items.shuffled(random) }.first { it != step.items }
    }
    val picked = remember { mutableStateListOf<String>() }
    val right = stringResource(R.string.lesson_order_right)
    Question(step)
    Text(stringResource(R.string.lesson_order_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // Once marked, every item travels to where it belongs, still wearing the number it was given.
    val heights = remember { mutableStateMapOf<String, Int>() }
    val home = remember { Animatable(0f) }
    LaunchedEffect(marked) { if (marked) home.animateTo(1f, tween(650)) }
    val gap = with(LocalDensity.current) { ORDER_GAP.toPx() }
    fun top(order: List<String>, item: String): Float =
        order.takeWhile { it != item }.sumOf { heights[it] ?: 0 } + order.indexOf(item) * gap

    Column(verticalArrangement = Arrangement.spacedBy(ORDER_GAP)) {
        for (item in shown) {
            val place = picked.indexOf(item)
            val travel = top(step.items, item) - top(shown, item)
            Option(
                item,
                picked = place >= 0,
                right = if (!marked) null else place == step.items.indexOf(item),
                badge = if (place >= 0) (place + 1).toString() else null,
                enabled = !marked,
                modifier = Modifier.onSizeChanged { heights[item] = it.height }.graphicsLayer { translationY = travel * home.value },
                still = true,
            ) {
                // A second tap takes it back out.
                if (place >= 0) picked.remove(item) else picked += item
                val order = picked.toList()
                onReady(order.size == shown.size)
                answer(right + " " + step.items.joinToString(" → "))
                judge { order == step.items }
            }
        }
    }
}

private val ORDER_GAP = 16.dp

@Composable
private fun EstimateStep(step: Step.Estimate, marked: Boolean, answer: (String) -> Unit, judge: (() -> Boolean) -> Unit) {
    var value by remember { mutableFloatStateOf(step.min.toFloat()) }
    val feel = rememberFeel()
    fun stop(raw: Float): Double = step.min + Math.round((raw - step.min) / step.step) * step.step
    val unit = if (step.unit.isEmpty()) "" else " " + step.unit
    val line = stringResource(R.string.lesson_answer, plain(step.answer) + unit)
    LaunchedEffect(Unit) {
        answer(line)
        judge { kotlin.math.abs(stop(value) - step.answer) <= step.tolerance + 1e-9 }
    }
    Question(step)
    Text(
        plain(stop(value)) + unit,
        Modifier.fillMaxWidth().padding(top = 8.dp),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    if (marked) {
        GuessLine(step, stop(value))
    } else {
        val stops = ((step.max - step.min) / step.step).toInt()
        Slider(value, {
            if (notch(it, step.min, step.step, stops) != notch(value, step.min, step.step, stops)) feel.tick()
            value = it
        }, valueRange = step.min.toFloat()..step.max.toFloat())
    }
    Row(Modifier.fillMaxWidth()) {
        Text(plain(step.min), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(plain(step.max), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A marked estimate: the guess stays where it was put, as a ring, and the answer travels
 * out from it to where it belongs, with the stretch that counts as right opening around it.
 */
@Composable
private fun GuessLine(step: Step.Estimate, guess: Double) {
    val along = remember { Animatable(0f) }
    LaunchedEffect(Unit) { along.animateTo(1f, tween(750)) }
    val scheme = MaterialTheme.colorScheme
    val good = Right.ink
    val right = kotlin.math.abs(guess - step.answer) <= step.tolerance + 1e-9
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    Canvas(Modifier.fillMaxWidth().height(76.dp)) {
        val inset = 12.dp.toPx()
        val y = size.height / 2
        fun x(v: Double) = inset + (size.width - 2 * inset) * ((v - step.min) / (step.max - step.min)).toFloat()
        fun label(text: String, at: Float, above: Boolean, color: Color, alpha: Float) {
            val words = measurer.measure(text, style)
            val left = (at - words.size.width / 2f).coerceIn(0f, (size.width - words.size.width).coerceAtLeast(0f))
            drawText(words, color, Offset(left, if (above) y - 16.dp.toPx() - words.size.height else y + 16.dp.toPx()), alpha)
        }

        drawLine(scheme.outlineVariant, Offset(x(step.min), y), Offset(x(step.max), y), 4.dp.toPx(), StrokeCap.Round)
        val reach = step.tolerance * along.value
        drawLine(
            good.copy(alpha = 0.3f),
            Offset(x((step.answer - reach).coerceAtLeast(step.min)), y),
            Offset(x((step.answer + reach).coerceAtMost(step.max)), y),
            12.dp.toPx(),
            StrokeCap.Round,
        )
        val answer = x(guess + (step.answer - guess) * along.value)
        drawCircle(good, 8.dp.toPx(), Offset(answer, y))
        drawCircle(if (right) good else scheme.error, 11.dp.toPx(), Offset(x(guess), y), style = Stroke(3.dp.toPx()))
        label(plain(step.answer), answer, above = true, good, along.value)
        if (!right) label(plain(guess), x(guess), above = false, scheme.error, 1f)
    }
}

@Composable
private fun ExploreStep(step: Step.Explore, marked: Boolean, onHit: (Boolean) -> Unit) {
    var raw by remember { mutableFloatStateOf(step.param.start.toFloat()) }
    val a = step.param.min + Math.round((raw - step.param.min) / step.param.step) * step.param.step
    val hit = step.hits(a)
    LaunchedEffect(hit) { onHit(hit) }
    val feel = rememberFeel()
    val stops = ((step.param.max - step.param.min) / step.param.step).toInt()

    Question(step)
    val scheme = MaterialTheme.colorScheme
    val good = Right.ink
    Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainer) {
        Column(Modifier.padding(14.dp)) {
            Row {
                Column(Modifier.padding(end = 6.dp)) {
                    Text(plain(step.yTo), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
                Canvas(Modifier.weight(1f).aspectRatio(1.5f)) {
                    fun x(v: Double) = (size.width * (v - step.xFrom) / (step.xTo - step.xFrom)).toFloat()
                    fun y(v: Double) = (size.height * (1 - (v - step.yFrom) / (step.yTo - step.yFrom))).toFloat()
                    drawRect(scheme.outlineVariant, style = Stroke(1.dp.toPx()))
                    for (i in 1..3) {
                        drawLine(scheme.outlineVariant.copy(alpha = 0.5f), Offset(size.width * i / 4, 0f), Offset(size.width * i / 4, size.height))
                        drawLine(scheme.outlineVariant.copy(alpha = 0.5f), Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4))
                    }
                    if (step.yFrom < 0 && step.yTo > 0) drawLine(scheme.outline, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1.5.dp.toPx())
                    if (step.xFrom < 0 && step.xTo > 0) drawLine(scheme.outline, Offset(x(0.0), 0f), Offset(x(0.0), size.height), 1.5.dp.toPx())
                    clipRect {
                        val path = Path()
                        var drawing = false
                        for (i in 0..120) {
                            val vx = step.xFrom + (step.xTo - step.xFrom) * i / 120
                            val vy = step.plot.at(vx, a)
                            // The pen lifts where the curve has no value, and where it runs far outside the picture.
                            if (!vy.isFinite() || kotlin.math.abs(vy) > 1e6) {
                                drawing = false
                                continue
                            }
                            if (drawing) path.lineTo(x(vx), y(vy)) else path.moveTo(x(vx), y(vy))
                            drawing = true
                        }
                        drawPath(path, if (hit) good else scheme.primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    step.target?.let {
                        val centre = Offset(x(it.x), y(it.y))
                        if (hit) drawCircle(good, 7.dp.toPx(), centre)
                        drawCircle(if (hit) good else scheme.tertiary, 9.dp.toPx(), centre, style = Stroke(2.5.dp.toPx()))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(plain(step.yFrom), Modifier.padding(end = 6.dp), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text(plain(step.xFrom), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(plain(step.xTo), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("a = " + plain(a), Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Slider(raw, {
            if (notch(it, step.param.min, step.param.step, stops) != notch(raw, step.param.min, step.param.step, stops)) feel.tick()
            raw = it
        }, Modifier.weight(1f), enabled = !marked || step.target == null, valueRange = step.param.min.toFloat()..step.param.max.toFloat())
    }
    for (show in step.shows) {
        Row(Modifier.fillMaxWidth()) {
            Text(show.label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(plain(show.value.at(0.0, a)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
    if (step.target == null) Text(step.why, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
}

@Composable
private fun Finished(lesson: Lesson, marks: List<Boolean>, onBack: () -> Unit) {
    val feel = rememberFeel()
    LaunchedEffect(Unit) { feel(Chime.FINISH) }
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(14.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        Icon(AppIcons.Check, null, Modifier.size(56.dp), tint = Right.ink)
        Text(stringResource(R.string.lesson_done_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(lesson.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (marks.isNotEmpty()) Text(stringResource(R.string.lesson_done_score, marks.count { it }, marks.size), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onBack) { Text(stringResource(R.string.lesson_done_back)) }
    }
}
