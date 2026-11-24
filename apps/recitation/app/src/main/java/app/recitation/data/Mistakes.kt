package app.recitation.data

import android.content.Context
import app.recitation.core.Drill
import app.recitation.core.Drills
import app.recitation.core.Topic
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
 * each as the go of an exercise that practises it.
 */
class Tally(val counts: Map<Topic, Int> = emptyMap(), val open: List<Drill> = emptyList()) {
    /** The topics corrected most often, the most first: where the reader is weakest. */
    val weakest: List<Pair<Topic, Int>> get() = counts.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key to it.value }
}

/**
 * Keeps a [Tally] in a small file on the phone. Every verse recited to the phone is noted:
 * what was corrected in it is added, and what was corrected in it before and is sound now
 * is taken off the list of verses to go over.
 */
class Mistakes(private val file: File) {
    private val _tally = MutableStateFlow(load())
    val tally: StateFlow<Tally> = _tally.asStateFlow()

    /** Notes how [verse] of [surah] (both from 1) went: [words] as it was heard. */
    @Synchronized
    fun note(surah: Int, verse: Int, words: List<WordRecited>) {
        val now = _tally.value
        val counts = HashMap(now.counts)
        val missed = LinkedHashSet<Drill>()
        for (correction in words.flatMap { it.corrections }) {
            val topic = Drills.topicOf(correction) ?: continue
            // A letter said for another is practised only where the two are a pair that is drilled.
            val letters = if (topic == Topic.LETTERS) Drills.pairOf(correction) else ""
            if (topic == Topic.LETTERS && letters.isEmpty()) continue
            missed += Drill(surah, verse, topic, letters)
        }
        for (drill in missed) counts[drill.topic] = (counts[drill.topic] ?: 0) + 1
        // Recited again: what was to go over in it stands only if it still is.
        val open = (missed + now.open.filter { it.surah != surah || it.verse != verse }).take(KEPT)
        if (missed.isEmpty() && open.size == now.open.size) return
        _tally.value = Tally(counts, open)
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
            .put("open", JSONArray().apply { for (d in now.open) put(JSONObject().put("surah", d.surah).put("verse", d.verse).put("topic", d.topic.name).put("letters", d.letters)) })
        // With the storage full it can't be kept; the next note tries again.
        try {
            file.writeWhole(json.toString())
        } catch (_: IOException) {
        }
    }

    companion object {
        /** How many verses to go over are kept: the latest. */
        private const val KEPT = 40

        @Volatile private var instance: Mistakes? = null

        fun get(context: Context): Mistakes =
            instance ?: synchronized(this) { instance ?: Mistakes(File(context.applicationContext.filesDir, "mistakes.json")).also { instance = it } }
    }
}
