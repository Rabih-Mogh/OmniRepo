package app.spark.data

import android.content.Context
import android.content.SharedPreferences
import app.spark.core.Days
import app.spark.core.Leaning
import app.spark.core.Lesson
import app.spark.core.Reel
import app.spark.core.Taste
import app.spark.core.Watch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * What the app remembers about its user: what they want to learn, what has held their
 * attention, which reels they have seen and liked, the lessons they finished, and the
 * day's goal and streak. Kept in the app's own preferences file. Used from the main
 * thread only.
 */
object Store {

    /** How a finished lesson went: [right] of the [of] steps that are marked. */
    data class Score(val right: Int, val of: Int)

    data class Kept(
        val welcomed: Boolean = false,
        val goal: Int = 10,
        val wishes: List<String> = emptyList(),
        val taste: Taste = Taste(),
        val seen: Set<String> = emptySet(),
        val liked: Set<String> = emptySet(),
        val lessons: Map<String, Score> = emptyMap(),
        val days: Days = Days(),
    )

    val GOALS = listOf(5, 10, 15, 20)

    /** Today, as a day counted from 1970. Tests set their own. */
    internal var today: () -> Long = { LocalDate.now().toEpochDay() }

    private val _kept = MutableStateFlow(Kept())
    val kept: StateFlow<Kept> = _kept.asStateFlow()

    private var prefs: SharedPreferences? = null

    /** Reads what was kept, the first time it is called. */
    fun open(context: Context) {
        if (prefs == null) load(context)
    }

    internal fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences("spark", Context.MODE_PRIVATE)
        prefs = p
        _kept.value = p.getString("kept", null)?.let {
            try {
                read(JSONObject(it))
            } catch (_: Exception) {
                null // Unreadable: start again sooner than not start at all.
            }
        } ?: Kept()
    }

    fun welcomed() = change { it.copy(welcomed = true) }

    fun goal(reels: Int) = change { it.copy(goal = reels) }

    fun wish(text: String) {
        val wish = text.replace(Regex("\\s+"), " ").trim().take(120)
        if (wish.isNotEmpty()) change { if (it.wishes.any { w -> w.equals(wish, ignoreCase = true) }) it else it.copy(wishes = it.wishes + wish) }
    }

    fun unwish(wish: String) = change { it.copy(wishes = it.wishes - wish) }

    fun like(reel: Reel, liked: Boolean) = change { it.copy(liked = if (liked) it.liked + reel.id else it.liked - reel.id) }

    /**
     * [reel] has left the screen, and [watch] says how it went. It won't be shown again, the
     * taste moves, and unless it was flicked past it counts towards the day's goal.
     */
    fun watched(reel: Reel, watch: Watch, lessonsLeft: Boolean) = change {
        val counts = watch.share >= 0.15 || watch.liked || watch.lesson
        it.copy(
            seen = it.seen + reel.id,
            taste = it.taste.after(reel, watch.reward()),
            days = if (counts && reel.id !in it.seen) it.days.watched(today(), it.goal, lessonsLeft) else it.days,
        )
    }

    fun finished(lesson: Lesson, score: Score, lessonsLeft: Boolean) = change {
        it.copy(
            // The first go at a lesson is the one that is kept.
            lessons = if (lesson.id in it.lessons) it.lessons else it.lessons + (lesson.id to score),
            days = it.days.learned(today(), it.goal, lessonsLeft),
        )
    }

    /** Forgets reels and lessons the library no longer has, so what is kept doesn't grow for ever. */
    fun keepOnly(reels: Set<String>, lessons: Set<String>) {
        val now = _kept.value
        if (now.seen.all(reels::contains) && now.liked.all(reels::contains) && now.lessons.keys.all(lessons::contains)) return
        change { it.copy(seen = it.seen intersect reels, liked = it.liked intersect reels, lessons = it.lessons.filterKeys(lessons::contains)) }
    }

    private fun change(how: (Kept) -> Kept) {
        val next = how(_kept.value)
        if (next == _kept.value) return
        _kept.value = next
        prefs?.edit()?.putString("kept", write(next).toString())?.apply()
    }

    private fun write(kept: Kept) = JSONObject()
        .put("welcomed", kept.welcomed)
        .put("goal", kept.goal)
        .put("wishes", JSONArray(kept.wishes))
        .put("topics", leanings(kept.taste.topics))
        .put("tags", leanings(kept.taste.tags))
        .put("seen", JSONArray(kept.seen))
        .put("liked", JSONArray(kept.liked))
        .put("lessons", JSONObject().also { json -> kept.lessons.forEach { (id, s) -> json.put(id, JSONArray(listOf(s.right, s.of))) } })
        .put("day", kept.days.day)
        .put("reels", kept.days.reels)
        .put("lesson", kept.days.lesson)
        .put("streak", kept.days.streak)
        .put("met", kept.days.met)

    private fun read(json: JSONObject): Kept {
        val lessons = json.getJSONObject("lessons")
        return Kept(
            welcomed = json.getBoolean("welcomed"),
            goal = json.getInt("goal"),
            wishes = strings(json.getJSONArray("wishes")),
            taste = Taste(leanings(json.getJSONObject("topics")), leanings(json.getJSONObject("tags"))),
            seen = strings(json.getJSONArray("seen")).toSet(),
            liked = strings(json.getJSONArray("liked")).toSet(),
            lessons = lessons.keys().asSequence().associateWith { id -> lessons.getJSONArray(id).let { Score(it.getInt(0), it.getInt(1)) } },
            days = Days(json.getLong("day"), json.getInt("reels"), json.getBoolean("lesson"), json.getInt("streak"), json.getLong("met")),
        )
    }

    private fun leanings(map: Map<String, Leaning>) =
        JSONObject().also { json -> map.forEach { (name, l) -> json.put(name, JSONArray().put(l.value).put(l.count)) } }

    private fun leanings(json: JSONObject): Map<String, Leaning> =
        json.keys().asSequence().associateWith { name -> json.getJSONArray(name).let { Leaning(it.getDouble(0), it.getInt(1)) } }

    private fun strings(array: JSONArray): List<String> = (0 until array.length()).map(array::getString)
}
