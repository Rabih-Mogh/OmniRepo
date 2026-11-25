package app.recitation.data

import android.content.Context
import app.recitation.core.SURAHS
import app.recitation.core.SurahSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * One word. [arabic] is its Uthmani spelling, which the recitation is lined up with; the
 * others are the same word as the other scripts write it.
 */
class Word(val arabic: String, val meaning: String, val madinah: String = arabic, val indopak: String = arabic, val everyday: String = arabic) {
    fun shownIn(script: Script): String = when (script) {
        Script.MADINAH -> madinah
        Script.AMIRI -> arabic
        Script.INDOPAK -> indopak
        Script.EVERYDAY -> everyday
    }
}

class Verse(val words: List<Word>, val translation: String)

/** A verse found by searching: where it is, its words as the Madinah mushaf writes them, and its translation. */
class Hit(val at: VerseRef, val arabic: String, val translation: String)

/**
 * The words of a surah as the Madinah mushaf writes them and in everyday spelling (which
 * is also what a search goes by), each word's meaning in English, and the verse's
 * translation (Saheeh International), from Quran.com. A surah is fetched the first time
 * it is needed and kept on the phone from then on; the app also fetches the rest quietly
 * while it is open, so the whole Quran ends up readable offline. Anything else a reader
 * chooses (another script's spelling, meanings in another language, another translation,
 * an exegesis) is fetched when it is chosen: see [Extras].
 */
class QuranStore(context: Context, private val api: String = API) {

    private val dir = File(context.filesDir, "quran")

    fun has(surah: Int): Boolean = file(surah).exists()

    /** Whether [surah] can be read with no connection: [has], or what an earlier version kept. */
    fun onPhone(surah: Int): Boolean = has(surah) || old(surah).exists()

    private fun file(surah: Int) = File(dir, "$surah.v2.json")

    /** What earlier versions of the app kept: the Uthmani spelling only. */
    private fun old(surah: Int) = File(dir, "$surah.json")

    /** The surah's verses, from the phone if they're there, else from Quran.com. */
    suspend fun surah(surah: Int): List<Verse> = withContext(Dispatchers.IO) {
        val file = file(surah)
        // The last few surahs read are kept as read: a list of kept verses asks for the same
        // surah once for every verse it shows of it.
        synchronized(lately) { lately[file.path]?.takeIf { it.first == file.lastModified() }?.second }?.let { return@withContext it }
        read(file)?.also { synchronized(lately) { lately[file.path] = file.lastModified() to it } } ?: fetching.withLock {
            // One fetch at a time: the same surah can be asked for by the screen and the background at once.
            read(file) ?: try {
                val fetched = fetch(surah).toString()
                file.writeWhole(fetched)
                old(surah).delete()
                verses(fetched)
            } catch (e: IOException) {
                // No connection: what an earlier version kept will do, in the one script it has.
                read(old(surah)) ?: throw e
            }
        }
    }

    /** The verses kept in [file]; null if it isn't there, or holds something that can't be read, which is then fetched again. */
    private fun read(file: File): List<Verse>? = if (!file.exists()) null else runCatching { verses(file.readText()) }.getOrNull()

    private fun verses(kept: String): List<Verse> {
        val verses = JSONArray(kept)
        return List(verses.length()) { i ->
            val verse = verses.getJSONObject(i)
            val words = verse.getJSONArray("w")
            Verse(
                List(words.length()) { k ->
                    val word = words.getJSONArray(k)
                    val uthmani = word.getString(0)
                    fun other(at: Int) = word.optString(at).ifEmpty { uthmani }
                    Word(uthmani, word.getString(1), other(2), other(3), other(4))
                },
                verse.getString("t"),
            )
        }
    }

    /**
     * The verses containing [query], in the Quran's order, [limit] at most: Arabic letters
     * are looked for in the verses' words (whatever their vowel marks), anything else in
     * the translation. Only the surahs already on the phone are searched.
     */
    suspend fun search(query: String, limit: Int = 60): List<Hit> = withContext(Dispatchers.IO) {
        val wanted = SurahSearch.plain(query)
        if (wanted.length < SHORTEST_SEARCH) return@withContext emptyList()
        val inArabic = wanted.any { it in '\u0600'..'\u06FF' }
        index().asSequence().filter { wanted in if (inArabic) it.arabic else it.translation }.take(limit)
            .map { Hit(it.at, it.shown, it.said) }.toList()
    }

