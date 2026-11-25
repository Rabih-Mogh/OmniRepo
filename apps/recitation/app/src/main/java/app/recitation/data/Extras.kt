package app.recitation.data

import android.content.Context
import app.recitation.core.SURAHS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** What the reader can choose for the page, and have put on the phone if it isn't there. */
enum class Kind(val key: String) {
    /** How the verses are written. */
    SCRIPT("script"),

    /** The language each word's meaning is given in. */
    MEANINGS("meanings"),

    /** The translation of a whole verse. */
    TRANSLATION("translation"),

    /** An exegesis (tafsir) of the verses. */
    EXEGESIS("exegesis"),
}

/**
 * One thing of a [kind] to choose. [id] is a script's key, the code of a language of word
 * meanings, or Quran.com's number for a translation or an exegesis. [name] is what it is
 * called (a script's is in the app's own words instead), [language] the language it is in
 * as Quran.com names it, [author] who made it, and [rtl] whether it reads from the right.
 */
data class Pack(val kind: Kind, val id: String, val name: String, val language: String = "", val author: String = "", val rtl: Boolean = false) {
    /** Its name on the phone's storage, and wherever one is told from another. */
    val key: String get() = "${kind.key}-$id"

    /** Whether it comes with the app or with every surah's own words: nothing more to fetch for it. */
    val inApp: Boolean
        get() = when (kind) {
            Kind.SCRIPT -> id != Script.INDOPAK.key
            Kind.MEANINGS -> id == ENGLISH.id
            Kind.TRANSLATION -> id == SAHEEH.id
            Kind.EXEGESIS -> false
        }

    fun json(): JSONObject = JSONObject().put("kind", kind.key).put("id", id).put("name", name).put("language", language).put("author", author).put("rtl", rtl)

    companion object {
        fun of(json: JSONObject): Pack? {
            val kind = Kind.entries.firstOrNull { it.key == json.optString("kind") } ?: return null
            return Pack(kind, json.optString("id").ifEmpty { return null }, json.optString("name"), json.optString("language"), json.optString("author"), json.optBoolean("rtl"))
        }

        fun of(script: Script) = Pack(Kind.SCRIPT, script.key, script.key)

        /** The script that has to be fetched: its spelling of every word, and its font. */
        val INDOPAK = of(Script.INDOPAK)

        /** Word meanings in English come with every surah's words. */
        val ENGLISH = Pack(Kind.MEANINGS, "en", "English", "english")

        /** The translation that comes with every surah's words. */
        val SAHEEH = Pack(Kind.TRANSLATION, "20", "Saheeh International", "english", "Saheeh International")

        /** The languages Quran.com has each word's meaning in, by their own names. */
        val MEANINGS = listOf(
            ENGLISH,
            Pack(Kind.MEANINGS, "ur", "اردو", "urdu", rtl = true),
            Pack(Kind.MEANINGS, "fa", "فارسی", "persian", rtl = true),
            Pack(Kind.MEANINGS, "tr", "Türkçe", "turkish"),
            Pack(Kind.MEANINGS, "id", "Bahasa Indonesia", "indonesian"),
            Pack(Kind.MEANINGS, "bn", "বাংলা", "bengali"),
            Pack(Kind.MEANINGS, "hi", "हिन्दी", "hindi"),
            Pack(Kind.MEANINGS, "ta", "தமிழ்", "tamil"),
        )

        val SCRIPTS = Script.entries.map(::of)
    }
}

/** What is being fetched right now, and how much of it is on the phone, 0..1. */
class Fetching(val pack: Pack, val fraction: Float)

/** A verse's exegesis as it is shown: [text] is as Quran.com gives it, with its markup; [from] the verse it stands under, which may be an earlier one of a passage explained together. */
class Explained(val pack: Pack, val from: Int, val text: String)

/**
 * The scripts, word meanings, translations and exegeses a reader can choose beyond what
 * comes with the app: from Quran.com, a surah to a file, kept on the phone once fetched.
 * What is asked for is put in a queue ([want]) that the background work empties, the way
 * it downloads everything else (see PrepareWorker): with the app closed, picking up where
 * it stopped, the surah being read first.
 */
class Extras(private val context: Context) {

    private val dir = File(context.filesDir, "extras")
    private fun dir(pack: Pack) = File(dir, pack.key)
    private fun file(pack: Pack, surah: Int) = File(dir(pack), "$surah.json")

