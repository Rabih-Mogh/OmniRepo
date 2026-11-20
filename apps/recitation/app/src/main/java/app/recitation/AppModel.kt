package app.recitation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.recitation.core.Mark
import app.recitation.core.Pointer
import app.recitation.core.SURAHS
import app.recitation.core.Tajweed
import app.recitation.core.SurahMatcher
import app.recitation.core.SurahSearch
import app.recitation.core.Timings
import app.recitation.data.Hit
import app.recitation.data.IndoPakFont
import app.recitation.data.Library
import app.recitation.data.Marks
import app.recitation.data.Place
import app.recitation.data.Playlist
import app.recitation.data.QuranStore
import app.recitation.data.Settings
import app.recitation.data.Space
import app.recitation.data.Stats
import app.recitation.data.SurahNames
import app.recitation.data.Track
import app.recitation.data.Verse
import app.recitation.data.VerseCollection
import app.recitation.data.VerseRef
import app.recitation.prepare.PrepareWorker
import app.recitation.speech.ModelStore
import app.recitation.yt.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Which screen is showing. */
sealed interface Screen {
    /** The surahs and the playlists, on two tabs. */
    data object Home : Screen

    data class Recordings(val url: String) : Screen

    /** The verses kept in one collection. */
    data class Collection(val id: String) : Screen

    /** A surah's words. [trackId] is the recording to play with it, or null to just read. [from] is where Back goes. */
    data class Surah(val number: Int, val trackId: String?, val from: Screen) : Screen
}

/**
 * Everything the reading screen needs for one surah. [track] and [timings] are there when a
 * recording goes with it. The timings are given as the alignment made them and kept here
 * made finer: a time for every letter and vowel sign (see [Pointer]).
 */
class Reading(val surah: Int, val verses: List<Verse>, val track: Track? = null, timings: Timings? = null) {
    private val words = verses.map { verse -> verse.words.map { it.arabic } }
    private val refined = timings?.let { Pointer.refine(words, it) }

    val timings: Timings? = refined?.timings

    /**
     * The rules of recitation at work in each word of each verse. Some depend on where the
     * reciter stops: with a recording, that is where its reciter stopped; without one, at
     * the end of each verse.
     */
    val rules: List<List<List<Mark>>> = run {
        val stops = refined?.stops ?: Tajweed.stopsAtVerseEnds(words.map { it.size })
        val marks = Tajweed.marks(words.flatten(), stops)
        var w = 0
        words.map { verse -> List(verse.size) { marks[w++] } }
    }
}

class AppModel(app: Application) : AndroidViewModel(app) {

    val library = Library.get(app)

    /** The pins left on verses and the collections verses are kept in. */
    val marks = Marks.get(app)

    /** How long has been listened on each day, and which verses have been heard. */
    val stats = Stats.get(app)
    private val quran = QuranStore(app)

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    /** The tab of the first screen: 0 the Quran, 1 the playlists, 2 the pins and collections, 3 the stats. */
    var tab by mutableIntStateOf(0)

    /** What is typed in the search box; null when it isn't open. */
    var query by mutableStateOf<String?>(null)
        private set

    /** The verses whose words or translation contain [query]. */
    var hits by mutableStateOf<List<Hit>>(emptyList())
        private set
    private var searching: Job? = null

    /** A link waiting in the "add playlist" box: shared from YouTube, or being typed. Null when the box is closed. */
    var link by mutableStateOf<String?>(null)
    var adding by mutableStateOf(false)
        private set
    var addError by mutableStateOf<String?>(null)
        private set

    /** The open surah's words and timings; null while they load, or when they couldn't be fetched. */
    var reading by mutableStateOf<Reading?>(null)
        private set
    var readingFailed by mutableStateOf(false)
        private set

    /** Where the recitation listened to last was left, for the offer to go back to it. */
    var lastRead by mutableStateOf(Settings.lastRead(app))
        private set

    /** How far into its recording the surah about to open should start, when carrying on from [lastRead]. */
    private var startAt: Int? = null

