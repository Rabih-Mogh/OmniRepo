package app.accentcoach.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.accentcoach.core.MyWord
import app.accentcoach.core.Word
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Keeps the words the learner added in a small file on the phone, the latest first. */
class Wordbook(private val file: File) {

    var words by mutableStateOf(load())
        private set

    /** Adds [word] at the top; one with the same text is replaced, keeping its best score. */
    fun add(word: MyWord) {
        val before = words.firstOrNull { it.text.equals(word.text, ignoreCase = true) }
        words = listOf(word.copy(best = word.best ?: before?.best)) + words.filter { it !== before }
        save()
    }

    fun remove(text: String) {
        words = words.filter { it.text != text }
        save()
    }

    /** Notes that [text] was said with [score]; the best one is kept. */
    fun scored(text: String, score: Int) {
        words = words.map { if (it.text == text) it.copy(best = maxOf(score, it.best ?: 0)) else it }
        save()
    }

    private fun load(): List<MyWord> = if (!file.exists()) emptyList() else try {
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val w = arr.getJSONObject(i)
            val parts = w.getJSONArray("words")
            MyWord(
                text = w.getString("text"),
                words = List(parts.length()) {
                    val part = parts.getJSONObject(it)
                    val p = part.getJSONArray("phonemes")
                    Word(part.getString("text"), List(p.length()) { k -> p.getString(k) })
                },
                heard = w.getBoolean("heard"),
                best = if (w.has("best")) w.getInt("best") else null,
            )
        }
    } catch (_: Exception) {
        // One that can't be read: start afresh, keeping it beside in case it can be mended.
        file.renameTo(File(file.path + ".unread"))
        emptyList()
    }

    private fun save() {
        val arr = JSONArray()
        for (word in words) {
            val parts = JSONArray()
            for (part in word.words) parts.put(JSONObject().put("text", part.text).put("phonemes", JSONArray(part.phonemes)))
            val w = JSONObject().put("text", word.text).put("words", parts).put("heard", word.heard)
            word.best?.let { w.put("best", it) }
            arr.put(w)
        }
        // Written beside and moved into place, so a write cut short doesn't lose what was there.
        try {
            val beside = File(file.path + ".new")
            beside.writeText(arr.toString())
            if (!beside.renameTo(file)) throw IOException("couldn't replace ${file.name}")
        } catch (_: IOException) {
        }
    }
}
