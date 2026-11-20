package app.recitation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recitation.R
import app.recitation.core.SURAHS
import app.recitation.core.Surah
import androidx.activity.compose.BackHandler
import app.recitation.data.Hit
import app.recitation.data.Playlist
import app.recitation.data.VerseRef
import kotlinx.coroutines.launch
import app.recitation.data.Track
import app.recitation.prepare.ModelProblem
import app.recitation.prepare.PrepareWorker
import app.recitation.prepare.Progress
import app.recitation.speech.ModelStore

/** A surah's name in the phone's language: Arabic script, or Latin letters. */
@Composable
fun surahName(surah: Surah): String =
    if (LocalConfiguration.current.locales[0].language == "ar") surah.arabic else surah.latin

/** What the background work is up to, for the card at the top of the lists. */
sealed interface Status {
    /** [title] is the recording being worked on; null while the speech model is fetched. */
    data class Working(val progress: Progress, val title: String?) : Status

    /** Accent Coach is installed but couldn't lend its speech model. */
    data class ModelBlocked(val problem: ModelProblem) : Status

    data object Paused : Status

    data object WaitingForNetwork : Status

    data object Starting : Status
}

/** What the buttons on the status card do. */
class StatusActions(
    val pause: () -> Unit = {},
    val resume: () -> Unit = {},
    val cancel: () -> Unit = {},
    val retryModel: (download: Boolean) -> Unit = {},
)

/**
 * The first screen, on four tabs: the Quran, every surah open to read whether or not a
 * recitation of it has been downloaded; the playlists recitations come from; [saved], the
 * pins and collections; and [stats], what has been listened to. The band at the top has the app's name between the button
 * that opens [menu] and the search button; while searching ([query] isn't null) the
 * screen shows what was found instead of the tabs.
 * [recited] holds the surahs that have a recording ready to play.
 */
