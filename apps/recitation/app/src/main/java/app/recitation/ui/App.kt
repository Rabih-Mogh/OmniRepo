package app.recitation.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.recitation.AppModel
import app.recitation.R
import app.recitation.Reading
import app.recitation.Screen
import app.recitation.core.SURAHS
import app.recitation.data.Place
import app.recitation.data.Playlist
import app.recitation.data.Settings
import app.recitation.data.SurahNames
import app.recitation.data.Track
import app.recitation.player.Artwork
import app.recitation.player.PlaybackService
import app.recitation.prepare.PrepareWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The whole app: which screen shows, and the pop-ups over it. */
@Composable
fun App(model: AppModel) {
    BackHandler(enabled = model.screen != Screen.Home) { model.back() }
    UpdateDialog()

    // The surahs' names in calligraphy, once that font has been fetched; plainer calligraphy until then.
    val surahNames by SurahNames.font.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalSurahNames provides surahNames) { Screens(model) }
}

@Composable
private fun Screens(model: AppModel) {
    val context = LocalContext.current
    val playlists by model.library.playlists.collectAsStateWithLifecycle()
    val progress by PrepareWorker.progress.collectAsStateWithLifecycle()
    val modelProblem by PrepareWorker.modelProblem.collectAsStateWithLifecycle()

    // One line to the player for the whole app, so leaving a surah, or the app, doesn't stop the recitation.
    val playback = rememberPlayback(onMovedOn = model::playing)

    // Preparing shows its progress in a notification; Android 13+ wants a yes for that. It runs either way.
    var asked by remember { mutableStateOf<List<Track>?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked?.let(model::want)
        asked = null
    }
    val get: (List<Track>) -> Unit = { tracks ->
        val needsAsking = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsking) {
            asked = tracks
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            model.want(tracks)
        }
    }

    // One line of truth about the background work, so nothing ever just sits there unexplained.
    val tracks = playlists.flatMap { it.tracks }
    val working = progress
    val problem = modelProblem
    val status: Status? = when {
        working != null -> Status.Working(working, working.trackId?.let { id -> tracks.firstOrNull { it.id == id }?.title })
        tracks.none { it.wanted && !it.ready } -> null
        model.paused -> Status.Paused
        problem != null -> Status.ModelBlocked(problem)
        model.waitingForNetwork -> Status.WaitingForNetwork
        else -> Status.Starting
    }
    val actions = remember(model) { StatusActions(model::pause, model::resume, model::cancel, model::retryModel) }

    // What is reciting in the background, on the screens that aren't its surah.
    val nowPlaying: @Composable () -> Unit = {
        val reciting = playback.trackId?.let { id -> tracks.firstOrNull { it.id == id } }
        val player = playback.controller
        if (reciting != null && player != null) {
            NowPlayingBar(
                title = surahName(SURAHS[reciting.surah - 1]),
                playing = playback.playing,
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onOpen = { model.openSurah(reciting.surah, prefer = reciting) },
                onStop = { player.stop(); player.clearMediaItems() },
            )
        }
    }

    when (val screen = model.screen) {
        Screen.Home -> HomeContent(
            tab = model.tab,
            onTab = { model.tab = it },
            playlists = playlists,
            status = status,
            actions = actions,
            recited = tracks.filter { it.ready }.map { it.surah }.toSet(),
            onSurah = { model.openSurah(it) },
            onOpen = model::open,
            onAdd = { model.link = "" },
            nowPlaying = nowPlaying,
            // Not offered while that very recording is the one playing.
            lastRead = model.lastReadable()?.takeIf { it.trackId != playback.trackId }?.let { LastRead(SURAHS[it.surah - 1], it.verse) },
            onLastRead = { model.lastReadable()?.let(model::continueFrom) },
        )
        is Screen.Recordings -> {
            val playlist = playlists.firstOrNull { it.url == screen.url }
            if (playlist == null) {
                LaunchedEffect(Unit) { model.back() }
            } else {
                RecordingsContent(
                    playlist = playlist,
                    status = status,
                    actions = actions,
                    onBack = { model.back() },
                    onOpen = { model.openSurah(it.surah, prefer = it) },
                    onGet = get,
                    onCancel = model::cancel,
                    onSurah = model::setSurah,
                    onForget = model::forget,
                    onRemove = { model.remove(playlist) },
                    nowPlaying = nowPlaying,
                )
            }
        }
        is Screen.Surah -> SurahScreen(model, screen, playlists, status, playback, get)
    }

    model.link?.let { link ->
        AddPlaylistDialog(
            link = link, busy = model.adding, error = model.addError,
            onChange = { model.link = it },
            onConfirm = { model.add(link) },
            onDismiss = { if (!model.adding) model.link = null },
        )
    }
}

