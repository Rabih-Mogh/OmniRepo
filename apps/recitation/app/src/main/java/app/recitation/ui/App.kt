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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.recitation.AppModel
import app.recitation.Screen
import app.recitation.core.SURAHS
import app.recitation.data.Playlist
import app.recitation.data.Track
import app.recitation.player.PlaybackService
import app.recitation.prepare.PrepareWorker
import kotlinx.coroutines.delay

/** The whole app: which screen shows, and the pop-ups over it. */
@Composable
fun App(model: AppModel) {
    val context = LocalContext.current
    val playlists by model.library.playlists.collectAsStateWithLifecycle()
    val progress by PrepareWorker.progress.collectAsStateWithLifecycle()
    val modelProblem by PrepareWorker.modelProblem.collectAsStateWithLifecycle()

    BackHandler(enabled = model.screen != Screen.Playlists) { model.back() }
    UpdateDialog()

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

    when (val screen = model.screen) {
        Screen.Playlists -> PlaylistsContent(
            playlists = playlists,
            progress = progress,
            progressTitle = progress?.trackId?.let { model.library.track(it)?.title },
            modelProblem = modelProblem,
            onRetryModel = model::retryModel,
            onOpen = model::open,
            onAdd = { model.link = "" },
            onCancel = model::cancel,
        )
        is Screen.Recordings -> {
            val playlist = playlists.firstOrNull { it.url == screen.url }
            if (playlist == null) {
                LaunchedEffect(Unit) { model.back() }
            } else {
                RecordingsContent(
                    playlist = playlist,
                    progress = progress,
                    modelProblem = modelProblem,
                    onRetryModel = model::retryModel,
                    onBack = { model.back() },
                    onOpen = { model.open(playlist, it) },
                    onGet = get,
                    onCancel = model::cancel,
                    onSurah = model::setSurah,
                    onForget = model::forget,
                    onRemove = { model.remove(playlist) },
                )
            }
        }
        is Screen.Reader -> {
            val playlist = playlists.firstOrNull { it.url == screen.url }
            if (playlist == null) LaunchedEffect(Unit) { model.back() } else ReaderScreen(model, playlist, screen.trackId)
        }
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

/** A line to the playback service, for as long as the screen that asks for it is showing. */
@Composable
private fun rememberController(): MediaController? {
    val context = LocalContext.current.applicationContext
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(future) }
    }
    return controller
}

/** Plays a recording and shows its words. When it ends, the playlist's next prepared recording follows. */
@Composable
private fun ReaderScreen(model: AppModel, playlist: Playlist, trackId: String) {
    val controller = rememberController()
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    val position = remember { mutableIntStateOf(0) }
    // Word meanings are in English, so they start hidden when the phone is in Arabic.
    var meanings by rememberSaveable { mutableStateOf(!arabic) }

    LaunchedEffect(controller, trackId) {
        val player = controller ?: return@LaunchedEffect
        if (player.currentMediaItem?.mediaId == trackId) return@LaunchedEffect
        val items = playlist.tracks.filter { it.ready }.mapNotNull { track ->
            val file = model.library.audio(track.id) ?: return@mapNotNull null
            val surah = SURAHS[track.surah - 1]
            MediaItem.Builder().setMediaId(track.id).setUri(Uri.fromFile(file))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(if (arabic) surah.arabic else surah.latin).setArtist(playlist.channel).build())
                .build()
        }
        val start = items.indexOfFirst { it.mediaId == trackId }
        if (start < 0) return@LaunchedEffect
        player.setMediaItems(items, start, 0L)
        player.prepare()
        player.play()
    }

    DisposableEffect(controller) {
        val player = controller
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                duration = player.duration.coerceAtLeast(0).toInt()
                // The player has moved on to the next recording: show its words.
                player.currentMediaItem?.mediaId?.let(model::load)
            }
        }
        player?.addListener(listener)
        if (player != null) {
            playing = player.isPlaying
            duration = player.duration.coerceAtLeast(0).toInt()
        }
        onDispose { player?.removeListener(listener) }
    }

    // Follow the player closely while it plays, so words light up on time; loosely when it's paused.
    LaunchedEffect(controller, playing) {
        val player = controller ?: return@LaunchedEffect
        while (true) {
            position.intValue = player.currentPosition.toInt()
            if (playing) withFrameMillis { } else delay(200)
        }
    }

    val reading = model.reading
    if (reading == null || controller == null) {
        ReaderLoading(onBack = { model.back() })
    } else {
        ReaderContent(
            reading = reading,
            showMeanings = meanings,
            playing = playing,
            position = { position.intValue },
            duration = duration,
            onBack = { model.back() },
            onMeanings = { meanings = it },
            onPlayPause = { if (controller.isPlaying) controller.pause() else controller.play() },
            onSeek = { ms -> controller.seekTo(ms.toLong()); position.intValue = ms },
        )
    }
}
