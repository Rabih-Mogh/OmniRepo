package app.recitation.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import app.recitation.data.Appearance
import app.recitation.data.IndoPakFont
import app.recitation.data.Pin
import app.recitation.data.Playlist
import app.recitation.data.Script
import app.recitation.data.Settings
import app.recitation.data.Space
import androidx.compose.runtime.produceState
import java.time.LocalDate
import app.recitation.data.SurahNames
import app.recitation.data.Track
import app.recitation.data.VerseCollection
import app.recitation.data.VerseRef
import app.recitation.player.Artwork
import app.recitation.player.PlaybackService
import app.recitation.prepare.Playing
import app.recitation.prepare.PrepareWorker
import app.recitation.prepare.Progress
import app.recitation.prepare.Step
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The whole app: which screen shows, and the pop-ups over it. */
@Composable
fun App(model: AppModel, choices: Choices) {
    BackHandler(enabled = model.screen != Screen.Home || model.query != null) { model.back() }
    UpdateDialog()

    // The surahs' names in calligraphy, once that font has been fetched; plainer calligraphy until then.
    val surahNames by SurahNames.font.collectAsStateWithLifecycle()
    // The rules of recitation open over whatever is showing, which stays as it was underneath.
    var rules by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(LocalSurahNames provides surahNames, LocalTajweedRules provides { rules = true }) {
        Screens(model, choices)
        AnimatedVisibility(rules, enter = fadeIn() + slideInVertically { it / 12 }, exit = fadeOut() + slideOutVertically { it / 12 }) {
            TajweedRulesPage(onBack = { rules = false })
        }
    }
}

