package app.spark.data

import android.content.Context
import app.spark.core.Letter
import app.spark.core.Packs
import app.spark.core.Prompt
import app.spark.core.Supply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * Writes a pack on the phone when the feed needs one, by asking a Gemini model with the
 * user's own key: first for the reels, with the last days' news to draw on, then for the
 * lessons. It is asked only when the feed is running out or what it has is old
 * ([Supply.low]), and at most once a day. Each
 * answer is checked as any pack is, and sent back once with what was wrong with it. What
 * still breaks a rule after that is left out, as it is from a fetched pack.
 *
 * The key is kept in a file of the app's that Android leaves out of backups, and goes
 * nowhere but to Google with each request.
 */
object Writer {

    sealed interface State {
        data object Idle : State

        /** Asking Google whether it accepts a key just given. */
        data object Checking : State

        /** It does. */
        data object Works : State

        /** At work on [step] of [STEPS]: the news, the reels, the lessons. */
        data class Working(val step: Int) : State
        data class Done(val reels: Int, val lessons: Int) : State

        /** [said] is the service's own explanation, when it gave one. */
        data class Failed(val why: Why, val said: String? = null) : State
    }

    enum class Why { OFFLINE, KEY, QUOTA, EMPTY, OTHER }

    const val STEPS = 3
    private const val RESTING_MS = 60 * 60 * 1000L
    private const val BRIEF = "GENERATE.md"
    private const val STARTER = "starter.json"

    /** Today. Tests set their own. */
    internal var today: () -> LocalDate = LocalDate::now

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _key = MutableStateFlow<String?>(null)

    /** The user's key, or null when they have given none. */
    val key: StateFlow<String?> = _key.asStateFlow()

    private var opened = false
    private var tried = 0L
    private val working = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Reads the key, the first time it is called. */
    fun open(context: Context) {
        if (!opened) load(context)
    }

    internal fun load(context: Context) {
        opened = true
        tried = 0
        _state.value = State.Idle
        _key.value = try {
            file(context).readText().trim().takeIf { it.isNotEmpty() }
        } catch (_: IOException) {
            null
        }
    }

