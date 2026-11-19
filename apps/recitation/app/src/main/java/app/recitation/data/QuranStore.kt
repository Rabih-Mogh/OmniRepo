package app.recitation.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class Word(val arabic: String, val meaning: String)

class Verse(val words: List<Word>, val translation: String)

/**
 * The words of a surah in the Uthmani script, each word's meaning in English, and the
 * verse's translation (Saheeh International), from Quran.com. A surah is fetched the
 * first time it is needed and kept on the phone from then on; the app also fetches the
 * rest quietly while it is open, so the whole Quran ends up readable offline.
 */
class QuranStore(context: Context) {

    private val dir = File(context.filesDir, "quran")

    fun has(surah: Int): Boolean = file(surah).exists()

    private fun file(surah: Int) = File(dir, "$surah.json")

    /** The surah's verses, from the phone if they're there, else from Quran.com. */
    suspend fun surah(surah: Int): List<Verse> = withContext(Dispatchers.IO) {
        val file = file(surah)
        if (!file.exists()) {
            // One fetch at a time: the same surah can be asked for by the screen and the background at once.
            fetching.withLock {
                if (!file.exists()) {
                    dir.mkdirs()
                    val tmp = File(dir, "$surah.json.tmp")
                    tmp.writeText(fetch(surah).toString())
                    tmp.renameTo(file)
                }
            }
        }
        val verses = JSONArray(file.readText())
        List(verses.length()) { i ->
            val verse = verses.getJSONObject(i)
            val words = verse.getJSONArray("w")
            Verse(
                List(words.length()) { k -> words.getJSONArray(k).let { Word(it.getString(0), it.getString(1)) } },
                verse.getString("t"),
            )
        }
    }

    /** All verses, 50 to a page as the service allows, in the compact form kept on the phone. */
    private fun fetch(surah: Int): JSONArray {
        val out = JSONArray()
        var page = 1
        while (page > 0) {
            val json = JSONObject(get("$API/verses/by_chapter/$surah?words=true&word_fields=text_uthmani&translations=$SAHEEH_INTERNATIONAL&language=en&per_page=50&page=$page"))
            val verses = json.getJSONArray("verses")
            for (i in 0 until verses.length()) {
                val verse = verses.getJSONObject(i)
                val words = JSONArray()
                val all = verse.getJSONArray("words")
                for (k in 0 until all.length()) {
                    val word = all.getJSONObject(k)
                    // The verse-number sign at the end comes as a word too.
                    if (word.getString("char_type_name") != "word") continue
                    words.put(JSONArray().put(word.getString("text_uthmani")).put(word.optJSONObject("translation")?.optString("text").orEmpty()))
                }
                val translation = verse.optJSONArray("translations")?.optJSONObject(0)?.optString("text").orEmpty()
                out.put(JSONObject().put("w", words).put("t", plain(translation)))
            }
            page = json.getJSONObject("pagination").optInt("next_page", 0)
        }
        if (out.length() == 0) error("Quran.com sent no verses for surah $surah")
        return out
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("Quran.com answered HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val fetching = Mutex()
        private const val API = "https://api.quran.com/api/v4"
        private const val SAHEEH_INTERNATIONAL = 20

        /** The translation without its footnote numbers and other markup. */
        internal fun plain(html: String): String =
            html.replace(Regex("<sup[^>]*>.*?</sup>"), "").replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()
    }
}
