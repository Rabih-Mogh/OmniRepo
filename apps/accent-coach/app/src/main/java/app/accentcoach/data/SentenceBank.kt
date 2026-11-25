package app.accentcoach.data

import android.content.Context
import app.accentcoach.core.Lesson
import app.accentcoach.core.Sentence
import app.accentcoach.core.Word
import org.json.JSONArray
import org.json.JSONObject

/** The practice sentences, the sound lessons and the recogniser's symbols, bundled in assets/. */
object SentenceBank {

    fun sentences(context: Context): List<Sentence> {
        val arr = JSONArray(context.assets.open("sentences.json").bufferedReader().use { it.readText() })
        return List(arr.length()) { i ->
            val s = arr.getJSONObject(i)
            val words = s.getJSONArray("words")
            Sentence(
                id = s.getString("id"),
                focus = s.getString("focus"),
                text = s.getString("text"),
                words = List(words.length()) { word(words.getJSONObject(it)) },
            )
        }
    }

    fun lessons(context: Context): List<Lesson> {
        val arr = JSONArray(context.assets.open("lessons.json").bufferedReader().use { it.readText() })
        return List(arr.length()) { i ->
            val l = arr.getJSONObject(i)
            val words = l.getJSONArray("words")
            val pairs = l.getJSONArray("pairs")
            Lesson(
                id = l.getString("id"),
                title = l.getString("title"),
                sound = l.getString("sound"),
                made = l.getString("made"),
                slip = l.getString("slip"),
                words = List(words.length()) { word(words.getJSONObject(it)) },
                pairs = List(pairs.length()) {
                    val pair = pairs.getJSONArray(it)
                    word(pair.getJSONObject(0)) to word(pair.getJSONObject(1))
                },
            )
        }
    }

    private fun word(json: JSONObject): Word {
        val p = json.getJSONArray("phonemes")
        return Word(json.getString("text"), List(p.length()) { p.getString(it) })
    }

    /** Symbols in the recogniser's output order (index → symbol). */
    fun vocab(context: Context): List<String> {
        val obj = JSONObject(context.assets.open("vocab.json").bufferedReader().use { it.readText() })
        val out = arrayOfNulls<String>(obj.length())
        for (key in obj.keys()) out[obj.getInt(key)] = key
        return out.map { it ?: "" }
    }
}