/** A surah open to read: with its recitation playing along when one is ready, and on its own when not. */
@Composable
private fun SurahScreen(model: AppModel, screen: Screen.Surah, playlists: List<Playlist>, status: Status?, playback: Playback, get: (List<Track>) -> Unit) {
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    // Word meanings are in English, so they start hidden when the phone is in Arabic. The choice is kept.
    val context = LocalContext.current
    var meanings by rememberSaveable { mutableStateOf(Settings.wordMeanings(context, byDefault = !arabic)) }
    val setMeanings: (Boolean) -> Unit = {
        meanings = it
        Settings.setWordMeanings(context, it)
    }
    var textScale by remember { mutableFloatStateOf(Settings.textScale(context)) }
    val setTextScale: (Float) -> Unit = {
        textScale = it
        Settings.setTextScale(context, it)
    }
    val reading = model.reading
    val track = reading?.track
    when {
        reading == null || reading.surah != screen.number ->
            ReaderLoading(failed = model.readingFailed, onRetry = model::reload, onBack = { model.back() })
        track != null && reading.timings != null ->
            PlayingReader(model, reading, track, playlists, playback, meanings, setMeanings, textScale, setTextScale)
        else -> ReaderContent(
            reading = reading, showMeanings = meanings, playing = false, position = { 0 }, duration = 0,
            onBack = { model.back() }, onMeanings = setMeanings, onPlayPause = {}, onSeek = {},
            textScale = textScale, onTextScale = setTextScale,
            withoutRecitation = {
                // Looked up afresh each time the playlists change, so the bar keeps up with the download.
                val recording = remember(playlists, screen.number) { model.anyRecordingOf(screen.number) }
                val working = (status as? Status.Working)?.progress?.takeIf { recording != null && it.trackId == recording.id }
                when {
                    recording == null -> NoRecitationBar(stringResource(R.string.no_recitation))
                    recording.ready -> NoRecitationBar(stringResource(R.string.recitation_ready), stringResource(R.string.play), AppIcons.Play) { model.openSurah(screen.number, recording) }
                    working != null -> NoRecitationBar(stringResource(PrepareWorker.stepLabel(working.step), (working.fraction * 100).toInt()))
                    recording.wanted -> NoRecitationBar(stringResource(if (status == Status.Paused) R.string.paused else R.string.queued))
                    recording.error != null -> NoRecitationBar(recording.error, stringResource(R.string.try_again), AppIcons.Retry) { get(listOf(recording)) }
                    else -> NoRecitationBar(stringResource(R.string.recitation_not_ready), stringResource(R.string.get), AppIcons.Download) { get(listOf(recording)) }
                }
            },
        )
    }
}

/** The line to the playback service, and what it is playing. */
class Playback {
    var controller by mutableStateOf<MediaController?>(null)
    var playing by mutableStateOf(false)
    var duration by mutableIntStateOf(0)

    /** The recording loaded in the player, playing or paused; null when there is none. */
    var trackId by mutableStateOf<String?>(null)

    fun read(player: Player) {
        playing = player.isPlaying
        duration = player.duration.coerceAtLeast(0).toInt()
        trackId = player.currentMediaItem?.mediaId
    }
}

/**
 * Connects to the playback service for as long as the app's screen exists. The service
 * itself outlives the screen while it is reciting. [onMovedOn] is told when the player
 * goes on to the next recording.
 */
@Composable
private fun rememberPlayback(onMovedOn: (String) -> Unit): Playback {
    val context = LocalContext.current.applicationContext
    val playback = remember { Playback() }
    // Counts up when the service goes away (Android stopped it while nothing was playing), to connect afresh.
    var connection by remember { mutableIntStateOf(0) }
    DisposableEffect(connection) {
        var open = true
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = playback.read(player)

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let(onMovedOn)
            }
        }
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (!open) return // this screen let go of it itself
                    playback.controller = null
                    playback.playing = false
                    playback.trackId = null
                    connection++
                }
            })
            .buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (open && controller != null) {
                    controller.addListener(listener)
                    playback.controller = controller
                    playback.read(controller)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            open = false
            playback.controller?.removeListener(listener)
            playback.controller = null
            MediaController.releaseFuture(future)
        }
    }
    return playback
}

