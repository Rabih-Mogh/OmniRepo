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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.recitation.prepare.PrepareWorker
import app.recitation.prepare.Progress

/** A surah's name in the phone's language: Arabic script, or Latin letters. */
@Composable
fun surahName(surah: Surah): String =
    if (LocalConfiguration.current.locales[0].language == "ar") surah.arabic else surah.latin

/** The first screen: the playlists that have been added, and a way to add one. */
@Composable
fun PlaylistsContent(
    playlists: List<Playlist>,
    progress: Progress?,
    progressTitle: String?,
    onOpen: (Playlist) -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (progress != null) item { ProgressCard(progress, progressTitle, onCancel) }
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

/** What's being downloaded or prepared right now, with a way to stop it. */
@Composable
fun ProgressCard(progress: Progress, title: String?, onCancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(PrepareWorker.stepLabel(progress.step), (progress.fraction * 100).toInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp))
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.stop)) }
        }
    }
}

/** One playlist: its recordings, which surah each is, and getting them ready to read along with. */
@Composable
fun RecordingsContent(
    playlist: Playlist,
    progress: Progress?,
    onBack: () -> Unit,
    onOpen: (Track) -> Unit,
    onGet: (List<Track>) -> Unit,
    onCancel: () -> Unit,
    onSurah: (Track, Int) -> Unit,
    onForget: (Track) -> Unit,
    onRemove: () -> Unit,
) {
    var picking by remember { mutableStateOf<Track?>(null) }
    var menu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val waiting = playlist.tracks.filter { !it.ready && !it.wanted && it.surah > 0 }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(playlist.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.ready_of, playlist.tracks.count { it.ready }, playlist.tracks.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.remove_playlist)) }, onClick = { menu = false; removing = true })
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (progress != null) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ProgressCard(progress, playlist.tracks.firstOrNull { it.id == progress.trackId }?.title, onCancel)
                    }
                }
            }
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
                    progress = progress?.takeIf { it.trackId == track.id },
                    onOpen = { onOpen(track) },
                    onGet = { onGet(listOf(track)) },
                    onPickSurah = { picking = track },
                    onForget = { onForget(track) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
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
private fun RecordingRow(track: Track, progress: Progress?, onOpen: () -> Unit, onGet: () -> Unit, onPickSurah: () -> Unit, onForget: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val surah = SURAHS.getOrNull(track.surah - 1)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = track.ready, onClick = onOpen).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
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
            val note = when {
                track.error != null -> track.error
                progress != null -> stringResource(PrepareWorker.stepLabel(progress.step), (progress.fraction * 100).toInt())
                track.wanted -> stringResource(R.string.queued)
                track.ready && track.clarity < app.recitation.core.Timings.GOOD_ENOUGH -> stringResource(R.string.mismatch_short)
                else -> null
            }
            if (note != null) {
                Text(
                    note, style = MaterialTheme.typography.bodySmall,
                    color = if (track.error != null || (track.ready && progress == null && !track.wanted)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        when {
            progress != null -> CircularProgressIndicator(progress = { progress.fraction }, modifier = Modifier.padding(12.dp).size(24.dp), strokeWidth = 3.dp)
            track.wanted -> CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 3.dp)
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
