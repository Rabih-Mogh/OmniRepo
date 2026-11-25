package app.spark.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spark.R
import app.spark.core.Lesson
import app.spark.core.Letter
import app.spark.data.Library
import app.spark.data.Store
import kotlinx.coroutines.launch

private const val ISSUES = "https://github.com/Rabih-Mogh/OmniRepo/issues/new"

/**
 * The screen about the user: the streak and the day, the goal, what they want to learn,
 * what the app has learned about them, every lesson, and the library.
 */
@Composable
fun You(kept: Store.Kept, shelf: Library.Shelf, onBack: () -> Unit, onLesson: (Lesson) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = Store.today()
    val days = kept.days.on(today)
    val fetch by Library.state.collectAsStateWithLifecycle()
    var noBrowser by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(AppIcons.Back, stringResource(R.string.back)) }
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(18.dp), Arrangement.spacedBy(18.dp), Alignment.CenterVertically) {
                    Icon(AppIcons.Flame, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.you_streak), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(streak(kept.days.streakOn(today)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.you_today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.you_today_reels, days.reels, kept.goal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(
                                when {
                                    days.lesson -> R.string.you_lesson_done
                                    FeedState.lessonsLeft() -> R.string.you_lesson_todo
                                    else -> R.string.you_lesson_none
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            Section(stringResource(R.string.goal_title), stringResource(R.string.goal_about)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (reels in Store.GOALS) {
                        FilterChip(kept.goal == reels, onClick = { Store.goal(reels) }, label = { Text(stringResource(R.string.goal_reels, reels)) })
                    }
                }
            }
        }

        item {
            Section(stringResource(R.string.wishes_title), stringResource(R.string.wishes_about)) {
                WishEditor(kept.wishes)
                OutlinedButton(
                    onClick = {
                        val url = Uri.parse(ISSUES).buildUpon()
                            .appendQueryParameter("title", Letter.TITLE)
                            .appendQueryParameter("body", Letter.write(kept.wishes, kept.taste))
                            .build()
                        noBrowser = !openPage(context, url)
                    },
                    enabled = kept.wishes.isNotEmpty(),
                ) { Text(stringResource(R.string.wishes_send)) }
                Note(stringResource(if (noBrowser) R.string.wishes_send_failed else R.string.wishes_send_about))
            }
        }

        item {
            Section(stringResource(R.string.taste_title), stringResource(R.string.taste_about)) {
                if (kept.taste.topics.isEmpty()) Note(stringResource(R.string.taste_empty))
                for ((topic, leaning) in kept.taste.topics.entries.sortedByDescending { it.value.value }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(topicName(topic), Modifier.width(104.dp), style = MaterialTheme.typography.bodyMedium)
                        // Half full is neither here nor there; empty is always skipped, full always held.
                        LinearProgressIndicator(progress = { ((leaning.value + 1) / 2).toFloat() }, Modifier.weight(1f), drawStopIndicator = {})
                    }
                }
            }
        }

        item {
            Text(stringResource(R.string.lessons_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(shelf.lessons.values.reversed(), key = { it.id }) { lesson ->
            val score = kept.lessons[lesson.id]
            Surface(onClick = { onLesson(lesson) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lesson.title, style = MaterialTheme.typography.titleSmall)
                        Text(topicName(lesson.topic) + " · " + lesson.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    score?.let {
                        if (it.of > 0) Text(stringResource(R.string.lessons_score, it.right, it.of), style = MaterialTheme.typography.labelLarge, color = Right.ink)
                        Icon(AppIcons.Check, stringResource(R.string.reel_lesson_done), Modifier.size(20.dp), tint = Right.ink)
                    }
                }
            }
        }

        item {
            Section(stringResource(R.string.library_title), stringResource(R.string.library_count, shelf.reels.size, shelf.lessons.size, shelf.reels.keys.count { it !in kept.seen })) {
                OutlinedButton(onClick = { scope.launch { Library.refresh(context, quiet = false) } }, enabled = fetch != Library.State.Looking) {
                    Text(stringResource(R.string.library_fetch))
                }
                when (val state = fetch) {
                    Library.State.Idle -> Unit
                    Library.State.Looking -> Note(stringResource(R.string.library_looking))
                    Library.State.Failed -> Note(stringResource(R.string.library_failed))
                    is Library.State.Found ->
                        Note(if (state.reels > 0) pluralStringResource(R.plurals.library_found, state.reels, state.reels) else stringResource(R.string.library_nothing))
                }
            }
        }

        item { VersionFooter() }
    }
}

@Composable
private fun Section(title: String, about: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Note(about)
        content()
    }
}

@Composable
private fun Note(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** The list of things the user wants to learn, with a box to add to it. */
@Composable
fun WishEditor(wishes: List<String>) {
    var text by rememberSaveable { mutableStateOf("") }
    fun add() {
        Store.wish(text)
        text = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                text,
                { text = it },
                Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.wish_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            Button(onClick = ::add, enabled = text.isNotBlank()) { Text(stringResource(R.string.add)) }
        }
        if (wishes.isEmpty()) Note(stringResource(R.string.wishes_empty))
        for (wish in wishes) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(wish, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { Store.unwish(wish) }) { Icon(AppIcons.Close, stringResource(R.string.remove_item, wish), Modifier.size(20.dp)) }
            }
        }
    }
}

/** The first screen: what Spark is, and what the user wants from it. Shown once. */
@Composable
fun Welcome(kept: Store.Kept) {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.size(24.dp))
        Icon(AppIcons.Flame, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WishEditor(kept.wishes)
        Spacer(Modifier.weight(1f))
        Button(onClick = Store::welcomed, Modifier.fillMaxWidth()) { Text(stringResource(R.string.welcome_start)) }
    }
}
