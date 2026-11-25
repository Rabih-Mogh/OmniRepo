package app.spark.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.spark.R
import app.spark.core.Kind
import app.spark.core.Reel
import kotlinx.coroutines.launch

private val Ink = Color(0xFFFFFFFF)
private val Night = Color(0xFF0F0F14)

/**
 * One reel, filling the page. Its hook, its beats and its takeaway come up one after
 * another while it is [active]; a tap brings the next one at once. A puzzle keeps its
 * answer back until it is asked for.
 *
 * [lessonDone] is null when no lesson hangs off the reel.
 */
@Composable
fun ReelPage(
    reel: Reel,
    active: Boolean,
    played: Boolean,
    liked: Boolean,
    lessonDone: Boolean?,
    onPlayed: () -> Unit,
    onLike: () -> Unit,
    onLess: () -> Unit,
    onLesson: () -> Unit,
    onSource: () -> Unit,
) {
    val look = look(reel.topic)
    val scope = rememberCoroutineScope()
    val puzzle = reel.kind == Kind.PUZZLE
    val total = FeedState.runtime(reel)

    // When each part starts, as a share of the whole: the hook, each beat, then the takeaway.
    val starts = remember(reel) {
        val at = ArrayList<Float>()
        var ms = 0
        at += 0f
        ms += FeedState.HOOK_MS
        repeat(reel.beats.size) {
            at += ms / total.toFloat()
            ms += FeedState.BEAT_MS
        }
        if (!puzzle) at += ms / total.toFloat()
        at
    }
    val progress = remember(reel) { Animatable(if (played) 1f else 0f) }
    var revealed by remember(reel) { mutableStateOf(false) }
    val scroll = rememberScrollState()

    suspend fun run() {
        progress.animateTo(1f, tween(((1f - progress.value) * total).toInt(), easing = LinearEasing))
        onPlayed()
    }
    LaunchedEffect(reel, active) { if (active && progress.value < 1f) run() }

    val p = progress.value
    fun shown(part: Int): Float = ((p - starts[part]) * total / 350f).coerceIn(0f, 1f)

    // The reels are in English whatever the phone's language, so they always read from the left.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(look.deep, Night)))
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    // A tap is "go on": the next part comes without waiting for its turn.
                    val next = starts.firstOrNull { it > p + 0.001f } ?: 1f
                    scope.launch {
                        progress.snapTo(next)
                        if (active) run()
                    }
                }
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                starts.forEachIndexed { i, start ->
                    val end = starts.getOrElse(i + 1) { 1f }
                    val filled = ((p - start) / (end - start)).coerceIn(0f, 1f)
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50)).background(Ink.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(filled).height(3.dp).background(Ink))
                    }
                }
            }
            Row(Modifier.padding(top = 14.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
                Text(
                    topicName(reel.topic),
                    Modifier.clip(RoundedCornerShape(50)).background(look.glow.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 5.dp),
                    color = look.glow,
                    style = MaterialTheme.typography.labelLarge,
                )
                val kind = when (reel.kind) {
                    Kind.NEWS -> stringResource(R.string.kind_news)
                    Kind.PUZZLE -> stringResource(R.string.kind_puzzle)
                    Kind.IDEA -> null
                }
                kind?.let { Text(it, color = Ink.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge) }
            }

            // What the reel says, at its usual size or set smaller.
            val body: @Composable (Boolean) -> Unit = { compact ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = if (compact) 6.dp else 12.dp),
                    Arrangement.spacedBy(if (compact) 9.dp else 14.dp),
                ) {
                    reel.visual?.let {
                        VisualView(it, (p * total / 1600f).coerceIn(0f, 1f), Ink, look.glow, Modifier.padding(bottom = if (compact) 0.dp else 6.dp), compact)
                    }
                    Text(reel.hook, color = Ink, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    reel.beats.forEachIndexed { i, beat ->
                        val a = shown(i + 1)
                        Text(
                            beat,
                            Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 12.dp.toPx() },
                            color = Ink.copy(alpha = 0.92f),
                            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                        )
                    }
                    val a = if (puzzle) (if (revealed) 1f else 0f) else shown(starts.lastIndex)
                    Box {
                        Text(
                            reel.takeaway,
                            Modifier
                                .graphicsLayer { alpha = a; translationY = (1f - a) * 12.dp.toPx() }
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(look.glow.copy(alpha = 0.16f))
                                .padding(if (compact) 10.dp else 14.dp),
                            color = look.glow,
                            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        // The answer's box is already in place underneath, so nothing moves when it is shown.
                        if (puzzle && !revealed && p >= 1f) {
                            OutlinedButton(
                                onClick = { revealed = true },
                                Modifier.align(Alignment.Center),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = look.glow),
                            ) { Text(stringResource(R.string.reel_reveal)) }
                        }
                    }
                    reel.source?.let {
                        Text(
                            stringResource(R.string.reel_source, it.title),
                            Modifier.clickable(onClick = onSource).padding(vertical = 4.dp),
                            color = Ink.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelLarge,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
            }
            }
            // A reel with a lot to say is set smaller, so it still fits the page. Only one that
            // doesn't fit even then can be scrolled: a page that scrolls is a page harder to swipe.
            SubcomposeLayout(Modifier.weight(1f).fillMaxWidth()) { constraints ->
                val free = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                var placed = subcompose(false) { body(false) }.first().measure(free)
                if (placed.height > constraints.maxHeight) {
                    placed = subcompose(true) {
                        Box(Modifier.verticalScroll(scroll, enabled = scroll.maxValue > 0)) { body(true) }
                    }.first().measure(constraints.copy(minHeight = 0))
                }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placed.place(0, (constraints.maxHeight - placed.height) / 2)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (liked) AppIcons.Heart else AppIcons.HeartOutline,
                        stringResource(if (liked) R.string.reel_liked else R.string.reel_like),
                        tint = if (liked) look.glow else Ink,
                    )
                }
                IconButton(onClick = onLess) { Icon(AppIcons.Less, stringResource(R.string.reel_less), tint = Ink.copy(alpha = 0.8f)) }
                Spacer(Modifier.weight(1f))
                if (lessonDone != null) {
                    Button(onClick = onLesson, colors = ButtonDefaults.buttonColors(containerColor = look.glow, contentColor = Night)) {
                        if (lessonDone) {
                            Icon(AppIcons.Check, null, Modifier.padding(end = 6.dp).width(18.dp))
                        }
                        Text(stringResource(if (lessonDone) R.string.reel_lesson_done else R.string.reel_lesson))
                    }
                }
            }
        }
    }
}
