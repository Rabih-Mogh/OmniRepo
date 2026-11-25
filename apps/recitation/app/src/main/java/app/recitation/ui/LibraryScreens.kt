package app.recitation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recitation.R
import app.recitation.core.SURAHS
import app.recitation.core.Surah
import app.recitation.data.Hit
import app.recitation.data.Playlist
import app.recitation.data.VerseRef
import app.recitation.data.Summary
import app.recitation.data.Track
import app.recitation.prepare.PrepareWorker
import app.recitation.prepare.Progress

/**
 * A surah's name. The app is for readers whose first language is Arabic and whose second is
 * English, so the Arabic name always comes first: alone in Arabic, and in English with
 * the Latin spelling after it.
 */
@Composable
fun surahName(surah: Surah): String =
    if (LocalConfiguration.current.locales[0].language == "ar") surah.arabic else arabicThenLatin(surah)

fun arabicThenLatin(surah: Surah): String = "${surah.arabic} · ${surah.latin}"

/** What the background work is up to, for the card at the top of the lists. */
sealed interface Status {
    /**
     * [title] is the recording being worked on and [surah] its surah; null and 0 while the
     * speech model is fetched. [fetching] is another recording, whose sound is being
     * downloaded meanwhile.
     */
    data class Working(val progress: Progress, val title: String?, val surah: Int = 0, val fetching: Working? = null) : Status {
        /** The work on the recording [trackId], which may be the download beside the rest; null when none of it is for that recording. */
        fun on(trackId: String?): Working? = when {
            trackId == null -> null
            progress.trackId == trackId -> this
            else -> fetching?.on(trackId)
        }

        /** The work on a recording of the surah [number]: likewise. */
        fun onSurah(number: Int): Working? = when {
            number <= 0 -> null
            surah == number -> this
            else -> fetching?.onSurah(number)
        }
    }

    data object Paused : Status

    data object WaitingForNetwork : Status

    /** YouTube wouldn't let the sound be fetched; it is asked again after a while. */
    data object Refused : Status

    data object Starting : Status
}

/** What the buttons on the status card do. */
class StatusActions(
    val pause: () -> Unit = {},
    val resume: () -> Unit = {},
    val cancel: () -> Unit = {},
)

/**
 * The first screen, on five tabs: the Quran, every surah open to read whether or not a
 * recitation of it has been downloaded; the playlists recitations come from; [saved], the
 * pins and collections; [stats], what has been listened to; and [practice], reciting to the phone and the voice exercises. The band at the top has the app's name between the button
 * that opens [menu] and the search button; while searching ([query] isn't null) the
 * screen shows what was found instead of the tabs.
 * [recited] holds the surahs that have a recitation downloaded: at least one recording whose sound is on the phone.
 * [unheard] holds those of them with no recording that has been listened to the end yet,
 * and [queued] the surahs with a recording waiting its turn to be got ready. The surah
 * being worked on right now is known from [status].
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
    practice: @Composable (Modifier) -> Unit = {},
    query: String? = null,
    onQuery: (String?) -> Unit = {},
    hits: List<Hit> = emptyList(),
    onVerse: (VerseRef) -> Unit = {},
    menu: @Composable () -> Unit = {},
    unheard: Set<Int> = emptySet(),
    queued: Set<Int> = emptySet(),
    streak: Summary? = null,
) {
    // Where the work is: the surah being worked on shows the work's picture under its number.
    val workingOn = status as? Status.Working
    val row: @Composable (Surah) -> Unit = { surah ->
        SurahRow(
            surah, recited = surah.number in recited, heard = surah.number !in unheard, queued = surah.number in queued,
            working = workingOn?.onSurah(surah.number), onClick = { onSurah(surah.number) },
        )
    }
    // The menu's panel comes in beside the page when its button is pressed, pushing the page
    // aside; its cross, a tap on the page, or Back closes it.
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    SidePanel(open = menuOpen, onClose = { menuOpen = false }, title = stringResource(R.string.app_name), panel = { menu() }) {
    Column(Modifier.fillMaxSize()) {
        TitleBand(query, onQuery, onMenu = { menuOpen = true }, streak = streak)
        if (query != null) {
            SearchResults(query, hits, onSurah, onVerse, Modifier.weight(1f)) { surah -> row(surah) }
        } else if (tab == 0) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                // What the app is at is in the top bar here, and opens from there: the list is for the surahs.
                if (lastRead != null) item { Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { LastReadCard(lastRead, onLastRead) } }
                items(SURAHS, key = { it.number }) { surah ->
                    row(surah)
                    // A faint line between surahs, the same margin at either end. A whole dp thick: a
                    // thinner one lands on the screen's pixels unevenly, and the lines come out unlike.
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } else if (tab == 2) {
            saved(Modifier.weight(1f))
        } else if (tab == 3) {
            stats(Modifier.weight(1f))
        } else if (tab == 4) {
            practice(Modifier.weight(1f))
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
                    PlaylistCard(playlist, status, onClick = { onOpen(playlist) })
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
                    BottomTab(stringResource(R.string.tab_practice), AppIcons.Mic, selected = tab == 4, onClick = { onTab(4) }, modifier = Modifier.weight(1f))
                }
            }
        } else {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
    }
}

/**
 * A playlist in the list of them: a ring that fills with the share of its recordings that
 * are ready, or holds the picture of the work while one of them is being worked on; its
 * title and channel; and, in the app's own marks, how many are ready, how many wait their
 * turn and how many couldn't be got ready.
 */