    /** The verse (counted from 0) the surah about to open should open at, when coming from a pin or a collection. */
    private var startVerse: Int? = null

    /** Downloads were paused by the user and stay so until resumed. */
    var paused by mutableStateOf(Settings.paused(app))
        private set

    /** The background work is queued but not running: it is waiting for a connection. */
    var waitingForNetwork by mutableStateOf(false)
        private set

    init {
        // Recordings still waiting from last time (the app was closed, or the model couldn't be had): carry on.
        if (!paused && library.playlists.value.any { p -> p.tracks.any { it.wanted && (!it.ready || it.againFrom >= 0) } }) PrepareWorker.start(app)
        fetchTheRest()
        viewModelScope.launch { SurahNames.load(app) }
        viewModelScope.launch { IndoPakFont.load(app) }
        // A copy of the speech model made before the app learnt to read Accent Coach's: free its 318 MB.
        viewModelScope.launch { runCatching { ModelStore(app).tidy() } }
        // While the open recording is being listened to its word timings grow: show the latest.
        viewModelScope.launch {
            PrepareWorker.live.collect { live ->
                val open = reading
                if (live != null && open?.track?.id == live.trackId && open.surah == live.surah) {
                    // Working out every letter's time and the rules takes a moment on a long surah.
                    val latest = withContext(Dispatchers.Default) { Reading(open.surah, open.verses, open.track, live.timings) }
                    if (reading === open) reading = latest
                }
            }
        }
        viewModelScope.launch {
            WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(PrepareWorker.WORK_NAME).collect { infos ->
                waitingForNetwork = infos.any { it.state == WorkInfo.State.ENQUEUED }
            }
        }
    }

    /**
     * Fetches the surahs that aren't on the phone yet, one after another with a short gap,
     * so the whole Quran can be read without a connection. It stops at the first failure
     * (most likely no connection) and picks up the next time the app is opened.
     */
    private fun fetchTheRest() {
        viewModelScope.launch(Dispatchers.IO) {
            for (surah in SURAHS) {
                if (quran.has(surah.number)) continue
                if (runCatching { quran.surah(surah.number) }.isFailure) break
                delay(300)
            }
        }
    }

    fun open(playlist: Playlist) {
        screen = Screen.Recordings(playlist.url)
    }

    /**
     * Opens a surah to read. If [prefer] or any other recording of it is ready, that
     * recording plays along; otherwise the words are shown on their own.
     */
    fun openSurah(number: Int, prefer: Track? = null) {
        val track = prefer?.takeIf(::playable) ?: recordingOf(number)
        // Whatever recording of it is still being prepared goes to the front of the queue.
        PrepareWorker.preferred = (prefer ?: track ?: anyRecordingOf(number))?.takeIf { !it.ready }?.id
        val from = (screen as? Screen.Surah)?.from ?: screen
        screen = Screen.Surah(number, track?.id, from)
        load(number, track)
    }

    /** Opens the surah that was being recited last, at the place it was left. */
    fun continueFrom(place: Place) {
        val track = library.track(place.trackId)?.takeIf(::playable) ?: return
        startAt = place.ms
        openSurah(place.surah, track)
    }

    /** Opens a surah at one of its verses: where a pin is, or a verse kept in a collection. */
    fun openVerse(verse: VerseRef) {
        startVerse = verse.verse - 1
        openSurah(verse.surah)
    }

    /** The verse to open the surah now opening at, asked for once by its screen. */
    fun takeStartVerse(): Int? = startVerse.also { startVerse = null }

    fun open(collection: VerseCollection) {
        screen = Screen.Collection(collection.id)
    }

    /** One verse's words and translation, for a list of kept verses; null if they can't be had. */
    suspend fun verse(verse: VerseRef): Verse? = withContext(Dispatchers.IO) {
        runCatching { quran.surah(verse.surah).getOrNull(verse.verse - 1) }.getOrNull()
    }