/** Plays a recording and shows its words. When it ends, the playlist's next prepared recording follows. */
@Composable
private fun PlayingReader(
    model: AppModel,
    reading: Reading,
    track: Track,
    playlists: List<Playlist>,
    playback: Playback,
    meanings: Boolean,
    onMeanings: (Boolean) -> Unit,
    textScale: Float,
    onTextScale: (Float) -> Unit,
) {
    val controller = playback.controller
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    // Until the player has switched to this recording, what it reports belongs to the one before.
    val mine = playback.trackId == track.id
    val playing = mine && playback.playing
    val position = remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    // Where this recording was left last time, read before listening now overwrites it.
    val left = remember(track.id) { Settings.place(context, track.id) }
    // Set when the recording starts from the beginning though it was left further in: offer to go there.
    var offer by remember(track.id) { mutableStateOf<Place?>(null) }

    LaunchedEffect(controller, track.id) {
        val player = controller ?: return@LaunchedEffect
        if (player.currentMediaItem?.mediaId == track.id) return@LaunchedEffect
        // Queue the rest of the playlist the recording is from, so listening carries on after it.
        val playlist = playlists.firstOrNull { p -> p.tracks.any { it.id == track.id } } ?: return@LaunchedEffect
        // Each comes with a picture in the theme's colours; Android's own media player takes its colours from it.
        val items = withContext(Dispatchers.Default) {
            playlist.tracks.filter { it.ready }.mapNotNull { queued ->
                val file = model.library.audio(queued.id) ?: return@mapNotNull null
                val surah = SURAHS[queued.surah - 1]
                val metadata = MediaMetadata.Builder().setTitle(if (arabic) surah.arabic else surah.latin).setArtist(playlist.channel)
                    .setArtworkData(Artwork.of(context, surah), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                MediaItem.Builder().setMediaId(queued.id).setUri(Uri.fromFile(file)).setMediaMetadata(metadata.build()).build()
            }
        }
        val start = items.indexOfFirst { it.mediaId == track.id }
        if (start < 0) return@LaunchedEffect
        val startAt = model.takeStartAt()
        // Loaded and waiting: the recitation starts when the reader presses play, not on opening the surah.
        player.pause()
        player.setMediaItems(items, start, startAt.toLong())
        player.prepare()
        if (startAt == 0 && left != null && left.ms > 10_000) offer = left
    }

    // Remember the place: every few seconds while reciting, when it pauses, and on leaving.
    // Not while the offer is up, or the place it offers would be overwritten before it is answered.
    val keepPlace by rememberUpdatedState {
        val player = controller
        val timings = reading.timings
        if (player != null && timings != null && mine && offer == null) {
            val ms = player.currentPosition.toInt()
            val whole = player.duration
            when {
                whole > 0 && ms > whole - 3_000 -> Settings.clearPlace(context, track.id) // heard to the end
                ms > 3_000 -> Settings.savePlace(context, Place(track.id, reading.surah, ms, timings.verseAt(ms) + 1))
            }
        }
    }
    LaunchedEffect(controller, playing, track.id) {
        if (!playing) {
            keepPlace()
            return@LaunchedEffect
        }
        while (true) {
            delay(4_000)
            keepPlace()
        }
    }
    DisposableEffect(track.id) { onDispose { keepPlace() } }
    LaunchedEffect(offer) {
        if (offer != null) {
            delay(20_000)
            offer = null
        }
    }

    // Which verses to recite: the whole surah unless the reader chooses a first and a last verse.
    val verses = reading.timings?.verses.orEmpty()
    var range by remember(track.id) { mutableStateOf(0..verses.lastIndex.coerceAtLeast(0)) }
    // The first verse starts at the very beginning, so the opening formulas are heard with it.
    fun startOf(r: IntRange): Int = if (r.first == 0) 0 else verses.getOrNull(r.first)?.firstOrNull()?.startMs ?: 0
    // The stretch ends where the verse after its last one begins; the last verse runs to the end.
    fun endOf(r: IntRange): Int = verses.getOrNull(r.last + 1)?.firstOrNull()?.startMs ?: Int.MAX_VALUE

    // Follow the player closely while it plays, so words light up on time; loosely when it's paused.
    LaunchedEffect(controller, playing, mine) {
        val player = controller ?: return@LaunchedEffect
        while (true) {
            position.intValue = if (mine) player.currentPosition.toInt() else 0
            // The last verse asked for has been recited: stop there.
            if (playing && mine && position.intValue >= endOf(range)) player.pause()
            if (playing) withFrameMillis { } else delay(200)
        }
    }

    if (controller == null) {
        ReaderLoading(failed = false, onRetry = {}, onBack = { model.back() })
    } else {
        ReaderContent(
            reading = reading,
            showMeanings = meanings,
            playing = playing,
            position = { position.intValue },
            duration = if (mine) playback.duration else 0,
            onBack = { model.back() },
            onMeanings = onMeanings,
            onPlayPause = {
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    // Outside the chosen verses (before them, or stopped at their end): begin at the first.
                    val at = controller.currentPosition.toInt()
                    if (mine && (at < startOf(range) - 500 || at >= endOf(range) - 300)) {
                        controller.seekTo(startOf(range).toLong())
                        position.intValue = startOf(range)
                    }
                    controller.play()
                }
            },
            onSeek = { ms -> controller.seekTo(ms.toLong()); position.intValue = ms },
            textScale = textScale,
            onTextScale = onTextScale,
            range = range,
            onRange = { chosen ->
                range = chosen
                val at = controller.currentPosition.toInt()
                if (mine && (at < startOf(chosen) || at >= endOf(chosen))) {
                    controller.seekTo(startOf(chosen).toLong())
                    position.intValue = startOf(chosen)
                }
            },
            suggestion = {
                offer?.let { place ->
                    LastReadBar(
                        verse = place.verse,
                        onGo = {
                            controller.seekTo(place.ms.toLong())
                            position.intValue = place.ms
                            offer = null
                        },
                        onDismiss = { offer = null },
                    )
                }
            },
        )
    }
}
