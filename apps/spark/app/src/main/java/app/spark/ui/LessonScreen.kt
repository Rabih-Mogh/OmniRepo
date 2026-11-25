package app.spark.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.spark.R
import app.spark.core.Lesson
import app.spark.core.Step
import app.spark.data.Store
import kotlin.random.Random

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
                StepPage(lesson, at, last = at == steps.lastIndex) { mark ->
                    mark?.let(marks::add)
                    at++
                }
            }
        }
    }
}

/** One step: what it asks, then what was right and why, then on to the next. [next] is given the mark, or null for a step that has none. */
@Composable
private fun StepPage(lesson: Lesson, index: Int, last: Boolean, next: (Boolean?) -> Unit) {
    val step = lesson.steps[index]
    // The same shuffle every time the lesson is opened, and a different one for each step.
    val random = remember { Random(lesson.id.hashCode() * 31 + index) }
    var mark by remember { mutableStateOf<Boolean?>(null) }
    var ready by remember { mutableStateOf(step is Step.Read || step is Step.Estimate || (step is Step.Explore && step.target == null)) }
    var check by remember { mutableStateOf<() -> Boolean>({ true }) }
    var answer by remember { mutableStateOf<String?>(null) }
    val marked = step !is Step.Read && !(step is Step.Explore && step.target == null)

    Column(Modifier.fillMaxSize()) {
        // Lessons are in English whatever the phone's language, so they read from the left.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    is Step.Read -> ReadStep(step)
                    is Step.Choice -> ChoiceStep(step, random, mark != null, { ready = true }) { check = it }
                    is Step.Order -> OrderStep(step, random, mark != null, { ready = it }, { answer = it }) { check = it }
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
                    Surface(
                        Modifier.fillMaxWidth(),
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

@Composable
private fun Question(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

@Composable
private fun ReadStep(step: Step.Read) {
    step.title?.let { Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
    step.visual?.let {
        val drawn = remember { Animatable(0f) }
        LaunchedEffect(Unit) { drawn.animateTo(1f, tween(1400, easing = LinearEasing)) }
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            VisualView(it, drawn.value, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, Modifier.padding(18.dp))
        }
    }
    Text(step.text, style = MaterialTheme.typography.bodyLarge)
}

/** An answer to pick: outlined, filled once picked, and green or red once marked. */
@Composable
private fun Option(text: String, picked: Boolean, right: Boolean?, badge: String? = null, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
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
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ChoiceStep(step: Step.Choice, random: Random, marked: Boolean, onPicked: () -> Unit, judge: (() -> Boolean) -> Unit) {
    val options = remember { (step.wrong + step.right).shuffled(random) }
    var picked by remember { mutableStateOf<String?>(null) }
    Question(step.text)
    for (option in options) {
        Option(
            option,
            picked = option == picked,
            right = if (!marked) null else if (option == step.right) true else if (option == picked) false else null,
            enabled = !marked,
        ) {
            picked = option
            onPicked()
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
    Question(step.text)
    Text(stringResource(R.string.lesson_order_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (item in shown) {
        val place = picked.indexOf(item)
        Option(
            item,
            picked = place >= 0,
            right = if (!marked) null else place == step.items.indexOf(item),
            badge = if (place >= 0) (place + 1).toString() else null,
            enabled = !marked,
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

@Composable
private fun EstimateStep(step: Step.Estimate, marked: Boolean, answer: (String) -> Unit, judge: (() -> Boolean) -> Unit) {
    var value by remember { mutableFloatStateOf(step.min.toFloat()) }
    fun stop(raw: Float): Double = step.min + Math.round((raw - step.min) / step.step) * step.step
    val unit = if (step.unit.isEmpty()) "" else " " + step.unit
    val line = stringResource(R.string.lesson_answer, plain(step.answer) + unit)
    LaunchedEffect(Unit) {
        answer(line)
        judge { kotlin.math.abs(stop(value) - step.answer) <= step.tolerance + 1e-9 }
    }
    Question(step.text)
    Text(
        plain(stop(value)) + unit,
        Modifier.fillMaxWidth().padding(top = 8.dp),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Slider(value, { value = it }, enabled = !marked, valueRange = step.min.toFloat()..step.max.toFloat())
    Row(Modifier.fillMaxWidth()) {
        Text(plain(step.min), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(plain(step.max), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExploreStep(step: Step.Explore, marked: Boolean, onHit: (Boolean) -> Unit) {
    var raw by remember { mutableFloatStateOf(step.param.start.toFloat()) }
    val a = step.param.min + Math.round((raw - step.param.min) / step.param.step) * step.param.step
    val hit = step.hits(a)
    LaunchedEffect(hit) { onHit(hit) }

    Question(step.text)
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
        Slider(raw, { raw = it }, Modifier.weight(1f), enabled = !marked || step.target == null, valueRange = step.param.min.toFloat()..step.param.max.toFloat())
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
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(14.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        Icon(AppIcons.Check, null, Modifier.size(56.dp), tint = Right.ink)
        Text(stringResource(R.string.lesson_done_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(lesson.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (marks.isNotEmpty()) Text(stringResource(R.string.lesson_done_score, marks.count { it }, marks.size), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onBack) { Text(stringResource(R.string.lesson_done_back)) }
    }
}