    /** Every verse on the phone, reduced for comparing; surahs fetched since last time are added. */
    private suspend fun index(): List<Indexed> = indexing.withLock {
        for (surah in SURAHS) {
            if (surah.number in indexed || !has(surah.number)) continue
            val verses = runCatching { surah(surah.number) }.getOrNull() ?: continue
            verses.forEachIndexed { v, verse ->
                index += Indexed(
                    VerseRef(surah.number, v + 1), verse.words.joinToString(" ") { it.madinah }, verse.translation,
                    SurahSearch.plain(verse.words.joinToString("") { it.everyday }), SurahSearch.plain(verse.translation),
                )
            }
            indexed += surah.number
            index.sortBy { it.at }
        }
        index.toList()
    }

    private class Indexed(val at: VerseRef, val shown: String, val said: String, val arabic: String, val translation: String)

    /** All verses, 50 to a page as the service allows, in the compact form kept on the phone. */
    private fun fetch(surah: Int): JSONArray = try {
        fetched(surah)
    } catch (e: JSONException) {
        // A page that isn't Quran.com's answer, such as a Wi-Fi sign-in page: no better than no connection.
        throw IOException("Quran.com's answer couldn't be read", e)
    }

    private fun fetched(surah: Int): JSONArray {
        val out = JSONArray()
        var page = 1
        while (page > 0) {
            val json = JSONObject(get("$api/verses/by_chapter/$surah?words=true&word_fields=text_uthmani,text_qpc_hafs,text_imlaei&translations=$SAHEEH_INTERNATIONAL&language=en&per_page=50&page=$page"))
            val verses = json.getJSONArray("verses")
            for (i in 0 until verses.length()) {
                val verse = verses.getJSONObject(i)
                val words = JSONArray()
                val all = verse.getJSONArray("words")
                for (k in 0 until all.length()) {
                    val word = all.getJSONObject(k)
                    // The verse-number sign at the end comes as a word too.
                    if (word.getString("char_type_name") != "word") continue
                    words.put(
                        JSONArray().put(word.getString("text_uthmani")).put(word.optJSONObject("translation")?.optString("text").orEmpty())
                            // The IndoPak spelling isn't fetched with the rest: it is fetched when that script is chosen (see Extras).
                            .put(text(word, "text_qpc_hafs")).put("").put(text(word, "text_imlaei")),
                    )
                }
                val translation = verse.optJSONArray("translations")?.optJSONObject(0)?.optString("text").orEmpty()
                out.put(JSONObject().put("w", words).put("t", plain(translation)))
            }
            page = json.getJSONObject("pagination").optInt("next_page", 0)
        }
        // Kept for good once written, so only all of it will do.
        val whole = SURAHS[surah - 1].verses
        if (out.length() != whole) throw IOException("Quran.com sent ${out.length()} of surah $surah's $whole verses")
        return out
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Quran.com answered HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val fetching = Mutex()

        /** The surahs read last, by their file and when it was written. */
        private val lately = object : LinkedHashMap<String, Pair<Long, List<Verse>>>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<Verse>>>?) = size > 4
        }

        // One index for the whole app, built the first time something is searched for.
        private val indexing = Mutex()
        private val index = ArrayList<Indexed>()
        private val indexed = HashSet<Int>()

        /** Fewer letters than this find too much to be of use. */
        const val SHORTEST_SEARCH = 3
        private const val API = "https://api.quran.com/api/v4"
        private const val SAHEEH_INTERNATIONAL = 20

        /** A word in one script; empty when the service has none (optString alone would say "null"). */
        private fun text(word: JSONObject, field: String): String = if (word.isNull(field)) "" else word.optString(field)

        /** The translation without its footnote numbers and other markup. */
        internal fun plain(html: String): String =
            html.replace(Regex("<sup[^>]*>.*?</sup>"), "").replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()
    }
}