@Composable
fun HomeContent(
    tab: Int,
    onTab: (Int) -> Unit,
    playlists: List<Playlist>,
    status: Status?,
    actions: StatusActions,
    recited: Set<Int>,
    onSurah: (Int) -> Unit,
    onOpen: (Playlist) -> Unit,
    onAdd: () -> Unit,
    nowPlaying: @Composable () -> Unit = {},
    lastRead: LastRead? = null,
    onLastRead: () -> Unit = {},
    saved: @Composable (Modifier) -> Unit = {},
    stats: @Composable (Modifier) -> Unit = {},
    query: String? = null,
    onQuery: (String?) -> Unit = {},
    hits: List<Hit> = emptyList(),
    onVerse: (VerseRef) -> Unit = {},
    menu: @Composable () -> Unit = {},
) {
    // The menu slides in from the side when its button is pressed, and Back closes it.
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        // Only the button opens it: a swipe from the edge is the phone's own Back.
        gesturesEnabled = drawer.isOpen,
        drawerContent = { ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) { menu() } },
    ) {
    Column(Modifier.fillMaxSize()) {
        TitleBand(query, onQuery, onMenu = { scope.launch { drawer.open() } })
        if (query != null) {
            SearchResults(query, hits, onSurah, onVerse, Modifier.weight(1f)) { surah ->
                SurahRow(surah, recited = surah.number in recited, onClick = { onSurah(surah.number) })
            }
        } else if (tab == 0) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (status != null) item { Box(Modifier.padding(16.dp)) { StatusCard(status, actions) } }
                if (lastRead != null) item { Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { LastReadCard(lastRead, onLastRead) } }
                items(SURAHS, key = { it.number }) { surah ->
                    SurahRow(surah, recited = surah.number in recited, onClick = { onSurah(surah.number) })
                    // A fine gold line from under the names to the far edge; the numbers stand clear of it.
                    HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), thickness = 0.6.dp, color = LocalHighlight.current.copy(alpha = 0.6f))
                }
            }
        } else if (tab == 2) {
            saved(Modifier.weight(1f))
        } else if (tab == 3) {
            stats(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (status != null) item { StatusCard(status, actions) }
                if (playlists.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                items(playlists, key = { it.url }) { playlist ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpen(playlist) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(playlist.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (playlist.channel.isNotEmpty()) {
                                Text(playlist.channel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                stringResource(R.string.ready_of, playlist.tracks.count { it.ready }, playlist.tracks.size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                item { ThemeButton(stringResource(R.string.add_playlist), onAdd, Modifier.fillMaxWidth(), icon = AppIcons.Add, filled = true) }
            }
        }
        nowPlaying()
        // The tabs step aside while searching.
        if (query == null) {
            BottomBand {
                Row(Modifier.fillMaxWidth()) {
                    BottomTab(stringResource(R.string.tab_quran), AppIcons.Book, selected = tab == 0, onClick = { onTab(0) }, modifier = Modifier.weight(1f))
                    BottomTab(stringResource(R.string.tab_playlists), AppIcons.Playlists, selected = tab == 1, onClick = { onTab(1) }, modifier = Modifier.weight(1f))
                    BottomTab(stringResource(R.string.tab_saved), AppIcons.Bookmark, selected = tab == 2, onClick = { onTab(2) }, modifier = Modifier.weight(1f))
                    BottomTab(stringResource(R.string.tab_stats), AppIcons.Stats, selected = tab == 3, onClick = { onTab(3) }, modifier = Modifier.weight(1f))
                }
            }
        } else {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
    }
}

/** One of the tabs at the bottom of the first screen: gold, with a short gold bar over it, when it is the one showing. */
@Composable
private fun BottomTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val band = LocalBanner.current
    // On a bar the colour of the page, the tab showing is in full colour and the others are faint.
    val ink = if (selected) band.content else band.content.faded(0.55f, band.background)
    Column(
        modifier.selectable(selected = selected, onClick = onClick, role = Role.Tab).padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(48.dp).height(3.dp).background(if (selected) band.rule else Color.Transparent))
        Spacer(Modifier.height(6.dp))
        Icon(icon, null, Modifier.size(24.dp), tint = ink)
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink)
    }
}

/**
 * Sits at the bottom of the lists while a recitation is loaded in the player: what it is,
 * play or pause, and a cross to stop it. Tapping it goes back to the surah.
 */
@Composable
fun NowPlayingBar(title: String, playing: Boolean, onPlayPause: () -> Unit, onOpen: () -> Unit, onStop: () -> Unit) {
    val band = LocalBanner.current
    Box(Modifier.clickable(onClick = onOpen)) {
        BottomBand(clearOfSystemBar = false) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlayPause) {
                    if (playing) Icon(AppIcons.Pause, stringResource(R.string.pause), tint = band.content)
                    else Icon(AppIcons.Play, stringResource(R.string.play), tint = band.content)
                }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(if (playing) R.string.now_reciting else R.string.recitation_paused),
                        style = MaterialTheme.typography.bodySmall, color = band.quiet,
                    )
                }
                IconButton(onClick = onStop) { Icon(AppIcons.Close, stringResource(R.string.stop_reciting)) }
            }
        }
    }
}

/** The place the recitation was left, for the card that offers to go back to it. */
class LastRead(val surah: Surah, val verse: Int)

/** Offers to carry on from where the recitation was left. */
@Composable
fun LastReadCard(place: LastRead, onGo: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.last_read_card, surahName(place.surah), place.verse), Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(12.dp))
            ThemeButton(stringResource(R.string.move_to_last_read), onGo, icon = AppIcons.Bookmark, filled = true)
        }
    }
}

/**
 * One surah in the Quran tab: its number under a pointed arch and its name in calligraphy.
 * The play mark says a recitation of it is ready.
 */