    /** How much room the app's files take on the phone. */
    suspend fun space(): Space = withContext(Dispatchers.IO) {
        fun size(file: File): Long = if (file.isDirectory) file.listFiles().orEmpty().sumOf(::size) else file.length()
        val files = getApplication<Application>().filesDir
        val recordings = size(File(files, "audio"))
        val engine = size(File(files, "engine"))
        val model = size(File(files, "model"))
        Space(recordings, engine, model, size(files) - recordings - engine - model)
    }

    /** The place to start the recording now opening at, asked for once by the player. */
    fun takeStartAt(): Int = (startAt ?: 0).also { startAt = null }

    /** The last place that can still be gone back to: its recording is still on the phone. */
    fun lastReadable(): Place? = lastRead?.takeIf { place -> library.track(place.trackId)?.let(::playable) == true }

    /** A recording of [surah] that is ready to play, if there is one. */
    fun recordingOf(surah: Int): Track? =
        library.playlists.value.asSequence().flatMap { it.tracks }.firstOrNull { it.surah == surah && playable(it) }

    /** Any recording of [surah] in the playlists, ready or not: the one being prepared first. */
    fun anyRecordingOf(surah: Int): Track? {
        val all = library.playlists.value.flatMap { it.tracks }.filter { it.surah == surah }
        return all.firstOrNull(::playable) ?: all.firstOrNull { it.wanted } ?: all.firstOrNull()
    }

    private fun playable(track: Track) = track.canPlay && library.audio(track.id) != null

    /** The player has moved on to another recording: show its surah. */
    fun playing(trackId: String) {
        val track = library.track(trackId) ?: return
        val shown = screen as? Screen.Surah ?: return
        // A surah open just to read stays put, whatever is playing in the background.
        if (shown.trackId == null || shown.trackId == trackId) return
        screen = shown.copy(number = track.surah, trackId = trackId)
        load(track.surah, track)
    }

    /** Fetches the open surah again, after it couldn't be. */
    fun reload() {
        val shown = screen as? Screen.Surah ?: return
        load(shown.number, shown.trackId?.let(library::track))
    }