    /** Whether [surah] can be shown in [pack] with what is on the phone. */
    fun has(pack: Pack, surah: Int): Boolean = pack.inApp || file(pack, surah).exists()

    /** How many surahs of [pack] are on the phone. */
    fun count(pack: Pack): Int = if (pack.inApp) SURAHS.size else dir(pack).list()?.count { it.endsWith(".json") && it != ABOUT } ?: 0

    /** Whether all of [pack] is on the phone: every surah, and a script's font. */
    fun complete(pack: Pack): Boolean = pack.inApp || (count(pack) == SURAHS.size && (pack != Pack.INDOPAK || indopakFont().exists()))

    private fun indopakFont() = File(File(context.filesDir, "fonts"), "indopak.ttf")

    /** How much room [pack] takes on the phone, in bytes. */
    fun bytes(pack: Pack): Long = dir(pack).listFiles()?.sumOf { it.length() } ?: 0L

    /** Everything fetched so far, whole or not: for listing what is on the phone when the list of what there is can't be had. */
    fun onPhone(kind: Kind): List<Pack> = dir.listFiles { f -> f.isDirectory && f.name.startsWith(kind.key + "-") }.orEmpty()
        .mapNotNull { folder -> runCatching { Pack.of(JSONObject(File(folder, ABOUT).readText())) }.getOrNull() }

    /** Takes [pack] off the phone, and out of the queue. */
    fun delete(pack: Pack) {
        drop(pack)
        dir(pack).deleteRecursively()
    }

    /** The surah of [pack] to fetch next, the one being read ([first]) before the rest; null when none is missing. */
    fun missing(pack: Pack, first: Int = 0): Int? =
        if (pack.inApp) null else (listOf(first) + SURAHS.map { it.number }).firstOrNull { it in 1..SURAHS.size && !file(pack, it).exists() }

    /** Each verse's words of [surah] as [pack] has them: a script's spelling, or the words' meanings. Null if it isn't on the phone. */
    fun words(pack: Pack, surah: Int): List<List<String>>? = read(pack, surah)?.let { verses ->
        List(verses.length()) { v -> verses.getJSONArray(v).let { words -> List(words.length()) { words.optString(it) } } }
    }

    /** Each verse of [surah] as [pack] has it: its translation, or its exegesis. Null if it isn't on the phone. */
    fun texts(pack: Pack, surah: Int): List<String>? = read(pack, surah)?.let { verses -> List(verses.length()) { verses.optString(it) } }

    private fun read(pack: Pack, surah: Int): JSONArray? = file(pack, surah).takeIf { it.exists() }?.let { runCatching { JSONArray(it.readText()) }.getOrNull() }

    /**
     * [verses] of [surah] as the reader has chosen to see them: the words' meanings in
     * [meanings]' language and the verses in [translation], wherever those are on the
     * phone (what comes with the surah's words stands until they are), and the IndoPak
     * spelling if that is the script.
     */
    fun dress(verses: List<Verse>, surah: Int, script: Script, meanings: Pack, translation: Pack): List<Verse> {
        val spelt = if (script == Script.INDOPAK) words(Pack.INDOPAK, surah) else null
        val meant = if (meanings.inApp) null else words(meanings, surah)
        val said = if (translation.inApp) null else texts(translation, surah)
        if (spelt == null && meant == null && said == null) return verses
        return verses.mapIndexed { v, verse ->
            // Word for word only where the counts agree: a word out of step would put every one after it wrong.
            val spelling = spelt?.getOrNull(v)?.takeIf { it.size == verse.words.size }
            val meaning = meant?.getOrNull(v)?.takeIf { it.size == verse.words.size }
            Verse(
                if (spelling == null && meaning == null) verse.words else verse.words.mapIndexed { k, word ->
                    Word(word.arabic, meaning?.get(k)?.ifEmpty { null } ?: word.meaning, word.madinah, spelling?.get(k)?.ifEmpty { null } ?: word.indopak, word.everyday)
                },
                said?.getOrNull(v)?.ifEmpty { null } ?: verse.translation,
            )
        }
    }

