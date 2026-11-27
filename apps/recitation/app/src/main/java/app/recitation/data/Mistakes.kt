package app.recitation.data

import android.content.Context
import app.recitation.core.Drill
import app.recitation.core.Drills
import app.recitation.core.Topic
import app.recitation.core.Went
import app.recitation.core.WordRecited
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * What the reader has been corrected on: how often each topic came up ([counts]), and
 * the verses in which one did and hasn't been put right since ([open], the latest first),
 * each as the go of an exercise that practises it. And the lessons learned ([learned]),
 * with the best each lesson's exercise has gone so far ([best]: the goes that were sound,
 * and of how many).
 */
class Tally(
    val counts: Map<Topic, Int> = emptyMap(),
    val open: List<Drill> = emptyList(),
    val learned: Set<String> = emptySet(),
    val best: Map<String, Pair<Int, Int>> = emptyMap(),
) {
    /** Whether the lesson on [topic] (for letters, on the pair [letters]) has been learned: an exercise on it was done and went well. */
    fun learned(topic: Topic, letters: String = ""): Boolean = Mistakes.key(topic, letters) in learned

    /** The best the exercise on [topic] has gone: how many goes were sound, of how many. Null before it has been tried. */
    fun best(topic: Topic, letters: String = ""): Pair<Int, Int>? = best[Mistakes.key(topic, letters)]

    /** The topics corrected most often, the most first: where the reader is weakest. */
    val weakest: List<Pair<Topic, Int>> get() = counts.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key to it.value }
}

/**
 * Keeps a [Tally] in a small file on the phone. Every verse recited to the phone is noted:
 * what was corrected in it is added, and what was corrected in it before and is sound now
 * is taken off the list of verses to go over. What was corrected in a word that this time
 * wasn't said stays on the list: it wasn't put right.
 */
class Mistakes(private val file: File) {
    private val _tally = MutableStateFlow(load())
    val tally: StateFlow<Tally> = _tally.asStateFlow()

    /**
     * Notes how [verse] of [surah] (both from 1) went: [words] as it was heard. Recited for
     * an exercise on one thing ([only]), only that thing is noted: whatever else was heard
     * in the verse is neither counted against the reader nor taken as put right.
     */
    @Synchronized
    fun note(surah: Int, verse: Int, words: List<WordRecited>, only: Drill? = null) {
        val now = _tally.value
        val counts = HashMap(now.counts)
        val missed = LinkedHashSet<Drill>()
        for (correction in words.flatMap { it.corrections }) {
            val topic = Drills.topicOf(correction) ?: continue
            // A letter said for another is practised only where the two are a pair that is drilled.
            val letters = if (topic == Topic.LETTERS) Drills.pairOf(correction) else ""
            if (topic == Topic.LETTERS && letters.isEmpty()) continue
            if (only != null && (topic != only.topic || (topic == Topic.LETTERS && letters != only.letters))) continue
            missed += Drill(surah, verse, topic, letters)
        }
        for (drill in missed) counts[drill.topic] = (counts[drill.topic] ?: 0) + 1
        // Recited again: what was to go over in it stands only if it still is, or if the words that carry it weren't said this time.
        val open = (missed + now.open.filter { it.surah != surah || it.verse != verse || (only != null && it.topic != only.topic) || Drills.went(it, words) == Went.UNHEARD }).take(KEPT)
        if (missed.isEmpty() && open.size == now.open.size) return
        _tally.value = Tally(counts, open, now.learned, now.best)
        save()
    }

    /** Notes that the lesson on [topic] (with its [letters]) has been learned. */
    @Synchronized
    fun learned(topic: Topic, letters: String = "") {
        val now = _tally.value
        if (now.learned(topic, letters)) return
        _tally.value = Tally(now.counts, now.open, now.learned + key(topic, letters), now.best)
        save()
    }

    /**
     * Notes how far an exercise on [topic] (with its [letters]) has got: [sound] of its [of]
     * goes were sound. The best it has gone is kept, and the lesson counts as learned once
     * three goes in four are sound in one exercise (see [Drills.needed]).
     */
    @Synchronized
    fun scored(topic: Topic, letters: String, sound: Int, of: Int) {
        if (of <= 0) return
        val now = _tally.value
        val key = key(topic, letters)
        val before = now.best[key]
        // The better share; of two alike, the longer exercise.
        val better = before == null || sound * before.second > before.first * of || (sound * before.second == before.first * of && of > before.second)
        val learned = sound >= Drills.needed(of)
        if (!better && (!learned || key in now.learned)) return
        _tally.value = Tally(now.counts, now.open, if (learned) now.learned + key else now.learned, if (better) now.best + (key to (sound to of)) else now.best)
        save()
    }

    private fun load(): Tally = if (!file.exists()) Tally() else try {
        val json = JSONObject(file.readText())
        val counts = json.getJSONObject("counts")
        val open = json.getJSONArray("open")
        Tally(
            counts.keys().asSequence().mapNotNull { name -> Topic.entries.firstOrNull { it.name == name }?.let { it to counts.getInt(name) } }.toMap(),
            List(open.length()) { open.getJSONObject(it) }.mapNotNull { o ->
                Topic.entries.firstOrNull { it.name == o.getString("topic") }?.let { Drill(o.getInt("surah"), o.getInt("verse"), it, o.optString("letters")) }
            },
            json.optJSONArray("learned")?.let { all -> List(all.length()) { all.getString(it) }.toSet() }.orEmpty(),
            json.optJSONObject("best")?.let { best -> best.keys().asSequence().mapNotNull { key -> best.optJSONArray(key)?.takeIf { it.length() == 2 }?.let { key to (it.getInt(0) to it.getInt(1)) } }.toMap() }.orEmpty(),
        )
    } catch (_: Exception) {
        // One that can't be read: start afresh, but not by writing over it.
        file.setAside()
        Tally()
    }

    private fun save() {
        val now = _tally.value
        val json = JSONObject()
            .put("counts", JSONObject().apply { for ((topic, count) in now.counts) put(topic.name, count) })
            .put("learned", JSONArray().apply { for (key in now.learned) put(key) })
            .put("best", JSONObject().apply { for ((key, score) in now.best) put(key, JSONArray().put(score.first).put(score.second)) })
            .put("open", JSONArray().apply { for (d in now.open) put(JSONObject().put("surah", d.surah).put("verse", d.verse).put("topic", d.topic.name).put("letters", d.letters)) })
        // With the storage full it can't be kept; the next note tries again.
        try {
            file.writeWhole(json.toString())
        } catch (_: IOException) {
        }
    }

    companion object {
        /** A lesson's name in the file: the topic, and for letters the pair. */
        internal fun key(topic: Topic, letters: String = ""): String = if (letters.isEmpty()) topic.name else "${topic.name}:$letters"

        /** How many verses to go over are kept: the latest. */
        private const val KEPT = 40

        @Volatile private var instance: Mistakes? = null

        fun get(context: Context): Mistakes =
            instance ?: synchronized(this) { instance ?: Mistakes(File(context.applicationContext.filesDir, "mistakes.json")).also { instance = it } }
    }
}