@Composable
private fun Screens(model: AppModel, choices: Choices) {
    val context = LocalContext.current
    val playlists by model.library.playlists.collectAsStateWithLifecycle()
    val progress by PrepareWorker.progress.collectAsStateWithLifecycle()
    val fetching by PrepareWorker.fetching.collectAsStateWithLifecycle()
    val modelProblem by PrepareWorker.modelProblem.collectAsStateWithLifecycle()

    val indopak by IndoPakFont.font.collectAsStateWithLifecycle()

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

    val pins by model.marks.pins.collectAsStateWithLifecycle()
    val collections by model.marks.collections.collectAsStateWithLifecycle()
    val listening by model.stats.listening.collectAsStateWithLifecycle()

    // One line of truth about the background work, so nothing ever just sits there unexplained.
    val tracks = playlists.flatMap { it.tracks }
    // The listening comes first in it; a sound being downloaded meanwhile stands beside that, or alone when nothing else is going on.
    fun work(progress: Progress) = tracks.firstOrNull { it.id == progress.trackId }.let { Status.Working(progress, it?.title, it?.surah ?: 0) }
    val working = progress ?: fetching
    val problem = modelProblem
    val status: Status? = when {
        working != null -> work(working).copy(fetching = fetching?.takeIf { it !== working }?.let(::work))
        tracks.none { it.wanted && !it.ready } -> null
        model.paused -> Status.Paused
        problem != null -> Status.ModelBlocked(problem)
        model.refusedForNow -> Status.Refused
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
                onPlayPause = { if (player.reciting) player.pause() else player.recite() },
                onOpen = { model.openSurah(reciting.surah, prefer = reciting) },
                onStop = { player.stop(); player.clearMediaItems() },
            )
        }
    }

    // Asked for by a tap on the recitation's notification.
    LaunchedEffect(model.showReciting, playback.trackId) {
        val reciting = playback.trackId?.let { id -> tracks.firstOrNull { it.id == id } }
        if (model.showReciting && reciting != null) {
            model.showReciting = false
            if ((model.screen as? Screen.Surah)?.trackId != reciting.id) model.openSurah(reciting.surah, prefer = reciting)
        }
    }

    // What the app is at goes down to every screen, each of which shows it in one of its bars.
    CompositionLocalProvider(LocalDoing provides status?.let { Doing(it, actions) }) {
    when (val screen = model.screen) {
        Screen.Home -> HomeContent(
            tab = model.tab,
            onTab = { model.tab = it },
            playlists = playlists,
            status = status,
            actions = actions,
            recited = tracks.filter { it.canPlay }.map { it.surah }.toSet(),
            unheard = tracks.filter { it.canPlay }.map { it.surah }.toSet() - tracks.filter { it.ready }.map { it.surah }.toSet(),
            queued = tracks.filter { it.wanted && !it.ready }.map { it.surah }.toSet(),
            onSurah = { model.openSurah(it) },
            onOpen = model::open,
            onAdd = { model.link = "" },
            nowPlaying = nowPlaying,
            // Not offered while that very recording is the one playing. Whether its sound is
            // still on the phone is looked up when the place or the playlists change, not
            // each time the screen is drawn.
            lastRead = remember(model.lastRead, playlists) { model.lastReadable() }
                ?.takeIf { it.trackId != playback.trackId }?.let { LastRead(SURAHS[it.surah - 1], it.verse) },
            onLastRead = { model.lastReadable()?.let(model::continueFrom) },
            saved = { modifier ->
                SavedTab(pins, collections, onVerse = model::openVerse, onRemovePin = { model.marks.removePin(it.id) }, onCollection = model::open, modifier = modifier)
            },
            stats = { modifier ->
                // Measured afresh each time the tab is opened: downloads come and go.
                val space by produceState<Space?>(null) { value = model.space() }
                StatsTab(
                    listening.summary(LocalDate.now()),
                    RecordingStats(tracks.count { it.ready }, tracks.size, tracks.filter { it.ready }.sumOf { it.seconds * 1000L }),
                    SavedStats(pins.size, collections.size, collections.sumOf { it.verses.size }),
                    space, modifier,
                )
            },
            practice = { modifier ->
                // Which surah to recite from is chosen first; its verses are stepped through on the next screen.
                var choosing by remember { mutableStateOf(false) }
                // Opening the tab is reason enough to have the tajweed model fetched, so it is there when a verse is chosen.
                LaunchedEffect(Unit) { model.wantTajweed() }
                PracticeTab(
                    Exercise.entries.associateWith { Settings.voiceBest(context, it.name) },
                    onRecite = { choosing = true }, onExercise = { model.openExercise(it.ordinal) }, modifier = modifier,
                )
                if (choosing) SurahPicker(onPick = { choosing = false; model.openRecite(VerseRef(it, 1)) }, onDismiss = { choosing = false })
            },
            query = model.query,
            onQuery = model::search,
            hits = model.hits,
            onVerse = model::openVerse,
            menu = {
                MenuContent(
                    appearance = choices.appearance, onAppearance = choices::look,
                    script = choices.script, onScript = choices::write, withIndoPak = indopak != null,
                    footer = {
                        VersionFooter()
                        CopyReport()
                    },
                )
            },
        )
        is Screen.Collection -> {
            val collection = collections.firstOrNull { it.id == screen.id }
            if (collection == null) {
                LaunchedEffect(Unit) { model.back() }
            } else {
                CollectionContent(
                    collection, text = model::verse, onBack = { model.back() }, onVerse = model::openVerse,
                    onRemove = { model.marks.setSaved(collection.id, it, false) },
                    onDelete = { model.marks.removeCollection(collection.id) },
                )
            }
        }
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
                    onListenAgain = { get(listOf(model.listenAgain(it))) },
                    onListenToVerses = model::listenAgain,
                    onRemove = { model.remove(playlist) },
                    nowPlaying = nowPlaying,
                )
            }
        }
        // A verse of the surah on show can be recited to the phone right on its page, from the microphone beside its number.
        is Screen.Surah -> {
            val recital = rememberPageRecital(
                screen.number,
                // The model it checks with comes by the background work, like everything else that is fetched.
                fetching = progress?.takeIf { it.trackId == null && (it.step == Step.TAJWEED || it.step == Step.ENGINE) }?.fraction,
                // A microphone tapped is a wish to fetch it now, even with the downloads paused.
                onWant = { pressed -> model.wantTajweed(); if (pressed && model.paused) model.resume() },
            )
            CompositionLocalProvider(LocalPageRecital provides recital) {
                SurahScreen(model, screen, playlists, status, playback, get, pins, collections, choices, indopak)
            }
        }
        is Screen.Recite -> ReciteScreen(
            SURAHS[screen.surah - 1], screen.verse, verseOf = { model.verse(VerseRef(screen.surah, it)) },
            // The model it checks with comes by the background work, like everything else that is fetched.
            fetching = progress?.takeIf { it.trackId == null && (it.step == Step.TAJWEED || it.step == Step.ENGINE) }?.fraction,
            // Pressing its button is a wish to fetch it now, even with the downloads paused.
            onWant = { pressed -> model.wantTajweed(); if (pressed && model.paused) model.resume() }, onBack = { model.back() },
        )
        is Screen.Voice -> ExerciseScreen(Exercise.entries[screen.exercise], onBack = { model.back() })
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

