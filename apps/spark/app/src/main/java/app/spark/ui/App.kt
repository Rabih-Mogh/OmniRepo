package app.spark.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spark.data.Library
import app.spark.data.Store
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Feed : Screen
    data object You : Screen
    data class Learn(val lesson: String) : Screen
}

/** The whole app: the first screen once, then the feed, with a lesson or the screen about you over it. */
@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kept by Store.kept.collectAsStateWithLifecycle()
    val shelf by Library.shelf.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.Feed) }
    // Kept here, above the screens, so the feed is where it was left after a lesson.
    val feed = remember { FeedState() }
    val pager = rememberPagerState { feed.pages.size }

    // Each time the app comes to the front it looks for the morning's batch, at most once an hour.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { scope.launch { Library.refresh(context) } }
    LaunchedEffect(shelf) { if (shelf.reels.isNotEmpty()) Store.keepOnly(shelf.reels.keys, shelf.lessons.keys) }
    BackHandler(kept.welcomed && screen != Screen.Feed) { screen = Screen.Feed }
    UpdateDialog()

    val current = screen
    val lesson = (current as? Screen.Learn)?.let { shelf.lessons[it.lesson] }
    when {
        !kept.welcomed -> Welcome(kept)
        lesson != null -> LessonScreen(lesson) { score ->
            score?.let { Store.finished(lesson, it, FeedState.lessonsLeft()) }
            screen = Screen.Feed
        }

        current == Screen.You -> You(kept, shelf, onBack = { screen = Screen.Feed }, onLesson = { screen = Screen.Learn(it.id) })
        else -> FeedScreen(feed, pager, shelf, kept, onLesson = { screen = Screen.Learn(it.id) }, onYou = {
            feed.pause()
            screen = Screen.You
        })
    }
}