@Composable
private fun SurahRow(surah: Surah, recited: Boolean, onClick: () -> Unit) {
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val verses = pluralStringResource(R.plurals.verses, surah.verses, surah.verses)
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RuledNumber(surah.number, Modifier.width(44.dp))
        Spacer(Modifier.width(12.dp))
        if (arabic) {
            // In Arabic the calligraphy is the name; the Latin spelling steps back to the far side.
            Column(Modifier.weight(1f)) {
                SurahTitle(surah, 30.sp, colors.primary)
                Text(verses, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (recited) Icon(AppIcons.Play, stringResource(R.string.recitation_ready), Modifier.padding(horizontal = 8.dp).size(20.dp), tint = colors.primary)
            Text(surah.latin, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            Column(Modifier.weight(1f)) {
                Text(surah.latin, style = MaterialTheme.typography.titleMedium)
                Text("${surah.meaning} · $verses", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (recited) Icon(AppIcons.Play, stringResource(R.string.recitation_ready), Modifier.padding(horizontal = 8.dp).size(20.dp), tint = colors.primary)
            SurahTitle(surah, 30.sp, colors.primary)
        }
    }
}

/** The box a playlist's link is pasted into. */
@Composable
fun AddPlaylistDialog(link: String, busy: Boolean, error: String?, onChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.add_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = link, onValueChange = onChange, singleLine = true, enabled = !busy,
                    label = { Text(stringResource(R.string.add_link)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy && link.isNotBlank()) {
                Text(stringResource(if (busy) R.string.add_looking else R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** What the background work is doing right now, with the buttons that go with it. */
@Composable
fun StatusCard(status: Status, actions: StatusActions) {
    if (status is Status.ModelBlocked) {
        ModelProblemCard(status.problem, actions.retryModel)
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            when (status) {
                is Status.Working -> {
                    if (status.title != null) Text(status.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(PrepareWorker.stepLabel(status.progress.step), (status.progress.fraction * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(progress = { status.progress.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp))
                }
                Status.Paused -> Text(stringResource(R.string.status_paused), style = MaterialTheme.typography.bodyMedium)
                Status.WaitingForNetwork -> Text(stringResource(R.string.status_waiting), style = MaterialTheme.typography.bodyMedium)
                else -> Text(stringResource(R.string.status_starting), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.align(Alignment.End).padding(top = 8.dp, end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeButton(stringResource(R.string.cancel), actions.cancel, icon = AppIcons.Close)
                when (status) {
                    Status.Paused -> ThemeButton(stringResource(R.string.resume), actions.resume, icon = AppIcons.Play, filled = true)
                    else -> ThemeButton(stringResource(R.string.pause_work), actions.pause, icon = AppIcons.Pause)
                }
            }
        }
    }
}

/**
 * Shown when Accent Coach is on the phone but couldn't lend its speech model: says why,
 * and offers to try again once that's put right, or to download the model after all.
 */
@Composable
fun ModelProblemCard(problem: ModelProblem, onRetry: (download: Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.model_problem_title), style = MaterialTheme.typography.titleSmall)
            Text(
                when (problem.reason) {
                    ModelStore.Lender.TOO_OLD -> stringResource(R.string.model_too_old)
                    ModelStore.Lender.NO_MODEL -> stringResource(R.string.model_no_model)
                    else -> stringResource(R.string.model_refused, problem.detail.orEmpty())
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.align(Alignment.End).padding(top = 8.dp, end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeButton(stringResource(R.string.model_download_instead), { onRetry(true) }, icon = AppIcons.Download)
                ThemeButton(stringResource(R.string.try_again), { onRetry(false) }, icon = AppIcons.Retry, filled = true)
            }
        }
    }
}

/** One playlist: its recordings, which surah each is, and getting them ready to read along with. */
@Composable
fun RecordingsContent(
    playlist: Playlist,
    status: Status?,
    actions: StatusActions,
    onBack: () -> Unit,
    onOpen: (Track) -> Unit,
    onGet: (List<Track>) -> Unit,
    onCancel: (Track) -> Unit,
    onSurah: (Track, Int) -> Unit,
    onForget: (Track) -> Unit,
    onRemove: () -> Unit,
    nowPlaying: @Composable () -> Unit = {},
    onListenAgain: (Track) -> Unit = {},
) {
    var picking by remember { mutableStateOf<Track?>(null) }
    var menu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val waiting = playlist.tracks.filter { !it.ready && !it.wanted && it.surah > 0 }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        Banner {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(playlist.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.ready_of, playlist.tracks.count { it.ready }, playlist.tracks.size),
                        style = MaterialTheme.typography.bodySmall, color = LocalBanner.current.quiet,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_playlist)) }, onClick = { menu = false; removing = true })
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (status != null) item { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { StatusCard(status, actions) } }
            if (waiting.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ThemeButton(pluralStringResource(R.plurals.get_all, waiting.size, waiting.size), { onGet(waiting) }, Modifier.fillMaxWidth(), icon = AppIcons.Download)
                        Text(
                            stringResource(R.string.get_all_note), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            items(playlist.tracks, key = { it.id }) { track ->
                RecordingRow(
                    track = track,
                    status = status,
                    onOpen = { onOpen(track) },
                    onGet = { onGet(listOf(track)) },
                    onCancel = { onCancel(track) },
                    onPickSurah = { picking = track },
                    onForget = { onForget(track) },
                    onListenAgain = { onListenAgain(track) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        nowPlaying()
    }

    picking?.let { track ->
        SurahPicker(onPick = { onSurah(track, it); picking = null }, onDismiss = { picking = null })
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.remove_playlist)) },
            text = { Text(stringResource(R.string.remove_playlist_body)) },
            confirmButton = { TextButton(onClick = { removing = false; onRemove() }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun RecordingRow(
    track: Track,
    status: Status?,
    onOpen: () -> Unit,
    onGet: () -> Unit,
    onCancel: () -> Unit,
    onPickSurah: () -> Unit,
    onForget: () -> Unit,
    onListenAgain: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val surah = SURAHS.getOrNull(track.surah - 1)
    val working = (status as? Status.Working)?.progress
    val progress = working?.takeIf { it.trackId == track.id }
    val listening = progress != null || track.wanted
    // Tapping a recording opens its surah: to play along if it is ready, otherwise just to read.
    Row(
        Modifier.fillMaxWidth().clickable(enabled = surah != null, onClick = onOpen).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onPickSurah,
                    label = { Text(if (surah != null) stringResource(R.string.surah_chip, surah.number, surahName(surah)) else stringResource(R.string.choose_surah)) },
                    colors = if (surah != null) AssistChipDefaults.assistChipColors() else AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error),
                )
                Text(clock((track.seconds * 1000).toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val mismatch = track.ready && track.clarity < app.recitation.core.Timings.GOOD_ENOUGH
            val note = when {
                track.error != null -> track.error
                progress != null -> stringResource(PrepareWorker.stepLabel(progress.step), (progress.fraction * 100).toInt())
                // Say what the queue is really waiting for, so it never looks stuck.
                track.wanted -> when {
                    status == Status.Paused -> stringResource(R.string.paused)
                    status == Status.WaitingForNetwork -> stringResource(R.string.status_waiting)
                    status is Status.ModelBlocked -> stringResource(R.string.queued_for_model)
                    working != null && working.trackId == null -> stringResource(PrepareWorker.stepLabel(working.step), (working.fraction * 100).toInt())
                    else -> stringResource(R.string.queued)
                }
                mismatch -> stringResource(R.string.mismatch_short)
                // It plays, but the listening was stopped before the end.
                track.canPlay && !track.ready -> stringResource(R.string.heard_in_part)
                else -> null
            }
            if (note != null) {
                Text(
                    note, style = MaterialTheme.typography.bodySmall,
                    color = if (track.error != null || (mismatch && !track.wanted && progress == null)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        when {
            track.ready -> IconButton(onClick = onOpen) { Icon(AppIcons.Play, stringResource(R.string.play), tint = MaterialTheme.colorScheme.primary) }
            // It can be played as soon as its sound is here, while the listening carries on. (Cancel moves to the menu.)
            listening && track.canPlay -> IconButton(onClick = onOpen) { Icon(AppIcons.Play, stringResource(R.string.play), tint = MaterialTheme.colorScheme.primary) }
            listening -> IconButton(onClick = onCancel) { Icon(AppIcons.Close, stringResource(R.string.cancel)) }
            track.error != null -> IconButton(onClick = onGet, enabled = surah != null) { Icon(AppIcons.Retry, stringResource(R.string.try_again), tint = MaterialTheme.colorScheme.primary) }
            else -> IconButton(onClick = onGet, enabled = surah != null) {
                Icon(AppIcons.Download, stringResource(R.string.get), tint = if (surah != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (listening && track.canPlay) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.stop_listening)) }, onClick = { menu = false; onCancel() })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.choose_surah)) }, onClick = { menu = false; onPickSurah() })
                // What the model heard isn't kept once the words are placed, so placing them better means hearing it again.
                if (track.ready) DropdownMenuItem(text = { Text(stringResource(R.string.listen_again)) }, onClick = { menu = false; onListenAgain() })
                DropdownMenuItem(text = { Text(stringResource(R.string.forget)) }, onClick = { menu = false; onForget() })
            }
        }
    }
}

/** All 114 surahs, to say which one a recording is. */
@Composable
fun SurahPicker(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_surah)) },
        text = {
            LazyColumn(Modifier.height(420.dp)) {
                items(SURAHS) { surah ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(surah.number) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${surah.number}", Modifier.width(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(surah.latin, Modifier.weight(1f))
                        Text(surah.arabic)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
