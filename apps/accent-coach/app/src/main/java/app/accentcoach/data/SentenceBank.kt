package app.accentcoach.data

import android.content.Context
import app.accentcoach.core.Sentence
import app.accentcoach.core.Word
import org.json.JSONArray
import org.json.JSONObject

/** The practice sentences and the recogniser's symbols, bundled in assets/. */
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
                words = List(words.length()) { w ->
                    val word = words.getJSONObject(w)
                    val p = word.getJSONArray("phonemes")
                    Word(word.getString("text"), List(p.length()) { p.getString(it) })
                },
            )
        }
    }

    /** Symbols in the recogniser's output order (index → symbol). */
    fun vocab(context: Context): List<String> {
        val obj = JSONObject(context.assets.open("vocab.json").bufferedReader().use { it.readText() })
        val out = arrayOfNulls<String>(obj.length())
        for (key in obj.keys()) out[obj.getInt(key)] = key
        return out.map { it ?: "" }
    }
}