    /**
     * Keeps [key] as the user's, or forgets the one kept when it is blank. A key given is
     * tried on Google at once, and if it is accepted and the feed is low, set to work.
     */
    fun key(context: Context, key: String) {
        val app = context.applicationContext
        val clean = key.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) file(app).delete() else file(app).writeText(clean)
        _key.value = clean.takeIf { it.isNotEmpty() }
        _state.value = State.Idle
        tried = 0
        if (clean.isNotEmpty()) scope.launch { check(app, clean) }
    }

    internal suspend fun check(app: Context, key: String) {
        _state.value = State.Checking
        val found = try {
            withContext(Dispatchers.IO) { Gemini.models(key) }
            State.Works
        } catch (e: Gemini.Refused) {
            State.Failed(if (e.why == Gemini.Why.KEY) Why.KEY else Why.OTHER, e.said)
        } catch (_: IOException) {
            State.Failed(Why.OFFLINE)
        }
        // The key may have been taken back, or another given, while Google was asked.
        if (_key.value != key) return
        _state.value = found
        if (found == State.Works) write(app, quiet = true)
    }

    /** Whether today's pack has been written. */
    fun written(context: Context): Boolean = Library.made(context, today().toString())

    /** Whether the feed is running out or has gone old, so that a pack is worth asking for. */
    fun needed(): Boolean {
        val reels = Library.shelf.value.reels
        val kept = Store.kept.value
        val newest = reels.values.maxOfOrNull { it.day } ?: return true
        return Supply.low(reels.keys.count { it !in kept.seen }, kept.goal, newest, today().toEpochDay())
    }

    /**
     * Sets about writing today's pack, unless there is no key or it is already written or
     * being written. A [quiet] start, the app's own, also holds back while the feed has
     * enough that is fresh, and for an hour after a try. Carries on when the screen that
     * asked is closed.
     */
    fun start(context: Context, quiet: Boolean) {
        val app = context.applicationContext
        scope.launch { write(app, quiet) }
    }

    internal suspend fun write(app: Context, quiet: Boolean, now: Long = System.currentTimeMillis()) {
        val key = _key.value ?: return
        val date = today()
        if (Library.made(app, date.toString())) return
        if (quiet && (now - tried < RESTING_MS || !needed())) return
        if (!working.tryLock()) return
        try {
            tried = now
            val kept = Store.kept.value
            val shelf = Library.shelf.value
            _state.value = State.Working(1)
            _state.value = try {
                withContext(Dispatchers.IO) {
                    Draft(app, key, date, Letter.reader(kept.wishes, kept.taste), shelf).write { _state.value = State.Working(it) }
                }
            } catch (e: Gemini.Refused) {
                when (e.why) {
                    Gemini.Why.KEY -> State.Failed(Why.KEY, e.said)
                    Gemini.Why.QUOTA -> State.Failed(Why.QUOTA, e.said)
                    else -> State.Failed(Why.OTHER, e.said)
                }
            } catch (_: IOException) {
                State.Failed(Why.OFFLINE)
            }
        } finally {
            working.unlock()
        }
    }

    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, "gemini.key")

    /** One day's pack in the making. */
    private class Draft(val app: Context, val key: String, val date: LocalDate, val reader: String, val shelf: Library.Shelf) {
        val day = date.toString()
        val brief = app.assets.open(BRIEF).bufferedReader().use { it.readText() }
        val starter = JSONObject(app.assets.open(STARTER).bufferedReader().use { it.readText() })
        var models = emptyList<String>()

        fun write(step: (Int) -> Unit): State {
            val stories = News.fetch()
            models = Gemini.models(key)

            step(2)
            val hooks = shelf.reels.values.map { it.hook }
            val reels = part(Prompt.system(brief, examples(lessons = false)), Prompt.reels(day, reader, hooks, stories), stories)

            step(3)
            val used = shelf.lessons.values.map { it.title } + list(reels, "reels").map { it.optString("hook") }
            val lessons = part(Prompt.system(brief, examples(lessons = true)), Prompt.lessons(day, reader, used), emptyList())

            val pack = pack(list(reels, "reels") + list(lessons, "reels"), list(lessons, "lessons"))
            val read = Packs.read(pack).pack
            if (read == null || read.reels.isEmpty()) return State.Failed(Why.EMPTY)
            Library.keep(app, day, pack, date.minusDays(Library.KEPT_DAYS).toString())
            return State.Done(read.reels.size, read.lessons.size)
        }

        /**
         * One of the pack's two answers. An answer the checker faults is sent back once with
         * its faults, and the better of the two is kept. News that doesn't point at one of
         * the [stories] it was given is left out: a link nobody handed the model is a link it made up.
         */
        private fun part(system: String, ask: String, stories: List<Prompt.Story>): JSONObject {
            val urls = stories.map { it.url }.toSet()
            val answer = ask(system, ask)
            val first = read(answer)
            val faults = first?.let { faults(it, urls) } ?: listOf("the answer wasn't one JSON object")
            var best = first
            if (faults.isNotEmpty()) {
                // A second answer that never comes leaves the first to stand.
                val second = try {
                    read(ask(system, Prompt.again(ask, answer, faults)))
                } catch (_: IOException) {
                    null
                }
                if (second != null && (first == null || faults(second, urls).size <= faults.size)) best = second
            }
            val kept = best ?: throw Gemini.Refused(Gemini.Why.OTHER, "The model's answer could not be read.")
            val reels = JSONArray()
            for (reel in list(kept, "reels")) if (sourced(reel, urls)) reels.put(reel)
            return kept.put("reels", reels)
        }

        // The first model that answers is the one the rest of the pack is asked of.
        private fun ask(system: String, text: String): String {
            var last: Gemini.Refused? = null
            for (model in models) {
                try {
                    return Gemini.ask(key, model, system, text).also { models = listOf(model) }
                } catch (e: Gemini.Refused) {
                    if (e.why == Gemini.Why.KEY) throw e
                    last = e
                }
            }
            throw last ?: Gemini.Refused(Gemini.Why.OTHER, "No model to ask.")
        }

        /** The JSON object in [answer], whatever was put around it. */
        private fun read(answer: String): JSONObject? {
            val from = answer.indexOf('{')
            val to = answer.lastIndexOf('}')
            if (from < 0 || to < from) return null
            return try {
                JSONObject(answer.substring(from, to + 1))
            } catch (_: Exception) {
                null
            }
        }

        /** What the checker finds wrong with an answer, read as the pack it is part of. */
        private fun faults(answer: JSONObject, urls: Set<String>): List<String> {
            val unsourced = list(answer, "reels").filterNot { sourced(it, urls) }
                .map { "${it.optString("id")}: a news reel's \"source\" must be one of the stories given, its \"url\" exactly as given" }
            return unsourced + Packs.read(pack(list(answer, "reels"), list(answer, "lessons"))).problems
        }

        // Only news has a source, and only one it was handed.
        private fun sourced(reel: JSONObject, urls: Set<String>): Boolean {
            if (reel.optString("kind") != "news") {
                reel.remove("source")
                return true
            }
            return reel.optJSONObject("source")?.optString("url") in urls
        }

        private fun pack(reels: List<JSONObject>, lessons: List<JSONObject>): String =
            JSONObject().put("format", Packs.FORMAT).put("day", day).put("reels", JSONArray(reels)).put("lessons", JSONArray(lessons)).toString()

        private fun list(json: JSONObject, name: String): List<JSONObject> {
            val array = json.optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).mapNotNull(array::optJSONObject)
        }

        /**
         * Reels, or a lesson with the reel that leads to it, from the pack the app ships
         * with: what the model is shown as the standard to match. A different few each day.
         */
        private fun examples(lessons: Boolean): String {
            val reels = list(starter, "reels")
            val turn = date.toEpochDay().toInt()
            val shown = JSONObject()
            if (lessons) {
                val lesson = list(starter, "lessons").let { it[turn.mod(it.size)] }
                shown.put("reels", JSONArray(reels.filter { it.optString("lesson") == lesson.optString("id") }))
                shown.put("lessons", JSONArray(listOf(lesson)))
            } else {
                val alone = reels.filterNot { it.has("lesson") }
                val puzzles = alone.filter { it.optString("kind") == "puzzle" }
                val ideas = alone.filter { it.optString("kind") == "idea" }
                shown.put("reels", JSONArray(listOf(puzzles[turn.mod(puzzles.size)], ideas[turn.mod(ideas.size)], ideas[(turn + ideas.size / 2).mod(ideas.size)])))
            }
            return shown.toString(1)
        }
    }
}