/** A surah open to read: with its recitation playing along when one is ready, and on its own when not. */
@Composable
private fun SurahScreen(
    model: AppModel,
    screen: Screen.Surah,
    playlists: List<Playlist>,
    status: Status?,
    playback: Playback,
    get: (List<Track>) -> Unit,
    pins: List<Pin>,
    collections: List<VerseCollection>,
    choices: Choices,
    indopak: FontFamily?,
) {
    val meanings = choices.meanings
    val setMeanings: (Boolean) -> Unit = choices::show
    val textScale = choices.textScale
    val setTextScale: (Float) -> Unit = choices::size
    // The verse to open at, when the surah was opened from a pin or a collection.
    val startVerse = remember(screen.number) { model.takeStartVerse() }
    // What marks this surah's verses, and the verse whose pins and collections are open.
    val marks = remember(pins, collections, screen.number) {
        VerseMarks(
            pins.filter { it.at.surah == screen.number }.groupBy({ it.at.verse - 1 }, { it.name }),
            collections.flatMap { it.verses }.filter { it.surah == screen.number }.map { it.verse - 1 }.toSet(),
        )
    }
    var marking by remember(screen.number) { mutableStateOf<Int?>(null) }
    val page = Page(
        meanings, setMeanings, textScale, setTextScale,
        choices.script, indopak, startVerse, marks, { marking = it }, choices.appearance, choices::look,
        choices.tajweed, choices::colour,
    )
    marking?.let { verse ->
        val here = VerseRef(screen.number, verse + 1)
        VerseMarksDialog(
            here, pins, collections,
            MarkActions(
                newPin = { model.marks.addPin(it, here) },
                movePin = { model.marks.movePin(it.id, here) },
                removePin = { model.marks.removePin(it.id) },
                keep = { collection, kept -> model.marks.setSaved(collection.id, here, kept) },
                newCollection = { model.marks.setSaved(model.marks.addCollection(it).id, here, true) },
            ),
            onDismiss = { marking = null },
        )
    }
    val reading = model.reading
    val track = reading?.track
    when {
        reading == null || reading.surah != screen.number ->
            ReaderLoading(failed = model.readingFailed, onRetry = model::reload, onBack = { model.back() })
        track != null && reading.timings != null -> PlayingReader(model, reading, track, playlists, playback, page)
        else -> ReaderContent(
            reading = reading, showMeanings = meanings, playing = false, position = { 0 }, duration = 0,
            onBack = { model.back() }, onMeanings = setMeanings, onPlayPause = {}, onSeek = {},
            textScale = textScale, onTextScale = setTextScale,
            script = page.script, indopak = page.indopak,
            startVerse = page.startVerse, marks = page.marks, onMark = page.onMark,
            appearance = page.appearance, onAppearance = page.onAppearance,
            tajweed = page.tajweed, onTajweed = page.onTajweed,
            withoutRecitation = {
                // Looked up afresh each time the playlists change, so the bar keeps up with the download.
                val recording = remember(playlists, screen.number) { model.anyRecordingOf(screen.number) }
                val working = (status as? Status.Working)?.on(recording?.id)
                when {
                    recording == null -> NoRecitationBar(stringResource(R.string.no_recitation))
                    recording.canPlay -> NoRecitationBar(stringResource(R.string.recitation_ready), stringResource(R.string.play), AppIcons.Play) { model.openSurah(screen.number, recording) }
                    working != null -> RecitationComingBar(working, here = true)
                    recording.wanted -> RecitationComingBar(status, here = false)
                    recording.error != null -> NoRecitationBar(recording.error, stringResource(R.string.try_again), AppIcons.Retry) { get(listOf(recording)) }
                    else -> NoRecitationBar(stringResource(R.string.recitation_not_ready), stringResource(R.string.get), AppIcons.Download) { get(listOf(recording)) }
                }
            },
        )
    }
}

