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
import app.recitation.core.Drill
import app.recitation.core.Drills
import app.recitation.core.Family
import app.recitation.core.TajweedReference
import app.recitation.core.Topic
import app.recitation.core.WordForms
import app.recitation.data.Echo
import app.recitation.data.Hear
import app.recitation.data.Mistakes
import app.recitation.data.Spot
import app.recitation.data.Voice
import kotlin.random.Random
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
import app.recitation.speech.TajweedStore
import app.recitation.speech.EngineStore
import app.recitation.data.VerseCollection
import app.recitation.data.VerseRef
import app.recitation.prepare.PrepareWorker
import app.recitation.yt.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

    /** Reciting a verse to the phone to be corrected: [verse] counted from 1. [from] is where Back goes. */
    data class Recite(val surah: Int, val verse: Int, val from: Screen) : Screen

    /** One of the voice exercises, by its place in their list. */
    data class Voice(val exercise: Int) : Screen

    /**
     * A tajweed exercise of verses to recite one after another, each judged for one thing:
     * [topic] with its [letters], or, with no topic, whatever the reader was last corrected
     * on in each verse.
     */
    data class Drills(val topic: Topic?, val letters: String, val drills: List<Drill>) : Screen

    /** The lesson on [topic] (for letters, on the pair [letters]), with words of the reader's recordings to hear it in. */
    data class Lesson(val topic: Topic, val letters: String, val hears: List<Hear>) : Screen

    /** Counting a madd as it is held. */
    data object Madd : Screen

    /** Spotting the rule at work in a word. */
    data class Spots(val spots: List<Spot>) : Screen

    /** Echoing a reciter, a verse at a time. */
    data class Echoes(val echoes: List<Echo>) : Screen
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

    /** The notification of the recitation that is playing was tapped: its surah is to be shown, once the player says which it is. */
    var showReciting by mutableStateOf(false)

    /** Downloads were paused by the user and stay so until resumed. */
    var paused by mutableStateOf(Settings.paused(app))
        private set

    /** Something still has to be fetched and the background work is waiting for a connection to do it. */
    var waitingForNetwork by mutableStateOf(false)
        private set

    /** YouTube wouldn't let the sound be fetched, and the background work is giving it a while before it asks again. */
    var refusedForNow by mutableStateOf(false)
        private set

    init {
        keepBusy()
        // Whenever the work runs out while the app is open, what else could be listened to is looked for.
        viewModelScope.launch {
            combine(PrepareWorker.progress, PrepareWorker.fetching) { listening, fetching -> listening == null && fetching == null }
                .collectLatest { idle ->
                    if (idle) {
                        delay(IDLE_BEFORE_MORE_MS)
                        keepBusy()
                    }
                }
        }
        carryOn()
        fetchTheRest()
        viewModelScope.launch { SurahNames.load(app) }
        viewModelScope.launch { IndoPakFont.load(app) }
        // What the model the app listened with before left on the phone is of no use now: free it.
        viewModelScope.launch(Dispatchers.IO) { runCatching { TajweedStore.dropOld(app); library.dropOldHearing() } }
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
            WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(PrepareWorker.WAITING_NAME).collect { infos ->
                waitingForNetwork = infos.any { it.state == WorkInfo.State.ENQUEUED }
                refusedForNow = infos.any { it.state == WorkInfo.State.ENQUEUED && PrepareWorker.REFUSED_TAG in it.tags }
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

    /** Opens a verse to be recited to the phone and corrected. */
    fun openRecite(verse: VerseRef) {
        screen = Screen.Recite(verse.surah, verse.verse, screen)
    }

    /** What the reader has been corrected on, for the exercises that go over it. */
    val mistakes = Mistakes.get(app)

    // The verses the exercises choose among, as a correct recitation of each sounds: read once.
    private var pool: Map<Int, List<List<WordForms>>>? = null

    private suspend fun pool(): Map<Int, List<List<WordForms>>> = pool ?: withContext(Dispatchers.IO) {
        getApplication<Application>().assets.open("tajweed_reference.bin").use { TajweedReference.surahs(it, EXERCISE_SURAHS) }
    }.also { pool = it }

    /**
     * Opens a drill on [topic] (for letters, on the pair [letters]): verses that have it,
     * chosen afresh each time. Verses of surahs whose recitation is on the phone come
     * first, so that the reciter can be heard saying each.
     */
    fun openDrill(topic: Topic, letters: String = "") {
        viewModelScope.launch {
            val voiced = library.playlists.value.flatMap { it.tracks }.filter { it.ready }.map { it.surah }.toSet()
            val drills = withContext(Dispatchers.Default) {
                runCatching { Drills.pick(pool(), topic, Int.MAX_VALUE, Random(System.nanoTime()), letters) }.getOrDefault(emptyList())
                    .sortedBy { it.surah !in voiced }.take(GOES)
            }
            screen = Screen.Drills(topic, letters, drills)
        }
    }

    // The timings of the recordings a voice was last taken from: read once, not for every verse.
    private val placed = LinkedHashMap<String, Timings>()

    private fun placed(track: Track): Timings? = synchronized(placed) { placed[track.id] }
        ?: runCatching { Timings.decode(library.timings(track.id, track.surah).readText()) }.getOrNull()?.also {
            synchronized(placed) {
                placed[track.id] = it
                if (placed.size > TIMINGS_KEPT) placed.remove(placed.keys.first())
            }
        }

    /**
     * How the reciter of a recording on the phone says [drill]'s verse, and within it the
     * words that show what the drill is about: the teaching is in a reciter's own voice
     * wherever there is one. Null where no recording has that verse's words placed.
     */
    suspend fun voiceOf(drill: Drill): Voice? = withContext(Dispatchers.IO) {
        runCatching {
            for (track in library.playlists.value.flatMap { it.tracks }.filter { it.surah == drill.surah && it.canPlay }) {
                val sound = library.audio(track.id) ?: continue
                val timings = placed(track) ?: continue
                val times = timings.verses.getOrNull(drill.verse - 1)?.takeIf { it.isNotEmpty() } ?: continue
                val from = times.first().startMs
                val until = timings.startOfVerse(drill.verse)?.takeIf { it > from } ?: (times.last().startMs + 3_000)
                // Still being listened to: only a verse the listening has passed is whole.
                if (!timings.complete && until > timings.heardMs) continue
                val verse = (pool()[drill.surah] ?: getApplication<Application>().assets.open("tajweed_reference.bin").use { TajweedReference.surah(it, drill.surah) }).getOrNull(drill.verse - 1)
                // The word the drill is about, where the reference and the recording count the verse's words alike.
                val at = verse?.takeIf { it.size == times.size }?.let { if (drill.topic == Topic.STOP) drill.stopAfter.takeIf { w -> w in times.indices } else Drills.places(it, drill.topic, drill.letters).firstOrNull() }
                val first = at?.let { if (drill.topic == Topic.STOP) maxOf(0, it - 1) else it }
                return@runCatching Voice(
                    sound, maxOf(0, from - 200), until,
                    first?.let { maxOf(0, times[it].startMs - 150) } ?: -1,
                    first?.let { times.getOrNull(it + 2)?.startMs ?: until } ?: -1,
                )
            }
            null
        }.getOrNull()
    }

    /** Words of a recording on the phone that show [topic], for hearing it there and then; null if there are none. */
    suspend fun hearOf(topic: Topic): Hear? = withContext(Dispatchers.IO) { runCatching { hears(topic, "", 1).firstOrNull() }.getOrNull() }

    /** Opens the lesson on [topic] (for letters, on the pair [letters]): it is taught before it is practised. */
    fun openLesson(topic: Topic, letters: String = "") {
        screen = Screen.Lesson(topic, letters, emptyList())
        // The words to hear it in are looked for meanwhile, and added when they are found.
        viewModelScope.launch {
            val hears = withContext(Dispatchers.IO) { runCatching { hears(topic, letters, HEARS) }.getOrDefault(emptyList()) }
            (screen as? Screen.Lesson)?.takeIf { it.topic == topic && it.letters == letters }?.let { screen = it.copy(hears = hears) }
        }
    }

    /**
     * Words that show [topic], from the recordings that have been listened to the end: the
     * word the rule is in and the one after it, since most rules lie between two words,
     * with where they are in the recording. A few, each from another verse.
     */
    private suspend fun hears(topic: Topic, letters: String, count: Int): List<Hear> {
        val random = Random(System.nanoTime())
        val found = ArrayList<Hear>()
        // The short surahs first: their verses are the ones the exercises are made of.
        val tracks = library.playlists.value.flatMap { it.tracks }.filter { it.ready }.shuffled(random).sortedBy { it.surah !in EXERCISE_SURAHS }
        for (track in tracks.take(HEARS_FROM)) {
            if (found.size >= count) break
            val sound = library.audio(track.id) ?: continue
            val timings = placed(track)?.takeIf { it.complete } ?: continue
            if (!quran.has(track.surah)) continue
            val verses = runCatching { quran.surah(track.surah) }.getOrNull() ?: continue
            val forms = pool()[track.surah] ?: getApplication<Application>().assets.open("tajweed_reference.bin").use { TajweedReference.surah(it, track.surah) }
            var fromThis = 0
            for (v in verses.indices.shuffled(random)) {
                // No more than two from one recording, so that more than one surah is heard.
                if (found.size >= count || fromThis >= 2) break
                val words = verses[v].words
                val times = timings.verses.getOrNull(v) ?: continue
                val verse = forms.getOrNull(v) ?: continue
                // Only where the three count the verse's words alike: then a word is the same word in each.
                if (verse.size != words.size || times.size != words.size || words.size > LONGEST_TO_LOOK_IN) continue
                val at = Drills.places(verse, topic, letters).shuffled(random).firstOrNull() ?: continue
                val first = if (topic == Topic.STOP) maxOf(0, at - 1) else at
                val last = minOf(words.lastIndex, first + 1)
                val until = times.getOrNull(last + 1)?.startMs ?: timings.startOfVerse(v + 1) ?: (times[last].startMs + 2_500)
                found += Hear((first..last).joinToString(" ") { words[it].arabic }, sound, maxOf(0, times[first].startMs - 150), minOf(until, times[first].startMs + LONGEST_HEAR_MS))
                fromThis++
            }
        }
        return found
    }

    /** Opens the verses the reader was last corrected in, each to be recited again for what was corrected. */
    fun openWeak() {
        screen = Screen.Drills(null, "", mistakes.tally.value.open.take(GOES))
    }

    fun openMadd() {
        screen = Screen.Madd
    }

    /** Opens spotting the rule, with words chosen afresh from the short surahs that are on the phone. */
    fun openSpot() {
        viewModelScope.launch { screen = Screen.Spots(withContext(Dispatchers.IO) { runCatching { spots() }.getOrDefault(emptyList()) }) }
    }

    /** Opens echoing a reciter, with short verses chosen afresh from the recordings that have been listened to. */
    fun openEcho() {
        viewModelScope.launch { screen = Screen.Echoes(withContext(Dispatchers.IO) { runCatching { echoes() }.getOrDefault(emptyList()) }) }
    }

    /**
     * Words with one family of rules at work in them (not counting letters that are simply
     * not sounded, which nearly every word has), a family at a time so that every family
     * comes up; each with three other families to choose among, and with its sound where a
     * recording of its surah has been listened to.
     */
    private suspend fun spots(): List<Spot> {
        val random = Random(System.nanoTime())
        val tracks = library.playlists.value.flatMap { it.tracks }
        val byFamily = HashMap<Family, ArrayList<Spot>>()
        for (surah in EXERCISE_SURAHS.sorted()) {
            if (!quran.has(surah)) continue
            val verses = runCatching { quran.surah(surah) }.getOrNull() ?: continue
            val words = verses.map { verse -> verse.words.map { it.arabic } }
            val marks = Tajweed.marks(words.flatten(), Tajweed.stopsAtVerseEnds(words.map { it.size }))
            val track = tracks.firstOrNull { it.surah == surah && it.ready }
            val sound = track?.let { library.audio(it.id) }
            val times = if (track == null || sound == null) null else {
                runCatching { Timings.decode(library.timings(track.id, surah).readText()) }.getOrNull()?.verses?.flatten()?.takeIf { it.size == marks.size }
            }
            var w = 0
            for ((v, verse) in verses.withIndex()) for (word in verse.words) {
                val family = marks[w].map { it.rule.family }.toSet().minus(Family.QUIET).singleOrNull()
                if (family != null) {
                    val from = times?.get(w)?.startMs ?: 0
                    byFamily.getOrPut(family) { ArrayList() } += Spot(
                        surah, v + 1, word.arabic, family,
                        (Family.entries - family - Family.QUIET).shuffled(random).take(3).plus(family).shuffled(random),
                        sound.takeIf { times != null }, from, times?.getOrNull(w + 1)?.startMs?.takeIf { it > from } ?: (from + 1_500),
                        marks[w].firstOrNull { it.rule.family == family }?.rule,
                    )
                }
                w++
            }
        }
        val families = byFamily.keys.shuffled(random)
        if (families.isEmpty()) return emptyList()
        return List(GOES + 2) { byFamily.getValue(families[it % families.size]).random(random) }.distinctBy { Triple(it.surah, it.verse, it.word) }
    }

    /** Short verses of the recordings that have been listened to the end, each with where it is in its recording. */
    private fun echoes(): List<Echo> {
        val random = Random(System.nanoTime())
        val all = ArrayList<Echo>()
        for (track in library.playlists.value.flatMap { it.tracks }.filter { it.ready }) {
            val sound = library.audio(track.id) ?: continue
            val timings = runCatching { Timings.decode(library.timings(track.id, track.surah).readText()) }.getOrNull()?.takeIf { it.complete } ?: continue
            for ((v, words) in timings.verses.withIndex()) {
                val from = timings.startOfVerse(v) ?: continue
                val until = timings.startOfVerse(v + 1) ?: continue
                if (words.size in 3..10 && until - from in 2_000..25_000) all += Echo(track.surah, v + 1, sound, maxOf(0, from - 300), until)
            }
        }
        return all.shuffled(random).take(ECHOES)
    }

    /** Opens one of the voice exercises. */
    fun openExercise(exercise: Int) {
        screen = Screen.Voice(exercise)
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
        val model = size(File(files, "tajweed"))
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

    /** The recording the player had before the one it has now. */
    private var playedBefore: String? = null

    /** The player has moved on to another recording: show its surah, if the one it moved on from is the one on show. */
    fun playing(trackId: String) {
        val before = playedBefore
        playedBefore = trackId
        val track = library.track(trackId) ?: return
        val shown = screen as? Screen.Surah ?: return
        // A surah open just to read stays put, whatever is playing in the background; so
        // does one whose own recording isn't the one that was playing.
        if (shown.trackId == null || shown.trackId == trackId || shown.trackId != before) return
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
            is Screen.Recite -> s.from
            is Screen.Drills, is Screen.Lesson, Screen.Madd, is Screen.Spots, is Screen.Echoes -> Screen.Home
            is Screen.Voice -> Screen.Home
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
        val asked = tracks.associateBy { it.id }
        // All of them in one go: a whole playlist is one change to keep, not one for each recording.
        library.updateAll {
            val track = asked[it.id]
            when {
                track == null -> it
                !track.ready && track.surah in 1..SURAHS.size -> it.copy(wanted = true, error = null, aside = false)
                // A recording that is ready and shows that some verses couldn't be heard again: that is put away.
                track.ready && track.error != null -> it.copy(error = null)
                else -> it
            }
        }
        resume()
    }

    /**
     * The speech model is never left with nothing to do while there is something it could
     * listen to: every recording whose sound is on the phone and that hasn't been listened
     * to the end goes into the queue by itself. That needs no connection. Not while the
     * work is paused, and not a recording the reader took out of the queue themselves, or
     * one that failed: those wait to be asked for.
     *
     * With none of those left, a recording that is ready but whose words were placed the
     * way an earlier version placed them is listened to once more, one at a time, so that
     * what has been put right since (passages said again, for one) is put right in it too
     * without being asked for. It keeps what it has until that is done.
     */
    private fun keepBusy() {
        // Only with the app on the screen: closed, the listening stops (see PrepareWorker).
        if (paused || !PrepareWorker.watched) return
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = library.playlists.value.flatMap { it.tracks }
            fun mayBeTakenUp(track: Track) = !track.wanted && !track.aside && track.error == null && track.surah in 1..SURAHS.size && library.audio(track.id) != null
            val more = tracks.filter { !it.ready && mayBeTakenUp(it) }.map { it.id }.toSet()
            if (more.isNotEmpty()) {
                library.updateAll { if (it.id in more) it.copy(wanted = true) else it }
            } else {
                // Some wait that could be listened to this minute, and nothing is at them: a run
                // that ended as the app was being opened again. Started afresh.
                if (tracks.any { waiting(it) && hearable(it) }) PrepareWorker.start(getApplication())
                if (tracks.any { it.wanted }) return@launch
                val old = tracks.firstOrNull { it.ready && mayBeTakenUp(it) && madeBy(it) < Timings.METHOD } ?: return@launch
                library.update(old.id) { it.copy(wanted = true, refresh = true) }
            }
            if (!paused) PrepareWorker.start(getApplication())
        }
    }

    private fun waiting(track: Track) = track.wanted && track.error == null && (!track.ready || track.againFrom >= 0 || track.refresh)

    /** Whether everything [track] needs to be listened to is on the phone. */
    private fun hearable(track: Track): Boolean {
        val app = getApplication<Application>()
        return library.audio(track.id) != null && quran.has(track.surah) && TajweedStore(app).isReady && EngineStore(app).isReady
    }

    /**
     * Recordings still waiting from last time (the app was closed, or the model couldn't be
     * had) are carried on with: when the app starts, and every time it comes back to the
     * screen, since the listening stops while it is away.
     */
    fun carryOn() {
        val app = getApplication<Application>()
        if (!paused && (Settings.tajweedWanted(app) || library.playlists.value.any { p -> p.tracks.any(::waiting) })) PrepareWorker.start(app)
    }

    /** The way a recording's words were placed (see [Timings.method]); the way it is done now if that can't be read, so it is left alone. */
    private fun madeBy(track: Track): Int = runCatching {
        library.timings(track.id, track.surah).useLines { lines -> lines.first().split(' ').getOrNull(2)?.toIntOrNull() ?: 0 }
    }.getOrDefault(Timings.METHOD)

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

    /**
     * Has the background work fetch the model that checks the reader's own recitation, if
     * it isn't on the phone yet: asked for when the Practise tab or a verse to recite is
     * opened, so it is there by the time it is needed.
     */
    fun wantTajweed() {
        val app = getApplication<Application>()
        if (TajweedStore(app).isReady && EngineStore(app).isReady) return
        Settings.setTajweedWanted(app, true)
        if (!paused) PrepareWorker.start(app)
    }

    /** Takes everything out of the queue. Half-downloaded sound is deleted; finished downloads stay. */
    fun cancel() {
        Settings.setTajweedWanted(getApplication(), false)
        val queued = library.playlists.value.flatMap { it.tracks }.filter { it.wanted }
        library.updateAll { if (it.wanted) it.copy(wanted = false, aside = true) else it }
        for (track in queued) library.dropPartial(track.id)
        paused = false
        Settings.setPaused(getApplication(), false)
        PrepareWorker.stop(getApplication())
    }

    /** Takes one recording out of the queue; the rest carry on. */
    fun cancel(track: Track) {
        library.update(track.id) { it.copy(wanted = false, aside = true) }
        library.dropPartial(track.id)
    }

    /** Corrects which surah a recording is. If it was already downloaded it is lined up again with the new words. */
    fun setSurah(track: Track, surah: Int) {
        if (surah == track.surah) return
        val downloaded = library.audio(track.id) != null
        library.update(track.id) { it.copy(surah = surah, ready = false, playable = false, clarity = 0f, error = null, wanted = downloaded, aside = false) }
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

/** The surahs the exercises take their verses from: Al-Fatihah and the last part of the Quran, whose verses are short and the most widely known by heart. */
private val EXERCISE_SURAHS = (78..114).toSet() + 1

/** How many verses an exercise is made of, and how many an echoing. */
private const val GOES = 8
private const val ECHOES = 6

/** How many stretches of the reader's recordings a lesson offers to hear, out of how many recordings at most, in verses no longer than so many words, and none longer than this. */
private const val HEARS = 5
private const val HEARS_FROM = 12

/** How many recordings' timings are kept at hand for taking a reciter's voice from. */
private const val TIMINGS_KEPT = 6
private const val LONGEST_TO_LOOK_IN = 40
private const val LONGEST_HEAR_MS = 8_000

/** How long the work has had nothing to do before more is looked for, in ms: a run that is only between two things isn't disturbed. */
private const val IDLE_BEFORE_MORE_MS = 4_000L
