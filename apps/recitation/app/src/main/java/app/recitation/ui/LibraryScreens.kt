package app.recitation.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.recitation.R
import app.recitation.core.SURAHS
import app.recitation.core.Surah
import app.recitation.data.Playlist
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
 * The first screen, on two tabs: the Quran, every surah open to read whether or not a
 * recitation of it has been downloaded, and the playlists recitations come from.
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
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        Banner {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.8f))
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { onTab(0) }, text = { Text(stringResource(R.string.tab_quran)) })
            Tab(selected = tab == 1, onClick = { onTab(1) }, text = { Text(stringResource(R.string.tab_playlists)) })
        }
        if (tab == 0) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (status != null) item { Box(Modifier.padding(16.dp)) { StatusCard(status, actions) } }
                items(SURAHS, key = { it.number }) { surah ->
                    SurahRow(surah, recited = surah.number in recited, onClick = { onSurah(surah.number) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
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
                item {
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_playlist))
                    }
                }
                item { VersionFooter() }
            }
        }
        nowPlaying()
    }
}

/**
 * Sits at the bottom of the lists while a recitation is loaded in the player: what it is,
 * play or pause, and a cross to stop it. Tapping it goes back to the surah.
 */
@Composable
fun NowPlayingBar(title: String, playing: Boolean, onPlayPause: () -> Unit, onOpen: () -> Unit, onStop: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlayPause) {
                if (playing) Icon(AppIcons.Pause, stringResource(R.string.pause), tint = MaterialTheme.colorScheme.primary)
                else Icon(Icons.Filled.PlayArrow, stringResource(R.string.play), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (playing) R.string.now_reciting else R.string.recitation_paused),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onStop) { Icon(Icons.Filled.Close, stringResource(R.string.stop_reciting)) }
        }
    }
}

/** One surah in the Quran tab. The play mark says a recitation of it is ready. */
@Composable
private fun SurahRow(surah: Surah, recited: Boolean, onClick: () -> Unit) {
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.number, surah.number), Modifier.width(44.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(surahName(surah), style = MaterialTheme.typography.titleMedium)
            val verses = pluralStringResource(R.plurals.verses, surah.verses, surah.verses)
            Text(
                if (arabic) verses else "${surah.meaning} · $verses",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (recited) Icon(Icons.Filled.PlayArrow, stringResource(R.string.recitation_ready), Modifier.padding(horizontal = 8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
        // The name in the other script, for whoever knows the surahs by it.
        Text(if (arabic) surah.latin else surah.arabic, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = actions.cancel) { Text(stringResource(R.string.cancel)) }
                when (status) {
                    Status.Paused -> TextButton(onClick = actions.resume) { Text(stringResource(R.string.resume)) }
                    else -> TextButton(onClick = actions.pause) { Text(stringResource(R.string.pause_work)) }
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
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = { onRetry(true) }) { Text(stringResource(R.string.model_download_instead)) }
                TextButton(onClick = { onRetry(false) }) { Text(stringResource(R.string.try_again)) }
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
                        style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.8f),
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
                        FilledTonalButton(onClick = { onGet(waiting) }, modifier = Modifier.fillMaxWidth()) {
                            Text(pluralStringResource(R.plurals.get_all, waiting.size, waiting.size))
                        }
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
) {
    var menu by remember { mutableStateOf(false) }
    val surah = SURAHS.getOrNull(track.surah - 1)
    val working = (status as? Status.Working)?.progress
    val progress = working?.takeIf { it.trackId == track.id }
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
            progress != null || track.wanted -> IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
            track.ready -> IconButton(onClick = onOpen) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.play), tint = MaterialTheme.colorScheme.primary) }
            track.error != null -> IconButton(onClick = onGet, enabled = surah != null) { Icon(Icons.Filled.Refresh, stringResource(R.string.try_again)) }
            else -> IconButton(onClick = onGet, enabled = surah != null) { Icon(AppIcons.Download, stringResource(R.string.get)) }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.choose_surah)) }, onClick = { menu = false; onPickSurah() })
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