/**
 * How the reader likes the app, kept from one opening of it to the next: light or dark,
 * whether each word's meaning shows, how large the verses are, and the script they are
 * written in. Word meanings are in English, so they start hidden when the phone is in Arabic.
 */
class Choices(private val context: Context, arabic: Boolean) {
    var meanings by mutableStateOf(Settings.wordMeanings(context, byDefault = !arabic))
        private set
    var textScale by mutableFloatStateOf(Settings.textScale(context))
        private set
    var script by mutableStateOf(Settings.script(context))
        private set
    var appearance by mutableStateOf(Settings.appearance(context))
        private set
    var tajweed by mutableStateOf(Settings.tajweed(context))
        private set

    fun colour(tajweed: Boolean) {
        this.tajweed = tajweed
        Settings.setTajweed(context, tajweed)
    }

    fun look(appearance: Appearance) {
        this.appearance = appearance
        Settings.setAppearance(context, appearance)
    }

    fun show(meanings: Boolean) {
        this.meanings = meanings
        Settings.setWordMeanings(context, meanings)
    }

    fun size(scale: Float) {
        textScale = scale
        Settings.setTextScale(context, scale)
    }

    fun write(script: Script) {
        this.script = script
        Settings.setScript(context, script)
    }
}

/** A verse gone to counts as heard when the listening has got this far past its start. */
private const val HEARD_AHEAD_MS = 2_000

/** How long the recitation waits for a verse to be heard before playing regardless. */
private const val WAIT_AT_MOST_MS = 90_000L

/** How often, while a recitation plays, its place is kept and the listening noted. */
private const val NOTE_EVERY_MS = 4_000

/** How long after the playback service couldn't be reached it is tried again. */
private const val RECONNECT_AFTER_MS = 2_000L

/** How the reader has set the page up, and what marks its verses: the same whether or not a recitation plays along. */
private class Page(
    val meanings: Boolean,
    val onMeanings: (Boolean) -> Unit,
    val textScale: Float,
    val onTextScale: (Float) -> Unit,
    val script: Script,
    val indopak: FontFamily?,
    val startVerse: Int?,
    val marks: VerseMarks,
    val onMark: (Int) -> Unit,
    val appearance: Appearance,
    val onAppearance: (Appearance) -> Unit,
    val tajweed: Boolean,
    val onTajweed: (Boolean) -> Unit,
)

/**
 * Whether the player is reciting or means to be: so also while it is held up for a moment,
 * by a phone call or directions being read out, and will carry on by itself.
 */
private val Player.reciting: Boolean
    get() = playWhenReady && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED

/** Starts the recitation where the player is; a recording that had run to its end starts again. */
private fun Player.recite() {
    if (playbackState == Player.STATE_IDLE) prepare()
    if (playbackState == Player.STATE_ENDED) seekTo(0)
    play()
}