    private fun load(surah: Int, track: Track?) {
        val current = reading
        if (current != null && current.surah == surah && current.track?.id == track?.id) return
        reading = null
        readingFailed = false
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val verses = quran.surah(surah)
                    val timings = track?.let { runCatching { Timings.decode(library.timings(it.id, surah).readText()) }.getOrNull() }
                    Reading(surah, verses, track.takeIf { timings != null }, timings)
                }.getOrNull()
            }
            // The reader may have moved to another surah while this one was being fetched.
            if ((screen as? Screen.Surah)?.number != surah) return@launch
            // What the listening has placed since the file was written, if it is this recording being listened to.
            val live = PrepareWorker.live.value?.takeIf { loaded?.track != null && it.trackId == loaded.track.id && it.surah == surah }
            reading = if (loaded != null && live != null) {
                withContext(Dispatchers.Default) { Reading(surah, loaded.verses, loaded.track, live.timings) }
            } else {
                loaded
            }
            readingFailed = loaded == null
        }
    }

    /** Opens the search box ([text] empty), searches for what is typed in it, or closes it (null). */
    fun search(text: String?) {
        query = text
        searching?.cancel()
        if (text == null || SurahSearch.plain(text).length < QuranStore.SHORTEST_SEARCH) {
            hits = emptyList()
            return
        }
        // A moment's pause, so the verses aren't gone through for every letter typed.
        searching = viewModelScope.launch {
            delay(250)
            hits = quran.search(text)
        }
    }

    /** One screen back. False when there's nowhere to go back to. */
    fun back(): Boolean {
        lastRead = Settings.lastRead(getApplication())
        screen = when (val s = screen) {
            Screen.Home -> {
                // The search box closes first.
                if (query == null) return false
                search(null)
                return true
            }
            is Screen.Recordings -> Screen.Home
            is Screen.Collection -> Screen.Home
            is Screen.Surah -> s.from
        }
        return true
    }

    /** Takes a link out of text shared from another app, e.g. "Check this out: https://…". */
    fun offer(shared: String) {
        link = Regex("https?://\\S+").find(shared)?.value ?: shared.trim()
        addError = null
        tab = 1
        screen = Screen.Home
    }

    fun add(url: String) {
        if (adding) return
        adding = true
        addError = null
        viewModelScope.launch {
            try {
                val found = withContext(Dispatchers.IO) { YouTube.playlist(url.trim()) }
                val playlist = Playlist(
                    found.url, found.title, found.channel,
                    found.videos.map { Track(it.id, it.title, it.seconds, SurahMatcher.match(it.title)?.number ?: 0) },
                )
                library.add(playlist)
                link = null
                tab = 1
                screen = Screen.Recordings(playlist.url)
            } catch (e: Exception) {
                val app = getApplication<Application>()
                addError = if (e is IllegalArgumentException) {
                    app.getString(R.string.add_not_a_link)
                } else {
                    app.getString(R.string.add_failed, e.message?.let { "${e.javaClass.simpleName}: $it" } ?: e.javaClass.simpleName)
                }
            } finally {
                adding = false
            }
        }
    }

    /** Asks for recordings to be downloaded and prepared. Ones without a known surah are left for the user to set. */
    fun want(tracks: List<Track>) {
        for (track in tracks) {
            if (!track.ready && track.surah in 1..SURAHS.size) library.update(track.id) { it.copy(wanted = true, error = null) }
            // A recording that is ready and shows that some verses couldn't be heard again: that is put away.
            else if (track.ready && track.error != null) library.update(track.id) { it.copy(error = null) }
        }
        resume()
    }

    /** Stops the work where it is. What has been downloaded and heard so far is kept, and nothing leaves the queue. */
    fun pause() {
        paused = true
        Settings.setPaused(getApplication(), true)
        PrepareWorker.stop(getApplication())
    }

    fun resume() {
        paused = false
        Settings.setPaused(getApplication(), false)
        PrepareWorker.start(getApplication())
    }

    /** Takes everything out of the queue. Half-downloaded sound is deleted; finished downloads stay. */
    fun cancel() {
        for (track in library.playlists.value.flatMap { it.tracks }) {
            if (track.wanted) cancel(track)
        }
        paused = false
        Settings.setPaused(getApplication(), false)
        PrepareWorker.stop(getApplication())
    }

    /** Takes one recording out of the queue; the rest carry on. */
    fun cancel(track: Track) {
        library.update(track.id) { it.copy(wanted = false) }
        library.dropPartial(track.id)
    }

    /** Tries again to get the speech model, after the user has put Accent Coach right or chosen to download it. */
    fun retryModel(download: Boolean) {
        if (download) Settings.setDownloadModel(getApplication(), true)
        resume()
    }

    /** Corrects which surah a recording is. If it was already downloaded it is lined up again with the new words. */
    fun setSurah(track: Track, surah: Int) {
        if (surah == track.surah) return
        val downloaded = library.audio(track.id) != null
        library.update(track.id) { it.copy(surah = surah, ready = false, playable = false, clarity = 0f, error = null, wanted = downloaded) }
        if (downloaded) resume()
    }

    fun forget(track: Track) = library.forget(track.id)

    /**
     * Has a recording that is on the phone listened to once more and its words placed
     * afresh, as a newer version of the app would place them. It stays playable meanwhile.
     */
    /**
     * Has only [verses] (counting from 0) of a recording listened to once more and placed
     * afresh: much quicker than the whole of it, when the words were off in one place.
     */
    fun listenAgain(track: Track, verses: IntRange) {
        library.update(track.id) { it.copy(wanted = true, error = null, againFrom = verses.first, againTo = verses.last) }
        resume()
    }

    fun listenAgain(track: Track): Track {
        library.update(track.id) { it.copy(ready = false, error = null) }
        return library.track(track.id) ?: track.copy(ready = false)
    }

    fun remove(playlist: Playlist) {
        library.remove(playlist.url)
        screen = Screen.Home
    }
}
