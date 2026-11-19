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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.recitation.data.Playlist
import app.recitation.data.Track
import app.recitation.player.PlaybackService
import app.recitation.prepare.PrepareWorker
import app.recitation.prepare.Settings
import kotlinx.coroutines.delay

/** The whole app: which screen shows, and the pop-ups over it. */
@Composable
fun App(model: AppModel) {
    val context = LocalContext.current
    val playlists by model.library.playlists.collectAsStateWithLifecycle()
    val progress by PrepareWorker.progress.collectAsStateWithLifecycle()
    val modelProblem by PrepareWorker.modelProblem.collectAsStateWithLifecycle()

    BackHandler(enabled = model.screen != Screen.Home) { model.back() }
    UpdateDialog()

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
    val reading = model.reading
    val track = reading?.track
    when {
        reading == null || reading.surah != screen.number ->
            ReaderLoading(failed = model.readingFailed, onRetry = model::reload, onBack = { model.back() })
        track != null && reading.timings != null ->
            PlayingReader(model, reading, track, playlists, playback, meanings, onMeanings = setMeanings)
        else -> ReaderContent(
            reading = reading, showMeanings = meanings, playing = false, position = { 0 }, duration = 0,
            onBack = { model.back() }, onMeanings = setMeanings, onPlayPause = {}, onSeek = {},
            withoutRecitation = {
                // Looked up afresh each time the playlists change, so the bar keeps up with the download.
                val recording = remember(playlists, screen.number) { model.anyRecordingOf(screen.number) }
                val working = (status as? Status.Working)?.progress?.takeIf { recording != null && it.trackId == recording.id }
                when {
                    recording == null -> NoRecitationBar(stringResource(R.string.no_recitation))
                    recording.ready -> NoRecitationBar(stringResource(R.string.recitation_ready), stringResource(R.string.play)) { model.openSurah(screen.number, recording) }
                    working != null -> NoRecitationBar(stringResource(PrepareWorker.stepLabel(working.step), (working.fraction * 100).toInt()))
                    recording.wanted -> NoRecitationBar(stringResource(if (status == Status.Paused) R.string.paused else R.string.queued))
                    recording.error != null -> NoRecitationBar(recording.error, stringResource(R.string.try_again)) { get(listOf(recording)) }
                    else -> NoRecitationBar(stringResource(R.string.recitation_not_ready), stringResource(R.string.get)) { get(listOf(recording)) }
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
) {
    val controller = playback.controller
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    // Until the player has switched to this recording, what it reports belongs to the one before.
    val mine = playback.trackId == track.id
    val playing = mine && playback.playing
    val position = remember { mutableIntStateOf(0) }

    LaunchedEffect(controller, track.id) {
        val player = controller ?: return@LaunchedEffect
        if (player.currentMediaItem?.mediaId == track.id) return@LaunchedEffect
        // Queue the rest of the playlist the recording is from, so listening carries on after it.
        val playlist = playlists.firstOrNull { p -> p.tracks.any { it.id == track.id } } ?: return@LaunchedEffect
        val items = playlist.tracks.filter { it.ready }.mapNotNull { queued ->
            val file = model.library.audio(queued.id) ?: return@mapNotNull null
            val surah = SURAHS[queued.surah - 1]
            MediaItem.Builder().setMediaId(queued.id).setUri(Uri.fromFile(file))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(if (arabic) surah.arabic else surah.latin).setArtist(playlist.channel).build())
                .build()
        }
        val start = items.indexOfFirst { it.mediaId == track.id }
        if (start < 0) return@LaunchedEffect
        player.setMediaItems(items, start, 0L)
        player.prepare()
        player.play()
    }

    // Follow the player closely while it plays, so words light up on time; loosely when it's paused.
    LaunchedEffect(controller, playing, mine) {
        val player = controller ?: return@LaunchedEffect
        while (true) {
            position.intValue = if (mine) player.currentPosition.toInt() else 0
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
            onPlayPause = { if (controller.isPlaying) controller.pause() else controller.play() },
            onSeek = { ms -> controller.seekTo(ms.toLong()); position.intValue = ms },
        )
    }
}
