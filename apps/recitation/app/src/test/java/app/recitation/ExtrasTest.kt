package app.recitation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.data.Extras
import app.recitation.data.Kind
import app.recitation.data.Pack
import app.recitation.data.Script
import app.recitation.data.Verse
import app.recitation.data.Word
import app.recitation.ui.passages
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * What a reader chooses for the page beyond what comes with the app: fetched a surah at
 * a time from a small server on this machine standing in for Quran.com, kept, and put on
 * the verses. Al-Kawthar is asked for: three verses, here of two words each.
 */
@RunWith(AndroidJUnit4::class)
class ExtrasTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val asked = ArrayList<String>()
    private var down = false
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                asked += path
                if (down) return MockResponse().setResponseCode(503)
                return MockResponse().setBody(
                    when {
                        "/verses/by_chapter/108" in path -> words(if ("language=ur" in path) "translation" else "text_indopak")
                        "/quran/translations/131" in path -> """{"translations":[{"text":"Un<sup foot_note=1>1</sup>"},{"text":"Deux"},{"text":"Trois"}]}"""
                        "/tafsirs/169/by_chapter/108" in path -> """{"tafsirs":[{"verse_key":"108:1","text":"<h2>The river</h2><p>Al-Kawthar is a river.</p><p>Second paragraph.</p>"},{"verse_key":"108:2","text":""},{"verse_key":"108:3","text":"<p>The one cut off.</p>"}],"pagination":{"next_page":null}}"""
                        // QuranEnc.com's Arabic word meanings: a verse at a time, and none for the second verse.
                        "/translation/sura/arabic_seraj/108" in path -> """{"result":[{"sura":"108","aya":"1","translation":"كلمة01: المعنى الأول."},{"sura":"108","aya":"2","translation":""},{"sura":"108","aya":"3","translation":"كلمة20 كلمة21: معنى الكلمتين معاً.\nغريبة: ليست في الآية."}]}"""
                        "/resources/languages" in path -> """{"languages":[{"name":"Urdu","direction":"rtl"},{"name":"French","direction":"ltr"}]}"""
                        "/resources/translations" in path -> """{"translations":[{"id":131,"name":"Hamidullah","author_name":"Muhammad Hamidullah","language_name":"french"},{"id":97,"name":"Maududi","author_name":"Abul Ala Maududi","language_name":"urdu"}]}"""
                        "/resources/tafsirs" in path -> """{"tafsirs":[{"id":169,"name":"Ibn Kathir (Abridged)","author_name":"Hafiz Ibn Kathir","language_name":"english"}]}"""
                        else -> "{}"
                    },
                )
            }
        }
        start()
    }

    /** Every verse's two words, each with its meaning in Urdu or its IndoPak spelling, and the sign that ends a verse. */
    private fun words(field: String) = List(3) { v ->
        List(2) { k -> if (field == "translation") """{"char_type_name":"word","translation":{"text":"معنی$v$k"}}""" else """{"char_type_name":"word","text_indopak":"هند$v$k"}""" }
            .joinToString(",", """{"words":[""", """,{"char_type_name":"end"}]}""")
    }.joinToString(",", """{"verses":[""", """],"pagination":{"next_page":null}}""")

    private val extras get() = Extras(context)
    private val urdu = Pack.MEANINGS.first { it.id == "ur" }
    private val french = Pack(Kind.TRANSLATION, "131", "Hamidullah", "french", "Muhammad Hamidullah")
    private val ibnKathir = Pack(Kind.EXEGESIS, "169", "Ibn Kathir (Abridged)", "english", "Hafiz Ibn Kathir")
    private val kawthar = List(3) { v -> Verse(List(2) { k -> Word("كلمة$v$k", "word $v$k") }, "Verse $v") }

    @Before
    fun there() {
        Extras.api = server.url("/").toString().trimEnd('/')
        Extras.quranEnc = server.url("/").toString().trimEnd('/')
    }

    @After
    fun stop() {
        server.shutdown()
        for (pack in listOf(urdu, french, ibnKathir, Pack.INDOPAK, Pack.ARABIC)) extras.delete(pack)
    }

    @Test
    fun whatIsChosenIsFetchedASurahAtATimeAndPutOnTheVerses() = runBlocking {
        val extras = extras
        assertFalse(extras.has(urdu, 108))
        // Nothing of it on the phone: the verses stand as they came.
        assertTrue(extras.dress(kawthar, 108, Script.MADINAH, urdu, french) === kawthar)
        extras.fetch(urdu, 108)
        extras.fetch(french, 108)
        assertTrue(extras.has(urdu, 108) && extras.has(french, 108))
        val dressed = extras.dress(kawthar, 108, Script.MADINAH, urdu, french)
        assertEquals(listOf("معنی00", "معنی01"), dressed[0].words.map { it.meaning })
        assertEquals("the words themselves are untouched", "كلمة21", dressed[2].words[1].arabic)
        assertEquals("a translation is kept without its footnote marks", listOf("Un", "Deux", "Trois"), dressed.map { it.translation })
        // What comes with the surah's own words needs nothing fetched, and stands.
        assertEquals("word 00", extras.dress(kawthar, 108, Script.MADINAH, Pack.ENGLISH, french)[0].words[0].meaning)
        assertEquals("Verse 1", extras.dress(kawthar, 108, Script.MADINAH, urdu, Pack.SAHEEH)[1].translation)
        // Kept: another look asks for nothing.
        val before = asked.size
        Extras(context).dress(kawthar, 108, Script.MADINAH, urdu, french)
        assertEquals(before, asked.size)
    }

    @Test
    fun wordMeaningsInArabicComeAVerseAtATimeAndStandUnderTheWordsTheyExplain() = runBlocking {
        val extras = extras
        // Arabic is among the languages to choose, though Quran.com has no word meanings in it.
        assertTrue(Pack.ARABIC in extras.catalogue(Kind.MEANINGS))
        assertFalse(Pack.ARABIC.inApp)
        // Until its surah is on the phone, the meanings that came with the words stand.
        assertEquals("word 00", extras.dress(kawthar, 108, Script.MADINAH, Pack.ARABIC, Pack.SAHEEH)[0].words[0].meaning)
        extras.fetch(Pack.ARABIC, 108)
        assertTrue("it is asked of QuranEnc.com", asked.any { "/translation/sura/arabic_seraj/108" in it })
        val dressed = extras.dress(kawthar, 108, Script.MADINAH, Pack.ARABIC, Pack.SAHEEH)
        // The word explained has its meaning (these two are the same letters, so the first is taken); the one
        // beside it has none, and no English one in its place.
        assertEquals(listOf("المعنى الأول.", ""), dressed[0].words.map { it.meaning })
        // A verse with nothing to explain has nothing under any word.
        assertEquals(listOf("", ""), dressed[1].words.map { it.meaning })
        // A meaning of two words stands under the first; a line whose words aren't in the verse is kept whole under the last.
        assertEquals(listOf("معنى الكلمتين معاً.", "غريبة: ليست في الآية."), dressed[2].words.map { it.meaning })
        // The translation is another choice, and untouched by this one.
        assertEquals("Verse 0", dressed[0].translation)
        // Kept as it came, a verse to a line.
        assertEquals(3, extras.texts(Pack.ARABIC, 108)?.size)
    }

    @Test
    fun aScriptsSpellingIsUsedOnlyWhenThatScriptIsChosenAndItsWordsLineUp() = runBlocking {
        val extras = extras
        extras.fetch(Pack.INDOPAK, 108)
        assertEquals("هند10", extras.dress(kawthar, 108, Script.INDOPAK, Pack.ENGLISH, Pack.SAHEEH)[1].words[0].shownIn(Script.INDOPAK))
        assertTrue(extras.dress(kawthar, 108, Script.MADINAH, Pack.ENGLISH, Pack.SAHEEH) === kawthar)
        // A verse counted differently by the two: left as it is rather than put a word out of step.
        val odd = kawthar.mapIndexed { v, verse -> if (v == 0) Verse(verse.words + Word("زائدة", "extra"), verse.translation) else verse }
        val dressed = extras.dress(odd, 108, Script.INDOPAK, Pack.ENGLISH, Pack.SAHEEH)
        assertEquals("كلمة00", dressed[0].words[0].shownIn(Script.INDOPAK))
        assertEquals("هند20", dressed[2].words[0].shownIn(Script.INDOPAK))
        // All of it means every surah, and the font too.
        assertFalse(extras.complete(Pack.INDOPAK))
        assertEquals(1, extras.count(Pack.INDOPAK))
    }

    @Test
    fun aVerseExplainedWithTheOnesBeforeItShowsTheirExegesis() = runBlocking {
        val extras = extras
        assertNull(extras.explained(ibnKathir, 108, 2))
        extras.fetch(ibnKathir, 108)
        val second = extras.explained(ibnKathir, 108, 2)!!
        assertEquals(1, second.from)
        assertEquals(listOf(true to "The river", false to "Al-Kawthar is a river.", false to "Second paragraph."), passages(second.text).map { it.heading to it.text })
        assertEquals(3, extras.explained(ibnKathir, 108, 3)!!.from)
        assertEquals(listOf("The one cut off."), passages(extras.explained(ibnKathir, 108, 3)!!.text).map { it.text })
    }

    @Test
    fun whatCannotBeFetchedWholeIsNotKept() {
        down = true
        assertThrows(IOException::class.java) { runBlocking { extras.fetch(french, 108) } }
        assertFalse(extras.has(french, 108))
        down = false
        // Three verses of a surah of seven: not this surah's translation.
        assertThrows(IOException::class.java) { runBlocking { extras.fetch(Pack(Kind.TRANSLATION, "131", "Hamidullah"), 1) } }
        assertFalse(extras.has(french, 1))
    }

    @Test
    fun theListOfWhatThereIsIsAskedForOnceAndKept() = runBlocking {
        assertEquals(Script.entries.map { it.key }, extras.catalogue(Kind.SCRIPT).map { it.id })
        assertTrue(extras.catalogue(Kind.MEANINGS).first() == Pack.ENGLISH)
        val listed = extras.catalogue(Kind.TRANSLATION, fresh = true)
        assertEquals(listOf("131", "97"), listed.map { it.id })
        assertEquals(french, listed[0])
        assertTrue("Urdu reads from the right", listed[1].rtl && !listed[0].rtl)
        // With no connection the list is still there.
        down = true
        assertEquals(listed, Extras(context).catalogue(Kind.TRANSLATION))
        assertThrows(IOException::class.java) { runBlocking { extras.catalogue(Kind.TRANSLATION, fresh = true) } }
        down = false
        assertEquals(listOf(ibnKathir), extras.catalogue(Kind.EXEGESIS, fresh = true))
    }

    @Test
    fun whatIsAskedForWaitsInAQueueThatOutlastsTheApp() = runBlocking {
        val extras = extras
        extras.want(Pack.ENGLISH)
        assertEquals("what comes with the app is never fetched", emptyList<Pack>(), extras.wanted.value)
        extras.want(urdu)
        extras.want(french)
        assertEquals("the one asked for last is the one being waited for", listOf(french, urdu), extras.wanted.value)
        assertEquals(listOf(french, urdu), Extras(context).wanted.value)
        // The surah being read is fetched before the rest.
        assertEquals(36, extras.missing(french, first = 36))
        assertEquals(1, extras.missing(french))
        extras.fetch(urdu, 108)
        assertEquals(listOf(urdu), extras.onPhone(Kind.MEANINGS))
        assertTrue(extras.bytes(urdu) > 0)
        extras.delete(urdu)
        assertFalse(extras.has(urdu, 108))
        assertEquals(listOf(french), extras.wanted.value)
        extras.drop(french)
        assertEquals(emptyList<Pack>(), Extras(context).wanted.value)
    }
}
