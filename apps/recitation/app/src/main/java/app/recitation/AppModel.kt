package app.recitation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.recitation.core.SURAHS
import app.recitation.core.SurahMatcher
import app.recitation.core.Timings
import app.recitation.data.Library
import app.recitation.data.Playlist
import app.recitation.data.QuranStore
import app.recitation.data.Track
import app.recitation.data.Verse
import app.recitation.prepare.PrepareWorker
import app.recitation.yt.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which screen is showing. */
sealed interface Screen {
    data object Playlists : Screen
    data class Recordings(val url: String) : Screen
    data class Reader(val url: String, val trackId: String) : Screen
}

/** Everything the reading screen needs for one recording. */
class Reading(val track: Track, val verses: List<Verse>, val timings: Timings)

class AppModel(app: Application) : AndroidViewModel(app) {

    val library = Library.get(app)
    private val quran = QuranStore(app)

    var screen by mutableStateOf<Screen>(Screen.Playlists)
        private set

    /** A link waiting in the "add playlist" box: shared from YouTube, or being typed. Null when the box is closed. */
    var link by mutableStateOf<String?>(null)
    var adding by mutableStateOf(false)
        private set
    var addError by mutableStateOf<String?>(null)
        private set

    /** The open recording's words and timings; null while they load. */
    var reading by mutableStateOf<Reading?>(null)
        private set

    fun open(playlist: Playlist) {
        screen = Screen.Recordings(playlist.url)
    }

    fun open(playlist: Playlist, track: Track) {
        screen = Screen.Reader(playlist.url, track.id)
        load(track.id)
    }

    /** Loads the words and timings of a recording, when it's opened or the player moves on to it. */
    fun load(trackId: String) {
        if (reading?.track?.id == trackId) return
        reading = null
        val track = library.track(trackId) ?: return
        viewModelScope.launch {
            reading = withContext(Dispatchers.IO) {
                runCatching { Reading(track, quran.surah(track.surah), Timings.decode(library.timings(track.id, track.surah).readText())) }.getOrNull()
            }
        }
    }

    /** One screen back. False when there's nowhere to go back to. */
    fun back(): Boolean {
        screen = when (val s = screen) {
            Screen.Playlists -> return false
            is Screen.Recordings -> Screen.Playlists
            is Screen.Reader -> Screen.Recordings(s.url)
        }
        return true
    }

    /** Takes a link out of text shared from another app, e.g. "Check this out: https://…". */
    fun offer(shared: String) {
        link = Regex("https?://\\S+").find(shared)?.value ?: shared.trim()
        addError = null
        screen = Screen.Playlists
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
                screen = Screen.Recordings(playlist.url)
            } catch (e: Exception) {
                addError = getApplication<Application>().getString(
                    if (e is IllegalArgumentException) R.string.add_not_a_link else R.string.add_failed,
                )
            } finally {
                adding = false
            }
        }
    }

    /** Asks for recordings to be downloaded and prepared. Ones without a known surah are left for the user to set. */
    fun want(tracks: List<Track>) {
        for (track in tracks) {
            if (!track.ready && track.surah in 1..SURAHS.size) library.update(track.id) { it.copy(wanted = true, error = null) }
        }
        PrepareWorker.start(getApplication())
    }

    fun cancel() {
        for (track in library.playlists.value.flatMap { it.tracks }) {
            if (track.wanted) library.update(track.id) { it.copy(wanted = false) }
        }
        PrepareWorker.stop(getApplication())
    }

    /** Corrects which surah a recording is. If it was already downloaded it is lined up again with the new words. */
    fun setSurah(track: Track, surah: Int) {
        if (surah == track.surah) return
        val downloaded = library.audio(track.id) != null
        library.update(track.id) { it.copy(surah = surah, ready = false, clarity = 0f, error = null, wanted = downloaded) }
        if (downloaded) PrepareWorker.start(getApplication())
    }

    fun forget(track: Track) = library.forget(track.id)

    fun remove(playlist: Playlist) {
        library.remove(playlist.url)
        screen = Screen.Playlists
    }
}