    /** The exegesis of [verse] of [surah] in [pack], if that surah is on the phone: the verse's own, or that of the passage it is explained with. */
    fun explained(pack: Pack, surah: Int, verse: Int): Explained? {
        val all = texts(pack, surah) ?: return null
        var from = verse.coerceIn(1, maxOf(1, all.size))
        while (from > 1 && all.getOrNull(from - 1).isNullOrBlank()) from--
        return Explained(pack, from, all.getOrNull(from - 1).orEmpty())
    }

    /** Fetches [surah] of [pack] from Quran.com and keeps it. */
    suspend fun fetch(pack: Pack, surah: Int) = withContext(Dispatchers.IO) {
        val fetched = try {
            when (pack.kind) {
                Kind.SCRIPT -> wordsOf(surah, "word_fields=text_${pack.id}") { it.optString("text_${pack.id}") }
                Kind.MEANINGS -> wordsOf(surah, "word_fields=text_uthmani&language=${pack.id}") { it.optJSONObject("translation")?.optString("text").orEmpty() }
                Kind.TRANSLATION -> translation(pack.id, surah)
                Kind.EXEGESIS -> exegesis(pack.id, surah)
            }
        } catch (e: JSONException) {
            // A page that isn't Quran.com's answer, such as a Wi-Fi sign-in page: no better than no connection.
            throw IOException("Quran.com's answer couldn't be read", e)
        }
        dir(pack).mkdirs()
        File(dir(pack), ABOUT).takeIf { !it.exists() }?.writeWhole(pack.json().toString())
        file(pack, surah).writeWhole(fetched.toString())
        _arrived.tryEmit(pack to surah)
    }

    /** Every verse's words of [surah], asked for with [fields], each word read by [text]. */
    private fun wordsOf(surah: Int, fields: String, text: (JSONObject) -> String): JSONArray {
        val out = JSONArray()
        var page = 1
        while (page > 0) {
            val json = JSONObject(get("$api/verses/by_chapter/$surah?words=true&$fields&per_page=50&page=$page"))
            val verses = json.getJSONArray("verses")
            for (i in 0 until verses.length()) {
                val words = JSONArray()
                val all = verses.getJSONObject(i).getJSONArray("words")
                // The verse-number sign at the end comes as a word too.
                for (k in 0 until all.length()) all.getJSONObject(k).takeIf { it.getString("char_type_name") == "word" }?.let { words.put(text(it)) }
                out.put(words)
            }
            page = json.getJSONObject("pagination").optInt("next_page", 0)
        }
        return whole(out, surah)
    }

    private fun translation(id: String, surah: Int): JSONArray {
        val all = JSONObject(get("$api/quran/translations/$id?chapter_number=$surah")).getJSONArray("translations")
        return whole(JSONArray().also { out -> for (i in 0 until all.length()) out.put(QuranStore.plain(all.getJSONObject(i).optString("text"))) }, surah)
    }

    /** A verse explained together with the ones before it comes with nothing of its own: it is kept empty, in its place. */
    private fun exegesis(id: String, surah: Int): JSONArray {
        val verses = SURAHS[surah - 1].verses
        val texts = arrayOfNulls<String>(verses)
        var page = 1
        while (page > 0) {
            val json = JSONObject(get("$api/tafsirs/$id/by_chapter/$surah?per_page=50&page=$page"))
            val all = json.getJSONArray("tafsirs")
            for (i in 0 until all.length()) {
                val one = all.getJSONObject(i)
                val verse = one.optString("verse_key").substringAfter(':').toIntOrNull() ?: continue
                if (verse in 1..verses) texts[verse - 1] = if (one.isNull("text")) "" else one.optString("text")
            }
            page = json.getJSONObject("pagination").optInt("next_page", 0)
        }
        if (texts.all { it.isNullOrBlank() }) throw IOException("Quran.com sent nothing of surah $surah's exegesis")
        return JSONArray().also { out -> for (text in texts) out.put(text.orEmpty()) }
    }

    /** Kept for good once written, so only all of a surah will do. */
    private fun whole(verses: JSONArray, surah: Int): JSONArray {
        val all = SURAHS[surah - 1].verses
        if (verses.length() != all) throw IOException("Quran.com sent ${verses.length()} of surah $surah's $all verses")
        return verses
    }

