package app.spark.data

import android.content.Context
import app.spark.core.Lesson
import app.spark.core.Packs
import app.spark.core.Reel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Every reel and lesson the app has. The starter pack ships inside the app, so the feed is
 * never empty. The rest are packs kept in the app's files, of two kinds. Some are fetched
 * from the repo's `spark-content` branch: `index.json` names the packs on offer, each is
 * fetched once, and one the index no longer names is deleted. The others are written on
 * the phone ([Writer]), one a day, and kept for [KEPT_DAYS] days.
 */
object Library {

    /** The reels and lessons by id, oldest pack first. */
    data class Shelf(val reels: Map<String, Reel> = emptyMap(), val lessons: Map<String, Lesson> = emptyMap())

    sealed interface State {
        data object Idle : State
        data object Looking : State

        /** [reels] new reels arrived; none is zero. */
        data class Found(val reels: Int) : State
        data object Failed : State
    }

    /** Where the packs are published. Null keeps the app to what it already has. */
    internal var base: String? = "https://raw.githubusercontent.com/Rabih-Mogh/OmniRepo/spark-content/"

    private const val STARTER = "starter.json"
    private const val LIMIT = 2_000_000 // No pack comes near this; anything larger isn't one.
    private const val RESTING_MS = 60 * 60 * 1000L

    /** How long a pack written on the phone is kept, as the published ones are. */
    const val KEPT_DAYS = 45L
    private val NAME = Regex("[a-z0-9-]{1,40}")

    private val _shelf = MutableStateFlow(Shelf())
    val shelf: StateFlow<Shelf> = _shelf.asStateFlow()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var opened = false
    private var looked = 0L
    private val fetching = Mutex()

    /** Reads what is on the phone, the first time it is called. */
    fun open(context: Context) {
        if (!opened) load(context)
    }

    internal fun load(context: Context) {
        opened = true
        looked = 0
        _state.value = State.Idle
        read(context.applicationContext)
    }

    private fun read(app: Context) {
        val texts = ArrayList<String>()
        texts += app.assets.open(STARTER).bufferedReader().use { it.readText() }
        for (file in (folder(app).listFiles().orEmpty().toList() + made(app).listFiles().orEmpty()).sortedBy { it.name }) {
            if (file.extension == "json") texts += file.readText()
        }
        val reels = LinkedHashMap<String, Reel>()
        val lessons = LinkedHashMap<String, Lesson>()
        for (text in texts) {
            val pack = Packs.read(text).pack ?: continue
            pack.lessons.forEach { lessons.putIfAbsent(it.id, it) }
            pack.reels.forEach { reels.putIfAbsent(it.id, it) }
        }
        _shelf.value = Shelf(reels, lessons)
    }

    /**
     * Fetches the packs the phone doesn't have yet. A [quiet] look happens at most once an
     * hour and says nothing; one the user asked for always happens and reports what it found.
     */
    suspend fun refresh(context: Context, quiet: Boolean = true, now: Long = System.currentTimeMillis()) {
        val from = base ?: return
        if (quiet && now - looked < RESTING_MS) return
        val app = context.applicationContext
        fetching.withLock {
            if (!quiet) _state.value = State.Looking
            val before = _shelf.value.reels.size
            val result = try {
                val (changed, whole) = withContext(Dispatchers.IO) { fetch(from, folder(app)) }
                if (changed) read(app)
                if (whole) looked = now
                if (whole) State.Found(_shelf.value.reels.size - before) else State.Failed
            } catch (_: IOException) {
                State.Failed
            }
            if (!quiet) _state.value = result
        }
    }

    /**
     * Brings [dir] in line with the index at [from]. Says whether a pack was added or
     * deleted, and whether every pack the index names is now here or known to be unreadable.
     */
    private fun fetch(from: String, dir: File): Pair<Boolean, Boolean> {
        val index = Packs.index(get(from + "index.json")) ?: throw IOException("The index can't be read.")
        val names = index.filter(NAME::matches)
        dir.mkdirs()
        var changed = false
        var whole = true
        for (file in dir.listFiles().orEmpty()) {
            if (file.nameWithoutExtension !in names && file.delete()) changed = true
        }
        for (name in names) {
            val file = File(dir, "$name.json")
            if (file.exists()) continue
            val text = try {
                get(from + "packs/$name.json")
            } catch (_: IOException) {
                // One pack that won't come shouldn't keep the ones after it away.
                whole = false
                continue
            }
            // A pack this version can't read is passed over.
            if (Packs.read(text).pack == null) continue
            val part = File(dir, "$name.part")
            part.writeText(text)
            if (part.renameTo(file)) changed = true
        }
        return changed to whole
    }

    private fun get(url: String): String = Net.get(url, LIMIT)

    /** Whether the pack written on the phone for [day] (a date like 2026-11-28) is here. */
    fun made(context: Context, day: String): Boolean = File(made(context), "$day.json").exists()

    /**
     * Keeps [text] as the pack written on the phone for [day], puts its reels and lessons
     * on the shelf, and lets go of such packs from before [oldest].
     */
    fun keep(context: Context, day: String, text: String, oldest: String) {
        val dir = made(context.applicationContext)
        dir.mkdirs()
        for (file in dir.listFiles().orEmpty()) if (file.nameWithoutExtension < oldest) file.delete()
        val part = File(dir, "$day.part")
        part.writeText(text)
        if (part.renameTo(File(dir, "$day.json"))) read(context.applicationContext)
    }

    private fun folder(context: Context) = File(context.filesDir, "packs")

    private fun made(context: Context) = File(context.filesDir, "made")
}
