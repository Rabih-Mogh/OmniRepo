package app.recitation.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * One recording of a playlist.
 * [surah]: which surah it is, 0 when the title didn't say. [wanted]: the user asked for it
 * to be downloaded and prepared. [playable]: its sound and its surah's words are on the
 * phone, so it can be played; words light up as far as the listening has got. [ready]:
 * the listening is finished and every word's timing is saved.
 * [clarity]: how well the recording matched the surah's words (see `Timings.clarity`).
 */
data class Track(
    val id: String,
    val title: String,
    val seconds: Long,
    val surah: Int,
    val wanted: Boolean = false,
    val ready: Boolean = false,
    val clarity: Float = 0f,
    val error: String? = null,
    val playable: Boolean = false,
    /** Verses (counting from 0) waiting to be listened to again by themselves; -1 when none are. */
    val againFrom: Int = -1,
    val againTo: Int = -1,
) {
    /** Whether it can be opened to play: fully prepared, or far enough along. */
    val canPlay: Boolean get() = ready || playable
}

data class Playlist(val url: String, val title: String, val channel: String, val tracks: List<Track>)

/** The playlists the user has added, kept in one small file, and where each recording's files live. */
class Library private constructor(context: Context) {

    private val dir = context.filesDir
    private val file = File(dir, "library.json")
    private val audioDir = File(dir, "audio")
    private val timingsDir = File(dir, "timings")
    private val heardDir = File(dir, "heard")
    private val forgotten = ConcurrentHashMap<String, Int>()

    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    /** The downloaded sound of a recording, or null if it isn't (fully) here. */
    fun audio(id: String): File? = audioDir.listFiles { f -> f.name.startsWith("$id.") && !f.name.endsWith(".part") }?.firstOrNull()

    fun audioPart(id: String, extension: String): File = File(audioDir.apply { mkdirs() }, "$id.$extension.part")

    /** Kept beside a half-finished download: how big the sound it is the start of is, so that it is only ever carried on with more of the same. */
    fun sizeNote(part: File): File = File(part.path.removeSuffix(".part") + ".size.part")

    fun timings(id: String, surah: Int): File = File(timingsDir.apply { mkdirs() }, "$id-$surah.txt")

    /** What the model heard in a recording, kept until its timings are settled. */
    fun heard(id: String): File = File(heardDir.apply { mkdirs() }, "$id.rows")

    /** Where the reciter pauses in a recording, found by a quick look over its sound. */
    fun pauses(id: String): File = File(heardDir.apply { mkdirs() }, "$id.pauses")

    fun track(id: String): Track? = _playlists.value.firstNotNullOfOrNull { p -> p.tracks.firstOrNull { it.id == id } }

    @Synchronized
    fun add(playlist: Playlist) {
        // Adding a playlist again refreshes its list of videos but keeps what was done with them.
        val old = _playlists.value.firstOrNull { it.url == playlist.url }
        val merged = playlist.copy(tracks = playlist.tracks.map { t -> old?.tracks?.firstOrNull { it.id == t.id } ?: t })
        save(_playlists.value.filter { it.url != playlist.url } + merged)
    }

    @Synchronized
    fun remove(url: String) {
        val gone = _playlists.value.firstOrNull { it.url == url } ?: return
        save(_playlists.value - gone)
        val kept = _playlists.value.flatMap { it.tracks }.map { it.id }.toSet()
        for (track in gone.tracks) if (track.id !in kept) {
            forgotten.merge(track.id, 1, Int::plus)
            deleteFiles(track.id)
        }
    }

    @Synchronized
    fun update(id: String, change: (Track) -> Track) = updateAll { if (it.id == id) change(it) else it }

    /** Changes every recording that [change] has something to change in, and keeps the lot once. */
    @Synchronized
    fun updateAll(change: (Track) -> Track) {
        save(_playlists.value.map { p -> p.copy(tracks = p.tracks.map(change)) })
    }

    /** Frees a recording's storage and forgets its timings; it stays in the playlist. */
    @Synchronized
    fun forget(id: String) {
        forgotten.merge(id, 1, Int::plus)
        update(id) { it.copy(wanted = false, ready = false, playable = false, error = null, clarity = 0f) }
        deleteFiles(id)
    }

    /**
     * How many times a recording's files have been deleted, by [forget] or with its playlist.
     * The background work may be in the middle of that recording at the time: it asks before
     * and after, and if the number has changed it stops and clears away what it wrote meanwhile.
     */
    fun timesForgotten(id: String): Int = forgotten[id] ?: 0

    /** Deletes whatever files a recording has: for the background work, after the recording was forgotten under it. */
    fun clear(id: String) = deleteFiles(id)

    /** Deletes a recording's half-finished download, except [keep] and its [sizeNote]. */
    fun dropPartial(id: String, keep: File? = null) {
        val kept = setOfNotNull(keep, keep?.let(::sizeNote))
        audioDir.listFiles { f -> f.name.startsWith("$id.") && f.name.endsWith(".part") && f !in kept }?.forEach { it.delete() }
    }

    private fun deleteFiles(id: String) {
        audioDir.listFiles { f -> f.name.startsWith("$id.") }?.forEach { it.delete() }
        timingsDir.listFiles { f -> f.name.startsWith("$id-") }?.forEach { it.delete() }
        heardDir.listFiles { f -> f.name.startsWith("$id.") }?.forEach { it.delete() }
    }

    private fun save(playlists: List<Playlist>) {
        _playlists.value = playlists
        val json = JSONArray()
        for (p in playlists) {
            val tracks = JSONArray()
            for (t in p.tracks) {
                tracks.put(
                    JSONObject().put("id", t.id).put("title", t.title).put("seconds", t.seconds).put("surah", t.surah)
                        .put("wanted", t.wanted).put("ready", t.ready).put("clarity", t.clarity.toDouble()).put("error", t.error ?: JSONObject.NULL)
                        .put("playable", t.playable).put("againFrom", t.againFrom).put("againTo", t.againTo),
                )
            }
            json.put(JSONObject().put("url", p.url).put("title", p.title).put("channel", p.channel).put("tracks", tracks))
        }
        // With the storage full the list can't be kept. It is still right for as long as the
        // app stays open, and the next change tries again.
        try {
            file.writeWhole(json.toString())
        } catch (_: IOException) {
        }
    }

    private fun load(): List<Playlist> = if (!file.exists()) emptyList() else try {
        val json = JSONArray(file.readText())
        List(json.length()) { i ->
            val p = json.getJSONObject(i)
            val tracks = p.getJSONArray("tracks")
            Playlist(
                p.getString("url"), p.getString("title"), p.getString("channel"),
                List(tracks.length()) { k ->
                    val t = tracks.getJSONObject(k)
                    Track(
                        t.getString("id"), t.getString("title"), t.getLong("seconds"), t.getInt("surah"),
                        t.getBoolean("wanted"), t.getBoolean("ready"), t.getDouble("clarity").toFloat(),
                        if (t.isNull("error")) null else t.getString("error"),
                        t.optBoolean("playable", false), t.optInt("againFrom", -1), t.optInt("againTo", -1),
                    )
                },
            )
        }
    } catch (_: Exception) {
        // It can't be read. Start afresh, but not by writing over it.
        file.setAside()
        emptyList()
    }

    companion object {
        @Volatile private var instance: Library? = null

        fun get(context: Context): Library =
            instance ?: synchronized(this) { instance ?: Library(context.applicationContext).also { instance = it } }
    }
}