    /**
     * What there is to choose of [kind]. The scripts and the languages of word meanings are
     * the app's to know; the translations and the exegeses are asked of Quran.com, and the
     * answer kept, so the list is there without a connection from then on ([fresh] asks
     * again). Throws if it has to be asked for and can't be.
     */
    suspend fun catalogue(kind: Kind, fresh: Boolean = false): List<Pack> = withContext(Dispatchers.IO) {
        when (kind) {
            Kind.SCRIPT -> Pack.SCRIPTS
            Kind.MEANINGS -> Pack.MEANINGS
            Kind.TRANSLATION, Kind.EXEGESIS -> {
                val kept = File(dir, "list-${kind.key}.json")
                if (!fresh) runCatching { packs(JSONArray(kept.readText())) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
                val listed = try {
                    listed(kind)
                } catch (e: JSONException) {
                    throw IOException("Quran.com's answer couldn't be read", e)
                }
                dir.mkdirs()
                kept.writeWhole(JSONArray().also { out -> for (pack in listed) out.put(pack.json()) }.toString())
                listed
            }
        }
    }

    private fun packs(json: JSONArray): List<Pack> = List(json.length()) { json.getJSONObject(it) }.mapNotNull(Pack::of)

    private fun listed(kind: Kind): List<Pack> {
        // Which languages read from the right, by the names the lists use for them.
        val fromTheRight = runCatching {
            val languages = JSONObject(get("$api/resources/languages")).getJSONArray("languages")
            List(languages.length()) { languages.getJSONObject(it) }.filter { it.optString("direction") == "rtl" }.map { it.optString("name").lowercase() }.toSet()
        }.getOrDefault(emptySet()) + FROM_THE_RIGHT
        val name = if (kind == Kind.TRANSLATION) "translations" else "tafsirs"
        val all = JSONObject(get("$api/resources/$name")).getJSONArray(name)
        return List(all.length()) { all.getJSONObject(it) }.map { one ->
            val language = one.optString("language_name").lowercase()
            Pack(kind, one.getInt("id").toString(), one.optString("name").trim(), language, one.optString("author_name").trim(), language in fromTheRight)
        }
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

    // ---- the queue

    private val queue = File(dir, "wanted.json")
    private val _wanted = MutableStateFlow(runCatching { packs(JSONArray(queue.readText())) }.getOrDefault(emptyList()))

    /** What has been asked for and isn't all on the phone yet, in the order it will be fetched. */
    val wanted: StateFlow<List<Pack>> = _wanted.asStateFlow()

    /** Asks for [pack] to be put on the phone. The one asked for last is fetched first: it is the one being waited for. */
    @Synchronized
    fun want(pack: Pack) {
        if (complete(pack)) return
        keep(listOf(pack) + _wanted.value.filter { it.key != pack.key })
    }

    /** Takes [pack] out of the queue: it is all here, or no longer wanted. */
    @Synchronized
    fun drop(pack: Pack) {
        if (_wanted.value.any { it.key == pack.key }) keep(_wanted.value.filter { it.key != pack.key })
        if (progress.value?.pack?.key == pack.key) progress.value = null
    }

    private fun keep(packs: List<Pack>) {
        _wanted.value = packs
        // With the storage full it isn't kept; what is on the screen still stands.
        runCatching {
            dir.mkdirs()
            queue.writeWhole(JSONArray().also { out -> for (pack in packs) out.put(pack.json()) }.toString())
        }
    }

    /** What the background work is fetching right now; null when nothing. */
    val progress = MutableStateFlow<Fetching?>(null)

    private val _arrived = MutableSharedFlow<Pair<Pack, Int>>(extraBufferCapacity = 256)

    /** A surah of something, just put on the phone. */
    val arrived: SharedFlow<Pair<Pack, Int>> = _arrived.asSharedFlow()

    companion object {
        /** Where Quran.com is asked. */
        @Volatile internal var api = "https://api.quran.com/api/v4"

        /** The surah being read: fetched before the rest of whatever is being fetched. */
        @Volatile var reading = 0

        /** In each folder: what it is the folder of. */
        private const val ABOUT = "about.json"

        /** Languages written from the right, should the list of languages not be had. */
        private val FROM_THE_RIGHT = setOf("arabic", "urdu", "persian", "pashto", "sindhi", "uyghur", "hebrew", "divehi", "dari", "kurdish")

        @Volatile private var instance: Extras? = null

        fun get(context: Context): Extras = instance ?: synchronized(this) { instance ?: Extras(context.applicationContext).also { instance = it } }
    }
}
