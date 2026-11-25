package app.spark.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.spark.R
import app.spark.core.Lesson
import app.spark.data.Library
import app.spark.data.Store
import kotlinx.coroutines.launch

/** Opens [url] in whatever the phone uses for web pages. False when nothing does. */
fun openPage(context: Context, url: Uri): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}

/**
 * The feed: one reel to a page, swiped upwards, under a strip with the streak and how far
 * along today's goal is.
 */
@Composable
fun FeedScreen(
    feed: FeedState,
    pager: PagerState,
    shelf: Library.Shelf,
    kept: Store.Kept,
    onLesson: (Lesson) -> Unit,
    onYou: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = Store.today()
    val days = kept.days.on(today)

    LaunchedEffect(pager.settledPage) { feed.arrive(pager.settledPage) }
    // New packs may have come in while the feed had run out.
    LaunchedEffect(shelf) { feed.extend() }
    // Time spent away from the feed, in the app or out of it, isn't taken for attention.
    LaunchedEffect(Unit) { feed.resume() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { feed.resume() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { feed.pause() }

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            val label = stringResource(R.string.feed_you)
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onYou)
                    .semantics { contentDescription = label }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                Arrangement.spacedBy(6.dp),
                Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Flame, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(String.format("%d", kept.days.streakOn(today)), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.feed_progress, minOf(days.reels, kept.goal), kept.goal),
                    Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (kept.days.metOn(today)) Icon(AppIcons.Check, null, Modifier.size(16.dp), tint = Right.ink)
            }
        }

        VerticalPager(pager, Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp), pageSpacing = 10.dp) { index ->
            when (val page = feed.pages.getOrNull(index)) {
                is FeedState.Page.Show -> {
                    val reel = page.reel
                    val lesson = reel.lesson?.let(shelf.lessons::get)
                    ReelPage(
                        reel = reel,
                        active = pager.settledPage == index,
                        played = feed.played[reel.id] == true,
                        liked = reel.id in kept.liked,
                        lessonDone = lesson?.let { it.id in kept.lessons },
                        onPlayed = { feed.played[reel.id] = true },
                        onLike = { Store.like(reel, reel.id !in kept.liked) },
                        onLess = {
                            feed.leave(less = true)
                            scope.launch { pager.animateScrollToPage(index + 1) }
                        },
                        onLesson = {
                            feed.toLesson()
                            lesson?.let(onLesson)
                        },
                        onSource = { reel.source?.let { openPage(context, Uri.parse(it.url)) } },
                    )
                }

                FeedState.Page.Done -> DonePage(kept, onLesson)
                FeedState.Page.End -> EndPage(onYou)
                null -> Unit
            }
        }
    }
}

/** The gentle stop. It says where the day stands; swiping past it carries on. */
@Composable
private fun DonePage(kept: Store.Kept, onLesson: (Lesson) -> Unit) {
    val today = Store.today()
    val days = kept.days.on(today)
    val left = kept.goal - days.reels
    val lesson = if (days.lesson) null else FeedState.suggested()
    Card {
        Icon(AppIcons.Flame, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        when {
            kept.days.metOn(today) -> {
                Title(stringResource(R.string.done_title))
                Body(stringResource(if (days.lesson) R.string.done_body else R.string.done_body_no_lesson, kept.goal))
                Text(
                    stringResource(R.string.you_streak) + ": " + streak(kept.days.streakOn(today)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            left > 0 -> {
                Title(stringResource(R.string.done_reels_title))
                Body(pluralStringResource(R.plurals.done_reels_body, left, left))
            }

            else -> {
                Title(stringResource(R.string.done_lesson_title))
                Body(stringResource(R.string.done_lesson_body))
                lesson?.let { Button(onClick = { onLesson(it) }) { Text(stringResource(R.string.done_lesson_button)) } }
            }
        }
    }
}

@Composable
private fun EndPage(onYou: () -> Unit) {
    Card {
        Title(stringResource(R.string.end_title))
        Body(stringResource(R.string.end_body))
        OutlinedButton(onClick = onYou) { Text(stringResource(R.string.end_wishes)) }
    }
}

@Composable
fun streak(days: Int): String = pluralStringResource(R.plurals.days, days, days)

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(28.dp),
        Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

@Composable
private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