@Composable
private fun PlaylistCard(playlist: Playlist, status: Status?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ready = playlist.tracks.count { it.ready }
    val waiting = playlist.tracks.count { it.wanted }
    val failed = playlist.tracks.count { it.error != null }
    val work = (status as? Status.Working)?.let { all -> playlist.tracks.firstNotNullOfOrNull { all.on(it.id) } }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkIcon(
                work?.picture() ?: Picture(AppIcons.Playlists, fraction = if (playlist.tracks.isEmpty()) 0f else ready.toFloat() / playlist.tracks.size),
                work?.says(), size = 46.dp, ground = colors.surfaceContainerLow,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(playlist.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (playlist.channel.isNotEmpty()) {
                    Text(playlist.channel, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ReadyCount(playlist, colors.primary)
                    if (waiting > 0) Count(AppIcons.Hourglass, waiting, pluralStringResource(R.plurals.count_waiting, waiting, waiting), colors.onSurfaceVariant)
                    if (failed > 0) Count(AppIcons.Warning, failed, pluralStringResource(R.plurals.count_failed, failed, failed), colors.error)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.outline)
        }
    }
}

/** A mark with a figure beside it; [says] is what the two mean together. */
@Composable
private fun Count(icon: ImageVector, count: Int, says: String, color: Color) {
    Row(Modifier.clearAndSetSemantics { contentDescription = says }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Text(stringResource(R.string.number, count), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** How many of a playlist's recordings are ready to read along with, of how many: the bars of sound and the two figures. */
@Composable
private fun ReadyCount(playlist: Playlist, color: Color) {
    val ready = playlist.tracks.count { it.ready }
    val says = stringResource(R.string.ready_of, ready, playlist.tracks.size)
    Row(Modifier.clearAndSetSemantics { contentDescription = says }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(AppIcons.Sound, null, Modifier.size(14.dp), tint = color)
        Text(stringResource(R.string.count_of, ready, playlist.tracks.size), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** One of the tabs at the bottom of the first screen. The one showing has a short line of the bar's accent at the bar's top edge, and is bright; the others are faint. */
@Composable
private fun BottomTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val band = LocalBanner.current
    val ink = if (selected) band.content else band.content.faded(0.7f, band.background)
    Box(modifier.selectable(selected = selected, onClick = onClick, role = Role.Tab), contentAlignment = Alignment.TopCenter) {
        if (selected) Box(Modifier.width(44.dp).height(3.dp).background(band.accent, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)))
        Column(Modifier.padding(top = 10.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(24.dp), tint = ink)
            Spacer(Modifier.height(3.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = ink)
        }
    }
}

/**
 * Sits at the bottom of the lists while a recitation is loaded in the player, and does
 * there what the player in the surah does: along its top edge a thin line for how far
 * the recitation has got; previous verse, play or pause, next verse; the surah's number
 * and name with the verse being recited and the time gone of the whole; and a cross to
 * stop it. Tapping it goes back to the surah. [verse] is counted from 1, 0 when it isn't
 * known; the verse buttons are there only where the verses' starts are.
 */
@Composable
fun NowPlayingBar(
    surah: Surah,
    playing: Boolean,
    position: Int,
    duration: Int,
    verse: Int,
    onPlayPause: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onOpen: () -> Unit,
    onStop: () -> Unit,
) {
    val band = LocalBanner.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(Modifier.clickable(onClick = onOpen).testTag("now-playing")) {
        BottomBand(clearOfSystemBar = false) {
            Column {
                // The time line runs left to right in every language, as in the surah's player.
                Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                    drawRect(band.content.faded(0.25f, band.background))
                    if (duration > 0) drawRect(band.accent, size = Size(size.width * (position.toFloat() / duration).coerceIn(0f, 1f), size.height))
                }
                Row(Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onPrevious != null) IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                        Icon(AppIcons.SkipPrevious, stringResource(R.string.previous_verse), Modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f), tint = band.content)
                    }
                    PlayButton(playing, onPlayPause)
                    if (onNext != null) IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                        Icon(AppIcons.SkipNext, stringResource(R.string.next_verse), Modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f), tint = band.content)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.number, surah.number), style = MaterialTheme.typography.titleSmall, color = band.quiet)
                            SurahTitle(surah, 22.sp, band.content)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Reciting or paused, as a picture: the bars of sound, or the pause mark.
                            Icon(
                                if (playing) AppIcons.Sound else AppIcons.Pause, stringResource(if (playing) R.string.now_reciting else R.string.recitation_paused),
                                Modifier.size(13.dp), tint = band.quiet,
                            )
                            val small = MaterialTheme.typography.labelSmall
                            if (verse > 0) Text(stringResource(R.string.verse, verse), style = small, color = band.content, maxLines = 1)
                            if (duration > 0) Text("${clock(position)} / ${clock(duration)}", style = small, color = band.quiet, maxLines = 1)
                        }
                    }
                    IconButton(onClick = onStop) { Icon(AppIcons.Close, stringResource(R.string.stop_reciting), tint = band.content) }
                }
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
 * The mark under its number says a recitation of it is on the phone, and whether it has been listened to the end ([heard]),
 * is waiting its turn ([queued]) or is being worked on right now ([working]).
 */
@Composable
private fun SurahRow(surah: Surah, recited: Boolean, heard: Boolean, queued: Boolean, working: Status.Working?, onClick: () -> Unit) {
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val verses = pluralStringResource(R.plurals.verses, surah.verses, surah.verses)
    val colors = MaterialTheme.colorScheme
    // Room to breathe: the rows are the page, so each gets a comfortable height.
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        // A surah with a recitation downloaded is told apart by its number: in colour, with the bars of sound under it.
        // Until it has been listened to the end the mark says where it stands: being worked on, waiting, or left off.
        SurahNumber(surah.number, Modifier.width(44.dp), recitation = recited, heard = heard, queued = queued, working = working?.picture(), workSays = working?.says())
        Spacer(Modifier.width(12.dp))
        if (arabic) {
            // In Arabic the calligraphy is the name; the Latin spelling steps back to the far side.
            Column(Modifier.weight(1f)) {
                SurahTitle(surah, 30.sp, colors.primary)
                Text(verses, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Text(surah.latin, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            // In English too the Arabic name comes first; its Latin spelling and meaning follow, at the far side.
            SurahTitle(surah, 32.sp, colors.primary, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(surah.latin, style = MaterialTheme.typography.titleMedium)
                Text("${surah.meaning} · $verses", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
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
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
    onListenToVerses: (Track, IntRange) -> Unit = { _, _ -> },
) {
    var picking by remember { mutableStateOf<Track?>(null) }
    var again by remember { mutableStateOf<Track?>(null) }
    var menu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val waiting = playlist.tracks.filter { !it.ready && !it.wanted && it.surah > 0 }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        Banner {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(playlist.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ReadyCount(playlist, LocalBanner.current.quiet)
                }
                DoingChip()
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
                    ThemeButton(
                        pluralStringResource(R.plurals.get_all, waiting.size, waiting.size), { onGet(waiting) },
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), icon = AppIcons.Download,
                    )
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
                    onListenToVerses = { again = track },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        nowPlaying()
    }

    picking?.let { track ->
        SurahPicker(onPick = { onSurah(track, it); picking = null }, onDismiss = { picking = null })
    }
    again?.let { track ->
        val verses = SURAHS.getOrNull(track.surah - 1)?.verses ?: 1
        var range by remember(track.id) { mutableStateOf(0..0) }
        AlertDialog(
            onDismissRequest = { again = null },
            title = { Text(stringResource(R.string.listen_again_verses_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.listen_again_verses_body), style = MaterialTheme.typography.bodyMedium)
                    RangeChooser(verses, range) { range = it }
                }
            },
            confirmButton = { TextButton(onClick = { again = null; onListenToVerses(track, range) }) { Text(stringResource(R.string.listen_again_verses_go)) } },
            dismissButton = { TextButton(onClick = { again = null }) { Text(stringResource(R.string.cancel)) } },
        )
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

/**
 * One recording, laid out like a surah in the Quran tab: its surah's number, with the mark
 * under it for where its recitation stands (the bars of sound, the work in its ring, an
 * hourglass or the picture of what it waits for); the surah's name in calligraphy; under
 * that the recording's own title and how long it is; then what can be done with it. Only
 * what went wrong is put in words.
 */
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
    onListenToVerses: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val surah = SURAHS.getOrNull(track.surah - 1)
    val work = (status as? Status.Working)?.on(track.id)
    val listening = work != null || track.wanted
    // What a recording in the queue is really waiting for, so it never looks stuck: that
    // thing's own picture, where it isn't simply the recording's turn. The words for it are the short ones.
    val waitsFor: Status? = if (work != null || !track.wanted) null else status.takeIf {
        // The speech model or the engine, which every recording waits for, being fetched; or a state of waiting.
        (it is Status.Working && it.progress.trackId == null) || it == Status.Paused || it == Status.WaitingForNetwork || it == Status.Refused
    }
    val waitsSays = when (waitsFor) {
        null -> null
        Status.Paused -> stringResource(R.string.paused)
        Status.WaitingForNetwork -> stringResource(R.string.status_waiting)
        Status.Refused -> stringResource(R.string.status_refused_short)
        else -> waitsFor.says()
    }
    val mismatch = track.ready && track.clarity < app.recitation.core.Timings.GOOD_ENOUGH
    // Tapping a recording opens its surah: to play along if it is ready, otherwise just to read.
    Row(
        Modifier.fillMaxWidth().clickable(enabled = surah != null, onClick = onOpen).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (surah != null) {
            SurahNumber(
                surah.number, Modifier.width(44.dp), recitation = track.canPlay, heard = track.ready,
                // A ready one waiting to be listened to again by itself isn't shown as waiting: nothing about it is.
                queued = track.wanted && !track.ready && work == null && waitsFor == null,
                working = work?.picture() ?: waitsFor?.picture(), workSays = work?.says() ?: waitsSays,
            )
        } else {
            // Which surah it is isn't known: nothing can be done with it until that is said.
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { Icon(AppIcons.Warning, null, Modifier.size(22.dp), tint = colors.error) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (surah != null) {
                SurahTitle(surah, 28.sp, if (track.canPlay) colors.primary else colors.onSurface)
            } else {
                Text(
                    stringResource(R.string.choose_surah), Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onPickSurah),
                    style = MaterialTheme.typography.titleMedium, color = colors.error,
                )
            }
            Text(track.title, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(clock((track.seconds * 1000).toInt()), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                // How the listening compares with playing the recording.
                if (work != null && work.progress.speed > 0f) Text(times(work.progress.speed), style = MaterialTheme.typography.labelMedium, color = colors.primary)
            }
            when {
                track.error != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(AppIcons.Warning, null, Modifier.size(16.dp), tint = colors.error)
                    Text(track.error, style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
                mismatch && !listening -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(AppIcons.Warning, null, Modifier.size(16.dp), tint = colors.error)
                    Text(stringResource(R.string.mismatch_short), style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
            }
        }
        when {
            track.ready -> IconButton(onClick = onOpen) { Icon(AppIcons.Play, stringResource(R.string.play), tint = colors.primary) }
            // It can be played as soon as its sound is here, while the listening carries on. (Cancel moves to the menu.)
            listening && track.canPlay -> IconButton(onClick = onOpen) { Icon(AppIcons.Play, stringResource(R.string.play), tint = colors.primary) }
            listening -> IconButton(onClick = onCancel) { Icon(AppIcons.Close, stringResource(R.string.cancel), tint = colors.onSurfaceVariant) }
            track.error != null -> IconButton(onClick = onGet, enabled = surah != null) { Icon(AppIcons.Retry, stringResource(R.string.try_again), tint = colors.primary) }
            else -> IconButton(onClick = onGet, enabled = surah != null) {
                Icon(AppIcons.Download, stringResource(R.string.get), tint = if (surah != null) colors.primary else colors.outline)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more), tint = colors.onSurfaceVariant) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (listening && track.canPlay) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.stop_listening)) }, onClick = { menu = false; onCancel() })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.choose_surah)) }, onClick = { menu = false; onPickSurah() })
                // What the model heard isn't kept once the words are placed, so placing them better means hearing it again.
                if (track.ready) DropdownMenuItem(text = { Text(stringResource(R.string.listen_again)) }, onClick = { menu = false; onListenAgain() })
                if (track.ready && surah != null) DropdownMenuItem(text = { Text(stringResource(R.string.listen_again_verses)) }, onClick = { menu = false; onListenToVerses() })
                DropdownMenuItem(text = { Text(stringResource(R.string.forget)) }, onClick = { menu = false; onForget() })
            }
        }
    }
}

/**
 * All 114 surahs: to say which one a recording is, or to go to another from the one that
 * is open ([current], which the list opens at and marks).
 */
@Composable
fun SurahPicker(onPick: (Int) -> Unit, onDismiss: () -> Unit, current: Int = 0) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_surah)) },
        text = {
            LazyColumn(Modifier.height(420.dp), state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))) {
                items(SURAHS) { surah ->
                    val here = surah.number == current
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (here) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onPick(surah.number) }.padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${surah.number}", Modifier.width(40.dp), color = if (here) colors.primary else colors.onSurfaceVariant)
                        SurahTitle(surah, 24.sp, if (here) colors.primary else colors.onSurface, Modifier.weight(1f))
                        Text(surah.latin, color = if (here) colors.primary else colors.onSurface)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