/** The line to the playback service, and what it is playing. */
class Playback {
    var controller by mutableStateOf<MediaController?>(null)

    /** Reciting, or held up for a moment and about to carry on: what the play button goes by. */
    var playing by mutableStateOf(false)

    /** Sound is coming out right now. */
    var sounding by mutableStateOf(false)
    var duration by mutableIntStateOf(0)

    /** The recording loaded in the player, playing or paused; null when there is none. */
    var trackId by mutableStateOf<String?>(null)

    fun read(player: Player) {
        playing = player.reciting
        sounding = player.isPlaying
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
                    playback.sounding = false
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
                } else if (open) {
                    // The service couldn't be reached. Rather than wait on it for ever: once more, shortly.
                    Handler(Looper.getMainLooper()).postDelayed({ if (open) connection++ }, RECONNECT_AFTER_MS)
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
    page: Page,
) {
    val controller = playback.controller
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    // Until the player has switched to this recording, what it reports belongs to the one before.
    val mine = playback.trackId == track.id
    val playing = mine && playback.playing
    val sounding = mine && playback.sounding
    // Play was pressed before the player had been given this recording: it starts once it has.
    var playWhenLoaded by remember(track.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Opened at a verse: the recitation is moved to where that verse begins, if the listening has reached it.
    val startMs = remember(track.id) { page.startVerse?.let { reading.timings?.verses?.getOrNull(it)?.firstOrNull()?.startMs } }
    var moved by remember(track.id) { mutableStateOf(false) }
    val position = remember { mutableIntStateOf(startMs ?: 0) }
    val context = LocalContext.current
    // The picture Android's own media player is given follows the app's light or dark, not the phone's.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Where this recording was left last time, read before listening now overwrites it.
    val left = remember(track.id) { Settings.place(context, track.id) }
    // Set when the recording starts from the beginning though it was left further in: offer to go there.
    var offer by remember(track.id) { mutableStateOf<Place?>(null) }

    LaunchedEffect(controller, track.id) {
        val player = controller ?: return@LaunchedEffect
        if (player.currentMediaItem?.mediaId == track.id) {
            // Already loaded: only the place changes, and only once.
            if (startMs != null && !moved) player.seekTo(startMs.toLong())
            moved = true
            return@LaunchedEffect
        }
        val playlist = playlists.firstOrNull { p -> p.tracks.any { it.id == track.id } } ?: return@LaunchedEffect
        // Each comes with a picture in the theme's colours; Android's own media player takes its colours from it.
        fun item(queued: Track): MediaItem? {
            val file = model.library.audio(queued.id) ?: return null
            val surah = SURAHS[queued.surah - 1]
            val metadata = MediaMetadata.Builder().setTitle(if (arabic) surah.arabic else arabicThenLatin(surah)).setArtist(playlist.channel)
                .setArtworkData(Artwork.of(context, surah, dark = dark), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            return MediaItem.Builder().setMediaId(queued.id).setUri(Uri.fromFile(file)).setMediaMetadata(metadata.build()).build()
        }
        val own = withContext(Dispatchers.Default) { item(track) } ?: return@LaunchedEffect
        val startAt = startMs?.takeIf { !moved } ?: model.takeStartAt()
        moved = true
        // Loaded and waiting: the recitation starts when the reader presses play, not on opening the surah.
        player.pause()
        player.setMediaItems(listOf(own), 0, startAt.toLong())
        player.prepare()
        if (playWhenLoaded) player.play()
        if (startAt == 0 && left != null && left.ms > 10_000) offer = left
        // Then the rest of the playlist the recording is from is queued around it, so listening
        // carries on after it. A picture is drawn for each, which for a long playlist takes a
        // while: the recording itself doesn't wait for that. Leaving the surah meanwhile
        // doesn't leave the queue half made.
        withContext(NonCancellable) {
            val all = playlist.tracks.filter { it.canPlay }
            val at = all.indexOfFirst { it.id == track.id }
            val (before, after) = withContext(Dispatchers.Default) { all.take(at).mapNotNull(::item) to all.drop(at + 1).mapNotNull(::item) }
            // Unless the player has been given something else since.
            if (player.mediaItemCount == 1 && player.currentMediaItem?.mediaId == track.id) {
                player.addMediaItems(0, before)
                player.addMediaItems(after)
            }
        }
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
    // For the stats: every few seconds of reciting, the time listened and the verses heard since the last note.
    val heardUpTo = remember(track.id) { intArrayOf(-1, -1) } // the verse and the place, in ms, at the last note
    val noteListening by rememberUpdatedState {
        val player = controller
        val timings = reading.timings
        if (player != null && timings != null && mine) {
            val ms = player.currentPosition.toInt()
            val verse = timings.verseAt(ms)
            // Verses passed since the last note count as heard, unless the reciting jumped.
            val ranOn = heardUpTo[1] >= 0 && ms - heardUpTo[1] in 0..2 * NOTE_EVERY_MS && heardUpTo[0] in 0..verse
            val verses = if (ranOn) heardUpTo[0]..verse else verse..verse
            // Kept on the phone each time, which isn't for the thread that draws the page to wait on.
            scope.launch(Dispatchers.IO) { model.stats.listened(reading.surah, verses, NOTE_EVERY_MS.toLong()) }
            heardUpTo[0] = verse
            heardUpTo[1] = ms
        }
    }
    LaunchedEffect(controller, sounding, track.id) {
        if (!sounding) {
            keepPlace()
            heardUpTo[1] = -1
            return@LaunchedEffect
        }
        while (true) {
            delay(NOTE_EVERY_MS.toLong())
            keepPlace()
            noteListening()
        }
    }
    DisposableEffect(track.id) { onDispose { keepPlace() } }
    // While the recording is open, the listening is told where it is being played, and goes
    // there if it hasn't got that far (see PrepareWorker).
    LaunchedEffect(controller, track.id) {
        while (true) {
            val player = controller
            if (player != null && player.currentMediaItem?.mediaId == track.id) PrepareWorker.playing = Playing(track.id, player.currentPosition.toInt())
            delay(1_500)
        }
    }
    DisposableEffect(track.id) { onDispose { PrepareWorker.playing = null } }
    LaunchedEffect(offer) {
        if (offer != null) {
            delay(20_000)
            offer = null
        }
    }

    val working by PrepareWorker.progress.collectAsStateWithLifecycle()
    // Whether the rest of it is still being listened to, or is waiting its turn for that.
    val listening = !model.paused && playlists.any { p -> p.tracks.any { it.id == track.id && it.wanted } }

    // Going to a verse that hasn't been heard yet: the listening is sent there at once, with
    // nothing else in its way, and the recitation waits until it has been there, so the words
    // light up from the verse's first. Pressing play doesn't wait.
    var waitingAt by remember(track.id) { mutableStateOf<Int?>(null) }
    fun heard(ms: Int): Boolean {
        val timings = reading.timings ?: return true
        return timings.complete || ms <= timings.heardMs || timings.ahead.any { ms in it }
    }
    fun goTo(ms: Int) {
        val player = controller ?: return
        // Until the player has been given this recording, a place gone to would be one in the recording before.
        if (!mine) return
        player.seekTo(ms.toLong())
        position.intValue = ms
        PrepareWorker.playing = Playing(track.id, ms)
        if (heard(ms + HEARD_AHEAD_MS)) {
            waitingAt = null
            return
        }
        // A recording whose listening was left off is taken up again, unless everything was paused.
        if (!listening && !model.paused && !track.ready) model.want(listOf(track))
        if (listening || (!model.paused && !track.ready)) {
            player.pause()
            waitingAt = ms
        }
    }
    LaunchedEffect(waitingAt, reading.timings) {
        val at = waitingAt ?: return@LaunchedEffect
        if (heard(at + HEARD_AHEAD_MS)) {
            waitingAt = null
            controller?.play()
        }
    }
    LaunchedEffect(waitingAt) {
        val at = waitingAt ?: return@LaunchedEffect
        // The model may be busy starting up, or not there at all: after long enough, play regardless.
        delay(WAIT_AT_MOST_MS)
        if (waitingAt == at) {
            waitingAt = null
            controller?.play()
        }
    }
    // Which verses to recite: the whole surah unless the reader chooses a first and a last verse.
    // While the recording is still being listened to, only the first verses have a place yet.
    val verses = reading.timings?.verses.orEmpty()
    var range by remember(track.id) { mutableStateOf(0..reading.verses.lastIndex.coerceAtLeast(0)) }
    // The first verse starts at the very beginning, so the opening formulas are heard with it.
    // A verse whose words aren't placed yet is gone to by the rough start the quick look gave it.
    fun startOf(r: IntRange): Int = if (r.first == 0) 0 else reading.timings?.startOfVerse(r.first) ?: 0
    // The stretch ends where the verse after its last one begins; the last verse runs to the end.
    fun endOf(r: IntRange): Int = reading.timings?.startOfVerse(r.last + 1) ?: Int.MAX_VALUE

    // The player is told where the last verse to recite ends, and stops there by itself: this
    // screen can only see how far the recitation has got while it is on show, and the
    // recitation carries on with the phone locked.
    val stopAt = endOf(range)
    LaunchedEffect(controller, mine, stopAt) {
        if (controller != null && mine) {
            controller.sendCustomCommand(PlaybackService.STOP_AT, PlaybackService.stopAt(track.id, if (stopAt == Int.MAX_VALUE) -1L else stopAt.toLong()))
        }
    }

    // Follow the player closely while it plays, so words light up on time; loosely when it's paused.
    LaunchedEffect(controller, playing, mine) {
        val player = controller ?: return@LaunchedEffect
        while (true) {
            position.intValue = if (mine) player.currentPosition.toInt() else startMs ?: 0
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
            showMeanings = page.meanings,
            playing = playing || (!mine && playWhenLoaded),
            position = { position.intValue },
            duration = if (mine) playback.duration else 0,
            onBack = { model.back() },
            onMeanings = page.onMeanings,
            onPlayPause = {
                waitingAt = null
                when {
                    // What the player has is still the recording before: this one plays once it is loaded.
                    !mine -> playWhenLoaded = !playWhenLoaded
                    controller.reciting -> controller.pause()
                    else -> {
                        // Outside the chosen verses (before them, or stopped at their end), or
                        // at the end of the recording: begin at the first.
                        val at = controller.currentPosition.toInt()
                        if (at < startOf(range) - 500 || at >= endOf(range) - 300 || controller.playbackState == Player.STATE_ENDED) {
                            controller.seekTo(startOf(range).toLong())
                            position.intValue = startOf(range)
                        }
                        controller.recite()
                    }
                }
            },
            onSeek = ::goTo,
            textScale = page.textScale,
            onTextScale = page.onTextScale,
            script = page.script, indopak = page.indopak,
            startVerse = page.startVerse, marks = page.marks, onMark = page.onMark,
            appearance = page.appearance, onAppearance = page.onAppearance,
            tajweed = page.tajweed, onTajweed = page.onTajweed,
            listening = listening,
            speed = working?.takeIf { it.trackId == track.id }?.speed ?: 0f,
            listeningAt = working?.takeIf { it.trackId == track.id && it.step == Step.LISTEN }?.atMs ?: -1,
            waiting = waitingAt != null,
            range = range,
            onRange = { chosen ->
                range = chosen
                val at = controller.currentPosition.toInt()
                if (mine && (at < startOf(chosen) || at >= endOf(chosen))) goTo(startOf(chosen))
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
